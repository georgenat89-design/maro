package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera-only spoofing leaves native entity aim, ray casts and movement packets intact. */
@Mixin(Camera.class)
public abstract class BuilderCameraMixin {
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("RETURN"))
    private void maro$builderCamera(World world,Entity entity,boolean thirdPerson,boolean mirrored,float partialTick,CallbackInfo info){
        if(thirdPerson||entity!=MinecraftClient.getInstance().player)return;
        var builder=ModuleManager.get(AutoBuilder.class);var look=builder==null?null:builder.builderCameraLook();
        if(look!=null)setRotation(look[0],look[1]);
    }
}
