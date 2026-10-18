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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Pack icons as textures: Modrinth's fetched from the web, your own packs' read from their
 * pack.png. Each is loaded once, off the game thread, fitted into 64 by 64 and kept for the session.
 * Modrinth stores many icons as WebP, which Java cannot read itself, so those go through the
 * bundled WebP reader.
 */
public final class PackIcons {
    private PackIcons() {
    }

    private static final int SIZE = 64;

    private static final class Entry {
        volatile Identifier id;
        volatile NativeImage ready;
        volatile boolean failed, forgotten;
    }

    private static final Map<String, Entry> ICONS = new ConcurrentHashMap<>();

    /** The icon's texture once it is here, or null while it loads (or if it could not be read). */
    public static Identifier get(String key, String url) {
        if (url == null || url.isBlank()) return null;
        return load(key, () -> Modrinth.bytes(url));
    }

    /** Full screenshot previews preserve their aspect ratio in a 512×288 canvas. */
    public static Identifier preview(String key, String url) {
        if (url == null || url.isBlank()) return null;
        return load("preview/" + key, () -> PacksMcCatalog.previewBytes(url), 512, 288);
    }

    /** A pack's own icon, read by {@code read} off the game thread; null while it loads or if it has none. */
    public static Identifier local(String key, Callable<byte[]> read) {
        return load(key, () -> CompletableFuture.supplyAsync(() -> {
            try {
                return read.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Modrinth.POOL));
    }

    /** Drops a loaded icon so the next ask reads it again (a pack file that was swapped for another). */
    public static void forget(String key) {
        Entry entry=ICONS.remove(key); if(entry==null)return;
        synchronized(entry) { entry.forgotten=true;if(entry.ready!=null){entry.ready.close();entry.ready=null;} }
        if(entry.id!=null)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id));
    }

    private static Identifier load(String key, Supplier<CompletableFuture<byte[]>> source) {
        return load(key, source, SIZE, SIZE);
    }

    private static Identifier load(String key, Supplier<CompletableFuture<byte[]>> source, int width, int height) {
        Entry entry = ICONS.computeIfAbsent(key, k -> {
            Entry e = new Entry();
            source.get().thenApplyAsync(bytes -> decode(bytes, width, height), Modrinth.POOL).whenComplete((image, error) -> {
                synchronized(e) {
                    if(e.forgotten){if(image!=null)image.close();}
                    else if (error != null || image == null) e.failed = true;
                    else e.ready = image;
                }
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
        return decode(bytes, SIZE, SIZE);
    }

    private static NativeImage decode(byte[] bytes, int width, int height) {
        BufferedImage source = null;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) {
            // Not a format Java knows: WebP is tried next.
        }
        if (source == null) source = webp(bytes);
        if (source == null) throw new IllegalStateException("Unreadable icon");

        // Fit inside the square, centred: smooth when shrunk, crisp pixels when a small pixel-art icon is blown up.
        boolean small = source.getWidth() < width && source.getHeight() < height;
        BufferedImage square = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, small ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float scale = Math.min((float) width / source.getWidth(), (float) height / source.getHeight());
            int w = Math.max(1, Math.round(source.getWidth() * scale)), h = Math.max(1, Math.round(source.getHeight() * scale));
            g.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        } finally {
            g.dispose();
        }
        NativeImage image = new NativeImage(width, height, true);
        float radius=width==512&&height==288?15f:0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel=square.getRGB(x,y);
                if(radius>0){
                    float dx=x+.5f-Math.clamp(x+.5f,radius,width-radius),dy=y+.5f-Math.clamp(y+.5f,radius,height-radius);
                    float coverage=Math.clamp(radius+.5f-(float)Math.sqrt(dx*dx+dy*dy),0,1);
                    pixel=(pixel&0xFFFFFF)|(Math.round((pixel>>>24)*coverage)<<24);
                }
                image.setColorArgb(x,y,pixel);
            }
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
