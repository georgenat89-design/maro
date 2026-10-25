package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.Maro;
import dev.maro.config.ConfigManager;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/** One owned background asset. File access and bounded decoding run off the render thread. */
public final class GuiBackgroundImage implements AutoCloseable {
    public static final int MAX_EDGE = 4096, MAX_PIXELS = 4_194_304;
    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    public record Decoded(byte[] png, byte[] rgba, int width, int height, int originalWidth, int originalHeight) { }
    private final Path file;
    private final Object io = new Object();
    private volatile int generation;
    private boolean attempted, busy, closed;
    private String status = "No PNG imported. Choose a file or drop it into the studio.";
    private Texture texture;
    private byte[] pixels;
    private int width, height;

    public GuiBackgroundImage() { this(ConfigManager.DIR.resolve("assets/gui-background.png")); }
    public GuiBackgroundImage(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Path file() { return file; }
    public boolean busy() { return busy; }
    public boolean loaded() { return texture != null; }
    public Texture texture() { return texture; }
    public int width() { return width; }
    public int height() { return height; }
    public String status() { return status; }

    /** Checks headers before allocating pixels and downsamples before decoding huge source images. */
    public static Decoded decode(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("This PNG file is missing or unavailable.");
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Choose a PNG smaller than 32 MB.");
        byte[] input;
        try (var source = Files.newInputStream(path)) { input = source.readNBytes((int)MAX_FILE_BYTES + 1); }
        if (input.length > MAX_FILE_BYTES) throw new IOException("Choose a PNG smaller than 32 MB.");
        if (input.length < 8 || input[0] != (byte)137 || input[1] != 80 || input[2] != 78 || input[3] != 71
                || input[4] != 13 || input[5] != 10 || input[6] != 26 || input[7] != 10) throw new IOException("Choose a valid PNG image.");
        BufferedImage decoded;
        int originalWidth, originalHeight, targetWidth, targetHeight;
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("This PNG could not be decoded.");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Choose a PNG image.");
                originalWidth = reader.getWidth(0); originalHeight = reader.getHeight(0);
                if (originalWidth < 1 || originalHeight < 1 || originalWidth > 32768 || originalHeight > 32768
                        || (long)originalWidth * originalHeight > 67_108_864L) throw new IOException("PNG dimensions exceed the safe 64-megapixel import limit.");
                double ratio = Math.min(1, Math.min(MAX_EDGE / (double)Math.max(originalWidth, originalHeight),
                        Math.sqrt(MAX_PIXELS / ((double)originalWidth * originalHeight))));
                targetWidth = Math.max(1, (int)Math.floor(originalWidth * ratio));
                targetHeight = Math.max(1, (int)Math.floor(originalHeight * ratio));
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, (int)Math.floor(1 / ratio));
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                decoded = reader.read(0, parameters);
            } finally { reader.dispose(); }
        } catch (RuntimeException error) { throw new IOException("This PNG is damaged or unsupported.", error); }
        if (decoded == null) throw new IOException("This PNG could not be decoded.");
        BufferedImage normalized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = normalized.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
        } finally { graphics.dispose(); decoded.flush(); }
        byte[] rgba = new byte[targetWidth * targetHeight * 4];
        for (int y = 0; y < targetHeight; y++) for (int x = 0; x < targetWidth; x++) {
            int argb = normalized.getRGB(x, y), offset = (y * targetWidth + x) * 4;
            rgba[offset] = (byte)(argb >> 16); rgba[offset + 1] = (byte)(argb >> 8);
            rgba[offset + 2] = (byte)argb; rgba[offset + 3] = (byte)(argb >>> 24);
        }
        var encoded = new ByteArrayOutputStream();
        try { if (!ImageIO.write(normalized, "PNG", encoded)) throw new IOException("PNG encoding is unavailable."); }
        finally { normalized.flush(); }
        return new Decoded(encoded.toByteArray(), rgba, targetWidth, targetHeight, originalWidth, originalHeight);
    }

    public void ensureLoaded() {
        if (closed || attempted || busy) return;
        attempted = true;
        load(file, false);
    }
    public void importPng(Path source) { if (!closed && !busy) { attempted = true; load(source, true); } }
    private void load(Path source, boolean persist) {
        busy = true; status = persist ? "Importing PNG…" : "Loading saved PNG…";
        int token = ++generation;
        CompletableFuture.supplyAsync(() -> {
            try {
                if (!persist && !Files.exists(source)) return null;
                Decoded result = decode(source);
                if (persist) synchronized (io) {
                    if (generation != token) return null;
                    Files.createDirectories(file.getParent());
                    Path temporary = Files.createTempFile(file.getParent(), "background-", ".tmp");
                    try {
                        Files.write(temporary, result.png());
                        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                        catch (AtomicMoveNotSupportedException error) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(temporary); }
                }
                return result;
            } catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((decoded, error) -> MinecraftClient.getInstance().execute(() -> {
            if (closed || token != generation) return;
            busy = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                status = cause instanceof IOException ? cause.getMessage() : "PNG import failed. The previous image is still available.";
                Maro.LOGGER.warn("GUI background PNG could not be loaded: {}", status);
                return;
            }
            if (decoded == null) { status = "No saved PNG. Import one or use another background style."; return; }
            try {
                upload(decoded.rgba(), decoded.width(), decoded.height());
                status = decoded.width() + " × " + decoded.height() + (decoded.width() != decoded.originalWidth() || decoded.height() != decoded.originalHeight() ? " · resized safely from " + decoded.originalWidth() + " × " + decoded.originalHeight() : " · transparency preserved");
                if (persist) { dev.maro.config.ClientSettings.guiBackgroundMode.set("PNG Image"); ConfigManager.saveClient(); }
            } catch (RuntimeException failure) {
                status = "The graphics driver could not upload this PNG. Try a smaller image.";
                Maro.LOGGER.warn("GUI background texture upload failed", failure);
            }
        }));
    }
    private void upload(byte[] rgba, int w, int h) {
        Texture next = new Texture(w, h, TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
        try { next.upload(rgba); } catch (RuntimeException error) { next.close(); throw error; }
        if (texture != null) texture.close();
        texture = next; pixels = rgba; width = w; height = h;
    }
    /** Resource reloads reuse already-decoded pixels; no per-frame file checking or decoding. */
    public void reload() { if (!closed && pixels != null) upload(pixels, width, height); }
    public void remove() {
        if (closed) return;
        int token = ++generation;
        busy = true; attempted = true;
        if (texture != null) texture.close();
        texture = null; pixels = null; width = height = 0;
        status = "PNG removed. Choose another image whenever you like.";
        CompletableFuture.runAsync(() -> {
            synchronized (io) {
                if (generation != token) return;
                try { Files.deleteIfExists(file); }
                catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
            }
        }).whenComplete((unused,error)->MinecraftClient.getInstance().execute(()->{
            if(token!=generation||closed)return;
            busy=false;
            if(error!=null)status="Could not remove the saved PNG. Check file permissions.";
        }));
    }
    @Override public void close() {
        closed = true; generation++; busy = false;
        if (texture != null) texture.close();
        texture = null; pixels = null;
    }
}
