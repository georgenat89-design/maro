package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;

/** The existing custom M is a capital glyph, followed by literal lowercase "aro". */
public final class MaroWordmark {
    public enum Style {
        THEMED(8,12,112,104), BLUE(8,12,112,104), CONTOUR(2,1,124,126);
        final float left,top,width,height;
        Style(float left,float top,float width,float height){this.left=left;this.top=top;this.width=width;this.height=height;}
    }
    /** Visible ink bounds in the caller's GUI coordinates, including any .gg descenders. */
    public record Bounds(float x,float y,float width,float height,float capHeight){ }
    private record Layout(SmoothHudText.BrandMetrics text,float size,float markSize,float markWidth,float gap,float width,float height){ }
    private MaroWordmark(){ }

    private static Layout layout(DrawContext ctx,float capHeight,Style style,boolean domain){
        float size=Math.max(.1f,capHeight)/8;
        var text=SmoothHudText.brandMetrics(ctx,domain?"aro.gg":"aro",size);
        float markSize=text.capHeight()*128/style.height,markWidth=text.capHeight()*style.width/style.height;
        float gap=text.capHeight()*.04f;
        return new Layout(text,size,markSize,markWidth,gap,markWidth+gap+text.width(),
                Math.max(text.capHeight(),text.top()+text.height()));
    }
    public static float width(DrawContext ctx,float capHeight,Style style,boolean domain){
        return layout(ctx,capHeight,style,domain).width;
    }
    public static Bounds bounds(DrawContext ctx,float x,float capTop,float capHeight,Style style,boolean domain){
        var l=layout(ctx,capHeight,style,domain);return new Bounds(x,capTop,l.width,l.height,l.text.capHeight());
    }
    public static Bounds draw(DrawContext ctx,float x,float capTop,float capHeight,int color,Style style,boolean domain){
        return draw(ctx,x,capTop,capHeight,color,style,domain,0);
    }
    public static Bounds draw(DrawContext ctx,float x,float capTop,float capHeight,int color,Style style,boolean domain,float hover){
        var l=layout(ctx,capHeight,style,domain);
        // The complete name moves together; never lift the M away from its baseline.
        float lift=style==Style.CONTOUR?hover*1.4f:0,top=capTop-lift;
        float markX=x-style.left*l.markSize/128,markY=capTop-style.top*l.markSize/128;
        switch(style){
            case THEMED -> BootMark.drawThemed(ctx,markX,top-style.top*l.markSize/128,l.markSize,0xFFFFFFFF);
            case BLUE -> BootMark.draw(ctx,markX,top-style.top*l.markSize/128,l.markSize,0xFFFFFFFF);
            case CONTOUR -> MenuBrand.draw(ctx,markX,markY,l.markSize,hover);
        }
        SmoothHudText.drawBrand(ctx,domain?"aro.gg":"aro",x+l.markWidth+l.gap-l.text.left(),top,color,l.size);
        return new Bounds(x,top,l.width,l.height,l.text.capHeight());
    }
    public static Bounds centered(DrawContext ctx,float centerX,float capTop,float capHeight,int color,Style style,boolean domain){
        return draw(ctx,centerX-width(ctx,capHeight,style,domain)/2,capTop,capHeight,color,style,domain);
    }
}
