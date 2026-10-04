package dev.maro.module.impl.visuals;

import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
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
 * and the item plays one of several templates - lifted, spun, flipped, tossed or shown off - and
 * settles back into your hand, with a glowing swoosh following the fast parts. Cosmetic only. The
 * item is moved in {@link dev.maro.mixin.InspectMixin}; the swoosh is drawn over the HUD here.
 *
 * <p>Each template is a set of keyframes per channel, joined by Catmull-Rom curves so the motion
 * flows through each key instead of stopping at it.
 */
public class ItemInspect extends Module {
    private static ItemInspect instance;

    private static final String[] TEMPLATES = {"Twirl", "Butterfly", "Toss", "Showcase", "Flick", "Random"};

    private final KeybindSetting key = add(new KeybindSetting("Inspect Key", "Press to inspect what you are holding", GLFW.GLFW_KEY_Y));
    private final ModeSetting template = add(new ModeSetting("Animation", "Which inspect to play. Random picks a different one each time", "Twirl", TEMPLATES));
    private final NumberSetting duration = add(new NumberSetting("Duration", "How long the inspect lasts", 2.6, 1, 6, 0.1).suffix("s"));
    private final BooleanSetting trail = add(new BooleanSetting("Swoosh", "A glowing trail that follows the fast parts", true));
    private final ColorSetting color = add(new ColorSetting("Swoosh Color", "The colour of the swoosh", 0xFFA66BFF).visible(trail::get));
    private final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", "Cycle the swoosh through every colour", false).visible(trail::get));
    private final NumberSetting trailSize = add(new NumberSetting("Swoosh Size", "How wide the swoosh sweeps", 100, 50, 160, 5)
            .suffix("%").visible(trail::get));
    private final BooleanSetting sound = add(new BooleanSetting("Swish Sound", "A soft swish when it spins", true));
    private final BooleanSetting onEquip = add(new BooleanSetting("Inspect On Equip", "Inspect swords and axes by themselves when you switch to them", false));

    // ---- templates ----------------------------------------------------------------------

    /** One channel: keyframe times (0 to 1) and values. */
    private record Track(double[] t, double[] v) {
        double at(double time) {
            if (time <= t[0]) return v[0];
            int n = t.length;
            if (time >= t[n - 1]) return v[n - 1];
            int i = 0;
            while (time > t[i + 1]) i++;
            double u = (time - t[i]) / (t[i + 1] - t[i]);
            double p0 = v[Math.max(0, i - 1)], p1 = v[i], p2 = v[i + 1], p3 = v[Math.min(n - 1, i + 2)];
            // Catmull-Rom through the keys.
            return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
        }
    }

    private static Track k(double[] t, double... v) {
        return new Track(t, v);
    }

    /** A whole animation: offsets in view space and turns in degrees, plus which turn drives the swoosh. */
    private record Template(Track x, Track y, Track z, Track rx, Track ry, Track rz, int swooshChannel, double swishAt) {
        double channel(int which, double t) {
            return switch (which) {
                case 0 -> rx.at(t);
                case 1 -> ry.at(t);
                case 2 -> rz.at(t);
                default -> 0;
            };
        }
    }

    private static final Template TWIRL;
    private static final Template BUTTERFLY;
    private static final Template TOSS;
    private static final Template SHOWCASE;
    private static final Template FLICK;

