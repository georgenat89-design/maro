// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class CharacterModel {
    public final String id;
    public final String name;
    public final Detail detail;
    public final float scale;
    public final PartMesh[] parts;
    public final int motionSlots;
    public final int atlasWidth;
    public final int atlasHeight;
    public final int[][] atlasMips;
    public final float whiteU;
    public final float whiteV;
    public final float minY;
    public final float maxY;
    public final float hipY;
    public final boolean firstPersonArms;
    public final long buildMillis;
    public final int weldedSeams;
    /** The skinned character (skeleton, weights, levels, materials), or null for a rigid-part one. */
    public final dev.maro.render.character.skin.SkinnedCharacter skinned;
    /** Its textures, decoded and waiting for upload (null once uploaded). */
    public volatile net.minecraft.client.texture.NativeImage[][] pendingImages;

    CharacterModel(String id, String name, Detail detail, float scale, PartMesh[] parts, int motionSlots, int atlasWidth, int atlasHeight, int[][] atlasMips, float whiteU, float whiteV, float minY, float maxY, float hipY, boolean firstPersonArms, long buildMillis) {
        this.id = id;
        this.name = name;
        this.detail = detail;
        this.scale = scale;
        this.parts = CharacterAttachments.repair(parts);
        this.motionSlots = motionSlots;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.atlasMips = atlasMips;
        this.whiteU = whiteU;
        this.whiteV = whiteV;
        this.minY = minY;
        this.maxY = maxY;
        this.hipY = hipY;
        this.firstPersonArms = firstPersonArms;
        this.buildMillis = buildMillis;
        this.weldedSeams = dev.maro.render.character.CharacterSeams.prepare(this.parts,maxY-minY);
        this.skinned = null;
    }

    /** A skinned character: no rigid parts, its own textures per material. */
    CharacterModel(String id, String name, Detail detail, dev.maro.render.character.skin.SkinnedCharacter skinned,
                   net.minecraft.client.texture.NativeImage[][] images, float maxY, long buildMillis) {
        this.id = id;
        this.name = name;
        this.detail = detail;
        this.scale = 1.0f;
        this.parts = new PartMesh[0];
        this.motionSlots = 0;
        this.atlasWidth = 1;
        this.atlasHeight = 1;
        this.atlasMips = new int[][]{{-1}};
        this.whiteU = 0.0f;
        this.whiteV = 0.0f;
        this.minY = 0.0f;
        this.maxY = maxY;
        this.hipY = skinned.rig.hipHeight;
        var rig = skinned.rig;
        this.firstPersonArms = rig.has(dev.maro.render.character.skin.Human.RIGHT_UPPER_ARM) && rig.has(dev.maro.render.character.skin.Human.LEFT_UPPER_ARM);
        this.buildMillis = buildMillis;
        this.weldedSeams = 0;
        this.skinned = skinned;
        this.pendingImages = images;
    }

    /** A skinned character built in code (the bendable default skin). */
    public static CharacterModel of(String id, String name, dev.maro.render.character.skin.SkinnedCharacter skinned) {
        return new CharacterModel(id, name, Detail.HIGH, skinned, null, skinned.rig.height, 0);
    }

    public int triangles() {
        if (this.skinned != null) return this.skinned.level(this.detail.ordinal()).triangles();
        int n = 0;
        for (PartMesh p : this.parts) {
            n += p.triangles();
        }
        return n;
    }

    public PartMesh part(Bone bone) {
        for (PartMesh p : this.parts) {
            if (p.extra || p.bone != bone) continue;
            return p;
        }
        return null;
    }
}
