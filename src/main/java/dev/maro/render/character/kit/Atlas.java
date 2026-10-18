// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class Atlas {
    static final int WHITE = 16;
    private static final int PAD = 3;
    private static final int MAX = 2048;
    private static final int LEVELS = 5;
    final int width;
    final int height;
    final int[][] mips;
    private final Map<Canvas, int[]> rects;

    private Atlas(int width, int height, int[][] mips, Map<Canvas, int[]> rects) {
        this.width = width;
        this.height = height;
        this.mips = mips;
        this.rects = rects;
    }

    float whiteU() {
        return 8.0f / (float)this.width;
    }

    float whiteV() {
        return 8.0f / (float)this.height;
    }

    float u(Canvas c, float u) {
        int[] r = this.rects.get(c);
        return ((float)r[0] + u * (float)r[2]) / (float)this.width;
    }

    float v(Canvas c, float v) {
        int[] r = this.rects.get(c);
        return ((float)r[1] + v * (float)r[3]) / (float)this.height;
    }

    static Atlas pack(List<Canvas> input) {
        ArrayList<Canvas> canvases = new ArrayList<Canvas>();
        for (Canvas c : input) {
            if (canvases.contains(c)) continue;
            canvases.add(c);
        }
        canvases.sort((a, b) -> Integer.compare(b.height(), a.height()));
        long area = 484L;
        int widest = 22;
        for (Canvas c : canvases) {
            area += (long)(c.width() + 6) * (long)(c.height() + 6);
            widest = Math.max(widest, c.width() + 6);
        }
        int width = Atlas.pow2(Math.max(widest, (int)Math.ceil(Math.sqrt(area))));
        while (true) {
            IdentityHashMap<Canvas, int[]> rects = new IdentityHashMap<Canvas, int[]>();
            int x = 22;
            int y = 0;
            int shelf = 22;
            for (Canvas c : canvases) {
                int w = c.width() + 6;
                int h = c.height() + 6;
                if (x + w > width) {
                    x = 0;
                    y += shelf;
                    shelf = 0;
                }
                rects.put(c, new int[]{x + 3, y + 3, c.width(), c.height()});
                x += w;
                shelf = Math.max(shelf, h);
            }
            int height = Atlas.pow2(Math.max(22, y + shelf));
            if (height <= 2048 || width >= 2048) {
                if (height > 2048) {
                    throw new IllegalStateException("decals do not fit into a 2048x2048 atlas");
                }
                return Atlas.render(width, height, canvases, rects);
            }
            width *= 2;
        }
    }

    private static Atlas render(int width, int height, List<Canvas> canvases, Map<Canvas, int[]> rects) {
        int[] px = new int[width * height];
        for (int y = 0; y < 19; ++y) {
            for (int x = 0; x < 19; ++x) {
                px[y * width + x] = -1;
            }
        }
        for (Canvas c : canvases) {
            int[] r = rects.get(c);
            int[] src = Atlas.dilate(c);
            for (int y = -3; y < c.height() + 3; ++y) {
                int sy = Math.max(0, Math.min(c.height() - 1, y));
                for (int x = -3; x < c.width() + 3; ++x) {
                    int sx = Math.max(0, Math.min(c.width() - 1, x));
                    px[(r[1] + y) * width + r[0] + x] = src[sy * c.width() + sx];
                }
            }
        }
        return new Atlas(width, height, Atlas.mips(px, width, height), rects);
    }

    static int[][] mips(int[] px, int width, int height) {
        int levels = Math.min(5, 1 + (31 - Integer.numberOfLeadingZeros(Math.min(width, height))));
        int[][] mips = new int[levels][];
        mips[0] = px;
        int w = width;
        int h = height;
        for (int l = 1; l < levels; ++l) {
            mips[l] = Atlas.down(mips[l - 1], w, h);
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        return mips;
    }

    private static int[] dilate(Canvas c) {
        int w = c.width();
        int h = c.height();
        int[] px = Arrays.copyOf(c.pixels, c.pixels.length);
        boolean[] known = new boolean[px.length];
        for (int i = 0; i < px.length; ++i) {
            known[i] = px[i] >>> 24 != 0;
        }
        for (int pass = 0; pass < 4; ++pass) {
            boolean[] next = (boolean[])known.clone();
            for (int y = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x) {
                    int i = y * w + x;
                    if (known[i]) continue;
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            int j;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h || !known[j = ny * w + nx]) continue;
                            r += px[j] >> 16 & 0xFF;
                            g += px[j] >> 8 & 0xFF;
                            b += px[j] & 0xFF;
                            ++n;
                        }
                    }
                    if (n == 0) continue;
                    px[i] = r / n << 16 | g / n << 8 | b / n;
                    next[i] = true;
                }
            }
            known = next;
        }
        return px;
    }

    private static int[] down(int[] src, int w, int h) {
        int nw = Math.max(1, w / 2);
        int nh = Math.max(1, h / 2);
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; ++y) {
            for (int x = 0; x < nw; ++x) {
                int ob;
                int og;
                int or;
                float a = 0.0f;
                float r = 0.0f;
                float g = 0.0f;
                float b = 0.0f;
                float rr = 0.0f;
                float gg = 0.0f;
                float bb = 0.0f;
                for (int k = 0; k < 4; ++k) {
                    int sx = Math.min(w - 1, x * 2 + (k & 1));
                    int sy = Math.min(h - 1, y * 2 + (k >> 1));
                    int p = src[sy * w + sx];
                    float pa = (float)(p >>> 24) / 255.0f;
                    a += pa;
                    r += (float)(p >> 16 & 0xFF) * pa;
                    g += (float)(p >> 8 & 0xFF) * pa;
                    b += (float)(p & 0xFF) * pa;
                    rr += (float)(p >> 16 & 0xFF);
                    gg += (float)(p >> 8 & 0xFF);
                    bb += (float)(p & 0xFF);
                }
                int oa = Math.round(a / 4.0f * 255.0f);
                if (a > 1.0E-4f) {
                    or = Math.round(r / a);
                    og = Math.round(g / a);
                    ob = Math.round(b / a);
                } else {
                    or = Math.round(rr / 4.0f);
                    og = Math.round(gg / 4.0f);
                    ob = Math.round(bb / 4.0f);
                }
                out[y * nw + x] = Colors.clamp(oa) << 24 | Colors.clamp(or) << 16 | Colors.clamp(og) << 8 | Colors.clamp(ob);
            }
        }
        return out;
    }

    private static int pow2(int v) {
        int p;
        for (p = 16; p < v; p <<= 1) {
        }
        return p;
    }
}
