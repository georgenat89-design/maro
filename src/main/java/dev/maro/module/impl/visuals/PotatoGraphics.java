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
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.particle.ParticlesMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when it turns things down and back up", true));

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
        return List.of(SettingSection.of("Level", level, notify),
                SettingSection.of("Distance", limitRender, renderCap, limitSim, simCap, entityRange, clouds),
                SettingSection.of("World", particles, smoothLighting, fastLeaves, biomeBlend, shadows, transparency, weather, chunkFade),
                SettingSection.of("Screen", mipmaps, vsync, unlockFps, menuBlur, vignette));
    }

    private void applyLevel(String name) {
        boolean light = name.equals("Light");
        renderCap.set(switch (name) {
            case "Light" -> 10.0;
            case "Ultra Potato" -> 3.0;
            default -> 6.0;
        });
        simCap.set(light ? 8.0 : 5.0);
        for (BooleanSetting b : new BooleanSetting[]{limitRender, limitSim, entityRange, clouds, particles, biomeBlend, shadows,
                weather, chunkFade, vsync, unlockFps, menuBlur, vignette, mipmaps}) b.set(true);
        // Light keeps the look mostly intact.
        smoothLighting.set(!light);
        fastLeaves.set(!light);
        transparency.set(!light);
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
        if (first && lowered > 0 && notify.get()) {
            Notifications.push("Potato Graphics", lowered + " video settings turned down", Notifications.Type.ENABLED);
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
