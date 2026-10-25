package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;
import java.util.Locale;

/** Classpath-backed vector branding and native-size text, available during initial loading. */
public final class BootAssets {
    private BootAssets() { }
    public static float labelWidth(String text,float size,boolean bold){
        float sample=size<4?32:size;
        return SmoothHudText.width(text,bold,sample*.73f/8,1)*size/sample;
    }
    public static void label(DrawContext ctx,String text,float x,float y,float size,int color,boolean bold){
        SmoothHudText.draw(ctx,text,x,y,color,bold,size*.73f/8);
    }
    public static void mark(DrawContext ctx, float x, float y, float size, int color) {
        BootMark.draw(ctx, x, y, size, color);
    }
    public static String caps(String text) { return text.toUpperCase(Locale.ROOT); }
    public static float width(String text, float size) {
        // Layout also requests size=1. Normalize a real font measurement so the
        // renderer's minimum physical font size cannot shrink fitted labels.
        float sample = size < 4 ? 32 : size;
        return SmoothHudText.width(caps(text), true, sample * .73f / 8, 1) * size / sample;
    }
    public static void text(DrawContext ctx, String text, float x, float y, float size, int color) {
        // Font bytes come directly from this JAR; no Minecraft font resource reload
        // is required. Initial startup uses the same pixel-aligned renderer as menus.
        SmoothHudText.draw(ctx, caps(text), x, y, color, true, size * .73f / 8);
    }
    public static void centered(DrawContext ctx, String text, float cx, float y, float size, int color) {
        float width = SmoothHudText.width(ctx, caps(text), true, size * .73f / 8);
        text(ctx, text, cx - width / 2, y, size, color);
    }
}
