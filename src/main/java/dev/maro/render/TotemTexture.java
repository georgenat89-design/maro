package dev.maro.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns a picture into the Totem of Undying's texture, in one of three shapes:
 * <ul>
 *   <li>{@link Shape#WRAP}: the picture wrapped over the game's own totem, keeping its outline and
 *   some of its shading, like a vinyl wrap; see-through parts of the picture show the totem.</li>
 *   <li>{@link Shape#CUT_OUT}: a plain background is taken away, so what is left of the picture
 *   is the shape of the item.</li>
 *   <li>{@link Shape#SQUARE}: the whole picture as it is.</li>
 * </ul>
 * Textures are squares of 16, 32 or 64 pixels. Pictures that already fit keep their exact pixels
 * (pixel art stays sharp); bigger ones are shrunk by averaging, keeping their shape.
 */
public final class TotemTexture {
    /** The biggest the totem texture gets; more detail than this only slows the 3D item down. */
    public static final int MAX_SIZE = 64;
    /** Pictures are kept at up to this size to work from: enough to find a background's edge cleanly. */
    private static final int WORK_SIZE = 256;

    public enum Shape { WRAP, CUT_OUT, SQUARE }

    /** Your picture as read, at up to 256 pixels on its longest side; {@code width * height} pixels, row by row. */
    public record Decoded(int[] argb, int width, int height, int sourceWidth, int sourceHeight) { }

    /** A totem texture ready to use: {@code size * size} pixels, row by row. */
    public record Made(int[] argb, int size) { }

    /** One frame of the game's own totem texture, whose outline and shading {@link Shape#WRAP} follows. */
    public record Base(int[] argb, int width, int height) { }

    private TotemTexture() {
    }

    public static Decoded decode(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Choose a PNG image");
        if (Files.size(path) > 16L * 1024 * 1024) throw new IOException("Choose an image smaller than 16 MB");
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) throw new IOException("Not an image this can read - use a PNG");
        int w = source.getWidth(), h = source.getHeight();
        if (w > 8192 || h > 8192) throw new IOException("Image must be 8192 x 8192 or smaller");
        double scale = Math.min(1, WORK_SIZE / (double) Math.max(w, h));
        int dw = Math.max(1, (int) Math.round(w * scale)), dh = Math.max(1, (int) Math.round(h * scale));
        int[] pixels = new int[dw * dh];
        if (dw == w && dh == h) {
            source.getRGB(0, 0, w, h, pixels, 0, w);
        } else {
            // A band of rows at a time, so a huge picture is never held in full.
            int[] band = new int[w * (h / dh + 2)];
            for (int ty = 0; ty < dh; ty++) {
                int y0 = ty * h / dh, y1 = Math.max(y0 + 1, (ty + 1) * h / dh);
                source.getRGB(0, y0, w, y1 - y0, band, 0, w);
                for (int tx = 0; tx < dw; tx++) {
                    int x0 = tx * w / dw, x1 = Math.max(x0 + 1, (tx + 1) * w / dw);
                    pixels[ty * dw + tx] = average(band, w, x0, 0, x1, y1 - y0);
                }
            }
        }
        boolean visible = false;
        for (int argb : pixels) visible |= (argb >>> 24) != 0;
        if (!visible) throw new IOException("This image is completely transparent");
        return new Decoded(pixels, dw, dh, w, h);
    }

    /** The totem texture for {@code picture} in {@code shape}; {@code detail} (0 to 1) is how much of the totem's shading a wrap keeps. */
    public static Made make(Decoded picture, Shape shape, Base totem, float detail) {
        return switch (shape) {
            case WRAP -> totem != null ? wrap(picture, totem, detail) : whole(picture);
            case CUT_OUT -> cutOut(picture);
            case SQUARE -> whole(picture);
        };
    }

    private static Made whole(Decoded picture) {
        return fit(picture.argb(), picture.width(), 0, 0, picture.width(), picture.height());
    }

    // ---- wrap -------------------------------------------------------------------------------

    private static Made wrap(Decoded picture, Base totem, float detail) {
        int tw = totem.width(), th = totem.height();
        int size = 16;
        while (size < Math.min(Math.max(Math.max(picture.width(), picture.height()), Math.max(tw, th)), MAX_SIZE)) size *= 2;

        // The totem blown up to the texture's size, pixel for pixel, and the box round its outline.
        int[] base = new int[size * size];
        int x0 = size, y0 = size, x1 = 0, y1 = 0, solid = 0;
        double brightness = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int t = totem.argb()[(y * th / size) * tw + x * tw / size];
                base[y * size + x] = t;
                if (t >>> 24 < 128) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x + 1);
                y1 = Math.max(y1, y + 1);
                brightness += luminance(t);
                solid++;
            }
        }
        if (solid == 0) return whole(picture);
        double mean = brightness / solid;

        // The picture covers the totem's box keeping its own shape; what overhangs is cropped evenly.
        int boxW = x1 - x0, boxH = y1 - y0;
        double scale = Math.max(boxW / (double) picture.width(), boxH / (double) picture.height());
        double ox = (picture.width() - boxW / scale) / 2, oy = (picture.height() - boxH / scale) / 2;
        int[] out = new int[size * size];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int t = base[y * size + x];
                if (t >>> 24 < 128) continue;
                double sx = ox + (x - x0) / scale, sy = oy + (y - y0) / scale;
                int p = sample(picture, sx, sy, sx + 1 / scale, sy + 1 / scale);
                if (p >>> 24 < 128) {
                    // See-through parts of your picture show the totem underneath.
                    out[y * size + x] = t | 0xFF000000;
                    continue;
                }
                // The totem's own light and dark - its edges, face and folds - showing through.
                int shade = (int) Math.round((luminance(t) - mean) * detail * 255);
                out[y * size + x] = 0xFF000000 | channel((p >> 16 & 0xFF) + shade) << 16 | channel((p >> 8 & 0xFF) + shade) << 8
                        | channel((p & 0xFF) + shade);
            }
        }
        return new Made(out, size);
    }

    // ---- cut out ----------------------------------------------------------------------------

    private static Made cutOut(Decoded picture) {
        int w = picture.width(), h = picture.height();
        int[] px = picture.argb().clone();
        int background = borderColour(px, w, h);

        // Everything joined to the edge that is see-through or the background's colour goes.
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int top = 0;
        for (int x = 0; x < w; x++) {
            top = push(px, seen, stack, top, x, background);
            top = push(px, seen, stack, top, (h - 1) * w + x, background);
        }
        for (int y = 0; y < h; y++) {
            top = push(px, seen, stack, top, y * w, background);
            top = push(px, seen, stack, top, y * w + w - 1, background);
        }
        while (top > 0) {
            int i = stack[--top];
            px[i] = 0;
            int x = i % w, y = i / w;
            if (x > 0) top = push(px, seen, stack, top, i - 1, background);
            if (x < w - 1) top = push(px, seen, stack, top, i + 1, background);
            if (y > 0) top = push(px, seen, stack, top, i - w, background);
            if (y < h - 1) top = push(px, seen, stack, top, i + w, background);
        }

        // Then what is left is cropped to and fills the texture.
        int x0 = w, y0 = h, x1 = 0, y1 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (px[y * w + x] >>> 24 < 128) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x + 1);
                y1 = Math.max(y1, y + 1);
            }
        }
        if (x1 <= x0) return whole(picture); // it was all background: keep the picture
        Made made = fit(px, w, x0, y0, x1, y1);
        // Crisp edges: the 3D item is either there or not, with nothing in between.
        int[] argb = made.argb();
        for (int i = 0; i < argb.length; i++) argb[i] = argb[i] >>> 24 >= 128 ? argb[i] | 0xFF000000 : 0;
        return made;
    }

    private static int push(int[] px, boolean[] seen, int[] stack, int top, int i, int background) {
        if (seen[i] || !isBackground(px[i], background)) return top;
        seen[i] = true;
        stack[top] = i;
        return top + 1;
    }

    private static boolean isBackground(int argb, int background) {
        if (argb >>> 24 < 128) return true;
        if (background == -1) return false;
        return Math.abs((argb >> 16 & 0xFF) - (background >> 16 & 0xFF)) + Math.abs((argb >> 8 & 0xFF) - (background >> 8 & 0xFF))
                + Math.abs((argb & 0xFF) - (background & 0xFF)) <= 96;
    }

    /**
     * The colour most of the picture's edge is, if it has one: the background to take away. -1 if
     * the edge is mostly see-through or has no main colour (a photo, say), when only see-through
     * parts go.
     */
    private static int borderColour(int[] px, int w, int h) {
        int[] counts = new int[4096];
        int[] first = new int[4096];
        int border = 0;
        for (int i = 0; i < w * h; i++) {
            int x = i % w, y = i / w;
            if (x != 0 && y != 0 && x != w - 1 && y != h - 1) continue;
            border++;
            int argb = px[i];
            if (argb >>> 24 < 128) continue;
            int bucket = (argb >> 20 & 0xF) << 8 | (argb >> 12 & 0xF) << 4 | (argb >> 4 & 0xF);
            if (counts[bucket]++ == 0) first[bucket] = argb;
        }
        int best = 0;
        for (int b = 1; b < counts.length; b++) if (counts[b] > counts[best]) best = b;
        return counts[best] * 10 >= border * 4 ? first[best] : -1;
    }

    // ---- shared -----------------------------------------------------------------------------

    /** Part of a picture, fitted in the middle of the smallest texture that holds it, pixel for pixel if it fits. */
    private static Made fit(int[] src, int stride, int x0, int y0, int x1, int y1) {
        int w = x1 - x0, h = y1 - y0, longest = Math.max(w, h);
        int size = 16;
        while (size < Math.min(longest, MAX_SIZE)) size *= 2;
        int[] out = new int[size * size];
        if (longest <= size) {
            int ox = (size - w) / 2, oy = (size - h) / 2;
            for (int y = 0; y < h; y++) System.arraycopy(src, (y0 + y) * stride + x0, out, (y + oy) * size + ox, w);
            return new Made(out, size);
        }
        double scale = size / (double) longest;
        int dw = Math.max(1, (int) Math.round(w * scale)), dh = Math.max(1, (int) Math.round(h * scale));
        int ox = (size - dw) / 2, oy = (size - dh) / 2;
        for (int ty = 0; ty < dh; ty++) {
            int sy0 = y0 + ty * h / dh, sy1 = Math.max(sy0 + 1, y0 + (ty + 1) * h / dh);
            for (int tx = 0; tx < dw; tx++) {
                int sx0 = x0 + tx * w / dw, sx1 = Math.max(sx0 + 1, x0 + (tx + 1) * w / dw);
                out[(ty + oy) * size + tx + ox] = average(src, stride, sx0, sy0, sx1, sy1);
            }
        }
        return new Made(out, size);
    }

    /** The picture over x0..x1, y0..y1 in its own pixels: averaged when that is more than a pixel, else the pixel there. */
    private static int sample(Decoded picture, double x0, double y0, double x1, double y1) {
        int w = picture.width(), h = picture.height();
        int ix0, ix1, iy0, iy1;
        if (x1 - x0 < 1) {
            ix0 = clamp((int) Math.floor((x0 + x1) / 2), 0, w - 1);
            ix1 = ix0 + 1;
        } else {
            ix0 = clamp((int) Math.floor(x0), 0, w - 1);
            ix1 = clamp((int) Math.ceil(x1), ix0 + 1, w);
        }
        if (y1 - y0 < 1) {
            iy0 = clamp((int) Math.floor((y0 + y1) / 2), 0, h - 1);
            iy1 = iy0 + 1;
        } else {
            iy0 = clamp((int) Math.floor(y0), 0, h - 1);
            iy1 = clamp((int) Math.ceil(y1), iy0 + 1, h);
        }
        return average(picture.argb(), w, ix0, iy0, ix1, iy1);
    }

    /** The average of a block of pixels, weighted by alpha so see-through pixels do not darken edges. */
    private static int average(int[] src, int stride, int x0, int y0, int x1, int y1) {
        long a = 0, r = 0, g = 0, b = 0, n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int argb = src[y * stride + x];
                int pa = argb >>> 24;
                a += pa;
                r += (long) (argb >> 16 & 0xFF) * pa;
                g += (long) (argb >> 8 & 0xFF) * pa;
                b += (long) (argb & 0xFF) * pa;
                n++;
            }
        }
        if (a == 0) return 0;
        return (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    }

    private static double luminance(int argb) {
        return (0.299 * (argb >> 16 & 0xFF) + 0.587 * (argb >> 8 & 0xFF) + 0.114 * (argb & 0xFF)) / 255.0;
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
