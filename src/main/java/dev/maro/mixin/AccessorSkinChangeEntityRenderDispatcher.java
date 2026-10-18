// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(value=EnvType.CLIENT)
@Mixin(value={EntityRenderManager.class})
public interface AccessorSkinChangeEntityRenderDispatcher {
    @Accessor(value="playerRenderers")
    public Map<PlayerSkinType, PlayerEntityRenderer<AbstractClientPlayerEntity>> anubis$skinChangePlayerRenderers();
}
