package dev.maro.mixin;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class BuilderUseMixin {
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void maro$builderUse(CallbackInfo info){if(AutoBuilder.consumesUse())info.cancel();}
}
