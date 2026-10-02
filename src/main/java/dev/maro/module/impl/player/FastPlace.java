package dev.maro.module.impl.player;

import dev.maro.mixin.MinecraftClientAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.item.BlockItem;

/**
 * Shortens the delay between placements. Vanilla waits 4 ticks after every right-click use;
 * this caps that cooldown to the chosen delay (0 = a placement every tick, 20 per second).
 */
public class FastPlace extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between placements (vanilla is 4)", 0, 0, 4, 1).suffix(" ticks"));
    private final BooleanSetting onlyBlocks = add(new BooleanSetting("Only Blocks", "Only speed up placing blocks, not other items", true));

    public FastPlace() {
        super("Fast Place", "Place blocks faster by shortening the right-click delay", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (onlyBlocks.get() && !holdingBlock()) return;
        MinecraftClientAccessor client = (MinecraftClientAccessor) mc;
        int max = delay.getInt();
        if (client.maro$getItemUseCooldown() > max) client.maro$setItemUseCooldown(max);
    }

    private boolean holdingBlock() {
        return mc.player.getMainHandStack().getItem() instanceof BlockItem
                || mc.player.getOffHandStack().getItem() instanceof BlockItem;
    }
}
