package dev.maro.gui.render;

import dev.maro.mixin.DrawContextAccessor;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.List;

/** A clean, symmetric vector monogram, shared by startup and menu branding. */
final class BootMark {
    // A single outline keeps the diagonals seamless at fractional GUI scales.
    private static final float[][] SHAPES = {
        {8,12, 35,12, 64,56, 93,12, 120,12, 120,116, 94,116, 94,59, 64,103, 34,59, 34,116, 8,116}
    };
    private static final List<float[]> TRIANGLES = new ArrayList<>();
    static {
        for (float[] shape : SHAPES) triangulate(shape);
    }
    private static float cross(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }
    private static void triangulate(float[] shape) {
        var points = new ArrayList<Integer>();
        for (int i = 0; i < shape.length; i += 2) points.add(i);
        while (points.size() > 2) {
            boolean found = false;
            for (int j = 0; j < points.size(); j++) {
                int a = points.get((j + points.size() - 1) % points.size()), b = points.get(j), c = points.get((j + 1) % points.size());
                if (cross(shape[a], shape[a+1], shape[b], shape[b+1], shape[c], shape[c+1]) <= 0) continue;
                boolean inside = false;
                for (int p : points) if (p != a && p != b && p != c
                        && cross(shape[a],shape[a+1],shape[b],shape[b+1],shape[p],shape[p+1]) >= 0
                        && cross(shape[b],shape[b+1],shape[c],shape[c+1],shape[p],shape[p+1]) >= 0
                        && cross(shape[c],shape[c+1],shape[a],shape[a+1],shape[p],shape[p+1]) >= 0) { inside = true; break; }
                if (inside) continue;
                TRIANGLES.add(new float[]{shape[a],shape[a+1],shape[b],shape[b+1],shape[c],shape[c+1]});
                points.remove(j); found = true; break;
            }
            if (!found) throw new IllegalStateException("Invalid Maro outline");
        }
    }
    private static int tint(int color, int tint) {
        return ColorUtil.argb(Math.round(ColorUtil.alpha(tint) * Render2D.getAlpha()),
                ColorUtil.red(color)*ColorUtil.red(tint)/255, ColorUtil.green(color)*ColorUtil.green(tint)/255,
                ColorUtil.blue(color)*ColorUtil.blue(tint)/255);
    }
    private static int gradient(float y, int top, int bottom, int tint) {
        return tint(ColorUtil.lerp(top, bottom, y / 128), tint);
    }
    static void draw(DrawContext ctx, float x, float y, float size, int tint) {
        draw(ctx,x,y,size,tint,0xFF82E7FF,0xFF388BF5);
    }
    static void drawThemed(DrawContext ctx, float x, float y, float size, int tint) {
        Theme.update();
        draw(ctx,x,y,size,tint,ColorUtil.shade(Theme.accent(),.32f),Theme.accent2());
    }
    private static void draw(DrawContext ctx,float x,float y,float size,int tint,int top,int bottom){
        float s = size / 128;
        var batch = new ShapeRenderState.Builder(ctx, TRIANGLES.size()*4);
        for (float[] t : TRIANGLES) batch.quad(x+t[0]*s,y+t[1]*s,gradient(t[1],top,bottom,tint),
                x+t[2]*s,y+t[3]*s,gradient(t[3],top,bottom,tint),x+t[4]*s,y+t[5]*s,gradient(t[5],top,bottom,tint),
                x+t[4]*s,y+t[5]*s,gradient(t[5],top,bottom,tint));
        var state=batch.build(Render2D.getScissor());
        if(state!=null) ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(state);
        for(float[] shape:SHAPES) for(int i=0;i<shape.length;i+=2) {
            int j=(i+2)%shape.length;
            Render2D.line(ctx,x+shape[i]*s,y+shape[i+1]*s,x+shape[j]*s,y+shape[j+1]*s,
                    .65f*s,ColorUtil.mulAlpha(gradient(shape[i+1],top,bottom,tint),.8f));
        }
    }
}
