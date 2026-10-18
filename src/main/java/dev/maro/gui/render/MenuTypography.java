package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;

/** Menu-only Inter typography; restores the user's HUD and Click GUI font after every draw. */
public final class MenuTypography {
    private MenuTypography(){}
    public static float labelWidth(String text,float size,boolean bold){
        int previous=SmoothHudText.pushFamily(1);
        try{return BootAssets.labelWidth(text,size,bold);}finally{SmoothHudText.restoreFamily(previous);}
    }
    public static void label(DrawContext ctx,String text,float x,float y,float size,int color,boolean bold){
        int previous=SmoothHudText.pushFamily(1);
        try{BootAssets.label(ctx,text,x,y,size,color,bold);}finally{SmoothHudText.restoreFamily(previous);}
    }
}
