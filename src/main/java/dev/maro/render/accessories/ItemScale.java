package dev.maro.render.accessories;

/** An item about to be drawn that can be drawn bigger or smaller, about the point it is held by. */
public interface ItemScale {
    void maro$setScale(float scale);

    float maro$scale();
}
