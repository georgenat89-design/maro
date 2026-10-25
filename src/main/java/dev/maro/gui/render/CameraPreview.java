package dev.maro.gui.render;

import dev.maro.module.Module;
import dev.maro.module.impl.visuals.StretchRes;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import dev.maro.mixin.DrawContextAccessor;

/** A fixed comparison scene projected with the same horizontal scale as Stretch Res. */
public final class CameraPreview {
    private CameraPreview(){}
    private static double number(Module m,String key,double fallback){
        return EffectSamples.number(m,key,fallback);
    }
    public static float configuredFov(Module m){
        float base=MinecraftClient.getInstance().options.getFov().getValue();
        if(m instanceof dev.maro.nathan.modules.KeyZoom){double strength=Math.clamp(number(m,"initialzoom",.90),.10,.98);return (float)Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(base)/2)*(1-strength)));}
        return (float)Math.clamp(number(m,"fov",100),10,155);
    }
    public static float stretchScale(StretchRes m){var window=MinecraftClient.getInstance().getWindow();return ((float)window.getFramebufferWidth()/Math.max(1,window.getFramebufferHeight()))/m.targetAspect();}
    public static void draw(DrawContext ctx,Module module,float x,float y,float w,float h){
        boolean stretch=module instanceof StretchRes;float gap=8,half=(w-gap)/2,caption=14;
        float base=MinecraftClient.getInstance().options.getFov().getValue();
        panel(ctx,x,y,half,h-caption,base,1);
        panel(ctx,x+half+gap,y,half,h-caption,stretch?base:configuredFov(module),stretch?stretchScale((StretchRes)module):1);
        String right=stretch?String.format(java.util.Locale.ROOT,"Stretch %.2fx",stretchScale((StretchRes)module)):String.format(java.util.Locale.ROOT,"FOV %.0f°",configuredFov(module));
        // Each caption centred under its picture, on one line with the other.
        float line=y+h-4;
        Fonts.drawCentered(ctx,Fonts.trim(ctx,"Normal",half-4,false,.62f),x+half/2,line,0xFFAFBECE,false,.62f);
        Fonts.drawCentered(ctx,Fonts.trim(ctx,right,half-4,false,.62f),x+half+gap+half/2,line,0xFFB9DCFF,false,.62f);
    }
    private static float[] project(float x,float y,float z,float cx,float cy,float focal,float stretch){return new float[]{cx+x/z*focal*stretch,cy-(y-1.2f)/z*focal};}
    private static void quad(ShapeRenderState.Builder batch,float[][] p,int a,int b,int c,int d,int color){batch.quad(p[a][0],p[a][1],color,p[b][0],p[b][1],color,p[c][0],p[c][1],color,p[d][0],p[d][1],color);}
    private static void panel(DrawContext ctx,float x,float y,float w,float h,float fov,float stretch){
        Render2D.roundRect(ctx,x,y,w,h,3,0xFF172D46);
        Render2D.rect(ctx,x,y+h*.47f,w,h*.53f,0xFF243C31);
        float focal=(float)(h/(2*Math.tan(Math.toRadians(fov/2)))),cx=x+w/2,cy=y+h*.47f;
        var previousClip=Render2D.getScissor();
        var panelClip=new net.minecraft.client.gui.ScreenRect((int)Math.ceil(x),(int)Math.ceil(y),Math.max(1,(int)Math.floor(w)),Math.max(1,(int)Math.floor(h))).transformEachVertex(ctx.getMatrices());
        var clip=previousClip==null?panelClip:previousClip.intersection(panelClip);
        if(clip==null)return;
        ctx.enableScissor((int)Math.ceil(x),(int)Math.ceil(y),(int)Math.floor(x+w),(int)Math.floor(y+h));
        Render2D.setScissor(clip);
        try{
        var batch=new ShapeRenderState.Builder(ctx,400);
        for(int z=13;z>=3;z--)for(int xx=-5;xx<5;xx++){
            var p=new float[][]{project(xx,0,z,cx,cy,focal,stretch),project(xx+1,0,z,cx,cy,focal,stretch),project(xx+1,0,z+1,cx,cy,focal,stretch),project(xx,0,z+1,cx,cy,focal,stretch)};
            quad(batch,p,0,1,2,3,((xx+z)&1)==0?0xFF365647:0xFF294737);
        }
        for(int xx:new int[]{-1,1}){
            float left=xx*.8f-.35f,right=left+.7f;
            var p=new float[][]{project(left,0,4,cx,cy,focal,stretch),project(right,0,4,cx,cy,focal,stretch),project(right,1.4f,4,cx,cy,focal,stretch),project(left,1.4f,4,cx,cy,focal,stretch),project(left,0,4.7f,cx,cy,focal,stretch),project(right,0,4.7f,cx,cy,focal,stretch),project(right,1.4f,4.7f,cx,cy,focal,stretch),project(left,1.4f,4.7f,cx,cy,focal,stretch)};
            quad(batch,p,0,1,2,3,0xFF8FADD0);quad(batch,p,1,5,6,2,0xFF4F7299);quad(batch,p,3,2,6,7,0xFFBCD8F3);
        }
        var state=batch.build(Render2D.getScissor());if(state!=null)((DrawContextAccessor)ctx).maro$getState().addSimpleElement(state);
        }finally{Render2D.setScissor(previousClip);ctx.disableScissor();}
        Render2D.roundOutline(ctx,x,y,w,h,3,Render2D.px(ctx),0x6A51708F);
    }
}
