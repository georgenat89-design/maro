package dev.maro.mixin;

import dev.maro.gui.render.BootFlow;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressScreen.class)
public abstract class MaroProgressMixin {
    @Shadow private Text title;
    @Shadow private int progress;
    @Shadow private boolean done;
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void maro$render(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        if (done) return; // Vanilla retains its close-after-completion behavior.
        BootFlow.draw(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                progress > 0 ? Math.min(1, progress / 100f) : -1, title == null ? "Preparing your world" : title.getString());
        ci.cancel();
    }
}
