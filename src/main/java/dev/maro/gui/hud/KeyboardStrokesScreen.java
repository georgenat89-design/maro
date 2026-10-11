package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses which keybinds Keyboard Strokes shows. Every keybind in the game is a row: click it to
 * put it on the HUD or take it off. A preview of the real layout sits at the top and changes as
 * you click; the buttons underneath place the HUD, reset to the classic keys or clear them all.
 */
public final class KeyboardStrokesScreen extends Screen {
    private static final float ROW = 22, ROW_GAP = 3, PREVIEW_MAX = 120;

    private final Screen parent;
    private final KeyboardStrokes module;

    private float scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run();
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float px, py, pw, ph;

    public KeyboardStrokesScreen(Screen parent, KeyboardStrokes module) {
        super(Text.literal("Keyboard Strokes"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        hits.clear();
        Theme.update();

        KeyBinding[] all = module.allBindings();
        float rowsContent = all.length * (ROW + ROW_GAP);
        pw = Math.min(380, width - 24);
        // Header, buttons and footer take what they take; the preview and the list share the rest.
        float chrome = 52 + 8 + 8 + 22 + 12 + 26;
        float avail = Math.max(0, height - 24 - chrome);
        float previewBox = Math.max(34, Math.min(PREVIEW_MAX + 12, avail * 0.45f));
        float layoutH = Math.max(KeyboardStrokes.KEY, module.layoutHeight());
        float previewScale = Math.min(1f, (previewBox - 12) / layoutH);
        float rowsH = Math.min(rowsContent, Math.max(ROW + ROW_GAP, avail - previewBox));
        ph = chrome + previewBox + rowsH;
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);
        float x = px + 16, w = pw - 32;

        float open = Easing.outCubic(Math.min(1f, (now - openedAt) / 1e9f / 0.18f));
        Render2D.setAlpha(open);
        Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
        Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 20, 0x90000000);
        Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
        Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
        Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));

        Fonts.drawV(ctx, "KEYBOARD STROKES", px + 16, py + 19, Theme.accent(), true, 0.95f);
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, "Click a keybind to show it on your screen or hide it.", px + 16, py + 34, Theme.TEXT_MUTED, false, 0.62f);
        } finally {
            Fonts.endRaw();
        }
        Render2D.rect(ctx, px + 16, py + 46, pw - 32, 1, 0x14FFFFFF);

        // Preview: the real layout, shrunk to fit if it is tall.
        float by = py + 52;
        Render2D.roundRect(ctx, x, by, w, previewBox, 8, 0xFF0C0E14);
        Render2D.roundOutline(ctx, x, by, w, previewBox, 8, 1, 0x14FFFFFF);
        if (module.shownCount() == 0) {
            Fonts.drawCentered(ctx, "Nothing selected", x + w / 2f, by + previewBox / 2f, Theme.TEXT_MUTED, false, 0.7f);
        } else {
            float pwid = KeyboardStrokes.WIDTH * previewScale;
            module.draw(ctx, Math.round(x + (w - pwid) / 2f), Math.round(by + (previewBox - layoutH * previewScale) / 2f), previewScale, false);
        }

        // The keybind list, scrolling if there are more than fit.
        float rowsY = by + previewBox + 8;
        float maxScroll = Math.max(0, rowsContent - rowsH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        Render2D.clip(ctx, Math.round(px), Math.round(rowsY - 2), Math.round(px + pw), Math.round(rowsY + rowsH));
        float ry = rowsY - scroll;
        float rw = w - (maxScroll > 0 ? 8 : 0);
        for (KeyBinding b : all) {
            if (ry + ROW >= rowsY && ry <= rowsY + rowsH) row(ctx, b, x, ry, rw, mx, my, rowsY, rowsY + rowsH);
            ry += ROW + ROW_GAP;
        }
        Render2D.unclip(ctx);
        if (maxScroll > 0) {
            float bar = Math.max(16, rowsH * rowsH / rowsContent);
            Render2D.roundRect(ctx, px + pw - 18, rowsY + (rowsH - bar) * (scroll / maxScroll), 3, bar, 1.5f, 0x40FFFFFF);
        }

        // Buttons.
        float y = rowsY + rowsH + 8;
        float bw = (w - 8) / 3f;
        button(ctx, "Place on screen", x, y, bw, mx, my, () -> client.setScreen(new HudPlacementScreen(this, module)));
        button(ctx, "Classic keys", x + bw + 4, y, bw, mx, my, module::resetKeys);
        button(ctx, "Clear all", x + (bw + 4) * 2, y, bw, mx, my, module::clearKeys);
        y += 22 + 12;

        Fonts.drawV(ctx, module.isEnabled() ? "On" : "Off", x + 2, y + 10, module.isEnabled() ? Theme.accent() : Theme.TEXT_MUTED, true, 0.7f);
        float sw = 24, sh = 13, sx = x + 26, sy = y + 10 - sh / 2f;
        Render2D.roundRect(ctx, sx, sy, sw, sh, sh / 2f, module.isEnabled() ? Theme.accent() : 0xFF2A2F3D);
        Render2D.circle(ctx, module.isEnabled() ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, sh / 2f - 2, 0xFFFFFFFF);
        hit(x, y, 54, 20, module::toggle);

        float dw = 64, dx = px + pw - 16 - dw;
        boolean doneHover = inside(mx, my, dx, y, dw, 20);
        Render2D.roundRect(ctx, dx, y, dw, 20, 10, doneHover ? Theme.accent() : Theme.accent(0xD0));
        Fonts.drawCentered(ctx, "Done", dx + dw / 2f, y + 10, 0xFFFFFFFF, true, 0.68f);
        hit(dx, y, dw, 20, this::close);
        Render2D.setAlpha(1f);
    }

    /** One keybind: lit when shown, with what it does and the key on it. */
    private void row(DrawContext ctx, KeyBinding b, float x, float y, float w, int mx, int my, float top, float bottom) {
        boolean on = module.isShown(b);
        boolean hover = inside(mx, my, x, y, w, ROW) && my >= top && my < bottom;
        Render2D.roundRect(ctx, x, y, w, ROW, 6, on ? Theme.accent(0x26) : hover ? 0xFF1C202C : 0xFF171A24);
        Render2D.roundOutline(ctx, x, y, w, ROW, 6, 1, on ? Theme.accent(0x90) : 0x14FFFFFF);

        float cy = y + ROW / 2f;
        Render2D.circle(ctx, x + 12, cy, 5, on ? Theme.accent() : 0xFF2A2F3D);
        if (on) Render2D.circle(ctx, x + 12, cy, 2, 0xFFFFFFFF);

        String key = KeyboardStrokes.label(b);
        float kw = Math.max(26, Fonts.width(key, true, 0.62f) + 14);
        float kx = x + w - 8 - kw;
        Render2D.roundRect(ctx, kx, y + 3, kw, ROW - 6, 4, 0xFF0C0E14);
        Fonts.drawCentered(ctx, key, kx + kw / 2f, cy, on ? Theme.TEXT : Theme.TEXT_DIM, true, 0.62f);

        String name = Fonts.trim(I18n.translate(b.getId()), kx - (x + 26) - 8, false, 0.72f);
        Fonts.drawV(ctx, name, x + 26, cy, on ? Theme.TEXT : Theme.TEXT_DIM, false, 0.72f);

        float hitTop = Math.max(y, top), hitBottom = Math.min(y + ROW, bottom);
        if (hitBottom > hitTop) hit(x, hitTop, w, hitBottom - hitTop, () -> {
            module.toggleKey(b);
            Sounds.click();
        });
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, int mx, int my, Action action) {
        boolean hover = inside(mx, my, x, y, w, 22);
        Render2D.roundRect(ctx, x, y, w, 22, 6, hover ? 0xFF232838 : 0xFF1A1E29);
        Render2D.roundOutline(ctx, x, y, w, 22, 6, 1, hover ? Theme.accent(0xA0) : 0x1EFFFFFF);
        Fonts.drawCentered(ctx, label, x + w / 2f, y + 11, hover ? Theme.TEXT : Theme.TEXT_DIM, true, 0.66f);
        hit(x, y, w, 22, () -> {
            Sounds.click();
            action.run();
        });
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(click.x(), click.y())) {
                    hit.action().run();
                    return true;
                }
            }
            if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) close();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 28;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
