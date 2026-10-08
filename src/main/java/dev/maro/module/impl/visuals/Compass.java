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
        for (var s : new dev.maro.setting.Setting<?>[]{deathMarker, spawnMarker, homeMarker, distances, setHome, clearHome}) markers.add(add(s));
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

    private int panel() {
        return ColorUtil.withAlpha(0xFF0B0D12, Math.round(255 * opacity.getFloat() / 100f));
    }

    private void strip(DrawContext ctx, float s) {
        float w = width.getFloat();
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float fov = view.getFloat();
        float mid = w / 2f;

        Render2D.shadow(ctx, 0, 1, w, STRIP_H, 8, 7, ColorUtil.withAlpha(0xFF000000, Math.round(90 * opacity.getFloat() / 100f)));
        Render2D.roundRect(ctx, 0, 0, w, STRIP_H, 8, panel());
        Render2D.roundOutline(ctx, 0, 0, w, STRIP_H, 8, hair, 0x1CFFFFFF);

        // Ticks and points, fading out towards the ends of the strip.
        for (int deg = 0; deg < 360; deg += 5) {
            double rel = turn(shown, deg);
            if (Math.abs(rel) > fov / 2) continue;
            float px = (float) (mid + rel / fov * (w - 16));
            float fade = (float) Math.max(0, 1 - Math.pow(Math.abs(rel) / (fov / 2), 3));
            if (deg % 45 == 0) {
                String point = POINTS[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int color = deg == 0 ? Theme.accent() : cardinal ? Theme.TEXT : ColorUtil.withAlpha(Theme.TEXT, 170);
                Fonts.drawCentered(ctx, point, px, STRIP_H / 2f, ColorUtil.mulAlpha(color, fade), true, cardinal ? 0.9f : 0.7f);
            } else {
                boolean major = deg % 15 == 0;
                float tick = major ? 5 : 3;
                Render2D.rect(ctx, px - hair / 2, STRIP_H - 3 - tick, hair, tick, ColorUtil.mulAlpha(0x8CFFFFFF, fade));
            }
        }

        // Markers: on the strip if in view, waiting at the nearer end if not.
        for (Marker m : markers()) {
            double rel = turn(shown, m.bearing());
            boolean inView = Math.abs(rel) <= fov / 2;
            float px = (float) (mid + Math.max(-fov / 2, Math.min(fov / 2, rel)) / fov * (w - 16));
            float my = 4.5f;
            Render2D.circle(ctx, px, my, 2.6f, inView ? m.color() : ColorUtil.withAlpha(m.color(), 140));
            Render2D.ring(ctx, px, my, 3.6f, 0.8f, ColorUtil.withAlpha(0xFF000000, 120));
            if (distances.get()) {
                Fonts.drawCentered(ctx, m.label() + " " + distance(m.distance()), px, STRIP_H + 7, ColorUtil.withAlpha(m.color(), inView ? 230 : 150), true, 0.6f);
            }
        }

        // The middle: a notch at the top and a fine line through.
        Render2D.rect(ctx, mid - hair / 2, 2, hair, STRIP_H - 4, Theme.accent(150));
        Render2D.roundRect(ctx, mid - 4, -2, 8, 4, 2, Theme.accent());

        if (degrees.get()) {
            String text = Math.round(target()) % 360 + "°  " + POINTS[(int) Math.round(target() / 45) % 8];
            float tw = Fonts.width(text, true, 0.75f) + 12;
            float ty = STRIP_H + (distances.get() && !markers().isEmpty() ? 14 : 4);
            Render2D.roundRect(ctx, mid - tw / 2, ty, tw, 12, 6, panel());
            Fonts.drawCentered(ctx, text, mid, ty + 6, Theme.TEXT, true, 0.75f);
        }
    }

    private double target() {
        return heading();
    }

    private void dial(DrawContext ctx, float s) {
        float c = DIAL_R + 4;
        float hair = Math.max(Render2D.px() / s, 0.5f);

        Render2D.shadow(ctx, c - DIAL_R, c - DIAL_R + 1, DIAL_R * 2, DIAL_R * 2, DIAL_R, 8, ColorUtil.withAlpha(0xFF000000, Math.round(100 * opacity.getFloat() / 100f)));
        Render2D.circle(ctx, c, c, DIAL_R, panel());
        Render2D.ring(ctx, c, c, DIAL_R, hair * 1.5f, 0x22FFFFFF);
        Render2D.ring(ctx, c, c, DIAL_R - 9, hair, 0x14FFFFFF);

        for (int deg = 0; deg < 360; deg += 15) {
            double a = Math.toRadians(turn(shown, deg));
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            if (deg % 45 == 0) {
                String point = POINTS[deg / 45];
                boolean cardinal = deg % 90 == 0;
                float r = DIAL_R - 9;
                int color = deg == 0 ? Theme.accent() : cardinal ? Theme.TEXT : ColorUtil.withAlpha(Theme.TEXT, 150);
                Fonts.drawCentered(ctx, point, c + sin * r, c - cos * r, color, true, cardinal ? 0.8f : 0.55f);
            } else {
                float r1 = DIAL_R - 2, r2 = DIAL_R - 5;
                Render2D.line(ctx, c + sin * r1, c - cos * r1, c + sin * r2, c - cos * r2, hair * 1.2f, 0x66FFFFFF);
            }
        }

        for (Marker m : markers()) {
            double a = Math.toRadians(turn(shown, m.bearing()));
            float r = DIAL_R - 2.5f;
            Render2D.circle(ctx, c + (float) Math.sin(a) * r, c - (float) Math.cos(a) * r, 2.4f, m.color());
        }

        // Fixed pointer at the top, and the heading in the middle.
        Render2D.roundRect(ctx, c - 1.5f, c - DIAL_R - 3, 3, 7, 1.5f, Theme.accent());
        if (degrees.get()) {
            Fonts.drawCentered(ctx, Math.round(target()) % 360 + "°", c, c, Theme.TEXT, true, 0.8f);
        }
    }
}
