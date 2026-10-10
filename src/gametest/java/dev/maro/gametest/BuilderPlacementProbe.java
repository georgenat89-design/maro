package dev.maro.gametest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Simulate missing inventory corrections after real server-rejected placements. */
public final class BuilderPlacementProbe {
    private static BlockPos target;
    private static boolean holdInventory;
    private static int sourceSlot;
    public static int slotClicks,emptySlotClicks,withheld,wrongSelectedClicks,firstSlotClick;
    public static final List<Integer> attempts=new ArrayList<>();
    public static final List<Double> distances=new ArrayList<>();
    public static void begin(BlockPos pos){begin(pos,0);}
    public static void begin(BlockPos pos,int slot){target=pos;sourceSlot=slot;holdInventory=false;slotClicks=emptySlotClicks=withheld=wrongSelectedClicks=0;firstSlotClick=-1;attempts.clear();distances.clear();}
    public static void end(){target=null;holdInventory=false;}
    public static void outbound(Packet<?> packet){
        var client=MinecraftClient.getInstance();if(target==null||!client.isOnThread())return;
        if(packet instanceof PlayerInteractBlockC2SPacket block&&block.getBlockHitResult().getBlockPos().equals(target.down())){
            attempts.add(client.player.age);distances.add(client.player.getEyePos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(target)));holdInventory=attempts.size()<=3;
        }
        if(packet instanceof ClickSlotC2SPacket click&&click.syncId()==0&&click.slot()==36+sourceSlot&&click.actionType()==SlotActionType.PICKUP){
            if(firstSlotClick<0)firstSlotClick=client.player.age;
            slotClicks++;if(client.player.getInventory().getStack(sourceSlot).isEmpty())emptySlotClicks++;
            if(client.player.getInventory().getSelectedSlot()!=sourceSlot)wrongSelectedClicks++;
            // Discard only the missed rejection corrections. The two actual native
            // inventory clicks must now obtain fresh server slot/cursor responses.
            if(slotClicks%2==0)holdInventory=false;
        }
    }
    public static boolean intercept(Packet<?> packet){
        var client=MinecraftClient.getInstance();if(target==null||!holdInventory||!client.isOnThread())return false;
        boolean inventory=packet instanceof InventoryS2CPacket full&&full.syncId()==0
            ||packet instanceof ScreenHandlerSlotUpdateS2CPacket slot&&slot.getSyncId()==0
            ||packet instanceof SetPlayerInventoryS2CPacket||packet instanceof SetCursorItemS2CPacket;
        if(inventory)withheld++;return inventory;
    }
}
