package dev.maro.nathan.render;

/** Cached, antialiased artwork for the player surface; all coordinates use layout pixels. */
public final class SpotifyCardRaster {
    public enum Theme { AlbumColours, Midnight, FrostedGlass, Monochrome }
    public static final int PANEL_WIDTH = 420;
    public static final int PANEL_HEIGHT = 108;
    public static final int LYRICS_PANEL_HEIGHT = 184;
    public static final int PADDING = 18;
    private static final int DENSITY = 2;
    private static final double RADIUS = 18;

    /** Straight RGBA bytes, including the transparent padding around the card. */
    public record Raster(int width, int height, byte[] rgba) { }

    /** Fixed theme surfaces are baked once when the addon is initialized. */
    public static void prepareThemes() { Presets.RASTERS.size(); }
    public static Raster preset(Theme theme) { return Presets.RASTERS.get(theme); }
    public static Raster lyricsPreset(Theme theme) { return Presets.LYRICS.get(theme); }
    private static final class Presets {
        private static final java.util.Map<Theme, Raster> RASTERS = new java.util.EnumMap<>(Theme.class);
        private static final java.util.Map<Theme, Raster> LYRICS = new java.util.EnumMap<>(Theme.class);
        static { for (Theme theme : Theme.values()) {
            RASTERS.put(theme, render(0x8A9FC2, theme));
            LYRICS.put(theme, render(0x8A9FC2, theme, LYRICS_PANEL_HEIGHT));
        } }
    }

    private SpotifyCardRaster() { }

    /** Shared white alpha mask; tint and pulse its draw colour without rebuilding the raster. */
    public static Raster glow() {
        return GlowHolder.RASTER;
    }

    private static final class GlowHolder {
        private static final Raster RASTER = createGlow();
    }

    private static Raster createGlow() {
        int width = (PANEL_WIDTH + PADDING * 2) * DENSITY;
        int height = (PANEL_HEIGHT + PADDING * 2) * DENSITY;
        byte[] rgba = new byte[width * height * 4];
        double halfWidth = PANEL_WIDTH / 2.0;
        double halfHeight = PANEL_HEIGHT / 2.0;
        for (int y = 0; y < height; y++) {
            double py = (y + 0.5) / DENSITY - PADDING;
            for (int x = 0; x < width; x++) {
                double px = (x + 0.5) / DENSITY - PADDING;
                double distance = roundedDistance(px - halfWidth, py - halfHeight, halfWidth, halfHeight);
                double coverage = clamp(0.5 - distance * DENSITY);
                double fade = clamp(1 - Math.hypot((px - 46) / 52, (py - 44) / 50));
                int alpha = (int) Math.round(255 * coverage * fade * fade * (3 - 2 * fade));
                if (alpha == 0) continue;
                int offset = (y * width + x) * 4;
                rgba[offset] = (byte) 255;
                rgba[offset + 1] = (byte) 255;
                rgba[offset + 2] = (byte) 255;
                rgba[offset + 3] = (byte) alpha;
            }
        }
        return new Raster(width, height, rgba);
    }

    /** Generate only when the cached artwork tint changes, never during ordinary frame drawing. */
    public static Raster render(int tintRgb) {
        return render(tintRgb, Theme.AlbumColours);
    }

    public static Raster render(int tintRgb, Theme theme) {
        return render(tintRgb, theme, PANEL_HEIGHT);
    }

    public static Raster render(int tintRgb, Theme theme, int panelHeight) {
        int width = (PANEL_WIDTH + PADDING * 2) * DENSITY;
        int height = (panelHeight + PADDING * 2) * DENSITY;
        byte[] rgba = new byte[width * height * 4];
        double tintR = tintRgb >> 16 & 255;
        double tintG = tintRgb >> 8 & 255;
        double tintB = tintRgb & 255;
        double halfWidth = PANEL_WIDTH / 2.0;
        double halfHeight = panelHeight / 2.0;

        for (int y = 0; y < height; y++) {
            double py = (y + 0.5) / DENSITY - PADDING;
            double vertical = clamp(py / panelHeight);
            double topHighlight = 0.055 + 0.085 * Math.exp(-Math.max(0, py) / 22);
            double glowY = (py - 44) / 54;
            double coolY = (py - 40) / 80;
            for (int x = 0; x < width; x++) {
                double px = (x + 0.5) / DENSITY - PADDING;
                double distance = roundedDistance(px - halfWidth, py - halfHeight, halfWidth, halfHeight);
                double coverage = clamp(0.5 - distance * DENSITY);
                double shadowDistance = Math.max(0,
                    roundedDistance(px - halfWidth, py - halfHeight - 4, halfWidth, halfHeight));
                double shadowAlpha = 0.38 * Math.exp(-shadowDistance * shadowDistance / (2 * 4.5 * 4.5));

                // Colour overlays are composed into the opaque face first. The
                // rounded coverage and shadow then compose in premultiplied form.
                double red = mix(theme == Theme.FrostedGlass ? 64 : 22, theme == Theme.FrostedGlass ? 42 : 11, vertical);
                double green = mix(theme == Theme.FrostedGlass ? 76 : 27, theme == Theme.FrostedGlass ? 54 : 16, vertical);
                double blue = mix(theme == Theme.FrostedGlass ? 96 : 40, theme == Theme.FrostedGlass ? 75 : 29, vertical);
                double glowX = (px - 64) / 76;
                double glow = theme == Theme.AlbumColours ? 0.24 * Math.exp(-0.5 * (glowX * glowX + glowY * glowY)) : 0;
                red = mix(red, tintR, glow);
                green = mix(green, tintG, glow);
                blue = mix(blue, tintB, glow);
                double coolX = (px - 310) / 130;
                double cool = 0.035 * Math.exp(-0.5 * (coolX * coolX + coolY * coolY));
                red = mix(red, 92, cool);
                green = mix(green, 116, cool);
                blue = mix(blue, 170, cool);
                double edge = clamp(1 + distance / 1.2) * topHighlight;
                red = mix(red, 184, edge);
                green = mix(green, 201, edge);
                blue = mix(blue, 238, edge);

                if (theme == Theme.FrostedGlass) {
                    int noise = ((x * 73428767 ^ y * 912931) >>> 5) & 7;
                    red += (noise - 3.5) * 0.45;
                    green += (noise - 3.5) * 0.45;
                    blue += (noise - 3.5) * 0.45;
                    coverage *= 0.80;
                } else if (theme == Theme.Monochrome) {
                    double gray = red * 0.2126 + green * 0.7152 + blue * 0.0722;
                    red = green = blue = gray;
                }

                double alpha = coverage + shadowAlpha * (1 - coverage);
                int offset = (y * width + x) * 4;
                if (alpha > 0) {
                    // The shadow is black. Divide the accumulated premultiplied
                    // channels by alpha for the GPU's straight-alpha texture.
                    rgba[offset] = channel(red * coverage / alpha);
                    rgba[offset + 1] = channel(green * coverage / alpha);
                    rgba[offset + 2] = channel(blue * coverage / alpha);
                    rgba[offset + 3] = channel(alpha * 255);
                }
            }
        }
        return new Raster(width, height, rgba);
    }

    private static double roundedDistance(double x, double y, double halfWidth, double halfHeight) {
        double qx = Math.abs(x) - (halfWidth - RADIUS);
        double qy = Math.abs(y) - (halfHeight - RADIUS);
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - RADIUS;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double mix(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static byte channel(double value) {
        return (byte) Math.round(Math.max(0, Math.min(255, value)));
    }
}
