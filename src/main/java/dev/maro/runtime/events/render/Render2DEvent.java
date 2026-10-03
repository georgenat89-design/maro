package dev.maro.runtime.events.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
public final class Render2DEvent {
 public final DrawContext drawContext; public final double frameTime; public final int screenWidth, screenHeight;
 public Render2DEvent(DrawContext context, double seconds) { drawContext = context; frameTime = seconds; var w = MinecraftClient.getInstance().getWindow(); screenWidth = w.getScaledWidth(); screenHeight = w.getScaledHeight(); }
}
