package dev.maro.gui.preview;

import dev.maro.gui.VisualPreview;

/** Explicit presentation metadata for a renderer supported by the shared preview session. */
public interface PreviewProvider {
    enum Content { PLAYER, ENTITY, HUD, SHADER, ITEM, SAMPLE, COMPARISON }
    enum Controls { MODEL, PLAYBACK, STATIC, COMPARISON }

    String id();
    VisualPreview.Kind kind();
    Content content();
    Controls controls();
    String caption();
    boolean exactRenderer();
    boolean animated();
}
