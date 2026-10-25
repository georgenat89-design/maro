package dev.maro.gui.render;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
public final class MaroFontWidgets {
    private MaroFontWidgets() { }
    public static boolean clientScreen() {
        if (dev.maro.render.capture.CapturePass.active()) return false;
        var screen = MinecraftClient.getInstance().currentScreen;
        return screen != null && screen.getClass().getName().startsWith("dev.maro.");
    }
    public static String string(OrderedText text) {
        var value = new StringBuilder();
        text.accept((index, style, codePoint) -> { value.appendCodePoint(codePoint); return true; });
        return value.toString();
    }
    public static int ink(int color) { return (color >>> 24) == 0 ? color | 0xFF000000 : color; }
    /** Retains colors, links and decorations while selecting the client's real font cuts. */
    public static OrderedText clientFont(OrderedText text){return clientFont(null,text);}
    public static OrderedText clientFont(DrawContext context,OrderedText text){
        if(dev.maro.render.capture.CapturePass.active()||!dev.maro.config.ClientSettings.customFont.get())return text;
        Style regular=context==null?Fonts.text("",false).getStyle():Fonts.text(context,"",false,1).getStyle();
        Style bold=context==null?Fonts.text("",true).getStyle():Fonts.text(context,"",true,1).getStyle();
        var styles=new java.util.HashMap<Style,Style>();
        return visitor->text.accept((index,style,codePoint)->visitor.accept(index,styles.computeIfAbsent(style,key->
                key.getFont() instanceof StyleSpriteSource.Font?key.withFont((key.isBold()?bold:regular).getFont()).withBold(false):key),codePoint));
    }
    private static Text clientFont(DrawContext context,Text text){
        if(dev.maro.render.capture.CapturePass.active()||!dev.maro.config.ClientSettings.customFont.get())return text;
        Style regular=Fonts.text(context,"",false,1).getStyle(),bold=Fonts.text(context,"",true,1).getStyle();
        var result=Text.empty();
        text.visit((style,value)->{
            Style mapped=style.getFont() instanceof StyleSpriteSource.Font?style.withFont((style.isBold()?bold:regular).getFont()).withBold(false):style;
            result.append(Text.literal(value).setStyle(mapped));return java.util.Optional.<Void>empty();
        },Style.EMPTY);
        return result;
    }
    public static DrawnTextConsumer button(DrawContext ctx, ClickableWidget widget, DrawnTextConsumer original) {
        if (dev.maro.render.capture.CapturePass.active()) return original;
        return new DrawnTextConsumer() {
            public Transformation getTransformation() { return original.getTransformation(); }
            public void setTransformation(Transformation transform) { original.setTransformation(transform); }
            public void text(Alignment alignment, int x, int y, Transformation transform, OrderedText text) {
                original.text(alignment, x, y, transform, clientFont(ctx,text));
            }
            public void marqueedText(Text text, int a, int b, int c, int d, int e, Transformation transform) {
                original.marqueedText(clientFont(ctx,text),a,b,c,d,e,transform);
            }
            @Override public void text(Text text, int left, int right, int top, int bottom) {
                String label = Fonts.trim(ctx, text.getString(), Math.max(0,right-left-4), false, 1);
                Fonts.drawCentered(ctx, label, (left+right)/2f, (top+bottom)/2f,
                        widget.active ? 0xFFEFF5FF : 0xFF7D8B9E, false, 1);
            }
        };
    }
}
