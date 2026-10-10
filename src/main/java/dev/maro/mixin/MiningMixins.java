package dev.maro.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.maro.module.impl.player.BreakDelay;
import dev.maro.module.impl.player.SpeedMine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
public final class MiningMixins {
    @Mixin(ClientPlayerEntity.class)
    public static abstract class Tick {
        @Inject(method = "tick", at = @At("HEAD"))
        private void maro$speedMine(CallbackInfo info) { SpeedMine.beforeTick(); }
    }
    @Mixin(PlayerEntity.class)
    public static abstract class Speed {
        @ModifyReturnValue(method = "getBlockBreakingSpeed", at = @At("RETURN"))
        private float maro$miningSpeed(float original, BlockState state) {
            return (Object) this == MinecraftClient.getInstance().player ? SpeedMine.breakingSpeed(original, state) : original;
        }
    }
    @Mixin(value = ClientPlayerInteractionManager.class, priority = 900)
    public static abstract class Interaction {
        @ModifyConstant(method = {"attackBlock", "updateBlockBreakingProgress"}, constant = @Constant(intValue = 5))
        private int maro$breakDelay(int vanilla) {
            if (dev.maro.module.impl.player.AutoBuilder.holdingBreak() || dev.maro.module.impl.player.AutoMine.holdingBreak()) return vanilla;
            return BreakDelay.cooldown(vanilla);
        }
        @ModifyExpressionValue(method = "method_41930", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F"))
        private float maro$noInstant(float original) {
            if (original < 1 || !BreakDelay.preventInstant() || dev.maro.module.impl.player.AutoBuilder.holdingBreak()) return original;
            ((MiningAccessor) this).maro$cooldown(BreakDelay.cooldown(5)); return 0;
        }
        @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
        private void maro$instant(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> info) {
            var mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.player.isSpectator() || mc.player.isCreative()
                || !mc.world.getWorldBorder().contains(pos) || mc.player.isBlockBreakingRestricted(mc.world, pos, mc.interactionManager.getCurrentGameMode())) return;
            var state = mc.world.getBlockState(pos);
            if (!SpeedMine.instamine(state) || state.calcBlockBreakingDelta(mc.player, mc.world, pos) <= 0.5) return;
            mc.interactionManager.breakBlock(pos);
            var im = (MiningAccessor) this;
            im.maro$sequenced(mc.world, sequence -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face, sequence));
            im.maro$sequenced(mc.world, sequence -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face, sequence));
            info.setReturnValue(true);
        }
    }
    @Mixin(Mouse.class)
    public static abstract class Clicks {
        @Inject(method = "onMouseButton", at = @At("HEAD"))
        private void maro$breakClick(long window, MouseInput input, int action, CallbackInfo info) {
            var mc = MinecraftClient.getInstance();
            if (window == mc.getWindow().getHandle() && mc.currentScreen == null) BreakDelay.clicked(input.button(), action);
        }
    }
    @Mixin(net.minecraft.network.ClientConnection.class)
    public static abstract class Packets {
        @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
        private void maro$extraAbort(net.minecraft.network.packet.Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush, CallbackInfo info) {
            var mc = MinecraftClient.getInstance();
            if (!SpeedMine.extraAbort() || !mc.isOnThread() || mc.getNetworkHandler() == null
                || (Object) this != mc.getNetworkHandler().getConnection() || dev.maro.module.impl.player.AutoBuilder.holdingBreak()) return;
            if (packet instanceof PlayerActionC2SPacket action && action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, action.getPos().up(), action.getDirection()));
        }
    }
}
