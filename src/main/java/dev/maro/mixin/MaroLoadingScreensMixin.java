package dev.maro.mixin;

import dev.maro.gui.render.BootFlow;
import dev.maro.gui.render.MaroTitleStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MaroLoadingScreensMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void maro$connection(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        Screen screen = (Screen)(Object)this;
        if (dev.maro.gui.render.MaroFontWidgets.clientScreen() && !dev.maro.gui.MaroHomeScreen.embeddedRendering()) {
            dev.maro.gui.render.Render2D.setAlpha(1);
            dev.maro.gui.render.Render2D.setScissor(null);
            dev.maro.gui.render.Fonts.setMeasureScale(1);
        }
        if (!BootFlow.isConnection(screen)) {
            if (!(screen instanceof TitleScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen)) return;
            ctx.createNewRootLayer();
            screen.render(ctx, mx, my, delta);
            ctx.drawDeferredElements();
            ci.cancel();
            return;
        }
        if (screen instanceof ConnectScreen) {
            var connection = (MaroConnectAccessor)screen;
            long now = Util.getMeasuringTimeMs();
            if (now - connection.maro$getLastNarrationTime() > 2000) {
                connection.maro$setLastNarrationTime(now);
                MinecraftClient.getInstance().getNarratorManager().narrateSystemImmediately(Text.translatable("narrator.joining"));
            }
        }
        String status = screen instanceof ConnectScreen ? ((MaroConnectAccessor)screen).maro$getStatus().getString() : screen.getTitle().getString();
        ctx.createNewRootLayer();
        BootFlow.draw(ctx, screen.width, screen.height, -1, status);
        MaroTitleStyle.layoutLoading(screen);
        ctx.createNewRootLayer();
        for (var child : screen.children()) if (child instanceof PressableWidget button) button.render(ctx, mx, my, delta);
        ctx.drawDeferredElements();
        ci.cancel();
    }
}
