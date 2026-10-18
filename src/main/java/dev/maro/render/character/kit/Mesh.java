// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class Mesh {
    final float[] pos;
    final int vertexCount;
    final int[] tris;
    final int triCount;

    Mesh(float[] pos, int vertexCount, int[] tris, int triCount) {
        this.pos = pos;
        this.vertexCount = vertexCount;
        this.tris = tris;
        this.triCount = triCount;
    }
}
