package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.nathan.modules.ChatMacros;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.KeyUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat Macros, simply: one row a macro, the command or message it sends and the key it is on, typed
 * and bound right there. "+ Command" adds a row, the minus takes one away, the dots open the full
 * editor for steps, waits and cooldowns. Under them, chat feedback, the cancel key and whether the
 * module is on at all.
 */
public final class ChatMacroScreen extends Screen {
    private static final float ROW = 24, ROW_GAP = 5;
    /** The cancel key, when it is the one being bound. */
    private static final Object CANCEL = new Object();

    private final Screen parent;
    private final ChatMacros module;

    /** The macro whose text is being typed, and what is waiting for a key press, if anything. */
    private ChatMacros.Macro focused;
    private Object listening;

    private float scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run(double mx, double my, int button);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float px, py, pw, ph, rowsY, rowsH;

    public ChatMacroScreen(Screen parent, ChatMacros module) {
        super(Text.literal("Chat Macros"));
        this.parent = parent;
        this.module = module;
    }

    // ---- the macros ---------------------------------------------------------------------------

    private static String textOf(ChatMacros.Macro macro) {
        List<String> steps = macro.steps.get();
        return steps.isEmpty() ? "" : steps.getFirst();
    }

    /** The row's text is the macro's first step; any further steps stay as they are. */
    public void setText(ChatMacros.Macro macro, String text) {
        List<String> steps = new ArrayList<>(macro.steps.get());
        if (steps.isEmpty()) steps.add(text);
        else steps.set(0, text);
        macro.steps.set(steps);
        macro.name.set(text.isBlank() ? "New macro" : text.trim());
    }

    /** Adds an empty row and puts the cursor in it. */
    public ChatMacros.Macro addMacro() {
        ChatMacros.Macro macro = new ChatMacros.Macro();
        macro.steps.set(new ArrayList<>(List.of("")));
        module.macros.add(macro);
        focused = macro;
        listening = null;
        scrollTarget = Float.MAX_VALUE;
        return macro;
    }

    public void removeMacro(ChatMacros.Macro macro) {
        module.macros.remove(macro);
        if (focused == macro) focused = null;
        if (listening == macro) listening = null;
    }

    /** Starts listening for the macro's key; for tests as well as clicks. */
    public void listenFor(ChatMacros.Macro macro) {
        focused = null;
        listening = macro;
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        hits.clear();
        Theme.update();

        List<ChatMacros.Macro> macros = module.macros;
        float rowsContent = Math.max(1, macros.size()) * (ROW + ROW_GAP);
        pw = Math.min(470, width - 24);
        float fixed = 58 + 6 + 26 + 18 + 3 * 22 + 40;
        rowsH = Math.min(rowsContent, Math.max(ROW + ROW_GAP, height - 24 - fixed));
        ph = fixed + rowsH;
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);
        rowsY = py + 58;

