/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * marked All-Rights-Reserved. Ported from Meteor 26.2 to 1.21.11.
 */
package dev.maro.nathan.mixin;

import dev.maro.nathan.modules.Highlight;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The other half: the outline colour. getTeamColor is what EntityRenderer feeds into
 * outlineColor, and overriding it only changes what this client draws.
 */
@Mixin(Entity.class)
public abstract class GlowColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void nameeprotect$color(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (Highlight.has(self.getId())) cir.setReturnValue(Highlight.color);
    }
}
