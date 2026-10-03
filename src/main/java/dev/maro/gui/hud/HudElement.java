package dev.maro.gui.hud;

/**
 * Something drawn on the HUD that {@link HudPlacementScreen} can move and resize. All sizes are
 * in GUI pixels, the same units as a screen's mouse coordinates.
 */
public interface HudElement {
    String hudName();

    float hudLeft();

    float hudTop();

    float hudWidth();

    float hudHeight();

    float hudScale();

    /** Puts the element's top-left corner here, kept on screen. */
    void hudMove(float left, float top);

    /** Changes the scale by {@code by}, keeping it in range. */
    void hudResize(float by);

    void hudReset();
}
