package dev.maro.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The game's picture-in-picture renderers (player models, banners...), shared with the Spatial menu's renderer. */
@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Accessor("specialElementRenderers")
    Map<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> maro$specialRenderers();
}
