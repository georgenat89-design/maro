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

/** Minecraft presents clean pixels; private pixels stay in an excluded layer attached to its window. */
public final class CleanCapture {
    private static SimpleFramebuffer target;
    private static long window,lastFrame,frames,privateFrames,mainFrames;
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
    public static Framebuffer targetOr(Framebuffer original){return CapturePass.active()&&target!=null?target:original;}
    /** Redirect only Minecraft's final window blit, preserving its private framebuffer. */
    public static Framebuffer mainTargetOr(Framebuffer original){var module=Streamproof.get();if(module!=null&&module.isEnabled()&&protectedLayer&&target!=null&&frames>0){mainFrames++;return target;}return original;}
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
        align(mc);status="Main screen protected · share Minecraft normally";
    }
    private static void align(MinecraftClient mc){
        var area=CaptureWindows.clientArea(mc.getWindow().getHandle());
        if(alignedX==area.x()&&alignedY==area.y()&&alignedW==area.width()&&alignedH==area.height())return;
        CaptureWindows.align(window,area.x(),area.y(),area.width(),area.height());
        alignedX=area.x();alignedY=area.y();alignedW=area.width();alignedH=area.height();
    }
    public static void before(RenderTickCounter ticks,boolean tick){
        if(CapturePass.active())return;var module=Streamproof.get();if(module==null||!module.isEnabled())return;
        var mc=MinecraftClient.getInstance();
        try{
            if(window==0)create(mc);
            if(GLFW.glfwWindowShouldClose(window)){module.setEnabled(false);return;}
            align(mc);
            long now=System.nanoTime(),period=1_000_000_000L/module.captureFps();if(now<lastFrame)return;lastFrame=lastFrame==0||now-lastFrame>period?now+period:lastFrame+period;
            int w=mc.getWindow().getFramebufferWidth(),h=mc.getWindow().getFramebufferHeight();if(w<1||h<1)return;
            if(target==null)target=new SimpleFramebuffer("Clean gameplay capture",w,h,true);else if(target.textureWidth!=w||target.textureHeight!=h)target.resize(w,h);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorAttachment(),0xFF000000,target.getDepthAttachment(),1);
            if(mc.world!=null&&mc.player!=null&&mc.getOverlay()==null){
                var screen=mc.currentScreen;CapturePass.begin();
                if(screen!=null&&(screen.getClass().getName().startsWith("dev.maro.")||screen instanceof net.minecraft.client.gui.screen.ChatScreen&&Streamproof.hideChat()))mc.currentScreen=null;
                var lighting=(dev.maro.mixin.CleanCaptureMixins.Lighting)mc.gameRenderer.getLightmapTextureManager();lighting.maro$dirtyCaptureLighting(true);
                try{mc.gameRenderer.render(ticks,tick);}finally{mc.currentScreen=screen;CapturePass.end();lighting.maro$dirtyCaptureLighting(true);}
            }
            frames++;
        }catch(Throwable error){CapturePass.end();status="Capture stopped: "+error.getClass().getSimpleName();Maro.LOGGER.error("Clean capture stopped",error);module.setEnabled(false);}
    }
    public static void present(){
        if(CapturePass.active()||!protectedLayer||window==0||target==null||frames==0)return;
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
        if(failed){var m=Streamproof.get();if(m!=null)m.setEnabled(false);}
    }
    public static void close(){
        if(window!=0){long previous=GLFW.glfwGetCurrentContext();var caps=GL.getCapabilities();try{if(outputCaps!=null){GLFW.glfwMakeContextCurrent(window);GL.setCapabilities(outputCaps);if(readFramebuffer!=0)GL30.glDeleteFramebuffers(readFramebuffer);}}finally{GLFW.glfwMakeContextCurrent(previous);GL.setCapabilities(caps);GLFW.glfwDestroyWindow(window);window=0;outputCaps=null;readFramebuffer=0;}}
        if(target!=null){target.delete();target=null;}protectedLayer=visible=false;lastFrame=frames=privateFrames=mainFrames=0;alignedX=Integer.MIN_VALUE;
        if(!status.startsWith("Capture stopped"))status="Off";
    }
}
