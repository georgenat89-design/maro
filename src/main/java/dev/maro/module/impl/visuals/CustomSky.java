package dev.maro.module.impl.visuals;

import dev.maro.Maro;
import dev.maro.gui.hud.SkyImageScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.sky.CustomSkyRenderer;
import dev.maro.render.sky.SkyImage;
import dev.maro.runtime.renderer.Texture;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Replaces the sky with one of fifteen animated skies, or a picture of your own. They are painted by
 * {@code custom_sky.fsh} through {@link CustomSkyRenderer}, in place of the game's own sky (see
 * {@link dev.maro.mixin.CustomSkyMixins}). Changing the sky fades from the old one to the new one.
 */
public class CustomSky extends Module {
    /** Order matches sky() in custom_sky.fsh; the last is your own picture. */
    public static final String[] SKIES = {"Neon Waves", "Aurora", "Nebula", "Synthwave", "Golden Hour", "Cotton Candy",
            "Blood Moon", "Starry Night", "Matrix", "Inferno", "Frozen", "Deep Ocean", "Black Hole", "Prism", "Thunderstorm",
            "Image"};
    private static final int PICTURE = SKIES.length - 1;
    /** Each sky's colour just above the horizon, which far-away land fades into when Match Fog is on. */
    private static final int[] HORIZONS = {0x23093C, 0x092728, 0x170C1D, 0xF95D72, 0xFC955A, 0xFBBFDF,
            0x590808, 0x181F42, 0x020B05, 0x831E03, 0xD7EBFF, 0x07304B, 0x0A050C, 0x9A92A6, 0x1C1F29, 0x10141C};
    private static final double FADE_SECONDS = 1.5;

    private static CustomSky instance;

    private final ModeSetting sky = add(new ModeSetting("Sky", "Which sky to show", "Neon Waves", SKIES)
            .onChange(v -> skyChanged()));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the sky moves", 100, 0, 300, 5).suffix("%"));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "How bright the sky is", 100, 20, 150, 5).suffix("%"));
    private final BooleanSetting cycle = add(new BooleanSetting("Cycle", "Move on to the next sky every so often", false)
            .onChange(on -> pickedAt = System.nanoTime()));
    private final NumberSetting cycleTime = add(new NumberSetting("Cycle Time", "How long each sky stays before the next", 60, 10, 600, 5)
            .suffix("s").visible(cycle::get));

    private final ButtonSetting choosePicture = add(new ButtonSetting("Your Image", "Use your own picture or 360° panorama as the sky",
            "Choose", () -> mc.setScreen(new SkyImageScreen(mc.currentScreen, this))));
    private final ModeSetting fit = add(new ModeSetting("Image Fit",
            "Auto goes by its shape: 2:1 is a 360° panorama, 4:3 a skybox cross, anything else wraps round",
            "Auto", "Auto", "Panorama", "Cube Cross", "Wrap Around").visible(this::showingPicture));
    private final NumberSetting turn = add(new NumberSetting("Image Turn", "Turn the picture round the horizon", 0, 0, 355, 5)
            .suffix("°").visible(this::showingPicture));
    private final NumberSetting spin = add(new NumberSetting("Image Spin", "Keep turning the picture slowly, in degrees a second", 0, 0, 10, 0.5)
            .visible(this::showingPicture));

