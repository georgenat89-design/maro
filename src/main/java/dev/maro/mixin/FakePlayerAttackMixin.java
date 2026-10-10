package dev.maro.mixin;

import dev.maro.module.impl.player.FakePlayer;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitting the Fake Player: it takes the hit here, and nothing goes to the server about an entity it does not know. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class FakePlayerAttackMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void maro$fakePlayer(PlayerEntity player, Entity target, CallbackInfo ci) {
        FakePlayer module = FakePlayer.get();
        if (module == null || !module.isFake(target)) return;
        module.hit(player);
        player.resetTicksSince();
        ci.cancel();
    }
}
