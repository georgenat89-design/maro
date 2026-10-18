package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.hud.KeyboardLayout.Cap;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.util.ColorUtil;
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

import static dev.maro.gui.hud.KeyboardLayout.*;

/** A full, clickable keyboard; selected keycaps are filled with their section's color. */
public final class KeyboardStrokesScreen extends Screen {
    private static final float W = 820, H = 455;
    private record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
    private static final List<Cap> CAPS = KeyboardLayout.keys();
    private final List<Hit> hits = new ArrayList<>();
    private final Screen parent;
    private final KeyboardStrokes module;
    private boolean actions;
    private float scale = 1, ox, oy, mx, my, scroll, scrollTarget;
    private long lastFrame;
    private String detail = "";
    private java.util.Set<Integer> selectedKeys = java.util.Set.of();

    public KeyboardStrokesScreen(Screen parent, KeyboardStrokes module) {
        super(Text.literal("Choose Keyboard Strokes")); this.parent = parent; this.module = module;
    }
    public double[] keyPoint(int code) {
        Cap cap = CAPS.stream().filter(c -> c.code() == code).findFirst().orElseThrow();
        return new double[]{ox + (cap.x() + cap.w() / 2) * scale, oy + (cap.y() + cap.h() / 2) * scale};
    }
    public int keyCount() { return CAPS.size(); }
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1e9f); lastFrame = now;
        scale = Math.min((width - 16f) / W, (height - 16f) / H);
        ox = (width - W * scale) / 2; oy = (height - H * scale) / 2;
        mx = (mouseX - ox) / scale; my = (mouseY - oy) / scale;
        hits.clear(); detail = ""; Theme.update(); selectedKeys = module.physicalKeys();
        scroll += (scrollTarget - scroll) * (1 - (float)Math.exp(-18 * dt));
        Render2D.rect(ctx, 0, 0, width, height, 0xBA060A12);
        ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(ox, oy); ctx.getMatrices().scale(scale, scale);
        Fonts.beginRaw(); Fonts.setMeasureScale(scale);
        try {
            Render2D.shadow(ctx, 0, 5, W, H, 14, 22, 0x90000000);
            Render2D.roundRect(ctx, 0, 0, W, H, 13, 0xFC111925);
            Render2D.roundOutline(ctx, 0, 0, W, H, 13, .8f, 0x35FFFFFF);
            Render2D.roundRect(ctx, 17, 17, 4, 29, 2, Theme.accent());
            text(ctx, "Keyboard Strokes", 30, 25, 1.1f, true, Theme.TEXT);
            text(ctx, "Click the keys you want. Use Arrange Keys to customize their HUD positions.", 30, 44, .73f, false, Theme.TEXT_MUTED);
            button(ctx, actions ? "Keyboard" : "Game actions", 646, 19, 150, () -> { actions = !actions; scroll = scrollTarget = 0; });
            Render2D.rect(ctx, 22, 60, W - 44, .7f, 0x24FFFFFF);
            if (actions) actionList(ctx);
            else {
                text(ctx, "MAIN KEYS", 26, 77, .61f, true, MAIN);
                text(ctx, "FUNCTIONS", 244, 77, .61f, true, FUNCTION);
                text(ctx, "NAVIGATION", 533, 77, .61f, true, NAV);
                text(ctx, "NUMPAD", 647, 77, .61f, true, PAD);
                Render2D.roundRect(ctx, 18, 87, 768, 223, 9, 0xFF090F19);
                for (Cap cap : CAPS) cap(ctx, cap);
                text(ctx, "MOUSE BUTTONS", 26, 322, .61f, true, FUNCTION);
                text(ctx, module.shownCount() + " keys selected", 300, 340, .9f, true, Theme.TEXT);
                text(ctx, "Click again to remove a key.", 300, 360, .7f, false, Theme.TEXT_MUTED);
                text(ctx, Fonts.trim(detail.isEmpty() ? "Unused keys can be selected too." : detail, 314, false, .67f),
                        300, 382, .67f, false, detail.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_DIM);
                preview(ctx);
            }
            Render2D.rect(ctx, 22, 412, W - 44, .7f, 0x24FFFFFF);
            button(ctx, "Place HUD", 24, 423, 105, () -> client.setScreen(new HudPlacementScreen(this, module)));
            button(ctx, "Classic keys", 137, 423, 105, module::resetKeys);
            button(ctx, "Clear all", 250, 423, 87, module::clearKeys);
            button(ctx, "Arrange Keys", 345, 423, 112, () -> client.setScreen(new KeyboardArrangeScreen(this, module)));
            button(ctx, module.isEnabled() ? "HUD: On" : "HUD: Off", 623, 423, 91, module::toggle);
            button(ctx, "Done", 722, 423, 74, this::close);
        } finally {
            Fonts.setMeasureScale(1); Fonts.endRaw(); Render2D.setScissor(null); Render2D.setAlpha(1);
            ctx.getMatrices().popMatrix();
        }
    }

    private void cap(DrawContext ctx, Cap cap) {
        boolean selected = selectedKeys.contains(cap.code()), over = inside(cap.x(), cap.y(), cap.w(), cap.h());
        int fill = selected ? ColorUtil.lerp(cap.color(), 0xFFFFFFFF, .12f)
                : ColorUtil.lerp(0xFF1B2434, cap.color(), over ? .31f : .10f);
        Render2D.roundRect(ctx, cap.x(), cap.y() + 2, cap.w(), cap.h(), 4, 0xFF04070D);
        Render2D.roundRect(ctx, cap.x(), cap.y(), cap.w(), cap.h() - 1, 4, fill);
        Render2D.roundOutline(ctx, cap.x(), cap.y(), cap.w(), cap.h() - 1, 4, .7f,
                selected ? ColorUtil.lerp(cap.color(), 0xFFFFFFFF, .5f) : ColorUtil.withAlpha(cap.color(), over ? 195 : 90));
        Render2D.roundOutline(ctx, cap.x() + 3, cap.y() + 2, cap.w() - 6, cap.h() - 6, 2, .5f, selected ? 0x35FFFFFF : 0x10FFFFFF);
        Fonts.drawCentered(ctx, Fonts.trim(cap.label(), cap.w() - 5, true, .65f), cap.x() + cap.w() / 2, cap.y() + (cap.h() - 1) / 2,
                selected ? 0xFF172334 : Theme.TEXT_DIM, true, .65f);
        if (selected) Render2D.circle(ctx, cap.x() + cap.w() - 5, cap.y() + 5, 1.4f, 0xFF33435A);
        if (over) {
            var names = java.util.Arrays.stream(module.allBindings()).filter(b -> KeyboardStrokes.code(b) == cap.code())
                    .map(b -> I18n.translate(b.getId())).distinct().toList();
            detail = cap.label() + " · " + (names.isEmpty() ? "Physical key" : String.join(", ", names));
        }
        hit(cap.x(), cap.y(), cap.w(), cap.h(), () -> module.togglePhysical(cap.code()));
    }
    private void preview(DrawContext ctx) {
        Render2D.roundRect(ctx, 633, 325, 163, 77, 8, 0xFF0A111C);
        text(ctx, "HUD PREVIEW", 645, 337, .6f, true, Theme.TEXT_MUTED);
        if (module.shownCount() == 0) { text(ctx, "Choose some keys", 647, 370, .72f, false, Theme.TEXT_DIM); return; }
        float height = Math.max(KeyboardStrokes.KEY, module.layoutHeight());
        float width = Math.max(KeyboardStrokes.KEY, module.layoutWidth());
        float fit = Math.min(1f, Math.min(143 / width, 49 / height));
        module.draw(ctx, 714 - width * fit / 2, 348, fit, false);
    }
    private void actionList(DrawContext ctx) {
        text(ctx, "Select actions individually, including rebound and additional mod controls.", 25, 78, .75f, false, Theme.TEXT_MUTED);
        KeyBinding[] bindings = module.allBindings();
        float max = Math.max(0, ((bindings.length + 2) / 3) * 29 - 300);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget)); scroll = Math.max(0, Math.min(max, scroll));
        Render2D.clip(ctx, 24, 94, 796, 400);
        try {
            for (int i = 0; i < bindings.length; i++) {
                KeyBinding binding = bindings[i]; float x = 24 + (i % 3) * 260, y = 96 + (i / 3) * 29 - scroll;
                if (y + 25 < 94 || y > 400) continue;
                boolean selected = module.isShown(binding);
                Render2D.roundRect(ctx, x, y, 251, 25, 5, selected ? Theme.accent(0x40) : 0xFF1C293B);
                Render2D.roundOutline(ctx, x, y, 251, 25, 5, .7f, selected ? Theme.accent() : 0x20FFFFFF);
                text(ctx, Fonts.trim(I18n.translate(binding.getId()), 180, false, .71f), x + 8, y + 12, .71f, false, Theme.TEXT);
                Fonts.drawRight(ctx, Fonts.trim(KeyboardStrokes.label(binding), 52, true, .63f), x + 244, y + 12, Theme.TEXT_DIM, true, .63f);
                float top = Math.max(94, y), bottom = Math.min(400, y + 25);
                if (bottom > top) hit(x, top, 251, bottom - top, () -> module.toggleKey(binding));
            }
        } finally { Render2D.unclip(ctx); }
    }
    private void text(DrawContext ctx, String text, float x, float y, float size, boolean bold, int color) {
        Fonts.drawV(ctx, text, x, y, color, bold, size);
    }
    private void button(DrawContext ctx, String label, float x, float y, float w, Runnable action) {
        boolean over = inside(x, y, w, 24);
        Render2D.roundRect(ctx, x, y, w, 24, 6, over ? 0xFF30415A : 0xFF202F43);
        Render2D.roundOutline(ctx, x, y, w, 24, 6, .7f, over ? Theme.accent() : 0x30FFFFFF);
        Fonts.drawCentered(ctx, label, x + w / 2, y + 12, Theme.TEXT, false, .73f);
        hit(x, y, w, 24, action);
    }
    private boolean inside(float x, float y, float w, float h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private void hit(float x, float y, float w, float h, Runnable action) { hits.add(new Hit(x, y, w, h, action)); }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        double x = (click.x() - ox) / scale, y = (click.y() - oy) / scale;
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(x, y)) {
            Sounds.click(); hits.get(i).action.run(); return true;
        }
        if (x < 0 || y < 0 || x > W || y > H) close();
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (actions && (y - oy) / scale >= 94 && (y - oy) / scale < 400) scrollTarget -= (float)vertical * 42;
        return true;
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return true;
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public void removed() { ConfigManager.saveAll(); }
    @Override public boolean shouldPause() { return false; }

}
