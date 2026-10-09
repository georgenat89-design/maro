package dev.maro.render.sky;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.runtime.renderer.Texture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A picture used as the sky. Decoded on a worker thread ({@link #decode}), then uploaded and drawn on
 * the render thread ({@link #install}). Big pictures are read at a reduced size so they fit in memory.
 */
public final class SkyImage implements AutoCloseable {
    /** How the picture is laid on the sky; order matches imageSky() in custom_sky.fsh. */
    public enum Fit { PANORAMA, WRAP, CUBE }

    /** About 4096 x 4096: plenty for a sky, and 64 MB at most. */
    private static final long MAX_PIXELS = 4096L * 4096L;
    private static final int MAX_SIDE = 8192;
    private static final long MAX_FILE = 64L * 1024 * 1024;

    /**
     * @param top    average colour along the top of the picture, for the sky above a wrapped picture
     * @param bottom average colour along the bottom, for below it
     * @param horizon average colour where the picture meets the horizon, for the fog
     */
    public record Decoded(byte[] pixels, int width, int height, int top, int bottom, int horizon, Fit fit) { }

    private Decoded image;
    private Texture texture;

    public static Decoded decode(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Choose a PNG or JPG image");
        if (Files.size(path) > MAX_FILE) throw new IOException("Choose an image smaller than 64 MB");
        BufferedImage read;
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Could not open this file");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Not an image this can read - use a PNG or JPG");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                long w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 2 || h < 2) throw new IOException("This image is too small");
                // Read huge pictures at a fraction of their size rather than all of it.
                int step = 1;
                while ((w / step) * (h / step) > MAX_PIXELS || w / step > MAX_SIDE || h / step > MAX_SIDE) step++;
                ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                read = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
        int w = read.getWidth(), h = read.getHeight();
        byte[] pixels = new byte[w * h * 4];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            read.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x], at = (y * w + x) * 4;
                pixels[at] = (byte) (argb >> 16);
                pixels[at + 1] = (byte) (argb >> 8);
                pixels[at + 2] = (byte) argb;
                pixels[at + 3] = (byte) 0xFF; // the sky is opaque
            }
        }
        Fit fit = autoFit(w, h);
        int horizonTop = fit == Fit.WRAP ? (int) (h * 0.8) : (int) (h * 0.45);
        int horizonBottom = fit == Fit.WRAP ? (int) (h * 0.92) : (int) (h * 0.55);
        int edge = Math.max(1, h / 30);
        return new Decoded(pixels, w, h, average(pixels, w, 0, edge), average(pixels, w, h - edge, h),
                average(pixels, w, horizonTop, Math.max(horizonTop + 1, horizonBottom)), fit);
    }

    /** A 2:1 picture is a 360 degree panorama, a 4:3 one a cube cross; anything else is wrapped round. */
    public static Fit autoFit(int width, int height) {
        double aspect = width / (double) height;
        if (Math.abs(aspect - 2.0) < 0.15) return Fit.PANORAMA;
        if (Math.abs(aspect - 4.0 / 3.0) < 0.08) return Fit.CUBE;
        return Fit.WRAP;
    }

    private static int average(byte[] pixels, int width, int fromRow, int toRow) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = fromRow; y < toRow; y++) {
            for (int x = 0; x < width; x += 4) {
                int at = (y * width + x) * 4;
                r += pixels[at] & 0xFF;
                g += pixels[at + 1] & 0xFF;
                b += pixels[at + 2] & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    public boolean loaded() {
        return texture != null;
    }

    public Decoded image() {
        return image;
    }

    public Texture texture() {
        return texture;
    }

    /** Uploads the picture; call on the render thread. */
    public void install(Decoded decoded) {
        Texture next = new Texture(decoded.width(), decoded.height(), TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
        try {
            next.upload(decoded.pixels());
        } catch (RuntimeException e) {
            next.close();
            throw e;
        }
        if (texture != null) texture.close();
        texture = next;
        // The GPU has it now; keep only the description, not the pixels.
        image = new Decoded(new byte[0], decoded.width(), decoded.height(), decoded.top(), decoded.bottom(), decoded.horizon(), decoded.fit());
    }

    @Override
    public void close() {
        if (texture != null) texture.close();
        texture = null;
        image = null;
    }
}
