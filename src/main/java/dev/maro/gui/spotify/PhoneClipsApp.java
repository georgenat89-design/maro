package dev.maro.gui.spotify;

import com.google.gson.*;
import dev.maro.config.ConfigManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.widget.TextField;
import dev.maro.phone.PhoneVideoPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** A vertical, looping clip feed with bounded background decoding and a reusable GPU texture. */
final class PhoneClipsApp implements AutoCloseable {
    private record Clip(String title,URI source) { }
    private record Hit(float x,float y,float w,float h,Runnable action){boolean has(float mx,float my){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}}
    private final Path FOLDER,CACHE,LINKS;
    private static final int WHITE=0xFFF5F7F6,DIM=0xFFA3ADA7,GREEN=0xFF1ED760;
    private final ExecutorService files=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Maro clip library");t.setDaemon(true);return t;});
    private final PhoneVideoPlayer player=new PhoneVideoPlayer();
    private final TextField link=new TextField("Direct MP4 video link",1024);
    private final List<Hit> hits=new ArrayList<>();
    private volatile List<Clip> clips=List.of();
    private volatile String error="";
    private volatile boolean loading,closed;
    private boolean manage,typing,menuOpen;
    private Runnable home=()->{},tikTok=()->{};
    void navigation(Runnable home,Runnable tikTok){this.home=home;this.tikTok=tikTok;}
    private int index;
    private String playing="";
    private long shownFrame;
    private final FeedWheel wheel=new FeedWheel();
    private NativeImageBackedTexture texture;
    private final Identifier textureId=Identifier.of("maro","phone_video/"+UUID.randomUUID());
    PhoneClipsApp(Path folder){FOLDER=folder==null?ConfigManager.DIR.resolve("phone-clips"):folder;CACHE=FOLDER.resolve("cache");LINKS=FOLDER.resolve("links.json");}
    void open(){wheel.reset();reload();}
    boolean typing(){return typing;}
    void stop(){player.stop();playing="";typing=false;destroyTexture();}
    void reload(){
        if(loading||closed)return;loading=true;
        files.execute(()->{
            try{
                Files.createDirectories(FOLDER);Files.createDirectories(CACHE);List<Clip> out=new ArrayList<>();
                try(var paths=Files.list(FOLDER)){for(Path p:paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toString().toLowerCase(Locale.ROOT).endsWith(".mp4")).sorted().limit(200).toList())out.add(new Clip(p.getFileName().toString().replaceFirst("(?i)\\.mp4$",""),p.toUri()));}
                for(var item:readLinks())try{URI uri=videoUrl(item.getAsString());out.add(new Clip(title(uri),uri));}catch(RuntimeException ignored){}
                if(!closed){clips=List.copyOf(out);error="";}
            }catch(Exception e){error="Could not read clips folder";}finally{loading=false;}
        });
    }
    static URI videoUrl(String value){
        URI uri=URI.create(value.trim());String host=uri.getHost();
        if(host==null || uri.getUserInfo()!=null || uri.getFragment()!=null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())&&(host.equals("127.0.0.1")||host.equals("localhost"))))
            throw new IllegalArgumentException("Use a direct HTTPS MP4 video link");
        return uri;
    }
    private static String title(URI uri){String path=uri.getPath();int at=path.lastIndexOf('/');String name=path.substring(at+1).replaceFirst("(?i)\\.mp4$","");return name.isBlank()?uri.getHost():name;}
    private JsonArray readLinks()throws IOException{
        if(!Files.exists(LINKS))return new JsonArray();
        if(Files.size(LINKS)>262144)throw new IOException("Clip list too large");
        return JsonParser.parseString(Files.readString(LINKS)).getAsJsonArray();
    }
    private void add(){
        URI uri=videoUrl(link.getText());link.clear();typing=false;
        files.execute(()->{
            try{Files.createDirectories(FOLDER);var list=readLinks();for(int i=list.size()-1;i>=0;i--)if(list.get(i).getAsString().equals(uri.toString()))list.remove(i);
                list.add(uri.toString());while(list.size()>200)list.remove(0);
                Path tmp=FOLDER.resolve("links.json.tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(list));
                Files.move(tmp,LINKS,StandardCopyOption.REPLACE_EXISTING);error="";
            }catch(Exception e){error="Could not save video link";}
            if(!closed)reload();
        });
    }
    void render(DrawContext ctx,float width,float height,float mx,float my){
        hits.clear();float x=20,w=width-40;
        button(ctx,12,81,32,22,"‹",home,mx,my,false);
        button(ctx,50,81,width-94,22,manage?"Watch videos":"Your library",()->{manage=!manage;typing=false;menuOpen=false;if(manage)stop();},mx,my,false);
        button(ctx,width-38,81,26,22,"···",()->menuOpen=!menuOpen,mx,my,false);
        if(manage){
            text(ctx,"Add your own videos",x,141,WHITE,.95f,w);
            Render2D.roundRect(ctx,x,169,w,25,6,0xFF20292B);
            String value=link.getText().isEmpty()?"Paste a direct MP4 link…":link.getText();
            while(typing&&value.length()>1&&Fonts.width(value,false,.78f)>w-14)value=value.substring(1);
            text(ctx,value+(typing&&System.currentTimeMillis()%1000<500?"|":""),x+6,177,link.getText().isEmpty()?DIM:WHITE,.78f,w-12);
            hits.add(new Hit(x,169,w,25,()->typing=true));
            button(ctx,x,202,(w-6)/2,25,"Add link",()->{try{add();}catch(IllegalArgumentException e){error=e.getMessage();}},mx,my,true);
            button(ctx,x+(w+6)/2,202,(w-6)/2,25,"Open folder",()->files.execute(()->{try{Files.createDirectories(FOLDER);Util.getOperatingSystem().open(FOLDER);}catch(IOException e){error="Could not open videos folder";}}),mx,my,false);
            button(ctx,x,237,w,24,"Refresh library",this::reload,mx,my,false);
            text(ctx,"H.264 MP4 · Up to 720p / 64 MB",x,282,DIM,.72f,w);
            if(height>390){text(ctx,"Put your saved videos in the folder.",x,300,DIM,.72f,w);text(ctx,"No demo clips are added to your feed.",x,317,DIM,.72f,w);}
            text(ctx,clips.size()+" videos in your library",x,height-39,DIM,.76f,w);
        }else{
            var list=clips;index=list.isEmpty()?0:Math.floorMod(index,list.size());
            float top=110,bottom=height-23,vx=8,vw=width-16,available=bottom-top;
            var matrix=ctx.getMatrices();
            double scale=Math.hypot(matrix.m00(),matrix.m01())*MinecraftClient.getInstance().getWindow().getScaleFactor();
            var size=dev.maro.phone.PhoneVideoSizing.viewport(vw,available,scale);player.outputSize(size.width(),size.height());
            Render2D.roundRect(ctx,vx,top,vw,available,9,0xFF050708);
            if(!list.isEmpty()){
                Clip clip=list.get(index);String id=clip.source().toString();if(!id.equals(playing)){playing=id;destroyTexture();player.play(clip.source(),CACHE);}
                upload();
                if(texture!=null){
                    NativeImage image=texture.getImage();float fit=Math.min(vw/image.getWidth(),available/image.getHeight());
                    int dw=Math.max(1,Math.round(image.getWidth()*fit)),dh=Math.max(1,Math.round(image.getHeight()*fit));
                    ctx.createNewRootLayer();Render2D.clip(ctx,Math.round(vx),Math.round(top),Math.round(vx+vw),Math.round(bottom));
                    try{ctx.drawTexture(RenderPipelines.GUI_TEXTURED,textureId,Math.round(vx+(vw-dw)/2),Math.round(top+(available-dh)/2),0,0,dw,dh,image.getWidth(),image.getHeight(),image.getWidth(),image.getHeight());}
                    finally{Render2D.unclip(ctx);ctx.createNewRootLayer();}
                    if(player.paused()){
                        Render2D.roundRect(ctx,width/2-30,top+available/2-14,60,28,14,0xD91C2B32);
                        Fonts.drawCentered(ctx,"Paused",width/2,top+available/2,WHITE,true,.86f);
                    }
                }
                Render2D.roundRect(ctx,vx+5,bottom-37,vw-10,31,7,0xCA0B131B);
                text(ctx,clip.title(),vx+13,bottom-28,WHITE,.82f,vw-60);
                text(ctx,(index+1)+" / "+list.size(),vx+vw-43,bottom-28,DIM,.7f,38);
                hits.add(new Hit(vx,top,vw,available,player::pause));
                String status=player.status();if(!status.isEmpty()){
                    Render2D.roundRect(ctx,vx+9,top+available*.36f,vw-18,77,9,0xF013202B);
                    text(ctx,status,vx+19,top+available*.36f+13,WHITE,.83f,vw-38);
                    if(!status.startsWith("Loading"))button(ctx,vx+19,top+available*.36f+39,vw-38,25,"Try again",()->playing="",mx,my,true);
                    else text(ctx,"Preparing video…",vx+19,top+available*.36f+38,DIM,.72f,vw-38);
                }
            }else{
                text(ctx,loading?"Loading your library…":"No local videos yet",x,155,WHITE,.94f,w);
                text(ctx,"Watch TikTok or add your own files.",x,182,DIM,.76f,w);
                button(ctx,x,217,w,28,"Add videos",()->manage=true,mx,my,true);
                button(ctx,x,255,w,28,"Watch TikTok",tikTok,mx,my,false);
            }
            text(ctx,"Tap to pause · Scroll for next",14,height-15,DIM,.64f,width-28);
        }
        if(menuOpen){
            Render2D.roundRect(ctx,12,108,width-24,110,10,0xFC16232C);
            button(ctx,18,115,width-36,28,player.paused()?"Play video":"Pause video",()->{menuOpen=false;player.pause();},mx,my,false);
            button(ctx,18,149,width-36,28,player.muted()?"Turn sound on":"Mute video",()->{menuOpen=false;player.mute();},mx,my,false);
            button(ctx,18,183,width-36,28,"TikTok feeds",()->{menuOpen=false;tikTok.run();},mx,my,false);
        }
        if(!error.isBlank())text(ctx,error,x,height-27,0xFFFFA0A0,.7f,w);
    }
    private void upload(){
        var frame=player.frame();if(frame==null||frame.serial()==shownFrame)return;
        if(texture==null||texture.getImage().getWidth()!=frame.width()||texture.getImage().getHeight()!=frame.height()){
            destroyTexture();texture=new dev.maro.phone.PhoneFrameTexture("Maro phone clip",frame.width(),frame.height());
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId,texture);
        }
        dev.maro.runtime.renderer.NativeImageUpload.rgba(texture.getImage(),frame.rgba());
        texture.upload();shownFrame=frame.serial();
    }
    private void destroyTexture(){if(texture!=null){MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);texture=null;}shownFrame=0;}
    private void next(int amount){if(!clips.isEmpty()){index=Math.floorMod(index+amount,clips.size());playing="";typing=false;}}
    void scroll(double amount){if(manage||menuOpen)return;int direction=wheel.take(amount,System.nanoTime());if(direction!=0)next(direction);}
    boolean click(float x,float y){typing=false;for(int i=hits.size()-1;i>=0;i--){var h=hits.get(i);if(h.has(x,y)){h.action().run();return true;}}return false;}
    boolean key(KeyInput input){
        if(typing){if(input.key()==GLFW.GLFW_KEY_ESCAPE)typing=false;else if(input.key()==GLFW.GLFW_KEY_ENTER){try{add();}catch(IllegalArgumentException e){error=e.getMessage();}}else link.keyPressed(input.key(),input.modifiers());return true;}
        if(manage)return false;
        if(input.key()==GLFW.GLFW_KEY_DOWN||input.key()==GLFW.GLFW_KEY_RIGHT){next(1);return true;}
        if(input.key()==GLFW.GLFW_KEY_UP||input.key()==GLFW.GLFW_KEY_LEFT){next(-1);return true;}
        if(input.key()==GLFW.GLFW_KEY_SPACE){player.pause();return true;}return false;
    }
    boolean character(int point){if(!typing)return false;if(point<=Character.MAX_VALUE)link.charTyped((char)point);return true;}
    private void button(DrawContext ctx,float x,float y,float w,float h,String label,Runnable action,float mx,float my,boolean accent){
        Render2D.roundRect(ctx,x,y,w,h,6,accent?GREEN:0xFF293331);Fonts.drawCentered(ctx,label,x+w/2,y+h/2,accent?0xFF0B2013:WHITE,true,.72f);hits.add(new Hit(x,y,w,h,action));
    }
    private static void text(DrawContext ctx,String value,float x,float y,int color,float size,float width){Fonts.draw(ctx,Fonts.trim(value,width,false,size),x,y,color,false,size);}
    @Override public void close(){closed=true;stop();files.shutdownNow();player.close();}
}
