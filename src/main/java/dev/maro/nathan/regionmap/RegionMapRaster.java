package dev.maro.nathan.regionmap;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.GradientPaint;
import java.awt.font.GlyphVector;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** The static map artwork, rebuilt only when its scale, colours or label settings change. */
public final class RegionMapRaster {
    public static final double GRID_UNITS = 108;
    public static final double GUTTER_UNITS = 0.5;
    public static final double CORNER_UNITS = 0.9;
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
        return create(size, fills, inks, numbers, numberFont, numberSize, fitNumbers, false, -1, 0);
    }

    /**
     * @param gloss     tiles lit from above: a soft gradient, a bright top edge, a darker
     *                  bottom edge, and a faint shadow under each number
     * @param spotlight the {@link RegionGrid.Locale} ordinal left at full strength, or -1 for none
     * @param dim       how far every other group is sunk towards the panel, 0 to 1
     */
    public static Rendered create(int size, int[] fills, int[] inks, boolean numbers,
                                  NumberFont numberFont, double numberSize, boolean fitNumbers,
                                  boolean gloss, int spotlight, double dim) {
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
                int fill = sunk(fills[locale], spotlight >= 0 && locale != spotlight ? dim : 0);
                RoundRectangle2D tile = new RoundRectangle2D.Double(left, top, width, height, radius * 2, radius * 2);

                if (!gloss) {
                    graphics.setColor(new java.awt.Color(fill, true));
                    graphics.fill(tile);
                    continue;
                }

                graphics.setPaint(new GradientPaint((float) left, (float) top, new java.awt.Color(mix(fill, 0xFFFFFFFF, 0.16), true),
                    (float) left, (float) (top + height), new java.awt.Color(mix(fill, 0xFF000000, 0.12), true)));
                graphics.fill(tile);

                // A lit top lip and a shaded bottom lip, cut to the tile's own
                // rounded outline so the corners stay antialiased.
                double lip = Math.max(1, 0.4 * unit);
                Area light = new Area(tile);
                light.intersect(new Area(new Rectangle2D.Double(left, top, width, lip)));
                graphics.setColor(new java.awt.Color(255, 255, 255, (int) (70 * alpha(fill))));
                graphics.fill(light);

                Area shade = new Area(tile);
                shade.intersect(new Area(new Rectangle2D.Double(left, top + height - lip, width, lip)));
                graphics.setColor(new java.awt.Color(0, 0, 0, (int) (60 * alpha(fill))));
                graphics.fill(shade);
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
                    float glyphX = (float) (Math.round(left + (width - bounds.width) / 2) - bounds.x);
                    float glyphY = (float) (Math.round(top + (height - bounds.height) / 2) - bounds.y);
                    boolean faded = spotlight >= 0 && locale != spotlight;
                    int ink = faded ? (inks[locale] & 0xFFFFFF) | (int) ((inks[locale] >>> 24) * (1 - dim * 0.55)) << 24 : inks[locale];

                    if (gloss) {
                        graphics.setColor(new java.awt.Color(0, 0, 0, (int) (90 * alpha(ink))));
                        graphics.drawGlyphVector(glyphs, glyphX, glyphY + Math.max(1, Math.round(0.3f * (float) unit)));
                    }

                    graphics.setColor(new java.awt.Color(ink, true));
                    graphics.drawGlyphVector(glyphs, glyphX, glyphY);
                    labels++;
                }
            }
        } finally {
            graphics.dispose();
        }

        return new Rendered(image, labels);
    }

    /** A colour sunk {@code amount} of the way into the dark panel behind the map. */
    private static int sunk(int argb, double amount) {
        return amount <= 0 ? argb : mix(argb, 0xFF0B0D12, amount);
    }

    /** {@code from} blended towards {@code to}'s colour by {@code amount}, keeping {@code from}'s alpha. */
    private static int mix(int from, int to, double amount) {
        int r = (int) Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * amount);
        int g = (int) Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * amount);
        int b = (int) Math.round((from & 255) + ((to & 255) - (from & 255)) * amount);

        return from & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static double alpha(int argb) {
        return (argb >>> 24) / 255.0;
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
