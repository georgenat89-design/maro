package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.impl.visuals.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright: when the lightmap reads the Brightness option, it gets {@link Fullbright#gamma}
 * instead. Every other option it reads (darkness pulsing, for one) is passed through untouched.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object maro$fullbright(SimpleOption<?> option, Operation<Object> original) {
        Object value = original.call(option);
        if (option == MinecraftClient.getInstance().options.getGamma() && value instanceof Double gamma) {
            return Fullbright.gamma(gamma);
        }
        return value;
    }
}
