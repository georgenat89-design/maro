package dev.maro.nathan.modules;

import java.util.List;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.render.PostEffect;

/**
 * Colour grades the finished frame: hue, saturation, colour boost, brightness,
 * shadows, contrast, gamma and a tint.
 *
 * <p>Every control has a value at which it does nothing, and at that value its
 * step is skipped outright rather than done with numbers that ought to change
 * nothing - so a control left alone leaves the picture exactly as it was.
 *
 * <p>It is a post-processing pass, the same machinery the game uses for the
 * green wash when you spectate a creeper. The world is drawn to a texture, that
 * texture is run through a fragment shader, and the result is blitted back. A
 * pass cannot read and write one target at once, so it goes out to a scratch
 * target and comes back through the game's own blit shader.
 *
 * <p>It runs after the world and before the HUD, so chat, the inventory and
 * everything else Meteor draws stay the colour they were meant to be. It runs
 * before any of the interface is even recorded, too, so a mod that copies the
 * world to draw behind a menu copies it graded. Grade Menus is for a mod that
 * gets round even that: with a menu open, the grade goes over the whole frame,
 * menu and all, once it is finished.
 */
public class ColorCorrect extends Module {
    /** Set from Better Looks' panel, so not listed on its own. */
    @Override
    public boolean hiddenInGui() {
        return true;
    }

    private static final Identifier SCRATCH = PostEffect.ours("scratch");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> gradeMenus = sgGeneral.add(new BoolSetting.Builder()
        .name("grade-menus")
        .description("While a menu is open (a chest, /shop, settings), grade the whole screen, menu and all. Turn it on if the world loses its colour behind menus, which another mod redrawing the world there can cause.")
        .defaultValue(false)
        .build()
    );
    private final SettingGroup sgColor = settings.createGroup("Color");
    private final SettingGroup sgLight = settings.createGroup("Light");
    private final SettingGroup sgTint = settings.createGroup("Tint");

