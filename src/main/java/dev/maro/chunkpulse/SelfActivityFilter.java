package dev.maro.chunkpulse;

import java.util.*;

/** Pure bounded ingestion filter: never touches a world or sends packets. */
public final class SelfActivityFilter {
    public record Position(double x,double y,double z) {
        double distance2(double x,double y,double z){return sq(this.x-x)+sq(this.y-y)+sq(this.z-z);}
        boolean valid(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public record Context(Position local,List<Position> others,boolean heldLight) { }
    public record Mask(int x,int y,int z,long time,boolean light) { }
    public record Decision(String reason,double factor,boolean self) { public boolean kept(){return factor>0;} }
    private record Emitter(long cell,SignalEvent.Kind kind,String source) { }
    private static final class Repetition {
        final int x,y,z;final PeriodicityAnalyzer cadence=new PeriodicityAnalyzer();
        Repetition(SignalEvent e){x=e.x();y=e.y();z=e.z();}
    }
    private final PulseConfig config;
    private final LinkedHashMap<Long,Mask> masks=new LinkedHashMap<>(32,.75f,true);
    private final Map<Long,Set<Long>> maskCells=new HashMap<>();
    private final Map<Long,List<Position>> players=new HashMap<>();
    private final LinkedHashMap<Emitter,Repetition> repetitions=new LinkedHashMap<>(128,.75f,true);
    private Position local;
    private long lightUntil;
    public SelfActivityFilter(PulseConfig config){this.config=config;}
    private static double sq(double v){return v*v;}
    private static long position(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|(y&4095L);}
    private static long cell(int x,int y,int z,int size){return position(Math.floorDiv(x,size),Math.floorDiv(y,size),Math.floorDiv(z,size));}
    public void context(Context c,long time){
        players.clear();if(c==null){local=null;return;}
        local=c.local()!=null&&c.local().valid()?c.local():null;
        if(c.heldLight())lightUntil=time+config.lightHoldTicks*50L;
        if(c.others()!=null)for(var p:c.others().stream().filter(Objects::nonNull).filter(Position::valid).limit(512).toList())
            players.computeIfAbsent(cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()),config.selfRadius),key->new ArrayList<>()).add(p);
    }
    public void mask(Mask m){
        long key=position(m.x(),m.y(),m.z());var old=masks.remove(key);if(old!=null)unlink(key,old);
        masks.put(key,m);maskCells.computeIfAbsent(cell(m.x(),m.y(),m.z(),8),k->new HashSet<>()).add(key);
        if(m.light())lightUntil=Math.max(lightUntil,m.time()+config.lightHoldTicks*50L);
        while(masks.size()>config.maxMasks){long first=masks.keySet().iterator().next();unlink(first,masks.remove(first));}
    }
    private void unlink(long key,Mask m){long c=cell(m.x(),m.y(),m.z(),8);var set=maskCells.get(c);if(set!=null){set.remove(key);if(set.isEmpty())maskCells.remove(c);}}
    public boolean depth(SignalEvent e){
        if(e.y()<config.minY||e.y()>=config.maxY)return false;
        if(e.kind()==SignalEvent.Kind.LIGHT){int bottom=Math.floorDiv(e.y(),16)*16;return bottom<0&&bottom>=config.minY&&bottom+16<=config.maxY;}
        return e.exact()||e.rawY()>=config.minY+1&&e.rawY()<config.maxY-1;
    }
    private static boolean nearMachine(SignalEvent e){return e.exact()&&(e.kind()==SignalEvent.Kind.PISTON||e.kind()==SignalEvent.Kind.CRAFTER||e.kind()==SignalEvent.Kind.DISPENSER||e.kind()==SignalEvent.Kind.STATE&&(e.source().equals("observer")||e.source().equals("comparator")||e.source().equals("repeater")));}
    private static boolean miningItem(String s){return s.contains("stone")||s.contains("ore")||s.contains("dirt")||s.contains("gravel")||s.contains("sand")||s.contains("netherrack")||s.contains("tuff");}
    private boolean nearOther(SignalEvent e){
        int size=config.selfRadius,cx=Math.floorDiv(e.x(),size),cy=Math.floorDiv(e.y(),size),cz=Math.floorDiv(e.z(),size);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            var list=players.get(position(cx+dx,cy+dy,cz+dz));if(list!=null)for(var p:list)if(p.distance2(e.x()+.5,e.rawY(),e.z()+.5)<=sq(size))return true;
        }return false;
    }
    private double sectionDistance2(Position p,SignalEvent e){
        int x=(e.x()>>4)*16,y=Math.floorDiv(e.y(),16)*16,z=(e.z()>>4)*16;
        return sq(Math.max(0,Math.max(x-p.x(),p.x()-(x+16))))+sq(Math.max(0,Math.max(y-p.y(),p.y()-(y+16))))+sq(Math.max(0,Math.max(z-p.z(),p.z()-(z+16))));
    }
    private boolean masked(SignalEvent e,int radius){
        int cx=Math.floorDiv(e.x(),8),cy=Math.floorDiv(e.y(),8),cz=Math.floorDiv(e.z(),8);
        int reach=e.kind()==SignalEvent.Kind.LIGHT?2:1;
        for(int dx=-reach;dx<=reach;dx++)for(int dy=-reach;dy<=reach;dy++)for(int dz=-reach;dz<=reach;dz++){
            var set=maskCells.get(position(cx+dx,cy+dy,cz+dz));if(set==null)continue;
            for(long key:set){var m=masks.get(key);if(m==null||e.time()-m.time()>config.selfMaskTicks*50L||e.time()<m.time())continue;
                var p=new Position(m.x()+.5,m.y()+.5,m.z()+.5);
                if(e.kind()==SignalEvent.Kind.LIGHT?sectionDistance2(p,e)<=sq(radius):p.distance2(e.x()+.5,e.rawY(),e.z()+.5)<=sq(radius))return true;
            }
        }return false;
    }
    public Decision decide(SignalEvent e){
        if(!depth(e))return new Decision(e.y()>=config.maxY||e.rawY()>=config.maxY-1?"DROP_Y_ABOVE":"DROP_Y_BELOW",0,false);
        boolean near=local!=null&&local.distance2(e.x()+.5,e.rawY(),e.z()+.5)<=sq(config.selfRadius);
        boolean mask=masked(e,e.kind()==SignalEvent.Kind.ENTITY&&miningItem(e.source())?config.miningDropRadius:config.maskRadius);
        boolean self=near||mask;
        if(e.kind()==SignalEvent.Kind.FLUID)return new Decision("DROP_FLUID",0,self);
        if(mask)return new Decision("DROP_SELF_MASK",0,true);
        if(e.kind()==SignalEvent.Kind.LIGHT&&local!=null&&e.time()<=lightUntil&&sectionDistance2(local,e)<=sq(config.selfRadius))return new Decision("DROP_SELF_LIGHT",0,true);
        if(near&&!nearMachine(e))return new Decision("DROP_SELF_RADIUS",0,true);
        if(e.kind()==SignalEvent.Kind.DENSITY&&local!=null&&local.distance2(e.x()+.5,e.rawY(),e.z()+.5)<=sq(config.mobSelfRadius))return new Decision("DROP_SELF_RADIUS",0,true);
        if(e.kind()==SignalEvent.Kind.BLOCK_UPDATE)return new Decision("DROP_UNRELATED",0,self);
        if((e.kind()==SignalEvent.Kind.LIGHT||e.kind()==SignalEvent.Kind.ENTITY)&&!periodic(e))return new Decision("DROP_NON_PERIODIC",0,self);
        boolean other=!e.kind().machine&&nearOther(e);
        return new Decision(other?"DROP_PLAYER_NEARBY":"KEPT",other?config.otherPlayerWeight:1,self);
    }
    private boolean periodic(SignalEvent e){
        int size=e.kind()==SignalEvent.Kind.LIGHT?16:3,cx=Math.floorDiv(e.x(),size),cy=Math.floorDiv(e.y(),size),cz=Math.floorDiv(e.z(),size);
        Emitter key=new Emitter(position(cx,cy,cz),e.kind(),e.source());Repetition lane=null;
        int reach=size==16?0:1;
        outer:for(int dx=-reach;dx<=reach;dx++)for(int dy=-reach;dy<=reach;dy++)for(int dz=-reach;dz<=reach;dz++){
            var candidateKey=new Emitter(position(cx+dx,cy+dy,cz+dz),e.kind(),e.source());var c=repetitions.get(candidateKey);
            if(c!=null&&(size==16||Math.abs(c.x-e.x())<=1&&Math.abs(c.y-e.y())<=1&&Math.abs(c.z-e.z())<=1)){lane=c;key=candidateKey;break outer;}
        }
        if(lane==null){lane=new Repetition(e);repetitions.put(key,lane);}
        lane.cadence.add(e.time());while(repetitions.size()>config.maxEmitters)repetitions.remove(repetitions.keySet().iterator().next());
        return lane.cadence.cadence().quality()>=config.cadenceQuality;
    }
}
