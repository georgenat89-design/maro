package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;

/** Main-menu labels share the centralized client face and exact glyph measurements. */
public final class MenuTypography {
    private MenuTypography(){}
    public static float labelWidth(String text,float size,boolean bold){
        return BootAssets.labelWidth(text,size,bold);
    }
    public static void label(DrawContext ctx,String text,float x,float y,float size,int color,boolean bold){
        BootAssets.label(ctx,text,x,y,size,color,bold);
    }
}
