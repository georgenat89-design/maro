/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * marked All-Rights-Reserved. Ported from Meteor 26.2 to 1.21.11.
 */
package dev.maro.nathan.mixin;

import dev.maro.nathan.modules.Highlight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Half of the client-side glow: make the game think the entity is glowing.
 *
 * EntityRenderer.extractRenderState sets outlineColor to ARGB.opaque(entity.getTeamColor()) when
 * this returns true, and EntityRenderState.appearsGlowing() is just "outlineColor != 0", so this
 * one return value is what puts an entity into the outline pass.
 */
@Mixin(MinecraftClient.class)
public abstract class GlowMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void nameeprotect$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (Highlight.has(entity.getId())) cir.setReturnValue(true);
    }
}
