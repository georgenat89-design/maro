package dev.maro.render.capture;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;

/** Our capture-excluded, click-through tool layer, owned by the Minecraft window. */
final class CaptureWindows {
    private interface User extends StdCallLibrary {
        boolean SetWindowDisplayAffinity(Pointer window,int affinity);
        boolean GetWindowDisplayAffinity(Pointer window,IntByReference affinity);
        boolean SetWindowPos(Pointer window,Pointer after,int x,int y,int w,int h,int flags);
        Pointer SetWindowLongPtrW(Pointer window,int index,Pointer value);
        Pointer GetWindowLongPtrW(Pointer window,int index);
        boolean GetClientRect(Pointer window,Rect rectangle);
        boolean ClientToScreen(Pointer window,Point point);
    }
    @Structure.FieldOrder({"left","top","right","bottom"})
    public static final class Rect extends Structure {public int left,top,right,bottom;}
    @Structure.FieldOrder({"x","y"})
    public static final class Point extends Structure {public int x,y;}
    record Area(int x,int y,int width,int height) { }
    private static final Rect CLIENT=new Rect();
    private static final Point ORIGIN=new Point();
    private static final class Api {static final User USER=Native.load("user32",User.class);}
    static boolean supported(){return Platform.get()==Platform.WINDOWS;}
    private static Pointer handle(long window){return new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));}
    static boolean prepare(long layer,long main){
        if(!supported())return false;var hwnd=handle(layer);var owner=handle(main);
        Api.USER.SetWindowLongPtrW(hwnd,-8,owner);
        long style=Pointer.nativeValue(Api.USER.GetWindowLongPtrW(hwnd,-20));
        Api.USER.SetWindowLongPtrW(hwnd,-20,new Pointer((style|0x08000000L|0x80L|0x20L)&~0x40000L));
        return Pointer.nativeValue(Api.USER.GetWindowLongPtrW(hwnd,-8))==Pointer.nativeValue(owner)
            &&Api.USER.SetWindowDisplayAffinity(hwnd,0x11)&&privateLayer(layer,main);
    }
    static boolean privateLayer(long layer,long main){
        if(!supported()||layer==0)return false;var hwnd=handle(layer);var value=new IntByReference();long style=Pointer.nativeValue(Api.USER.GetWindowLongPtrW(hwnd,-20));
        return Api.USER.GetWindowDisplayAffinity(hwnd,value)&&value.getValue()==0x11
            &&Pointer.nativeValue(Api.USER.GetWindowLongPtrW(hwnd,-8))==Pointer.nativeValue(handle(main))
            &&(style&0x080000A0L)==0x080000A0L&&(style&0x40000L)==0;
    }
    /** Native client pixels avoid decorated-frame and mixed-DPI coordinate offsets. */
    static Area clientArea(long main){
        ORIGIN.x=ORIGIN.y=0;
        if(!Api.USER.GetClientRect(handle(main),CLIENT)||!Api.USER.ClientToScreen(handle(main),ORIGIN))throw new IllegalStateException("Could not read Minecraft's client area");
        return new Area(ORIGIN.x,ORIGIN.y,Math.max(1,CLIENT.right-CLIENT.left),Math.max(1,CLIENT.bottom-CLIENT.top));
    }
    static void align(long layer,int x,int y,int w,int h){if(!Api.USER.SetWindowPos(handle(layer),Pointer.NULL,x,y,w,h,0x0010|0x0200|0x0004))throw new IllegalStateException("Could not align the private layer");}
}
