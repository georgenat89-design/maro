package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.Emotes;
import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The emote wheel: a ring of glass segments round a centre, each showing you doing its emote. Hold
 * the wheel key, point and let go to play one; or tap the key, then click. Scroll for the next page.
 */
public final class EmoteWheelScreen extends Screen {
    private static final int PER_PAGE = 10;
    private static final float GAP_DEGREES = 2.4f;

    private final Emotes module;
    private final long openedAt = System.nanoTime();
    private final PlayerEntityRenderState[] previews = new PlayerEntityRenderState[PER_PAGE];
    private int page;
    private int hovered = -1;
    private boolean held = true;
    private float[] hoverAnim = new float[PER_PAGE];
    private long lastFrame = System.nanoTime();

    public EmoteWheelScreen(Emotes module) {
        super(Text.literal("Emotes"));
        this.module = module;
    }

    private static int pages() {
        return (Emote.values().length + PER_PAGE - 1) / PER_PAGE;
    }

    private Emote emoteAt(int slot) {
        int i = page * PER_PAGE + slot;
        return slot >= 0 && slot < PER_PAGE && i < Emote.values().length ? Emote.values()[i] : null;
    }

    private int slots() {
        return Math.min(PER_PAGE, Emote.values().length - page * PER_PAGE);
    }

    /** Plays the emote in {@code slot} of this page and closes the wheel. */
    public void choose(int slot) {
        Emote emote = emoteAt(slot);
        if (emote == null) return;
        module.play(emote);
        Sounds.click();
        close();
    }

    /** The emote under the pointer, if any; for tests. */
    public Emote hoveredEmote() {
        return emoteAt(hovered);
    }

    private float outer() {
        return Math.max(70f, Math.min(130f, Math.min(width, height) * 0.36f));
    }

    private float inner() {
        return outer() * 0.42f;
    }

    // ---- input ----------------------------------------------------------------------------

    @Override
    public void tick() {
        // Let go of the key: play what is under the pointer. A quick tap leaves the wheel open to click.
        if (held && !module.wheelKeyDown()) {
            held = false;
            if (hovered >= 0) choose(hovered);
            else if ((System.nanoTime() - openedAt) / 1e9 > 0.3) close();
        }
    }

    @Override
    public void mouseMoved(double mx, double my) {
        hovered = slotAt(mx, my);
    }

