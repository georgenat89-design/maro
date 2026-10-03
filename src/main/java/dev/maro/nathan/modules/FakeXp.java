package dev.maro.nathan.modules;

import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.misc.Keybind;

import dev.maro.runtime.event.EventHandler;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Fake XP: what the experience bar and level number say, and nothing else.
 *
 * <p><b>Where it acts.</b> At the two places the HUD reads your experience, and
 * only while it is drawing:
 *
 * <pre>
 * ExperienceBarRenderer.renderBackground   experienceProgress * 183 = bar fill
 * Gui.renderHotbarAndDecorations           experienceLevel = the number
 * </pre>
 *
 * <p>Neither value is stored anywhere. The player's real
 * {@code experienceLevel}, {@code experienceProgress} and {@code totalExperience}
 * are never assigned to, so nothing downstream of them can notice: enchanting
 * costs, what an anvil will let you do, and how many levels you actually have are
 * all read from the real fields somewhere else entirely. No packet is involved -
 * this is a number being drawn differently.
 *
 * <p><b>Stable against real updates.</b> Because the substitution happens at draw
 * time rather than by writing to the player, an incoming XP change simply has no
 * route to the display. Picking up an orb, dying, respawning or changing world
 * moves the real values; the HUD keeps showing what is set here, without needing
 * anything to be re-applied or reset.
 *
 * <p><b>Turning it off.</b> Both hooks return their argument untouched when the
 * module is inactive, so the real display is not restored so much as never
 * replaced - there is no state to unwind.
 */
public class FakeXp extends Module {
    /** The bar sprite is 182 wide and vanilla scales progress by 183. */
    private static final float FULL_BAR = 1.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /**
     * The whole int range, as asked.
     *
     * <p>The slider covers 0 to 1000, which is where anyone actually lives, while
     * the box beside it takes any value up to {@link Integer#MAX_VALUE}. Meteor
     * parses that box as an int and refuses anything that is not one, so a value
     * that would overflow never reaches the setting - it is rejected at the edit
     * rather than wrapping round to a negative level.
     */
    private final Setting<Integer> level = sgGeneral.add(new IntSetting.Builder()
        .name("level")
        .description("The level to display. The slider covers 0 to 1000; type into the box for anything larger, up to 2147483647.")
        .defaultValue(100)
        .min(0)
        .max(Integer.MAX_VALUE)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> barProgress = sgGeneral.add(new IntSetting.Builder()
        .name("bar-progress")
        .description("How full the bar looks, as a percentage.")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> animate = sgGeneral.add(new BoolSetting.Builder()
        .name("animate-bar")
        .description("Fills the bar smoothly over and over instead of holding it still.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("Fills a second. 1 is one full sweep of the bar every second.")
        .defaultValue(0.5)
        .min(0.05)
        .max(10)
        .sliderRange(0.05, 5)
        .visible(animate::get)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("Turns the module on and off.")
        .defaultValue(Keybind.none())
        .build()
    );

    private boolean keyWasDown;

    /**
     * When the sweep started.
     *
     * <p>{@code System.nanoTime()} counts from an arbitrary origin that can be
     * enormous on a machine that has been up a while. Measuring from here keeps
     * the number small, so the fraction stays precise, and has the nicer property
     * that the bar starts empty when the module is switched on instead of
     * wherever the clock happened to be.
     */
    private long animationStart = System.nanoTime();

    public FakeXp() {
        super(NameeProtectAddon.CATEGORY, "fake-xp",
            "Changes the XP level and bar on your HUD. Display only - your real XP, enchanting costs and the server are untouched.");
    }

    @Override
    public void onActivate() {
        animationStart = System.nanoTime();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean down = mc.currentScreen == null && keybind.get().isPressed();

        if (down && !keyWasDown) toggle();

        keyWasDown = down;
    }

    private static FakeXp active() {
        Modules modules = Modules.get();
        if (modules == null) return null;

        FakeXp module = modules.get(FakeXp.class);

        return module != null && module.isActive() ? module : null;
    }

    /**
     * The level to draw, or the real one when this is off.
     *
     * <p>Zero is passed through as zero on purpose: vanilla draws no number at
     * level 0, and a fake level of 0 should look like that rather than like a
     * drawn "0".
     */
    public static int levelFor(int real) {
        FakeXp module = active();

        return module == null ? real : module.level.get();
    }

    /**
     * How full to draw the bar, 0 to 1, or the real value when this is off.
     *
     * <p>The animation runs on the wall clock rather than on ticks, so it sweeps
     * at the same rate whatever the frame rate or the server's tick rate, and
     * carries on while the game is paused in a menu.
     */
    public static float progressFor(float real) {
        FakeXp module = active();
        if (module == null) return real;

        if (!module.animate.get()) return module.barProgress.get() / 100.0f;

        // Modulo on the seconds, not on a counter that is added to - a counter
        // would drift and, after long enough, lose precision as a float.
        double sweeps = (System.nanoTime() - module.animationStart) / 1.0e9 * module.animationSpeed.get();

        return (float) (sweeps - Math.floor(sweeps)) * FULL_BAR;
    }
}
