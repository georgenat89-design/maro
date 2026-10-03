package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.SkinAccessoriesScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.Items;
import java.awt.Color;
import java.util.List;

/** Cosmetic player geometry. Settings are read at submission time, so changes are immediate. */
public final class SkinAccessories extends Module {
    public static final String[] PRESETS = {"Dragon", "Angel", "Demon", "Fox", "Cat", "Bunny", "Cyber", "Royal", "Forest", "Butterfly", "Astral", "Adventurer"};
    private final ModeSetting preset = add(new ModeSetting("Preset", "Apply a complete look, then customize any part", "Dragon", PRESETS).onChange(this::applyPreset));
    private final ButtonSetting preview = add(new ButtonSetting("3D Preview", "Rotate your character and try themed looks", "Open",
        () -> mc.setScreen(new SkinAccessoriesScreen(mc.currentScreen, this))));
    private final ModeSetting target = add(new ModeSetting("Players", "Who wears the accessories on your screen", "Self", "Self", "Everyone"));
    private final ModeSetting head = add(new ModeSetting("Head", "Horns, animal ears, or headwear", "Dragon Horns", "None", "Dragon Horns", "Devil Horns", "Cat Ears", "Fox Ears", "Bunny Ears", "Antlers", "Crown", "Headphones"));
    private final ModeSetting wings = add(new ModeSetting("Wings", "Choose a wing silhouette", "Dragon", "None", "Dragon", "Angel", "Bat", "Butterfly", "Cyber"));
    private final ModeSetting tail = add(new ModeSetting("Tail", "An animated segmented tail", "Dragon", "None", "Dragon", "Devil", "Cat", "Fox", "Cyber"));
    private final ModeSetting halo = add(new ModeSetting("Halo", "Floating rings and orbiting gems", "None", "None", "Ring", "Double Ring", "Star", "Orbit"));
    private final ModeSetting shoulders = add(new ModeSetting("Shoulders", "Armor attached to your arms", "None", "None", "Spikes", "Pauldrons", "Crystals"));
    private final ModeSetting back = add(new ModeSetting("Back", "A backpack, jetpack, or sheathed sword", "None", "None", "Backpack", "Jetpack", "Sword"));
    private final NumberSetting headSize = add(new NumberSetting("Head Size", "Scale head accessories", 1, 0.5, 1.75, 0.05));
    private final NumberSetting wingSize = add(new NumberSetting("Wing Size", "Scale wings from their attachment points", 1, 0.4, 1.8, 0.05));
    private final NumberSetting wingSpread = add(new NumberSetting("Wing Spread", "Open or fold your wings", 20, 0, 75, 1).suffix("°"));
    private final NumberSetting tailLength = add(new NumberSetting("Tail Length", "Scale the tail", 1, 0.4, 1.8, 0.05));
    private final NumberSetting haloHeight = add(new NumberSetting("Halo Height", "Distance above the head in model pixels", 3, 1, 10, 0.25));
    private final ColorSetting primary = add(new ColorSetting("Primary Color", "Main accessory color", 0xFF48385E));
    private final ColorSetting accent = add(new ColorSetting("Accent Color", "Inner ears, wing membranes, and highlights", 0xFFBA7BFF));
    private final ColorSetting haloColor = add(new ColorSetting("Halo Color", "Floating ring color", 0xFFFFD98A));
    private final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", "Slowly cycle the accent and halo colors", false));
    private final BooleanSetting glow = add(new BooleanSetting("Glowing Accents", "Keep colored accents bright in the dark", true));
    private final BooleanSetting motion = add(new BooleanSetting("Animate", "Wing flaps, tail sway, and floating halos", true));
    private final NumberSetting speed = add(new NumberSetting("Animation Speed", "Speed of accessory motion", 1, 0.1, 3, 0.05));
    private final NumberSetting strength = add(new NumberSetting("Motion Strength", "Amount of flapping and swaying", 1, 0, 2, 0.05));
    private final BooleanSetting movement = add(new BooleanSetting("React To Movement", "Stronger flaps and tail motion while moving", true));
    private final BooleanSetting hideHelmet = add(new BooleanSetting("Hide Head With Helmet", "Avoid overlap with equipped helmets", true));
    private final BooleanSetting hideElytra = add(new BooleanSetting("Hide Wings With Elytra", "Avoid overlap with equipped elytra", true));
    private final long animationOrigin = System.nanoTime();

