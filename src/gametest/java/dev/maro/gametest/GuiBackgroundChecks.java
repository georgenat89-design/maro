package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.hud.GuiBackgroundScreen;
import dev.maro.gui.PngFilePickerScreen;
import dev.maro.gui.render.GuiBackground;
import dev.maro.gui.render.GuiBackgroundImage;
import dev.maro.gui.render.Render2D;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native PNG import, real GPU alpha blending, layout, persistence and responsive studio checks. */
final class GuiBackgroundChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void near(float actual,float expected,String label){require(Math.abs(actual-expected)<.002f,label+": "+actual+" != "+expected);}
    static void run(ClientGameTestContext context) {
        Path fixtures;
        try { fixtures=Files.createTempDirectory("maro-background-test-"); }
        catch(IOException error){throw new AssertionError(error);}
        Path alpha=fixtures.resolve("alpha.png"),opaque=fixtures.resolve("opaque.png"),wrong=fixtures.resolve("not-png.png");
        try {
            var image=new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<32;x++)image.setRGB(x,y,0x80FF00FF);
            ImageIO.write(image,"PNG",alpha.toFile());
            image.setRGB(0,0,0xFFFF0000);ImageIO.write(image,"PNG",opaque.toFile());image.flush();
            Files.writeString(wrong,"this is not a png");
            var decoded=GuiBackgroundImage.decode(alpha);
            require(decoded.width()==32&&decoded.height()==16,"PNG dimensions changed unnecessarily");
            require((decoded.rgba()[3]&255)==128&&(decoded.rgba()[0]&255)==255&&(decoded.rgba()[1]&255)==0,"PNG alpha/color were lost");
            expectInvalid(wrong);expectInvalid(fixtures.resolve("missing.png"));
            var large=new BufferedImage(5000,1000,BufferedImage.TYPE_INT_ARGB);
            Path largeFile=fixtures.resolve("large.png");ImageIO.write(large,"PNG",largeFile.toFile());large.flush();
            var limited=GuiBackgroundImage.decode(largeFile);
            require(limited.width()==4096&&limited.height()==819,"Large image did not downscale proportionally");
            require((long)limited.width()*limited.height()<=GuiBackgroundImage.MAX_PIXELS,"PNG exceeded texture budget");
        }catch(IOException error){throw new AssertionError(error);}
        layout();
        int originalScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        Map<Setting<?>,JsonElement> saved=context.computeOnClient(c->{var map=new LinkedHashMap<Setting<?>,JsonElement>();for(var section:ClientSettings.ALL)for(var setting:section.getSettings())map.put(setting,setting.toJson());return map;});
        Path stored=context.computeOnClient(c->GuiBackground.image().file());
        byte[] previous;
        try{previous=Files.exists(stored)?Files.readAllBytes(stored):null;}catch(IOException error){throw new AssertionError(error);}
        try {
            context.runOnClient(c->{
                ClientSettings.guiBackgroundMode.set("PNG Image");ClientSettings.guiBackgroundFit.set("Stretch");
                ClientSettings.guiBackgroundDim.set(0d);ClientSettings.guiBackgroundOpacity.set(100d);ClientSettings.guiBackgroundTint.set(0xFFFFFFFF);
                GuiBackground.image().importPng(alpha);c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new AlphaProbe());
            });
            await(context);require(context.computeOnClient(c->GuiBackground.image().loaded()),"Imported PNG never uploaded");
            require(Files.isRegularFile(stored),"Imported PNG was not saved in Maro assets");
            GuiBackgroundImage reopened=context.computeOnClient(c->{var asset=new GuiBackgroundImage(stored);asset.ensureLoaded();return asset;});
            for(int tick=0;tick<200&&context.computeOnClient(c->reopened.busy());tick++)context.waitTick();
            require(context.computeOnClient(c->reopened.loaded()&&reopened.width()==32&&reopened.height()==16),"Saved PNG could not reopen without the source file");
            context.runOnClient(c->reopened.close());
            context.waitTicks(3);
            try{
                var frame=ImageIO.read(context.takeScreenshot("maro-background-alpha").toFile());
                int rgb=frame.getRGB(frame.getWidth()/2,frame.getHeight()/2),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                require(r>245&&b>245&&g>115&&g<140,"PNG did not alpha-blend over gameplay: "+Integer.toHexString(rgb));
            }catch(IOException error){throw new AssertionError(error);}
            Object texture=context.computeOnClient(c->GuiBackground.image().texture());
            context.waitTicks(8);
            require(context.computeOnClient(c->GuiBackground.image().texture())==texture,"Unchanged PNG was reuploaded every frame");
            context.runOnClient(c->GuiBackground.image().importPng(wrong));await(context);
            require(context.computeOnClient(c->GuiBackground.image().texture())==texture,"Invalid PNG destroyed the previous texture");
            var reload=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(c->reload.set(c.reloadResources()));
            for(int tick=0;tick<400&&(!reload.get().isDone()||context.computeOnClient(c->c.getOverlay()!=null));tick++)context.waitTick();
            require(reload.get().isDone()&&!reload.get().isCompletedExceptionally(),"Native resource reload failed");
            require(context.computeOnClient(c->GuiBackground.image().loaded()),"Resource reload lost the cached PNG");
            picker(context,fixtures,alpha);
            context.runOnClient(c->{
                ClientSettings.guiBackgroundMode.set("Gradient");ClientSettings.guiBackgroundPreset.set("Dusk");
                ClientSettings.guiBackgroundColor.set(0xFF183858);ClientSettings.guiBackgroundColor2.set(0xFF652B60);
                ClientSettings.guiBackgroundDirection.set(135d);ClientSettings.guiBackgroundScale.set(155d);
                ConfigManager.saveClient();ClientSettings.GUI_BACKGROUND.getSettings().forEach(Setting::reset);ConfigManager.loadClient();
                require(ClientSettings.guiBackgroundMode.is("Gradient")&&ClientSettings.guiBackgroundColor.get()==0xFF183858&&ClientSettings.guiBackgroundColor2.get()==0xFF652B60,"Customized gradient palette did not persist");
                require(ClientSettings.guiBackgroundDirection.getInt()==135&&ClientSettings.guiBackgroundScale.getInt()==155,"Background layout settings did not persist");
            });
            for(int guiScale:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new GuiBackgroundScreen(null));});
                context.waitTicks(4);context.takeScreenshot("maro-background-studio-"+guiScale);
                int[] point=context.computeOnClient(c->{double[] p=((GuiBackgroundScreen)c.currentScreen).point(230,78);double device=c.getWindow().getScaleFactor();return new int[]{(int)(p[0]*device),(int)(p[1]*device)};});
                context.getInput().setCursorPos(point[0],point[1]);context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTick();context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(3);context.takeScreenshot("maro-background-image-controls-"+guiScale);
                context.runOnClient(c->ClientSettings.guiBackgroundMode.set("PNG Image"));
                for(String mode:new String[]{"Fit","Fill","Stretch","Tile"}){
                    context.runOnClient(c->ClientSettings.guiBackgroundFit.set(mode));context.waitTicks(2);
                    context.takeScreenshot("maro-background-"+mode.toLowerCase()+"-"+guiScale);
                }
            }
            context.runOnClient(c->{ClientSettings.guiBackgroundMode.set("Transparent");ClientSettings.guiBackgroundDim.set(0d);c.setScreen(new AlphaProbe());});
            context.waitTicks(2);
            try{var frame=ImageIO.read(context.takeScreenshot("maro-background-transparent").toFile());require((frame.getRGB(frame.getWidth()/2,frame.getHeight()/2)&0xFFFFFF)==0xFFFFFF,"Transparent backdrop changed gameplay with dimming at zero");}
            catch(IOException error){throw new AssertionError(error);}
            context.runOnClient(c->GuiBackground.reset());await(context);
            require(!context.computeOnClient(c->GuiBackground.image().loaded()),"Removed texture remained active");
            require(context.computeOnClient(c->ClientSettings.guiBackgroundMode.is("Default")),"Reset did not restore the default background");
            PhonePickerChecks.run(context,fixtures);
            MenuBlurChecks.run(context);
            System.out.println("[gui-background] PASS: PNG validation/downscale/alpha, GPU caching, four layouts, persisted palettes and studio GUI scales 2/3/4");
        } finally {
            context.runOnClient(c->{saved.forEach(Setting::fromJson);c.setScreen(null);c.options.getGuiScale().setValue(originalScale);c.onResolutionChanged();ConfigManager.saveClient();});
            try{if(previous!=null)Files.write(stored,previous);else Files.deleteIfExists(stored);}catch(IOException error){throw new AssertionError(error);}
        }
    }
    private static void expectInvalid(Path file){try{GuiBackgroundImage.decode(file);throw new AssertionError("Invalid PNG accepted: "+file);}catch(IOException expected){}}
    private static void await(ClientGameTestContext context){for(int i=0;i<200;i++){if(!context.computeOnClient(c->GuiBackground.image().busy()))return;context.waitTick();}throw new AssertionError("PNG worker did not complete");}
    private static void click(ClientGameTestContext context,double[] position){
        require(position!=null,"Picker row was not visible");double device=context.computeOnClient(c->c.getWindow().getScaleFactor());
        context.getInput().setCursorPos(position[0]*device,position[1]*device);context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTick();context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);context.waitTicks(2);
    }
    private static void awaitPicker(ClientGameTestContext context){for(int i=0;i<200;i++){if(!context.computeOnClient(c->((PngFilePickerScreen)c.currentScreen).busy()))return;context.waitTick();}throw new AssertionError("Picker directory scan timed out");}
    private static void picker(ClientGameTestContext context,Path fixtures,Path alpha){
        context.runOnClient(c->{
            var pictures=new PngFilePickerScreen(null,fixtures,"Choose picture",file->{},"png","jpg","jpeg");
            require(pictures.accepts(Path.of("picture.PNG"))&&pictures.accepts(Path.of("picture.JpG"))&&pictures.accepts(Path.of("picture.jpeg")),"Image picker suffix filter is case-sensitive or incomplete");
            require(!pictures.accepts(Path.of("picture.png.exe"))&&!pictures.accepts(Path.of("archive.zip")),"Image picker admitted an unsupported file");
            var videos=new PngFilePickerScreen(null,fixtures,"Choose video",file->{},"mp4");
            require(videos.accepts(Path.of("clip.MP4"))&&!videos.accepts(Path.of("clip.mov")),"MP4 picker filter failed");
            var packs=new PngFilePickerScreen(null,fixtures,"Choose resource pack",file->{},"zip");
            require(packs.accepts(Path.of("resource-pack.ZIP"))&&!packs.accepts(Path.of("resource-pack.jar")),"ZIP picker filter failed");
        });
        GuiBackgroundScreen studio=context.computeOnClient(c->{var screen=new GuiBackgroundScreen(null);c.setScreen(screen);return screen;});context.waitTicks(3);
        click(context,context.computeOnClient(c->studio.point(380,382)));
        require(context.computeOnClient(c->c.currentScreen instanceof PngFilePickerScreen),"Import opened an operating-system dialog instead of the protected in-client picker");
        awaitPicker(context);context.takeScreenshot("maro-background-private-file-picker");
        click(context,context.computeOnClient(c->((PngFilePickerScreen)c.currentScreen).point(590,406)));
        require(context.computeOnClient(c->c.currentScreen==studio),"Picker Cancel did not restore the studio");
        var chosen=new java.util.concurrent.atomic.AtomicReference<Path>();
        context.runOnClient(c->c.setScreen(new PngFilePickerScreen(studio,fixtures,file->{chosen.set(file);GuiBackground.image().importPng(file);})));
        awaitPicker(context);context.waitTicks(2);
        click(context,context.computeOnClient(c->((PngFilePickerScreen)c.currentScreen).entryPoint(alpha.getFileName().toString())));
        click(context,context.computeOnClient(c->((PngFilePickerScreen)c.currentScreen).point(693,406)));
        require(alpha.toAbsolutePath().normalize().equals(chosen.get()),"Picker did not import the selected PNG");
        require(context.computeOnClient(c->c.currentScreen==studio),"Picker Import did not restore the same studio");
        await(context);
    }
    private static void layout(){
        var fit=GuiBackground.placement(1600,900,1000,1000,"Fit",1,.5f,.5f);near(fit.x(),350,"Fit center");near(fit.width(),900,"Fit width");near(fit.u0(),0,"Fit UV");
        var fill=GuiBackground.placement(1600,900,1000,1000,"Fill",1,.5f,.5f);near(fill.width(),1600,"Fill width");near(fill.height(),900,"Fill crop");near(fill.v0(),.21875f,"Fill top UV");
        var align=GuiBackground.placement(1600,900,1000,1000,"Fill",1,0,1);near(align.v1(),1,"Bottom alignment");
        var stretch=GuiBackground.placement(1600,900,32,16,"Stretch",2,0,0);near(stretch.width(),1600,"Stretch");near(stretch.u1(),1,"Stretch UV");
        var tile=GuiBackground.placement(1600,900,32,16,"Tile",2,.5f,.5f);near(tile.u1()-tile.u0(),25,"Tile repeats");
        for(String mode:new String[]{"Fit","Fill","Stretch","Tile"})for(float zoom:new float[]{.1f,1,4}){
            var p=GuiBackground.placement(317,191,32,16,mode,zoom,.22f,.71f);
            require(p.x()>=0&&p.y()>=0&&p.x()+p.width()<=317.01&&p.y()+p.height()<=191.01,"Image escaped preview bounds");
        }
    }
    private static final class AlphaProbe extends Screen {
        private AlphaProbe(){super(Text.literal("GUI background alpha probe"));}
        @Override public boolean shouldPause(){return false;}
        @Override public void renderBackground(DrawContext c,int x,int y,float d){}
        @Override public void render(DrawContext c,int x,int y,float d){Render2D.setAlpha(1);c.fill(0,0,width,height,0xFFFFFFFF);GuiBackground.draw(c,0,0,width,height);}
    }
}
