package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.hud.KeyboardStrokesScreen;
import dev.maro.gui.hud.KeyboardArrangeScreen;
import com.google.gson.JsonObject;
import dev.maro.gui.hud.KeyboardLayout;
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
import dev.maro.util.KeyUtil;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows the keys you hold, lit while they are down. You choose which of the game's keybinds appear
 * in a menu of their own (right-click the module, or "Choose Keys"). It reads the bindings rather
 * than fixed keys, so it follows whatever you have rebound and labels each cell with the key that is
 * really on it. Selected keyboard keys keep the chooser's physical positions, with mouse buttons
 * grouped below. The bounds follow the selection, including wide and tall keys.
 */
public class KeyboardStrokes extends Module implements HudElement {
    public static final float KEY = 22, GAP = 3;
    /** Reference width of a pair of mouse buttons. Keyboard bounds follow the selected keys. */
    public static final float WIDTH = KEY * 3 + GAP * 2;
    private static final float HALF = (WIDTH - GAP) / 2f;
    private static final float RADIUS = 5;
    private static final int MARGIN = 4;
    private static final String DEFAULT_KEYS = "key.forward,key.left,key.back,key.right,key.jump,key.attack,key.use";

    private final ButtonSetting chooseKeys = add(new ButtonSetting("Choose Keys", "Pick which keybinds are shown", "Open",
            () -> mc.setScreen(new KeyboardStrokesScreen(mc.currentScreen, this))));
    private final ButtonSetting arrange = add(new ButtonSetting("Arrange Keys", "Drag individual keys into your own HUD layout", "Edit",
            () -> mc.setScreen(new KeyboardArrangeScreen(mc.currentScreen, this))));
    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag it where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 2, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 38, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big it is", 1, 0.5, 3, 0.05).suffix("x"));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the keys are", 80, 0, 100, 1).suffix("%"));
    /** The chosen keybinds, as their translation keys joined by commas. Edited in the menu, not the settings box. */
    private final TextSetting keys = add(new TextSetting("Keys", "", DEFAULT_KEYS, 2000, "").visible(() -> false));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Keys", chooseKeys, arrange),
            SettingSection.of("Look", opacity),
            SettingSection.of("Placement", position, x, y, scale));

    public record KeyPosition(float x, float y) { }
    private final Map<String, KeyPosition> positions = new HashMap<>();
    private long layoutRevision, cachedRevision = -1;
    public boolean customLayout() { return !positions.isEmpty(); }
    public void resetLayout() { positions.clear(); layoutRevision++; }
    /** Capture all current positions before the first move, so moving one key cannot move its neighbors. */
    public void moveKey(String id, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        var cells = cells();
        if (cells.stream().noneMatch(c -> c.id().equals(id))) return;
        for (Cell cell : cells) positions.putIfAbsent(cell.id(), new KeyPosition(cell.x(), cell.y()));
        positions.put(id, new KeyPosition(Math.clamp(x, 0, 1200), Math.clamp(y, 0, 600)));
        layoutRevision++;
    }
    @Override public JsonObject saveExtra() {
        var data = new JsonObject(); var keys = new JsonObject();
        positions.forEach((id,p) -> { var v=new JsonObject();v.addProperty("x",p.x());v.addProperty("y",p.y());keys.add(id,v); });
        data.add("keyPositions", keys); return data;
    }
    @Override public void loadExtra(JsonObject data) {
        positions.clear(); layoutRevision++;
        if (!data.has("keyPositions") || !data.get("keyPositions").isJsonObject()) return;
        for (var e : data.getAsJsonObject("keyPositions").entrySet()) try {
            if (positions.size() >= 300 || !e.getKey().matches("(key\\.[a-zA-Z0-9_.-]+|physical:[0-9]+)")) continue;
            var v=e.getValue().getAsJsonObject();float x=v.get("x").getAsFloat(),y=v.get("y").getAsFloat();
            if (Float.isFinite(x)&&Float.isFinite(y)&&x>=0&&x<=1200&&y>=0&&y<=600) positions.put(e.getKey(),new KeyPosition(x,y));
        } catch (RuntimeException ignored) { }
    }

    private final Map<String, Animation> glow = new HashMap<>();

    /** A selected physical key, or a fallback cell for an action without an ANSI position. */
    public record Cell(KeyBinding binding, int code, float x, float y, float w, float h) {
        public String id() { return binding != null ? binding.getId() : "physical:" + code; }
    }

    private List<Cell> cached = List.of();
    private String cachedFor = null;
    private long cachedBindings;
    private Set<Integer> physicalKeys = Set.of();
    private float cachedWidth, cachedHeight;
    private String chosenFor;
    private Set<String> chosenCache = Set.of();
    private final Set<Integer> heldPhysical = new java.util.HashSet<>();

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
        if (keys.get().equals(chosenFor)) return chosenCache;
        Set<String> out = new LinkedHashSet<>();
        for (String id : keys.get().split(",")) if (!id.isBlank()) out.add(id.trim());
        chosenFor = keys.get(); chosenCache = Set.copyOf(out);
        return chosenCache;
    }

    public boolean isShown(KeyBinding binding) {
        return chosen().contains(binding.getId());
    }

    public void toggleKey(KeyBinding binding) {
        Set<String> set = new LinkedHashSet<>(chosen());
        if (!set.remove(binding.getId())) set.add(binding.getId());
        keys.set(String.join(",", set));
    }

    /** Physical keyboard positions map to existing actions; unused keys can be displayed too. */
    public static int code(KeyBinding binding) {
        var key = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding);
        return key.getCategory() == InputUtil.Type.MOUSE ? KeyUtil.mouse(key.getCode())
                : key.getCategory() == InputUtil.Type.KEYSYM ? key.getCode() : KeyUtil.NONE;
    }
    public Set<Integer> physicalKeys() { layout(); return physicalKeys; }
    public boolean isPhysicalShown(int code) { return physicalKeys().contains(code); }
    public void togglePhysical(int code) {
        Set<String> set = new LinkedHashSet<>(chosen());
        String raw = "physical:" + code;
        if (isPhysicalShown(code)) {
            set.remove(raw);
            for (KeyBinding binding : allBindings()) if (code(binding) == code) set.remove(binding.getId());
        } else {
            KeyBinding match = null;
            for (KeyBinding binding : allBindings()) if (code(binding) == code) { match = binding; break; }
            set.add(match == null ? raw : match.getId());
        }
        keys.set(String.join(",", set));
    }
    public void physicalInput(int code, int action) {
        if (action == GLFW.GLFW_RELEASE) heldPhysical.remove(code);
        else if (action == GLFW.GLFW_PRESS && isEnabled()) heldPhysical.add(code);
    }
    @Override protected void onDisable() { heldPhysical.clear(); }
    @Override public void onTick() { if (!mc.isWindowFocused() || mc.currentScreen != null) heldPhysical.clear(); }
    public boolean physicalDown(int code) {
        if (code == KeyUtil.NONE || !mc.isWindowFocused() || mc.currentScreen != null) return false;
        if (heldPhysical.contains(code)) return true;
        return KeyUtil.isMouse(code) ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow(), code);
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

    private void layout() {
        String now = keys.get();
        Set<String> chosen = chosen();
        KeyBinding[] bindings = allBindings();
        long signature = 1;
        for (KeyBinding b : bindings) if (chosen.contains(b.getId())) {
            signature = 31 * signature + code(b);
            signature = 31 * signature + System.identityHashCode(b);
        }
        if (now.equals(cachedFor) && signature == cachedBindings && cachedRevision == layoutRevision) return;
        cachedFor = now; cachedBindings = signature; cachedRevision = layoutRevision;
        Map<Integer, KeyBinding> selected = new LinkedHashMap<>();
        List<Cell> extras = new ArrayList<>();
        for (KeyBinding b : bindings) {
            if (!chosen.contains(b.getId())) continue;
            int code = code(b);
            if (code == KeyUtil.NONE) extras.add(new Cell(b, code, 0, 0, KEY, KEY));
            else selected.putIfAbsent(code, b);
        }
        chosen.stream().filter(id -> id.startsWith("physical:")).map(id -> {
            try { return Integer.parseInt(id.substring(9)); } catch (NumberFormatException ignored) { return KeyUtil.NONE; }
        }).filter(code -> code >= GLFW.GLFW_KEY_SPACE && code <= GLFW.GLFW_KEY_LAST
                || code >= KeyUtil.MOUSE_OFFSET && code < KeyUtil.MOUSE_OFFSET + 8).sorted().forEach(code -> {
            if (!selected.containsKey(code)) selected.put(code, null);
        });
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = 0, maxY = 0;
        for (var entry : selected.entrySet()) {
            if (KeyUtil.isMouse(entry.getKey())) continue;
            var cap = KeyboardLayout.key(entry.getKey());
            if (cap == null) { extras.add(new Cell(entry.getValue(), entry.getKey(), 0, 0, KEY, KEY)); continue; }
            minX = Math.min(minX, cap.x()); minY = Math.min(minY, cap.y());
            maxX = Math.max(maxX, cap.x() + cap.w()); maxY = Math.max(maxY, cap.y() + cap.h());
        }
        float unit = KEY / 30;
        float keyboardW = Float.isFinite(minX) ? (maxX - minX) * unit : 0;
        float keyboardH = Float.isFinite(minY) ? (maxY - minY) * unit : 0;
        List<Integer> mouse = selected.keySet().stream().filter(KeyUtil::isMouse).sorted().toList();
        float mouseW = mouse.isEmpty() ? 0 : Math.min(3, mouse.size()) * (HALF + GAP) - GAP;
        float extrasW = extras.isEmpty() ? 0 : Math.min(3, extras.size()) * (KEY + GAP) - GAP;
        cachedWidth = Math.max(keyboardW, Math.max(mouseW, extrasW));
        List<Cell> cells = new ArrayList<>();
        for (var cap : KeyboardLayout.keys()) if (!KeyUtil.isMouse(cap.code()) && selected.containsKey(cap.code())) {
            cells.add(new Cell(selected.get(cap.code()), cap.code(),
                    (cap.x() - minX) * unit + (cachedWidth - keyboardW) / 2, (cap.y() - minY) * unit,
                    cap.w() * unit, cap.h() * unit));
        }
        float row = keyboardH;
        if (!mouse.isEmpty()) {
            if (row > 0) row += GAP * 2;
            for (int i = 0; i < mouse.size(); i++) {
                int columns = Math.min(3, mouse.size() - i / 3 * 3);
                float width = columns * (HALF + GAP) - GAP;
                int code = mouse.get(i);
                cells.add(new Cell(selected.get(code), code, (cachedWidth - width) / 2 + i % 3 * (HALF + GAP),
                        row + i / 3 * (KEY + GAP), HALF, KEY));
            }
            row += ((mouse.size() + 2) / 3) * (KEY + GAP) - GAP;
        }
        if (!extras.isEmpty()) {
            if (row > 0) row += GAP * 2;
            for (int i = 0; i < extras.size(); i++) {
                int columns = Math.min(3, extras.size() - i / 3 * 3);
                Cell extra = extras.get(i);
                cells.add(new Cell(extra.binding(), extra.code(), (cachedWidth - (columns * (KEY + GAP) - GAP)) / 2
                        + i % 3 * (KEY + GAP), row + i / 3 * (KEY + GAP), KEY, KEY));
            }
            row += ((extras.size() + 2) / 3) * (KEY + GAP) - GAP;
        }
        physicalKeys = Set.copyOf(selected.keySet());
        cachedHeight = row;
        if (!positions.isEmpty()) {
            List<Cell> arranged = new ArrayList<>(); cachedWidth = cachedHeight = 0;
            for (Cell c : cells) {
                KeyPosition p = positions.get(c.id());
                Cell placed = p == null ? c : new Cell(c.binding(),c.code(),p.x(),p.y(),c.w(),c.h());
                arranged.add(placed);cachedWidth=Math.max(cachedWidth,placed.x()+placed.w());cachedHeight=Math.max(cachedHeight,placed.y()+placed.h());
            }
            cells=arranged;
        }
        cached = List.copyOf(cells);
    }

    public List<Cell> cells() {
        layout();
        return cached;
    }

    public float layoutHeight() {
        layout();
        return cachedHeight;
    }

    public float layoutWidth() {
        layout();
        return cachedWidth;
    }

    // ---- placement ----------------------------------------------------------------------------

    @Override
    public String hudName() {
        return getName();
    }

    @Override
    public float hudScale() {
        float fitX = Math.max(1, mc.getWindow().getScaledWidth() - MARGIN * 2) / Math.max(KEY, layoutWidth());
        float fitY = Math.max(1, mc.getWindow().getScaledHeight() - MARGIN * 2) / Math.max(KEY, layoutHeight());
        return Math.min(scale.getFloat(), Math.min(fitX, fitY));
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

    public static String label(int code) {
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) return "KP" + (code - GLFW.GLFW_KEY_KP_0);
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "WIN";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "RWIN";
            case KeyUtil.MOUSE_OFFSET -> "LMB";
            case KeyUtil.MOUSE_OFFSET + 1 -> "RMB";
            case KeyUtil.MOUSE_OFFSET + 2 -> "MMB";
            default -> KeyUtil.name(code).toUpperCase(Locale.ROOT);
        };
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
        float hair = Render2D.hairline(ctx, 0.5f * s) / s;
        float strength = opacity.getFloat() / 100f;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(left, top);
        matrices.scale(s, s);
        Fonts.beginRaw();
        try {
            for (Cell cell : cells) cell(ctx, cell, live && ((cell.binding() != null && cell.binding().isPressed()) || physicalDown(cell.code())), hair, strength);
        } finally {
            Fonts.endRaw();
            matrices.popMatrix();
        }
    }

    private void cell(DrawContext ctx, Cell c, boolean down, float hair, float strength) {
        float t = glow.computeIfAbsent(c.id(), k -> new Animation(26f, 0f)).update(down ? 1f : 0f);
        float inset = 1.2f * t;
        float x = c.x() + inset, y = c.y() + inset, w = c.w() - inset * 2, h = c.h() - inset * 2;
        int idle = ColorUtil.withAlpha(0xFF0B0D12, Math.round(235 * strength));
        int lit = Theme.accent(0xE6);
        Render2D.roundRect(ctx, x, y, w, h, RADIUS, ColorUtil.lerp(idle, lit, t));
        Render2D.roundOutline(ctx, x, y, w, h, RADIUS, hair, ColorUtil.lerp(0x1CFFFFFF, Theme.accent(0xFF), t));
        String text = Fonts.trim(c.binding() != null ? label(c.binding()) : label(c.code()), w - 4, true, 0.55f);
        Fonts.drawCentered(ctx, text, c.x() + c.w() / 2f, c.y() + c.h() / 2f, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t), true, 0.55f);
    }
}
