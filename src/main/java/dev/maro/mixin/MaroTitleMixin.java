package dev.maro.mixin;

import dev.maro.gui.render.MaroTitleStyle;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MaroTitleMixin extends Screen {
    protected MaroTitleMixin(Text title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void maro$layout(CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.translatable("maro.menu.accounts"),
                button -> client.setScreen(new dev.maro.gui.accounts.AccountsScreen(this))).dimensions(0,0,100,20).build());
        MaroTitleStyle.layout(this);
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void maro$render(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        MaroTitleStyle.render(ctx, width, height);
        for (var child : children()) if (child instanceof Drawable drawable) drawable.render(ctx, mx, my, delta);
        ci.cancel();
    }
}
