package dev.maro.gui.spotify;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifyPlayback;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/** A foreground phone with the player's skin, live album art and local media controls. */
public final class SpotifyPhoneScreen extends Screen {
    public static final float PHONE_WIDTH = 208, PHONE_HEIGHT = 408;
    private static final int WHITE = 0xFFF5F7F6, MUTED = 0xFFA3ADA7, GREEN = 0xFF1ED760;
    private final SpotifyPhone owner;
    private final SpotifyPlayback player;
    private final long openedAt = System.nanoTime();
    private long lastControlAt;
    private Texture cover;
    private SpotifyMedia.Artwork shownArtwork;
    private boolean dragging, removed;
    private long scrubPosition;
    private String scrubTrack = "";

    public record Layout(float x, float y, float scale) {
        public float localX(double mouseX) { return (float) (mouseX - x) / scale; }
        public float localY(double mouseY) { return (float) (mouseY - y) / scale; }
        public float screenX(float local) { return x + local * scale; }
        public float screenY(float local) { return y + local * scale; }
    }

    public SpotifyPhoneScreen(SpotifyPhone owner, SpotifyPlayback player) {
        super(Text.literal("Spotify Phone"));
        this.owner = owner;
        this.player = player;
    }

    public Layout layout() {
        float scale = Math.min(height * .77f / PHONE_HEIGHT, width * .43f / PHONE_WIDTH) * owner.size.getFloat();
        scale = Math.max(.1f, Math.min(scale, (height - 16f) / PHONE_HEIGHT));
        float x = owner.hand.is("Left") ? 25 * scale : width - (PHONE_WIDTH + 25) * scale;
        double age = (System.nanoTime() - openedAt) / 1e9;
        float slide = (float) Math.pow(Math.max(0, 1 - age / .22), 3) * (PHONE_HEIGHT + 30) * scale;
        return new Layout(x, height - (PHONE_HEIGHT + 10) * scale + slide, scale);
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var state = player.state();
        var layout = layout();
        float mx = layout.localX(mouseX), my = layout.localY(mouseY);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(layout.x, layout.y);
        matrices.scale(layout.scale, layout.scale);
        Fonts.beginRaw();
        try {
            if (owner.showHand.get()) hand(ctx, false);
            int tint = 0xFF173E2A;
            updateArtwork(state);
            if (shownArtwork != null) tint = 0xFF000000 | shownArtwork.tintRgb();
            Render2D.shadow(ctx, 0, 0, PHONE_WIDTH, PHONE_HEIGHT, 27, 14, 0xA0000000);
            Render2D.roundGradientV(ctx, -1, -1, PHONE_WIDTH + 2, PHONE_HEIGHT + 2, 27, 0xFF89938E, 0xFF28332D);
            Render2D.roundRect(ctx, 1, 1, PHONE_WIDTH - 2, PHONE_HEIGHT - 2, 26, 0xFF080B09);
            Render2D.roundGradientV(ctx, 6, 6, PHONE_WIDTH - 12, PHONE_HEIGHT - 12, 22,
                ColorUtil.lerp(0xFF12221A, tint, .32f), 0xFF101713);
            // Camera island and physical side buttons.
            Render2D.roundRect(ctx, 74, 13, 60, 14, 7, 0xFF050806);
            Render2D.circle(ctx, 124, 20, 2.5f, 0xFF1C2929);
            Render2D.circle(ctx, 124, 20, 1, 0xFF48676B);
            Render2D.roundRect(ctx, -3, 66, 3, 24, 1, 0xFF59645E);
            Render2D.roundRect(ctx, -3, 101, 3, 38, 1, 0xFF59645E);
            Render2D.roundRect(ctx, PHONE_WIDTH, 91, 3, 49, 1, 0xFF3B4841);
            text(ctx, "MARO", 22, 18, WHITE, true, .7f);
            Render2D.roundOutline(ctx, 168, 17, 17, 8, 2, 1, MUTED);
            Render2D.roundRect(ctx, 170, 19, 12, 4, 1, GREEN);
            Render2D.rect(ctx, 185, 19, 2, 4, MUTED);
            spotifyLogo(ctx, 30, 56);
            text(ctx, "Spotify", 45, 51, WHITE, true, 1.2f);
            boolean closeHover = hit(mx, my, 170, 44, 24, 24);
            Render2D.roundRect(ctx, 170, 44, 24, 24, 12, closeHover ? 0xFF34483D : 0xFF1E3025);
            Render2D.line(ctx, 178, 52, 186, 60, 1.5f, MUTED);
            Render2D.line(ctx, 186, 52, 178, 60, 1.5f, MUTED);
            Render2D.shadow(ctx, 24, 86, 160, 160, 8, 8, 0x65000000);
            Render2D.roundRect(ctx, 23, 85, 162, 162, 9, 0xFF23372B);
            if (cover != null) drawCover(ctx);
            else {
                Render2D.roundGradientV(ctx, 24, 86, 160, 160, 8,
                    ColorUtil.lerp(0xFF243D2F, tint, .4f), 0xFF14221A);
                spotifyLogo(ctx, 104, 160);
                centered(ctx, state.available() ? "NOW PLAYING" : "YOUR MUSIC, IN GAME", 104, 195, MUTED, false, .85f);
            }
            text(ctx, Fonts.trim(state.available() ? state.title() : "Ready when you are", 160, true, 1.35f),
                24, 263, WHITE, true, 1.35f);
            text(ctx, Fonts.trim(state.available() ? state.artist() : "Open Spotify and start a song", 160, false, .95f),
                24, 281, MUTED, false, .95f);
            long position = dragging ? scrubPosition : state.currentPositionMs();
            double fraction = state.durationMs() <= 0 ? 0 : Math.clamp((double) position / state.durationMs(), 0, 1);
            Render2D.roundRect(ctx, 24, 306, 160, 3, 1.5f, 0xFF3B4940);
            if (fraction > 0) Render2D.roundRect(ctx, 24, 306, (float) fraction * 160, 3, 1.5f, GREEN);
            if (state.available() && state.canSeek() && (dragging || hit(mx, my, 20, 298, 168, 19)))
                Render2D.circle(ctx, 24 + (float) fraction * 160, 307.5f, 3.5f, WHITE);
            text(ctx, time(position), 24, 316, MUTED, false, .75f);
            String duration = time(state.durationMs());
            text(ctx, duration, 184 - Fonts.width(duration, false, .75f), 316, MUTED, false, .75f);
            if (state.available()) {
                skip(ctx, 53, mx, my, false);
                skip(ctx, 155, mx, my, true);
                boolean hover = circleHit(mx, my, 104, 350, 22);
                Render2D.circle(ctx, 104, 350, hover ? 22 : 21, hover ? 0xFF59E58B : GREEN);
                if (state.playing()) {
                    Render2D.roundRect(ctx, 98, 343, 4, 14, 1, 0xFF07140C);
                    Render2D.roundRect(ctx, 106, 343, 4, 14, 1, 0xFF07140C);
                } else triangle(ctx, 100, 342, 100, 358, 112, 350, 0xFF07140C);
                String hint = state.error().isBlank() ? "Space: play / pause   Arrows: skip" : state.error();
                centered(ctx, Fonts.trim(hint, 170, false, .7f), 104, 380, MUTED, false, .7f);
            } else {
                boolean hover = hit(mx, my, 24, 337, 160, 27);
                Render2D.roundRect(ctx, 24, 337, 160, 27, 13.5f, hover ? 0xFF59E58B : GREEN);
                centered(ctx, "Open Spotify", 104, 346, 0xFF07140C, true, 1);
                centered(ctx, Fonts.trim(state.error().isBlank() ? "Desktop or web player" : state.error(), 166, false, .7f),
                    104, 380, MUTED, false, .7f);
            }
            Render2D.roundRect(ctx, 77, 395, 54, 3, 1.5f, 0xFFAFB9B2);
            if (owner.showHand.get()) hand(ctx, true);
        } finally { Fonts.endRaw(); matrices.popMatrix(); }
    }

