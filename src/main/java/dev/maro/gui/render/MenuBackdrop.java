package dev.maro.gui.render;
import net.minecraft.client.gui.DrawContext;
/** A quiet neutral canvas shared by home and loading. */
public final class MenuBackdrop {
    private MenuBackdrop(){}
    public static void draw(DrawContext ctx,int width,int height,float time){
        Render2D.rectGradient(ctx,0,0,width,height,0xFF11141B,0xFF11141B,0xFF090C11,0xFF090C11);
    }
}
