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
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.texture.NativeImage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Potato Graphics: paints every block texture as one solid colour (Flat) or nearly so (Soft) while
 * it is on, and brings the real textures back when it is off.
 *
 * <p>It used to turn the game's video settings down as well. Anyone who updated with it on still
 * has their own values saved in the Maro config; those are put back once, as soon as the game's
 * options are available, and then forgotten.
 */
public class PotatoGraphics extends Module {
    public static final String TEXTURES_NORMAL = "Normal", TEXTURES_FLAT = "Flat", TEXTURES_SOFT = "Soft";
    private final ModeSetting textures = add(new ModeSetting("Textures",
            "Flat paints every block one solid colour, Soft keeps a hint of the texture (reloads textures for a few seconds)",
            TEXTURES_FLAT, TEXTURES_FLAT, TEXTURES_SOFT, TEXTURES_NORMAL));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when it turns on and off", true));

    /** The video options older versions lowered, by the key their saved originals were stored under. */
    private static final Map<String, Function<GameOptions, SimpleOption<?>>> LEGACY_OPTIONS = legacyOptions();

    private static PotatoGraphics instance;

    /** The texture style the loaded block textures were made with, set while they load. */
    private static volatile String loadedTextures = TEXTURES_NORMAL;
    /** The style of the last texture reload this module asked for, so it never asks twice. */
    private static String requestedTextures;
    private static final AtomicInteger flattenedSprites = new AtomicInteger();

    /** Your own video settings saved by an older version, waiting to be put back. */
    private JsonObject legacyOriginals;

    public PotatoGraphics() {
        super("Potato Graphics", "Flat, solid-colour block textures, and back when off", Category.VISUALS);
        instance = this;
        // Runs whether or not the module is on, so switching it off also brings the textures back.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            syncTextures();
            restoreLegacyOriginals();
        });
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Look", textures, notify));
    }

    @Override
    protected void onEnable() {
        if (!notify.get()) return;
        String message = textures.is(TEXTURES_NORMAL) ? "On - pick Flat or Soft textures" : textures.get() + " textures on";
        Notifications.push("Potato Graphics", message, Notifications.Type.ENABLED);
    }

    @Override
    protected void onDisable() {
        if (notify.get() && !textures.is(TEXTURES_NORMAL)) {
            Notifications.push("Potato Graphics", "Textures back to normal", Notifications.Type.DISABLED);
        }
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

    // ---- video settings lowered by older versions --------------------------------------------

    private static Map<String, Function<GameOptions, SimpleOption<?>>> legacyOptions() {
        Map<String, Function<GameOptions, SimpleOption<?>>> options = new LinkedHashMap<>();
        options.put("render-distance", GameOptions::getViewDistance);
        options.put("simulation-distance", GameOptions::getSimulationDistance);
        options.put("entity-distance", GameOptions::getEntityDistanceScaling);
        options.put("clouds", GameOptions::getCloudRenderMode);
        options.put("particles", GameOptions::getParticles);
        options.put("smooth-lighting", GameOptions::getAo);
        options.put("cutout-leaves", GameOptions::getCutoutLeaves);
        options.put("biome-blend", GameOptions::getBiomeBlendRadius);
        options.put("entity-shadows", GameOptions::getEntityShadows);
        options.put("improved-transparency", GameOptions::getImprovedTransparency);
        options.put("weather-radius", GameOptions::getWeatherRadius);
        options.put("chunk-fade", GameOptions::getChunkFade);
        options.put("mipmaps", GameOptions::getMipmapLevels);
        options.put("vsync", GameOptions::getEnableVsync);
        options.put("max-fps", GameOptions::getMaxFps);
        options.put("menu-blur", GameOptions::getMenuBackgroundBlurriness);
        options.put("vignette", GameOptions::getVignette);
        return options;
    }

    /** Puts back, once, the video settings an older version saved before turning them down. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void restoreLegacyOriginals() {
        GameOptions options = mc.options;
        if (legacyOriginals == null || options == null) return;
        JsonObject saved = legacyOriginals;
        legacyOriginals = null;
        int restored = 0;
        for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
            Function<GameOptions, SimpleOption<?>> getter = LEGACY_OPTIONS.get(entry.getKey());
            if (getter == null) continue;
            try {
                SimpleOption option = getter.apply(options);
                Object original = ((Codec) option.getCodec()).parse(JsonOps.INSTANCE, entry.getValue()).result().orElse(null);
                if (original == null || Objects.equals(option.getValue(), original)) continue;
                option.setValue(original);
                restored++;
            } catch (RuntimeException e) {
                Maro.LOGGER.warn("Potato Graphics could not put back {}", entry.getKey(), e);
            }
        }
        if (restored == 0) return;
        options.write();
        Notifications.push("Potato Graphics", "It no longer changes video settings - your " + restored + " are back",
                Notifications.Type.INFO);
    }

    @Override
    public JsonObject saveExtra() {
        // Keep anything not yet put back, so it is not lost if the game closes first.
        return legacyOriginals != null ? legacyOriginals.deepCopy() : new JsonObject();
    }

    @Override
    public void loadExtra(JsonObject data) {
        if (data != null && !data.entrySet().isEmpty()) legacyOriginals = data.deepCopy();
    }
}
