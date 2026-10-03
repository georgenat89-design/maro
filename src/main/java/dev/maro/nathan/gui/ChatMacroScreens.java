package dev.maro.nathan.gui;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.WindowScreen;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.containers.WTable;
import dev.maro.runtime.gui.widgets.containers.WVerticalList;
import dev.maro.runtime.gui.widgets.input.WTextBox;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.gui.widgets.pressable.WCheckbox;
import dev.maro.runtime.gui.widgets.pressable.WMinus;
import net.minecraft.client.MinecraftClient;
import dev.maro.nathan.modules.ChatMacros;

/**
 * The macro manager and the macro editor.
 *
 * <p>Both are Meteor windows made of Meteor's widgets, drawn by whatever theme
 * Meteor is using, so they are the client's own charcoal and purple, its own
 * text boxes and its own scrolling, and not an imitation of them.
 */
public final class ChatMacroScreens {
    private ChatMacroScreens() {
    }

    /** The list: search, one compact row a macro, and what can be done to each. */
    public static final class Manager extends WindowScreen {
        private final ChatMacros module;

        private String search = "";
        private WVerticalList rows;

        public Manager(GuiTheme theme, ChatMacros module) {
            super(theme, "Chat Macros");

            this.module = module;
        }

        @Override
        public void initWidgets() {
            WHorizontalList top = add(theme.horizontalList()).expandX().widget();

            WTextBox box = top.add(theme.textBox(search, "Search by name, message or key...")).expandX().minWidth(260).widget();

            box.action = () -> {
                search = box.get();
                fill();
            };

            WButton add = top.add(theme.button("New Macro")).widget();

            add.action = () -> {
                ChatMacros.Macro macro = new ChatMacros.Macro();

                module.macros.add(macro);
                MinecraftClient.getInstance().setScreen(new Editor(theme, module, macro, this::fill));
            };

            add(theme.horizontalSeparator()).expandX();

            // Only this part is rebuilt as you type, so the search box keeps
            // the cursor.
            rows = add(theme.verticalList()).expandX().widget();

            fill();
        }

        private void fill() {
            if (rows == null) return;

            rows.clear();

            String wanted = search.trim().toLowerCase(Locale.ROOT);
            boolean searching = !wanted.isEmpty();
            int shown = 0;

            WTable table = rows.add(theme.table()).expandX().widget();

            for (int i = 0; i < module.macros.size(); i++) {
                ChatMacros.Macro macro = module.macros.get(i);
                String key = macro.keybind.get().isSet() ? macro.keybind.get().toString() : "no key";

                if (searching && !(macro.name.get() + " " + String.join(" ", macro.steps.get()) + " " + key).toLowerCase(Locale.ROOT).contains(wanted)) continue;

                shown++;

                int index = i;

                WCheckbox enabled = table.add(theme.checkbox(macro.enabled.get())).widget();
                enabled.action = () -> macro.enabled.set(enabled.checked);

                // Name, with the key as a badge after it.
                table.add(theme.label(macro.name.get()));
                table.add(theme.label("[" + key + "]"));

                // What it says, cut to fit the row, and how much more there is.
                String preview = macro.preview();
                int extra = macro.steps.get().size() - 1;

                if (preview.length() > 34) preview = preview.substring(0, 33) + "...";
                if (extra > 0) preview += "  +" + extra;

                table.add(theme.label(preview)).expandCellX();

                String conflict = module.conflict(macro);
                table.add(theme.label(conflict.isEmpty() ? "" : "! key shared with " + conflict));

                WButton edit = table.add(theme.button("Edit")).widget();
                edit.action = () -> MinecraftClient.getInstance().setScreen(new Editor(theme, module, macro, this::fill));

                WButton copy = table.add(theme.button("Duplicate")).widget();

                copy.action = () -> {
                    module.macros.add(index + 1, macro.duplicate());
                    fill();
                };

                // Order is what the list is sorted by, so it is only changed
                // while the whole list is on show.
                if (!searching) {
                    WButton up = table.add(theme.button("Up")).widget();

                    up.action = () -> {
                        if (index > 0) Collections.swap(module.macros, index, index - 1);
                        fill();
                    };

                    WButton down = table.add(theme.button("Down")).widget();

                    down.action = () -> {
                        if (index < module.macros.size() - 1) Collections.swap(module.macros, index, index + 1);
                        fill();
                    };
                }

                WMinus delete = table.add(theme.minus()).widget();

                delete.action = () -> {
                    module.macros.remove(macro);
                    fill();
                };

                table.row();
            }

            if (shown == 0) {
                rows.add(theme.label(module.macros.isEmpty()
                    ? "No macros yet. New Macro makes one."
                    : "Nothing matches \"" + search.trim() + "\"."));
            }
        }
    }

    /** One macro: Meteor's own editor for its settings, the placeholders, and the preview. */
    public static final class Editor extends WindowScreen {
        private final ChatMacros module;
        private final ChatMacros.Macro macro;
        private final Runnable whenClosed;

        private WVerticalList preview;

        public Editor(GuiTheme theme, ChatMacros module, ChatMacros.Macro macro, Runnable whenClosed) {
            super(theme, "Edit Macro");

            this.module = module;
            this.macro = macro;
            this.whenClosed = whenClosed;
        }

        @Override
        public void initWidgets() {
            // Name, enabled, key, steps, and Advanced shut underneath.
            add(theme.settings(macro.settings)).expandX();

            add(theme.horizontalSeparator("Placeholders")).expandX();

            // A text box does not say where its cursor is, so these cannot type
            // into a step for you; they put the placeholder on the clipboard,
            // to be pasted where it is wanted.
            WHorizontalList holders = add(theme.horizontalList()).expandX().widget();

            for (String holder : ChatMacros.PLACEHOLDERS) {
                WButton copy = holders.add(theme.button(holder)).widget();

                copy.action = () -> {
                    MinecraftClient.getInstance().keyboard.setClipboard(holder);
                    copy.set("copied");
                };
            }

            add(theme.label("Click one to copy it, then paste it into a step. \"" + ChatMacros.WAIT + " 1500\" as a step waits 1.5 seconds."));

            add(theme.horizontalSeparator("Preview")).expandX();

            WButton test = add(theme.button("Test Preview - sends nothing")).expandX().widget();
            test.action = this::showPreview;

            preview = add(theme.verticalList()).expandX().widget();

            String conflict = module.conflict(macro);
            if (!conflict.isEmpty()) add(theme.label("! This key is also used by " + conflict + "."));
        }

        /** Exactly what would be sent, line for line, with the placeholders as they stand right now. */
        private void showPreview() {
            preview.clear();

            List<String> lines = module.testPreview(macro);

            for (int i = 0; i < lines.size(); i++) preview.add(theme.label((i + 1) + ".  " + lines.get(i)));
        }

        @Override
        public void close() {
            super.close();

            if (whenClosed != null) whenClosed.run();
        }
    }
}
