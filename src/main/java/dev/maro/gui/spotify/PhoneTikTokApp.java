package dev.maro.gui.spotify;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.config.ConfigManager;
import dev.maro.phone.browser.PhoneWebBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Util;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real TikTok pages rendered by an isolated installed Edge/Chrome browser. */
final class PhoneTikTokApp implements AutoCloseable {
    enum Feed {
        FOR_YOU("For You", "/foryou"), ENTERTAINMENT("Entertainment", "/tag/funny"), GAMING("Gaming", "/tag/gaming");
        final String label, path;
        Feed(String label, String path) { this.label = label; this.path = path; }
    }
    private record Hit(float x,float y,float w,float h,Runnable action) {
        boolean contains(float mx,float my) { return mx>=x && my>=y && mx<x+w && my<y+h; }
    }
    private static final int WHITE=0xFFF5F7F6, DIM=0xFFA3ADA7, CYAN=0xFF58E5E1;
    private static final URI INSTALL=URI.create("https://www.google.com/chrome/");
    private final List<Hit> hits=new ArrayList<>();
    private final dev.maro.module.impl.misc.SpotifyPhone owner;
    private PhoneWebBrowser browser;
    private Path profile=ConfigManager.DIR.resolve("tiktok-browser");
    private NativeImageBackedTexture texture;
    private final Identifier textureId=Identifier.of("maro","phone_tiktok/"+java.util.UUID.randomUUID());
    private long shownFrame;
    private PhoneWebBrowser.Frame shownData;
    // A local origin is injected by the native fixture before opening; production always uses TikTok.
    private URI origin=URI.create("https://www.tiktok.com");
    private Feed feed=Feed.FOR_YOU;
    private boolean opened, focused, pressed, failed, feedsOpen, menuOpen;
    private long loadingAt, lastStep;
    private final FeedWheel wheel=new FeedWheel();
    private boolean videoFocus=true;
    private float drawX,drawY,drawW,drawH;
    private Runnable localVideos = () -> { };
    void onLocalVideos(Runnable action) { localVideos = action; }
    private float x=20,y=141,w=168,h=296;
    private int pixelsW=360,pixelsH=634;
    PhoneTikTokApp(dev.maro.module.impl.misc.SpotifyPhone owner){this.owner=owner;}
    private dev.maro.setting.ModeSetting framing(){return owner.device.is("iPad")?owner.tabletVideoFraming:owner.videoFraming;}