    private int slotAt(double mx, double my) {
        float cx = width / 2f, cy = height / 2f;
        double dx = mx - cx, dy = my - cy, distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < inner() * 0.75 || distance > outer() + 24) return -1;
        int n = slots();
        float sweep = 360f / n;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double fromTop = ((angle + 90 + sweep / 2) % 360 + 360) % 360;
        return Math.min(n - 1, (int) (fromTop / sweep));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int slot = slotAt(click.x(), click.y());
        if (slot >= 0) choose(slot);
        else if (Math.hypot(click.x() - width / 2f, click.y() - height / 2f) > outer() + 24) close();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (pages() > 1) {
            page = Math.floorMod(page + (vertical < 0 ? 1 : -1), pages());
            hovered = slotAt(mx, my);
            Sounds.click();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        // 1 to 9 and 0 pick a slot directly.
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_0) {
            choose(9);
            return true;
        }
        if (!held && module.wheelKeyDown()) close();
        return true;
    }

    // ---- drawing --------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float open = Easing.outCubic((now - openedAt) / 1e9f / 0.22f);
        float time = (now - openedAt) / 1e9f;
        Theme.update();
        Render2D.setAlpha(open);
        Render2D.rect(ctx, 0, 0, width, height, 0x55000000);

        float cx = width / 2f, cy = height / 2f;
        float scale = 0.85f + 0.15f * open;
        float outer = outer() * scale, inner = inner() * scale, band = outer - inner;
        int n = slots();
        float sweep = 360f / n;

        for (int i = 0; i < n; i++) {
            float target = i == hovered ? 1f : 0f;
            hoverAnim[i] += (target - hoverAnim[i]) * Math.min(1f, dt * 14f);
            float h = hoverAnim[i];
            float start = -90f - sweep / 2f + i * sweep + GAP_DEGREES / 2f, arc = sweep - GAP_DEGREES;
            float grow = 4f * h;
            // Glass: a dark translucent band, lit at its outer rim, warmer when pointed at.
            if (h > 0.01f && Theme.glow()) Render2D.arc(ctx, cx, cy, outer + grow + 5f, band + 10f, start, arc, Theme.accent(Math.round(0x26 * h)), Theme.accent2(Math.round(0x26 * h)));
            int glass = ColorUtil.lerp(0xA8141821, 0xC0242030, h);
            Render2D.arc(ctx, cx, cy, outer + grow, band, start, arc, glass, glass);
            if (h > 0.01f) Render2D.arc(ctx, cx, cy, outer + grow, band, start, arc, Theme.accent(Math.round(0x40 * h)), Theme.accent2(Math.round(0x28 * h)));
            Render2D.arc(ctx, cx, cy, outer + grow, 1.2f, start, arc, ColorUtil.lerp(0x40FFFFFF, Theme.accent(0xD0), h), ColorUtil.lerp(0x40FFFFFF, Theme.accent2(0xD0), h));
            Render2D.arc(ctx, cx, cy, inner + grow * 0.3f + 1f, 1f, start, arc, 0x1CFFFFFF, 0x1CFFFFFF);

            // You, doing it.
            double mid = Math.toRadians(start + arc / 2f);
            float radius = inner + band / 2f + grow * 0.6f;
            float px = cx + (float) Math.cos(mid) * radius, py = cy + (float) Math.sin(mid) * radius;
            preview(ctx, i, emoteAt(i), px, py, band * (0.92f + 0.08f * h), time);
        }

        // The centre: the name of what is pointed at, or the wheel's title.
        Render2D.circle(ctx, cx, cy, inner - 5f, 0xC20F1117);
        Render2D.ring(ctx, cx, cy, inner - 5f, 1f, 0x30FFFFFF);
        Emote pointed = emoteAt(hovered);
        Fonts.drawCentered(ctx, pointed != null ? pointed.label() : "Emotes", cx, cy - (pages() > 1 ? 3f : 0f),
                pointed != null ? 0xFFFFFFFF : 0xFFD6DAE4, true, pointed != null ? 0.95f : 0.85f);
        if (pages() > 1) Fonts.drawCentered(ctx, (page + 1) + " / " + pages(), cx, cy + 8f, 0xFF8890A0, false, 0.62f);
        Fonts.drawCentered(ctx, "Let go over an emote to play it  •  or click  •  scroll for more  •  moving stops it",
                cx, Math.max(8f, cy - outer - 14f), 0xC0B4BAC8, false, 0.62f);
        Render2D.setAlpha(1f);
    }

    /** Draws your player doing {@code emote}, looping, in a square round {@code (x, y)}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void preview(DrawContext ctx, int slot, Emote emote, float x, float y, float size, float time) {
        if (emote == null || client == null || client.player == null) return;
        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
        if (previews[slot] == null) {
            if (!(renderer.createRenderState() instanceof PlayerEntityRenderState created)) return;
            previews[slot] = created;
        }
        PlayerEntityRenderState state = previews[slot];
        renderer.updateRenderState(client.player, state, 1f);
        state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.bodyYaw = 180;
        state.relativeHeadYaw = 0;
        state.pitch = 0;
        state.width /= state.baseScale;
        state.height /= state.baseScale;
        state.baseScale = 1;
        // A one-off emote plays, rests a moment, and plays again.
        float t = emote.loops() ? time : time % (emote.length() + 0.8f);
        float blend = emote.loops() ? 1f : Math.min(1f, Math.min(t / 0.18f, Math.max(0f, (emote.length() + 0.6f - t) / 0.28f)));
        ((EmoteRenderState) state).maro$setEmote(emote, Math.min(t, emote.length()), blend);
        int half = Math.round(size / 2f);
        int x1 = Math.round(x) - half, y1 = Math.round(y) - half;
        ctx.addEntity(state, size * 0.4f, new Vector3f(0, state.height / 2 + 0.05f, 0),
                new Quaternionf().rotateZ((float) Math.PI).rotateX(0.12f), new Quaternionf().rotateX(0.12f),
                x1, y1, x1 + half * 2, y1 + half * 2);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
