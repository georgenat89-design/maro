package dev.maro.mixin;
import dev.maro.gui.render.TextScissor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayDeque;
@Mixin(DrawContext.class)
public abstract class MaroTextScissorMixin implements TextScissor {
    @Unique private final ArrayDeque<ScreenRect> maro$textClips = new ArrayDeque<>();
    @Override public ScreenRect maro$textScissor() { return maro$textClips.peek(); }
    @Inject(method = "getHoverListener", at = @At("RETURN"), cancellable = true)
    private void maro$button(net.minecraft.client.gui.widget.ClickableWidget widget, DrawContext.HoverType hover,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.font.DrawnTextConsumer> ci) {
        if (dev.maro.gui.render.MaroFontWidgets.clientScreen()) ci.setReturnValue(
            dev.maro.gui.render.MaroFontWidgets.button((DrawContext)(Object)this, widget, ci.getReturnValue()));
    }
    @Inject(method = "enableScissor", at = @At("RETURN"))
    private void maro$clip(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        var rect = new ScreenRect(x1, y1, Math.max(0,x2-x1), Math.max(0,y2-y1))
                .transformEachVertex(((DrawContext)(Object)this).getMatrices());
        if (!maro$textClips.isEmpty()) rect = rect.intersection(maro$textClips.peek());
        maro$textClips.push(rect == null ? new ScreenRect(0,0,0,0) : rect);
    }
    @Inject(method = "disableScissor", at = @At("RETURN"))
    private void maro$unclip(CallbackInfo ci) { if (!maro$textClips.isEmpty()) maro$textClips.pop(); }
}
