package dev.maro.mixin;

import dev.maro.gui.spatial.SpatialCanvas;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The Spatial menu's own GUI renderer draws into its canvas instead of the screen. */
@Mixin(GuiRenderer.class)
public class GuiRendererSpatialMixin {
    @ModifyVariable(method = "render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V",
            at = @At("HEAD"), argsOnly = true)
    private Framebuffer maro$spatialTarget(Framebuffer framebuffer) {
        return SpatialCanvas.target((GuiRenderer) (Object) this, framebuffer);
    }
}
