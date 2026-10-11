package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.maro.module.impl.misc.SkinChanger;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Change the skin before renderer/model selection, including first-person hands. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkinChangerMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private SkinTextures maro$wearSkin(SkinTextures original) {
        return SkinChanger.skinFor(((AbstractClientPlayerEntity) (Object) this).getUuid(), original);
    }
}
