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

    /** Skinned characters: the layer each material draws in, and its see-through version. */
    final RenderLayer[] layers, ghosts;
    /** Per material: whether it gets an ink outline (opaque surfaces only). */
    final boolean[] outlined;

    WornCharacter(CharacterModel model, Identifier texture) {
        this.model = model;
        this.solid = RenderLayers.entitySolid((Identifier)texture);
        this.decal = RenderLayers.entityTranslucent((Identifier)texture);
        this.ghost = RenderLayers.itemEntityTranslucentCull((Identifier)texture);
        this.layers = this.ghosts = null;
        this.outlined = null;
        this.projected = false;
    }

    /**
     * A skinned character with one texture per image: opaque materials draw solid (or without
     * culling when double-sided), cut-out ones (hair, lashes) with alpha testing, blended ones
     * translucent. Untextured materials draw over a white texture.
     */
    WornCharacter(CharacterModel model, Identifier[] textures, Identifier white) {
        this.model = model;
        dev.maro.render.character.skin.SkinMaterial[] materials = model.skinned.materials;
        this.layers = new RenderLayer[materials.length];
        this.ghosts = new RenderLayer[materials.length];
        this.outlined = new boolean[materials.length];
        for (int m = 0; m < materials.length; m++) {
            var mat = materials[m];
            Identifier t = mat.image >= 0 && mat.image < textures.length ? textures[mat.image] : white;
            this.layers[m] = switch (mat.mode) {
                case dev.maro.render.character.skin.SkinMaterial.BLEND -> RenderLayers.entityTranslucent(t);
                case dev.maro.render.character.skin.SkinMaterial.CUTOUT -> mat.doubleSided ? RenderLayers.entityCutoutNoCull(t) : RenderLayers.entityCutout(t);
                default -> mat.doubleSided ? RenderLayers.entityCutoutNoCull(t) : RenderLayers.entitySolid(t);
            };
            this.ghosts[m] = RenderLayers.itemEntityTranslucentCull(t);
            this.outlined[m] = mat.mode == dev.maro.render.character.skin.SkinMaterial.OPAQUE && !mat.doubleSided;
        }
        this.solid = this.layers.length > 0 ? this.layers[0] : RenderLayers.entitySolid(white);
        this.decal = RenderLayers.entityTranslucent(white);
        this.ghost = RenderLayers.itemEntityTranslucentCull(white);
        this.projected = false;
    }

    /** The bendable default skin: the game's armour, cape and items stay drawn, on the folded pose. */
    public final boolean projected;

    private WornCharacter(CharacterModel model, Identifier skin, boolean projected) {
        this.model = model;
        int mats = model.skinned.materials.length;
        this.layers = new RenderLayer[mats];
        this.ghosts = new RenderLayer[mats];
        this.outlined = new boolean[mats];
        for (int m = 0; m < mats; m++) {
            this.layers[m] = RenderLayers.entityTranslucent(skin);
            this.ghosts[m] = RenderLayers.itemEntityTranslucentCull(skin);
        }
        this.solid = this.layers[0];
        this.decal = this.layers[0];
        this.ghost = this.ghosts[0];
        this.projected = projected;
    }

    /** A player's own skin on the bendable body. */
    static WornCharacter skin(CharacterModel model, Identifier skin) {
        return new WornCharacter(model, skin, true);
    }

    public boolean skinned() {
        return model.skinned != null;
    }
}
