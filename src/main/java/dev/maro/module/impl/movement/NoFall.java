package dev.maro.module.impl.movement;

import dev.maro.module.Category;
import dev.maro.module.Module;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Adapted from Big jeff's supplied addon-template 0.1.0 nofall, CC0-1.0. */
public final class NoFall extends Module {
    private static volatile NoFall active;
    private ClientPlayerEntity player;
    private ClientWorld world;
    private ClientPlayNetworkHandler session;
    private double sampledFallDistance;
    private int groundReports;
    private boolean protecting;

    public NoFall() {
        super("No Fall", "Reduces fall damage using the supplied addon's movement-packet method", Category.MOVEMENT);
    }

    @Override protected void onEnable() {
        reset();
        active = this;
    }

    @Override protected void onDisable() {
        if (active == this) active = null;
        reset();
    }

    private void reset() {
        player = null;
        world = null;
        session = null;
        sampledFallDistance = 0;
        groundReports = 0;
        protecting = false;
    }

    private boolean syncSession() {
        if (!inGame() || mc.getNetworkHandler() == null || !mc.player.isAlive()) { reset(); return false; }
        if (player != mc.player || world != mc.world || session != mc.getNetworkHandler()) {
            reset();
            player = mc.player;
            world = mc.world;
            session = mc.getNetworkHandler();
        }
        return true;
    }

    /** Runs before local movement, matching the source addon's pre-tick sampling. */
    public static void beforeTick() {
        NoFall module = active;
        if (module == null || !module.isEnabled() || !module.syncSession() || AirStuck.freezing(mc.player)) return;
        if (module.protecting) module.player.setOnGround(false);
        if (module.sampledFallDistance > 3) module.protecting = true;
        module.sampledFallDistance = module.player.fallDistance;
    }

    /** Replaces the packet argument once, preserving the connection's normal send/callback path. */
    public static Packet<?> rewrite(ClientConnection connection, Packet<?> packet) {
        NoFall module = active;
        if (module == null || !module.isEnabled() || !(packet instanceof PlayerMoveC2SPacket movement)
                || !mc.isOnThread() || !module.syncSession() || module.session.getConnection() != connection
                || AirStuck.freezing(mc.player)) return packet;
        if (module.protecting && movement.isOnGround()) module.groundReports++;
        if (module.groundReports % 2 == 1) {
            module.player.setPosition(module.player.getX(), module.player.getY() + 1.0E-8, module.player.getZ());
        }
        Packet<?> replacement = new PlayerMoveC2SPacket.Full(module.player.getX(), module.player.getY(), module.player.getZ(),
                module.player.getYaw(), module.player.getPitch(), !module.protecting && module.player.isOnGround(), false);
        if (module.groundReports == 4) {
            module.protecting = false;
            module.groundReports = 0;
        }
        return replacement;
    }

    @Override public void onTick() { syncSession(); }

    public boolean protecting() { return protecting; }
}
