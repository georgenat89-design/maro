package dev.maro.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.accounts.TokenField;
import org.spongepowered.asm.mixin.Unique;
import dev.maro.gui.render.MaroFontWidgets;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Uses matching display/measurement while Minecraft still owns editing, selection and narration. */
@Mixin(TextFieldWidget.class)
public abstract class MaroTextFieldMixin {
    @Unique private String maro$safe(String text){return (Object)this instanceof TokenField ? TokenField.mask(text) : text;}
    @Unique private String maro$prefix(String text,int width,boolean reverse){
        if(!((Object)this instanceof TokenField))return Fonts.prefix(text,width,reverse);
        int count=Fonts.prefix(TokenField.mask(text),width,reverse).length();
        return reverse?text.substring(text.length()-count):text.substring(0,count);
    }
    @WrapOperation(method={"renderWidget","updateTextPosition","getCharacterX"}, at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;getWidth(Ljava/lang/String;)I"))
    private int maro$width(TextRenderer renderer, String text, Operation<Integer> original) {
        return MaroFontWidgets.clientScreen() ? Math.round(Fonts.width(maro$safe(text),false,1)) : original.call(renderer,text);
    }
    @WrapOperation(method="renderWidget", at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/OrderedText;)I"))
    private int maro$orderedWidth(TextRenderer renderer, OrderedText text, Operation<Integer> original) {
        return MaroFontWidgets.clientScreen() ? Math.round(Fonts.width(maro$safe(MaroFontWidgets.string(text)),false,1)) : original.call(renderer,text);
    }
    @WrapOperation(method={"renderWidget","updateTextPosition","updateFirstCharacterIndex","calculateCursorPos"}, at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;trimToWidth(Ljava/lang/String;I)Ljava/lang/String;"))
    private String maro$trim(TextRenderer renderer, String text, int width, Operation<String> original) {
        return MaroFontWidgets.clientScreen() ? maro$prefix(text,width,false) : original.call(renderer,text,width);
    }
    @WrapOperation(method="updateFirstCharacterIndex", at=@At(value="INVOKE",target="Lnet/minecraft/client/font/TextRenderer;trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;"))
    private String maro$trimEnd(TextRenderer renderer, String text, int width, boolean reverse, Operation<String> original) {
        return MaroFontWidgets.clientScreen() ? maro$prefix(text,width,reverse) : original.call(renderer,text,width,reverse);
    }
    @WrapOperation(method="renderWidget",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V"))
    private void maro$text(DrawContext ctx, TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow, Operation<Void> original) {
        if(MaroFontWidgets.clientScreen()) Fonts.draw(ctx,maro$safe(MaroFontWidgets.string(text)),x,y,MaroFontWidgets.ink(color),false,1);
        else original.call(ctx,renderer,text,x,y,color,shadow);
    }
    @WrapOperation(method="renderWidget",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V"))
    private void maro$string(DrawContext ctx, TextRenderer renderer, String text, int x, int y, int color, boolean shadow, Operation<Void> original) {
        if(MaroFontWidgets.clientScreen()) Fonts.draw(ctx,maro$safe(text),x,y,MaroFontWidgets.ink(color),false,1);
        else original.call(ctx,renderer,text,x,y,color,shadow);
    }
    @WrapOperation(method="renderWidget",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
    private void maro$placeholder(DrawContext ctx, TextRenderer renderer, Text text, int x, int y, int color, Operation<Void> original) {
        if(MaroFontWidgets.clientScreen()) Fonts.draw(ctx,text.getString(),x,y,MaroFontWidgets.ink(color),false,1);
        else original.call(ctx,renderer,text,x,y,color);
    }
}
