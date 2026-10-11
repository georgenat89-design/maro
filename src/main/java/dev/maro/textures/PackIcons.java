package dev.maro.textures;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modrinth pack icons as textures: fetched once each, off the game thread, shrunk to 64 by 64 and
 * kept for the session. Modrinth stores many icons as WebP, which Java cannot read itself, so those
 * go through the bundled WebP reader.
 */
public final class PackIcons {
    private PackIcons() {
    }

    private static final int SIZE = 64;

    private static final class Entry {
        volatile Identifier id;
        volatile NativeImage ready;
        volatile boolean failed;
    }

    private static final Map<String, Entry> ICONS = new ConcurrentHashMap<>();

    /** The icon's texture once it is here, or null while it loads (or if it could not be read). */
    public static Identifier get(String key, String url) {
        if (url == null || url.isBlank()) return null;
        Entry entry = ICONS.computeIfAbsent(key, k -> {
            Entry e = new Entry();
            Modrinth.bytes(url).thenApplyAsync(PackIcons::decode, Modrinth.POOL).whenComplete((image, error) -> {
                if (error != null || image == null) e.failed = true;
                else e.ready = image;
            });
            return e;
        });
        NativeImage image = entry.ready;
        if (image != null && entry.id == null) {
            // Uploaded on the render thread, the first frame it is wanted.
            entry.ready = null;
            Identifier id = Identifier.of("maro", "pack_icons/" + key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "maro pack icon " + key, image));
            entry.id = id;
        }
        return entry.id;
    }

    /** Whether the icon could not be had at all, so a stand-in is drawn instead of a spinner. */
    public static boolean failed(String key) {
        Entry entry = ICONS.get(key);
        return entry != null && entry.failed;
    }

    static NativeImage decode(byte[] bytes) {
        BufferedImage source = null;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) {
            // Not a format Java knows: WebP is tried next.
        }
        if (source == null) source = webp(bytes);
        if (source == null) throw new IllegalStateException("Unreadable icon");

        // Fit inside the square, centred, smooth.
        BufferedImage square = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float scale = Math.min((float) SIZE / source.getWidth(), (float) SIZE / source.getHeight());
            int w = Math.max(1, Math.round(source.getWidth() * scale)), h = Math.max(1, Math.round(source.getHeight() * scale));
            g.drawImage(source, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        } finally {
            g.dispose();
        }
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) image.setColorArgb(x, y, square.getRGB(x, y));
        }
        return image;
    }

    private static BufferedImage webp(byte[] bytes) {
        try {
            ImageReader reader = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                reader.setInput(in);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            // No reader, or a damaged file: a stand-in tile is drawn instead.
            return null;
        }
    }
}
