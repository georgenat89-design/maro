package dev.maro.nathan.gui;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import dev.maro.nathan.modules.RegionMap;

/**
 * Puts the Region Map where it should sit: dragged, scaled with the wheel or with
 * + and -, nudged a pixel at a time with the arrow keys, and put back with R.
 *
 * <p>It draws no background and no copy of the map. The map is a HUD element, and
 * the HUD is drawn before whatever screen is over it, so what is being dragged
 * here is the real thing over the real world rather than a sample of it against a
 * dimmed screen. That also means the map's own hover details work while this is
 * open, which is the other reason not to cover anything up.
 */
public class RegionMapScreen extends Screen {
    private final Screen parent;
    private final RegionMap module;

    private boolean dragging;
    private double grabX;
    private double grabY;

    public RegionMapScreen(Screen parent, RegionMap module) {
        super(Text.literal("Region Map - placement"));

        this.parent = parent;
        this.module = module;
    }

    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float delta) {
        // Deliberately nothing: no dimming, no blur.
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        MinecraftClient minecraft = MinecraftClient.getInstance();

        graphics.drawCenteredTextWithShadow(minecraft.textRenderer,
            "Drag to move  -  Scroll or + / - to scale  -  Arrows to nudge (Shift: 10)  -  R to reset  -  Esc when done",
            width / 2, 8, 0xFFFFFFFF);

        graphics.drawCenteredTextWithShadow(minecraft.textRenderer,
            "X " + Math.round(module.mapLeft()) + "    Y " + Math.round(module.mapTop())
                + "    Scale " + String.format(Locale.ROOT, "%.2fx", module.mapScale()),
            width / 2, 20, 0xFFA5A5B2);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() == 0 && module.mapContains(event.x(), event.y())) {
            dragging = true;
            grabX = event.x() - module.mapLeft();
            grabY = event.y() - module.mapTop();

            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (dragging) {
            module.moveMap(event.x() - grabX, event.y() - grabY);
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(Click event) {
        dragging = false;

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;

        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean fine = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        module.scaleMap(Math.signum(scrollY) * (fine ? 0.02 : 0.1));

        return true;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        int step = event.hasShift() ? 10 : 1;

        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> module.moveMap(module.mapLeft() - step, module.mapTop());
            case GLFW.GLFW_KEY_RIGHT -> module.moveMap(module.mapLeft() + step, module.mapTop());
            case GLFW.GLFW_KEY_UP -> module.moveMap(module.mapLeft(), module.mapTop() - step);
            case GLFW.GLFW_KEY_DOWN -> module.moveMap(module.mapLeft(), module.mapTop() + step);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> module.scaleMap(0.05);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> module.scaleMap(-0.05);
            case GLFW.GLFW_KEY_R -> module.resetLayout();
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
