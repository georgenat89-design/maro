// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import dev.maro.render.character.CharacterLighting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DiffuseLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={DiffuseLighting.class})
public abstract class CustomModelLighting {
    @Inject(method={"setShaderLights"}, at={@At(value="HEAD")})
    private void maro$trackCharacterLighting(DiffuseLighting.Type entry, CallbackInfo ci) {
        CharacterLighting.setup((DiffuseLighting.Type)entry);
    }
}
