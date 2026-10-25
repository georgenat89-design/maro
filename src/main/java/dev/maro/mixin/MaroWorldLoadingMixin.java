package dev.maro.mixin;

import dev.maro.gui.render.BootFlow;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientChunkLoadProgress;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MaroWorldLoadingMixin extends Screen {
    @Shadow private ClientChunkLoadProgress chunkLoadProgress;
    @Shadow private float loadProgress;
    @Shadow private long lastNarrationTime;
    protected MaroWorldLoadingMixin(Text title) { super(title); }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void maro$render(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        long now = Util.getMeasuringTimeMs();
        if (now - lastNarrationTime > 2000) {
            lastNarrationTime = now;
            narrateScreenIfNarrationEnabled(true);
        }
        BootFlow.draw(ctx, width, height, chunkLoadProgress.hasProgress() ? loadProgress : -1, "Entering your world");
        ci.cancel();
    }
}
