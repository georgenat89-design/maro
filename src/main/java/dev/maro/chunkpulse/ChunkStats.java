package dev.maro.chunkpulse;

import java.util.*;

/** Bounded emitter histories and three exponential windows (10 s, 60 s, 10 min). */
public final class ChunkStats {
    public record Point(int x,int y,int z,long seen,boolean machine,int toggles,double machineEvents,long machineSeen) {
        public Point(int x,int y,int z,long seen,boolean machine,int toggles){this(x,y,z,seen,machine,toggles,0,seen);}
        double evidence(long now,PulseConfig c){return machineEvents*Math.exp(-Math.max(0,now-machineSeen)/(c.machineWindowSeconds*1000));}
    }
    private record Lane(long position,SignalEvent.Kind kind,String source) { }
    private static final double[] WINDOWS={10_000,60_000,600_000};
    public record Snapshot(int x,int z,long first,long last,long updated,long streak,
                           double[][] counts,List<Point> points,double cx,double cy,double cz,double mass,
                           int minY,int maxY,double cadence,int period,long lastMachine,double machineSpan,
                           double attempted,double self,long causesUpdated,int evidenceVersion) {
        public Snapshot compact(){return new Snapshot(x,z,first,last,updated,streak,counts,points.stream().sorted(Comparator.comparing(Point::machine).reversed().thenComparing(Comparator.comparingLong(Point::seen).reversed())).limit(32).toList(),cx,cy,cz,mass,minY,maxY,cadence,period,lastMachine,machineSpan,attempted,self,causesUpdated,evidenceVersion);}
    }
    public final int x,z;
    private final double[][] counts=new double[3][SignalEvent.Kind.values().length];
    private final LinkedHashMap<Long,Point> points=new LinkedHashMap<>();
    private final LinkedHashMap<Lane,PeriodicityAnalyzer> lanes=new LinkedHashMap<>(16,.75f,true);
    private final long[] recent=new long[64];
    private int recentAt;
    private long first=-1,last,updated,streak,lastMachine,rememberedMachineTime;
    private double cx,cy,cz,mass,rememberedCadence,rememberedMachineSpan;
    private int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,rememberedPeriod;
    private double attempted,self;
    private long causesUpdated;
    public void causes(double attempted,double self,long now){this.attempted=attempted;this.self=self;causesUpdated=now;}
    public double attempted(){return attempted;}
    public double self(){return self;}
    public boolean selfCaused(PulseConfig config){return attempted>=6&&self/attempted>config.selfCapRatio;}
    public ChunkStats(int x,int z){this.x=x;this.z=z;}
    private void decay(long now) {
        if(updated==0){updated=now;return;}
        long elapsed=Math.max(0,now-updated);
        if(elapsed==0)return;
        for(int w=0;w<3;w++){double factor=Math.exp(-elapsed/WINDOWS[w]);for(int k=0;k<counts[w].length;k++)counts[w][k]*=factor;}
        updated=now;
    }
    public void add(SignalEvent event,PulseConfig config) {
        add(event,config,1);
    }
    public void add(SignalEvent event,PulseConfig config,double factor) {
        long time=Math.max(last,event.time());decay(time);
        if(first<0)first=time;
        if(last==0||time-last>10_000){streak=time;lanes.clear();rememberedCadence=0;}
        last=time;recent[recentAt++&63]=time;
        if(event.kind().machine){if(lastMachine>0&&time-lastMachine>10000){rememberedCadence=0;rememberedMachineSpan=0;}lastMachine=time;}
        for(double[] window:counts)window[event.kind().ordinal()]+=factor;
        int bottom=event.kind()==SignalEvent.Kind.LIGHT?Math.floorDiv(event.y(),16)*16:event.y();
        int top=event.kind()==SignalEvent.Kind.LIGHT?bottom+15:event.y();
        minY=Math.min(minY,bottom);maxY=Math.max(maxY,top);
        if(event.exact()){
            long key=event.position();var old=points.remove(key);
            boolean machine=event.kind().machine||old!=null&&old.machine();
            int toggles=(old==null?0:old.toggles())+(event.kind()==SignalEvent.Kind.STATE?1:0);
            double evidence=old==null?0:old.evidence(time,config);
            long machineSeen=old==null?0:old.machineSeen();
            if(event.kind().machine){evidence+=factor;machineSeen=time;}else if(old!=null)machineSeen=time;
            points.put(key,new Point(event.x(),event.y(),event.z(),time,machine,toggles,evidence,machineSeen));
            while(points.size()>config.maxPositions)points.remove(points.keySet().iterator().next());
        }
        // A running weighted mean avoids growing sums over long sessions.
        double weight=event.exact()?1:.12,newMass=Math.min(4096,mass+weight),fraction=weight/newMass;
        cx+=(event.x()+.5-cx)*fraction;cy+=(event.y()+.5-cy)*fraction;cz+=(event.z()+.5-cz)*fraction;mass=newMass;
        if(event.kind().machine||event.kind()==SignalEvent.Kind.LIGHT||event.kind()==SignalEvent.Kind.SOUND){
            var lane=new Lane(event.position(),event.kind(),event.source());
            lanes.computeIfAbsent(lane,key->new PeriodicityAnalyzer()).add(time);
            while(lanes.size()>config.maxLanes)lanes.remove(lanes.keySet().iterator().next());
        }
    }
    public PeriodicityAnalyzer.Cadence cadence() {
        return cadence(false);
    }
    public PeriodicityAnalyzer.Cadence machineCadence(){return cadence(true);}
    private record Clock(double quality,int period,double span,long last){
        boolean qualified(PulseConfig config){return quality>=config.cadenceQuality&&span>=config.sustainSeconds;}
    }
    private Clock savedClock(PulseConfig config){
        var best=new Clock(rememberedCadence,rememberedPeriod,rememberedMachineSpan,rememberedMachineTime);
        for(var entry:lanes.entrySet())if(entry.getKey().kind().machine){
            var lane=entry.getValue();var c=lane.cadence();var next=new Clock(c.quality(),c.ticks(),lane.stableSpan(),lane.last());
            if(next.qualified(config)&&!best.qualified(config)||next.qualified(config)==best.qualified(config)&&(next.last()>best.last()||next.last()==best.last()&&next.quality()>best.quality()))best=next;
        }
        return best;
    }
    public boolean confirmedClock(long now,PulseConfig config){
        if(rememberedCadence>=config.cadenceQuality&&rememberedMachineSpan>=config.sustainSeconds&&now-rememberedMachineTime<=config.confirmedFreshSeconds*1000)return true;
        for(var entry:lanes.entrySet())if(entry.getKey().kind().machine){var lane=entry.getValue();if(lane.cadence().quality()>=config.cadenceQuality&&lane.stableSpan()>=config.sustainSeconds&&now-lane.last()<=config.confirmedFreshSeconds*1000)return true;}
        return false;
    }
    private PeriodicityAnalyzer.Cadence cadence(boolean machineOnly){
        var best=new PeriodicityAnalyzer.Cadence(rememberedCadence,rememberedPeriod,0);
        for(var entry:lanes.entrySet()){if(machineOnly&&!entry.getKey().kind().machine)continue;var current=entry.getValue().cadence();if(current.quality()>best.quality())best=current;}
        return best;
    }
    public double count(SignalEvent.Kind kind){return counts[1][kind.ordinal()];}
    public double strong(){double total=0;for(var kind:SignalEvent.Kind.values())if(kind.machine)total+=count(kind);return total;}
    public double sustained(){return Math.max(0,last-streak)/1000d;}
    public long last(){return last;}
    public int minY(){return minY==Integer.MAX_VALUE?0:minY;}
    public int maxY(){return maxY==Integer.MIN_VALUE?0:maxY;}
    public int distinct(){return points.size();}
    public List<Point> points(){return List.copyOf(points.values());}
    public int machinePositions(){return (int)points.values().stream().filter(Point::machine).count();}
    public int qualifiedPositions(long now,PulseConfig config){return (int)points.values().stream().filter(p->p.machine()&&p.evidence(now,config)>=config.minEventsForFlag).count();}
    public double machineY(){double weight=0,sum=0;for(var p:points.values())if(p.machine()){double w=Math.max(1,p.machineEvents());sum+=(p.y()+.5)*w;weight+=w;}return weight>0?sum/weight:centerY();}
    public double centerX(){return mass>0?cx:x*16+8;}
    public double centerY(){return mass>0?cy:64;}
    public double centerZ(){return mass>0?cz:z*16+8;}
    public double score(long now,PulseConfig config){
        decay(now);
        points.values().removeIf(p->now-p.seen()>600_000);
        double score=0;
        for(var kind:SignalEvent.Kind.values()){
            int k=kind.ordinal();score+=(counts[0][k]*.45+counts[1][k]*.45+counts[2][k]*.10)*config.weights.getOrDefault(kind,kind.weight);
        }
        double weakCap=qualifiedPositions(now,config)==0||selfCaused(config)?Math.nextDown(config.medium):Double.MAX_VALUE;
        double periodic=cadence().quality();
        score*=1+periodic*1.5;
        score*=1+Math.min(1,Math.log1p(points.size())/5)*.65;
        score*=1+Math.min(1,sustained()/60)*.4;
        // Long-window observations remember coordinates, but cannot stay bright after activity stops.
        return Math.min(weakCap,score)*Math.exp(-Math.max(0,now-last)/180_000d);
    }
    public String explanation(){
        var parts=new ArrayList<String>();
        for(var kind:SignalEvent.Kind.values())if(count(kind)>=1)parts.add(kind.name().toLowerCase(Locale.ROOT)+" x"+Math.round(count(kind)));
        var cadence=cadence();if(cadence.quality()>=.5)parts.add("periodic "+cadence.ticks()+"gt");
        parts.add("y"+minY()+".."+maxY());
        return String.join(", ",parts);
    }
    public Snapshot snapshot(PulseConfig config){var c=savedClock(config);double[][] copy=new double[3][];for(int i=0;i<3;i++)copy[i]=counts[i].clone();return new Snapshot(x,z,first,last,updated,streak,copy,List.copyOf(points.values()),cx,cy,cz,mass,minY(),maxY(),c.quality(),c.period(),c.last(),c.span(),attempted,self,causesUpdated,2);}
    public static ChunkStats restore(Snapshot saved,long now,PulseConfig config){
        if(saved==null||saved.counts()==null||saved.counts().length!=3||saved.last()<0||saved.last()>now+60000
                ||now-saved.last()>config.retentionHours*3600000L||Math.abs((long)saved.x())>1875000||Math.abs((long)saved.z())>1875000
                ||saved.minY()<config.minY||saved.maxY()>=config.maxY||saved.minY()>saved.maxY())return null;
        var stats=new ChunkStats(saved.x(),saved.z());stats.first=saved.first();stats.last=saved.last();stats.updated=Math.min(now,saved.updated());stats.streak=Math.min(saved.last(),saved.streak());
        for(int i=0;i<3;i++){if(saved.counts()[i]==null||saved.counts()[i].length>stats.counts[i].length)return null;for(int k=0;k<saved.counts()[i].length;k++){double n=saved.counts()[i][k];if(!Double.isFinite(n)||n<0||n>1e9)return null;stats.counts[i][k]=n;}}
        if(saved.points()!=null)for(var p:saved.points()){if(p==null||stats.points.size()>=config.maxPositions)break;var e=new SignalEvent(p.seen(),p.x(),p.y(),p.z(),SignalEvent.Kind.STATE,true,"saved");if(e.valid()&&p.y()>=config.minY&&p.y()<config.maxY&&p.x()>>4==saved.x()&&p.z()>>4==saved.z()&&Double.isFinite(p.machineEvents())&&p.machineEvents()>=0)stats.points.put(e.position(),p);}
        stats.cx=Double.isFinite(saved.cx())?saved.cx():saved.x()*16+8;stats.cy=Double.isFinite(saved.cy())?saved.cy():64;stats.cz=Double.isFinite(saved.cz())?saved.cz():saved.z()*16+8;
        stats.mass=Math.clamp(saved.mass(),0,4096);stats.minY=Math.clamp(saved.minY(),-2048,2047);stats.maxY=Math.clamp(saved.maxY(),-2048,2047);
        stats.rememberedCadence=Double.isFinite(saved.cadence())?Math.clamp(saved.cadence(),0,1):0;stats.rememberedPeriod=Math.clamp(saved.period(),0,200);
        stats.lastMachine=Math.clamp(saved.lastMachine(),0,stats.last);stats.rememberedMachineSpan=Double.isFinite(saved.machineSpan())?Math.clamp(saved.machineSpan(),0,config.retentionHours*3600):0;
        stats.rememberedMachineTime=stats.lastMachine;
        if(saved.evidenceVersion()<2){stats.rememberedCadence=0;stats.rememberedMachineSpan=0;}
        double causeDecay=Math.exp(-Math.max(0,now-saved.causesUpdated())/60000d);
        stats.attempted=Double.isFinite(saved.attempted())?Math.clamp(saved.attempted(),0,1e9)*causeDecay:0;
        stats.self=Double.isFinite(saved.self())?Math.clamp(saved.self(),0,saved.attempted())*causeDecay:0;stats.causesUpdated=now;
        stats.decay(now);return stats;
    }
}
