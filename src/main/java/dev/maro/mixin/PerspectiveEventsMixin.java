package dev.maro.mixin;
import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.events.game.ChangePerspectiveEvent;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameOptions.class)
public abstract class PerspectiveEventsMixin {
    @Inject(method="setPerspective",at=@At("HEAD"),cancellable=true)
    private void maro$perspective(Perspective perspective,CallbackInfo info){
        if(MeteorClient.EVENT_BUS.post(new ChangePerspectiveEvent(perspective)).isCancelled())info.cancel();
    }
}
