package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.nathan.render.SpotifyHudAnimation;
import dev.maro.render.esp.Renderer3D;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ground-anchored, elapsed-time jump effects. Spotify uses the shared live Windows audio feed. */
public final class JumpCircle extends Module {
    private static final int SEGMENTS = 128, MAX_CIRCLES = 32;
    private static final double TAU = Math.PI * 2;
    private static final double[] SIN = new double[SEGMENTS + 1], COS = new double[SEGMENTS + 1];
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            SIN[i] = Math.sin(i * TAU / SEGMENTS); COS[i] = Math.cos(i * TAU / SEGMENTS);
        }
    }
    private final ModeSetting style = add(new ModeSetting("Style", "Neon halos, orbiting accents, or layered ripples", "Orbit", "Neon", "Orbit", "Ripple"));
    private final ModeSetting trigger = add(new ModeSetting("Trigger", "When to leave a circle at your feet", "Jump", "Jump", "Land", "Both"));
    private final ModeSetting who = add(new ModeSetting("Players", "Whose jumps create circles on your screen", "Self", "Self", "Everyone"));
    private final ColorSetting primary = add(new ColorSetting("Color", "First color of the flowing gradient", 0xFFAA65FF));
    private final ColorSetting secondary = add(new ColorSetting("Accent", "Second color of the flowing gradient", 0xFF45E8FF));
    private final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", "Flow through the full color spectrum", false));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Maximum circle radius", 1.8, .5, 4, .1).suffix("m"));
    private final NumberSetting lifetime = add(new NumberSetting("Lifetime", "How long each circle lasts", 1.6, .5, 4, .1).suffix("s"));
    private final NumberSetting width = add(new NumberSetting("Width", "Width of the bright ring", .035, .015, .12, .005).suffix("m"));
    private final NumberSetting glow = add(new NumberSetting("Glow", "Strength of the soft feathered light", 70, 0, 100, 1).suffix("%"));
    private final BooleanSetting spotifySync = add(new BooleanSetting("Spotify Sync", "Pulse with live bass while Spotify plays; other desktop audio can also affect the Windows output feed", false).onChange(v -> syncMedia()));
    private final BooleanSetting beatRipples = add(new BooleanSetting("Beat Ripples", "Create a smaller circle at your feet on bass hits, even while standing", true).visible(spotifySync::get));
    private final NumberSetting sensitivity = add(new NumberSetting("Beat Strength", "How strongly the rings respond to live audio", 1.5, .5, 3, .1).suffix("x").visible(spotifySync::get));
    private final List<SettingSection> sections = List.of(
            SettingSection.of("Circles", style, trigger, who, radius, lifetime, width),
            SettingSection.of("Appearance", primary, secondary, rainbow, glow),
            SettingSection.of("Spotify", spotifySync, beatRipples, sensitivity));

    private record Circle(double x, double y, double z, double born, double duration, double size, double phase) { }
    private final List<Circle> circles = new ArrayList<>();
    private final Map<UUID, Boolean> grounded = new HashMap<>();
    private final SpotifyHudAnimation music = new SpotifyHudAnimation();
    private final float[] audioBands = new float[7];
    private ClientWorld world;
    private SpotifyMedia media;
    private long frameAt;
    private double lastBeatAt = -100, energyFloor, previousEnergy;
    private int emitted;

    public JumpCircle() {
        super("Jump Circle", "Neon circles bloom under your jumps, with orbiting accents and optional Spotify beat sync", Category.VISUALS);
    }
    @Override public List<SettingSection> getSettingSections() { return sections; }
    @Override protected void onEnable() { reset(); syncMedia(); }
    @Override protected void onDisable() { reset(); if (media != null) { SpotifySession.release(); media = null; } }

    private void reset() {
        circles.clear(); grounded.clear(); world = null; frameAt = 0;
        energyFloor = previousEnergy = 0; lastBeatAt = -100; music.reset(false);
    }
    private void syncMedia() {
        boolean wanted = isEnabled() && spotifySync.get();
        if (wanted && media == null) { media = SpotifySession.acquire(); frameAt = 0; music.reset(false); }
        else if (!wanted && media != null) { SpotifySession.release(); media = null; music.reset(false); }
    }
    private void emit(double x, double y, double z, double now, boolean beat) {
        if (circles.size() == MAX_CIRCLES) circles.removeFirst();
        circles.add(new Circle(x, y + .024, z, now, lifetime.get() * 20 * (beat ? .65 : 1),
                radius.get() * (beat ? .65 : 1), (emitted++ * .38196601125) % 1));
    }

    @Override public void onTick() {
        syncMedia();
        if (!inGame()) { if (world != null) reset(); return; }
        if (world != mc.world) { reset(); world = mc.world; }
        double now = world.getTime();
        circles.removeIf(c -> now - c.born >= c.duration);
        for (PlayerEntity player : world.getPlayers()) {
            if (player != mc.player && !who.is("Everyone")) continue;
            boolean down = player.isOnGround();
            Boolean was = grounded.put(player.getUuid(), down);
            if (was == null || player.isSpectator() || player.getAbilities().flying || player.hasVehicle()
                    || player.isTouchingWater() || player.isClimbing()) continue;
            if (was && !down && player.getY() > player.lastY && !trigger.is("Land"))
                emit(player.lastX, player.lastY, player.lastZ, now, false);
            else if (!was && down && !trigger.is("Jump")) emit(player.getX(), player.getY(), player.getZ(), now, false);
        }
        grounded.keySet().removeIf(id -> world.getPlayerByUuid(id) == null);
    }

    private double updateMusic(double now) {
        long nanos = System.nanoTime();
        double dt = frameAt == 0 ? 1.0 / 60 : Math.max(0, Math.min(.1, (nanos - frameAt) / 1e9));
        frameAt = nanos;
        boolean playing = media != null && media.state().available() && media.state().playing();
        float[] measured = playing ? media.audioLevels() : null;
        double energy = 0;
        for (int i = 0; i < audioBands.length; i++) {
            audioBands[i] = measured != null && i < measured.length ? measured[i] : 0;
            if (i < 3 && Float.isFinite(audioBands[i])) energy += Math.max(0, audioBands[i]) * (i == 0 ? .5 : .25);
        }
        music.step(dt, playing, audioBands, false, false, false, false, false);
        double previousFloor = energyFloor;
        energyFloor += (energy - energyFloor) * (1 - Math.exp(-3 * dt));
        if (playing && beatRipples.get() && !mc.isPaused() && mc.player.isOnGround()
                && energy > .08 / sensitivity.get() && energy > previousFloor + .045 / sensitivity.get()
                && energy - previousEnergy > .025 / sensitivity.get()
                && now - lastBeatAt >= 4) {
            var pos = mc.player.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false));
            emit(pos.x, pos.y, pos.z, now, true); lastBeatAt = now;
        }
        previousEnergy = energy;
        return spotifySync.get() ? Math.min(1, music.beat() * sensitivity.get()) : 0;
    }

    public void render(Renderer3D renderer, float tickDelta) {
        if (!inGame() || world != mc.world) return;
        double now = world.getTime() + tickDelta;
        double beat = updateMusic(now);
        renderer.throughWalls(false);
        for (Circle circle : circles) {
            double t = Math.max(0, Math.min(1, (now - circle.born) / circle.duration));
            if (t >= 1) continue;
            double ease = 1 - Math.pow(1 - t, 3);
            double r = circle.size * (.18 + .82 * ease) * (1 + beat * .09);
            double fade = smooth(t / .1) * (1 - smooth((t - .32) / .68));
            double phase = circle.phase + (now - circle.born) / 80;
            double w = width.get() * (1 + beat * .6);
            double light = fade * (.78 + beat * .22);
            // Radial alpha ramps give a soft edge without transparent texture seams or z-fighting.
            band(renderer, circle, r - w * 4, r - w * .65, 0, light * .25 * glow.get() / 100, phase, -1, 0);
            band(renderer, circle, r - w * .65, r, light * .5, light, phase, -1, 0);
            band(renderer, circle, r, r + w * .65, light, light * .5, phase, -1, 0);
            band(renderer, circle, r + w * .65, r + w * 4, light * .25 * glow.get() / 100, 0, phase, -1, 0);
            if (style.is("Orbit")) {
                band(renderer, circle, r * .76 - w * .4, r * .76 + w * .4, light * .55, light * .55,
                        -phase, 16, phase * TAU);
                band(renderer, circle, r * 1.12 - w * .25, r * 1.12 + w * .25, light * .4, light * .4,
                        phase + .3, 8, -phase * TAU);
            } else if (style.is("Ripple")) {
                band(renderer, circle, r * .66 - w * .35, r * .66 + w * .35, light * .6, light * .15, phase + .2, -1, 0);
                band(renderer, circle, r * .36 - w * .3, r * .36 + w * .3, light * .4, 0, phase + .4, -1, 0);
            }
        }
    }

    private void band(Renderer3D renderer, Circle circle, double inner, double outer, double alphaIn,
                      double alphaOut, double phase, int dash, double rotation) {
        inner = Math.max(.001, inner); outer = Math.max(inner + .001, outer);
        double cr = Math.cos(rotation), sr = Math.sin(rotation);
        for (int i = 0; i < SEGMENTS; i++) {
            if (dash > 0 && i % dash >= dash * .65) continue;
            int a = tint(i / (double) SEGMENTS, phase), b = tint((i + 1) / (double) SEGMENTS, phase);
            double ax = COS[i] * cr - SIN[i] * sr, az = SIN[i] * cr + COS[i] * sr;
            double bx = COS[i + 1] * cr - SIN[i + 1] * sr, bz = SIN[i + 1] * cr + COS[i + 1] * sr;
            renderer.gradientQuad(circle.x + ax * inner, circle.y, circle.z + az * inner,
                    circle.x + ax * outer, circle.y, circle.z + az * outer,
                    circle.x + bx * outer, circle.y, circle.z + bz * outer,
                    circle.x + bx * inner, circle.y, circle.z + bz * inner,
                    alpha(a, alphaIn), alpha(a, alphaOut), alpha(b, alphaOut), alpha(b, alphaIn));
        }
    }
    private int tint(double around, double phase) {
        return rainbow.get() ? ColorUtil.hsv((float) ((around + phase) % 1 + 1) % 1, .7f, 1)
                : ColorUtil.lerp(primary.get(), secondary.get(), (float) (.5 + .5 * Math.sin(TAU * (around + phase))));
    }
    private static int alpha(int rgb, double amount) { return (rgb & 0xFFFFFF) | Math.min(255, Math.max(0, (int) Math.round(amount * 255))) << 24; }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    public int circleCount() { return circles.size(); }
    public int emittedCount() { return emitted; }
    public double beatLevel() { return music.beat(); }
}
