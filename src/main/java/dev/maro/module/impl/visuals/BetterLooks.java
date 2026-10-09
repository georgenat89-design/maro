package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.BetterLooksScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Better Looks: one panel of switches that clean up how the game looks: sky, weather, fog and light;
 * particles; the menus and HUD; the hand in first person; and Color Correct's grade, all in one place.
 * Each switch only changes what you see. The panel opens from its button or a right click on the
 * module. Hooks: {@link dev.maro.mixin.BetterLooksMixins}, and No Render's for the overlays they share.
 */
public class BetterLooks extends Module {
    private static BetterLooks instance;

    /** A section of the panel: its title, the line under it, and its settings. */
    public record Section(String title, String subtitle, List<Setting<?>> settings) {
    }

    // ---- World
    private final NumberSetting gammaBoost = add(new NumberSetting("Gamma Boost", "Lightens dark places; 0% leaves light as it is", 0, 0, 100, 5).suffix("%"));
    private final BooleanSetting clearWater = toggle("Clear Water", "See as far underwater as on land");
    private final BooleanSetting brightUnderwater = toggle("Bright Underwater", "Full sight underwater straight away, not after a while under");
    private final BooleanSetting clearLava = toggle("Clear Lava", "See through lava, as with fire resistance");
    private final BooleanSetting noFog = toggle("No Fog", "No haze over far land");
    private final BooleanSetting noBlindFog = toggle("No Blindness Fog", "Blindness and Darkness do not close the view in");
    private final BooleanSetting noWeather = toggle("No Weather", "No rain or snow falling");
    private final BooleanSetting noRainGloom = toggle("No Rain Gloom", "Sky and light stay clear while it rains");
    private final BooleanSetting noClouds = toggle("No Clouds", "No clouds in the sky");
    private final BooleanSetting noSunMoon = toggle("No Sun & Moon", "A clean sky without the sun and moon");
    private final BooleanSetting noStars = toggle("No Stars", "No stars at night");
    private final BooleanSetting noSunrise = toggle("No Sunrise Glow", "No orange glow at sunrise and sunset");
    private final BooleanSetting noShadows = toggle("No Entity Shadows", "No round shadows under mobs, players and items");
    private final BooleanSetting noGlint = toggle("No Enchant Glint", "Enchanted items without the purple shimmer");

    // ---- Particles
    private final ModeSetting particleMode = add(new ModeSetting("Particle Mode",
            "Vanilla: all of them. Density: the share set below. Minimal: one in ten", "Vanilla", "Vanilla", "Density", "Minimal"));
    private final NumberSetting particleDensity = add(new NumberSetting("Particle Density", "The share of particles kept in Density mode", 0.55, 0, 1, 0.05)
            .visible(() -> particleMode.is("Density")));
    private final BooleanSetting reduceCrit = toggle("Reduce Crit FX", "Far fewer critical and enchanted hit sparks");
    private final BooleanSetting reducePotion = toggle("Reduce Potion FX", "Far fewer potion swirls round players and mobs");
    private final BooleanSetting noExplode = toggle("No Explode FX", "No explosion clouds or flying debris");
    private final BooleanSetting reduceFlash = toggle("Reduce Flash", "No bright firework flashes");
    private final BooleanSetting reduceLeaf = toggle("Reduce Leaf Particles", "Far fewer falling leaves");
    private final BooleanSetting reduceTotem = toggle("Reduce Totem FX", "A quarter of the particles when a totem pops");
    private final BooleanSetting smallTotem = toggle("Small Totem Anim", "The totem pop drawn at half size");
    private final BooleanSetting noTotemOverlay = toggle("Remove Totem Screen Overlay", "No totem filling the screen when one pops");
    private final BooleanSetting noRainSplash = toggle("No Rain Splashes", "No splashes where rain lands");
    private final BooleanSetting noCampfireSmoke = toggle("No Campfire Smoke", "No smoke columns from campfires");
    private final BooleanSetting noPortalParticles = toggle("No Portal Particles", "No purple specks round portals, obsidian and endermen");
    private final BooleanSetting noSweep = toggle("No Sweep Particles", "No sweep arcs from sword hits");
    private final BooleanSetting noDamageHearts = toggle("No Damage Hearts", "No dark hearts when something takes a hit");
    private final BooleanSetting noMining = toggle("No Mining Particles", "No chips flying off blocks being mined");
    private final BooleanSetting noAmbient = toggle("No Ambient Particles", "No ash, spores or underwater specks drifting in the air");

