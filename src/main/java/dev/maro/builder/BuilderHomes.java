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
    private String failure="";
    public BuilderHomes(BuilderWalk walker){this.walker=walker;}
    public boolean ready(){return points[0]!=null;}
    public boolean busy(){return stage!=Stage.IDLE;}
    public void cancel(){
        if(stage==Stage.CHECK&&mc.player!=null&&mc.currentScreen instanceof HandledScreen<?> menu
            &&menu.getTitle().getString().toLowerCase(Locale.ROOT).contains("home")&&menu.getScreenHandler().getCursorStack().isEmpty())mc.player.closeHandledScreen();
        stage=Stage.IDLE;pending=null;receipt=false;failure="";settled=0;walker.stop();
    }
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
        if(text.contains("cancel")||text.contains("cooldown")||text.contains("combat")||text.contains("permission")||text.contains("cannot")||text.contains("can't")||text.contains("not found")||text.contains("not set")||text.contains("does not exist")||text.contains("maximum")||text.contains("full")||text.contains("unknown command")||text.contains("failed")){failure=raw;return;}
        if(stage==Stage.SAVE&&(text.contains("set")||text.contains("created")||text.contains("saved")))receipt=true;
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
        if(!ready()||busy()||clock<retryAt||mc.currentScreen!=null)return false;
        int best=-1;double score=Double.MAX_VALUE;
        for(int i=1;i<points.length;i++){var point=points[i];if(point==null||!safe(point)||mc.player.getEntityPos().squaredDistanceTo(point.position)<9)continue;
            boolean onward=false;for(var view:views)if(walker.canReachStandFrom(point.feet(),view)){onward=true;break;}
            double distance=target.getSquaredDistance(point.feet());if(onward&&distance<score){score=distance;best=i;}
        }
        if(best>=0&&score+16>=target.getSquaredDistance(mc.player.getBlockPos())){
            for(var view:views)if(walker.canReachStand(view))return false;
        }
        return best>=0&&travel(best);
    }
    public boolean storage(BlockPos chest){
        return ready()&&!busy()&&clock>=retryAt&&mc.currentScreen==null&&safe(points[0])&&chest.getSquaredDistance(points[0].feet())<=25&&mc.player.getEntityPos().squaredDistanceTo(points[0].position)>64&&travel(0);
    }
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
