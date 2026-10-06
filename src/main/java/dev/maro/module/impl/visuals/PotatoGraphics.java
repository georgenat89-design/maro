package dev.maro.module.impl.visuals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.maro.Maro;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticlesMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Potato Graphics: turns the game's own video settings down for frames while it is on, and puts
 * every one of them back exactly as it was when it is turned off. Each setting it touches has its
 * own toggle. Your original values are kept in the Maro config too, so they come back even after
 * a restart with it still on.
 *
 * <p>It only acts when it is turned on or when one of its own settings changes. Changing a video
 * setting yourself while it is on is left alone.
 */
public class PotatoGraphics extends Module {
    private static final String[] LEVELS = {"Light", "Potato", "Ultra Potato"};

    private final ModeSetting level = add(new ModeSetting("Level", "How far to turn things down; sets the options below", "Potato", LEVELS)
            .onChange(this::applyLevel));

    // distance
    private final BooleanSetting limitRender = add(new BooleanSetting("Limit Render Distance", "Draw fewer chunks around you", true));
    private final NumberSetting renderCap = add(new NumberSetting("Render Distance", "Most chunks to draw; lower is faster", 6, 2, 16, 1)
            .suffix(" chunks").visible(limitRender::get));
    private final BooleanSetting limitSim = add(new BooleanSetting("Limit Simulation", "Tick fewer chunks in singleplayer", true));
    private final NumberSetting simCap = add(new NumberSetting("Simulation Distance", "Most chunks to tick", 5, 5, 16, 1)
            .suffix(" chunks").visible(limitSim::get));
    private final BooleanSetting entityRange = add(new BooleanSetting("Short Entity Range", "Stop drawing mobs and items sooner", true));
    private final BooleanSetting clouds = add(new BooleanSetting("No Clouds", "Turn clouds off", true));

    // world
    private final BooleanSetting particles = add(new BooleanSetting("Minimal Particles", "Only the particles that matter", true));
    private final BooleanSetting smoothLighting = add(new BooleanSetting("No Smooth Lighting", "Flat block lighting", true));
    private final BooleanSetting fastLeaves = add(new BooleanSetting("Fast Leaves", "Solid leaves instead of see-through ones", true));
    private final BooleanSetting biomeBlend = add(new BooleanSetting("No Biome Blend", "Hard edges between biome colours", true));
    private final BooleanSetting shadows = add(new BooleanSetting("No Entity Shadows", "No round shadows under mobs and players", true));
    private final BooleanSetting transparency = add(new BooleanSetting("Simple Transparency", "Cheaper glass, water and particle layering", true));
    private final BooleanSetting weather = add(new BooleanSetting("Less Weather", "Rain and snow only close to you", true));
    private final BooleanSetting chunkFade = add(new BooleanSetting("No Chunk Fade", "New chunks pop in instead of fading", true));

    // textures and screen
    private final BooleanSetting mipmaps = add(new BooleanSetting("No Mipmaps", "Skip texture mipmaps", true));
    private final BooleanSetting vsync = add(new BooleanSetting("No VSync", "Do not wait for the monitor", true));
    private final BooleanSetting unlockFps = add(new BooleanSetting("Unlimited FPS", "Lift the frame rate cap", true));
    private final BooleanSetting menuBlur = add(new BooleanSetting("No Menu Blur", "Do not blur the world behind menus", true));
    private final BooleanSetting vignette = add(new BooleanSetting("No Vignette", "No dark screen edges", true));

    // the potato look
    public static final String TEXTURES_NORMAL = "Normal", TEXTURES_FLAT = "Flat", TEXTURES_SOFT = "Soft";
    private final ModeSetting textures = add(new ModeSetting("Textures",
            "Flat paints every block one solid colour, Soft keeps a hint of the texture (reloads textures for a few seconds)",
            TEXTURES_FLAT, TEXTURES_FLAT, TEXTURES_SOFT, TEXTURES_NORMAL));

    // beyond the video settings
    private final BooleanSetting hideFarEntities = add(new BooleanSetting("Hide Far Entities", "Do not draw mobs, items and other entities past a distance (players are always drawn)", true));
    private final NumberSetting entityCap = add(new NumberSetting("Entity Distance", "Entities further than this are not drawn", 32, 8, 128, 4)
            .suffix(" blocks").visible(hideFarEntities::get));
    private final BooleanSetting noParticles = add(new BooleanSetting("No Particles", "No particles at all, not just fewer", false));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when it turns things down and back up", true));

