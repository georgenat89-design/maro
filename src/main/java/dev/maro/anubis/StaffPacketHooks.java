package dev.maro.anubis;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.anubis.module.impl.misc.AntiVanishModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
/** Called only on the client thread. Cancels only this module's completion responses. */
public final class StaffPacketHooks {
    private StaffPacketHooks() {}
    public static boolean receive(Packet<?> packet) {
        if(!MinecraftClient.getInstance().isOnThread())return false;
        var staff=ModuleManager.get(StaffNotifier.class);
        if(staff!=null&&staff.isEnabled())staff.onIncoming(packet);
        var anti=ModuleManager.get(AntiVanishModule.class);
        return anti!=null&&anti.isEnabled()&&anti.handleInbound(packet);
    }
    public static void send(Packet<?> packet) {
        if(!MinecraftClient.getInstance().isOnThread())return;
        var anti=ModuleManager.get(AntiVanishModule.class);
        if(anti!=null&&anti.isEnabled())anti.handleOutbound(packet);
    }
}
