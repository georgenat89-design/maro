package dev.maro.module.impl.player;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;

/** Repeats ordinary trident use/release while the use button is held. */
public final class AutoTrident extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed",
        "Higher is faster: 10 charges for 10 ticks; 1 charges for 28 ticks", 10, 1, 10, 1));

    public AutoTrident() {
        super("Auto Trident", "Hold right-click to repeatedly charge and release your trident", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.interactionManager == null || mc.currentScreen != null
            || !mc.player.isAlive() || mc.player.isSpectator() || !mc.options.useKey.isPressed()) return;

        if (mc.player.isUsingItem()) {
            // Leave other item uses alone, including food, shields and bows.
            if (mc.player.getActiveItem().isOf(Items.TRIDENT)
                && mc.player.getItemUseTime() >= TridentItem.MIN_DRAW_DURATION + (10 - speed.getInt()) * 2) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
            return;
        }

        Hand hand = mc.player.getMainHandStack().isOf(Items.TRIDENT) ? Hand.MAIN_HAND
            : mc.player.getOffHandStack().isOf(Items.TRIDENT) ? Hand.OFF_HAND : null;
        if (hand == null || mc.player.getItemCooldownManager().isCoolingDown(mc.player.getStackInHand(hand))) return;
        // Vanilla validates durability and the water/rain requirement for Riptide.
        mc.interactionManager.interactItem(mc.player, hand);
    }
}
