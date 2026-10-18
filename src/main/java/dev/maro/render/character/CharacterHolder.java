// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public interface CharacterHolder {
    public WornCharacter maro$character();

    public void maro$setCharacter(WornCharacter var1);

    public float maro$seat();

    public void maro$setSeat(float var1);
}
