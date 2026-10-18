package dev.maro.render.capture;

import dev.maro.module.Module;
import java.util.concurrent.CompletableFuture;

/** Network/background logic must continue seeing the real enabled state during a render scope. */
public final class CaptureScopeChecks {
    public static void aligned(){
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var main=CaptureWindows.clientArea(client.getWindow().getHandle());var layer=CaptureWindows.clientArea(CleanCapture.window());
        if(!main.equals(layer))throw new AssertionError("Private layer does not match native client area: "+main+" / "+layer);
    }
    public static void during(Runnable check){CapturePass.begin();try{check.run();}finally{CapturePass.end();}}
    public static void run(Module module){
        CapturePass.begin();
        try{
            if(module.isEnabled())throw new AssertionError("Module rendering was not suppressed in clean scope");
            if(!CompletableFuture.supplyAsync(module::isEnabled).join())throw new AssertionError("Clean scope changed module state on a background thread");
        }finally{CapturePass.end();}
        if(!module.isEnabled())throw new AssertionError("Enabled state did not return after capture scope");
    }
}
