package dev.maro.nathan.modules;

import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.misc.Keybind;

import dev.maro.runtime.event.EventHandler;
import dev.maro.nathan.NameeProtectAddon;

/**
 * Swing Speed: how long the arm takes to swing, and nothing else.
 *
 * <p><b>What it changes.</b> One number - the value
 * {@code LivingEntity.getCurrentSwingDuration()} returns, for your own player
 * only. That method is private and is read from exactly two places in the whole
 * class, both of them animation:
 *
 * <pre>
 * swing(hand, updateSelf)   whether a new swing may restart the current one
 * updateSwingTime()         how far along the swing is, as attackAnim
 * </pre>
 *
 * <p><b>What it therefore cannot change.</b> Attack cooldown lives in
 * {@code attackStrengthTicker} and is never read from here. Mining speed is
 * {@code destroyProgress} on the interaction manager. Damage is worked out
 * server side from the cooldown, which this does not touch. And the swing packet
 * is not gated on any of it: {@code LocalPlayer.swing} calls super and then sends
 * {@code ServerboundSwingPacket} unconditionally - there is no branch between the
 * two - so a swing is sent when you attack, at exactly the same moment, however
 * long the animation happens to run for.
 *
 * <p><b>Eating, drinking, blocking, bows.</b> Those are not swings. They are
 * driven by {@code getUseItemRemainingTicks} and the use-item animation, which
 * shares nothing with the swing clock, so they run at their normal speed whatever
 * this is set to.
 *
 * <p><b>Your player only.</b> The hook checks the entity is
 * {@code Minecraft.getInstance().player} before doing anything. In single player
 * the server's copy of you is a different object, so the integrated server's idea
 * of a swing is left exactly as it was. Other players' arms are untouched too.
 *
 * <p><b>No stuck arm.</b> Raising the speed mid-swing shortens the duration under
 * a swing already in progress, which can leave {@code swingTime} past the end.
 * {@code updateSwingTime} tests {@code swingTime >= duration} and, when it is,
 * zeroes both the timer and the swinging flag - so the next tick ends the swing
 * cleanly rather than stranding the arm. The duration is also floored at one
 * tick, so it can never reach zero and divide by it.
 *
 * <p>Client-side visual only.
 */
public class SwingSpeed extends Module {
    /** Which arm the change applies to. */
    public enum Hand {
        MainHand,
        Offhand,
        Both
    }

    /** A swing can never be shorter than this, in ticks. */
    public static final int MIN_DURATION = 1;

    /** Maximum acceleration and maximum slowdown factor. */
    public static final double MAX_SPEED = 3.5;

    private static final int MIN_LEVEL = -10;
    private static final int NORMAL_LEVEL = 1;
    private static final int MAX_LEVEL = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> level = sgGeneral.add(new IntSetting.Builder()
        .name("swing-speed")
        .description("Negative strength slows the swing: -10 takes 3.5x as long. 0 and 1 are normal; 2 to 10 speed it up, up to 3.5x.")
        .defaultValue(NORMAL_LEVEL)
        .min(MIN_LEVEL)
        .max(MAX_LEVEL)
        .sliderRange(MIN_LEVEL, MAX_LEVEL)
        .build()
    );

    private final Setting<Hand> hand = sgGeneral.add(new EnumSetting.Builder<Hand>()
        .name("hand")
        .description("Which arm this applies to. The other one keeps its normal speed.")
        .defaultValue(Hand.Both)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("Turns the module on and off.")
        .defaultValue(Keybind.none())
        .build()
    );

    private boolean keyWasDown;

    public SwingSpeed() {
        super(NameeProtectAddon.CATEGORY, "swing-speed",
            "Changes how fast your arm swings. Animation only - not attack cooldown, damage, mining speed or packet timing.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean down = mc.currentScreen == null && keybind.get().isPressed();

        if (down && !keyWasDown) toggle();

        keyWasDown = down;
    }

    /** Negative levels lengthen swings; existing positive levels keep their speeds. */
    public static double speedOf(int level) {
        int strength = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        if (strength < 0) return 1 / (1 + -strength * (MAX_SPEED - 1) / -MIN_LEVEL);
        if (strength <= NORMAL_LEVEL) return 1;
        return 1 + (strength - NORMAL_LEVEL) * (MAX_SPEED - 1) / (MAX_LEVEL - NORMAL_LEVEL);
    }

    /**
     * How much to divide the swing duration by, for the arm now swinging.
     *
     * <p>Returns 1 - meaning "leave it exactly as the game had it" - whenever the
     * module is off, the arm is not one this applies to, or the speed is at its
     * default. Turning the module off therefore restores the vanilla animation on
     * the very next tick, with nothing to undo.
     *
     * @param arm the swinging arm, which is null before the first swing of a life
     */
    public static double scaleFor(net.minecraft.util.Hand arm) {
        Modules modules = Modules.get();
        if (modules == null) return 1;

        SwingSpeed module = modules.get(SwingSpeed.class);
        if (module == null || !module.isActive()) return 1;

        // Before anything has swung this is null; treat it as the main hand, which
        // is what the game is about to use anyway.
        net.minecraft.util.Hand which = arm == null ? net.minecraft.util.Hand.MAIN_HAND : arm;

        boolean applies = switch (module.hand.get()) {
            case MainHand -> which == net.minecraft.util.Hand.MAIN_HAND;
            case Offhand -> which == net.minecraft.util.Hand.OFF_HAND;
            case Both -> true;
        };

        return applies ? speedOf(module.level.get()) : 1;
    }
}