    private final BooleanSetting matchFog = add(new BooleanSetting("Match Fog", "Far-away land fades into the sky's colour instead of the normal fog", true));
    private final BooleanSetting sunMoon = add(new BooleanSetting("Sun & Moon", "Keep the normal sun and moon in front of the sky", false));
    private final BooleanSetting clouds = add(new BooleanSetting("Clouds", "Keep the normal clouds", false));
    private final BooleanSetting end = add(new BooleanSetting("In The End", "Replace the End's sky too", true));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Sky", sky, speed, brightness, cycle, cycleTime),
            SettingSection.of("Your Image", choosePicture, fit, turn, spin),
            SettingSection.of("World", matchFog, sunMoon, clouds, end));

    /** The sky showing, the one fading out and when that fade began (0 for none). */
    private int shown;
    private int previous;
    private long changedAt;
    /** When the sky showing was picked, or the module turned on; Cycle counts from here. */
    private long pickedAt;

    /** Animation clock in seconds, advanced each frame at the Speed setting. */
    private double clock;
    private long lastFrame;

    /** Your picture, once loaded; it is kept in {@link #pictureFolder()} between games. */
    private final SkyImage image = new SkyImage();
    private boolean imageBusy, closing;
    private String imageStatus = "No image chosen yet";

    public CustomSky() {
        super("Custom Sky", "Swap the sky for one of fifteen animated skies or your own picture", Category.VISUALS);
        instance = this;
        shown = previous = sky.index();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            closing = true;
            image.close();
        });
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        // Turning on shows the chosen sky straight away; only changing it fades.
        previous = shown = sky.index();
        changedAt = 0;
        pickedAt = System.nanoTime();
        lastFrame = 0;
        if (showingPicture()) loadStoredPicture();
    }

    @Override
    public void onTick() {
        if (cycle.get() && (System.nanoTime() - pickedAt) / 1e9 >= cycleTime.get()) sky.cycle(1);
    }

    private void skyChanged() {
        previous = shown;
        shown = sky.index();
        changedAt = pickedAt = System.nanoTime();
        if (showingPicture()) loadStoredPicture();
    }

    /** How much of the previous sky is still showing, 1 just after a change down to 0. */
    private float fadeLeft() {
        if (changedAt == 0 || previous == shown) return 0f;
        double t = (System.nanoTime() - changedAt) / 1e9 / FADE_SECONDS;
        if (t >= 1) return 0f;
        double eased = t * t * (3 - 2 * t);
        return (float) (1 - eased);
    }

    // ---- your picture ---------------------------------------------------------------------

    public boolean showingPicture() {
        return sky.is("Image");
    }

    public Path pictureFolder() {
        return mc.runDirectory.toPath().resolve("maro/skies");
    }

    public boolean pictureBusy() {
        return imageBusy;
    }

    public String pictureStatus() {
        return imageBusy ? "Loading image…" : imageStatus;
    }

    public SkyImage picture() {
        return image;
    }

    /** The picture saved last time, if it is not loaded yet. */
    public void loadStoredPicture() {
        if (image.loaded() || imageBusy) return;
        for (String ext : new String[] {"png", "jpg", "jpeg"}) {
            Path stored = pictureFolder().resolve("custom." + ext);
            if (Files.isRegularFile(stored)) {
                loadPicture(stored, false);
                return;
            }
        }
    }

    /** Loads {@code path} as the sky picture, keeps a copy for next time and switches to it. */
    public CompletableFuture<Boolean> importPicture(Path path) {
        return loadPicture(path, true);
    }

    private CompletableFuture<Boolean> loadPicture(Path path, boolean importing) {
        if (imageBusy || closing) return CompletableFuture.completedFuture(false);
        imageBusy = true;
        var result = new CompletableFuture<Boolean>();
        CompletableFuture.supplyAsync(() -> {
            try {
                var decoded = SkyImage.decode(path);
                if (importing) keepCopy(path);
                return decoded;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((decoded, error) -> mc.execute(() -> {
            imageBusy = false;
            if (closing) {
                result.complete(false);
                return;
            }
            try {
                if (error != null) throw new CompletionException(error);
                image.install(decoded);
                imageStatus = "Loaded " + decoded.width() + " × " + decoded.height() + " image (" + fitName(decoded.fit()) + ")";
                if (importing) {
                    sky.set("Image");
                    Notifications.push(getName(), "Image loaded as your sky", Notifications.Type.INFO);
                }
                result.complete(true);
            } catch (RuntimeException e) {
                Throwable cause = e;
                while (cause.getCause() != null) cause = cause.getCause();
                imageStatus = cause.getMessage() == null ? "Could not load this image" : cause.getMessage();
                Notifications.push(getName(), imageStatus, Notifications.Type.ERROR);
                result.complete(false);
            }
        }));
        return result;
    }

    /** Copies the chosen file into the skies folder as custom.png / custom.jpg, replacing the last one. */
    private void keepCopy(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "png";
        Path folder = pictureFolder();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "sky-", ".tmp");
        try {
            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
            for (String old : new String[] {"png", "jpg", "jpeg"}) Files.deleteIfExists(folder.resolve("custom." + old));
            Files.move(temporary, folder.resolve("custom." + ext), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String fitName(SkyImage.Fit fit) {
        return switch (fit) {
            case PANORAMA -> "360° panorama";
            case CUBE -> "skybox cross";
            case WRAP -> "wrapped round";
        };
    }

    /** How the picture is laid on the sky right now. */
    private SkyImage.Fit currentFit() {
        return switch (fit.get()) {
            case "Panorama" -> SkyImage.Fit.PANORAMA;
            case "Cube Cross" -> SkyImage.Fit.CUBE;
            case "Wrap Around" -> SkyImage.Fit.WRAP;
            default -> image.image() != null ? image.image().fit() : SkyImage.Fit.PANORAMA;
        };
    }

    /**
     * For a wrapped picture: how many copies go round the horizon (always even, so mirrored
     * neighbours meet seamlessly) and how high each one reaches, in degrees, keeping its shape.
     */
    private static double[] wrapLayout(double aspect) {
        int best = 2;
        double bestError = Double.MAX_VALUE;
        for (int copies = 2; copies <= 16; copies += 2) {
            double height = 360.0 / copies / aspect;
            if (height > 95) continue;
            double error = Math.abs(height - 65);
            if (error < bestError) {
                bestError = error;
                best = copies;
            }
        }
        return new double[] {best, Math.min(95, 360.0 / best / aspect)};
    }

    // ---- what the renderer and hooks ask --------------------------------------------------

    private static boolean on() {
        return instance != null && instance.isEnabled() && mc.world != null;
    }

    /** Whether to paint the custom sky over the overworld-style sky. */
    public static boolean active() {
        return on();
    }

    /** Whether to paint the custom sky over the End's sky. */
    public static boolean activeInEnd() {
        return on() && instance.end.get();
    }

    public static boolean keepsSunAndMoon() {
        return instance != null && instance.sunMoon.get();
    }

    public static boolean hidesClouds() {
        return on() && !instance.clouds.get();
    }

    /** Whether the sky showing now is your picture. */
    public static boolean showsPicture() {
        return on() && instance.shown == PICTURE;
    }

    /** Your picture on the GPU, or null if there is none. */
    public static Texture pictureTexture() {
        return instance == null ? null : instance.image.texture();
    }

    /** Whether the picture repeats round the horizon (a panorama) rather than stopping at its edges. */
    public static boolean pictureWraps() {
        return instance != null && instance.currentFit() == SkyImage.Fit.PANORAMA;
    }

    private int horizon(int index) {
        if (index == PICTURE && image.image() != null) return image.image().horizon();
        return HORIZONS[index];
    }

    /**
     * The fog colour to use instead of {@code original}, or null to keep it. Only while the custom
     * sky was actually drawn on the last frame, so water, lava, blindness and the Nether keep theirs.
     */
    public static Vector4f fogColor(Vector4f original) {
        if (!on() || !instance.matchFog.get() || !CustomSkyRenderer.drewLastFrame()) return null;
        float fade = instance.fadeLeft();
        int now = instance.horizon(instance.shown), before = instance.horizon(instance.previous);
        float bright = instance.brightness.getFloat() / 100f;
        float r = channel(now, before, 16, fade) * bright;
        float g = channel(now, before, 8, fade) * bright;
        float b = channel(now, before, 0, fade) * bright;
        return new Vector4f(Math.min(r, 1f), Math.min(g, 1f), Math.min(b, 1f), original.w);
    }

    private static float channel(int now, int before, int shift, float fade) {
        float a = (now >> shift & 0xFF) / 255f, b = (before >> shift & 0xFF) / 255f;
        return a + (b - a) * fade;
    }

    /** The SkyData values after the matrix: Params, View, ImageParams, ImageTop and ImageBottom. */
    public static float[] uniformValues(float pixelAngle) {
        CustomSky m = instance;
        long now = System.nanoTime();
        if (m.lastFrame != 0) {
            // A long pause (a loading screen) must not make the sky jump.
            double dt = Math.min((now - m.lastFrame) / 1e9, 0.25);
            m.clock = (m.clock + dt * m.speed.get() / 100.0) % 7200.0;
        }
        m.lastFrame = now;

        SkyImage.Decoded info = m.image.image();
        double aspect = info != null ? info.width() / (double) info.height() : 2.0;
        double[] wrap = wrapLayout(aspect);
        double turnDegrees = (m.turn.get() + m.clock * m.spin.get()) % 360.0;
        int top = info != null ? info.top() : 0, bottom = info != null ? info.bottom() : 0;
        return new float[] {
                (float) m.clock, m.shown, m.previous, m.fadeLeft(),
                m.brightness.getFloat() / 100f, pixelAngle, m.currentFit().ordinal(), (float) wrap[0],
                (float) Math.toRadians(turnDegrees), (float) Math.toRadians(wrap[1]), 0, 0,
                (top >> 16 & 0xFF) / 255f, (top >> 8 & 0xFF) / 255f, (top & 0xFF) / 255f, 0,
                (bottom >> 16 & 0xFF) / 255f, (bottom >> 8 & 0xFF) / 255f, (bottom & 0xFF) / 255f, 0
        };
    }

    /** The graphics driver would not compile the sky shader; the game's own sky stays. */
    public static void shaderFailed() {
        Maro.LOGGER.error("Custom Sky: the graphics driver could not compile the custom_sky shader (its errors are logged above)");
        if (instance == null || !instance.isEnabled()) return;
        Notifications.push(instance.getName(), "Your graphics driver could not build the sky - see the log. Turned off.", Notifications.Type.ERROR);
        instance.setEnabled(false);
    }

    public static void renderFailed(RuntimeException e) {
        Maro.LOGGER.error("Custom Sky could not render", e);
        if (instance == null) return;
        Notifications.push(instance.getName(), "Rendering failed - see the log. Turned off.", Notifications.Type.ERROR);
        instance.setEnabled(false);
    }
}
