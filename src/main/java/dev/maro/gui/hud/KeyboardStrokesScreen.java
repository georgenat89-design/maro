package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.module.impl.visuals.KeyboardStrokes.KeyDef;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the keys Keyboard Strokes shows, on a picture of a keyboard: click a key to put it on the
 * HUD, click it again to take it off. The mouse buttons sit under the keyboard. Keys the game uses are
 * brighter, and pointing at one says what it does. The buttons underneath place the HUD, pick the keys
 * your controls are on, clear everything or switch the layout.
 */
public final class KeyboardStrokesScreen extends Screen {
    private final Screen parent;
    private final KeyboardStrokes module;
    private final long openedAt = System.nanoTime();

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
        hits.clear();
        Theme.update();

        // Size the keyboard to the window: as wide as fits, never taller than there is room for.
        pw = Math.min(width - 24, 780);
        float chrome = 58 + 12 + 22 + 22 + 12 + 20 + 16;
        float u = Math.max(8, Math.min(36, Math.min((pw - 32) / KeyboardStrokes.BOARD_W, (height - 24 - chrome) / KeyboardStrokes.BOARD_H)));
        float kbW = KeyboardStrokes.BOARD_W * u, kbH = KeyboardStrokes.BOARD_H * u;
        ph = chrome + kbH;
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);
        float x = px + 16, w = pw - 32;
        float kbX = Math.round(px + (pw - kbW) / 2f), kbY = py + 58;

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
            Fonts.drawV(ctx, "Click the keys you want on your screen. Click again to remove one.", px + 16, py + 34, Theme.TEXT_MUTED, false, 0.62f);
        } finally {
            Fonts.endRaw();
        }
        Render2D.rect(ctx, px + 16, py + 46, pw - 32, 1, 0x14FFFFFF);

        // The keyboard.
        float gap = Math.max(1.5f, u * 0.1f);
        float radius = Math.min(6, u * 0.2f);
        KeyDef hovered = null;
        for (KeyDef key : KeyboardStrokes.KEYS) {
            float kx = kbX + key.col() * u, ky = kbY + key.row() * u, kw = key.w() * u - gap, kh = u - gap;
            boolean over = mx >= kx && my >= ky && mx < kx + kw && my < ky + kh;
            if (over) hovered = key;
            boolean on = module.isShown(key.code());
            boolean used = !module.bindingsFor(key.code()).isEmpty();
            Render2D.roundRect(ctx, kx, ky, kw, kh, radius, on ? Theme.accent(0xE0) : over ? 0xFF262B3A : 0xFF171A24);
            Render2D.roundOutline(ctx, kx, ky, kw, kh, radius, 1, on ? Theme.accent() : over ? Theme.accent(0x90) : 0x1CFFFFFF);
            int color = on ? 0xFFFFFFFF : used || over ? Theme.TEXT : Theme.TEXT_MUTED;
            KeyboardStrokes.fitted(ctx, key.label(), kx + kw / 2f, ky + kh / 2f, kw - 4, color, Math.min(0.7f, u / 40f + 0.25f));
            final int code = key.code();
            hit(kx, ky, kw, kh, () -> {
                module.toggleKey(code);
                Sounds.click();
            });
        }
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, "MOUSE", kbX + 8.4f * u, kbY + 7.25f * u - gap / 2f, Theme.TEXT_MUTED, true, 0.5f);
        } finally {
            Fonts.endRaw();
        }

        // What is pointed at, or how many are chosen.
        float infoY = kbY + kbH + 12;
        String info;
        if (hovered != null) {
            String action = module.actionFor(hovered.code());
            info = hovered.label() + "   " + (action.isEmpty() ? "not used in the game" : action);
        } else {
            int n = module.shownCount();
            info = n == 0 ? "No keys chosen" : n + (n == 1 ? " key" : " keys") + " on your screen";
        }
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, info, x + 2, infoY, hovered != null ? Theme.TEXT : Theme.TEXT_MUTED, hovered != null, 0.68f);
        } finally {
            Fonts.endRaw();
        }

        // Buttons.
        float by = infoY + 10;
        float bw = (w - 12) / 4f;
        button(ctx, "Place on screen", x, by, bw, mx, my, () -> client.setScreen(new HudPlacementScreen(this, module)));
        button(ctx, "My controls", x + (bw + 4), by, bw, mx, my, module::useGameKeys);
        button(ctx, "Clear all", x + (bw + 4) * 2, by, bw, mx, my, module::clearKeys);
        button(ctx, "Layout: " + module.layoutSetting().get(), x + (bw + 4) * 3, by, bw, mx, my, () -> module.layoutSetting().cycle(1));

        // On/off and Done.
        float y = by + 22 + 12;
        boolean enabled = module.isEnabled();
        Fonts.drawV(ctx, enabled ? "On" : "Off", x + 2, y + 10, enabled ? Theme.accent() : Theme.TEXT_MUTED, true, 0.7f);
        float sw = 24, sh = 13, sx = x + 26, sy = y + 10 - sh / 2f;
        Render2D.roundRect(ctx, sx, sy, sw, sh, sh / 2f, enabled ? Theme.accent() : 0xFF2A2F3D);
        Render2D.circle(ctx, enabled ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, sh / 2f - 2, 0xFFFFFFFF);
        hit(x, y, 54, 20, module::toggle);

        float dw = 64, dx = px + pw - 16 - dw;
        boolean doneHover = inside(mx, my, dx, y, dw, 20);
        Render2D.roundRect(ctx, dx, y, dw, 20, 10, doneHover ? Theme.accent() : Theme.accent(0xD0));
        Fonts.drawCentered(ctx, "Done", dx + dw / 2f, y + 10, 0xFFFFFFFF, true, 0.68f);
        hit(dx, y, dw, 20, this::close);
        Render2D.setAlpha(1f);
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, int mx, int my, Action action) {
        boolean hover = inside(mx, my, x, y, w, 22);
        Render2D.roundRect(ctx, x, y, w, 22, 6, hover ? 0xFF232838 : 0xFF1A1E29);
        Render2D.roundOutline(ctx, x, y, w, 22, 6, 1, hover ? Theme.accent(0xA0) : 0x1EFFFFFF);
        KeyboardStrokes.fitted(ctx, label, x + w / 2f, y + 11, w - 10, hover ? Theme.TEXT : Theme.TEXT_DIM, 0.66f);
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