    private final Setting<Double> saturation = sgColor.add(new DoubleSetting.Builder()
        .name("saturation")
        .description("0 is greyscale, 1 leaves it alone, higher is more colourful.")
        .defaultValue(1)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Double> hue = sgColor.add(new DoubleSetting.Builder()
        .name("hue")
        .description("Turns every colour round the colour wheel by this many degrees. 0 leaves them alone; 180 and -180 are the same place. How bright and how colourful things are is left as it was.")
        .defaultValue(0)
        .range(-180, 180)
        .sliderRange(-180, 180)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> colorBoost = sgColor.add(new DoubleSetting.Builder()
        .name("color-boost")
        .description("More colour where there is little and hardly any more where there is already a lot, so dull things come up without bright things burning out. Below 0 it takes colour away evenly. 0 leaves it alone.")
        .defaultValue(0)
        .range(-100, 100)
        .sliderRange(-100, 100)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> brightness = sgLight.add(new DoubleSetting.Builder()
        .name("brightness")
        .description("Multiplies every channel. 1 leaves it alone.")
        .defaultValue(1)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<Double> shadows = sgLight.add(new DoubleSetting.Builder()
        .name("shadows")
        .description("Brightens the dark parts of the picture, or below 0 darkens them, and leaves the bright parts nearly alone. Black stays black. 0 leaves it alone.")
        .defaultValue(0)
        .range(-100, 100)
        .sliderRange(-100, 100)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> contrast = sgLight.add(new DoubleSetting.Builder()
        .name("contrast")
        .description("Pushes light and dark apart around mid grey. 1 leaves it alone.")
        .defaultValue(1)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Double> gamma = sgLight.add(new DoubleSetting.Builder()
        .name("gamma")
        .description("Lifts or crushes the midtones without moving black or white. 1 leaves it alone.")
        .defaultValue(1)
        .sliderRange(0.1, 3)
        .build()
    );

    private final Setting<SettingColor> tint = sgTint.add(new ColorSetting.Builder()
        .name("tint")
        .description("Colour the frame is washed towards. Its alpha is ignored - how much is Tint Amount.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    // Under a new name because it is on a new scale: the old Tint Strength ran
    // from 0 to 1, and a saved 0.5 read as a percentage would be half a percent.
    // The old value is carried over once; see fromTag.
    private final Setting<Double> tintAmount = sgTint.add(new DoubleSetting.Builder()
        .name("tint-amount")
        .description("How far towards the tint colour to go, as a percentage. 0 is off.")
        .defaultValue(0)
        .range(0, 100)
        .sliderRange(0, 100)
        .decimalPlaces(0)
        .build()
    );

    private final PostEffect effect = new PostEffect("color_correct");

    public ColorCorrect() {
        super(NameeProtectAddon.CATEGORY, "color-correct", "Hue, saturation, colour boost, brightness, shadows, contrast, gamma and tint over the whole frame.");
    }

    /** Under the settings: everything back to where it does nothing, in one press. */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();

        WButton reset = list.add(theme.button("Reset")).expandX().widget();

        reset.action = () -> {
            for (SettingGroup group : settings) for (Setting<?> setting : group) setting.reset();
        };

        return list;
    }

    /**
     * Tint Strength, from 0 to 1, became Tint Amount, from 0 to 100. A saved
     * strength is carried over as the same amount of tint - but only while no
     * amount has been saved, so it happens once and never undoes a change made
     * since.
     */
    @Override
    public Module fromTag(NbtCompound tag) {
        Module loaded = super.fromTag(tag);

        try {
            NbtCompound strength = saved(tag, "tint-strength");

            if (strength != null && saved(tag, "tint-amount") == null) {
                tintAmount.set(Math.max(0, Math.min(100, strength.getDouble("value", 0) * 100)));
            }
        } catch (RuntimeException e) {
            NameeProtectAddon.LOG.warn("color-correct could not carry over its old tint strength", e);
        }

        return loaded;
    }

    private static NbtCompound saved(NbtCompound module, String name) {
        for (NbtElement groupTag : module.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(groupTag instanceof NbtCompound group)) continue;

            for (NbtElement settingTag : group.getListOrEmpty("settings")) {
                if (settingTag instanceof NbtCompound setting && name.equals(setting.getString("name", ""))) return setting;
            }
        }

        return null;
    }

    /** Whether this frame's grade waits for the menu to be drawn: decided once a frame, before the interface. */
    private static boolean overMenu;
    /** Where the grade last ran in the frame, for the in-game test: "hud", "screen", "gui" or "over-menu". */
    private static String lastPoint = "";

    /**
     * Called from the frame, after the world is drawn and before the HUD is:
     * as the first of the HUD, the screen or the interface pass begins.
     * Static because the mixin has no other way to reach the module, and
     * tolerant of the module being absent or off.
     */
    public static void applyTo(Framebuffer target, ObjectAllocator allocator, String point) {
        ColorCorrect module = active();
        overMenu = module != null && module.gradeMenus.get() && MinecraftClient.getInstance().currentScreen != null;
        if (module == null || overMenu) return;

        module.process(target, allocator);
        lastPoint = point;
    }

    /** Called once the interface is drawn: with Grade Menus on and a menu open, grades the whole frame. */
    public static void applyOverMenu(Framebuffer target, ObjectAllocator allocator) {
        ColorCorrect module = active();
        if (!overMenu || module == null) return;
        overMenu = false;

        module.process(target, allocator);
        lastPoint = "over-menu";
    }

    public static String lastPoint() {
        return lastPoint;
    }

    private static ColorCorrect active() {
        Modules modules = Modules.get();
        if (modules == null) return null;

        ColorCorrect module = modules.get(ColorCorrect.class);
        return module == null || !module.isActive() ? null : module;
    }

    @Override
    public void onActivate() {
        effect.reset();
    }

    @Override
    public void onDeactivate() {
        effect.discard();
    }

    private void process(Framebuffer target, ObjectAllocator allocator) {
        // Reported rather than toggled off: this runs inside the frame, and
        // toggling a module from there would unsubscribe it in the middle of
        // the event that is calling it.
        if (!effect.run(target, allocator, key(), this::config)) {
            error("The colour shader would not run - see the log. Toggle the module to try again.");
        }
    }

    private PostEffectPipeline config() {
        PostEffectPipeline.Pass correct = PostEffect.pass(
            "color_correct",
            List.of(PostEffect.input("In", PostEffect.MAIN)),
            SCRATCH,
            "ColorCorrectConfig",
            List.of(
                new UniformValue.FloatValue((float) (double) saturation.get()),
                new UniformValue.FloatValue((float) (double) brightness.get()),
                new UniformValue.FloatValue((float) (double) contrast.get()),
                new UniformValue.FloatValue((float) (double) gamma.get()),
                new UniformValue.FloatValue(tint.get().r / 255f),
                new UniformValue.FloatValue(tint.get().g / 255f),
                new UniformValue.FloatValue(tint.get().b / 255f),
                new UniformValue.FloatValue((float) (tintAmount.get() / 100)),
                new UniformValue.FloatValue((float) Math.toRadians(hue.get())),
                new UniformValue.FloatValue((float) (shadows.get() / 100)),
                new UniformValue.FloatValue((float) (colorBoost.get() / 100))
            ));

        // A pass cannot read the target it writes, so the corrected frame comes
        // back from the scratch target through the game's own blit shader.
        return new PostEffectPipeline(
            PostEffect.target("scratch", false),
            List.of(correct, PostEffect.copy(SCRATCH, PostEffect.MAIN)));
    }

    /** Every value the chain was built from, in one comparable string. */
    private String key() {
        SettingColor color = tint.get();

        return saturation.get() + "|" + brightness.get() + "|" + contrast.get() + "|" + gamma.get()
            + "|" + color.r + "," + color.g + "," + color.b + "|" + tintAmount.get() + "|" + hue.get()
            + "|" + shadows.get() + "|" + colorBoost.get();
    }
}
