package dev.maro.module.impl.movement;

import dev.maro.module.Category;
import dev.maro.module.Module;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Adapted from 4ldenz's Anubis 0.9.9 Air Stuck and local-player mixin, GPL-3.0-only. */
public final class AirStuck extends Module {
    private static volatile AirStuck active;
    private ClientPlayerEntity player;
    private ClientWorld world;
    private ClientPlayNetworkHandler session;

    public AirStuck() {
        super("Air Stuck", "Freeze in place, including mid-air; toggle again to resume", Category.MOVEMENT);
    }

    @Override public boolean persistEnabled() { return false; }

    @Override protected void onEnable() {
        if (!inGame() || !mc.player.isAlive() || mc.getNetworkHandler() == null) { setEnabled(false); return; }
        player = mc.player;
        world = mc.world;
        session = mc.getNetworkHandler();
        active = this;
    }

    @Override protected void onDisable() {
        if (active == this) active = null;
        player = null;
        world = null;
        session = null;
    }

    private boolean sameSession() {
        return isEnabled() && player != null && player == mc.player && world == mc.world
                && session == mc.getNetworkHandler() && player.isAlive();
    }

    @Override public void onTick() {
        if (!sameSession()) setEnabled(false);
    }

    public static boolean freezing(ClientPlayerEntity player) {
        AirStuck module = active;
        return module != null && module.player == player && module.sameSession();
    }

    public static boolean blocks(ClientConnection connection, Packet<?> packet) {
        AirStuck module = active;
        return module != null && module.sameSession() && module.session.getConnection() == connection
                && (packet instanceof PlayerMoveC2SPacket || packet instanceof ClientTickEndC2SPacket);
    }
}
