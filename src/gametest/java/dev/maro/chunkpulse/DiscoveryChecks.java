package dev.maro.chunkpulse;

import java.util.*;

/** Discovery and guidance contracts run headlessly with the same real activity scorer. */
public final class DiscoveryChecks {
    private static final long START=9_000_000,END=START+79800;
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static SelfActivityFilter.Position p(double x,double y,double z){return new SelfActivityFilter.Position(x,y,z);}
    private static void farm(ActivityEngine e,int chunk,int y,int ticks){for(int tick=0;tick<ticks;tick++)for(int machine=0;machine<4;machine++)e.accept(new SignalEvent(END-(ticks-1L-tick)*200,chunk*16+4+machine,y,8,SignalEvent.Kind.PISTON,true,"piston"),false);}
    private static ActivityEngine farm(PulseConfig c,int...chunks){var e=new ActivityEngine(c);for(int chunk:chunks)farm(e,chunk,-45,400);return e;}
    public static void run(){
        var c=new PulseConfig();var e=farm(c,0);var clusters=e.clusters(END);require(clusters.getFirst().tier()==ClusterEngine.Tier.HIGH,"Fixture must be HIGH");
        var d=new ClusterDiscovery(c);var above=p(8,60,8);d.update(clusters,above,END);
        var guide=d.guidance(above);require(guide!=null&&guide.mode()==ClusterDiscovery.Mode.DIG_DOWN&&guide.depth()==105,"Initial DIG DOWN/depth missing");String id=guide.target().id();
        require(d.drainCues().equals(List.of(id)),"First HIGH footprint entry did not cue exactly once");
        d.tick(p(8,20,8),END+50);require(d.guidance(p(8,20,8)).depth()==65,"Descending depth failed to decrease live");
        d.tick(p(-3,30,8),END+100);require(d.guidance(p(-3,30,8)).mode()==ClusterDiscovery.Mode.APPROACH,"Outside footprint did not get directional guidance");
        d.tick(p(8,-20,8),END+150);require(d.guidance(p(8,-20,8)).depth()==25&&d.drainCues().isEmpty(),"Re-entry cued twice or depth incorrect");
        d.tick(p(8,-22,8),END+200);require(d.state(id)==ClusterDiscovery.State.REVEALED&&d.guidance(p(8,-22,8))==null,"Close exact machine did not reveal and clear signal");
        require(d.transitions().getLast().reason().equals("C1_EXACT_MACHINE"),"Exact reveal reason missing");
        var tiles=new TileTracker();tiles.discovery(d);tiles.update(clusters,END,c,k->true);require(tiles.visible(END,c).isEmpty(),"Found tile stayed bright/visible without history");c.showHistory=true;require(tiles.visible(END,c).size()==1&&tiles.found(tiles.visible(END,c).getFirst()),"Found ghost missing");
        var reconnect=new ClusterDiscovery(c);var saved=PulseConfig.JSON.fromJson(PulseConfig.JSON.toJson(d.snapshots()),ClusterDiscovery.Snapshot[].class);reconnect.restore(Arrays.asList(saved));reconnect.update(clusters,above,END+250);
        require(reconnect.state(id)==ClusterDiscovery.State.REVEALED&&reconnect.guidance(above)==null&&reconnect.drainCues().isEmpty(),"FOUND was lost after reconnect");
        var close=new ClusterDiscovery(c);close.update(clusters,p(8,-30,8),END);require(close.views().getFirst().state()==ClusterDiscovery.State.REVEALED&&close.guidance(above)==null&&close.drainCues().isEmpty(),"Already nearby cluster signalled before revealing");
        require(close.transitions().getLast().reason().contains("INITIAL_NEAR"),"Initial near reveal reason missing");
        var update=new ClusterDiscovery(c);update.update(clusters,above,END);update.drainCues();update.observeMachine(4,-45,8,p(4,-40,8),END+1,true);require(update.views().getFirst().state()==ClusterDiscovery.State.REVEALED&&update.transitions().getLast().reason().equals("C3_BLOCK_UPDATE"),"Real machine update reveal missing");
        var exposed=new ClusterDiscovery(c);exposed.observeMachine(4,-45,8,p(4,-40,8),END-100,true);exposed.update(clusters,above,END);require(exposed.guidance(above)==null&&exposed.views().getFirst().state()==ClusterDiscovery.State.REVEALED,"An already exposed machine retriggered when its score later reached threshold");
        var manual=new ClusterDiscovery(c);manual.update(clusters,above,END);String marked=manual.mark(null,true,above,END+1);require(marked!=null&&manual.transitions().getLast().reason().equals("C4_MANUAL_FOUND")&&manual.guidance(above)==null,"Nearest manual found failed");require(manual.mark(marked,false,above,END+2).equals(marked)&&manual.guidance(above)!=null,"Manual unfound failed");
        require(manual.mark(marked.toUpperCase(Locale.ROOT),true,above,END+3).equals(marked),"HUD uppercase cluster ID did not resolve");manual.mark(marked,false,above,END+4);
        c.signalEnabled=false;require(manual.guidance(above)==null,"Signal off did not suppress guidance");c.signalEnabled=true;c.signalMinTier=ClusterEngine.Tier.HUGE;require(manual.guidance(above)==null,"Signal minimum tier ignored");c.signalMinTier=ClusterEngine.Tier.MEDIUM;
        // Alignment is useful before the five-second dwell rule when the nearest machines are
        // horizontally separated, or with a deliberately smaller configured reveal radius.
        var alignConfig=new PulseConfig();alignConfig.revealRadius=1;var align=new ClusterDiscovery(alignConfig);var inBand=p(15,-40,15);align.update(clusters,inBand,END);
        require(align.guidance(inBand).mode()==ClusterDiscovery.Mode.ALIGN&&align.guidance(inBand).offset().contains("W")&&align.guidance(inBand).offset().contains("N"),"Near-band tunnelling guidance missing");
        align.tick(inBand,END+4999);require(align.views().getFirst().state()==ClusterDiscovery.State.HIDDEN,"Band dwell revealed early");align.tick(inBand,END+5000);require(align.guidance(inBand)==null&&align.transitions().getLast().reason().equals("C2_BAND_DWELL"),"Five-second band dwell failed");
        var reset=new ClusterDiscovery(alignConfig);reset.update(clusters,inBand,END);reset.tick(p(15,0,15),END+4000);reset.tick(inBand,END+4900);reset.tick(inBand,END+6000);require(reset.views().getFirst().state()==ClusterDiscovery.State.HIDDEN,"Leaving Y band did not reset dwell");
        // 3D selection: the horizontally closest source can be much deeper.
        var distances=new ActivityEngine(c);farm(distances,0,-60,400);farm(distances,2,-5,400);var nearest=new ClusterDiscovery(c);var outside=p(-1,60,8);nearest.update(distances.clusters(END),outside,END);require(nearest.guidance(outside).target().footprint().contains(SignalEvent.key(2,0)),"Nearest signal used only horizontal distance");
        var ties=new ActivityEngine(c);farm(ties,0,-45,10);farm(ties,2,-45,400);var tie=new ClusterDiscovery(c);var between=p(22,60,8);tie.update(ties.clusters(END),between,END);require(tie.guidance(between).target().cluster().tier()==ClusterEngine.Tier.HIGH,"Tier did not break an equal-distance tie");
        expansion(c,clusters,above);
        System.out.println("[chunkpulse-discovery] PASS: live 105/65/25-block descent, exact/initial/band/update/manual reveal reasons, once/session cue, 3D nearest selection, alignment, signal/min-tier controls, found ghosts, reconnect persistence and isolated high-tier expansion");
    }
    private static void expansion(PulseConfig c,List<ClusterEngine.Cluster> original,SelfActivityFilter.Position player){
        var d=new ClusterDiscovery(c);d.update(original,player,END);String found=d.mark(null,true,player,END+1);
        var one=farm(c,0,1);d.update(one.clusters(END),player,END+2);require(d.guidance(player)==null&&d.revealed(SignalEvent.key(1,0)),"One expansion chunk retriggered a found farm");
        var two=farm(c,0,1,2);d.update(two.clusters(END),player,END+3);require(d.guidance(player)==null,"Two expansion chunks bypassed splitChunks");
        var shortBurst=farm(c,0);for(int chunk=1;chunk<=3;chunk++)farm(shortBurst,chunk,-45,10);d.update(shortBurst.clusters(END),player,END+4);require(d.guidance(player)==null,"Old farm clocks promoted a short new expansion");
        var three=farm(c,0,1,2,3);d.update(three.clusters(END),player,END+5);var g=d.guidance(player);require(g!=null&&!g.target().id().equals(found)&&g.target().footprint().size()==3&&!g.target().footprint().contains(SignalEvent.key(0,0)),"Fresh three-chunk HIGH expansion did not get a separate hidden identity");require(d.state(found)==ClusterDiscovery.State.REVEALED,"Expansion re-promoted the found footprint");
        var quiet=new ClusterDiscovery(c);quiet.restore(d.snapshots());var adjacent=farm(c,4);quiet.update(adjacent.clusters(END),player,END+6); // adjacency to HIDDEN expansion may be a separate active region, but old found ownership survives.
        require(quiet.state(found)==ClusterDiscovery.State.REVEALED&&quiet.revealed(SignalEvent.key(0,0)),"Found ownership depended on current scored chunks");
    }
    private DiscoveryChecks(){ }
}
