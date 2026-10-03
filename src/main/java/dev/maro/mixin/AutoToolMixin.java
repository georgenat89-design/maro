package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoTool;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class AutoToolMixin {
    @Inject(method = {"attackBlock", "updateBlockBreakingProgress"}, at = @At("HEAD"))
    private void maro$selectMiningTool(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        AutoTool tool = ModuleManager.get(AutoTool.class);
        if (tool != null && tool.isEnabled()) tool.selectTool(pos);
    }
}
