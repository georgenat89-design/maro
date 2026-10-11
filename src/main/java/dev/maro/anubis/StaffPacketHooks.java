package dev.maro.anubis;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
/** Called only on the client thread: lets Staff Notifier see incoming packets. Cancels nothing. */
public final class StaffPacketHooks {
    private StaffPacketHooks() {}
    public static boolean receive(Packet<?> packet) {
        if(!MinecraftClient.getInstance().isOnThread())return false;
        var staff=ModuleManager.get(StaffNotifier.class);
        if(staff!=null&&staff.isEnabled())staff.onIncoming(packet);
        return false;
    }
    public static void send(Packet<?> packet) {
    }
}
