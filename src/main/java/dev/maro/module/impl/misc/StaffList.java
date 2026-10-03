package dev.maro.module.impl.misc;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.runtime.settings.StringListSetting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Who on the server is staff, worked out from what the tab list already shows.
 *
 * <p>Every player's rank is what the tab list draws in front of their name: a word such as [Mod],
 * or - on servers like Donut - an icon from the server's resource pack. Ranks containing one of the
 * Staff Ranks words count by themselves. Any other rank counts once it has been picked in
 * {@link StaffRanksScreen}, which remembers the choice for each server. Names added by hand always
 * count. Staff are listed in the server's own tab order, which puts higher ranks first.
 */
public class StaffList extends Module implements HudElement {
    private static final int WIDTH = 150;
    private static final int PAD = 6;
    private static final int HEADER = 8;
    private static final int ROW = 13;
    private static final int HEAD = 9;
    private static final int MARGIN = 4;
    private static final float RADIUS = 6;
    private static final char SEPARATOR = '\t';

    /** The order the vanilla tab list sorts players in, which servers use to put higher ranks first. */
    private static final Comparator<PlayerListEntry> TAB_ORDER = Comparator
            .comparingInt((PlayerListEntry e) -> -e.getListOrder())
            .thenComparing(e -> e.getScoreboardTeam() == null ? "" : e.getScoreboardTeam().getName())
            .thenComparing(e -> e.getProfile().name(), String::compareToIgnoreCase);

    private final ButtonSetting pick = add(new ButtonSetting("Server Ranks", "Pick which of this server's ranks are staff. Remembered for each server", "Pick",
            () -> mc.setScreen(new StaffRanksScreen(mc.currentScreen, this))));
    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag the panel where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 0, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 55, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big the panel is", 1, 0.5, 2.5, 0.05).suffix("x"));

    private final dev.maro.runtime.settings.Setting<List<String>> ranks = new StringListSetting.Builder()
            .name("Staff Ranks")
            .description("Rank words that count as staff on any server. One per line or separated by ;")
            .defaultValue("Owner", "Co-Owner", "Founder", "Manager", "Admin", "Administrator", "Sr.Mod", "SrMod", "Moderator", "Mod",
                    "Jr.Mod", "JrMod", "Helper", "Trial", "Trainee", "Support", "Staff", "Developer", "Dev", "Builder")
            .build();
    private final dev.maro.runtime.settings.Setting<List<String>> names = new StringListSetting.Builder()
            .name("Extra Names")
            .description("Usernames to always count as staff, even with no rank shown. One per line or separated by ;")
            .defaultValue()
            .build();
    /** Ranks picked as staff, each stored as the server's address, a tab, and the rank's text. */
    private final dev.maro.runtime.settings.Setting<List<String>> picked = new StringListSetting.Builder()
            .name("Picked Ranks")
            .description("The ranks picked as staff on each server")
            .defaultValue()
            .visible(() -> false)
            .build();

