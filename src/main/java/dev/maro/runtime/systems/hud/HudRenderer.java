package dev.maro.runtime.systems.hud;

import dev.maro.runtime.renderer.Renderer2D;
import net.minecraft.client.gui.DrawContext;
public final class HudRenderer {
 public static final HudRenderer INSTANCE=new HudRenderer();
 public void begin(DrawContext context){Renderer2D.context(context);Renderer2D.COLOR.begin();}
 public void end(){Renderer2D.COLOR.render();}
}
