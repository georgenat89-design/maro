package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * Where your hands and what they hold sit in first person: position, turn and size for each hand,
 * presets, how the swing looks, no dip when switching items, the old sword block pose, and the
 * off hand hidden if you like. Cosmetic only. Applied by {@link dev.maro.mixin.ViewModelMixin}.
 */
public class ViewModel extends Module {
    private static ViewModel instance;

    private static final String[] PRESETS = {"Vanilla", "Low", "Small", "Centered", "Lowered Wide", "Far"};

    private final ModeSetting preset = add(new ModeSetting("Preset", "A starting point for the sliders below", "Vanilla", PRESETS));
    private final ButtonSetting apply = add(new ButtonSetting("Apply Preset", "Set the sliders to the chosen preset", "Use", this::applyPreset));

    private final Hand main = new Hand("Main");
    private final Hand off = new Hand("Off");

    private final ModeSetting swingMode = add(new ModeSetting("Swing Mode", "How the swing looks: Vanilla, none at all, eased, a spin, or a stab forward",
            "Vanilla", "Vanilla", "None", "Smooth", "Spin", "Stab"));
    private final BooleanSetting noEquip = add(new BooleanSetting("No Equip Dip", "Keep the item still when you switch to it instead of dipping in", false));
    private final BooleanSetting blockPose = add(new BooleanSetting("Sword Block Pose", "Hold right-click with a sword for the old 1.8 block pose", false));
    private final BooleanSetting hideOff = add(new BooleanSetting("Hide Off Hand", "Leave the off hand out of first person", false));

    private final List<SettingSection> sections;

    /** One hand's sliders. */
    private final class Hand {
        final NumberSetting x, y, z, rotX, rotY, rotZ, scale;

        Hand(String name) {
            x = add(new NumberSetting(name + " X", "Left and right", 0, -2, 2, 0.01));
            y = add(new NumberSetting(name + " Y", "Up and down", 0, -2, 2, 0.01));
            z = add(new NumberSetting(name + " Z", "Nearer and further", 0, -2, 2, 0.01));
            rotX = add(new NumberSetting(name + " Rot X", "Tilt forward and back", 0, -180, 180, 1).suffix("°"));
            rotY = add(new NumberSetting(name + " Rot Y", "Turn left and right", 0, -180, 180, 1).suffix("°"));
            rotZ = add(new NumberSetting(name + " Rot Z", "Roll", 0, -180, 180, 1).suffix("°"));
            scale = add(new NumberSetting(name + " Scale", "Size", 1, 0.1, 3, 0.05).suffix("x"));
        }

        SettingSection section(String title) {
            SettingSection section = new SettingSection(title);
            for (NumberSetting s : new NumberSetting[]{x, y, z, rotX, rotY, rotZ, scale}) section.add(s);
            return section;
        }

        void set(double px, double py, double pz, double rx, double ry, double rz, double s) {
            x.set(px);
            y.set(py);
            z.set(pz);
            rotX.set(rx);
            rotY.set(ry);
            rotZ.set(rz);
            scale.set(s);
        }
    }

    public ViewModel() {
        super("View Model", "Move, turn and resize your hands and items in first person", Category.VISUALS);
        instance = this;
        SettingSection presets = new SettingSection("Preset");
        presets.add(preset);
        presets.add(apply);
        SettingSection animation = new SettingSection("Animation");
        animation.add(swingMode);
        animation.add(noEquip);
        animation.add(blockPose);
        animation.add(hideOff);
        sections = List.of(presets, main.section("Main Hand"), off.section("Off Hand"), animation);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    private void applyPreset() {
        switch (preset.get()) {
            case "Low" -> { main.set(0, -0.15, 0, 0, 0, 0, 1); off.set(0, -0.15, 0, 0, 0, 0, 1); }
            case "Small" -> { main.set(0.05, -0.05, 0, 0, 0, 0, 0.7); off.set(-0.05, -0.05, 0, 0, 0, 0, 0.7); }
            case "Centered" -> { main.set(-0.25, -0.05, -0.1, 0, 0, 0, 0.85); off.set(0.25, -0.05, -0.1, 0, 0, 0, 0.85); }
            case "Lowered Wide" -> { main.set(0.12, -0.2, 0, 0, 10, 0, 1); off.set(-0.12, -0.2, 0, 0, -10, 0, 1); }
            case "Far" -> { main.set(0.05, 0.02, -0.35, 0, 0, 0, 1.1); off.set(-0.05, 0.02, -0.35, 0, 0, 0, 1.1); }
            default -> { main.set(0, 0, 0, 0, 0, 0, 1); off.set(0, 0, 0, 0, 0, 0, 1); }
        }
    }

    // ---- used while the hands are drawn -------------------------------------------------

    /** The swing of the hand being drawn right now, kept for the item turns that follow. */
    private static float swing;
    private static boolean blockingNow;

    private static boolean on() {
        return instance != null && instance.isEnabled();
    }

    public static boolean hideOffHand() {
        return on() && instance.hideOff.get() || BetterLooks.hideOffHand();
    }

    public static float equipProgress(float progress) {
        return on() && instance.noEquip.get() || BetterLooks.noEquipDip() ? 0 : progress;
    }

    /**
     * The swing progress to give the game's own swing for this hand, remembering the real one
     * for {@link #itemTransform}. None, Spin and Stab do their own movement, so the game's is off.
     */
    public static float swingProgress(float progress, boolean mainHand, ItemStack stack) {
        swing = progress;
        blockingNow = on() && mainHand && instance.blockPose.get() && stack.isIn(ItemTags.SWORDS)
                && net.minecraft.client.MinecraftClient.getInstance().options.useKey.isPressed();
        if (!on()) return progress;
        if (blockingNow) return 0;
        return switch (instance.swingMode.get()) {
            case "None", "Spin", "Stab" -> 0;
            case "Smooth" -> (float) (progress * progress * (3 - 2 * progress));
            default -> progress;
        };
    }

    /** The whole hand, moved in view space before the game lays it out. */
    public static void handTransform(MatrixStack matrices, boolean mainHand, boolean leftArm) {
        if (!on()) return;
        Hand hand = mainHand ? instance.main : instance.off;
        // X follows the arm, so positive X moves either hand outwards.
        float side = leftArm ? -1 : 1;
        matrices.translate(hand.x.getFloat() * side, hand.y.getFloat(), hand.z.getFloat());
    }

    /** The item itself, turned and sized about its own middle, plus the swing styles and block pose. */
    public static void itemTransform(MatrixStack matrices, boolean mainHand, boolean leftArm) {
        if (!on()) return;
        Hand hand = mainHand ? instance.main : instance.off;
        float side = leftArm ? -1 : 1;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(hand.rotX.getFloat()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(hand.rotY.getFloat() * side));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(hand.rotZ.getFloat() * side));
        float s = hand.scale.getFloat();
        if (s != 1) matrices.scale(s, s, s);

        if (mainHand && blockingNow) {
            // The 1.7/1.8 sword block.
            matrices.translate(-0.14142136f * side, 0.08f, 0.14142136f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(13.365f * side));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(78.05f * side));
            return;
        }
        if (swing <= 0) return;
        switch (instance.swingMode.get()) {
            case "Spin" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-360f * swing * side));
            case "Stab" -> matrices.translate(0, 0, -0.45f * (float) Math.sin(swing * Math.PI));
            default -> {
            }
        }
    }
}
