// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public interface ImageDecoder {
    public Picture decode(byte[] var1) throws IOException;

    @Environment(value=EnvType.CLIENT)
    public record Picture(int width, int height, int[] argb) {
    }
}
