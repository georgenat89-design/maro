// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import dev.maro.render.character.CharacterHolder;
import dev.maro.render.character.WornCharacter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityRenderState.class})
public abstract class CustomModelAvatarRenderState
implements CharacterHolder {
    @Unique
    private WornCharacter maro$character;
    @Unique
    private float maro$seat;

    public WornCharacter maro$character() {
        return this.maro$character;
    }

    public void maro$setCharacter(WornCharacter character) {
        this.maro$character = character;
    }

    public float maro$seat() {
        return this.maro$seat;
    }

    public void maro$setSeat(float knee) {
        this.maro$seat = knee;
    }
}
