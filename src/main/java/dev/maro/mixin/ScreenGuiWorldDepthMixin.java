package dev.maro.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.gui.render.ScreenGuiSurface;
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

/** Preserve the terrain's depth before GameRenderer clears it for first-person hands. */
@Mixin(WorldRenderer.class)
public abstract class ScreenGuiWorldDepthMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void maro$screenGuiProjection(ObjectAllocator allocator, RenderTickCounter ticks, boolean blockOutline,
                                         Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullingProjection,
                                         GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        ScreenGuiSurface.worldBegin(camera, view, projection);
        dev.maro.gui.render.HandPlacementRender.worldBegin(view);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void maro$screenGuiTerrainDepth(CallbackInfo ci) { ScreenGuiSurface.worldEnd();dev.maro.gui.render.HandPlacementRender.worldEnd(); }
}
