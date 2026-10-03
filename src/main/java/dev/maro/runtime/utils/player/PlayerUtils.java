package dev.maro.runtime.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
public final class PlayerUtils {
 public static double squaredDistanceTo(BlockPos pos){var p=MinecraftClient.getInstance().player;return p==null?Double.POSITIVE_INFINITY:p.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(pos));}
 public static double squaredDistanceTo(Entity entity){var p=MinecraftClient.getInstance().player;return p==null?Double.POSITIVE_INFINITY:p.squaredDistanceTo(entity);}
 public static double distanceTo(BlockPos pos){return Math.sqrt(squaredDistanceTo(pos));}
 public static double distanceTo(Entity entity){return Math.sqrt(squaredDistanceTo(entity));}
}
