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
import net.minecraft.util.PlayerInput;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/** A foreground phone with the player's skin, live album art and local media controls. */
public final class SpotifyPhoneScreen extends Screen {
    public static final float PHONE_WIDTH = 208, PHONE_HEIGHT = 464;
    private static final int WHITE = 0xFFF5F7F6, MUTED = 0xFFA3ADA7, GREEN = 0xFF1ED760;
    private record CoverMesh(float[] vertices, int[] colors) { }
    private static final CoverMesh COVER_MESH = coverMesh();
    private final SpotifyPhone owner;
    private final SpotifyPlayback player;
    private final PhoneMovement movement = new PhoneMovement();
    private final long openedAt = System.nanoTime();
    private long closingAt;
    private float closingFrom;
    private long lastControlAt;
    private Texture cover;
    private SpotifyMedia.Artwork shownArtwork;
    private boolean dragging, draggingVolume, looking, removed, sizeChanged;
    private double lastAudibleVolume = .5;
    private long scrubPosition;
    private String scrubTrack = "";

    public record Layout(float x, float y, float scale, float rotation) {
        private static final float PX = PHONE_WIDTH / 2, PY = PHONE_HEIGHT - 40;
        public float localX(double mx, double my) {
            double dx = (mx - x) / scale - PX, dy = (my - y) / scale - PY;
            return PX + (float) (dx * Math.cos(rotation) + dy * Math.sin(rotation));
        }
        public float localY(double mx, double my) {
            double dx = (mx - x) / scale - PX, dy = (my - y) / scale - PY;
            return PY + (float) (dy * Math.cos(rotation) - dx * Math.sin(rotation));
        }
        public float screenX(float lx, float ly) {
            return x + scale * (PX + (float) ((lx - PX) * Math.cos(rotation) - (ly - PY) * Math.sin(rotation)));
        }
        public float screenY(float lx, float ly) {
            return y + scale * (PY + (float) ((lx - PX) * Math.sin(rotation) + (ly - PY) * Math.cos(rotation)));
        }
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
        float pocket = pocketProgress();
        float direction = owner.hand.is("Left") ? -1 : 1;
        return new Layout(x + direction * pocket * 38 * scale,
            height - (PHONE_HEIGHT + 10) * scale + pocket * (PHONE_HEIGHT + 50) * scale,
            scale, direction * pocket * .27f);
    }

