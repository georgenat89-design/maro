package dev.maro.gui.render;

import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

/** The owner's original contour logo, using the existing transparent brand asset. */
public final class MenuBrand {
    private MenuBrand(){}
    public static void draw(DrawContext ctx,float x,float y,float size,float hover){
        float lift=hover*1.4f;y-=lift;
        Render2D.shadow(ctx,x+size*.22f,y+size*.22f,size*.56f,size*.56f,size*.28f,size*.18f,ColorUtil.withAlpha(0x3391FC,Math.round(12+hover*18)));
        int side=Math.max(1,Math.round(size));
        ctx.getMatrices().pushMatrix();
        try{
            ctx.getMatrices().translate(x,y);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED,Logo.MARK,0,0,0,0,side,side,128,128,128,128,ColorUtil.mulAlpha(0xFFFFFFFF,Render2D.getAlpha()));
        }finally{ctx.getMatrices().popMatrix();}
    }
}
