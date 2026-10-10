package dev.maro.module.impl.movement;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.render.esp.Renderer3D;
import dev.maro.render.esp.ShapeMode;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coords Fly: type in where you want to go and it guides you there. A card on the HUD shows how far
 * it is in kilometres and metres, an arrow turning to where it is, which way to turn, how fast you
 * are closing in and when you will get there. A beam stands on the spot and a marker shows exactly
 * where it is on screen, waiting at the edge when it is behind you.
 *
 * <p>With an elytra it can fly you there: it steers you towards the coords while you glide, holds a
 * cruising height, glides down when you are close, and fires rockets from your hotbar to keep you
 * fast. Coords written for the other dimension are worked out for you (an Overworld spot in the
 * Nether is its eighth).
 */
public class CoordsFly extends Module implements HudElement {
    private static final String[] POINTS16 = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?k?");
    private static final int MARGIN = 4;
    private static final float CARD_W = 184;
    /** How far away the beam and marker are drawn at most: further targets are drawn this far along the way. */
    private static final double DRAW_RANGE = 220;

    /** The draw range, kept inside what the game draws at all at your render distance. */
    private static double drawRange() {
        return Math.max(32, Math.min(DRAW_RANGE, mc.gameRenderer.getFarPlaneDistance() * 0.7));
    }

    // ---- coords ---------------------------------------------------------------------------
    private final TextSetting coords = new TextSetting("Coords", "Where to go: X Z, or X Y Z. Commas, x: labels and 1.5k all work",
            "", 48, "X Z  or  X Y Z");
    private final ButtonSetting setHere = new ButtonSetting("Set To Here", "Make where you stand the coords", "Set", this::useHere);
    private final ButtonSetting paste = new ButtonSetting("Paste", "Take the coords you copied", "Paste", this::pasteCoords);
    private final ButtonSetting clear = new ButtonSetting("Clear", "Forget the coords", "Clear", () -> coords.set(""));
    private final ModeSetting world = new ModeSetting("Coords Are For",
            "Which dimension the coords were written for: in the other one they are worked out (Nether coords are an eighth of the Overworld's)",
            "This World", "This World", "Overworld", "Nether");
    private final BooleanSetting otherWorld = new BooleanSetting("Other World Coords", "Show the same spot in the Nether or Overworld too", true);

