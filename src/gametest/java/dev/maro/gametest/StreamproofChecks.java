package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.module.impl.visuals.BlockESP;
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
    /** Read the actual native window front buffers, independently of Minecraft's offscreen target. */
    private static NativeImage presented(ClientGameTestContext context,boolean layer){return context.computeOnClient(c->{
        long previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();
        try{
            if(layer){try{var f=CleanCapture.class.getDeclaredField("outputCaps");f.setAccessible(true);GLFW.glfwMakeContextCurrent(CleanCapture.window());GL.setCapabilities((GLCapabilities)f.get(null));}catch(Exception e){throw new AssertionError(e);}}
            int[] w={0},h={0};GLFW.glfwGetFramebufferSize(layer?CleanCapture.window():previous,w,h);var pixels=MemoryUtil.memAlloc(w[0]*h[0]*4);var out=new NativeImage(w[0],h[0],false);
            int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);GL11.glReadBuffer(GL11.GL_FRONT);GL11.glReadPixels(0,0,w[0],h[0],GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                for(int y=0;y<h[0];y++)for(int x=0;x<w[0];x++){int i=(y*w[0]+x)*4;out.setColorArgb(x,h[0]-1-y,(pixels.get(i+3)&255)<<24|(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}return out;
            }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(buffer);MemoryUtil.memFree(pixels);}
        }finally{GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);}
    });}
    private static Setting<?> setting(BlockESP module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var m=ModuleManager.get(Streamproof.class);var pickaxe=ModuleManager.get(SkinAccessories.class);var esp=ModuleManager.get(BlockESP.class);var savedEsp=SharedModuleConfig.capture(esp);var probe=new Probe();context.runOnClient(c->ModuleManager.register(probe));
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 8 -58 8 0 8");context.waitTicks(8);
            world.getServer().runCommand("setblock 8 -59 15 minecraft:diamond_ore");world.getServer().runCommand("fill 7 -60 13 9 -57 13 minecraft:stone");
            context.runOnClient(c->{esp.getSettings().forEach(Setting::reset);esp.clearPicked();esp.pick(Blocks.DIAMOND_ORE);((ModeSetting)setting(esp,"Color")).set("One Color");((ColorSetting)setting(esp,"ESP Color")).set(0xFFFF00FF);((NumberSetting)setting(esp,"Fill Opacity")).set(100d);((BooleanSetting)setting(esp,"ESP Bloom")).set(false);((BooleanSetting)setting(esp,"Tracer Bloom")).set(false);((BooleanSetting)setting(esp,"Tracers")).set(false);esp.setEnabled(true);});
            ready(context,()->!esp.shownPositions().isEmpty(),"Real ESP fixture did not load");
            context.runOnClient(c->{c.setScreen(null);c.player.getInventory().setSelectedSlot(0);c.player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_PICKAXE));pickaxe.select("Driver");pickaxe.setEnabled(true);probe.getBind().set(66);probe.setEnabled(true);m.setEnabled(true);GLFW.glfwFocusWindow(c.getWindow().getHandle());});
            ready(context,()->CleanCapture.frames()>=5,"No clean frames");context.waitTicks(4);
            ready(context,()->CleanCapture.privateFrames()>0&&CleanCapture.mainFrames()>0,"Main-screen presentation did not start");
            context.runOnClient(c->{require(CleanCapture.protectedLayer()&&CleanCapture.verifiedLayer(),"Private layer affinity/ownership/styles unverified");require(GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_MOUSE_PASSTHROUGH)==GLFW.GLFW_TRUE,"Layer intercepts mouse input");dev.maro.render.capture.CaptureScopeChecks.run(probe);});
            context.runOnClient(c->{require(m.isEnabled()&&pickaxe.isEnabled()&&probe.isEnabled()&&!CapturePass.active(),"Capture leaked enabled-state guard");require(probe.enabled==1&&probe.disabled==0&&probe.ticks>0&&probe.draws>0&&probe.getBind().get()==66,"Capture changed module lifecycle, tick or bind");require(GLFW.glfwGetCurrentContext()==c.getWindow().getHandle(),"Output GL context leaked into main game");});
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
            var gui=context.computeOnClient(c->{var s=new ClickGuiScreen();c.setScreen(s);return s;});long before=CleanCapture.frames();ready(context,()->CleanCapture.frames()>=before+4,"Menu capture stopped");
            context.runOnClient(c->require(c.currentScreen==gui,"Private menu was not restored"));context.takeScreenshot("maro-streamproof-private-menu");try(var clean=image(context,true)){require(magenta(clean)==0,"HUD leaked while menu open");write(context,clean,"maro-streamproof-clean-with-menu-open");}
            context.runOnClient(c->c.setScreen(m.panel(new ClickGuiScreen())));context.waitTicks(4);context.takeScreenshot("maro-streamproof-setup");
            var inventory=context.computeOnClient(c->{c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));return c.currentScreen;});context.waitTicks(4);long inventoryFrame=CleanCapture.frames();ready(context,()->CleanCapture.frames()>inventoryFrame,"Inventory capture stopped");try(var clean=image(context,true)){write(context,clean,"maro-streamproof-vanilla-inventory");}context.runOnClient(c->require(c.currentScreen==inventory,"Vanilla inventory was not preserved"));
            context.runOnClient(c->{c.setScreen(null);((NumberSetting)m.getSettings().stream().filter(s->s.getName().equals("Capture FPS")).findFirst().orElseThrow()).set(15d);});
            long count=CleanCapture.frames(),start=System.nanoTime();context.waitTicks(20);long elapsed=System.nanoTime()-start,produced=CleanCapture.frames()-count;require(produced>0&&produced<=elapsed*15/1_000_000_000L+3,"Capture FPS limiter failed");
            context.runOnClient(c->{require(CleanCapture.verifiedLayer(),"Private layer lost Windows capture exclusion");require(CleanCapture.privateFrames()>=CleanCapture.frames(),"Private view was limited to capture FPS");});
            int[] originalX={0},originalY={0};context.runOnClient(c->GLFW.glfwGetWindowPos(c.getWindow().getHandle(),originalX,originalY));
            for(int offset:new int[]{24,-12,0}){
                context.runOnClient(c->GLFW.glfwSetWindowPos(c.getWindow().getHandle(),originalX[0]+offset,originalY[0]+offset));context.waitTicks(3);
                context.runOnClient(c->{dev.maro.render.capture.CaptureScopeChecks.aligned();require(CleanCapture.verifiedLayer(),"Moving lost capture exclusion");});
            }
            for(int[] size:new int[][]{{960,540},{1280,720},{1100,640}}){
                context.getInput().resizeWindow(size[0],size[1]);ready(context,()->CleanCapture.framebuffer()!=null&&CleanCapture.framebuffer().textureWidth==size[0]&&CleanCapture.framebuffer().textureHeight==size[1],"Capture resize stale");context.waitTicks(4);
                context.runOnClient(c->dev.maro.render.capture.CaptureScopeChecks.aligned());
                for(int frame=0;frame<4;frame++){
                    context.waitTicks(1);
                    try(var layer=presented(context,true)){
                        float scale=context.computeOnClient(c->(float)c.getWindow().getScaleFactor());int first=layer.getColorArgb(Math.round(88*scale),Math.round(16*scale));
                        for(int x:new int[]{88,104,120,136})for(int y:new int[]{16,24,32})require(layer.getColorArgb(Math.round(x*scale),Math.round(y*scale))==first,"Mixed frame in private native output after resize");
                        require(magenta(layer)>500,"Private HUD vanished after resize");
                    }
                }
            }
            context.runOnClient(c->GLFW.glfwHideWindow(c.getWindow().getHandle()));context.waitTicks(4);
            context.runOnClient(c->{require(GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_FALSE,"Private layer stayed visible after hiding the game");GLFW.glfwShowWindow(c.getWindow().getHandle());GLFW.glfwFocusWindow(c.getWindow().getHandle());});
            ready(context,()->GLFW.glfwGetWindowAttrib(CleanCapture.window(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,"Private view did not return with the game");
            context.runOnClient(c->dev.maro.render.capture.CaptureScopeChecks.aligned());
            context.runOnClient(c->GLFW.glfwSetWindowShouldClose(CleanCapture.window(),true));ready(context,()->!m.isEnabled(),"Closing private layer did not stop capture");context.runOnClient(c->require(CleanCapture.window()==0&&CleanCapture.framebuffer()==null&&!CleanCapture.protectedLayer()&&!CapturePass.active()&&probe.isEnabled(),"Capture resources or guard leaked on close"));
            System.out.println("[streamproof] PASS: real nonblack GPU clean view excludes private HUD and through-wall ESP, local HUD/ESP/pickaxe retained, private menu hidden and vanilla inventory preserved, module lifecycle/ticks/binds unchanged, GL context restored, 15 FPS cap, native client-area alignment after moves and three resizes, twelve uniform-frame readbacks, hide/show recovery, owned click-through exclusion and cleanup");
        }finally{context.runOnClient(c->{m.setEnabled(false);probe.setEnabled(false);pickaxe.setEnabled(false);esp.setEnabled(false);SharedModuleConfig.apply(esp,savedEsp);c.setScreen(null);});context.getInput().resizeWindow(1280,720);}
    }
}
