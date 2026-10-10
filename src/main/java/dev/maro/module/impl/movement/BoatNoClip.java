package dev.maro.module.impl.movement;

import dev.maro.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.ShapeContext;

/** Adapted from Anubis BoatNoclipModule. */
public final class BoatNoClip extends BoatControl {
    private final NumberSetting insideSpeed = add(new NumberSetting("Speed Inside Blocks", "Horizontal speed inside blocks or entities", 5, 0, 50, 0.5));
    public BoatNoClip() { super("Boat No Clip", "Fly your boat through blocks and entities; anti-cheat off only"); }
    @Override protected double horizontalSpeed(Entity boat) {
        var bounds = boat.getBoundingBox();
        var scan = bounds.offset(0, 0.05, 0).expand(0.5, 0, 0.5);
        for (BlockPos pos : BlockPos.iterate((int) Math.floor(scan.minX), (int) Math.floor(scan.minY), (int) Math.floor(scan.minZ),
            (int) Math.floor(scan.maxX), (int) Math.floor(scan.maxY), (int) Math.floor(scan.maxZ))) {
            var shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos, ShapeContext.absent());
            if (!shape.isEmpty() && bounds.intersects(shape.getBoundingBox().offset(pos))) return insideSpeed.get();
        }
        for (Entity other : mc.world.getOtherEntities(boat, scan)) {
            if (other != mc.player && !other.hasVehicle() && other.isAlive() && other.getBoundingBox().intersects(scan)) return insideSpeed.get();
        }
        return speed.get();
    }
}
