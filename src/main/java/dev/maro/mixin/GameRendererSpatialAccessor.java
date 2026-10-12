package dev.maro.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What the Spatial menu needs from the game renderer to draw the menu off screen and match the world's view. */
@Mixin(GameRenderer.class)
public interface GameRendererSpatialAccessor {
    @Accessor("fogRenderer")
    FogRenderer maro$fogRenderer();

    @Accessor("orderedRenderCommandQueue")
    OrderedRenderCommandQueueImpl maro$commandQueue();

    @Accessor("guiRenderer")
    GuiRenderer maro$guiRenderer();

    @Invoker("getFov")
    float maro$fov(Camera camera, float tickProgress, boolean changingFov);
}
