package dev.maro.render.emote;

/** An emote carried on a render state, so the model is posed by the state it is drawn from. */
public interface EmoteRenderState {
    void maro$setEmote(Emote emote, float time, float blend);

    Emote maro$getEmote();

    float maro$getEmoteTime();

    float maro$getEmoteBlend();
}
