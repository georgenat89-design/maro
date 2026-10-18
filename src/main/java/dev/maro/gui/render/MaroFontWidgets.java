package dev.maro.gui.render;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
public final class MaroFontWidgets {
    private MaroFontWidgets() { }
    public static boolean clientScreen() {
        var screen = MinecraftClient.getInstance().currentScreen;
        return screen != null && screen.getClass().getName().startsWith("dev.maro.");
    }
    public static String string(OrderedText text) {
        var value = new StringBuilder();
        text.accept((index, style, codePoint) -> { value.appendCodePoint(codePoint); return true; });
        return value.toString();
    }
    public static int ink(int color) { return (color >>> 24) == 0 ? color | 0xFF000000 : color; }
    public static DrawnTextConsumer button(DrawContext ctx, ClickableWidget widget, DrawnTextConsumer original) {
        return new DrawnTextConsumer() {
            public Transformation getTransformation() { return original.getTransformation(); }
            public void setTransformation(Transformation transform) { original.setTransformation(transform); }
            public void text(Alignment alignment, int x, int y, Transformation transform, OrderedText text) {
                original.text(alignment, x, y, transform, text);
            }
            public void marqueedText(Text text, int a, int b, int c, int d, int e, Transformation transform) {
                original.marqueedText(text,a,b,c,d,e,transform);
            }
            @Override public void text(Text text, int left, int right, int top, int bottom) {
                String label = Fonts.trim(ctx, text.getString(), Math.max(0,right-left-4), false, 1);
                Fonts.drawCentered(ctx, label, (left+right)/2f, (top+bottom)/2f,
                        widget.active ? 0xFFEFF5FF : 0xFF7D8B9E, false, 1);
            }
        };
    }
}
