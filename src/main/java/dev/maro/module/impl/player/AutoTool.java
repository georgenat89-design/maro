package dev.maro.module.impl.player;

import dev.maro.mixin.ClientPlayerInteractionManagerAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class AutoTool extends Module {
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back",
        "Return to your previous slot when you stop mining", true));
    private final BooleanSetting protectTools = add(new BooleanSetting("Protect Tools",
        "Skip tools with only one durability point remaining", true));

    private ClientPlayerEntity miningPlayer;
    private int originalSlot = -1, toolSlot = -1;

    public AutoTool() {
        super("Auto Tool", "Select the best hotbar tool for the block you mine", Category.PLAYER);
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { restore(); }

    @Override public void onTick() {
        if (!inGame() || mc.player != miningPlayer) { clear(); return; }
        if (mc.currentScreen != null || !mc.options.attackKey.isPressed() || mc.player.isUsingItem()
            || mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) restore();
    }

    /** Called before vanilla evaluates mining progress and sends the digging packet. */
    public void selectTool(BlockPos pos) {
        if (!isEnabled() || !inGame() || mc.interactionManager == null || mc.currentScreen != null
            || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.isUsingItem()
            || mc.player.getAbilities().creativeMode) return;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getHardness(mc.world, pos) < 0) return;
        if (miningPlayer != mc.player) clear();

        var inventory = mc.player.getInventory();
        int current = inventory.getSelectedSlot();
        // Remember a manual slot change as the new return slot.
        if (originalSlot >= 0 && current != toolSlot) { originalSlot = -1; toolSlot = -1; }
        RegistryEntry<Enchantment> efficiency = mc.world.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        int best = current;
        double bestSpeed = miningSpeed(inventory.getStack(current), state, efficiency);
        boolean bestHarvests = harvests(inventory.getStack(current), state) && bestSpeed >= 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            double speed = miningSpeed(stack, state, efficiency);
            if (speed < 0) continue;
            boolean harvests = harvests(stack, state);
            if ((harvests && !bestHarvests) || (harvests == bestHarvests && speed > bestSpeed)) {
                best = slot; bestSpeed = speed; bestHarvests = harvests;
            }
        }
        // Equal tools keep the current slot, so the hotbar does not flicker.
        if (best == current) return;
        if (originalSlot < 0) originalSlot = current;
        miningPlayer = mc.player;
        toolSlot = best;
        inventory.setSelectedSlot(best);
        syncSlot();
    }

    private boolean harvests(ItemStack stack, BlockState state) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }

    private double miningSpeed(ItemStack stack, BlockState state, RegistryEntry<Enchantment> efficiency) {
        if (protectTools.get() && stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) return -1;
        double speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1) {
            int level = EnchantmentHelper.getLevel(efficiency, stack);
            if (level > 0) speed += level * level + 1;
        }
        return speed;
    }

    private void restore() {
        if (switchBack.get() && originalSlot >= 0 && mc.player == miningPlayer && mc.interactionManager != null
            && mc.player.getInventory().getSelectedSlot() == toolSlot) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            syncSlot();
        }
        clear();
    }

    private void syncSlot() {
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).maro$syncSelectedSlot();
    }

    private void clear() { miningPlayer = null; originalSlot = -1; toolSlot = -1; }
}
