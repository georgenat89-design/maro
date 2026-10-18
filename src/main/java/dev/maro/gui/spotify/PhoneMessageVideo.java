package dev.maro.gui.spotify;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.phone.PhoneNetwork;
import dev.maro.phone.PhoneVideoPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Authenticated downloads stay on the relay worker; rendering reuses one small frame texture. */
final class PhoneMessageVideo implements AutoCloseable {
    private final PhoneVideoPlayer player=new PhoneVideoPlayer();
    private final Identifier textureId=Identifier.of("maro","message_video/"+java.util.UUID.randomUUID());
    private NativeImageBackedTexture texture;
    private long shown,generation;
    private String status="";
    private boolean closed;
    void open(PhoneNetwork network,PhoneNetwork.Message message){
        stop();long ticket=generation;status="Downloading video…";
        network.video(message).whenComplete((path,error)->MinecraftClient.getInstance().execute(()->{
            if(closed||ticket!=generation)return;
            if(error!=null){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();status=cause.getMessage()==null?"Could not load video":cause.getMessage();return;}
            status="";player.play(path.toUri(),path.getParent());
        }));
    }
    void render(DrawContext ctx,float x,float y,float w,float h){
        Render2D.roundRect(ctx,x,y,w,h,9,0xFF080E14);player.outputSize(Math.round(w*3),Math.round(h*3));
        var frame=player.frame();
        if(frame!=null&&frame.serial()!=shown){
            if(texture==null||texture.getImage().getWidth()!=frame.width()||texture.getImage().getHeight()!=frame.height()){
                destroy();texture=new dev.maro.phone.PhoneFrameTexture("Maro chat video",frame.width(),frame.height());MinecraftClient.getInstance().getTextureManager().registerTexture(textureId,texture);
            }
            dev.maro.runtime.renderer.NativeImageUpload.rgba(texture.getImage(),frame.rgba());texture.upload();shown=frame.serial();
        }
        if(texture!=null){
            var image=texture.getImage();float fit=Math.min(w/image.getWidth(),h/image.getHeight());int dw=Math.max(1,Math.round(image.getWidth()*fit)),dh=Math.max(1,Math.round(image.getHeight()*fit));
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED,textureId,Math.round(x+(w-dw)/2),Math.round(y+(h-dh)/2),0,0,dw,dh,image.getWidth(),image.getHeight(),image.getWidth(),image.getHeight());
        }
        String message=status.isEmpty()?player.status():status;
        if(!message.isEmpty())Fonts.drawCentered(ctx,Fonts.trim(message,w-18,false,.75f),x+w/2,y+h/2,0xFFEAF1F7,false,.75f);
    }
    boolean paused(){return player.paused();} boolean muted(){return player.muted();}
    void pause(){player.pause();} void mute(){player.mute();}
    private void destroy(){if(texture!=null){MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);texture=null;}shown=0;}
    void stop(){generation++;player.stop();destroy();status="";}
    @Override public void close(){closed=true;stop();player.close();}
}
