package dev.maro.nathan.regionmap;

import java.awt.Font;
import java.awt.BasicStroke;
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
    public static final double GUTTER_UNITS = 0.85;
    public static final double CORNER_UNITS = 1.35;
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
        return create(size, fills, inks, numbers, numberFont, numberSize, fitNumbers, gloss, spotlight, dim, true);
    }

    public static Rendered create(int size, int[] fills, int[] inks, boolean numbers,
                                  NumberFont numberFont, double numberSize, boolean fitNumbers,
                                  boolean gloss, int spotlight, double dim, boolean compactNumberFit) {
        return create(size, fills, inks, numbers, numberFont, numberSize, fitNumbers, gloss, spotlight, dim, compactNumberFit, false);
    }

    /**
     * @param seamless the shards drawn edge to edge as one map, divided by fine lines, instead of
     *                 as separate rounded tiles; the whole map gets rounded corners
     */
    public static Rendered create(int size, int[] fills, int[] inks, boolean numbers,
                                  NumberFont numberFont, double numberSize, boolean fitNumbers,
                                  boolean gloss, int spotlight, double dim, boolean compactNumberFit, boolean seamless) {
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
            double gutter = seamless ? 0 : GUTTER_UNITS * unit;
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
                if (seamless) {
                    // Edge to edge on whole pixels, so neighbours meet without a seam; a fine
                    // darker line along the right and bottom divides each from the next.
                    int x0 = (int) Math.round(shard.col() * pitch), y0 = (int) Math.round(shard.row() * pitch);
                    int x1 = (int) Math.round((shard.col() + shard.width()) * pitch), y1 = (int) Math.round((shard.row() + shard.height()) * pitch);
                    graphics.setPaint(new GradientPaint(x0, y0, new java.awt.Color(mix(fill, 0xFFFFFFFF, 0.05), true),
                        x0, y1, new java.awt.Color(mix(fill, 0xFF000000, 0.05), true)));
                    graphics.fillRect(x0, y0, x1 - x0, y1 - y0);
                    graphics.setColor(new java.awt.Color(mix(fill, 0xFF0B0D12, 0.5), true));
                    graphics.fillRect(x1 - 1, y0, 1, y1 - y0);
                    graphics.fillRect(x0, y1 - 1, x1 - x0, 1);
                    continue;
                }
                RoundRectangle2D tile = new RoundRectangle2D.Double(left, top, width, height, radius * 2, radius * 2);

                if (!gloss) {
                    // A soft light from above: a touch lighter at the top, a touch deeper at the bottom.
                    graphics.setPaint(new GradientPaint((float) left, (float) top, new java.awt.Color(mix(fill, 0xFFFFFFFF, 0.07), true),
                        (float) left, (float) (top + height), new java.awt.Color(mix(fill, 0xFF000000, 0.08), true)));
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
                    double padding = Math.min(0.4 * unit, Math.min(width, height) * 0.06);
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
                    // Two digits in a narrow shard can otherwise shrink to only
                    // a few pixels high. Use the narrower cut only when it lets
                    // that label be taller; ordinary cells keep the chosen font.
                    if (compactNumberFit && fitNumbers && label.length() > 1 && numberFont != NumberFont.Condensed
                        && bounds.height < availableHeight - 1) {
                        Font narrow = font(NumberFont.Condensed);
                        double ratio = narrow.deriveFont(100f).createGlyphVector(graphics.getFontRenderContext(), "0").getVisualBounds().getHeight() / 100;
                        int narrowSize = Math.max(1, (int) Math.ceil(caps / ratio));
                        GlyphVector narrowGlyphs; Rectangle narrowBounds;
                        do {
                            narrowGlyphs = narrow.deriveFont((float)narrowSize).createGlyphVector(graphics.getFontRenderContext(), label);
                            narrowBounds = narrowGlyphs.getPixelBounds(graphics.getFontRenderContext(), 0, 0);
                            if ((narrowBounds.width <= availableWidth && narrowBounds.height <= availableHeight) || narrowSize == 1) break;
                            narrowSize--;
                        } while (true);
                        if (narrowBounds.height > bounds.height) { glyphs = narrowGlyphs; bounds = narrowBounds; }
                    }
                    float glyphX = (float) (Math.round(left + (width - bounds.width) / 2) - bounds.x);
                    float glyphY = (float) (Math.round(top + (height - bounds.height) / 2) - bounds.y);
                    // Numbers outside your group step back with their tiles, so yours read first.
                    boolean faded = spotlight >= 0 && locale != spotlight;
                    int ink = faded ? (inks[locale] & 0xFFFFFF) | (int) ((inks[locale] >>> 24) * Math.max(0.3, 1 - dim * 1.1)) << 24 : inks[locale];

                    // A soft shadow under each number keeps it clear on light fills without the
                    // heavy look of an outline.
                    graphics.setColor(new java.awt.Color(0, 0, 0, (int) ((gloss ? 90 : 105) * alpha(ink))));
                    graphics.drawGlyphVector(glyphs, glyphX, glyphY + Math.max(1, Math.round(0.3f * (float) unit)));
                    if (!faded && !gloss && !seamless) {
                        graphics.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        graphics.setColor(new java.awt.Color(0, 0, 0, (int) (60 * alpha(ink))));
                        graphics.draw(glyphs.getOutline(glyphX, glyphY));
                    }

                    graphics.setColor(new java.awt.Color(ink, true));
                    graphics.drawGlyphVector(glyphs, glyphX, glyphY);
                    labels++;
                }
            }
            if (seamless) roundCorners(image, Math.max(2, CORNER_UNITS * 2.2 * unit));
        } finally {
            graphics.dispose();
        }

        return new Rendered(image, labels);
    }

    /** Fades the image's corners out round a quarter circle of {@code radius} pixels, antialiased. */
    private static void roundCorners(BufferedImage image, double radius) {
        int w = image.getWidth(), h = image.getHeight(), r = (int) Math.ceil(radius);
        for (int y = 0; y < Math.min(r, h); y++) {
            for (int x = 0; x < Math.min(r, w); x++) {
                double dx = radius - (x + 0.5), dy = radius - (y + 0.5);
                double cover = Math.max(0, Math.min(1, radius - Math.sqrt(dx * dx + dy * dy) + 0.5));
                if (cover >= 1) continue;
                for (int[] at : new int[][] {{x, y}, {w - 1 - x, y}, {x, h - 1 - y}, {w - 1 - x, h - 1 - y}}) {
                    int argb = image.getRGB(at[0], at[1]);
                    image.setRGB(at[0], at[1], argb & 0xFFFFFF | (int) Math.round((argb >>> 24) * cover) << 24);
                }
            }
        }
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
