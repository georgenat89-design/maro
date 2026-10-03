package dev.maro.gametest.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.gametest.StretchProjectionChecks;
import net.minecraft.client.render.RawProjectionMatrix;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the actual matrix sent to the GPU, only in the test mod. */
@Mixin(RawProjectionMatrix.class)
public abstract class ProjectionUploadProbe {
    @Inject(method = "set", at = @At("HEAD"))
    private void maroTest$upload(Matrix4f projection, CallbackInfoReturnable<GpuBufferSlice> info) {
        StretchProjectionChecks.lastUpload = new Matrix4f(projection);
    }
}
