package dev.maro.anubis;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
public final class RotationUtils {
    public static Rotation fromTo(Vec3d from,Vec3d to) { var d=to.subtract(from);return new Rotation((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))); }
    public static Rotation entityRotation(Entity entity) { return new Rotation(entity.getYaw(),entity.getPitch()); }
}
