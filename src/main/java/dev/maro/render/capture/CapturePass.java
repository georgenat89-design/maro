package dev.maro.render.capture;

/** Render-thread-only scope. Never changes enabled flags or runs module lifecycle callbacks. */
public final class CapturePass {
    private static volatile Thread rendering;
    private CapturePass(){}
    public static boolean active(){return rendering==Thread.currentThread();}
    static void begin(){if(rendering!=null)throw new IllegalStateException("Nested clean capture");rendering=Thread.currentThread();}
    static void end(){rendering=null;}
}
