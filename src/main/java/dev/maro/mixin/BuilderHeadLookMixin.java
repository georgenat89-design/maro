package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class BuilderHeadLookMixin {
    @Inject(method="changeLookDirection",at=@At("HEAD"),cancellable=true)
    private void maro$builderMouseLook(double x,double y,CallbackInfo info){
        if((Object)this!=MinecraftClient.getInstance().player)return;
        var builder=ModuleManager.get(AutoBuilder.class);if(builder!=null&&builder.turnBuilderCamera(x,y))info.cancel();
    }
}
