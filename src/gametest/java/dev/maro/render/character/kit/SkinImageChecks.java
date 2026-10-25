package dev.maro.render.character.kit;

/** Texture reduction must preserve color energy and avoid a black halo around transparent edges. */
public final class SkinImageChecks {
    private SkinImageChecks() { }
    public static void run() {
        int[] pixels = new int[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pixels[y * 8 + x] = ((x + y) & 1) == 0 ? 0xFFFFFFFF : 0xFF000000;
        var levels = SkinImages.mips(pixels, 8, 8);
        try {
            int gray = levels[1].getColorArgb(0, 0) & 255;
            if (gray < 186 || gray > 190) throw new AssertionError("Mip color darkened: " + gray);
        } finally { for (var image : levels) image.close(); }
        for (int i = 0; i < pixels.length; i++) pixels[i] = (i & 1) == 0 ? 0xFFFF0000 : 0x00000000;
        levels = SkinImages.mips(pixels, 8, 8);
        try {
            int color = levels[1].getColorArgb(0, 0);
            if ((color >> 16 & 255) != 255 || (color & 65535) != 0 || (color >>> 24) != 128)
                throw new AssertionError("Transparent edges lost their original color: " + Integer.toHexString(color));
        } finally { for (var image : levels) image.close(); }
        System.out.println("[character-images] PASS: linear-light mip color and alpha-weighted edges");
    }
}
