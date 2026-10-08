package dev.maro.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A picture made ready to be the Totem of Undying's texture: a transparent square of 16, 32 or 64
 * pixels with the picture centred in it. Pictures that already fit keep their exact pixels (pixel
 * art stays sharp); bigger ones are shrunk by averaging, keeping their shape and transparency.
 */
public final class TotemTexture {
    /** The biggest the totem texture gets; more detail than this only slows the 3D item down. */
    public static final int MAX_SIZE = 64;

    /** @param argb {@code size * size} pixels, row by row */
    public record Decoded(int[] argb, int size, int sourceWidth, int sourceHeight) { }

    private TotemTexture() {
    }

    public static Decoded decode(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Choose a PNG image");
        if (Files.size(path) > 16L * 1024 * 1024) throw new IOException("Choose an image smaller than 16 MB");
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) throw new IOException("Not an image this can read - use a PNG");
        int w = source.getWidth(), h = source.getHeight();
        if (w > 8192 || h > 8192) throw new IOException("Image must be 8192 × 8192 or smaller");
        int longest = Math.max(w, h);
        int size = 16;
        while (size < Math.min(longest, MAX_SIZE)) size *= 2;

        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[size * size];
        if (longest <= size) {
            // Fits already: copy it in, centred, pixel for pixel.
            int ox = (size - w) / 2, oy = (size - h) / 2;
            for (int y = 0; y < h; y++) System.arraycopy(pixels, y * w, out, (y + oy) * size + ox, w);
        } else {
            shrink(pixels, w, h, out, size);
        }
        boolean visible = false;
        for (int argb : out) visible |= (argb >>> 24) != 0;
        if (!visible) throw new IOException("This image is completely transparent");
        return new Decoded(out, size, w, h);
    }

    /** Averages {@code pixels} down into the middle of {@code out}, keeping the picture's shape. */
    private static void shrink(int[] pixels, int w, int h, int[] out, int size) {
        double scale = size / (double) Math.max(w, h);
        int dw = Math.max(1, (int) Math.round(w * scale)), dh = Math.max(1, (int) Math.round(h * scale));
        int ox = (size - dw) / 2, oy = (size - dh) / 2;
        for (int ty = 0; ty < dh; ty++) {
            int y0 = ty * h / dh, y1 = Math.max(y0 + 1, (ty + 1) * h / dh);
            for (int tx = 0; tx < dw; tx++) {
                int x0 = tx * w / dw, x1 = Math.max(x0 + 1, (tx + 1) * w / dw);
                // Weighted by alpha, so transparent pixels do not darken the edges.
                long a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int argb = pixels[y * w + x];
                        int pa = argb >>> 24;
                        a += pa;
                        r += (long) (argb >> 16 & 0xFF) * pa;
                        g += (long) (argb >> 8 & 0xFF) * pa;
                        b += (long) (argb & 0xFF) * pa;
                        n++;
                    }
                }
                int alpha = (int) (a / n);
                int argb = a == 0 ? 0 : alpha << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                out[(ty + oy) * size + tx + ox] = argb;
            }
        }
    }
}
