package dev.maro.module.impl.misc;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Original local /pay simulation, using Glazed's visible behavior as a reference. */
public final class FakePay extends Module {
    private static final BigDecimal LIMIT = new BigDecimal("1000000000000000");
    private static final Pattern AMOUNT = Pattern.compile("\\$?((?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?)([kKmMbBtT]?)");
    private static final Pattern PLAYER = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");
    private static final Pattern BALANCE = Pattern.compile("(?:\\$\\s*|\\b(?:money|balance|coins|cash)\\s*:\\s*\\$?\\s*)([0-9][0-9,]*(?:\\.[0-9]+)?[kKmMbBtT]?)(?![\\w.,])", Pattern.CASE_INSENSITIVE);
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "Play the hit-marker ding", true));
    private final BooleanSetting actionBar = add(new BooleanSetting("Action Bar", "Also show the payment above the hotbar", true));
    private final ModeSetting amountStyle = add(new ModeSetting("Amount Style", "Compact suffixes or the full amount", "Compact", "Compact", "Full"));
    private final BooleanSetting balance = add(new BooleanSetting("Adjust Sidebar Balance", "Subtract simulated payments from money lines on your screen", false).onChange(v -> resetBalance()));
    private final ButtonSetting reset = add(new ButtonSetting("Reset Balance", "Clear the local balance adjustment", "Reset", this::resetBalance));
    private final TextSetting previewPlayer = add(new TextSetting("Preview Player", "Name for the preview button", "Steve", 17, "Steve"));
    private final TextSetting previewAmount = add(new TextSetting("Preview Amount", "Amount for the preview button", "1m", 32, "1m"));
    private final ButtonSetting preview = add(new ButtonSetting("Preview", "Show a sample without changing the balance", "Show", this::preview));
    private final List<SettingSection> sections = List.of(
        SettingSection.of("Payment Feedback", sound, actionBar, amountStyle),
        SettingSection.of("Local Balance", balance, reset), SettingSection.of("Preview", previewPlayer, previewAmount, preview));
    private BigDecimal spent = BigDecimal.ZERO;
    private ClientPlayNetworkHandler connection;
    private Text last = Text.empty();

    public FakePay() {
        super("Fake Pay", "While enabled, /pay shows a payment on your screen without sending the command", Category.MISC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { connection = handler; resetBalance(); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { connection = null; resetBalance(); });
    }
    @Override public List<SettingSection> getSettingSections() { return sections; }
    @Override public boolean persistEnabled() { return false; }
    @Override protected void onEnable() { connection = mc.getNetworkHandler(); resetBalance(); }
    @Override protected void onDisable() { resetBalance(); }
    @Override public void onTick() {
        if (connection != mc.getNetworkHandler()) { connection = mc.getNetworkHandler(); resetBalance(); }
    }
    public void resetBalance() { spent = BigDecimal.ZERO; }
    public BigDecimal spentOffset() { return spent; }
    public Text lastMessage() { return last; }

    /** Malformed /pay is consumed too: a simulation must never fall through to a real payment. */
    public boolean handlePay(String command) {
        if (!isEnabled() || command == null) return false;
        String value = command.strip();
        if (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty()) return false;
        String[] parts = value.split("\\s+");
        String verb = parts[0].toLowerCase(Locale.ROOT);
        if (!verb.equals("pay") && !verb.matches("[a-z0-9_.-]+:pay")) return false;
        if (!inGame()) return true;
        onTick();
        if (parts.length != 3 || !PLAYER.matcher(parts[1]).matches()) {
            error("Usage: /pay <player> <amount>, for example /pay Steve 1.5m");
            return true;
        }
        BigDecimal amount = parseAmount(parts[2]);
        if (amount == null || amount.signum() == 0) {
            error("Use $0.01 to $1,000,000,000,000,000. Try 1500, 1.5k or 2m.");
            return true;
        }
        show(parts[1], amount);
        if (balance.get()) spent = spent.add(amount).min(LIMIT);
        return true;
    }
    private void error(String message) {
        mc.inGameHud.getChatHud().addMessage(Text.literal("[Fake Pay] " + message).formatted(Formatting.RED));
    }
    private void preview() {
        if (!inGame()) return;
        BigDecimal amount = parseAmount(previewAmount.get());
        if (!PLAYER.matcher(previewPlayer.get().strip()).matches() || amount == null || amount.signum() == 0) {
            error("Enter a valid player and amount for the preview."); return;
        }
        show(previewPlayer.get().strip(), amount);
    }
    private void show(String player, BigDecimal amount) {
        MutableText message = Text.literal("You paid " + player + " ").formatted(Formatting.WHITE)
            .append(Text.literal("$").formatted(Formatting.GREEN))
            .append(Text.literal(" " + formatAmount(amount, amountStyle.is("Full"))).formatted(Formatting.WHITE));
        last = message;
        mc.inGameHud.getChatHud().addMessage(message);
        if (actionBar.get()) mc.inGameHud.setOverlayMessage(message, false);
        if (sound.get()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1, 1));
    }
    /** Exact cents; rejects ambiguous commas, exponents, nonfinite values and excessive input. */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.length() > 48) return null;
        Matcher match = AMOUNT.matcher(raw.strip());
        if (!match.matches()) return null;
        int power = switch (match.group(2).toLowerCase(Locale.ROOT)) { case "k" -> 3; case "m" -> 6; case "b" -> 9; case "t" -> 12; default -> 0; };
        BigDecimal amount = new BigDecimal(match.group(1).replace(",", "")).scaleByPowerOfTen(power).stripTrailingZeros();
        return amount.scale() > 2 || amount.compareTo(LIMIT) > 0 ? null : amount;
    }
    public static String formatAmount(BigDecimal amount, boolean full) {
        if (full) return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
        int power = 0;
        while (power < 12 && amount.compareTo(BigDecimal.TEN.pow(power + 3)) >= 0) power += 3;
        BigDecimal rounded = amount.scaleByPowerOfTen(-power).setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(new BigDecimal("1000")) >= 0 && power < 12) { power += 3; rounded = amount.scaleByPowerOfTen(-power).setScale(2, RoundingMode.HALF_UP); }
        return rounded.stripTrailingZeros().toPlainString() + switch (power) { case 3 -> "k"; case 6 -> "M"; case 9 -> "B"; case 12 -> "T"; default -> ""; };
    }
    /** Rendering only: server entries and teams remain untouched, including their styles. */
    public MutableText sidebarText(MutableText original) {
        if (!isEnabled() || !balance.get() || spent.signum() == 0) return original;
        Matcher match = BALANCE.matcher(original.getString());
        if (!match.find()) return original;
        int start = match.start(1), end = match.end(1);
        BigDecimal base = parseAmount(match.group(1));
        if (base == null) return original;
        String adjusted = formatAmount(base.subtract(spent).max(BigDecimal.ZERO), amountStyle.is("Full"));
        MutableText result = Text.empty(); int[] offset = {0};
        original.visit((style, fragment) -> {
            int at = offset[0], until = at + fragment.length();
            if (at < start) result.append(Text.literal(fragment.substring(0, Math.min(fragment.length(), start - at))).setStyle(style));
            if (at <= start && until > start) result.append(Text.literal(adjusted).setStyle(style));
            if (until > end) result.append(Text.literal(fragment.substring(Math.max(0, end - at))).setStyle(style));
            offset[0] = until; return Optional.empty();
        }, original.getStyle());
        return result;
    }
}
