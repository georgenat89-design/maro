// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class TextureAtlas {
    private static final int SIZE = 2048;
    private static final int PAD = 4;
    private static final int MAX_SIDE = 8192;
    final int width;
    final int height;
    private final int[] pixels;
    private final Map<Integer, float[]> regions = new HashMap<Integer, float[]>();
    private final Map<Integer, int[]> tiles = new HashMap<Integer, int[]>();

    private TextureAtlas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    static TextureAtlas pack(GltfFile file, Map<Integer, float[]> used, ImageDecoder decoder) throws IOException {
        LinkedHashMap<Integer, ImageDecoder.Picture> crops = new LinkedHashMap<Integer, ImageDecoder.Picture>();
        HashMap<Integer, float[]> cropUv = new HashMap<Integer, float[]>();
        HashMap<Integer, int[]> tiles = new HashMap<Integer, int[]>();
        for (Map.Entry<Integer, float[]> e : used.entrySet()) {
            float[] b = e.getValue();
            int tu = (int)Math.floor((double)(b[0] + 0.001f));
            int tv = (int)Math.floor((double)(b[1] + 0.001f));
            int nu = Math.min(8, Math.max(1, (int)Math.ceil((double)(b[2] - 0.001f)) - tu));
            int nv = Math.min(8, Math.max(1, (int)Math.ceil((double)(b[3] - 0.001f)) - tv));
            ImageDecoder.Picture src = TextureAtlas.tile(TextureAtlas.baseColor(file, e.getKey(), decoder), nu, nv);
            tiles.put(e.getKey(), new int[]{tu, tv, nu, nv});
            float var14_18 = (b[0] - (float)tu) / (float)nu;
            float var15_19 = (b[1] - (float)tv) / (float)nv;
            float var16_20 = (b[2] - (float)tu) / (float)nu;
            float var17_21 = (b[3] - (float)tv) / (float)nv;
            int x0 = TextureAtlas.clamp((int)Math.floor((double)(var14_18 * (float)src.width())) - 2, 0, src.width() - 1);
            int y0 = TextureAtlas.clamp((int)Math.floor((double)(var15_19 * (float)src.height())) - 2, 0, src.height() - 1);
            int x1 = TextureAtlas.clamp((int)Math.ceil((double)(var16_20 * (float)src.width())) + 2, x0 + 1, src.width());
            int y1 = TextureAtlas.clamp((int)Math.ceil((double)(var17_21 * (float)src.height())) + 2, y0 + 1, src.height());
            crops.put(e.getKey(), TextureAtlas.crop(src, x0, y0, x1, y1));
            cropUv.put(e.getKey(), new float[]{(float)x0 / (float)src.width(), (float)y0 / (float)src.height(), (float)x1 / (float)src.width(), (float)y1 / (float)src.height()});
        }
        double area = 0.0;
        for (ImageDecoder.Picture c : crops.values()) {
            area += (double)(c.width() + 8) * (double)(c.height() + 8);
        }
        double k = Math.min(1.0, Math.sqrt(3858759.68 / Math.max(1.0, area)));
        while (true) {
            TextureAtlas a;
            if ((a = TextureAtlas.tryPack(crops, cropUv, k)) != null) {
                a.tiles.putAll(tiles);
                return a;
            }
            k *= 0.93;
        }
    }

    private static ImageDecoder.Picture baseColor(GltfFile file, int material, ImageDecoder decoder) throws IOException {
        byte[] encoded = file.baseColorImage(material);
        ImageDecoder.Picture src = encoded == null ? new ImageDecoder.Picture(4, 4, TextureAtlas.filled(16, -1)) : decoder.decode(encoded);
        float[] f = file.baseColorFactor(material);
        if (f[0] == 1.0f && f[1] == 1.0f && f[2] == 1.0f && f[3] == 1.0f) {
            return src;
        }
        for (int c = 0; c < 3; ++c) {
            f[c] = (float)Math.pow(f[c], 0.45454545454545453);
        }
        int[] out = new int[src.argb().length];
        for (int i = 0; i < out.length; ++i) {
            int p = src.argb()[i];
            int a = Math.round((float)(p >>> 24) * f[3]);
            int r = Math.round((float)(p >> 16 & 0xFF) * f[0]);
            int g = Math.round((float)(p >> 8 & 0xFF) * f[1]);
            int b = Math.round((float)(p & 0xFF) * f[2]);
            out[i] = a << 24 | r << 16 | g << 8 | b;
        }
        return new ImageDecoder.Picture(src.width(), src.height(), out);
    }

    private static int[] filled(int n, int argb) {
        int[] out = new int[n];
        Arrays.fill(out, argb);
        return out;
    }

    private static ImageDecoder.Picture tile(ImageDecoder.Picture src, int nu, int nv) {
        if (nu == 1 && nv == 1) {
            return src;
        }
        if (src.width() * nu > 8192 || src.height() * nv > 8192) {
            src = TextureAtlas.resize(src, Math.max(1, Math.min(src.width(), 8192 / nu)), Math.max(1, Math.min(src.height(), 8192 / nv)));
        }
        int w = src.width() * nu;
        int h = src.height() * nv;
        int[] out = new int[w * h];
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                out[y * w + x] = src.argb()[y % src.height() * src.width() + x % src.width()];
            }
        }
        return new ImageDecoder.Picture(w, h, out);
    }

    private static ImageDecoder.Picture crop(ImageDecoder.Picture src, int x0, int y0, int x1, int y1) {
        int w = x1 - x0;
        int h = y1 - y0;
        int[] out = new int[w * h];
        for (int y = 0; y < h; ++y) {
            System.arraycopy(src.argb(), (y0 + y) * src.width() + x0, out, y * w, w);
        }
        return new ImageDecoder.Picture(w, h, out);
    }

    private static TextureAtlas tryPack(Map<Integer, ImageDecoder.Picture> crops, Map<Integer, float[]> cropUv, double k) {
        ArrayList<Integer> order = new ArrayList<Integer>(crops.keySet());
        order.sort((a, b) -> Integer.compare(((ImageDecoder.Picture)crops.get(b)).height(), ((ImageDecoder.Picture)crops.get(a)).height()));
        int x = 0;
        int y = 0;
        int shelf = 0;
        int right = 0;
        HashMap<Integer, int[]> place = new HashMap<Integer, int[]>();
        Iterator iterator = order.iterator();
        while (iterator.hasNext()) {
            int m = (Integer)iterator.next();
            ImageDecoder.Picture c = crops.get(m);
            int w = Math.max(1, (int)Math.round((double)c.width() * k));
            int h = Math.max(1, (int)Math.round((double)c.height() * k));
            if (x + w + 8 > 2048) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (w + 8 > 2048 || y + h + 8 > 2048) {
                return null;
            }
            place.put(m, new int[]{x + 4, y + 4, w, h});
            shelf = Math.max(shelf, h + 8);
            right = Math.max(right, x += w + 8);
        }
        TextureAtlas a2 = new TextureAtlas(TextureAtlas.pow2(right), TextureAtlas.pow2(y + shelf));
        Iterator iterator2 = order.iterator();
        while (iterator2.hasNext()) {
            int m = (Integer)iterator2.next();
            int[] r = (int[])place.get(m);
            ImageDecoder.Picture scaled = TextureAtlas.resize(crops.get(m), r[2], r[3]);
            for (int py = -4; py < r[3] + 4; ++py) {
                for (int px = -4; px < r[2] + 4; ++px) {
                    int sx = TextureAtlas.clamp(px, 0, r[2] - 1);
                    int sy = TextureAtlas.clamp(py, 0, r[3] - 1);
                    a2.pixels[(r[1] + py) * a2.width + r[0] + px] = scaled.argb()[sy * r[2] + sx];
                }
            }
            float[] c = cropUv.get(m);
            a2.regions.put(m, new float[]{c[0], c[1], c[2], c[3], r[0], r[1], r[2], r[3]});
        }
        return a2;
    }

    float[] map(int material, float u, float v) {
        float[] r = this.regions.get(material);
        int[] t = this.tiles.get(material);
        float tu = (u - (float)t[0]) / (float)t[2];
        float tv = (v - (float)t[1]) / (float)t[3];
        float cu = (tu - r[0]) / (r[2] - r[0]);
        float cv = (tv - r[1]) / (r[3] - r[1]);
        return new float[]{(r[4] + cu * r[6]) / (float)this.width, (r[5] + cv * r[7]) / (float)this.height};
    }

    byte[] packed() throws IOException {
        byte[] raw = new byte[this.pixels.length * 4];
        for (int i = 0; i < this.pixels.length; ++i) {
            int left = i % this.width == 0 ? 0 : this.pixels[i - 1];
            for (int c = 0; c < 4; ++c) {
                raw[i * 4 + c] = (byte)((this.pixels[i] >>> 24 - c * 8) - (left >>> 24 - c * 8));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream((OutputStream)out, new Deflater(9));){
            z.write(raw);
        }
        return out.toByteArray();
    }

    private static ImageDecoder.Picture resize(ImageDecoder.Picture src, int w, int h) {
        ImageDecoder.Picture cur = src;
        while (cur.width() / 2 >= w && cur.height() / 2 >= h) {
            cur = TextureAtlas.half(cur);
        }
        return cur.width() == w && cur.height() == h ? cur : TextureAtlas.bilinear(cur, w, h);
    }

    private static ImageDecoder.Picture half(ImageDecoder.Picture src) {
        int w = src.width() / 2;
        int h = src.height() / 2;
        int[] out = new int[w * h];
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int[] s = src.argb();
                int i = y * 2 * src.width() + x * 2;
                out[y * w + x] = TextureAtlas.average(s[i], s[i + 1], s[i + src.width()], s[i + src.width() + 1]);
            }
        }
        return new ImageDecoder.Picture(w, h, out);
    }

    private static int average(int a, int b, int c, int d) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int sum = (a >>> shift & 0xFF) + (b >>> shift & 0xFF) + (c >>> shift & 0xFF) + (d >>> shift & 0xFF);
            out |= (sum + 2) / 4 << shift;
        }
        return out;
    }

    private static ImageDecoder.Picture bilinear(ImageDecoder.Picture src, int w, int h) {
        int[] out = new int[w * h];
        int sw = src.width();
        int sh = src.height();
        for (int y = 0; y < h; ++y) {
            float fy = Math.max(0.0f, ((float)y + 0.5f) * (float)sh / (float)h - 0.5f);
            int y0 = Math.min(sh - 1, (int)fy);
            int y1 = Math.min(sh - 1, y0 + 1);
            float ty = fy - (float)y0;
            for (int x = 0; x < w; ++x) {
                float fx = Math.max(0.0f, ((float)x + 0.5f) * (float)sw / (float)w - 0.5f);
                int x0 = Math.min(sw - 1, (int)fx);
                int x1 = Math.min(sw - 1, x0 + 1);
                float tx = fx - (float)x0;
                int[] s = src.argb();
                int p = 0;
                for (int shift = 0; shift < 32; shift += 8) {
                    float top = (float)(s[y0 * sw + x0] >>> shift & 0xFF) * (1.0f - tx) + (float)(s[y0 * sw + x1] >>> shift & 0xFF) * tx;
                    float bottom = (float)(s[y1 * sw + x0] >>> shift & 0xFF) * (1.0f - tx) + (float)(s[y1 * sw + x1] >>> shift & 0xFF) * tx;
                    p |= Math.round(top * (1.0f - ty) + bottom * ty) << shift;
                }
                out[y * w + x] = p;
            }
        }
        return new ImageDecoder.Picture(w, h, out);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int pow2(int v) {
        int p;
        for (p = 16; p < v; p <<= 1) {
        }
        return p;
    }
}
