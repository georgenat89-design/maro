package dev.maro.nathan.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.textures.FilterMode;

import dev.maro.runtime.renderer.MeshBuilder;
import dev.maro.runtime.renderer.MeshRenderer;
import dev.maro.runtime.renderer.MeteorRenderPipelines;
import dev.maro.runtime.renderer.Texture;
import dev.maro.runtime.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Text that is sharp at whatever size it is asked for.
 *
 * <p>Both of the ready-made ways of drawing text blur small text, and for the
 * same reason. The game's font is a bitmap, so any size but a whole multiple of
 * its own is that bitmap stretched. Meteor's is a real font, but it is
 * rasterised at a handful of fixed heights - 27 pixels, 40, 54 and up - and
 * every other size is the nearest of those stretched to fit, so a 14 pixel line
 * is a 27 pixel one at about half size. Either way the glyphs that reach the
 * screen were made for a different size and resampled on the way.
 *
 * <p>This rasterises the font at the exact height it will be drawn at, in real
 * pixels, and draws it at a scale of one with every glyph started on a whole
 * pixel, so a texel of the glyph is a pixel of the screen and nothing is
 * resampled at all. A size is rasterised the first time it is wanted - a couple
 * of hundred glyphs into a small sheet, a millisecond or two - and the last few
 * are kept.
 *
 * <p>Weights are real cuts of the font, never a lighter one struck twice.
 *
 * <p>Text can be turned, because it is a mesh and a mesh can be given a matrix.
 * Turned text is resampled - nothing on a pixel grid can be turned without - but
 * from glyphs of the right size, smoothly.
 *
 * <p>It draws through Meteor's mesh renderer, which opens and closes a render
 * pass of its own with its pipeline's state inside it, so there is no graphics
 * state left behind to put back.
 */
public final class CrispFont {
    /** Wide and round, for small figures and body text. SIL Open Font License. */
    public static final CrispFont POPPINS_MEDIUM = new CrispFont("Poppins-Medium.ttf");

    /** The same, a weight up, for titles. */
    public static final CrispFont POPPINS_SEMIBOLD = new CrispFont("Poppins-SemiBold.ttf");

    private static final int FIRST = 32;
    private static final int COUNT = 224;

    /** Sizes kept per font. A panel has two or three; the rest is room for a slider being dragged. */
    private static final int KEPT = 8;

    private static final Color SHADOW = new Color(0, 0, 0, 205);

    // One mesh for all of it. Glyphs from different sheets cannot share a draw,
    // so it is flushed whenever the sheet changes.
    private static MeshBuilder mesh;
    private static Sized drawing;
    private static Matrix4f transform;
    private static boolean open;

    private final String file;

    private ByteBuffer data;
    private STBTTFontinfo info;
    private boolean failed;

    // In font units.
    private int ascent;
    private int descent;
    private int capHeight;
    private final int[] advances = new int[COUNT];

    private final Map<Integer, Sized> sizes = new LinkedHashMap<>(16, 0.75f, true);

    private CrispFont(String file) {
        this.file = file;
    }

