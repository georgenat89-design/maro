package dev.maro.module.impl.player;

import com.google.gson.*;
import dev.maro.Maro;
import dev.maro.builder.*;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.mixin.BlockItemAccessor;
import dev.maro.mixin.ClientPlayerInteractionManagerAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.setting.*;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.runtime.settings.StringSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Schematic building through vanilla interactions. World reads and actions stay on the client thread. */
public final class AutoBuilder extends Module {
    public static final byte UNKNOWN=0,CORRECT=1,MISSING=2,WRONG_BLOCK=3,WRONG_STATE=4,IGNORED=5;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"maro-schematic-io");t.setDaemon(true);return t;});
    private final LinkedHashMap<String,SettingSection> groups=new LinkedHashMap<>();
    private final ModeSetting mode=mode("Build","Build Mode","Hold Use in Semi Auto; Automatic keeps working after you start","Semi Auto","Preview Only","Semi Auto","Automatic");
    private final BooleanSetting autoMove=bool("Build","Auto Move","Walk safe ground routes toward out-of-reach blocks",true);
    private final BooleanSetting mineOut=bool("Build","Mine Out Schematic","Also clear blocks where the schematic explicitly contains air",false);
    private final BooleanSetting replaceWrong=bool("Build","Replace Wrong Blocks","Mine mismatching block types before placing the requested block",false);
    private final BooleanSetting repairStates=bool("Build","Repair Wrong States","Break and replace mismatching states when their placement can be reproduced",false);
    private final BooleanSetting protectContainers=bool("Build","Protect Containers","Never mine block entities such as chests, signs or machines",true);
    private final NumberSetting spacing=number("Build","Action Delay","Ticks between placements and inventory operations",3,0,20,1);
    private final NumberSetting reach=number("Build","Reach","Maximum vanilla interaction distance; also clamped to player reach",4.4,2,5, .1);
    private final NumberSetting turnSpeed=number("Build","Turn Speed","Maximum view rotation per tick",45,5,180,1);
    private final BooleanSetting support=bool("Build","Temporary Supports","Place dirt under floating targets when an adjacent face is reachable",true);
    private final NumberSetting tempDirt=number("Build","Temporary Dirt Limit","Maximum temporary supports placed during this build",128,0,512,1);
    private final BooleanSetting cleanup=bool("Build","Clean Temporary Supports","Remove supports created by this builder after completion",true);
    private final ModeSetting rotation=mode("Schematic","Rotation","Rotate positions and block states clockwise","0","0","90","180","270").onChange(v->replan());
    private final ModeSetting mirror=mode("Schematic","Mirror","Mirror positions and facing properties before rotation","None","None","X","Z").onChange(v->replan());
    private final BooleanSetting useOffset=bool("Schematic","Apply File Offset","Include the schematic's stored origin offset",false).onChange(v->replan());
    private final KeybindSetting markBind=bind("Restock","Mark Restock Bind","Mark or unmark the chest you are looking at",GLFW.GLFW_KEY_R);
    private final BooleanSetting restock=bool("Restock","Restock When Empty","Take missing building materials from marked containers",true);
    private final NumberSetting walkDistance=number("Restock","Restock Walk Distance","Maximum distance to a marked container",64,4,64,1);
    private final NumberSetting restockDirt=number("Restock","Restock Temp Dirt","Reserve this much dirt when restocking",128,0,512,1);
    private final BooleanSetting stockpile=bool("Restock","Stockpile In Chests","Deposit surplus whole stacks of building materials into marked chests",false);
    public final BooleanSetting showContainers=bool("Restock","Show Restock Containers","Outline marked chests",true);
    public final BooleanSetting showLabels=bool("Restock","Show Restock Labels","Show marked container coordinates on the progress panel",true);
    public final NumberSetting containerRange=number("Restock","Restock Render Distance","Visible range of marked chest outlines",64,8,128,1);
    public final NumberSetting containerAlpha=number("Restock","Restock Outline Alpha","Opacity of marked chest outlines",.85,.05,1,.05);
    public final NumberSetting labelScale=number("Restock","Restock Label Scale","Size of chest information in the progress panel",.8,.5,1.5,.05);
    private final KeybindSetting buyBind=bind("Auto Buy","Auto Buy Key","Start a material buying session",-1);
    private final BooleanSetting autoBuy=bool("Auto Buy","Auto Buy When Missing","Start buying after marked containers are exhausted",false);
    private final BooleanSetting preferStacks=bool("Auto Buy","Prefer Stacks","Prefer full stacks when their unit price is within tolerance",true);
    private final NumberSetting tolerance=number("Auto Buy","Stack Price Tolerance %","Maximum premium for a preferred stack",15,0,100,1);
    private final NumberSetting overbuy=number("Auto Buy","Max Overbuy","Maximum extra items beyond the missing quantity",16,0,64,1);
    private final NumberSetting maxItem=number("Auto Buy","Max Price Per Item","0 disables the per-item ceiling; total budget still applies",1000,0,1_000_000_000,1);
    private final NumberSetting maxSpend=number("Auto Buy","Max Total Spend","Session budget in server currency; 0 disables buying",0,0,1_000_000_000,1);
    private final NumberSetting maxPages=number("Auto Buy","Max AH Pages","Maximum auction pages checked per material",3,1,20,1);
    private final NumberSetting buySpacing=number("Auto Buy","Buy Click Spacing","Minimum ticks between menu clicks",4,2,40,1);
    private final BooleanSetting buyNotifications=bool("Auto Buy","Buy Notifications","Notify when buying starts, finishes, or stops",true);
    private final NumberSetting buyDirt=number("Auto Buy","Buy Temp Dirt","Include dirt reserve in the shopping list",64,0,512,1);
    private final ModeSetting ahSearch=mode("Auto Buy","AH Search By","How item registry names are written in the search command","Spaced","Spaced","Underscored","Registry ID");
    private final dev.maro.runtime.settings.Setting<String> ahCommand=text("Auction Menu","AH Command","Auction search command without a leading slash","ah");
    private final dev.maro.runtime.settings.Setting<String> ahTitle=text("Auction Menu","AH Title Word","Expected auction menu title","Auction");
    private final dev.maro.runtime.settings.Setting<String> priceKeyword=text("Auction Menu","Price Keyword","Marker preceding the listing's total price","$");
    private final dev.maro.runtime.settings.Setting<String> confirmYes=text("Auction Menu","Confirm Yes Word","Accepted confirmation labels, separated with ;","Confirm;Purchase;Buy;Yes");
    private final dev.maro.runtime.settings.Setting<String> confirmNo=text("Auction Menu","Confirm No Word","Rejected/cancel labels, separated with ;","Cancel;No");
    private final dev.maro.runtime.settings.Setting<String> confirmTitle=text("Auction Menu","Confirm Title Word","Recognized confirmation titles, separated with ;","Confirm;Purchase;Sure");
    private final dev.maro.runtime.settings.Setting<String> nextWord=text("Auction Menu","Next Page Word","Label on the next-page button","Next page");
    private final dev.maro.runtime.settings.Setting<String> boughtWord=text("Auction Menu","Already Bought Word","Label indicating an unavailable auction","already bought");
    public final BooleanSetting showPreview=bool("Preview & Render","Show Preview","Render the schematic while the module is enabled",true);
    public final BooleanSetting textured=bool("Preview & Render","Textured Preview","Render the actual block models and textures",true);
    public final ModeSetting previewMode=mode("Preview & Render","Preview Mode","Full blueprint or only cells which need work","Missing & Wrong","Full","Missing & Wrong","Outline Only");
    public final NumberSetting previewRange=number("Preview & Render","Preview Range","Maximum distance from the camera",48,8,96,1);
    public final NumberSetting ghostFill=number("Preview & Render","Ghost Fill","Overall textured preview opacity",.5,.05,1,.05);
    public final BooleanSetting renderCorrect=bool("Preview & Render","Render Correct","Include matching blocks",false);
    public final BooleanSetting renderMissing=bool("Preview & Render","Render Missing","Include missing blocks",true);
    public final BooleanSetting renderIncorrect=bool("Preview & Render","Render Incorrect","Include wrong block types or states",true);
    public final BooleanSetting buildBox=bool("Preview & Render","Show Build Box","Outline the schematic's transformed bounds",true);
    public final BooleanSetting originMarker=bool("Preview & Render","Show Origin Marker","Draw the placement anchor",true);
    private final ModeSetting preset=mode("Preview & Render","Preview Preset","Apply coordinated ghost and outline settings","Building","Building","Blueprint","Verification").onChange(v->applyPreset(v));
    public final ModeSetting outlineStrength=mode("Preview & Render","Outline Strength","Outline contrast","Strong","Soft","Strong","Bright");
    public final ModeSetting clarity=mode("Preview & Render","Interior Clarity","Fade ghosts near the camera to keep the view clear","Fade Near Camera","None","Fade Near Camera","Surface Only");
    public final NumberSetting fadeRadius=number("Preview & Render","Interior Fade Radius","Radius of camera-adjacent fade",2,.5,8,.25);
    public final BooleanSetting throughWalls=bool("Preview & Render","Missing Through Walls","Show a faint outline for hidden missing blocks",false);
    public final NumberSetting missingOpacity=number("Preview & Render","Missing Opacity","Colored fill for missing blocks",.14,0,1,.01);
    public final NumberSetting wrongOpacity=number("Preview & Render","Wrong Block Opacity","Colored fill for wrong block types",.2,0,1,.01);
    public final NumberSetting stateOpacity=number("Preview & Render","Wrong State Opacity","Colored fill for wrong block states",.18,0,1,.01);
    public final NumberSetting occludedOpacity=number("Preview & Render","Occluded Opacity","Faint outline opacity when through-walls is enabled",.07,0,.5,.01);
    public final BooleanSetting outlines=bool("Preview & Render","Outlines","Outline individual cells",true);
    public final NumberSetting outlineWidth=number("Preview & Render","Outline Width","Line width in pixels",1,.5,3,.25);
    public final ModeSetting layerMode=mode("Preview & Render","Layer Mode","Preview and build every layer or the selected height","All","All","Single","Below");
    public final NumberSetting layer=number("Preview & Render","Layer","Local Y level for Single / Below modes",0,0,2047,1);
    private final NumberSetting budget=number("Preview & Render","Rebuild Budget","Maximum scan time per client tick in milliseconds",2,.25,8,.25);
    public final NumberSetting renderLimit=number("Preview & Render","Max Preview Blocks","Hard cap on rendered cells per frame",1500,100,6000,100);
    private final BooleanSetting stopStaff=bool("Safety","Stop On Staff Nearby","Pause for loaded players whose names are in your staff list",true);
    private final NumberSetting staffRange=number("Safety","Staff Detect Range","Distance to a configured staff player",48,4,96,1);
    private final BooleanSetting logoff=bool("Safety","Log Off After Staff Stop","Disconnect after the selected delay; off by default",false);
    private final NumberSetting logoffDelay=number("Safety","Logoff Delay","Seconds after staff proximity stop",5,0,30,.5).visible(logoff::get);
    private final dev.maro.runtime.settings.Setting<String> logoffMessage=text("Safety","Logoff Message","Local disconnect reason","Auto Builder stopped — staff nearby");
    private final NumberSetting minHealth=number("Safety","Minimum Health","Pause building below this health in hearts",4,0,10,.5);
    private final BooleanSetting pausePlayers=bool("Multiplayer","Pause Near Players","Pause automatic actions for other nearby players",false);
    private final NumberSetting playerRange=number("Multiplayer","Player Pause Range","Radius of the optional player pause",8,2,32,1);
    private final BooleanSetting statusHud=bool("Build","Progress HUD","Show progress, materials and the current task",true);
    private final NumberSetting captureX=number("Capture","Capture Width","X size of the snapshot, beginning at the origin",16,1,64,1);
    private final NumberSetting captureY=number("Capture","Capture Height","Y size of the snapshot",8,1,64,1);
    private final NumberSetting captureZ=number("Capture","Capture Length","Z size of the snapshot",16,1,64,1);
    private Schematic schematic;
    private BlockPos origin;
    private ClientWorld world;
    private String dimension="",selected="",status="Choose a schematic";
    private byte[] states=new byte[0];
    private byte[] unitsLeft=new byte[0];
    private int scanCursor,correct,solid,completedScans,ticks,delay,originalSlot=-1,ioGeneration,passTasks,lastPassTasks,lastAction;
    private boolean loading,building,preview=true,ownsSneak;
    private final Map<Item,Integer> remaining=new HashMap<>();
    private final Set<BlockPos> containers=new LinkedHashSet<>(),supports=new LinkedHashSet<>();
    private final Set<BlockPos> triedContainers=new HashSet<>();
    private final Set<Integer> restockTriedSlots=new HashSet<>();
    private final Map<Integer,Integer> retryAt=new HashMap<>();
    private final Map<Integer,Set<BlockPos>> triedStands=new HashMap<>();
    private BlockPos standGoal;
    private int standIndex,standStarted;
    private final IdentityHashMap<BlockState,BlockState> transformedStates=new IdentityHashMap<>();
    private final BuilderWalk walker=new BuilderWalk();
    private List<Integer> visible=List.of();
    private List<Integer> workCells=List.of();
    private final PriorityQueue<Visible> visibleScan=new PriorityQueue<>(Comparator.comparingDouble(Visible::distance).reversed());
    private final PriorityQueue<Visible> workScan=new PriorityQueue<>(Comparator.comparingDouble(Visible::distance).reversed());
    private record Visible(int index,double distance){}
    private record Place(BlockPos target,BlockState state,BlockHitResult hit,Item item,int index,boolean temporary){}
    private Place placement;
    private BlockPos mining,restockTarget;
    private Item needed;
    private int restockWait,inventoryWait;
    private long staffStopAt;
    private ScreenHandler ownedHandler;
    private static boolean digging;
    private Schematic capture;
    private BlockState[] captureStates;
    private BlockPos captureOrigin;
    private int captureCursor;
    private int capturedWidth,capturedHeight,capturedLength;
    private boolean pasting;
    private int pasteCursor;
    private final LinkedHashMap<Item,Integer> shopping=new LinkedHashMap<>();
    private boolean buying,estimating;
    private boolean resumeAfterMarket;
    private String worldScope="";
    private Item buyingItem;
    private int marketPage,marketWait,marketStage,marketSync=-1,inventoryBefore;
    private double spent,estimate;
    private AuctionMarket.Offer pendingOffer;
    private final Set<String> boughtListings=new HashSet<>();

    public AutoBuilder(){
        super("Auto Builder","Load, preview and build schematics with materials, chest restocking and auction buying",Category.PLAYER);
        button("Files","Open Folder","Open the schematic folder","Open",()->net.minecraft.util.Util.getOperatingSystem().open(folder().toFile()));
        button("Files","Choose Schematic","Choose a .schem, .schematic, .litematic or .nbt file","Choose",()->mc.setScreen(new BuilderScreen(mc.currentScreen,this,false)));
        button("Files","Refresh","Refresh the file list and reload the current file","Refresh",()->{if(!selected.isEmpty())load(folder().resolve(selected));else mc.setScreen(new BuilderScreen(mc.currentScreen,this,false));});
        button("Files","Show Materials","Show total, remaining and inventory counts","Materials",()->mc.setScreen(new BuilderScreen(mc.currentScreen,this,true)));
        button("Files","Creative Materials Get","Fill empty inventory slots with needed materials in creative","Get Materials",this::creativeMaterials);
        button("Files","Paste Schematic","Paste block states using setblock commands; needs creative and server permission","Paste",this::startPaste);
        button("Files","Preview Schematic","Show the blueprint without starting actions","Preview",this::preview);
        button("Files","Cancel Preview","Hide ghosts and stop building","Cancel",()->{preview=false;pause("Preview hidden");});
        button("Files","Start / Resume Build","Start using the selected Build Mode","Build",this::startBuild);
        button("Files","Pause Build","Keep the preview visible and release all inputs","Pause",()->pause("Paused"));
        button("Capture","Capture Snapshot","Save the configured area from the placement origin to a vanilla .nbt file","Capture",this::startCapture);
        button("Schematic","Origin: Player","Anchor at your feet","Set to Player",()->setOrigin(mc.player==null?null:mc.player.getBlockPos()));
        button("Schematic","Origin: Looking","Anchor at the exposed side of the target block","Set to Target",()->{if(mc.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK)setOrigin(hit.getBlockPos().offset(hit.getSide()));});
        for(var dir:Direction.values())button("Schematic","Nudge "+switch(dir){case EAST->"X+";case WEST->"X-";case UP->"Y+";case DOWN->"Y-";case SOUTH->"Z+";case NORTH->"Z-";},"Move the schematic one block",dir.asString(),()->{if(origin!=null)setOrigin(origin.offset(dir));});
        button("Restock","Mark Restock Container","Mark or unmark the container you are looking at","Mark",this::markContainer);
        button("Restock","Clear Restock Marks","Clear this world's marked containers","Clear",()->{containers.clear();triedContainers.clear();});
        button("Auto Buy","Buy Materials","Buy missing materials within your configured budget","Buy",()->startBuying(false));
        button("Auto Buy","Estimate Cost","Read current auction listings without buying","Estimate",()->startBuying(true));
        button("Auto Buy","Cancel Buying","Stop the shopping session","Cancel",()->finishBuying("Buying cancelled"));
        ClientTickEvents.START_CLIENT_TICK.register(client->{digging=false;if(isEnabled())tickWork();});
    }
    private <S extends Setting<?>> S setting(String group,S value){add(value);groups.computeIfAbsent(group,SettingSection::new).add(value);return value;}
    private BooleanSetting bool(String g,String n,String d,boolean v){return setting(g,new BooleanSetting(n,d,v));}
    private NumberSetting number(String g,String n,String d,double v,double min,double max,double step){return setting(g,new NumberSetting(n,d,v,min,max,step));}
    private ModeSetting mode(String g,String n,String d,String v,String... choices){return setting(g,new ModeSetting(n,d,v,choices));}
    private KeybindSetting bind(String g,String n,String d,int v){return setting(g,new KeybindSetting(n,d,v));}
    private void button(String g,String n,String d,String label,Runnable action){setting(g,new ButtonSetting(n,d,label,action));}
    private dev.maro.runtime.settings.Setting<String> text(String g,String n,String d,String value){
        var nativeSetting=new StringSetting.Builder().name(n).description(d).defaultValue(value).build();setting(g,SettingAdapters.adapt(nativeSetting));return nativeSetting;
    }
    @Override public List<SettingSection> getSettingSections(){
        List<SettingSection> result=new ArrayList<>();if(groups.containsKey("Files"))result.add(groups.get("Files"));
        groups.forEach((name,section)->{if(!name.equals("Files"))result.add(section);});return result;
    }
    public static boolean holdingBreak(){return digging;}
    public static boolean consumesUse(){var module=ModuleManager.get(AutoBuilder.class);return module!=null&&module.isEnabled()&&module.building&&module.mode.is("Semi Auto")&&mc.currentScreen==null;}
    public Path folder(){
        Path path=FabricLoader.getInstance().getGameDir().resolve("schematics");
        try{Files.createDirectories(path);}catch(IOException e){notify("Cannot create schematic folder: "+e.getMessage());}return path;
    }
    public void load(Path file){
        if(!SchematicIO.supported(file)){notify("Choose a supported schematic file");return;}
        pause("Loading "+file.getFileName());loading=true;int generation=++ioGeneration;
        CompletableFuture.supplyAsync(()->{try{return SchematicIO.read(file);}catch(IOException e){throw new CompletionException(e);}},IO)
            .whenComplete((data,error)->mc.execute(()->{
                if(generation!=ioGeneration)return;loading=false;
                if(error!=null){notify("Could not load: "+rootMessage(error));status="Load failed";return;}
                selected=file.toAbsolutePath().normalize().startsWith(folder().toAbsolutePath().normalize())?folder().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString():file.toAbsolutePath().toString();
                install(data);status=data.name+" · "+data.width+" × "+data.height+" × "+data.length;
                preview=true;if(inGame()&&origin==null)setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));
            }));
    }
    private static String rootMessage(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    public void install(Schematic data){pause("Schematic loaded");schematic=data;supports.clear();replan();}
    public Schematic schematic(){return schematic;}
    public String status(){return status;}
    public boolean loading(){return loading;}
    public boolean building(){return building;}
    public boolean buying(){return buying;}
    public double sessionSpend(){return spent;}
    public boolean previewVisible(){return isEnabled()&&preview&&showPreview.get()&&schematic!=null&&origin!=null&&world==mc.world;}
    public BlockPos origin(){return origin;}
    public int turns(){return rotation.index();}
    public BlockPos position(int index){return anchor().add(schematic.transformed(index,turns(),mirror.get()));}
    public BlockState desired(int index){return transformedStates.computeIfAbsent(schematic.state(index),state->schematic.transformedState(index,turns(),mirror.get()));}
    public byte state(int index){return states[index];}
    public List<Integer> visibleCells(){return visible;}
    public Set<BlockPos> restockContainers(){return Collections.unmodifiableSet(containers);}
    public BlockPos anchor(){return useOffset.get()?origin.add(schematic.offset):origin;}
    public boolean layerAllows(int index){int y=schematic.local(index).getY();return layerMode.is("All")||layerMode.is("Single")&&y==layer.getInt()||layerMode.is("Below")&&y<=layer.getInt();}
    public boolean showCell(int index){
        if(!layerAllows(index))return false;byte s=states[index];
        return s==CORRECT&&(renderCorrect.get()||previewMode.is("Full"))||s==MISSING&&renderMissing.get()||(s==WRONG_BLOCK||s==WRONG_STATE)&&renderIncorrect.get()||s==UNKNOWN&&!desired(index).isAir();
    }
    public Map<Item,Integer> remainingMaterials(){return Map.copyOf(remaining);}
    public int inventoryCount(Item item){if(mc.player==null)return 0;int count=0;for(int i=0;i<36;i++){var stack=mc.player.getInventory().getStack(i);if(stack.isOf(item))count+=stack.getCount();}return count;}
    public void setOrigin(BlockPos pos){
        if(pos==null||!inGame())return;pause("Origin moved");
        String nextDimension=mc.world.getRegistryKey().getValue().toString();
        String nextScope=scope();
        if(!dimension.isEmpty()&&(!dimension.equals(nextDimension)||!worldScope.equals(nextScope))){containers.clear();supports.clear();}
        worldScope=nextScope;
        origin=pos.toImmutable();world=mc.world;dimension=nextDimension;replan();
    }
    private void replan(){
        if(schematic==null)return;pause("Placement changed");transformedStates.clear();triedStands.clear();states=new byte[schematic.size()];unitsLeft=new byte[schematic.size()];scanCursor=correct=completedScans=passTasks=0;lastPassTasks=schematic.size();solid=schematic.solidCount();remaining.clear();remaining.putAll(schematic.materials());visible=workCells=List.of();visibleScan.clear();workScan.clear();retryAt.clear();triedContainers.clear();
    }
    private void applyPreset(String name){
        if(ghostFill==null)return;
        previewMode.set(name.equals("Blueprint")?"Full":"Missing & Wrong");textured.set(!name.equals("Verification"));renderCorrect.set(name.equals("Blueprint"));ghostFill.set(name.equals("Blueprint")?.35:.5);outlines.set(true);
    }
    public void preview(){if(schematic==null){notify("Choose a schematic first");return;}if(origin==null&&inGame())setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));setEnabled(true);preview=true;pause("Previewing "+schematic.name);}
    public void startBuild(){
        if(!inGame()||schematic==null||loading){notify("Join a world and load a schematic first");return;}
        if(world==null&&origin!=null&&dimension.equals(mc.world.getRegistryKey().getValue().toString())&&worldScope.equals(scope()))world=mc.world;
        if(origin==null||world!=mc.world)setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));
        if(mode.is("Preview Only")){preview();return;}
        var miner=ModuleManager.get(AutoMine.class);if(miner!=null&&miner.isEnabled())miner.setEnabled(false);
        setEnabled(true);building=true;preview=true;staffStopAt=0;triedContainers.clear();retryAt.clear();status=mode.is("Semi Auto")?"Hold right mouse to build":"Building";
    }
    @Override protected void onEnable(){
        if(!inGame()){setEnabled(false);return;}
        originalSlot=mc.player.getInventory().getSelectedSlot();
        if(world==null&&origin!=null&&dimension.equals(mc.world.getRegistryKey().getValue().toString())&&worldScope.equals(scope()))world=mc.world;
        if(origin==null||world!=mc.world)setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));
        building=schematic!=null&&!mode.is("Preview Only");preview=true;
    }
    @Override protected void onDisable(){pause("Disabled");staffStopAt=0;captureStates=null;if(mc.player!=null&&originalSlot>=0)select(originalSlot);originalSlot=-1;}
    public void pause(String reason){
        building=false;pasting=false;placement=null;mining=null;standGoal=null;digging=false;walker.stop();releaseSneak();
        if(mc.interactionManager!=null)mc.interactionManager.cancelBlockBreaking();
        if(ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();
        ownedHandler=null;restockTarget=null;buying=false;pendingOffer=null;shopping.clear();status=reason;
    }
    private void notify(String message){Notifications.push("Auto Builder",message,Notifications.Type.INFO,5000);if(mc.player!=null)mc.player.sendMessage(Text.literal("[Auto Builder] "+message),false);}
    public void onActionBind(int key){if(!isEnabled())return;if(markBind.matches(key))markContainer();if(buyBind.matches(key))startBuying(false);}
    private void markContainer(){
        if(!inGame()||!(mc.crosshairTarget instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return;
        BlockPos pos=hit.getBlockPos();var block=mc.world.getBlockState(pos).getBlock();
        if(!(block instanceof AbstractChestBlock<?>||block instanceof BarrelBlock||block instanceof ShulkerBoxBlock||block instanceof HopperBlock)){notify("Look at a chest, barrel, shulker or hopper");return;}
        if(world!=mc.world)setOrigin(mc.player.getBlockPos());
        if(!containers.remove(pos))containers.add(pos.toImmutable());triedContainers.clear();notify("Restock containers: "+containers.size());
    }
    private void tickWork(){
        ticks++;if(delay>0)delay--;
        if(!inGame()||world!=mc.world){pause("World changed — set the origin again");world=null;return;}
        if(!mc.player.isAlive()||mc.player.isSpectator()){pause("Player is not able to build");return;}
        scan();captureTick();
        if(staffStopAt>0){if(logoff.get()&&System.currentTimeMillis()-staffStopAt>=logoffDelay.get()*1000){mc.world.disconnect(Text.literal(logoffMessage.get()));setEnabled(false);}return;}
        if((building||buying||pasting)&&unsafe()){walker.release();return;}
        if(buying){marketTick();return;}
        if(pasting){pasteTick();return;}
        if(!building||schematic==null||origin==null||loading)return;
        if(restockTarget!=null){restockTick();return;}
        if(mc.currentScreen!=null){walker.release();digging=false;return;}
        if(mode.is("Semi Auto")&&!mc.options.useKey.isPressed()){walker.release();digging=false;status="Hold right mouse to build";return;}
        if(mc.player.isUsingItem()){walker.release();return;}
        if(delay>0){walker.release();return;}
        if(placement!=null){placeTick();return;}
        if(mining!=null){mineTick();return;}
        if(standGoal!=null){
            if(walker.standAt(standGoal)||ticks-standStarted>100){standGoal=null;walker.stop();}
            else status=walker.status;return;
        }
        findWork();
    }
    private boolean unsafe(){
        if(stopStaff.get()){
            StaffNotifier staff=ModuleManager.get(StaffNotifier.class);
            for(var player:mc.world.getPlayers())if(player!=mc.player&&player.squaredDistanceTo(mc.player)<=staffRange.get()*staffRange.get()
                &&(staff!=null?staff.isStaffName(player.getGameProfile().name()):StaffNotifier.DEFAULT_STAFF.stream().anyMatch(n->n.equalsIgnoreCase(player.getGameProfile().name())))){
                pause("Staff nearby: "+player.getName().getString());staffStopAt=System.currentTimeMillis();notify(status);return true;
            }
        }
        if(mc.player.getHealth()<minHealth.get()*2){status="Paused — low health";return true;}
        if(pausePlayers.get()&&mc.world.getPlayers().stream().anyMatch(p->p!=mc.player&&p.squaredDistanceTo(mc.player)<playerRange.get()*playerRange.get())){status="Paused — player nearby";return true;}
        return false;
    }
    private void scan(){
        if(schematic==null||origin==null||states.length!=schematic.size())return;
        long deadline=System.nanoTime()+(long)(budget.get()*1_000_000);int count=0;
        Vec3d camera=mc.gameRenderer.getCamera().getCameraPos();
        do{
            int i=scanCursor++;updateState(i);BlockState expected=desired(i);
            double distance=Vec3d.ofCenter(position(i)).squaredDistanceTo(camera);
            if(layerAllows(i)&&schematic.included(i)&&(!expected.isAir()||mineOut.get())&&states[i]!=CORRECT){
                passTasks++;var candidate=new Visible(i,distance+schematic.local(i).getY()*.3);
                if(workScan.size()<256)workScan.add(candidate);else if(candidate.distance<workScan.peek().distance){workScan.poll();workScan.add(candidate);}
            }
            if(!expected.isAir()&&!expected.isOf(Blocks.STRUCTURE_VOID)&&distance<=previewRange.get()*previewRange.get()&&layerAllows(i)){
                var cell=new Visible(i,distance);int limit=renderLimit.getInt();
                if(visibleScan.size()<limit)visibleScan.add(cell);else if(distance<visibleScan.peek().distance){visibleScan.poll();visibleScan.add(cell);}
            }
            if(scanCursor==states.length){
                scanCursor=0;completedScans++;lastPassTasks=passTasks;passTasks=0;
                visible=visibleScan.stream().sorted(Comparator.comparingDouble(Visible::distance).reversed()).map(Visible::index).toList();visibleScan.clear();
                workCells=workScan.stream().sorted(Comparator.comparingDouble(Visible::distance)).map(Visible::index).toList();workScan.clear();
            }
        }while(++count<8192&&System.nanoTime()<deadline&&scanCursor!=0);
    }
    private byte classify(int i){
        var wanted=desired(i);if(wanted.isOf(Blocks.STRUCTURE_VOID))return IGNORED;
        var pos=position(i);if(!mc.world.isChunkLoaded(pos))return UNKNOWN;
        var actual=mc.world.getBlockState(pos);
        return wanted.equals(actual)||wanted.isAir()&&actual.isAir()?CORRECT:actual.isAir()||actual.isReplaceable()?MISSING:actual.getBlock()==wanted.getBlock()?WRONG_STATE:WRONG_BLOCK;
    }
    private void updateState(int index){
        byte next=classify(index),old=states[index];
        var expected=desired(index);if(!expected.isAir()&&!expected.isOf(Blocks.STRUCTURE_VOID)){
            int change=(next==CORRECT?1:0)-(old==CORRECT?1:0);correct+=change;
            var item=Schematic.material(expected);int oldUnits=old==UNKNOWN?Schematic.units(expected):unitsLeft[index],newUnits=next==CORRECT?0:Schematic.units(expected);
            if(newUnits==2&&mc.world.isChunkLoaded(position(index))){var actual=mc.world.getBlockState(position(index));if(actual.getBlock()==expected.getBlock()&&actual.contains(net.minecraft.state.property.Properties.SLAB_TYPE)&&actual.get(net.minecraft.state.property.Properties.SLAB_TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)newUnits=1;}
            unitsLeft[index]=(byte)newUnits;int delta=oldUnits-newUnits;
            if(item!=Items.AIR&&delta!=0)remaining.compute(item,(k,v)->Math.max(0,(v==null?0:v)-delta));
        }
        states[index]=next;
    }
    private double effectiveReach(){return Math.min(reach.get(),mc.player.getBlockInteractionRange()-.1);}
    private void findWork(){
        if(completedScans==0){status="Checking schematic: "+(100L*scanCursor/Math.max(1,states.length))+"%";return;}
        if((lastPassTasks==0||correct==solid&&!supports.isEmpty())&&ticks-lastAction>=20){
            if(cleanup.get()&&!supports.isEmpty()){
                var pos=supports.iterator().next();
                if(mc.world.getBlockState(pos).isOf(Blocks.DIRT)&&!new Box(pos).intersects(mc.player.getBoundingBox())){mining=pos;mineTick();return;}
                supports.remove(pos);return;
            }
            pause("Build complete");notify("Build complete: "+solid+" blocks");return;
        }
        List<Integer> candidates=new ArrayList<>();
        // A local neighborhood is cheap even for multi-million-cell schematics; global nearest cells
        // from the scan are appended for walking. A candidate's actual world state is rechecked below.
        for(int i:workCells)if(layerAllows(i)&&states[i]!=CORRECT&&states[i]!=IGNORED&&retryAt.getOrDefault(i,0)<=ticks)candidates.add(i);
        candidates.sort(Comparator.comparingDouble(i->schematic.local(i).getY()*100+position(i).getSquaredDistance(mc.player.getBlockPos())));
        needed=null;Integer distant=null,blocked=null;
        long deadline=System.nanoTime()+2_000_000;int checked=0;
        for(int i:candidates){
            if(checked++>=96||System.nanoTime()>deadline)break;
            updateState(i);if(states[i]==CORRECT||states[i]==UNKNOWN)continue;
            var target=position(i);var desired=desired(i);var actual=mc.world.getBlockState(target);
            if(desired.isAir()&&supports.contains(target)&&correct!=solid)continue;
            if(desired.isAir()&&!mineOut.get()||Schematic.companion(desired))continue;
            if(mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target))>effectiveReach()*effectiveReach()){if(distant==null)distant=i;continue;}
            if(desired.isAir()&&mineOut.get()){
                if(!actual.getFluidState().isEmpty()||protectContainers.get()&&actual.hasBlockEntity()||actual.getHardness(mc.world,target)<0){retryAt.put(i,ticks+200);continue;}
                mining=target;mineTick();return;
            }
            if(!actual.isReplaceable()&&!actual.isAir()&&!(actual.getBlock()==desired.getBlock()&&desired.contains(net.minecraft.state.property.Properties.SLAB_TYPE)&&desired.get(net.minecraft.state.property.Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE)){
                if(!mineOut.get()&&!replaceWrong.get()&&!(states[i]==WRONG_STATE&&repairStates.get()))continue;
                if(protectContainers.get()&&actual.hasBlockEntity()||actual.getHardness(mc.world,target)<0)continue;
                mining=target;mineTick();return;
            }
            if(desired.isAir())continue;
            var item=Schematic.material(desired);if(item==Items.AIR){retryAt.put(i,ticks+200);continue;}
            if(inventoryCount(item)==0){if(needed==null)needed=item;continue;}
            var plan=placement(target,desired,item,i,false);
            if(plan!=null){placement=plan;placeTick();return;}
            if(support.get()&&supports.size()<tempDirt.getInt()&&inventoryCount(Items.DIRT)>0)for(var direction:Direction.values()){
                var supportPos=target.offset(direction);int local=schematic.indexAt(supportPos.subtract(anchor()),turns(),mirror.get());
                if(local>=0&&!desired(local).isAir()&&!desired(local).isOf(Blocks.STRUCTURE_VOID))continue;
                if(!mc.world.getBlockState(supportPos).isReplaceable()||new Box(supportPos).intersects(mc.player.getBoundingBox()))continue;
                var scaffold=placement(supportPos,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true);
                if(scaffold!=null){placement=scaffold;placeTick();return;}
            }
            if(blocked==null)blocked=i;
        }
        if(needed!=null){if(restock.get()&&beginRestock())return;if(autoBuy.get()){startBuying(false);return;}status="Missing "+needed.getName().getString()+" — check Materials";walker.release();return;}
        if(blocked!=null&&autoMove.get()&&reposition(blocked))return;
        if(distant!=null&&autoMove.get()){
            var target=position(distant);if(!walker.approach(target,effectiveReach()-.75)){status=walker.status;return;}
        }else walker.release();
        status=lastPassTasks==0?"Verifying completed build":"No placeable target — move closer, add supports, or inspect wrong states";
    }
    private boolean reposition(int index){
        var target=position(index);Set<BlockPos> tried=triedStands.computeIfAbsent(index,i->new HashSet<>());
        List<BlockPos> options=new ArrayList<>();
        for(var side:Direction.Type.HORIZONTAL)for(int distance:new int[]{2,3})for(int dy:new int[]{-2,-1,0,1}){
            var stand=target.offset(side,distance).up(dy);
            if(tried.contains(stand)||!walker.canStand(stand)||mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(stand))<.5)continue;
            Vec3d eye=Vec3d.ofBottomCenter(stand).add(0,mc.player.getStandingEyeHeight(),0);
            if(eye.squaredDistanceTo(Vec3d.ofCenter(target))>effectiveReach()*effectiveReach())continue;
            var hit=mc.world.raycast(new RaycastContext(eye,Vec3d.ofCenter(target),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK&&!hit.getBlockPos().equals(target))continue;
            options.add(stand);
        }
        standGoal=options.stream().min(Comparator.comparingDouble(p->p.getSquaredDistance(mc.player.getBlockPos()))).orElse(null);
        if(standGoal==null){retryAt.put(index,ticks+60);return false;}
        tried.add(standGoal);standIndex=index;standStarted=ticks;walker.stop();status="Moving around an obstructed block";return true;
    }
    private Place placement(BlockPos target,BlockState wanted,Item item,int index,boolean temporary){
        if(!(item instanceof BlockItem blockItem)||new Box(target).intersects(mc.player.getBoundingBox())&&!wanted.getCollisionShape(mc.world,target).isEmpty())return null;
        ItemStack stack=new ItemStack(item);double range=effectiveReach();float oldYaw=mc.player.getYaw(),oldPitch=mc.player.getPitch();
        try{
            for(int direct=0;direct<2;direct++)for(var side:Direction.values()){
                BlockPos neighbor=direct==1?target:target.offset(side.getOpposite());var supportState=mc.world.getBlockState(neighbor);
                if(direct==1&&supportState.getBlock()!=wanted.getBlock())continue;
                if(supportState.isAir()||supportState.isReplaceable()||!supportState.getFluidState().isEmpty())continue;
                for(double height:new double[]{.5,.25,.75}){
                    Vec3d point=Vec3d.ofCenter(neighbor).add(side.getOffsetX()*.5,side.getOffsetY()*.5,side.getOffsetZ()*.5);
                    if(side.getAxis()!=Direction.Axis.Y)point=new Vec3d(point.x,neighbor.getY()+height,point.z);
                    if(direct==1&&side==Direction.UP&&!supportState.getOutlineShape(mc.world,neighbor).isEmpty())point=new Vec3d(point.x,neighbor.getY()+supportState.getOutlineShape(mc.world,neighbor).getMax(Direction.Axis.Y),point.z);
                    if(mc.player.getEyePos().squaredDistanceTo(point)>range*range)continue;
                    var ray=mc.world.raycast(new RaycastContext(mc.player.getEyePos(),point.add(Vec3d.of(side.getVector()).multiply(-.002)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
                    if(ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(neighbor)||ray.getSide()!=side)continue;
                    float[] angles=angles(point);mc.player.setYaw(angles[0]);mc.player.setPitch(angles[1]);
                    var hit=new BlockHitResult(point,side,neighbor,false);var context=new ItemPlacementContext(mc.player,Hand.MAIN_HAND,stack,hit);
                    context=blockItem.getPlacementContext(context);if(context==null)continue;
                    BlockState predicted=((BlockItemAccessor)(Object)blockItem).maro$placementState(context);
                    if(predicted==null||!context.getBlockPos().equals(target)||!compatible(predicted,wanted))continue;
                    return new Place(target,wanted,hit,item,index,temporary);
                }
            }
        }finally{mc.player.setYaw(oldYaw);mc.player.setPitch(oldPitch);}
        return null;
    }
    /** Neighbour-derived connections and waterlogging are verified after placement, not forged. */
    public static boolean compatible(BlockState predicted,BlockState desired){
        if(predicted==null||predicted.getBlock()!=desired.getBlock())return false;
        for(var property:desired.getProperties()){
            String name=property.getName();
            if(Set.of("shape","north","south","east","west","up","down","waterlogged","powered","lit","distance","persistent").contains(name)||name.equals("type")&&desired.getBlock() instanceof AbstractChestBlock<?>)continue;
            if(property==net.minecraft.state.property.Properties.SLAB_TYPE&&desired.get(net.minecraft.state.property.Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE)continue;
            if(!predicted.get(property).equals(desired.get(property)))return false;
        }
        return true;
    }
    private void placeTick(){
        walker.release();Place job=placement;
        if(job.index>=0){updateState(job.index);if(states[job.index]==CORRECT){placement=null;return;}}
        if(inventoryCount(job.item)==0){placement=null;return;}
        if(!selectMaterial(job.item)){status=delay>0?"Moving material to hotbar":"Material unavailable";placement=null;return;}
        if(clickable(mc.world.getBlockState(job.hit.getBlockPos()).getBlock())&&!mc.player.isSneaking()){
            ownsSneak=true;mc.options.sneakKey.setPressed(true);status="Sneaking to place";return;
        }
        if(!aim(job.hit.getPos())){status="Aiming";return;}
        var aimed=(BlockHitResult)mc.player.raycast(effectiveReach(),0,false);
        if(aimed.getType()!=HitResult.Type.BLOCK||!aimed.getBlockPos().equals(job.hit.getBlockPos())||aimed.getSide()!=job.hit.getSide()){
            if(job.index>=0)retryAt.put(job.index,ticks+20);placement=null;releaseSneak();return;
        }
        var context=((BlockItem)job.item).getPlacementContext(new ItemPlacementContext(mc.player,Hand.MAIN_HAND,mc.player.getMainHandStack(),aimed));
        if(context==null||!context.getBlockPos().equals(job.target)||!compatible(((BlockItemAccessor)(Object)job.item).maro$placementState(context),job.state)){placement=null;releaseSneak();return;}
        if(mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,aimed).isAccepted()){
            mc.player.swingHand(Hand.MAIN_HAND);delay=Math.max(2,spacing.getInt());lastAction=ticks;
            if(job.temporary)supports.add(job.target);status=job.temporary?"Placing temporary support":"Placing "+job.item.getName().getString();
        }else if(job.index>=0)retryAt.put(job.index,ticks+40);
        placement=null;releaseSneak();
    }
    private void mineTick(){
        walker.release();if(mining==null)return;
        var state=mc.world.getBlockState(mining);
        if(state.isAir()){supports.remove(mining);mining=null;digging=false;mc.interactionManager.cancelBlockBreaking();delay=spacing.getInt();return;}
        if(protectContainers.get()&&state.hasBlockEntity()||state.getHardness(mc.world,mining)<0||!state.getFluidState().isEmpty()){mining=null;return;}
        if(new Box(mining).intersects(mc.player.getBoundingBox().offset(0,-1,0))){status="Move off the block before clearing it";mining=null;return;}
        var visibleHit=visibleHit(mining);
        if(visibleHit==null){status="Mining target is obstructed";mining=null;return;}
        if(!aim(visibleHit.getPos())){status="Aiming to mine";return;}
        var hit=(BlockHitResult)mc.player.raycast(effectiveReach(),0,false);
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(mining)){status="Mining target is obstructed";mining=null;return;}
        int best=mc.player.getInventory().getSelectedSlot();float speed=0;
        for(int i=0;i<9;i++){var stack=mc.player.getInventory().getStack(i);float candidate=stack.getMiningSpeedMultiplier(state);if(candidate>speed){best=i;speed=candidate;}}
        select(best);digging=true;mc.interactionManager.updateBlockBreakingProgress(mining,hit.getSide());mc.player.swingHand(Hand.MAIN_HAND);lastAction=ticks;status="Clearing mismatching block";
    }
    private BlockHitResult visibleHit(BlockPos pos){
        var shape=mc.world.getBlockState(pos).getOutlineShape(mc.world,pos);if(shape.isEmpty())return null;
        var bounds=shape.getBoundingBox();
        for(var face:Direction.values()){
            double x=(bounds.minX+bounds.maxX)/2,y=(bounds.minY+bounds.maxY)/2,z=(bounds.minZ+bounds.maxZ)/2;
            switch(face){case UP->y=bounds.maxY;case DOWN->y=bounds.minY;case NORTH->z=bounds.minZ;case SOUTH->z=bounds.maxZ;case EAST->x=bounds.maxX;case WEST->x=bounds.minX;}
            Vec3d point=new Vec3d(pos.getX()+x,pos.getY()+y,pos.getZ()+z);
            if(point.squaredDistanceTo(mc.player.getEyePos())>effectiveReach()*effectiveReach())continue;
            var hit=mc.world.raycast(new RaycastContext(mc.player.getEyePos(),point.add(Vec3d.of(face.getVector()).multiply(-.002)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos))return hit;
        }return null;
    }
    private float[] angles(Vec3d point){var delta=point.subtract(mc.player.getEyePos());return new float[]{(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)))};}
    private boolean aim(Vec3d point){
        float[] goal=angles(point);float yaw=MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()),pitch=goal[1]-mc.player.getPitch();
        mc.player.setYaw(mc.player.getYaw()+MathHelper.clamp(yaw,-turnSpeed.getFloat(),turnSpeed.getFloat()));mc.player.setPitch(MathHelper.clamp(mc.player.getPitch()+MathHelper.clamp(pitch,-turnSpeed.getFloat(),turnSpeed.getFloat()),-90,90));
        return Math.abs(yaw)<1&&Math.abs(pitch)<1;
    }
    private void select(int slot){if(mc.player==null||mc.interactionManager==null)return;if(mc.player.getInventory().getSelectedSlot()!=slot){mc.player.getInventory().setSelectedSlot(slot);((ClientPlayerInteractionManagerAccessor)mc.interactionManager).maro$syncSelectedSlot();}}
    private boolean selectMaterial(Item item){
        for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isOf(item)){select(i);return true;}
        int source=-1,dest=-1;for(int i=9;i<36;i++)if(mc.player.getInventory().getStack(i).isOf(item)){source=i;break;}
        for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isEmpty()){dest=i;break;}
        if(dest<0)dest=mc.player.getInventory().getSelectedSlot();
        if(source<0||mc.player.currentScreenHandler!=mc.player.playerScreenHandler||!mc.player.currentScreenHandler.getCursorStack().isEmpty())return false;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,source,dest,SlotActionType.SWAP,mc.player);select(dest);delay=Math.max(2,spacing.getInt());return false;
    }
    private static boolean clickable(Block block){return dev.maro.runtime.utils.world.BlockUtils.isClickable(block);}
    private void releaseSneak(){if(ownsSneak){mc.options.sneakKey.setPressed(false);ownsSneak=false;}}
    private boolean beginRestock(){
        restockTarget=containers.stream().filter(p->!triedContainers.contains(p)&&p.getSquaredDistance(mc.player.getBlockPos())<=walkDistance.get()*walkDistance.get()&&mc.world.isChunkLoaded(p))
            .min(Comparator.comparingDouble(p->p.getSquaredDistance(mc.player.getBlockPos()))).orElse(null);
        if(restockTarget==null)return false;restockWait=inventoryWait=0;restockTriedSlots.clear();walker.stop();status="Restocking";return true;
    }
    private void restockTick(){
        if(ownedHandler!=null){
            if(!(mc.currentScreen instanceof HandledScreen<?> screen)||screen.getScreenHandler()!=ownedHandler){ownedHandler=null;triedContainers.add(restockTarget);restockTarget=null;return;}
            if(!ownedHandler.getCursorStack().isEmpty()){status="Restock paused — clear the cursor";return;}
            if(inventoryWait>0){inventoryWait--;return;}
            Map<Item,Integer> required=new HashMap<>(remaining);if(support.get())required.merge(Items.DIRT,restockDirt.getInt(),Integer::sum);
            for(var slot:ownedHandler.slots){
                var stack=slot.getStack();if(stack.isEmpty()||slot.inventory==mc.player.getInventory()||restockTriedSlots.contains(slot.id))continue;
                if(inventoryCount(stack.getItem())<required.getOrDefault(stack.getItem(),0)&&canReceive(stack.getItem())){
                    mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);restockTriedSlots.add(slot.id);inventoryWait=8;return;
                }
            }
            if(stockpile.get())for(var slot:ownedHandler.slots){
                var stack=slot.getStack();if(slot.inventory!=mc.player.getInventory()||stack.isEmpty()||restockTriedSlots.contains(slot.id)||!schematic.materials().containsKey(stack.getItem()))continue;
                if(inventoryCount(stack.getItem())-stack.getCount()>=required.getOrDefault(stack.getItem(),0)){
                    mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);restockTriedSlots.add(slot.id);inventoryWait=8;return;
                }
            }
            mc.player.closeHandledScreen();ownedHandler=null;triedContainers.add(restockTarget);restockTarget=null;delay=6;status="Restock checked";return;
        }
        if(mc.currentScreen instanceof HandledScreen<?> screen){
            if(restockWait>0&&screen.getScreenHandler() instanceof GenericContainerScreenHandler){ownedHandler=screen.getScreenHandler();walker.release();return;}
            walker.release();status="Close the current menu to restock";return;
        }
        if(restockWait>0){if(++restockWait>40){triedContainers.add(restockTarget);restockTarget=null;}return;}
        if(!walker.approach(restockTarget,effectiveReach()-.5)){status=walker.status;return;}
        if(!aim(Vec3d.ofCenter(restockTarget)))return;
        var hit=(BlockHitResult)mc.player.raycast(effectiveReach(),0,false);
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(restockTarget)){triedContainers.add(restockTarget);restockTarget=null;return;}
        releaseSneak();mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,hit);restockWait=1;
    }
    private boolean canReceive(Item item){for(int i=0;i<36;i++){var stack=mc.player.getInventory().getStack(i);if(stack.isEmpty()||stack.isOf(item)&&stack.getCount()<stack.getMaxCount())return true;}return false;}
    private void creativeMaterials(){
        if(!inGame()||schematic==null||!mc.player.getAbilities().creativeMode){notify("Get Materials requires creative mode");return;}
        for(var entry:remaining.entrySet()){
            int missing=entry.getValue()-inventoryCount(entry.getKey());
            for(int i=0;i<36&&missing>0;i++)if(mc.player.getInventory().getStack(i).isEmpty()){
                int count=Math.min(entry.getKey().getMaxCount(),missing);var stack=new ItemStack(entry.getKey(),count);
                int slot=i<9?36+i:i;mc.interactionManager.clickCreativeStack(stack,slot);mc.player.getInventory().setStack(i,stack);missing-=count;
            }
        }
        notify("Creative materials supplied to empty slots");
    }
    private void startPaste(){
        if(!inGame()||schematic==null||!mc.player.getAbilities().creativeMode||mc.getNetworkHandler().getCommandDispatcher().getRoot().getChild("setblock")==null){notify("Paste needs creative mode and server permission for /setblock");return;}
        preview();pasting=true;pasteCursor=0;status="Pasting block states";
    }
    private void pasteTick(){
        if(mc.currentScreen!=null)return;int actions=0;
        while(pasteCursor<schematic.size()&&actions<2){
            int i=pasteCursor++;if(!schematic.included(i)||!layerAllows(i))continue;var state=desired(i);if(state.isAir()&&!mineOut.get())continue;
            var pos=position(i);mc.getNetworkHandler().sendChatCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+blockString(state));actions++;
        }
        if(pasteCursor==schematic.size()){pasting=false;notify("Paste commands sent");status="Paste complete";}
    }
    public static String blockString(BlockState state){
        var n=net.minecraft.nbt.NbtHelper.fromBlockState(state);StringBuilder result=new StringBuilder(n.getString("Name","minecraft:air"));var properties=n.getCompoundOrEmpty("Properties");
        if(!properties.isEmpty()){result.append('[');boolean first=true;for(String key:new TreeSet<>(properties.getKeys())){if(!first)result.append(',');first=false;result.append(key).append('=').append(properties.getString(key,""));}result.append(']');}return result.toString();
    }
    private void startCapture(){
        if(!inGame()){notify("Join a world to capture");return;}pause("Capturing block states");setEnabled(true);building=false;
        capturedWidth=captureX.getInt();capturedHeight=captureY.getInt();capturedLength=captureZ.getInt();
        captureOrigin=origin==null?mc.player.getBlockPos():origin;captureStates=new BlockState[Schematic.volume(capturedWidth,capturedHeight,capturedLength)];captureCursor=0;
    }
    private void captureTick(){
        if(captureStates==null)return;long end=System.nanoTime()+2_000_000;int w=capturedWidth,l=capturedLength;
        while(captureCursor<captureStates.length&&System.nanoTime()<end){
            int i=captureCursor;var pos=captureOrigin.add(i%w,i/(w*l),(i/w)%l);
            if(!mc.world.isChunkLoaded(pos)){captureStates=null;status="Capture cancelled — unloaded chunk";notify(status);return;}
            captureStates[captureCursor++]=mc.world.getBlockState(pos);
        }
        if(captureCursor!=captureStates.length)return;
        String name="snapshot-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())+".nbt";
        capture=new Schematic(name,"Snapshot",w,capturedHeight,l,BlockPos.ORIGIN,captureStates);captureStates=null;Schematic snapshot=capture;Path path=folder().resolve(name);
        CompletableFuture.runAsync(()->{try{NbtIo.writeCompressed(SchematicIO.encodeStructure(snapshot),path);}catch(IOException e){throw new CompletionException(e);}},IO)
            .whenComplete((result,error)->mc.execute(()->{if(error!=null)notify("Snapshot save failed: "+rootMessage(error));else{load(path);notify("Saved "+name+" (block states)");}}));
    }
    private Map<Item,Integer> shoppingNeeds(int dirt){
        Map<Item,Integer> result=new HashMap<>(remaining);if(support.get())result.merge(Items.DIRT,dirt,Integer::sum);
        result.replaceAll((item,count)->Math.max(0,count-inventoryCount(item)));result.values().removeIf(count->count<=0);return result;
    }
    private void startBuying(boolean estimateOnly){
        if(!inGame()||schematic==null){notify("Load a schematic first");return;}
        if(!estimateOnly&&maxSpend.get()<=0){notify("Set Max Total Spend before buying");return;}
        if(mc.player.currentScreenHandler!=mc.player.playerScreenHandler){notify("Close the current container before buying");return;}
        boolean resume=building&&!estimateOnly&&autoBuy.get();
        pause(estimateOnly?"Estimating auction cost":"Buying materials");setEnabled(true);building=false;buying=true;resumeAfterMarket=resume;estimating=estimateOnly;shopping.clear();
        shoppingNeeds(buyDirt.getInt()).entrySet().stream().sorted(Comparator.comparing(e->Registries.ITEM.getId(e.getKey()).toString())).forEach(e->shopping.put(e.getKey(),e.getValue()));
        spent=estimate=0;buyingItem=null;marketWait=marketStage=0;pendingOffer=null;boughtListings.clear();
        if(buyNotifications.get())notify(estimateOnly?"Reading auction prices":"Material buying started — budget "+(long)maxSpend.get().doubleValue());
    }
    private void searchMarket(){
        String key=Registries.ITEM.getId(buyingItem).toString();if(!ahSearch.is("Registry ID")){key=Registries.ITEM.getId(buyingItem).getPath();if(ahSearch.is("Spaced"))key=key.replace('_',' ');}
        String command=ahCommand.get().strip().replaceFirst("^/","");
        if(!command.matches("[a-zA-Z0-9_]+")){finishBuying("Invalid AH command");return;}
        if(mc.currentScreen instanceof HandledScreen<?>)mc.player.closeHandledScreen();
        mc.getNetworkHandler().sendChatCommand(command+" "+key);marketWait=30;marketStage=1;marketSync=-1;status="Searching "+buyingItem.getName().getString();
    }
    private void marketTick(){
        if(marketWait>0){marketWait--;return;}
        if(buyingItem==null){
            if(shopping.isEmpty()){boolean resume=resumeAfterMarket&&!estimating;finishBuying(estimating?"Estimated material cost: "+Math.round(estimate):"Buying finished — spent "+Math.round(spent));if(resume){building=true;delay=6;status="Continuing build after buying";}return;}
            buyingItem=shopping.keySet().iterator().next();marketPage=1;searchMarket();return;
        }
        if(marketStage==3){
            int gained=inventoryCount(buyingItem)-inventoryBefore;
            if(gained<=0){finishBuying("Purchase not confirmed in inventory; no retry");return;}
            shopping.computeIfPresent(buyingItem,(k,v)->Math.max(0,v-gained));if(shopping.getOrDefault(buyingItem,0)<=0)shopping.remove(buyingItem);
            pendingOffer=null;buyingItem=null;marketStage=0;marketWait=buySpacing.getInt();return;
        }
        if(!(mc.currentScreen instanceof HandledScreen<?> screen)){finishBuying("Auction menu did not open or was closed");return;}
        ScreenHandler handler=screen.getScreenHandler();
        if(!handler.getCursorStack().isEmpty()){finishBuying("Buying stopped — cursor is occupied");return;}
        String title=screen.getTitle().getString();
        if(marketStage==2){
            if(AuctionMarket.word(title,boughtWord.get())){finishBuying("Auction is no longer available");return;}
            if(handler.syncId==marketSync||!AuctionMarket.word(title,confirmTitle.get())){finishBuying("Unrecognized purchase confirmation — stopped");return;}
            boolean itemVerified=AuctionMarket.offers(handler,mc.player.getInventory(),buyingItem,priceKeyword.get()).stream()
                .anyMatch(o->o.count()==pendingOffer.count()&&Math.abs(o.total()-pendingOffer.total())<.01);
            if(!itemVerified){finishBuying("Confirmation item, count or price changed — stopped");return;}
            ownedHandler=handler;
            for(var slot:handler.slots){
                var stack=slot.getStack();String label=stack.getName().getString();
                if(slot.inventory==mc.player.getInventory()||stack.isEmpty()||AuctionMarket.word(label,confirmNo.get())||!AuctionMarket.word(label,confirmYes.get()))continue;
                if(spent+pendingOffer.total()>maxSpend.get()){finishBuying("Session budget reached");return;}
                inventoryBefore=inventoryCount(buyingItem);spent+=pendingOffer.total();
                mc.interactionManager.clickSlot(handler.syncId,slot.id,0,SlotActionType.PICKUP,mc.player);marketStage=3;marketWait=30;return;
            }
            finishBuying("No recognized confirmation button");return;
        }
        if(!AuctionMarket.word(title,ahTitle.get())){finishBuying("Unexpected auction menu title — stopped");return;}
        ownedHandler=handler;
        var offers=AuctionMarket.offers(handler,mc.player.getInventory(),buyingItem,priceKeyword.get()).stream()
            .filter(o->!boughtListings.contains(handler.syncId+":"+o.slot()+":"+o.total()+":"+o.count())).toList();
        if(estimating){
            var cheapest=offers.stream().min(Comparator.comparingDouble(AuctionMarket.Offer::each)).orElse(null);
            if(cheapest!=null){estimate+=cheapest.each()*shopping.get(buyingItem);shopping.remove(buyingItem);buyingItem=null;marketWait=buySpacing.getInt();return;}
        }else{
            int requested=shopping.getOrDefault(buyingItem,0);
            var choice=AuctionMarket.choose(offers,requested,overbuy.getInt(),maxItem.get(),maxSpend.get()-spent,preferStacks.get(),tolerance.get());
            if(choice!=null){
                pendingOffer=choice;marketSync=handler.syncId;boughtListings.add(handler.syncId+":"+choice.slot()+":"+choice.total()+":"+choice.count());
                mc.interactionManager.clickSlot(handler.syncId,choice.slot(),0,SlotActionType.PICKUP,mc.player);marketStage=2;marketWait=Math.max(12,buySpacing.getInt());return;
            }
        }
        if(marketPage<maxPages.getInt())for(var slot:handler.slots){
            if(slot.inventory!=mc.player.getInventory()&&!slot.getStack().isEmpty()&&AuctionMarket.word(slot.getStack().getName().getString(),nextWord.get())){
                mc.interactionManager.clickSlot(handler.syncId,slot.id,0,SlotActionType.PICKUP,mc.player);marketPage++;marketWait=20;return;
            }
        }
        finishBuying("No suitable listing for "+buyingItem.getName().getString()+" within price / quantity limits");
    }
    private void finishBuying(String reason){
        buying=false;buyingItem=null;pendingOffer=null;shopping.clear();
        if(ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();ownedHandler=null;status=reason;if(buyNotifications.get())notify(reason);
    }
    @Override public void onRender2D(DrawContext ctx,float delta){
        if(!statusHud.get()||!inGame()||mc.currentScreen!=null)return;
        SmoothHudText.beginFrame();float w=280,x=(ctx.getScaledWindowWidth()-w)/2f,y=10;
        Render2D.shadow(ctx,x,y,w,58,9,8,0x50000000);Render2D.roundRect(ctx,x,y,w,58,9,0xE818202C);
        Render2D.roundRect(ctx,x+10,y+12,3,16,1.5f,building?0xFF7EF0C1:0xFF87B6FF);
        SmoothHudText.draw(ctx,"AUTO BUILDER",x+22,y+10,0xFFADBBD0,true,.75f);
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,status,w-32,true,.95f),x+12,y+25,0xFFF1F5FF,true,.95f);
        String details=schematic==null?"Choose a schematic in Player → Auto Builder":correct+" / "+solid+" blocks  ·  "+containers.size()+" restock marks";
        SmoothHudText.draw(ctx,details,x+12,y+41,0xFFB6C5D9,false,.75f);
        Render2D.roundRect(ctx,x+12,y+53,w-24,2,1,0xFF334255);if(solid>0)Render2D.roundRect(ctx,x+12,y+53,(w-24)*correct/solid,2,1,0xFF7EF0C1);
        if(showLabels.get()&&restockTarget!=null)SmoothHudText.draw(ctx,"Restock "+restockTarget.toShortString(),x+12,y+64,0xFF9DCBFF,false,labelScale.getFloat());
    }
    @Override public JsonObject saveExtra(){
        var result=new JsonObject();result.addProperty("file",selected);result.addProperty("dimension",dimension);result.addProperty("world-scope",worldScope);
        if(origin!=null)result.add("origin",posJson(origin));var marks=new JsonArray();for(var pos:containers)marks.add(posJson(pos));result.add("restock",marks);return result;
    }
    private static JsonArray posJson(BlockPos pos){var a=new JsonArray();a.add(pos.getX());a.add(pos.getY());a.add(pos.getZ());return a;}
    private static BlockPos jsonPos(JsonElement value){var a=value.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Position");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    @Override public void loadExtra(JsonObject data){
        try{if(data.has("world-scope"))worldScope=data.get("world-scope").getAsString();if(data.has("dimension"))dimension=data.get("dimension").getAsString();if(data.has("origin"))origin=jsonPos(data.get("origin"));containers.clear();if(data.has("restock"))for(var pos:data.getAsJsonArray("restock"))containers.add(jsonPos(pos));
            if(data.has("file")){selected=data.get("file").getAsString();if(!selected.isBlank()&&Files.isRegularFile(folder().resolve(selected)))load(folder().resolve(selected));}
        }catch(RuntimeException e){Maro.LOGGER.warn("Invalid Auto Builder saved placement",e);}
    }
    private String scope(){return mc.getCurrentServerEntry()!=null?"server:"+mc.getCurrentServerEntry().address:mc.getServer()!=null?"local:"+mc.getServer().getSaveProperties().getLevelName():"";}
}
