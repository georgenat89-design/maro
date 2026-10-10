package dev.maro.module.impl.movement;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Shared Anubis boat movement and its bounded down/restore anti-floating sequence. */
public abstract class BoatControl extends Module {
    public static volatile BoatControl active;
    protected final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal blocks per second", 10, 0, 50, 0.5));
    protected final NumberSetting verticalSpeed = add(new NumberSetting("Vertical Speed", "Jump ascends; sprint descends", 6, 0, 20, 0.5));
    protected final NumberSetting fallSpeed = add(new NumberSetting("Fall Speed", "Downward drift when hovering", 0, 0, 10, 0.1));
    private final BooleanSetting antiKick = add(new BooleanSetting("Anti Fly Kick", "Periodically send a small downward movement and restore it", true).onChange(v -> forget()));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between downward movements", 40, 1, 80, 1).visible(antiKick::get));
    private Object player, world;
    private Entity vehicle;
    private int delayLeft;
    private double lastY = Double.NaN, restoreY = Double.NaN;

    protected BoatControl(String name, String description) { super(name, description, Category.ANTI_CHEAT_OFF); }
    @Override public boolean persistEnabled() { return false; }
    @Override protected void onEnable() {
        if (!inGame()) { setEnabled(false); return; }
        var previous = active;
        if (previous != null && previous != this) previous.setEnabled(false);
        player = mc.player; world = mc.world; vehicle = null;
        delayLeft = delay.getInt(); forget(); active = this;
    }
    @Override protected void onDisable() {
        if (active == this) active = null;
        releaseVehicle(); player = world = null; forget();
    }
    @Override public void onTick() {
        if (!inGame() || player != mc.player || world != mc.world || !mc.player.isAlive()) { setEnabled(false); return; }
        Entity ridden = mc.player.getVehicle();
        if (ridden != vehicle) { releaseVehicle(); vehicle = ridden; forget(); delayLeft = delay.getInt(); }
        if (!Double.isNaN(restoreY) && controls(vehicle) && mc.getNetworkHandler() != null) {
            var packet = VehicleMoveC2SPacket.fromVehicle(vehicle);
            double y = restoreY;
            restoreY = Double.NaN;
            mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(
                new Vec3d(packet.position().x, y, packet.position().z), packet.yaw(), packet.pitch(), packet.onGround()));
        }
        if (delayLeft > 0) delayLeft--;
    }
    protected void releaseVehicle() { if (vehicle != null && this instanceof BoatNoClip) vehicle.noClip = false; vehicle = null; }
    public boolean controls(Entity entity) {
        return entity != null && inGame() && entity.getEntityWorld() == mc.world
            && mc.player.getVehicle() == entity && entity.getControllingPassenger() == mc.player;
    }
    public void forget() { lastY = restoreY = Double.NaN; }
    public boolean cancelCorrection() { return false; }
    protected double horizontalSpeed(Entity entity) { return speed.get(); }

    public static Vec3d horizontal(double bps) {
        if (!inGame() || mc.currentScreen != null) return Vec3d.ZERO;
        var input = mc.player.input.playerInput;
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double sideways = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        if (forward != 0 && sideways != 0) { forward /= Math.sqrt(2); sideways /= Math.sqrt(2); }
        Vec3d ahead = Vec3d.fromPolar(0, mc.player.getYaw()), right = Vec3d.fromPolar(0, mc.player.getYaw() + 90);
        return ahead.multiply(forward * bps / 20).add(right.multiply(sideways * bps / 20));
    }
    private double vertical() {
        if (mc.currentScreen != null) return 0;
        double result = mc.options.jumpKey.isPressed() ? verticalSpeed.get() / 20 : 0;
        return result - (mc.options.sprintKey.isPressed() ? verticalSpeed.get() : fallSpeed.get()) / 20;
    }
    public static Vec3d movement(Entity entity, MovementType type, Vec3d original) {
        var module = active;
        if (module == null || type != MovementType.SELF || !module.controls(entity)) return original;
        if (module instanceof BoatNoClip && !(entity instanceof AbstractBoatEntity)) return original;
        if (module.vehicle != entity) { module.releaseVehicle(); module.vehicle = entity; module.forget(); module.delayLeft = module.delay.getInt(); }
        if (module instanceof BoatNoClip) entity.noClip = true;
        var horizontal = horizontal(module.horizontalSpeed(entity));
        var result = new Vec3d(horizontal.x, module.vertical(), horizontal.z);
        entity.setYaw(mc.player.getYaw());
        if (original == entity.getVelocity()) entity.setVelocity(result);
        return result;
    }
    public static boolean locksTurning(AbstractBoatEntity boat) {
        var module = active; return module != null && module.controls(boat);
    }
    public static boolean noClip(AbstractBoatEntity boat) {
        var module = active; return module instanceof BoatNoClip && module.controls(boat);
    }
    public static boolean onAir(Entity entity) {
        return entity.getEntityWorld().getStatesInBox(entity.getBoundingBox().expand(0.0625).offset(0, -0.55, 0))
            .allMatch(net.minecraft.block.BlockState::isAir);
    }
    public static Packet<?> rewrite(ClientConnection connection, Packet<?> original) {
        var module = active;
        if (module == null || !mc.isOnThread() || mc.getNetworkHandler() == null
            || connection != mc.getNetworkHandler().getConnection() || !(original instanceof VehicleMoveC2SPacket packet)
            || !module.antiKick.get() || !module.controls(mc.player.getVehicle())) return original;
        Entity ridden = mc.player.getVehicle();
        if (module.vehicle != ridden) { module.releaseVehicle(); module.vehicle = ridden; module.forget(); module.delayLeft = module.delay.getInt(); }
        double y = packet.position().y;
        boolean down = module.delayLeft <= 0 && Double.isNaN(module.restoreY) && !Double.isNaN(module.lastY)
            && y >= module.lastY - 0.0313 && onAir(ridden) && !ridden.isFlyingVehicle();
        double previous = module.lastY;
        module.lastY = y;
        if (!down) return original;
        module.restoreY = y; module.delayLeft = module.delay.getInt();
        return new VehicleMoveC2SPacket(new Vec3d(packet.position().x, previous - 0.0313, packet.position().z),
            packet.yaw(), packet.pitch(), packet.onGround());
    }
}
