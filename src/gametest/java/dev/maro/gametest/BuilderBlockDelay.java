package dev.maro.gametest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Hold real server corrections/sequence acknowledgements to expose client-only mined air. */
public final class BuilderBlockDelay {
    private static BlockPos target;
    private static boolean replaying;
    private static final List<Packet<ClientPlayPacketListener>> held=new ArrayList<>();
    public static void begin(BlockPos pos){target=pos;held.clear();}
    public static boolean intercept(Packet<ClientPlayPacketListener> packet){
        var client=MinecraftClient.getInstance();if(target==null||replaying||!client.isOnThread())return false;
        boolean relevant=packet instanceof PlayerActionResponseS2CPacket;
        if(packet instanceof BlockUpdateS2CPacket block)relevant=target.equals(block.getPos());
        if(packet instanceof ChunkDeltaUpdateS2CPacket delta){boolean[] found={false};delta.visitUpdates((pos,state)->{if(target.equals(pos))found[0]=true;});relevant=found[0];}
        if(relevant)held.add(packet);return relevant;
    }
    public static void release(){
        replaying=true;try{for(var packet:held)packet.apply(MinecraftClient.getInstance().getNetworkHandler());}finally{held.clear();target=null;replaying=false;}
    }
}
