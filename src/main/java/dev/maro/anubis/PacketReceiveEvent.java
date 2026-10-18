package dev.maro.anubis;
import net.minecraft.network.packet.Packet;
public final class PacketReceiveEvent {
    public final Packet<?> packet;
    private boolean cancelled;
    public PacketReceiveEvent(Packet<?> packet) { this.packet=packet; }
    public void setCancelled(boolean value) { cancelled=value; }
    public boolean cancelled() { return cancelled; }
}
