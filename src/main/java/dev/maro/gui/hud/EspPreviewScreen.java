package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/**
 * Player ESP preview: the camera turns to face you and the ESP is drawn on your own character,
 * live in the world behind this see-through screen, while the buttons flip through its looks.
 */
public final class EspPreviewScreen extends Screen {
    private final Screen parent;
    private final PlayerESP module;
    private Perspective previousPerspective;
    private boolean restored;
    private int leftX, rightX, panelY, panelW, panelH;

    public EspPreviewScreen(Screen parent, PlayerESP module) {
        super(Text.literal("Player ESP Preview"));
        this.parent = parent;
        this.module = module;
    }

    private Setting<?> setting(String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private ModeSetting mode(String name) {
        return (ModeSetting) setting(name);
    }

    private BooleanSetting toggle(String name) {
        return (BooleanSetting) setting(name);
    }

    @Override
    protected void init() {
        if (previousPerspective == null) previousPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        PlayerESP.setPreviewing(true);
        restored = false;

        // Two panels at the sides, so the middle of the screen - where you stand - stays clear.
        int gap = 4, rowH = 20, pad = 8;
        panelW = Math.max(110, Math.min(170, (width - 150) / 2));
        panelH = pad * 2 + rowH * 3 + gap * 2;
        panelY = (height - panelH) / 2;
        leftX = 10;
        rightX = width - 10 - panelW;
        int inner = panelW - pad * 2, rows = panelY + pad;

        int arrow = 18, styleW = inner - arrow * 2 - gap * 2;
        int lx = leftX + pad;
        button(() -> "◀", b -> mode("Fill Style").cycle(-1), lx, rows, arrow, rowH);
        button(() -> mode("Fill Style").get(), b -> mode("Fill Style").cycle(1), lx + arrow + gap, rows, styleW, rowH);
        button(() -> "▶", b -> mode("Fill Style").cycle(1), lx + arrow + gap * 2 + styleW, rows, arrow, rowH);
        button(() -> "Fill " + onOff("Fill"), b -> flip("Fill"), lx, rows + rowH + gap, inner, rowH);
        button(() -> "Glow " + onOff("Glow"), b -> flip("Glow"), lx, rows + (rowH + gap) * 2, inner, rowH);

        int rx = rightX + pad;
        button(() -> "Outline " + onOff("Outline"), b -> flip("Outline"), rx, rows, inner, rowH);
        button(() -> "Outline: " + mode("Outline Color").get(), b -> mode("Outline Color").cycle(1), rx, rows + rowH + gap, inner, rowH);
        button(() -> "Done", b -> close(), rx, rows + (rowH + gap) * 2, inner, rowH);
    }

    private String onOff(String name) {
        return toggle(name).get() ? "on" : "off";
    }

    private void flip(String name) {
        BooleanSetting setting = toggle(name);
        setting.set(!setting.get());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int titleW = Math.min(360, width - 20), titleX = (width - titleW) / 2;
        Render2D.roundRect(ctx, titleX, 10, titleW, 34, 9, 0xD0191D2B);
        Fonts.drawCentered(ctx, "PLAYER ESP PREVIEW", width / 2f, 21, 0xFFE4DCFF, true, .8f);
        Fonts.drawCentered(ctx, "This is how players look through walls", width / 2f, 34, 0xFF8691AA, false, .62f);
        if (client.player == null) Fonts.drawCentered(ctx, "Join a world to preview", width / 2f, height / 2f, 0xFFBBC2D5, false, .8f);
        Render2D.roundRect(ctx, leftX, panelY, panelW, panelH, 10, 0xD0191D2B);
        Render2D.roundRect(ctx, rightX, panelY, panelW, panelH, 10, 0xD0191D2B);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        restore();
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        restore();
        super.removed();
    }

    private void restore() {
        if (restored) return;
        restored = true;
        PlayerESP.setPreviewing(false);
        if (previousPerspective != null) client.options.setPerspective(previousPerspective);
    }

    private void button(Supplier<String> label, ButtonWidget.PressAction action, int bx, int by, int bw, int bh) {
        addDrawableChild(new PreviewButton(label, action, bx, by, bw, bh));
    }

    private final class PreviewButton extends ButtonWidget {
        private final Supplier<String> label;

        PreviewButton(Supplier<String> label, PressAction action, int bx, int by, int bw, int bh) {
            super(bx, by, bw, bh, net.minecraft.text.Text.literal(label.get()), action, DEFAULT_NARRATION_SUPPLIER);
            this.label = label;
        }

        @Override
        protected void drawIcon(DrawContext ctx, int mx, int my, float delta) {
            boolean hover = isHovered() || isFocused();
            int accent = module.accentColor() & 0xFFFFFF;
            Render2D.roundRect(ctx, getX(), getY(), getWidth(), getHeight(), 5, hover ? 0xFF303A51 : 0xFF232B3D);
            Render2D.roundOutline(ctx, getX(), getY(), getWidth(), getHeight(), 5, 1, hover ? 0xFF000000 | accent : 0xFF364057);
            Fonts.beginRaw();
            try {
                String text = label.get();
                float size = .72f;
                float measured = Fonts.width(text, false, size);
                if (measured > getWidth() - 8) size *= Math.max(.6f, (getWidth() - 8) / measured);
                Fonts.drawCentered(ctx, text, getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0xFFDCE2F0, false, size);
            } finally {
                Fonts.endRaw();
            }
        }
    }
}
