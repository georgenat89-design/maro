package dev.maro.module.impl.player;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The crafter's nine slots: click the ones Crafter Disabler should disable. */
public class CrafterSlotsScreen extends Screen {
    private static final Identifier DISABLED = Identifier.ofVanilla("container/crafter/disabled_slot");
    private static final int CELL = 34;
    private static final int GAP = 6;
    private static final int PANEL_W = 196;
    private static final int PANEL_H = 214;

    private final Screen parent;
    private final CrafterDisabler module;

    public CrafterSlotsScreen(Screen parent, CrafterDisabler module) {
        super(Text.literal("Configure Disabler"));
        this.parent = parent;
        this.module = module;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private int gridLeft() {
        return left() + (PANEL_W - (CELL * 3 + GAP * 2)) / 2;
    }

    private int gridTop() {
        return top() + 44;
    }

    private int clearTop() {
        return gridTop() + CELL * 3 + GAP * 2 + 14;
    }

    private static boolean inside(double x, double y, float rx, float ry, float rw, float rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int left = left();
        int top = top();

        Render2D.shadow(ctx, left, top + 2, PANEL_W, PANEL_H, 14, 14, 0x80000000);
        Render2D.roundRect(ctx, left, top, PANEL_W, PANEL_H, 14, 0xF00B0D12);
        Render2D.roundOutline(ctx, left, top, PANEL_W, PANEL_H, 14, Render2D.px(), 0x22FFFFFF);

        // Back button and title, with a rule under them.
        boolean overBack = inside(mouseX, mouseY, left + 10, top + 10, 20, 16);
        Render2D.roundRect(ctx, left + 10, top + 10, 20, 16, 5, overBack ? 0x26FFFFFF : 0x14FFFFFF);
        Fonts.drawCentered(ctx, "<", left + 20, top + 18, Theme.accent(), true, 0.9f);
        Fonts.drawCentered(ctx, "Configure Disabler", left + PANEL_W / 2f, top + 18, Theme.TEXT, true, 0.9f);
        Render2D.rect(ctx, left + 10, top + 32, PANEL_W - 20, Math.max(Render2D.px(), 0.5f), 0x22FFFFFF);

        for (int slot = 0; slot < 9; slot++) {
            float x = gridLeft() + (slot % 3) * (CELL + GAP);
            float y = gridTop() + (slot / 3) * (CELL + GAP);
            boolean on = module.chosen(slot);
            boolean over = inside(mouseX, mouseY, x, y, CELL, CELL);
            Render2D.roundRect(ctx, x, y, CELL, CELL, 7, on ? ColorUtil.withAlpha(Theme.RED, over ? 60 : 42) : over ? 0xFF1A1C24 : 0xFF121318);
            Render2D.roundOutline(ctx, x, y, CELL, CELL, 7, 1, on ? ColorUtil.withAlpha(Theme.RED, 190) : over ? 0x33FFFFFF : 0x18FFFFFF);
            if (on) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, DISABLED, Math.round(x) + 5, Math.round(y) + 5, CELL - 10, CELL - 10);
        }

        float clearW = 76;
        float clearX = left + (PANEL_W - clearW) / 2f;
        boolean overClear = inside(mouseX, mouseY, clearX, clearTop(), clearW, 18);
        Render2D.roundRect(ctx, clearX, clearTop(), clearW, 18, 6, overClear ? 0x26FFFFFF : 0x12FFFFFF);
        Render2D.roundOutline(ctx, clearX, clearTop(), clearW, 18, 6, Render2D.px(), 0x26FFFFFF);
        Fonts.drawCentered(ctx, "Clear All", clearX + clearW / 2f, clearTop() + 9, ColorUtil.withAlpha(Theme.TEXT, 200), true, 0.8f);

        String count = module.chosenCount() + " of 9 slots disabled";
        Fonts.drawCentered(ctx, count, left + PANEL_W / 2f, clearTop() + 32, ColorUtil.withAlpha(Theme.TEXT, 120), true, 0.7f);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        if (inside(mx, my, left() + 10, top() + 10, 20, 16)) {
            close();
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            float x = gridLeft() + (slot % 3) * (CELL + GAP);
            float y = gridTop() + (slot / 3) * (CELL + GAP);
            if (inside(mx, my, x, y, CELL, CELL)) {
                module.toggle(slot);
                return true;
            }
        }
        float clearW = 76;
        if (inside(mx, my, left() + (PANEL_W - clearW) / 2f, clearTop(), clearW, 18)) {
            module.clearAll();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
