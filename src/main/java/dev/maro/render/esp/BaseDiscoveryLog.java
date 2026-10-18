package dev.maro.render.esp;

import com.google.gson.*;
import net.minecraft.util.math.Box;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Local discoveries, separate from profiles and from the currently rendered chunks. */
public final class BaseDiscoveryLog {
    public enum Condition { LikelyIntact, LikelyRaided, Uncertain }
    public record Sighting(Box bounds, int storage, Condition condition, boolean complete) {}
    public record Totals(int today, int total, int automaticToday, int correction, int raided) {}
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Maro base history"); t.setDaemon(true); return t;
    });
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            WRITER.shutdown();
            try{WRITER.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        },"Maro base history flush"));
    }
    private final Path file;
    private final Clock clock;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String,Integer> corrections = new TreeMap<>();
    private volatile boolean saveFailed;
    private boolean readable = true;
    private long revision;
    private long boundsRevision=-1,totalsRevision=-1;
    private List<Box> cachedBounds=List.of();
    private String totalsDay;
    private Totals cachedTotals;
    private static final class Entry {
        String id, day; long firstSeen, lastSeen;
        double x0,y0,z0,x1,y1,z1; int peakStorage, storage;
        Condition condition;
        Box box() { return new Box(x0,y0,z0,x1,y1,z1); }
        void bounds(Box b) { x0=b.minX;y0=b.minY;z0=b.minZ;x1=b.maxX;y1=b.maxY;z1=b.maxZ; }
    }

    public BaseDiscoveryLog(Path folder, String scope, Clock clock) {
        this.clock=clock;
        this.file=folder.resolve(scopeHash(scope)+".json");
        load();
    }
    public static String scopeHash(String scope) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Path file() { return file; }
    public boolean saveFailed() { return saveFailed || !readable; }
    public List<Box> knownBounds() {
        if(boundsRevision!=revision){cachedBounds=entries.stream().map(Entry::box).toList();boundsRevision=revision;}
        return cachedBounds;
    }
    public static boolean sameSite(Box a, Box b) {
        double verticalGap=Math.max(a.minY-b.maxY,b.minY-a.maxY);
        if(verticalGap>12)return false;
        double overlapX=Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX));
        double overlapZ=Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));
        double small=Math.min((a.maxX-a.minX)*(a.maxZ-a.minZ),(b.maxX-b.minX)*(b.maxZ-b.minZ));
        if(small>0 && overlapX*overlapZ/small>=.5)return true;
        var ac=a.getCenter();var bc=b.getCenter();
        return Math.abs(ac.y-bc.y)<=12 && Math.hypot(ac.x-bc.x,ac.z-bc.z)<=10;
    }
    /** Partial views can match a discovery, but cannot downgrade its condition. */
    public void observe(List<Sighting> sightings) {
        boolean changed=false; long now=clock.millis(); String day=LocalDate.now(clock).toString();
        for(Sighting s:sightings) {
            List<Entry> matches=entries.stream().filter(e->sameSite(e.box(),s.bounds)).toList();
            Entry e;
            if(matches.isEmpty()) {
                if(entries.size()>=50_000)continue;
                e=new Entry();e.id=UUID.randomUUID().toString();e.day=day;e.firstSeen=now;e.bounds(s.bounds);e.condition=s.condition;
                entries.add(e);changed=true;
            } else {
                e=matches.stream().min(Comparator.comparingLong(v->v.firstSeen)).orElseThrow();
                // Joined partial shells are one discovery, even if first seen as two fragments.
                if(matches.size()>1){entries.removeIf(v->v!=e && matches.contains(v));changed=true;}
                Box union=e.box().union(s.bounds);
                if(!union.equals(e.box())){e.bounds(union);changed=true;}
            }
            Condition next=e.condition;
            if(s.complete) {
                next=s.condition;
                if(e.peakStorage>=8 && s.storage*4<=e.peakStorage)next=Condition.LikelyRaided;
            } else if(s.storage>=16 && next!=Condition.LikelyIntact)next=Condition.LikelyIntact;
            if(e.peakStorage<s.storage || e.storage!=s.storage || next!=e.condition)changed=true;
            e.peakStorage=Math.max(e.peakStorage,s.storage);e.storage=s.storage;e.condition=next;e.lastSeen=now;
        }
        if(changed)save();
    }
    public Condition condition(Box box, Condition fallback) {
        return entries.stream().filter(e->sameSite(e.box(),box)).map(e->e.condition).findFirst().orElse(fallback);
    }
    public Totals totals() {
        String day=LocalDate.now(clock).toString();
        if(totalsRevision==revision&&day.equals(totalsDay))return cachedTotals;
        int auto=(int)entries.stream().filter(e->day.equals(e.day)).count();
        int correction=corrections.getOrDefault(day,0);
        int allCorrection=corrections.values().stream().mapToInt(Integer::intValue).sum();
        cachedTotals=new Totals(Math.max(0,auto+correction),Math.max(0,entries.size()+allCorrection),auto,correction,
                (int)entries.stream().filter(e->e.condition==Condition.LikelyRaided).count());
        totalsRevision=revision;totalsDay=day;return cachedTotals;
    }
    /** A visible correction; automatic identities remain intact, so revisits cannot undo a minus. */
    public void adjust(int delta) {
        Totals t=totals();int applied=delta<0?-Math.min(t.today,-delta):Math.min(delta,1_000_000-t.today);
        if(applied==0)return;
        corrections.merge(LocalDate.now(clock).toString(),applied,Integer::sum);save();
    }
    private void load() {
        if(!Files.exists(file))return;
        try {
            if(Files.size(file)>32*1024*1024)throw new IllegalArgumentException("History too large");
            JsonObject root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("History version");
            List<Entry> loaded=new ArrayList<>();
            for(JsonElement value:root.getAsJsonArray("discoveries")) {
                Entry e=JSON.fromJson(value,Entry.class);
                if(e.id==null || e.condition==null || e.firstSeen<0 || e.storage<0 || e.peakStorage<e.storage)throw new IllegalArgumentException("Invalid discovery");
                LocalDate.parse(e.day);
                for(double n:new double[]{e.x0,e.y0,e.z0,e.x1,e.y1,e.z1})if(!Double.isFinite(n)||Math.abs(n)>60_000_000)throw new IllegalArgumentException("Invalid bounds");
                if(e.x1<=e.x0||e.y1<=e.y0||e.z1<=e.z0||loaded.size()>=50_000)throw new IllegalArgumentException("Invalid history");
                loaded.add(e);
            }
            Map<String,Integer> adjustments=new TreeMap<>();
            for(var row:root.getAsJsonObject("corrections").entrySet()) {
                LocalDate.parse(row.getKey());int n=row.getValue().getAsInt();if(Math.abs((long)n)>1_000_000)throw new IllegalArgumentException("Invalid adjustment");adjustments.put(row.getKey(),n);
            }
            entries.addAll(loaded);corrections.putAll(adjustments);
        } catch(Exception error) {
            // Preserve unreadable history, never silently overwrite it with a zero count.
            readable=false;
            dev.maro.Maro.LOGGER.warn("Base discovery history could not be read; original file preserved");
        }
    }
    private void save() {
        revision++;
        if(!readable)return;
        JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("revision",revision);
        root.add("discoveries",JSON.toJsonTree(entries));root.add("corrections",JSON.toJsonTree(corrections));
        String data=JSON.toJson(root);
        WRITER.execute(()->{
            try {
                Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),".bases-",".tmp");
                try {
                    Files.writeString(tmp,data,StandardCharsets.UTF_8);
                    try {Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
                } finally { Files.deleteIfExists(tmp); }
                saveFailed=false;
            } catch(Exception error){saveFailed=true;dev.maro.Maro.LOGGER.warn("Base discovery history could not be saved");}
        });
    }
    public static void awaitWrites() throws Exception { WRITER.submit(()->{}).get(10,TimeUnit.SECONDS); }
}