    void open() { opened=true; failed=false; loadingAt=System.nanoTime(); lastStep=0;wheel.reset(); }
    boolean typing() { return focused; }
    private String url() { return origin.resolve(feed.path).toString(); }
    private void choose(Feed next) { feed=next; feedsOpen=false; blur(); wheel.reset(); loadingAt=System.nanoTime(); if(browser!=null)browser.navigate(url()); }
    private void blur() {
        if(browser!=null) { if(pressed)browser.release(0,0); browser.focus(false); }
        focused=false; pressed=false;
    }
    void stop() {
        opened=false; feedsOpen=menuOpen=false; blur();wheel.reset();
        if(browser!=null) { browser.close(); browser=null; }
        destroyTexture();
        hits.clear();
    }
    void render(DrawContext ctx,float width,float height,float mx,float my) {
        hits.clear(); x=8; y=106; w=width-16; h=height-y-52;
        var matrix=ctx.getMatrices();
        double scale=Math.hypot(matrix.m00(),matrix.m01())*MinecraftClient.getInstance().getWindow().getScaleFactor();
        var size=dev.maro.phone.PhoneVideoSizing.feedViewport(w,h,scale);
        int nextW=size.width(),nextH=size.height();
        try {
            if(opened && browser==null && !failed) {
                browser=PhoneWebBrowser.create(url(),nextW,nextH,profile);
                if(browser!=null)browser.videoFocus(videoFocus && origin.getHost().endsWith("tiktok.com"));
            }
            if(browser!=null && (pixelsW!=nextW || pixelsH!=nextH))browser.resize(nextW,nextH);
        } catch(RuntimeException | LinkageError ex) { failed=true; if(browser!=null) {browser.close();browser=null;} destroyTexture(); }
        pixelsW=nextW; pixelsH=nextH;
        text(ctx,"TikTok",18,55,WHITE,1.05f,width-65);
        button(ctx,12,77,width-112,23,feed.label+" ▾",()->{feedsOpen=!feedsOpen;menuOpen=false;},false);
        button(ctx,width-88,77,40,23,framing().get(),()->{framing().set(framing().is("Fill")?"Fit":"Fill");ConfigManager.saveAll();},false);
        button(ctx,width-42,77,30,23,"···",()->{menuOpen=!menuOpen;feedsOpen=false;},false);
        Render2D.roundRect(ctx,x,y,w,h,8,0xFF080B0D);
        upload();
        if(texture!=null) {
            var image=texture.getImage();
            boolean fill=videoFocus&&shownData!=null&&shownData.focusedMedia()&&framing().is("Fill");
            float fit=fill?Math.max(w/image.getWidth(),h/image.getHeight()):Math.min(w/image.getWidth(),h/image.getHeight());
            drawW=image.getWidth()*fit;drawH=image.getHeight()*fit;drawX=x+(w-drawW)/2;drawY=y+(h-drawH)/2;
            ctx.createNewRootLayer();Render2D.clip(ctx,Math.round(x),Math.round(y),Math.round(x+w),Math.round(y+h));
            try {ctx.drawTexture(RenderPipelines.GUI_TEXTURED,textureId,Math.round(drawX),Math.round(drawY),0,0,
                    Math.round(drawW),Math.round(drawH),image.getWidth(),image.getHeight(),image.getWidth(),image.getHeight());}
            finally {Render2D.unclip(ctx);ctx.createNewRootLayer();}
            if(browser!=null && contains(mx,my) && !feedsOpen && !menuOpen)browser.move(bx(mx),by(my));
        }
        boolean slow = System.nanoTime()-loadingAt>20_000_000_000L && (browser==null || browser.loading());
        if(texture==null || slow || browser!=null&&!browser.status().isBlank()) {
            float cy=y+Math.max(22,h*.35f);
            Render2D.roundRect(ctx,x+7,cy-12,w-14,slow||failed?118:100,10,0xEE111B23);
            String title=!PhoneWebBrowser.available()?"A browser is needed":failed?"Could not open TikTok":slow?"Still loading TikTok":"Loading your feed…";
            text(ctx,title,x+17,cy,WHITE,.95f,w-34);
            String detail=!PhoneWebBrowser.available()?"Install Edge or Chrome, then restart.":slow?"Check your connection or sign in.":failed?"Try again or continue in your browser.":"Video and website controls will appear here.";
            text(ctx,detail,x+17,cy+23,DIM,.72f,w-34);
            if(!PhoneWebBrowser.available())button(ctx,x+17,cy+49,w-34,27,"Get Chrome",()->openExternal(INSTALL),true);
            else if(slow || failed || browser!=null&&!browser.status().isBlank()) {
                button(ctx,x+17,cy+48,(w-40)/2,26,"Retry",this::reload,true);
                button(ctx,x+23+(w-40)/2,cy+48,(w-40)/2,26,"Browser",()->openExternal(URI.create(url())),false);
            } else {
                float t=(float)((System.nanoTime()/1e9)%1);
                Render2D.roundRect(ctx,x+17,cy+53,w-34,3,1.5f,0xFF2B3A42);
                Render2D.roundRect(ctx,x+17+t*(w-72),cy+53,38,3,1.5f,CYAN);
            }
        } else if(browser!=null && browser.loading()) {
            Render2D.roundRect(ctx,x+8,y+8,77,19,8,0xD9111B23);
            text(ctx,"Loading…",x+16,y+13,WHITE,.7f,61);
        }
        button(ctx,12,height-42,32,28,"↑",()->step(-1),false);
        button(ctx,54,height-42,width-66,28,"Scroll ↓",()->step(1),true);
        if(feedsOpen) {
            Render2D.roundRect(ctx,12,104,width-24,157,10,0xFC16232C);
            for(var item:Feed.values()) {
                float fy=111+item.ordinal()*47;
                button(ctx,18,fy,width-36,28,item.label,()->choose(item),item==feed);
                String description=switch(item){case FOR_YOU->"Your TikTok recommendations";case ENTERTAINMENT->"Comedy, creators and trends";case GAMING->"Plays, streams and game clips";};
                text(ctx,description,24,fy+32,DIM,.62f,width-48);
            }
        }
        if(menuOpen) {
            Render2D.roundRect(ctx,12,104,width-24,146,10,0xFC16232C);
            button(ctx,18,111,width-36,28,"Reload feed",()->{menuOpen=false;reload();},false);
            button(ctx,18,145,width-36,28,"Open in browser",()->{menuOpen=false;openExternal(URI.create(url()));},false);
            button(ctx,18,179,width-36,28,"Local video files",()->{menuOpen=false;localVideos.run();},false);
            button(ctx,18,213,width-36,28,videoFocus?"Show full website":"Focus content",()->{menuOpen=false;videoFocus=!videoFocus;wheel.reset();if(browser!=null)browser.videoFocus(videoFocus);},false);
        }
    }
    private void reload() {
        loadingAt=System.nanoTime();failed=false;blur();wheel.reset();
        if(browser!=null && !browser.status().isBlank()){browser.close();browser=null;destroyTexture();}
        else if(browser!=null)browser.reload();
    }
    private void upload() {
        var frame=browser==null?null:browser.frame();if(frame==null||frame.serial()==shownFrame)return;
        if(texture==null||texture.getImage().getWidth()!=frame.width()||texture.getImage().getHeight()!=frame.height()) {
            destroyTexture();texture=new dev.maro.phone.PhoneFrameTexture("Maro TikTok",frame.width(),frame.height());MinecraftClient.getInstance().getTextureManager().registerTexture(textureId,texture);
        }
        dev.maro.runtime.renderer.NativeImageUpload.rgba(texture.getImage(),frame.rgba());
        texture.upload();shownFrame=frame.serial();shownData=frame;
    }
    private void destroyTexture() {if(texture!=null){MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);texture=null;}shownFrame=0;shownData=null;}
    private static void openExternal(URI uri) { Util.getMainWorkerExecutor().execute(()->Util.getOperatingSystem().open(uri)); }
    private boolean contains(float mx,float my) { return mx>=x && my>=y && mx<x+w && my<y+h; }
    private int bx(float mx) { var f=shownData;return Math.clamp(Math.round(f==null?(mx-x)/w*pixelsW:f.viewX()+Math.clamp((mx-drawX)/Math.max(1,drawW),0,1)*f.viewWidth()),0,pixelsW-1); }
    private int by(float my) { var f=shownData;return Math.clamp(Math.round(f==null?(my-y)/h*pixelsH:f.viewY()+Math.clamp((my-drawY)/Math.max(1,drawH),0,1)*f.viewHeight()),0,pixelsH-1); }
    boolean click(float mx,float my) {
        for(int i=hits.size()-1;i>=0;i--) {var hit=hits.get(i);if(hit.contains(mx,my)) { blur(); hit.action.run();return true; }}
        if(feedsOpen || menuOpen) {feedsOpen=menuOpen=false;return true;}
        if(browser!=null && contains(mx,my)) { focused=true;pressed=true;browser.focus(true);browser.press(bx(mx),by(my));return true; }
        blur();return false;
    }
    boolean release(float mx,float my) { if(!pressed||browser==null)return false; pressed=false;browser.release(bx(mx),by(my));return true; }
    boolean drag(float mx,float my) { if(!pressed||browser==null)return false;browser.move(bx(mx),by(my));return true; }
    private void step(int direction) {
        if (browser == null || feedsOpen || menuOpen) return;
        lastStep=System.nanoTime();browser.videoStep(direction);
    }
    void scroll(float mx,float my,double amount) {
        if(browser==null || !contains(mx,my) || feedsOpen || menuOpen || !Double.isFinite(amount) || amount==0)return;
        if(videoFocus) {
            int direction=wheel.take(amount,System.nanoTime());if(direction!=0)step(direction);
        } else browser.scroll(bx(mx),by(my),Math.clamp(amount,-10,10));
    }
    boolean key(KeyInput input) {
        if(!focused||browser==null)return false;
        if(input.key()==GLFW.GLFW_KEY_ESCAPE)blur();
        else if(input.key()==GLFW.GLFW_KEY_V && (input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0) {
            String value=MinecraftClient.getInstance().keyboard.getClipboard();
            int[] points=value.codePoints().limit(4096).toArray();browser.paste(new String(points,0,points.length));
        } else browser.key(input,true);
        return true;
    }
    boolean keyReleased(KeyInput input) { if(!focused||browser==null)return false;browser.key(input,false);return true; }
    boolean character(int codepoint) { if(!focused||browser==null)return false;browser.character(codepoint);return true; }
    private void button(DrawContext ctx,float x,float y,float w,float h,String label,Runnable action,boolean selected) {
        Render2D.roundRect(ctx,x,y,w,h,6,selected?CYAN:0xFF293331);
        Fonts.drawCentered(ctx,label,x+w/2,y+h/2,selected?0xFF082323:WHITE,true,.7f);
        hits.add(new Hit(x,y,w,h,action));
    }
    private static void text(DrawContext ctx,String value,float x,float y,int color,float size,float width) { Fonts.draw(ctx,Fonts.trim(value,width,false,size),x,y,color,false,size); }
    @Override public void close() { stop(); }
}
