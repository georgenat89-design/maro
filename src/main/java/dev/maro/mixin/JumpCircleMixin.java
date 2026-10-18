package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.JumpCircle;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anubis-style local takeoff trigger, adapted to Maro's module registry. */
@Mixin(LivingEntity.class)
public abstract class JumpCircleMixin {
    @Inject(method="jump",at=@At("TAIL"))
    private void maro$jumpCircle(CallbackInfo ci) {
        var module=ModuleManager.get(JumpCircle.class);
        if(module!=null && module.isEnabled())module.onJump((LivingEntity)(Object)this);
    }
}
