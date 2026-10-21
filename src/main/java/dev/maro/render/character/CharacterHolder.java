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

    /**
     * How the player moves this frame, for the skinned animation: ground speed, vertical speed,
     * speed along the body's facing and to its left (blocks per second), on the ground (1/0),
     * whether it was filled in (1/0), sprinting (1/0).
     */
    public float[] maro$motion();

    /** The player's skin texture and arm width, for the bendable default body. */
    public net.minecraft.util.Identifier maro$skin();

    public boolean maro$slim();

    public void maro$setSkin(net.minecraft.util.Identifier skin, boolean slim);

    /** Fills in the movement for a player drawn without an entity (previews, tests). */
    public default void maro$setMotion(float speed, float vertical, boolean grounded) {
        float[] m = this.maro$motion();
        m[0] = speed;
        m[1] = vertical;
        m[2] = speed;
        m[3] = 0;
        m[4] = grounded ? 1 : 0;
        m[5] = 1;
        m[6] = speed > 5 ? 1 : 0;
    }
}