    private float pocketProgress() {
        long now = System.nanoTime();
        if (closingAt != 0) {
            float t = (float) Math.clamp((now - closingAt) / 240_000_000.0, 0, 1);
            return closingFrom + (1 - closingFrom) * t * t * (3 - 2 * t);
        }
        return (float) Math.pow(1 - Math.clamp((now - openedAt) / 320_000_000.0, 0, 1), 3);
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var state = player.state();
        var layout = layout();
        float mx = layout.localX(mouseX, mouseY), my = layout.localY(mouseX, mouseY);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(layout.x, layout.y);
        matrices.scale(layout.scale, layout.scale);
        matrices.translate(Layout.PX, Layout.PY);
        matrices.rotate(layout.rotation);
        matrices.translate(-Layout.PX, -Layout.PY);
        Fonts.beginRaw();
        try {
            if (owner.showHand.get()) hand(ctx, false);
            int tint = 0xFF25352B;
            updateArtwork(state);
            if (shownArtwork != null) tint = 0xFF000000 | shownArtwork.tintRgb();
            Render2D.shadow(ctx, 0, 0, PHONE_WIDTH, PHONE_HEIGHT, 27, 14, 0xA0000000);
            Render2D.roundRect(ctx, -1, -1, PHONE_WIDTH + 2, PHONE_HEIGHT + 2, 27, 0xFF49504E);
            Render2D.roundRect(ctx, 0, 0, PHONE_WIDTH, PHONE_HEIGHT, 27, 0xFF090B0C);
            Render2D.roundGradientV(ctx, 6, 6, PHONE_WIDTH - 12, PHONE_HEIGHT - 12, 22,
                ColorUtil.lerp(0xFF171B1D, tint, .18f), 0xFF111516);
            Render2D.roundRect(ctx, 84, 17, 40, 4, 2, 0xFF080A0B);
            spotifyLogo(ctx, 30, 56);
            text(ctx, "Spotify", 45, 51, WHITE, true, 1.2f);
            boolean closeHover = hit(mx, my, 170, 44, 24, 24);
            if (closeHover) Render2D.roundRect(ctx, 170, 44, 24, 24, 12, 0xFF303736);
            Render2D.line(ctx, 178, 52, 186, 60, 1.5f, MUTED);
            Render2D.line(ctx, 186, 52, 178, 60, 1.5f, MUTED);
            Render2D.shadow(ctx, 24, 86, 160, 160, 8, 8, 0x65000000);
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
                String hint = state.error().isBlank()
                    ? (allowsMovement() ? "Ctrl+Space: play   Arrows: skip" : "Space: play   Arrows: skip") : state.error();
                centered(ctx, Fonts.trim(hint, 170, false, .7f), 104, 376, MUTED, false, .7f);
            } else {
                boolean hover = hit(mx, my, 24, 337, 160, 27);
                Render2D.roundRect(ctx, 24, 337, 160, 27, 13.5f, hover ? 0xFF59E58B : GREEN);
                centered(ctx, "Open Spotify", 104, 346, 0xFF07140C, true, 1);
                centered(ctx, Fonts.trim(state.error().isBlank() ? "Desktop or web player" : state.error(), 166, false, .7f),
                    104, 376, MUTED, false, .7f);
            }
            drawVolume(ctx, mx, my);
            text(ctx, "Size", 24, 433, MUTED, false, .8f);
            sizeButton(ctx, 85, mx, my, false);
            centered(ctx, Math.round(owner.size.get() * 100) + "%", 134, 433, WHITE, false, .85f);
            sizeButton(ctx, 162, mx, my, true);
            Render2D.roundRect(ctx, 77, 452, 54, 3, 1.5f, 0xFF8B9591);
            if (owner.showHand.get()) hand(ctx, true);
        } finally { Fonts.endRaw(); matrices.popMatrix(); }
        if (allowsMovement() && closingAt == 0) {
            float hintX = owner.hand.is("Left") ? layout.x + (PHONE_WIDTH + 30) * layout.scale : 8;
            Fonts.beginRaw();
            try { Fonts.draw(ctx, Fonts.trim("Move keys · Right-drag outside phone to look", width - hintX - 8, false, .7f),
                hintX, height - 11, 0xBDE6ECE8, false, .7f); }
            finally { Fonts.endRaw(); }
        }
    }

    private void drawVolume(DrawContext ctx, float mx, float my) {
        var volume = player.volume();
        int color = volume.available() ? WHITE : 0xFF68716D;
        text(ctx, "Output volume", 24, 393, MUTED, false, .75f);
        String percent = volume.available() ? (volume.muted() ? "Muted" : volume.percent() + "%") : "—";
        text(ctx, percent, 184 - Fonts.width(percent, false, .75f), 393, color, false, .75f);
        if (hit(mx, my, 20, 403, 25, 21) && volume.available()) Render2D.circle(ctx, 32, 413, 11, 0xFF303736);
        Render2D.roundRect(ctx, 25, 410, 4, 6, .5f, color);
        triangle(ctx, 28, 410, 34, 406, 34, 420, color);
        if (volume.muted() || !volume.available()) {
            Render2D.line(ctx, 37, 410, 42, 416, 1.2f, color);
            Render2D.line(ctx, 42, 410, 37, 416, 1.2f, color);
        } else Render2D.arc(ctx, 34, 413, 6, 1.3f, -55, 110, color, color);
        float fraction = volume.available() && !volume.muted() ? (float) Math.clamp(volume.level(), 0, 1) : 0;
        Render2D.roundRect(ctx, 52, 411, 132, 3, 1.5f, 0xFF3A4240);
        if (fraction > 0) Render2D.roundRect(ctx, 52, 411, 132 * fraction, 3, 1.5f, 0xFFDCE7E0);
        if (volume.available()) Render2D.circle(ctx, 52 + 132 * fraction, 412.5f, draggingVolume ? 4 : 3, WHITE);
    }

    private static void sizeButton(DrawContext ctx, float x, float mx, float my, boolean plus) {
        Render2D.roundRect(ctx, x, 426, 22, 22, 6, hit(mx, my, x, 426, 22, 22) ? 0xFF3A4541 : 0xFF262E2B);
        Render2D.line(ctx, x + 7, 437, x + 15, 437, 1.3f, WHITE);
        if (plus) Render2D.line(ctx, x + 11, 433, x + 11, 441, 1.3f, WHITE);
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

    private static CoverMesh coverMesh() {
        // Immutable geometry shared across frames; only the pose and texture change.
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
        return new CoverMesh(vertices, colors);
    }

    private void drawCover(DrawContext ctx) {
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect(24, 86, 160, 160).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, COVER_MESH.vertices, COVER_MESH.colors,
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
    private static String time(long ms) { long s = Math.max(0, ms) / 1000; return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60; }
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
        if (closingAt != 0) return true;
        var l = layout(); float x = l.localX(event.x(), event.y()), y = l.localY(event.x(), event.y());
        if (allowsMovement() && !hit(x, y, -5, -5, PHONE_WIDTH + 10, PHONE_HEIGHT + 10)) {
            movement.mouse(event, true);
            if (event.button() == 1) { looking = true; return true; }
        }
        if (event.button() != 0) return true;
        if (hit(x, y, 170, 44, 24, 24) || hit(x, y, 67, 448, 74, 12)) { close(); return true; }
        if (hit(x, y, 85, 426, 22, 22) || hit(x, y, 162, 426, 22, 22)) {
            owner.size.set(owner.size.get() + (x < 120 ? -.05 : .05)); sizeChanged = true; return true;
        }
        if (hit(x, y, 20, 403, 25, 21)) { toggleMute(); return true; }
        if (hit(x, y, 48, 402, 140, 22)) {
            if (player.volume().available()) { draggingVolume = true; volumeAt(x); }
            return true;
        }
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
        if (closingAt != 0 || !client.isWindowFocused()) { cancelDrags(); return true; }
        if (draggingVolume && event.button() == 0) { volumeAt(layout().localX(event.x(), event.y())); return true; }
        if (dragging && event.button() == 0) { scrub(layout().localX(event.x(), event.y())); return true; }
        if (looking && event.button() == 1 && allowsMovement() && client.player != null
            && !dev.maro.nathan.modules.FreeCam.active()) {
            double sensitivity = client.options.getMouseSensitivity().getValue() * .6 + .2;
            double factor = sensitivity * sensitivity * sensitivity * 8 * client.getWindow().getScaleFactor();
            client.player.changeLookDirection(dx * factor * (client.options.getInvertMouseX().getValue() ? -1 : 1),
                dy * factor * (client.options.getInvertMouseY().getValue() ? -1 : 1));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(Click event) {
        movement.mouse(event, false);
        if (event.button() == 1) { looking = false; return true; }
        if (draggingVolume && event.button() == 0) {
            if (client.isWindowFocused()) volumeAt(layout().localX(event.x(), event.y()));
            draggingVolume = false; return true;
        }
        if (dragging && event.button() == 0) {
            scrub(layout().localX(event.x(), event.y())); dragging = false;
            var state = player.state();
            if (client.isWindowFocused() && state.available() && state.canSeek() && scrubTrack.equals(SpotifyMedia.artworkKey(state))) player.seekTo(scrubPosition);
            return true;
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        var l = layout();
        if (closingAt == 0 && hit(l.localX(mx, my), l.localY(mx, my), 20, 390, 168, 34)
            && player.volume().available() && Double.isFinite(vertical)) {
            setVolume(player.volume().level() + vertical * .02, false);
        }
        return true;
    }
    private void volumeAt(float x) { setVolume((x - 52) / 132.0, false); }
    private void setVolume(double level, boolean muted) {
        if (!Double.isFinite(level) || !player.volume().available()) { draggingVolume = false; return; }
        double bounded = Math.clamp(level, 0, 1);
        if (bounded > .001 && !muted) lastAudibleVolume = bounded;
        player.setVolume(bounded, muted);
    }
    private void toggleMute() {
        var v = player.volume();
        if (!v.available()) return;
        if (v.muted() || v.level() <= .001) setVolume(v.level() > .001 ? v.level() : lastAudibleVolume, false);
        else { lastAudibleVolume = v.level(); setVolume(v.level(), true); }
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (owner.getBind().matches(input.key()) || input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (closingAt != 0) { if (allowsMovement()) movement.key(input, true); return true; }
        if (input.key() == GLFW.GLFW_KEY_SPACE && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            control("toggle"); return true;
        }
        if (allowsMovement() && movement.key(input, true)) return true;
        switch (input.key()) {
            case GLFW.GLFW_KEY_SPACE -> control("toggle");
            case GLFW.GLFW_KEY_LEFT -> control("previous");
            case GLFW.GLFW_KEY_RIGHT -> control("next");
            default -> { return true; }
        }
        return true;
    }
    @Override public boolean keyReleased(KeyInput input) { movement.key(input, false); return true; }
    public boolean allowsMovement() { return owner.moveWhileOpen.get() && !removed; }
    public PlayerInput movementInput() { return allowsMovement() ? movement.read() : PlayerInput.DEFAULT; }
    private void cancelDrags() { dragging = false; draggingVolume = false; looking = false; }
    @Override public void close() {
        if (closingAt != 0 || removed) return;
        closingFrom = pocketProgress(); closingAt = System.nanoTime(); cancelDrags();
    }
    @Override public void tick() {
        if (!client.isWindowFocused()) { movement.clear(); cancelDrags(); }
        if (client.player == null || client.world == null
            || closingAt != 0 && System.nanoTime() - closingAt >= 240_000_000L) {
            if (client.currentScreen == this) client.setScreen(null);
        }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() {
        if (removed) return;
        removed = true; cancelDrags(); movement.clear();
        if (cover != null) { cover.close(); cover = null; }
        shownArtwork = null;
        owner.screenClosed(this);
        if (sizeChanged && dev.maro.config.ClientSettings.autoSave.get())
            dev.maro.config.ConfigManager.save(dev.maro.config.ConfigManager.getCurrent());
        super.removed();
    }
}
