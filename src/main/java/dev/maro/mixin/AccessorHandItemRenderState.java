// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ItemRenderState.class})
public interface AccessorHandItemRenderState {
    @Accessor("displayContext")
    void maro$setDisplayContext(net.minecraft.item.ItemDisplayContext context);
    @Accessor(value="layers")
    public ItemRenderState.LayerRenderState[] anubis$handLayers();

    @Accessor(value="layerCount")
    public int anubis$handLayerCount();
}