    private static PotatoGraphics instance;

    /** The texture style the loaded block textures were made with, set while they load. */
    private static volatile String loadedTextures = TEXTURES_NORMAL;
    /** The style of the last texture reload this module asked for, so it never asks twice. */
    private static String requestedTextures;
    private static final AtomicInteger flattenedSprites = new AtomicInteger();

    /** One video option it can lower. */
    private record Knob(String key, BooleanSetting toggle, Function<GameOptions, SimpleOption<?>> option, UnaryOperator<Object> potato) {
    }

    private final List<Knob> knobs = new ArrayList<>();
    /** The values you had before, by knob key. */
    private final Map<String, Object> backup = new LinkedHashMap<>();
    /** Saved originals read from the config, decoded once the options exist. */
    private JsonObject pendingBackup;
    /** Options that refused the lowered value, so they are not tried every time. */
    private final Set<String> refused = new HashSet<>();
    private int appliedSignature;
    private boolean dirty;

    public PotatoGraphics() {
        super("Potato Graphics", "Turns video settings right down for more FPS, and back when off", Category.VISUALS);
        instance = this;
        // Runs whether or not the module is on, so switching it off also brings the textures back.
        ClientTickEvents.END_CLIENT_TICK.register(client -> syncTextures());
        knob("render-distance", limitRender, GameOptions::getViewDistance, v -> v instanceof Integer i ? Math.min(i, renderCap.getInt()) : null);
        knob("simulation-distance", limitSim, GameOptions::getSimulationDistance, v -> v instanceof Integer i ? Math.min(i, simCap.getInt()) : null);
        knob("entity-distance", entityRange, GameOptions::getEntityDistanceScaling, v -> v instanceof Double d ? Math.min(d, 0.5) : null);
        knob("clouds", clouds, GameOptions::getCloudRenderMode, v -> v instanceof CloudRenderMode ? CloudRenderMode.OFF : null);
        knob("particles", particles, GameOptions::getParticles, v -> v instanceof ParticlesMode ? ParticlesMode.MINIMAL : null);
        knob("smooth-lighting", smoothLighting, GameOptions::getAo, PotatoGraphics::off);
        knob("cutout-leaves", fastLeaves, GameOptions::getCutoutLeaves, PotatoGraphics::off);
        knob("biome-blend", biomeBlend, GameOptions::getBiomeBlendRadius, v -> v instanceof Integer ? 0 : null);
        knob("entity-shadows", shadows, GameOptions::getEntityShadows, PotatoGraphics::off);
        knob("improved-transparency", transparency, GameOptions::getImprovedTransparency, PotatoGraphics::off);
        knob("weather-radius", weather, GameOptions::getWeatherRadius, v -> v instanceof Integer i ? Math.min(i, 3) : null);
        knob("chunk-fade", chunkFade, GameOptions::getChunkFade, v -> v instanceof Double ? 0.0 : null);
        knob("mipmaps", mipmaps, GameOptions::getMipmapLevels, v -> v instanceof Integer ? 0 : null);
        knob("vsync", vsync, GameOptions::getEnableVsync, PotatoGraphics::off);
        knob("max-fps", unlockFps, GameOptions::getMaxFps, v -> v instanceof Integer ? 260 : null);
        knob("menu-blur", menuBlur, GameOptions::getMenuBackgroundBlurriness, v -> v instanceof Integer ? 0 : null);
        knob("vignette", vignette, GameOptions::getVignette, PotatoGraphics::off);
    }

    private void knob(String key, BooleanSetting toggle, Function<GameOptions, SimpleOption<?>> option, UnaryOperator<Object> potato) {
        knobs.add(new Knob(key, toggle, option, potato));
    }

