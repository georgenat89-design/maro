package dev.maro.mixin;

import dev.maro.gui.render.MaroTitleStyle;
import dev.maro.gui.render.BootFlow;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PressableWidget.class)
public abstract class MaroTitleButtonMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/PressableWidget;drawIcon(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void maro$draw(PressableWidget widget, DrawContext ctx, int mx, int my, float delta, Operation<Void> original) {
        if (!dev.maro.render.capture.CapturePass.active() && (MinecraftClient.getInstance().currentScreen instanceof TitleScreen
                || BootFlow.isLoading(MinecraftClient.getInstance().currentScreen))
                && MaroTitleStyle.button(ctx, widget)) return;
        original.call(widget, ctx, mx, my, delta);
    }
}
