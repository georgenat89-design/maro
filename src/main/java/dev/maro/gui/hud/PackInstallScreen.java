package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.phone.PhoneFrameTexture;
import dev.maro.phone.browser.PhoneWebBrowser;
import dev.maro.textures.Modrinth;
import dev.maro.textures.PacksMcCatalog;
import dev.maro.textures.PacksMcInstallQueue;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;

/** Creator's normal download page, with completed resource ZIPs installed inside Minecraft. */
public final class PackInstallScreen extends Screen {
    private static final int W=960,H=654,VY=70,VH=530;
    private final Screen parent;
    private final TexturePacks module;
    private PacksMcCatalog.Pack pack;
    private PacksMcInstallQueue queue;
    private String url;
    private Path staging;
    private PhoneWebBrowser browser;
    private PhoneFrameTexture texture;
    private final Identifier image=Identifier.of("maro","pack_install/"+UUID.randomUUID());
    private long serial;
    private float scale=1,ox,oy;
    private boolean pressed,focused,closed,handled,installed;
    private String message="Choose a version and download on PacksMC. The pack installs automatically.";
    private CompletableFuture<Path> importing;
    private long nextAt;
    public PackInstallScreen(Screen parent,TexturePacks module,PacksMcCatalog.Pack pack){
        super(Text.literal("Install · "+pack.name()));this.parent=parent;this.module=module;this.pack=pack;
        url=PacksMcScreen.downloadPage(pack).toString();
        staging=ConfigManager.DIR.resolve("packsmc-downloads").resolve(UUID.randomUUID().toString()).toAbsolutePath().normalize();
    }
    public PackInstallScreen(Screen parent,TexturePacks module,List<PacksMcCatalog.Pack> packs){
        super(Text.literal("Download collection"));this.parent=parent;this.module=module;
        module.opened();queue=new PacksMcInstallQueue(packs,module::cached);pack=queue.current();
        url=pack==null?"":PacksMcScreen.downloadPage(pack).toString();
        staging=ConfigManager.DIR.resolve("packsmc-downloads").resolve(UUID.randomUUID().toString()).toAbsolutePath().normalize();
        message="Download each pack on its creator page. Saved packs are skipped automatically.";
    }
    private void start(){
        if(browser!=null||closed||pack==null)return;
        browser=PhoneWebBrowser.forDownloads(url,W-24,VH,ConfigManager.DIR.resolve("packsmc-browser"),staging);
        if(browser==null)message="Install Edge or Chrome to download inside the game, then reopen this page.";
    }
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mx,int my,float d){
        if(nextAt>0&&System.currentTimeMillis()>=nextAt)advance(false);
        start();module.pump();upload();pollDownload();Theme.update();
        scale=Math.min((width-16f)/W,(height-16f)/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        float x=(mx-ox)/scale,y=(my-oy)/scale;
        Render2D.rect(c,0,0,width,height,0xC3060A12);c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);
        Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.roundRect(c,0,0,W,H,12,0xFF101724);
            label(c,Fonts.trim(pack==null?"Collection finished":pack.name(),queue==null?650:620,true,1.3f),20,24,1.3f,true,Theme.TEXT);
            label(c,Fonts.trim(queue==null?"PacksMC · by "+pack.author()+" · sign in here if required":queue.progress()+" · "+(pack==null?(queue.saved()>0?"Ready in your library":"No packs saved yet"):"by "+pack.author()),W-40,false,.90f),20,49,.90f,false,Theme.TEXT_MUTED);
            button(c,installed||pack==null?"Done":"Back",W-170,16,70,28,Theme.TEXT);
            if(pack!=null)button(c,"Reload",W-90,16,70,28,Theme.TEXT);
            if(queue!=null&&pack!=null)button(c,"Skip",W-250,16,70,28,Theme.TEXT);
            Render2D.roundRect(c,12,VY,W-24,VH,5,0xFF080D15);
            if(pack==null){
                label(c,queue.saved()>0?"Saved once. Ready whenever you play.":"No packs saved yet.",60,180,1.65f,true,Theme.TEXT);
                label(c,queue.progress(),60,226,1.2f,true,0xFF8DDDC7);
                label(c,queue.skipped()>0?"Run Download all again to revisit skipped packs.":"Choose Apply on any saved pack. No more downloading or file picking.",60,265,1f,false,Theme.TEXT_MUTED);
                button(c,"Open library",60,314,180,38,Theme.TEXT);
            }
            else if(texture!=null){var f=texture.getImage();c.drawTexture(RenderPipelines.GUI_TEXTURED,image,12,VY,0,0,W-24,VH,f.getWidth(),f.getHeight(),f.getWidth(),f.getHeight());}
            else label(c,browser==null?"Browser unavailable":"Opening PacksMC…",32,VY+40,.95f,false,Theme.TEXT_DIM);
            if(browser!=null&&x>=12&&x<W-12&&y>=VY&&y<VY+VH)browser.move(Math.round(x-12),Math.round(y-VY));
            String status=browser!=null&&!browser.status().isBlank()?browser.status():message;
            label(c,Fonts.trim(status,W-40,false,1f),20,H-34,1f,false,installed?Theme.GREEN:Theme.TEXT);
            label(c,focused?"Esc releases website controls · Ctrl+V pastes":pack==null?"Saved resource packs can be applied offline from your library":queue!=null?"Download on the page · Saves to your library · Next pack opens automatically":"Scroll and click the page · Downloads install automatically",20,H-14,.85f,false,Theme.TEXT_MUTED);
            if(queue!=null){Render2D.roundRect(c,12,H-53,W-24,3,1.5f,0xFF29343B);if(queue.total()>0)Render2D.roundRect(c,12,H-53,(W-24)*(float)queue.processed()/queue.total(),3,1.5f,0xFF8DDDC7);}
        }finally{Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
    }
    private void upload(){
        var f=browser==null?null:browser.frame();if(f==null||f.serial()==serial)return;
        if(texture==null||texture.getImage().getWidth()!=f.width()||texture.getImage().getHeight()!=f.height()){
            destroyTexture();texture=new PhoneFrameTexture("PacksMC download page",f.width(),f.height());client.getTextureManager().registerTexture(image,texture);
        }
        dev.maro.runtime.renderer.NativeImageUpload.rgba(texture.getImage(),f.rgba());texture.upload();serial=f.serial();
    }
    private void pollDownload(){
        var download=browser==null?null:browser.download();if(download==null||handled)return;
        if(download.state().equals("failed")||download.state().equals("canceled")){message=download.message()+" · Reload to retry";return;}
        if(!download.state().equals("completed")){
            message="Downloading "+download.filename()+" · "+Math.round(download.received()/1048576d)+" MB"+(download.total()>0?" / "+Math.round(download.total()/1048576d)+" MB":"");return;
        }
        handled=true;message="Checking and installing the resource pack…";
        var downloadingPack=pack;
        importing=module.importPack(downloadingPack,download.file(),queue==null);
        importing.whenComplete((file,error)->client.execute(()->{
            installed=error==null;message=installed?"Saved · "+downloadingPack.name()+" · ready in Gameplay Textures":module.importStatus()+" · Reload to retry";
            if(installed&&queue!=null&&!closed)nextAt=System.currentTimeMillis()+900;
        }));
    }
    private void advance(boolean skipped){
        if(queue==null||pack==null||importing!=null&&!importing.isDone())return;
        if(skipped)queue.skip();else queue.downloaded();
        releaseBrowser();cleanup(staging,importing);staging=staging.getParent().resolve(UUID.randomUUID().toString());
        nextAt=0;importing=null;handled=installed=focused=pressed=false;pack=queue.current();
        url=pack==null?"":PacksMcScreen.downloadPage(pack).toString();
        message=pack==null?"Collection finished · "+queue.progress():"Download on this page. The next pack opens after it is saved.";
    }
    private void label(DrawContext c,String s,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,s,x,y,color,bold,size);}
    private void button(DrawContext c,String s,float x,float y,float w,float h,int color){Render2D.roundRect(c,x,y,w,h,6,0xFF29394E);Fonts.drawCentered(c,s,x+w/2,y+h/2,color,false,.95f);}
    private float localX(double x){return (float)((x-ox)/scale);}
    private float localY(double y){return (float)((y-oy)/scale);}
    private boolean inside(float x,float y){return x>=12&&x<W-12&&y>=VY&&y<VY+VH;}
    @Override public boolean mouseClicked(Click event,boolean doubled){
        float x=localX(event.x()),y=localY(event.y());if(event.button()!=0)return true;
        if(y>=16&&y<44&&x>=W-170&&x<W-100){close();return true;}
        if(y>=16&&y<44&&x>=W-90&&x<W-20){if(pack!=null&&(importing==null||importing.isDone()))restart();return true;}
        if(queue!=null&&pack!=null&&y>=16&&y<44&&x>=W-250&&x<W-180){advance(!installed);return true;}
        if(pack==null&&x>=60&&x<240&&y>=314&&y<352){client.setScreen(new TexturePacksScreen(parent,module));return true;}
        focused=browser!=null&&inside(x,y);if(browser!=null)browser.focus(focused);
        if(focused){pressed=true;browser.press(Math.round(x-12),Math.round(y-VY));}return true;
    }
    @Override public boolean mouseReleased(Click event){if(browser!=null&&pressed){pressed=false;browser.release(Math.round(localX(event.x())-12),Math.round(localY(event.y())-VY));}return true;}
    @Override public boolean mouseDragged(Click event,double dx,double dy){if(browser!=null&&pressed)browser.move(Math.round(localX(event.x())-12),Math.round(localY(event.y())-VY));return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(browser!=null&&inside(localX(x),localY(y)))browser.scroll(Math.round(localX(x)-12),Math.round(localY(y)-VY),vertical);return true;}
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(focused){focused=false;if(browser!=null)browser.focus(false);}else close();return true;}
        if(browser!=null&&focused){if(input.key()==GLFW.GLFW_KEY_V&&(input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0){String value=client.keyboard.getClipboard();browser.paste(value.substring(0,Math.min(4096,value.length())));}else browser.key(input,true);}return true;
    }
    @Override public boolean keyReleased(KeyInput input){if(browser!=null&&focused)browser.key(input,false);return true;}
    @Override public boolean charTyped(CharInput input){if(browser!=null&&focused)browser.character(input.codepoint());return true;}
    private void destroyTexture(){if(texture!=null){client.getTextureManager().destroyTexture(image);texture=null;}serial=0;}
    private void releaseBrowser(){if(browser!=null){browser.cancelDownload();browser.close();browser=null;}destroyTexture();}
    private void restart(){
        releaseBrowser();cleanup(staging,importing);staging=staging.getParent().resolve(UUID.randomUUID().toString());
        handled=installed=focused=pressed=false;importing=null;nextAt=0;message="Choose a version and download. It installs automatically.";
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public void removed(){
        closed=true;if(importing!=null&&!importing.isDone())module.cancelPendingActivation();releaseBrowser();cleanup(staging,importing);ConfigManager.saveAll();
    }
    private static void cleanup(Path directory,CompletableFuture<?> job){
        (job==null?CompletableFuture.completedFuture(null):job.handle((v,e)->null)).thenRunAsync(()->{
            // Only the UUID-owned flat staging directory. No arbitrary or recursive file removal.
            Path root=ConfigManager.DIR.resolve("packsmc-downloads").toAbsolutePath().normalize();
            if(!directory.getParent().equals(root)||!directory.getFileName().toString().matches("[a-f0-9-]{36}"))return;
            try(var files=Files.list(directory)){for(Path file:files.toList())if(!Files.isDirectory(file))Files.deleteIfExists(file);}catch(Exception ignored){}
            try{Files.deleteIfExists(directory);}catch(Exception ignored){}
        },CompletableFuture.delayedExecutor(4,TimeUnit.SECONDS,Modrinth.POOL));
    }
    @Override public boolean shouldPause(){return false;}
}