    /** Loads the font the first time it is asked for. False, from then on, if it could not be. */
    public boolean ready() {
        if (failed) return false;
        if (info != null) return true;

        try (InputStream stream = CrispFont.class.getResourceAsStream("/assets/nameeprotect/fonts/" + file)) {
            if (stream == null) throw new IOException("missing from the jar: " + file);

            byte[] bytes = stream.readAllBytes();

            // Kept for as long as the font is: the rasteriser reads from it
            // every time a size is made.
            data = BufferUtils.createByteBuffer(bytes.length);
            data.put(bytes).flip();

            STBTTFontinfo loaded = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(loaded, data)) throw new IOException("not a font the rasteriser can read: " + file);

            int[] up = new int[1];
            int[] down = new int[1];
            int[] gap = new int[1];
            STBTruetype.stbtt_GetFontVMetrics(loaded, up, down, gap);

            ascent = up[0];
            descent = down[0];

            // The top of a flat capital is the cap height, whatever the font's
            // own tables say or leave out.
            int[] x0 = new int[1];
            int[] y0 = new int[1];
            int[] x1 = new int[1];
            int[] y1 = new int[1];
            capHeight = STBTruetype.stbtt_GetCodepointBox(loaded, 'H', x0, y0, x1, y1) ? y1[0] : (int) (ascent * 0.7);

            int[] advance = new int[1];
            int[] bearing = new int[1];

            for (int i = 0; i < COUNT; i++) {
                STBTruetype.stbtt_GetCodepointHMetrics(loaded, FIRST + i, advance, bearing);
                advances[i] = advance[0];
            }

            info = loaded;
            return true;
        } catch (IOException | RuntimeException e) {
            failed = true;
            NameeProtectAddon.LOG.error("could not load the bundled font {}", file, e);

            return false;
        }
    }

    /** How tall a capital is, as a fraction of the line. */
    public double capsRatio() {
        return (double) capHeight / (ascent - descent);
    }

    /** How far down the line the middle of a capital sits. */
    public double capsMiddle() {
        return (ascent - capHeight / 2.0) / (ascent - descent);
    }

    /**
     * Width of {@code text} on a line one pixel tall, straight from the font's
     * own measurements. For laying out before a size has been settled on;
     * {@link Sized#width} is what to place text by once it has.
     */
    public double unitWidth(String text) {
        long total = 0;

        for (int i = 0; i < text.length(); i++) total += advances[index(text.charAt(i))];

        return (double) total / (ascent - descent);
    }

    /** The size whose capitals come nearest to {@code pixels} tall. */
    public Sized forCaps(double pixels) {
        return at((int) Math.round(pixels / capsRatio()));
    }

    /** The font with a line {@code pixels} tall. Call {@link #ready()} first. */
    public Sized at(int pixels) {
        pixels = Math.max(6, Math.min(160, pixels));

        Sized sized = sizes.get(pixels);

        if (sized == null) {
            sized = new Sized(pixels);
            sizes.put(pixels, sized);

            // Oldest out, but never the one a draw is part way through.
            Iterator<Sized> oldest = sizes.values().iterator();

            while (sizes.size() > KEPT && oldest.hasNext()) {
                Sized candidate = oldest.next();

                if (candidate != drawing && candidate != sized) {
                    candidate.sheet.close();
                    oldest.remove();
                }
            }
        }

        return sized;
    }

    private static int index(char c) {
        return c >= FIRST && c < FIRST + COUNT ? c - FIRST : '?' - FIRST;
    }

    // ------------------------------------------------------------- drawing

    /**
     * Starts a run of text. Everything drawn until {@link #end()} is turned by
     * {@code matrix}, or left alone if it is null. Positions are real pixels.
     */
    public static void begin(Matrix4f matrix) {
        if (open) end();

        if (mesh == null) mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);

        transform = matrix;
        open = true;
    }

    public static void end() {
        flush();

        open = false;
        transform = null;
    }

    private static void flush() {
        if (drawing == null) return;

        if (mesh.isBuilding()) {
            mesh.end();

            MeshRenderer renderer = MeshRenderer.begin()
                .attachments(MinecraftClient.getInstance().getFramebuffer())
                .pipeline(MeteorRenderPipelines.UI_TEXT)
                .mesh(mesh);

            if (transform != null) renderer.transform(transform);

            renderer.sampler("u_Texture", drawing.sheet.getGlTextureView(), drawing.sheet.getSampler()).end();
        }

        drawing = null;
    }

    /** One font at one height: a sheet of glyphs and where each one is on it. */
    public final class Sized {
        private final int pixels;
        private final int baseline;
        private final Texture sheet;

        private final float[] x0 = new float[COUNT];
        private final float[] y0 = new float[COUNT];
        private final float[] x1 = new float[COUNT];
        private final float[] y1 = new float[COUNT];
        private final float[] u0 = new float[COUNT];
        private final float[] v0 = new float[COUNT];
        private final float[] u1 = new float[COUNT];
        private final float[] v1 = new float[COUNT];
        private final float[] advance = new float[COUNT];

        private Sized(int pixels) {
            this.pixels = pixels;
            this.baseline = Math.round(ascent * STBTruetype.stbtt_ScaleForPixelHeight(info, pixels));

            ByteBuffer bitmap = null;
            int side = 128;

            try (STBTTPackedchar.Buffer packed = STBTTPackedchar.malloc(COUNT); STBTTPackContext context = STBTTPackContext.malloc()) {
                // The smallest sheet the glyphs fit on. No oversampling: that is
                // for text that lands between pixels, and this never does.
                boolean fits = false;

                while (!fits && side < 4096) {
                    side *= 2;
                    bitmap = BufferUtils.createByteBuffer(side * side);

                    STBTruetype.stbtt_PackBegin(context, bitmap, side, side, 0, 1);
                    STBTruetype.stbtt_PackSetOversampling(context, 1, 1);
                    fits = STBTruetype.stbtt_PackFontRange(context, data, 0, pixels, FIRST, packed);
                    STBTruetype.stbtt_PackEnd(context);
                }

                for (int i = 0; i < COUNT; i++) {
                    STBTTPackedchar glyph = packed.get(i);

                    x0[i] = glyph.xoff();
                    y0[i] = glyph.yoff();
                    x1[i] = glyph.xoff2();
                    y1[i] = glyph.yoff2();
                    u0[i] = (float) glyph.x0() / side;
                    v0[i] = (float) glyph.y0() / side;
                    u1[i] = (float) glyph.x1() / side;
                    v1[i] = (float) glyph.y1() / side;
                    advance[i] = glyph.xadvance();
                }
            }

            // Linear, which at a scale of one on whole pixels reads each texel
            // exactly, and is what turned text needs.
            sheet = new Texture(side, side, TextureFormat.RED8, FilterMode.LINEAR, FilterMode.LINEAR);
            sheet.upload(bitmap);
        }

        /** Height of a line. */
        public int height() {
            return pixels;
        }

        /** Height of a capital, and how far below the top of the line it starts. */
        public double caps() {
            return pixels * capsRatio();
        }

        public double capsTop() {
            return baseline - caps();
        }

        /** Width with {@code tracking} extra pixels between each pair of characters. */
        public double width(String text, double tracking) {
            double total = 0;

            for (int i = 0; i < text.length(); i++) total += advance[index(text.charAt(i))];

            return total + tracking * Math.max(0, text.length() - 1);
        }

        /** Draws with the capitals' top at {@code capsTop} and the text centred on {@code centre}. */
        public void centred(String text, double centre, double capsTop, Color color, double tracking, boolean shadow) {
            draw(text, centre - width(text, tracking) / 2, capsTop - capsTop(), color, tracking, shadow);
        }

        /**
         * Draws with the top of the line at {@code top}. Between a
         * {@link CrispFont#begin} and an {@link CrispFont#end()}.
         */
        public void draw(String text, double left, double top, Color color, double tracking, boolean shadow) {
            if (!open) throw new IllegalStateException("CrispFont.draw outside begin/end");

            if (drawing != this) {
                flush();
                drawing = this;
                mesh.begin();
            }

            if (shadow) {
                // A shadow's strength follows the text's, or fading text would
                // leave its shadow behind.
                int offset = Math.max(1, pixels / 14);
                run(text, left + offset, top + offset, new Color(SHADOW.r, SHADOW.g, SHADOW.b, SHADOW.a * color.a / 255), tracking);
            }

            run(text, left, top, color, tracking);
        }

        /** Clips glyph geometry and UVs horizontally without changing global render state. */
        public void drawClipped(String text, double left, double top, Color color, double tracking, double clipLeft, double clipRight) {
            if (!open) throw new IllegalStateException("CrispFont.drawClipped outside begin/end");
            if (clipRight <= clipLeft) return;
            if (drawing != this) { flush(); drawing = this; mesh.begin(); }
            run(text, left, top, color, tracking, clipLeft, clipRight);
        }

        private void run(String text, double left, double top, Color color, double tracking) {
            run(text, left, top, color, tracking, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        private void run(String text, double left, double top, Color color, double tracking, double clipLeft, double clipRight) {
            double pen = left;
            double line = Math.round(top) + baseline;

            mesh.ensureCapacity(text.length() * 4, text.length() * 6);

            for (int i = 0; i < text.length(); i++) {
                int glyph = index(text.charAt(i));

                // Every glyph on a whole pixel, not just the first: advances are
                // fractions, and a glyph half a pixel over is a smeared glyph.
                double x = Math.round(pen);

                if (x1[glyph] > x0[glyph]) {
                    double originalLeft = x + x0[glyph], originalRight = x + x1[glyph];
                    double visibleLeft = Math.max(originalLeft, clipLeft), visibleRight = Math.min(originalRight, clipRight);
                    if (visibleRight <= visibleLeft) { pen += advance[glyph] + tracking; continue; }
                    double textureLeft = u0[glyph] + (u1[glyph] - u0[glyph]) * (visibleLeft - originalLeft) / (originalRight - originalLeft);
                    double textureRight = u0[glyph] + (u1[glyph] - u0[glyph]) * (visibleRight - originalLeft) / (originalRight - originalLeft);
                    mesh.quad(
                        mesh.vec2(visibleLeft, line + y0[glyph]).vec2(textureLeft, v0[glyph]).color(color).next(),
                        mesh.vec2(visibleLeft, line + y1[glyph]).vec2(textureLeft, v1[glyph]).color(color).next(),
                        mesh.vec2(visibleRight, line + y1[glyph]).vec2(textureRight, v1[glyph]).color(color).next(),
                        mesh.vec2(visibleRight, line + y0[glyph]).vec2(textureRight, v0[glyph]).color(color).next());
                }

                pen += advance[glyph] + tracking;
            }
        }
    }
}
