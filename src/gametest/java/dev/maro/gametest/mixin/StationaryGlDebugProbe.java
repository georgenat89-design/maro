package dev.maro.gametest.mixin;

import net.minecraft.client.gl.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native diagnostic only: synchronous test callbacks identify the actual failing GL call. */
@Mixin(GlDebug.class)
public abstract class StationaryGlDebugProbe {
    @Inject(method="onDebugMessage",at=@At("HEAD"))
    private void maroTest$enumOrigin(int source,int type,int id,int severity,int length,long message,long user,CallbackInfo ci){
        if(id==1280&&Boolean.getBoolean("maro.gametest.stationaryScreenGuiOnly"))
            new IllegalStateException("Stationary Screen GUI GL_INVALID_ENUM origin").printStackTrace(System.out);
    }
}
