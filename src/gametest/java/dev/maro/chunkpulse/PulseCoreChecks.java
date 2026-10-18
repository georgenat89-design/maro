package dev.maro.chunkpulse;

import java.util.*;

/** Synthetic unit cases; executable without a running game or network connection. */
public final class PulseCoreChecks {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){run();}
    public static void run(){
        var cadence=new PeriodicityAnalyzer();var random=new PeriodicityAnalyzer();long time=1_000_000;
        var rng=new Random(417);
        for(int i=0;i<120;i++){cadence.add(1_000_000+i*400+(i%3-1)*5);time+=25+rng.nextInt(1500);random.add(time);}
        require(cadence.cadence().ticks()==8&&cadence.cadence().quality()>.9,"8gt quantised clock missed");
        require(random.cadence().quality()<.4,"Random activity mistaken for a clock");
        var alternating=new PeriodicityAnalyzer();long alternatingTime=1_000_000;
        for(int i=0;i<120;i++){alternating.add(alternatingTime);alternatingTime+=i%2==0?100:300;}
        require(alternating.cadence().ticks()==8&&alternating.cadence().quality()>.9,"Asymmetric 2gt/6gt redstone clock missed");
        var burst=new PeriodicityAnalyzer();for(int i=0;i<100;i++)burst.add(1_000_000);
        require(burst.cadence().quality()==0,"Same-tick packets invented a clock");
        var lateClock=new PeriodicityAnalyzer();long late=1_000_000;for(int i=0;i<200;i++){late+=50+rng.nextInt(900);lateClock.add(late);}for(int i=0;i<32;i++){late+=200;lateClock.add(late);}require(lateClock.cadence().quality()>.9&&lateClock.stableSpan()<10,"Random prelude became sustained periodic evidence");
        require(ServerGate.allows("play.donutsmp.net:25565","donutsmp.net",false),"Subdomain rejected");
        require(ServerGate.allows("DONUTSMP.NET.","donutsmp.net",false),"Case/trailing dot rejected");
        require(!ServerGate.allows("notdonutsmp.net","donutsmp.net",false),"Substring host accepted");
        require(!ServerGate.allows("donutsmp.net.evil.test","donutsmp.net",false),"Suffix host accepted");
        require(!ServerGate.allows("donutsmp.net","donutsmp.net",true),"Singleplayer gate failed");
        require(SignalEvent.chunkX(new SignalEvent(1,-1,20,-17,SignalEvent.Kind.PISTON,true,"piston").chunk())==-1,"Negative chunk X attribution wrong");
        require(SignalEvent.chunkZ(new SignalEvent(1,-1,20,-17,SignalEvent.Kind.PISTON,true,"piston").chunk())==-2,"Negative chunk Z attribution wrong");
        var config=new PulseConfig();var farm=new ActivityEngine(config);long start=2_000_000;
        for(int tick=0;tick<400;tick++)for(int chunk=0;chunk<5;chunk++)for(int machine=0;machine<6;machine++)
            farm.accept(new SignalEvent(start+tick*200,chunk*16+machine,-40,chunk%2*16+3,SignalEvent.Kind.PISTON,true,"piston"),false);
        var found=farm.clusters(start+399*200);
        require(found.size()==1&&found.getFirst().chunks().size()==5,"Eight-neighbour clustering split adjacent farm");
        require(found.getFirst().tier()==ClusterEngine.Tier.HUGE,"Sustained multi-machine farm not HUGE: "+found);
        require(found.getFirst().minY()==-40&&found.getFirst().positions()==30,"Exact positions/Y lost");
        require(found.getFirst().x()>16&&found.getFirst().x()<64,"Weighted centroid escaped the farm");
        for(int i=0;i<4;i++)farm.accept(new SignalEvent(start+399*200,1600+i,-20,0,SignalEvent.Kind.PISTON,true,"piston"),false);
        require(farm.clusters(start+399*200).size()==2,"Disconnected chunks merged");
        var saved=farm.snapshots();String json=PulseConfig.JSON.toJson(saved);
        var copy=PulseConfig.JSON.fromJson(json,ChunkStats.Snapshot[].class);var restored=new ActivityEngine(config);
        restored.restore(Arrays.asList(copy),start+399*200);
        require(restored.clusters(start+399*200).getFirst().tier()==ClusterEngine.Tier.HUGE,"Persistence lost confidence or exact anchors");
        require(farm.clusters(start+2_000_000).isEmpty(),"Stale activity failed to fade");
        var weak=new ActivityEngine(config);
        for(int tick=0;tick<500;tick++)for(int chunk=0;chunk<5;chunk++)for(int i=0;i<6;i++)
            weak.accept(new SignalEvent(start+tick*100,chunk*16+i,-40,chunk%2*16,SignalEvent.Kind.PARTICLE,false,"smoke"),false);
        require(weak.clusters(start+49900).stream().noneMatch(c->c.tier().ordinal()>=ClusterEngine.Tier.HIGH.ordinal()),"Particle bursts produced a farm alert");
        var fiddling=new ActivityEngine(config);
        for(int i=0;i<8;i++)fiddling.accept(new SignalEvent(start+i*400,0,-40,0,SignalEvent.Kind.STATE,true,"observer"),false);
        require(fiddling.clusters(start+2800).stream().noneMatch(c->c.tier().ordinal()>=ClusterEngine.Tier.HIGH.ordinal()),"Short manual interaction produced HIGH");
        var mixed=new ActivityEngine(config);
        for(int i=0;i<20;i++)for(int machine=0;machine<3;machine++)mixed.accept(new SignalEvent(start+i*200,machine,-30,0,SignalEvent.Kind.STATE,true,"piston"),false);
        for(int i=0;i<200;i++)mixed.accept(new SignalEvent(start+4000+i*200,1,-30,0,SignalEvent.Kind.LIGHT,false,"block-light"),false);
        require(mixed.clusters(start+43800).stream().noneMatch(c->c.tier().ordinal()>=ClusterEngine.Tier.HIGH.ordinal()),"Weak signals extended a short machine clock into sustained evidence");
        config.maxChunks=32;config.maxDebugEvents=100;
        var bounded=new ActivityEngine(config);
        for(int i=0;i<300;i++)bounded.accept(new SignalEvent(start+i*50,i*16,-30,0,SignalEvent.Kind.STATE,true,"piston"),true);
        require(bounded.size()==32&&bounded.debugEvents().size()==100,"Memory limits exceeded");
        config.maxSignalsPerTick=64;
        for(int i=0;i<1000;i++)bounded.accept(new SignalEvent(start+50000,0,-30,0,SignalEvent.Kind.STATE,true,"piston"),false);
        require(bounded.dropped>=936,"Per-tick work budget missing");
        config.low=Double.NaN;config.cadenceQuality=Double.POSITIVE_INFINITY;config.validate();
        require(Double.isFinite(config.low)&&Double.isFinite(config.cadenceQuality),"Invalid config was not repaired");
        depthAndSelf();tiles(found,start+399*200);DiscoveryChecks.run();
        System.out.println("[chunkpulse-core] PASS: cadence, same-tick/random rejection, domain gate, negative coordinates, 8-neighbour clustering, huge farm, quiet decay, false positives, JSON roundtrip and bounded memory/work");
    }
    private static void depthAndSelf(){
        var config=new PulseConfig();var mine=new ActivityEngine(config);var nearby=new ActivityEngine(config);long start=4_000_000;
        for(int tick=0;tick<=350;tick++){
            long now=start+tick*200;int y=60-Math.min(120,tick/2);
            var context=new SelfActivityFilter.Context(new SelfActivityFilter.Position(.5,y+.5,.5),List.of(),true);
            for(var engine:List.of(mine,nearby)){
                engine.context(context,now,true);engine.mask(new SelfActivityFilter.Mask(0,y,0,now,tick%5==0),true);
                for(var kind:List.of(SignalEvent.Kind.BLOCK_UPDATE,SignalEvent.Kind.SOUND,SignalEvent.Kind.PARTICLE,SignalEvent.Kind.ENTITY,SignalEvent.Kind.FLUID,SignalEvent.Kind.LIGHT,SignalEvent.Kind.DENSITY))
                    engine.accept(new SignalEvent(now,0,y,0,kind,kind==SignalEvent.Kind.BLOCK_UPDATE||kind==SignalEvent.Kind.FLUID,kind==SignalEvent.Kind.ENTITY?"item:minecraft:cobbled_deepslate":"shaft"),true);
                engine.accept(new SignalEvent(now,1,y,0,SignalEvent.Kind.ENTITY,false,"spawn-falling_block:gravel"),true);
            }
            for(int machine=0;machine<4;machine++)nearby.accept(new SignalEvent(now,30+machine,-40,0,machine==3?SignalEvent.Kind.STATE:SignalEvent.Kind.PISTON,true,machine==3?"observer":"piston"),true);
            nearby.accept(new SignalEvent(now,32,-40,1,SignalEvent.Kind.ENTITY,false,"item:minecraft:sugar_cane"),true);
        }
        long end=start+70000;
        require(mine.clusters(end).stream().allMatch(c->c.tier()==ClusterEngine.Tier.LOW),"Own shaft produced a farm flag");
        require(mine.size()==0,"Own mining allocated ChunkStats");
        require(nearby.clusters(end).stream().anyMatch(c->c.tier().ordinal()>=2),"Periodic underground farm 30 blocks from miner did not reach HIGH");
        require(nearby.snapshots().stream().allMatch(s->s.minY()>=-64&&s.maxY()<0),"Above-ground observations entered stats");
        var replay=new ActivityEngine(config);for(var f:nearby.recording().frames()){if(f.context()!=null)replay.context(f.context(),f.time(),false);if(f.mask()!=null)replay.mask(f.mask(),false);if(f.event()!=null)replay.accept(f.event(),false);}
        require(replay.clusters(end).getFirst().tier()==nearby.clusters(end).getFirst().tier()&&replay.accepted==nearby.accepted,"Context/mask replay changed scoring");
        var filter=new SelfActivityFilter(config);
        require(!filter.decide(new SignalEvent(start,0,0,0,SignalEvent.Kind.PISTON,true,"piston")).kept(),"Y=0 passed depth gate");
        require(filter.decide(new SignalEvent(start,0,-1,0,SignalEvent.Kind.PISTON,true,"piston")).kept(),"Exact Y=-1 rejected");
        require(!filter.decide(new SignalEvent(start,0,-1,0,SignalEvent.Kind.SOUND,false,"piston",-.2)).kept(),"Approximate boundary event passed");
        require(!filter.decide(new SignalEvent(start,0,-65,0,SignalEvent.Kind.PISTON,true,"piston")).kept(),"Below minY passed");
        filter.context(new SelfActivityFilter.Context(new SelfActivityFilter.Position(.5,-40,.5),List.of(new SelfActivityFilter.Position(30.5,-40,.5)),false),start);
        require(filter.decide(new SignalEvent(start,0,-40,0,SignalEvent.Kind.PISTON,true,"piston")).factor()==1,"Exact piston exception lost");
        require(!filter.decide(new SignalEvent(start,0,-40,0,SignalEvent.Kind.SOUND,false,"piston")).kept(),"Generic near-self sound scored");
        require(filter.decide(new SignalEvent(start,30,-40,0,SignalEvent.Kind.SOUND,false,"piston")).factor()==.1,"Other player reduction missing");
        require(filter.decide(new SignalEvent(start,30,-40,0,SignalEvent.Kind.PISTON,true,"piston")).factor()==1,"Other player reduced machines");
        filter.mask(new SelfActivityFilter.Mask(16,-40,0,start,false));
        require(filter.decide(new SignalEvent(start,17,-40,0,SignalEvent.Kind.PISTON,true,"piston")).reason().equals("DROP_SELF_MASK"),"Self mask did not cross grid boundary");
        require(filter.decide(new SignalEvent(start+3100,17,-40,0,SignalEvent.Kind.PISTON,true,"piston")).kept(),"Self mask never expired");
        require(!filter.decide(new SignalEvent(start,20,-40,0,SignalEvent.Kind.DENSITY,false,"hostile-density-20")).kept(),"Nearby mobs scored");
        require(filter.decide(new SignalEvent(start,40,-40,0,SignalEvent.Kind.FLUID,true,"water")).reason().equals("DROP_FLUID"),"Fluid update scored");
        var self=new ActivityEngine(config);self.context(new SelfActivityFilter.Context(new SelfActivityFilter.Position(.5,-40,.5),List.of(),false),start,false);
        for(int tick=0;tick<=350;tick++)for(int machine=0;machine<4;machine++)self.accept(new SignalEvent(start+tick*200,machine,-40,0,SignalEvent.Kind.PISTON,true,"piston"),false);
        var own=self.clusters(end);require(!own.isEmpty()&&own.getFirst().tier()==ClusterEngine.Tier.LOW&&own.getFirst().explanation().contains("SELF"),"Self ratio failed to cap a local machine cluster");
        var light=new ActivityEngine(config);for(int tick=0;tick<20;tick++)light.accept(new SignalEvent(start+tick*400,40,-8,40,SignalEvent.Kind.LIGHT,false,"block-light"),true);
        require(light.accepted>0&&light.decisions().getFirst().reason().equals("DROP_NON_PERIODIC"),"Single light update scored or repeating light was missed");
        require(light.snapshots().getFirst().minY()==-16&&light.snapshots().getFirst().maxY()==-1,"Light section Y band is inaccurate");
        System.out.println("[chunkpulse-self] PASS: Y60 to -60 mining shaft, torches, fluid, falling blocks, nearby farm HIGH, surface rejection, masks, other players, SELF cap, periodic light and context replay");
    }
    private static void tiles(List<ClusterEngine.Cluster> farm,long now){
        var config=new PulseConfig();var tracker=new TileTracker();tracker.update(farm,now,config,c->true);
        var shown=tracker.visible(now,config);require(shown.size()==5,"One tile per chunk missing");
        var tile=shown.getFirst();require(Math.abs(tile.plane(config,-37.3)+37.98)<1e-8,"PLAYER plane does not follow feet");
        config.planeMode=PulseConfig.Plane.FIXED;require(tile.plane(config,10)==.02,"FIXED plane incorrect");config.planeMode=PulseConfig.Plane.CLUSTER;require(tile.plane(config,10)<0,"CLUSTER plane escaped underground");
        config.planeMode=PulseConfig.Plane.PLAYER;var a=new TileTracker.Tile(0,0,1,ClusterEngine.Tier.HIGH,100,-40,-40,-40,now,now);var b=new TileTracker.Tile(1,0,1,ClusterEngine.Tier.HIGH,100,-40,-40,-40,now,now);var map=Map.of(a.key(),a,b.key(),b);
        require((TileTracker.edges(a,map,config,0)&2)==0&&(TileTracker.edges(b,map,config,0)&8)==0,"Shared inner border was drawn");
        require(tile.fade(now+5000,config)==.5&&tracker.visible(now+10001,config).isEmpty(),"Ten-second tile fade incorrect");
        config.showHistory=true;require(tracker.visible(now+10001,config).size()==5,"History ghost disappeared");var restored=new TileTracker();restored.restore(tracker.snapshots(),now+10001,config);require(restored.snapshots().size()==5,"History tiles failed to roundtrip");
        System.out.println("[chunkpulse-tiles] PASS: single flat tiles, all planes, shared-border removal, fade and bounded history");
    }
}
