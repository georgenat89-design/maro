package dev.maro.render.capture;

import dev.maro.module.Module;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Network/background logic must continue seeing the real enabled state during a render scope. */
public final class CaptureScopeChecks {
    private CaptureScopeChecks() { }
    private interface User extends StdCallLibrary {
        Pointer GetDC(Pointer window);
        int ReleaseDC(Pointer window,Pointer context);
        boolean PrintWindow(Pointer window,Pointer context,int flags);
        int GetWindowTextW(Pointer window,char[] text,int length);
        Pointer GetForegroundWindow();
    }
    private interface Gdi extends StdCallLibrary {
        Pointer CreateCompatibleDC(Pointer context);
        Pointer CreateDIBSection(Pointer context,BitmapInfo bitmap,int usage,PointerByReference bits,Pointer section,int offset);
        Pointer SelectObject(Pointer context,Pointer object);
        boolean DeleteObject(Pointer object);
        boolean DeleteDC(Pointer context);
        boolean BitBlt(Pointer destination,int x,int y,int width,int height,Pointer source,int sourceX,int sourceY,int operation);
    }
    @Structure.FieldOrder({"size","width","height","planes","bitCount","compression","sizeImage","xPelsPerMeter","yPelsPerMeter","colorsUsed","colorsImportant"})
    public static final class BitmapInfoHeader extends Structure {
        public int size=40,width,height;
        public short planes=1,bitCount=32;
        public int compression,sizeImage,xPelsPerMeter,yPelsPerMeter,colorsUsed,colorsImportant;
    }
    @Structure.FieldOrder({"header","colors"})
    public static final class BitmapInfo extends Structure {
        public BitmapInfoHeader header=new BitmapInfoHeader();
        public int[] colors=new int[1];
    }
    private static final class Api {
        static final User USER=Native.load("user32",User.class);
        static final Gdi GDI=Native.load("gdi32",Gdi.class);
    }
    public record NativeCapture(String method,BufferedImage image,boolean supported,String detail) { }
    public static String windowLabel() {
        long handle=GLFWNativeWin32.glfwGetWin32Window(net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle());
        char[] title=new char[256];Api.USER.GetWindowTextW(new Pointer(handle),title,title.length);
        return "HWND="+handle+" title="+Native.toString(title);
    }
    public static boolean ownsForeground(){
        long main=GLFWNativeWin32.glfwGetWin32Window(net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle());
        return Pointer.nativeValue(Api.USER.GetForegroundWindow())==main;
    }

    /** The only captured desktop pixels are the Minecraft client area, excluding decorations. */
    public static NativeCapture displayCapture() {
        long expected=GLFWNativeWin32.glfwGetWin32Window(net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle());
        long foreground=Pointer.nativeValue(Api.USER.GetForegroundWindow());
        if(foreground!=expected)return new NativeCapture("Win32 desktop BitBlt (Minecraft client-area crop)",null,false,
                "OCCLUDED: test HWND="+expected+" is not the foreground HWND="+foreground+"; desktop capture not attempted");
        return nativeCapture(true);
    }

    /** Tests Windows window capture independently of the game's screenshot framebuffer. */
    public static NativeCapture windowCapture() {
        return nativeCapture(false);
    }

    private static NativeCapture nativeCapture(boolean desktop) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var area=CaptureWindows.clientArea(client.getWindow().getHandle());
        var hwnd=desktop?Pointer.NULL:new Pointer(GLFWNativeWin32.glfwGetWin32Window(client.getWindow().getHandle()));
        String method=desktop?"Win32 desktop BitBlt (Minecraft client-area crop)":"Win32 PrintWindow client-only/full-content";
        Pointer source=Pointer.NULL,context=Pointer.NULL,bitmap=Pointer.NULL,previous=Pointer.NULL;
        try {
            source=Api.USER.GetDC(hwnd);if(source==null)throw new IllegalStateException("GetDC failed");
            context=Api.GDI.CreateCompatibleDC(source);if(context==null)throw new IllegalStateException("CreateCompatibleDC failed");
            var info=new BitmapInfo();info.header.width=area.width();info.header.height=-area.height();
            info.header.sizeImage=Math.multiplyExact(Math.multiplyExact(area.width(),area.height()),4);
            var data=new PointerByReference();bitmap=Api.GDI.CreateDIBSection(source,info,0,data,Pointer.NULL,0);
            if(bitmap==null||data.getValue()==null)throw new IllegalStateException("CreateDIBSection failed");
            previous=Api.GDI.SelectObject(context,bitmap);
            if(previous==null||Pointer.nativeValue(previous)==-1)throw new IllegalStateException("SelectObject failed");
            data.getValue().clear(info.header.sizeImage);
            // A screen DC exercises display capture, cropped before readback. No unrelated desktop pixels are copied.
            // SRCCOPY | CAPTUREBLT includes ordinary layered windows, so exclusion must actually work.
            boolean copied=desktop?Api.GDI.BitBlt(context,0,0,area.width(),area.height(),source,area.x(),area.y(),0x40CC0020)
                    :Api.USER.PrintWindow(hwnd,context,3); // PW_CLIENTONLY | PW_RENDERFULLCONTENT
            if(!copied)return new NativeCapture(method,null,false,"Windows capture returned failure");
            int[] pixels=data.getValue().getIntArray(0,area.width()*area.height());
            for(int i=0;i<pixels.length;i++)pixels[i]|=0xFF000000;
            var image=new BufferedImage(area.width(),area.height(),BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,area.width(),area.height(),pixels,0,area.width());
            return new NativeCapture(method,image,true,desktop?"Actual desktop screen DC, SRCCOPY | CAPTUREBLT, private layer visible":"Actual main HWND capture, flags PW_CLIENTONLY | PW_RENDERFULLCONTENT");
        } catch(Exception error) {
            return new NativeCapture(method,null,false,error.getClass().getSimpleName()+": "+error.getMessage());
        } finally {
            if(context!=null&&previous!=null&&Pointer.nativeValue(previous)!=-1)Api.GDI.SelectObject(context,previous);
            if(bitmap!=null)Api.GDI.DeleteObject(bitmap);
            if(context!=null)Api.GDI.DeleteDC(context);
            if(source!=null)Api.USER.ReleaseDC(hwnd,source);
        }
    }
    public static void aligned(){
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var main=CaptureWindows.clientArea(client.getWindow().getHandle());var layer=CaptureWindows.clientArea(CleanCapture.window());
        if(!main.equals(layer))throw new AssertionError("Private layer does not match native client area: "+main+" / "+layer);
    }
    public static void during(Runnable check){CapturePass.begin();try{check.run();}finally{CapturePass.end();}}
    /** Exercises the real failure transition without generating a synthetic production ERROR. */
    public static void failPrivateSurface(){
        try{
            var transition=CleanCapture.class.getDeclaredMethod("failSurface",boolean.class);
            transition.setAccessible(true);transition.invoke(null,true);
        }catch(ReflectiveOperationException error){throw new AssertionError("Could not inject private-surface failure",error);}
    }
    public static void run(Module module){
        CapturePass.begin();
        try{
            if(module.isEnabled())throw new AssertionError("Module rendering was not suppressed in clean scope");
            if(!CompletableFuture.supplyAsync(module::isEnabled).join())throw new AssertionError("Clean scope changed module state on a background thread");
        }finally{CapturePass.end();}
        if(!module.isEnabled())throw new AssertionError("Enabled state did not return after capture scope");
    }
}
