package dev.maro.mixin;

import dev.maro.render.esp.EquipmentTextures;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes which texture each armour, elytra and trim render layer draws, for the Player ESP silhouette. */
@Mixin(RenderLayers.class)
public abstract class EquipmentLayersMixin {
    @Inject(method = {"armorCutoutNoCull", "armorDecalCutoutNoCull", "armorTranslucent"}, at = @At("RETURN"))
    private static void maro$rememberTexture(Identifier texture, CallbackInfoReturnable<RenderLayer> cir) {
        EquipmentTextures.remember(cir.getReturnValue(), texture);
    }
}
