package dev.maro.gui.render;

import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** The shared clean M in the title's original blue gradient and hover treatment. */
public final class MenuBrand {
    private MenuBrand(){}
    public static void draw(DrawContext ctx,float x,float y,float size,float hover){
        float lift=hover*1.4f;y-=lift;
        Render2D.shadow(ctx,x+size*.22f,y+size*.22f,size*.56f,size*.56f,size*.28f,size*.18f,ColorUtil.withAlpha(0x3391FC,Math.round(12+hover*18)));
        BootMark.draw(ctx,x,y,size,0xFFFFFFFF);
    }
}
