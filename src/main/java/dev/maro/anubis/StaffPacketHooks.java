package dev.maro.anubis;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
/** Client-thread dispatch; only completion replies owned by the teleport bot may be consumed. */
public final class StaffPacketHooks {
    private StaffPacketHooks() {}
    public static boolean receive(Packet<?> packet) {
        if(!MinecraftClient.getInstance().isOnThread())return false;
        var staff=ModuleManager.get(StaffNotifier.class);
        if(staff!=null&&staff.isEnabled())staff.onIncoming(packet);
        return TpPacketHooks.receive(packet);
    }
    public static void send(Packet<?> packet) {
    }
}
