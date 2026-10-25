package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.gui.render.MaroFontWidgets;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native tooltip layout and rich text remain intact; only Maro screens use the client face. */
@Mixin(OrderedTextTooltipComponent.class)
public abstract class MaroTooltipFontMixin {
    @WrapOperation(method="getWidth",at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/OrderedText;)I"))
    private int maro$clientWidth(TextRenderer renderer,OrderedText text,Operation<Integer> original){
        return original.call(renderer,MaroFontWidgets.clientScreen()?MaroFontWidgets.clientFont(text):text);
    }
    @WrapOperation(method="drawText",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V"))
    private void maro$clientText(DrawContext context,TextRenderer renderer,OrderedText text,int x,int y,int color,boolean shadow,Operation<Void> original){
        original.call(context,renderer,MaroFontWidgets.clientScreen()?MaroFontWidgets.clientFont(context,text):text,x,y,color,shadow);
    }
}
