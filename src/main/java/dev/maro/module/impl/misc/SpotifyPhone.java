package dev.maro.module.impl.misc;

import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.KeybindSetting;
import org.lwjgl.glfw.GLFW;

/** A momentary handheld player; closing it never stops the music. */
public final class SpotifyPhone extends Module {
    public final ModeSetting device = add(new ModeSetting("Device", "Choose a phone or a wider iPad", "Phone", "Phone", "iPad"));
    public final ModeSetting videoFraming = add(new ModeSetting("Phone Video Framing", "Fill zooms to cover the video area; Fit shows the whole clip without cropping", "Fill", "Fit", "Fill"));
    public final ModeSetting tabletVideoFraming = add(new ModeSetting("iPad Video Framing", "Fit shows the whole clip; Fill zooms to cover the video area", "Fit", "Fit", "Fill"));
    public final NumberSetting size = add(new NumberSetting("Size", "Size of the handheld phone", 1, .55, 1.45, .05));
    public final ModeSetting hand = add(new ModeSetting("Hand", "Which side holds the phone", "Right", "Right", "Left"));
    public final BooleanSetting showHand = add(new BooleanSetting("Show Hand", "Draw your skin's hand holding the phone", true));
    public final BooleanSetting moveWhileOpen = add(new BooleanSetting("Move While Open", "Keep movement keys active while using the phone.", true));
    public final BooleanSetting mouseLook = add(new BooleanSetting("Mouse Look", "Look around normally. Hold Alt to use the phone controls.", true));
    public final KeybindSetting interactBind = add(new KeybindSetting("Interact Key", "Switch phone apps and use its controls", GLFW.GLFW_KEY_I));
    public final BooleanSetting gameInteraction = add(new BooleanSetting("Game Actions", "Mine, fire rockets, use held items and change hotbar slots with the phone open. Hold Alt or press Interact to use phone controls.", true));
    public final BooleanSetting swipeAnimation = add(new BooleanSetting("Swipe Animation", "Your opposite hand swipes the artwork when the song changes", true));
    public final KeybindSetting previousBind = add(new KeybindSetting("Previous Song", "Playback shortcut while the phone is open", GLFW.GLFW_KEY_LEFT));
    public final KeybindSetting nextBind = add(new KeybindSetting("Next Song", "Playback shortcut while the phone is open", GLFW.GLFW_KEY_RIGHT));
    public final KeybindSetting playPauseBind = add(new KeybindSetting("Play / Pause", "Playback shortcut; Ctrl+Space also works", GLFW.GLFW_KEY_P));
    public final KeybindSetting volumeUpBind = add(new KeybindSetting("Volume Up", "Raise output volume by five percent", GLFW.GLFW_KEY_PAGE_UP));
    public final KeybindSetting volumeDownBind = add(new KeybindSetting("Volume Down", "Lower output volume by five percent", GLFW.GLFW_KEY_PAGE_DOWN));
    public final KeybindSetting muteBind = add(new KeybindSetting("Mute", "Mute or restore output volume", GLFW.GLFW_KEY_M));
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
