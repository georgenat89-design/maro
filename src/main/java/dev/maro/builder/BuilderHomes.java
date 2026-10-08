package dev.maro.builder;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Consumer;

/** Confirmed server homes. Travel never changes blocks or assumes a command succeeded. */
public final class BuilderHomes {
    private record Point(Vec3d position,String floor,String dimension,boolean work,BlockPos footing) {
        BlockPos feet(){return new BlockPos((int)Math.floor(position.x),(int)Math.ceil(position.y-.001),(int)Math.floor(position.z));}
    }
    private enum Stage { IDLE, APPROACH, STORAGE_TRAVEL, DELETE, SAVE, TRAVEL, RESTOCK_WAIT, RETURN_WAIT, RETURN_DELETE }
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private final BuilderWalk walker;
    private final Point[] points=new Point[2];
    private Stage stage=Stage.IDLE;
    private Point pending;
    private int slot,started,clock,settled,retryAt;
    private boolean receipt,savingReturn,returnTrip,travellingBack,storageArrived;
    private BlockPos storageChest,storageStand;
    private List<BlockPos> storageViews=List.of();
    private int storageCursor,storageProgressAt;
    private Vec3d storageProgress;
    private boolean checkingRoutes;
    private BlockPos routeTarget,routeFeet;
    private List<BlockPos> routeViews=List.of();
    private List<Integer> routeHomes=List.of();
    private int routeHomeCursor,routeViewCursor,routeRetryAt;
    private String failure="";
    public BuilderHomes(BuilderWalk walker){this.walker=walker;}
    public boolean ready(){return points[0]!=null;}
    public boolean readyFor(BlockPos chest){return chest!=null&&ready()&&chest.getSquaredDistance(points[0].feet())<=25&&safe(points[0]);}
    public boolean hasSafeReturn(){return ready()&&safe(points[0]);}
    /** Old escape columns are no longer an exit once native travel reaches permanent storage ground. */
    public boolean onStorageGround(){return !busy()&&!returnTrip&&hasSafeReturn()&&safeHere()
        &&mc.player.getEntityPos().squaredDistanceTo(points[0].position)<=.36;}
    public boolean busy(){return stage!=Stage.IDLE;}
    public void cancel(){
        stage=Stage.IDLE;pending=null;storageChest=storageStand=null;storageViews=List.of();storageProgress=null;receipt=savingReturn=travellingBack=false;failure="";settled=0;invalidateRoutes();walker.stop();
    }
    public boolean checkingRoutes(){return checkingRoutes;}
    public void invalidateRoutes(){checkingRoutes=false;routeTarget=routeFeet=null;routeViews=List.of();routeHomes=List.of();routeRetryAt=0;}
    public void reset(){cancel();Arrays.fill(points,null);returnTrip=storageArrived=false;retryAt=0;}
    private Point current(){return current(false);}
    private Box standingBody(Vec3d position){var dimensions=mc.player.getDimensions(net.minecraft.entity.EntityPose.STANDING);double half=dimensions.width()/2;return new Box(position.x-half,position.y,position.z-half,position.x+half,position.y+dimensions.height(),position.z+half).contract(.000001);}
    private Box footingContact(Vec3d position){var body=standingBody(position);return new Box(body.minX,position.y-.05,body.minZ,body.maxX,position.y+.001,body.maxZ);}
    private boolean supportsPosition(BlockPos floor,Vec3d position){var contact=footingContact(position);return mc.world.getBlockState(floor).getCollisionShape(mc.world,floor).getBoundingBoxes().stream().anyMatch(box->box.offset(floor).intersects(contact));}
    private Point pointAt(Vec3d position,boolean work){
        var contact=footingContact(position);BlockPos footing=null;double best=Double.MAX_VALUE;
        for(var cell:BlockPos.iterate(BlockPos.ofFloored(contact.minX,contact.minY,contact.minZ),BlockPos.ofFloored(contact.maxX,contact.maxY,contact.maxZ))){
            if(!mc.world.isChunkLoaded(cell)||!mc.world.getFluidState(cell).isEmpty()||!supportsPosition(cell,position))continue;
            double distance=Vec3d.ofCenter(cell).squaredDistanceTo(position);if(distance<best){best=distance;footing=cell.toImmutable();}
        }
        if(footing==null)footing=BlockPos.ofFloored(position.x,Math.ceil(position.y-.001)-1,position.z);
        return new Point(position,mc.world.getBlockState(footing).toString(),mc.world.getRegistryKey().getValue().toString(),work,footing);
    }
    private Point current(boolean work){return pointAt(mc.player.getEntityPos(),work);}
    public boolean safeHere(){
        if(mc.player==null||mc.world==null||!mc.player.isOnGround()||mc.player.getVelocity().horizontalLengthSquared()>.0004)return false;
        return safe(current());
    }
    private boolean safe(Point point){
        if(mc.world==null||mc.player==null||!point.dimension.equals(mc.world.getRegistryKey().getValue().toString()))return false;
        var feet=point.feet();
        if(!mc.world.isChunkLoaded(feet)||!mc.world.isChunkLoaded(point.footing)||!mc.world.getBlockState(point.footing).toString().equals(point.floor)||!supportsPosition(point.footing,point.position))return false;
        var state=mc.world.getBlockState(point.footing);
        if(state.isOf(net.minecraft.block.Blocks.DIRT)&&!point.work||!state.getFluidState().isEmpty())return false;
        var body=standingBody(point.position);
        if(!mc.world.isSpaceEmpty(mc.player,body))return false;
        for(var pos:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))if(!mc.world.getFluidState(pos).isEmpty())return false;
        return true;
    }
    /** Replace only the explicitly reserved storage slot, after reaching the marked chest. */
    public boolean setup(BlockPos chest){
        if(busy()||chest==null||mc.player==null||mc.world==null||mc.currentScreen!=null||mc.player.currentScreenHandler!=mc.player.playerScreenHandler)return false;
        storageChest=chest.toImmutable();storageStand=null;storageViews=List.of();storageCursor=0;
        storageProgress=mc.player.getEntityPos();storageProgressAt=clock;slot=0;begin(Stage.APPROACH);return true;
    }
    /** Save the current work area once before a storage journey; slot 2 is transient. */
    public boolean restock(BlockPos chest){
        if(!readyFor(chest)||busy()||mc.currentScreen!=null||returnTrip&&storageArrived||chest.getSquaredDistance(mc.player.getBlockPos())<=9)return false;
        if(!returnTrip)storageArrived=false;
        slot=1;savingReturn=!returnTrip;begin(Stage.RESTOCK_WAIT);return true;
    }
    public boolean returnToWork(){
        if(!returnTrip||points[1]==null||busy())return false;
        slot=1;pending=points[1];begin(Stage.RETURN_WAIT);return true;
    }
    public boolean protectsFooting(BlockPos floor){return returnTrip&&points[1]!=null&&points[1].footing.equals(floor);}
    /** Keep the confirmed storage arrival body clear of the builder's own scaffolding. */
    public boolean reservesStorageSpace(BlockPos pos){
        if(points[0]==null||mc.player==null)return false;
        var point=points[0].position;var dimensions=mc.player.getDimensions(net.minecraft.entity.EntityPose.STANDING);double half=dimensions.width()/2;
        return new Box(point.x-half,point.y,point.z-half,point.x+half,point.y+dimensions.height(),point.z+half).intersects(new Box(pos));
    }
    private void clearReturn(){
        slot=1;pending=points[1];points[1]=null;returnTrip=storageArrived=false;begin(Stage.RETURN_DELETE);mc.getNetworkHandler().sendChatCommand("delhome 2");
    }
    private void begin(Stage next){walker.stop();stage=next;started=clock;receipt=false;failure="";settled=0;}
    private void save(){begin(Stage.SAVE);mc.getNetworkHandler().sendChatCommand("sethome");}
    private boolean besideStorage(Point point){
        if(storageChest==null||!(mc.world.getBlockState(storageChest).getBlock() instanceof net.minecraft.block.ChestBlock)||storageChest.getSquaredDistance(point.feet())>9||!safe(point))return false;
        var hit=mc.world.raycast(new net.minecraft.world.RaycastContext(point.position.add(0,mc.player.getStandingEyeHeight(),0),Vec3d.ofCenter(storageChest),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player));
        return hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK&&hit.getBlockPos().equals(storageChest);
    }
    private void approachStorage(Consumer<String> status,Consumer<String> pause){
        status.accept("Walking directly to marked storage before replacing home 1");
        if(mc.currentScreen!=null||mc.player.currentScreenHandler!=mc.player.playerScreenHandler){cancel();pause.accept("Close the container and resume storage home setup");return;}
        if(storageProgress.squaredDistanceTo(mc.player.getEntityPos())>.04){storageProgress=mc.player.getEntityPos();storageProgressAt=clock;}
        if(clock-storageProgressAt>180){cancel();pause.accept("No safe route to marked storage — home 1 was kept");return;}
        if(readyFor(storageChest)&&mc.player.getEntityPos().squaredDistanceTo(points[0].position)>64){
            if(!settledToTravel())return;
            pending=points[0];begin(Stage.STORAGE_TRAVEL);mc.getNetworkHandler().sendChatCommand("home 1");return;
        }
        if(safeHere()&&besideStorage(current())){
            if(++settled<4)return;
            pending=current();points[0]=null;begin(Stage.DELETE);mc.getNetworkHandler().sendChatCommand("delhome 1");return;
        }
        settled=0;
        // Long journeys use the walker's checked segments before selecting a final dry view.
        if(storageChest.getSquaredDistance(mc.player.getBlockPos())>48*48){walker.approach(storageChest,3);return;}
        if(storageStand!=null){
            if(walker.standAt(storageStand)){walker.release();return;}
            if(!walker.routeUnavailable())return;
            storageStand=null;walker.stop();
        }
        if(storageViews.isEmpty()){
            var views=new ArrayList<BlockPos>();
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-2;dy<=2;dy++){
                var feet=storageChest.add(dx,dy,dz);if(!walker.canStand(feet)||mc.world.getBlockState(feet.down()).hasBlockEntity())continue;
                var point=pointAt(walker.standingPoint(feet),false);
                if(besideStorage(point))views.add(feet);
            }
            views.sort(Comparator.comparingDouble(feet->feet.getSquaredDistance(mc.player.getBlockPos())));storageViews=List.copyOf(views);
        }
        long deadline=System.nanoTime()+6_000_000;
        while(storageCursor<storageViews.size()){
            var feet=storageViews.get(storageCursor++);
            if(walker.canReachStand(feet)){storageStand=feet;walker.stop();return;}
            if(System.nanoTime()>=deadline)return;
        }
        cancel();pause.accept("No safe route to marked storage — home 1 was kept");
    }
    public void message(String raw){
        if(!busy())return;String text=raw.toLowerCase(Locale.ROOT);
        if(!text.contains("home")&&!text.contains("teleport")&&!text.contains("command"))return;
        if(text.contains("cancel")||text.contains("cooldown")||text.contains("combat")||text.contains("permission")||text.contains("cannot")||text.contains("can't")||text.contains("could not")||text.contains("unable")||text.contains("not allowed")||text.contains("not deleted")||text.contains("not removed")||text.contains("unknown command")||text.contains("failed")){failure=raw;return;}
        boolean missing=text.contains("not found")||text.contains("not set")||text.contains("does not exist")||text.contains("no home")||text.contains("don't have")||text.contains("do not have");
        if((stage==Stage.DELETE||stage==Stage.RETURN_DELETE)&&(missing||text.contains("deleted")||text.contains("removed"))){
            var id=java.util.regex.Pattern.compile("home\\s*#?\\s*(\\d+)\\b").matcher(text);
            if(id.find()&&!id.group(1).equals(Integer.toString(slot+1))){failure="Server confirmed a different home slot; resume to retry home "+(slot+1);return;}
            receipt=true;return;
        }
        if(missing||text.contains("maximum")||text.matches(".*\\bfull\\b.*")){failure=raw;return;}
        if(stage==Stage.SAVE&&(text.contains("set")||text.contains("created")||text.contains("saved"))){
            var id=java.util.regex.Pattern.compile("home\\s*#?\\s*(\\d+)\\b").matcher(text);
            if(id.find()&&!id.group(1).equals(Integer.toString(slot+1))){if(id.group(1).equals("1")&&slot==1)points[0]=null;failure="Server saved a different home slot; check homes before resuming";return;}receipt=true;
        }
    }
    public boolean tick(Consumer<String> status,Consumer<String> pause){
        clock++;if(!busy())return false;walker.release();
        if(!failure.isEmpty()){String reason=failure;cancel();pause.accept("Home command failed: "+reason);return true;}
        if(clock-started>(stage==Stage.APPROACH?1200:300)){
            cancel();pause.accept("Home did not confirm — check server feedback and resume");return true;
        }
        if(stage==Stage.APPROACH){approachStorage(status,pause);return true;}
        if(stage==Stage.RESTOCK_WAIT){
            status.accept("Saving home 2 at the current work area before restocking");
            if(!settledToTravel()||mc.player.currentScreenHandler!=mc.player.playerScreenHandler)return true;
            if(!savingReturn){if(points[1]==null||!safe(points[1])){cancel();pause.accept("Restock return footing changed — resume from dry ground");return true;}travel(0);return true;}
            var point=current(true);if(!safe(point))return true;
            pending=point;points[1]=null;begin(Stage.DELETE);mc.getNetworkHandler().sendChatCommand("delhome 2");return true;
        }
        if(stage==Stage.RETURN_WAIT){
            status.accept("Returning to the saved work area through /home 2");
            if(!safe(pending)){cancel();pause.accept("Saved work footing changed — home 2 was kept");return true;}
            if(!settledToTravel()||mc.player.currentScreenHandler!=mc.player.playerScreenHandler)return true;
            if(mc.player.getEntityPos().squaredDistanceTo(pending.position)<=.36){clearReturn();return true;}
            travellingBack=true;travel(1);return true;
        }
        if(stage==Stage.DELETE){
            status.accept("Confirming /delhome "+(slot+1)+" before saving "+(slot==0?"at storage":"the restock return point"));
            if(!safe(pending)||slot==0&&!besideStorage(pending)||mc.player.getEntityPos().squaredDistanceTo(pending.position)>.04){cancel();pause.accept("Home setup moved — resume from dry footing");return true;}
            if(receipt&&clock-started>=4)save();return true;
        }
        if(stage==Stage.SAVE){
            status.accept("Confirming home "+(slot+1));
            if(!safe(pending)||mc.player.getEntityPos().squaredDistanceTo(pending.position)>.04){cancel();pause.accept("Home setup moved — return to dry footing and resume");return true;}
            if(receipt&&clock-started>=4){
                points[slot]=pending;
                if(slot==1&&savingReturn){returnTrip=true;savingReturn=false;travel(0);}
                else cancel();
            }return true;
        }
        if(stage==Stage.RETURN_DELETE){
            status.accept("Deleting temporary home 2 after returning to work");
            if(!safe(pending)||!settledToTravel()||mc.player.getEntityPos().squaredDistanceTo(pending.position)>.36){cancel();pause.accept("Work return moved before home 2 deletion confirmed");return true;}
            if(receipt&&clock-started>=4)cancel();return true;
        }
        status.accept("Waiting for /home "+(slot+1)+" arrival");
        if(mc.player.getEntityPos().squaredDistanceTo(pending.position)<=.6*.6&&settledToTravel()&&safe(pending)){
            if(++settled>=4){
                // Local chest routing may leave the three-cell radius while
                // walking around an obstruction. It must not restart home 1.
                if(returnTrip&&slot==0)storageArrived=true;
                if(stage==Stage.STORAGE_TRAVEL){storageProgress=mc.player.getEntityPos();storageProgressAt=clock;begin(Stage.APPROACH);}
                else if(travellingBack&&slot==1)clearReturn();
                else{retryAt=clock+40;cancel();}
            }
        }else settled=0;
        return true;
    }
    /** A selected work home must prove an onward native route before teleporting. */
    public boolean work(BlockPos target,List<BlockPos> views){
        if(!ready()||busy()||clock<retryAt||mc.currentScreen!=null){checkingRoutes=false;return false;}
        var feet=mc.player.getBlockPos();
        if(!target.equals(routeTarget)||!feet.equals(routeFeet)||!views.equals(routeViews)||!checkingRoutes&&clock>=routeRetryAt){
            routeTarget=target.toImmutable();routeFeet=feet.toImmutable();routeViews=List.copyOf(views);routeHomeCursor=routeViewCursor=0;
            // Home 2 belongs only to the active restock trip. Home 3 is never used.
            var candidates=new ArrayList<Integer>();if(safe(points[0])&&mc.player.getEntityPos().squaredDistanceTo(points[0].position)>=9)candidates.add(0);
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
        var data=new JsonArray();for(int i=0;i<points.length;i++){var point=points[i];if(point==null){data.add(JsonNull.INSTANCE);continue;}var entry=new JsonObject();entry.addProperty("x",point.position.x);entry.addProperty("y",point.position.y);entry.addProperty("z",point.position.z);entry.addProperty("floor",point.floor);entry.addProperty("dimension",point.dimension);var support=new JsonArray();support.add(point.footing.getX());support.add(point.footing.getY());support.add(point.footing.getZ());entry.add("footing",support);if(i==1&&returnTrip){entry.addProperty("restock-return",true);if(storageArrived)entry.addProperty("restock-storage",true);}data.add(entry);}return data;
    }
    public void loadData(JsonArray data){
        reset();if(data==null||data.size()<2||data.size()>3)return;
        for(int i=0;i<2;i++){if(data.get(i).isJsonNull())continue;var entry=data.get(i).getAsJsonObject();if(i==1&&(!entry.has("restock-return")||!entry.get("restock-return").getAsBoolean()))continue;double x=entry.get("x").getAsDouble(),y=entry.get("y").getAsDouble(),z=entry.get("z").getAsDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096)throw new IllegalArgumentException("Home position");
            var footing=BlockPos.ofFloored(x,Math.ceil(y-.001)-1,z);if(entry.has("footing")){var support=entry.getAsJsonArray("footing");if(support.size()!=3)throw new IllegalArgumentException("Home footing");footing=new BlockPos(support.get(0).getAsInt(),support.get(1).getAsInt(),support.get(2).getAsInt());if(Math.abs(footing.getX()-Math.floor(x))>1||Math.abs(footing.getZ()-Math.floor(z))>1||Math.abs(footing.getY()-(Math.ceil(y-.001)-1))>1)throw new IllegalArgumentException("Home footing");}
            points[i]=new Point(new Vec3d(x,y,z),entry.get("floor").getAsString(),entry.get("dimension").getAsString(),i==1,footing);if(i==1){returnTrip=true;storageArrived=entry.has("restock-storage")&&entry.get("restock-storage").getAsBoolean();}}
    }
}
