package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.maro.module.impl.misc.NameProtect;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ChatMessages;
import dev.maro.privacy.AliasSnapshot;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

public final class NameProtectMixins {
    @Mixin(ChatHud.class)
    public interface Chat {@Invoker("refresh")void maro$refreshAliases();}
    @Mixin(TextHandler.class)
    public abstract static class Layout {
        @WrapMethod(method="getWidth(Ljava/lang/String;)F")
        private float maro$string(String text,Operation<Float> original){String shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown);}}
        @WrapMethod(method="getWidth(Lnet/minecraft/text/OrderedText;)F")
        private float maro$ordered(OrderedText text,Operation<Float> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown);}}
        @WrapMethod(method="getWidth(Lnet/minecraft/text/StringVisitable;)F")
        private float maro$styled(StringVisitable text,Operation<Float> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown);}}
        @WrapMethod(method="wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;Ljava/util/function/BiConsumer;)V")
        private void maro$wrap(StringVisitable text,int width,Style style,BiConsumer<StringVisitable,Boolean> consumer,Operation<Void> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){original.call(shown,width,style,consumer);}}
    }
    @Mixin(ChatMessages.class)
    public abstract static class ChatLines {
        @WrapMethod(method="breakRenderedChatMessageLines")
        private static List<OrderedText> maro$lines(StringVisitable text,int width,TextRenderer renderer,Operation<List<OrderedText>> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,width,renderer).stream().map(AliasSnapshot::protect).toList();}}
    }
    @Mixin(TextRenderer.class)
    public abstract static class Rendering {
        @WrapMethod(method="wrapLines")
        private List<OrderedText> maro$lines(StringVisitable text,int width,Operation<List<OrderedText>> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,width).stream().map(AliasSnapshot::protect).toList();}}
        @WrapMethod(method="prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;")
        private TextRenderer.GlyphDrawable maro$prepare(String text,float x,float y,int color,boolean shadow,int background,Operation<TextRenderer.GlyphDrawable> original){String shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,x,y,color,shadow,background);}}
        @ModifyVariable(method="prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",at=@At("HEAD"),argsOnly=true)
        private OrderedText maro$prepareStyled(OrderedText text){return NameProtect.mask(text);}
        @WrapMethod(method="draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V")
        private void maro$drawString(String text,float x,float y,int color,boolean shadow,Matrix4f matrix,VertexConsumerProvider out,TextRenderer.TextLayerType layer,int background,int light,Operation<Void> original){String shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){original.call(shown,x,y,color,shadow,matrix,out,layer,background,light);}}
        @WrapMethod(method="draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V")
        private void maro$drawText(Text text,float x,float y,int color,boolean shadow,Matrix4f matrix,VertexConsumerProvider out,TextRenderer.TextLayerType layer,int background,int light,Operation<Void> original){Text shown=(Text)NameProtect.mask((StringVisitable)text);try(var ignored=NameProtect.unprotected()){original.call(shown,x,y,color,shadow,matrix,out,layer,background,light);}}
        @WrapMethod(method="draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V")
        private void maro$drawOrdered(OrderedText text,float x,float y,int color,boolean shadow,Matrix4f matrix,VertexConsumerProvider out,TextRenderer.TextLayerType layer,int background,int light,Operation<Void> original){OrderedText shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){original.call(shown,x,y,color,shadow,matrix,out,layer,background,light);}}
        @WrapMethod(method="drawWithOutline")
        private void maro$outline(OrderedText text,float x,float y,int color,int outline,Matrix4f matrix,VertexConsumerProvider out,int light,Operation<Void> original){OrderedText shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){original.call(shown,x,y,color,outline,matrix,out,light);}}
        @WrapMethod(method="trimToWidth(Ljava/lang/String;I)Ljava/lang/String;")
        private String maro$trim(String text,int width,Operation<String> original){String shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,width);}}
        @WrapMethod(method="trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;")
        private String maro$trimReverse(String text,int width,boolean reverse,Operation<String> original){String shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,width,reverse);}}
        @WrapMethod(method="trimToWidth(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/StringVisitable;")
        private StringVisitable maro$trimStyled(StringVisitable text,int width,Operation<StringVisitable> original){var shown=NameProtect.mask(text);try(var ignored=NameProtect.unprotected()){return original.call(shown,width);}}
    }
    @Mixin(TextFieldWidget.class)
    public abstract static class Inputs {
        @WrapMethod(method="renderWidget")
        private void maro$input(DrawContext c,int x,int y,float delta,Operation<Void> original){try(var ignored=NameProtect.unprotected()){original.call(c,x,y,delta);}}
        @WrapMethod(method="getCharacterX")
        private int maro$caret(int index,Operation<Integer> original){try(var ignored=NameProtect.unprotected()){return original.call(index);}}
        @WrapMethod(method="calculateCursorPos")
        private int maro$cursor(Click click,Operation<Integer> original){try(var ignored=NameProtect.unprotected()){return original.call(click);}}
        @WrapMethod(method="updateTextPosition")
        private void maro$position(Operation<Void> original){try(var ignored=NameProtect.unprotected()){original.call();}}
        @WrapMethod(method="updateFirstCharacterIndex")
        private void maro$first(int index,Operation<Void> original){try(var ignored=NameProtect.unprotected()){original.call(index);}}
    }
}
