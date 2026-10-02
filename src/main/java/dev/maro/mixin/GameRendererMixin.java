package dev.maro.mixin;

import dev.maro.module.impl.visuals.StretchRes;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Swaps the window aspect ratio for the Stretch Res one when the 3D projection is built. */
    @ModifyArg(method = {"getBasicProjectionMatrix", "getProjectionMatrix"},
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"),
            index = 1, require = 1)
    private float maro$stretchAspect(float aspect) {
        StretchRes stretch = StretchRes.get();
        return stretch != null && stretch.isEnabled() ? stretch.targetAspect() : aspect;
    }
}
