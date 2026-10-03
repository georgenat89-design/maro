package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decode on a worker; upload/draw/close on the render thread. */
public final class PngCrosshairImage implements AutoCloseable {
    public record Decoded(byte[] file, byte[] pixels, int width, int height, int left, int top, int right, int bottom) { }
    private Decoded image;
    private Texture texture;
    private boolean smooth;

    public static Decoded decode(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 8 * 1024 * 1024) throw new IOException("Choose a PNG smaller than 8 MB");
        byte[] file = Files.readAllBytes(path);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Could not read this image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("PNG")) throw new IOException("Choose a PNG image");
                if (reader.getWidth(0) > 1024 || reader.getHeight(0) > 1024) throw new IOException("PNG must be 1024 × 1024 or smaller");
            } finally { reader.dispose(); }
        }
        try (NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(file))) {
            int w = nativeImage.getWidth(), h = nativeImage.getHeight();
            byte[] pixels = new byte[w * h * 4];
            int left = w, top = h, right = -1, bottom = -1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int argb = nativeImage.getColorArgb(x, y), offset = (y * w + x) * 4;
                pixels[offset] = (byte) (argb >> 16); pixels[offset + 1] = (byte) (argb >> 8);
                pixels[offset + 2] = (byte) argb; pixels[offset + 3] = (byte) (argb >>> 24);
                if ((argb >>> 24) > 0) {
                    left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
            }
            if (right < left) throw new IOException("This PNG is completely transparent");
            return new Decoded(file, pixels, w, h, left, top, right + 1, bottom + 1);
        }
    }

    public boolean loaded() { return image != null; }
    public int width() { return image == null ? 0 : image.width(); }
    public int height() { return image == null ? 0 : image.height(); }
    public void install(Decoded decoded, boolean smooth) {
        Decoded previous = image; boolean previousSmooth = this.smooth;
        image = decoded; this.smooth = smooth;
        try { upload(); } catch (RuntimeException e) { image = previous; this.smooth = previousSmooth; throw e; }
    }
    public void setSmooth(boolean value) { if (smooth != value) { smooth = value; if (image != null) upload(); } }
    private void upload() {
        FilterMode filter = smooth ? FilterMode.LINEAR : FilterMode.NEAREST;
        Texture next = new Texture(image.width(), image.height(), TextureFormat.RGBA8, filter, filter);
        try { next.upload(image.pixels()); } catch (RuntimeException e) { next.close(); throw e; }
        if (texture != null) texture.close();
        texture = next;
    }

    public void draw(DrawContext ctx, float cx, float cy, float size, boolean trim, int color) {
        if (texture == null) return;
        float left = trim ? image.left() : 0, top = trim ? image.top() : 0;
        float right = trim ? image.right() : image.width(), bottom = trim ? image.bottom() : image.height();
        float scale = size / Math.max(right - left, bottom - top);
        float w = (right - left) * scale, h = (bottom - top) * scale;
        float x1 = cx - w / 2, x2 = cx + w / 2, y1 = cy - h / 2, y2 = cy + h / 2;
        float u1 = left / image.width(), u2 = right / image.width(), v1 = top / image.height(), v2 = bottom / image.height();
        float[] vertices = {x1,y1,u1,v1, x1,y2,u1,v2, x2,y2,u2,v2, x2,y1,u2,v1};
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect((int) Math.floor(x1), (int) Math.floor(y1),
            Math.max(1, (int) Math.ceil(x2) - (int) Math.floor(x1)), Math.max(1, (int) Math.ceil(y2) - (int) Math.floor(y1))).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, vertices,
            new int[]{color, color, color, color}, RenderPipelines.GUI_TEXTURED,
            TextureSetup.of(texture.getGlTextureView(), texture.getSampler()), bounds));
    }
    @Override public void close() { if (texture != null) { texture.close(); texture = null; } image = null; }
}
