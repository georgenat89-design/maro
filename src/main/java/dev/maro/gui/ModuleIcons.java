package dev.maro.gui;

import dev.maro.module.Category;
import dev.maro.module.Module;

import java.util.Locale;
import java.util.Map;

import static dev.maro.gui.render.GlyphNames.*;

/** The icon each module and category shows in the menu. A module without one gets its category's. */
public final class ModuleIcons {
    private static final Map<String, String> MODULES = Map.ofEntries(
            // Movement
            Map.entry("air stuck", SNOWFLAKE), Map.entry("coords fly", NAVIGATION), Map.entry("no fall", FEATHER),
            Map.entry("vehicles", CAR), Map.entry("auto walk", FOOTPRINTS), Map.entry("free cam", CAMERA),
            Map.entry("freelook", SCAN_EYE), Map.entry("boat fly", SAILBOAT), Map.entry("boat no clip", GHOST),
            // Anti-cheat off
            Map.entry("flight", PLANE), Map.entry("break delay", TIMER), Map.entry("speed mine", GAUGE),
            // Player
            Map.entry("auto builder", HAMMER), Map.entry("auto mine", PICKAXE), Map.entry("auto tool", WRENCH),
            Map.entry("auto trident", WAVES), Map.entry("crafter disabler", BAN), Map.entry("fake player", BOT),
            Map.entry("fast place", MOUSE_POINTER_CLICK), Map.entry("maro relog", REFRESH_CW), Map.entry("trident util", SPARKLE),
            Map.entry("chest stealer", PACKAGE_OPEN), Map.entry("chest dumper", PACKAGE_PLUS), Map.entry("fast xp", FLASK_CONICAL),
            Map.entry("smart eat", APPLE), Map.entry("spawner protect", SHIELD_CHECK),
            // Visuals
            Map.entry("acrobatics", PERSON_STANDING), Map.entry("armor hud", SHIELD), Map.entry("better looks", SPARKLES),
            Map.entry("better textures", IMAGE), Map.entry("compass", COMPASS), Map.entry("cosmetics", SHIRT),
            Map.entry("custom crosshair", CROSSHAIR), Map.entry("custom sky", CLOUD_SUN), Map.entry("custom totem", GEM),
            Map.entry("emotes", SMILE), Map.entry("fake block", BOX), Map.entry("fullbright", SUN), Map.entry("geeked", BRAIN),
            Map.entry("hand shader", HAND), Map.entry("inventory", BACKPACK), Map.entry("item inspect", SCAN_SEARCH),
            Map.entry("keyboard strokes", KEYBOARD), Map.entry("nametags", TAG), Map.entry("no render", EYE_OFF),
            Map.entry("pet", DOG), Map.entry("player esp", SCAN_FACE), Map.entry("potato graphics", MONITOR_DOWN),
            Map.entry("spawner notifier", BELL), Map.entry("stretch res", STRETCH_HORIZONTAL), Map.entry("trajectories", SPLINE),
            Map.entry("view model", AXIS_3D), Map.entry("block esp", SCAN_LINE), Map.entry("storage esp", ARCHIVE),
            Map.entry("base esp", RADAR), Map.entry("staff notifier", SHIELD_ALERT), Map.entry("spin bot", ROTATE_CW),
            Map.entry("bloom", FLOWER_2), Map.entry("swing speed", SWORD), Map.entry("region map", MAP),
            Map.entry("custom fov", FOCUS), Map.entry("color correct", PALETTE), Map.entry("spotify hud", MUSIC),
            Map.entry("key zoom", ZOOM_IN), Map.entry("fake xp", TROPHY), Map.entry("hats", HARD_HAT), Map.entry("motion blur", WIND),
            // Misc
            Map.entry("auto goliath", TARGET), Map.entry("block disconnect", UNPLUG), Map.entry("coord snapper", LOCATE_FIXED),
            Map.entry("discord presence", MESSAGE_CIRCLE), Map.entry("example", LIGHTBULB), Map.entry("inventory hider", LOCK),
            Map.entry("order dropper", SHOPPING_CART), Map.entry("screen hider", MONITOR_OFF), Map.entry("skin changer", VENETIAN_MASK),
            Map.entry("spotify phone", SMARTPHONE), Map.entry("spotify playlists", LIST_MUSIC), Map.entry("chat macros", MESSAGES_SQUARE),
            Map.entry("key sounds", VOLUME_2));

    private ModuleIcons() {
    }

    public static String of(Module module) {
        String icon = MODULES.get(module.getName().toLowerCase(Locale.ROOT));
        return icon != null ? icon : of(module.getCategory());
    }

    public static String of(Category category) {
        return switch (category) {
            case COMBAT -> SWORDS;
            case MOVEMENT -> MOVE;
            case PLAYER -> USER;
            case VISUALS -> EYE;
            case MISC -> LAYERS;
            case ANTI_CHEAT_OFF -> SHIELD_OFF;
        };
    }
}
