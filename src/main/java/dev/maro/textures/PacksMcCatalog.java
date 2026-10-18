package dev.maro.textures;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** The first 50 public Trending cards. Files are downloaded through their creator's site page. */
public final class PacksMcCatalog {
    public static final int LIMIT = 50;
    public static final String WEBSITE = "https://packsmc.com/showcase?sortBy=trending";
    public static final String CATALOG = "https://packsmc.com/api/packs?search=&category=All&resolution=All&tag=&sortBy=trending&limit=50&offset=0";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public record Pack(String id, String name, String author, String description, String resolution,
                       List<String> versions, String preview, String page, long downloads, long bytes,
                       String license, String credits, boolean exclusive) {
        public String key() { return "packsmc:" + id; }
    }
    private final Path cache;
    private volatile List<Pack> packs = List.of();
    private volatile boolean loading;
    private volatile String message = "First 50 Trending packs";
    private CompletableFuture<List<Pack>> request;
    private long fetchedAt;
    public PacksMcCatalog(Path cache) { this.cache = cache; }
    public List<Pack> packs() { return packs; }
    public boolean loading() { return loading; }
    public String message() { return message; }
    public Pack find(String id) { return packs.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }

    public synchronized CompletableFuture<List<Pack>> refresh(boolean force) {
        if (loading) return request;
        if (!force && !packs.isEmpty() && System.currentTimeMillis()-fetchedAt < 300_000)
            return CompletableFuture.completedFuture(packs);
        loading = true;
        request = CompletableFuture.supplyAsync(() -> {
            if (packs.isEmpty()) {
                try { if (Files.isRegularFile(cache)) packs = parse(Files.readString(cache)); } catch (Exception ignored) { }
                if (packs.isEmpty()) try (var in = PacksMcCatalog.class.getResourceAsStream("/assets/maro/catalog/packsmc.json")) {
                    if (in != null) packs = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            }
            try {
                String json = new String(fetch(CATALOG, 3*1024*1024), StandardCharsets.UTF_8);
                List<Pack> next = parse(json);
                if (next.size() != LIMIT) throw new IOException("Incomplete catalog");
                Files.createDirectories(cache.getParent());
                Path temp = Files.createTempFile(cache.getParent(), "catalog-", ".tmp");
                try { Files.writeString(temp, json); Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temp); }
                packs = next; fetchedAt = System.currentTimeMillis();
                prunePreviews(next);
                message = "First 50 · PacksMC Trending";
            } catch (Exception e) { message = packs.isEmpty() ? "Could not load packs. Try Refresh." : "Saved first 50 · refresh unavailable"; }
            finally { loading = false; }
            return packs;
        }, Modrinth.POOL);
        return request;
    }
    public static List<Pack> parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        var list = root.getAsJsonArray("packs");
        if (list == null) throw new IllegalArgumentException("Missing packs");
        List<Pack> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        // Apply the cap before validation: malformed cards must never pull a 51st pack into this import.
        for (int i=0; i<Math.min(LIMIT, list.size()); i++) try {
            var e = list.get(i).getAsJsonObject();
            String id = str(e,"id"), slug = str(e,"slug"), preview = str(e,"thumbnail_url");
            if (!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}") || !seen.add(id)
                    || !slug.matches("[a-z0-9-]{1,180}")) continue;
            if (!imageUri(preview)) preview = "";
            String name = clean(str(e,"pack_name"),140), author = clean(str(e,"author_display_name"),80);
            if (author.isEmpty()) author = clean(str(e,"author_username"),80);
            if (name.isEmpty() || author.isEmpty()) continue;
            var license = e.get("license");
            String terms = license != null && license.isJsonObject() ? str(license.getAsJsonObject(),"label")
                    : license != null && license.isJsonPrimitive() ? license.getAsString() : "Creator's terms on PacksMC";
            String credits = e.has("creators") && !e.get("creators").isJsonNull() ? e.get("creators").toString() : "";
            out.add(new Pack(id,name,author,clean(str(e,"description"),2000),clean(str(e,"resolution"),20),
                    strings(e,"mc_versions"),preview,"https://packsmc.com/pack/"+slug,
                    number(e,"download_count"),number(e,"file_size"),clean(terms,300),clean(credits,1000),
                    e.has("is_packs_plus_exclusive") && !e.get("is_packs_plus_exclusive").isJsonNull()
                            && e.get("is_packs_plus_exclusive").getAsBoolean() || number(e,"price_cents")>0));
        } catch (RuntimeException ignored) { }
        if (out.isEmpty()) throw new IllegalArgumentException("No usable packs");
        return List.copyOf(out);
    }
    private static String str(JsonObject e,String key) { return e.has(key) && !e.get(key).isJsonNull() && e.get(key).isJsonPrimitive() ? e.get(key).getAsString() : ""; }
    private static long number(JsonObject e,String key) { try { return Math.max(0,Long.parseLong(str(e,key))); } catch (Exception ignored) { return 0; } }
    private static String clean(String s,int limit) { s=s.replaceAll("[\\p{Cntrl}§]"," ").strip(); return s.substring(0,Math.min(limit,s.length())); }
    private static List<String> strings(JsonObject e,String key) {
        List<String> out=new ArrayList<>();
        if(e.has(key)&&e.get(key).isJsonArray()) for(var s:e.getAsJsonArray(key)) if(s.isJsonPrimitive()&&out.size()<30)out.add(clean(s.getAsString(),30));
        return List.copyOf(out);
    }
    public static boolean imageUri(String url) {
        try { URI u=URI.create(url);return u.getScheme().equals("https") && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443)
                && Set.of("dedicated.packsmc.com","packsmc.com").contains(u.getHost()) && !u.getPath().contains(".."); }
        catch(Exception ignored){return false;}
    }
    public static boolean packPage(String url) {
        try { URI u=URI.create(url);return "https".equals(u.getScheme())&&"packsmc.com".equals(u.getHost())&&u.getUserInfo()==null
                && u.getPort()==-1 && u.getPath().matches("/pack/[a-z0-9-]{1,180}")&&u.getQuery()==null&&u.getFragment()==null; }
        catch(Exception ignored){return false;}
    }
    public static CompletableFuture<byte[]> previewBytes(String url) {
        return previewBytes(url,false);
    }
    public static CompletableFuture<byte[]> previewBytes(String url,boolean fresh) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if(!imageUri(url))throw new IOException("Invalid preview source");Path file=previewFile(url);
                if(!fresh&&Files.isRegularFile(file)&&Files.size(file)>0&&Files.size(file)<=4*1024*1024)return Files.readAllBytes(file);
                byte[] bytes=fresh?null:BundledPacks.preview(url);
                if(bytes==null)bytes=fetch(url,4*1024*1024);
                try {
                    Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"preview-",".tmp");
                    try{Files.write(temp,bytes);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
                }catch(IOException ignored){} // A read-only instance can still show a live preview.
                return bytes;
            }
            catch(Exception e){throw new CompletionException(e);}
        }, Modrinth.POOL);
    }
    public static Path previewFile(String url) {
        if(!imageUri(url))throw new IllegalArgumentException("Invalid preview source");
        try{return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("maro/packsmc/previews")
                .resolve(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)))+".img");}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static void forgetPreview(String url){try{Files.deleteIfExists(previewFile(url));}catch(Exception ignored){}}
    private static void prunePreviews(List<Pack> packs){
        var keep=new HashSet<String>();Path directory=null;
        for(var p:packs)if(imageUri(p.preview())){Path f=previewFile(p.preview());keep.add(f.getFileName().toString());directory=f.getParent();}
        if(directory==null||!Files.isDirectory(directory))return;
        try(var files=Files.list(directory)){for(var file:files.toList())if(file.getFileName().toString().matches("[a-f0-9]{64}\\.img")&&!keep.contains(file.getFileName().toString()))Files.deleteIfExists(file);}
        catch(IOException ignored){}
    }
    private static byte[] fetch(String url,int limit) throws IOException,InterruptedException {
        if(!url.equals(CATALOG)&&!imageUri(url))throw new IOException("Unexpected source");
        var req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("User-Agent","Maro/1.0 resource-pack browser").GET().build();
        var response=HTTP.send(req,HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()) {
            if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode());
            byte[] data=in.readNBytes(limit+1); if(data.length>limit)throw new IOException("Response too large");return data;
        }
    }
}
