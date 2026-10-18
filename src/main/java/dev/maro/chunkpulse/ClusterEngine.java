package dev.maro.chunkpulse;

import java.util.*;

/** Rebuilt at most twice a second, independently of packet handling. */
public final class ClusterEngine {
    public enum Tier { LOW,MEDIUM,HIGH,HUGE }
    public record Cluster(long id,List<ChunkStats> chunks,double score,Tier tier,double x,double y,double z,
                          int minX,int maxX,int minZ,int maxZ,int minY,int maxY,int positions,long last,String explanation) { }
    public static List<Cluster> clusters(Collection<ChunkStats> records,long now,PulseConfig config) {
        var pending=new HashMap<Long,ChunkStats>();var scores=new HashMap<Long,Double>();
        for(var stats:records){double score=stats.score(now,config);if(score>=config.low){long key=SignalEvent.key(stats.x,stats.z);pending.put(key,stats);scores.put(key,score);}}
        var result=new ArrayList<Cluster>();
        while(!pending.isEmpty()){
            long start=pending.keySet().iterator().next();var queue=new ArrayDeque<Long>();queue.add(start);
            var chunks=new ArrayList<ChunkStats>();var seed=pending.remove(start);chunks.add(seed);
            while(!queue.isEmpty()){long key=queue.removeFirst();int x=SignalEvent.chunkX(key),z=SignalEvent.chunkZ(key);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){if(dx==0&&dz==0)continue;long neighbour=SignalEvent.key(x+dx,z+dz);var next=pending.remove(neighbour);if(next!=null){chunks.add(next);queue.addLast(neighbour);}}
            }
            double score=0,x=0,y=0,z=0,strong=0;int positions=0,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX,minY=minX,maxY=maxX;
            boolean clock=false,self=false;long last=0,id=Long.MAX_VALUE;ChunkStats loudest=seed;double loudestScore=0;
            for(var stats:chunks){
                long key=SignalEvent.key(stats.x,stats.z);double weight=scores.get(key);score+=weight;x+=stats.centerX()*weight;y+=stats.centerY()*weight;z+=stats.centerZ()*weight;
                strong+=stats.strong();positions+=stats.qualifiedPositions(now,config);clock|=stats.confirmedClock(now,config);self|=stats.selfCaused(config);last=Math.max(last,stats.last());id=Math.min(id,key);
                minX=Math.min(minX,stats.x*16);maxX=Math.max(maxX,stats.x*16+16);minZ=Math.min(minZ,stats.z*16);maxZ=Math.max(maxZ,stats.z*16+16);minY=Math.min(minY,stats.minY());maxY=Math.max(maxY,stats.maxY());
                if(weight>loudestScore){loudest=stats;loudestScore=weight;}
            }
            Tier tier=score>=config.medium&&positions>=1?Tier.MEDIUM:Tier.LOW;
            boolean confirmed=positions>=config.minDistinctMachines&&clock;
            if(confirmed&&score>=config.high)tier=Tier.HIGH;
            if(confirmed&&score>=config.huge&&chunks.size()>=config.hugeChunks&&positions>=config.hugePositions)tier=Tier.HUGE;
            if(self)tier=Tier.LOW;
            result.add(new Cluster(id,List.copyOf(chunks),score,tier,x/score,y/score,z/score,minX,maxX,minZ,maxZ,minY,maxY,positions,last,(self?"SELF / ":"")+loudest.explanation()));
        }
        result.sort(Comparator.comparingDouble(Cluster::score).reversed());return List.copyOf(result);
    }
    private ClusterEngine(){ }
}
