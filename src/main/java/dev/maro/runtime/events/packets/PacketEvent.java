package dev.maro.runtime.events.packets;

import net.minecraft.network.packet.Packet;
public class PacketEvent { public final Packet<?> packet; protected PacketEvent(Packet<?> p) { packet = p; } public static final class Receive extends PacketEvent { public Receive(Packet<?> p) { super(p); } } }
