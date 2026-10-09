package dev.maro.module.impl.misc;

import dev.maro.gui.hud.GoliathPickerScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.nathan.regionmap.RegionGrid;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Auto Goliath: random-teleports until you land in the goliath you picked, "East 2" say. On Donut a
 * second /rtp into the region you are already in keeps you on the same goliath, so to be sent to
 * another one it goes out to a different region and back: /rtp eu central, /rtp east, check, and
 * again, picking a different region to bounce through each time. Pick the goliath here or on the map.
 */
public class AutoGoliath extends Module {
    /** The regions /rtp knows, and their place on the region map. */
    public static final String[] REGIONS = {"East", "West", "EU Central", "EU West", "Oceania", "Asia"};
    private static final RegionGrid.Locale[] LOCALES = {RegionGrid.Locale.NaEast, RegionGrid.Locale.NaWest, RegionGrid.Locale.EuCentral,
            RegionGrid.Locale.EuWest, RegionGrid.Locale.Oceania, RegionGrid.Locale.Asia};

    private final ModeSetting region = add(new ModeSetting("Region", "The region of the goliath you want", "East", REGIONS));
    private final NumberSetting goliath = add(new NumberSetting("Goliath", "Its number, as on the region map", 1, 1, 60, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds between one /rtp and the next", 6, 2, 60, 1).suffix("s"));
    private final ButtonSetting map = add(new ButtonSetting("Pick On Map", "Choose the goliath on the region map", "Open",
            () -> mc.setScreen(new GoliathPickerScreen(mc.currentScreen, this))));

    private final List<SettingSection> sections = List.of(SettingSection.of("Target", region, goliath, delay, map));

    private enum Phase {SEND, TELEPORTING, WAITING}

    private Phase phase = Phase.SEND;
    private long sentAt, landedAt;
    private Vec3d sentFrom;
    private String lastCommand = "";
    private String landed = "";
    private int attempts;
    private final List<Integer> bounces = new ArrayList<>();

    public AutoGoliath() {
        super("Auto Goliath", "Random-teleports between regions until you land in the goliath you picked", Category.MISC);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    // ---- the target ---------------------------------------------------------------------------

    public int regionIndex() {
        return Math.max(0, region.index());
    }

    public int goliathNumber() {
        return goliath.getInt();
    }

    /** Picks the goliath to land in; for the map and tests. */
    public void pick(int regionIndex, int number) {
        region.set(REGIONS[regionIndex]);
        goliath.set((double) Math.max(1, Math.min(count(regionIndex), number)));
    }

    public static RegionGrid.Locale locale(int regionIndex) {
        return LOCALES[regionIndex];
    }

    /** Which /rtp region a map locale is, or -1 for one /rtp cannot reach. */
    public static int regionOf(RegionGrid.Locale locale) {
        for (int i = 0; i < LOCALES.length; i++) if (LOCALES[i] == locale) return i;
        return -1;
    }

    /** How many goliaths a region has. */
    public static int count(int regionIndex) {
        int n = 0;
        for (int i = 0; i < RegionGrid.count(); i++) if (RegionGrid.shard(i).locale() == LOCALES[regionIndex]) n++;
        return n;
    }

    /** The goliath you are standing in now, or null off the map or out of the overworld. */
    public RegionGrid.Shard here() {
        if (mc.player == null || mc.world == null || mc.world.getRegistryKey() != World.OVERWORLD) return null;
        int id = RegionGrid.cell(RegionGrid.column(mc.player.getX()), RegionGrid.row(mc.player.getZ()));
        return id < 0 ? null : RegionGrid.shard(id);
    }

    private boolean onTarget(RegionGrid.Shard shard) {
        return shard != null && shard.locale() == LOCALES[regionIndex()] && shard.number() == goliathNumber();
    }

    public static String name(RegionGrid.Shard shard) {
        int r = regionOf(shard.locale());
        return (r >= 0 ? REGIONS[r] : shard.locale().toString()) + " " + shard.number();
    }

    // ---- the loop ------------------------------------------------------------------------------

    @Override
    protected void onEnable() {
        phase = Phase.SEND;
        attempts = 0;
        landed = "";
        bounces.clear();
        if (goliathNumber() > count(regionIndex())) {
            goliath.set((double) count(regionIndex()));
        }
        if (inGame() && onTarget(here())) {
            Notifications.push("Auto Goliath", "You are already in " + REGIONS[regionIndex()] + " " + goliathNumber(), Notifications.Type.SUCCESS);
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();
        switch (phase) {
            case SEND -> {
                String command = nextCommand();
                lastCommand = command;
                sentFrom = mc.player.getEntityPos();
                sentAt = now;
                attempts++;
                mc.getNetworkHandler().sendChatCommand(command);
                phase = Phase.TELEPORTING;
            }
            case TELEPORTING -> {
                // A jump of more than a few chunks, or a new world, is the teleport.
                if (mc.player.getEntityPos().squaredDistanceTo(sentFrom) > 64 * 64) {
                    landedAt = now;
                    phase = Phase.WAITING;
                } else if (now - sentAt > Math.max(20_000, delay.get() * 3000)) {
                    phase = Phase.SEND;
                }
            }
            case WAITING -> {
                // A moment to settle where we landed, then look at which goliath it is.
                if (now - landedAt < 600) return;
                RegionGrid.Shard shard = here();
                landed = shard == null ? "off the map" : name(shard);
                if (onTarget(shard)) {
                    Notifications.push("Auto Goliath", "Landed in " + landed + " after " + attempts + " teleports", Notifications.Type.SUCCESS, 6000);
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.8f));
                    setEnabled(false);
                    return;
                }
                if (now - sentAt >= delay.get() * 1000) phase = Phase.SEND;
            }
        }
    }

    /**
     * The next /rtp: into the target region, unless we are already in it on the wrong goliath, when
     * it is out to another region first, a different one each time.
     */
    private String nextCommand() {
        int target = regionIndex();
        RegionGrid.Shard shard = here();
        boolean inTarget = shard != null && shard.locale() == LOCALES[target];
        int to = target;
        if (inTarget) {
            if (bounces.isEmpty()) {
                for (int i = 0; i < REGIONS.length; i++) if (i != target) bounces.add(i);
                Collections.shuffle(bounces);
            }
            to = bounces.removeFirst();
        }
        return "rtp " + REGIONS[to].toLowerCase(java.util.Locale.ROOT);
    }

    /** The last /rtp sent, without the slash; for tests. */
    public String lastCommand() {
        return lastCommand;
    }

    /** What it is doing, for the bar at the top. */
    public String status() {
        String target = REGIONS[regionIndex()] + " " + goliathNumber();
        String last = landed.isEmpty() ? "" : "  ·  last: " + landed;
        return switch (phase) {
            case SEND, TELEPORTING -> "/" + lastCommand + "  →  " + target + last;
            case WAITING -> "Next /rtp in " + Math.max(0, (int) Math.ceil((sentAt + delay.get() * 1000 - System.currentTimeMillis()) / 1000.0))
                    + "s  ·  target " + target + last;
        };
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden) return;
        Fonts.beginRaw();
        try {
            String text = "Auto Goliath  ·  try " + attempts + "  ·  " + status();
            float w = Fonts.width(text, false, 0.7f) + 20, x = (ctx.getScaledWindowWidth() - w) / 2f, y = 4;
            Render2D.roundRect(ctx, x, y, w, 15, 7.5f, 0xD00D0F14);
            Render2D.roundOutline(ctx, x, y, w, 15, 7.5f, 1, Theme.accent(0x80));
            Render2D.circle(ctx, x + 8, y + 7.5f, 2.5f, Theme.accent());
            Fonts.drawV(ctx, text, x + 14, y + 7.5f, 0xFFE8EAF0, false, 0.7f);
        } finally {
            Fonts.endRaw();
        }
    }
}
