package dev.maro.module.impl.misc;

import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** A momentary handheld player; closing it never stops the music. */
public final class SpotifyPhone extends Module {
    public final NumberSetting size = add(new NumberSetting("Size", "Size of the handheld phone", 1, .8, 1.2, .05));
    public final ModeSetting hand = add(new ModeSetting("Hand", "Which side holds the phone", "Right", "Right", "Left"));
    public final BooleanSetting showHand = add(new BooleanSetting("Show Hand", "Draw your skin's hand holding the phone", true));
    private SpotifyPhoneScreen screen;
    private boolean acquired;

    public SpotifyPhone() {
        super("Spotify Phone", "Open a handheld Spotify player. F10 opens or closes it; click to change songs.", Category.MISC);
        getBind().set(GLFW.GLFW_KEY_F10);
    }

    @Override public boolean persistEnabled() { return false; }

    @Override public void onTick() {
        if (!inGame()) { setEnabled(false); return; }
        if (screen != null) return;
        // Wait until the opening key's event has finished, so it cannot immediately close the screen.
        var media = SpotifySession.acquire();
        acquired = true;
        screen = new SpotifyPhoneScreen(this, media);
        mc.setScreen(screen);
    }

    @Override protected void onDisable() {
        var old = screen;
        screen = null;
        if (old != null && mc.currentScreen == old) mc.setScreen(null);
        if (acquired) { acquired = false; SpotifySession.release(); }
    }

    public void screenClosed(SpotifyPhoneScreen closed) {
        if (screen != closed) return;
        screen = null;
        setEnabled(false);
    }
}
