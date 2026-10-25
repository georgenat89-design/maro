package dev.maro.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.VisualPreview;
import dev.maro.gui.render.VisualPreviewEffect;
import dev.maro.module.impl.visuals.PlayerESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the marked Click GUI portrait uses this path; inventories and the world stay native. */
@Mixin(SpecialGuiElementRenderer.class)
public abstract class VisualPreviewMixin {
    @Shadow private GpuTextureView textureView;
    @Inject(method="renderElement",at=@At("HEAD"),cancellable=true)
    private void maro$portrait(SpecialGuiElementRenderState element,GuiRenderState state,CallbackInfo ci){
        if(!(element instanceof EntityGuiElementRenderState entity)||!(entity.renderState() instanceof PlayerEntityRenderState player)||player.id!=VisualPreview.ENTITY_ID
            ||!(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui))return;
        var preview=gui.visualPreview();GpuTextureView view=textureView;
        if(preview.module() instanceof PlayerESP esp)view=VisualPreviewEffect.apply(view,esp,preview.playbackTime());
        int alpha=Math.clamp(Math.round(preview.opacity()*255),0,255);
        state.addSimpleElementToCurrentLayer(new TexturedQuadGuiElementRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.of(view,RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),element.pose(),
            element.x1(),element.y1(),element.x2(),element.y2(),0,1,1,0,alpha<<24|alpha<<16|alpha<<8|alpha,element.scissorArea(),null));
        ci.cancel();
    }
}
