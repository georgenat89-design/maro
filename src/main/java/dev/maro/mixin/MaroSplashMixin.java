package dev.maro.mixin;

import dev.maro.gui.render.BootScene;
import dev.maro.gui.render.BootFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only replaces drawing. Vanilla tick still owns completion, failures and screen initialization. */
@Mixin(SplashOverlay.class)
public abstract class MaroSplashMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadStartTime;
    @Shadow private long reloadCompleteTime;
    @Shadow private float progress;
    @Unique private long maro$lastFrame;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void maro$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        long now = Util.getMeasuringTimeMs();
        if (reloading && reloadStartTime == -1) reloadStartTime = now;
        float elapsed = reloadCompleteTime < 0 ? -1 : (now - reloadCompleteTime) / 550f;
        float opacity = elapsed >= 0 ? Math.max(0, 1 - elapsed)
                : reloading ? Math.min(1, (now - reloadStartTime) / 180f) : 1;
        if (elapsed >= 0 || reloading && opacity < 1) {
            if (client.currentScreen != null) client.currentScreen.renderWithTooltip(ctx, mouseX, mouseY, delta);
            else client.inGameHud.renderDeferredSubtitles();
        }
        float dt = maro$lastFrame == 0 ? 1 / 60f : Math.min(.1f, (now - maro$lastFrame) / 1000f);
        maro$lastFrame = now;
        float target = elapsed >= 0 ? 1 : Math.max(0, Math.min(1, reload.getProgress()));
        if (!Float.isFinite(target)) target = progress;
        progress += (Math.max(progress, target) - progress) * (1 - (float)Math.exp(-12 * dt));
        ctx.createNewRootLayer();
        if (opacity > .001f) BootScene.loading(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                elapsed >= 0 ? 1 : progress, reloading ? "Reloading resources" : "Loading resources", opacity,
                !reloading || !BootFlow.isLoading(client.currentScreen));
        if (elapsed >= 1) client.setOverlay(null);
        ci.cancel();
    }
}