    public SkinAccessories() { super("Skin Accessories", "Custom 3D horns, wings, tails, halos, and more. Visible in your client.", Category.VISUALS); }
    @Override public List<SettingSection> getSettingSections() {
        return List.of(section("Looks & Preview", preset, preview, target), section("Accessories", head, wings, tail, halo, shoulders, back),
            section("Fit", headSize, wingSize, wingSpread, tailLength, haloHeight), section("Palette", primary, accent, haloColor, rainbow, glow),
            section("Animation", motion, speed, strength, movement), section("Equipment", hideHelmet, hideElytra));
    }
    private SettingSection section(String name, Setting<?>... values) {
        var section = new SettingSection(name); for (var setting : values) section.add(setting); return section;
    }
    public String head() { return head.get(); }
    public String wings() { return wings.get(); }
    public String tail() { return tail.get(); }
    public String halo() { return halo.get(); }
    public String shoulders() { return shoulders.get(); }
    public String back() { return back.get(); }
    public String preset() { return preset.get(); }
    public float headSize() { return headSize.getFloat(); }
    public float wingSize() { return wingSize.getFloat(); }
    public float wingSpread() { return wingSpread.getFloat(); }
    public float tailLength() { return tailLength.getFloat(); }
    public float haloHeight() { return haloHeight.getFloat(); }
    public boolean glow() { return glow.get(); }
    public int primaryColor() { return primary.get(); }
    public int accentColor() { return rainbow.get() ? rainbowColor(0) : accent.get(); }
    public int haloColor() { return rainbow.get() ? rainbowColor(0.12f) : haloColor.get(); }
    private int rainbowColor(float offset) { return Color.HSBtoRGB((float)((System.nanoTime() - animationOrigin) / 1e9 / 16 + offset) % 1, 0.55f, 1); }
    public boolean wants(PlayerEntityRenderState state) {
        if (mc.player == null || state.invisible || state.spectator) return false;
        boolean previewing = mc.currentScreen instanceof SkinAccessoriesScreen;
        if (!isEnabled() && !previewing) return false;
        if (previewing) return state.id == mc.player.getId();
        if (mc.options.getPerspective().isFirstPerson() && mc.getCameraEntity() != null && mc.getCameraEntity().getId() == state.id) return false;
        return target.is("Everyone") || state.id == mc.player.getId();
    }
    public boolean showHead(PlayerEntityRenderState s) { return !hideHelmet.get() || s.equippedHeadStack.isEmpty(); }
    public boolean showWings(PlayerEntityRenderState s) { return !hideElytra.get() || !s.equippedChestStack.isOf(Items.ELYTRA); }
    public float phase(PlayerEntityRenderState s) {
        return motion.get() ? (float)(((System.nanoTime() - animationOrigin) / 1e9 * speed.get()) % (Math.PI * 200)) + s.id * 0.31f : 0;
    }
    public float motionAmount(PlayerEntityRenderState s) {
        return motion.get() ? strength.getFloat() * (movement.get() ? 0.45f + Math.min(1, Math.abs(s.limbAmplitudeInverse) * 3) * 0.55f : 1) : 0;
    }
    public void selectPreset(String name) { preset.set(name); applyPreset(preset.get()); }
    private void applyPreset(String name) {
        // Presets only change the accessory combination and palette; personal fit is retained.
        head.set("None"); wings.set("None"); tail.set("None"); halo.set("None"); shoulders.set("None"); back.set("None");
        switch (name) {
            case "Dragon" -> { head.set("Dragon Horns"); wings.set("Dragon"); tail.set("Dragon"); palette(0x48385E, 0xBA7BFF); }
            case "Angel" -> { wings.set("Angel"); halo.set("Ring"); palette(0xE9E5EF, 0xFFD98A); }
            case "Demon" -> { head.set("Devil Horns"); wings.set("Bat"); tail.set("Devil"); shoulders.set("Spikes"); palette(0x342833, 0xFF536D); }
            case "Fox" -> { head.set("Fox Ears"); tail.set("Fox"); palette(0xD77B3B, 0xFFF0D4); }
            case "Cat" -> { head.set("Cat Ears"); tail.set("Cat"); palette(0x393442, 0xF5A9CF); }
            case "Bunny" -> { head.set("Bunny Ears"); back.set("Backpack"); palette(0xEFEAF4, 0xF2A4C3); }
            case "Cyber" -> { head.set("Headphones"); wings.set("Cyber"); tail.set("Cyber"); palette(0x293447, 0x55E6F5); }
            case "Royal" -> { head.set("Crown"); shoulders.set("Pauldrons"); back.set("Sword"); palette(0xE6B958, 0xB386F0); }
            case "Forest" -> { head.set("Antlers"); shoulders.set("Crystals"); tail.set("Fox"); palette(0x685146, 0x8DE7B4); }
            case "Butterfly" -> { wings.set("Butterfly"); halo.set("Double Ring"); palette(0x4B3C69, 0xFFA7E9); }
            case "Astral" -> { head.set("Dragon Horns"); halo.set("Orbit"); shoulders.set("Crystals"); palette(0x3D4270, 0x98BCFF); }
            case "Adventurer" -> { head.set("Headphones"); back.set("Jetpack"); shoulders.set("Pauldrons"); palette(0x484F5C, 0xFFBC6C); }
        }
    }
    private void palette(int main, int detail) { primary.set(main | 0xFF000000); accent.set(detail | 0xFF000000); haloColor.set(detail | 0xFF000000); }
}