    private final BooleanSetting alerts = add(new BooleanSetting("Join Alerts", "A notification when staff join or leave", true));
    private final BooleanSetting heads = add(new BooleanSetting("Heads", "Each player's head beside their name", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "Each player's ping on the right", true));
    private final BooleanSetting hideEmpty = add(new BooleanSetting("Hide When Empty", "Hides the panel while no staff are online", false));
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "How many names are listed before the rest are counted", 10, 1, 30, 1));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the panel is", 88, 0, 100, 1).suffix("%"));
    private final ColorSetting background = add(new ColorSetting("Background", "The panel colour", 0xFF0B0D12));

    /** A rank as the tab list draws it, and who has it. */
    public record Rank(String key, Text badge, List<String> players, boolean byWord, boolean picked) {
    }

    /** One staff member as found this tick. */
    private record Staff(String name, Text badge, String label, PlayerListEntry entry) {
    }

    private List<Staff> online = List.of();
    private final Map<String, String> lastSeen = new HashMap<>();
    private boolean primed;
    private String lastServer = "";

    public StaffList() {
        super("Staff List", "Shows which staff are online, from the ranks in the tab list", Category.MISC);
        add(SettingAdapters.adapt(ranks));
        add(SettingAdapters.adapt(names));
        add(SettingAdapters.adapt(picked));
    }

    @Override
    protected void onEnable() {
        lastSeen.clear();
        primed = false;
    }

    // ---- reading the tab list -----------------------------------------------------------

    /** The address of the server you are on, which picked ranks are remembered under. */
    public String serverKey() {
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            return mc.getCurrentServerEntry().address.trim().toLowerCase(Locale.ROOT);
        }
        return "singleplayer";
    }

    private List<PlayerListEntry> tabEntries() {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (network == null) return List.of();
        List<PlayerListEntry> entries = new ArrayList<>(network.getPlayerList());
        entries.sort(TAB_ORDER);
        return entries;
    }

    /** What the tab list shows for this player: their own display name, or their team's prefix and suffix round it. */
    private static Text tabText(PlayerListEntry entry) {
        if (entry.getDisplayName() != null) return entry.getDisplayName();
        Team team = entry.getScoreboardTeam();
        Text name = Text.literal(entry.getProfile().name());
        return team == null ? name : Text.empty().append(team.getPrefix()).append(name).append(team.getSuffix());
    }

    /**
     * Everything drawn in front of the name, with its own styles and fonts, so a rank icon from a
     * server resource pack comes back as that icon. If the name is not there (a nickname), the run
     * of symbols at the start is taken instead.
     */
    private static Text badgeOf(Text shown, String name) {
        MutableText before = Text.empty();
        boolean[] found = {false};
        shown.visit((style, part) -> {
            int at = part.indexOf(name);
            if (at >= 0) {
                if (at > 0) before.append(Text.literal(part.substring(0, at)).setStyle(style));
                found[0] = true;
                return Optional.of(Boolean.TRUE);
            }
            before.append(Text.literal(part).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        if (found[0]) return before;

        MutableText symbols = Text.empty();
        shown.visit((style, part) -> {
            int end = 0;
            while (end < part.length() && !Character.isLetterOrDigit(part.charAt(end))) end++;
            if (end > 0) symbols.append(Text.literal(part.substring(0, end)).setStyle(style));
            return end < part.length() ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return symbols;
    }

    /** What makes two ranks the same: their text, which for an icon is the icon's own character. */
    private static String keyOf(Text badge) {
        return badge.getString().strip();
    }

    private Set<String> pickedHere() {
        String server = serverKey() + SEPARATOR;
        Set<String> keys = new HashSet<>();
        for (String entry : picked.get()) if (entry.startsWith(server)) keys.add(entry.substring(server.length()));
        return keys;
    }

    /** Every rank on this server, highest first in the tab list's own order. */
    public List<Rank> ranksHere() {
        Map<String, Text> badges = new LinkedHashMap<>();
        Map<String, List<String>> players = new HashMap<>();
        for (PlayerListEntry entry : tabEntries()) {
            String name = entry.getProfile().name();
            Text badge = badgeOf(tabText(entry), name);
            String key = keyOf(badge);
            if (key.isEmpty()) continue;
            badges.putIfAbsent(key, badge);
            players.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
        }
        List<Pattern> words = rankPatterns();
        Set<String> chosen = pickedHere();
        List<Rank> result = new ArrayList<>();
        for (Map.Entry<String, Text> rank : badges.entrySet()) {
            String key = rank.getKey();
            result.add(new Rank(key, rank.getValue(), players.get(key), wordFor(key, words) != null, chosen.contains(key)));
        }
        return result;
    }

    public void togglePicked(String key) {
        List<String> next = new ArrayList<>(picked.get());
        String entry = serverKey() + SEPARATOR + key;
        if (!next.remove(entry)) next.add(entry);
        picked.set(next);
        onTick();
    }

    private List<Pattern> rankPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        for (String word : ranks.get()) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(trimmed) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE));
            }
        }
        return patterns;
    }

    private static String wordFor(String key, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            var match = pattern.matcher(key);
            if (match.find()) return match.group();
        }
        return null;
    }

    // ---- finding staff ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame() || mc.getNetworkHandler() == null) {
            online = List.of();
            lastSeen.clear();
            primed = false;
            return;
        }

        String server = serverKey();
        if (!server.equals(lastServer)) {
            lastServer = server;
            lastSeen.clear();
            primed = false;
        }

        List<Pattern> words = rankPatterns();
        Set<String> chosen = pickedHere();
        Set<String> extra = new HashSet<>();
        for (String name : names.get()) extra.add(name.trim().toLowerCase(Locale.ROOT));

        List<Staff> found = new ArrayList<>();
        for (PlayerListEntry entry : tabEntries()) {
            String name = entry.getProfile().name();
            Text badge = badgeOf(tabText(entry), name);
            String key = keyOf(badge);
            String word = key.isEmpty() ? null : wordFor(key, words);
            if (word != null || (!key.isEmpty() && chosen.contains(key))) {
                found.add(new Staff(name, badge, word != null ? word : "Staff", entry));
            } else if (extra.contains(name.toLowerCase(Locale.ROOT))) {
                found.add(new Staff(name, key.isEmpty() ? null : badge, "Staff", entry));
            }
        }
        online = found;

        Map<String, String> now = new HashMap<>();
        for (Staff staff : found) now.put(staff.name(), staff.label());
        if (primed && alerts.get()) {
            for (Map.Entry<String, String> joined : now.entrySet()) {
                if (!lastSeen.containsKey(joined.getKey())) {
                    Notifications.push("Staff joined", joined.getKey() + " · " + joined.getValue(), Notifications.Type.WARNING);
                }
            }
            for (Map.Entry<String, String> left : lastSeen.entrySet()) {
                if (!now.containsKey(left.getKey())) {
                    Notifications.push("Staff left", left.getKey() + " · " + left.getValue(), Notifications.Type.INFO);
                }
            }
        }
        lastSeen.clear();
        lastSeen.putAll(now);
        // The first look after joining a server lists everyone already on, which is not news.
        primed = true;
    }

    /** The names of the staff found on the last tick, in tab order. */
    public List<String> onlineNames() {
        return online.stream().map(staff -> staff.name() + " (" + staff.label() + ")").toList();
    }

    // ---- placement ----------------------------------------------------------------------

    private boolean needsPicking() {
        return online.isEmpty() && pickedHere().isEmpty() && !"singleplayer".equals(serverKey());
    }

    private int shownRows() {
        if (online.isEmpty()) return needsPicking() ? 2 : 1;
        int max = maxRows.getInt();
        return online.size() > max ? max + 1 : online.size();
    }

    private int panelHeight() {
        return PAD + HEADER + 5 + shownRows() * ROW + PAD - 2;
    }

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
        return WIDTH * hudScale();
    }

    @Override
    public float hudHeight() {
        return panelHeight() * hudScale();
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
        float rx = roomX();
        float ry = roomY();
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

    // ---- drawing ------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden) return;
        boolean placing = mc.currentScreen instanceof HudPlacementScreen screen && screen.element() == this;
        if (online.isEmpty() && hideEmpty.get() && !placing) return;

        float s = hudScale();
        int h = panelHeight();
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float strength = opacity.getFloat() / 100f;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(hudLeft(), hudTop());
        matrices.scale(s, s);

        Render2D.shadow(ctx, 0, 1, WIDTH, h, RADIUS, 9, ColorUtil.withAlpha(0xFF000000, Math.round(120 * Math.max(0.3f, strength))));
        Render2D.roundRect(ctx, 0, 0, WIDTH, h, RADIUS, ColorUtil.withAlpha(background.get(), Math.round(255 * strength)));
        Render2D.roundOutline(ctx, 0, 0, WIDTH, h, RADIUS, hair, 0x1CFFFFFF);
        crown(ctx);

        // ---- header: the title, and a chip with how many are on
        float mid = PAD + HEADER / 2f;
        Fonts.drawV(ctx, "Staff", PAD, mid, ColorUtil.withAlpha(Theme.TEXT, 150), true, 0.8f);
        String count = online.isEmpty() ? "None online" : online.size() + " online";
        float chipWidth = Fonts.width(count, true, 0.7f) + 10;
        int chipColor = online.isEmpty() ? Theme.GREEN : Theme.YELLOW;
        Render2D.roundRect(ctx, WIDTH - PAD - chipWidth, mid - 5, chipWidth, 10, 5, ColorUtil.withAlpha(chipColor, 40));
        Fonts.drawCentered(ctx, count, WIDTH - PAD - chipWidth / 2f, mid, chipColor, true, 0.7f);

        float rowTop = PAD + HEADER + 5;
        Render2D.rect(ctx, PAD, rowTop - 3, WIDTH - PAD * 2, hair, 0x14FFFFFF);

        if (online.isEmpty()) {
            if (needsPicking()) {
                Fonts.drawV(ctx, "No staff ranks picked here", PAD, rowTop + ROW / 2f, ColorUtil.withAlpha(Theme.TEXT, 130), true, 0.72f);
                Fonts.drawV(ctx, "Settings > Server Ranks > Pick", PAD, rowTop + ROW * 1.5f, Theme.accent(220), true, 0.72f);
            } else {
                Fonts.drawV(ctx, "No staff in the tab list", PAD, rowTop + ROW / 2f, ColorUtil.withAlpha(Theme.TEXT, 110), true, 0.75f);
            }
        } else {
            int max = maxRows.getInt();
            for (int i = 0; i < Math.min(max, online.size()); i++) row(ctx, online.get(i), rowTop + i * ROW);
            if (online.size() > max) {
                Fonts.drawV(ctx, "+" + (online.size() - max) + " more", PAD, rowTop + max * ROW + ROW / 2f,
                        ColorUtil.withAlpha(Theme.TEXT, 110), true, 0.72f);
            }
        }

        matrices.popMatrix();
    }

    private void row(DrawContext ctx, Staff staff, float top) {
        float mid = top + ROW / 2f;
        float cx = PAD;

        if (heads.get()) {
            PlayerSkinDrawer.draw(ctx, staff.entry().getSkinTextures(), PAD, Math.round(mid - HEAD / 2f), HEAD);
            cx += HEAD + 5;
        }

        if (staff.badge() != null && !staff.badge().getString().isBlank()) {
            // The rank exactly as the tab list draws it, icon fonts and colours included.
            Text badge = staff.badge();
            ctx.drawText(mc.textRenderer, badge, Math.round(cx), Math.round(mid - 4), 0xFFFFFFFF, false);
            cx += mc.textRenderer.getWidth(badge) + 3;
        } else {
            String label = staff.label();
            float pill = Fonts.width(label, true, 0.66f) + 8;
            Render2D.roundRect(ctx, cx, mid - 4.5f, pill, 9, 4.5f, Theme.accent(46));
            Fonts.drawCentered(ctx, label, cx + pill / 2f, mid, Theme.accent(), true, 0.66f);
            cx += pill + 5;
        }

        float right = WIDTH - PAD;
        if (ping.get()) {
            int latency = staff.entry().getLatency();
            String ms = latency + "ms";
            int color = latency < 80 ? Theme.GREEN : latency < 180 ? Theme.YELLOW : Theme.RED;
            Fonts.drawRight(ctx, ms, right, mid, ColorUtil.withAlpha(color, 210), true, 0.66f);
            right -= Fonts.width(ms, true, 0.66f) + 6;
        }

        Fonts.beginRaw();
        Fonts.drawV(ctx, Fonts.trim(staff.name(), right - cx, true, 0.8f), cx, mid, Theme.TEXT, true, 0.8f);
        Fonts.endRaw();
    }

    /** A thin band of the accent colours along the top edge, fading out towards both corners. */
    private static void crown(DrawContext ctx) {
        float from = RADIUS;
        float third = (WIDTH - RADIUS * 2) / 3f;
        int a = Theme.accent();
        int b = Theme.accent2();
        Render2D.rectGradient(ctx, from, 0, third, 1, ColorUtil.withAlpha(a, 0), a, a, ColorUtil.withAlpha(a, 0));
        Render2D.rectGradient(ctx, from + third, 0, third, 1, a, b, b, a);
        Render2D.rectGradient(ctx, from + third * 2, 0, third, 1, b, ColorUtil.withAlpha(b, 0), ColorUtil.withAlpha(b, 0), b);
    }
}
