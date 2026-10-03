package dev.maro.nathan.regionmap;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** The static map artwork, rebuilt only when its scale, colours or label settings change. */
public final class RegionMapRaster {
    public static final double GRID_UNITS = 108;
    public static final double GUTTER_UNITS = 0.25;
    public static final double CORNER_UNITS = 0.65;
    private static final double NUMBER_CAPS = 3.2;
    public enum NumberFont {
        Rounded("Poppins-Medium.ttf"),
        RoundedBold("Poppins-SemiBold.ttf"),
        Condensed("BarlowCondensed-SemiBold.ttf");

        private final String file;

        NumberFont(String file) {
            this.file = file;
        }

        @Override
        public String toString() {
            return this == RoundedBold ? "Rounded Bold" : name();
        }
    }

    private static final Font[] FONTS = new Font[NumberFont.values().length];

    public record Rendered(BufferedImage image, int labels) {}

    private RegionMapRaster() {}

    public static Rendered create(int size, int[] fills, int[] inks, boolean numbers) {
        return create(size, fills, inks, numbers, NumberFont.RoundedBold, 1.25, true);
    }

    public static Rendered create(int size, int[] fills, int[] inks, boolean numbers,
                                  NumberFont numberFont, double numberSize, boolean fitNumbers) {
        if (size <= 0 || fills.length != RegionGrid.Locale.values().length || inks.length != fills.length
            || numberFont == null || !Double.isFinite(numberSize) || numberSize < 0.5 || numberSize > 2.5)
            throw new IllegalArgumentException("Invalid region-map raster size or colours");

        // Rasterise directly onto the screen's pixel grid. Supersampling and
        // shrinking tiny digits loses their hinted stems and softens them twice
        // when the GPU also filters the texture.
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int labels = 0;
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            double unit = size / GRID_UNITS;
            double pitch = (double) size / RegionGrid.SIDE;
            double gutter = GUTTER_UNITS * unit;
            Font font = numbers ? font(numberFont) : null;
            double capRatio = numbers
                ? font.deriveFont(100f).createGlyphVector(graphics.getFontRenderContext(), "0").getVisualBounds().getHeight() / 100
                : 1;

            for (int id = 0; id < RegionGrid.count(); id++) {
                RegionGrid.Shard shard = RegionGrid.shard(id);
                double left = shard.col() * pitch + gutter / 2;
                double top = shard.row() * pitch + gutter / 2;
                double width = shard.width() * pitch - gutter;
                double height = shard.height() * pitch - gutter;
                double radius = Math.min(CORNER_UNITS * unit, Math.min(width, height) / 4);
                int locale = shard.locale().ordinal();
                graphics.setColor(new java.awt.Color(fills[locale], true));
                graphics.fill(new RoundRectangle2D.Double(left, top, width, height, radius * 2, radius * 2));
            }

            // Draw all labels after all fills so disabling Fit Numbers allows
            // a larger label to extend over a neighbour instead of being erased.
            if (numbers) {
                for (int id = 0; id < RegionGrid.count(); id++) {
                    RegionGrid.Shard shard = RegionGrid.shard(id);
                    double left = shard.col() * pitch + gutter / 2;
                    double top = shard.row() * pitch + gutter / 2;
                    double width = shard.width() * pitch - gutter;
                    double height = shard.height() * pitch - gutter;
                    int locale = shard.locale().ordinal();
                    String label = Integer.toString(shard.number());
                    double padding = Math.min(0.65 * unit, Math.min(width, height) * 0.10);
                    double caps = NUMBER_CAPS * unit * numberSize;
                    int availableWidth = Math.max(1, (int) Math.floor(width - padding * 2));
                    int availableHeight = Math.max(1, (int) Math.floor(fitNumbers ? Math.min(caps, height - padding * 2) : caps));
                    int fontSize = Math.max(1, (int) Math.ceil(caps / capRatio));
                    GlyphVector glyphs;
                    Rectangle bounds;
                    // Whole font sizes preserve hinting. Fitting can be switched
                    // off when the user prefers larger digits in narrow shards.
                    do {
                        glyphs = font.deriveFont((float) fontSize).createGlyphVector(graphics.getFontRenderContext(), label);
                        bounds = glyphs.getPixelBounds(graphics.getFontRenderContext(), 0, 0);
                        if (((!fitNumbers || bounds.width <= availableWidth) && bounds.height <= availableHeight) || fontSize == 1) break;
                        fontSize--;
                    } while (true);
                    graphics.setColor(new java.awt.Color(inks[locale], true));
                    graphics.drawGlyphVector(glyphs, (float) (Math.round(left + (width - bounds.width) / 2) - bounds.x),
                        (float) (Math.round(top + (height - bounds.height) / 2) - bounds.y));
                    labels++;
                }
            }
        } finally {
            graphics.dispose();
        }

        return new Rendered(image, labels);
    }

    private static Font font(NumberFont choice) {
        if (FONTS[choice.ordinal()] != null) return FONTS[choice.ordinal()];
        try (InputStream stream = RegionMapRaster.class.getResourceAsStream("/assets/nameeprotect/fonts/" + choice.file)) {
            if (stream == null) throw new IllegalStateException("Region Map number font is missing");
            FONTS[choice.ordinal()] = Font.createFont(Font.TRUETYPE_FONT, stream);
            return FONTS[choice.ordinal()];
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Region Map number font", exception);
        }
    }
}
