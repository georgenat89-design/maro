package dev.maro.module.impl.movement;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.mixin.FlightPlayerAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/** Meteor Flight: abilities/velocity modes, vertical matching and packet/normal anti-floating. */
public final class Flight extends Module {
    public static volatile Flight active;
    private final ModeSetting mode = add(new ModeSetting("Mode", "How flight controls your movement", "Abilities", "Abilities", "Velocity").onChange(v -> restoreAbilities()));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Flight speed", 0.1, 0, 1, 0.01));
    private final BooleanSetting verticalMatch = add(new BooleanSetting("Vertical Speed Match", "Match vertical and horizontal speed", false));
    private final BooleanSetting noSneak = add(new BooleanSetting("No Sneak", "Descend without the sneaking animation", false).visible(() -> mode.is("Velocity")));
    private final ModeSetting antiKick = add(new ModeSetting("Anti Kick", "Periodic downward movement", "Packet", "Normal", "Packet", "None"));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between downward movements", 20, 1, 200, 1).visible(() -> !antiKick.is("None")));
    private final NumberSetting offTime = add(new NumberSetting("Off Time", "Ticks spent descending", 1, 1, 20, 1).visible(() -> !antiKick.is("None")));
    private ClientPlayerEntity player;
    private Object world;
    private boolean oldFlying, oldAllow, abilitiesChanged, flip;
    private float oldSpeed, lastYaw;
    private int delayLeft, offLeft;
    private double lastY = Double.NaN;

    public Flight() { super("Flight", "Meteor flight modes; use on servers with anti-cheat switched off", Category.ANTI_CHEAT_OFF); }
    @Override public boolean persistEnabled() { return false; }
    @Override protected void onEnable() {
        if (!inGame()) { setEnabled(false); return; }
        player = mc.player; world = mc.world;
        var abilities = player.getAbilities();
        oldFlying = abilities.flying; oldAllow = abilities.allowFlying; oldSpeed = abilities.getFlySpeed();
        delayLeft = delay.getInt(); offLeft = offTime.getInt(); lastY = Double.NaN; lastYaw = player.getYaw();
        active = this; applyAbilities();
        if (mode.is("Abilities")) player.setVelocity(player.getVelocity().multiply(1, 0, 1));
    }
    @Override protected void onDisable() {
        if (active == this) active = null;
        restoreAbilities(); player = null; world = null; lastY = Double.NaN;
    }
    private boolean current() { return player != null && player == mc.player && world == mc.world && player.isAlive(); }
    private void applyAbilities() {
        if (!current() || !mode.is("Abilities") || player.isSpectator()) return;
        var abilities = player.getAbilities(); abilitiesChanged = true;
        abilities.flying = true; abilities.allowFlying = true; abilities.setFlySpeed(speed.get().floatValue());
    }
    private void restoreAbilities() {
        if (player != null && abilitiesChanged) {
            var abilities = player.getAbilities();
            abilities.flying = oldFlying; abilities.allowFlying = oldAllow; abilities.setFlySpeed(oldSpeed);
        }
        abilitiesChanged = false;
    }
    public static void beforeTick() {
        var m = active;
        if (m == null || !m.current() || AirStuck.freezing(mc.player)) return;
        float yaw = mc.player.getYaw();
        if (mc.player.fallDistance >= 3 && yaw == m.lastYaw && mc.player.getVelocity().length() < 0.003) {
            mc.player.setYaw(yaw + (m.flip ? 1 : -1)); m.flip = !m.flip;
        }
        m.lastYaw = yaw;
    }
    @Override public void onTick() {
        if (!current()) { setEnabled(false); return; }
        if (AirStuck.freezing(player) || player.hasVehicle()) return;
        if (delayLeft > 0) delayLeft--;
        if (offLeft <= 0 && delayLeft <= 0) {
            delayLeft = delay.getInt(); offLeft = offTime.getInt();
            if (antiKick.is("Packet")) ((FlightPlayerAccessor) player).maro$positionAge(20);
        } else if (delayLeft <= 0) {
            if (antiKick.is("Normal") && mode.is("Abilities")) { restoreAbilities(); offLeft--; return; }
            if (antiKick.is("Packet") && offLeft == offTime.getInt()) ((FlightPlayerAccessor) player).maro$positionAge(20);
            offLeft--;
        }
        if (player.getYaw() != lastYaw) player.setYaw(lastYaw);
        if (mode.is("Abilities")) applyAbilities();
        else {
            // The off-ground speed mixin applies horizontal input as in Meteor.
            double vertical = speed.get() * (verticalMatch.get() ? 10 : 5);
            double y = (mc.currentScreen == null && mc.options.jumpKey.isPressed() ? vertical : 0)
                - (mc.currentScreen == null && mc.options.sneakKey.isPressed() ? vertical : 0);
            player.setVelocity(0, y, 0);
            if (noSneak.get()) player.setSneaking(false);
        }
    }
    public static float offGroundSpeed(ClientPlayerEntity user, float original) {
        var m = active;
        return m != null && m.current() && user == m.player && m.mode.is("Velocity") && !user.hasVehicle()
            ? m.speed.get().floatValue() * (user.isSprinting() ? 15 : 10) : original;
    }
    public static boolean hideSneak(ClientPlayerEntity user) {
        var m = active; return m != null && m.current() && user == m.player && m.mode.is("Velocity") && m.noSneak.get();
    }
    public static void abilitiesUpdated() {
        var m = active;
        if (m == null || !m.current()) return;
        // Retain the server's legitimate permissions for disable, then reapply this mode.
        var abilities = m.player.getAbilities();
        m.oldFlying = abilities.flying; m.oldAllow = abilities.allowFlying; m.oldSpeed = abilities.getFlySpeed();
        m.abilitiesChanged = false; m.applyAbilities();
    }
    public static Packet<?> rewrite(ClientConnection connection, Packet<?> original) {
        var m = active;
        if (m == null || !m.current() || !mc.isOnThread() || mc.getNetworkHandler() == null
            || connection != mc.getNetworkHandler().getConnection() || m.player.hasVehicle()
            || !m.antiKick.is("Packet") || noFallProtecting() || !(original instanceof PlayerMoveC2SPacket packet)) return original;
        double y = packet.getY(m.player.getY());
        boolean lower = m.delayLeft <= 0 && !Double.isNaN(m.lastY) && y >= m.lastY - 0.0313 && BoatControl.onAir(m.player);
        double sentY = lower ? m.lastY - 0.0313 : y;
        if (!lower) m.lastY = y;
        if (packet.changesLook()) return new PlayerMoveC2SPacket.Full(packet.getX(m.player.getX()), sentY, packet.getZ(m.player.getZ()),
            packet.getYaw(m.player.getYaw()), packet.getPitch(m.player.getPitch()), packet.isOnGround(), packet.horizontalCollision());
        return new PlayerMoveC2SPacket.PositionAndOnGround(packet.getX(m.player.getX()), sentY, packet.getZ(m.player.getZ()),
            packet.isOnGround(), packet.horizontalCollision());
    }
    private static boolean noFallProtecting() {
        var module = dev.maro.module.ModuleManager.get(NoFall.class);
        return module != null && module.protecting();
    }
}
