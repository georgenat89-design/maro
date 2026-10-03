package dev.maro.runtime.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
public final class Rotations {
 public static double getYaw(Vec3d point){var player=MinecraftClient.getInstance().player;Vec3d delta=point.subtract(player.getEyePos());return Math.toDegrees(Math.atan2(delta.z,delta.x))-90;}
 public static double getPitch(Vec3d point){Vec3d delta=point.subtract(MinecraftClient.getInstance().player.getEyePos());return -Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));}
}