    // ---- Interface
    private final BooleanSetting noPumpkin = toggle("No Pumpkin Blur", "No pumpkin outline when wearing one");
    private final BooleanSetting noVignette = toggle("No Vignette", "No dark corners round the screen");
    private final BooleanSetting transparentInvBg = toggle("Transparent Inv BG", "No dark shade behind chests and inventories");
    private final BooleanSetting noMenuBlur = toggle("No Menu Blur", "The world stays sharp behind menus");
    private final BooleanSetting noToasts = toggle("No Toasts", "No advancement, recipe or tutorial pop-ups");
    private final BooleanSetting noBossBar = toggle("No Boss Bar", "No boss or event bars at the top");
    private final BooleanSetting noScoreboard = toggle("No Scoreboard", "No sidebar scoreboard");
    private final BooleanSetting noPotionIcons = toggle("No Potion Icons", "No effect icons in the top right");
    private final BooleanSetting noNausea = toggle("No Nausea Warp", "No warping from nausea or portals");
    private final BooleanSetting noPowderSnow = toggle("No Powder Snow Frost", "No frost round the screen in powder snow");
    private final BooleanSetting noHurtCam = toggle("No Hurt Cam", "No camera shake when you take damage");
    private final BooleanSetting noFireOverlay = toggle("No Fire Overlay", "No flames across the screen while you burn");
    private final BooleanSetting noUnderwaterOverlay = toggle("No Underwater Overlay", "No water texture over the screen underwater");

    // ---- ViewModel
    private final BooleanSetting lowFire = toggle("Low Fire", "The fire on screen sits low, out of the way");
    private final BooleanSetting lowShield = toggle("Low Shield", "Your shield held lower so it covers less");
    private final BooleanSetting noEquipDip = toggle("No Equip Dip", "Items do not dip when you switch to them");
    private final BooleanSetting hideOffHand = toggle("Hide Off Hand", "Leave the off hand out of first person");

    private final List<Section> sections = List.of(
            new Section("World", "Sky, weather, light and fog", List.of(gammaBoost, clearWater, brightUnderwater, clearLava, noFog, noBlindFog,
                    noWeather, noRainGloom, noClouds, noSunMoon, noStars, noSunrise, noShadows, noGlint)),
            new Section("Particles", "Particle density, totems and effects", List.of(particleMode, particleDensity, reduceCrit, reducePotion,
                    noExplode, reduceFlash, reduceLeaf, reduceTotem, smallTotem, noTotemOverlay, noRainSplash, noCampfireSmoke,
                    noPortalParticles, noSweep, noDamageHearts, noMining, noAmbient)),
            new Section("Interface", "Menus, inventories and the HUD", List.of(noPumpkin, noVignette, transparentInvBg, noMenuBlur, noToasts,
                    noBossBar, noScoreboard, noPotionIcons, noNausea, noPowderSnow, noHurtCam, noFireOverlay, noUnderwaterOverlay)),
            new Section("ViewModel", "Your hand and items in first person", List.of(lowFire, lowShield, noEquipDip, hideOffHand)));

    public BetterLooks() {
        super("Better Looks", "One panel to clean up the sky, fog, particles, menus and your hand, with Color Correct built in", Category.VISUALS);
        instance = this;
    }

    private BooleanSetting toggle(String name, String description) {
        return add(new BooleanSetting(name, description, false));
    }

    public static BetterLooks get() {
        return instance != null ? instance : ModuleManager.get(BetterLooks.class);
    }

    /** The panel's sections, World to ViewModel; the panel adds Color Correct's after them. */
    public List<Section> sections() {
        return sections;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        List<SettingSection> out = new ArrayList<>();
        for (Section s : sections) out.add(SettingSection.of(s.title(), s.settings().toArray(Setting<?>[]::new)));
        return out;
    }

    @Override
    public Screen panel(Screen parent) {
        return new BetterLooksScreen(parent, this);
    }

    // ---- read by the hooks, every frame ------------------------------------------------------

    private static boolean on(java.util.function.Function<BetterLooks, BooleanSetting> pick) {
        BetterLooks m = instance;
        return m != null && m.isEnabled() && pick.apply(m).get();
    }

