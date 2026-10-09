package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.SettingSection;

import java.util.List;

/**
 * Stops things from being drawn, each one its own switch: screen overlays, camera effects, HUD
 * parts, weather and particles, and kinds of entity. Only what you see changes; the game itself
 * carries on as before. Applied by the NoRender mixins in {@link dev.maro.mixin}.
 */
public class NoRender extends Module {
    public enum Part {
        FIRE, PUMPKIN, POWDER_SNOW, UNDERWATER, IN_WALL, PORTAL, NAUSEA, VIGNETTE, TOTEM, HURT_CAMERA,
        BOSS_BAR, SCOREBOARD, POTION_ICONS,
        WEATHER, EXPLOSIONS, MINING_PARTICLES,
        DROPPED_ITEMS, XP_ORBS, ARMOR_STANDS, FALLING_BLOCKS, FIREWORKS, ITEM_FRAMES
    }

    private static NoRender instance;

    private final BooleanSetting[] parts = new BooleanSetting[Part.values().length];
    private final List<SettingSection> sections;

    public NoRender() {
        super("No Render", "Hide overlays, weather, particles and clutter you do not want to see", Category.VISUALS);
        instance = this;

        SettingSection overlays = new SettingSection("Overlays");
        part(overlays, Part.FIRE, "Fire", "The flames across the screen while you burn", true);
        part(overlays, Part.PUMPKIN, "Pumpkin", "The carved pumpkin overlay when wearing one", true);
        part(overlays, Part.POWDER_SNOW, "Powder Snow", "The frost around the screen in powder snow", true);
        part(overlays, Part.UNDERWATER, "Underwater", "The water texture over the screen when submerged", false);
        part(overlays, Part.IN_WALL, "In Wall", "The block texture when your head is inside a block", true);
        part(overlays, Part.PORTAL, "Portal", "The purple swirl while standing in a nether portal", false);
        part(overlays, Part.NAUSEA, "Nausea", "The warping overlay from nausea", true);
        part(overlays, Part.VIGNETTE, "Vignette", "The dark edges round the screen", false);
        part(overlays, Part.TOTEM, "Totem Animation", "The totem that fills the screen when one pops", true);
        part(overlays, Part.HURT_CAMERA, "Hurt Camera", "The camera shake when you take damage", true);

        SettingSection hud = new SettingSection("HUD");
        part(hud, Part.BOSS_BAR, "Boss Bar", "Boss and event bars at the top of the screen", false);
        part(hud, Part.SCOREBOARD, "Scoreboard", "The sidebar scoreboard on the right", false);
        part(hud, Part.POTION_ICONS, "Potion Icons", "The status effect icons in the top right", false);

        SettingSection world = new SettingSection("World");
        part(world, Part.WEATHER, "Weather", "Rain and snow, with their splashes and sound", true);
        part(world, Part.EXPLOSIONS, "Explosions", "Explosion particles", true);
        part(world, Part.MINING_PARTICLES, "Mining Particles", "The chips that fly off a block while it is mined", false);

        SettingSection entities = new SettingSection("Entities");
        part(entities, Part.DROPPED_ITEMS, "Dropped Items", "Items lying on the ground", false);
        part(entities, Part.XP_ORBS, "XP Orbs", "Experience orbs", false);
        part(entities, Part.ARMOR_STANDS, "Armor Stands", "Armor stands and what they wear", false);
        part(entities, Part.FALLING_BLOCKS, "Falling Blocks", "Sand, gravel and other blocks while they fall", true);
        part(entities, Part.FIREWORKS, "Fireworks", "Firework rockets in flight", false);
        part(entities, Part.ITEM_FRAMES, "Item Frames", "Item frames and what they hold", false);

        sections = List.of(overlays, hud, world, entities);
    }

    private void part(SettingSection section, Part part, String name, String description, boolean on) {
        parts[part.ordinal()] = section.add(add(new BooleanSetting(name, description, on)));
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** Whether this part is hidden right now. Cheap: called from render code every frame. */
    public static boolean hides(Part part) {
        NoRender m = instance;
        return m != null && m.isEnabled() && m.parts[part.ordinal()].get() || BetterLooks.hides(part);
    }

    public static NoRender get() {
        return instance != null ? instance : ModuleManager.get(NoRender.class);
    }
}