    static {
        // Lifted towards the middle, two spins that wind up and wind down, a little overshoot, home.
        double[] t1 = {0, 0.12, 0.22, 0.42, 0.62, 0.74, 0.84, 1};
        TWIRL = new Template(
                k(t1, 0, -0.10, -0.16, -0.16, -0.16, -0.12, -0.04, 0),
                k(t1, 0, 0.06, 0.11, 0.12, 0.12, 0.08, 0.02, 0),
                k(t1, 0, 0.03, 0.06, 0.06, 0.06, 0.04, 0.01, 0),
                k(t1, 0, -10, -18, -12, -12, -16, 4, 0),
                k(t1, 0, -18, -30, -30, -30, -22, 6, 0),
                k(t1, 0, 0, 40, 330, 690, 742, 716, 720),
                2, 0.22);

        // Swung from side to side, flipping over each time like a balisong.
        double[] t2 = {0, 0.12, 0.26, 0.40, 0.54, 0.68, 0.82, 1};
        BUTTERFLY = new Template(
                k(t2, 0, -0.10, -0.15, -0.15, -0.15, -0.15, -0.08, 0),
                k(t2, 0, 0.07, 0.10, 0.10, 0.10, 0.10, 0.05, 0),
                k(t2, 0, 0.04, 0.05, 0.05, 0.05, 0.05, 0.02, 0),
                k(t2, 0, -12, -8, -14, -8, -14, -6, 0),
                k(t2, 0, -30, 150, -30, 150, -30, -10, 0),
                k(t2, 0, 12, -18, 18, -18, 18, -6, 0),
                1, 0.2);

        // Thrown straight up spinning end over end, caught with a little dip.
        double[] t3 = {0, 0.10, 0.20, 0.42, 0.62, 0.74, 0.86, 1};
        TOSS = new Template(
                k(t3, 0, -0.04, -0.08, -0.10, -0.08, -0.06, -0.02, 0),
                k(t3, 0, -0.04, 0.04, 0.42, 0.06, -0.05, 0.01, 0),
                k(t3, 0, 0, 0.02, 0.05, 0.03, 0.01, 0, 0),
                k(t3, 0, 12, -40, -380, -700, -730, -716, -720),
                k(t3, 0, -10, -20, -20, -20, -12, -2, 0),
                k(t3, 0, 0, 8, 12, 8, 4, 0, 0),
                0, 0.18);

        // Brought to the middle and slowly turned all the way round to show it off.
        double[] t4 = {0, 0.16, 0.30, 0.55, 0.78, 0.90, 1};
        SHOWCASE = new Template(
                k(t4, 0, -0.18, -0.22, -0.22, -0.22, -0.10, 0),
                k(t4, 0, 0.10, 0.14, 0.15, 0.14, 0.06, 0),
                k(t4, 0, 0.08, 0.12, 0.12, 0.12, 0.05, 0),
                k(t4, 0, -20, -24, -18, -24, -8, 0),
                k(t4, 0, -40, -10, 150, 320, 352, 360),
                k(t4, 0, 10, 14, 6, 14, 4, 0),
                -1, 2);

        // A wind-up and two quick flips forward, snapped back to rest.
        double[] t5 = {0, 0.14, 0.24, 0.38, 0.50, 0.64, 0.76, 0.88, 1};
        FLICK = new Template(
                k(t5, 0, -0.04, -0.06, -0.08, -0.08, -0.08, -0.06, -0.02, 0),
                k(t5, 0, 0.02, 0.05, 0.08, 0.08, 0.08, 0.05, 0.01, 0),
                k(t5, 0, 0.02, 0.03, 0.04, 0.04, 0.04, 0.02, 0, 0),
                k(t5, 0, 28, 20, -340, -370, -715, -730, -716, -720),
                k(t5, 0, -12, -16, -18, -18, -18, -12, -2, 0),
                k(t5, 0, 6, 8, 4, 4, 4, 2, 0, 0),
                0, 0.26);
    }

    private long startedAt;
    private boolean playing;
    private boolean keyWasDown;
    private int slot = -1;
    private ItemStack held = ItemStack.EMPTY;
    private boolean swished;
    private Template current = TWIRL;

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

