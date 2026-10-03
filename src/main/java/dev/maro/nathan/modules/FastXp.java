package dev.maro.nathan.modules;

import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.player.FindItemResult;
import dev.maro.runtime.utils.player.InvUtils;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import dev.maro.nathan.NameeProtectAddon;

/**
 * Throws experience bottles for you, on a clock you set in milliseconds.
 *
 * <p>Only while the use key is held. The module does not throw on its own - it
 * makes your own right-click faster, rather than deciding when to throw for
 * you. Let go and it stops mid-stack.
 *
 * <p>The throw is the same call the game makes when you right-click: the item
 * is used from whichever hand is holding it and the arm swings. Nothing is
 * fabricated and no packet is hand-built, so a bottle leaves your hand exactly
 * as it would if you were clicking.
 */
public class FastXp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Milliseconds between throws while you hold right-click. 0 is as fast as Throws Per Tick allows.")
        .defaultValue(0)
        .range(0, 500)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> perTick = sgGeneral.add(new IntSetting.Builder()
        .name("throws-per-tick")
        .description("How many bottles may go out in a single tick. A bottle has no cooldown, so the game happily throws several at once.")
        .defaultValue(4)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder()
        .name("swap")
        .description("Switch to a bottle in your hotbar when you are not already holding one.")
        .defaultValue(true)
        .build()
    );

    private long lastThrowAt;

    public FastXp() {
        super(NameeProtectAddon.CATEGORY, "fast-xp", "Throws experience bottles as fast as you set, while you hold right-click.");
    }

    @Override
    public void onActivate() {
        // So the first throw is immediate rather than however long it has been
        // since the module was last on.
        lastThrowAt = 0;
    }

    /**
     * Shown next to the module in the list: how many bottles are in the hand or
     * hotbar slot it is throwing from, so you can see it running out.
     */
    @Override
    public String getInfoString() {
        FindItemResult bottles = bottles();
        return bottles.found() ? bottles.count() + " left" : "none";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Held, not automatic. The key is also down for a right-click on a
        // block or a mob, but a bottle in your hand is thrown by either, so
        // there is nothing here to tell apart.
        if (!mc.options.useKey.isPressed()) {
            // So letting go and pressing again throws at once, rather than
            // waiting out a delay that elapsed while your finger was off.
            lastThrowAt = 0;
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(delay.get(), 1);
        long waited = now - lastThrowAt;

        if (waited < interval) return;

        // A tick is 50 ms, so any delay shorter than that leaves more than one
        // throw owed by the time this runs again. An experience bottle has no
        // cooldown of its own, and the server acts on every use packet that
        // reached it during the tick - so those owed throws are real bottles,
        // not packets thrown away.
        int owed = (int) Math.min(waited / interval, perTick.get());

        for (int i = 0; i < owed; i++) {
            if (!throwOne()) break;
        }

        lastThrowAt = now;
    }

    /** @return whether a bottle was actually thrown. */
    private boolean throwOne() {
        Hand hand = handHoldingABottle();

        if (hand == null) {
            if (!swap.get()) return false;

            FindItemResult bottle = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
            if (!bottle.found()) return false;

            // Held rather than swapped back after each throw. Swapping back
            // would mean two held-item packets for every bottle, twenty times a
            // second, to return to a slot this module is about to leave again.
            InvUtils.swap(bottle.slot(), false);

            hand = handHoldingABottle();
            if (hand == null) return false;
        }

        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);

        return true;
    }

    private Hand handHoldingABottle() {
        if (InvUtils.testInMainHand(Items.EXPERIENCE_BOTTLE)) return Hand.MAIN_HAND;
        if (InvUtils.testInOffHand(Items.EXPERIENCE_BOTTLE)) return Hand.OFF_HAND;

        return null;
    }

    private FindItemResult bottles() {
        Hand hand = handHoldingABottle();

        if (hand == Hand.MAIN_HAND || hand == Hand.OFF_HAND) {
            return InvUtils.find(Items.EXPERIENCE_BOTTLE);
        }

        return InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
    }
}
