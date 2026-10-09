package dev.maro.render.esp;

/**
 * How one ESP module wants its tracers and glow drawn. Block ESP, Storage ESP and Hole ESP share one
 * renderer; each tracer remembers the module that queued it and is drawn in that module's style.
 */
public interface EspStyle {
    boolean isEnabled();

    boolean tracersFromBottom();

    /** Tracer thickness in pixels for a frame {@code height} pixels tall. */
    float tracerWidthPx(int height);

    float tracerHalo();

    boolean tracerPackets();

    float packetSpeed();

    /** Whether the tracers glow too. */
    boolean tracerBloom();

    /** Whether any of this module's bloom is on. */
    boolean bloomOn();

    float bloomStrength();

    float bloomSize();

    float seconds();

    void renderFailed(RuntimeException e);
}
