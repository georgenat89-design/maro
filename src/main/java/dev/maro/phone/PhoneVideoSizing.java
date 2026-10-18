package dev.maro.phone;

/** Match visible device pixels with a small sampling margin and a bounded total pixel budget. */
public final class PhoneVideoSizing {
    public record Size(int width,int height){}
    private PhoneVideoSizing(){}
    /** A portrait browser viewport keeps the complete TikTok player visible on landscape tablets. */
    public static Size feedViewport(float width,float height,double scale){
        Size fitted=viewport(width,height,scale);
        // TikTok's feed has a 492 CSS-pixel minimum layout; a narrower visual viewport clips its player.
        int w=Math.max(512,Math.min(720,(int)Math.ceil(fitted.height()*9d/16/32)*32));
        return new Size(w,w*16/9);
    }
    public static Size viewport(float width,float height,double scale){
        if(!Float.isFinite(width)||!Float.isFinite(height)||!Double.isFinite(scale)||width<=0||height<=0||scale<=0)
            return new Size(360,640);
        double ratio=Math.clamp(height/width,1/16d,16d);
        double target=Math.max(360,Math.ceil(width*scale*1.15/32)*32);
        target=Math.min(target,Math.min(960,Math.min(1440/ratio,Math.sqrt(921600/ratio))));
        int w=Math.max(2,((int)target)/2*2),h=Math.max(2,((int)(w*ratio))/2*2);
        return new Size(w,h);
    }
}
