package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A compass on the HUD, as a heading strip across the screen or a round dial, turning smoothly with
 * you. Markers on it point to your last death, the world spawn and a home you set, each with how
 * far away it is; one that is off the strip waits at the edge.
 */
public class Compass extends Module implements HudElement {
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final int MARGIN = 4;
    private static final int STRIP_H = 22;
    private static final int DIAL_R = 34;

    private final ButtonSetting position = new ButtonSetting("Position", "Drag the compass where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this)));
    private final NumberSetting x = new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 50, 0, 100, 0.5).suffix("%");
    private final NumberSetting y = new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 0, 0, 100, 0.5).suffix("%");
    private final NumberSetting scale = new NumberSetting("Scale", "How big the compass is", 1, 0.5, 2.5, 0.05).suffix("x");
    private final ButtonSetting center = new ButtonSetting("Center", "Put the compass in the exact middle, across the screen", "Center",
            () -> x.set(50.0));

    private final ModeSetting style = new ModeSetting("Style", "A strip across the screen, or a round dial", "Strip", "Strip", "Dial");
    private final NumberSetting width = new NumberSetting("Strip Width", "How wide the strip is", 260, 120, 480, 5);
    private final NumberSetting view = new NumberSetting("View Angle", "How much of the compass the strip shows at once", 180, 90, 360, 5).suffix("°");
    private final BooleanSetting smooth = new BooleanSetting("Smooth Turning", "Glide to your heading instead of snapping", true);
    private final BooleanSetting degrees = new BooleanSetting("Degrees", "Your exact heading in degrees", true);
    private final NumberSetting opacity = new NumberSetting("Background Opacity", "How solid the panel is", 80, 0, 100, 1).suffix("%");

    private final BooleanSetting deathMarker = new BooleanSetting("Last Death", "Point to where you last died", true);
    private final BooleanSetting spawnMarker = new BooleanSetting("Spawn", "Point to the world spawn", false);
    private final BooleanSetting homeMarker = new BooleanSetting("Home", "Point to the home you set", true);
    private final BooleanSetting coordsMarker = new BooleanSetting("Coords Fly", "Point to where Coords Fly is taking you, while it is on", true);
    private final BooleanSetting distances = new BooleanSetting("Distances", "How far away each marker is", true);
    private final ButtonSetting setHome = new ButtonSetting("Set Home Here", "Make where you stand your home", "Set", this::markHome);
    private final ButtonSetting clearHome = new ButtonSetting("Clear Home", "Forget your home", "Clear", this::forgetHome);
    private final NumberSetting homeX = new NumberSetting("Home X", "", 0, -30000000, 30000000, 1).visible(() -> false);
    private final NumberSetting homeY = new NumberSetting("Home Y", "", 0, -2048, 2048, 1).visible(() -> false);
    private final NumberSetting homeZ = new NumberSetting("Home Z", "", 0, -30000000, 30000000, 1).visible(() -> false);
    private final BooleanSetting homeSet = new BooleanSetting("Home Set", "", false).visible(() -> false);
    private final ModeSetting homeWorld = new ModeSetting("Home World", "", "overworld", "overworld", "the_nether", "the_end", "other").visible(() -> false);

    private final List<SettingSection> sections;

    private double shown = Double.NaN;
    private long lastFrame;

    private record Marker(String label, int color, double bearing, double distance) {
    }

    public Compass() {
        super("Compass", "A good-looking compass with markers for home, spawn and your last death", Category.VISUALS);
        SettingSection placement = new SettingSection("Placement");
        for (var s : new dev.maro.setting.Setting<?>[]{position, center, x, y, scale}) placement.add(add(s));
        SettingSection look = new SettingSection("Look");
        for (var s : new dev.maro.setting.Setting<?>[]{style, width, view, smooth, degrees, opacity}) look.add(add(s));
        width.visible(() -> style.is("Strip"));
        view.visible(() -> style.is("Strip"));
        SettingSection markers = new SettingSection("Markers");
        for (var s : new dev.maro.setting.Setting<?>[]{deathMarker, spawnMarker, homeMarker, coordsMarker, distances, setHome, clearHome}) markers.add(add(s));
        for (var s : new dev.maro.setting.Setting<?>[]{homeX, homeY, homeZ, homeSet, homeWorld}) add(s);
        sections = List.of(look, markers, placement);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** Compasses saved before placement snapped to the middle; those placed near it are centered once. */
    private static final int PLACEMENT_REVISION = 1;

    @Override
    public com.google.gson.JsonObject saveExtra() {
        var data = super.saveExtra();
        data.addProperty("placement-revision", PLACEMENT_REVISION);
        return data;
    }

    @Override
    public void loadExtra(com.google.gson.JsonObject data) {
        super.loadExtra(data);
        int saved = data.has("placement-revision") ? data.get("placement-revision").getAsInt() : 0;
        // Dragged by hand to roughly the middle, it was a few pixels off; anywhere else was meant.
        if (saved < 1 && Math.abs(x.get() - 50) <= 15) x.set(50.0);
    }

    private void markHome() {
        if (!inGame()) return;
        BlockPos pos = mc.player.getBlockPos();
        homeX.set((double) pos.getX());
        homeY.set((double) pos.getY());
        homeZ.set((double) pos.getZ());
        homeWorld.set(worldName());
        homeSet.set(true);
        Notifications.push("Home set", pos.getX() + " " + pos.getY() + " " + pos.getZ(), Notifications.Type.SUCCESS);
    }

    private void forgetHome() {
        homeSet.set(false);
        Notifications.push("Home cleared", "The Home marker is gone", Notifications.Type.INFO);
    }

    private String worldName() {
        String path = mc.world.getRegistryKey().getValue().getPath();
        return path.equals("overworld") || path.equals("the_nether") || path.equals("the_end") ? path : "other";
    }

    // ---- headings -----------------------------------------------------------------------

    /** Your heading as a compass bearing: 0 north, 90 east, 180 south, 270 west. */
    private double heading() {
        return wrap(mc.player.getYaw() + 180);
    }

    private static double wrap(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    /** The shortest turn from one bearing to another, -180 to 180. */
    private static double turn(double from, double to) {
        return ((to - from) % 360 + 540) % 360 - 180;
    }

    private double bearingTo(double tx, double tz) {
        double dx = tx - mc.player.getX();
        double dz = tz - mc.player.getZ();
        return wrap(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    private List<Marker> markers() {
        List<Marker> list = new ArrayList<>();
        String here = mc.world.getRegistryKey().getValue().toString();
        if (deathMarker.get()) {
            mc.player.getLastDeathPos().ifPresent(death -> {
                if (death.dimension().getValue().toString().equals(here)) list.add(marker("Death", Theme.RED, death.pos()));
            });
        }
        if (spawnMarker.get()) {
            var spawn = mc.world.getSpawnPoint();
            if (spawn.getDimension().getValue().toString().equals(here)) list.add(marker("Spawn", Theme.GREEN, spawn.getPos()));
        }
        if (homeMarker.get() && homeSet.get() && homeWorld.get().equals(worldName())) {
            list.add(marker("Home", Theme.accent(), new BlockPos(homeX.getInt(), homeY.getInt(), homeZ.getInt())));
        }
        var coordsFly = dev.maro.module.ModuleManager.get(dev.maro.module.impl.movement.CoordsFly.class);
        if (coordsMarker.get() && coordsFly != null && coordsFly.showsTarget()) {
            var t = coordsFly.target();
            list.add(new Marker("Coords", Theme.accent2(), bearingTo(t.x(), t.z()), coordsFly.distance(t)));
        }
        return list;
    }

    private Marker marker(String label, int color, BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return new Marker(label, color, bearingTo(pos.getX() + 0.5, pos.getZ() + 0.5), Math.sqrt(dx * dx + dz * dz));
    }

    private static String distance(double blocks) {
        return blocks >= 10000 ? String.format(java.util.Locale.ROOT, "%.1fk", blocks / 1000) : Math.round(blocks) + "m";
    }

    // ---- placement ----------------------------------------------------------------------

    private float panelWidth() {
        return style.is("Strip") ? width.getFloat() : DIAL_R * 2 + 8;
    }

    private float panelHeight() {
        if (style.is("Strip")) return STRIP_H + (degrees.get() ? 16 : 0) + 12;
        return DIAL_R * 2 + 8;
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
        return panelWidth() * hudScale();
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

        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double target = heading();
        if (Double.isNaN(shown) || !smooth.get()) shown = target;
        else shown = wrap(shown + turn(shown, target) * Math.min(1, seconds * 14));

        float s = hudScale();
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(hudLeft(), hudTop());
        matrices.scale(s, s);
        if (style.is("Strip")) strip(ctx, s);
        else dial(ctx, s);
        matrices.popMatrix();
    }

    private int alpha(int color, float of) {
        return ColorUtil.withAlpha(color, Math.round((color >>> 24) * of * opacity.getFloat() / 100f));
    }

    private static final String[] POINTS16 = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    /** "247°  WSW", for the readout. */
    private String reading() {
        double h = target();
        return Math.round(h) % 360 + "\u00B0";
    }

    private String direction() {
        return POINTS16[(int) Math.round(target() / 22.5) % 16];
    }

    /** A small square turned on its corner: markers and the pointer. */
    private static void diamond(DrawContext ctx, float cx, float cy, float r, int color) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate((float) Math.toRadians(45));
        Render2D.roundRect(ctx, -r, -r, r * 2, r * 2, r * 0.35f, color);
        m.popMatrix();
    }

    private void strip(DrawContext ctx, float s) {
        float w = width.getFloat();
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float fov = view.getFloat();
        float mid = w / 2f;
        float r = STRIP_H / 2f;
        int accent = Theme.accent();

        // Glass: a soft shadow, a gradient body, a lighter top edge and an accent glow along the bottom.
        Render2D.shadow(ctx, 0, 2, w, STRIP_H, r, 9, alpha(0xC0000000, 1));
        Render2D.roundRect(ctx, 0, 0, w, STRIP_H, r, alpha(0xF2181C27, 1), alpha(0xF2181C27, 1), alpha(0xF20A0C12, 1), alpha(0xF20A0C12, 1));
        Render2D.roundOutline(ctx, 0, 0, w, STRIP_H, r, hair, 0x30FFFFFF, 0x30FFFFFF, 0x0CFFFFFF, 0x0CFFFFFF);
        float glow = w * 0.38f;
        Render2D.rectGradient(ctx, mid - glow, STRIP_H - 1.6f, glow, 1.3f, Theme.accent(0), Theme.accent(0xD0), Theme.accent(0xD0), Theme.accent(0));
        Render2D.rectGradient(ctx, mid, STRIP_H - 1.6f, glow, 1.3f, Theme.accent(0xD0), Theme.accent(0), Theme.accent(0), Theme.accent(0xD0));
        // A faint band of the accent behind the middle.
        Render2D.rectGradient(ctx, mid - 18, 1, 18, STRIP_H - 2, Theme.accent(0), Theme.accent(0x26), Theme.accent(0x26), Theme.accent(0));
        Render2D.rectGradient(ctx, mid, 1, 18, STRIP_H - 2, Theme.accent(0x26), Theme.accent(0), Theme.accent(0), Theme.accent(0x26));

        // Ticks, numbers and points, fading towards the ends.
        for (int deg = 0; deg < 360; deg += 5) {
            double rel = turn(shown, deg);
            if (Math.abs(rel) > fov / 2) continue;
            float px = (float) (mid + rel / fov * (w - 18));
            float fade = (float) Math.max(0, 1 - Math.pow(Math.abs(rel) / (fov / 2), 2.5));
            float near = (float) Math.max(0, 1 - Math.abs(rel) / 40);
            if (deg % 45 == 0) {
                String point = POINTS[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int color = deg == 0 ? accent : cardinal ? 0xFFF4F6FA : 0xFFAEB4C2;
                if (deg == 0) Render2D.shadow(ctx, px - 5, r - 5, 10, 10, 5, 7, Theme.accent(Math.round(0x70 * fade)));
                Fonts.drawCentered(ctx, point, px, r - 0.5f, ColorUtil.mulAlpha(color, fade), true, cardinal ? 0.92f : 0.66f);
            } else if (deg % 15 == 0) {
                if (deg % 30 == 0) Fonts.drawCentered(ctx, String.valueOf(deg), px, r - 1.5f, ColorUtil.mulAlpha(0xFF7D8494, fade), false, 0.48f);
                int tick = ColorUtil.lerp(0xA0FFFFFF, accent, near * 0.8f);
                Render2D.rect(ctx, px - hair * 0.6f, STRIP_H - 7.5f, hair * 1.2f, 4.5f, ColorUtil.mulAlpha(tick, fade));
            } else {
                Render2D.rect(ctx, px - hair / 2, STRIP_H - 5.5f, hair, 2.5f, ColorUtil.mulAlpha(0x60FFFFFF, fade));
            }
        }

        // Markers: glowing diamonds, waiting at the nearer end when out of view.
        List<Marker> markers = markers();
        for (Marker m : markers) {
            double rel = turn(shown, m.bearing());
            boolean inView = Math.abs(rel) <= fov / 2;
            float px = (float) (mid + Math.max(-fov / 2, Math.min(fov / 2, rel)) / fov * (w - 18));
            Render2D.shadow(ctx, px - 3, 1.5f, 6, 6, 3, 4, ColorUtil.withAlpha(m.color(), inView ? 0x80 : 0x40));
            diamond(ctx, px, 4.5f, 2.4f, inView ? m.color() : ColorUtil.withAlpha(m.color(), 150));
            if (distances.get()) {
                Fonts.drawCentered(ctx, m.label() + " " + distance(m.distance()), px, STRIP_H + 7, ColorUtil.withAlpha(m.color(), inView ? 235 : 150), true, 0.58f);
            }
        }

        // The middle: a glowing pointer above a fine line.
        Render2D.rect(ctx, mid - hair / 2, 3, hair, STRIP_H - 6, Theme.accent(0xB0));
        Render2D.shadow(ctx, mid - 4, -4, 8, 8, 4, 5, Theme.accent(0x90));
        diamond(ctx, mid, -0.5f, 3f, accent);
        diamond(ctx, mid, -0.5f, 1.3f, 0xFFFFFFFF);

        if (degrees.get()) {
            String deg = reading(), dir = direction();
            float dw = Fonts.width(deg, true, 0.78f), gw = Fonts.width(dir, true, 0.66f);
            float tw = dw + gw + 20;
            float ty = STRIP_H + (distances.get() && !markers.isEmpty() ? 14 : 5);
            Render2D.shadow(ctx, mid - tw / 2, ty + 1, tw, 13, 6.5f, 5, alpha(0x90000000, 1));
            Render2D.roundRect(ctx, mid - tw / 2, ty, tw, 13, 6.5f, alpha(0xF2141822, 1));
            Render2D.roundOutline(ctx, mid - tw / 2, ty, tw, 13, 6.5f, hair, Theme.accent(0x90), Theme.accent2(0x50), Theme.accent2(0x50), Theme.accent(0x90));
            float tx = mid - tw / 2 + 8;
            Fonts.drawV(ctx, deg, tx, ty + 6.5f, 0xFFF4F6FA, true, 0.78f);
            Render2D.circle(ctx, tx + dw + 2.5f, ty + 6.5f, 1, Theme.accent(0xA0));
            Fonts.drawV(ctx, dir, tx + dw + 6, ty + 6.5f, accent, true, 0.66f);
        }
    }

    private double target() {
        return heading();
    }

    private void dial(DrawContext ctx, float s) {
        float c = DIAL_R + 4;
        float hair = Math.max(Render2D.px() / s, 0.5f);
        int accent = Theme.accent();

        // A dark disc with a lighter middle, a fine rim, and the accent glowing round the top.
        Render2D.shadow(ctx, c - DIAL_R, c - DIAL_R + 2, DIAL_R * 2, DIAL_R * 2, DIAL_R, 10, alpha(0xC0000000, 1));
        Render2D.circle(ctx, c, c, DIAL_R, alpha(0xF20A0C12, 1));
        Render2D.circle(ctx, c, c, DIAL_R - 10, alpha(0xF2151924, 1));
        Render2D.ring(ctx, c, c, DIAL_R, hair * 1.4f, 0x2EFFFFFF);
        Render2D.ring(ctx, c, c, DIAL_R - 10, hair, 0x18FFFFFF);
        Render2D.arc(ctx, c, c, DIAL_R + 0.5f, 1.8f, 225, 45, Theme.accent(0), Theme.accent(0xE0));
        Render2D.arc(ctx, c, c, DIAL_R + 0.5f, 1.8f, 270, 45, Theme.accent(0xE0), Theme.accent(0));

        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(turn(shown, deg));
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            if (deg % 45 == 0) {
                String point = POINTS[deg / 45];
                boolean cardinal = deg % 90 == 0;
                float pr = DIAL_R - 15.5f;
                int color = deg == 0 ? accent : cardinal ? 0xFFF4F6FA : 0xFF9AA1B0;
                if (deg == 0) Render2D.shadow(ctx, c + sin * pr - 5, c - cos * pr - 5, 10, 10, 5, 6, Theme.accent(0x70));
                Fonts.drawCentered(ctx, point, c + sin * pr, c - cos * pr, color, true, cardinal ? 0.78f : 0.5f);
                Render2D.line(ctx, c + sin * (DIAL_R - 1.5f), c - cos * (DIAL_R - 1.5f), c + sin * (DIAL_R - 7), c - cos * (DIAL_R - 7),
                        hair * 1.6f, deg == 0 ? accent : 0xC0FFFFFF);
            } else if (deg % 15 == 0) {
                Render2D.line(ctx, c + sin * (DIAL_R - 1.5f), c - cos * (DIAL_R - 1.5f), c + sin * (DIAL_R - 5.5f), c - cos * (DIAL_R - 5.5f), hair * 1.2f, 0x80FFFFFF);
            } else {
                Render2D.circle(ctx, c + sin * (DIAL_R - 3), c - cos * (DIAL_R - 3), hair * 0.7f, 0x50FFFFFF);
            }
        }

        for (Marker m : markers()) {
            double a = Math.toRadians(turn(shown, m.bearing()));
            float mr = DIAL_R - 3;
            float mx = c + (float) Math.sin(a) * mr, my = c - (float) Math.cos(a) * mr;
            Render2D.shadow(ctx, mx - 3, my - 3, 6, 6, 3, 4, ColorUtil.withAlpha(m.color(), 0x90));
            diamond(ctx, mx, my, 2.2f, m.color());
        }

        // A fixed glowing pointer at the top, and the heading in the middle.
        Render2D.shadow(ctx, c - 4, c - DIAL_R - 7, 8, 8, 4, 5, Theme.accent(0x90));
        diamond(ctx, c, c - DIAL_R - 3, 3f, accent);
        diamond(ctx, c, c - DIAL_R - 3, 1.3f, 0xFFFFFFFF);
        if (degrees.get()) {
            Fonts.drawCentered(ctx, reading(), c, c - 2.5f, 0xFFF4F6FA, true, 0.82f);
            Fonts.drawCentered(ctx, direction(), c, c + 6.5f, accent, true, 0.52f);
        }
    }
}
