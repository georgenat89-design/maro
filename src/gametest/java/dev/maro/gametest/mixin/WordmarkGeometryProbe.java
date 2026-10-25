package dev.maro.gametest.mixin;

import dev.maro.gametest.MenuBrandingChecks;
import dev.maro.gui.render.MaroWordmark;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe exact native ink bounds without adding a production diagnostic API. */
@Mixin(MaroWordmark.class)
public abstract class WordmarkGeometryProbe {
    @Inject(method="draw(Lnet/minecraft/client/gui/DrawContext;FFFILdev/maro/gui/render/MaroWordmark$Style;ZF)Ldev/maro/gui/render/MaroWordmark$Bounds;",at=@At("RETURN"))
    private static void maroTest$wordmark(DrawContext context,float x,float top,float capHeight,int color,MaroWordmark.Style style,boolean domain,float hover,CallbackInfoReturnable<MaroWordmark.Bounds> info){
        if(capHeight>=12)MenuBrandingChecks.wordmarkDrawn(context,info.getReturnValue());
    }
}
