package dev.maro.module.impl.visuals;

import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.KeyUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/**
 * An inspect animation for whatever you hold, after the knife inspects in Valorant: press the key
 * and the item lifts towards the middle of the screen, spins twice with a glowing swoosh following
 * it round, and settles back into your hand. Cosmetic only - nobody else sees it. The item is
 * moved in {@link dev.maro.mixin.InspectMixin}; the swoosh is drawn over the HUD here.
 */
public class ItemInspect extends Module {
    private static ItemInspect instance;

    private final KeybindSetting key = add(new KeybindSetting("Inspect Key", "Press to inspect what you are holding", GLFW.GLFW_KEY_Y));
    private final ModeSetting style = add(new ModeSetting("Style", "The colour of the swoosh",
            "Reaver", "Reaver", "RGX", "Crimson", "Theme", "Rainbow"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the inspect plays", 1, 0.5, 2, 0.05).suffix("x"));
    private final NumberSetting spins = add(new NumberSetting("Spins", "How many times it spins round", 2, 1, 4, 1));
    private final BooleanSetting trail = add(new BooleanSetting("Swoosh", "A glowing trail that follows the spin", true));
    private final NumberSetting trailSize = add(new NumberSetting("Swoosh Size", "How wide the swoosh sweeps", 100, 50, 160, 5)
            .suffix("%").visible(trail::get));
    private final BooleanSetting sound = add(new BooleanSetting("Swish Sound", "A soft swish as it spins", true));
    private final BooleanSetting onEquip = add(new BooleanSetting("Inspect On Equip", "Inspect swords and axes by themselves when you switch to them", false));

    /** How long the whole inspect takes at a Speed of 1, in seconds. */
    private static final double LENGTH = 1.7;
    /** Where the spin starts and ends, as parts of the whole. */
    private static final double SPIN_FROM = 0.2;
    private static final double SPIN_TO = 0.78;

    private long startedAt;
    private boolean playing;
    private boolean keyWasDown;
    private int slot = -1;
    private ItemStack held = ItemStack.EMPTY;
    private boolean swished;

    public ItemInspect() {
        super("Item Inspect", "A Valorant-style inspect animation for what you hold", Category.VISUALS);
        instance = this;
    }

    @Override
    protected void onDisable() {
        playing = false;
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            playing = false;
            return;
        }
        var inventory = mc.player.getInventory();
        ItemStack stack = mc.player.getMainHandStack();
        boolean switched = inventory.getSelectedSlot() != slot || !ItemStack.areItemsEqual(stack, held);
        slot = inventory.getSelectedSlot();
        held = stack.copy();

        // Like any inspect, swapping or swinging puts it away.
        if (playing && (switched || mc.player.handSwinging)) playing = false;

        boolean down = mc.currentScreen == null && isDown(key.get());
        if (down && !keyWasDown && !stack.isEmpty()) start();
        keyWasDown = down;

        if (switched && onEquip.get() && (stack.isIn(ItemTags.SWORDS) || stack.getItem() instanceof AxeItem)) start();

        if (playing && progress() >= 1) playing = false;

        if (playing && !swished && progress() >= SPIN_FROM) {
            swished = true;
            if (sound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.7f, 0.25f));
            }
        }
    }

    private void start() {
        startedAt = System.nanoTime();
        playing = true;
        swished = false;
    }

    private boolean isDown(int code) {
        if (code == KeyUtil.NONE) return false;
        long window = mc.getWindow().getHandle();
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(window, code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    /** How far through the inspect it is, 0 to 1. */
    private double progress() {
        return (System.nanoTime() - startedAt) / 1e9 * speed.get() / LENGTH;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double easeInOutCubic(double t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** How far the spin has turned, in degrees. */
    private double spinAngle(double t) {
        return easeInOutCubic((t - SPIN_FROM) / (SPIN_TO - SPIN_FROM)) * 360 * spins.getInt();
    }

    /** How far the item is lifted out of its rest: in at the start, out at the end. */
    private static double lift(double t) {
        return smooth(t / SPIN_FROM) * (1 - smooth((t - 0.86) / 0.14));
    }

    /**
     * Turns the item in first person for the current point of the inspect: lifted towards the
     * middle and tilted to face you, spun round, with a small flick as it settles.
     */
    public static void transform(MatrixStack matrices, boolean leftHand) {
        ItemInspect m = instance;
        if (m == null || !m.isEnabled() || !m.playing) return;
        double t = Math.min(1, m.progress());
        float side = leftHand ? -1 : 1;
        float raise = (float) lift(t);
        float spin = (float) m.spinAngle(t);
        // A little overshoot after the spin, settling back to nothing.
        double settle = (t - SPIN_TO) / 0.12;
        float flick = settle > 0 && settle < 1 ? (float) (Math.sin(settle * Math.PI) * 22 * (1 - settle)) : 0;

        matrices.translate(-0.14f * raise * side, 0.1f * raise, 0.06f * raise);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-28 * raise * side));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18 * raise));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((spin + flick) * side));
    }

    // ---- the swoosh ---------------------------------------------------------------------

    private int[] colors() {
        return switch (style.get()) {
            case "RGX" -> new int[]{0xFF3CF0D0, 0xFF2B8CFF};
            case "Crimson" -> new int[]{0xFFFF3B5C, 0xFFFFA23B};
            case "Theme" -> new int[]{Theme.accent(), Theme.accent2()};
            case "Rainbow" -> {
                float hue = (System.currentTimeMillis() % 2400) / 2400f;
                yield new int[]{ColorUtil.hsv(hue, 0.75f, 1f), ColorUtil.hsv((hue + 0.18f) % 1f, 0.8f, 1f)};
            }
            default -> new int[]{0xFFB06BFF, 0xFF5B3BFF};
        };
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!playing || !trail.get() || !inGame() || mc.options.hudHidden || !mc.options.getPerspective().isFirstPerson()) return;
        double t = Math.min(1, progress());
        double inSpin = (t - SPIN_FROM) / (SPIN_TO - SPIN_FROM);
        if (inSpin <= 0 || inSpin >= 1) return;

        // Strongest mid-spin, where it is fastest; gone at either end.
        float strength = (float) Math.sin(inSpin * Math.PI);
        boolean left = mc.player.getMainArm() == net.minecraft.util.Arm.LEFT;
        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();
        float cx = left ? w * 0.29f : w * 0.71f;
        float cy = h * 0.74f;
        float radius = h * 0.19f * trailSize.getFloat() / 100f;

        float head = (float) m360(spinAngle(t) * (left ? -1 : 1) - 90);
        float sweep = 150 * strength * trailSize.getFloat() / 100f * (left ? 1 : -1);
        int[] c = colors();
        int tail = ColorUtil.withAlpha(c[1], 0);

        // A wide soft glow, the bright band, and a thin bright edge, all trailing behind the head.
        Render2D.arc(ctx, cx, cy, radius + 6, 16, head + sweep, -sweep, tail, ColorUtil.withAlpha(c[1], Math.round(70 * strength)));
        Render2D.arc(ctx, cx, cy, radius, 7, head + sweep, -sweep, tail, ColorUtil.withAlpha(c[0], Math.round(220 * strength)));
        Render2D.arc(ctx, cx, cy, radius - 1, 1.5f, head + sweep * 0.6f, -sweep * 0.6f, tail,
                ColorUtil.withAlpha(0xFFFFFFFF, Math.round(200 * strength)));
    }

    private static double m360(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    /** Whether an inspect is playing, for the in-game test. */
    public boolean inspecting() {
        return playing;
    }

    public void inspect() {
        if (inGame() && !mc.player.getMainHandStack().isEmpty()) start();
    }
}