        float open = Easing.outCubic(Math.min(1f, (now - openedAt) / 1e9f / 0.18f));
        Render2D.setAlpha(open);
        Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
        Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 20, 0x90000000);
        Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
        Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
        Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));

        Fonts.drawV(ctx, "CHAT MACROS", px + 16, py + 19, Theme.accent(), true, 0.95f);
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, "Each command has its own key. Press + Command to add another.", px + 16, py + 34, Theme.TEXT_MUTED, false, 0.62f);
        } finally {
            Fonts.endRaw();
        }
        Render2D.rect(ctx, px + 16, py + 46, pw - 32, 1, 0x14FFFFFF);

        // The rows, scrolling if there are more than fit.
        float maxScroll = Math.max(0, rowsContent - rowsH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        float x = px + 16, w = pw - 32;
        if (macros.isEmpty()) {
            Fonts.beginRaw();
            try {
                Fonts.drawV(ctx, "No macros yet. Add one below, type /rtp or a message, then click its key box.",
                        x + 2, rowsY + ROW / 2f, Theme.TEXT_MUTED, false, 0.62f);
            } finally {
                Fonts.endRaw();
            }
        } else {
            Render2D.clip(ctx, Math.round(px), Math.round(rowsY - 2), Math.round(px + pw), Math.round(rowsY + rowsH));
            float y = rowsY - scroll;
            for (ChatMacros.Macro macro : new ArrayList<>(macros)) {
                if (y + ROW >= rowsY && y <= rowsY + rowsH) row(ctx, macro, x, y, w - (maxScroll > 0 ? 6 : 0), mx, my, now);
                y += ROW + ROW_GAP;
            }
            Render2D.unclip(ctx);
            if (maxScroll > 0) {
                float bar = Math.max(16, rowsH * rowsH / rowsContent);
                Render2D.roundRect(ctx, px + pw - 18, rowsY + (rowsH - bar) * (scroll / maxScroll), 3, bar, 1.5f, 0x40FFFFFF);
            }
        }

        // + Command
        float y = rowsY + rowsH + 6;
        boolean addHover = inside(mx, my, x, y, w, 22);
        Render2D.roundRect(ctx, x, y, w, 22, 6, addHover ? 0xFF232838 : 0xFF1A1E29);
        Render2D.roundOutline(ctx, x, y, w, 22, 6, 1, addHover ? Theme.accent(0xA0) : 0x1EFFFFFF);
        Fonts.drawCentered(ctx, "+ Command", x + w / 2f, y + 11, addHover ? Theme.TEXT : Theme.TEXT_DIM, true, 0.7f);
        hit(x, y, w, 22, (hx, hy, b) -> {
            addMacro();
            Sounds.click();
        });
        y += 22 + 14;

        // Options, under a divider.
        float label = Fonts.width("OPTIONS", true, 0.58f);
        Render2D.rect(ctx, x, y, (w - label) / 2f - 8, 1, 0x14FFFFFF);
        Render2D.rect(ctx, x + (w + label) / 2f + 8, y, (w - label) / 2f - 8, 1, 0x14FFFFFF);
        Fonts.drawCentered(ctx, "OPTIONS", x + w / 2f, y, Theme.TEXT_MUTED, true, 0.58f);
        y += 10;

        optionToggle(ctx, "Chat Feedback", "A line over the hotbar when a macro runs", module.feedbackOn(), x, y, w, mx, my,
                () -> module.setFeedback(!module.feedbackOn()));
        y += 22;
        Fonts.drawV(ctx, "Cancel Key", x + 2, y + 10, Theme.TEXT, false, 0.7f);
        keyBox(ctx, CANCEL, module.cancelKey(), x + w - 64, y + 2, 64, 18, mx, my, now, null);
        y += 22;
        optionToggle(ctx, "Active", "Chat Macros on; off, no key sends anything", module.isEnabled(), x, y, w, mx, my, module::toggle);
        y += 26;

        float dw = 64, dx = px + pw - 16 - dw;
        boolean doneHover = inside(mx, my, dx, y, dw, 20);
        Render2D.roundRect(ctx, dx, y, dw, 20, 10, doneHover ? Theme.accent() : Theme.accent(0xD0));
        Fonts.drawCentered(ctx, "Done", dx + dw / 2f, y + 10, 0xFFFFFFFF, true, 0.68f);
        hit(dx, y, dw, 20, (hx, hy, b) -> close());
        Render2D.setAlpha(1f);
    }

    /** One macro: on/off, its text, its key, the full editor and remove. */
    private void row(DrawContext ctx, ChatMacros.Macro macro, float x, float y, float w, int mx, int my, long now) {
        float top = Math.max(y, rowsY), bottom = Math.min(y + ROW, rowsY + rowsH);
        boolean on = macro.enabled.get();

        // On/off: a dot, lit when on.
        float dotX = x + 7, cy = y + ROW / 2f;
        Render2D.circle(ctx, dotX, cy, 5, on ? Theme.accent() : 0xFF2A2F3D);
        if (on) Render2D.circle(ctx, dotX, cy, 2, 0xFFFFFFFF);
        clipped(x, top, bottom, 15, (hx, hy, b) -> {
            macro.enabled.set(!macro.enabled.get());
            Sounds.click();
        });

        // Remove, full editor, key, then the text gets the rest.
        float rx = x + w - 20;
        boolean overRemove = inside(mx, my, rx, y, 20, ROW);
        Render2D.roundRect(ctx, rx, y, 20, ROW, 5, overRemove ? 0x40E5484D : 0xFF1A1E29);
        Render2D.rect(ctx, rx + 6, cy - 0.7f, 8, 1.4f, overRemove ? Theme.RED : 0xFFE5484D);
        clipped(rx, top, bottom, 20, (hx, hy, b) -> {
            removeMacro(macro);
            Sounds.click();
        });

        float ex = rx - 24;
        boolean overEdit = inside(mx, my, ex, y, 20, ROW);
        Render2D.roundRect(ctx, ex, y, 20, ROW, 5, overEdit ? 0xFF262B3A : 0xFF1A1E29);
        for (int i = -1; i <= 1; i++) Render2D.circle(ctx, ex + 10 + i * 4, cy, 1.2f, overEdit ? Theme.TEXT : Theme.TEXT_MUTED);
        clipped(ex, top, bottom, 20, (hx, hy, b) -> {
            Sounds.click();
            // The full editor: steps, waits, cooldown, server and ask-first. It comes back here when closed.
            client.setScreen(new dev.maro.nathan.gui.ChatMacroScreens.Editor(new GuiTheme(), module, macro, null));
        });

        float kw = 58, kx = ex - 4 - kw;
        keyBox(ctx, macro, macro.keybind.get(), kx, y, kw, ROW, mx, my, now, module.conflict(macro));

        float tx = x + 18, tw = kx - 4 - tx;
        boolean editing = focused == macro;
        boolean overText = inside(mx, my, tx, y, tw, ROW);
        Render2D.roundRect(ctx, tx, y, tw, ROW, 5, 0xFF0C0E14);
        Render2D.roundOutline(ctx, tx, y, tw, ROW, 5, 1, editing ? Theme.accent(0xC0) : overText ? 0x30FFFFFF : 0x18FFFFFF);
        String text = textOf(macro);
        int extra = Math.max(0, macro.steps.get().size() - 1);
        Fonts.beginRaw();
        try {
            float room = tw - 14 - (extra > 0 ? 26 : 0);
            if (text.isEmpty() && !editing) {
                Fonts.drawV(ctx, "/rtp or a message", tx + 7, cy, 0xFF545A6C, false, 0.72f);
            } else {
                String shown = text;
                // While typing, the end stays in view; otherwise the start does.
                if (editing) while (shown.length() > 1 && Fonts.width(shown, false, 0.72f) > room) shown = shown.substring(1);
                else shown = Fonts.trim(shown, room, false, 0.72f);
                int color = text.startsWith("/") ? 0xFFF2F4F8 : 0xFFD6DAE4;
                Fonts.drawV(ctx, shown, tx + 7, cy, on ? color : Theme.TEXT_MUTED, false, 0.72f);
                if (editing && (now / 500_000_000L) % 2 == 0) {
                    Render2D.rect(ctx, tx + 8 + Fonts.width(shown, false, 0.72f), y + 6, 1, ROW - 12, Theme.TEXT_DIM);
                }
            }
            if (extra > 0) Fonts.drawRight(ctx, "+" + extra, tx + tw - 7, cy, Theme.accent(), true, 0.6f);
        } finally {
            Fonts.endRaw();
        }
        clipped(tx, top, bottom, tw, (hx, hy, b) -> {
            focused = macro;
            listening = null;
        });
    }

    /** A key box: the key's name, "Press a key" while listening, red when another macro has it. */
    private void keyBox(DrawContext ctx, Object owner, Keybind bind, float x, float y, float w, float h, int mx, int my, long now,
                        String conflict) {
        boolean waiting = listening == owner;
        boolean hover = inside(mx, my, x, y, w, h);
        float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 1e9 * 6) : 0;
        int bg = waiting ? ColorUtil.lerp(0xFF1A1E29, Theme.accent(0xFF), 0.25f + 0.2f * pulse) : hover ? 0xFF262B3A : 0xFF1A1E29;
        Render2D.roundRect(ctx, x, y, w, h, 5, bg);
        int edge = waiting ? Theme.accent() : conflict != null ? Theme.RED : 0x1EFFFFFF;
        Render2D.roundOutline(ctx, x, y, w, h, 5, 1, edge);
        String label = waiting ? "Press…" : bind.isSet() ? KeyUtil.name(bind.getValue()) : "None";
        Fonts.beginRaw();
        try {
            label = Fonts.trim(label, w - 8, true, 0.66f);
            int color = waiting ? 0xFFFFFFFF : bind.isSet() ? (conflict != null ? Theme.RED : Theme.TEXT) : Theme.TEXT_MUTED;
            Fonts.drawCentered(ctx, label, x + w / 2f, y + h / 2f, color, bind.isSet() || waiting, 0.66f);
        } finally {
            Fonts.endRaw();
        }
        Action action = (hx, hy, button) -> {
            focused = null;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                bind(owner, Keybind.none());
                listening = null;
            } else {
                listening = waiting ? null : owner;
            }
            Sounds.click();
        };
        if (owner == CANCEL) hit(x, y, w, h, action);
        else {
            float top = Math.max(y, rowsY), bottom = Math.min(y + h, rowsY + rowsH);
            if (bottom > top) hit(x, top, w, bottom - top, action);
        }
    }

    private void optionToggle(DrawContext ctx, String name, String description, boolean value, float x, float y, float w,
                              int mx, int my, Runnable flip) {
        boolean hover = inside(mx, my, x, y, w, 20);
        Fonts.drawV(ctx, name, x + 2, y + 10, Theme.TEXT, false, 0.7f);
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, description, x + 6 + Fonts.width(name, false, 0.7f) + 4, y + 10, Theme.TEXT_MUTED, false, 0.56f);
        } finally {
            Fonts.endRaw();
        }
        float sw = 24, sh = 13, sx = x + w - sw, sy = y + 10 - sh / 2f;
        Render2D.roundRect(ctx, sx, sy, sw, sh, sh / 2f, value ? Theme.accent() : hover ? 0xFF323849 : 0xFF2A2F3D);
        Render2D.circle(ctx, value ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, sh / 2f - 2, 0xFFFFFFFF);
        hit(x, y, w, 20, (hx, hy, b) -> {
            flip.run();
            Sounds.click();
        });
    }

    private void bind(Object owner, Keybind key) {
        if (owner == CANCEL) module.setCancelKey(key);
        else if (owner instanceof ChatMacros.Macro macro) macro.keybind.set(key);
    }

    private void clipped(float x, float top, float bottom, float w, Action action) {
        if (bottom > top) hit(x, top, w, bottom - top, action);
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        // Listening for a key: a side or middle mouse button is the key; anything else stops listening.
        if (listening != null && button > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            bind(listening, Keybind.fromButton(button));
            listening = null;
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                hit.action().run(click.x(), click.y(), button);
                return true;
            }
        }
        focused = null;
        listening = null;
        if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) close();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 24;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focused == null || input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if (c < 32 || c == 127 || c == '§') return true;
        String text = textOf(focused);
        if (text.length() < 256) setText(focused, text + c);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (listening != null) {
            // Escape clears the key; any other key becomes it.
            bind(listening, key == GLFW.GLFW_KEY_ESCAPE ? Keybind.none() : Keybind.fromKey(key));
            listening = null;
            Sounds.click();
            return true;
        }
        if (focused != null) {
            String text = textOf(focused);
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (ctrl) {
                        String trimmed = text.stripTrailing();
                        int cut = trimmed.lastIndexOf(' ');
                        setText(focused, cut < 0 ? "" : trimmed.substring(0, cut + 1));
                    } else if (!text.isEmpty()) {
                        setText(focused, text.substring(0, text.length() - 1));
                    }
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrl && client != null) {
                        String paste = client.keyboard.getClipboard().replace("\n", " ").replace("\r", "");
                        setText(focused, (text + paste).substring(0, Math.min(256, text.length() + paste.length())));
                    }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ESCAPE -> focused = null;
                default -> {
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