    private static BetterLooks active() {
        BetterLooks m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    /** Whether one of No Render's parts is hidden by a switch here. */
    public static boolean hides(NoRender.Part part) {
        BetterLooks m = active();
        if (m == null) return false;
        return switch (part) {
            case FIRE -> m.noFireOverlay.get();
            case PUMPKIN -> m.noPumpkin.get();
            case POWDER_SNOW -> m.noPowderSnow.get();
            case UNDERWATER -> m.noUnderwaterOverlay.get();
            case VIGNETTE -> m.noVignette.get();
            case PORTAL, NAUSEA -> m.noNausea.get();
            case TOTEM -> m.noTotemOverlay.get();
            case HURT_CAMERA -> m.noHurtCam.get();
            case BOSS_BAR -> m.noBossBar.get();
            case SCOREBOARD -> m.noScoreboard.get();
            case POTION_ICONS -> m.noPotionIcons.get();
            case WEATHER -> m.noWeather.get();
            case EXPLOSIONS -> m.noExplode.get();
            case MINING_PARTICLES -> m.noMining.get();
            default -> false;
        };
    }

    /** The lightmap's gamma with Gamma Boost on top. */
    public static double gamma(double gamma) {
        BetterLooks m = active();
        if (m == null || m.gammaBoost.get() <= 0) return gamma;
        return Math.max(gamma, 1 + 14 * m.gammaBoost.get() / 100.0);
    }

    public static boolean clearWater() {
        return on(m -> m.clearWater);
    }

    public static boolean brightUnderwater() {
        return on(m -> m.brightUnderwater);
    }

    public static boolean clearLava() {
        return on(m -> m.clearLava);
    }

    public static boolean noFog() {
        return on(m -> m.noFog);
    }

    public static boolean noBlindFog() {
        return on(m -> m.noBlindFog);
    }

    public static boolean noRainGloom() {
        return on(m -> m.noRainGloom);
    }

    public static boolean noClouds() {
        return on(m -> m.noClouds);
    }

    public static boolean noSunMoon() {
        return on(m -> m.noSunMoon);
    }

    public static boolean noStars() {
        return on(m -> m.noStars);
    }

    public static boolean noSunrise() {
        return on(m -> m.noSunrise);
    }

    public static boolean noShadows() {
        return on(m -> m.noShadows);
    }

    public static boolean noGlint() {
        return on(m -> m.noGlint);
    }

    public static boolean transparentInvBg() {
        return on(m -> m.transparentInvBg);
    }

    public static boolean noMenuBlur() {
        return on(m -> m.noMenuBlur);
    }

    public static boolean noToasts() {
        return on(m -> m.noToasts);
    }

    public static boolean lowShield() {
        return on(m -> m.lowShield);
    }

    public static boolean noEquipDip() {
        return on(m -> m.noEquipDip);
    }

    public static boolean hideOffHand() {
        return on(m -> m.hideOffHand);
    }

    /** How far down to move the fire on screen. */
    public static float fireDrop() {
        return on(m -> m.lowFire) ? 0.3f : 0f;
    }

    /** The totem pop animation's size. */
    public static float totemScale() {
        return on(m -> m.smallTotem) ? 0.5f : 1f;
    }

    // ---- particles -----------------------------------------------------------------------------

    private enum Kind {OTHER, CRIT, POTION, FLASH, LEAF, TOTEM, RAIN, SMOKE, PORTAL, SWEEP, DAMAGE, AMBIENT}

    private static final Map<ParticleType<?>, Kind> KINDS = new IdentityHashMap<>();

    private static Kind kindOf(ParticleType<?> type) {
        Kind kind = KINDS.get(type);
        if (kind != null) return kind;
        Identifier id = Registries.PARTICLE_TYPE.getId(type);
        String path = id == null ? "" : id.getPath();
        kind = switch (path) {
            case "crit", "enchanted_hit" -> Kind.CRIT;
            case "entity_effect", "effect", "instant_effect", "witch" -> Kind.POTION;
            case "flash" -> Kind.FLASH;
            case "tinted_leaves", "cherry_leaves", "pale_oak_leaves" -> Kind.LEAF;
            case "totem_of_undying" -> Kind.TOTEM;
            case "rain" -> Kind.RAIN;
            case "campfire_cosy_smoke", "campfire_signal_smoke" -> Kind.SMOKE;
            case "portal", "reverse_portal" -> Kind.PORTAL;
            case "sweep_attack" -> Kind.SWEEP;
            case "damage_indicator" -> Kind.DAMAGE;
            case "ash", "white_ash", "crimson_spore", "warped_spore", "spore_blossom_air", "falling_spore_blossom", "mycelium", "underwater" -> Kind.AMBIENT;
            default -> Kind.OTHER;
        };
        KINDS.put(type, kind);
        return kind;
    }

    /** Whether a particle of this type should be left out this time. */
    public static boolean dropParticle(ParticleType<?> type) {
        BetterLooks m = active();
        if (m == null) return false;
        float keep = switch (kindOf(type)) {
            case CRIT -> m.reduceCrit.get() ? 0.2f : 1f;
            case POTION -> m.reducePotion.get() ? 0.15f : 1f;
            case FLASH -> m.reduceFlash.get() ? 0f : 1f;
            case LEAF -> m.reduceLeaf.get() ? 0.1f : 1f;
            case TOTEM -> m.reduceTotem.get() ? 0.25f : 1f;
            case RAIN -> m.noRainSplash.get() ? 0f : 1f;
            case SMOKE -> m.noCampfireSmoke.get() ? 0f : 1f;
            case PORTAL -> m.noPortalParticles.get() ? 0f : 1f;
            case SWEEP -> m.noSweep.get() ? 0f : 1f;
            case DAMAGE -> m.noDamageHearts.get() ? 0f : 1f;
            case AMBIENT -> m.noAmbient.get() ? 0f : 1f;
            case OTHER -> 1f;
        };
        if (m.particleMode.is("Density")) keep *= m.particleDensity.getFloat();
        else if (m.particleMode.is("Minimal")) keep *= 0.1f;
        return keep < 1f && (keep <= 0f || ThreadLocalRandom.current().nextFloat() >= keep);
    }
}
