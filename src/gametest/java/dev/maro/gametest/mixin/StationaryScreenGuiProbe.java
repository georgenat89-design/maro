package dev.maro.gametest.mixin;

import dev.maro.gametest.StationaryScreenGuiChecks;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.widget.Dropdown;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test landmarks use the real menu context and never exist in production. */
public final class StationaryScreenGuiProbe {
    @Mixin(value=ClickGuiScreen.class,remap=false)
    public abstract static class Panel {
        @Inject(method="renderWindow",at=@At("TAIL"),remap=false)
        private void maroTest$corners(DrawContext ctx,float progress,int width,int height,CallbackInfo ci){StationaryScreenGuiChecks.panelMarker(ctx);}
        @Inject(method="renderTooltip",at=@At("HEAD"),remap=false)
        private void maroTest$tooltip(DrawContext ctx,float progress,int width,int height,CallbackInfo ci){StationaryScreenGuiChecks.tooltipPass(ctx);}
    }
    @Mixin(value=Dropdown.class,remap=false)
    public abstract static class Choice {
        @Inject(method="render",at=@At("HEAD"),remap=false)
        private static void maroTest$dropdown(ClickGuiScreen gui,DrawContext ctx,float width,float height,CallbackInfo ci){StationaryScreenGuiChecks.dropdownPass(ctx);}
    }
}
