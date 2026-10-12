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
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.KeyUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows the keys you choose on screen, lit while held. You choose them in a menu that is a picture of
 * your keyboard (right-click the module, or "Choose Keys"): click a key to put it on the HUD, click it
 * again to take it off. Keys are physical, so it shows what you press whatever the game binds it to,
 * and a key the game holds for you (Auto Walk's forward) lights as well.
 *
 * <p>Two layouts. Compact tidies what you picked: W / A S D in their classic shape, the arrows the same,
 * mouse buttons paired, space along the bottom, the rest in rows of three. Keyboard keeps the keys exactly
 * where they sit on a real keyboard, cropped to the ones you picked.
 */
public class KeyboardStrokes extends Module implements HudElement {
    public static final float KEY = 22, GAP = 3, PITCH = KEY + GAP;
    /** Three keys across, which is also the width of a space bar or a pair of mouse buttons in the compact layout. */
    public static final float WIDTH = KEY * 3 + GAP * 2;
    private static final float HALF = (WIDTH - GAP) / 2f;
    private static final float RADIUS = 5;
    private static final int MARGIN = 4;
    private static final int LMB = KeyUtil.mouse(0), RMB = KeyUtil.mouse(1), MMB = KeyUtil.mouse(2);
    private static final String DEFAULT_KEYS = GLFW.GLFW_KEY_W + "," + GLFW.GLFW_KEY_A + "," + GLFW.GLFW_KEY_S + "," + GLFW.GLFW_KEY_D
            + "," + GLFW.GLFW_KEY_SPACE + "," + LMB + "," + RMB;

    // ---- the keyboard picture -------------------------------------------------------------------

    /** A key on the picture: its code (a GLFW key, or {@link KeyUtil#mouse}), what it says, and where it sits in key widths. */
    public record KeyDef(int code, String label, float col, float row, float w) {
    }

    /** The picture is this many key widths wide and tall. */
    public static final float BOARD_W = 18.5f, BOARD_H = 7.75f;
    public static final List<KeyDef> KEYS = buildKeys();
    private static final Map<Integer, KeyDef> BY_CODE = new HashMap<>();

    static {
        for (KeyDef k : KEYS) BY_CODE.put(k.code(), k);
    }

    private static List<KeyDef> buildKeys() {
        List<KeyDef> k = new ArrayList<>();
        k.add(new KeyDef(GLFW.GLFW_KEY_ESCAPE, "ESC", 0, 0, 1));
        float[] fx = {2, 3, 4, 5, 6.5f, 7.5f, 8.5f, 9.5f, 11, 12, 13, 14};
        for (int i = 0; i < 12; i++) k.add(new KeyDef(GLFW.GLFW_KEY_F1 + i, "F" + (i + 1), fx[i], 0, 1));

        k.add(new KeyDef(GLFW.GLFW_KEY_GRAVE_ACCENT, "`", 0, 1.25f, 1));
        for (int i = 1; i <= 10; i++) k.add(new KeyDef(GLFW.GLFW_KEY_0 + i % 10, String.valueOf(i % 10), i, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_MINUS, "-", 11, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_EQUAL, "=", 12, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_BACKSPACE, "BKSP", 13, 1.25f, 2));

        k.add(new KeyDef(GLFW.GLFW_KEY_TAB, "TAB", 0, 2.25f, 1.5f));
        String top = "QWERTYUIOP";
        for (int i = 0; i < top.length(); i++) k.add(new KeyDef(top.charAt(i), String.valueOf(top.charAt(i)), 1.5f + i, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT_BRACKET, "[", 11.5f, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT_BRACKET, "]", 12.5f, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_BACKSLASH, "\\", 13.5f, 2.25f, 1.5f));

        k.add(new KeyDef(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS", 0, 3.25f, 1.75f));
        String home = "ASDFGHJKL";
        for (int i = 0; i < home.length(); i++) k.add(new KeyDef(home.charAt(i), String.valueOf(home.charAt(i)), 1.75f + i, 3.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_SEMICOLON, ";", 10.75f, 3.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_APOSTROPHE, "'", 11.75f, 3.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_ENTER, "ENTER", 12.75f, 3.25f, 2.25f));

        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT_SHIFT, "SHIFT", 0, 4.25f, 2.25f));
        String bottom = "ZXCVBNM";
        for (int i = 0; i < bottom.length(); i++) k.add(new KeyDef(bottom.charAt(i), String.valueOf(bottom.charAt(i)), 2.25f + i, 4.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_COMMA, ",", 9.25f, 4.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_PERIOD, ".", 10.25f, 4.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_SLASH, "/", 11.25f, 4.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT_SHIFT, "SHIFT", 12.25f, 4.25f, 2.75f));

        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT_CONTROL, "CTRL", 0, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT_SUPER, "WIN", 1.25f, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT_ALT, "ALT", 2.5f, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_SPACE, "SPACE", 3.75f, 5.25f, 6.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT_ALT, "ALT", 10, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT_SUPER, "WIN", 11.25f, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_MENU, "MENU", 12.5f, 5.25f, 1.25f));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT_CONTROL, "CTRL", 13.75f, 5.25f, 1.25f));

        k.add(new KeyDef(GLFW.GLFW_KEY_INSERT, "INS", 15.5f, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_HOME, "HOME", 16.5f, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_PAGE_UP, "PGUP", 17.5f, 1.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_DELETE, "DEL", 15.5f, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_END, "END", 16.5f, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_PAGE_DOWN, "PGDN", 17.5f, 2.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_UP, "UP", 16.5f, 4.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_LEFT, "LEFT", 15.5f, 5.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_DOWN, "DOWN", 16.5f, 5.25f, 1));
        k.add(new KeyDef(GLFW.GLFW_KEY_RIGHT, "RIGHT", 17.5f, 5.25f, 1));

        k.add(new KeyDef(LMB, "LMB", 0, 6.75f, 1.5f));
        k.add(new KeyDef(MMB, "MMB", 1.6f, 6.75f, 1.5f));
        k.add(new KeyDef(RMB, "RMB", 3.2f, 6.75f, 1.5f));
        k.add(new KeyDef(KeyUtil.mouse(3), "M4", 4.8f, 6.75f, 1.5f));
        k.add(new KeyDef(KeyUtil.mouse(4), "M5", 6.4f, 6.75f, 1.5f));
        return List.copyOf(k);
    }

    // ---- settings -------------------------------------------------------------------------------

    private final ButtonSetting chooseKeys = add(new ButtonSetting("Choose Keys", "Open a picture of your keyboard and click the keys to show", "Open",
            () -> mc.setScreen(new KeyboardStrokesScreen(mc.currentScreen, this))));
    private final ModeSetting layout = add(new ModeSetting("Layout", "Compact tidies the keys you picked; Keyboard keeps them where they sit on a real keyboard",
            "Compact", "Compact", "Keyboard"));
    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag it where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 2, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 38, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big it is", 1, 0.5, 3, 0.05).suffix("x"));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the keys are", 80, 0, 100, 1).suffix("%"));
    /** The chosen keys, as key codes joined by commas. Edited in the picker, not the settings box. */
    private final TextSetting keys = add(new TextSetting("Keys", "", DEFAULT_KEYS, 1000, "").visible(() -> false));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Keys", chooseKeys, layout),
            SettingSection.of("Look", opacity),
            SettingSection.of("Placement", position, x, y, scale));

    private final Map<Integer, Animation> glow = new HashMap<>();

    /** A key drawn at (x, y) in the layout, w wide and KEY tall. */
    public record Cell(KeyDef key, float x, float y, float w) {
    }

    private List<Cell> cached = List.of();
    private String cachedFor = null;
    private float cachedWidth, cachedHeight;

    private String chosenFor = null;
    private Set<Integer> chosenCache = Set.of();

    private Map<Integer, List<KeyBinding>> bound = Map.of();
    private long boundAt;

    public KeyboardStrokes() {
        super("Keyboard Strokes", "Shows the keys you press on screen. Right-click it to pick the keys from a picture of your keyboard", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    public Screen panel(Screen parent) {
        return new KeyboardStrokesScreen(parent, this);
    }

    public ModeSetting layoutSetting() {
        return layout;
    }

    // ---- which keys ---------------------------------------------------------------------------

    /** The code of the key a binding is on: a GLFW key, or a mouse button as {@link KeyUtil#mouse}. */
    public static int codeOf(KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        return key.getCategory() == InputUtil.Type.MOUSE ? KeyUtil.mouse(key.getCode()) : key.getCode();
    }

    /** Parses the saved list. Plain numbers are key codes; an older "key.forward" style entry means the key that is on it now. */
    private Set<Integer> chosen() {
        String now = keys.get();
        if (now.equals(chosenFor)) return chosenCache;
        Set<Integer> out = new LinkedHashSet<>();
        for (String token : now.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(token));
            } catch (NumberFormatException e) {
                for (KeyBinding b : mc.options.allKeys) if (b.getId().equals(token)) out.add(codeOf(b));
            }
        }
        chosenFor = now;
        chosenCache = out;
        return out;
    }

    private void save(Set<Integer> set) {
        StringBuilder sb = new StringBuilder();
        for (int code : set) sb.append(sb.isEmpty() ? "" : ",").append(code);
        keys.set(sb.toString());
    }

    public boolean isShown(int code) {
        return chosen().contains(code);
    }

    public void toggleKey(int code) {
        Set<Integer> set = new LinkedHashSet<>(chosen());
        if (!set.remove(code)) set.add(code);
        save(set);
    }

    public void resetKeys() {
        keys.set(DEFAULT_KEYS);
    }

    public void clearKeys() {
        keys.set("");
    }

    /** The keys your controls are on right now: movement, jump, sneak, sprint, attack and use. */
    public void useGameKeys() {
        var o = mc.options;
        Set<Integer> set = new LinkedHashSet<>();
        for (KeyBinding b : new KeyBinding[] {o.forwardKey, o.leftKey, o.backKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey}) {
            set.add(codeOf(b));
        }
        save(set);
    }

    public int shownCount() {
        return chosen().size();
    }

    /** What the game does with a key right now, for the picker's hover line; empty if nothing. */
    public String actionFor(int code) {
        List<String> names = new ArrayList<>();
        for (KeyBinding b : bindingsFor(code)) names.add(I18n.translate(b.getId()));
        return String.join(", ", names);
    }

    public List<KeyBinding> bindingsFor(int code) {
        long now = System.nanoTime();
        if (now - boundAt > 500_000_000L) {
            Map<Integer, List<KeyBinding>> map = new HashMap<>();
            for (KeyBinding b : mc.options.allKeys) map.computeIfAbsent(codeOf(b), k -> new ArrayList<>()).add(b);
            bound = map;
            boundAt = now;
        }
        return bound.getOrDefault(code, List.of());
    }

    // ---- layout -------------------------------------------------------------------------------

    private static int kind(int code) {
        if (code == LMB || code == RMB || code == MMB) return 1;
        if (code == GLFW.GLFW_KEY_SPACE) return 2;
        return 0;
    }

    private static final int[] WASD = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D};
    private static final int[] ARROWS = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT};

    /** Top key over a row of left, middle and right: the shape W / A S D takes, and the arrows. Returns the next free row. */
    private float cluster(List<Cell> cells, Set<Integer> chosen, int[] c, float row) {
        boolean top = chosen.contains(c[0]);
        boolean base = chosen.contains(c[1]) || chosen.contains(c[2]) || chosen.contains(c[3]);
        if (top) {
            cells.add(new Cell(BY_CODE.get(c[0]), PITCH, row, KEY));
            row += PITCH;
        }
        if (base) {
            for (int i = 1; i <= 3; i++) if (chosen.contains(c[i])) cells.add(new Cell(BY_CODE.get(c[i]), (i - 1) * PITCH, row, KEY));
            row += PITCH;
        }
        return row;
    }

    private void layout() {
        String now = layout.get() + "|" + keys.get();
        if (now.equals(cachedFor)) return;
        cachedFor = now;
        Set<Integer> chosen = chosen();
        List<Cell> cells = new ArrayList<>();
        if (layout.is("Keyboard")) keyboardLayout(cells, chosen);
        else compactLayout(cells, chosen);
        float w = 0, h = 0;
        for (Cell c : cells) {
            w = Math.max(w, c.x() + c.w());
            h = Math.max(h, c.y() + KEY);
        }
        cached = cells;
        cachedWidth = w;
        cachedHeight = h;
    }

    /** The keys where they sit on a real keyboard, cropped to the ones chosen. */
    private void keyboardLayout(List<Cell> cells, Set<Integer> chosen) {
        float minCol = Float.MAX_VALUE, minRow = Float.MAX_VALUE;
        List<KeyDef> picked = new ArrayList<>();
        for (KeyDef k : KEYS) {
            if (!chosen.contains(k.code())) continue;
            picked.add(k);
            minCol = Math.min(minCol, k.col());
            minRow = Math.min(minRow, k.row());
        }
        for (KeyDef k : picked) cells.add(new Cell(k, (k.col() - minCol) * PITCH, (k.row() - minRow) * PITCH, k.w() * PITCH - GAP));
    }

    /** W / A S D and the arrows in their shapes, then the other keys in rows of three, mouse pairs, then space. */
    private void compactLayout(List<Cell> cells, Set<Integer> chosen) {
        float row = cluster(cells, chosen, WASD, 0);
        row = cluster(cells, chosen, ARROWS, row);
        List<KeyDef> rest = new ArrayList<>();
        for (KeyDef k : KEYS) {
            if (!chosen.contains(k.code())) continue;
            boolean clustered = false;
            for (int c : WASD) clustered |= c == k.code();
            for (int c : ARROWS) clustered |= c == k.code();
            if (!clustered) rest.add(k);
        }
        rest.sort((a, b) -> Integer.compare(kind(a.code()), kind(b.code())));
        float cx = 0;
        int prev = -1;
        for (KeyDef k : rest) {
            int kind = kind(k.code());
            float w = kind == 0 ? KEY : kind == 1 ? HALF : WIDTH;
            // A new kind starts a row, so mouse buttons pair up and space runs on its own.
            if (cx > 0 && (kind != prev || cx + w > WIDTH + 0.01f)) {
                row += PITCH;
                cx = 0;
            }
            cells.add(new Cell(k, cx, row, w));
            cx += w + GAP;
            prev = kind;
        }
    }

    public List<Cell> cells() {
        layout();
        return cached;
    }

    public float layoutWidth() {
        layout();
        return cachedWidth;
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
        return Math.max(KEY, layoutWidth()) * hudScale();
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

    // ---- drawing ------------------------------------------------------------------------------

    /** Whether the key is down: held on the keyboard or mouse, or held for you by the game. Nothing while a screen is open. */
    private boolean down(int code) {
        if (mc.currentScreen != null || !mc.isWindowFocused()) return false;
        long window = mc.getWindow().getHandle();
        boolean raw = KeyUtil.isMouse(code)
                ? GLFW.glfwGetMouseButton(window, code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        if (raw) return true;
        for (KeyBinding b : bindingsFor(code)) if (b.isPressed()) return true;
        return false;
    }

    /** Draws a label centred, shrunk to fit if it is wider than maxWidth. Call inside the same font mode it is measured in. */
    public static void fitted(DrawContext ctx, String text, float cx, float cy, float maxWidth, int color, float scale) {
        float width = Fonts.width(text, true, scale);
        Fonts.drawCentered(ctx, text, cx, cy, color, true, width > maxWidth ? scale * maxWidth / width : scale);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden || dev.maro.module.impl.misc.InventoryHider.hidesHotbar()) return;
        List<Cell> cells = cells();
        if (cells.isEmpty()) return;
        float s = hudScale();
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float strength = opacity.getFloat() / 100f;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(hudLeft(), hudTop());
        matrices.scale(s, s);
        Fonts.beginRaw();
        try {
            for (Cell cell : cells) cell(ctx, cell, down(cell.key().code()), hair, strength);
        } finally {
            Fonts.endRaw();
            matrices.popMatrix();
        }
    }

    private void cell(DrawContext ctx, Cell c, boolean down, float hair, float strength) {
        float t = glow.computeIfAbsent(c.key().code(), k -> new Animation(26f, 0f)).update(down ? 1f : 0f);
        float inset = 1.2f * t;
        float x = c.x() + inset, y = c.y() + inset, w = c.w() - inset * 2, h = KEY - inset * 2;
        int idle = ColorUtil.withAlpha(0xFF0B0D12, Math.round(235 * strength));
        int lit = Theme.accent(0xE6);
        Render2D.roundRect(ctx, x, y, w, h, RADIUS, ColorUtil.lerp(idle, lit, t));
        Render2D.roundOutline(ctx, x, y, w, h, RADIUS, hair, ColorUtil.lerp(0x1CFFFFFF, Theme.accent(0xFF), t));
        fitted(ctx, c.key().label(), c.x() + c.w() / 2f, c.y() + KEY / 2f, c.w() - 4, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t), 0.55f);
    }
}
