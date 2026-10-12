package dev.maro.module.impl.misc;

import dev.maro.donut.OrderMarket;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fake Pay: puts a DonutSMP-style payment line in your own chat, the "Name paid you $1.5m." kind
 * (or "You paid Name $1.5m."), with the name and amount you set. It is drawn on your screen only:
 * nothing is sent to the server and nobody else sees it.
 *
 * <p>Its key is the module's own: pressing it switches the module on, which shows the line once
 * and switches it straight back off, ready for the next press. The wording of both lines is a
 * setting ({player} and {amount} are filled in) in case the server's own wording differs.
 */
public class FakePay extends Module {
    private static final Pattern SLOT = Pattern.compile("\\{(player|amount)}");

    private final ModeSetting type = add(new ModeSetting("Type", "Received: someone paid you. Sent: you paid someone", "Received", "Received", "Sent"));
    private final TextSetting player = add(new TextSetting("Player", "The name on the payment", "Steve", 16, "Name"));
    private final TextSetting amount = add(new TextSetting("Amount", "How much: 1500, 1.5k, 2m, $3,500", "1m", 16, "1m"));
    private final ModeSetting style = add(new ModeSetting("Amount Style", "Short writes $1.5m, like DonutSMP; Full writes $1,500,000", "Short", "Short", "Full"));
    private final TextSetting receivedText = add(new TextSetting("Received Wording", "The line for a payment you got. {player} and {amount} are filled in",
            "{player} paid you {amount}.", 120, "{player} paid you {amount}."));
    private final TextSetting sentText = add(new TextSetting("Sent Wording", "The line for a payment you made. {player} and {amount} are filled in",
            "You paid {player} {amount}.", 120, "You paid {player} {amount}."));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "A soft chime with it", true));
    private final ButtonSetting show = add(new ButtonSetting("Show Now", "Put the payment in your chat now", "Show", this::send));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Payment", type, player, amount, style),
            SettingSection.of("Wording", receivedText, sentText, sound),
            SettingSection.of("Actions", show));

    private boolean pending;
    /** The last line shown, as plain text; for tests. */
    private String last = "";

    public FakePay() {
        super("Fake Pay", "Press its key to show a payment line in your own chat. On your screen only; nothing is sent", Category.MISC);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        // Shown on the next tick, then off again: the key is a button, not a switch.
        pending = true;
    }

    @Override
    public void onTick() {
        if (!pending) return;
        pending = false;
        send();
        setEnabled(false);
    }

    /** "$1.5m" the way DonutSMP writes it, or "$1,500,000" when full. */
    static String write(double value, boolean full) {
        if (full) return String.format(Locale.ROOT, "$%,.0f", value);
        double[] size = {1e12, 1e9, 1e6, 1e3};
        String[] tag = {"t", "b", "m", "k"};
        for (int i = 0; i < size.length; i++) {
            if (value >= size[i]) {
                String n = String.format(Locale.ROOT, "%.2f", value / size[i]).replaceAll("0+$", "").replaceAll("\\.$", "");
                return "$" + n + tag[i];
            }
        }
        return value == Math.floor(value) ? "$" + (long) value : String.format(Locale.ROOT, "$%.2f", value);
    }

    /** The template with the name and amount filled in, the name white and the amount green. */
    private static MutableText fill(String template, String name, String money, Formatting base) {
        MutableText out = Text.empty();
        Matcher m = SLOT.matcher(template);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) out.append(Text.literal(template.substring(at, m.start())).formatted(base));
            out.append(m.group(1).equals("player")
                    ? Text.literal(name).formatted(Formatting.WHITE)
                    : Text.literal(money).formatted(Formatting.GREEN));
            at = m.end();
        }
        if (at < template.length()) out.append(Text.literal(template.substring(at)).formatted(base));
        return out;
    }

    /** Shows the payment in your chat, as set. */
    public void send() {
        if (!inGame() || mc.inGameHud == null) {
            Notifications.push(getName(), "Join a world first", Notifications.Type.INFO);
            return;
        }
        double value = OrderMarket.amount(amount.get());
        if (Double.isNaN(value) || value <= 0) {
            Notifications.push(getName(), "That amount does not read as money. Try 1500, 1.5k or 2m", Notifications.Type.ERROR);
            return;
        }
        String name = player.get().isBlank() ? "Steve" : player.get().trim();
        String money = write(value, style.is("Full"));
        boolean got = type.is("Received");
        String template = got ? receivedText.get() : sentText.get();
        MutableText line = fill(template, name, money, Formatting.GRAY);
        last = line.getString();
        mc.inGameHud.getChatHud().addMessage(line);
        if (sound.get()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1f));
    }

    public String last() {
        return last;
    }
}
