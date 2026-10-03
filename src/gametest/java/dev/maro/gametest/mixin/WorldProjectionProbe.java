package dev.maro.gametest.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.gametest.StretchProjectionChecks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldProjectionProbe {
    @Inject(method = "render", at = @At("HEAD"))
    private void maroTest$world(ObjectAllocator allocator, RenderTickCounter tick, boolean outlines, Camera camera,
            Matrix4f view, Matrix4f projection, Matrix4f culling, GpuBufferSlice fog, Vector4f color, boolean sky, CallbackInfo info) {
        StretchProjectionChecks.worldProjection = new Matrix4f(projection);
        StretchProjectionChecks.worldView = new Matrix4f(view);
        StretchProjectionChecks.cullingProjection = new Matrix4f(culling);
        StretchProjectionChecks.worldUpload = StretchProjectionChecks.lastUpload;
    }
}
