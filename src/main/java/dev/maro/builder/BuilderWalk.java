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
    public boolean standAt(BlockPos target){return approach(target,.42,true);}
    public boolean canStand(BlockPos pos){return walkable(pos);}
    public boolean canReachStand(BlockPos pos){return walkable(pos)&&(mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(pos))<=.42*.42||!find(mc.player.getBlockPos(),pos,.42,true).isEmpty());}
    public boolean needsRecovery(){return recoveryRequested;}
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
        if((exact?mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(target)):mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)))<=distance*distance){release();return true;}
        if(mc.player.squaredDistanceTo(Vec3d.ofCenter(target))>64*64){release();status="Target is beyond walking range";return false;}
        if(retry>0)retry--;
        if(cursor>=path.size()){
            if(retry>0){release();return false;}
            path=find(mc.player.getBlockPos(),target,distance,exact);cursor=0;retry=20;
            if(path.isEmpty()){if(++failedRoutes>=2)recoveryRequested=true;release();status="No safe walking route — move closer or add stairs";return false;}
        }
        var node=path.get(cursor);var point=Vec3d.ofBottomCenter(node);
        double dx=point.x-mc.player.getX(),dz=point.z-mc.player.getZ();
        while(dx*dx+dz*dz<.04&&Math.abs(node.getY()-mc.player.getY())<.65){
            if(++cursor>=path.size()){release();return false;}
            node=path.get(cursor);point=Vec3d.ofBottomCenter(node);dx=point.x-mc.player.getX();dz=point.z-mc.player.getZ();
        }
        for(int next=Math.min(cursor+3,path.size()-1);next>cursor;next--){
            if(path.get(next).getY()==node.getY()&&straightTo(path.get(next))){cursor=next;node=path.get(cursor);point=Vec3d.ofBottomCenter(node);dx=point.x-mc.player.getX();dz=point.z-mc.player.getZ();break;}
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
        if(Math.abs(MathHelper.wrapDegrees(yaw-mc.player.getYaw()))<24){forward=true;mc.options.forwardKey.setPressed(true);}
        else if(forward){mc.options.forwardKey.setPressed(false);forward=false;}
        if(node.getY()>mc.player.getY()+.4&&mc.player.isOnGround()){jump=true;mc.options.jumpKey.setPressed(true);}
        else if(jump){mc.options.jumpKey.setPressed(false);jump=false;}
        Vec3d now=mc.player.getEntityPos();
        if(last==null||now.squaredDistanceTo(last)>.04){last=now;stuck=0;movementStalled=false;}else if(forward)stuck++;
        if(stuck>30){recoveryRequested=movementStalled=true;path=List.of();release();stuck=0;retry=20;}
        status="Walking to build position";return false;
    }
    private boolean straightTo(BlockPos node){
        var start=mc.player.getEntityPos();var end=Vec3d.ofBottomCenter(node);double length=start.distanceTo(end);
        if(Math.abs(start.y-end.y)>.2||length>4)return false;
        int samples=Math.max(1,(int)Math.ceil(length/.2));
        for(int i=1;i<=samples;i++){
            var sample=start.lerp(end,(double)i/samples);var feet=BlockPos.ofFloored(sample);
            if(!walkable(feet)||!mc.world.isSpaceEmpty(mc.player,mc.player.getBoundingBox().offset(sample.subtract(start))))return false;
        }
        return true;
    }
    private List<BlockPos> find(BlockPos start,BlockPos target,double reach,boolean exactGoal){
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos,Double> costs=new HashMap<>();Set<BlockPos> closed=new HashSet<>();
        open.add(new Node(start,0,heuristic(start,target),null));costs.put(start,0.0);
        long deadline=System.nanoTime()+3_000_000;int visited=0;
        while(!open.isEmpty()&&visited++<2048&&System.nanoTime()<deadline){
            Node n=open.poll();if(!closed.add(n.pos))continue;
            Vec3d eye=Vec3d.ofBottomCenter(n.pos).add(0,mc.player.getStandingEyeHeight(),0);
            if(exactGoal?n.pos.equals(target):eye.squaredDistanceTo(Vec3d.ofCenter(target))<=reach*reach){
                LinkedList<BlockPos> result=new LinkedList<>();for(Node p=n;p.parent!=null;p=p.parent)result.addFirst(p.pos);return result;
            }
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;boolean diagonal=dx!=0&&dz!=0;
                if(diagonal&&(!walkable(n.pos.add(dx,0,0))||!walkable(n.pos.add(0,0,dz))))continue;
                var adjacent=n.pos.add(dx,0,dz);BlockPos step=null;
                for(int dy:new int[]{0,1,-1,-2}){var p=adjacent.up(dy);if(walkable(p)){step=p;break;}}
                if(step==null||closed.contains(step)||step.getManhattanDistance(start)>64)continue;
                if(diagonal&&step.getY()!=n.pos.getY())continue;
                if(step.getY()>n.pos.getY()&&!clear(n.pos.up(2)))continue;
                if(step.getY()<n.pos.getY()-1&&!clear(step.up(2)))continue;
                double cost=n.cost+(diagonal?Math.sqrt(2):1)+(step.getY()!=n.pos.getY()?.35:0);
                if(cost>=costs.getOrDefault(step,Double.POSITIVE_INFINITY))continue;
                costs.put(step,cost);open.add(new Node(step,cost,cost+heuristic(step,target),n));
            }
        }
        return List.of();
    }
    private static double heuristic(BlockPos a,BlockPos b){int x=Math.abs(a.getX()-b.getX()),z=Math.abs(a.getZ()-b.getZ());return Math.max(x,z)+(Math.sqrt(2)-1)*Math.min(x,z)+Math.abs(a.getY()-b.getY())*.6;}
    private boolean clear(BlockPos p){return mc.world.isChunkLoaded(p)&&mc.world.getBlockState(p).getCollisionShape(mc.world,p).isEmpty()&&safe(p);}
    private boolean walkable(BlockPos p){
        return clear(p)&&clear(p.up())&&safe(p.down())&&mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world,p.down(),Direction.UP);
    }
    private boolean safe(BlockPos p){
        var state=mc.world.getBlockState(p);
        return state.getFluidState().isEmpty()&&!state.isOf(Blocks.FIRE)&&!state.isOf(Blocks.SOUL_FIRE)&&!state.isOf(Blocks.MAGMA_BLOCK)&&!state.isOf(Blocks.CACTUS)&&!state.isOf(Blocks.SWEET_BERRY_BUSH)&&!state.isOf(Blocks.POWDER_SNOW)&&!state.isOf(Blocks.CAMPFIRE)&&!state.isOf(Blocks.SOUL_CAMPFIRE);
    }
}
