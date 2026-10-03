package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoTool;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class AutoToolMixin {
    @Inject(method = {"attackBlock", "updateBlockBreakingProgress"}, at = @At("HEAD"))
    private void maro$selectMiningTool(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        AutoTool tool = ModuleManager.get(AutoTool.class);
        // Auto Mine picks its own tool; two pickers could swap the slot mid-break and restart it.
        if (dev.maro.module.impl.player.AutoMine.holdingBreak()) return;
        if (tool != null && tool.isEnabled()) tool.selectTool(pos);
    }

    /**
     * The game cancels the current break every tick attack is not held. While Auto Mine is digging
     * it is "holding attack", so the break carries on instead of being aborted and restarted.
     */
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void maro$keepAutoMineBreak(CallbackInfo ci) {
        if (dev.maro.module.impl.player.AutoMine.holdingBreak()) ci.cancel();
    }
}
