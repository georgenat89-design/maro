package dev.maro.gametest;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GlyphNames;
import dev.maro.gui.render.Glyphs;
import dev.maro.gui.render.Render2D;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.util.Arrays;

/** Measures the rendered ink, so a font's bearings cannot silently offset its icon buttons. */
final class RenderingQualityChecks {
    private static final String[] GLYPHS = Arrays.stream(GlyphNames.class.getFields())
            .filter(f -> f.getType() == String.class).sorted(java.util.Comparator.comparing(java.lang.reflect.Field::getName))
            .map(f -> { try { return (String)f.get(null); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } })
            .toArray(String[]::new);

    static void run(ClientGameTestContext context) {
        int previous = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        try {
            for (int guiScale : new int[]{2, 3, 4}) for (float resize : new float[]{1f, .73f}) {
                context.runOnClient(c -> {
                    c.options.getGuiScale().setValue(guiScale);
                    c.onResolutionChanged();
                    c.setScreen(new Probe(resize));
                });
                context.waitTicks(4);
                double device = context.computeOnClient(c -> c.getWindow().getScaleFactor());
                try {
                    var image = ImageIO.read(context.takeScreenshot("maro-render-quality-" + guiScale + "-" + resize).toFile());
                    int antialiased = 0;
                    for (int i = 0; i < GLYPHS.length; i++) {
                        double cx = (5.25 + (20 + i % 14 * 20) * resize) * device;
                        double cy = (3.25 + (24 + i / 14 * 18) * resize) * device;
                        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
                        int radius = (int)Math.ceil(7 * resize * device);
                        for (int y = Math.max(0, (int)cy - radius); y <= Math.min(image.getHeight() - 1, (int)cy + radius); y++)
                            for (int x = Math.max(0, (int)cx - radius); x <= Math.min(image.getWidth() - 1, (int)cx + radius); x++) {
                                int red = image.getRGB(x, y) >> 16 & 255;
                                if (red > 4) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
                                if (red > 4 && red < 250) antialiased++;
                            }
                        if (maxX < minX || Math.abs((minX + maxX + 1) / 2.0 - cx) > 1.5 || Math.abs((minY + maxY + 1) / 2.0 - cy) > 1.5)
                            throw new AssertionError("Icon " + i + " is missing or off-center at GUI scale " + guiScale + ", resize " + resize);
                    }
                    if (antialiased < GLYPHS.length * 4) throw new AssertionError("Icons lost grayscale edge coverage");
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            }
            System.out.println("[render-quality] PASS: " + GLYPHS.length + " centered antialiased icons at scales 2/3/4 and fractional HUD resize");
        } finally {
            context.runOnClient(c -> { c.setScreen(null); c.options.getGuiScale().setValue(previous); c.onResolutionChanged(); });
        }
    }

    private static final class Probe extends Screen {
        private final float resize;
        Probe(float resize) { super(Text.literal("Render quality")); this.resize = resize; }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            Render2D.setAlpha(1);
            ctx.fill(0, 0, width, height, 0xFF000000);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(5.25f, 3.25f);
            ctx.getMatrices().scale(resize, resize);
            for (int i = 0; i < GLYPHS.length; i++) Glyphs.draw(ctx, GLYPHS[i], 20 + i % 14 * 20, 24 + i / 14 * 18, 9, 0xFFFFFFFF);
            Fonts.drawCentered(ctx, "Centered client controls", 150, 210, 0xFFFFFFFF, true, 1);
            ctx.getMatrices().popMatrix();
        }
    }
}
