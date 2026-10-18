// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ItemRenderState.LayerRenderState.class})
public interface AccessorHandItemLayer {
    @Accessor("specialModelType")
    net.minecraft.client.render.item.model.special.SpecialModelRenderer<?> maro$specialModel();
    @Accessor(value="transform")
    public Transformation anubis$handTransform();

    @Accessor(value="vertices")
    public Supplier<Vector3fc[]> anubis$handExtents();
}
