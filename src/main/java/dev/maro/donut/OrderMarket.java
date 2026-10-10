package dev.maro.donut;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads DonutSMP's orders menu: which orders want an item, what each pays per item and how many it
 * still wants. Menus there write in small capitals ("ᴏʀᴅᴇʀꜱ"), so text is folded to plain lower
 * case first. A price that cannot be read is never taken as zero.
 */
public final class OrderMarket {
    private OrderMarket() {
    }

    /** An order: its slot in the menu, what it pays for each item, and how many it still wants (-1 when not shown). */
    public record Order(int slot, double each, int remaining) {
    }

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?(?![a-z])");
    private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?(?![a-z])");
    private static final Pattern FRACTION = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?)\\s*/\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?)(?![a-z])");
    private static final Pattern WANTS = Pattern.compile("(?:remaining|left|needed|wants|wanted|still)");

    /** Text as plain lower case: colour codes gone, small capitals made ordinary letters, spaces tidied. */
    public static String plain(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            int small = SMALL_CAPS.indexOf(c);
            out.append(small >= 0 ? (char) ('a' + small) : Character.toLowerCase(c));
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /** Whether the text holds any of the words, given separated by ';'. */
    public static boolean has(String text, String words) {
        if (text == null || words == null) return false;
        String value = plain(text);
        return Arrays.stream(words.split(";")).map(OrderMarket::plain).filter(w -> !w.isEmpty()).anyMatch(value::contains);
    }

    /** "1.2k" as 1200, "$3,500" as 3500, "2m" as 2000000; NaN when it does not read as an amount. */
    public static double amount(String text) {
        Matcher m = NUMBER.matcher(plain(text).replace("$", ""));
        return m.find() ? value(m.group(1), m.group(2)) : Double.NaN;
    }

    private static double value(String digits, String suffix) {
        try {
            double v = Double.parseDouble(digits.replace(",", ""));
            if (suffix != null) v *= switch (suffix) {
                case "k" -> 1e3;
                case "m" -> 1e6;
                default -> 1e9;
            };
            return Double.isFinite(v) && v >= 0 ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) for (var line : lore.lines()) out.add(line.getString());
        return out;
    }

    /**
     * What an order pays for each item: the $ amount on a line that says so ("$1.2k each", "Price
     * per item: $40"), or the only $ amount in the lore; NaN if neither.
     */
    public static double priceEach(List<String> lore, String priceWords) {
        for (String line : lore) {
            String p = plain(line);
            if (!has(p, priceWords)) continue;
            Matcher m = MONEY.matcher(p);
            if (m.find()) return value(m.group(1), m.group(2));
        }
        double found = Double.NaN;
        int count = 0;
        for (String line : lore) {
            Matcher m = MONEY.matcher(plain(line));
            while (m.find()) {
                count++;
                found = value(m.group(1), m.group(2));
            }
        }
        return count == 1 ? found : Double.NaN;
    }

    /** How many an order still wants: from "120/2,304 delivered" or "Remaining: 50"; -1 when not shown. */
    public static int remaining(List<String> lore) {
        for (String line : lore) {
            String p = plain(line);
            if (p.contains("$")) continue;
            Matcher m = FRACTION.matcher(p);
            if (m.find()) {
                double done = amount(m.group(1)), total = amount(m.group(2));
                if (Double.isFinite(done) && Double.isFinite(total) && total >= done) return (int) Math.min(Integer.MAX_VALUE, total - done);
            }
        }
        for (String line : lore) {
            String p = plain(line);
            if (p.contains("$") || !WANTS.matcher(p).find()) continue;
            double n = amount(p);
            if (Double.isFinite(n)) return (int) Math.min(Integer.MAX_VALUE, n);
        }
        return -1;
    }

    /** Every order in a menu for this item that shows what it pays. */
    public static List<Order> orders(ScreenHandler handler, PlayerInventory inventory, Item item, String priceWords) {
        List<Order> out = new ArrayList<>();
        for (var slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (slot.inventory == inventory || !stack.isOf(item)) continue;
            List<String> lines = lore(stack);
            double each = priceEach(lines, priceWords);
            if (Double.isFinite(each) && each > 0) out.add(new Order(slot.id, each, remaining(lines)));
        }
        return out;
    }

    /** The order paying most per item, at least {@code minEach}, that still wants some; null if none. */
    public static Order best(List<Order> orders, double minEach) {
        return orders.stream().filter(o -> o.remaining() != 0 && o.each() >= minEach)
                .max(Comparator.comparingDouble(Order::each).thenComparingInt(o -> o.remaining() < 0 ? Integer.MAX_VALUE : o.remaining()))
                .orElse(null);
    }

    /** "$1.2k", "$350", "$2.5m": money written short. */
    public static String money(double amount) {
        if (amount >= 1e9) return String.format(Locale.ROOT, "$%.2fb", amount / 1e9);
        if (amount >= 1e6) return String.format(Locale.ROOT, "$%.2fm", amount / 1e6);
        if (amount >= 1e4) return String.format(Locale.ROOT, "$%.1fk", amount / 1e3);
        return amount == Math.floor(amount) ? "$" + (long) amount : String.format(Locale.ROOT, "$%.2f", amount);
    }
}
