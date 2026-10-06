package dev.maro.builder;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Consumer;

/** Confirmed server homes. Travel never changes blocks or assumes a command succeeded. */
public final class BuilderHomes {
    private record Point(Vec3d position,String floor,String dimension) {
        BlockPos feet(){return BlockPos.ofFloored(position.add(0,.01,0));}
    }
    private enum Stage { IDLE, CHECK, SAVE, TRAVEL }
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private final BuilderWalk walker;
    private final Point[] points=new Point[3];
    private Stage stage=Stage.IDLE;
    private Point pending;
    private int slot,started,clock,settled,retryAt,menuStable,menuHash;
    private boolean receipt;
    private boolean checkingRoutes;
    private BlockPos routeTarget,routeFeet;
    private List<BlockPos> routeViews=List.of();
    private List<Integer> routeHomes=List.of();
    private int routeHomeCursor,routeViewCursor,routeRetryAt;
    private String failure="";
    public BuilderHomes(BuilderWalk walker){this.walker=walker;}
    public boolean ready(){return points[0]!=null;}
    public boolean hasSafeReturn(){return ready()&&safe(points[0]);}
    public boolean busy(){return stage!=Stage.IDLE;}
    public void cancel(){
        if(stage==Stage.CHECK&&mc.player!=null&&mc.currentScreen instanceof HandledScreen<?> menu
            &&menu.getTitle().getString().toLowerCase(Locale.ROOT).contains("home")&&menu.getScreenHandler().getCursorStack().isEmpty())mc.player.closeHandledScreen();
        stage=Stage.IDLE;pending=null;receipt=false;failure="";settled=0;invalidateRoutes();walker.stop();
    }
    public boolean checkingRoutes(){return checkingRoutes;}
    public void invalidateRoutes(){checkingRoutes=false;routeTarget=routeFeet=null;routeViews=List.of();routeHomes=List.of();routeRetryAt=0;}
    public void reset(){cancel();Arrays.fill(points,null);retryAt=0;}
    private Point current(){return new Point(mc.player.getEntityPos(),mc.world.getBlockState(mc.player.getBlockPos().down()).toString(),mc.world.getRegistryKey().getValue().toString());}
    public boolean safeHere(){
        if(mc.player==null||mc.world==null||!mc.player.isOnGround()||mc.player.getVelocity().horizontalLengthSquared()>.0004)return false;
        return safe(current());
    }
    private boolean safe(Point point){
        if(mc.world==null||mc.player==null||!point.dimension.equals(mc.world.getRegistryKey().getValue().toString()))return false;
        var feet=point.feet();
        if(!mc.world.isChunkLoaded(feet)||!mc.world.getBlockState(feet.down()).toString().equals(point.floor)||!walker.canStand(feet))return false;
        var state=mc.world.getBlockState(feet.down());
        if(state.isOf(net.minecraft.block.Blocks.DIRT)||!state.getFluidState().isEmpty())return false;
        var body=mc.player.getBoundingBox().offset(point.position.subtract(mc.player.getEntityPos())).contract(.000001);
        if(!mc.world.isSpaceEmpty(mc.player,body))return false;
        for(var pos:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))if(!mc.world.getFluidState(pos).isEmpty())return false;
        return true;
    }
    /** Only a read-only homes menu can authorize allocating the three empty slots. */
    public boolean setup(){
        if(busy()||!safeHere()||mc.player.currentScreenHandler!=mc.player.playerScreenHandler)return false;
        pending=current();slot=0;begin(Stage.CHECK);mc.getNetworkHandler().sendChatCommand("home");return true;
    }
    public boolean capture(boolean inside,boolean upper){
        if(!ready()||busy()||!inside||!safeHere()||mc.currentScreen!=null)return false;
        int next=points[1]==null&&!upper?1:points[1]!=null&&points[2]==null&&upper?2:-1;
        if(next<0)return false;slot=next;pending=current();begin(Stage.CHECK);mc.getNetworkHandler().sendChatCommand("home");return true;
    }
    private void begin(Stage next){walker.stop();stage=next;started=clock;receipt=false;failure="";settled=menuStable=menuHash=0;}
    private void save(){begin(Stage.SAVE);mc.getNetworkHandler().sendChatCommand("sethome");}
    public void message(String raw){
        if(!busy())return;String text=raw.toLowerCase(Locale.ROOT);
        if(!text.contains("home")&&!text.contains("teleport")&&!text.contains("command"))return;
        if(text.contains("cancel")||text.contains("cooldown")||text.contains("combat")||text.contains("permission")||text.contains("cannot")||text.contains("can't")||text.contains("not found")||text.contains("not set")||text.contains("does not exist")||text.contains("maximum")||text.matches(".*\\bfull\\b.*")||text.contains("unknown command")||text.contains("failed")){failure=raw;return;}
        if(stage==Stage.SAVE&&(text.contains("set")||text.contains("created")||text.contains("saved"))){
            var id=java.util.regex.Pattern.compile("home\\s*#?\\s*(\\d+)\\b").matcher(text);
            if(id.find()&&!id.group(1).equals(Integer.toString(slot+1))){failure="Server saved a different home slot; check homes before resuming";return;}receipt=true;
        }
    }
    public boolean tick(Consumer<String> status,Consumer<String> pause){
        clock++;if(!busy())return false;walker.release();
        if(!failure.isEmpty()){String reason=failure;cancel();pause.accept("Home command failed: "+reason);return true;}
        if(clock-started>300){cancel();pause.accept("Home did not confirm — check server feedback and resume");return true;}
        if(stage==Stage.CHECK){
            status.accept("Checking reserved builder home slots");
            if(!(mc.currentScreen instanceof HandledScreen<?> screen)||!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("home")||!(screen.getScreenHandler() instanceof GenericContainerScreenHandler menu)||clock-started<8)return true;
            int empty=0,saved=0;int[] slots={-1,-1,-1};int hash=1;
            for(var item:menu.slots){if(item.inventory==mc.player.getInventory())continue;var stack=item.getStack();if(stack.isEmpty())continue;
                String label=stack.getName().getString();var lore=stack.get(DataComponentTypes.LORE);if(lore!=null)for(var line:lore.lines())label+=" "+line.getString();label=label.toLowerCase(Locale.ROOT);
                if(label.contains("no home")||label.contains("empty home")||label.contains("home not set"))empty++;
                else if(stack.isIn(ItemTags.BEDS))saved++;
                else continue;
                boolean vacant=label.contains("no home")||label.contains("empty home")||label.contains("home not set");
                var number=java.util.regex.Pattern.compile("(?:home\\s*#?\\s*|#)([123])\\b").matcher(label);
                if(number.find())slots[Integer.parseInt(number.group(1))-1]=vacant?0:1;
                hash=31*hash+label.hashCode();
            }
            if(hash==menuHash)menuStable++;else{menuHash=hash;menuStable=0;}
            if(menuStable<3)return true;
            boolean numbered=Arrays.stream(slots).allMatch(value->value>=0);
            boolean valid=numbered?true:empty==3-slot&&saved==slot;
            if(numbered)for(int i=0;i<3;i++)if(slots[i]!=(i<slot?1:0))valid=false;
            if(!valid){if(clock-started<30)return true;cancel();pause.accept("Builder home slots changed or could not be verified; existing homes were kept");return true;}
            mc.player.closeHandledScreen();if(!safe(pending)||mc.player.getEntityPos().squaredDistanceTo(pending.position)>.04){cancel();pause.accept("Stand still on dry ground next to storage to set home 1");return true;}
            save();return true;
        }
        if(stage==Stage.SAVE){
            status.accept("Confirming home "+(slot+1));
            if(!safe(pending)||mc.player.getEntityPos().squaredDistanceTo(pending.position)>.04){cancel();pause.accept("Home setup moved — return to dry footing and resume");return true;}
            if(receipt&&clock-started>=4){points[slot]=pending;cancel();}return true;
        }
        status.accept("Waiting for /home "+(slot+1)+" arrival");
        if(mc.player.getEntityPos().squaredDistanceTo(pending.position)<=.6*.6&&safeHere()&&safe(pending)){
            if(++settled>=4){retryAt=clock+40;cancel();}
        }else settled=0;
        return true;
    }
    /** A selected work home must prove an onward native route before teleporting. */
    public boolean work(BlockPos target,List<BlockPos> views){
        if(!ready()||busy()||clock<retryAt||mc.currentScreen!=null){checkingRoutes=false;return false;}
        var feet=mc.player.getBlockPos();
        if(!target.equals(routeTarget)||!feet.equals(routeFeet)||!views.equals(routeViews)||!checkingRoutes&&clock>=routeRetryAt){
            routeTarget=target.toImmutable();routeFeet=feet.toImmutable();routeViews=List.copyOf(views);routeHomeCursor=routeViewCursor=0;
            var candidates=new ArrayList<Integer>();for(int i=1;i<points.length;i++)if(points[i]!=null&&safe(points[i])&&mc.player.getEntityPos().squaredDistanceTo(points[i].position)>=9)candidates.add(i);
            candidates.sort(Comparator.comparingDouble(i->target.getSquaredDistance(points[i].feet())));routeHomes=List.copyOf(candidates);checkingRoutes=true;
        }
        if(!checkingRoutes)return false;
        long deadline=System.nanoTime()+6_000_000;
        while(routeHomeCursor<routeHomes.size()){
            int index=routeHomes.get(routeHomeCursor);var point=points[index];
            if(!safe(point)||routeViewCursor>=routeViews.size()){routeHomeCursor++;routeViewCursor=0;continue;}
            var view=routeViews.get(routeViewCursor++);
            if(walker.canReachStandFrom(point.feet(),view)){
                // Avoid bouncing between homes when normal walking already serves this view.
                if(target.getSquaredDistance(point.feet())+16>=target.getSquaredDistance(feet)&&walker.canReachStand(view)){checkingRoutes=false;routeRetryAt=clock+40;return false;}
                if(!settledToTravel()){routeViewCursor--;walker.release();return false;}
                return travel(index);
            }
            if(System.nanoTime()>=deadline)return false;
        }
        checkingRoutes=false;routeRetryAt=clock+40;return false;
    }
    public boolean storage(BlockPos chest){
        if(!ready()||busy()||clock<retryAt||mc.currentScreen!=null||!safe(points[0])||chest.getSquaredDistance(points[0].feet())>25||mc.player.getEntityPos().squaredDistanceTo(points[0].position)<=64)return false;
        if(!settledToTravel()){walker.release();return true;}return travel(0);
    }
    private boolean settledToTravel(){return mc.player.isOnGround()&&mc.player.getVelocity().horizontalLengthSquared()<.0004&&!mc.player.isTouchingWater()&&!mc.player.isInLava()&&!mc.player.isUsingItem();}
    private boolean travel(int index){
        slot=index;pending=points[index];begin(Stage.TRAVEL);mc.getNetworkHandler().sendChatCommand("home "+(slot+1));return true;
    }
    public JsonArray saveData(){
        var data=new JsonArray();for(var point:points){if(point==null){data.add(JsonNull.INSTANCE);continue;}var entry=new JsonObject();entry.addProperty("x",point.position.x);entry.addProperty("y",point.position.y);entry.addProperty("z",point.position.z);entry.addProperty("floor",point.floor);entry.addProperty("dimension",point.dimension);data.add(entry);}return data;
    }
    public void loadData(JsonArray data){
        reset();if(data==null||data.size()!=3)return;
        for(int i=0;i<3;i++){if(data.get(i).isJsonNull())continue;var entry=data.get(i).getAsJsonObject();double x=entry.get("x").getAsDouble(),y=entry.get("y").getAsDouble(),z=entry.get("z").getAsDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096)throw new IllegalArgumentException("Home position");points[i]=new Point(new Vec3d(x,y,z),entry.get("floor").getAsString(),entry.get("dimension").getAsString());}
    }
}
