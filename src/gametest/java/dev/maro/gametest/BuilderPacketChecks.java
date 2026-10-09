package dev.maro.gametest;

import dev.maro.mixin.ClientPlayerLookAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import java.util.Set;
import net.minecraft.util.math.MathHelper;

/** Observe actual outbound packets while a selected-chest restock and build run. */
public final class BuilderPacketChecks {
    public static boolean recording,vanillaMovement,vanillaTeleport;
    private static int extraMovement,interactions,lastSequence,invalidSequence,unpublishedLook,repeatedMovement,lastMovementAge;
    private static Set<BlockPos> crouchTargets=Set.of();
    private static int crouchMining,crouchFailures;
    private static Set<BlockPos> protectedBlocks=Set.of();
    private static int protectedMining,hopperMining;
    private static float lookYaw,lookPitch,maxYaw,maxPitch;
    private static int abruptLooks;
    private static dev.maro.builder.BuilderHomes checkedHomes;
    public static void begin(){recording=true;extraMovement=interactions=lastSequence=invalidSequence=unpublishedLook=repeatedMovement=crouchMining=crouchFailures=abruptLooks=protectedMining=hopperMining=0;lastMovementAge=-1;crouchTargets=protectedBlocks=Set.of();checkedHomes=null;}
    public static void expectIntactBlocks(Set<BlockPos> targets){protectedBlocks=Set.copyOf(targets);}
    public static void expectLookLimits(float yaw,float pitch){
        var player=MinecraftClient.getInstance().player;lookYaw=player.getYaw();lookPitch=player.getPitch();maxYaw=yaw;maxPitch=pitch;
        try{var field=dev.maro.module.impl.player.AutoBuilder.class.getDeclaredField("homes");field.setAccessible(true);checkedHomes=(dev.maro.builder.BuilderHomes)field.get(dev.maro.module.ModuleManager.get(dev.maro.module.impl.player.AutoBuilder.class));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    public static void expectCrouchedMining(Set<BlockPos> targets){crouchTargets=Set.copyOf(targets);}
    public static void movementStart(){
        vanillaMovement=true;if(!recording)return;
        int age=MinecraftClient.getInstance().player.age;
        if(age==lastMovementAge)repeatedMovement++;
        lastMovementAge=age;
    }
    public static void outbound(Packet<?> packet){
        if(!recording)return;
        if(packet instanceof PlayerActionC2SPacket mine&&protectedBlocks.contains(mine.getPos())
            &&(mine.getAction()==PlayerActionC2SPacket.Action.START_DESTROY_BLOCK||mine.getAction()==PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK))protectedMining++;
        if(packet instanceof PlayerActionC2SPacket mine
            &&(mine.getAction()==PlayerActionC2SPacket.Action.START_DESTROY_BLOCK||mine.getAction()==PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)){
            var client=MinecraftClient.getInstance();var builder=dev.maro.module.ModuleManager.get(dev.maro.module.impl.player.AutoBuilder.class);
            // Check actual outbound mining independently of the planner and its caches.
            // Owned dirt is deliberately removable, even over a hopper.
            if(client.world!=null&&builder!=null&&!builder.temporarySupports().contains(mine.getPos())){
                boolean caught=false;
                for(int dx=-1;dx<=1&&!caught;dx++)for(int dz=-1;dz<=1&&!caught;dz++)
                    for(int y=client.world.getBottomY();y<mine.getPos().getY();y++)
                        if(client.world.getBlockState(new BlockPos(mine.getPos().getX()+dx,y,mine.getPos().getZ()+dz)).getBlock() instanceof net.minecraft.block.HopperBlock){caught=true;break;}
                if(caught)hopperMining++;
            }
        }
        if(packet instanceof PlayerMoveC2SPacket move&&checkedHomes!=null){
            float yaw=move.getYaw(lookYaw),pitch=move.getPitch(lookPitch);
            float dy=Math.abs(MathHelper.wrapDegrees(yaw-lookYaw)),dp=Math.abs(pitch-lookPitch);
            if(!checkedHomes.busy()&&(dy>maxYaw+.02||dp>maxPitch+.02)){if(abruptLooks++==0)System.out.println("[builder-look] Packet exceeded rate: yaw="+dy+" pitch="+dp);}
            lookYaw=yaw;lookPitch=pitch;
        }
        if(packet instanceof PlayerActionC2SPacket mine&&crouchTargets.contains(mine.getPos())
            &&(mine.getAction()==PlayerActionC2SPacket.Action.START_DESTROY_BLOCK||mine.getAction()==PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)){
            crouchMining++;var player=MinecraftClient.getInstance().player;
            if(!player.isSneaking()||!player.isOnGround()||player.getHealth()!=20)crouchFailures++;
        }
        if(packet instanceof PlayerMoveC2SPacket&&!vanillaMovement&&!vanillaTeleport)extraMovement++;
        if(packet instanceof PlayerInteractBlockC2SPacket block){
            interactions++;if(block.getSequence()<=lastSequence)invalidSequence++;lastSequence=block.getSequence();
            var player=MinecraftClient.getInstance().player;var sent=(ClientPlayerLookAccessor)player;
            if(Math.abs(MathHelper.wrapDegrees(player.getYaw()-sent.maro$lastSentYaw()))>.01f
                ||Math.abs(player.getPitch()-sent.maro$lastSentPitch())>.01f)unpublishedLook++;
        }
        if(packet instanceof PlayerInteractItemC2SPacket item){
            interactions++;if(item.getSequence()<=lastSequence)invalidSequence++;lastSequence=item.getSequence();
            // Bucket/food use carries its own orientation in the native packet.
            var player=MinecraftClient.getInstance().player;
            if(Math.abs(MathHelper.wrapDegrees(player.getYaw()-item.getYaw()))>.01f
                ||Math.abs(player.getPitch()-item.getPitch())>.01f)unpublishedLook++;
        }
    }
    public static void verify(){
        verify(2);
    }
    public static void verify(int minimumInteractions){
        recording=false;
        if(protectedMining>0)throw new AssertionError("Mined a protected build block: "+protectedMining);
        if(hopperMining>0)throw new AssertionError("Native mining packet cut a build block above a hopper: "+hopperMining);
        if(abruptLooks>0)throw new AssertionError("Native head movement exceeded yaw/pitch limits: "+abruptLooks);
        if(!crouchTargets.isEmpty()&&(crouchMining==0||crouchFailures>0))throw new AssertionError("Native crouch mining: actions="+crouchMining+" invalid pose="+crouchFailures);
        if(interactions<minimumInteractions||extraMovement!=0||invalidSequence!=0||unpublishedLook!=0||repeatedMovement!=0)
            throw new AssertionError("Builder packet order: interactions="+interactions+" extraMovement="+extraMovement+" invalidSequence="+invalidSequence+" unpublishedLook="+unpublishedLook+" repeatedMovement="+repeatedMovement);
    }
}
