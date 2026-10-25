package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.nathan.modules.FreeCam;
import dev.maro.config.SharedModuleConfig;
import dev.maro.render.capture.CapturePass;
import dev.maro.render.capture.CleanCapture;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.Setting;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Captures actual GPU output, rather than treating a privacy switch as proof of a clean feed. */
final class StreamproofChecks {
    private static final class Probe extends Module {
        int enabled,disabled,ticks,draws;
        Probe(){super("Capture probe","Native streamproof check",Category.MISC);}
        @Override protected void onEnable(){enabled++;}
        @Override protected void onDisable(){disabled++;}
        @Override public void onTick(){ticks++;}
        @Override public void onRender2D(DrawContext c,float delta){if(CapturePass.active())throw new AssertionError("Private HUD entered capture");draws++;Render2D.rect(c,8,8,64,32,0xFFFF00FF);int stamp=draws%2==0?0xFF00BFFF:0xFFFFA000;Render2D.rect(c,80,8,32,32,stamp);Render2D.rect(c,112,8,32,32,stamp);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void ready(ClientGameTestContext c,java.util.function.BooleanSupplier check,String message){for(int i=0;i<160;i++){if(c.computeOnClient(mc->check.getAsBoolean()))return;c.waitTick();}throw new AssertionError(message+": "+CleanCapture.status());}
    private static NativeImage image(ClientGameTestContext context,boolean clean){
        var future=new CompletableFuture<NativeImage>();context.runOnClient(c->ScreenshotRecorder.takeScreenshot(clean?CleanCapture.framebuffer():c.getFramebuffer(),future::complete));
        ready(context,future::isDone,"GPU readback did not finish");return future.join();
    }
    private static void write(ClientGameTestContext context,NativeImage image,String name){context.runOnClient(c->{try{Path file=c.runDirectory.toPath().resolve("screenshots").resolve(name+".png");image.writeTo(file);}catch(Exception e){throw new AssertionError(e);}});}
    private static int magenta(NativeImage image){int count=0;for(int y=0;y<Math.min(150,image.getHeight());y++)for(int x=0;x<Math.min(230,image.getWidth());x++){int c=image.getColorArgb(x,y);if((c>>16&255)>220&&(c>>8&255)<40&&(c&255)>220)count++;}return count;}
    private static int worldMagenta(NativeImage image){int count=0;for(int y=150;y<image.getHeight()-100;y++)for(int x=100;x<image.getWidth()-250;x++){int c=image.getColorArgb(x,y);if((c>>16&255)>180&&(c>>8&255)<60&&(c&255)>180)count++;}return count;}
    private static int worldCyan(NativeImage image){int count=0;for(int y=120;y<image.getHeight()-60;y++)for(int x=100;x<image.getWidth()-200;x++){int c=image.getColorArgb(x,y);if((c>>16&255)<60&&(c>>8&255)>200&&(c&255)>200)count++;}return count;}
    private static boolean rgbNear(int a,int b,int tolerance){return Math.abs((a>>16&255)-(b>>16&255))<=tolerance&&Math.abs((a>>8&255)-(b>>8&255))<=tolerance&&Math.abs((a&255)-(b&255))<=tolerance;}
    private static boolean nonblack(NativeImage image){
        int lit=0;var colors=new java.util.HashSet<Integer>();
        for(int y=image.getHeight()/4;y<image.getHeight();y+=13)for(int x=0;x<image.getWidth();x+=13){int color=image.getColorArgb(x,y)&0xFFFFFF;if(color!=0)lit++;colors.add(color);}
        return lit>150&&colors.size()>12;
    }
    private static int changedTerrain(NativeImage before,NativeImage after){
        require(before.getWidth()==after.getWidth()&&before.getHeight()==after.getHeight(),"Fault-path viewport unexpectedly changed");
        int changed=0;
        for(int y=after.getHeight()/20;y<after.getHeight()*7/10;y+=3)for(int x=after.getWidth()*3/10;x<after.getWidth()*7/10;x+=3){
            int a=before.getColorArgb(x,y),b=after.getColorArgb(x,y);
            if(Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255))>90)changed++;
        }
        return changed;
    }
    private static int magenta(BufferedImage image){int count=0;for(int y=0;y<Math.min(150,image.getHeight());y++)for(int x=0;x<Math.min(230,image.getWidth());x++){int c=image.getRGB(x,y);if((c>>16&255)>220&&(c>>8&255)<40&&(c&255)>220)count++;}return count;}
    private static boolean nonblack(BufferedImage image){
        if(image==null)return false;
        int lit=0;var colors=new java.util.HashSet<Integer>();
        for(int y=image.getHeight()/4;y<image.getHeight();y+=13)for(int x=0;x<image.getWidth();x+=13){int color=image.getRGB(x,y)&0xFFFFFF;if(color!=0)lit++;colors.add(color);}
        return lit>150&&colors.size()>12;
    }
    private static double gameplayMatch(BufferedImage actual,NativeImage expected){
        int matching=0,total=0;
        // Ignore HUD and moving hands. Stable terrain must resemble the clean game framebuffer.
        for(int yy=25;yy<85;yy+=3)for(int xx=10;xx<90;xx+=3){
            int a=actual.getRGB(actual.getWidth()*xx/100,actual.getHeight()*yy/100);
            int b=expected.getColorArgb(expected.getWidth()*xx/100,expected.getHeight()*yy/100);
            if(Math.abs((a>>16&255)-(b>>16&255))<45&&Math.abs((a>>8&255)-(b>>8&255))<45&&Math.abs((a&255)-(b&255))<45)matching++;
            total++;
        }
        return matching/(double)total;
    }
    private static String nativeCapture(ClientGameTestContext context,boolean display,String name){
        try(var expected=image(context,true)){
            var capture=context.computeOnClient(c->display?dev.maro.render.capture.CaptureScopeChecks.displayCapture():dev.maro.render.capture.CaptureScopeChecks.windowCapture());
            String result;
            if(capture.image()!=null)try{Path file=context.computeOnClient(c->c.runDirectory.toPath().resolve("screenshots").resolve(name+".png"));ImageIO.write(capture.image(),"png",file.toFile());}catch(Exception error){throw new AssertionError(error);}
            double match=capture.image()==null?0:gameplayMatch(capture.image(),expected);
            if(!capture.supported()||!nonblack(capture.image())||match<.55){
                result=capture.method()+": "+(!capture.supported()&&capture.detail().startsWith("OCCLUDED")?"UNAVAILABLE: ":"UNSUPPORTED on this driver/API: ")+(capture.supported()?"black or non-gameplay output, clean-terrain match="+match:capture.detail());
                System.out.println("[streamproof-capture] "+result);
                boolean occluded=!capture.supported()&&capture.detail().startsWith("OCCLUDED");
                require(!display||occluded,"Display capture did not return usable gameplay: "+result);
            }else{
                int privatePixels=magenta(capture.image());
                require(privatePixels==0,"Private HUD leaked through "+capture.method()+": "+privatePixels+" pixels");
                result=capture.method()+": PASS, nonblack gameplay, 0 magenta private HUD pixels, clean-terrain match="+match+"; "+capture.detail();
                System.out.println("[streamproof-capture] "+result);
            }
            return result;
        }
    }
    /** Read the actual native window front buffers, independently of Minecraft's offscreen target. */
    private static NativeImage presented(ClientGameTestContext context,boolean layer){return context.computeOnClient(c->{
        long previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();
        try{
            if(layer){try{var f=CleanCapture.class.getDeclaredField("outputCaps");f.setAccessible(true);GLFW.glfwMakeContextCurrent(CleanCapture.window());GL.setCapabilities((GLCapabilities)f.get(null));}catch(Exception e){throw new AssertionError(e);}}
            int[] w={0},h={0};GLFW.glfwGetFramebufferSize(layer?CleanCapture.window():previous,w,h);var pixels=MemoryUtil.memAlloc(w[0]*h[0]*4);var out=new NativeImage(w[0],h[0],false);
            int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            int packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            int[] packNames={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
            int[] pack=new int[packNames.length];for(int i=0;i<pack.length;i++)pack[i]=GL11.glGetInteger(packNames[i]);
            if(packBuffer!=0||pack[1]!=0||pack[2]!=0||pack[3]!=0)System.out.println("[streamproof-readback] "+(layer?"private":"main")+" PBO="+packBuffer+" rowLength="+pack[1]+" skipRows="+pack[2]+" skipPixels="+pack[3]+"; restoring after tightly packed read");
            try{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int name:packNames)GL11.glPixelStorei(name,name==GL11.GL_PACK_ALIGNMENT?1:0);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);GL11.glReadBuffer(GL11.GL_FRONT);GL11.glReadPixels(0,0,w[0],h[0],GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                for(int y=0;y<h[0];y++)for(int x=0;x<w[0];x++){int i=(y*w[0]+x)*4;out.setColorArgb(x,h[0]-1-y,(pixels.get(i+3)&255)<<24|(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}return out;
            }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(buffer);for(int i=0;i<pack.length;i++)GL11.glPixelStorei(packNames[i],pack[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);MemoryUtil.memFree(pixels);}
        }finally{GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);}
    });}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void surfaceFailure(ClientGameTestContext context,TestSingleplayerContext world,Streamproof module,Probe probe){
        var previousTarget=context.computeOnClient(c->CleanCapture.framebuffer());
        context.runOnClient(c->c.setScreen(new ClickGuiScreen()));context.waitTicks(5);
        context.runOnClient(c->{
            require(c.currentScreen instanceof ClickGuiScreen,"Fault fixture did not open its private GUI");
            require(CleanCapture.frames()>0&&CleanCapture.window()!=0,"Fault fixture has no valid private surface");
            dev.maro.render.capture.CaptureScopeChecks.failPrivateSurface();
            require(c.currentScreen==null,"Private-surface failure did not release private GUI input");
            require(module.isEnabled(),"Private-surface failure changed the requested Streamproof state");
            require(!CleanCapture.protectedLayer()&&GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_FALSE,"Failed private surface remained visible");
        });context.waitTicks(5);
        long[] frozen=context.computeOnClient(c->new long[]{CleanCapture.frames(),CleanCapture.worldRenders(),probe.draws});
        try(var before=presented(context,false)){
            require(nonblack(before)&&magenta(before)==0&&worldMagenta(before)==0,"Fallback main window was black or contained private markers");
            write(context,before,"maro-streamproof-failed-surface-before-world-change");
            world.getServer().runCommand("fill 7 -60 13 9 -57 13 minecraft:gold_block");context.waitTicks(8);
            try(var after=presented(context,false);var original=image(context,false)){
                write(context,after,"maro-streamproof-failed-surface-live-world");
                require(nonblack(after)&&nonblack(original),"Failed private surface blacked out ordinary gameplay");
                require(magenta(after)==0&&worldMagenta(after)==0&&magenta(original)==0&&worldMagenta(original)==0,"Failed private surface exposed private markers");
                int changed=changedTerrain(before,after);
                require(changed>600,"Fallback native window froze its old capture instead of showing the changed world: "+changed);
                System.out.println("[streamproof-fault] native main window stayed live after private-surface failure: "+changed+" terrain samples changed");
            }
        }
        context.runOnClient(c->{
            require(CleanCapture.frames()==frozen[0]&&CleanCapture.worldRenders()>frozen[1],"Failure fallback kept using the stale clean GUI target or stopped rendering the world");
            require(probe.draws==frozen[2]&&probe.isEnabled(),"Failure fallback rendered private HUD or changed its module state");
            require(CleanCapture.mainTargetOr(c.getFramebuffer())==c.getFramebuffer(),"Failure fallback still presents the stale clean framebuffer");
            require(c.currentScreen==null&&!CapturePass.active()&&GLFW.glfwGetCurrentContext()==c.getWindow().getHandle(),"Failure fallback leaked GUI input, capture scope or GL context");
            module.setEnabled(false);
            require(CleanCapture.window()==0&&CleanCapture.framebuffer()==null,"Failed resources were not released on disable");
            module.setEnabled(true);
        });
        ready(context,()->CleanCapture.frames()>=4&&CleanCapture.privateFrames()>0,"Private surface did not recover after disable/re-enable");
        context.runOnClient(c->{
            require(module.isEnabled()&&CleanCapture.protectedLayer()&&CleanCapture.verifiedLayer()&&CleanCapture.framebuffer()!=previousTarget,"Private surface recovery did not rebuild verified resources");
            require(probe.enabled==1&&probe.disabled==0,"Surface recovery changed generic module lifecycle");
            dev.maro.render.capture.CaptureScopeChecks.aligned();
        });
        try(var local=image(context,false);var clean=image(context,true)){
            require(nonblack(clean)&&magenta(local)>500&&magenta(clean)==0,"Private/clean separation did not recover after rebuilding the surface");
            write(context,local,"maro-streamproof-recovered-local");write(context,clean,"maro-streamproof-recovered-clean");
        }
        System.out.println("[streamproof-fault] PASS: private helper hidden, GUI released, requested state retained, live nonblack ordinary main framebuffer without private markers, disable/re-enable rebuild and recovery");
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var m=ModuleManager.get(Streamproof.class);var pickaxe=ModuleManager.get(SkinAccessories.class);var freecam=ModuleManager.get(FreeCam.class);var esp=ModuleManager.get(BlockESP.class);var savedEsp=SharedModuleConfig.capture(esp);var probe=new Probe();context.runOnClient(c->ModuleManager.register(probe));
        var playerEsp=ModuleManager.get(PlayerESP.class);var savedPlayerEsp=SharedModuleConfig.capture(playerEsp);int[] otherId={0};
        int originalScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 8 -58 8 0 8");context.waitTicks(8);
            world.getServer().runCommand("setblock 8 -59 15 minecraft:diamond_ore");world.getServer().runCommand("fill 7 -60 13 9 -57 13 minecraft:stone");
            context.runOnClient(c->{esp.getSettings().forEach(Setting::reset);esp.clearPicked();esp.pick(Blocks.DIAMOND_ORE);((ModeSetting)setting(esp,"Color")).set("One Color");((ColorSetting)setting(esp,"ESP Color")).set(0xFFFF00FF);((NumberSetting)setting(esp,"Fill Opacity")).set(100d);((BooleanSetting)setting(esp,"ESP Bloom")).set(false);((BooleanSetting)setting(esp,"Tracer Bloom")).set(false);((BooleanSetting)setting(esp,"Tracers")).set(false);esp.setEnabled(true);});
            ready(context,()->!esp.shownPositions().isEmpty(),"Real ESP fixture did not load");
            context.runOnClient(c->{c.setScreen(null);c.player.getInventory().setSelectedSlot(0);c.player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_PICKAXE));pickaxe.select("Driver");pickaxe.setEnabled(true);freecam.setEnabled(true);probe.getBind().set(66);probe.setEnabled(true);m.setEnabled(true);GLFW.glfwFocusWindow(c.getWindow().getHandle());});
            ready(context,()->CleanCapture.frames()>=5,"No clean frames");context.waitTicks(4);
            ready(context,()->CleanCapture.privateFrames()>0&&CleanCapture.mainFrames()>0,"Main-screen presentation did not start");
            context.runOnClient(c->{require(CleanCapture.protectedLayer()&&CleanCapture.verifiedLayer(),"Private layer affinity/ownership/styles unverified");require(GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_MOUSE_PASSTHROUGH)==GLFW.GLFW_TRUE,"Layer intercepts mouse input");dev.maro.render.capture.CaptureScopeChecks.run(probe);});
            context.runOnClient(c->{require(m.isEnabled()&&!pickaxe.isEnabled()&&!freecam.isEnabled()&&probe.isEnabled()&&!CapturePass.active(),"Scene effects were not suspended or capture leaked enabled-state guard");require(probe.enabled==1&&probe.disabled==0&&probe.ticks>0&&probe.draws>0&&probe.getBind().get()==66,"Capture changed generic module lifecycle, tick or bind");require(GLFW.glfwGetCurrentContext()==c.getWindow().getHandle(),"Output GL context leaked into main game");});
            context.runOnClient(c->{
                freecam.setEnabled(true);
                require(!freecam.isEnabled()&&dev.maro.render.capture.StreamproofScenePolicy.configuredEnabled(freecam,false),"Paused FreeCam enabled itself or lost requested state");
                freecam.toggle();require(!dev.maro.render.capture.StreamproofScenePolicy.configuredEnabled(freecam,false),"Paused FreeCam toggle did not clear requested state");
                freecam.toggle();require(!freecam.isEnabled()&&dev.maro.render.capture.StreamproofScenePolicy.configuredEnabled(freecam,false),"Paused FreeCam toggle did not restore requested state safely");
            });
            try(var main=image(context,false);var clean=image(context,true)){
                require(magenta(main)>500,"Private HUD missing from local window");require(magenta(clean)==0,"Private HUD leaked to clean capture");write(context,main,"maro-streamproof-private-view");write(context,clean,"maro-streamproof-clean-view");
                require(worldMagenta(main)>30&&worldMagenta(clean)==0,"Through-wall ESP missing locally or leaked into capture: "+worldMagenta(main)+"/"+worldMagenta(clean));
                int diverse=0;for(int y=clean.getHeight()/4;y<clean.getHeight();y+=13)for(int x=0;x<clean.getWidth();x+=13)if((clean.getColorArgb(x,y)&0xFFFFFF)!=0)diverse++;
                require(diverse>150,"Capture is black instead of gameplay");
            }
            try(var main=presented(context,false);var layer=presented(context,true)){
                require(magenta(main)==0&&magenta(layer)>500,"Native main/layer presentation mismatch: "+magenta(main)+"/"+magenta(layer));
                write(context,main,"maro-streamproof-native-main");write(context,layer,"maro-streamproof-native-private-layer");
            }
            var captureResults=new java.util.ArrayList<String>();
            context.runOnClient(c->{
                ((BooleanSetting)setting(esp,"ESP Bloom")).set(true);((BooleanSetting)setting(esp,"Tracer Bloom")).set(true);((BooleanSetting)setting(esp,"Tracers")).set(true);
                ((ModeSetting)setting(esp,"Tracer Color")).set("Custom");((ColorSetting)setting(esp,"Tracer Custom")).set(0xFFFF00FF);
                var other=new net.minecraft.client.network.OtherClientPlayerEntity(c.world,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MaroCaptureFixture"));
                other.setPosition(8.8,-59,16);other.lastX=other.lastRenderX=other.getX();other.lastY=other.lastRenderY=other.getY();other.lastZ=other.lastRenderZ=other.getZ();other.setNoGravity(true);c.world.addEntity(other);otherId[0]=other.getId();
                playerEsp.getSettings().forEach(Setting::reset);((ModeSetting)setting(playerEsp,"Fill Style")).set("Solid");((ColorSetting)setting(playerEsp,"Color")).set(0xFF00FFFF);((NumberSetting)setting(playerEsp,"Fill Opacity")).set(100d);((NumberSetting)setting(playerEsp,"Edge Fade")).set(0d);
                ((ModeSetting)setting(playerEsp,"Tracer Color")).set("Custom");((ColorSetting)setting(playerEsp,"Tracer Custom")).set(0xFF00FFFF);playerEsp.setEnabled(true);
            });context.waitTicks(8);
            try(var local=image(context,false);var clean=image(context,true)){
                write(context,local,"maro-streamproof-local-bloom-tracers-player-esp");write(context,clean,"maro-streamproof-clean-bloom-tracers-player-esp");
                require(worldMagenta(local)>30&&worldMagenta(clean)==0,"Bloom/tracer ESP missing locally or leaked into clean gameplay");
                require(worldCyan(local)>100&&worldCyan(clean)==0,"Player silhouette/glow/tracers missing locally or leaked: "+worldCyan(local)+"/"+worldCyan(clean));
            }
            context.runOnClient(c->{playerEsp.setEnabled(false);c.world.removeEntity(otherId[0],net.minecraft.entity.Entity.RemovalReason.DISCARDED);otherId[0]=0;});
            context.runOnClient(c->{try{java.nio.file.Files.write(c.runDirectory.toPath().resolve("screenshots/streamproof-capture-methods.txt"),captureResults);}catch(Exception error){throw new AssertionError(error);}});
            var gui=context.computeOnClient(c->{var s=new ClickGuiScreen();c.setScreen(s);return s;});long before=CleanCapture.frames();ready(context,()->CleanCapture.frames()>=before+4,"Menu capture stopped");
            context.runOnClient(c->require(c.currentScreen==gui,"Private menu was not restored"));context.takeScreenshot("maro-streamproof-private-menu");try(var clean=image(context,true)){require(magenta(clean)==0,"HUD leaked while menu open");write(context,clean,"maro-streamproof-clean-with-menu-open");}
            if(Boolean.getBoolean("maro.gametest.captureReview")){
                Path reviewAck=context.computeOnClient(c->c.runDirectory.toPath().resolve("streamproof-review.done"));
                try{java.nio.file.Files.deleteIfExists(reviewAck);}catch(Exception error){throw new AssertionError(error);}
                try(var local=image(context,false);var clean=image(context,true)){write(context,local,"maro-streamproof-review-local-menu");write(context,clean,"maro-streamproof-review-clean-menu");}
                context.runOnClient(c->{
                    GLFW.glfwFocusWindow(c.getWindow().getHandle());
                    System.out.println("[streamproof-review] READY "+dev.maro.render.capture.CaptureScopeChecks.windowLabel()+"; native foreground is own="+dev.maro.render.capture.CaptureScopeChecks.ownsForeground()+"; private menu visible; review hold up to2400ticks; write run/streamproof-review.done to continue");
                });
                for(int i=0;i<24;i++){context.waitTicks(100);if(java.nio.file.Files.exists(reviewAck))break;}
                try{java.nio.file.Files.deleteIfExists(reviewAck);}catch(Exception error){throw new AssertionError(error);}
                System.out.println("[streamproof-review] COMPLETE");
            }
            if(Boolean.getBoolean("maro.gametest.skipNativeCapture")){
                captureResults.add("OS display/window capture: NOT TESTED; desktop capture was stopped by the user");
            }else{
                captureResults.add(nativeCapture(context,true,"maro-streamproof-display-client-area"));
                captureResults.add(nativeCapture(context,false,"maro-streamproof-printwindow-client-area"));
            }
            context.runOnClient(c->c.setScreen(m.panel(new ClickGuiScreen())));context.waitTicks(4);context.takeScreenshot("maro-streamproof-setup");
            var inventory=context.computeOnClient(c->{c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));return c.currentScreen;});context.waitTicks(4);long inventoryFrame=CleanCapture.frames();ready(context,()->CleanCapture.frames()>inventoryFrame,"Inventory capture stopped");try(var clean=image(context,true)){write(context,clean,"maro-streamproof-vanilla-inventory");}context.runOnClient(c->require(c.currentScreen==inventory,"Vanilla inventory was not preserved"));
            context.runOnClient(c->c.setScreen(null));
            long[] beforeFrames=context.computeOnClient(c->new long[]{CleanCapture.frames(),CleanCapture.worldRenders()});
            context.waitTicks(20);
            long[] afterFrames=context.computeOnClient(c->new long[]{CleanCapture.frames(),CleanCapture.worldRenders()});
            long produced=afterFrames[0]-beforeFrames[0],worldDraws=afterFrames[1]-beforeFrames[1];
            require(produced>0&&worldDraws>0&&worldDraws<=produced+1,"Stream Proof rendered the world more than once per frame: "+worldDraws+" renders / "+produced+" frames");
            context.runOnClient(c->{require(CleanCapture.verifiedLayer(),"Private layer lost Windows capture exclusion");require(CleanCapture.privateFrames()>=CleanCapture.frames()-1,"Private presentation stopped while clean GUI continued");});
            for(int guiScale:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new ClickGuiScreen());});context.waitTicks(5);
                try(var clean=image(context,true)){require(magenta(clean)==0,"Private HUD leaked at GUI scale "+guiScale);write(context,clean,"maro-streamproof-clean-scale-"+guiScale);}
            }
            context.runOnClient(c->{c.setScreen(null);c.options.getGuiScale().setValue(originalScale);c.onResolutionChanged();});
            var reload=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(c->reload.set(c.reloadResources()));
            for(int i=0;i<600&&!reload.get().isDone();i++)context.waitTick();
            require(reload.get().isDone(),"Resource reload did not finish during Stream Proof");reload.get().join();
            long reloaded=CleanCapture.frames();int reloadedDraws=probe.draws;
            ready(context,()->dev.maro.render.capture.StreamproofScenePolicy.ready()&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null&&CleanCapture.frames()>=reloaded+4&&probe.draws>=reloadedDraws+4,"Private HUD did not recover after resource reload and overlay fade");
            context.runOnClient(c->{require(m.isEnabled()&&CleanCapture.verifiedLayer(),"Reload lost Stream Proof or exclusion");c.getWindow().toggleFullscreen();});context.waitTicks(6);
            context.runOnClient(c->require(!c.getWindow().isFullscreen()&&m.isEnabled(),"Exclusive fullscreen was not safely returned to supported windowed mode"));
            captureResults.add("Exclusive fullscreen request: safely reverted to windowed mode; exclusive fullscreen capture is unsupported");
            int[] originalX={0},originalY={0};context.runOnClient(c->GLFW.glfwGetWindowPos(c.getWindow().getHandle(),originalX,originalY));
            for(int offset:new int[]{24,-12,0}){
                context.runOnClient(c->GLFW.glfwSetWindowPos(c.getWindow().getHandle(),originalX[0]+offset,originalY[0]+offset));context.waitTicks(3);
                context.runOnClient(c->{dev.maro.render.capture.CaptureScopeChecks.aligned();require(CleanCapture.verifiedLayer(),"Moving lost capture exclusion");});
            }
            for(int[] size:new int[][]{{960,540},{1280,720},{1100,640}}){
                int resizeDraws=probe.draws;
                context.getInput().resizeWindow(size[0],size[1]);ready(context,()->dev.maro.render.capture.StreamproofScenePolicy.ready()&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null&&probe.draws>=resizeDraws+4&&CleanCapture.framebuffer()!=null&&CleanCapture.framebuffer().textureWidth==size[0]&&CleanCapture.framebuffer().textureHeight==size[1],"Capture resize or private HUD stale");context.waitTicks(4);
                context.runOnClient(c->dev.maro.render.capture.CaptureScopeChecks.aligned());
                for(int frame=0;frame<4;frame++){
                    context.waitTicks(1);
                    try(var layer=presented(context,true)){
                        float scale=context.computeOnClient(c->(float)c.getWindow().getScaleFactor());int first=layer.getColorArgb(Math.round(88*scale),Math.round(16*scale));
                        if(!rgbNear(first,0xFF00BFFF,3)&&!rgbNear(first,0xFFFFA000,3)){
                            write(context,layer,"maro-streamproof-mixed-private-front");
                            try(var local=image(context,false)){write(context,local,"maro-streamproof-mixed-local-framebuffer");}
                            throw new AssertionError("Private native stamp is not the expected blue/orange palette after resize: requested="+size[0]+"x"+size[1]+", native="+layer.getWidth()+"x"+layer.getHeight()+", scale="+scale+", first=0x"+Integer.toHexString(first));
                        }
                        for(int x:new int[]{88,104,120,136})for(int y:new int[]{16,24,32}){
                            int actual=layer.getColorArgb(Math.round(x*scale),Math.round(y*scale));
                            if(!rgbNear(actual,first,3)){
                                String diagnostic=context.computeOnClient(c->"requested="+size[0]+"x"+size[1]+", native="+layer.getWidth()+"x"+layer.getHeight()+", sample="+x+","+y+", scale="+scale+", guiScale="+c.options.getGuiScale().getValue()+", mainFramebuffer="+c.getFramebuffer().textureWidth+"x"+c.getFramebuffer().textureHeight+", expected=0x"+Integer.toHexString(first)+", actual=0x"+Integer.toHexString(actual)+", ready="+dev.maro.render.capture.StreamproofScenePolicy.ready()+", overlay="+(c.getOverlay()==null?"none":c.getOverlay().getClass().getSimpleName())+", probeDraws="+probe.draws);
                                write(context,layer,"maro-streamproof-mixed-private-front");
                                try(var local=image(context,false)){write(context,local,"maro-streamproof-mixed-local-framebuffer");}
                                throw new AssertionError("Mixed frame in private native output after resize: "+diagnostic);
                            }
                        }
                        require(magenta(layer)>500,"Private HUD vanished after resize");
                    }
                }
            }
            context.runOnClient(c->GLFW.glfwHideWindow(c.getWindow().getHandle()));context.waitTicks(4);
            context.runOnClient(c->{require(GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_FALSE,"Private layer stayed visible after hiding the game");GLFW.glfwShowWindow(c.getWindow().getHandle());GLFW.glfwFocusWindow(c.getWindow().getHandle());});
            ready(context,()->GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,"Private view did not return with the game");
            context.runOnClient(c->dev.maro.render.capture.CaptureScopeChecks.aligned());
            surfaceFailure(context,world,m,probe);
            captureResults.add("Injected private-surface failure: helper hidden, GUI released, ordinary native main output remained live/nonblack/private-marker-free, state retained and resources recovered after disable/re-enable");
            context.runOnClient(c->GLFW.glfwSetWindowShouldClose(CleanCapture.window(),true));ready(context,()->!m.isEnabled(),"Closing private layer did not stop capture");context.runOnClient(c->require(CleanCapture.window()==0&&CleanCapture.framebuffer()==null&&!CleanCapture.protectedLayer()&&!CapturePass.active()&&probe.isEnabled()&&pickaxe.isEnabled()&&freecam.isEnabled(),"Capture resources/guard leaked or suspended scene effects were not restored on close"));
            context.runOnClient(c->{try{java.nio.file.Files.write(c.runDirectory.toPath().resolve("screenshots/streamproof-capture-methods.txt"),captureResults);}catch(Exception error){throw new AssertionError(error);}});
            System.out.println("[streamproof] PASS: one world render per frame ("+worldDraws+"/"+produced+"), nonblack GPU/native clean output excludes private HUD/ESP; native desktop and PrintWindow outcomes separately recorded (desktop unavailable when test window is occluded); menus hidden and vanilla inventory preserved, cosmetics/FreeCam suspended then restored, generic module lifecycle/ticks/binds unchanged, context restored, scales 2/3/4, resource reload, exclusive fullscreen request safely returned to windowed, native alignment after moves/resizes, uniform readbacks, hide/show, surface-failure recovery and cleanup. OBS/Discord/WGC/DXGI not tested by this suite.");
        }finally{context.runOnClient(c->{m.setEnabled(false);probe.setEnabled(false);pickaxe.setEnabled(false);freecam.setEnabled(false);esp.setEnabled(false);playerEsp.setEnabled(false);if(otherId[0]!=0&&c.world!=null)c.world.removeEntity(otherId[0],net.minecraft.entity.Entity.RemovalReason.DISCARDED);SharedModuleConfig.apply(esp,savedEsp);SharedModuleConfig.apply(playerEsp,savedPlayerEsp);c.options.getGuiScale().setValue(originalScale);c.onResolutionChanged();c.setScreen(null);});context.getInput().resizeWindow(1280,720);}
        ScenePolicyChecks.run(context);
    }
}
