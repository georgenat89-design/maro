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
    public String status="";
    public void stop(){
        release();path=List.of();goal=null;cursor=retry=stuck=failedRoutes=0;last=null;recoveryRequested=false;
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
    public boolean needsRecovery(){return recoveryRequested;}
    public boolean canPillar(BlockPos feet){return clear(feet)&&clear(feet.up())&&clear(feet.up(2))&&clear(feet.up(3))&&safe(feet.down())&&mc.world.getBlockState(feet.down()).isSideSolidFullSquare(mc.world,feet.down(),Direction.UP);}
    private boolean approach(BlockPos target,double distance,boolean stand){
        if(mc.player==null||mc.world==null)return false;
        if(!target.equals(goal)||exact!=stand){stop();goal=target;exact=stand;}
        if((exact?mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(target)):mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)))<=distance*distance){release();return true;}
        if(mc.player.squaredDistanceTo(Vec3d.ofCenter(target))>64*64){release();status="Target is beyond walking range";return false;}
        if(retry>0)retry--;
        if(cursor>=path.size()){
            if(retry>0){release();return false;}
            path=find(mc.player.getBlockPos(),target,distance);cursor=0;retry=20;
            if(path.isEmpty()){if(++failedRoutes>=2)recoveryRequested=true;release();status="No safe walking route — move closer or add stairs";return false;}
        }
        var node=path.get(cursor);var point=Vec3d.ofBottomCenter(node);
        double dx=point.x-mc.player.getX(),dz=point.z-mc.player.getZ();
        if(dx*dx+dz*dz<.14&&Math.abs(node.getY()-mc.player.getY())<.65){cursor++;release();return false;}
        if(!walkable(node)||!safe(mc.player.getBlockPos())){path=List.of();release();return false;}
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        mc.player.setYaw(mc.player.getYaw()+MathHelper.clamp(MathHelper.wrapDegrees(yaw-mc.player.getYaw()),-45,45));
        if(Math.abs(MathHelper.wrapDegrees(yaw-mc.player.getYaw()))<35){forward=true;mc.options.forwardKey.setPressed(true);}
        else if(forward){mc.options.forwardKey.setPressed(false);forward=false;}
        if(node.getY()>mc.player.getY()+.4&&mc.player.isOnGround()){jump=true;mc.options.jumpKey.setPressed(true);}
        else if(jump){mc.options.jumpKey.setPressed(false);jump=false;}
        Vec3d now=mc.player.getEntityPos();
        if(last!=null&&now.squaredDistanceTo(last)<.0025)stuck++;else stuck=0;
        last=now;
        if(stuck>30){recoveryRequested=true;path=List.of();release();stuck=0;retry=20;}
        status="Walking to build position";return false;
    }
    private List<BlockPos> find(BlockPos start,BlockPos target,double reach){
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos,Double> costs=new HashMap<>();Set<BlockPos> closed=new HashSet<>();
        open.add(new Node(start,0,heuristic(start,target),null));costs.put(start,0.0);
        long deadline=System.nanoTime()+3_000_000;int visited=0;
        while(!open.isEmpty()&&visited++<2048&&System.nanoTime()<deadline){
            Node n=open.poll();if(!closed.add(n.pos))continue;
            Vec3d eye=Vec3d.ofBottomCenter(n.pos).add(0,mc.player.getStandingEyeHeight(),0);
            if(exact?n.pos.equals(target):eye.squaredDistanceTo(Vec3d.ofCenter(target))<=reach*reach){
                LinkedList<BlockPos> result=new LinkedList<>();for(Node p=n;p.parent!=null;p=p.parent)result.addFirst(p.pos);return result;
            }
            for(var side:Direction.Type.HORIZONTAL){
                var adjacent=n.pos.offset(side);BlockPos step=null;
                for(int dy:new int[]{0,1,-1}){var p=adjacent.up(dy);if(walkable(p)){step=p;break;}}
                if(step==null||closed.contains(step)||step.getManhattanDistance(start)>64)continue;
                if(step.getY()>n.pos.getY()&&!clear(n.pos.up(2)))continue;
                double cost=n.cost+1+(step.getY()!=n.pos.getY()?.35:0);
                if(cost>=costs.getOrDefault(step,Double.POSITIVE_INFINITY))continue;
                costs.put(step,cost);open.add(new Node(step,cost,cost+heuristic(step,target),n));
            }
        }
        return List.of();
    }
    private static double heuristic(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*.6;}
    private boolean clear(BlockPos p){return mc.world.isChunkLoaded(p)&&mc.world.getBlockState(p).getCollisionShape(mc.world,p).isEmpty()&&safe(p);}
    private boolean walkable(BlockPos p){
        return clear(p)&&clear(p.up())&&safe(p.down())&&mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world,p.down(),Direction.UP);
    }
    private boolean safe(BlockPos p){
        var state=mc.world.getBlockState(p);
        return state.getFluidState().isEmpty()&&!state.isOf(Blocks.FIRE)&&!state.isOf(Blocks.SOUL_FIRE)&&!state.isOf(Blocks.MAGMA_BLOCK)&&!state.isOf(Blocks.CACTUS)&&!state.isOf(Blocks.SWEET_BERRY_BUSH)&&!state.isOf(Blocks.POWDER_SNOW)&&!state.isOf(Blocks.CAMPFIRE)&&!state.isOf(Blocks.SOUL_CAMPFIRE);
    }
}
