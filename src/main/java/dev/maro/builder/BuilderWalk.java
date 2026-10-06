package dev.maro.builder;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
import java.util.*;

/** Bounded native movement pathfinding. Uses normal keys and never teleports or mines a route. */
public final class BuilderWalk {
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private record Node(BlockPos pos,double cost,double score,Node parent){}
    private List<BlockPos> path=List.of();
    private BlockPos goal;
    private int cursor,retry,stuck;
    private Vec3d last;
    private boolean forward,jump;
    private boolean exact;
    private int failedRoutes;
    private boolean recoveryRequested;
    private boolean movementStalled;
    private boolean smooth=true;
    private Set<BlockPos> clearedForSearch=Set.of();
    private BlockPos pillarForSearch;
    private Set<BlockPos> stairsForSearch=Set.of();
    private Set<BlockPos> waterExitCells=Set.of();
    private Set<BlockPos> waterDepartureCells=Set.of();
    private final Set<BlockPos> waterExitRejected=new HashSet<>();
    private BlockPos waterExitGoal,waterExitHint;
    private int waterExitSearchTicks;
    private double waterExitReach;
    private boolean strictDrySearch;
    private final BuilderRotation rotation=new BuilderRotation();
    public void turning(boolean smooth,float speed){this.smooth=smooth;rotation.configure(smooth,speed);}
    public void beginLookTick(int tick){rotation.begin(tick);}
    public boolean lookAt(float yaw,float pitch){return rotation.turn(yaw,pitch);}
    public boolean finishLook(float yaw,float pitch){return rotation.finish(yaw,pitch);}
    public void resetLook(){rotation.reset();}
    public String status="";
    public void stop(){
        release();path=List.of();goal=waterExitGoal=null;cursor=retry=stuck=failedRoutes=0;last=null;recoveryRequested=movementStalled=false;
        waterDepartureCells=Set.of();waterExitRejected.clear();waterExitSearchTicks=0;waterExitHint=null;
    }
    public void release(){
        if(forward)mc.options.forwardKey.setPressed(false);
        if(jump)mc.options.jumpKey.setPressed(false);
        forward=jump=false;
    }
    public boolean moving(){return forward||jump;}
    /** Native sneak edging keeps part of the body over the original solid footing. */
    public boolean peekToward(BlockPos target,BlockPos floor){
        if(!mc.player.isOnGround()||!mc.player.isSneaking()||mc.player.isTouchingWater()||mc.player.isInLava())return false;
        if(floor==null||!hasPeekFooting(floor))return false;var state=mc.world.getBlockState(floor);
        if(!state.getFluidState().isEmpty()||!net.minecraft.block.Block.isShapeFullCube(state.getCollisionShape(mc.world,floor)))return false;
        var offset=Vec3d.ofCenter(target).subtract(Vec3d.ofCenter(floor));double length=Math.hypot(offset.x,offset.z);if(length<.001)return false;
        var goal=new Vec3d(floor.getX()+.5+offset.x/length*.58,mc.player.getY(),floor.getZ()+.5+offset.z/length*.58);
        if(!mc.world.isSpaceEmpty(mc.player,mc.player.getBoundingBox().offset(goal.subtract(mc.player.getEntityPos()))))return false;
        var delta=goal.subtract(mc.player.getEntityPos());if(delta.horizontalLengthSquared()<.0016){release();return true;}
        float heading=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90),error=MathHelper.wrapDegrees(heading-mc.player.getYaw());
        lookAt(heading,mc.player.getPitch());
        if(Math.abs(error)<12){forward=true;mc.options.forwardKey.setPressed(true);}else release();return true;
    }
    public boolean hasPeekFooting(BlockPos floor){
        if(floor==null||mc.player==null||mc.world==null||!mc.player.isOnGround())return false;
        var state=mc.world.getBlockState(floor);if(!state.getFluidState().isEmpty()||!net.minecraft.block.Block.isShapeFullCube(state.getCollisionShape(mc.world,floor)))return false;
        var body=mc.player.getBoundingBox();return Math.abs(body.minY-floor.getY()-1)<.05
            &&Math.min(body.maxX,floor.getX()+1)-Math.max(body.minX,floor.getX())>.1
            &&Math.min(body.maxZ,floor.getZ()+1)-Math.max(body.minZ,floor.getZ())>.1
            &&new Box(body.minX,body.minY-.02,body.minZ,body.maxX,body.minY,body.maxZ).intersects(new Box(floor));
    }
    public BlockPos peekFooting(BlockPos excluded){
        var feet=mc.player.getBlockPos();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var floor=new BlockPos(feet.getX()+dx,(int)Math.floor(mc.player.getY()-.01),feet.getZ()+dz);
            if(!floor.equals(excluded)&&hasPeekFooting(floor))return floor;
        }return null;
    }
    public boolean atPeekEdge(BlockPos target,BlockPos floor){
        if(floor==null)return false;var direction=Vec3d.ofCenter(target).subtract(Vec3d.ofCenter(floor));double length=Math.hypot(direction.x,direction.z);if(length<.001)return true;
        var position=mc.player.getEntityPos().subtract(Vec3d.ofCenter(floor));return (position.x*direction.x+position.z*direction.z)/length>=.5;
    }
    public boolean approach(BlockPos target,double distance){
        return approach(target,distance,false);
    }
    public boolean standAt(BlockPos target){return approach(target,.22,true);}
    public boolean centerForJump(BlockPos target){return approach(target,.12,true);}
    public boolean canStand(BlockPos pos){return walkable(pos);}
    public boolean hasStandingClearance(BlockPos pos){return clear(pos)&&clear(pos.up());}
    public Vec3d standingPoint(BlockPos pos){double floor=footingHeight(pos.down());return Vec3d.ofBottomCenter(pos).add(0,floor<.625&&swimmingCell(pos)?0:floor-1,0);}
    /** Depart only the connected water already occupied; every route ends on dry native footing. */
    public boolean leaveWater(BlockPos work,double reach){
        if(mc.player==null||mc.world==null){stop();return false;}
        if(!mc.player.isTouchingWater()&&waterDepartureCells.isEmpty()){stop();return true;}
        var cells=waterDepartureCells.isEmpty()?occupiedWater():waterDepartureCells;
        var previous=waterExitCells;waterExitCells=cells;waterExitHint=work;waterExitReach=reach;
        try{
            if(waterExitCells.isEmpty()){release();status="No checked water departure";return false;}
            if(waterExitGoal==null||!dryStand(waterExitGoal)||path.isEmpty()){
                waterExitSearchTicks++;
                var route=find(walkingCell(),walkingCell(),.12,true,false,true);
                if(route.isEmpty()){waterDepartureCells=cells;release();status="Checking a dry route out of water";return false;}
                stop();waterDepartureCells=cells;waterExitGoal=goal=route.getLast();exact=true;path=route;cursor=0;retry=20;
            }
            boolean arrived=approach(waterExitGoal,.12,true)&&!mc.player.isTouchingWater();
            if(arrived){stop();return true;}
            status="Leaving water before building";return false;
        }finally{waterExitCells=previous;}
    }
    private boolean dryDepartureGoal(BlockPos pos){
        if(!dryStand(pos))return false;
        // Give breathing priority if no ordinary work route is available.
        // Usually prove the onward dry route too, instead of stopping on a wet island.
        if(waterExitHint==null||waterExitSearchTicks>=80)return true;
        if(waterExitRejected.contains(pos))return false;
        var previous=waterExitCells;boolean previousStrict=strictDrySearch;waterExitCells=Set.of();strictDrySearch=true;
        try{
            var eye=standingPoint(pos).add(0,mc.player.getStandingEyeHeight(),0);
            if(eye.squaredDistanceTo(Vec3d.ofCenter(waterExitHint))<=waterExitReach*waterExitReach||!find(pos,waterExitHint,waterExitReach,false).isEmpty())return true;
            waterExitRejected.add(pos);return false;
        }finally{waterExitCells=previous;strictDrySearch=previousStrict;}
    }
    private Set<BlockPos> occupiedWater(){
        var cells=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();var body=mc.player.getBoundingBox();var start=mc.player.getBlockPos();
        for(var p:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))
            if(mc.world.isChunkLoaded(p)&&mc.world.getFluidState(p).isIn(net.minecraft.registry.tag.FluidTags.WATER)){var seed=p.toImmutable();if(cells.add(seed))queue.add(seed);}
        while(!queue.isEmpty()&&cells.size()<512){
            var p=queue.removeFirst();
            for(var side:Direction.values()){
                if(cells.size()>=512)break;
                var next=p.offset(side);
                if(Math.abs(next.getX()-start.getX())>12||Math.abs(next.getZ()-start.getZ())>12||Math.abs(next.getY()-start.getY())>4
                    ||!mc.world.isChunkLoaded(next)||!mc.world.getFluidState(next).isIn(net.minecraft.registry.tag.FluidTags.WATER))continue;
                if(cells.add(next))queue.add(next);
            }
        }
        return cells;
    }
    private boolean swimmingCell(BlockPos pos){return waterExitCells.contains(pos)||waterExitCells.contains(pos.up());}
    private boolean dryStand(BlockPos pos){
        if(!walkable(pos)||!mc.world.getFluidState(pos.down()).isEmpty())return false;
        var point=standingPoint(pos);var dimensions=mc.player.getDimensions(net.minecraft.entity.EntityPose.STANDING);double half=dimensions.width()/2;
        var body=new Box(point.x-half,point.y,point.z-half,point.x+half,point.y+dimensions.height(),point.z+half).contract(.000001);
        for(var p:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))if(!mc.world.getFluidState(p).isEmpty())return false;
        return true;
    }
    public boolean canReachStand(BlockPos pos){return walkable(pos)&&(walkingCell().equals(pos)||mc.player.getEntityPos().squaredDistanceTo(standingPoint(pos))<=.22*.22||!find(walkingCell(),pos,.22,true).isEmpty());}
    public boolean canReachStandFrom(BlockPos from,BlockPos to){return walkable(from)&&walkable(to)&&(from.equals(to)||!find(from,to,.22,true).isEmpty());}
    /** Collision-only feasibility query; never changes client or server blocks. */
    public boolean canReachAfterClearing(BlockPos from,BlockPos to,Set<BlockPos> removed){
        var previous=clearedForSearch;clearedForSearch=removed;
        try{return canReachStandFrom(from,to);}finally{clearedForSearch=previous;}
    }
    /** Prove a route from a future native pillar without modifying the world. */
    public boolean canReachFromPillar(BlockPos top,BlockPos to){
        var previous=pillarForSearch;pillarForSearch=top.down();
        try{return canReachStandFrom(top,to);}finally{pillarForSearch=previous;}
    }
    /** Check a complete proposed stair using collision masks, without changing blocks. */
    public boolean canReachWithStairs(BlockPos to,Set<BlockPos> stairs){
        return !stairRoute(to,stairs).isEmpty();
    }
    public List<BlockPos> stairRoute(BlockPos to,Set<BlockPos> stairs){
        var previous=stairsForSearch;stairsForSearch=stairs;
        try{return walkable(to)?find(walkingCell(),to,.22,true):List.of();}
        finally{stairsForSearch=previous;}
    }
    /** Combined column/door feasibility, still without changing real blocks. */
    public boolean canReachFromPillarAfterClearing(BlockPos top,BlockPos to,Set<BlockPos> removed){
        var previous=clearedForSearch;clearedForSearch=removed;
        try{return canReachFromPillar(top,to);}finally{clearedForSearch=previous;}
    }
    /** Prove every native jump of an opened ceiling column and its onward route. */
    public boolean canClimbAfterClearing(BlockPos base,BlockPos top,BlockPos to,Set<BlockPos> removed){
        int rise=top.getY()-base.getY();
        if(rise<1||rise>6||base.getX()!=top.getX()||base.getZ()!=top.getZ())return false;
        var previous=clearedForSearch;var previousStairs=stairsForSearch;
        clearedForSearch=removed;var posts=new HashSet<BlockPos>();stairsForSearch=posts;
        try{
            if(!canPillar(base))return false;
            for(int step=0;step<rise;step++){
                var feet=base.up(step);
                if(!clear(feet)||!clear(feet.up())||!clear(feet.up(2))||!clear(feet.up(3)))return false;
                var point=Vec3d.ofBottomCenter(feet);
                if(!bodyClear(point)||!bodyCorridor(point,point.add(0,1.25,0)))return false;
                posts.add(feet);
            }
            return canReachStandFrom(top,to);
        }finally{clearedForSearch=previous;stairsForSearch=previousStairs;}
    }
    public BlockPos descentLanding(BlockPos removed){
        if(!canDescendThrough(removed))return null;
        for(int drop=1;drop<=3;drop++)if(footingHeight(removed.down(drop))>=.625)return removed.down(drop).up();
        return null;
    }
    public BlockPos descentLandingAfterClearing(BlockPos removed,Set<BlockPos> cleared){
        var previous=clearedForSearch;clearedForSearch=cleared;
        try{return descentLanding(removed);}finally{clearedForSearch=previous;}
    }
    private BlockPos walkingCell(){return BlockPos.ofFloored(mc.player.getEntityPos().add(0,.4,0));}
    public boolean needsRecovery(){return recoveryRequested;}
    public BlockPos destination(){return goal;}
    public boolean canDescendThrough(BlockPos pos){
        if(!clear(pos.up())||!clear(pos.up(2)))return false;
        for(int drop=1;drop<=3;drop++){
            var floor=pos.down(drop);
            if(!safe(floor))return false;
            if(footingHeight(floor)>=.625)return true;
            if(!clear(floor))return false;
        }
        return false;
    }
    public boolean movementStalled(){return movementStalled;}
    public boolean routeUnavailable(){return goal!=null&&path.isEmpty()&&failedRoutes>0;}
    public BlockPos blockingSupport(Set<BlockPos> supports){
        if(goal==null||mc.player==null)return null;
        var feet=mc.player.getBlockPos();var direction=Vec3d.ofBottomCenter(goal).subtract(mc.player.getEntityPos());
        var flat=new Vec3d(direction.x,0,direction.z).normalize();
        if(flat.lengthSquared()<.1)return null;
        // Only a block intersecting the first metre of the intended walking corridor.
        // Leave footing and climbable one-block steps intact.
        var corridor=mc.player.getBoundingBox().stretch(flat.multiply(1.1)).contract(.03);
        return supports.stream().filter(pos->pos.getY()>=feet.getY()&&pos.getY()<=feet.getY()+1)
            .filter(pos->mc.world.getBlockState(pos).isOf(Blocks.DIRT)&&new Box(pos).intersects(corridor))
            .filter(pos->!new Box(pos).intersects(mc.player.getBoundingBox().offset(0,-.15,0)))
            .filter(pos->pos.getY()>feet.getY()||!clear(pos.up())||!clear(feet.up(2)))
            .min(Comparator.comparingDouble(pos->pos.getSquaredDistance(feet))).orElse(null);
    }
    public void requestRecovery(){recoveryRequested=true;}
    public boolean canPillar(BlockPos feet){return clear(feet)&&clear(feet.up())&&clear(feet.up(2))&&clear(feet.up(3))&&safe(feet.down())&&mc.world.getBlockState(feet.down()).isSideSolidFullSquare(mc.world,feet.down(),Direction.UP);}
    private boolean approach(BlockPos target,double distance,boolean stand){
        if(mc.player==null||mc.world==null)return false;
        if(!target.equals(goal)||exact!=stand){stop();goal=target;exact=stand;}
        if(exact?atStandingView(target,distance):mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target))<=distance*distance){
            release();if(!exact||mc.player.isOnGround()&&mc.player.getVelocity().horizontalLengthSquared()<.0004){failedRoutes=0;recoveryRequested=movementStalled=false;return true;}
            status="Settling at build position";return false;
        }
        if(retry>0)retry--;
        if(cursor>=path.size()){
            if(retry>0){release();return false;}
            path=exact&&walkingCell().equals(target)&&walkable(target)?List.of(target):find(walkingCell(),target,distance,exact,true);cursor=0;retry=20;
            if(path.isEmpty()){if(++failedRoutes>=2)recoveryRequested=true;release();status="No safe walking route — move closer or add stairs";return false;}
            // A previous failed search must not keep requesting underfoot steps
            // once ordinary walking has a verified route again.
            failedRoutes=0;recoveryRequested=movementStalled=false;
        }
        var node=path.get(cursor);var point=standingPoint(node);
        double dx=point.x-mc.player.getX(),dz=point.z-mc.player.getZ();
        boolean swimming=!waterExitCells.isEmpty()&&mc.player.isTouchingWater();
        while(dx*dx+dz*dz<.16&&Math.abs(point.y-mc.player.getY())<(swimming?.2:.65)&&(mc.player.isOnGround()||swimming)){
            if(exact&&cursor==path.size()-1&&node.equals(target))break;
            if(++cursor>=path.size()){release();return false;}
            node=path.get(cursor);point=standingPoint(node);dx=point.x-mc.player.getX();dz=point.z-mc.player.getZ();
        }
        for(int next=Math.min(cursor+3,path.size()-1);next>cursor;next--){
            if(path.get(next).getY()==node.getY()&&straightTo(path.get(next))){cursor=next;node=path.get(cursor);point=standingPoint(node);dx=point.x-mc.player.getX();dz=point.z-mc.player.getZ();break;}
        }
        if(!walkable(node)||!safe(mc.player.getBlockPos())){path=List.of();release();return false;}
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        float error=MathHelper.wrapDegrees(yaw-mc.player.getYaw());
        // A placement may leave the view pointing at the ground. Bring it back toward
        // the walking corridor gradually instead of carrying that pitch along the route.
        float walkingPitch=point.y>mc.player.getY()+.4?-12:12;
        lookAt(yaw,walkingPitch);
        float headingError=Math.abs(MathHelper.wrapDegrees(yaw-mc.player.getYaw()));
        // At the final viewpoint, let each short movement settle before the
        // next input. Full walking speed otherwise overshoots a small target
        // and spends repeated turns chasing it around the standing cell.
        boolean lower=point.y<mc.player.getY()-.2;
        boolean close=dx*dx+dz*dz<.75*.75&&(exact&&node.equals(target)&&Math.abs(point.y-mc.player.getY())<.2
            ||lower&&mc.player.isOnGround());
        // Brake over the centre of a lower landing while gravity catches up.
        // Continuing forward at the old height can carry us past a one-block
        // post, even though the planned three-block drop itself is safe.
        // A grounded body can still overlap the upper ledge while its centre
        // is above the lower cell. Use short centring inputs until that overlap
        // clears; otherwise an early brake parks permanently on the ledge.
        boolean landing=lower&&dx*dx+dz*dz<.4*.4&&(!mc.player.isOnGround()||dx*dx+dz*dz<.12*.12);
        boolean move=!landing&&headingError<(close?8:24)&&(!close||mc.player.getVelocity().horizontalLengthSquared()<.0004);
        if(move){forward=true;mc.options.forwardKey.setPressed(true);}
        else if(forward){mc.options.forwardKey.setPressed(false);forward=false;}
        // Turn toward a raised waypoint before starting the jump. An early
        // jump while turning spends its height without reaching the ledge.
        boolean leavingWater=mc.player.isTouchingWater();
        if(forward&&headingError<12&&dx*dx+dz*dz<1.3*1.3&&point.y>mc.player.getY()+(swimming?-.05:leavingWater?.05:.4)
            &&(mc.player.isOnGround()||leavingWater)){jump=true;mc.options.jumpKey.setPressed(true);}
        else if(jump){mc.options.jumpKey.setPressed(false);jump=false;}
        Vec3d now=mc.player.getEntityPos();
        if(last==null||now.subtract(last).horizontalLengthSquared()>.04||mc.player.isOnGround()&&Math.abs(now.y-last.y)>.2){last=now;stuck=0;movementStalled=false;}else if(forward)stuck++;
        if(stuck>30){recoveryRequested=movementStalled=true;path=List.of();release();stuck=0;retry=20;}
        status="Walking to build position";return false;
    }
    private boolean atStandingView(BlockPos target,double distance){
        var point=standingPoint(target);var position=mc.player.getEntityPos();
        if(position.squaredDistanceTo(point)<=distance*distance)return true;
        double dx=position.x-point.x,dz=position.z-point.z;
        if(dx*dx+dz*dz>distance*distance||!mc.player.isOnGround()||!walkable(target))return false;
        // A hopper rim (or adjacent partial surface) can support the actual
        // player above the nominal centre height. Do not orbit the centre to
        // force that height: require an actual nearby native footing contact.
        var contact=mc.player.getBoundingBox().offset(0,-.02,0);
        for(var shape:mc.world.getBlockCollisions(mc.player,contact))for(var box:shape.getBoundingBoxes())
            if(box.intersects(contact)&&Math.abs(box.maxY-position.y)<.025&&Math.abs(box.maxY-point.y)<=.5)return true;
        return false;
    }
    private boolean straightTo(BlockPos node){
        var start=mc.player.getEntityPos();var end=standingPoint(node);double length=start.distanceTo(end);
        if(Math.abs(start.y-end.y)>.2||length>4)return false;
        int samples=Math.max(1,(int)Math.ceil(length/.2));
        for(int i=1;i<=samples;i++){
            var sample=start.lerp(end,(double)i/samples);var feet=BlockPos.ofFloored(sample.add(0,.4,0));
            if(!walkable(feet)||!mc.world.isSpaceEmpty(mc.player,mc.player.getBoundingBox().offset(sample.subtract(start))))return false;
        }
        return true;
    }
    private List<BlockPos> find(BlockPos start,BlockPos target,double reach,boolean exactGoal){
        return find(start,target,reach,exactGoal,false);
    }
    private List<BlockPos> find(BlockPos start,BlockPos target,double reach,boolean exactGoal,boolean allowSegments){
        return find(start,target,reach,exactGoal,allowSegments,false);
    }
    private List<BlockPos> find(BlockPos start,BlockPos target,double reach,boolean exactGoal,boolean allowSegments,boolean dryGoal){
        // A search reads one immutable collision/mask snapshot. Neighbour checks
        // revisit these same cells many times; resolve their shapes once per pass.
        Map<BlockPos,Boolean> standingCache=new HashMap<>(),clearCache=new HashMap<>();
        Map<BlockPos,Vec3d> pointCache=new HashMap<>();
        java.util.function.Predicate<BlockPos> standable=pos->standingCache.computeIfAbsent(pos,this::walkable);
        java.util.function.Predicate<BlockPos> clearance=pos->clearCache.computeIfAbsent(pos,this::clear);
        java.util.function.Function<BlockPos,Vec3d> points=pos->pointCache.computeIfAbsent(pos,this::standingPoint);

        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos,Double> costs=new HashMap<>();Set<BlockPos> closed=new HashSet<>();
        open.add(new Node(start,0,heuristic(start,target),null));costs.put(start,0.0);
        Node frontier=null;double initialDistance=heuristic(start,target),frontierDistance=initialDistance;
        double initialHeight=clearedForSearch.isEmpty()&&start.equals(walkingCell())?mc.player.getY():points.apply(start).y;
        long deadline=System.nanoTime()+3_000_000;int visited=0;
        while(!open.isEmpty()&&visited++<2048&&System.nanoTime()<deadline){
            Node n=open.poll();if(!closed.add(n.pos))continue;
            double remaining=heuristic(n.pos,target);
            if(remaining<frontierDistance){frontier=n;frontierDistance=remaining;}
            Vec3d eye=points.apply(n.pos).add(0,mc.player.getStandingEyeHeight(),0);
            if(dryGoal?n.parent!=null&&dryDepartureGoal(n.pos):exactGoal?n.pos.equals(target):eye.squaredDistanceTo(Vec3d.ofCenter(target))<=reach*reach){
                LinkedList<BlockPos> result=new LinkedList<>();for(Node p=n;p.parent!=null;p=p.parent)result.addFirst(p.pos);return result;
            }
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                boolean sameColumn=dx==0&&dz==0;
                // A player can straddle a post with their centre over air. In
                // that case moving toward this column's centre safely drops
                // onto the floor below; cardinal-only neighbours miss it.
                boolean swimColumn=dryGoal&&swimmingCell(n.pos);
                if(sameColumn&&!swimColumn&&(!n.pos.equals(start)||standable.test(n.pos)))continue;boolean diagonal=dx!=0&&dz!=0;
                if(diagonal&&(!standable.test(n.pos.add(dx,0,0))||!standable.test(n.pos.add(0,0,dz))))continue;
                var adjacent=n.pos.add(dx,0,dz);BlockPos step=null;
                for(int dy:sameColumn?(swimColumn?new int[]{1,-1}:new int[]{-1,-2,-3}):new int[]{0,1,-1,-2,-3}){var p=adjacent.up(dy);if(standable.test(p)){step=p;break;}}
                if(step==null||closed.contains(step)||step.getManhattanDistance(start)>64)continue;
                double rise=points.apply(step).y-(n.parent==null?initialHeight:points.apply(n.pos).y);
                if(rise>1.2||rise< -3)continue;
                if(diagonal&&step.getY()!=n.pos.getY())continue;
                // An open trapdoor can leave room for the body at a cell's
                // centre while blocking entry across one edge. Check that edge,
                // otherwise prefer another side instead of walking into its panel.
                if(Math.abs(rise)<.01&&(!clearance.test(n.pos)||!clearance.test(n.pos.up())||!clearance.test(step)||!clearance.test(step.up()))
                    &&!bodyCorridor(points.apply(n.pos),points.apply(step)))continue;
                if(rise>.01){
                    var from=n.parent==null&&start.equals(walkingCell())?mc.player.getEntityPos():points.apply(n.pos);
                    // A closed door's centre can fit the body while its entry
                    // panel still blocks the jump. Prove the lift and approach.
                    if(!jumpClear(from,step))continue;
                }
                if(rise<-.01){
                    var from=n.parent==null&&start.equals(walkingCell())?mc.player.getEntityPos():points.apply(n.pos);
                    var over=new Vec3d(points.apply(step).x,from.y,points.apply(step).z);
                    // A clear lower landing does not prove the ledge approach:
                    // doors and other partial panels can block the body above it.
                    if(!bodyCorridor(from,over)||!bodyCorridor(over,points.apply(step)))continue;
                }
                if(step.getY()>n.pos.getY()&&!clearance.test(n.pos.up(2)))continue;
                // Check the whole falling corridor, including the headroom at
                // the ledge. An ordinary three-block drop is safe when actual
                // footing heights agree, but a low ceiling can obstruct it.
                boolean corridor=true;
                if(step.getY()<n.pos.getY())for(int y=step.getY()+2;y<=n.pos.getY()+1;y++)if(!clearance.test(new BlockPos(step.getX(),y,step.getZ()))){corridor=false;break;}
                if(!corridor)continue;
                double cost=n.cost+(diagonal?Math.sqrt(2):1)+(step.getY()!=n.pos.getY()?.35:0);
                if(cost>=costs.getOrDefault(step,Double.POSITIVE_INFINITY))continue;
                costs.put(step,cost);open.add(new Node(step,cost,cost+(dryGoal?0:heuristic(step,target)),n));
            }
        }
        // Keep each search bounded. Long journeys advance along a verified safe
        // segment, then replan as new chunks arrive. Exact-view feasibility calls
        // still require a complete route and never accept a partial segment.
        if(allowSegments&&start.getSquaredDistance(target)>48*48&&frontier!=null&&frontierDistance<initialDistance-4){
            LinkedList<BlockPos> result=new LinkedList<>();for(Node p=frontier;p.parent!=null;p=p.parent)result.addFirst(p.pos);return result;
        }
        return List.of();
    }
    private static double heuristic(BlockPos a,BlockPos b){int x=Math.abs(a.getX()-b.getX()),z=Math.abs(a.getZ()-b.getZ());return Math.max(x,z)+(Math.sqrt(2)-1)*Math.min(x,z)+Math.abs(a.getY()-b.getY())*.6;}
    private boolean clear(BlockPos p){return !stairsForSearch.contains(p)&&!p.equals(pillarForSearch)&&mc.world.isChunkLoaded(p)&&(clearedForSearch.contains(p)||mc.world.getBlockState(p).getCollisionShape(mc.world,p).isEmpty())&&safe(p);}
    private double footingHeight(BlockPos p){
        if(p.equals(pillarForSearch)||stairsForSearch.contains(p))return 1;
        if(clearedForSearch.contains(p))return 0;
        var state=mc.world.getBlockState(p);
        if(state.isSideSolidFullSquare(mc.world,p,Direction.UP))return 1;
        double height=0;
        // Hoppers and chests are walkable although their top is not a full square.
        // Find the surface beneath a centred player, rather than the outer hopper rim.
        for(var box:state.getCollisionShape(mc.world,p).getBoundingBoxes())
            if(box.maxX>.2&&box.minX<.8&&box.maxZ>.2&&box.minZ<.8)height=Math.max(height,box.maxY);
        return height;
    }
    private boolean walkable(BlockPos p){
        if(!mc.world.isChunkLoaded(p)||!safe(p)||!safe(p.up())||!safe(p.down()))return false;
        double height=footingHeight(p.down());if(height<.625)return swimmingCell(p)&&bodyClear(standingPoint(p));
        if(height==1&&clear(p)&&clear(p.up()))return true;
        return bodyClear(standingPoint(p));
    }
    private boolean bodyCorridor(Vec3d from,Vec3d to){
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.2));
        for(int i=1;i<=samples;i++)if(!bodyClear(from.lerp(to,(double)i/samples)))return false;
        return true;
    }
    private boolean jumpClear(Vec3d from,BlockPos step){
        var to=standingPoint(step);var raised=new Vec3d(from.x,to.y,from.z);
        if(bodyCorridor(from,raised)&&bodyCorridor(raised,to))return true;
        // A hopper's centred footing is lower than its rim. A normal jump can
        // clear that lip and settle inside; the centre-height approach cannot.
        double lip=to.y;
        for(var box:mc.world.getBlockState(step.down()).getCollisionShape(mc.world,step.down()).getBoundingBoxes())
            lip=Math.max(lip,step.getY()-1+box.maxY);
        if(lip<=to.y+.001||lip>from.y+1.25)return false;
        raised=new Vec3d(from.x,lip,from.z);var over=new Vec3d(to.x,lip,to.z);
        return bodyCorridor(from,raised)&&bodyCorridor(raised,over)&&bodyCorridor(over,to);
    }
    /** Actual standing-body volume, including partial blocks in the head cell.
     * Future route masks are collision-only; no client or server state changes. */
    private boolean bodyClear(Vec3d feet){
        var dimensions=mc.player.getDimensions(net.minecraft.entity.EntityPose.STANDING);
        double half=dimensions.width()/2;
        var body=new Box(feet.x-half,feet.y,feet.z-half,feet.x+half,feet.y+dimensions.height(),feet.z+half).contract(.000001);
        for(var cell:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ))){
            if(!mc.world.isChunkLoaded(cell)||!safe(cell))return false;
            if(clearedForSearch.contains(cell))continue;
            var shape=stairsForSearch.contains(cell)||cell.equals(pillarForSearch)?net.minecraft.util.shape.VoxelShapes.fullCube()
                :mc.world.getBlockState(cell).getCollisionShape(mc.world,cell,net.minecraft.block.ShapeContext.of(mc.player));
            for(var bounds:shape.getBoundingBoxes())if(bounds.offset(cell).intersects(body))return false;
        }
        return true;
    }
    private boolean safe(BlockPos p){
        var state=mc.world.getBlockState(p);
        // Ordinary queries permit departure through water already touching the body.
        // The explicit departure search adds its bounded connected-volume mask;
        // other fluids and physical hazards remain blocked.
        boolean departingWater=!strictDrySearch&&state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)
            &&new Box(p).intersects(mc.player.getBoundingBox());
        return (state.getFluidState().isEmpty()||departingWater||waterExitCells.contains(p)&&state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER))&&!state.isOf(Blocks.FIRE)&&!state.isOf(Blocks.SOUL_FIRE)&&!state.isOf(Blocks.MAGMA_BLOCK)&&!state.isOf(Blocks.CACTUS)&&!state.isOf(Blocks.SWEET_BERRY_BUSH)&&!state.isOf(Blocks.POWDER_SNOW)&&!state.isOf(Blocks.CAMPFIRE)&&!state.isOf(Blocks.SOUL_CAMPFIRE);
    }
}
