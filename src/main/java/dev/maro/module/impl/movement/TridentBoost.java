package dev.maro.module.impl.movement;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;

/** Adapted from 4ldenz's Anubis Trident Boost (GPL-3.0-only); see THIRD_PARTY_NOTICES.md. */
public final class TridentBoost extends Module {
    private static TridentBoost active;
    private final NumberSetting boost = add(new NumberSetting("Boost",
        "Multiply the velocity added by Riptide", 2.0, 0.1, 10.0, 0.1));
    private final BooleanSetting outOfWater = add(new BooleanSetting("Out Of Water",
        "Allow Riptide charging and launching outside water or rain", true));

    public TridentBoost() {
        super("Trident Boost", "Boosts you when using Riptide with a trident", Category.MOVEMENT);
    }

    @Override protected void onEnable() { active = this; }
    @Override protected void onDisable() { if (active == this) active = null; }

    private static boolean local(PlayerEntity player) {
        return active != null && mc.player != null && player == mc.player;
    }

    public static double riptideMultiplier(PlayerEntity player) {
        return local(player) ? active.boost.get() : 1.0;
    }

    public static boolean allowOutOfWater(PlayerEntity player) {
        return local(player) && active.outOfWater.get();
    }
}
