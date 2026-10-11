package dev.maro.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.render.sky.CustomSkyRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The hooks behind {@link CustomSky}. */
public final class CustomSkyMixins {
    private CustomSkyMixins() {
    }

    /**
     * The game's sky pass. The dome is swapped for the custom sky; the sunrise glow, the darker
     * lower half, the stars and (unless kept) the sun and moon are left out.
     */
    @Mixin(SkyRendering.class)
    public abstract static class Sky {
        @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
        private void maro$sky(CallbackInfo ci) {
            if (CustomSky.active() && CustomSkyRenderer.draw()) ci.cancel();
        }

        @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
        private void maro$skyDark(CallbackInfo ci) {
            if (CustomSkyRenderer.drewLastFrame()) ci.cancel();
        }

        @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
        private void maro$sunrise(CallbackInfo ci) {
            if (CustomSkyRenderer.drewLastFrame()) ci.cancel();
        }

        @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
        private void maro$sunAndMoon(CallbackInfo ci) {
            if (CustomSkyRenderer.drewLastFrame() && !CustomSky.keepsSunAndMoon()) ci.cancel();
        }

        @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
        private void maro$stars(CallbackInfo ci) {
            if (CustomSkyRenderer.drewLastFrame()) ci.cancel();
        }

        @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
        private void maro$endSky(CallbackInfo ci) {
            if (CustomSky.activeInEnd() && CustomSkyRenderer.draw()) ci.cancel();
        }
    }

    /** The view the sky is seen through, and the clouds. */
    @Mixin(WorldRenderer.class)
    public abstract static class World {
        @Inject(method = "render", at = @At("HEAD"))
        private void maro$skyMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
                                      Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjection, GpuBufferSlice fog,
                                      Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
            CustomSkyRenderer.beginFrame(positionMatrix, projectionMatrix);
        }

        @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
        private void maro$clouds(CallbackInfo ci) {
            if (CustomSky.hidesClouds()) ci.cancel();
        }
    }

    /** Far-away land fades into the custom sky's colour rather than the normal sky's. */
    @Mixin(FogRenderer.class)
    public abstract static class Fog {
        @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
        private void maro$fogColor(CallbackInfoReturnable<Vector4f> cir) {
            Vector4f tinted = CustomSky.fogColor(cir.getReturnValue());
            if (tinted != null) cir.setReturnValue(tinted);
        }
    }
}