        if (playing && !swished && progress() >= current.swishAt()) {
            swished = true;
            if (sound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.25f));
            }
        }
    }

    private void start() {
        current = pick();
        startedAt = System.nanoTime();
        playing = true;
        swished = false;
    }

    private Template pick() {
        String name = template.get();
        if (name.equals("Random")) {
            Template[] all = {TWIRL, BUTTERFLY, TOSS, SHOWCASE, FLICK};
            Template next;
            do next = all[(int) (Math.random() * all.length)]; while (next == current && all.length > 1);
            return next;
        }
        return switch (name) {
            case "Butterfly" -> BUTTERFLY;
            case "Toss" -> TOSS;
            case "Showcase" -> SHOWCASE;
            case "Flick" -> FLICK;
            default -> TWIRL;
        };
    }

    private boolean isDown(int code) {
        if (code == KeyUtil.NONE) return false;
        long window = mc.getWindow().getHandle();
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(window, code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    /** How far through the inspect it is, 0 to 1. */
    private double progress() {
        return (System.nanoTime() - startedAt) / 1e9 / duration.get();
    }

    /** Moves and turns the item in first person for the current point of the inspect. */
    public static void transform(MatrixStack matrices, boolean leftHand) {
        ItemInspect m = instance;
        if (m == null || !m.isEnabled() || !m.playing) return;
        double t = Math.min(1, m.progress());
        Template a = m.current;
        float side = leftHand ? -1 : 1;
        matrices.translate((float) a.x().at(t) * side, (float) a.y().at(t), (float) a.z().at(t));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) a.ry().at(t) * side));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) a.rx().at(t)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) a.rz().at(t) * side));
    }

    // ---- the swoosh ---------------------------------------------------------------------

    private int[] colors() {
        if (rainbow.get()) {
            float hue = (System.currentTimeMillis() % 2400) / 2400f;
            return new int[]{ColorUtil.hsv(hue, 0.75f, 1f), ColorUtil.hsv((hue + 0.18f) % 1f, 0.8f, 1f)};
        }
        int base = color.get() | 0xFF000000;
        return new int[]{base, ColorUtil.shade(base, -0.25f)};
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!playing || !trail.get() || !inGame() || mc.options.hudHidden || !mc.options.getPerspective().isFirstPerson()) return;
        Template a = current;
        if (a.swooshChannel() < 0) return;
        double t = Math.min(1, progress());

        // How fast the swoosh's channel is turning decides how bright and long the swoosh is.
        double step = 0.01;
        double angle = a.channel(a.swooshChannel(), t);
        double speedNow = Math.abs(angle - a.channel(a.swooshChannel(), Math.max(0, t - step))) / step;
        float strength = (float) Math.min(1, speedNow / 1800);
        if (strength < 0.04f) return;

        boolean left = mc.player.getMainArm() == net.minecraft.util.Arm.LEFT;
        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();
        float cx = left ? w * 0.29f : w * 0.71f;
        float cy = h * 0.72f;
        float radius = h * 0.19f * trailSize.getFloat() / 100f;

        double direction = Math.signum(angle - a.channel(a.swooshChannel(), Math.max(0, t - step)));
        float head = (float) (((angle * (left ? -1 : 1) - 90) % 360 + 360) % 360);
        float sweep = (float) (150 * strength * trailSize.getFloat() / 100f * -direction * (left ? -1 : 1));
        int[] c = colors();
        int tail = ColorUtil.withAlpha(c[1], 0);

        // A wide soft glow, the bright band, and a thin bright edge, all trailing behind the head.
        Render2D.arc(ctx, cx, cy, radius + 6, 16, head + sweep, -sweep, tail, ColorUtil.withAlpha(c[1], Math.round(70 * strength)));
        Render2D.arc(ctx, cx, cy, radius, 7, head + sweep, -sweep, tail, ColorUtil.withAlpha(c[0], Math.round(220 * strength)));
        Render2D.arc(ctx, cx, cy, radius - 1, 1.5f, head + sweep * 0.6f, -sweep * 0.6f, tail,
                ColorUtil.withAlpha(0xFFFFFFFF, Math.round(200 * strength)));
    }

    /** Whether an inspect is playing, for the in-game test. */
    public boolean inspecting() {
        return playing;
    }

    public void inspect() {
        if (inGame() && !mc.player.getMainHandStack().isEmpty()) start();
    }
}
