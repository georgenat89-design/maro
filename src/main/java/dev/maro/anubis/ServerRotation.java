package dev.maro.anubis;
import net.minecraft.client.MinecraftClient;
import dev.maro.mixin.ClientPlayerLookAccessor;
public final class ServerRotation {
    public static void request(Rotation rotation) { var p=MinecraftClient.getInstance().player;if(p!=null){p.setYaw(rotation.yaw());p.setPitch(rotation.pitch());} }
    public static Rotation sent() { var p=MinecraftClient.getInstance().player;if(p==null)return null;var look=(ClientPlayerLookAccessor)p;return new Rotation(look.maro$lastSentYaw(),look.maro$lastSentPitch()); }
}
