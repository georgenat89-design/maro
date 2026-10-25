package dev.maro.render.character.kit;

import dev.maro.render.character.skin.SkinnedCharacter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.zip.InflaterInputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * A skinned character's textures, decoded on the loading thread at their own resolution with a
 * full mip chain, ready for the render thread to upload in one go. PNG and JPEG as the file stored
 * them; raw RGBA where the bake had to shrink an image.
 */
@Environment(value=EnvType.CLIENT)
public final class SkinImages {
    private static final int MAX_LEVELS = 9;
    private static final int[] TO_LINEAR = new int[256], TO_SRGB = new int[65536];
    static {
        for (int i = 0; i < TO_LINEAR.length; i++) {
            double s = i / 255.0;
            TO_LINEAR[i] = (int)Math.round((s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4)) * 65535);
        }
        for (int i = 0; i < TO_SRGB.length; i++) {
            double linear = i / 65535.0;
            TO_SRGB[i] = (int)Math.round((linear <= .0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - .055) * 255);
        }
    }

    private SkinImages() {
    }

    /** Every image's mip chain; a failed image rejects the model instead of rendering a white body. */
    public static NativeImage[][] prepare(SkinnedCharacter c) {
        NativeImage[][] out = new NativeImage[c.images.length][];
        try {
            for (int i = 0; i < out.length; i++) {
                int[] argb;
                int w = c.imageWidth[i], h = c.imageHeight[i];
                try {
                    if (c.imageRaw[i]) argb = inflate(c.images[i], w, h);
                    else {
                        int[] size = new int[2];
                        argb = decode(c.images[i], size);
                        w = size[0];
                        h = size[1];
                    }
                } catch (IOException | RuntimeException e) {
                    throw new IllegalStateException("Could not decode character texture " + i + " (" + w + "x" + h + ")", e);
                }
                out[i] = mips(argb, w, h);
            }
            return out;
        } catch (Throwable t) {
            close(out);
            throw t;
        }
    }

    public static void close(NativeImage[][] images) {
        if (images == null) return;
        for (NativeImage[] levels : images) {
            if (levels == null) continue;
            for (NativeImage image : levels) if (image != null) image.close();
        }
    }

    /** PNG or JPEG bytes to ARGB pixels. */
    public static int[] decode(byte[] bytes, int[] size) throws IOException {
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), comp = stack.mallocInt(1);
            ByteBuffer rgba = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
            if (rgba == null) throw new IOException("unreadable texture: " + STBImage.stbi_failure_reason());
            try {
                int width = w.get(0), height = h.get(0);
                int[] argb = new int[width * height];
                for (int p = 0; p < argb.length; p++) {
                    int o = p * 4;
                    argb[p] = (rgba.get(o + 3) & 0xFF) << 24 | (rgba.get(o) & 0xFF) << 16 | (rgba.get(o + 1) & 0xFF) << 8 | (rgba.get(o + 2) & 0xFF);
                }
                size[0] = width;
                size[1] = height;
                return argb;
            } finally {
                STBImage.stbi_image_free(rgba);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static int[] inflate(byte[] packed, int w, int h) throws IOException {
        byte[] raw = new byte[w * h * 4];
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(packed))) {
            if (in.readNBytes(raw, 0, raw.length) != raw.length) throw new IOException("texture is truncated");
        }
        int[] argb = new int[w * h];
        for (int p = 0; p < argb.length; p++) {
            int o = p * 4;
            argb[p] = (raw[o + 3] & 0xFF) << 24 | (raw[o] & 0xFF) << 16 | (raw[o + 1] & 0xFF) << 8 | (raw[o + 2] & 0xFF);
        }
        return argb;
    }

    /**
     * Halves down to a few pixels, averaging colour by coverage so the edges of hair and lashes
     * keep their colour as they thin out.
     */
    static NativeImage[] mips(int[] argb, int w, int h) {
        int levels = 1;
        for (int s = Math.min(w, h); s > 4 && levels < MAX_LEVELS; s /= 2) levels++;
        NativeImage[] out = new NativeImage[levels];
        try {
            for (int l = 0; l < levels; l++) {
                if (l > 0) {
                    argb = half(argb, w, h);
                    w = Math.max(1, w / 2);
                    h = Math.max(1, h / 2);
                }
                NativeImage image = new NativeImage(w, h, false);
                out[l] = image;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setColorArgb(x, y, argb[y * w + x]);
            }
            return out;
        } catch (Throwable t) {
            for (NativeImage image : out) if (image != null) image.close();
            throw t;
        }
    }

    private static int[] half(int[] px, int w, int h) {
        int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = px[Math.min(h - 1, y * 2 + dy) * w + Math.min(w - 1, x * 2 + dx)];
                        int pa = p >>> 24;
                        a += pa;
                        r += TO_LINEAR[p >> 16 & 0xFF] * pa;
                        g += TO_LINEAR[p >> 8 & 0xFF] * pa;
                        b += TO_LINEAR[p & 0xFF] * pa;
                    }
                }
                if (a == 0) {
                    // Fully clear: keep the plain average colour so filtering never pulls in black.
                    int p0 = px[Math.min(h - 1, y * 2) * w + Math.min(w - 1, x * 2)];
                    out[y * nw + x] = p0 & 0xFFFFFF;
                    continue;
                }
                out[y * nw + x] = (a + 2) / 4 << 24 | TO_SRGB[(r + a / 2) / a] << 16
                        | TO_SRGB[(g + a / 2) / a] << 8 | TO_SRGB[(b + a / 2) / a];
            }
        }
        return out;
    }
}
