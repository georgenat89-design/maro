package dev.maro.nathan.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.modules.MotionBlur;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;

/**
 * The matrices the world is drawn with this frame, handed to Motion Blur, which
 * works out from this frame's and last frame's where every pixel has moved.
 */
@Mixin(WorldRenderer.class)
public abstract class LevelMatricesMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void nameeprotect$levelMatrices(ObjectAllocator allocator, RenderTickCounter deltaTracker, boolean renderOutline, Camera camera,
                                            Matrix4f modelView, Matrix4f projection, Matrix4f cullingProjection, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        MotionBlur.matrices(modelView, projection, camera);
    }
}
