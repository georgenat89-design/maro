package dev.maro.chunkpulse;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Immutable snapshots only on this worker; it never touches the live world or engine. */
public final class Persistence {
    public record Saved(int version,String server,String dimension,List<ChunkStats.Snapshot> chunks,List<TileTracker.Tile> tiles,List<ClusterDiscovery.Snapshot> discoveries) { }
    public record State(List<ChunkStats.Snapshot> chunks,List<TileTracker.Tile> tiles,List<ClusterDiscovery.Snapshot> discoveries) {public static State empty(){return new State(List.of(),List.of(),List.of());}}
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Maro ChunkPulse files");t.setDaemon(true);return t;});
    private static final Set<Path> FAILED=ConcurrentHashMap.newKeySet();
    private static final com.google.gson.Gson DATA_JSON=new com.google.gson.Gson();
    private static final ConcurrentMap<Path,java.util.concurrent.atomic.AtomicLong> REVISIONS=new ConcurrentHashMap<>();
    public static long reserve(String server,String dimension){return REVISIONS.computeIfAbsent(file(server,dimension),key->new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();}
    public static Path folder(){return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("maro/chunkpulse");}
    public static Path file(String server,String dimension){
        try{String key=server+"\n"+dimension;byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));return folder().resolve("locations-"+HexFormat.of().formatHex(digest,0,12)+".json");}
        catch(Exception error){throw new IllegalStateException(error);}
    }
    public static CompletableFuture<List<ChunkStats.Snapshot>> load(String server,String dimension,int limit){
        return loadState(server,dimension,limit).thenApply(State::chunks);
    }
    public static CompletableFuture<State> loadState(String server,String dimension,int limit){
        return CompletableFuture.supplyAsync(()->{
            var path=file(server,dimension);
            try{
                if(!Files.isRegularFile(path))return State.empty();
                if(Files.size(path)>16*1024*1024)throw new java.io.IOException("Location file exceeds 16 MiB");
                var saved=PulseConfig.JSON.fromJson(Files.readString(path),Saved.class);
                if(saved==null||saved.version()<1||saved.version()>3||!server.equals(saved.server())||!dimension.equals(saved.dimension())||saved.chunks()==null)throw new java.io.IOException("Invalid location snapshot");
                return new State(List.copyOf(saved.chunks().subList(0,Math.min(limit,saved.chunks().size()))),saved.tiles()==null?List.of():List.copyOf(saved.tiles().subList(0,Math.min(limit,saved.tiles().size()))),saved.discoveries()==null?List.of():List.copyOf(saved.discoveries().subList(0,Math.min(4096,saved.discoveries().size()))));
            }catch(Exception error){FAILED.add(path);dev.maro.Maro.LOGGER.warn("ChunkPulse kept unreadable locations at {}",path,error);return State.empty();}
        },IO);
    }
    public static CompletableFuture<Void> save(String server,String dimension,List<ChunkStats.Snapshot> chunks){
        return save(server,dimension,chunks,reserve(server,dimension));
    }
    public static CompletableFuture<Void> save(String server,String dimension,List<ChunkStats.Snapshot> chunks,long revision){
        return save(server,dimension,chunks,List.of(),revision);
    }
    public static CompletableFuture<Void> save(String server,String dimension,List<ChunkStats.Snapshot> chunks,List<TileTracker.Tile> tiles,long revision){
        return save(server,dimension,chunks,tiles,List.of(),revision);
    }
    public static CompletableFuture<Void> save(String server,String dimension,List<ChunkStats.Snapshot> chunks,List<TileTracker.Tile> tiles,List<ClusterDiscovery.Snapshot> discoveries,long revision){
        return CompletableFuture.runAsync(()->{
            Path path=file(server,dimension);
            if(REVISIONS.get(path).get()!=revision)return;
            try{
                Files.createDirectories(path.getParent());
                if(FAILED.remove(path)&&Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".unreadable-"+System.currentTimeMillis()+".bak"));
                Path temporary=path.resolveSibling(path.getFileName()+".new");
                // Keep location files comfortably below the loader's size cap, even with maximum live histories.
                var compact=chunks.stream().sorted(Comparator.comparingLong(ChunkStats.Snapshot::last).reversed()).limit(2048).map(ChunkStats.Snapshot::compact).toList();
                var savedTiles=tiles.stream().sorted(Comparator.comparingLong(TileTracker.Tile::seen).reversed()).limit(2048).toList();
                var savedDiscoveries=List.copyOf(discoveries);
                String data=DATA_JSON.toJson(new Saved(3,server,dimension,compact,savedTiles,savedDiscoveries));
                // Permanent FOUND markers take priority over older raw counters at the file budget.
                while(data.length()>15*1024*1024&&!compact.isEmpty()){compact=compact.subList(0,compact.size()*3/4);data=DATA_JSON.toJson(new Saved(3,server,dimension,compact,savedTiles,savedDiscoveries));}
                if(data.length()>15*1024*1024)throw new java.io.IOException("Location snapshot exceeds size budget");
                Files.writeString(temporary,data);
                try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            }catch(Exception error){dev.maro.Maro.LOGGER.warn("ChunkPulse locations could not be saved",error);throw new CompletionException(error);}
        },IO);
    }
    public static CompletableFuture<Path> export(String server,String dimension,List<ClusterEngine.Cluster> clusters,List<SignalEvent> debug,boolean waypoints){
        // Snapshot display fields now; the worker must not dereference mutable ChunkStats.
        StringBuilder csv=new StringBuilder("server,dimension,tier,score,x,y,z,chunks,positions,min_y,max_y,last_active_ms,explanation\n");
        StringBuilder points=new StringBuilder("# ChunkPulse / "+server+" / "+dimension+"\n");
        int index=1;
        for(var c:clusters){
            csv.append(quote(server)).append(',').append(quote(dimension)).append(',').append(c.tier()).append(',').append(String.format(Locale.ROOT,"%.2f",c.score())).append(',')
                    .append(Math.round(c.x())).append(',').append(Math.round(c.y())).append(',').append(Math.round(c.z())).append(',').append(c.chunks().size()).append(',').append(c.positions()).append(',')
                    .append(c.minY()).append(',').append(c.maxY()).append(',').append(c.last()).append(',').append(quote(c.explanation())).append('\n');
            points.append("waypoint:Pulse ").append(index++).append(' ').append(c.tier()).append(":P:").append(Math.round(c.x())).append(':').append(Math.round(c.y())).append(':').append(Math.round(c.z())).append(":3:false:0:gui.xaero_default:false:0:false\n");
        }
        String csvText=csv.toString(),waypointText=points.toString();
        return CompletableFuture.supplyAsync(()->{
            try{
                Path out=folder().resolve("exports");Files.createDirectories(out);
                String name="chunkpulse-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,6);
                Path result=out.resolve(name+(waypoints?".points":".csv"));Files.writeString(result,waypoints?waypointText:csvText);
                if(!debug.isEmpty())Files.writeString(out.resolve(name+"-replay.json"),PulseConfig.JSON.toJson(debug));
                return result;
            }catch(Exception error){throw new CompletionException(error);}
        },IO);
    }
    private static String quote(String text){return "\""+text.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
    public static CompletableFuture<Void> exportDebug(Path csv,ActivityEngine.Recording recording,List<ActivityEngine.DebugRecord> decisions){
        String replay=PulseConfig.JSON.toJson(recording),trace=PulseConfig.JSON.toJson(decisions);
        return CompletableFuture.runAsync(()->{try{String base=csv.getFileName().toString().replaceFirst("\\.[^.]+$","");Files.writeString(csv.resolveSibling(base+"-replay.json"),replay);Files.writeString(csv.resolveSibling(base+"-decisions.json"),trace);}catch(Exception e){throw new CompletionException(e);}},IO);
    }
    public static CompletableFuture<Void> exportDiscovery(Path csv,List<ClusterDiscovery.Snapshot> clusters,List<ClusterDiscovery.Transition> transitions){
        String data=PulseConfig.JSON.toJson(Map.of("version",1,"clusters",clusters,"transitions",transitions));
        return CompletableFuture.runAsync(()->{try{String base=csv.getFileName().toString().replaceFirst("\\.[^.]+$","");Files.writeString(csv.resolveSibling(base+"-clusters.json"),data);}catch(Exception e){throw new CompletionException(e);}},IO);
    }
    public static void shutdown(){
        // A pending load can enqueue its merged save before this barrier completes.
        try{IO.submit(()->{}).get(3,TimeUnit.SECONDS);}catch(Exception error){dev.maro.Maro.LOGGER.warn("ChunkPulse location worker is still busy",error);}
        IO.shutdown();try{if(!IO.awaitTermination(3,TimeUnit.SECONDS))dev.maro.Maro.LOGGER.warn("ChunkPulse is still finishing its location save");}catch(InterruptedException error){Thread.currentThread().interrupt();}
    }
    private Persistence(){ }
}
