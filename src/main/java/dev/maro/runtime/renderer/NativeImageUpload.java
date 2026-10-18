package dev.maro.runtime.renderer;

import dev.maro.mixin.NativeImageAccessor;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Validated bulk RGBA copy; callers retain ownership of both image and source. */
public final class NativeImageUpload {
    private NativeImageUpload() { }
    public static void rgba(NativeImage image, byte[] pixels) { rgba(image, ByteBuffer.wrap(pixels)); }
    public static void rgba(NativeImage image, ByteBuffer source) {
        if (image.getFormat() != NativeImage.Format.RGBA) throw new IllegalArgumentException("RGBA image required");
        int size = Math.multiplyExact(Math.multiplyExact(image.getWidth(), image.getHeight()), 4);
        if (source.remaining() < size) throw new IllegalArgumentException("Incomplete RGBA pixels");
        long pointer = ((NativeImageAccessor)(Object)image).maro$pixels();
        if (pointer == 0) throw new IllegalStateException("Image is closed");
        var bytes = source.duplicate(); bytes.limit(bytes.position() + size);
        MemoryUtil.memByteBuffer(pointer, size).put(bytes);
    }
    /** Used by decoder workers, before a frame reaches Minecraft's render thread. */
    public static byte[] rgba(int[] argb) {
        byte[] pixels = new byte[Math.multiplyExact(argb.length, 4)];
        for (int i = 0; i < argb.length; i++) {
            int value = argb[i], p = i * 4;
            pixels[p] = (byte)(value >> 16); pixels[p + 1] = (byte)(value >> 8);
            pixels[p + 2] = (byte)value; pixels[p + 3] = (byte)(value >>> 24);
        }
        return pixels;
    }
}