    private static Object off(Object value) {
        return value instanceof Boolean ? Boolean.FALSE : null;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Level", level, textures, notify),
                SettingSection.of("Distance", limitRender, renderCap, limitSim, simCap, entityRange, clouds),
                SettingSection.of("World", particles, smoothLighting, fastLeaves, biomeBlend, shadows, transparency, weather, chunkFade),
                SettingSection.of("Screen", mipmaps, vsync, unlockFps, menuBlur, vignette),
                SettingSection.of("Extra", hideFarEntities, entityCap, noParticles));
    }

    private void applyLevel(String name) {
        boolean light = name.equals("Light");
        boolean ultra = name.equals("Ultra Potato");
        renderCap.set(switch (name) {
            case "Light" -> 10.0;
            case "Ultra Potato" -> 3.0;
            default -> 6.0;
        });
        simCap.set(light ? 8.0 : 5.0);
        for (BooleanSetting b : new BooleanSetting[]{limitRender, limitSim, entityRange, clouds, particles, biomeBlend, shadows,
                weather, chunkFade, vsync, unlockFps, menuBlur, vignette, mipmaps, hideFarEntities}) b.set(true);
        // Light keeps the look mostly intact.
        smoothLighting.set(!light);
        fastLeaves.set(!light);
        transparency.set(!light);
        entityCap.set(light ? 48.0 : ultra ? 16.0 : 32.0);
        noParticles.set(ultra);
        textures.set(light ? TEXTURES_NORMAL : TEXTURES_FLAT);
    }

    // ---- flat textures ------------------------------------------------------------------------

    private static String wantedTextures() {
        PotatoGraphics m = instance;
        return m != null && m.isEnabled() ? m.textures.get() : TEXTURES_NORMAL;
    }

    /** Asks for one texture reload whenever the wanted style differs from what is loaded. */
    private static void syncTextures() {
        String wanted = wantedTextures();
        if (wanted.equals(loadedTextures) || wanted.equals(requestedTextures) || mc.getOverlay() != null) return;
        requestedTextures = wanted;
        flattenedSprites.set(0);
        mc.reloadResources();
    }

    /** Called as each block sprite loads: the style to paint it in, remembered as what is loaded. */
    public static String textureStyleForLoad() {
        String style = wantedTextures();
        loadedTextures = style;
        return style;
    }

    /** Whether the loaded textures match the setting and no reload is running. */
    public static boolean texturesSettled() {
        return wantedTextures().equals(loadedTextures) && mc.getOverlay() == null;
    }

    public static int flattenedSprites() {
        return flattenedSprites.get();
    }

    /**
     * Repaints a block texture as its average colour (Flat), or mostly so (Soft). Transparent
     * pixels stay transparent, so leaves, glass and flowers keep their shapes.
     */
    public static void flatten(NativeImage image, String style) {
        if (image.getFormat() != NativeImage.Format.RGBA) return;
        int w = image.getWidth(), h = image.getHeight();
        long r = 0, g = 0, b = 0, weight = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getColorArgb(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                r += (long) (argb >> 16 & 0xFF) * a;
                g += (long) (argb >> 8 & 0xFF) * a;
                b += (long) (argb & 0xFF) * a;
                weight += a;
            }
        }
        if (weight == 0) return;
        int ar = (int) (r / weight), ag = (int) (g / weight), ab = (int) (b / weight);
        float keep = style.equals(TEXTURES_SOFT) ? 0.25f : 0f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getColorArgb(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                int nr = Math.round(ar + ((argb >> 16 & 0xFF) - ar) * keep);
                int ng = Math.round(ag + ((argb >> 8 & 0xFF) - ag) * keep);
                int nb = Math.round(ab + ((argb & 0xFF) - ab) * keep);
                image.setColorArgb(x, y, a << 24 | nr << 16 | ng << 8 | nb);
            }
        }
        flattenedSprites.incrementAndGet();
    }

    // ---- boosts applied while drawing (read from render code every frame) ----------------------

    /** Whether this entity is past the distance at which Potato Graphics stops drawing entities. */
    public static boolean hidesEntity(Entity entity) {
        PotatoGraphics m = instance;
        if (m == null || !m.isEnabled() || !m.hideFarEntities.get() || entity instanceof PlayerEntity || mc.player == null) return false;
        double cap = m.entityCap.get();
        return entity.squaredDistanceTo(mc.player) > cap * cap;
    }

    public static boolean hidesParticles() {
        PotatoGraphics m = instance;
        return m != null && m.isEnabled() && m.noParticles.get();
    }

    // ---- turning things down and back --------------------------------------------------------

    @Override
    protected void onEnable() {
        dirty = true;
        refused.clear();
    }

    @Override
    protected void onDisable() {
        int restored = restoreAll();
        if (restored > 0 && notify.get()) {
            Notifications.push("Potato Graphics", "Your video settings are back", Notifications.Type.DISABLED);
        }
    }

    @Override
    public void onTick() {
        int signature = signature();
        if (!dirty && signature == appliedSignature) return;
        boolean first = dirty;
        dirty = false;
        appliedSignature = signature;
        int lowered = apply();
        if (first && notify.get()) {
            String extra = hideFarEntities.get() ? ", entities past " + entityCap.getInt() + " blocks hidden" : "";
            if (noParticles.get()) extra += ", particles off";
            if (!textures.is(TEXTURES_NORMAL)) extra += ", " + textures.get().toLowerCase() + " textures";
            String settings = lowered > 0 ? lowered + " video settings turned down" : "Video settings already low";
            Notifications.push("Potato Graphics", settings + extra, Notifications.Type.ENABLED);
        }
    }

    private int signature() {
        List<Object> values = new ArrayList<>();
        for (Setting<?> s : getSettings()) if (s != notify && s != level) values.add(s.get());
        return values.hashCode();
    }

    /** Lowers every switched-on knob and puts back every switched-off one. Returns how many are lowered. */
    private int apply() {
        GameOptions options = mc.options;
        if (options == null) return 0;
        decodePendingBackup(options);
        int lowered = 0;
        for (Knob k : knobs) {
            SimpleOption<?> option = option(k, options);
            if (option == null) continue;
            if (k.toggle().get()) {
                if (refused.contains(k.key())) continue;
                Object base = backup.containsKey(k.key()) ? backup.get(k.key()) : option.getValue();
                Object target = k.potato().apply(base);
                if (target == null) continue;
                if (!backup.containsKey(k.key())) backup.put(k.key(), option.getValue());
                if (!Objects.equals(option.getValue(), target)) {
                    force(option, target);
                    if (!Objects.equals(option.getValue(), target)) {
                        // The game would not take it; leave this one alone from now on.
                        refused.add(k.key());
                        restore(k, options);
                        continue;
                    }
                }
                if (!Objects.equals(base, target)) lowered++;
            } else {
                restore(k, options);
            }
        }
        return lowered;
    }

    private int restoreAll() {
        GameOptions options = mc.options;
        if (options == null) return 0;
        decodePendingBackup(options);
        int restored = 0;
        for (Knob k : knobs) if (restore(k, options)) restored++;
        backup.clear();
        return restored;
    }

    private boolean restore(Knob k, GameOptions options) {
        if (!backup.containsKey(k.key())) return false;
        Object original = backup.remove(k.key());
        SimpleOption<?> option = option(k, options);
        if (option == null || Objects.equals(option.getValue(), original)) return false;
        force(option, original);
        return true;
    }

    private static SimpleOption<?> option(Knob k, GameOptions options) {
        try {
            return k.option().apply(options);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void force(SimpleOption option, Object value) {
        try {
            option.setValue(value);
        } catch (RuntimeException e) {
            Maro.LOGGER.warn("Potato Graphics could not set {}", value, e);
        }
    }

    // ---- keeping your originals across restarts -------------------------------------------------

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public JsonObject saveExtra() {
        JsonObject out = new JsonObject();
        if (pendingBackup != null) return pendingBackup.deepCopy();
        GameOptions options = mc.options;
        if (options == null) return out;
        for (Knob k : knobs) {
            if (!backup.containsKey(k.key())) continue;
            SimpleOption option = option(k, options);
            if (option == null) continue;
            try {
                ((Codec) option.getCodec()).encodeStart(JsonOps.INSTANCE, backup.get(k.key())).result()
                        .ifPresent(json -> out.add(k.key(), (JsonElement) json));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    @Override
    public void loadExtra(JsonObject data) {
        // Only take saved originals when none are held, so switching configs never loses the real ones.
        if (backup.isEmpty() && data != null && !data.entrySet().isEmpty()) pendingBackup = data.deepCopy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void decodePendingBackup(GameOptions options) {
        if (pendingBackup == null) return;
        JsonObject data = pendingBackup;
        pendingBackup = null;
        for (Knob k : knobs) {
            if (!data.has(k.key()) || backup.containsKey(k.key())) continue;
            SimpleOption option = option(k, options);
            if (option == null) continue;
            try {
                ((Codec) option.getCodec()).parse(JsonOps.INSTANCE, data.get(k.key())).result()
                        .ifPresent(value -> backup.put(k.key(), value));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