    // ---- guide card ---------------------------------------------------------------------------
    private final ModeSetting units = new ModeSetting("Units", "Kilometres and metres, or plain blocks", "Km & M", "Km & M", "Blocks");
    private final BooleanSetting turnHint = new BooleanSetting("Turn Hint", "Which way to turn and by how much", true);
    private final BooleanSetting progress = new BooleanSetting("Progress Bar", "How much of the way you have come", true);
    private final BooleanSetting speedEta = new BooleanSetting("Speed & ETA", "How fast you are closing in and when you will get there", true);
    private final BooleanSetting showCoords = new BooleanSetting("Show Coords", "The coords themselves and the height to climb or drop", true);
    private final NumberSetting opacity = new NumberSetting("Background Opacity", "How solid the card is", 85, 0, 100, 1).suffix("%");
    private final ButtonSetting position = new ButtonSetting("Position", "Drag the card where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this)));
    private final NumberSetting x = new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 100, 0, 100, 0.5).suffix("%");
    private final NumberSetting y = new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 42, 0, 100, 0.5).suffix("%");
    private final NumberSetting scale = new NumberSetting("Scale", "How big the card is", 1, 0.5, 2.5, 0.05).suffix("x");

    // ---- in the world ---------------------------------------------------------------------------
    private final BooleanSetting beam = new BooleanSetting("Beam", "A tall beam of light standing on the coords, seen through everything", true);
    private final ColorSetting beamColor = new ColorSetting("Beam Color", "The beam, marker and guide arrows", 0xFFB06BFF, true);
    private final BooleanSetting marker = new BooleanSetting("Screen Marker", "Exactly where the coords are on screen with the distance, at the edge when out of view", true);
    private final BooleanSetting arrows = new BooleanSetting("Guide Arrows", "Arrows on the way ahead, pointing to the coords", true);

    // ---- elytra ---------------------------------------------------------------------------
    private final BooleanSetting steer = new BooleanSetting("Auto Steer", "While you glide, turn you towards the coords", true);
    private final NumberSetting turnSpeed = new NumberSetting("Turn Speed", "How fast it turns you", 8, 1, 30, 0.5).suffix("°/t");
    private final BooleanSetting holdHeight = new BooleanSetting("Hold Height", "While you glide, climb or drop to a cruising height and stay there", false);
    private final NumberSetting cruise = new NumberSetting("Cruise Height", "The height to fly at", 200, -60, 320, 1);
    private final BooleanSetting glideDown = new BooleanSetting("Glide Down Near", "Close to the coords, glide down to them", true);
    private final NumberSetting descendAt = new NumberSetting("Glide Down From", "How far away to start gliding down", 120, 20, 600, 5);
    private final BooleanSetting rockets = new BooleanSetting("Auto Rocket", "Fire a rocket from your hand or hotbar when you slow down", false);
    private final NumberSetting rocketBelow = new NumberSetting("Rocket Below", "Fire one when slower than this", 24, 4, 60, 1).suffix(" m/s");
    private final NumberSetting rocketDelay = new NumberSetting("Rocket Delay", "The least time between two rockets", 2, 0.5, 6, 0.1).suffix("s");

    // ---- arriving ---------------------------------------------------------------------------
    private final NumberSetting arriveWithin = new NumberSetting("Arrive Within", "How close counts as there", 12, 2, 100, 1).suffix(" m");
    private final BooleanSetting arriveSound = new BooleanSetting("Arrive Sound", "A sound when you get there", true);
    private final BooleanSetting offOnArrive = new BooleanSetting("Turn Off There", "Switch Coords Fly off when you get there", false);

    private final List<SettingSection> sections;

    /** The coords in this world: x, y (NaN when not given), z. */
    public record Target(double x, double y, double z) {
        public boolean hasY() {
            return !Double.isNaN(y);
        }
    }

    private String lastText;
    private String lastWorld;
    private double startDistance = -1;
    private boolean arrived;
    private int rocketCooldown;
    private int rocketsUsed;
    private boolean boostSeen;
    private boolean warnedNoRockets;
    private double closing;
    private double speed;
    private int steeredTicks;

    public CoordsFly() {
        super("Coords Fly", "Type in coords and get guided there: km and m away, which way to turn, a beam on the spot and elytra auto steer", Category.MOVEMENT);
        SettingSection target = section("Coords", coords, setHere, paste, clear, world, otherWorld);
        SettingSection card = section("Guide", units, turnHint, progress, speedEta, showCoords, opacity);
        SettingSection inWorld = section("World", beam, beamColor, marker, arrows);
        SettingSection elytra = section("Elytra", steer, turnSpeed, holdHeight, cruise, glideDown, descendAt, rockets, rocketBelow, rocketDelay);
        SettingSection arriving = section("Arriving", arriveWithin, arriveSound, offOnArrive);
        SettingSection placement = section("Placement", position, x, y, scale);
        turnSpeed.visible(steer::get);
        cruise.visible(holdHeight::get);
        descendAt.visible(glideDown::get);
        rocketBelow.visible(rockets::get);
        rocketDelay.visible(rockets::get);
        beamColor.visible(() -> beam.get() || marker.get() || arrows.get());
        sections = List.of(target, card, inWorld, elytra, arriving, placement);
    }

    private SettingSection section(String name, Setting<?>... settings) {
        SettingSection section = new SettingSection(name);
        for (Setting<?> s : settings) section.add(add(s));
        return section;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        startDistance = -1;
        arrived = false;
        warnedNoRockets = false;
        lastText = null;
    }

    // ---- coords ---------------------------------------------------------------------------

    /**
     * The numbers in typed coords: two (X Z) or three (X Y Z), as doubles; null if it is neither.
     * "x: 100, z: -200", "100 64 -200" and "1.5k -2k" all read.
     */
    public static double[] parse(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT).replaceAll("[xyz]\\s*[:=]", " ");
        Matcher m = NUMBER.matcher(t);
        List<Double> found = new ArrayList<>();
        while (m.find()) {
            String n = m.group();
            boolean thousands = n.endsWith("k");
            try {
                double v = Double.parseDouble(thousands ? n.substring(0, n.length() - 1) : n);
                found.add(thousands ? v * 1000 : v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (found.size() == 2) return new double[] {found.get(0), Double.NaN, found.get(1)};
        if (found.size() == 3) return new double[] {found.get(0), found.get(1), found.get(2)};
        return null;
    }

    private static String dimension() {
        return mc.world.getRegistryKey().getValue().getPath();
    }

    /** How coords written for the chosen dimension scale into the one you are in. */
    private double dimensionScale() {
        String here = dimension();
        if (world.is("Overworld") && here.equals("the_nether")) return 1 / 8.0;
        if (world.is("Nether") && here.equals("overworld")) return 8;
        return 1;
    }

    /** The coords in the world you are in, the middle of the block; null when none are set or they do not read. */
    public Target target() {
        double[] p = parse(coords.get());
        if (p == null || !inGame()) return null;
        double s = dimensionScale();
        double tx = s == 1 && p[0] == Math.floor(p[0]) ? p[0] + 0.5 : p[0] * s;
        double tz = s == 1 && p[2] == Math.floor(p[2]) ? p[2] + 0.5 : p[2] * s;
        return new Target(tx, p[1], tz);
    }

    private void useHere() {
        if (!inGame()) return;
        BlockPos pos = mc.player.getBlockPos();
        world.set("This World");
        coords.set(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        Notifications.push(getName(), "Coords set to where you stand", Notifications.Type.SUCCESS);
    }

    private void pasteCoords() {
        String copied = mc.keyboard.getClipboard();
        if (parse(copied) == null) {
            Notifications.push(getName(), "What you copied is not coords", Notifications.Type.ERROR);
            return;
        }
        coords.set(copied.trim());
        Notifications.push(getName(), "Coords pasted: " + coords.get(), Notifications.Type.SUCCESS);
    }

    // ---- where it is ---------------------------------------------------------------------------

    private static double wrap(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    /** The shortest turn from one bearing to another, -180 to 180: positive is to the right. */
    private static double turn(double from, double to) {
        return ((to - from) % 360 + 540) % 360 - 180;
    }

    /** Your heading as a compass bearing: 0 north, 90 east. */
    private static double heading() {
        return wrap(mc.player.getYaw() + 180);
    }

    /** The compass bearing from you to the target. */
    public double bearing(Target t) {
        return wrap(Math.toDegrees(Math.atan2(t.x() - mc.player.getX(), -(t.z() - mc.player.getZ()))));
    }

    /** How far it is across the ground. */
    public double distance(Target t) {
        return Math.hypot(t.x() - mc.player.getX(), t.z() - mc.player.getZ());
    }

    /** Which way to turn to face it: -180 to 180, positive to the right. */
    public double relative(Target t) {
        return turn(heading(), bearing(t));
    }

    /** "640 m", "2.41 km", "18.5 km", "312 km". */
    public static String distanceText(double metres) {
        if (metres < 1000) return Math.round(metres) + " m";
        if (metres < 10000) return String.format(Locale.ROOT, "%.2f km", metres / 1000);
        if (metres < 100000) return String.format(Locale.ROOT, "%.1f km", metres / 1000);
        return Math.round(metres / 1000) + " km";
    }

    /** "2 km 410 m", or "640 m" under a kilometre. */
    public static String kmAndM(double metres) {
        long total = Math.round(metres);
        if (total < 1000) return total + " m";
        return total / 1000 + " km " + total % 1000 + " m";
    }

    private static String grouped(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(Math.floor(v)));
    }

    private String distanceShown(double metres) {
        return units.is("Blocks") ? grouped(metres) + " blocks" : distanceText(metres);
    }

    /** What to do to face it: "Straight ahead", "Turn right 34°", "Turn around". */
    public String turnText(double rel) {
        double a = Math.abs(rel);
        if (a < 4) return "Straight ahead";
        if (a > 160) return "Behind you, turn around";
        return (rel > 0 ? "Turn right " : "Turn left ") + Math.round(a) + "°";
    }

    private static String eta(double seconds) {
        if (!Double.isFinite(seconds) || seconds > 359999) return "--";
        long s = Math.round(seconds);
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m " + s % 60 + "s";
        return s / 3600 + "h " + s / 60 % 60 + "m";
    }

    /** The same spot in the other dimension, as you would type it there; empty in the End. */
    private String otherWorldText(Target t) {
        String here = dimension();
        if (here.equals("overworld")) return "Nether  " + grouped(t.x() / 8) + "  " + grouped(t.z() / 8);
        if (here.equals("the_nether")) return "Overworld  " + grouped(t.x() * 8) + "  " + grouped(t.z() * 8);
        return "";
    }

    // ---- flying ---------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (rocketCooldown > 0) rocketCooldown--;
        String text = coords.get(), here = dimension() + world.get();
        if (!text.equals(lastText) || !here.equals(lastWorld)) {
            lastText = text;
            lastWorld = here;
            startDistance = -1;
            arrived = false;
            warnedNoRockets = false;
        }
        Target t = target();
        Vec3d v = mc.player.getVelocity();
        speed = speed * 0.8 + v.length() * 20 * 0.2;
        if (t == null) return;

        double dist = distance(t);
        if (startDistance < dist) startDistance = dist;
        double dx = t.x() - mc.player.getX(), dz = t.z() - mc.player.getZ();
        double along = dist < 1e-6 ? 0 : (v.x * dx + v.z * dz) / dist * 20;
        closing = closing * 0.85 + along * 0.15;

        if (!arrived && dist <= arriveWithin.get()) {
            arrived = true;
            Notifications.push("You're there", "Arrived at " + coordsText(t), Notifications.Type.SUCCESS);
            if (arriveSound.get()) mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            if (offOnArrive.get()) {
                setEnabled(false);
                return;
            }
        } else if (arrived && dist > arriveWithin.get() * 2 + 8) {
            arrived = false;
        }

        if (!mc.player.isGliding() || arrived) return;
        if (boosting()) boostSeen = true;
        if (steer.get()) {
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float now = mc.player.getYaw();
            float step = MathHelper.clamp(MathHelper.wrapDegrees(want - now), -turnSpeed.getFloat(), turnSpeed.getFloat());
            mc.player.setYaw(now + step);
            steeredTicks++;
        }
        if (holdHeight.get() || glideDown.get()) {
            Float pitch = wantedPitch(t, dist);
            if (pitch != null) {
                float now = mc.player.getPitch();
                mc.player.setPitch(now + MathHelper.clamp(pitch - now, -3f, 3f));
            }
        }
        boolean landing = glideDown.get() && dist < descendAt.get();
        if (rockets.get() && !landing && rocketCooldown == 0 && speed < rocketBelow.get() && !boosting()) useRocket();
    }

    /**
     * The pitch to glide at: close in, down towards the coords; otherwise towards the cruising
     * height, nose down a little if too slow to climb. Null to leave it to you.
     */
    private Float wantedPitch(Target t, double dist) {
        if (glideDown.get() && dist < descendAt.get()) {
            double ground = groundAt(t);
            if (!Double.isNaN(ground)) {
                // Aim a little above the spot, never steeper than 32 degrees, levelling out as you get there.
                double drop = mc.player.getY() - (ground + 1.5);
                return (float) MathHelper.clamp(Math.toDegrees(Math.atan2(drop, Math.max(dist - arriveWithin.get() * 0.5, 4))), -15, 32);
            }
        }
        if (!holdHeight.get()) return null;
        double climb = cruise.get() - mc.player.getY();
        float pitch = (float) MathHelper.clamp(-climb * 0.8, -35, 25);
        if (pitch < 0 && speed < 12 && !boosting() && !hasRocket()) pitch = 8;
        return pitch;
    }

    /** The height to land at: the coords' Y if given, else the ground there once it is loaded; NaN if unknown. */
    private double groundAt(Target t) {
        if (t.hasY()) return t.y();
        int bx = MathHelper.floor(t.x()), bz = MathHelper.floor(t.z());
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;
        return mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
    }

    /** Whether a rocket is pushing you now. */
    private boolean boosting() {
        return !mc.world.getEntitiesByClass(FireworkRocketEntity.class, mc.player.getBoundingBox().expand(3), e -> true).isEmpty();
    }

    private int rocketSlot() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        return -1;
    }

    private boolean hasRocket() {
        return mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET) || rocketSlot() >= 0;
    }

    private void useRocket() {
        rocketCooldown = Math.round(rocketDelay.getFloat() * 20);
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        } else {
            int slot = rocketSlot();
            if (slot < 0) {
                if (!warnedNoRockets) Notifications.push(getName(), "No rockets in your hotbar or off hand", Notifications.Type.WARNING);
                warnedNoRockets = true;
                return;
            }
            var inventory = mc.player.getInventory();
            int previous = inventory.getSelectedSlot();
            inventory.setSelectedSlot(slot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            inventory.setSelectedSlot(previous);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
        rocketsUsed++;
    }

    // ---- in the world ---------------------------------------------------------------------------

    /** Where the target is drawn: itself when near, else this far along the way to it. */
    private Vec3d drawnAt(Target t, Vec3d camera) {
        double dx = t.x() - camera.x, dz = t.z() - camera.z;
        double d = Math.hypot(dx, dz);
        double y = t.hasY() ? t.y() : camera.y;
        double range = drawRange();
        if (d <= range) return new Vec3d(t.x(), y, t.z());
        double k = range / d;
        return new Vec3d(camera.x + dx * k, camera.y + (y - camera.y) * k, camera.z + dz * k);
    }

    /** Draws the beam and guide arrows. */
    public void render(Renderer3D renderer, float tickDelta) {
        Target t = target();
        if (t == null || !inGame()) return;
        Vec3d camera = renderer.camera();
        int color = beamColor.get();
        double time = (System.currentTimeMillis() % 100000L) / 1000.0;
        if (beam.get()) {
            Vec3d at = drawnAt(t, camera);
            double far = Math.hypot(at.x - camera.x, at.z - camera.z);
            double w = Math.max(0.18, far * 0.004);
            double bottom = mc.world.getBottomY(), top = mc.world.getBottomY() + mc.world.getHeight() + 64;
            if (far >= drawRange() - 1) {
                bottom = camera.y - 90;
                top = camera.y + 160;
            }
            renderer.throughWalls(true);
            renderer.box(at.x - w * 3, bottom, at.z - w * 3, at.x + w * 3, top, at.z + w * 3,
                    new Color(ColorUtil.withAlpha(color, 0x22)), new Color(0), ShapeMode.Sides, 0);
            renderer.box(at.x - w, bottom, at.z - w, at.x + w, top, at.z + w,
                    new Color(ColorUtil.withAlpha(color, 0x90)), new Color(ColorUtil.withAlpha(color, 0xE0)), ShapeMode.Both, 0);
            // Rings rising up the beam.
            for (int i = 0; i < 3; i++) {
                double phase = (time * 0.35 + i / 3.0) % 1.0;
                double ry = at.y + (phase - 0.15) * 24;
                double r = w * 6 + phase * w * 4;
                int a = (int) (0xC0 * (1 - phase));
                ring(renderer, at.x, ry, at.z, r, new Color(ColorUtil.withAlpha(color, a)));
            }
        }
        if (arrows.get() && distance(t) > arriveWithin.get()) {
            double dx = t.x() - mc.player.getX(), dz = t.z() - mc.player.getZ();
            double d = Math.hypot(dx, dz);
            double ux = dx / d, uz = dz / d;
            double px = MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX());
            double pz = MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ());
            double py = MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY()) + (mc.player.isGliding() ? -1.2 : 0.06);
            double flow = (time * 3) % 3;
            renderer.lineWidth(2.5f);
            for (int i = 0; i < 10; i++) {
                double along = 2.5 + i * 3 + flow;
                if (along > d) break;
                double cx = px + ux * along, cz = pz + uz * along;
                double size = 0.55;
                int a = (int) (0xE0 * (1 - along / 33.0));
                Color c = new Color(ColorUtil.withAlpha(color, Math.max(0x20, a)));
                // A chevron: two strokes meeting at the front.
                double fx = cx + ux * size, fz = cz + uz * size;
                double lx = cx - uz * size - ux * size * 0.2, lz = cz + ux * size - uz * size * 0.2;
                double rx = cx + uz * size - ux * size * 0.2, rz = cz - ux * size - uz * size * 0.2;
                renderer.line(lx, py, lz, fx, py, fz, c);
                renderer.line(rx, py, rz, fx, py, fz, c);
            }
            renderer.lineWidth(1.5f);
        }
    }

    private static void ring(Renderer3D renderer, double x, double y, double z, double r, Color color) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            renderer.line(x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, x + Math.cos(a2) * r, y, z + Math.sin(a2) * r, color);
        }
    }

    // ---- the card ---------------------------------------------------------------------------

    private int alpha(int color) {
        return ColorUtil.withAlpha(color, Math.round((color >>> 24) * opacity.getFloat() / 100f));
    }

    private String coordsText(Target t) {
        String s = "X " + grouped(t.x());
        if (t.hasY()) s += "  Y " + Math.round(t.y());
        return s + "  Z " + grouped(t.z());
    }

    private float cardHeight() {
        Target t = inGame() ? target() : null;
        if (t == null) return 40;
        float h = 46;
        if (progress.get()) h += 9;
        if (showCoords.get()) h += 11;
        if (speedEta.get()) h += 11;
        if (otherWorld.get() && inGame() && !otherWorldText(t).isEmpty()) h += 11;
        return h + 4;
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
        return CARD_W * hudScale();
    }

    @Override
    public float hudHeight() {
        return cardHeight() * hudScale();
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
        float rx = roomX(), ry = roomY();
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

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden) return;
        Target t = target();
        if (t != null && marker.get()) screenMarker(ctx, t);
        float s = hudScale();
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(hudLeft(), hudTop());
        m.scale(s, s);
        Fonts.beginRaw();
        try {
            card(ctx, t, s);
        } finally {
            Fonts.endRaw();
            m.popMatrix();
        }
    }

    private void card(DrawContext ctx, Target t, float s) {
        float w = CARD_W, h = cardHeight(), r = 9;
        float hair = Math.max(Render2D.px() / s, 0.5f);
        int accent = Theme.accent();
        Render2D.shadow(ctx, 0, 2, w, h, r, 10, alpha(0xC0000000));
        Render2D.roundRect(ctx, 0, 0, w, h, r, alpha(0xF2181C27), alpha(0xF2181C27), alpha(0xF20A0C12), alpha(0xF20A0C12));
        Render2D.roundOutline(ctx, 0, 0, w, h, r, hair, 0x30FFFFFF, 0x30FFFFFF, 0x0CFFFFFF, 0x0CFFFFFF);
        Render2D.rectGradient(ctx, r, 0.6f, w / 2 - r, 1.2f, Theme.accent(0), Theme.accent(0xC0), Theme.accent(0xC0), Theme.accent(0));
        Render2D.rectGradient(ctx, w / 2, 0.6f, w / 2 - r, 1.2f, Theme.accent(0xC0), Theme.accent(0), Theme.accent(0), Theme.accent(0xC0));

        if (t == null) {
            Fonts.draw(ctx, "COORDS FLY", 10, 9, accent, true, 0.62f);
            Fonts.draw(ctx, "No coords yet", 10, 19, 0xFFF4F6FA, true, 0.8f);
            Fonts.draw(ctx, "Type them in the module's Coords setting", 10, 29, 0xFF7D8494, false, 0.55f);
            return;
        }

        double dist = distance(t);
        double rel = relative(t);
        boolean aligned = Math.abs(rel) < 8;
        int pointer = arrived ? Theme.GREEN : aligned ? Theme.GREEN : accent;

        // The dial: an arrow turned to where the coords are from where you look.
        float cx = 24, cy = 24, dr = 17;
        Render2D.shadow(ctx, cx - dr, cy - dr + 1, dr * 2, dr * 2, dr, 6, alpha(0x90000000));
        Render2D.circle(ctx, cx, cy, dr, alpha(0xF20A0C12));
        Render2D.ring(ctx, cx, cy, dr, hair * 1.3f, 0x2EFFFFFF);
        Render2D.arc(ctx, cx, cy, dr + 0.4f, 1.6f, (float) (rel - 50 - 90), 50, ColorUtil.withAlpha(pointer, 0), ColorUtil.withAlpha(pointer, 0xE0));
        Render2D.arc(ctx, cx, cy, dr + 0.4f, 1.6f, (float) (rel - 90), 50, ColorUtil.withAlpha(pointer, 0xE0), ColorUtil.withAlpha(pointer, 0));
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate((float) Math.toRadians(arrived ? 0 : rel));
        if (arrived) {
            Render2D.circle(ctx, 0, 0, 5, pointer);
        } else {
            Render2D.shadow(ctx, -4, -13, 8, 8, 4, 5, ColorUtil.withAlpha(pointer, 0x90));
            Render2D.line(ctx, 0, 10, 0, -11.5f, 2.6f, pointer);
            Render2D.line(ctx, 0, -12.5f, -6.5f, -5, 2.6f, pointer);
            Render2D.line(ctx, 0, -12.5f, 6.5f, -5, 2.6f, pointer);
            Render2D.circle(ctx, 0, 10, 2.2f, ColorUtil.withAlpha(pointer, 0xA0));
        }
        m.popMatrix();

        // Distance, big; underneath it in km and m and the compass direction; then which way to turn.
        float tx = 50;
        String big = arrived ? "You're there" : distanceShown(dist);
        Fonts.draw(ctx, big, tx, 7, arrived ? Theme.GREEN : 0xFFF4F6FA, true, 1.15f);
        String bearing = POINTS16[(int) Math.round(bearing(t) / 22.5) % 16] + " " + Math.round(bearing(t)) % 360 + "°";
        String sub = (units.is("Blocks") ? distanceText(dist) : kmAndM(dist)) + "  ·  " + bearing;
        Fonts.draw(ctx, sub, tx, 22, 0xFF9AA1B0, false, 0.6f);
        if (turnHint.get()) {
            String hint = arrived ? "Within " + arriveWithin.getInt() + " m of the coords" : turnText(rel);
            Fonts.draw(ctx, hint, tx, 32, aligned || arrived ? Theme.GREEN : accent, true, 0.62f);
        }

        float yy = 46;
        if (progress.get()) {
            double done = startDistance <= 0 ? 0 : MathHelper.clamp(1 - dist / startDistance, 0, 1);
            float bw = w - 20;
            Render2D.roundRect(ctx, 10, yy, bw, 3, 1.5f, 0x30FFFFFF);
            if (done > 0) {
                float fw = (float) Math.max(3, bw * done);
                Render2D.roundGradientH(ctx, 10, yy, fw, 3, 1.5f, Theme.accent(), Theme.accent2());
                Render2D.shadow(ctx, 10 + fw - 3, yy - 1.5f, 6, 6, 3, 4, Theme.accent2(0x90));
            }
            Fonts.drawRight(ctx, Math.round(done * 100) + "%", w - 10, yy - 5, 0xFF7D8494, false, 0.5f);
            yy += 9;
        }
        if (showCoords.get()) {
            Fonts.draw(ctx, coordsText(t), 10, yy, 0xFFDCE0E8, true, 0.6f);
            if (t.hasY()) {
                long dy = Math.round(t.y() - mc.player.getY());
                String climb = dy == 0 ? "level" : (dy > 0 ? "↑ " : "↓ ") + Math.abs(dy) + " m";
                Fonts.drawRight(ctx, climb, w - 10, yy + Fonts.height(0.56f) / 2f, dy >= 0 ? 0xFF8FD3FF : 0xFFFFC27A, true, 0.56f);
            }
            yy += 11;
        }
        if (speedEta.get()) {
            String sp = Math.round(speed) + " m/s";
            String eta = arrived ? "here" : closing > 0.5 ? eta(dist / closing) : "--";
            Fonts.draw(ctx, sp, 10, yy, 0xFFDCE0E8, true, 0.6f);
            float sw = Fonts.width(sp, true, 0.6f);
            Fonts.draw(ctx, "  ·  " + Math.round(speed * 3.6) + " km/h", 10 + sw, yy, 0xFF7D8494, false, 0.6f);
            Fonts.drawRight(ctx, "ETA " + eta, w - 10, yy + Fonts.height(0.6f) / 2f, closing > 0.5 || arrived ? accent : 0xFF7D8494, true, 0.6f);
            yy += 11;
        }
        if (otherWorld.get()) {
            String other = otherWorldText(t);
            if (!other.isEmpty()) {
                Fonts.draw(ctx, other, 10, yy, 0xFF7D8494, false, 0.56f);
            }
        }
    }

    /** The diamond on the spot itself, or an arrow at the screen's edge pointing round to it. */
    private void screenMarker(DrawContext ctx, Target t) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d at = drawnAt(t, camera);
        float[] p = BlockEspRenderer.toScreen(at.x, at.y + (t.hasY() ? 0.5 : 0), at.z, camera, w, h);
        int color = beamColor.get() | 0xFF000000;
        String label = arrived ? "Here" : distanceShown(distance(t));
        float pad = 18;
        if (p != null && p[0] > pad && p[0] < w - pad && p[1] > pad && p[1] < h - pad) {
            float bob = (float) Math.sin(System.currentTimeMillis() / 300.0) * 1.2f;
            Render2D.shadow(ctx, p[0] - 6, p[1] - 6 + bob, 12, 12, 6, 6, ColorUtil.withAlpha(color, 0x90));
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(p[0], p[1] + bob);
            m.rotate((float) Math.toRadians(45));
            Render2D.roundRect(ctx, -4, -4, 8, 8, 1.5f, color);
            Render2D.roundRect(ctx, -1.8f, -1.8f, 3.6f, 3.6f, 0.8f, 0xFFFFFFFF);
            m.popMatrix();
            Fonts.beginRaw();
            try {
                float size = 0.7f, tw = Fonts.width(label, true, size) + 12;
                float ly = p[1] - 18 + bob;
                Render2D.roundRect(ctx, p[0] - tw / 2f, ly - 6.5f, tw, 13, 6.5f, 0xD00D0F14);
                Render2D.roundOutline(ctx, p[0] - tw / 2f, ly - 6.5f, tw, 13, 6.5f, 0.6f, ColorUtil.withAlpha(color, 0xB0));
                Fonts.drawCentered(ctx, label, p[0], ly, 0xFFF4F6FA, true, size);
            } finally {
                Fonts.endRaw();
            }
            return;
        }
        // Out of view: round the edge of the screen, at the side it is on.
        double rel = Math.toRadians(relative(t));
        float rx = w / 2f - 30, ry = h / 2f - 30;
        float ex = w / 2f + (float) Math.sin(rel) * rx, ey = h / 2f - (float) Math.cos(rel) * ry;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ex, ey);
        m.rotate((float) rel);
        Render2D.shadow(ctx, -6, -9, 12, 12, 6, 6, ColorUtil.withAlpha(color, 0x80));
        Render2D.line(ctx, 0, -8, -6, 0, 2.4f, color);
        Render2D.line(ctx, 0, -8, 6, 0, 2.4f, color);
        m.popMatrix();
        Fonts.beginRaw();
        try {
            Fonts.drawCentered(ctx, label, ex - (float) Math.sin(rel) * 14, ey + (float) Math.cos(rel) * 12, 0xFFF4F6FA, true, 0.62f);
        } finally {
            Fonts.endRaw();
        }
    }

    // ---- for tests and other modules ---------------------------------------------------------

    public boolean arrived() {
        return arrived;
    }

    public int rocketsUsed() {
        return rocketsUsed;
    }

    public boolean boostSeen() {
        return boostSeen;
    }

    public int steeredTicks() {
        return steeredTicks;
    }

    public double closingSpeed() {
        return closing;
    }

    /** Whether the target should show on the compass: on, with coords that read. */
    public boolean showsTarget() {
        return isEnabled() && target() != null;
    }
}
