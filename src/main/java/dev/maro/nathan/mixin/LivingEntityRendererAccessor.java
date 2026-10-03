package dev.maro.nathan.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code addLayer} is protected on the renderer, and a feature renderer has to go
 * on through it. Raw, because the descriptor is what is matched and the generics
 * are erased out of it anyway.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Invoker("addFeature")
    @SuppressWarnings("rawtypes")
    boolean nameeprotect$addLayer(FeatureRenderer layer);
}
