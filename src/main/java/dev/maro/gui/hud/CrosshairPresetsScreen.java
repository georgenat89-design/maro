package dev.maro.gui.hud;

import dev.maro.gui.render.CrosshairRenderer;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.CustomCrosshair;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Responsive, scrollable previews; mouse and arrow keys both choose a preset. */
public final class CrosshairPresetsScreen extends Screen {
    private final Screen parent;
    private final CustomCrosshair module;
    private float scroll;
    private static final float CELL_HEIGHT = 58, GAP = 7;

    public CrosshairPresetsScreen(Screen parent, CustomCrosshair module) {
        super(Text.literal("Crosshair presets")); this.parent = parent; this.module = module;
    }

    private float panelWidth() { return Math.min(640, width - 24); }
    private float panelHeight() { return Math.min(360, height - 24); }
    private float left() { return (width - panelWidth()) / 2; }
    private float top() { return (height - panelHeight()) / 2; }
    private int columns() { return Math.max(2, Math.min(6, (int) ((panelWidth() - 24) / 88))); }
    private float cellWidth() { return (panelWidth() - 24 - GAP * (columns() - 1)) / columns(); }
    private float gridTop() { return top() + 45; }
    private float gridBottom() { return top() + panelHeight() - 26; }
    private float maxScroll() {
        int rows = (CrosshairRenderer.PRESETS.size() + columns() - 1) / columns();
        return Math.max(0, rows * (CELL_HEIGHT + GAP) - GAP - (gridBottom() - gridTop()));
    }
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme.update();
        float x = left(), y = top(), w = panelWidth(), h = panelHeight();
        scroll = Math.min(scroll, maxScroll());
        Render2D.rect(ctx, 0, 0, width, height, 0x99000000);
        Render2D.roundRect(ctx, x, y, w, h, 10, 0xF20B0D14);
        Render2D.roundOutline(ctx, x, y, w, h, 10, 1, Theme.BORDER);
        Fonts.drawV(ctx, "Crosshair presets", x + 14, y + 17, Theme.TEXT, true, 1);
        Fonts.drawV(ctx, "24 styles  /  " + module.preset(), x + 14, y + 33, Theme.TEXT_MUTED, false, 0.7f);
        Fonts.drawRight(ctx, "Done", x + w - 14, y + 17, Theme.accent(), true, 0.8f);
        int clipLeft = (int) (x + 10), clipTop = (int) gridTop();
        int clipRight = (int) (x + w - 10), clipBottom = (int) gridBottom();
        ctx.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        Render2D.setScissor(new ScreenRect(clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop));
        try {
            for (int i = 0; i < CrosshairRenderer.PRESETS.size(); i++) {
                float cx = x + 12 + i % columns() * (cellWidth() + GAP);
                float cy = gridTop() + i / columns() * (CELL_HEIGHT + GAP) - scroll;
                if (cy + CELL_HEIGHT < gridTop() || cy > gridBottom()) continue;
                String name = CrosshairRenderer.PRESETS.get(i);
                boolean selected = name.equals(module.preset());
                boolean hover = indexAt(mouseX, mouseY) == i;
                Render2D.roundRect(ctx, cx, cy, cellWidth(), CELL_HEIGHT, 6,
                    selected ? Theme.accent(35) : hover ? Theme.CARD_HOVER : Theme.CARD);
                Render2D.roundOutline(ctx, cx, cy, cellWidth(), CELL_HEIGHT, 6, 1,
                    selected ? Theme.accent(220) : Theme.BORDER);
                module.renderPreview(ctx, name, cx + cellWidth() / 2, cy + 22);
                Fonts.drawCentered(ctx, name, cx + cellWidth() / 2, cy + 47,
                    selected ? Theme.TEXT : Theme.TEXT_DIM, selected, 0.65f);
            }
        } finally { Render2D.setScissor(null); ctx.disableScissor(); }
        if (maxScroll() > 0) {
            float viewport = gridBottom() - gridTop();
            float thumb = Math.max(18, viewport * viewport / (viewport + maxScroll()));
            Render2D.roundRect(ctx, x + w - 6, gridTop(), 2, viewport, 1, Theme.BORDER);
            Render2D.roundRect(ctx, x + w - 6, gridTop() + scroll / maxScroll() * (viewport - thumb),
                2, thumb, 1, Theme.accent(170));
        }
        Fonts.drawCentered(ctx, "Click to select  ·  Arrows to browse  ·  Scroll  ·  Esc to return",
            width / 2f, y + h - 13, Theme.TEXT_MUTED, false, 0.65f);
    }

    private int indexAt(double mx, double my) {
        if (my < gridTop() || my >= gridBottom()) return -1;
        double lx = mx - left() - 12, ly = my - gridTop() + scroll;
        if (lx < 0 || ly < 0) return -1;
        int col = (int) (lx / (cellWidth() + GAP)), row = (int) (ly / (CELL_HEIGHT + GAP));
        if (col >= columns() || lx % (cellWidth() + GAP) >= cellWidth() || ly % (CELL_HEIGHT + GAP) >= CELL_HEIGHT) return -1;
        int index = row * columns() + col;
        return index < CrosshairRenderer.PRESETS.size() ? index : -1;
    }
    @Override public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() == 0) {
            if (event.x() >= left() + panelWidth() - 55 && event.x() <= left() + panelWidth()
                && event.y() >= top() && event.y() <= top() + 30) { close(); return true; }
            int index = indexAt(event.x(), event.y());
            if (index >= 0) { module.selectPreset(CrosshairRenderer.PRESETS.get(index)); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = (float) Math.max(0, Math.min(maxScroll(), scroll - sy * 35)); return true;
    }
    @Override public boolean keyPressed(KeyInput event) {
        int step = switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> -1; case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> -columns(); case GLFW.GLFW_KEY_DOWN -> columns(); default -> 0;
        };
        if (step == 0) return super.keyPressed(event);
        int index = Math.floorMod(CrosshairRenderer.PRESETS.indexOf(module.preset()) + step, CrosshairRenderer.PRESETS.size());
        module.selectPreset(CrosshairRenderer.PRESETS.get(index));
        float rowTop = index / columns() * (CELL_HEIGHT + GAP);
        if (rowTop < scroll) scroll = rowTop;
        if (rowTop + CELL_HEIGHT > scroll + gridBottom() - gridTop()) scroll = rowTop + CELL_HEIGHT - (gridBottom() - gridTop());
        scroll = Math.max(0, Math.min(maxScroll(), scroll)); return true;
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
