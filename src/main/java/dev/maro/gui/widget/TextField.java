package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Single line text input with cursor, word deletion and clipboard support. */
public class TextField {
    private final String placeholder;
    private final int maxLength;
    private String text = "";
    private int cursor;
    private int scrollStart;
    private long lastInput = System.currentTimeMillis();
    private IntPredicate filter = c -> true;
    private Consumer<String> onChange = s -> {
    };
    private Runnable onEnter = () -> {
    };

    public TextField(String placeholder, int maxLength) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
    }

    public TextField filter(IntPredicate filter) {
        this.filter = filter;
        return this;
    }

    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextField onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text.length() > maxLength ? text.substring(0, maxLength) : text;
        cursor = this.text.length();
        scrollStart = 0;
        onChange.accept(this.text);
    }

    public void clear() {
        setText("");
    }

    public void render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, float h, Icons.Icon icon, String hint) {
        boolean focused = gui.focused == this;
        boolean hovered = gui.hovered(x, y, w, h);
        float f = Anims.of(this, "focus", focused);
        float hv = Anims.of(this, "hover", hovered);
        float r = Math.min(h / 2f, Theme.radius());

        if (f > 0.01f && Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 5f, Theme.accent(Math.round(0x40 * f)));
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.INPUT, 0xFF191822, hv * 0.6f));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, 0xFF3A374B, hv), Theme.accent(0xC0), f));

        float tx = x + 7;
        if (icon != null) {
            icon.draw(ctx, x + 10, y + h / 2f, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), f), f);
            tx = x + 19;
        }
        float right = x + w - 6;
        if (hint != null && text.isEmpty()) {
            float hw = Fonts.width(hint, false, 0.7f) + 8;
            float hx = x + w - hw - 4;
            Render2D.roundRect(ctx, hx, y + h / 2f - 5, hw, 10, 3, 0xFF22202C);
            Render2D.roundOutline(ctx, hx, y + h / 2f - 5, hw, 10, 3, 1f, Theme.BORDER);
            Fonts.drawCentered(ctx, hint, hx + hw / 2f, y + h / 2f, Theme.TEXT_MUTED, false, 0.7f);
            right = hx - 4;
        }
        float avail = Math.max(10f, right - tx);
        float cy = y + h / 2f;

        if (text.isEmpty()) {
            Fonts.drawV(ctx, Fonts.trim(placeholder, avail, false, 0.85f), tx, cy, Theme.TEXT_MUTED, false, 0.85f);
        } else {
            // keep the cursor visible by scrolling the start index
            if (cursor < scrollStart) scrollStart = cursor;
            while (scrollStart < cursor && Fonts.width(text.substring(scrollStart, cursor), false, 0.85f) > avail) scrollStart++;
            String visible = text.substring(scrollStart);
            visible = clipRight(visible, avail);
            Fonts.drawV(ctx, visible, tx, cy, Theme.TEXT, false, 0.85f);
        }
        if (focused && (System.currentTimeMillis() - lastInput) % 1000 < 550) {
            float cx = tx + Fonts.width(text.substring(Math.min(scrollStart, cursor), cursor), false, 0.85f);
            Render2D.rect(ctx, cx, cy - 4.5f, Math.max(0.6f, Render2D.px()), 9f, Theme.accent());
        }

        final float textX = tx;
        gui.hit(x, y, w, h, (button, mx, my) -> {
            gui.focused = this;
            lastInput = System.currentTimeMillis();
            // place the cursor where the user clicked
            float local = (float) mx - textX;
            int idx = scrollStart;
            while (idx < text.length() && Fonts.width(text.substring(scrollStart, idx + 1), false, 0.85f) < local) idx++;
            cursor = Math.max(0, Math.min(text.length(), idx));
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) clear();
        });
    }

    private static String clipRight(String s, float avail) {
        int end = s.length();
        while (end > 0 && Fonts.width(s.substring(0, end), false, 0.85f) > avail) end--;
        return s.substring(0, end);
    }

    public boolean keyPressed(int key, int mods) {
        lastInput = System.currentTimeMillis();
        boolean ctrl = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor == 0) return true;
                int start = ctrl ? wordStart(cursor) : cursor - 1;
                text = text.substring(0, start) + text.substring(cursor);
                cursor = start;
                onChange.accept(text);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor >= text.length()) return true;
                text = text.substring(0, cursor) + text.substring(cursor + 1);
                onChange.accept(text);
            }
            case GLFW.GLFW_KEY_LEFT -> cursor = ctrl ? wordStart(cursor) : Math.max(0, cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> cursor = ctrl ? wordEnd(cursor) : Math.min(text.length(), cursor + 1);
            case GLFW.GLFW_KEY_HOME -> cursor = 0;
            case GLFW.GLFW_KEY_END -> cursor = text.length();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onEnter.run();
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) insert(MinecraftClient.getInstance().keyboard.getClipboard().replaceAll("[\\r\\n\\t]", ""));
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) MinecraftClient.getInstance().keyboard.setClipboard(text);
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) cursor = text.length();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean charTyped(char c) {
        if (c < 32 || c == 127) return false;
        insert(String.valueOf(c));
        return true;
    }

    private void insert(String s) {
        StringBuilder ok = new StringBuilder();
        for (char c : s.toCharArray()) if (c >= 32 && c != 127 && filter.test(c)) ok.append(c);
        if (ok.isEmpty()) return;
        String add = ok.toString();
        int room = maxLength - text.length();
        if (room <= 0) return;
        if (add.length() > room) add = add.substring(0, room);
        text = text.substring(0, cursor) + add + text.substring(cursor);
        cursor += add.length();
        lastInput = System.currentTimeMillis();
        onChange.accept(text);
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        return i;
    }
}
