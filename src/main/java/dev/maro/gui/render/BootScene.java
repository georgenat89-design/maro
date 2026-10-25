package dev.maro.gui.render;
import net.minecraft.client.gui.DrawContext;
/** Honest loading progress and a stable background. Vanilla owns completion. */
public final class BootScene {
    private static final long ORIGIN=System.nanoTime();
    public static final int BLUE=0xFF73AFFF,CYAN=0xFF91BEFA,WHITE=0xFFF0F2F6;
    private BootScene(){}
    public static float time(){return (System.nanoTime()-ORIGIN)/1e9f;}
    public static float scale(int w,int h){return Math.max(.01f,Math.min(w/960f,h/540f)*.90f);}
    public static void background(DrawContext ctx,int w,int h,float t){
        if(!dev.maro.gui.MaroHomeScreen.embeddedRendering())MenuBackdrop.draw(ctx,w,h,t);
    }
    public static void emblem(DrawContext ctx,float x,float y,float size,float fill,float time){BootAssets.mark(ctx,x-size/2,y-size/2,size,WHITE);}
    public static void loading(DrawContext ctx,int w,int h,float fill,String status,float alpha){loading(ctx,w,h,fill,status,alpha,true);}
    public static void loading(DrawContext ctx,int w,int h,float fill,String status,float alpha,boolean fillMark){
        if(dev.maro.render.capture.CapturePass.active())return;
        float old=Render2D.getAlpha();Render2D.setAlpha(alpha);var m=ctx.getMatrices();m.pushMatrix();
        try{
            background(ctx,w,h,time());
            float s=scale(w,h);m.translate((w-960*s)/2,(h-540*s)/2);m.scale(s,s);
            MaroWordmark.centered(ctx,480,170,48,WHITE,MaroWordmark.Style.BLUE,true);
            String message=status==null||status.isBlank()?"Getting things ready":status;
            float size=Math.min(14,380/Math.max(1,BootAssets.labelWidth(message,1,false)));
            ctx.createNewRootLayer();
            BootAssets.label(ctx,message,480-BootAssets.labelWidth(message,size,false)/2,276,size,0xFFA9B1BD,false);
            float x=290,y=312,bar=380;
            Render2D.roundRect(ctx,x,y,bar,4,2,0xFF29303B);
            if(Float.isFinite(fill)&&fill>=0){
                float p=Math.max(0,Math.min(1,fill));if(p>0)Render2D.roundRect(ctx,x,y,Math.max(2,bar*p),4,2,BLUE);
                String percent=Math.round(p*100)+"%";BootAssets.label(ctx,percent,x+bar-BootAssets.labelWidth(percent,11,false),333,11,0xFF8F99A8,false);
            }else{
                float phase=(float)((Math.sin(time()*1.7-Math.PI/2)+1)/2);
                Render2D.roundRect(ctx,x+(bar-72)*phase,y,72,4,2,BLUE);
            }
            BootAssets.label(ctx,"Minecraft 1.21.11",54,490,11,0xFF626D7D,false);
            String community="discord.gg/maro";BootAssets.label(ctx,community,906-BootAssets.labelWidth(community,11,false),490,11,0xFF626D7D,false);
        }finally{m.popMatrix();Render2D.setAlpha(old);}
    }
}
