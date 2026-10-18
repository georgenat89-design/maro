package dev.maro.anubis;
import dev.maro.module.ModuleManager;
import dev.maro.anubis.tpa.AutoTpaHereModule;
import dev.maro.anubis.tpa.TpMacroModule;
import net.minecraft.network.packet.Packet;
public final class TpPacketHooks {
    public static boolean receive(Packet<?> packet) {
        var event=new PacketReceiveEvent(packet);
        var bot=ModuleManager.get(AutoTpaHereModule.class);
        if(bot!=null&&bot.isEnabled())bot.onPacket(event);
        var macro=ModuleManager.get(TpMacroModule.class);
        if(macro!=null&&macro.isEnabled())macro.onPacket(event);
        return event.cancelled();
    }
}
