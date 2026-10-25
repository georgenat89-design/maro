package dev.maro.render.capture;

import dev.maro.Maro;
import dev.maro.module.impl.misc.Streamproof;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.text.Text;

/** Minecraft presents clean pixels; private pixels stay in an excluded layer attached to its window. */
public final class CleanCapture {
    private static SimpleFramebuffer target,annotations;
    private static boolean cleanGui,frameActive,annotated,failed,safeFallback;
    private static int privateWorldDepth;
    private static Screen fallbackTitle;
    private static ProgressScreen preparationScreen;
    private static long worldRenders;
    private static long window,frames,privateFrames,mainFrames;
    private static GLCapabilities outputCaps;
    private static int readFramebuffer;
    private static int alignedX=Integer.MIN_VALUE,alignedY,alignedW,alignedH;
    private static boolean protectedLayer,visible;
    private static String status="Off";
    private CleanCapture(){}
    public static String status(){return status;}
    public static long frames(){return frames;}
    public static long window(){return window;}
    public static Framebuffer framebuffer(){return target;}
    public static boolean protectedLayer(){return protectedLayer;}
    public static long privateFrames(){return privateFrames;}
    public static long mainFrames(){return mainFrames;}
    public static boolean verifiedLayer(){return CaptureWindows.privateLayer(window,MinecraftClient.getInstance().getWindow().getHandle());}
    private static boolean mayShow(MinecraftClient mc){
        long main=mc.getWindow().getHandle();
        return mc.isWindowFocused()&&GLFW.glfwGetWindowAttrib(main,GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE
            &&GLFW.glfwGetWindowAttrib(main,GLFW.GLFW_ICONIFIED)==GLFW.GLFW_FALSE;
    }
    /** Also runs when Minecraft skips its world renderer while hidden/minimized. */
    public static void syncVisibility(){
        if(window!=0&&!mayShow(MinecraftClient.getInstance())){if(visible)GLFW.glfwHideWindow(window);visible=false;}
    }
    public static Framebuffer targetOr(Framebuffer original){return privateWorldDepth>0&&annotations!=null?annotations:cleanGui&&target!=null?target:original;}
    public static boolean activeFrame(){return frameActive;}
    public static long worldRenders(){return worldRenders;}
    public static void worldRendered(){if(frameActive)worldRenders++;}
    public static boolean localEnabled(dev.maro.module.Module module){
        if(frameActive&&(!protectedLayer||!StreamproofScenePolicy.ready()))return false;
        if(!CapturePass.active())return module.isEnabled();
        boolean clean=CapturePass.pause();try{return module.isEnabled();}finally{CapturePass.resume(clean);}
    }
    /** Private annotations use the world's existing matrices/depth, never another world render. */
    public static boolean beginPrivateWorld(){
        if(!frameActive||!protectedLayer||annotations==null||!StreamproofScenePolicy.ready())return false;
        try{
            if(privateWorldDepth==0){annotations.copyDepthFrom(MinecraftClient.getInstance().getFramebuffer());annotated=true;}
            boolean clean=CapturePass.pause();privateWorldDepth++;return clean;
        }catch(Throwable error){
            unavailable(error,true);
            if(!CapturePass.active())CapturePass.begin();
            return false;
        }
    }
    public static void endPrivateWorld(boolean clean){if(frameActive&&privateWorldDepth>0)privateWorldDepth--;CapturePass.resume(clean);}
    /** Redirect only Minecraft's final window blit, preserving its private framebuffer. */
    public static Framebuffer mainTargetOr(Framebuffer original){var module=Streamproof.get();if(module!=null&&module.isEnabled()&&!(failed&&safeFallback)&&target!=null&&frames>0){mainFrames++;return target;}return original;}
    /** Keep gameplay available if the private presentation surface cannot be used. */
    private static void unavailable(Throwable error,boolean ordinaryFrame){
        failSurface(ordinaryFrame);
        Maro.LOGGER.error("Private layer unavailable; keeping ordinary gameplay visible",error);
    }
    private static void failSurface(boolean ordinaryFrame){
        failed=true;safeFallback=ordinaryFrame;protectedLayer=false;privateWorldDepth=0;annotated=false;
        if(window!=0){try{GLFW.glfwHideWindow(window);}catch(Throwable ignored){}}visible=false;
        var mc=MinecraftClient.getInstance();
        if(mc.currentScreen!=null&&mc.currentScreen.getClass().getName().startsWith("dev.maro."))mc.setScreen(null);
        status="Private layer unavailable; client visuals hidden";
    }
    private static void create(MinecraftClient mc){
        if(!CaptureWindows.supported())throw new IllegalStateException("Main-screen Streamproof requires Windows capture exclusion");
        long main=mc.getWindow().getHandle(),previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API,GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_MOUSE_PASSTHROUGH,GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,GLFW.glfwGetWindowAttrib(main,GLFW.GLFW_CONTEXT_VERSION_MAJOR));
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,GLFW.glfwGetWindowAttrib(main,GLFW.GLFW_CONTEXT_VERSION_MINOR));
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.glfwGetWindowAttrib(main,GLFW.GLFW_OPENGL_PROFILE));
        window=GLFW.glfwCreateWindow(Math.max(1,mc.getWindow().getWidth()),Math.max(1,mc.getWindow().getHeight()),"Minecraft private render layer",0,main);
        GLFW.glfwDefaultWindowHints();
        if(window==0)throw new IllegalStateException("Could not create the private layer");
        if(!(protectedLayer=CaptureWindows.prepare(window,main)))throw new IllegalStateException("Windows could not verify capture exclusion");
        try{GLFW.glfwMakeContextCurrent(window);outputCaps=GL.createCapabilities();GLFW.glfwSwapInterval(0);readFramebuffer=GL30.glGenFramebuffers();}
        finally{GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);}
        align(mc);status="Private overlay ready · verify your capture preview";
    }
    private static void align(MinecraftClient mc){
        var area=CaptureWindows.clientArea(mc.getWindow().getHandle());
        if(alignedX==area.x()&&alignedY==area.y()&&alignedW==area.width()&&alignedH==area.height())return;
        CaptureWindows.align(window,area.x(),area.y(),area.width(),area.height());
        alignedX=area.x();alignedY=area.y();alignedW=area.width();alignedH=area.height();
    }
    public static void before(RenderTickCounter ticks,boolean tick){
        var module=Streamproof.get();if(module==null||!module.isEnabled())return;
        var mc=MinecraftClient.getInstance();
        try{
            if(failed){safeFallback=true;frameActive=true;CapturePass.begin();return;}
            if(mc.getWindow().isFullscreen()){mc.getWindow().toggleFullscreen();status="Exclusive fullscreen paused for capture exclusion";}
            if(window==0)create(mc);
            if(GLFW.glfwWindowShouldClose(window)){module.setEnabled(false);return;}
            align(mc);
            int w=mc.getWindow().getFramebufferWidth(),h=mc.getWindow().getFramebufferHeight();if(w<1||h<1)return;
            if(target==null)target=new SimpleFramebuffer("Clean gameplay capture",w,h,true);else if(target.textureWidth!=w||target.textureHeight!=h)target.resize(w,h);
            if(annotations==null)annotations=new SimpleFramebuffer("Private world annotations",w,h,true);else if(annotations.textureWidth!=w||annotations.textureHeight!=h)annotations.resize(w,h);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(annotations.getColorAttachment(),0,annotations.getDepthAttachment(),1);
            // Frame-driven gameplay (for example aim updates) still runs exactly once.
            // The later world hook is suppressed by the clean render scope.
            if(tick&&mc.world!=null&&mc.player!=null)dev.maro.runtime.RuntimeEvents.worldFrame();
            frameActive=true;annotated=false;privateWorldDepth=0;CapturePass.begin();
            if(frames==0)((dev.maro.mixin.CleanCaptureMixins.Lighting)mc.gameRenderer.getLightmapTextureManager()).maro$dirtyCaptureLighting(true);
        }catch(Throwable error){CapturePass.end();unavailable(error,true);frameActive=true;CapturePass.begin();}
    }
    /** One world, two inexpensive GUI passes: ordinary HUD to capture, private GUI to the local layer. */
    public static void guiBegin(GuiRenderState state,GuiRenderer renderer,FogRenderer fog,RenderTickCounter ticks,boolean tick){
        if(!frameActive||failed||target==null)return;
        var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();
        try{
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorAttachment(),target.getColorAttachment(),0,0,0,0,0,main.textureWidth,main.textureHeight);
            encoder.clearDepthTexture(target.getDepthAttachment(),1);
            cleanGui=true;state.clear();
            int mx=(int)mc.mouse.getScaledX(mc.getWindow()),my=(int)mc.mouse.getScaledY(mc.getWindow());
            var context=new DrawContext(mc,state,mx,my);
            if(!StreamproofScenePolicy.ready()){
                // Old atlas/chunk data can survive until a native resource reload commits.
                // Present the ordinary loading UI during this bounded preparation phase.
                if(preparationScreen==null){preparationScreen=new ProgressScreen(false);preparationScreen.setTitle(Text.literal("Reloading resources"));preparationScreen.setTask(Text.literal("Please wait..."));}
                if(preparationScreen.width!=mc.getWindow().getScaledWidth()||preparationScreen.height!=mc.getWindow().getScaledHeight())preparationScreen.init(mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight());
                context.fill(0,0,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),0xFF101010);
                preparationScreen.render(context,mx,my,ticks.getDynamicDeltaTicks());
            }else{
            if(mc.world!=null&&mc.player!=null&&tick)mc.inGameHud.render(context,ticks);
            Screen screen=mc.currentScreen;
            if(screen!=null&&screen.getClass().getName().startsWith("dev.maro."))screen=null;
            if(mc.world==null&&screen==null){
                if(fallbackTitle==null)fallbackTitle=new TitleScreen();
                if(fallbackTitle.width!=mc.getWindow().getScaledWidth()||fallbackTitle.height!=mc.getWindow().getScaledHeight())fallbackTitle.init(mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight());
                screen=fallbackTitle;
            }
            if(screen!=null&&!(screen instanceof net.minecraft.client.gui.screen.ChatScreen&&Streamproof.hideChat()))screen.renderWithTooltip(context,mx,my,ticks.getDynamicDeltaTicks());
            }
            context.drawDeferredElements();renderer.render(fog.getFogBuffer(FogRenderer.FogType.NONE));renderer.incrementFrame();
            frames++;
        }catch(Throwable error){unavailable(error,true);}
        finally{cleanGui=false;state.clear();if(protectedLayer&&StreamproofScenePolicy.ready())CapturePass.end();}
        if(protectedLayer&&annotated)OverlayComposite.draw(annotations,main);
        dev.maro.gui.render.HandPlacementRender.afterCleanGui();
    }
    public static void present(){
        CapturePass.end();frameActive=false;
        if(!protectedLayer||window==0||target==null||frames==0)return;
        var mc=MinecraftClient.getInstance();
        if(!mayShow(mc)){syncVisibility();return;}
        long previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();long fence=0,consumed=0;boolean failed=false;
        try{
            var source=mc.getFramebuffer();fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);GL11.glFlush();
            GLFW.glfwMakeContextCurrent(window);GL.setCapabilities(outputCaps);GL32.glWaitSync(fence,0,GL32.GL_TIMEOUT_IGNORED);GL32.glDeleteSync(fence);fence=0;
            int[] w={0},h={0};GLFW.glfwGetFramebufferSize(window,w,h);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer);GL32.glFramebufferTexture(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,((GlTexture)source.getColorAttachment()).getGlId(),0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Clean capture framebuffer is incomplete");
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBlitFramebuffer(0,0,source.textureWidth,source.textureHeight,0,0,w[0],h[0],GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            // The game must not overwrite its shared texture while this context still reads it.
            // A producer fence alone only orders main -> layer, leaving the next main frame racing.
            consumed=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);GL11.glFlush();
            GLFW.glfwSwapBuffers(window);privateFrames++;
            if(!visible){GLFW.glfwShowWindow(window);CaptureWindows.align(window,alignedX,alignedY,alignedW,alignedH);visible=true;}
        }catch(Throwable error){status="Capture stopped: output failed";failed=true;Maro.LOGGER.error("Clean capture output failed",error);}
        finally{
            if(fence!=0)GL32.glDeleteSync(fence);
            GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);
            if(consumed!=0){GL32.glWaitSync(consumed,0,GL32.GL_TIMEOUT_IGNORED);GL32.glDeleteSync(consumed);}
        }
        if(failed)unavailable(new IllegalStateException("Private presentation failed"),false);
    }
    public static void close(){
        if(window!=0){long previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();try{if(outputCaps!=null){GLFW.glfwMakeContextCurrent(window);GL.setCapabilities(outputCaps);if(readFramebuffer!=0)GL30.glDeleteFramebuffers(readFramebuffer);}}finally{GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);GLFW.glfwDestroyWindow(window);window=0;outputCaps=null;readFramebuffer=0;}}
        if(target!=null){target.delete();target=null;}if(annotations!=null){annotations.delete();annotations=null;}OverlayComposite.close();CapturePass.end();frameActive=cleanGui=annotated=failed=safeFallback=false;privateWorldDepth=0;fallbackTitle=null;preparationScreen=null;protectedLayer=visible=false;frames=privateFrames=mainFrames=worldRenders=0;alignedX=Integer.MIN_VALUE;
        if(!status.startsWith("Capture stopped"))status="Off";
    }
}
