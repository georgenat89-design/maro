package dev.maro.module.impl.player;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.EnchantmentHelper;

/** Anubis Trident Util, with a native-server timing option and bounded correction recovery. */
public final class TridentUtil extends Module {
    private static volatile TridentUtil active;
    private final BooleanSetting serverTiming = add(new BooleanSetting("Server Timing",
        "Keep the native charge time and water check to reduce rejected Riptide uses", true));
    private final NumberSetting chargeScale = add(new NumberSetting("Charge Scale",
        "Charge duration multiplier when Server Timing is off", 0, 0, 1, 0.1).visible(() -> !serverTiming.get()));
    private final BooleanSetting noWater = add(new BooleanSetting("No Water",
        "Allow dry Riptide on servers that accept it; requires Server Timing off", true).visible(() -> !serverTiming.get()));
    private Object player, world;
    private int nextUseAge, lastUseAge = -100;

    public TridentUtil() {
        super("Trident Util", "Faster trident charging with server timing and correction recovery", Category.PLAYER);
    }

    @Override protected void onEnable() { active = this; reset(); }
    @Override protected void onDisable() { if (active == this) active = null; reset(); }
    private void reset() { player = mc.player; world = mc.world; nextUseAge = 0; lastUseAge = -100; }
    private boolean current() {
        if (!inGame()) return false;
        if (player != mc.player || world != mc.world) reset();
        return mc.player.isAlive() && !mc.player.isSpectator();
    }
    @Override public void onTick() { current(); }

    public static boolean allowOutOfWater(PlayerEntity user) {
        var m = active;
        return m != null && user == mc.player && m.current() && !m.serverTiming.get() && m.noWater.get();
    }
    public static int minChargeTicks(int vanilla, PlayerEntity user) {
        var m = active;
        return m == null || user != mc.player || !m.current() || m.serverTiming.get()
            ? vanilla : Math.max(1, (int) Math.round(vanilla * m.chargeScale.get()));
    }
    public static boolean riptide(ItemStack stack) {
        return mc.player != null && stack.isOf(Items.TRIDENT)
            && EnchantmentHelper.getTridentSpinAttackStrength(stack, mc.player) > 0;
    }
    public static boolean eligible(ItemStack stack) {
        return mc.player != null && stack.isOf(Items.TRIDENT) && !stack.willBreakNextUse()
            && !mc.player.getItemCooldownManager().isCoolingDown(stack)
            && (!riptide(stack) || mc.player.isTouchingWaterOrRain() || allowOutOfWater(mc.player));
    }
    public static boolean ready() {
        var m = active;
        return m == null || !m.current() || mc.player.age >= m.nextUseAge;
    }
    public static boolean handlesUse() { return active != null && active.current(); }
    public static void attempted() {
        var m = active;
        if (m != null && m.current()) { m.lastUseAge = mc.player.age; m.nextUseAge = mc.player.age + 2; }
    }
    public static void released() {
        var m = active;
        if (m != null && m.current()) { m.lastUseAge = mc.player.age; m.nextUseAge = mc.player.age + 2; }
    }
    public static void serverCorrection() {
        var m = active;
        if (m == null || !m.current() || mc.player.age - m.lastUseAge > 40) return;
        // Accept the server position and wait, rather than immediately repeating a rejected burst.
        m.nextUseAge = mc.player.age + 20;
    }
}
