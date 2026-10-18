package dev.maro.chunkpulse;

import java.util.*;
import java.util.function.Predicate;

/** Immutable display samples, including a bounded ten-second fade and persisted history. */
public final class TileTracker {
    public record Tile(int x,int z,long cluster,ClusterEngine.Tier tier,double score,double machineY,int minY,int maxY,long seen,long flagged) {
        public long key(){return SignalEvent.key(x,z);}
        public double plane(PulseConfig c,double playerY){return switch(c.planeMode){case PLAYER->Math.floor(playerY)+.02;case FIXED->c.planeY;case CLUSTER->machineY+.02;};}
        public double fade(long now,PulseConfig c){return Math.clamp(1-Math.max(0,now-flagged)/(c.fadeSeconds*1000),0,1);}
    }
    private final LinkedHashMap<Long,Tile> tiles=new LinkedHashMap<>();
    private ClusterDiscovery discovery;
    public void discovery(ClusterDiscovery value){discovery=value;}
    public ClusterDiscovery discovery(){return discovery;}
    public boolean found(Tile tile){return discovery!=null&&discovery.revealed(tile.key());}
    public void update(List<ClusterEngine.Cluster> clusters,long now,PulseConfig config,Predicate<ClusterEngine.Cluster> visible){
        for(var c:clusters)if(visible.test(c))for(var chunk:c.chunks()){
            var tile=new Tile(chunk.x,chunk.z,c.id(),c.tier(),chunk.score(now,config),chunk.machineY(),chunk.minY(),chunk.maxY(),chunk.last(),now);
            tiles.remove(tile.key());tiles.put(tile.key(),tile);
        }
        tiles.values().removeIf(t->now-t.seen()>config.retentionHours*3600000L);
        while(tiles.size()>config.maxChunks)tiles.remove(tiles.keySet().iterator().next());
    }
    public List<Tile> visible(long now,PulseConfig config){return tiles.values().stream().filter(t->found(t)?config.showHistory:t.fade(now,config)>0||config.showHistory).toList();}
    public List<Tile> snapshots(){return List.copyOf(tiles.values());}
    public void restore(List<Tile> saved,long now,PulseConfig config){if(saved==null)return;for(var t:saved){if(t==null||t.tier()==null||t.minY()<config.minY||t.maxY()>=config.maxY||t.minY()>t.maxY()||!Double.isFinite(t.machineY())||!Double.isFinite(t.score())||Math.abs((long)t.x())>1875000||Math.abs((long)t.z())>1875000||t.flagged()>now||t.seen()>now||now-t.seen()>config.retentionHours*3600000L)continue;tiles.putIfAbsent(t.key(),t);if(tiles.size()>=config.maxChunks)break;}}
    public void clear(){tiles.clear();}
    /** Edge bits N/E/S/W. Neighbours on a different plane keep their own flat boundary. */
    public static int edges(Tile t,Map<Long,Tile> drawn,PulseConfig config,double playerY){int edges=0;int[][] offsets={{0,-1},{1,0},{0,1},{-1,0}};for(int i=0;i<4;i++){var other=drawn.get(SignalEvent.key(t.x()+offsets[i][0],t.z()+offsets[i][1]));if(other==null||Math.abs(t.plane(config,playerY)-other.plane(config,playerY))>.01)edges|=1<<i;}return edges;}
}
