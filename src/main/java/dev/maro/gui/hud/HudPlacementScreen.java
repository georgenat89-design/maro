package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Moves a {@link HudElement}: drag it, scroll or press + / - to resize, arrows to nudge (Shift for
 * 10), C to center it across the screen, R to reset, Esc when done. Dragged near the middle of the
 * screen, it snaps there, with a guide line to show it. Draws no background, so what you place is
 * the real element over the real world.
 */
public class HudPlacementScreen extends Screen {
    /** How close, in GUI pixels, the element's middle must come to the screen's to snap to it. */
    private static final float SNAP = 6;

    private final Screen parent;
    private final HudElement element;

    private boolean dragging;
    private float grabX;
    private float grabY;
    private boolean snappedX;
    private boolean snappedY;

    public HudPlacementScreen(Screen parent, HudElement element) {
        super(Text.literal(element.hudName() + " - placement"));
        this.parent = parent;
        this.element = element;
    }

    public HudElement element() {
        return element;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float left = element.hudLeft();
        float top = element.hudTop();
        float w = element.hudWidth();
        float h = element.hudHeight();
        boolean over = contains(mouseX, mouseY);

        // Guide lines through the middle of the screen while the element is snapped to it.
        if (dragging && snappedX) Render2D.rect(context, width / 2f - 0.5f, 0, 1, height, Theme.accent(150));
        if (dragging && snappedY) Render2D.rect(context, 0, height / 2f - 0.5f, width, 1, Theme.accent(150));

        Render2D.roundOutline(context, left - 3, top - 3, w + 6, h + 6, 8, 1,
                Theme.accent(dragging ? 255 : over ? 200 : 120));

        String hint = "Drag to move  ·  Scroll to resize  ·  Arrows to nudge  ·  C to center  ·  R to reset  ·  Esc when done";
        float hintWidth = Fonts.width(hint, true, 0.8f) + 16;
        Render2D.roundRect(context, width / 2f - hintWidth / 2, 8, hintWidth, 16, 8, ColorUtil.withAlpha(Theme.PANEL, 225));
        Fonts.drawCentered(context, hint, width / 2f, 16, Theme.TEXT, true, 0.8f);

        String where = element.hudName() + "   " + Math.round(left) + ", " + Math.round(top)
                + "   " + String.format(Locale.ROOT, "%.2fx", element.hudScale());
        Fonts.drawCentered(context, where, width / 2f, 32, Theme.TEXT_MUTED, true, 0.75f);
    }

    private boolean contains(double x, double y) {
        return x >= element.hudLeft() && y >= element.hudTop()
                && x <= element.hudLeft() + element.hudWidth() && y <= element.hudTop() + element.hudHeight();
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() == 0 && contains(event.x(), event.y())) {
            dragging = true;
            grabX = (float) event.x() - element.hudLeft();
            grabY = (float) event.y() - element.hudTop();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (dragging) {
            dragTo((float) event.x() - grabX, (float) event.y() - grabY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /** Moves the element's top left corner here, snapping its middle to the screen's when close. */
    public void dragTo(float left, float top) {
        float w = element.hudWidth(), h = element.hudHeight();
        snappedX = Math.abs(left + w / 2 - width / 2f) <= SNAP;
        snappedY = Math.abs(top + h / 2 - height / 2f) <= SNAP;
        element.hudMove(snappedX ? width / 2f - w / 2 : left, snappedY ? height / 2f - h / 2 : top);
    }

    @Override
    public boolean mouseReleased(Click event) {
        dragging = false;
        snappedX = snappedY = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean fine = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        element.hudResize((float) Math.signum(scrollY) * (fine ? 0.02f : 0.1f));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        int step = event.hasShift() ? 10 : 1;
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> element.hudMove(element.hudLeft() - step, element.hudTop());
            case GLFW.GLFW_KEY_RIGHT -> element.hudMove(element.hudLeft() + step, element.hudTop());
            case GLFW.GLFW_KEY_UP -> element.hudMove(element.hudLeft(), element.hudTop() - step);
            case GLFW.GLFW_KEY_DOWN -> element.hudMove(element.hudLeft(), element.hudTop() + step);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> element.hudResize(0.05f);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> element.hudResize(-0.05f);
            case GLFW.GLFW_KEY_C -> element.hudMove(width / 2f - element.hudWidth() / 2, element.hudTop());
            case GLFW.GLFW_KEY_R -> element.hudReset();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
