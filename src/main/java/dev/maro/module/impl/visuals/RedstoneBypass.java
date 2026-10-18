package dev.maro.module.impl.visuals;

import dev.maro.chunkpulse.*;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** ChunkPulse's passive underground activity detector, integrated into Maro. */
public final class RedstoneBypass extends Module implements PacketSniffer.Sink {
    private static RedstoneBypass instance;
    private final PulseConfig config=PulseConfig.load(Persistence.folder().resolve("config.json"));
    private final TextSetting servers=add(new TextSetting("Allowed Servers","Comma separated domains, including subdomains. Multiplayer only.",String.join(",",config.servers),1024,"donutsmp.net"));
    private final BooleanSetting overlay=add(new BooleanSetting("Chunk Tiles","Flat 16 x 16 tiles above received underground activity",config.overlay));
    private final ModeSetting plane=add(new ModeSetting("Tile Plane","Player follows your feet; Fixed uses Plane Y; Cluster uses machine depth",config.planeMode.name(),"PLAYER","FIXED","CLUSTER"));
    private final NumberSetting planeY=add(new NumberSetting("Plane Y","Height for Fixed mode",config.planeY,-2048,2048,.01).visible(()->plane.is("FIXED")));
    private final BooleanSetting through=add(new BooleanSetting("See Through","Show tiles through terrain",config.seeThrough));
    private final NumberSetting range=add(new NumberSetting("Render Distance","Tile distance from the camera, in chunks",config.renderDistanceChunks,1,32,1).suffix(" chunks"));
    private final BooleanSetting labels=add(new BooleanSetting("Tile Labels","Tier, score and underground Y band at each tile centre",config.showLabels));
    private final BooleanSetting history=add(new BooleanSetting("Show History","Keep a dim grey tile where activity was previously observed",config.showHistory));
    private final BooleanSetting pulse=add(new BooleanSetting("Pulse","Slow alpha pulse for HIGH and HUGE activity",config.pulse));
    private final NumberSetting fill=add(new NumberSetting("Tile Opacity","Translucent tile fill",config.fillAlpha*100,25,40,1).suffix("%"));
    private final NumberSetting border=add(new NumberSetting("Border Opacity","Crisp outer cluster border",config.borderAlpha*100,50,100,1).suffix("%"));
    private final BooleanSetting hud=add(new BooleanSetting("Activity HUD","Ranked clusters, coordinates and last-active time",config.activityHud));
    private final BooleanSetting nearest=add(new BooleanSetting("Nearest Direction","Direction and distance to the nearest flagged chunk",config.nearestHud));
    private final BooleanSetting signal=add(new BooleanSetting("Dig Guidance","Live descent and tunnelling guidance for hidden clusters",config.signalEnabled));
    private final ModeSetting signalTier=add(new ModeSetting("Signal Minimum Tier","Only hidden clusters at this tier or higher prompt you",config.signalMinTier.name(),"LOW","MEDIUM","HIGH","HUGE"));
    private final BooleanSetting digCue=add(new BooleanSetting("Dig Cue","One local toast and sound per hidden HIGH/HUGE cluster per session",config.digCue));
    private final NumberSetting align=add(new NumberSetting("Alignment Radius","Switch to horizontal tunnelling near the activity's Y band",config.alignRadius,1,32,1).suffix(" blocks"));
    private final NumberSetting reveal=add(new NumberSetting("Reveal Radius","Automatically mark nearby exact machine activity as FOUND",config.revealRadius,1,128,1).suffix(" blocks"));
    private final NumberSetting split=add(new NumberSetting("Expansion Chunks","Fresh HIGH chunks needed to signal a separate expansion",config.splitChunks,1,64,1));
    private final NumberSetting rows=add(new NumberSetting("HUD Rows","Maximum ranked clusters",config.hudRows,1,10,1));
    private final ModeSetting minimum=add(new ModeSetting("Minimum Tier","Hide weaker observations",config.minimumTier,"LOW","MEDIUM","HIGH","HUGE"));
    private final NumberSetting minY=add(new NumberSetting("Minimum Y","Only ingest activity at or above this depth",config.minY,-2048,-2,1));
    private final NumberSetting maxY=add(new NumberSetting("Maximum Y","Only ingest activity strictly below this height",config.maxY,-2046,0,1));
    private final NumberSetting selfRadius=add(new NumberSetting("Self Radius","Discard generic activity near you",config.selfRadius,1,64,1));
    private final NumberSetting maskTicks=add(new NumberSetting("Self Mask Ticks","Ignore effects around your break/place positions",config.selfMaskTicks,20,1200,1));
    private final NumberSetting events=add(new NumberSetting("Machine Events","Minimum repeated events at a fixed position for MEDIUM",config.minEventsForFlag,6,100,1));
    private final NumberSetting machines=add(new NumberSetting("Distinct Machines","Required fixed machines for HIGH/HUGE",config.minDistinctMachines,4,128,1));
    private final BooleanSetting alerts=add(new BooleanSetting("New Farm Alerts","Notify when a cluster first reaches HIGH/HUGE",config.alerts));
    private final BooleanSetting sound=add(new BooleanSetting("Alert Sound","Local ping with farm alerts",config.alertSound));
    private final BooleanSetting blockEntities=add(new BooleanSetting("Block Entity Signals","Weak supporting machine block-entity observations",config.blockEntitySignals));
    private final BooleanSetting debug=add(new BooleanSetting("Debug Recording","Record filter reasons, weights and local replay frames",config.debugRecording));
    private final NumberSetting low=add(new NumberSetting("Low Threshold","Minimum score to group a chunk",config.low,1,1000,1));
    private final NumberSetting medium=add(new NumberSetting("Medium Threshold","Also requires repeated fixed machine evidence",config.medium,1,5000,1));
    private final NumberSetting high=add(new NumberSetting("High Threshold","Also requires 60 seconds of periodic machine activity",config.high,1,20000,1));
    private final NumberSetting huge=add(new NumberSetting("Huge Threshold","Also requires several chunks and many machines",config.huge,1,100000,1));
    private final ButtonSetting export=add(new ButtonSetting("Export Locations","CSV plus recorded replay and event decisions","Export CSV",()->export(false)));
    private final ButtonSetting waypoints=add(new ButtonSetting("Export Waypoints","Cluster centroids as Xaero .points","Export",()->export(true)));
    private final ButtonSetting clear=add(new ButtonSetting("Clear This Dimension","Forget active and historical observations","Clear",this::clear));
    private final PacketSniffer sniffer=new PacketSniffer(this);
    private final TileTracker tiles=new TileTracker();
    private final Set<String> sessionDigCues=new HashSet<>();
    private Object cueHandler;
    private ClusterDiscovery discovery=new ClusterDiscovery(config);
    private boolean discoveryReady;
    private long savedDiscoveryRevision;
    private final LinkedHashMap<Long,ClusterEngine.Tier> announced=new LinkedHashMap<>();
    private ClientWorld world;
    private Object handler;
    private String server="",dimension="";
    private ActivityEngine engine=new ActivityEngine(config);
    private List<ClusterEngine.Cluster> clusters=List.of();
    private CompletableFuture<Persistence.State> loading=CompletableFuture.completedFuture(Persistence.State.empty());
    private long nextAnalysis,nextSave;
    private int generation;
    public RedstoneBypass(){super("Redstone Bypass","Flags received machine activity below deepslate with flat chunk markers and dig guidance",Category.VISUALS);strongDefaults();syncConfig();instance=this;tiles.discovery(discovery);}
    public static RedstoneBypass instance(){return instance;}
    public PulseConfig config(){return config;}
    public PacketSniffer sniffer(){return sniffer;}
    public ActivityEngine engine(){return engine;}
    public TileTracker tiles(){return tiles;}
    public ClusterDiscovery discovery(){return discovery;}
    public boolean discoveryReady(){return discoveryReady;}
    public List<ClusterEngine.Cluster> clusters(){return clusters;}
    public String server(){return server;}
    public String dimension(){return dimension;}
    public boolean blockEntities(){return blockEntities.get();}
    public boolean accepting(){return isEnabled()&&world!=null&&world==mc.world&&handler==mc.getNetworkHandler()&&!mc.isInSingleplayer()&&mc.getCurrentServerEntry()!=null&&ServerGate.allows(mc.getCurrentServerEntry().address,servers.get(),false);}
    @Override public List<SettingSection> getSettingSections(){return List.of(SettingSection.of("Underground Activity",overlay,range,signal,digCue,history));}
    @Override public com.google.gson.JsonObject saveExtra(){var extra=new com.google.gson.JsonObject();extra.addProperty("chunkPulseVersion",2);extra.addProperty("simpleActivityVersion",1);return extra;}
    @Override public void loadExtra(com.google.gson.JsonObject data){if(!data.has("simpleActivityVersion"))strongDefaults();syncConfig();}
    /** Fixed strong preset. Legacy fields stay readable for old configs and local expert commands. */
    private void strongDefaults(){
        plane.set("PLAYER");planeY.set(.02);through.set(true);labels.set(false);pulse.set(true);fill.set(32d);border.set(90d);
        hud.set(false);nearest.set(true);rows.set(4d);minimum.set("MEDIUM");minY.set(-64d);maxY.set(0d);
        selfRadius.set(10d);maskTicks.set(60d);events.set(6d);machines.set(4d);blockEntities.set(false);debug.set(false);
        alerts.set(false);sound.set(false);signalTier.set("MEDIUM");align.set(6d);reveal.set(24d);split.set(3d);
        low.set(12d);medium.set(55d);high.set(160d);huge.set(650d);config.simpleTiles=true;
    }
    private void syncConfig(){
        config.servers=Arrays.stream(servers.get().split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        config.overlay=overlay.get();config.planeMode=PulseConfig.Plane.valueOf(plane.get());config.planeY=planeY.get();config.seeThrough=through.get();config.renderDistanceChunks=range.getInt();config.showLabels=labels.get();config.showHistory=history.get();config.pulse=pulse.get();config.fillAlpha=fill.get()/100;config.borderAlpha=border.get()/100;
        config.activityHud=hud.get();config.nearestHud=nearest.get();config.hudRows=rows.getInt();config.minimumTier=minimum.get();config.minY=minY.getInt();config.maxY=maxY.getInt();config.selfRadius=selfRadius.getInt();config.selfMaskTicks=maskTicks.getInt();config.minEventsForFlag=events.getInt();config.minDistinctMachines=machines.getInt();config.alerts=alerts.get();config.alertSound=sound.get();config.blockEntitySignals=blockEntities.get();config.debugRecording=debug.get();
        config.signalEnabled=signal.get();config.signalMinTier=ClusterEngine.Tier.valueOf(signalTier.get());config.digCue=digCue.get();config.alignRadius=align.get();config.revealRadius=reveal.get();config.splitChunks=split.getInt();
        config.low=low.get();config.medium=medium.get();config.high=high.get();config.huge=huge.get();config.validate();
    }
    public void saveConfig(){syncConfig();PulseConfig.save(Persistence.folder().resolve("config.json"),config);}
    @Override protected void onEnable(){nextAnalysis=0;nextSave=System.currentTimeMillis()+30000;}
    @Override protected void onDisable(){finish();}
    public void finish(){flush();generation++;world=null;handler=null;server="";dimension="";sniffer.clear();clusters=List.of();tiles.clear();announced.clear();engine=new ActivityEngine(config);discoveryReady=false;discovery=new ClusterDiscovery(config);tiles.discovery(discovery);saveConfig();}
    private void syncSession(){
        var info=mc.getCurrentServerEntry();String address=info==null?"":info.address;
        boolean allowed=mc.world!=null&&mc.player!=null&&ServerGate.allows(address,servers.get(),mc.isInSingleplayer());
        String dim=allowed?mc.world.getRegistryKey().getValue().toString():"";
        if(!allowed){if(world!=null)finish();return;}
        if(world==mc.world&&handler==mc.getNetworkHandler()&&server.equals(address)&&dimension.equals(dim))return;
        flush();int ticket=++generation;world=mc.world;handler=mc.getNetworkHandler();server=address;dimension=dim;
        if(cueHandler!=handler){sessionDigCues.clear();cueHandler=handler;}
        discovery=new ClusterDiscovery(config,sessionDigCues,server+"\n"+dim+"\n");tiles.discovery(discovery);discoveryReady=false;savedDiscoveryRevision=0;
        sniffer.clear();announced.clear();tiles.clear();clusters=List.of();engine=new ActivityEngine(config);var current=engine;
        loading=Persistence.loadState(server,dimension,config.maxChunks);
        loading.thenAccept(saved->mc.execute(()->{if(ticket==generation&&engine==current&&isEnabled()){long now=System.currentTimeMillis();current.restore(saved.chunks(),now);tiles.restore(saved.tiles(),now,config);discovery.restore(saved.discoveries());discoveryReady=true;nextAnalysis=0;}}));
        nextAnalysis=0;nextSave=System.currentTimeMillis()+30000;
    }
    public static boolean lightItem(net.minecraft.item.ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        if(stack.getItem() instanceof net.minecraft.item.BlockItem block&&block.getBlock().getDefaultState().getLuminance()>0)return true;
        return stack.isOf(net.minecraft.item.Items.LAVA_BUCKET);
    }
    public static void selfAction(net.minecraft.util.math.BlockPos pos,boolean light){var m=instance;if(m!=null&&m.accepting())m.engine.mask(new SelfActivityFilter.Mask(pos.getX(),pos.getY(),pos.getZ(),System.currentTimeMillis(),light),m.debug.get());}
    private void context(long now){
        var p=mc.player;if(p==null)return;
        var others=mc.world.getPlayers().stream().filter(other->other!=p).limit(512).map(other->new SelfActivityFilter.Position(other.getX(),other.getY(),other.getZ())).toList();
        engine.context(new SelfActivityFilter.Context(new SelfActivityFilter.Position(p.getX(),p.getY(),p.getZ()),others,lightItem(p.getMainHandStack())||lightItem(p.getOffHandStack())),now,debug.get());
    }
    private SelfActivityFilter.Position playerPosition(){return mc.player==null?null:new SelfActivityFilter.Position(mc.player.getX(),mc.player.getY(),mc.player.getZ());}
    @Override public void revealedMachine(int x,int y,int z,long time){if(accepting())discovery.observeMachine(x,y,z,playerPosition(),time,true);}
    @Override public void observe(SignalEvent event){if(accepting()){engine.accept(event,debug.get());if(event.exact()&&event.kind().machine)discovery.observeMachine(event.x(),event.y(),event.z(),playerPosition(),event.time(),false);}}
    @Override public void onTick(){
        int beforeMin=config.minY,beforeMax=config.maxY;syncConfig();
        if(beforeMin!=config.minY||beforeMax!=config.maxY){engine.clear();sniffer.clear();tiles.clear();clusters=List.of();nextAnalysis=0;}
        syncSession();if(!accepting())return;long now=System.currentTimeMillis();context(now);if(!discoveryReady)return;
        if(now>=nextAnalysis){nextAnalysis=now+500;clusters=engine.clusters(now);discovery.update(clusters,playerPosition(),now);tiles.update(clusters,now,config,this::visible);if(alerts.get())announce(now);}
        else discovery.tick(playerPosition(),now);
        for(String id:discovery.drainCues()){var v=discovery.views().stream().filter(view->view.id().equals(id)&&view.state()==ClusterDiscovery.State.HIDDEN).findFirst().orElse(null);if(v!=null){Notifications.push("ChunkPulse", "DIG DOWN: "+Math.round(v.depth(playerPosition()))+" blocks to y"+v.cluster().minY()+".."+v.cluster().maxY()+" ["+v.cluster().tier()+"]",Notifications.Type.INFO,4500);mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f));}}
        if(discovery.revision()!=savedDiscoveryRevision){if(debug.get()){var changes=discovery.transitions();changes.stream().skip(Math.max(0,changes.size()-(discovery.revision()-savedDiscoveryRevision))).forEach(t->feedback(t.id()+" "+t.from()+" -> "+t.to()+" / "+t.reason()));}savedDiscoveryRevision=discovery.revision();flush();}
        if(now>=nextSave&&loading.isDone()){nextSave=now+30000;saveConfig();flush();}
    }
    private void announce(long now){
        int sent=0;
        for(var v:discovery.views()){var c=v.cluster();if(v.state()!=ClusterDiscovery.State.HIDDEN||c.tier().ordinal()<2||now-c.last()>10000)continue;boolean known=false;
            for(var chunk:c.chunks()){var old=announced.get(SignalEvent.key(chunk.x,chunk.z));if(old!=null&&old.ordinal()>=c.tier().ordinal())known=true;}
            for(var chunk:c.chunks())announced.put(SignalEvent.key(chunk.x,chunk.z),c.tier());
            if(!known&&sent++<2){String message=c.tier()+" activity at "+Math.round(c.x())+", "+Math.round(c.y())+", "+Math.round(c.z());Notifications.push("ChunkPulse",message,Notifications.Type.INFO,5000);feedback(message);
                if(sound.get())mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f));}
        }while(announced.size()>config.maxChunks)announced.remove(announced.keySet().iterator().next());
    }
    private void flush(){
        if(server.isEmpty())return;String key=server,dim=dimension;var current=engine.snapshots();var history=tiles.snapshots();var discoveries=discovery.snapshots();int limit=config.maxChunks;long revision=Persistence.reserve(key,dim);var currentConfig=PulseConfig.JSON.fromJson(PulseConfig.JSON.toJson(config),PulseConfig.class);
        loading.thenCompose(saved->{var merged=new HashMap<Long,ChunkStats.Snapshot>();var oldTiles=new HashMap<Long,TileTracker.Tile>();long now=System.currentTimeMillis();
            for(var s:saved.chunks())if(s!=null&&ChunkStats.restore(s,now,currentConfig)!=null)merged.put(SignalEvent.key(s.x(),s.z()),s);
            for(var s:current)merged.merge(SignalEvent.key(s.x(),s.z()),s,(a,b)->a.last()>b.last()?a:b);
            for(var t:saved.tiles())if(t!=null&&t.minY()>=currentConfig.minY&&t.maxY()<currentConfig.maxY)oldTiles.put(t.key(),t);
            for(var t:history)oldTiles.merge(t.key(),t,(a,b)->a.flagged()>b.flagged()?a:b);
            var found=new LinkedHashMap<String,ClusterDiscovery.Snapshot>();for(var d:saved.discoveries())if(d!=null)found.put(d.id(),d);for(var d:discoveries)found.put(d.id(),d);
            return Persistence.save(key,dim,merged.values().stream().sorted(Comparator.comparingLong(ChunkStats.Snapshot::last).reversed()).limit(limit).toList(),oldTiles.values().stream().sorted(Comparator.comparingLong(TileTracker.Tile::seen).reversed()).limit(limit).toList(),List.copyOf(found.values()),revision);
        }).exceptionally(error->{dev.maro.Maro.LOGGER.warn("ChunkPulse save failed",error);return null;});
    }
    public void clear(){if(!accepting()){feedback("Enable Redstone Bypass on an allowed multiplayer server first.");return;}generation++;engine.clear();sniffer.clear();tiles.clear();discovery=new ClusterDiscovery(config,sessionDigCues,server+"\n"+dimension+"\n");tiles.discovery(discovery);discoveryReady=true;clusters=List.of();announced.clear();loading=CompletableFuture.completedFuture(Persistence.State.empty());flush();feedback("Cleared activity, FOUND states and history for "+dimension+".");}
    public void export(boolean points){
        if(!accepting()){feedback("Enable Redstone Bypass on an allowed multiplayer server first.");return;}
        var recording=engine.recording();var decisions=engine.decisions();var found=discovery.snapshots();var transitions=discovery.transitions();
        Persistence.export(server,dimension,clusters,List.of(),points).thenCompose(path->Persistence.exportDiscovery(path,found,transitions).thenApply(ignored->path)).thenCompose(path->decisions.isEmpty()?CompletableFuture.completedFuture(path):Persistence.exportDebug(path,recording,decisions).thenApply(ignored->path)).whenComplete((path,error)->mc.execute(()->feedback(error==null?"Saved "+path:"Export failed; see the local log.")));
    }
    public void list(){if(!accepting()){feedback("Inactive. Enable on an allowed multiplayer server (default donutsmp.net).");return;}if(clusters.isEmpty())feedback("Listening for received underground machine activity.");clusters.stream().limit(10).forEach(c->feedback(c.tier()+" "+Math.round(c.score())+" @ "+Math.round(c.x())+" "+Math.round(c.y())+" "+Math.round(c.z())+" | "+c.explanation()));}
    public void debugToggle(){debug.set(!debug.get());feedback("Debug "+(debug.get()?"on":"off")+"; kept "+engine.accepted+", dropped "+engine.dropped+". Nearby-player signals retain 10% weight.");var recent=engine.decisions();recent.stream().skip(Math.max(0,recent.size()-8)).forEach(r->feedback(r.reason()+" "+r.event().source()+" @ "+r.event().x()+" "+r.event().y()+" "+r.event().z()+" weight="+String.format(Locale.ROOT,"%.3f",r.weight())));var changes=discovery.transitions();changes.stream().skip(Math.max(0,changes.size()-8)).forEach(t->feedback(t.id()+" "+t.from()+" -> "+t.to()+" / "+t.reason()));saveConfig();}
    public void found(String id,boolean value){if(!accepting()||!discoveryReady){feedback("Wait for ChunkPulse locations on an allowed server to load.");return;}String changed=discovery.mark(id,value,playerPosition(),System.currentTimeMillis());if(changed==null){feedback("Unknown cluster. Use /chunkpulse clusters for IDs.");return;}flush();feedback(changed+" marked "+(value?"FOUND":"HIDDEN; nearby reveal rules still apply")+".");}
    public void clusterList(){if(!accepting()||!discoveryReady){feedback("Waiting for an allowed server and saved locations.");return;}if(discovery.views().isEmpty())feedback("No active clusters.");var p=playerPosition();for(var v:discovery.views().stream().limit(20).toList())feedback(v.id()+" "+v.cluster().tier()+" "+v.state()+" | "+Math.round(v.depth(p))+" below | "+ClusterDiscovery.direction(v.cluster().x()-p.x(),v.cluster().z()-p.z()));}
    public void signal(boolean value){signal.set(value);changed("Dig guidance "+(value?"on":"off"));}
    public void threshold(String tier,double value){switch(tier.toLowerCase(Locale.ROOT)){case "low"->low.set(value);case "medium"->medium.set(value);case "high"->high.set(value);case "huge"->huge.set(value);default->{feedback("Tier must be low, medium, high or huge.");return;}}changed(tier+" threshold updated.");}
    public void plane(String value){try{plane.set(PulseConfig.Plane.valueOf(value.toUpperCase(Locale.ROOT)).name());changed("Tile plane: "+plane.get());}catch(Exception e){feedback("Plane must be PLAYER, FIXED or CLUSTER.");}}
    public void display(String name,boolean value){switch(name){case "see-through"->through.set(value);case "labels"->labels.set(value);case "history"->history.set(value);default->throw new IllegalArgumentException(name);}changed(name+" "+(value?"on":"off"));}
    private void changed(String message){saveConfig();dev.maro.config.ConfigManager.saveAll();feedback(message);}
    public static void feedback(String message){if(mc.player!=null)mc.player.sendMessage(Text.literal("[ChunkPulse] "+message),false);}
    public boolean visible(ClusterEngine.Cluster c){return c.tier().ordinal()>=minimum.index();}
    public double range(){return config.renderDistanceChunks*16d;}
    public boolean hasOverlay(){return accepting()&&discoveryReady&&overlay.get();}
    public void render(dev.maro.render.esp.Renderer3D renderer){if(hasOverlay())PulseRenderer.world(renderer,tiles,config,mc.player.getY(),System.currentTimeMillis());}
    @Override public void onRender2D(net.minecraft.client.gui.DrawContext ctx,float delta){if(!accepting()||mc.currentScreen!=null)return;if(hud.get())PulseRenderer.hud(ctx,this,true,rows.getInt());if(!discoveryReady)return;if(overlay.get()&&(labels.get()||history.get()))PulseRenderer.labels(ctx,tiles,config);PulseRenderer.guidance(ctx,discovery,config);}
}
