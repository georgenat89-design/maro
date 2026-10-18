// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import dev.maro.render.character.kit.CharacterModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

@Environment(value=EnvType.CLIENT)
public final class WornCharacter {
    public final CharacterModel model;
    final RenderLayer solid;
    final RenderLayer decal;
    final RenderLayer ghost;

    WornCharacter(CharacterModel model, Identifier texture) {
        this.model = model;
        this.solid = RenderLayers.entitySolid((Identifier)texture);
        this.decal = RenderLayers.entityTranslucent((Identifier)texture);
        this.ghost = RenderLayers.itemEntityTranslucentCull((Identifier)texture);
    }
}
