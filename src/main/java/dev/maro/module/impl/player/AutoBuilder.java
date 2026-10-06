package dev.maro.module.impl.player;

import com.google.gson.*;
import dev.maro.Maro;
import dev.maro.builder.*;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.mixin.BlockItemAccessor;
import dev.maro.mixin.ClientPlayerInteractionManagerAccessor;
import dev.maro.mixin.ClientPlayerLookAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.setting.*;
import dev.maro.runtime.settings.StringSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.BlockTags;
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
    private final BooleanSetting saveBuilds=bool("Saved Builds","Save Build Progress","Save the active placement on disconnect and every ten seconds; resume after rescanning the world",true);
    private final ModeSetting buildSlot=mode("Saved Builds","Build Slot","Choose a saved build or a slot for a new placement","Last Session","Last Session","1","2","3","4","5","6","7","8","9","10");
    private final BuilderPlacements savedBuilds=new BuilderPlacements(FabricLoader.getInstance().getGameDir().resolve("maro/builder-placements"));
    private final Map<Integer,JsonObject> savedBuildInfo=new HashMap<>();
    private int activeBuildSlot=-1;
    private String placementName="";
    private boolean restoringPlacement;
    private final ModeSetting mode=mode("Start","Build Mode","Automatic starts immediately; Semi Auto builds while right mouse is held","Automatic","Semi Auto","Automatic");
    private final ModeSetting supplyMode=mode("Materials","Material Supply","Nearby Sections finishes compact areas with inventory-sized material batches","Nearby Sections","Nearby Sections","Layer by Layer","Whole Schematic").onChange(v->replan());
    private final BooleanSetting prebuyWhole=bool("Materials","Prepare Whole Build","Buy missing supplies for the whole build, store them in selected chests, then fetch each work batch",true);
    private final BooleanSetting autoMove=bool("Build","Auto Move","Walk safe ground routes toward out-of-reach blocks",true);
    private final BooleanSetting unstuck=bool("Build","Auto Unstuck","Jump onto a temporary dirt step when a walking route is stuck, then remove it",true);
    private final BooleanSetting autoEat=bool("Food","Auto Eat","Pause movement and building to eat steak when hungry",true);
    private final NumberSetting hungerLimit=number("Food","Hunger Threshold","Start eating at or below this food level (20 is full)",14,1,19,1);
    private final BooleanSetting buySteak=bool("Food","Buy Steak","Buy missing steak after checking chests, using the AH budget",true);
    private final NumberSetting steakReserve=number("Food","Steak Reserve","Steak to fetch or buy when food is needed",16,1,64,1);
    private final BooleanSetting mineOut=bool("Build","Mine Out Schematic","Also clear blocks where the schematic explicitly contains air",false);
    private final BooleanSetting replaceWrong=bool("Build","Replace Wrong Blocks","Mine mismatching block types before placing the requested block",false);
    private final BooleanSetting repairStates=fixedBool("Repair Wrong States","Break and replace mismatching states when their placement can be reproduced",false);
    private final BooleanSetting protectContainers=fixedBool("Protect Containers","Never mine block entities such as chests, signs or machines",true);
    private final NumberSetting spacing=number("Build","Action Delay","Ticks between placements and inventory operations; server confirmation is still required",2,0,20,1);
    private final NumberSetting timingVariation=number("Build","Timing Variation","Extra random ticks added to action delay",1,0,6,1);
    private final BooleanSetting smoothTurning=bool("Build","Head Smoothing","Ease visible turns into and out of the target angle",true);
    private float yawVelocity,pitchVelocity;
    private final NumberSetting reach=fixedNumber("Reach","Maximum vanilla interaction distance; also clamped to player reach",4.4,2,5, .1);
    private final NumberSetting turnSpeed=fixedNumber("Turn Speed","Maximum view rotation per tick",45,5,180,1);
    private final BooleanSetting support=bool("Build","Temporary Supports","Place dirt under floating targets when an adjacent face is reachable",true);
    private final NumberSetting tempDirt=fixedNumber("Temporary Dirt Limit","Maximum temporary supports placed during this build",128,0,512,1);
    private final BooleanSetting cleanup=bool("Build","Clean Temporary Supports","Break this builder's temporary dirt after the schematic is complete",true);
    private final ModeSetting rotation=mode("Placement","Rotation","Rotate positions and block states clockwise","0","0","90","180","270").onChange(v->replan());
    private final ModeSetting mirror=mode("Placement","Mirror","Mirror positions and facing properties before rotation","None","None","X","Z").onChange(v->replan());
    private final BooleanSetting useOffset=fixedBool("Apply File Offset","Include the schematic's stored origin offset",false).onChange(v->replan());
    private final KeybindSetting markBind=bind("Materials","Mark Restock Bind","R adds or refreshes the double chest you are looking at; Shift + R removes it",GLFW.GLFW_KEY_R);
    private final BooleanSetting restock=bool("Materials","Restock When Empty","Take missing building materials from your selected double chests",true);
    private final NumberSetting walkDistance=fixedNumber("Restock Walk Distance","Maximum distance to a marked container",256,4,256,1);
    private final NumberSetting restockDirt=number("Materials","Support Dirt Reserve","Dirt reserved for temporary supports when restocking or buying",64,0,512,1);
    private final BooleanSetting stockpile=bool("Materials","Stockpile In Chests","Deposit surplus whole stacks of building materials into marked chests",false);
    private final ModeSetting depositWhen=mode("Materials","Deposit All Items","Move inventory and hotbar into your selected double chests and continue buying when full","Inventory Full","Inventory Full","Manual","After Buying","After Build");
    public final BooleanSetting showContainers=fixedBool("Show Restock Containers","Outline marked chests",true);
    public final BooleanSetting showLabels=fixedBool("Show Restock Labels","Show marked container coordinates on the progress panel",true);
    public final NumberSetting containerRange=fixedNumber("Restock Render Distance","Visible range of marked chest outlines",64,8,128,1);
    public final NumberSetting containerAlpha=fixedNumber("Restock Outline Alpha","Opacity of marked chest outlines",.85,.05,1,.05);
    public final NumberSetting labelScale=fixedNumber("Restock Label Scale","Size of chest information in the progress panel",.8,.5,1.5,.05);
    private final KeybindSetting buyBind=fixedBind("Auto Buy Key","Start a material buying session",-1);
    private final BooleanSetting autoBuy=bool("Materials","Auto Buy When Missing","Use inventory first, then your selected chests, then buy the current work batch's missing materials",true);
    private final BooleanSetting autoTools=bool("Materials","Auto Buy Tools","Supply a pickaxe and shovel for incorrect blocks and temporary-support cleanup",true);
    private final BooleanSetting preferStacks=fixedBool("Prefer Stacks","Prefer full stacks when their unit price is within tolerance",false);
    private final NumberSetting tolerance=fixedNumber("Stack Price Tolerance %","Maximum premium for a preferred stack",15,0,100,1);
    private final NumberSetting overbuy=fixedNumber("Max Overbuy","Maximum extra items beyond the missing quantity",16,0,64,1);
    private final NumberSetting maxItem=number("Materials","Max Price Per Item","0 disables the per-item ceiling; total budget still applies",1000,0,1_000_000_000,1);
    private final NumberSetting maxSpend=number("Materials","Max Total Spend","Session budget in server currency; 0 disables buying",0,0,1_000_000_000,1);
    private final NumberSetting maxPages=fixedNumber("Max AH Pages","Maximum auction pages checked per material",3,1,20,1);
    private final NumberSetting buySpacing=fixedNumber("Buy Click Spacing","Minimum ticks between menu clicks",4,2,40,1);
    private final BooleanSetting buyNotifications=fixedBool("Buy Notifications","Notify when buying starts, finishes, or stops",true);
    private final NumberSetting buyDirt=restockDirt;
    private final ModeSetting ahSearch=fixedMode("AH Search By","How item registry names are written in the search command","Spaced","Spaced","Underscored","Registry ID");
    private final dev.maro.runtime.settings.Setting<String> ahCommand=fixedText("AH Command","Auction search command without a leading slash","ah");
    private final dev.maro.runtime.settings.Setting<String> ahTitle=fixedText("AH Title Word","Expected auction menu title","Auction");
    private final dev.maro.runtime.settings.Setting<String> priceKeyword=fixedText("Price Keyword","Marker preceding the listing's total price","$");
    private final dev.maro.runtime.settings.Setting<String> confirmYes=fixedText("Confirm Yes Word","Accepted confirmation labels, separated with ;","Confirm;Purchase;Buy;Yes");
    private final dev.maro.runtime.settings.Setting<String> confirmNo=fixedText("Confirm No Word","Rejected/cancel labels, separated with ;","Cancel;No");
    private final dev.maro.runtime.settings.Setting<String> confirmTitle=fixedText("Confirm Title Word","Recognized confirmation titles, separated with ;","Confirm;Purchase;Sure");
    private final dev.maro.runtime.settings.Setting<String> nextWord=fixedText("Next Page Word","Label on the next-page button","Next page");
    public final BooleanSetting showPreview=bool("Preview","Show Preview","Render the schematic while the module is enabled",true);
    public final BooleanSetting textured=fixedBool("Textured Preview","Render the actual block models and textures",true);
    public final ModeSetting previewMode=fixedMode("Preview Mode","Full blueprint or only cells which need work","Missing & Wrong","Full","Missing & Wrong","Outline Only");
    public final NumberSetting previewRange=number("Preview","Preview Range","Maximum distance from the camera",48,8,96,1);
    public final NumberSetting ghostFill=number("Preview","Ghost Fill","Overall textured preview opacity",.5,.05,1,.05);
    public final BooleanSetting renderCorrect=fixedBool("Render Correct","Include matching blocks",false);
    public final BooleanSetting renderMissing=fixedBool("Render Missing","Include missing blocks",true);
    public final BooleanSetting renderIncorrect=fixedBool("Render Incorrect","Include wrong block types or states",true);
    public final BooleanSetting buildBox=fixedBool("Show Build Box","Outline the schematic's transformed bounds",true);
    public final BooleanSetting originMarker=fixedBool("Show Origin Marker","Draw the placement anchor",true);
    private final ModeSetting preset=mode("Preview","Preview Preset","Apply coordinated ghost and outline settings","Building","Building","Blueprint","Verification").onChange(v->applyPreset(v));
    public final ModeSetting outlineStrength=fixedMode("Outline Strength","Outline contrast","Strong","Soft","Strong","Bright");
    public final ModeSetting clarity=fixedMode("Interior Clarity","Fade ghosts near the camera to keep the view clear","Fade Near Camera","None","Fade Near Camera","Surface Only");
    public final NumberSetting fadeRadius=fixedNumber("Interior Fade Radius","Radius of camera-adjacent fade",2,.5,8,.25);
    public final BooleanSetting throughWalls=fixedBool("Missing Through Walls","Show a faint outline for hidden missing blocks",false);
    public final NumberSetting missingOpacity=fixedNumber("Missing Opacity","Colored fill for missing blocks",.14,0,1,.01);
    public final NumberSetting wrongOpacity=fixedNumber("Wrong Block Opacity","Colored fill for wrong block types",.2,0,1,.01);
    public final NumberSetting stateOpacity=fixedNumber("Wrong State Opacity","Colored fill for wrong block states",.18,0,1,.01);
    public final NumberSetting occludedOpacity=fixedNumber("Occluded Opacity","Faint outline opacity when through-walls is enabled",.07,0,.5,.01);
    public final BooleanSetting outlines=fixedBool("Outlines","Outline individual cells",true);
    public final NumberSetting outlineWidth=fixedNumber("Outline Width","Line width in pixels",1,.5,3,.25);
    public final ModeSetting layerMode=mode("Preview","Layer Mode","Preview and build every layer or the selected height","All","All","Single","Below");
    public final NumberSetting layer=number("Preview","Layer","Local Y level for Single / Below modes",0,0,2047,1).visible(()->!layerMode.is("All"));
    private final NumberSetting budget=fixedNumber("Rebuild Budget","Maximum scan time per client tick in milliseconds",2,.25,8,.25);
    public final NumberSetting renderLimit=fixedNumber("Max Preview Blocks","Hard cap on rendered cells per frame",1500,100,6000,100);
    private final BooleanSetting stopStaff=bool("Safety","Stop On Staff Nearby","Pause for loaded players whose names are in your staff list",true);
    private final NumberSetting staffRange=fixedNumber("Staff Detect Range","Distance to a configured staff player",48,4,96,1);
    private final BooleanSetting logoff=fixedBool("Log Off After Staff Stop","Disconnect after the selected delay; off by default",false);
    private final NumberSetting logoffDelay=fixedNumber("Logoff Delay","Seconds after staff proximity stop",5,0,30,.5).visible(logoff::get);
    private final dev.maro.runtime.settings.Setting<String> logoffMessage=fixedText("Logoff Message","Local disconnect reason","Auto Builder stopped — staff nearby");
    private final NumberSetting minHealth=number("Safety","Minimum Health","Pause building below this health in hearts",4,0,10,.5);
    private final BooleanSetting pausePlayers=bool("Safety","Pause Near Players","Pause automatic actions for other nearby players",false);
    private final NumberSetting playerRange=fixedNumber("Player Pause Range","Radius of the optional player pause",8,2,32,1);
    private final BooleanSetting statusHud=bool("Build","Progress HUD","Show progress, materials and the current task",true);
    private final NumberSetting captureX=number("Snapshot","Capture Width","X size of the snapshot, beginning at the origin",16,1,64,1);
    private final NumberSetting captureY=number("Snapshot","Capture Height","Y size of the snapshot",8,1,64,1);
    private final NumberSetting captureZ=number("Snapshot","Capture Length","Z size of the snapshot",16,1,64,1);
    private Schematic schematic;
    private BlockPos origin;
    private ClientWorld world;
    private String dimension="",selected="",status="Choose a schematic";
    private byte[] states=new byte[0];
    private byte[] unitsLeft=new byte[0];
    private final Map<Integer,Integer> potUnitsLeft=new HashMap<>();
    private int scanCursor,correct,solid,ignoredSolid,completedScans,ticks,delay,originalSlot=-1,ioGeneration,passTasks,lastPassTasks,lastAction;
    private final BuilderEta buildEta=new BuilderEta();
    private boolean loading,building,preview=true,ownsSneak;
    private final Map<Item,Integer> remaining=new HashMap<>();
    private final TreeMap<Integer,Map<Item,Integer>> remainingByLayer=new TreeMap<>();
    private int activeLayer=-1,scanLayer=Integer.MAX_VALUE,activePhase,scanPhase=Integer.MAX_VALUE;
    private final Set<BlockPos> containers=new LinkedHashSet<>(),supports=new LinkedHashSet<>();
    private final Set<Item> ignoredMaterials=new HashSet<>();
    private final Set<BlockPos> triedContainers=new HashSet<>();
    private final Map<BlockPos,Integer> chestAccessRetryAt=new HashMap<>();
    private Map<Item,Integer> restockBatch=Map.of();
    private final Map<BlockPos,Set<Item>> emptyChestItems=new HashMap<>();
    private final Map<Item,Integer> preparedStock=new HashMap<>();
    private final Map<BlockPos,Map<Item,Integer>> chestStocks=new HashMap<>();
    private int preparationStage;
    private boolean preparationReady,buildBudgetActive;
    private double buildBudgetSpent;
    private String preparationStopReason="";
    private final Set<Integer> restockTriedSlots=new HashSet<>();
    private final Map<Integer,Integer> restockSlotRetries=new HashMap<>();
    private final Map<Integer,Integer> retryAt=new HashMap<>();
    private final Map<Integer,Map<BlockPos,Integer>> triedStands=new HashMap<>();
    private final Map<BlockPos,Map<BlockPos,Integer>> cleanupStands=new HashMap<>();
    private BlockPos standGoal;
    private BlockPos accessStand;
    private int accessStarted;
    private boolean accessFloor;
    private BlockPos recycleTarget;
    private BlockPos routeOpening;
    private final Set<BlockPos> passageBlocks=new LinkedHashSet<>();
    private BlockPos passageStand,passageSearchFeet;
    private int passageSearchWork=-2,passageSearchCursor,passageRetryAt;
    private boolean descentLanding;
    private BlockPos descentPost,descentView;
    private BlockPos descentSearchFeet,descentSearchDestination;
    private List<BlockPos> descentSearchPosts=List.of(),descentSearchViews=List.of();
    private int descentSearchCursor,descentRetryAt,descentSearchPhase;
    private List<BlockPos> descentHatchViews=List.of();
    private boolean descentHatchesReady;
    private BlockPos hatchSearchFeet;
    private List<BlockPos> hatchCandidates=List.of();
    private final List<BlockPos> hatchViews=new ArrayList<>();
    private int hatchSearchCursor,hatchExitCursor;
    private static final Direction[] ESCAPE_SIDES={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final class EscapeSearch {int cursor;BlockPos view;}
    private final Map<BlockPos,EscapeSearch> descentEscapes=new HashMap<>();
    private BlockPos recycleSearchFeet;
    private int recycleSearchHash,recyclePostCursor,recycleViewCursor,recycleRouteCursor,recycleRetryAt;
    private List<BlockPos> recyclePosts=List.of();
    private final List<BlockPos> recycleViews=new ArrayList<>();
    private final Set<BlockPos> accessSupports=new HashSet<>();
    private int navigatingCell=-1,navigationStarted,eatPreviousSlot=-1,eatBefore,eatDeadline;
    private int accessProgressAt;
    private double accessBestDistance;
    private boolean eating,ownsFoodUse,foodRestock,foodShopping,supportRestock,supportShopping;
    private int standStarted,standProgressAt;
    private Vec3d standProgressPos;
    private final IdentityHashMap<BlockState,BlockState> transformedStates=new IdentityHashMap<>();
    private final BuilderWalk walker=new BuilderWalk();
    private final Set<BlockPos> escapeSupports=new LinkedHashSet<>();
    private BlockPos recoveryBase;
    private int recoveryPhase,recoveryStarted,recoveryAttempts,recoveryCooldown;
    private boolean recoveryJump;
    private List<Integer> visible=List.of();
    private List<Integer> workCells=List.of();
    private List<Integer> sectionCells=List.of();
    private int sectionProgressAt,sectionCorrect;
    private final PriorityQueue<Visible> visibleScan=new PriorityQueue<>(Comparator.comparingDouble(Visible::distance).reversed());
    private final PriorityQueue<Visible> workScan=new PriorityQueue<>(Comparator.comparingDouble(Visible::distance).reversed());
    private record Visible(int index,double distance){}
    private record Place(BlockPos target,BlockState state,BlockHitResult hit,Item item,int index,boolean temporary){}
    private Place placement;
    private BlockPos placementAttemptTarget;
    private int placementAttemptStarted;
    private final Map<BlockPos,Integer> failedPlacementUntil=new HashMap<>();
    private final Map<BlockPos,Integer> routeSupportExclusions=new HashMap<>();
    private final Map<BlockPos,List<BlockPos>> supportChainStarts=new HashMap<>();
    private record ViewKey(BlockPos target,BlockPos feet,boolean bridge){}
    private static final class ViewSearch {
        int expires,cursor,routeCursor,temporaryCursor;
        List<BlockPos> temporaryViews;
        final List<BlockPos> options=new ArrayList<>();
        final Set<BlockPos> direct=new HashSet<>();
        final Map<BlockPos,Integer> scaffoldDistance=new HashMap<>();
    }
    private final LinkedHashMap<ViewKey,ViewSearch> viewSearches=new LinkedHashMap<>();
    private long viewPlanningDeadline;
    private long floorPlanningDeadline;
    private BlockPos floorSearchFeet;
    private int floorSearchCursor;
    private BlockPos bridgeTarget;
    private BlockPos supportPickup;
    private int supportPickupUntil,supportRecycleAt;
    private Place pendingPlacement;
    private final Map<BlockPos,Place> unconfirmedPlacements=new HashMap<>();
    private record LatePlacement(Place job,int expires){}
    private final Map<BlockPos,LatePlacement> latePlacements=new HashMap<>();
    private BlockState pendingBefore,pendingServerState;
    private int placementDeadline;
    private BlockPos routeMining;
    private final Map<BlockPos,Integer> floorAccessWork=new HashMap<>();
    private BlockPos mining,restockTarget;
    private BlockPos tuningTarget,tuningSession;
    private int tuningObserved,tuningExpected,tuningDeadline,tuningClicks;
    private Item needed,restockAttemptItem;
    private int restockWait,inventoryWait;
    private int partialSource=-1,partialDestination,partialRemaining;
    private Item partialItem;
    private int sneakReadyAt;
    private long staffStopAt;
    private ScreenHandler ownedHandler;
    private ScreenHandler receivedChestInventory;
    private Runnable queuedLookAction;
    private float queuedLookYaw,queuedLookPitch;
    private Vec3d aimPoint,queuedAimPoint;
    private int lookWaitStarted=-1;
    private ScreenHandler unexpectedBuildHandler;
    private int lastBuildInteraction=-1000,unexpectedMenuAt;
    private BlockPos chestStand;
    private final Map<BlockPos,Integer> chestTriedStands=new HashMap<>();
    private Vec3d chestProgressPosition;
    private int chestProgressAt,chestSessionStarted,chestInventoryProgressAt,cursorReturns;
    private long chestInventoryFingerprint;
    private boolean chestJourneyFailed;
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
    private int marketPage,marketWait,marketStage,marketDeadline,inventoryBefore;
    private double spent,estimate;
    private AuctionMarket.Offer pendingOffer;
    private final Set<String> boughtListings=new HashSet<>();
    private final Set<String> unavailableListings=new HashSet<>();
    private String pendingListing="";
    private boolean soldNotice;
    private int soldSkips;
    private AuctionMarket.Offer bestMarketOffer;
    private String bestMarketKey="";
    private int bestMarketPage,marketRechecks;
    private boolean returningToOffer;
    private boolean marketInventoryBlocked;
    private boolean depositing;
    private BlockPos depositTarget;
    private final Deque<BlockPos> depositQueue=new ArrayDeque<>();
    private int depositOpenWait,depositSlot=-1,depositCount,depositDeadline,depositAckDeadline;
    private boolean resumeShoppingAfterDeposit;
    private final LinkedHashMap<Item,Integer> depositedShopping=new LinkedHashMap<>();

    public AutoBuilder(){
        super("Auto Builder","Load, preview and build schematics with materials, chest restocking and auction buying",Category.PLAYER);
        button("Start","Builder Panel","Open the simple schematic, position and build controls","Open Builder",()->mc.setScreen(new BuilderControlScreen(mc.currentScreen,this)));
        button("Saved Builds","Manage Saved Builds","Choose, name, save or load a build placement","Open",()->mc.setScreen(new BuilderPlacementsScreen(mc.currentScreen,this)));
        button("Saved Builds","Save Placement","Save this schematic and its placement in the selected slot","Save",()->savePlacement(placementName));
        button("Saved Builds","Load Placement","Load the selected build, paused at its saved origin","Load",this::loadPlacement);
        button("Start","Choose Schematic","Choose a .schem, .schematic, .litematic or .nbt file","Choose",()->mc.setScreen(new BuilderScreen(mc.currentScreen,this,false)));
        button("Materials","Show Materials","Show total, remaining and inventory counts","Materials",()->mc.setScreen(new BuilderScreen(mc.currentScreen,this,true)));
        button("Snapshot","Creative Materials Get","Fill empty inventory slots with needed materials in creative","Get Materials",this::creativeMaterials).visible(()->mc.player!=null&&mc.player.getAbilities().creativeMode);
        button("Snapshot","Paste Schematic","Paste block states using setblock commands; needs creative and server permission","Paste",this::startPaste).visible(()->mc.player!=null&&mc.player.getAbilities().creativeMode);
        button("Start","Start / Resume Build","Start using the selected Build Mode","Build",this::startBuild);
        button("Start","Pause Build","Keep the preview visible and release all inputs","Pause",()->pause("Paused"));
        button("Start","Restart Build","Rescan the current schematic and restart at the same origin","Restart",this::restartBuild);
        button("Start","Cancel Schematic","Stop all actions and unload the schematic; placed blocks remain","Cancel",this::cancelSchematic);
        button("Snapshot","Capture Snapshot","Save the configured area from the placement origin to a vanilla .nbt file","Capture",this::startCapture);
        button("Materials","Mark Restock Container","R adds or refreshes the double chest you are looking at; Shift + R removes it","Add",this::markContainer);
        button("Materials","Clear Restock Marks","Clear this world's selected supply chests","Clear",()->{pause("Supply chests cleared");preparationReady=false;containers.clear();triedContainers.clear();emptyChestItems.clear();chestStocks.clear();preparedStock.clear();});
        button("Materials","Buy Materials","Buy missing materials within your configured budget","Buy",()->startBuying(false));
        button("Materials","Estimate Cost","Read current auction listings without buying","Estimate",()->startBuying(true));
        button("Materials","Cancel Buying","Stop the shopping session","Cancel",()->finishBuying("Buying cancelled"));
        button("Materials","Deposit All","Move inventory and hotbar items into your selected double chests","Deposit",this::depositAll);
        ClientTickEvents.START_CLIENT_TICK.register(client->{digging=false;queuedLookAction=null;if(isEnabled())tickWork();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            checkpoint();pause("Build saved — reconnect and press Resume");world=null;
        });
        CompletableFuture.runAsync(()->{
            var infos=new HashMap<Integer,JsonObject>();
            for(int slot=0;slot<=10;slot++)try{var info=savedBuilds.info(slot);if(info!=null)infos.put(slot,info);}catch(IOException error){Maro.LOGGER.warn("Could not read build slot {}",slot,error);}
            Integer active=null;try{active=savedBuilds.activeSlot();}catch(IOException error){Maro.LOGGER.warn("Could not read active build slot",error);}
            int selectedSlot=active==null?infos.entrySet().stream().max(Comparator.comparingLong(e->e.getValue().get("saved-at").getAsLong())).map(Map.Entry::getKey).orElse(-1):active;
            mc.execute(()->{
                savedBuildInfo.putAll(infos);
                if(saveBuilds.get()&&infos.containsKey(selectedSlot)){buildSlot.set(selectedSlot==0?"Last Session":String.valueOf(selectedSlot));loadPlacement();}
            });
        },IO);
        ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(buying&&pendingOffer!=null&&(marketStage==2||marketStage==3)&&AuctionMarket.unavailable(message.getString()))soldNotice=true;});
    }
    // Rendering/protocol defaults are internal; they no longer clutter the saved settings UI.
    private BooleanSetting fixedBool(String n,String d,boolean value){return new BooleanSetting(n,d,value);}
    private NumberSetting fixedNumber(String n,String d,double value,double min,double max,double step){return new NumberSetting(n,d,value,min,max,step);}
    private ModeSetting fixedMode(String n,String d,String value,String... options){return new ModeSetting(n,d,value,options);}
    private KeybindSetting fixedBind(String n,String d,int value){return new KeybindSetting(n,d,value);}
    private dev.maro.runtime.settings.Setting<String> fixedText(String n,String d,String value){return new StringSetting.Builder().name(n).description(d).defaultValue(value).build();}
    private <S extends Setting<?>> S setting(String group,S value){add(value);groups.computeIfAbsent(group,SettingSection::new).add(value);return value;}
    private BooleanSetting bool(String g,String n,String d,boolean v){return setting(g,new BooleanSetting(n,d,v));}
    private NumberSetting number(String g,String n,String d,double v,double min,double max,double step){return setting(g,new NumberSetting(n,d,v,min,max,step));}
    private ModeSetting mode(String g,String n,String d,String v,String... choices){return setting(g,new ModeSetting(n,d,v,choices));}
    private KeybindSetting bind(String g,String n,String d,int v){return setting(g,new KeybindSetting(n,d,v));}
    private ButtonSetting button(String g,String n,String d,String label,Runnable action){return setting(g,new ButtonSetting(n,d,label,action));}
    @Override public List<SettingSection> getSettingSections(){
        List<SettingSection> result=new ArrayList<>();if(groups.containsKey("Start"))result.add(groups.get("Start"));
        groups.forEach((name,section)->{if(!name.equals("Start"))result.add(section);});return result;
    }
    public static boolean holdingBreak(){return digging;}
    public static void serverBlockUpdate(BlockPos pos,BlockState state){
        var builder=ModuleManager.get(AutoBuilder.class);
        if(builder==null||builder.world!=mc.world)return;
        if(state.isAir()&&builder.supports.contains(pos)&&builder.accessStand!=null)builder.accessProgressAt=builder.ticks;
        if(pos.equals(builder.routeMining)||pos.equals(builder.mining)||builder.unconfirmedPlacements.containsKey(pos))builder.viewSearches.clear();
        if(builder.pendingPlacement!=null&&builder.pendingPlacement.target.equals(pos))builder.pendingServerState=state;
        var job=builder.unconfirmedPlacements.get(pos);
        var late=builder.latePlacements.remove(pos);if(job==null&&late!=null)job=late.job;
        if(job!=null){
            builder.viewSearches.clear();
            if(state.getBlock()==job.state.getBlock()){
                builder.unconfirmedPlacements.remove(pos);
                if(job.temporary)builder.supports.add(pos.toImmutable());
            }else if(mc.world!=null&&mc.world.getBlockState(pos).equals(state))builder.unconfirmedPlacements.remove(pos);
        }
    }
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
    public void install(Schematic data){if(!restoringPlacement){checkpoint();activeBuildSlot=-1;placementName="";}pause("Schematic loaded");schematic=data;unconfirmedPlacements.clear();latePlacements.clear();supports.clear();escapeSupports.clear();cleanupStands.clear();recoveryAttempts=0;replan();}
    public Schematic schematic(){return schematic;}
    public String status(){return status;}
    public boolean loading(){return loading;}
    public boolean building(){return building;}
    public boolean buying(){return buying;}
    public boolean depositing(){return depositing;}
    public double sessionSpend(){return spent;}
    public double auctionBudget(){return maxSpend.get();}
    public void auctionBudget(double value){maxSpend.set(value);}
    public String buildMode(){return mode.get();}
    public void cycleBuildMode(){mode.cycle(1);}
    public void buyMaterials(){startBuying(false);}
    public void togglePreview(){boolean show=!previewVisible();showPreview.set(show);if(show&&!isEnabled())preview();}
    public boolean previewVisible(){return isEnabled()&&preview&&showPreview.get()&&schematic!=null&&origin!=null&&world==mc.world;}
    public BlockPos origin(){return origin;}
    public int turns(){return rotation.index();}
    public BlockPos position(int index){return anchor().add(schematic.transformed(index,turns(),mirror.get()));}
    public BlockState desired(int index){return transformedStates.computeIfAbsent(schematic.state(index),state->schematic.transformedState(index,turns(),mirror.get()));}
    public byte state(int index){return states[index];}
    public List<Integer> visibleCells(){return visible;}
    public Set<BlockPos> restockContainers(){return Collections.unmodifiableSet(containers);}
    public Set<BlockPos> temporarySupports(){return Set.copyOf(supports);}
    public boolean materialIgnored(Item item){return ignoredMaterials.contains(item);}
    private boolean materialIgnored(BlockState state){var item=Schematic.material(state);return materialIgnored(item==Items.AIR?state.getBlock().asItem():item)||potted(state)&&materialIgnored(Items.FLOWER_POT);}
    public void toggleMaterialIgnored(Item item){
        if(schematic==null||!schematic.materials().containsKey(item))return;
        boolean resume=building;if(!ignoredMaterials.remove(item))ignoredMaterials.add(item);replan();building=resume;
        status=(materialIgnored(item)?"Ignoring ":"Including ")+item.getName().getString();
    }
    public BlockPos anchor(){return useOffset.get()?origin.add(schematic.offset):origin;}
    public boolean layerAllows(int index){int y=schematic.local(index).getY();return layerMode.is("All")||layerMode.is("Single")&&y==layer.getInt()||layerMode.is("Below")&&y<=layer.getInt();}
    public boolean showCell(int index){
        if(!layerAllows(index)||materialIgnored(desired(index)))return false;byte s=states[index];
        return s==CORRECT&&(renderCorrect.get()||previewMode.is("Full"))||s==MISSING&&renderMissing.get()||(s==WRONG_BLOCK||s==WRONG_STATE)&&renderIncorrect.get()||s==UNKNOWN&&!desired(index).isAir();
    }
    public int supplyLayer(){if(activeLayer>=0)return activeLayer;for(var entry:remainingByLayer.entrySet())if(entry.getValue().values().stream().anyMatch(count->count>0))return entry.getKey();return -1;}
    public boolean layerSupply(){return supplyMode.is("Layer by Layer");}
    public boolean sectionSupply(){return supplyMode.is("Nearby Sections");}
    private int taskPhase(int index){var block=desired(index).getBlock();return block instanceof ObserverBlock?2:block instanceof FluidBlock?1:0;}
    private int taskLayer(int index){return schematic.local(index).getY()+taskPhase(index)*schematic.height;}
    private Map<Item,Integer> requiredMaterials(){
        if(!sectionSupply())return layerSupply()?remainingByLayer.getOrDefault(supplyLayer(),Map.of()):remaining;
        var result=new HashMap<Item,Integer>();
        for(int i:sectionCells)if(states[i]!=CORRECT&&states[i]!=IGNORED&&!floorDeferred(position(i))&&layerAllows(i)&&!materialIgnored(desired(i))){
            var item=Schematic.material(desired(i));if(item!=Items.AIR)result.merge(item,(int)unitsLeft[i],Integer::sum);
            if(potted(desired(i))&&potUnitsLeft.getOrDefault(i,1)>0)result.merge(Items.FLOWER_POT,1,Integer::sum);
        }
        return result;
    }
    private void refreshSection(){
        if(!sectionSupply())return;
        sectionCells=sectionCells.stream().filter(i->states[i]!=CORRECT&&states[i]!=IGNORED&&!floorDeferred(position(i))&&layerAllows(i)&&taskPhase(i)==activePhase).toList();
        if(correct!=sectionCorrect||!building||restockTarget!=null||buying||mc.currentScreen!=null){sectionCorrect=correct;sectionProgressAt=ticks;}
        if(!sectionCells.isEmpty()&&ticks-sectionProgressAt>240&&placement==null&&pendingPlacement==null&&mining==null){
            for(int i:sectionCells)retryAt.put(i,ticks+200);sectionCells=List.of();walker.stop();navigatingCell=-1;
        }
        if(!sectionCells.isEmpty())return;
        var available=workCells.stream().filter(i->states[i]!=CORRECT&&states[i]!=IGNORED&&!floorDeferred(position(i))&&retryAt.getOrDefault(i,0)<=ticks).toList();
        if(available.isEmpty())return;
        var first=schematic.local(available.getFirst());
        var materials=new HashMap<Item,Integer>();var batch=new ArrayList<Integer>();int slots=0;
        for(int i:available){
            var p=schematic.local(i);
            if(p.getX()/8!=first.getX()/8||p.getY()/4!=first.getY()/4||p.getZ()/8!=first.getZ()/8)continue;
            var item=Schematic.material(desired(i));int before=materials.getOrDefault(item,0),after=before+unitsLeft[i];
            int extra=item==Items.AIR?0:(after+item.getMaxCount()-1)/item.getMaxCount()-(before+item.getMaxCount()-1)/item.getMaxCount();
            if(slots+extra>24||batch.size()>=128)continue;
            slots+=extra;materials.put(item,after);batch.add(i);
        }
        sectionCells=List.copyOf(batch);sectionProgressAt=ticks;sectionCorrect=correct;
    }
    private boolean hasTool(boolean shovel){for(int i=0;i<36;i++)if(mc.player.getInventory().getStack(i).isIn(shovel?ItemTags.SHOVELS:ItemTags.PICKAXES))return true;return false;}
    private void addRequiredTools(Map<Item,Integer> needs){if(autoTools.get()){if(!hasTool(false))needs.put(Items.DIAMOND_PICKAXE,1);if(!hasTool(true))needs.put(Items.DIAMOND_SHOVEL,1);}if(foodNeeded())needs.put(Items.COOKED_BEEF,steakReserve.getInt());}
    private boolean foodNeeded(){return autoEat.get()&&mc.player!=null&&!mc.player.getAbilities().creativeMode&&mc.player.getHungerManager().getFoodLevel()<=hungerLimit.getInt();}
    public Map<Item,Integer> remainingMaterials(){return Map.copyOf(requiredMaterials());}
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
        buildEta.reset();ignoredSolid=0;
        preparationReady=false;chestStocks.clear();preparedStock.clear();emptyChestItems.clear();buildBudgetActive=false;buildBudgetSpent=0;
        if(schematic==null)return;pause("Placement changed");transformedStates.clear();triedStands.clear();states=new byte[schematic.size()];unitsLeft=new byte[schematic.size()];scanCursor=correct=completedScans=passTasks=0;lastPassTasks=schematic.size();solid=schematic.solidCount();
        for(int i=0;i<schematic.size();i++)if(materialIgnored(schematic.state(i))&&!schematic.state(i).isAir()&&!schematic.state(i).isOf(Blocks.STRUCTURE_VOID))solid--;
        remaining.clear();remaining.putAll(schematic.materials());remaining.keySet().removeAll(ignoredMaterials);remainingByLayer.clear();potUnitsLeft.clear();activeLayer=-1;scanLayer=scanPhase=Integer.MAX_VALUE;activePhase=0;
        for(int i=0;i<schematic.size();i++){var item=Schematic.material(schematic.state(i));if(item!=Items.AIR&&!materialIgnored(schematic.state(i)))remainingByLayer.computeIfAbsent(taskLayer(i),y->new HashMap<>()).merge(item,Schematic.units(schematic.state(i)),Integer::sum);}
        for(int i=0;i<schematic.size();i++)if(potted(desired(i))&&!materialIgnored(desired(i)))remainingByLayer.computeIfAbsent(taskLayer(i),y->new HashMap<>()).merge(Items.FLOWER_POT,1,Integer::sum);
        for(int i=0;i<schematic.size();i++)if(potted(desired(i))&&materialIgnored(desired(i))){
            if(!materialIgnored(Items.FLOWER_POT))remaining.computeIfPresent(Items.FLOWER_POT,(item,count)->Math.max(0,count-1));
            var flower=Schematic.material(desired(i));if(!materialIgnored(flower))remaining.computeIfPresent(flower,(item,count)->Math.max(0,count-1));
        }
        visible=workCells=sectionCells=List.of();visibleScan.clear();workScan.clear();retryAt.clear();triedContainers.clear();routeSupportExclusions.clear();
    }
    private void applyPreset(String name){
        if(ghostFill==null)return;
        previewMode.set(name.equals("Blueprint")?"Full":"Missing & Wrong");textured.set(!name.equals("Verification"));renderCorrect.set(name.equals("Blueprint"));ghostFill.set(name.equals("Blueprint")?.35:.5);outlines.set(true);
    }
    public void preview(){if(schematic==null){notify("Choose a schematic first");return;}if(origin==null&&inGame())setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));setEnabled(true);preview=true;pause("Previewing "+schematic.name);}
    public void startBuild(){
        if(!inGame()||schematic==null||loading){notify("Join a world and load a schematic first");return;}
        if(world==null&&origin!=null&&dimension.equals(mc.world.getRegistryKey().getValue().toString())&&worldScope.equals(scope())){world=mc.world;rescanSavedPlacement();}
        if(activeBuildSlot>=0&&origin!=null&&world!=mc.world){notify("This placement belongs to another world — choose a saved build or set a new origin");return;}
        if(origin==null||world!=mc.world)setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));
        var miner=ModuleManager.get(AutoMine.class);if(miner!=null&&miner.isEnabled())miner.setEnabled(false);
        checkpoint();
        if(prebuyWhole.get()&&!preparationReady&&!mc.player.getAbilities().creativeMode){startPreparation();return;}
        buildEta.reset();setEnabled(true);building=true;preview=true;staffStopAt=0;checkpoint();triedContainers.clear();retryAt.clear();status=mode.is("Semi Auto")?"Hold right mouse to build":"Building";mc.setScreen(null);
    }
    private void startPreparation(){
        if(mc.player.currentScreenHandler!=mc.player.playerScreenHandler){notify("Close the current container before preparing supplies");return;}
        pause("Preparing supplies for the whole build");setEnabled(true);building=false;preview=true;
        if(supplyChests().isEmpty()){status="Look at your double chests and press R before starting";notify(status);return;}
        chestStocks.clear();preparedStock.clear();preparationReady=false;if(!buildBudgetActive)buildBudgetSpent=0;buildBudgetActive=true;spent=buildBudgetSpent;preparationStage=1;depositAll();
    }
    private Map<Item,Integer> wholeBuildNeeds(){
        var needs=new HashMap<>(remaining);needs.keySet().removeAll(ignoredMaterials);if(support.get())needs.merge(Items.DIRT,buyDirt.getInt(),Integer::sum);addRequiredTools(needs);
        if(autoTools.get()){if(preparedStock.entrySet().stream().anyMatch(e->e.getValue()>0&&e.getKey().getDefaultStack().isIn(ItemTags.PICKAXES)))needs.remove(Items.DIAMOND_PICKAXE);if(preparedStock.entrySet().stream().anyMatch(e->e.getValue()>0&&e.getKey().getDefaultStack().isIn(ItemTags.SHOVELS)))needs.remove(Items.DIAMOND_SHOVEL);}
        if(autoEat.get()&&buySteak.get())needs.put(Items.COOKED_BEEF,steakReserve.getInt());
        needs.replaceAll((item,count)->Math.max(0,count-preparedStock.getOrDefault(item,0)-inventoryCount(item)));needs.values().removeIf(count->count<=0);return needs;
    }
    public void restartBuild(){
        if(!inGame()||schematic==null||loading){notify("Join a world and load a schematic first");return;}
        replan();cleanupStands.clear();delay=inventoryWait=0;needed=null;lastAction=ticks;
        startBuild();
    }
    public void cancelSchematic(){
        checkpoint();activeBuildSlot=-1;CompletableFuture.runAsync(()->{try{savedBuilds.activate(-1);}catch(IOException error){Maro.LOGGER.warn("Could not clear active build slot",error);}},IO);
        ++ioGeneration;loading=false;pause("Schematic cancelled");setEnabled(false);preparationReady=false;chestStocks.clear();preparedStock.clear();emptyChestItems.clear();buildBudgetActive=false;buildBudgetSpent=0;
        schematic=null;unconfirmedPlacements.clear();latePlacements.clear();selected="";preview=false;captureStates=null;capture=null;
        states=unitsLeft=new byte[0];scanCursor=correct=solid=completedScans=passTasks=lastPassTasks=0;
        remaining.clear();remainingByLayer.clear();activeLayer=-1;scanLayer=Integer.MAX_VALUE;ignoredMaterials.clear();supports.clear();cleanupStands.clear();triedStands.clear();retryAt.clear();transformedStates.clear();
        visible=workCells=sectionCells=List.of();visibleScan.clear();workScan.clear();needed=null;delay=inventoryWait=0;staffStopAt=0;
        status="Schematic cancelled — choose another to start";
    }
    @Override protected void onEnable(){
        if(!inGame()){setEnabled(false);return;}
        originalSlot=mc.player.getInventory().getSelectedSlot();
        if(world==null&&origin!=null&&dimension.equals(mc.world.getRegistryKey().getValue().toString())&&worldScope.equals(scope())){world=mc.world;rescanSavedPlacement();}
        if(activeBuildSlot>=0&&origin!=null&&world!=mc.world){notify("This placement belongs to another world — choose a saved build or set a new origin");return;}
        if(origin==null||world!=mc.world)setOrigin(mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(),3));
        building=schematic!=null;preview=true;
    }
    @Override protected void onDisable(){pause("Disabled");staffStopAt=0;captureStates=null;if(mc.player!=null&&originalSlot>=0)select(originalSlot);originalSlot=-1;}
    public void pause(String reason){
        buildEta.tick(System.nanoTime()/1_000_000,false);
        if(pendingPlacement!=null&&inGame()&&world==mc.world&&(pendingServerState==null||!compatible(pendingServerState,pendingPlacement.state)))
            reconcilePrediction(pendingPlacement,pendingServerState);
        queuedLookAction=null;
        lookWaitStarted=-1;queuedAimPoint=aimPoint=null;
        bridgeTarget=null;
        supportPickup=null;supportPickupUntil=supportRecycleAt=0;
        yawVelocity=pitchVelocity=0;
        placementAttemptTarget=null;failedPlacementUntil.clear();unexpectedBuildHandler=null;resetChestJourney();
        stopEating();navigatingCell=-1;foodRestock=foodShopping=supportRestock=supportShopping=false;
        preparationStage=0;depositQueue.clear();
        if(partialSource>=0&&ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler&&!ownedHandler.getCursorStack().isEmpty())mc.interactionManager.clickSlot(ownedHandler.syncId,partialSource,0,SlotActionType.PICKUP,mc.player);
        partialSource=-1;partialItem=null;
        building=false;pasting=false;depositing=false;depositTarget=null;depositSlot=-1;resumeShoppingAfterDeposit=false;depositedShopping.clear();placement=pendingPlacement=null;pendingServerState=null;routeMining=null;mining=null;tuningTarget=tuningSession=null;tuningClicks=0;standGoal=null;descentPost=descentView=null;descentLanding=false;accessStand=null;recycleTarget=null;accessFloor=false;accessSupports.clear();viewSearches.clear();digging=false;walker.stop();releaseSneak();endRecovery();
        if(mc.interactionManager!=null)mc.interactionManager.cancelBlockBreaking();
        if(ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler&&ownedHandler.getCursorStack().isEmpty())mc.player.closeHandledScreen();
        ownedHandler=null;restockTarget=null;routeOpening=null;restockBatch=Map.of();chestAccessRetryAt.clear();buying=false;pendingOffer=null;shopping.clear();status=reason;
        floorSearchFeet=null;floorSearchCursor=0;
        passageBlocks.clear();passageStand=passageSearchFeet=null;passageSearchWork=-2;passageSearchCursor=passageRetryAt=0;
        descentSearchFeet=descentSearchDestination=null;descentSearchPosts=descentSearchViews=descentHatchViews=List.of();descentSearchCursor=descentRetryAt=descentSearchPhase=0;descentHatchesReady=false;
        hatchSearchFeet=null;hatchCandidates=List.of();hatchViews.clear();hatchSearchCursor=hatchExitCursor=0;
        descentEscapes.clear();
        recycleSearchFeet=null;recyclePosts=List.of();recycleViews.clear();recyclePostCursor=recycleViewCursor=recycleRouteCursor=recycleRetryAt=0;
        floorAccessWork.clear();
    }
    private void notify(String message){Notifications.push("Auto Builder",message,Notifications.Type.INFO,5000);if(mc.player!=null)mc.player.sendMessage(Text.literal("[Auto Builder] "+message),false);}
    public void onActionBind(int key){if(markBind.matches(key)&&(isEnabled()||schematic!=null)){markContainer();return;}if(isEnabled()&&buyBind.matches(key))startBuying(false);}
    public void markContainer(){
        if(!inGame()||!(mc.crosshairTarget instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return;
        BlockPos pos=hit.getBlockPos();var block=mc.world.getBlockState(pos).getBlock();
        if(!doubleChest(pos)){notify("Look at the double chest you want to use");return;}
        if(world!=mc.world)setOrigin(mc.player.getBlockPos());
        var partner=pos.offset(ChestBlock.getFacing(mc.world.getBlockState(pos)));var existing=containers.stream().filter(p->p.equals(pos)||p.equals(partner)).findFirst().orElse(null);
        boolean remove=mc.player.isSneaking()||mc.options.sneakKey.isPressed();pause("Supply chest selection changed");preparationReady=false;chestStocks.clear();preparedStock.clear();triedContainers.clear();emptyChestItems.clear();
        if(remove){if(existing!=null)containers.remove(existing);notify("Supply chest removed — "+containers.size()+" selected");}
        else{if(existing==null)containers.add((pos.compareTo(partner)<0?pos:partner).toImmutable());notify("Supply chest added / refreshed — "+containers.size()+" selected");}
    }
    private void tickWork(){
        ticks++;supportChainStarts.clear();viewPlanningDeadline=floorPlanningDeadline=0;if(delay>0)delay--;
        latePlacements.values().removeIf(receipt->receipt.expires<=ticks);
        failedPlacementUntil.values().removeIf(until->until<=ticks);
        routeSupportExclusions.values().removeIf(until->until<=ticks);
        walker.turning(smoothTurning.get(),turnSpeed.getFloat());
        if(!inGame()||world!=mc.world){pause("World changed — set the origin again");world=null;return;}
        if(!mc.player.isAlive()||mc.player.isSpectator()){pause("Player is not able to build");return;}
        buildEta.tick(System.nanoTime()/1_000_000,building&&(!mode.is("Semi Auto")||mc.options.useKey.isPressed()));
        scan();captureTick();
        if(staffStopAt>0){if(logoff.get()&&System.currentTimeMillis()-staffStopAt>=logoffDelay.get()*1000){mc.world.disconnect(Text.literal(logoffMessage.get()));setEnabled(false);}return;}
        if((building||buying||pasting||depositing)&&unsafe()){walker.release();return;}
        if(building&&!buying&&!depositing&&recoverUnexpectedBuildMenu())return;
        if(pendingPlacement!=null){placementReceiptTick();return;}
        if(mc.currentScreen==null&&(building||depositing)&&clearPassageTick())return;
        if(mc.currentScreen==null&&(building||depositing)&&clearRouteSupportTick())return;
        if(building&&!buying&&mc.currentScreen==null&&restockTarget==null&&eatTick())return;
        if(mc.currentScreen==null&&(depositing||building&&(!mode.is("Semi Auto")||mc.options.useKey.isPressed()))&&recoveryTick())return;
        if(buying){marketTick();return;}
        if(depositing){depositTick();return;}
        if(pasting){pasteTick();return;}
        if(!building||schematic==null||origin==null||loading)return;
        if(restockTarget!=null){restockTick();return;}
        if(mc.currentScreen!=null){walker.release();digging=false;return;}
        if(mode.is("Semi Auto")&&!mc.options.useKey.isPressed()){walker.release();digging=false;status="Hold right mouse to build";return;}
        if(mc.player.isUsingItem()){walker.release();return;}
        if(delay>0){walker.release();return;}
        if(placement!=null){placeTick();return;}
        if(mining!=null){mineTick();return;}
        if(followStandGoal())return;
        if(continueAccess())return;
        if(autoTools.get()&&!mc.player.getAbilities().creativeMode&&(!hasTool(false)||!hasTool(true))){
            needed=!hasTool(false)?Items.DIAMOND_PICKAXE:Items.DIAMOND_SHOVEL;
            if(restock.get()&&beginRestock()){status="Checking selected chests for build tools";return;}
            if(autoBuy.get()&&maxSpend.get()>0){startBuying(false);if(buying)resumeAfterMarket=true;return;}
        }
        findWork();
    }
    /** Follow the same verified stand/descent intent during building and chest trips. */
    private boolean followStandGoal(){
        if(standGoal!=null){
            if(descentLanding){
                var feet=BlockPos.ofFloored(mc.player.getEntityPos().add(0,.4,0));
                // The route search allows two-block walking drops. Do not reject
                // a checked three-block fall while vanilla gravity is still
                // landing us on its owned post, or replace it with an up-pillar.
                if(!mc.player.isOnGround()||!walker.canStand(feet)){walker.release();status="Landing after checked scaffold descent";return true;}
                descentLanding=false;standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();
            }
            if(standProgressPos==null||mc.player.getEntityPos().subtract(standProgressPos).horizontalLengthSquared()>.04||mc.player.isOnGround()&&Math.abs(mc.player.getY()-standProgressPos.y)>.2){standProgressPos=mc.player.getEntityPos();standProgressAt=ticks;}
            if(walker.standAt(standGoal)){
                if(recycleTarget!=null){
                    var target=recycleTarget;recycleTarget=null;standGoal=null;walker.stop();
                    if(supports.contains(target)&&!servesActiveScaffold(target)&&safeToRecycle(target)&&visibleHit(target)!=null){
                        routeSupportExclusions.put(target,ticks+600);routeMining=mining=target;status="Reusing temporary support capacity";return true;
                    }
                    return true;
                }
                if(descentPost!=null&&standGoal.equals(descentPost.up())){
                    if(mc.player.getVelocity().horizontalLengthSquared()>=.0001){status="Settling before scaffold descent";return true;}
                    var post=descentPost;var lower=descentView;descentPost=descentView=null;
                    if(lower!=null&&walker.standingPoint(lower).y<mc.player.getY()-.5&&post.equals(mc.player.getBlockPos().down())&&removableRouteFloor(post)
                        &&descentReaches(post,lower)&&safeToRecycle(post)){
                        standGoal=lower;descentLanding=true;routeMining=mining=post;walker.stop();status="Descending temporary scaffold";return true;
                    }
                }
                if(standGoal.equals(accessStand)){accessStand=null;accessFloor=false;accessSupports.clear();}
                else accessSupports.removeIf(pos->pos.getY()<mc.player.getY());
                standGoal=null;navigationStarted=ticks;walker.stop();
            }
            else if(ticks-standProgressAt>50||ticks-standStarted>240||walker.routeUnavailable()){
                if(navigatingCell>=0){retryAt.put(navigatingCell,ticks+10);triedStands.computeIfAbsent(navigatingCell,i->new HashMap<>()).put(standGoal,ticks+600);}
                if(recycleTarget!=null){recycleTarget=null;standGoal=null;walker.stop();status="Replanning temporary support pickup";return true;}
                navigatingCell=-1;standGoal=null;descentPost=descentView=null;accessStand=null;accessFloor=false;accessSupports.clear();walker.stop();status="Replanning blocked build position";
            }else status=walker.status;
            return true;
        }
        return false;
    }
    private void stopEating(){
        if(ownsFoodUse)mc.options.useKey.setPressed(false);ownsFoodUse=false;
        if(eating&&mc.player!=null&&mc.player.isUsingItem()&&mc.player.getActiveItem().isOf(Items.COOKED_BEEF)&&mc.interactionManager!=null)mc.interactionManager.stopUsingItem(mc.player);
        eating=false;if(eatPreviousSlot>=0)select(eatPreviousSlot);eatPreviousSlot=-1;
    }
    private boolean eatTick(){
        if(eating){
            walker.release();
            if(inventoryCount(Items.COOKED_BEEF)<eatBefore){stopEating();delay=6;status="Steak eaten — resuming build";return true;}
            if(ticks>=eatDeadline){pause("Eating was not confirmed — paused");return true;}
            status="Eating steak";return true;
        }
        if(!foodNeeded()){if(eatPreviousSlot>=0)stopEating();return false;}
        if(mc.player.isUsingItem())return false;
        walker.release();releaseSneak();if(digging){mc.interactionManager.cancelBlockBreaking();digging=false;}endRecovery();
        if(inventoryCount(Items.COOKED_BEEF)==0){
            needed=Items.COOKED_BEEF;
            if(restock.get()&&beginRestock()){foodRestock=true;status="Checking chests for steak";return true;}
            if(buySteak.get()&&maxSpend.get()>0){boolean resume=building;startBuying(false);if(buying){foodShopping=true;resumeAfterMarket=resume;}return true;}
            status=buySteak.get()?"Hungry — add steak or set an AH budget":"Hungry — add steak to inventory or a chest";return true;
        }
        if(eatPreviousSlot<0)eatPreviousSlot=mc.player.getInventory().getSelectedSlot();
        if(delay>0)return true;
        if(!selectMaterial(Items.COOKED_BEEF)){status="Moving steak to hotbar";return true;}
        eatBefore=inventoryCount(Items.COOKED_BEEF);
        if(mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND).isAccepted()){
            eating=true;eatDeadline=ticks+100;ownsFoodUse=!mc.options.useKey.isPressed();mc.options.useKey.setPressed(true);status="Eating steak";
        }else pause("Unable to eat steak — paused");return true;
    }
    private void endRecovery(){if(recoveryJump)mc.options.jumpKey.setPressed(false);recoveryJump=false;recoveryPhase=0;recoveryBase=null;}
    private boolean clearRouteSupportTick(){
        if(buying||restockTarget!=null&&(ownedHandler!=null||restockWait>0))return false;
        if(routeMining!=null){
            if(!removableRouteFloor(routeMining)){supports.remove(routeMining);boolean escaped=escapeSupports.remove(routeMining);if(routeMining.equals(routeOpening))routeOpening=null;cleanupStands.clear();triedStands.clear();chestTriedStands.clear();chestProgressAt=ticks;routeMining=null;mining=null;digging=false;mc.interactionManager.cancelBlockBreaking();walker.stop();if(!escaped)recoveryAttempts=Math.min(recoveryAttempts,2);delay=actionDelay();return true;}
            mining=routeMining;mineTick();if(mining==null)routeMining=null;return true;
        }
        if(!walker.routeUnavailable()&&!walker.movementStalled())return false;
        var obstruction=walker.blockingSupport(supports);
        if(obstruction==null){var footing=mc.player.getBlockPos().down();if(descendingOwnedSupport(footing))obstruction=footing;}
        // Failed movement is not proof that the step we are currently assembling
        // should be deleted. Preserve committed stairs and escape footing until
        // the target is complete; explicit checked descents have their own intent.
        if(obstruction==null||servesActiveScaffold(obstruction)||escapeSupports.contains(obstruction)||!safeToRecycle(obstruction)||visibleHit(obstruction)==null)return false;
        routeSupportExclusions.put(obstruction,ticks+600);
        routeMining=obstruction;mining=obstruction;walker.release();status="Clearing temporary block from route";mineTick();return true;
    }
    private boolean recoveryTick(){
        if(descentLanding)return false;
        if(recoveryPhase==0){
            // Keep escape steps until completion: deleting them mid-route can destroy the return path.
            boolean committedClimb=accessFloor&&accessStand!=null&&accessStand.getX()==mc.player.getBlockX()&&accessStand.getZ()==mc.player.getBlockZ()&&accessStand.getY()>mc.player.getY()&&ticks-accessProgressAt<=600&&ticks-accessStarted<=2400;
            if(!unstuck.get()||!autoMove.get()||!walker.needsRecovery()||ticks<recoveryCooldown||recoveryAttempts>=3&&!committedClimb||schematic==null||!mc.player.isOnGround())return false;
            var feet=mc.player.getBlockPos();if(!walker.canPillar(feet)||!mc.world.getBlockState(feet).isReplaceable())return false;
            // A failed descent cannot be repaired by climbing farther above it.
            // Recycling capacity must not restart an endless upward pillar loop.
            var recoveryTarget=recoveryDestination();
            if(recoveryTarget==null||feet.getY()>=recoveryTarget.getY())return false;
            if(committedClimb&&reserveAccessCapacity())return true;
            int cell=schematic.indexAt(feet.subtract(anchor()),turns(),mirror.get());if(cell>=0&&!desired(cell).isAir()&&!desired(cell).isOf(Blocks.STRUCTURE_VOID)&&!desired(cell).isOf(Blocks.DIRT))return false;
            if(supports.size()>=tempDirt.getInt())return building&&support.get()&&recycleSupport();
            if(inventoryCount(Items.DIRT)==0){if(building){ensureSupportDirt();return true;}return false;}
            walker.stop();recoveryBase=feet;recoveryStarted=ticks;recoveryPhase=1;recoveryAttempts++;placement=null;mining=null;
        }
        if(ticks-recoveryStarted>60){endRecovery();recoveryCooldown=ticks+80;status="Unstuck step could not be placed — move or add a step";return true;}
        walker.release();
        if(recoveryPhase==1){
            if(!walker.centerForJump(recoveryBase)){status="Centering before temporary step";return true;}
            walker.stop();
            if(!selectMaterial(Items.DIRT)||!aim(Vec3d.ofCenter(recoveryBase.down()).add(0,.5,0))){status="Preparing temporary unstuck step";return true;}
            mc.options.jumpKey.setPressed(true);recoveryJump=true;recoveryPhase=2;status="Jumping out of stuck position";return true;
        }
        if(recoveryPhase==2){
            if(supports.contains(recoveryBase)&&mc.world.getBlockState(recoveryBase).isOf(Blocks.DIRT)){
                escapeSupports.add(recoveryBase);mc.options.jumpKey.setPressed(false);recoveryJump=false;recoveryPhase=3;status="Landing on confirmed temporary step";return true;
            }
            if(mc.player.getY()<recoveryBase.getY()+1.01)return true;
            var job=placement(recoveryBase,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true);
            if(job!=null){placement=job;placeTick();}return true;
        }
        if(mc.player.isOnGround()&&mc.world.getBlockState(recoveryBase).isOf(Blocks.DIRT)){endRecovery();walker.stop();recoveryCooldown=ticks+40;status="Temporary step placed — replanning route";}
        return true;
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
            // Our scaffolding in an air cell is a cleanup task, not an unfinished lower layer.
            // Otherwise that layer waits for cleanup while cleanup waits for upper layers.
            boolean cleanupOnly=expected.isAir()&&supports.contains(position(i))||floorDeferred(position(i));
            if(!cleanupOnly&&layerAllows(i)&&schematic.included(i)&&(!expected.isAir()||mineOut.get())&&states[i]!=CORRECT&&states[i]!=IGNORED){
                passTasks++;int phase=taskPhase(i),y=taskLayer(i);
                // An observer can pulse a partially assembled machine and destroy its
                // already placed shulker boxes while a note block is being tuned.
                // Assemble and configure the structure, then fluids, then observers.
                if(phase<scanPhase){scanPhase=phase;scanLayer=Integer.MAX_VALUE;workScan.clear();}
                if(phase==scanPhase){
                    if(y<scanLayer){scanLayer=y;if(layerSupply())workScan.clear();}
                    if(!layerSupply()||y==scanLayer){var candidate=new Visible(i,distance+y*.3+(sectionSupply()&&retryAt.getOrDefault(i,0)>ticks?1e7:0));if(workScan.size()<256)workScan.add(candidate);else if(candidate.distance<workScan.peek().distance){workScan.poll();workScan.add(candidate);}}
                }
            }
            if(!expected.isAir()&&!expected.isOf(Blocks.STRUCTURE_VOID)&&distance<=previewRange.get()*previewRange.get()&&layerAllows(i)){
                var cell=new Visible(i,distance);int limit=renderLimit.getInt();
                if(visibleScan.size()<limit)visibleScan.add(cell);else if(distance<visibleScan.peek().distance){visibleScan.poll();visibleScan.add(cell);}
            }
            if(scanCursor==states.length){
                scanCursor=0;completedScans++;lastPassTasks=passTasks;passTasks=0;activeLayer=scanLayer==Integer.MAX_VALUE?-1:scanLayer;activePhase=scanPhase;scanLayer=scanPhase=Integer.MAX_VALUE;
                visible=visibleScan.stream().sorted(Comparator.comparingDouble(Visible::distance).reversed()).map(Visible::index).toList();visibleScan.clear();
                workCells=workScan.stream().sorted(Comparator.comparingDouble(Visible::distance)).map(Visible::index).toList();workScan.clear();
                refreshSection();
            }
        }while(++count<8192&&System.nanoTime()<deadline&&scanCursor!=0);
    }
    private byte classify(int i){
        var wanted=desired(i);if(wanted.isOf(Blocks.STRUCTURE_VOID)||materialIgnored(wanted))return IGNORED;
        var pos=position(i);if(!mc.world.isChunkLoaded(pos))return UNKNOWN;
        if(pendingPlacement!=null&&pendingPlacement.target.equals(pos)&&pendingServerState==null)return MISSING;
        var uncertain=unconfirmedPlacements.get(pos);
        if(uncertain!=null){
            if(mc.world.getBlockState(pos).getBlock()==uncertain.state.getBlock())return MISSING;
            if(pendingPlacement==null||!pendingPlacement.target.equals(pos))unconfirmedPlacements.remove(pos);
        }
        var actual=mc.world.getBlockState(pos);
        if(wanted.isOf(Blocks.NOTE_BLOCK)&&actual.isOf(Blocks.NOTE_BLOCK)&&wanted.get(NoteBlock.NOTE).equals(actual.get(NoteBlock.NOTE)))return CORRECT;
        return matchesBuildState(actual,wanted)||wanted.isAir()&&actual.isAir()?CORRECT:actual.isAir()||actual.isReplaceable()?MISSING:actual.getBlock()==wanted.getBlock()?WRONG_STATE:WRONG_BLOCK;
    }
    private void updateState(int index){
        byte next=classify(index),old=states[index];
        var expected=desired(index);if(!expected.isAir()&&!expected.isOf(Blocks.STRUCTURE_VOID)){
            if(potted(expected)&&mc.world.isChunkLoaded(position(index))&&!materialIgnored(expected)){
                int before=potUnitsLeft.getOrDefault(index,1),after=mc.world.getBlockState(position(index)).getBlock() instanceof FlowerPotBlock?0:1;
                potUnitsLeft.put(index,after);int change=before-after;
                remaining.compute(Items.FLOWER_POT,(item,count)->Math.max(0,(count==null?0:count)-change));
                remainingByLayer.computeIfAbsent(taskLayer(index),y->new HashMap<>()).compute(Items.FLOWER_POT,(item,count)->Math.max(0,(count==null?0:count)-change));
            }
            int change=(next==CORRECT?1:0)-(old==CORRECT?1:0);correct+=change;
            if(change!=0&&old!=UNKNOWN&&next!=UNKNOWN&&building)buildEta.progress(change);
            ignoredSolid+=(next==IGNORED?1:0)-(old==IGNORED?1:0);
            var item=Schematic.material(expected);int oldUnits=old==UNKNOWN?Schematic.units(expected):unitsLeft[index],newUnits=next==CORRECT||next==IGNORED?0:Schematic.units(expected);
            if(newUnits==2&&mc.world.isChunkLoaded(position(index))){var actual=mc.world.getBlockState(position(index));if(actual.getBlock()==expected.getBlock()&&actual.contains(net.minecraft.state.property.Properties.SLAB_TYPE)&&actual.get(net.minecraft.state.property.Properties.SLAB_TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)newUnits=1;}
            unitsLeft[index]=(byte)newUnits;int delta=oldUnits-newUnits;
            if(item!=Items.AIR&&delta!=0&&!materialIgnored(item)){
                remaining.compute(item,(k,v)->Math.max(0,(v==null?0:v)-delta));remainingByLayer.computeIfAbsent(taskLayer(index),y->new HashMap<>()).compute(item,(k,v)->Math.max(0,(v==null?0:v)-delta));
            }
        }
        states[index]=next;
    }
    private double effectiveReach(){return Math.min(reach.get(),mc.player.getBlockInteractionRange()-.1);}
    private boolean floorDeferred(BlockPos pos){
        var work=floorAccessWork.get(pos);if(work==null)return false;
        if(work<0||work>=states.length||states[work]==CORRECT||states[work]==IGNORED){floorAccessWork.remove(pos);return false;}
        return true;
    }
    /** A block's nearest face can be reachable even when its centre is not. */
    private boolean withinReach(BlockPos pos,Vec3d eye){
        double dx=eye.x-MathHelper.clamp(eye.x,pos.getX(),pos.getX()+1);
        double dy=eye.y-MathHelper.clamp(eye.y,pos.getY(),pos.getY()+1);
        double dz=eye.z-MathHelper.clamp(eye.z,pos.getZ(),pos.getZ()+1);
        return dx*dx+dy*dy+dz*dz<=effectiveReach()*effectiveReach();
    }
    private void findWork(){
        if(completedScans==0){status="Checking schematic: "+(100L*scanCursor/Math.max(1,states.length))+"%";return;}
        if((lastPassTasks==0||correct==solid&&!supports.isEmpty())&&(!supports.isEmpty()||ticks-lastAction>=20)){
            if(cleanup.get()&&!supports.isEmpty()){
                // Keep low access stairs until upper pieces are gone. Within one
                // height, clear nearby pieces to avoid criss-crossing the build.
                var pos=supports.stream().min(Comparator.<BlockPos>comparingInt(p->-p.getY())
                    .thenComparingInt(p->new Box(p).intersects(mc.player.getBoundingBox().offset(0,-1,0))?1:0)
                    .thenComparingDouble(p->p.getSquaredDistance(mc.player.getBlockPos()))).orElseThrow();
                if(!mc.world.isChunkLoaded(pos)){status="Cleanup paused — support chunk is unloaded";return;}
                if(!mc.world.getBlockState(pos).isOf(Blocks.DIRT)){supports.remove(pos);cleanupStands.remove(pos);return;}
                int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());
                if(cell>=0&&desired(cell).isOf(Blocks.DIRT)){supports.remove(pos);return;}
                if(!withinReach(pos,mc.player.getEyePos())){
                    if(autoMove.get()){walker.approach(pos,effectiveReach()-.75);status="Returning to temporary supports";}else status="Move closer to clean temporary supports";return;
                }
                if(new Box(pos).intersects(mc.player.getBoundingBox().offset(0,-1,0))||visibleHit(pos)==null){
                    if(autoMove.get()&&repositionTarget(pos,cleanupStands.computeIfAbsent(pos,p->new HashMap<>())))status="Moving to clean temporary support";
                    else status="Cleanup needs a clear path — move off / around the support";return;
                }
                mining=pos;mineTick();return;
            }
            checkpoint();pause("Build complete");notify("Build complete: "+solid+" blocks");if(depositWhen.is("After Build"))depositAll();return;
        }
        List<Integer> candidates=new ArrayList<>();
        // A local neighborhood is cheap even for multi-million-cell schematics; global nearest cells
        // from the scan are appended for walking. A candidate's actual world state is rechecked below.
        for(int i:sectionSupply()?sectionCells:workCells)if(layerAllows(i)&&!floorDeferred(position(i))&&taskPhase(i)==activePhase&&(!layerSupply()||taskLayer(i)==supplyLayer())&&states[i]!=CORRECT&&states[i]!=IGNORED&&retryAt.getOrDefault(i,0)<=ticks)candidates.add(i);
        if(navigatingCell>=0&&(states[navigatingCell]==CORRECT||!candidates.contains(navigatingCell)||ticks-navigationStarted>240)){navigatingCell=-1;walker.stop();}
        candidates.sort(Comparator.<Integer>comparingInt(i->i==navigatingCell?0:1).thenComparingDouble(i->taskLayer(i)*(sectionSupply()?8:100)+position(i).getSquaredDistance(mc.player.getBlockPos())));
        needed=null;Integer distant=null;List<Integer> blocked=new ArrayList<>();
        long deadline=System.nanoTime()+2_000_000;int checked=0;
        for(int i:candidates){
            if(checked++>=96||System.nanoTime()>deadline)break;
            updateState(i);if(states[i]==CORRECT||states[i]==UNKNOWN)continue;
            var target=position(i);var desired=desired(i);var actual=mc.world.getBlockState(target);
            if(waitingForBuiltNeighbour(target,desired))continue;
            if(desired.isAir()&&supports.contains(target)&&correct!=solid)continue;
            if(desired.isAir()&&!mineOut.get()||Schematic.companion(desired))continue;
            if(!withinReach(target,mc.player.getEyePos())){
                // A scaffold view reaches its first piece, not necessarily the
                // final block. Complete that native job before routing away again.
                if(i==navigatingCell&&!desired.isAir()&&inventoryCount(Schematic.material(desired))>0&&!hasAttachment(target,desired)){
                    var first=supportPlacement(target,mc.player.getEyePos(),mc.player.getBoundingBox());
                    if(first!=null){if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return;}navigationStarted=ticks;commitAccess(first.target.up(),false);accessSupports.add(first.target);placement=first;placeTick();return;}
                }
                if(distant==null)distant=i;continue;
            }
            if(actual.isOf(Blocks.NOTE_BLOCK)&&desired.isOf(Blocks.NOTE_BLOCK)){tuneNote(target,desired.get(NoteBlock.NOTE));return;}
            if(actual.getBlock()==desired.getBlock()&&compatible(actual,desired)&&configuration(actual)!=configuration(desired)){tuneConfiguration(target,configuration(desired));return;}
            if(desired.isAir()&&mineOut.get()){
                if(!actual.getFluidState().isEmpty()||protectContainers.get()&&actual.hasBlockEntity()||actual.getHardness(mc.world,target)<0){retryAt.put(i,ticks+200);continue;}
                mining=target;mineTick();return;
            }
            if(!actual.isReplaceable()&&!actual.isAir()&&!(potted(desired)&&actual.isOf(Blocks.FLOWER_POT))&&!(actual.getBlock()==desired.getBlock()&&desired.contains(net.minecraft.state.property.Properties.SLAB_TYPE)&&desired.get(net.minecraft.state.property.Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE)){
                if(!mineOut.get()&&!replaceWrong.get()&&!(states[i]==WRONG_STATE&&repairStates.get()))continue;
                if(protectContainers.get()&&actual.hasBlockEntity()||actual.getHardness(mc.world,target)<0)continue;
                mining=target;mineTick();return;
            }
            if(desired.isAir())continue;
            var item=potted(desired)&&!actual.isOf(Blocks.FLOWER_POT)?Items.FLOWER_POT:Schematic.material(desired);if(item==Items.AIR){retryAt.put(i,ticks+200);continue;}
            if(inventoryCount(item)==0){if(needed==null)needed=item;continue;}
            var plan=placement(target,desired,item,i,false);
            if(plan!=null){placement=plan;placeTick();return;}
            // Finish a column with a usable attachment before switching targets. Leaving
            // it halfway through can obstruct its own final face with another column.
            if(hasAttachment(target,desired)){
                if(autoMove.get()&&(reposition(i)||walker.needsRecovery()&&recoveryAttempts<3))return;
                blocked.add(i);continue;
            }
            var scaffold=supportPlacement(target,mc.player.getEyePos(),mc.player.getBoundingBox());
            if(scaffold!=null){if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return;}navigatingCell=i;navigationStarted=ticks;placement=scaffold;placeTick();return;}
            blocked.add(i);
        }
        if(needed!=null){if(restock.get()&&beginRestock())return;if(autoBuy.get()&&maxSpend.get()>0){startBuying(false);return;}status="Missing "+needed.getName().getString()+" — check Materials";walker.release();return;}
        if(autoMove.get())for(int i:blocked){if(reposition(i))return;if(System.nanoTime()>deadline+8_000_000)break;}
        if(distant!=null&&autoMove.get()){
            if(navigatingCell!=distant){navigatingCell=distant;navigationStarted=ticks;}
            var target=position(distant);if(!walker.approach(target,effectiveReach()-.75)){if(walker.routeUnavailable()&&reposition(distant))return;status=walker.status;return;}
        }else walker.release();
        status=lastPassTasks==0?"Verifying completed build":"No placeable target — move closer, add supports, or inspect wrong states";
    }
    private boolean reposition(int index){
        var target=position(index);var tried=triedStands.computeIfAbsent(index,i->new HashMap<>());
        if(repositionTarget(target,tried)){navigatingCell=index;navigationStarted=ticks;return true;}retryAt.put(index,ticks+10);walker.requestRecovery();return false;
    }
    private boolean hasAttachment(BlockPos target,BlockState wanted){
        if(potted(wanted))return hasAttachment(target,Blocks.FLOWER_POT.getDefaultState());
        var item=Schematic.material(wanted);if(!(item instanceof BlockItem blockItem))return false;
        float[] facings=new float[wanted.contains(net.minecraft.state.property.Properties.ROTATION)?16:4];
        for(int direction=0;direction<facings.length;direction++)facings[direction]=facings.length==16?direction*22.5f-180:direction*90;
        float yaw=mc.player.getYaw(),pitch=mc.player.getPitch();
        try{
            for(var side:Direction.values()){
                var neighbor=target.offset(side.getOpposite());var state=mc.world.getBlockState(neighbor);
                if(state.isAir()||state.isReplaceable()||!state.getFluidState().isEmpty())continue;
                // Bottom and top trapdoors/slabs need different hit heights. Door
                // hinges also need positions on both sides of the face centre.
                for(double[] sample:new double[][]{{.25,.25},{.75,.75},{.5,.5}}){
                    var point=Vec3d.ofCenter(neighbor).add(Vec3d.of(side.getVector()).multiply(.5));
                    if(side.getAxis()==Direction.Axis.Y)point=new Vec3d(neighbor.getX()+sample[0],point.y,neighbor.getZ()+sample[1]);
                    else point=new Vec3d(side.getAxis()==Direction.Axis.Z?neighbor.getX()+sample[1]:point.x,neighbor.getY()+sample[0],side.getAxis()==Direction.Axis.X?neighbor.getZ()+sample[1]:point.z);
                    var hit=new BlockHitResult(point,side,neighbor,false);
                    for(float facing:facings)for(float tilt:new float[]{0,80,-80}){
                        mc.player.setYaw(facing);mc.player.setPitch(tilt);
                        var context=blockItem.getPlacementContext(new ItemPlacementContext(mc.player,Hand.MAIN_HAND,new ItemStack(item),hit));
                        if(context==null||!context.getBlockPos().equals(target))continue;
                        // Test the attachment without the player's current collision.
                        // An occupied target still needs a valid view to step aside to.
                        var predicted=wanted.getBlock().getPlacementState(context);
                        if(compatible(predicted,wanted)&&predicted.canPlaceAt(mc.world,target))return true;
                    }
                }
            }
            return false;
        }finally{mc.player.setYaw(yaw);mc.player.setPitch(pitch);}
    }
    private boolean repositionTarget(BlockPos target,Map<BlockPos,Integer> tried){
        return repositionTarget(target,tried,target.equals(bridgeTarget));
    }
    private boolean repositionTarget(BlockPos target,Map<BlockPos,Integer> tried,boolean bridge){
        tried.values().removeIf(until->until<=ticks);
        int cell=schematic.indexAt(target.subtract(anchor()),turns(),mirror.get());
        var wanted=cell<0?null:desired(cell);
        boolean supportFallback=wanted!=null&&!hasAttachment(target,wanted);
        boolean extendedScaffold=supportFallback&&(bridge||!straightSupportBase(target,attachmentSide(wanted)));
        int below=(int)Math.floor(effectiveReach()+mc.player.getStandingEyeHeight()-.5),above=(int)Math.floor(effectiveReach()-mc.player.getStandingEyeHeight()+.5);
        if(extendedScaffold)below=Math.max(below,8);
        var key=new ViewKey(target,mc.player.getBlockPos().toImmutable(),bridge);
        var search=viewSearches.get(key);
        if(search==null||search.expires>0&&ticks>search.expires){search=new ViewSearch();viewSearches.put(key,search);}
        while(viewSearches.size()>24)viewSearches.remove(viewSearches.keySet().iterator().next());
        var options=search.options;var directStands=search.direct;var scaffoldDistance=search.scaffoldDistance;
        if(viewPlanningDeadline==0)viewPlanningDeadline=System.nanoTime()+2_000_000;
        int heights=below+above+1,total=49*heights;
        // Retain the enumeration cursor across ticks. Repeatedly ray-testing every
        // hypothetical view in one tick freezes the client on difficult layer tails.
        while(search.cursor<total&&System.nanoTime()<viewPlanningDeadline){
            int sample=search.cursor++;int dy=sample%heights-below,dx=sample/heights/7-3,dz=sample/heights%7-3;
            var stand=target.add(dx,dy,dz);
            // A hotbar transfer can defer the next stair piece without changing
            // geometry. Keep the committed view eligible during its retry window.
            if(tried.containsKey(stand)&&!stand.equals(accessStand)||!walker.canStand(stand)||mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(stand))<.04)continue;
            Vec3d eye=walker.standingPoint(stand).add(0,mc.player.getStandingEyeHeight(),0);
            if(!extendedScaffold&&!withinReach(target,eye))continue;
            var body=mc.player.getBoundingBox().offset(eye.subtract(mc.player.getEyePos()));
            boolean direct=!mc.world.getBlockState(target).isReplaceable()?visibleHit(target,eye)!=null
                :wanted!=null&&placement(target,wanted,Schematic.material(wanted),cell,false,eye,body)!=null;
            // A connected bridge can start well below the final floating block.
            // Its first piece, rather than the final target, must be in reach.
            if(!direct){
                var scaffold=supportFallback?supportPlacement(target,eye,body,bridge):null;
                if(scaffold==null)continue;
                scaffoldDistance.put(stand,scaffold.target.getManhattanDistance(target));
            }
            if(direct)directStands.add(stand);
            options.add(stand);
        }
        if(search.cursor<total){walker.release();status="Checking placement access";return true;}
        if(search.expires==0)search.expires=ticks+120;
        options.sort(Comparator.<BlockPos>comparingInt(p->p.equals(accessStand)?-1:directStands.contains(p)?0:1)
            .thenComparingInt(p->scaffoldDistance.getOrDefault(p,0)).thenComparingDouble(p->p.getSquaredDistance(mc.player.getBlockPos())));standGoal=null;
        long routeDeadline=System.nanoTime()+6_000_000;
        while(search.routeCursor<options.size()){
            var option=options.get(search.routeCursor++);
            if(tried.containsKey(option)&&!option.equals(accessStand)||!walker.canStand(option))continue;
            tried.put(option,ticks+40);if(walker.canReachStand(option)){standGoal=option;break;}
            if(support.get()&&supports.size()<tempDirt.getInt()&&(directStands.contains(option)||supportFallback&&supports.contains(option.down()))&&accessStep(option))return true;
            if(System.nanoTime()>=routeDeadline)break;
        }
        if(standGoal==null&&search.routeCursor<options.size()){walker.release();status="Checking walking access";return true;}
        if(standGoal==null&&support.get()&&supports.size()>=tempDirt.getInt()&&(!directStands.isEmpty()||supportFallback)&&recycleSupport())return true;
        if(standGoal==null){
            // An anchored short column can still have no usable view. Only after
            // trying those views, allow a connected bridge from another side.
            if(supportFallback&&!bridge&&repositionTarget(target,tried,true)){bridgeTarget=target;return true;}
            if(prepareSupportDescent(options,false))return true;
            if(wanted!=null&&!wanted.isAir()&&states[cell]!=CORRECT&&preparePassage(options,cell))return true;
            if(wanted!=null&&temporaryView(target,wanted,cell,tried))return true;
            if(wanted!=null&&!wanted.isAir()&&states[cell]!=CORRECT&&prepareFloorOpening(options,cell))return true;
            if(prepareSupportDescent(options,true))return true;
            return false;
        }
        standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Moving around an obstructed block";return true;
    }
    /** Add a real, acknowledged floor when an otherwise usable placement view has none. */
    private boolean temporaryView(BlockPos target,BlockState wanted,int cell,Map<BlockPos,Integer> tried){
        if(!support.get()||wanted.isAir()||Schematic.material(wanted)==Items.AIR)return false;
        var key=new ViewKey(target,mc.player.getBlockPos().toImmutable(),false);
        var search=viewSearches.computeIfAbsent(key,k->new ViewSearch());
        if(search.temporaryViews==null){
        var candidates=new ArrayList<BlockPos>();
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-5;dy<=2;dy++){
            var stand=target.add(dx,dy,dz);var floor=stand.down();
            // Taller views are reached through intermediate owned steps, never
            // by asking the walker to jump the whole height in one move.
            if(stand.getY()<mc.player.getY()-2||stand.getY()>mc.player.getY()+6)continue;
            if(tried.containsKey(stand)||routeSupportExclusions.getOrDefault(floor,0)>ticks||plannedSolid(floor)||!mc.world.isChunkLoaded(floor)||!walker.hasStandingClearance(stand))continue;
            if(!mc.world.getBlockState(floor).isReplaceable()||!mc.world.getFluidState(floor).isEmpty())continue;
            candidates.add(stand);
        }
        candidates.sort(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos())));
        search.temporaryViews=candidates;
        }
        if(floorPlanningDeadline==0)floorPlanningDeadline=System.nanoTime()+2_000_000;
        for(int checked=0;checked<4&&search.temporaryCursor<search.temporaryViews.size()&&System.nanoTime()<floorPlanningDeadline;checked++){
            var stand=search.temporaryViews.get(search.temporaryCursor++);
            if(tried.containsKey(stand)||routeSupportExclusions.getOrDefault(stand.down(),0)>ticks||!walker.hasStandingClearance(stand)||!mc.world.getBlockState(stand.down()).isReplaceable())continue;
            var feet=Vec3d.ofBottomCenter(stand);var eye=feet.add(0,mc.player.getStandingEyeHeight(),0);
            if(!withinReach(target,eye))continue;
            var body=mc.player.getBoundingBox().offset(feet.subtract(mc.player.getEntityPos()));
            if(!mc.world.isSpaceEmpty(body))continue;
            if(placement(target,wanted,Schematic.material(wanted),cell,false,eye,body)==null)continue;
            // The first cell of an underfoot column is occupied until the
            // native jump. Commit its proven view before asking for that cell's
            // ground-level placement context, which necessarily rejects it.
            var current=mc.player.getBlockPos();
            boolean clearColumn=true;
            if(stand.getX()==current.getX()&&stand.getZ()==current.getZ())for(int y=current.getY();y<stand.getY();y++){
                var column=new BlockPos(current.getX(),y,current.getZ());
                if(plannedSolid(column)||!mc.world.getBlockState(column).isReplaceable()||!mc.world.getFluidState(column).isEmpty()){clearColumn=false;break;}
            }
            if(clearColumn&&stand.getX()==current.getX()&&stand.getZ()==current.getZ()&&stand.getY()>mc.player.getY()&&walker.canPillar(current)){
                if(supports.size()>=tempDirt.getInt())return recycleSupport();
                if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return true;}
                commitAccess(stand,true);walker.requestRecovery();status="Starting checked scaffold jump";return true;
            }
            var job=viewFloorJob(stand);
            if(job==null)continue;
            if(supports.size()>=tempDirt.getInt())return recycleSupport();
            if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return true;}
            commitAccess(stand,true);
            accessSupports.add(job.target);placement=job;placeTick();return true;
        }
        if(search.temporaryCursor<search.temporaryViews.size()){walker.release();status="Checking temporary placement views";return true;}
        return false;
    }
    /** Finish one access route before selecting another target or reclaiming its new steps. */
    private boolean continueAccess(){
        if(accessStand==null||navigatingCell<0)return false;
        double gap=mc.player.getEntityPos().distanceTo(Vec3d.ofBottomCenter(accessStand));
        if(mc.player.isOnGround()&&gap<accessBestDistance-.25){accessBestDistance=gap;accessProgressAt=ticks;}
        if(states[navigatingCell]==CORRECT||taskPhase(navigatingCell)!=activePhase||ticks-accessStarted>2400||ticks-accessProgressAt>600){
            triedStands.computeIfAbsent(navigatingCell,i->new HashMap<>()).put(accessStand,ticks+600);
            accessStand=null;accessFloor=false;accessSupports.clear();return false;
        }
        var wanted=desired(navigatingCell);var item=Schematic.material(wanted);
        var direct=inventoryCount(item)>0?placement(position(navigatingCell),wanted,item,navigatingCell,false):null;
        if(direct!=null){
            accessStand=null;accessFloor=false;accessSupports.clear();placement=direct;placeTick();return true;
        }
        if(reserveAccessCapacity())return true;
        if(walker.canReachStand(accessStand)){
            standGoal=accessStand;standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Finishing committed access route";return true;
        }
        var footing=mc.player.getBlockPos();
        if(accessFloor&&footing.getX()==accessStand.getX()&&footing.getZ()==accessStand.getZ()&&footing.getY()<accessStand.getY()&&walker.canPillar(footing)){
            // Once standing on our selected column, continue its native jump
            // placement instead of constructing another ring of side stairs.
            walker.requestRecovery();status="Climbing committed access column";return true;
        }
        if(accessStep(accessStand))return true;
        if(supports.size()>=tempDirt.getInt()&&recycleSupport())return true;
        if(supports.size()<tempDirt.getInt()&&accessFloor&&mc.world.getBlockState(accessStand.down()).isReplaceable()){
            var job=viewFloorJob(accessStand);
            if(job!=null){
                if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return true;}
                accessSupports.add(job.target);placement=job;placeTick();return true;
            }
            var feet=mc.player.getBlockPos();
            if(feet.getX()==accessStand.getX()&&feet.getZ()==accessStand.getZ()&&feet.getY()<accessStand.getY()&&walker.canPillar(feet)){
                walker.requestRecovery();status="Extending committed access tower";return true;
            }
        }
        // Geometry may have changed while walking. Defer this view rather than
        // alternately constructing and destroying it for another work target.
        triedStands.computeIfAbsent(navigatingCell,i->new HashMap<>()).put(accessStand,ticks+600);
        accessStand=null;accessFloor=false;accessSupports.clear();return false;
    }
    private void commitAccess(BlockPos stand,boolean floor){
        if(!stand.equals(accessStand)){accessStand=stand;accessStarted=accessProgressAt=ticks;accessBestDistance=mc.player.getEntityPos().distanceTo(Vec3d.ofBottomCenter(stand));accessSupports.clear();}
        accessFloor=floor;
    }
    /** Free a complete short access budget before climbing, instead of descending for every new piece. */
    private boolean reserveAccessCapacity(){
        if(!accessFloor||accessStand==null||walker.canReachStand(accessStand))return false;
        int reserve=Math.min(tempDirt.getInt(),Math.min(6,Math.max(1,accessStand.getY()-mc.player.getBlockY())));
        if(supports.size()+reserve<=tempDirt.getInt())return false;
        return recycleSupport();
    }
    private Place viewFloorJob(BlockPos stand){
        var floor=stand.down();
        if(routeSupportExclusions.getOrDefault(floor,0)>ticks||plannedSolid(floor)||!mc.world.getBlockState(floor).isReplaceable()||!mc.world.getFluidState(floor).isEmpty())return null;
        var job=placement(floor,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true);
        if(job!=null)return job;
        int height=Math.min(6,Math.max(2,stand.getY()-mc.player.getBlockY()));
        for(int depth=height;depth>=1;depth--){
            var base=stand.down(depth+1);boolean clear=true;
            for(int y=0;y<=depth;y++){
                var pos=base.up(y);
                if(plannedSolid(pos)||!mc.world.isChunkLoaded(pos)||!mc.world.getFluidState(pos).isEmpty()
                    ||!mc.world.getBlockState(pos).isReplaceable()&&!supports.contains(pos)){clear=false;break;}
            }
            if(!clear||routeSupportExclusions.getOrDefault(base,0)>ticks)continue;
            job=placement(base,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true);if(job!=null)return job;
        }
        return null;
    }
    /** Build the lowest attachable piece of a short support column before its upper pieces. */
    private Place supportPlacement(BlockPos target,Vec3d eye,Box body){
        return supportPlacement(target,eye,body,target.equals(bridgeTarget));
    }
    private Place supportPlacement(BlockPos target,Vec3d eye,Box body,boolean bridge){
        if(!support.get()||supports.size()>=tempDirt.getInt())return null;
        int targetCell=schematic.indexAt(target.subtract(anchor()),turns(),mirror.get());
        var wanted=targetCell<0?null:desired(targetCell);
        Direction attachment=attachmentSide(wanted);
        var sides=Direction.values();
        for(int depth=0;depth<3;depth++)for(var side:sides){
            // A face on another side cannot produce this block's requested orientation.
            // If the required neighbour is part of the schematic, place it instead.
            if(attachment!=null&&side!=attachment)continue;
            var top=target.offset(side);boolean allowed=true;
            for(int down=0;down<=depth;down++){
                var pos=top.down(down);int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());
                if(routeSupportExclusions.getOrDefault(pos,0)>ticks||cell>=0&&!desired(cell).isAir()&&!desired(cell).isOf(Blocks.STRUCTURE_VOID)||!mc.world.isChunkLoaded(pos)||!mc.world.getBlockState(pos).isReplaceable()||!mc.world.getBlockState(pos).getFluidState().isEmpty()){allowed=false;break;}
            }
            if(!allowed)continue;
            var plan=placement(top.down(depth),Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true,eye,body);
            if(plan!=null)return plan;
        }
        // A floating outer edge may need a short lateral bridge before a column
        // can start. Search backwards from its attachment to existing blocks.
        if(!bridge&&straightSupportBase(target,attachment))return null;
        var roots=supportChainStarts.computeIfAbsent(target,key->scaffoldChainRoots(key,attachment));int attempts=0;
        for(var root:roots){
            if(eye.squaredDistanceTo(Vec3d.ofCenter(root))>Math.pow(effectiveReach()+.75,2))continue;
            var plan=placement(root,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true,eye,body);
            if(plan!=null)return plan;if(++attempts>=12)break;
        }
        return null;
    }
    private Direction attachmentSide(BlockState wanted){return wanted!=null&&wanted.getBlock() instanceof HopperBlock?wanted.get(HopperBlock.FACING):wanted!=null&&wanted.getBlock() instanceof ShulkerBoxBlock?wanted.get(ShulkerBoxBlock.FACING).getOpposite():null;}
    private boolean straightSupportBase(BlockPos target,Direction attachment){
        for(int depth=0;depth<3;depth++)for(var side:Direction.values()){
            if(attachment!=null&&side!=attachment)continue;
            var top=target.offset(side);boolean allowed=true;
            for(int down=0;down<=depth;down++)if(!scaffoldCell(top.down(down),target)){allowed=false;break;}
            if(!allowed)continue;
            var root=top.down(depth);
            for(var face:Direction.values()){
                var state=mc.world.getBlockState(root.offset(face));
                if(!state.isAir()&&!state.isReplaceable()&&state.getFluidState().isEmpty())return true;
            }
        }
        return false;
    }
    private List<BlockPos> scaffoldChainRoots(BlockPos target,Direction attachment){
        record Step(BlockPos pos,int length){}
        var eye=mc.player.getEyePos();var queue=new PriorityQueue<Step>(Comparator.comparingInt(Step::length).thenComparingDouble(step->Vec3d.ofCenter(step.pos).squaredDistanceTo(eye)));
        var seen=new HashSet<BlockPos>();var result=new ArrayList<BlockPos>();
        for(var side:Direction.values())if(side!=Direction.UP&&(attachment==null||side==attachment)){
            var pos=target.offset(side);if(scaffoldCell(pos,target)&&seen.add(pos))queue.add(new Step(pos,1));
        }
        long deadline=System.nanoTime()+3_000_000;int visited=0;
        while(!queue.isEmpty()&&visited++<512&&result.size()<64&&System.nanoTime()<deadline){
            var step=queue.remove();boolean anchored=false;
            for(var side:Direction.values()){
                var pos=step.pos.offset(side);var state=mc.world.getBlockState(pos);
                if(!state.isAir()&&!state.isReplaceable()&&state.getFluidState().isEmpty())anchored=true;
                if(step.length<8&&scaffoldCell(pos,target)&&seen.add(pos))queue.add(new Step(pos,step.length+1));
            }
            if(anchored)result.add(step.pos);
        }
        return List.copyOf(result);
    }
    private boolean scaffoldCell(BlockPos pos,BlockPos target){
        return Math.abs(pos.getX()-target.getX())<=3&&Math.abs(pos.getZ()-target.getZ())<=3&&pos.getY()>=target.getY()-6&&pos.getY()<=target.getY()
            &&routeSupportExclusions.getOrDefault(pos,0)<=ticks&&!plannedSolid(pos)&&mc.world.isChunkLoaded(pos)&&mc.world.getBlockState(pos).isReplaceable()&&mc.world.getFluidState(pos).isEmpty();
    }
    private int supportReserve(){return Math.max(8,restockDirt.getInt());}
    private void ensureSupportDirt(){
        needed=Items.DIRT;
        if(supportPickup!=null){
            var drops=mc.world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(supportPickup).expand(3,8,3),entity->entity.getStack().isOf(Items.DIRT));
            var drop=drops.stream().min(Comparator.comparingDouble(entity->entity.squaredDistanceTo(mc.player))).orElse(null);
            if(ticks<supportPickupUntil&&(drop!=null||ticks<supportPickupUntil-180)){
                if(drop!=null)walker.standAt(drop.getBlockPos());
                if(drop==null||!walker.routeUnavailable()){status="Collecting recycled temporary blocks";return;}
            }
            supportPickup=null;walker.stop();
        }
        if(ticks>=supportRecycleAt&&!supports.isEmpty()){
            if(recycleSupport()){
                if(routeMining!=null){supportRecycleAt=ticks+60;supportPickup=routeMining;supportPickupUntil=ticks+200;}
                return;
            }
            supportRecycleAt=ticks+60;
        }
        if(restockTarget!=null){triedContainers.add(restockTarget);restockTarget=null;walker.stop();}
        if(restock.get()&&beginRestock()){supportRestock=true;status="Restocking temporary blocks";return;}
        if(autoBuy.get()&&maxSpend.get()>0){boolean resume=building;startBuying(false);if(buying){supportShopping=true;resumeAfterMarket=resume;status="Buying temporary blocks";}return;}
        walker.release();status="Out of temporary blocks — add dirt or enable AH buying with a budget";
    }
    private Place placement(BlockPos target,BlockState wanted,Item item,int index,boolean temporary){return placement(target,wanted,item,index,temporary,mc.player.getEyePos(),mc.player.getBoundingBox());}
    private static boolean potted(BlockState state){return state.getBlock() instanceof FlowerPotBlock pot&&pot.getContent()!=Blocks.AIR;}
    private Place placement(BlockPos target,BlockState wanted,Item item,int index,boolean temporary,Vec3d eye,Box body){
        if(failedPlacementUntil.getOrDefault(target,0)>ticks&&eye.squaredDistanceTo(mc.player.getEyePos())<.0001)return null;
        if(potted(wanted)){
            if(mc.world.getBlockState(target).isOf(Blocks.FLOWER_POT)){var hit=visibleHit(target,eye);return hit==null?null:new Place(target,wanted,hit,Schematic.material(wanted),index,false);}
            return placement(target,Blocks.FLOWER_POT.getDefaultState(),Items.FLOWER_POT,index,temporary,eye,body);
        }
        if(item==Items.WATER_BUCKET||item==Items.LAVA_BUCKET){
            for(var side:Direction.values()){
                var neighbor=target.offset(side.getOpposite());var hit=visibleHit(neighbor,eye);if(hit!=null&&hit.getSide()==side&&mc.world.getBlockState(target).isReplaceable())return new Place(target,wanted,hit,item,index,false);
            }return null;
        }
        if(!(item instanceof BlockItem blockItem))return null;
        ItemStack stack=new ItemStack(item);double range=effectiveReach();float oldYaw=mc.player.getYaw(),oldPitch=mc.player.getPitch();
        try{
            for(int direct=0;direct<2;direct++)for(var side:Direction.values()){
                BlockPos neighbor=direct==1?target:target.offset(side.getOpposite());var supportState=mc.world.getBlockState(neighbor);
                if(direct==1&&supportState.getBlock()!=wanted.getBlock())continue;
                if(supportState.isAir()||supportState.isReplaceable()||!supportState.getFluidState().isEmpty())continue;
                // Prefer points away from the centre boundary used by door hinges and
                // slab halves, and sample both height and width on vertical faces.
                boolean boundarySensitive=wanted.getBlock() instanceof DoorBlock||wanted.getBlock() instanceof TrapdoorBlock||wanted.getBlock() instanceof SlabBlock;
                double[][] samples=side.getAxis()==Direction.Axis.Y
                    ?(boundarySensitive?new double[][]{{.25,.25},{.75,.75},{.25,.75},{.75,.25},{.5,.5}}:new double[][]{{.5,.5},{.1,.5},{.9,.5},{.5,.1},{.5,.9},{.1,.1},{.1,.9},{.9,.1},{.9,.9}})
                    :(boundarySensitive?new double[][]{{.75,.25},{.75,.75},{.25,.25},{.25,.75},{.5,.5},{.25,.5},{.75,.5}}:new double[][]{{.5,.5},{.25,.5},{.75,.5}});
                for(double[] sample:samples){
                    double height=sample[0];
                    Vec3d point=Vec3d.ofCenter(neighbor).add(side.getOffsetX()*.5,side.getOffsetY()*.5,side.getOffsetZ()*.5);
                    if(side.getAxis()==Direction.Axis.Y)point=new Vec3d(neighbor.getX()+sample[0],point.y,neighbor.getZ()+sample[1]);
                    if(side.getAxis()!=Direction.Axis.Y)point=new Vec3d(side.getAxis()==Direction.Axis.Z?neighbor.getX()+sample[1]:point.x,neighbor.getY()+height,side.getAxis()==Direction.Axis.X?neighbor.getZ()+sample[1]:point.z);
                    if(side==Direction.UP&&!supportState.getOutlineShape(mc.world,neighbor).isEmpty())point=new Vec3d(point.x,neighbor.getY()+supportState.getOutlineShape(mc.world,neighbor).getMax(Direction.Axis.Y),point.z);
                    if(eye.squaredDistanceTo(point)>range*range)continue;
                    boolean shapedSupport=direct==0&&clickable(supportState.getBlock());
                    Vec3d rayEnd=shapedSupport?point.add(point.subtract(eye).normalize().multiply(1.1)):point.add(Vec3d.of(side.getVector()).multiply(-.002));
                    var ray=mc.world.raycast(new RaycastContext(eye,rayEnd,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
                    if(ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(neighbor)||ray.getSide()!=side)continue;
                    if(shapedSupport)point=ray.getPos();
                    float[] angles=angles(eye,point);mc.player.setYaw(angles[0]);mc.player.setPitch(angles[1]);
                    var hit=new BlockHitResult(point,side,neighbor,false);var context=new ItemPlacementContext(mc.player,Hand.MAIN_HAND,stack,hit);
                    context=blockItem.getPlacementContext(context);if(context==null)continue;
                    // Hypothetical standing positions use the supplied body box. The
                    // item-level check instead sees the player's current body and rejects
                    // every alternate view when the player needs to step out of the target.
                    BlockState predicted=eye.squaredDistanceTo(mc.player.getEyePos())>.0001?wanted.getBlock().getPlacementState(context)
                        :((BlockItemAccessor)(Object)blockItem).maro$placementState(context);
                    if(predicted==null||!context.getBlockPos().equals(target)||!compatible(predicted,wanted)||!predicted.canPlaceAt(mc.world,target))continue;
                    if(predicted.getCollisionShape(mc.world,target).getBoundingBoxes().stream().anyMatch(box->box.offset(target).intersects(body)))continue;
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
            if(desired.isOf(Blocks.NOTE_BLOCK)&&(name.equals("note")||name.equals("instrument")))continue;
            if(name.equals("delay")&&desired.getBlock() instanceof RepeaterBlock||name.equals("open")&&(desired.getBlock() instanceof TrapdoorBlock||desired.getBlock() instanceof DoorBlock))continue;
            if(derivedProperty(desired,name)||name.equals("waterlogged"))continue;
            if(property==net.minecraft.state.property.Properties.SLAB_TYPE&&desired.get(net.minecraft.state.property.Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE)continue;
            if(!predicted.get(property).equals(desired.get(property)))return false;
        }
        return true;
    }
    private static final Set<String> DERIVED_PROPERTIES=Set.of("shape","north","south","east","west","up","down","powered","power","lit","distance","persistent","locked","enabled","triggered","extended","occupied");
    private static boolean derivedProperty(BlockState state,String name){return DERIVED_PROPERTIES.contains(name)||name.equals("type")&&state.getBlock() instanceof AbstractChestBlock<?>;}
    public static boolean matchesBuildState(BlockState actual,BlockState wanted){
        if(actual.getBlock()!=wanted.getBlock())return false;
        for(var property:wanted.getProperties())if(!derivedProperty(wanted,property.getName())&&!(wanted.isOf(Blocks.NOTE_BLOCK)&&property.getName().equals("instrument"))&&!actual.get(property).equals(wanted.get(property)))return false;
        return true;
    }
    private static int configuration(BlockState state){
        if(state.getBlock() instanceof RepeaterBlock)return state.get(RepeaterBlock.DELAY);
        if(state.getBlock() instanceof TrapdoorBlock||state.getBlock() instanceof DoorBlock)return state.get(net.minecraft.state.property.Properties.OPEN)?1:0;
        return -1;
    }
    private void placeTick(){
        walker.release();Place job=placement;
        if(!job.target.equals(placementAttemptTarget)){placementAttemptTarget=job.target;placementAttemptStarted=ticks;}
        if(ticks-placementAttemptStarted>80){deferPlacement(job,"Placement stalled - trying another position");return;}
        if(job.index>=0){updateState(job.index);if(states[job.index]==CORRECT){placement=null;return;}}
        if(inventoryCount(job.item)==0){placement=null;return;}
        if(!selectMaterial(job.item)){
            status=delay>0?"Moving material to hotbar":"Material unavailable";
            // The swap defers this exact job; its ray and placement context are
            // checked again below after the hotbar update has settled.
            if(delay<=0)placement=null;
            return;
        }
        boolean planting=potted(job.state)&&mc.world.getBlockState(job.target).isOf(Blocks.FLOWER_POT);
        if(planting){releaseSneak();if(mc.player.isSneaking())return;}
        if(!planting&&clickable(mc.world.getBlockState(job.hit.getBlockPos()).getBlock())&&!mc.player.isSneaking()){
            if(!ownsSneak)sneakReadyAt=ticks+3;ownsSneak=true;mc.options.sneakKey.setPressed(true);status="Sneaking to place";return;
        }
        if(ownsSneak&&ticks<sneakReadyAt){status="Waiting for crouch before placement";return;}
        if(!aim(job.hit.getPos())){status="Aiming";return;}
        var aimed=(BlockHitResult)mc.player.raycast(effectiveReach(),1,false);
        if(aimed.getType()!=HitResult.Type.BLOCK||!aimed.getBlockPos().equals(job.hit.getBlockPos())||aimed.getSide()!=job.hit.getSide()){
            deferPlacement(job,"Placement ray changed - trying another position");return;
        }
        if(job.item==Items.WATER_BUCKET||job.item==Items.LAVA_BUCKET){
            releaseSneak();withPublishedLook(()->{beginPlacementReceipt(job);if(!mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND).isAccepted()){pendingPlacement=null;unconfirmedPlacements.remove(job.target);}delay=Math.max(10,actionDelay());placement=null;status="Placing schematic fluid source";});return;
        }
        var context=((BlockItem)job.item).getPlacementContext(new ItemPlacementContext(mc.player,Hand.MAIN_HAND,mc.player.getMainHandStack(),aimed));
        if(!planting&&(context==null||!context.getBlockPos().equals(job.target)||!compatible(((BlockItemAccessor)(Object)job.item).maro$placementState(context),job.state))){deferPlacement(job,"Placement state changed - trying another position");return;}
        withPublishedLook(()->{
            var actual=(BlockHitResult)mc.player.raycast(effectiveReach(),1,false);
            if(actual.getType()!=HitResult.Type.BLOCK||!actual.getBlockPos().equals(aimed.getBlockPos())||actual.getSide()!=aimed.getSide())return;
            var freshContext=((BlockItem)job.item).getPlacementContext(new ItemPlacementContext(mc.player,Hand.MAIN_HAND,mc.player.getMainHandStack(),actual));
            if(!planting&&(freshContext==null||!freshContext.getBlockPos().equals(job.target)||!compatible(((BlockItemAccessor)(Object)job.item).maro$placementState(freshContext),job.state))){
                deferPlacement(job,"Placement changed after movement — replanning");return;
            }
            beginPlacementReceipt(job);lastBuildInteraction=ticks;
            if(mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,actual).isAccepted()){
                mc.player.swingHand(Hand.MAIN_HAND);delay=actionDelay();status=job.temporary?"Placing temporary support":"Placing "+job.item.getName().getString();
            }else{pendingPlacement=null;unconfirmedPlacements.remove(job.target);deferPlacement(job,"Placement refused - trying another position");}
            placement=null;releaseSneak();
        });
    }
    private void deferPlacement(Place job,String reason){
        placement=null;placementAttemptTarget=null;releaseSneak();yawVelocity=pitchVelocity=0;
        failedPlacementUntil.put(job.target,ticks+20);
        if(job.index>=0){
            retryAt.put(job.index,ticks+20);triedStands.computeIfAbsent(job.index,i->new HashMap<>()).put(mc.player.getBlockPos().toImmutable(),ticks+600);
            if(autoMove.get())reposition(job.index);
        }
        status=reason;
    }
    private void beginPlacementReceipt(Place job){unconfirmedPlacements.put(job.target,job);pendingPlacement=job;pendingBefore=mc.world.getBlockState(job.target);pendingServerState=null;placementDeadline=ticks+80;}
    private void reconcilePrediction(Place job,BlockState authoritative){
        unconfirmedPlacements.remove(job.target);
        if(authoritative==null)latePlacements.put(job.target,new LatePlacement(job,ticks+500));
        var rollback=authoritative!=null?authoritative:pendingBefore;
        var predicted=mc.world.getBlockState(job.target);
        if(rollback!=null&&predicted.getBlock()==job.state.getBlock()&&!predicted.equals(rollback))
            mc.world.setBlockState(job.target,rollback,Block.NOTIFY_ALL);
    }
    /** Connect an existing raised build surface with normal one-block stair steps. */
    private boolean accessStep(BlockPos stand){
        if(stand.equals(accessStand)){
            var next=accessSupports.stream().map(BlockPos::up).filter(pos->pos.getY()<=stand.getY())
                .filter(pos->walker.standingPoint(pos).distanceTo(mc.player.getEntityPos())>.22)
                .filter(pos->walker.standingPoint(pos).distanceTo(Vec3d.ofBottomCenter(stand))<mc.player.getEntityPos().distanceTo(Vec3d.ofBottomCenter(stand))-.25)
                .filter(walker::canStand).filter(walker::canReachStand)
                .min(Comparator.<BlockPos>comparingInt(pos->-pos.getY()).thenComparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos()))).orElse(null);
            if(next!=null){standGoal=next;standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Climbing completed access step";return true;}
            var intermediate=accessSupports.stream().map(BlockPos::up).filter(pos->pos.getY()>mc.player.getY()+.5&&pos.getY()<=mc.player.getY()+3&&pos.getY()<stand.getY())
                .filter(walker::canStand).min(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos()))).orElse(null);
            if(intermediate!=null&&buildAccessStep(intermediate))return true;
        }
        return buildAccessStep(stand);
    }
    private boolean buildAccessStep(BlockPos stand){
        if(supports.size()>=tempDirt.getInt())return false;
        int rise=stand.getY()-mc.player.getBlockPos().getY();if(rise<1||rise>(stand.equals(accessStand)?6:3))return false;
        var floor=stand.down();int cell=schematic.indexAt(floor.subtract(anchor()),turns(),mirror.get());
        // Add stairs only for this committed placement view or its intermediate post.
        boolean ownedStep=supports.contains(floor)&&mc.world.getBlockState(floor).isOf(Blocks.DIRT);
        if(rise==1&&!ownedStep)return false;
        if(!ownedStep&&(cell<0||states[cell]!=CORRECT||desired(cell).isAir()||desired(cell).isOf(Blocks.STRUCTURE_VOID)))return false;
        var directions=new ArrayList<>(List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST));
        directions.sort(Comparator.comparingDouble(side->stand.offset(side,rise-1).getSquaredDistance(mc.player.getBlockPos())));
        for(var side:directions)for(int distance=Math.max(1,rise-1);distance>=1;distance--){
            var top=stand.offset(side,distance).down(distance+1);
            // Keep the walking clearance outside future solid cells, so the next layer
            // cannot immediately bury the access stair beneath the player's head.
            if(plannedSolid(top.up())||plannedSolid(top.up(2)))continue;
            for(int depth=2;depth>=0;depth--){
                var pos=top.down(depth);
                if(routeSupportExclusions.getOrDefault(pos,0)>ticks||plannedSolid(pos)||!mc.world.isChunkLoaded(pos)||!mc.world.getBlockState(pos).isReplaceable()||!mc.world.getFluidState(pos).isEmpty())continue;
                var job=placement(pos,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true);
                if(job==null)continue;
                if(inventoryCount(Items.DIRT)==0){ensureSupportDirt();return true;}
                if(accessStand==null)commitAccess(stand,false);
                accessSupports.add(pos);placement=job;placeTick();return true;
            }
        }
        return false;
    }
    private boolean plannedSolid(BlockPos pos){int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());return cell>=0&&!desired(cell).isAir()&&!desired(cell).isOf(Blocks.STRUCTURE_VOID);}
    private BlockPos recoveryDestination(){return standGoal!=null?standGoal:restockTarget!=null?restockTarget:depositTarget!=null?depositTarget:accessStand!=null?accessStand:navigatingCell>=0?position(navigatingCell):walker.destination();}
    private boolean prepareSupportDescent(List<BlockPos> views){
        return prepareSupportDescent(views,true);
    }
    private boolean prepareSupportDescent(List<BlockPos> views,boolean allowStaging){
        boolean staged=restockTarget!=null||navigatingCell>=0&&states[navigatingCell]!=CORRECT&&!desired(navigatingCell).isAir();
        if(!staged&&views.isEmpty())return false;
        var feet=mc.player.getBlockPos();var destination=restockTarget!=null?restockTarget:navigatingCell>=0?position(navigatingCell):views.getFirst();
        if(!feet.equals(descentSearchFeet)||!destination.equals(descentSearchDestination)){
            descentSearchFeet=feet.toImmutable();descentSearchDestination=destination;descentSearchCursor=descentRetryAt=descentSearchPhase=0;descentHatchesReady=false;
            descentSearchViews=views.stream().filter(pos->walker.standingPoint(pos).y<mc.player.getY()-.5)
                .sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(feet))).limit(8).toList();
            descentSearchPosts=supports.stream().filter(pos->pos.getY()>=mc.player.getY()-3.1&&pos.getY()<mc.player.getY()+.1)
                .filter(pos->mc.world.getBlockState(pos).isOf(Blocks.DIRT)&&!plannedSolid(pos)&&walker.canDescendThrough(pos)&&safeToRecycle(pos))
                .sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(feet))).limit(128).toList();
        }
        if(descentSearchPosts.isEmpty()||ticks<descentRetryAt)return false;
        // Exhaust useful placement/storage exits before considering a generic
        // staging drop. A nearer clearing must not hide a farther useful post.
        long deadline=System.nanoTime()+3_000_000;
        while(descentSearchPhase<3&&System.nanoTime()<deadline){
            if(descentSearchPhase>0&&(!staged||!allowStaging)){
                if(!staged){descentSearchPhase=descentSearchCursor=0;descentRetryAt=ticks+40;}
                return false;
            }
            if(descentSearchPhase==1&&!descentHatchesReady){
                if(collectDescentHatches()){walker.release();status="Checking staged scaffold return";return true;}
                descentHatchViews=hatchViews.stream().filter(pos->walker.standingPoint(pos).y<mc.player.getY()-.5)
                    .sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(feet))).limit(8).toList();descentHatchesReady=true;
            }
            var exits=descentSearchPhase==0?descentSearchViews:descentHatchViews;
            int viewCount=descentSearchPhase==2?1:exits.size(),total=descentSearchPosts.size()*viewCount;
            while(descentSearchCursor<total&&System.nanoTime()<deadline){
                int sample=descentSearchCursor++;var pos=descentSearchPosts.get(sample/viewCount);
                if(!supports.contains(pos)||!mc.world.getBlockState(pos).isOf(Blocks.DIRT))continue;
                BlockPos lower;
                if(descentSearchPhase<2){lower=exits.get(sample%viewCount);if(!descentReaches(pos,lower))continue;}
                else{
                    var search=descentEscapes.computeIfAbsent(pos,p->new EscapeSearch());var geometry=descentGeometry(pos);
                    if(geometry==null||walker.standingPoint(geometry.landing).y>=mc.player.getY()-.5||!safeToRecycle(geometry.removed))continue;
                    lower=openDescentExit(geometry,search,deadline);
                    if(lower==null){if(search.cursor<192){descentSearchCursor--;walker.release();status="Checking open scaffold exit";return true;}continue;}
                }
                if(beginSupportDescent(pos,lower))return true;
            }
            if(descentSearchCursor<total)break;
            descentSearchPhase++;descentSearchCursor=0;
        }
        if(descentSearchPhase<3){walker.release();status="Checking scaffold return route";return true;}
        descentSearchPhase=descentSearchCursor=0;descentRetryAt=ticks+40;
        return false;
    }
    private boolean beginSupportDescent(BlockPos pos,BlockPos lower){
        if(pos.equals(mc.player.getBlockPos().down())&&mc.player.isOnGround()&&mc.player.getVelocity().horizontalLengthSquared()<.0001){
            descentPost=descentView=null;
            standGoal=lower;descentLanding=true;routeMining=mining=pos;walker.stop();status="Descending temporary scaffold";mineTick();return true;
        }
        if(!walker.canReachStand(pos.up()))return false;
        descentPost=pos;descentView=lower;standGoal=pos.up();standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Moving to a scaffold descent";return true;
    }
    /** Checked intermediate views for routes which need both an owned-post drop and a floor hatch. */
    private boolean collectDescentHatches(){
        var feet=mc.player.getBlockPos();
        if(!feet.equals(hatchSearchFeet)){
            hatchSearchFeet=feet.toImmutable();hatchSearchCursor=hatchExitCursor=0;hatchViews.clear();descentEscapes.clear();
            hatchCandidates=supports.stream().filter(pos->mc.world.getBlockState(pos).isOf(Blocks.DIRT))
                .flatMap(pos->java.util.stream.IntStream.rangeClosed(1,3).mapToObj(pos::up)).distinct()
                .filter(pos->pos.getY()<mc.player.getY()-.5&&pos.getY()>=mc.player.getY()-5)
                .filter(pos->{int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());var state=mc.world.getBlockState(pos);
                    return cell>=0&&states[cell]==CORRECT&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()&&state.getHardness(mc.world,pos)>=0&&state.isFullCube(mc.world,pos);})
                .sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(feet))).limit(128).toList();
        }
        long deadline=System.nanoTime()+3_000_000;
        while(hatchSearchCursor<hatchCandidates.size()&&System.nanoTime()<deadline){
            var cover=hatchCandidates.get(hatchSearchCursor);
            if(!walker.canStand(cover.up())||!safeToRecycle(cover)||!walker.canDescendThrough(cover)){hatchSearchCursor++;hatchExitCursor=0;continue;}
            var geometry=descentGeometry(cover);if(geometry==null||!safeToRecycle(geometry.removed)){hatchSearchCursor++;hatchExitCursor=0;continue;}
            var search=new EscapeSearch();search.cursor=hatchExitCursor;boolean proved=openDescentExit(geometry,search,deadline)!=null;hatchExitCursor=search.cursor;
            if(proved){hatchViews.add(cover.up());hatchSearchCursor++;hatchExitCursor=0;}
            else if(hatchExitCursor==192){hatchSearchCursor++;hatchExitCursor=0;}
        }
        return hatchSearchCursor<hatchCandidates.size();
    }
    private BlockPos openDescentExit(DescentGeometry geometry,EscapeSearch search,long deadline){
        if(search.view!=null&&openScaffoldStand(search.view)&&walker.canReachAfterClearing(geometry.landing,search.view,geometry.removed))return search.view;
        while(search.cursor<192&&System.nanoTime()<deadline){
            int sample=search.cursor++;var exit=geometry.landing.offset(ESCAPE_SIDES[sample/3%4],sample/12+1).down(sample%3);
            if(!openScaffoldStand(exit))continue;
            if(walker.canReachAfterClearing(geometry.landing,exit,geometry.removed)){search.view=exit;return exit;}
        }
        return null;
    }
    private boolean openScaffoldStand(BlockPos pos){
        if(!walker.canStand(pos))return false;
        for(int up=2;up<=8;up++)if(plannedSolid(pos.up(up))||!walker.hasStandingClearance(pos.up(up)))return false;
        return true;
    }
    private record DescentGeometry(BlockPos landing,Set<BlockPos> removed){}
    private DescentGeometry descentGeometry(BlockPos removed){
        var landing=walker.descentLanding(removed);if(landing==null)return null;
        var cleared=new HashSet<BlockPos>();cleared.add(removed);
        for(int steps=0;steps<128;steps++){
            var post=landing.down();if(!supports.contains(post)||plannedSolid(post)||!mc.world.getBlockState(post).isOf(Blocks.DIRT))break;
            cleared.add(post);landing=walker.descentLandingAfterClearing(post,cleared);if(landing==null)return null;
        }
        return new DescentGeometry(landing,cleared);
    }
    private boolean descentReaches(BlockPos removed,BlockPos destination){
        var landing=walker.descentLanding(removed);
        if(landing==null)return false;
        var cleared=new HashSet<BlockPos>();cleared.add(removed);
        if(walker.canReachAfterClearing(landing,destination,cleared))return true;
        // A tall owned column is descended one acknowledged step at a time.
        // Prove its eventual exit without treating the whole column as one fall.
        for(int steps=0;steps<128;steps++){
            var post=landing.down();
            if(!supports.contains(post)||plannedSolid(post)||!mc.world.getBlockState(post).isOf(Blocks.DIRT))break;
            cleared.add(post);var next=walker.descentLandingAfterClearing(post,cleared);
            if(next==null)return false;landing=next;
        }
        return cleared.size()>1&&safeToRecycle(cleared)&&walker.canReachAfterClearing(landing,destination,cleared);
    }
    private boolean descendingOwnedSupport(BlockPos pos){
        var destination=recoveryDestination();
        return destination!=null&&destination.getY()<mc.player.getY()&&(pos.equals(routeMining)||walker.routeUnavailable()||walker.movementStalled())
            &&pos.equals(mc.player.getBlockPos().down())&&removableRouteFloor(pos)
            &&mc.player.isOnGround()&&mc.player.getVelocity().horizontalLengthSquared()<.0001
            &&descentReaches(pos,destination)&&safeToRecycle(pos);
    }
    private boolean removableRouteFloor(BlockPos pos){
        if(supports.contains(pos)&&!plannedSolid(pos)&&mc.world.getBlockState(pos).isOf(Blocks.DIRT))return true;
        int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());
        return (pos.equals(routeOpening)||passageBlocks.contains(pos))&&cell>=0&&!desired(cell).isAir()&&matchesBuildState(mc.world.getBlockState(pos),desired(cell));
    }
    /** Reopen only our finished, noninteractive wall cells after proving a useful route. */
    private boolean preparePassage(List<BlockPos> views,int work){
        if(!unstuck.get()||views.isEmpty()||work<0||ticks<passageRetryAt)return false;
        var feet=BlockPos.ofFloored(mc.player.getEntityPos().add(0,.4,0));
        if(!feet.equals(passageSearchFeet)||work!=passageSearchWork){passageSearchFeet=feet;passageSearchWork=work;passageSearchCursor=0;}
        var destinations=views.stream().sorted(Comparator.comparingDouble(p->p.getSquaredDistance(feet))).limit(8).toList();
        int total=75*destinations.size();long deadline=System.nanoTime()+3_000_000;
        while(passageSearchCursor<total&&System.nanoTime()<deadline){
            int sample=passageSearchCursor++,candidate=sample/destinations.size();
            var bottom=feet.add(candidate/15-2,candidate%3-1,candidate/3%5-2);var top=bottom.up();
            if(bottom.getY()<mc.player.getY()-.2||new Box(bottom).intersects(mc.player.getBoundingBox())||new Box(top).intersects(mc.player.getBoundingBox()))continue;
            var removed=Set.of(bottom,top);
            if(!passageCell(bottom)||!passageCell(top)||visibleHit(bottom)==null&&visibleHit(top)==null||!safeToRecycle(removed))continue;
            var destination=destinations.get(sample%destinations.size());
            if(!walker.canReachAfterClearing(feet,destination,removed))continue;
            passageBlocks.addAll(removed);passageStand=destination;
            floorAccessWork.put(bottom,work);floorAccessWork.put(top,work);
            placement=null;accessStand=null;accessFloor=false;accessSupports.clear();descentPost=descentView=null;standGoal=null;
            walker.stop();status="Opening verified placement passage";return true;
        }
        if(passageSearchCursor<total){walker.release();status="Checking enclosed placement route";return true;}
        passageSearchCursor=0;passageRetryAt=ticks+40;return false;
    }
    private boolean passageCell(BlockPos pos){
        int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());var state=mc.world.getBlockState(pos);
        return cell>=0&&states[cell]==CORRECT&&!floorDeferred(pos)&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()
            &&state.getHardness(mc.world,pos)>=0&&state.isFullCube(mc.world,pos)&&!state.getBlock().equals(Blocks.BEDROCK);
    }
    private boolean clearPassageTick(){
        if(passageStand==null)return false;
        if(routeMining!=null){clearRouteSupportTick();return true;}
        passageBlocks.removeIf(pos->mc.world.getBlockState(pos).isAir());
        if(!passageBlocks.isEmpty()){
            var next=passageBlocks.stream().filter(pos->removableRouteFloor(pos)&&visibleHit(pos)!=null).findFirst().orElse(null);
            if(next==null){passageBlocks.clear();passageStand=null;passageRetryAt=ticks+100;walker.stop();status="Replanning changed passage";return true;}
            routeMining=mining=next;walker.release();mineTick();return true;
        }
        standGoal=passageStand;passageStand=null;standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();
        status="Walking through verified placement passage";return true;
    }
    /** Open a safe schematic floor above our own landing post when a closed build has no exit. */
    private boolean prepareFloorOpening(List<BlockPos> views,int work){
        if(!unstuck.get())return false;
        var lowerView=views.stream().filter(pos->walker.standingPoint(pos).y<mc.player.getY()-.5)
            .min(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos()))).orElse(null);
        var candidates=supports.stream().filter(pos->mc.world.getBlockState(pos).isOf(Blocks.DIRT))
            .flatMap(pos->java.util.stream.IntStream.rangeClosed(1,3).mapToObj(pos::up)).distinct()
            .filter(pos->pos.getY()>=mc.player.getY()-2.1&&pos.getY()<mc.player.getY()+.1)
            .filter(pos->{int cell=schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get());var actual=mc.world.getBlockState(pos);
                return cell>=0&&states[cell]==CORRECT&&plannedSolid(pos)&&!actual.hasBlockEntity()&&actual.getHardness(mc.world,pos)>=0&&actual.isSideSolidFullSquare(mc.world,pos,Direction.UP);})
            .sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos()))).toList();
        var feet=mc.player.getBlockPos();if(!feet.equals(floorSearchFeet)){floorSearchFeet=feet.toImmutable();floorSearchCursor=0;}
        if(candidates.isEmpty())return false;
        if(floorPlanningDeadline==0)floorPlanningDeadline=System.nanoTime()+2_000_000;
        if(floorSearchCursor>=candidates.size())floorSearchCursor=0;
        for(int checked=0;checked<4&&floorSearchCursor<candidates.size()&&System.nanoTime()<floorPlanningDeadline;checked++){
            var cover=candidates.get(floorSearchCursor++);
            if(!walker.canDescendThrough(cover)||!safeToRecycle(cover)||!walker.canReachStand(cover.up()))continue;
            var lower=lowerView;
            for(int drop=1;drop<=3;drop++){
                var post=cover.down(drop);
                if(supports.contains(post)&&mc.world.getBlockState(post).isOf(Blocks.DIRT)&&walker.canStand(post.up())){
                    var landing=post.up();if(lower==null)lower=landing;
                    // Finish the descent outside the floor's low ceiling before
                    // planning upward work. Otherwise recovery can immediately
                    // pillar back through the same hatch and trap itself again.
                    exit:for(int distance=1;distance<=16;distance++)for(var side:Direction.Type.HORIZONTAL)for(int down=0;down<=2;down++){
                        var exit=landing.offset(side,distance).down(down);
                        if(plannedSolid(exit.up(2))||plannedSolid(exit.up(3))||plannedSolid(exit.up(4))||!walker.canStand(exit))continue;
                        if(walker.canReachStandFrom(landing,exit)){lower=exit;break exit;}
                        if(System.nanoTime()>=floorPlanningDeadline)break exit;
                    }
                    break;
                }
            }
            if(lower==null||!descentReaches(cover,lower))continue;
            int pendingWork=work;
            // A storage trip can start before a placement target is selected.
            // Keep its exit open for the pending build work as well, rather than
            // repairing the low floor first and trapping the return route again.
            if(pendingWork<0)for(int candidate:workCells){
                if(states[candidate]!=CORRECT&&states[candidate]!=IGNORED&&!desired(candidate).isAir()&&!position(candidate).equals(cover)){pendingWork=candidate;break;}
            }
            if(pendingWork>=0&&!position(pendingWork).equals(cover))floorAccessWork.put(cover,pendingWork);
            routeOpening=cover;descentPost=cover;descentView=lower;standGoal=cover.up();
            standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Opening checked temporary floor access";return true;
        }
        if(floorSearchCursor<candidates.size()){walker.release();status="Checking temporary floor access";return true;}
        floorSearchCursor=0;
        return false;
    }
    private boolean waitingForBuiltNeighbour(BlockPos target,BlockState wanted){
        var block=wanted.getBlock();Direction side=null;
        if(block instanceof HopperBlock)side=wanted.get(HopperBlock.FACING);
        else if(block instanceof ShulkerBoxBlock)side=wanted.get(ShulkerBoxBlock.FACING).getOpposite();
        else if(block instanceof WallTorchBlock||block instanceof WallSignBlock||block instanceof WallBannerBlock||block instanceof LadderBlock)side=wanted.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).getOpposite();
        else if(block instanceof PlantBlock||block instanceof RedstoneWireBlock||block instanceof FlowerPotBlock)side=Direction.DOWN;
        if(side==null)return false;
        var neighbour=target.offset(side);
        return plannedSolid(neighbour)&&mc.world.getBlockState(neighbour).isReplaceable();
    }
    /** Free an obsolete attachment base when the bounded scaffold pool is full. */
    private boolean servesActiveScaffold(BlockPos pos){
        if(escapeSupports.contains(pos))return true;
        if(pos.equals(descentPost))return true;
        if(accessSupports.contains(pos))return true;
        if(navigatingCell<0||states[navigatingCell]==CORRECT)return false;
        var target=position(navigatingCell);var side=attachmentSide(desired(navigatingCell));
        // Preserve the short column being assembled for this target. Otherwise
        // a full pool reclaims its new upper piece and immediately rebuilds it.
        for(var direction:Direction.values()){
            if(side!=null&&direction!=side)continue;
            var top=target.offset(direction);
            if(pos.getX()==top.getX()&&pos.getZ()==top.getZ()&&pos.getY()<=top.getY()&&pos.getY()>=top.getY()-2)return true;
        }
        return false;
    }
    private boolean recycleSupport(){
        var floorGuard=mc.player.getBoundingBox().offset(0,-1,0);
        // Reclaim a nearby column tip, never its base beneath another owned
        // piece. Capacity trips should not cross the whole build for a higher
        // but unreachable support while usable tips are close by.
        var candidate=supports.stream().filter(pos->mc.world.isChunkLoaded(pos)&&mc.world.getBlockState(pos).isOf(Blocks.DIRT)&&!plannedSolid(pos)&&!servesActiveScaffold(pos))
            .filter(pos->!supports.contains(pos.up())||!mc.world.getBlockState(pos.up()).isOf(Blocks.DIRT))
            .filter(pos->!new Box(pos).intersects(floorGuard)&&visibleHit(pos)!=null)
            .filter(this::safeToRecycle)
            .min(Comparator.<BlockPos>comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos())).thenComparingInt(pos->-pos.getY())).orElse(null);
        if(candidate==null){
            // A full pool must not strand the builder above supports hidden by
            // the finished floor. Walk to a verified mining view before trying
            // to free capacity; never mine an unseen or unrelated block.
            var feet=mc.player.getBlockPos();int hash=supports.hashCode();
            if(!feet.equals(recycleSearchFeet)||hash!=recycleSearchHash){
                recycleSearchFeet=feet.toImmutable();recycleSearchHash=hash;
                recyclePostCursor=recycleViewCursor=recycleRouteCursor=recycleRetryAt=0;recycleViews.clear();
                recyclePosts=supports.stream().filter(pos->mc.world.isChunkLoaded(pos)&&mc.world.getBlockState(pos).isOf(Blocks.DIRT)&&!plannedSolid(pos)&&!servesActiveScaffold(pos))
                    .filter(pos->!supports.contains(pos.up())||!mc.world.getBlockState(pos.up()).isOf(Blocks.DIRT))
                    .sorted(Comparator.<BlockPos>comparingDouble(pos->pos.getSquaredDistance(feet)).thenComparingInt(pos->-pos.getY())).limit(128).toList();
            }
            if(ticks<recycleRetryAt)return false;
            long deadline=System.nanoTime()+3_000_000;
            int below=(int)Math.floor(effectiveReach()+mc.player.getStandingEyeHeight()-.5),above=(int)Math.floor(effectiveReach()-mc.player.getStandingEyeHeight()+.5);
            int heights=below+above+1,total=49*heights;
            while(recyclePostCursor<recyclePosts.size()&&System.nanoTime()<deadline){
                var pos=recyclePosts.get(recyclePostCursor);
                if(!supports.contains(pos)||!mc.world.getBlockState(pos).isOf(Blocks.DIRT)||servesActiveScaffold(pos)||!safeToRecycle(pos)){
                    recyclePostCursor++;recycleViewCursor=recycleRouteCursor=0;recycleViews.clear();continue;
                }
                var tried=cleanupStands.computeIfAbsent(pos,p->new HashMap<>());tried.values().removeIf(until->until<=ticks);
                while(recycleViewCursor<total&&System.nanoTime()<deadline){
                    int sample=recycleViewCursor++;int dy=sample%heights-below,dx=sample/heights/7-3,dz=sample/heights%7-3;
                    var stand=pos.add(dx,dy,dz);
                    if(tried.containsKey(stand)||!walker.canStand(stand))continue;
                    var eye=walker.standingPoint(stand).add(0,mc.player.getStandingEyeHeight(),0);
                    if(new Box(pos).intersects(mc.player.getBoundingBox().offset(eye.subtract(mc.player.getEyePos())).offset(0,-1,0))||visibleHit(pos,eye)==null)continue;
                    recycleViews.add(stand);
                }
                if(recycleViewCursor<total)break;
                if(recycleRouteCursor==0)recycleViews.sort(Comparator.comparingDouble(stand->stand.getSquaredDistance(feet)));
                while(recycleRouteCursor<recycleViews.size()&&System.nanoTime()<deadline){
                    var stand=recycleViews.get(recycleRouteCursor++);
                    if(tried.containsKey(stand)||!walker.canStand(stand))continue;
                    tried.put(stand,ticks+100);
                    if(walker.canReachStand(stand)){
                        recycleTarget=pos;standGoal=stand;standStarted=standProgressAt=ticks;standProgressPos=mc.player.getEntityPos();walker.stop();status="Moving to reuse temporary supports";return true;
                    }
                }
                if(recycleRouteCursor<recycleViews.size())break;
                recyclePostCursor++;recycleViewCursor=recycleRouteCursor=0;recycleViews.clear();
            }
            if(recyclePostCursor<recyclePosts.size()){walker.release();status="Checking temporary support reuse route";return true;}
            recyclePostCursor=recycleViewCursor=recycleRouteCursor=0;recycleViews.clear();recycleRetryAt=ticks+40;
            return false;
        }
        routeSupportExclusions.put(candidate,ticks+600);routeMining=candidate;mining=candidate;walker.release();status="Reusing temporary support capacity";mineTick();return true;
    }
    private boolean safeToRecycle(BlockPos removed){
        return safeToRecycle(Set.of(removed));
    }
    private boolean safeToRecycle(Set<BlockPos> removed){
        // Query vanilla support rules through a read-only view with this one cell
        // absent. Full blocks can remain floating; attached blocks and gravity
        // blocks must retain their support. No real world state is changed here.
        var view=(net.minecraft.world.WorldView)java.lang.reflect.Proxy.newProxyInstance(net.minecraft.world.WorldView.class.getClassLoader(),new Class<?>[]{net.minecraft.world.WorldView.class},(proxy,method,args)->{
            if(args!=null&&args.length==1&&removed.contains(args[0])){
                if(method.getReturnType()==BlockState.class)return Blocks.AIR.getDefaultState();
                if(method.getReturnType()==net.minecraft.fluid.FluidState.class)return net.minecraft.fluid.Fluids.EMPTY.getDefaultState();
            }
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            try{return method.invoke(mc.world,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
        });
        for(var cell:removed)for(var side:Direction.values()){
            var neighbour=cell.offset(side);if(removed.contains(neighbour))continue;var state=mc.world.getBlockState(neighbour);
            if(!state.getFluidState().isEmpty()||side==Direction.UP&&state.getBlock() instanceof FallingBlock)return false;
            if(!state.isAir()&&!state.canPlaceAt(view,neighbour))return false;
        }
        return true;
    }
    private void placementReceiptTick(){
        walker.release();
        if(pendingServerState==null&&ticks<placementDeadline){status="Waiting for server placement";return;}
        var job=pendingPlacement;var actual=pendingServerState;pendingPlacement=null;pendingServerState=null;
        if(actual!=null&&compatible(actual,job.state)&&!actual.equals(pendingBefore)){
            placementAttemptTarget=null;failedPlacementUntil.remove(job.target);
            lastAction=ticks;triedContainers.clear();triedStands.clear();retryAt.clear();if(!job.temporary)navigatingCell=-1;else navigationStarted=ticks;
            if(job.temporary){supports.add(job.target);if(accessFloor&&accessStand!=null&&job.target.getX()==accessStand.getX()&&job.target.getZ()==accessStand.getZ()&&job.target.getY()<accessStand.getY())accessSupports.add(job.target);if(recoveryPhase==2&&job.target.equals(recoveryBase)){escapeSupports.add(job.target);mc.options.jumpKey.setPressed(false);recoveryJump=false;recoveryPhase=3;}}
            else{recoveryAttempts=0;accessStand=null;accessSupports.clear();}
            if(job.index>=0)updateState(job.index);status="Placement confirmed";
        }else{
            // A refused prediction must not remain as collision geometry or as a face for
            // the next placement. Only reconcile this builder's own predicted block.
            reconcilePrediction(job,actual);
            placementAttemptTarget=null;failedPlacementUntil.put(job.target,ticks+20);
            if(job.index>=0){retryAt.put(job.index,ticks+10);if(autoMove.get())reposition(job.index);}
            if(job.temporary&&recoveryPhase!=0){endRecovery();recoveryCooldown=ticks+20;}
            status=actual==null?"Placement response delayed — rechecking":"Placement rejected — replanning";
        }
    }
    private void mineTick(){
        walker.release();if(mining==null)return;
        var state=mc.world.getBlockState(mining);
        if(state.isAir()){supports.remove(mining);mining=null;digging=false;mc.interactionManager.cancelBlockBreaking();delay=actionDelay();return;}
        if(protectContainers.get()&&state.hasBlockEntity()||state.getHardness(mc.world,mining)<0||!state.getFluidState().isEmpty()){mining=null;return;}
        boolean shovel=state.isIn(BlockTags.SHOVEL_MINEABLE);
        if(autoTools.get()&&!mc.player.getAbilities().creativeMode&&!hasTool(shovel)&&!((restockTarget!=null||depositing)&&mining.equals(routeMining)&&removableRouteFloor(mining))){
            needed=shovel?Items.DIAMOND_SHOVEL:Items.DIAMOND_PICKAXE;
            if(restock.get()&&beginRestock()){mining=null;return;}
            if(maxSpend.get()>0){startBuying(false);if(buying)resumeAfterMarket=true;}else status="Set AH budget to buy the missing "+(shovel?"shovel":"pickaxe");return;
        }
        if(new Box(mining).intersects(mc.player.getBoundingBox().offset(0,-1,0))&&!(mining.equals(routeMining)&&descendingOwnedSupport(mining))){status="Move off the block before clearing it";mining=null;return;}
        var visibleHit=visibleHit(mining);
        if(visibleHit==null){status="Mining target is obstructed";mining=null;return;}
        if(!aim(visibleHit.getPos())){status="Aiming to mine";return;}
        var hit=(BlockHitResult)mc.player.raycast(effectiveReach(),1,false);
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(mining)){status="Mining target is obstructed";mining=null;return;}
        int best=mc.player.getInventory().getSelectedSlot();float speed=0;
        for(int i=0;i<36;i++){var stack=mc.player.getInventory().getStack(i);float candidate=stack.getMiningSpeedMultiplier(state);if(candidate>speed){best=i;speed=candidate;}}
        if(!selectInventorySlot(best))return;
        digging=true;var target=mining;
        withPublishedLook(()->{
            var actual=(BlockHitResult)mc.player.raycast(effectiveReach(),1,false);
            if(actual.getType()!=HitResult.Type.BLOCK||!actual.getBlockPos().equals(target)){digging=false;return;}
            mc.interactionManager.updateBlockBreakingProgress(target,actual.getSide());mc.player.swingHand(Hand.MAIN_HAND);lastAction=ticks;status="Clearing mismatching block";
        });
    }
    private void tuneNote(BlockPos pos,int wanted){
        if(!pos.equals(tuningSession)){tuningSession=pos;tuningTarget=null;tuningClicks=0;}
        walker.release();int note=mc.world.getBlockState(pos).get(NoteBlock.NOTE);
        if(note==wanted){tuningTarget=null;tuningClicks=0;return;}
        if(tuningTarget!=null&&tuningTarget.equals(pos)&&ticks<tuningDeadline){
            if(note==tuningObserved){status="Waiting for note-block update";return;}
            if(note==tuningExpected){tuningTarget=null;delay=actionDelay();return;}
            tuningTarget=null;
        }else if(tuningTarget!=null&&tuningTarget.equals(pos)){pause("Note-block tuning not confirmed — paused");return;}
        if(tuningClicks>=25){pause("Note-block tuning changed unexpectedly — paused");return;}
        var hit=visibleHit(pos);if(hit==null){status="Move closer to tune the note block";return;}
        releaseSneak();if(mc.player.isSneaking()){status="Release sneak to tune note block";return;}
        if(!aim(hit.getPos())){status="Aiming to tune note block";return;}
        withPublishedLook(()->{lastBuildInteraction=ticks;
            if(mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,hit).isAccepted()){
                tuningObserved=note;tuningExpected=(note+1)%25;tuningTarget=pos;tuningDeadline=ticks+80;tuningClicks++;mc.player.swingHand(Hand.MAIN_HAND);lastAction=ticks;status="Tuning note "+wanted;
            }
        });
    }
    private void tuneConfiguration(BlockPos pos,int wanted){
        if(!pos.equals(tuningSession)){tuningSession=pos;tuningTarget=null;tuningClicks=0;}
        walker.release();int value=configuration(mc.world.getBlockState(pos));
        if(value==wanted){tuningTarget=null;tuningClicks=0;return;}
        if(pos.equals(tuningTarget)){
            if(value!=tuningObserved){tuningTarget=null;delay=actionDelay();return;}
            if(ticks>=tuningDeadline){pause("Block configuration not confirmed — paused");return;}
            status="Waiting for block configuration update";return;
        }
        if(tuningClicks>=4){pause("Block configuration changed unexpectedly — paused");return;}
        var hit=visibleHit(pos);if(hit==null){if(autoMove.get())repositionTarget(pos,triedStands.computeIfAbsent(schematic.indexAt(pos.subtract(anchor()),turns(),mirror.get()),i->new HashMap<>()));status="Moving to configure block";return;}
        releaseSneak();if(mc.player.isSneaking()||!aim(hit.getPos()))return;
        withPublishedLook(()->{lastBuildInteraction=ticks;
            if(mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,hit).isAccepted()){tuningObserved=value;tuningTarget=pos;tuningDeadline=ticks+80;tuningClicks++;lastAction=ticks;mc.player.swingHand(Hand.MAIN_HAND);status="Configuring block";}
        });
    }
    private BlockHitResult visibleHit(BlockPos pos){
        return visibleHit(pos,mc.player.getEyePos());
    }
    private BlockHitResult visibleHit(BlockPos pos,Vec3d eye){
        var shape=mc.world.getBlockState(pos).getOutlineShape(mc.world,pos);if(shape.isEmpty())return null;
        var bounds=shape.getBoundingBox();
        for(var face:Direction.values()){
            double x=(bounds.minX+bounds.maxX)/2,y=(bounds.minY+bounds.maxY)/2,z=(bounds.minZ+bounds.maxZ)/2;
            switch(face){case UP->y=bounds.maxY;case DOWN->y=bounds.minY;case NORTH->z=bounds.minZ;case SOUTH->z=bounds.maxZ;case EAST->x=bounds.maxX;case WEST->x=bounds.minX;}
            Vec3d point=new Vec3d(pos.getX()+x,pos.getY()+y,pos.getZ()+z);
            if(point.squaredDistanceTo(eye)>effectiveReach()*effectiveReach())continue;
            var hit=mc.world.raycast(new RaycastContext(eye,point.add(Vec3d.of(face.getVector()).multiply(-.002)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos))return hit;
        }return null;
    }
    private float[] angles(Vec3d point){return angles(mc.player.getEyePos(),point);}
    private float[] angles(Vec3d eye,Vec3d point){var delta=point.subtract(eye);return new float[]{(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)))};}
    private boolean aim(Vec3d point){
        aimPoint=point;
        float[] goal=angles(point);float yaw=MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()),pitch=goal[1]-mc.player.getPitch();
        float speed=turnSpeed.getFloat();
        if(smoothTurning.get()){
            float acceleration=speed*.2f;
            if(Math.signum(yawVelocity)!=Math.signum(yaw))yawVelocity=0;
            if(Math.signum(pitchVelocity)!=Math.signum(pitch))pitchVelocity=0;
            yawVelocity+=MathHelper.clamp(MathHelper.clamp(yaw*.35f,-speed,speed)-yawVelocity,-acceleration,acceleration);
            pitchVelocity+=MathHelper.clamp(MathHelper.clamp(pitch*.35f,-speed,speed)-pitchVelocity,-acceleration,acceleration);
            mc.player.setYaw(mc.player.getYaw()+Math.copySign(Math.min(Math.abs(yaw),Math.abs(yawVelocity)),yaw));
            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch()+Math.copySign(Math.min(Math.abs(pitch),Math.abs(pitchVelocity)),pitch),-90,90));
        }else{
            mc.player.setYaw(mc.player.getYaw()+MathHelper.clamp(yaw,-speed,speed));mc.player.setPitch(MathHelper.clamp(mc.player.getPitch()+MathHelper.clamp(pitch,-speed,speed),-90,90));
        }
        if(Math.abs(MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()))<.75&&Math.abs(goal[1]-mc.player.getPitch())<.75){
            mc.player.setYaw(mc.player.getYaw()+MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()));mc.player.setPitch(goal[1]);yawVelocity=pitchVelocity=0;return true;
        }return false;
    }
    private boolean buildLookReady(){
        // Wait for the next ordinary player tick. Publishing movement here creates a second
        // movement update in the same tick, even when vanilla's bookkeeping is updated.
        var sent=(ClientPlayerLookAccessor)mc.player;
        boolean ready=Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-sent.maro$lastSentYaw()))<=.01f
            &&Math.abs(mc.player.getPitch()-sent.maro$lastSentPitch())<=.01f;
        if(!ready)status="Waiting for normal look update";return ready;
    }
    private void withPublishedLook(Runnable action){
        // Position as well as rotation must be published first. Even an unchanged view
        // can otherwise interact from the previous server position while walking stops.
        queuedLookAction=action;queuedLookYaw=mc.player.getYaw();queuedLookPitch=mc.player.getPitch();
        queuedAimPoint=aimPoint;
        if(lookWaitStarted<0)lookWaitStarted=ticks;
    }
    public void beforeNormalMovement(){
        if(queuedLookAction==null||queuedAimPoint==null||!isEnabled()||!inGame()||mc.currentScreen!=null)return;
        // Physics runs after START_CLIENT_TICK. Recompute the final, tiny aim correction
        // from the new eye position before vanilla publishes it, rather than repeatedly
        // aiming from the earlier position while the player is settling at a chest.
        var goal=angles(queuedAimPoint);
        if(Math.abs(MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()))>2||Math.abs(goal[1]-mc.player.getPitch())>2)return;
        mc.player.setYaw(mc.player.getYaw()+MathHelper.wrapDegrees(goal[0]-mc.player.getYaw()));mc.player.setPitch(goal[1]);
        queuedLookYaw=mc.player.getYaw();queuedLookPitch=mc.player.getPitch();
    }
    public void afterNormalMovement(){
        var action=queuedLookAction;queuedLookAction=null;
        if(action==null||!isEnabled()||!inGame()||world!=mc.world||mc.currentScreen!=null)return;
        if(Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()-queuedLookYaw))>.01f||Math.abs(mc.player.getPitch()-queuedLookPitch)>.01f||!buildLookReady()){
            if(ticks-lookWaitStarted>60)pause("View sync blocked — disable competing rotation modules and resume");
            return;
        }
        lookWaitStarted=-1;
        action.run();
    }
    private boolean recoverUnexpectedBuildMenu(){
        if(mc.currentScreen instanceof AbstractSignEditScreen sign&&ticks-lastBuildInteraction<=160){
            walker.release();releaseSneak();sign.close();delay=4;status="Sign placed - continuing build";return true;
        }
        if(!(mc.currentScreen instanceof HandledScreen<?> menu)||mc.player.currentScreenHandler!=menu.getScreenHandler()){unexpectedBuildHandler=null;return false;}
        var handler=menu.getScreenHandler();
        if(restockTarget!=null&&(restockWait>0||ownedHandler==handler))return false;
        // A manually opened menu stays under the user's control. Only recover a menu following
        // our own block interaction, including a late response to a timed-out chest request.
        if(unexpectedBuildHandler!=handler){
            if(ticks-lastBuildInteraction>160)return false;
            unexpectedBuildHandler=handler;unexpectedMenuAt=ticks;inventoryWait=0;cursorReturns=0;
        }
        walker.release();releaseSneak();status="Closing unexpected build menu";
        if(ticks-unexpectedMenuAt<6)return true;
        if(inventoryWait>0){inventoryWait--;return true;}
        if(!handler.getCursorStack().isEmpty()){
            if(cursorReturns++>=3||!returnCursor(handler)){pause("Could not return held item - clear the cursor and resume");return true;}
            inventoryWait=10;return true;
        }
        mc.player.closeHandledScreen();unexpectedBuildHandler=null;delay=4;return true;
    }
    private boolean returnCursor(ScreenHandler handler){
        var cursor=handler.getCursorStack();if(cursor.isEmpty())return true;
        var destination=handler.slots.stream().filter(slot->slot.canInsert(cursor))
            .filter(slot->slot.getStack().isEmpty()||ItemStack.areItemsAndComponentsEqual(slot.getStack(),cursor)&&slot.getStack().getCount()<slot.getMaxItemCount(cursor))
            .min(Comparator.comparingInt(slot->slot.id==partialSource?0:slot.inventory==mc.player.getInventory()?1:2)).orElse(null);
        if(destination==null)return false;
        mc.interactionManager.clickSlot(handler.syncId,destination.id,0,SlotActionType.PICKUP,mc.player);return true;
    }
    private void resetChestJourney(){
        receivedChestInventory=null;
        chestStand=null;chestTriedStands.clear();chestProgressPosition=null;chestProgressAt=chestSessionStarted=chestInventoryProgressAt=ticks;
        chestInventoryFingerprint=Long.MIN_VALUE;cursorReturns=0;chestJourneyFailed=false;
    }
    public void chestInventoryReceived(int syncId){
        if(!inGame()||!mc.isOnThread())return;
        var handler=mc.player.currentScreenHandler;
        if((depositing&&depositOpenWait>0||restockTarget!=null&&restockWait>0)
            &&handler.syncId==syncId&&handler instanceof GenericContainerScreenHandler chest&&chest.getRows()==6)
            receivedChestInventory=handler;
    }
    private boolean chestInventoryReady(){
        if(receivedChestInventory==ownedHandler)return true;
        walker.release();status="Waiting for confirmed supply chest contents";
        if(ticks-chestInventoryProgressAt>100){
            if(depositing)finishDeposit("Chest contents did not arrive — preparation paused before buying");
            else pause("Chest contents did not arrive — resume after checking the chest");
        }
        return false;
    }
    private boolean chestInventoryStalled(){
        // ItemStack identity is not stable across packets. Compare contents instead.
        var cursor=ownedHandler.getCursorStack();long fingerprint=31L*Registries.ITEM.getRawId(cursor.getItem())+cursor.getCount();
        for(var slot:ownedHandler.slots){var stack=slot.getStack();fingerprint=31*fingerprint+31L*Registries.ITEM.getRawId(stack.getItem())+stack.getCount();}
        if(fingerprint!=chestInventoryFingerprint){chestInventoryFingerprint=fingerprint;chestInventoryProgressAt=ticks;}
        return ticks-chestInventoryProgressAt>100||ticks-chestSessionStarted>1200;
    }
    private BlockHitResult chestHit(BlockPos chest,Vec3d eye){
        var hit=visibleHit(chest,eye);return hit!=null?hit:visibleHit(chest.offset(ChestBlock.getFacing(mc.world.getBlockState(chest))),eye);
    }
    private boolean openSupplyChest(BlockPos chest){
        if(chest==null||!doubleChest(chest)||mc.player.isSneaking()||mc.player.isUsingItem())return false;
        var partner=chest.offset(ChestBlock.getFacing(mc.world.getBlockState(chest)));
        if(ChestBlock.isChestBlocked(mc.world,chest)||ChestBlock.isChestBlocked(mc.world,partner)){
            pause("Selected double chest is blocked — clear the space above both halves");notify(status);return false;
        }
        // Use the current, in-range ray instead of the earlier approach hit after movement.
        var actual=(BlockHitResult)mc.player.raycast(effectiveReach(),1,false);
        if(actual.getType()!=HitResult.Type.BLOCK||!actual.getBlockPos().equals(chest)&&!actual.getBlockPos().equals(partner)){
            status="Re-aiming at selected double chest";return false;
        }
        lastBuildInteraction=ticks;
        mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,actual);
        mc.player.swingHand(Hand.MAIN_HAND);return true;
    }
    private BlockHitResult approachChest(BlockPos chest){
        if(standGoal!=null){followStandGoal();chestProgressAt=ticks;chestJourneyFailed=false;return null;}
        if(chestProgressPosition==null||mc.player.getEntityPos().squaredDistanceTo(chestProgressPosition)>.04){chestProgressPosition=mc.player.getEntityPos();chestProgressAt=ticks;}
        if(chestStand!=null){
            if(walker.standAt(chestStand)){chestStand=null;walker.stop();}
            else if(ticks-chestProgressAt>60||walker.routeUnavailable()){chestTriedStands.put(chestStand,ticks+200);chestStand=null;walker.stop();chestProgressAt=ticks;}
            else{status=walker.status;return null;}
        }
        var hit=chestHit(chest,mc.player.getEyePos());if(hit!=null){walker.release();return hit;}
        if(!withinReach(chest,mc.player.getEyePos())&&ticks-chestProgressAt<=60&&!walker.routeUnavailable()){
            walker.approach(chest,effectiveReach()-.5);status=walker.status;return null;
        }
        chestTriedStands.values().removeIf(until->until<=ticks);
        var options=new ArrayList<BlockPos>();
        int below=(int)Math.floor(effectiveReach()+mc.player.getStandingEyeHeight()-.5),above=(int)Math.floor(effectiveReach()-mc.player.getStandingEyeHeight()+.5);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-below;dy<=above;dy++){
            var stand=chest.add(dx,dy,dz);if(chestTriedStands.containsKey(stand)||!walker.canStand(stand)||mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(stand))<.04)continue;
            if(chestHit(chest,walker.standingPoint(stand).add(0,mc.player.getStandingEyeHeight(),0))!=null)options.add(stand);
        }
        options.sort(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos())));
        long deadline=System.nanoTime()+6_000_000;
        for(var stand:options){chestTriedStands.put(stand,ticks+200);if(walker.canReachStand(stand)){chestStand=stand;chestProgressAt=ticks;walker.stop();status="Walking around an obstructed chest";return null;}if(System.nanoTime()>deadline)break;}
        if(prepareSupportDescent(options)){chestProgressAt=ticks;chestJourneyFailed=false;return null;}
        if(prepareFloorOpening(options,-1)){chestProgressAt=ticks;chestJourneyFailed=false;return null;}
        chestJourneyFailed=ticks-chestProgressAt>100||ticks-chestSessionStarted>1200;
        walker.release();status="Replanning route to selected chest";return null;
    }
    private int actionDelay(){return Math.max(2,spacing.getInt()+ThreadLocalRandom.current().nextInt(timingVariation.getInt()+1));}
    private void select(int slot){if(mc.player==null||mc.interactionManager==null)return;if(mc.player.getInventory().getSelectedSlot()!=slot){mc.player.getInventory().setSelectedSlot(slot);((ClientPlayerInteractionManagerAccessor)mc.interactionManager).maro$syncSelectedSlot();}}
    private boolean selectMaterial(Item item){
        for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isOf(item)){select(i);return true;}
        int source=-1,dest=-1;for(int i=9;i<36;i++)if(mc.player.getInventory().getStack(i).isOf(item)){source=i;break;}
        for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isEmpty()){dest=i;break;}
        if(dest<0)dest=mc.player.getInventory().getSelectedSlot();
        if(source<0||mc.player.currentScreenHandler!=mc.player.playerScreenHandler||!mc.player.currentScreenHandler.getCursorStack().isEmpty())return false;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,source,dest,SlotActionType.SWAP,mc.player);select(dest);delay=actionDelay();return false;
    }
    private boolean selectInventorySlot(int source){
        if(source<9){select(source);return true;}
        if(mc.player.currentScreenHandler!=mc.player.playerScreenHandler||!mc.player.currentScreenHandler.getCursorStack().isEmpty())return false;
        int dest=mc.player.getInventory().getSelectedSlot();for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isEmpty()){dest=i;break;}
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,source,dest,SlotActionType.SWAP,mc.player);select(dest);delay=actionDelay();status="Moving mining tool to hotbar";return false;
    }
    private static boolean clickable(Block block){return block instanceof BlockWithEntity||block instanceof NoteBlock||block instanceof AbstractRedstoneGateBlock||block instanceof ComposterBlock||block instanceof CakeBlock||block instanceof FlowerPotBlock||dev.maro.runtime.utils.world.BlockUtils.isClickable(block);}
    private void releaseSneak(){if(ownsSneak){mc.options.sneakKey.setPressed(false);ownsSneak=false;}}
    private boolean beginRestock(){
        chestAccessRetryAt.values().removeIf(until->until<=ticks);
        if(needed!=restockAttemptItem){triedContainers.clear();restockAttemptItem=needed;}
        var excluded=new HashSet<>(triedContainers);if(needed!=null)emptyChestItems.forEach((pos,items)->{if(items.contains(needed))excluded.add(pos);});
        var selected=supplyChests();
        restockTarget=selected.stream().filter(chest->!excluded.contains(chest)&&!excluded.contains(chest.offset(ChestBlock.getFacing(mc.world.getBlockState(chest)))))
            .filter(chest->!chestAccessRetryAt.containsKey(chest)).findFirst().orElse(null);
        if(restockTarget==null){
            // A failed walk/open is not a stock check. Retry uninspected selected
            // storage instead of declaring its supplies absent or buying duplicates.
            if(selected.stream().anyMatch(chest->!excluded.contains(chest)&&chestAccessRetryAt.containsKey(chest))){walker.release();status="Retrying access to selected chest — contents not checked";return true;}
            return false;
        }
        restockBatch=Map.copyOf(requiredMaterials());foodRestock=supportRestock=false;restockWait=inventoryWait=0;partialSource=-1;partialItem=null;restockTriedSlots.clear();restockSlotRetries.clear();resetChestJourney();walker.stop();status="Restocking";return true;
    }
    private void finishRestock(String reason){
        if(ownedHandler!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();
        if(restockTarget!=null){
            if(ownedHandler!=null&&receivedChestInventory==ownedHandler){triedContainers.add(restockTarget);chestAccessRetryAt.remove(restockTarget);}
            else chestAccessRetryAt.put(restockTarget,ticks+100);
        }
        ownedHandler=null;restockTarget=null;restockBatch=Map.of();partialSource=-1;partialItem=null;restockWait=0;
        resetChestJourney();walker.stop();delay=6;status=reason;
    }
    private void restockTick(){
        if(ownedHandler==null&&ticks-chestSessionStarted>1200){finishRestock("Chest access timed out - continuing supply search");return;}
        if(ownedHandler!=null){
            if(!(mc.currentScreen instanceof HandledScreen<?> screen)||screen.getScreenHandler()!=ownedHandler||mc.player.currentScreenHandler!=ownedHandler){finishRestock("Chest closed - continuing supply search");return;}
            if(!chestInventoryReady())return;
            boolean stalled=chestInventoryStalled();
            if(inventoryWait>0){inventoryWait--;return;}
            if(stalled){
                if(!ownedHandler.getCursorStack().isEmpty()){
                    if(cursorReturns++>=3||!returnCursor(ownedHandler)){pause("Restock transfer rejected - clear the held item and resume");return;}
                    partialSource=-1;partialItem=null;inventoryWait=10;return;
                }
                finishRestock("Chest transfer stalled - trying remaining supplies");return;
            }
            if(partialSource>=0){partialRestockTick();return;}
            if(!ownedHandler.getCursorStack().isEmpty()){
                if(cursorReturns++>=3||!returnCursor(ownedHandler)){pause("Restock could not return the held item - clear the cursor and resume");return;}
                inventoryWait=10;status="Returning held restock item";return;
            }
            cursorReturns=0;
            Map<Item,Integer> required=new HashMap<>(restockBatch);if(support.get())required.merge(Items.DIRT,restockDirt.getInt(),Integer::sum);required.keySet().removeAll(ignoredMaterials);addRequiredTools(required);if(foodRestock){required.clear();required.put(Items.COOKED_BEEF,steakReserve.getInt());}
            if(supportRestock){required.clear();required.put(Items.DIRT,supportReserve());}
            if(autoTools.get()&&!foodRestock&&!supportRestock)for(boolean shovel:new boolean[]{false,true}){
                var tag=shovel?ItemTags.SHOVELS:ItemTags.PICKAXES;
                if(hasTool(shovel))continue;
                var stored=ownedHandler.slots.stream().filter(slot->slot.inventory!=mc.player.getInventory()&&slot.getStack().isIn(tag)).findFirst().orElse(null);
                if(stored!=null){required.remove(shovel?Items.DIAMOND_SHOVEL:Items.DIAMOND_PICKAXE);required.put(stored.getStack().getItem(),1);}
            }
            for(var slot:ownedHandler.slots){
                var stack=slot.getStack();if(stack.isEmpty()||slot.inventory==mc.player.getInventory()||restockTriedSlots.contains(slot.id))continue;
                if(inventoryCount(stack.getItem())<required.getOrDefault(stack.getItem(),0)&&canReceive(stack.getItem())){
                    int needed=required.get(stack.getItem())-inventoryCount(stack.getItem());
                    if(needed<stack.getCount()){
                        var destination=ownedHandler.slots.stream().filter(target->target.inventory==mc.player.getInventory()&&target.canInsert(stack))
                            .filter(target->target.getStack().isEmpty()||ItemStack.areItemsAndComponentsEqual(target.getStack(),stack)&&target.getStack().getCount()<target.getStack().getMaxCount()).findFirst().orElse(null);
                        if(destination==null)continue;int room=destination.getStack().isEmpty()?stack.getMaxCount():destination.getStack().getMaxCount()-destination.getStack().getCount();
                        partialSource=slot.id;partialDestination=destination.id;partialRemaining=Math.min(needed,room);partialItem=stack.getItem();
                        mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.PICKUP,mc.player);inventoryWait=6;status="Taking exact batch material quantity";return;
                    }
                    mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);restockTriedSlots.add(slot.id);inventoryWait=8;return;
                }
            }
            if(stockpile.get())for(var slot:ownedHandler.slots){
                var stack=slot.getStack();if(slot.inventory!=mc.player.getInventory()||stack.isEmpty()||restockTriedSlots.contains(slot.id)||!schematic.materials().containsKey(stack.getItem()))continue;
                if(inventoryCount(stack.getItem())-stack.getCount()>=required.getOrDefault(stack.getItem(),0)){
                    mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);restockTriedSlots.add(slot.id);inventoryWait=8;return;
                }
            }
            for(var entry:required.entrySet())if(inventoryCount(entry.getKey())<entry.getValue()&&ownedHandler.slots.stream().noneMatch(slot->slot.inventory!=mc.player.getInventory()&&slot.getStack().isOf(entry.getKey())))emptyChestItems.computeIfAbsent(restockTarget,p->new HashSet<>()).add(entry.getKey());
            finishRestock("Restock checked");return;
        }
        if(mc.currentScreen instanceof HandledScreen<?> screen){
            if(restockWait>0&&screen.getScreenHandler() instanceof GenericContainerScreenHandler chest){ownedHandler=chest;chestInventoryProgressAt=ticks;walker.release();if(chest.getRows()!=6)finishRestock("Unexpected chest size - checking another supply chest");return;}
            if(restockWait>0){ownedHandler=screen.getScreenHandler();finishRestock("Unexpected supply menu - checking another chest");return;}
            walker.release();status="Close the current menu to restock";return;
        }
        if(restockWait>0){if(++restockWait>80)finishRestock("Chest did not open - continuing supply search");return;}
        if(!doubleChest(restockTarget)){finishRestock("Supply chest unavailable - checking other supplies");return;}
        var hit=approachChest(restockTarget);if(hit==null){if(chestJourneyFailed)finishRestock("No route to this chest - checking other supplies");return;}
        if(!aim(hit.getPos()))return;
        releaseSneak();if(mc.player.isSneaking())return;
        withPublishedLook(()->{if(openSupplyChest(restockTarget))restockWait=1;});
    }
    private void partialRestockTick(){
        var cursor=ownedHandler.getCursorStack();if(cursor.isEmpty()){
            // A denied/late pickup must not leave the builder latched to this slot forever.
            if(ticks-chestInventoryProgressAt<40){inventoryWait=4;return;}
            if(restockSlotRetries.merge(partialSource,1,Integer::sum)>=2)restockTriedSlots.add(partialSource);
            partialSource=-1;partialItem=null;chestInventoryProgressAt=ticks;status="Restock pickup rejected - retrying supplies";return;
        }
        if(!cursor.isOf(partialItem)){
            partialSource=-1;partialItem=null;return;
        }
        if(partialRemaining>0){
            var destination=ownedHandler.getSlot(partialDestination).getStack();if(!destination.isEmpty()&&(!ItemStack.areItemsAndComponentsEqual(destination,cursor)||destination.getCount()>=destination.getMaxCount())){partialRemaining=0;return;}
            mc.interactionManager.clickSlot(ownedHandler.syncId,partialDestination,1,SlotActionType.PICKUP,mc.player);partialRemaining--;inventoryWait=actionDelay();return;
        }
        mc.interactionManager.clickSlot(ownedHandler.syncId,partialSource,0,SlotActionType.PICKUP,mc.player);restockTriedSlots.add(partialSource);partialSource=-1;partialItem=null;inventoryWait=6;
    }
    private boolean canReceive(Item item){for(int i=0;i<36;i++){var stack=mc.player.getInventory().getStack(i);if(stack.isEmpty()||stack.isOf(item)&&stack.getCount()<stack.getMaxCount())return true;}return false;}
    public void depositAll(){
        if(!inGame()){notify("Join a world before depositing");return;}
        if(mc.player.currentScreenHandler!=mc.player.playerScreenHandler&&ownedHandler!=mc.player.currentScreenHandler){notify("Close the current container before depositing");return;}
        int prep=preparationStage;pause("Returning to your selected double chests");preparationStage=prep;
        var selected=supplyChests();
        if(selected.isEmpty()||prep==1&&selected.size()!=containers.size()){preparationStage=0;status=containers.isEmpty()?"Look at your double chests and press R to add them":"A selected chest is unavailable — load it or remove its selection";notify(status);return;}
        depositQueue.addAll(selected);setEnabled(true);building=false;depositing=true;nextDepositChest();status="Depositing inventory into selected chests";mc.setScreen(null);
    }
    public BlockPos selectedSupplyChest(){return containers.stream().findFirst().orElse(null);}
    private List<BlockPos> supplyChests(){
        var normalized=new LinkedHashSet<BlockPos>();for(var pos:containers){if(mc.world.isChunkLoaded(pos)&&doubleChest(pos)){var partner=pos.offset(ChestBlock.getFacing(mc.world.getBlockState(pos)));normalized.add(pos.compareTo(partner)<0?pos:partner);}else normalized.add(pos);}containers.clear();containers.addAll(normalized);
        return containers.stream().filter(pos->mc.world.isChunkLoaded(pos)&&pos.getSquaredDistance(mc.player.getBlockPos())<=walkDistance.get()*walkDistance.get()&&doubleChest(pos)).sorted(Comparator.comparingDouble(pos->pos.getSquaredDistance(mc.player.getBlockPos()))).toList();}
    private void recordChestStock(){
        if(ownedHandler==null||receivedChestInventory!=ownedHandler||depositTarget==null)return;
        var contents=new HashMap<Item,Integer>();for(var slot:ownedHandler.slots)if(slot.inventory!=mc.player.getInventory()&&!slot.getStack().isEmpty())contents.merge(slot.getStack().getItem(),slot.getStack().getCount(),Integer::sum);
        chestStocks.put(depositTarget,contents);preparedStock.clear();chestStocks.forEach((chest,items)->{if(containers.contains(chest))items.forEach((item,count)->preparedStock.merge(item,count,Integer::sum));});
        emptyChestItems.remove(depositTarget);if(doubleChest(depositTarget))emptyChestItems.remove(depositTarget.offset(ChestBlock.getFacing(mc.world.getBlockState(depositTarget))));
    }
    private boolean nextDepositChest(){
        recordChestStock();if(ownedHandler!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();ownedHandler=null;
        if(depositQueue.isEmpty())return false;
        depositTarget=depositQueue.removeFirst();depositOpenWait=inventoryWait=0;depositSlot=-1;depositDeadline=ticks+1200;resetChestJourney();walker.stop();status="Continuing storage in selected chest "+depositTarget.toShortString();return true;
    }
    private boolean chestHasRoom(ItemStack stack){return ownedHandler.slots.stream().anyMatch(slot->slot.inventory!=mc.player.getInventory()&&slot.canInsert(stack)&&(slot.getStack().isEmpty()||ItemStack.areItemsAndComponentsEqual(slot.getStack(),stack)&&slot.getStack().getCount()<slot.getMaxItemCount(stack)));}
    private boolean keepBuildTool(ItemStack stack){return autoTools.get()&&(preparationStage>0||resumeShoppingAfterDeposit)&&(stack.isIn(ItemTags.PICKAXES)||stack.isIn(ItemTags.SHOVELS));}
    private boolean doubleChest(BlockPos pos){
        var state=mc.world.getBlockState(pos);if(!(state.getBlock() instanceof ChestBlock)||state.get(ChestBlock.CHEST_TYPE)==net.minecraft.block.enums.ChestType.SINGLE)return false;
        var partner=pos.offset(ChestBlock.getFacing(state));if(!mc.world.isChunkLoaded(partner))return false;var other=mc.world.getBlockState(partner);
        return other.getBlock()==state.getBlock()&&other.get(ChestBlock.CHEST_TYPE)!=net.minecraft.block.enums.ChestType.SINGLE&&other.get(ChestBlock.CHEST_TYPE)!=state.get(ChestBlock.CHEST_TYPE)&&other.get(ChestBlock.FACING)==state.get(ChestBlock.FACING);
    }
    private void finishDeposit(String reason){
        finishDeposit(reason,false);
    }
    private void finishDeposit(String reason,boolean success){
        int prep=preparationStage;
        if(ownedHandler!=null)recordChestStock();depositQueue.clear();
        if(success&&prep==1&&!chestStocks.keySet().containsAll(containers)){
            success=false;reason="Not all selected chests were scanned — preparation paused before buying";
        }
        if(ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();
        ownedHandler=null;depositing=false;depositTarget=null;depositSlot=-1;walker.stop();status=reason;notify(reason);
        boolean resume=success&&resumeShoppingAfterDeposit;
        if(resume){shopping.clear();shopping.putAll(depositedShopping);buyingItem=null;pendingOffer=null;buying=true;marketStage=0;marketWait=6;status="Continuing buying after deposit";}
        resumeShoppingAfterDeposit=false;depositedShopping.clear();
        if(!success){preparationStage=0;return;}
        if(prep==1){preparationStage=2;startBuying(false);}
        else if(prep==3){preparationStage=0;preparationReady=true;triedContainers.clear();emptyChestItems.clear();startBuild();status="Whole-build supplies stored — building nearby sections";}
        else if(prep==4){preparationStage=0;status=preparationStopReason;notify(status);}
    }
    private void depositTick(){
        if(!doubleChest(depositTarget)){finishDeposit("Deposit stopped — double chest is no longer available");return;}
        if(ticks>=depositDeadline){finishDeposit("Deposit stopped — no clear path or inventory update");return;}
        if(ownedHandler!=null){
            if(!(mc.currentScreen instanceof HandledScreen<?> menu)||menu.getScreenHandler()!=ownedHandler||mc.player.currentScreenHandler!=ownedHandler){finishDeposit("Deposit cancelled — chest closed");return;}
            if(!chestInventoryReady())return;
            boolean stalled=chestInventoryStalled();
            if(inventoryWait>0){inventoryWait--;return;}
            if(!ownedHandler.getCursorStack().isEmpty()){
                if(cursorReturns++>=3||!returnCursor(ownedHandler)){pause("Deposit could not return the held item - clear the cursor and resume");return;}
                inventoryWait=10;status="Returning held storage item";return;
            }
            cursorReturns=0;
            if(stalled){if(nextDepositChest())return;finishDeposit("Chest transfer stalled - remaining items kept");return;}
            if(depositSlot>=0){
                var current=ownedHandler.getSlot(depositSlot).getStack();
                if(!current.isEmpty()&&current.getCount()>=depositCount){if(ticks>=depositAckDeadline){if(!nextDepositChest())finishDeposit("Transfer rejected - remaining items kept");}return;}
                depositSlot=-1;
            }
            for(var slot:ownedHandler.slots){
                if(slot.inventory!=mc.player.getInventory()||slot.getStack().isEmpty()||!chestHasRoom(slot.getStack()))continue;
                if(keepBuildTool(slot.getStack()))continue;
                depositSlot=slot.id;depositCount=slot.getStack().getCount();
                mc.interactionManager.clickSlot(ownedHandler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);inventoryWait=actionDelay();depositAckDeadline=ticks+80;status="Moving items into double chest";return;
            }
            boolean remaining=ownedHandler.slots.stream().anyMatch(slot->slot.inventory==mc.player.getInventory()&&!slot.getStack().isEmpty()
                &&!keepBuildTool(slot.getStack()));
            if((remaining||preparationStage==1)&&nextDepositChest())return;
            finishDeposit(remaining?"Selected chests are full — add another chest; remaining items kept":"Inventory stored across selected chests",!remaining);return;
        }
        if(mc.currentScreen instanceof HandledScreen<?> screen){
            if(depositOpenWait>0&&screen.getScreenHandler() instanceof GenericContainerScreenHandler chest){
                ownedHandler=chest;chestInventoryProgressAt=ticks;walker.release();if(chest.getRows()!=6)finishDeposit("Deposit stopped — expected a double chest");return;
            }
            if(depositOpenWait>0){ownedHandler=screen.getScreenHandler();if(!nextDepositChest())finishDeposit("Unexpected storage menu - remaining items kept");return;}
            status="Close the current menu to deposit";walker.release();return;
        }
        if(depositOpenWait>0){if(++depositOpenWait>80)finishDeposit("Could not open double chest");return;}
        var hit=approachChest(depositTarget);if(hit==null){if(chestJourneyFailed){if(!nextDepositChest())finishDeposit("No route to selected storage - remaining items kept");}return;}
        if(!aim(hit.getPos())){status="Aiming at double chest";return;}
        releaseSneak();if(mc.player.isSneaking())return;
        withPublishedLook(()->{if(openSupplyChest(depositTarget))depositOpenWait=1;});
    }
    private void creativeMaterials(){
        if(!inGame()||schematic==null||!mc.player.getAbilities().creativeMode){notify("Get Materials requires creative mode");return;}
        for(var entry:requiredMaterials().entrySet()){
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
            int i=pasteCursor++;if(!schematic.included(i)||!layerAllows(i))continue;var state=desired(i);if(materialIgnored(state)||state.isAir()&&!mineOut.get())continue;
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
        capture=new Schematic(name,"Snapshot",w,capturedHeight,l,BlockPos.ORIGIN,captureStates);captureStates=null;Schematic snapshot=capture;Path path=folder().resolve(name);int generation=ioGeneration;
        CompletableFuture.runAsync(()->{try{NbtIo.writeCompressed(SchematicIO.encodeStructure(snapshot),path);}catch(IOException e){throw new CompletionException(e);}},IO)
            .whenComplete((result,error)->mc.execute(()->{if(generation!=ioGeneration)return;if(error!=null)notify("Snapshot save failed: "+rootMessage(error));else{load(path);notify("Saved "+name+" (block states)");}}));
    }
    private Map<Item,Integer> shoppingNeeds(int dirt){
        Map<Item,Integer> result=new HashMap<>(requiredMaterials());if(support.get())result.merge(Items.DIRT,dirt,Integer::sum);result.keySet().removeAll(ignoredMaterials);addRequiredTools(result);
        if(!buySteak.get())result.remove(Items.COOKED_BEEF);
        result.replaceAll((item,count)->Math.max(0,count-inventoryCount(item)));result.values().removeIf(count->count<=0);return result;
    }
    private void startBuying(boolean estimateOnly){
        if(!inGame()||schematic==null){notify("Load a schematic first");return;}
        if(!estimateOnly&&maxSpend.get()<=0&&preparationStage!=2){notify("Set Max Total Spend before buying");return;}
        if(mc.player.currentScreenHandler!=mc.player.playerScreenHandler){notify("Close the current container before buying");return;}
        boolean resume=building&&!estimateOnly&&autoBuy.get();
        int prep=preparationStage;pause(estimateOnly?"Estimating auction cost":"Buying materials");preparationStage=prep;setEnabled(true);building=false;buying=true;resumeAfterMarket=resume;estimating=estimateOnly;shopping.clear();
        spent=buildBudgetActive?buildBudgetSpent:0;estimate=0;buyingItem=null;marketWait=marketStage=0;pendingOffer=null;boughtListings.clear();unavailableListings.clear();pendingListing="";soldNotice=false;soldSkips=marketRechecks=0;
        marketStage=-1;
        if(buyNotifications.get())notify(estimateOnly?"Reading auction prices":"Material buying started — budget "+(long)maxSpend.get().doubleValue());
    }
    private void searchMarket(){
        searchMarket(false);
    }
    private void searchMarket(boolean returnToBest){
        String key=Registries.ITEM.getId(buyingItem).toString();if(!ahSearch.is("Registry ID")){key=Registries.ITEM.getId(buyingItem).getPath();if(ahSearch.is("Spaced"))key=buyingItem==Items.COOKED_BEEF?"steak":key.replace('_',' ');}
        String command=ahCommand.get().strip().replaceFirst("^/","");
        if(!command.matches("[a-zA-Z0-9_]+")){finishBuying("Invalid AH command");return;}
        if(mc.currentScreen instanceof HandledScreen<?>)mc.player.closeHandledScreen();
        ownedHandler=null;marketPage=1;returningToOffer=returnToBest;
        if(!returnToBest){bestMarketOffer=null;bestMarketKey="";bestMarketPage=0;marketInventoryBlocked=false;}
        boughtListings.clear();
        mc.getNetworkHandler().sendChatCommand(command+" "+key);marketWait=10;marketStage=1;marketDeadline=ticks+160;status="Searching "+buyingItem.getName().getString();
    }
    private void marketTick(){
        if(marketWait>0){marketWait--;return;}
        if(marketStage==-1){if(completedScans==0){status="Scanning material requirements";return;}var needs=foodShopping?Map.of(Items.COOKED_BEEF,Math.max(0,steakReserve.getInt()-inventoryCount(Items.COOKED_BEEF))):supportShopping?Map.of(Items.DIRT,Math.max(0,supportReserve()-inventoryCount(Items.DIRT))):preparationStage==2?wholeBuildNeeds():shoppingNeeds(buyDirt.getInt());needs.entrySet().stream().filter(e->e.getValue()>0).sorted(Comparator.comparing(e->Registries.ITEM.getId(e.getKey()).toString())).forEach(e->shopping.put(e.getKey(),e.getValue()));marketStage=0;}
        if(buyingItem==null){
            if(!estimating&&!shopping.isEmpty()&&maxSpend.get()<=spent){finishBuying("AH budget exhausted — increase the budget for missing supplies");return;}
            if(shopping.isEmpty()){boolean preparing=preparationStage==2;boolean resume=resumeAfterMarket&&!estimating;boolean deposit=!estimating&&depositWhen.is("After Buying");finishBuying(estimating?"Estimated material cost: "+Math.round(estimate):"Buying finished — spent "+Math.round(spent));if(preparing)return;if(deposit)depositAll();else if(resume){building=true;delay=6;status="Continuing build after buying";}return;}
            buyingItem=shopping.keySet().iterator().next();marketPage=1;searchMarket();return;
        }
        // Some AH servers buy on the listing click; others reuse the same handler for confirmation.
        // Observe actual inventory receipt before deciding which menu transition happened.
        if(marketStage==2||marketStage==3){
            int gained=inventoryCount(buyingItem)-inventoryBefore;
            if(gained>=pendingOffer.count()){
                if(marketStage==2)spent+=pendingOffer.total();
                if(buildBudgetActive)buildBudgetSpent=spent;
                shopping.computeIfPresent(buyingItem,(k,v)->Math.max(0,v-gained));if(shopping.getOrDefault(buyingItem,0)<=0)shopping.remove(buyingItem);
                status="Bought "+gained+" "+buyingItem.getName().getString();
                pendingOffer=null;buyingItem=null;marketStage=0;marketRechecks=0;soldNotice=false;marketWait=buySpacing.getInt();return;
            }
            boolean unavailable=soldNotice;
            if(mc.currentScreen instanceof HandledScreen<?> menu){
                unavailable|=AuctionMarket.unavailable(menu.getTitle().getString())||menu.getScreenHandler().slots.stream().anyMatch(slot->slot.inventory!=mc.player.getInventory()&&AuctionMarket.unavailable(slot.getStack().getName().getString()));
            }
            if(unavailable){
                soldNotice=false;
                if(gained!=0){finishBuying("Partial purchase receipt — check inventory before buying again");return;}
                if(marketStage==3)spent=Math.max(0,spent-pendingOffer.total());
                if(buildBudgetActive)buildBudgetSpent=spent;
                unavailableListings.add(pendingListing);pendingOffer=null;ownedHandler=null;
                if(++soldSkips>24){finishBuying("Too many sold listings — refresh the auction and try again");return;}
                searchMarket();status="Listing already purchased — finding another";return;
            }
            if(marketStage==3){if(ticks>=marketDeadline)finishBuying("Purchase not confirmed in inventory; no retry");else status="Waiting for purchased items";return;}
        }
        if(!(mc.currentScreen instanceof HandledScreen<?> screen)){
            if(ticks>=marketDeadline)finishBuying(marketStage==2?"No purchase receipt or recognized confirmation; no retry":"Auction menu did not open");return;
        }
        ScreenHandler handler=screen.getScreenHandler();
        if(!handler.getCursorStack().isEmpty()){if(ticks>=marketDeadline)finishBuying("Buying stopped — cursor is occupied");return;}
        String title=screen.getTitle().getString();
        if(marketStage==2){
            var yes=handler.slots.stream().filter(slot->slot.inventory!=mc.player.getInventory()&&!slot.getStack().isEmpty())
                .filter(slot->!AuctionMarket.word(slot.getStack().getName().getString(),confirmNo.get())&&AuctionMarket.word(slot.getStack().getName().getString(),confirmYes.get()))
                .filter(slot->!slot.getStack().isOf(buyingItem)||!Double.isFinite(AuctionMarket.price(slot.getStack(),priceKeyword.get()))).findFirst().orElse(null);
            boolean hasCancel=handler.slots.stream().anyMatch(slot->slot.inventory!=mc.player.getInventory()&&!slot.getStack().isEmpty()&&AuctionMarket.word(slot.getStack().getName().getString(),confirmNo.get()));
            // A title is a hint, not a protocol ID. Unknown/reused titles need both confirmation controls.
            if(yes==null||!hasCancel&&!AuctionMarket.word(title,confirmTitle.get())){
                if(ticks>=marketDeadline)finishBuying("No purchase receipt or recognized confirmation; no retry");else status="Waiting for purchase confirmation";return;
            }
            ownedHandler=handler;
            double controlPrice=AuctionMarket.price(yes.getStack(),priceKeyword.get());
            if(Double.isFinite(controlPrice)&&Math.abs(controlPrice-pendingOffer.total())>=.01){finishBuying("Confirmation price changed — stopped");return;}
            boolean itemVerified=handler.slots.stream().filter(slot->slot.inventory!=mc.player.getInventory()&&slot!=yes)
                .anyMatch(slot->{var item=slot.getStack();if(!item.isOf(buyingItem)||item.getCount()!=pendingOffer.count())return false;
                    double price=AuctionMarket.price(item,priceKeyword.get());
                    return Double.isFinite(price)?Math.abs(price-pendingOffer.total())<.01:!AuctionMarket.hasPriceField(item,priceKeyword.get())&&Double.isFinite(controlPrice);
                });
            if(!itemVerified){
                boolean populated=handler.slots.stream().anyMatch(slot->slot.inventory!=mc.player.getInventory()&&slot.getStack().isOf(buyingItem)&&Double.isFinite(AuctionMarket.price(slot.getStack(),priceKeyword.get())));
                if(populated||ticks>=marketDeadline)finishBuying("Confirmation item, count or price changed — stopped");return;
            }
            ownedHandler=handler;
            if(spent+pendingOffer.total()>maxSpend.get()){finishBuying("Session budget reached");return;}
            spent+=pendingOffer.total();
            if(buildBudgetActive)buildBudgetSpent=spent;
            mc.interactionManager.clickSlot(handler.syncId,yes.id,0,SlotActionType.PICKUP,mc.player);marketStage=3;marketWait=buySpacing.getInt();marketDeadline=ticks+160;return;
        }
        if(!AuctionMarket.word(title,ahTitle.get())){if(ticks>=marketDeadline)finishBuying("Unexpected auction menu title: "+title);return;}
        ownedHandler=handler;
        var offers=AuctionMarket.offers(handler,mc.player.getInventory(),buyingItem,priceKeyword.get()).stream()
            .filter(o->!boughtListings.contains(handler.syncId+":"+o.slot()+":"+o.total()+":"+o.count())&&!unavailableListings.contains(listingKey(handler,o))).toList();
        if(estimating){
            var cheapest=offers.stream().min(Comparator.comparingDouble(AuctionMarket.Offer::each)).orElse(null);
            if(cheapest!=null){estimate+=cheapest.each()*shopping.get(buyingItem);shopping.remove(buyingItem);buyingItem=null;marketWait=buySpacing.getInt();return;}
        }else{
            int requested=shopping.getOrDefault(buyingItem,0);
            int extra=buyingItem==Items.DIAMOND_PICKAXE||buyingItem==Items.DIAMOND_SHOVEL?0:preparationStage==2?Math.max(63,overbuy.getInt()):overbuy.getInt();
            var allChoice=AuctionMarket.choose(offers,requested,extra,maxItem.get(),maxSpend.get()-spent,preferStacks.get(),tolerance.get());
            var choice=AuctionMarket.choose(offers.stream().filter(offer->canReceiveOffer(buyingItem,offer.count())).toList(),requested,extra,maxItem.get(),maxSpend.get()-spent,preferStacks.get(),tolerance.get());
            marketInventoryBlocked|=allChoice!=null&&choice==null;
            if(returningToOffer&&marketPage<bestMarketPage){
                if(nextMarketPage(handler))return;
                if(++marketRechecks>3){finishBuying("Auction pages changed — try again");return;}searchMarket();return;
            }
            if(!returningToOffer){
                if(choice!=null&&(bestMarketOffer==null||choice.each()<bestMarketOffer.each()||choice.each()==bestMarketOffer.each()&&choice.total()<bestMarketOffer.total())){bestMarketOffer=choice;bestMarketKey=listingKey(handler,choice);bestMarketPage=marketPage;}
                if(nextMarketPage(handler))return;
                if(bestMarketOffer!=null&&bestMarketPage!=marketPage){searchMarket(true);status="Returning to cheapest listing";return;}
                if(bestMarketOffer==null&&marketInventoryBlocked){handleFullInventory();return;}
            }else if(choice==null||choice.each()>bestMarketOffer.each()||choice.each()==bestMarketOffer.each()&&choice.total()>bestMarketOffer.total()){
                if(++marketRechecks>3){finishBuying("Cheapest listing changed — try again");return;}searchMarket();return;
            }
            if(choice!=null){
                pendingOffer=choice;pendingListing=listingKey(handler,choice);soldNotice=false;inventoryBefore=inventoryCount(buyingItem);boughtListings.add(handler.syncId+":"+choice.slot()+":"+choice.total()+":"+choice.count());
                mc.interactionManager.clickSlot(handler.syncId,choice.slot(),0,SlotActionType.PICKUP,mc.player);marketStage=2;marketWait=buySpacing.getInt();marketDeadline=ticks+160;return;
            }
        }
        if(estimating&&nextMarketPage(handler))return;
        if(ticks>=marketDeadline)finishBuying("No suitable listing for "+buyingItem.getName().getString()+" within price / quantity limits");
    }
    private boolean nextMarketPage(ScreenHandler handler){
        if(marketPage<maxPages.getInt())for(var slot:handler.slots){
            if(slot.inventory!=mc.player.getInventory()&&!slot.getStack().isEmpty()&&AuctionMarket.word(slot.getStack().getName().getString(),nextWord.get())){
                mc.interactionManager.clickSlot(handler.syncId,slot.id,0,SlotActionType.PICKUP,mc.player);marketPage++;marketWait=20;marketDeadline=ticks+160;return true;
            }
        }
        return false;
    }
    private void handleFullInventory(){
        if(preparationStage==0&&(layerSupply()||sectionSupply())&&buyingItem!=Items.COOKED_BEEF&&buyingItem!=Items.DIAMOND_PICKAXE&&buyingItem!=Items.DIAMOND_SHOVEL&&requiredMaterials().entrySet().stream().anyMatch(entry->entry.getValue()>0&&inventoryCount(entry.getKey())>0)){
            boolean resume=resumeAfterMarket;finishBuying("Supply batch ready — continue building before buying more");if(resume){building=true;delay=6;status="Building with this supply batch";}return;
        }
        if(!depositWhen.is("Manual")){var outstanding=new LinkedHashMap<>(shopping);depositAll();if(depositing){depositedShopping.putAll(outstanding);resumeShoppingAfterDeposit=true;}return;}
        finishBuying("Inventory full — use Deposit All to make space");
    }
    private boolean canReceiveOffer(Item item,int count){
        var ordinary=new ItemStack(item);int space=0;for(int i=0;i<36;i++){var stack=mc.player.getInventory().getStack(i);if(stack.isEmpty())space+=item.getMaxCount();else if(ItemStack.areItemsAndComponentsEqual(stack,ordinary))space+=Math.max(0,stack.getMaxCount()-stack.getCount());}return space>=count;
    }
    private String listingKey(ScreenHandler handler,AuctionMarket.Offer offer){
        var stack=handler.getSlot(offer.slot()).getStack();return Registries.ITEM.getId(offer.item())+":"+marketPage+":"+offer.slot()+":"+offer.count()+":"+offer.total()+":"+AuctionMarket.listingIdentity(stack);
    }
    private void finishBuying(String reason){
        boolean prepared=preparationStage==2&&!estimating,complete=shopping.isEmpty()&&reason.startsWith("Buying finished");
        buying=false;buyingItem=null;pendingOffer=null;shopping.clear();
        if(ownedHandler!=null&&mc.player!=null&&mc.player.currentScreenHandler==ownedHandler)mc.player.closeHandledScreen();ownedHandler=null;status=reason;if(buyNotifications.get())notify(reason);
        if(prepared){preparationStopReason=reason;preparationStage=complete?3:4;depositAll();}
    }
    @Override public void onRender2D(DrawContext ctx,float delta){
        if(!statusHud.get()||!inGame()||mc.currentScreen!=null)return;
        SmoothHudText.beginFrame();float w=280,x=(ctx.getScaledWindowWidth()-w)/2f,y=10;
        Render2D.shadow(ctx,x,y,w,58,9,8,0x50000000);Render2D.roundRect(ctx,x,y,w,58,9,0xE818202C);
        Render2D.roundRect(ctx,x+10,y+12,3,16,1.5f,building?0xFF7EF0C1:0xFF87B6FF);
        SmoothHudText.draw(ctx,"AUTO BUILDER",x+22,y+10,0xFFADBBD0,true,.75f);
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,etaText(),116,false,.65f),x+152,y+11,0xFF9BDDCB,false,.65f);
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,status,w-32,true,.95f),x+12,y+25,0xFFF1F5FF,true,.95f);
        int required=Math.max(0,solid-ignoredSolid);
        String details=schematic==null?"Choose a schematic in Player → Auto Builder":correct+" / "+required+" blocks  ·  "+containers.size()+" restock marks";
        SmoothHudText.draw(ctx,details,x+12,y+41,0xFFB6C5D9,false,.75f);
        Render2D.roundRect(ctx,x+12,y+53,w-24,2,1,0xFF334255);if(required>0)Render2D.roundRect(ctx,x+12,y+53,(w-24)*Math.min(correct,required)/required,2,1,0xFF7EF0C1);
        if(showLabels.get()&&restockTarget!=null)SmoothHudText.draw(ctx,"Restock "+restockTarget.toShortString(),x+12,y+64,0xFF9DCBFF,false,labelScale.getFloat());
    }
    public String etaText(){
        if(schematic==null||loading)return "ETA · load schematic";
        if(preparationStage>0)return "ETA · preparing";
        if(!building)return status.equals("Build complete")?"ETA · done":"ETA · paused";
        return buildEta.label(Math.max(0,solid-correct-ignoredSolid),cleanup.get()?supports.size():0);
    }
    public int selectedBuildSlot(){return buildSlot.is("Last Session")?0:Integer.parseInt(buildSlot.get());}
    public void cycleBuildSlot(int direction){buildSlot.cycle(direction);}
    public JsonObject buildSlotInfo(){var info=savedBuildInfo.get(selectedBuildSlot());return info==null?null:info.deepCopy();}
    public String placementName(){return placementName;}
    public CompletableFuture<Void> savePlacement(String name){
        if(schematic==null||origin==null){notify("Load and position a schematic first");return CompletableFuture.completedFuture(null);}
        activeBuildSlot=selectedBuildSlot();placementName=name==null?"":name.strip();
        int slot=activeBuildSlot;return writePlacement(slot).whenComplete((unused,error)->mc.execute(()->{if(error!=null)notify("Build save failed: "+rootMessage(error));else status="Placement saved in build slot "+slot;}));
    }
    private void rescanSavedPlacement(){boolean active=buildBudgetActive;double spentSoFar=buildBudgetSpent;replan();buildBudgetActive=active;buildBudgetSpent=spentSoFar;}
    private void checkpoint(){
        if(!saveBuilds.get()||schematic==null||origin==null||loading||restoringPlacement)return;
        if(activeBuildSlot<0){
            int slot=selectedBuildSlot();
            if(slot>0&&savedBuildInfo.containsKey(slot))slot=java.util.stream.IntStream.rangeClosed(1,10).filter(i->!savedBuildInfo.containsKey(i)).findFirst().orElse(0);
            activeBuildSlot=slot;buildSlot.set(slot==0?"Last Session":String.valueOf(slot));
        }
        writePlacement(activeBuildSlot).whenComplete((unused,error)->{if(error!=null)mc.execute(()->notify("Build checkpoint failed: "+rootMessage(error)));});
    }
    private CompletableFuture<Void> writePlacement(int slot){
        var data=saveExtra();data.addProperty("schematic-name",schematic.name);data.addProperty("schematic-format",schematic.format);data.addProperty("name",placementName.isBlank()?schematic.name:placementName);data.addProperty("saved-at",System.currentTimeMillis());
        data.addProperty("correct",correct);data.addProperty("total",solid);data.addProperty("rotation",rotation.get());data.addProperty("mirror",mirror.get());data.addProperty("use-offset",useOffset.get());
        data.addProperty("material-supply",supplyMode.get());data.addProperty("layer-mode",layerMode.get());data.addProperty("layer",layer.getInt());data.addProperty("build-budget-active",buildBudgetActive);data.addProperty("build-budget-spent",buildBudgetSpent);data.addProperty("auction-budget",maxSpend.get());
        var temporary=new JsonArray();for(var pos:supports)temporary.add(posJson(pos));data.add("temporary-supports",temporary);
        var escape=new JsonArray();for(var pos:escapeSupports)escape.add(posJson(pos));data.add("escape-supports",escape);
        savedBuildInfo.put(slot,data.deepCopy());var snapshot=schematic;
        return CompletableFuture.runAsync(()->{try{savedBuilds.save(slot,snapshot,data);}catch(IOException error){throw new CompletionException(error);}},IO);
    }
    public void loadPlacement(){
        int slot=selectedBuildSlot();if(activeBuildSlot>=0)checkpoint();pause("Loading saved placement");loading=true;int generation=++ioGeneration;
        CompletableFuture.supplyAsync(()->{try{return savedBuilds.load(slot);}catch(IOException error){throw new CompletionException(error);}},IO).whenComplete((saved,error)->mc.execute(()->{
            if(generation!=ioGeneration)return;loading=false;
            if(error!=null){status="Cannot load build slot "+slot;notify(rootMessage(error));return;}
            restoringPlacement=true;
            try{
                var data=saved.placement();install(saved.schematic());ignoredMaterials.clear();restorePlacementFields(data);
                rotation.set(data.get("rotation").getAsString());mirror.set(data.get("mirror").getAsString());useOffset.set(data.get("use-offset").getAsBoolean());
                layerMode.set(data.get("layer-mode").getAsString());layer.set(data.get("layer").getAsDouble());
                if(data.has("material-supply"))supplyMode.set(data.get("material-supply").getAsString());
                supports.clear();escapeSupports.clear();if(data.has("temporary-supports"))for(var pos:data.getAsJsonArray("temporary-supports"))supports.add(jsonPos(pos));
                if(data.has("escape-supports"))for(var pos:data.getAsJsonArray("escape-supports"))escapeSupports.add(jsonPos(pos));
                replan();if(data.has("auction-budget"))maxSpend.set(data.get("auction-budget").getAsDouble());buildBudgetActive=data.get("build-budget-active").getAsBoolean();buildBudgetSpent=data.get("build-budget-spent").getAsDouble();spent=buildBudgetSpent;
                activeBuildSlot=slot;buildSlot.set(slot==0?"Last Session":String.valueOf(slot));placementName=data.get("name").getAsString();
                world=inGame()&&worldScope.equals(scope())&&dimension.equals(mc.world.getRegistryKey().getValue().toString())?mc.world:null;
                preview=true;building=false;status=world==null?"Placement loaded — join its saved world to resume":"Placement loaded — press Start / Resume";
                savedBuildInfo.put(slot,data.deepCopy());
            }catch(RuntimeException failure){status="Invalid saved placement";notify(rootMessage(failure));}
            finally{restoringPlacement=false;}
        }));
    }
    private void restorePlacementFields(JsonObject data){
        if(data.has("ignored-materials"))for(var value:data.getAsJsonArray("ignored-materials")){var id=net.minecraft.util.Identifier.tryParse(value.getAsString());if(id!=null){var item=Registries.ITEM.get(id);if(item!=Items.AIR)ignoredMaterials.add(item);}}
        if(data.has("world-scope"))worldScope=data.get("world-scope").getAsString();if(data.has("dimension"))dimension=data.get("dimension").getAsString();if(data.has("origin"))origin=jsonPos(data.get("origin"));
        containers.clear();if(data.has("restock"))for(var pos:data.getAsJsonArray("restock"))containers.add(jsonPos(pos));
        if(containers.isEmpty()&&data.has("supply-chest"))containers.add(jsonPos(data.get("supply-chest")));
        selected=data.has("file")?data.get("file").getAsString():"";
    }
    @Override public JsonObject saveExtra(){
        var result=new JsonObject();result.addProperty("file",selected);result.addProperty("dimension",dimension);result.addProperty("world-scope",worldScope);result.addProperty("active-build-slot",activeBuildSlot);
        var ignored=new JsonArray();for(var item:ignoredMaterials)ignored.add(Registries.ITEM.getId(item).toString());result.add("ignored-materials",ignored);
        if(selectedSupplyChest()!=null)result.add("supply-chest",posJson(selectedSupplyChest()));if(origin!=null)result.add("origin",posJson(origin));var marks=new JsonArray();for(var pos:containers)marks.add(posJson(pos));result.add("restock",marks);return result;
    }
    private static JsonArray posJson(BlockPos pos){var a=new JsonArray();a.add(pos.getX());a.add(pos.getY());a.add(pos.getZ());return a;}
    private static BlockPos jsonPos(JsonElement value){var a=value.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("Position");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    @Override public void loadExtra(JsonObject data){
        try{ignoredMaterials.clear();if(data.has("ignored-materials"))for(var value:data.getAsJsonArray("ignored-materials")){var id=net.minecraft.util.Identifier.tryParse(value.getAsString());if(id!=null){var item=Registries.ITEM.get(id);if(item!=Items.AIR)ignoredMaterials.add(item);}}
            if(data.has("world-scope"))worldScope=data.get("world-scope").getAsString();if(data.has("dimension"))dimension=data.get("dimension").getAsString();if(data.has("origin"))origin=jsonPos(data.get("origin"));containers.clear();if(data.has("restock"))for(var pos:data.getAsJsonArray("restock"))containers.add(jsonPos(pos));
            if(containers.isEmpty()&&data.has("supply-chest"))containers.add(jsonPos(data.get("supply-chest")));
            if(data.has("active-build-slot")&&data.get("active-build-slot").getAsInt()>=0){buildSlot.set(data.get("active-build-slot").getAsInt()==0?"Last Session":data.get("active-build-slot").getAsString());loadPlacement();}
            else if(data.has("file")){selected=data.get("file").getAsString();if(!selected.isBlank()&&Files.isRegularFile(folder().resolve(selected)))load(folder().resolve(selected));}
        }catch(RuntimeException e){Maro.LOGGER.warn("Invalid Auto Builder saved placement",e);}
    }
    private String scope(){return mc.getCurrentServerEntry()!=null?"server:"+mc.getCurrentServerEntry().address:mc.getServer()!=null?"local:"+mc.getServer().getSaveProperties().getLevelName():"";}
}
