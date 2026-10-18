// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.tpa;

import dev.maro.mixin.AccessorOrderDialogScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import dev.maro.anubis.tpa.ServerText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.dialog.type.ColumnsDialog;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.dialog.type.SimpleDialog;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.ItemDialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.input.BooleanInputControl;
import net.minecraft.dialog.input.InputControl;
import net.minecraft.dialog.input.TextInputControl;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.action.DialogAction;
import net.minecraft.text.ObjectTextContent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TextColor;

@Environment(value=EnvType.CLIENT)
final class Dialogs {
    private Dialogs() {
    }

    static View current(MinecraftClient mc) {
        Screen class_4372 = mc.currentScreen;
        if (!(class_4372 instanceof DialogScreen)) {
            return null;
        }
        DialogScreen screen = (DialogScreen)class_4372;
        Dialog dialog = ((AccessorOrderDialogScreen)screen).anubis$dialog();
        if (dialog == null) {
            return null;
        }
        ArrayList<DialogActionButtonData> raw = new ArrayList<>();
        if (dialog instanceof MultiActionDialog) {
            MultiActionDialog multi = (MultiActionDialog)dialog;
            raw.addAll(multi.actions());
        }
        if (dialog instanceof SimpleDialog) {
            SimpleDialog simple = (SimpleDialog)dialog;
            raw.addAll(simple.getButtons());
        }
        if (dialog instanceof ColumnsDialog) {
            ColumnsDialog list = (ColumnsDialog)dialog;
            list.exitAction().ifPresent(raw::add);
        }
        ArrayList<Button> buttons = new ArrayList<Button>(raw.size());
        for (DialogActionButtonData button : raw) {
            Text label = button.data().label();
            String text = Dialogs.visibleText(label);
            buttons.add(new Button(button, ServerText.normalize(text), ServerText.key(text), label));
        }
        ArrayList<String> body = new ArrayList<String>();
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        for (DialogBody part : dialog.common().body()) {
            if (part instanceof PlainMessageDialogBody) {
                PlainMessageDialogBody message2 = (PlainMessageDialogBody)part;
                body.add(ServerText.normalize(Dialogs.visibleText(message2.contents())));
            }
            if (!(part instanceof ItemDialogBody)) continue;
            ItemDialogBody item = (ItemDialogBody)part;
            items.add(item.item());
            item.description().ifPresent(message -> body.add(ServerText.normalize(Dialogs.visibleText(message.contents()))));
        }
        ArrayList<String> textInputs = new ArrayList<String>();
        for (DialogInput input : dialog.common().inputs()) {
            if (!(input.control() instanceof TextInputControl)) continue;
            textInputs.add(input.key());
        }
        return new View(dialog, ServerText.normalize(Dialogs.visibleText(dialog.common().title())), buttons, body, textInputs, items);
    }

    static String visibleText(Text component) {
        StringBuilder out = new StringBuilder();
        Dialogs.appendVisible(component, out);
        return out.toString();
    }

    private static void appendVisible(Text component, StringBuilder out) {
        if (!(component.getContent() instanceof ObjectTextContent)) {
            component.getContent().visit(text -> {
                out.append(text);
                return Optional.empty();
            });
        }
        for (Text sibling : component.getSiblings()) {
            Dialogs.appendVisible(sibling, out);
        }
    }

    static boolean press(MinecraftClient mc, View view, Button button, Map<String, String> text) {
        Screen class_4372 = mc.currentScreen;
        if (!(class_4372 instanceof DialogScreen)) {
            return false;
        }
        DialogScreen screen = (DialogScreen)class_4372;
        if (((AccessorOrderDialogScreen)screen).anubis$dialog() != view.dialog()) {
            return false;
        }
        HashMap<String, DialogAction.ValueGetter> values = new HashMap<String, DialogAction.ValueGetter>();
        for (DialogInput input : view.dialog().common().inputs()) {
            InputControl class_114382 = input.control();
            if (class_114382 instanceof TextInputControl) {
                TextInputControl textInput = (TextInputControl)class_114382;
                values.put(input.key(), DialogAction.ValueGetter.of((String)text.getOrDefault(input.key(), textInput.initial())));
                continue;
            }
            class_114382 = input.control();
            if (!(class_114382 instanceof BooleanInputControl)) continue;
            BooleanInputControl bool = (BooleanInputControl)class_114382;
            values.put(input.key(), Dialogs.booleanValue(bool));
        }
        Optional event = button.raw().action().flatMap(action -> action.createClickEvent(values));
        screen.runAction(event);
        return true;
    }

    static void close(MinecraftClient mc) {
        Screen class_4372 = mc.currentScreen;
        if (class_4372 instanceof DialogScreen) {
            DialogScreen screen = (DialogScreen)class_4372;
            screen.close();
        }
    }

    private static DialogAction.ValueGetter booleanValue(final BooleanInputControl input) {
        final boolean value = input.initial();
        return new DialogAction.ValueGetter(){

            public String get() {
                return value ? input.onTrue() : input.onFalse();
            }

            public NbtElement getAsNbt() {
                return NbtByte.of((boolean)value);
            }
        };
    }

    private static boolean isGreen(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return g >= 192 && r < 144 && b < 144;
    }

    @Environment(value=EnvType.CLIENT)
    record Button(DialogActionButtonData raw, String label, String key, Text component) {
        boolean green() {
            boolean[] found = new boolean[]{false};
            this.component.visit((style, text) -> {
                TextColor color = style.getColor();
                if (!text.isBlank() && color != null && Dialogs.isGreen(color.getRgb())) {
                    found[0] = true;
                }
                return Optional.empty();
            }, Style.EMPTY);
            return found[0];
        }
    }

    @Environment(value=EnvType.CLIENT)
    record View(Dialog dialog, String title, List<Button> buttons, List<String> body, List<String> textInputs, List<ItemStack> items) {
        Button find(Predicate<Button> test) {
            for (Button button : this.buttons) {
                if (!test.test(button)) continue;
                return button;
            }
            return null;
        }

        Button labelled(String ... words) {
            for (String word : words) {
                String wanted = ServerText.key(word);
                Button exact = this.find(button -> button.key().equals(wanted));
                if (exact == null) continue;
                return exact;
            }
            for (String word : words) {
                String wanted = ServerText.key(word);
                Button partial = this.find(button -> button.key().contains(wanted));
                if (partial == null) continue;
                return partial;
            }
            return null;
        }

        boolean titled(String text) {
            return this.title.contains(text);
        }

        String describe() {
            ArrayList<String> labels = new ArrayList<String>();
            for (Button button : this.buttons) {
                if (!button.label().isBlank()) {
                    labels.add(button.label());
                }
                if (labels.size() < 12) continue;
                break;
            }
            return "'" + this.title + "' buttons: " + (labels.isEmpty() ? "(none)" : String.join((CharSequence)", ", labels));
        }
    }
}
