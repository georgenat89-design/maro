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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Who on the server is staff, worked out from what the tab list already shows: the rank text
 * beside each name (prefixes such as [Mod] or Admin), plus any names you add yourself. Works on
 * any server, highest rank first, with a notification when staff join or leave.
 */
public class StaffList extends Module implements HudElement {
    private static final int WIDTH = 150;
    private static final int PAD = 6;
    private static final int HEADER = 8;
    private static final int ROW = 13;
    private static final int HEAD = 9;
    private static final int MARGIN = 4;
    private static final float RADIUS = 6;

    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag the panel where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 0, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 55, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big the panel is", 1, 0.5, 2.5, 0.05).suffix("x"));

    private final dev.maro.runtime.settings.Setting<List<String>> ranks = new StringListSetting.Builder()
            .name("Staff Ranks")
            .description("Rank words that mark staff in the tab list, highest first. One per line or separated by ;")
            .defaultValue("Owner", "Co-Owner", "Founder", "Manager", "Admin", "Administrator", "Sr.Mod", "SrMod", "Moderator", "Mod",
                    "Jr.Mod", "JrMod", "Helper", "Trial", "Trainee", "Support", "Staff", "Developer", "Dev", "Builder")
            .build();
    private final dev.maro.runtime.settings.Setting<List<String>> names = new StringListSetting.Builder()
            .name("Extra Names")
            .description("Usernames to always count as staff, even with no rank shown. One per line or separated by ;")
            .defaultValue()
            .build();

    private final BooleanSetting alerts = add(new BooleanSetting("Join Alerts", "A notification when staff join or leave", true));
    private final BooleanSetting heads = add(new BooleanSetting("Heads", "Each player's head beside their name", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "Each player's ping on the right", true));
    private final BooleanSetting hideEmpty = add(new BooleanSetting("Hide When Empty", "Hides the panel while no staff are online", false));
    private final NumberSetting maxRows = add(new NumberSetting("Max Rows", "How many names are listed before the rest are counted", 10, 1, 30, 1));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the panel is", 88, 0, 100, 1).suffix("%"));
    private final ColorSetting background = add(new ColorSetting("Background", "The panel colour", 0xFF0B0D12));

    /** One staff member as found this tick. */
    private record Staff(String name, String rank, int priority, int color, PlayerListEntry entry) {
    }

    private List<Staff> online = List.of();
    private final Map<String, String> lastSeen = new HashMap<>();
    private boolean primed;

    public StaffList() {
        super("Staff List", "Shows which staff are online, from the ranks in the tab list", Category.MISC);
        add(SettingAdapters.adapt(ranks));
        add(SettingAdapters.adapt(names));
    }

    @Override
    protected void onEnable() {
        lastSeen.clear();
        primed = false;
    }

    // ---- finding staff ------------------------------------------------------------------

    @Override
    public void onTick() {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (!inGame() || network == null) {
            online = List.of();
            lastSeen.clear();
            primed = false;
            return;
        }

        List<String> rankWords = ranks.get();
        List<Pattern> patterns = new ArrayList<>();
        for (String word : rankWords) {
            String trimmed = word.trim();
            patterns.add(trimmed.isEmpty() ? null
                    : Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(trimmed) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE));
        }
        Set<String> extra = new HashSet<>();
        for (String name : names.get()) extra.add(name.trim().toLowerCase(Locale.ROOT));

        List<Staff> found = new ArrayList<>();
        for (PlayerListEntry entry : network.getPlayerList()) {
            String name = entry.getProfile().name();
            Text shown = tabText(entry);
            // Only the text round the name is a rank, so a player called "Modest" is not a mod.
            String around = shown.getString().replace(name, " ");
            int priority = -1;
            String rank = null;
            for (int i = 0; i < patterns.size(); i++) {
                if (patterns.get(i) != null && patterns.get(i).matcher(around).find()) {
                    priority = i;
                    rank = rankWords.get(i).trim();
                    break;
                }
            }
            if (rank == null && extra.contains(name.toLowerCase(Locale.ROOT))) {
                priority = rankWords.size();
                rank = "Staff";
            }
            if (rank != null) found.add(new Staff(name, rank, priority, rankColor(shown, name), entry));
        }
        found.sort((a, b) -> a.priority() != b.priority() ? Integer.compare(a.priority(), b.priority()) : a.name().compareToIgnoreCase(b.name()));
        online = found;

        Map<String, String> now = new HashMap<>();
        for (Staff staff : found) now.put(staff.name(), staff.rank());
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

    /** What the tab list shows for this player: their own display name, or their team's prefix and suffix round it. */
    private static Text tabText(PlayerListEntry entry) {
        if (entry.getDisplayName() != null) return entry.getDisplayName();
        Team team = entry.getScoreboardTeam();
        Text name = Text.literal(entry.getProfile().name());
        return team == null ? name : Text.empty().append(team.getPrefix()).append(name).append(team.getSuffix());
    }

    /** The colour of the first coloured text that is not the name itself: usually the rank's own colour. */
    private static int rankColor(Text shown, String name) {
        Optional<Integer> color = shown.visit((style, part) -> {
            if (part.isBlank() || part.contains(name) || style.getColor() == null) return Optional.empty();
            return Optional.of(style.getColor().getRgb());
        }, Style.EMPTY);
        return color.map(rgb -> 0xFF000000 | rgb).orElse(Theme.accent());
    }

    /** The names of the staff found on the last tick, highest rank first. */
    public List<String> onlineNames() {
        return online.stream().map(staff -> staff.name() + " (" + staff.rank() + ")").toList();
    }

    // ---- placement ----------------------------------------------------------------------

    private int shownRows() {
        if (online.isEmpty()) return 1;
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
            Fonts.drawV(ctx, "No staff in the tab list", PAD, rowTop + ROW / 2f, ColorUtil.withAlpha(Theme.TEXT, 110), true, 0.75f);
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

        String rank = staff.rank();
        float rankWidth = Fonts.width(rank, true, 0.66f) + 8;
        Render2D.roundRect(ctx, cx, mid - 4.5f, rankWidth, 9, 4.5f, ColorUtil.withAlpha(staff.color(), 46));
        Fonts.drawCentered(ctx, rank, cx + rankWidth / 2f, mid, ColorUtil.lerp(staff.color(), 0xFFFFFFFF, 0.25f), true, 0.66f);
        cx += rankWidth + 5;

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
