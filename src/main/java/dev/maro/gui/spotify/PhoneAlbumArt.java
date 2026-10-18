package dev.maro.gui.spotify;

import dev.maro.nathan.audio.SpotifyMedia;

import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Runs on a worker: aspect-preserving album art with a clean, antialiased rounded mask. */
final class PhoneAlbumArt {
    static final int SIZE = 512;
    private PhoneAlbumArt() { }

    static byte[] prepare(SpotifyMedia.Artwork art) {
        return prepare(art,true);
    }
    static byte[] prepare(SpotifyMedia.Artwork art,boolean rounded) {
        int width = art.width(), height = art.height();
        if (width < 1 || height < 1 || (long)width * height > 16_777_216
                || (long)width * height * 4 != art.rawRgba().length) throw new IllegalArgumentException("Invalid artwork");
        var source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        byte[] rgba = art.rawRgba();
        for (int i = 0; i < pixels.length; i++) {
            int p = i * 4;
            pixels[i] = (rgba[p + 3] & 255) << 24 | (rgba[p] & 255) << 16 | (rgba[p + 1] & 255) << 8 | (rgba[p + 2] & 255);
        }
        source.setRGB(0, 0, width, height, pixels, 0, width);
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double fit = Math.max((double)SIZE / width, (double)SIZE / height);
            int w = (int)Math.ceil(width * fit), h = (int)Math.ceil(height * fit);
            g.drawImage(source, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
            if(rounded) {
            var mask = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            var mg = mask.createGraphics();
            try {
                mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                mg.setColor(java.awt.Color.WHITE);
                mg.fill(new RoundRectangle2D.Float(0, 0, SIZE, SIZE, SIZE * .1f, SIZE * .1f));
            } finally { mg.dispose(); }
            g.setComposite(AlphaComposite.DstIn);
            g.drawImage(mask, 0, 0, null);
            }
        } finally { g.dispose(); }
        pixels = image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        byte[] result = new byte[SIZE * SIZE * 4];
        for (int i = 0; i < pixels.length; i++) {
            int value = pixels[i], p = i * 4;
            result[p] = (byte)(value >> 16); result[p + 1] = (byte)(value >> 8);
            result[p + 2] = (byte)value; result[p + 3] = (byte)(value >>> 24);
        }
        return result;
    }
}