    private void updateArtwork(SpotifyMedia.State state) {
        var art = player.artwork();
        if (art != null && !art.key().equals(SpotifyMedia.artworkKey(state))) art = null;
        if (art == shownArtwork) return;
        if (cover != null) { cover.close(); cover = null; }
        shownArtwork = art;
        if (art != null) {
            cover = new Texture(art.width(), art.height(), TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
            cover.upload(art.rgba());
        }
    }

    private void drawCover(DrawContext ctx) {
        // Rounded image corners, using the same immutable GUI batches as Maro's other textures.
        int segments = 10;
        float[] vertices = new float[4 * 4 * 4 * segments];
        int[] colors = new int[vertices.length / 4];
        int index = 0;
        for (int corner = 0; corner < 4; corner++) for (int step = 0; step < segments; step++) {
            double a = Math.PI + corner * Math.PI / 2 + step * Math.PI / 2 / segments;
            double b = a + Math.PI / 2 / segments;
            float cx = corner == 0 || corner == 3 ? 32 : 176;
            float cy = corner < 2 ? 94 : 238;
            float[] points = {104, 166, cx + (float) Math.cos(a) * 8, cy + (float) Math.sin(a) * 8,
                cx + (float) Math.cos(b) * 8, cy + (float) Math.sin(b) * 8, 104, 166};
            // Include the straight edge between this corner and the next in the final segment.
            if (step == segments - 1) {
                int next = (corner + 1) % 4;
                points[6] = (next == 0 || next == 3 ? 32 : 176) + (float) Math.cos(b) * 8;
                points[7] = (next < 2 ? 94 : 238) + (float) Math.sin(b) * 8;
            }
            for (int p = 0; p < 4; p++) {
                // GUI_TEXTURED culls clockwise faces, including mirrored controls.
                int point = 3 - p;
                float x = points[point * 2], y = points[point * 2 + 1];
                vertices[index * 4] = x; vertices[index * 4 + 1] = y;
                vertices[index * 4 + 2] = (x - 24) / 160; vertices[index * 4 + 3] = (y - 86) / 160;
                colors[index++] = 0xFFFFFFFF;
            }
        }
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect(24, 86, 160, 160).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, vertices, colors,
            RenderPipelines.GUI_TEXTURED, TextureSetup.of(cover.getGlTextureView(), cover.getSampler()), bounds));
    }

    private void hand(DrawContext ctx, boolean thumb) {
        if (client.player == null) return;
        var skin = client.player.getSkin().body().texturePath();
        boolean left = owner.hand.is("Left");
        var m = ctx.getMatrices(); m.pushMatrix();
        if (thumb) {
            m.translate(left ? -2 : 194, 292); m.rotate(left ? -.22f : .22f);
            Render2D.rect(ctx, -2, -2, 20, 44, 0x70000000);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, 0, 0, left ? 36 : 44, left ? 60 : 28, 16, 40, 4, 4, 64, 64);
        } else {
            m.translate(left ? -31 : 201, 299); m.rotate(left ? .25f : -.25f);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, 0, 0, left ? 36 : 44, left ? 52 : 20, 38, 170, 4, 12, 64, 64);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, 0, 0, left ? 52 : 44, left ? 52 : 36, 38, 170, 4, 12, 64, 64);
        }
        m.popMatrix();
    }

    private static void text(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        Fonts.draw(ctx, text, x, y, color, bold, size);
    }
    private static void centered(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        text(ctx, text, x - Fonts.width(text, bold, size) / 2, y, color, bold, size);
    }
    private static String time(long ms) { long seconds = Math.max(0, ms) / 1000; return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60); }
    private static boolean hit(float x, float y, float rx, float ry, float w, float h) { return x >= rx && x <= rx + w && y >= ry && y <= ry + h; }
    private static boolean circleHit(float x, float y, float cx, float cy, float r) { return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r; }

    private static void spotifyLogo(DrawContext ctx, float x, float y) {
        Render2D.circle(ctx, x, y, 10, GREEN);
        for (int i = 0; i < 3; i++) Render2D.arc(ctx, x - 1, y + 7 - i * 3, 6 + i, 1.4f, 225, 85, 0xFF0B2013, 0xFF0B2013);
    }
    private static void triangle(DrawContext ctx, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0) {
            float swapX = bx, swapY = by; bx = cx; by = cy; cx = swapX; cy = swapY;
        }
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect((int) Math.min(ax, Math.min(bx, cx)) - 1, (int) Math.min(ay, Math.min(by, cy)) - 1,
            (int) (Math.max(ax, Math.max(bx, cx)) - Math.min(ax, Math.min(bx, cx))) + 2,
            (int) (Math.max(ay, Math.max(by, cy)) - Math.min(ay, Math.min(by, cy))) + 2).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,
            new float[]{ax,ay,0,0, bx,by,0,0, cx,cy,0,0, ax,ay,0,0}, new int[]{color,color,color,color},
            RenderPipelines.GUI, TextureSetup.empty(), bounds));
    }
    private static void skip(DrawContext ctx, float x, float mx, float my, boolean next) {
        boolean hover = circleHit(mx, my, x, 350, 19);
        if (hover) Render2D.circle(ctx, x, 350, 19, 0xFF2D4134);
        int color = hover ? WHITE : 0xFFCAD3CD;
        float direction = next ? 1 : -1;
        triangle(ctx, x - 5 * direction, 343, x - 5 * direction, 357, x + 4 * direction, 350, color);
        Render2D.roundRect(ctx, x + (next ? 6 : -8), 343, 2, 14, .5f, color);
    }

    private void control(String action) {
        if (!player.state().available()) return;
        long now = System.nanoTime();
        if (lastControlAt != 0 && now - lastControlAt < 300_000_000L) return;
        lastControlAt = now;
        player.control(action);
    }

    @Override public boolean mouseClicked(Click event, boolean doubleClick) {
        if (owner.getBind().matches(dev.maro.util.KeyUtil.mouse(event.button()))) { close(); return true; }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        var l = layout(); float x = l.localX(event.x()), y = l.localY(event.y());
        if (hit(x, y, 170, 44, 24, 24) || hit(x, y, 67, 389, 74, 14)) { close(); return true; }
        var state = player.state();
        if (!state.available()) {
            if (hit(x, y, 24, 337, 160, 27)) { player.openSpotify(); return true; }
            return true;
        }
        if (hit(x, y, 20, 298, 168, 19) && state.canSeek() && state.durationMs() > 0) {
            dragging = true; scrubTrack = SpotifyMedia.artworkKey(state); scrub(x); return true;
        }
        if (circleHit(x, y, 53, 350, 19)) control("previous");
        else if (circleHit(x, y, 104, 350, 22)) control("toggle");
        else if (circleHit(x, y, 155, 350, 19)) control("next");
        return true;
    }

    private void scrub(float x) {
        scrubPosition = Math.round(Math.clamp((x - 24) / 160, 0, 1) * player.state().durationMs());
    }
    @Override public boolean mouseDragged(Click event, double dx, double dy) {
        if (dragging && event.button() == 0) { scrub(layout().localX(event.x())); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(Click event) {
        if (dragging && event.button() == 0) {
            scrub(layout().localX(event.x())); dragging = false;
            var state = player.state();
            if (state.available() && state.canSeek() && scrubTrack.equals(SpotifyMedia.artworkKey(state))) player.seekTo(scrubPosition);
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (owner.getBind().matches(input.key()) || input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        switch (input.key()) {
            case GLFW.GLFW_KEY_SPACE -> control("toggle");
            case GLFW.GLFW_KEY_LEFT -> control("previous");
            case GLFW.GLFW_KEY_RIGHT -> control("next");
            default -> { return super.keyPressed(input); }
        }
        return true;
    }
    @Override public void tick() { if (client.player == null || client.world == null) close(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() {
        if (removed) return;
        removed = true; dragging = false;
        if (cover != null) { cover.close(); cover = null; }
        shownArtwork = null;
        owner.screenClosed(this);
        super.removed();
    }
}
