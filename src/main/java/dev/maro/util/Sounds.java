package dev.maro.util;

import dev.maro.config.ClientSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class Sounds {
    private Sounds() {
    }

    public static void click() {
        play(1.0f);
    }

    public static void toggle(boolean on) {
        play(on ? 1.25f : 0.9f);
    }

    private static void play(float pitch) {
        if (!ClientSettings.uiSounds.get()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35f));
    }
}
