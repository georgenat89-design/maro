package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.hud.KeyboardStrokesScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows the keys you hold, lit while they are down. You choose which of the game's keybinds appear
 * in a menu of their own (right-click the module, or "Choose Keys"). It reads the bindings rather
 * than fixed keys, so it follows whatever you have rebound and labels each cell with the key that is
 * really on it. Movement keys sit in the classic W / A S D shape; mouse buttons pair up; jump runs
 * along the bottom; everything else flows in rows of three underneath.
 */
public class KeyboardStrokes extends Module implements HudElement {
    public static final float KEY = 22, GAP = 3;
    /** Three keys across, which is also the width of a jump bar or a pair of mouse buttons. */
    public static final float WIDTH = KEY * 3 + GAP * 2;
    private static final float HALF = (WIDTH - GAP) / 2f;
    private static final float RADIUS = 5;
    private static final int MARGIN = 4;
    private static final String DEFAULT_KEYS = "key.forward,key.left,key.back,key.right,key.jump,key.attack,key.use";

    private final ButtonSetting chooseKeys = add(new ButtonSetting("Choose Keys", "Pick which keybinds are shown", "Open",
            () -> mc.setScreen(new KeyboardStrokesScreen(mc.currentScreen, this))));
    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag it where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 2, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 38, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big it is", 1, 0.5, 3, 0.05).suffix("x"));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the keys are", 80, 0, 100, 1).suffix("%"));
    /** The chosen keybinds, as their translation keys joined by commas. Edited in the menu, not the settings box. */
    private final TextSetting keys = add(new TextSetting("Keys", "", DEFAULT_KEYS, 2000, "").visible(() -> false));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Keys", chooseKeys),
            SettingSection.of("Look", opacity),
            SettingSection.of("Placement", position, x, y, scale));

    private final Map<String, Animation> glow = new HashMap<>();

    /** A key drawn at (x, y) in the layout, w wide and KEY tall. */
    public record Cell(KeyBinding binding, float x, float y, float w) {
    }

    private List<Cell> cached = List.of();
    private String cachedFor = null;
    private float cachedHeight;

    public KeyboardStrokes() {
        super("Keyboard Strokes", "Shows the keys you press on screen. Right-click it to choose which keybinds appear", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    public Screen panel(Screen parent) {
        return new KeyboardStrokesScreen(parent, this);
    }

    // ---- which keys ---------------------------------------------------------------------------

    public KeyBinding[] allBindings() {
        return mc.options.allKeys;
    }

    private Set<String> chosen() {
        Set<String> out = new LinkedHashSet<>();
        for (String id : keys.get().split(",")) if (!id.isBlank()) out.add(id.trim());
        return out;
    }

    public boolean isShown(KeyBinding binding) {
        return chosen().contains(binding.getId());
    }

    public void toggleKey(KeyBinding binding) {
        Set<String> set = chosen();
        if (!set.remove(binding.getId())) set.add(binding.getId());
        keys.set(String.join(",", set));
    }

    public void resetKeys() {
        keys.set(DEFAULT_KEYS);
    }

    public void clearKeys() {
        keys.set("");
    }

    public int shownCount() {
        return chosen().size();
    }

    // ---- layout -------------------------------------------------------------------------------

    /** Wide keys that start a row of their own: 0 normal, 1 half (mouse), 2 full-width. */
    private static int kind(KeyBinding b) {
        String id = b.getId();
        if (id.equals("key.attack") || id.equals("key.use") || id.equals("key.pickItem")) return 1;
        if (id.equals("key.jump")) return 2;
        return 0;
    }

    private void layout() {
        String now = keys.get();
        if (now.equals(cachedFor)) return;
        cachedFor = now;
        Set<String> chosen = chosen();
        KeyBinding forward = null, left = null, back = null, right = null;
        List<KeyBinding> rest = new ArrayList<>();
        // In the game's own order, so the layout never depends on the order you clicked in.
        for (KeyBinding b : allBindings()) {
            if (!chosen.contains(b.getId())) continue;
            switch (b.getId()) {
                case "key.forward" -> forward = b;
                case "key.left" -> left = b;
                case "key.back" -> back = b;
                case "key.right" -> right = b;
                default -> rest.add(b);
            }
        }
        List<Cell> cells = new ArrayList<>();
        float row = 0;
        if (forward != null) {
            cells.add(new Cell(forward, KEY + GAP, row, KEY));
            row += KEY + GAP;
        }
        if (left != null || back != null || right != null) {
            if (left != null) cells.add(new Cell(left, 0, row, KEY));
            if (back != null) cells.add(new Cell(back, KEY + GAP, row, KEY));
            if (right != null) cells.add(new Cell(right, (KEY + GAP) * 2, row, KEY));
            row += KEY + GAP;
        }
        float cx = 0;
        int prev = -1;
        for (KeyBinding b : rest) {
            int kind = kind(b);
            float w = kind == 0 ? KEY : kind == 1 ? HALF : WIDTH;
            // A new kind starts a row, so mouse buttons pair up and jump runs on its own.
            if (cx > 0 && (kind != prev || cx + w > WIDTH + 0.01f)) {
                row += KEY + GAP;
                cx = 0;
            }
            cells.add(new Cell(b, cx, row, w));
            cx += w + GAP;
            prev = kind;
        }
        if (cx > 0) row += KEY + GAP;
        cached = cells;
        cachedHeight = Math.max(0, row - GAP);
    }

    public List<Cell> cells() {
        layout();
        return cached;
    }

    public float layoutHeight() {
        layout();
        return cachedHeight;
    }

    // ---- placement ----------------------------------------------------------------------------

    @Override
    public String hudName() {
        return getName();
    }

    @Override
    public float hudScale() {
        return scale.getFloat();
    }

    @Override
    public float hudWidth() {
        return WIDTH * hudScale();
    }

    @Override
    public float hudHeight() {
        return Math.max(KEY, layoutHeight()) * hudScale();
    }

    private float roomX() {
        return Math.max(0, mc.getWindow().getScaledWidth() - hudWidth() - MARGIN * 2);
    }

    private float roomY() {
        return Math.max(0, mc.getWindow().getScaledHeight() - hudHeight() - MARGIN * 2);
    }

    @Override
    public float hudLeft() {
        return MARGIN + Math.round(roomX() * x.getFloat() / 100f);
    }

    @Override
    public float hudTop() {
        return MARGIN + Math.round(roomY() * y.getFloat() / 100f);
    }

    @Override
    public void hudMove(float left, float top) {
        float rx = roomX(), ry = roomY();
        x.set(rx <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (left - MARGIN) / rx * 100.0)));
        y.set(ry <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (top - MARGIN) / ry * 100.0)));
    }

    @Override
    public void hudResize(float by) {
        scale.set(Math.max(scale.getMin(), Math.min(scale.getMax(), Math.round((scale.get() + by) * 100) / 100.0)));
    }

    @Override
    public void hudReset() {
        x.reset();
        y.reset();
        scale.reset();
    }

    // ---- labels -------------------------------------------------------------------------------

    /** What goes on a cell: the key that is really bound, short enough to fit. */
    public static String label(KeyBinding b) {
        String id = b.getBoundKeyTranslationKey();
        switch (id) {
            case "key.mouse.left": return "LMB";
            case "key.mouse.right": return "RMB";
            case "key.mouse.middle": return "MMB";
            case "key.keyboard.space": return "SPACE";
            case "key.keyboard.left.shift": return "SHIFT";
            case "key.keyboard.right.shift": return "RSHIFT";
            case "key.keyboard.left.control": return "CTRL";
            case "key.keyboard.right.control": return "RCTRL";
            case "key.keyboard.left.alt": return "ALT";
            case "key.keyboard.right.alt": return "RALT";
            case "key.keyboard.tab": return "TAB";
            case "key.keyboard.caps.lock": return "CAPS";
            case "key.keyboard.unknown": return "-";
            default: break;
        }
        if (id.startsWith("key.mouse.")) return "M" + id.substring("key.mouse.".length());
        return b.getBoundKeyLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden || dev.maro.module.impl.misc.InventoryHider.hidesHotbar()) return;
        draw(ctx, hudLeft(), hudTop(), hudScale(), true);
    }

    /** Draws the chosen keys with their top-left corner at (left, top); live lights the ones held down. */
    public void draw(DrawContext ctx, float left, float top, float s, boolean live) {
        List<Cell> cells = cells();
        if (cells.isEmpty()) return;
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float strength = opacity.getFloat() / 100f;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(left, top);
        matrices.scale(s, s);
        Fonts.beginRaw();
        try {
            for (Cell cell : cells) cell(ctx, cell, live && cell.binding().isPressed(), hair, strength);
        } finally {
            Fonts.endRaw();
            matrices.popMatrix();
        }
    }

    private void cell(DrawContext ctx, Cell c, boolean down, float hair, float strength) {
        float t = glow.computeIfAbsent(c.binding().getId(), k -> new Animation(26f, 0f)).update(down ? 1f : 0f);
        float inset = 1.2f * t;
        float x = c.x() + inset, y = c.y() + inset, w = c.w() - inset * 2, h = KEY - inset * 2;
        int idle = ColorUtil.withAlpha(0xFF0B0D12, Math.round(235 * strength));
        int lit = Theme.accent(0xE6);
        Render2D.roundRect(ctx, x, y, w, h, RADIUS, ColorUtil.lerp(idle, lit, t));
        Render2D.roundOutline(ctx, x, y, w, h, RADIUS, hair, ColorUtil.lerp(0x1CFFFFFF, Theme.accent(0xFF), t));
        String text = Fonts.trim(label(c.binding()), w - 4, true, 0.55f);
        Fonts.drawCentered(ctx, text, c.x() + c.w() / 2f, c.y() + KEY / 2f, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t), true, 0.55f);
    }
}
