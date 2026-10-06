package dev.maro.gametest;

import dev.maro.mixin.ClientPlayerLookAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

/** Observe actual outbound packets while a selected-chest restock and build run. */
public final class BuilderPacketChecks {
    public static boolean recording,vanillaMovement;
    private static int extraMovement,interactions,lastSequence,invalidSequence,unpublishedLook,repeatedMovement,lastMovementAge;
    public static void begin(){recording=true;extraMovement=interactions=lastSequence=invalidSequence=unpublishedLook=repeatedMovement=0;lastMovementAge=-1;}
    public static void movementStart(){
        vanillaMovement=true;if(!recording)return;
        int age=MinecraftClient.getInstance().player.age;
        if(age==lastMovementAge)repeatedMovement++;
        lastMovementAge=age;
    }
    public static void outbound(Packet<?> packet){
        if(!recording)return;
        if(packet instanceof PlayerMoveC2SPacket&&!vanillaMovement)extraMovement++;
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
        if(interactions<minimumInteractions||extraMovement!=0||invalidSequence!=0||unpublishedLook!=0||repeatedMovement!=0)
            throw new AssertionError("Builder packet order: interactions="+interactions+" extraMovement="+extraMovement+" invalidSequence="+invalidSequence+" unpublishedLook="+unpublishedLook+" repeatedMovement="+repeatedMovement);
    }
}
