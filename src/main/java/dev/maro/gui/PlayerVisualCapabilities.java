package dev.maro.gui;

import dev.maro.module.Module;

/** Deliberate support list for the player portrait, independent of names and categories. */
public final class PlayerVisualCapabilities {
    private PlayerVisualCapabilities() { }

    public static boolean supports(Module module) {
        var provider=dev.maro.gui.preview.PreviewProviders.provider(module);
        return provider!=null&&provider.content()==dev.maro.gui.preview.PreviewProvider.Content.PLAYER;
    }
}
