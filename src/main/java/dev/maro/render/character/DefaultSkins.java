package dev.maro.render.character;

import dev.maro.render.character.kit.CharacterModel;
import dev.maro.render.character.skin.SkinBody;
import dev.maro.render.emote.EmoteRenderState;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Players without a character emote on a bendable copy of their own skin, so elbows, knees and the
 * waist bend through the dance the same as a character's do.
 */
@Environment(value=EnvType.CLIENT)
public final class DefaultSkins {
    private static CharacterModel wide, slim;
    private static final Map<String, WornCharacter> WORN = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WornCharacter> eldest) {
            return size() > 32;
        }
    };

    private DefaultSkins() {
    }

    public static WornCharacter worn(Identifier skin, boolean slimArms) {
        String key = skin + (slimArms ? "#slim" : "#wide");
        WornCharacter worn = WORN.get(key);
        if (worn == null) {
            CharacterModel model;
            if (slimArms) {
                if (slim == null) slim = CharacterModel.of("skin_slim", "Skin", SkinBody.of(true));
                model = slim;
            } else {
                if (wide == null) wide = CharacterModel.of("skin_wide", "Skin", SkinBody.of(false));
                model = wide;
            }
            worn = WornCharacter.skin(model, skin);
            WORN.put(key, worn);
        }
        return worn;
    }

    /** While an emote plays on a player with no character, they wear their skin's bendable body. */
    public static void wear(EntityRenderState state) {
        if (!(state instanceof CharacterHolder holder) || !(state instanceof EmoteRenderState emote)) return;
        if (emote.maro$getEmote() == null || holder.maro$character() != null || holder.maro$skin() == null) return;
        holder.maro$setCharacter(worn(holder.maro$skin(), holder.maro$slim()));
    }
}
