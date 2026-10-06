package dev.maro.builder;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
import java.util.*;

/** Bounded ground pathfinding. Uses normal movement keys and never teleports or mines a route. */
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
    private float yawVelocity,turnLimit=45;
    public void turning(boolean smooth,float speed){this.smooth=smooth;turnLimit=speed;}
    public String status="";
    public void stop(){
        release();path=List.of();goal=null;cursor=retry=stuck=failedRoutes=0;last=null;recoveryRequested=movementStalled=false;yawVelocity=0;
    }
    public void release(){
        if(forward)mc.options.forwardKey.setPressed(false);
        if(jump)mc.options.jumpKey.setPressed(false);
        forward=jump=false;
    }
    public boolean approach(BlockPos target,double distance){
        return approach(target,distance,false);
    }
    public boolean standAt(BlockPos target){return approach(target,.22,true);}
    public boolean centerForJump(BlockPos target){return approach(target,.12,true);}
    public boolean canStand(BlockPos pos){return walkable(pos);}
    public boolean hasStandingClearance(BlockPos pos){return clear(pos)&&clear(pos.up());}
    public Vec3d standingPoint(BlockPos pos){return Vec3d.ofBottomCenter(pos).add(0,footingHeight(pos.down())-1,0);}
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
        while(dx*dx+dz*dz<.16&&Math.abs(point.y-mc.player.getY())<.65){
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
        float speed=smooth?Math.min(turnLimit,24):turnLimit;
        if(smooth){
            if(Math.signum(yawVelocity)!=Math.signum(error))yawVelocity=0;
            yawVelocity+=MathHelper.clamp(MathHelper.clamp(error*.28f,-speed,speed)-yawVelocity,-speed*.15f,speed*.15f);
            mc.player.setYaw(mc.player.getYaw()+Math.copySign(Math.min(Math.abs(error),Math.abs(yawVelocity)),error));
        }else mc.player.setYaw(mc.player.getYaw()+MathHelper.clamp(error,-turnLimit,turnLimit));
        // A placement may leave the view pointing at the ground. Bring it back toward
        // the walking corridor gradually instead of carrying that pitch along the route.
        float walkingPitch=point.y>mc.player.getY()+.4?-12:12;
        mc.player.setPitch(mc.player.getPitch()+MathHelper.clamp((walkingPitch-mc.player.getPitch())*.2f,-6,6));
        float headingError=Math.abs(MathHelper.wrapDegrees(yaw-mc.player.getYaw()));
        // At the final viewpoint, let each short movement settle before the
        // next input. Full walking speed otherwise overshoots a small target
        // and spends repeated turns chasing it around the standing cell.
        boolean close=exact&&node.equals(target)&&dx*dx+dz*dz<.75*.75&&Math.abs(point.y-mc.player.getY())<.2;
        boolean move=headingError<(close?8:24)&&(!close||mc.player.getVelocity().horizontalLengthSquared()<.0004);
        if(move){forward=true;mc.options.forwardKey.setPressed(true);}
        else if(forward){mc.options.forwardKey.setPressed(false);forward=false;}
        // Turn toward a raised waypoint before starting the jump. An early
        // jump while turning spends its height without reaching the ledge.
        if(forward&&headingError<12&&dx*dx+dz*dz<1.3*1.3&&point.y>mc.player.getY()+.4&&mc.player.isOnGround()){jump=true;mc.options.jumpKey.setPressed(true);}
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
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos,Double> costs=new HashMap<>();Set<BlockPos> closed=new HashSet<>();
        open.add(new Node(start,0,heuristic(start,target),null));costs.put(start,0.0);
        Node frontier=null;double initialDistance=heuristic(start,target),frontierDistance=initialDistance;
        double initialHeight=clearedForSearch.isEmpty()&&start.equals(walkingCell())?mc.player.getY():standingPoint(start).y;
        long deadline=System.nanoTime()+3_000_000;int visited=0;
        while(!open.isEmpty()&&visited++<2048&&System.nanoTime()<deadline){
            Node n=open.poll();if(!closed.add(n.pos))continue;
            double remaining=heuristic(n.pos,target);
            if(remaining<frontierDistance){frontier=n;frontierDistance=remaining;}
            Vec3d eye=standingPoint(n.pos).add(0,mc.player.getStandingEyeHeight(),0);
            if(exactGoal?n.pos.equals(target):eye.squaredDistanceTo(Vec3d.ofCenter(target))<=reach*reach){
                LinkedList<BlockPos> result=new LinkedList<>();for(Node p=n;p.parent!=null;p=p.parent)result.addFirst(p.pos);return result;
            }
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                boolean sameColumn=dx==0&&dz==0;
                // A player can straddle a post with their centre over air. In
                // that case moving toward this column's centre safely drops
                // onto the floor below; cardinal-only neighbours miss it.
                if(sameColumn&&(!n.pos.equals(start)||walkable(n.pos)))continue;boolean diagonal=dx!=0&&dz!=0;
                if(diagonal&&(!walkable(n.pos.add(dx,0,0))||!walkable(n.pos.add(0,0,dz))))continue;
                var adjacent=n.pos.add(dx,0,dz);BlockPos step=null;
                for(int dy:sameColumn?new int[]{-1,-2,-3}:new int[]{0,1,-1,-2,-3}){var p=adjacent.up(dy);if(walkable(p)){step=p;break;}}
                if(step==null||closed.contains(step)||step.getManhattanDistance(start)>64)continue;
                double rise=standingPoint(step).y-(n.parent==null?initialHeight:standingPoint(n.pos).y);
                if(rise>1.2||rise< -3)continue;
                if(diagonal&&step.getY()!=n.pos.getY())continue;
                if(step.getY()>n.pos.getY()&&!clear(n.pos.up(2)))continue;
                // Check the whole falling corridor, including the headroom at
                // the ledge. An ordinary three-block drop is safe when actual
                // footing heights agree, but a low ceiling can obstruct it.
                boolean corridor=true;
                if(step.getY()<n.pos.getY())for(int y=step.getY()+2;y<=n.pos.getY()+1;y++)if(!clear(new BlockPos(step.getX(),y,step.getZ()))){corridor=false;break;}
                if(!corridor)continue;
                double cost=n.cost+(diagonal?Math.sqrt(2):1)+(step.getY()!=n.pos.getY()?.35:0);
                if(cost>=costs.getOrDefault(step,Double.POSITIVE_INFINITY))continue;
                costs.put(step,cost);open.add(new Node(step,cost,cost+heuristic(step,target),n));
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
    private boolean walkable(BlockPos p){return clear(p)&&clear(p.up())&&safe(p.down())&&footingHeight(p.down())>=.625;}
    private boolean safe(BlockPos p){
        var state=mc.world.getBlockState(p);
        return state.getFluidState().isEmpty()&&!state.isOf(Blocks.FIRE)&&!state.isOf(Blocks.SOUL_FIRE)&&!state.isOf(Blocks.MAGMA_BLOCK)&&!state.isOf(Blocks.CACTUS)&&!state.isOf(Blocks.SWEET_BERRY_BUSH)&&!state.isOf(Blocks.POWDER_SNOW)&&!state.isOf(Blocks.CAMPFIRE)&&!state.isOf(Blocks.SOUL_CAMPFIRE);
    }
}
