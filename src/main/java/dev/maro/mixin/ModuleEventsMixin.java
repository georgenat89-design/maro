package dev.maro.mixin;
import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.RuntimeEvents;
import dev.maro.runtime.events.render.GetFovEvent;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(GameRenderer.class)
public abstract class ModuleEventsMixin {
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void maro$fov(CallbackInfoReturnable<Float> result){
        var event=MeteorClient.EVENT_BUS.post(new GetFovEvent(result.getReturnValue()));
        result.setReturnValue((float)event.fov);
    }
    @Inject(method="renderWorld",at=@At("HEAD"))
    private void maro$worldFrame(CallbackInfo info){RuntimeEvents.worldFrame();}
    @Inject(method="render",at=@At("TAIL"))
    private void maro$disposeTextures(CallbackInfo info){dev.maro.runtime.renderer.Texture.endFrame();}
}
