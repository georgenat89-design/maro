package dev.maro.module.impl.misc;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Presentation-only alias: profiles, UUIDs, commands and outgoing chat keep their real identity. */
public final class NameChange extends Module {
    private static NameChange instance;
    private final TextSetting alias = add(new TextSetting("Name", "Your display alias on this screen", "Maro", 24, "Your new name"));
    private final ModeSetting style = add(new ModeSetting("Style", "Color treatment for your alias", "Gradient", "Gradient", "Solid", "Rainbow", "Plain"));
    private final ColorSetting primary = add(new ColorSetting("Color", "First alias color", 0xFFB477FF).visible(() -> !style.is("Plain")));
    private final ColorSetting accent = add(new ColorSetting("Accent", "Second alias color", 0xFF4BE8FF).visible(() -> style.is("Gradient")));
    private final BooleanSetting bold = add(new BooleanSetting("Bold", "Give your alias a stronger weight", true));
    private final BooleanSetting tags = add(new BooleanSetting("Name Tags", "Use your alias above your player and in Maro Nametags", true));
    private final BooleanSetting tab = add(new BooleanSetting("Tab List", "Use your alias in your own tab-list entry", true));
    private final BooleanSetting chat = add(new BooleanSetting("Chat", "Replace your name in incoming chat on your screen", true));

    public NameChange() {
        super("Name Change", "Give yourself a local display alias with gradient, rainbow and solid colors", Category.MISC);
        instance = this;
    }
    @Override public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Alias", alias, style, primary, accent, bold), SettingSection.of("Show In", tags, tab, chat));
    }
    private static boolean active() { return instance != null && instance.isEnabled() && !instance.alias.get().isBlank(); }
    public static boolean owns(UUID id) { return active() && mc.player != null && mc.player.getUuid().equals(id); }
    public static Text nameTag(UUID id, Text original) { return owns(id) && instance.tags.get() ? instance.replaceOrAlias(original) : original; }
    public static Text tabName(UUID id, Text original) { return owns(id) && instance.tab.get() ? instance.replaceOrAlias(original) : original; }
    public static Text chatText(Text original) { return active() && instance.chat.get() ? instance.replace(original) : original; }
    public static String label(PlayerEntity player) {
        return owns(player.getUuid()) && instance.tags.get() ? instance.alias.get().trim() : player.getName().getString();
    }
    private Text replaceOrAlias(Text original) {
        Text replaced = replace(original);
        return replaced == original ? styledAlias(original.getStyle()) : replaced;
    }
    private Text styledAlias(Style inherited) {
        String name = alias.get().trim();
        MutableText result = Text.empty();
        int count = name.codePointCount(0, name.length()), index = 0;
        for (int at = 0; at < name.length();) {
            int cp = name.codePointAt(at);
            double t = count < 2 ? 0 : index / (double) (count - 1);
            Style next = inherited.withBold(bold.get());
            if (!style.is("Plain")) {
                int rgb = style.is("Rainbow") ? ColorUtil.hsv((float) (t * .8), .7f, 1)
                        : style.is("Solid") ? primary.get() : ColorUtil.lerp(primary.get(), accent.get(), (float) t);
                next = next.withColor(rgb & 0xFFFFFF);
            }
            result.append(Text.literal(new String(Character.toChars(cp))).setStyle(next));
            at += Character.charCount(cp); index++;
        }
        return result;
    }
    private Text replace(Text original) {
        if (original == null || mc.getSession() == null) return original;
        String real = mc.getSession().getUsername();
        if (real.isEmpty() || !original.getString().contains(real)) return original;
        MutableText result = Text.empty();
        boolean[] changed = {false};
        original.visit((inherited, segment) -> {
            int cursor = 0, at;
            while ((at = segment.indexOf(real, cursor)) >= 0) {
                int end = at + real.length();
                if ((at > 0 && nameChar(segment.charAt(at - 1))) || (end < segment.length() && nameChar(segment.charAt(end)))) {
                    result.append(Text.literal(segment.substring(cursor, end)).setStyle(inherited)); cursor = end; continue;
                }
                if (at > cursor) result.append(Text.literal(segment.substring(cursor, at)).setStyle(inherited));
                result.append(styledAlias(inherited)); changed[0] = true; cursor = end;
            }
            if (cursor < segment.length()) result.append(Text.literal(segment.substring(cursor)).setStyle(inherited));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : original;
    }
    private static boolean nameChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
}
