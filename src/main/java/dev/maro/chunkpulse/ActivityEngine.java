package dev.maro.chunkpulse;

import java.util.*;

/** All writes stay on the client thread. LRU limits include quiet and previously seen chunks. */
public final class ActivityEngine {
    private final LinkedHashMap<Long,ChunkStats> chunks=new LinkedHashMap<>(64,.75f,true);
    public record DebugRecord(SignalEvent event,String reason,double weight,boolean self) { }
    public record ReplayFrame(long time,SelfActivityFilter.Context context,SelfActivityFilter.Mask mask,SignalEvent event) { }
    public record Recording(int version,PulseConfig config,List<ReplayFrame> frames) { }
    private static final class Causes {double attempted,self;long updated;void add(long time,boolean caused){decay(time);attempted++;if(caused)self++;}void decay(long time){double f=Math.exp(-Math.max(0,time-updated)/60000d);attempted*=f;self*=f;updated=time;}}
    private final LinkedHashMap<Long,Causes> causes=new LinkedHashMap<>(64,.75f,true);
    private final ArrayDeque<DebugRecord> debug=new ArrayDeque<>();
    private final ArrayDeque<ReplayFrame> frames=new ArrayDeque<>();
    private SelfActivityFilter filter;
    private final PulseConfig config;
    public long accepted,dropped;
    private long bucket=-1;
    private int budget;
    public ActivityEngine(PulseConfig config){this.config=config;filter=new SelfActivityFilter(config);}
    public void context(SelfActivityFilter.Context context,long time,boolean record){filter.context(context,time);if(record)frame(new ReplayFrame(time,context,null,null));}
    public void mask(SelfActivityFilter.Mask mask,boolean record){filter.mask(mask);if(record)frame(new ReplayFrame(mask.time(),null,mask,null));}
    private void frame(ReplayFrame frame){frames.addLast(frame);while(frames.size()>config.maxDebugEvents)frames.removeFirst();}
    public void accept(SignalEvent event,boolean record){
        if(event==null||!event.valid()){dropped++;return;}
        long tick=event.time()/50;if(tick!=bucket){bucket=tick;budget=0;}
        if(budget++>=config.maxSignalsPerTick){dropped++;return;}
        var decision=filter.decide(event);
        if(filter.depth(event)){
            causes.computeIfAbsent(event.chunk(),key->new Causes()).add(event.time(),decision.self());
            while(causes.size()>config.maxChunks)causes.remove(causes.keySet().iterator().next());
        }
        if(decision.kept()){
            chunks.computeIfAbsent(event.chunk(),key->new ChunkStats(SignalEvent.chunkX(key),SignalEvent.chunkZ(key))).add(event,config,decision.factor());accepted++;
            while(chunks.size()>config.maxChunks)chunks.remove(chunks.keySet().iterator().next());
        }else dropped++;
        if(record){debug.addLast(new DebugRecord(event,decision.reason(),decision.factor()*config.weights.getOrDefault(event.kind(),event.kind().weight),decision.self()));while(debug.size()>config.maxDebugEvents)debug.removeFirst();frame(new ReplayFrame(event.time(),null,null,event));}
    }
    public List<ClusterEngine.Cluster> clusters(long now){chunks.values().removeIf(c->now-c.last()>config.retentionHours*3600000L);for(var entry:chunks.entrySet()){var cause=causes.get(entry.getKey());if(cause!=null){cause.decay(now);entry.getValue().causes(cause.attempted,cause.self,now);}}return ClusterEngine.clusters(chunks.values(),now,config);}
    public List<ChunkStats.Snapshot> snapshots(){return chunks.values().stream().map(c->c.snapshot(config)).toList();}
    public List<SignalEvent> debugEvents(){return debug.stream().filter(r->r.weight()>0).map(DebugRecord::event).toList();}
    public List<DebugRecord> decisions(){return List.copyOf(debug);}
    public Recording recording(){return new Recording(2,PulseConfig.JSON.fromJson(PulseConfig.JSON.toJson(config),PulseConfig.class),List.copyOf(frames));}
    public void restore(List<ChunkStats.Snapshot> saved,long now){if(saved==null)return;for(var item:saved){var stats=ChunkStats.restore(item,now,config);if(stats!=null){long key=SignalEvent.key(stats.x,stats.z);chunks.putIfAbsent(key,stats);var cause=new Causes();cause.attempted=stats.attempted();cause.self=stats.self();cause.updated=now;causes.putIfAbsent(key,cause);}while(chunks.size()>config.maxChunks)chunks.remove(chunks.keySet().iterator().next());while(causes.size()>config.maxChunks)causes.remove(causes.keySet().iterator().next());}}
    public int size(){return chunks.size();}
    public void clear(){chunks.clear();causes.clear();debug.clear();frames.clear();filter=new SelfActivityFilter(config);accepted=0;dropped=0;bucket=-1;budget=0;}
}
