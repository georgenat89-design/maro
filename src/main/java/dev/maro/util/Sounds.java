package dev.maro.util;

import dev.maro.config.ClientSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
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
        if (mc.getSoundManager() == null) return;
        float volume = ClientSettings.uiVolume.getFloat() / 100f;
        SoundEvent sound = switch (ClientSettings.uiSound.get()) {
            case "Soft" -> event(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON);
            case "Tick" -> event(SoundEvents.BLOCK_NOTE_BLOCK_HAT);
            case "Pop" -> event(SoundEvents.ENTITY_ITEM_PICKUP);
            default -> event(SoundEvents.UI_BUTTON_CLICK);
        };
        if (ClientSettings.uiSound.is("Pop")) pitch *= 1.4f;
        mc.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    /** A sound from {@link SoundEvents}, which holds some as registry entries and some as sounds. */
    private static SoundEvent event(Object entry) {
        return entry instanceof RegistryEntry<?> e ? (SoundEvent) e.value() : (SoundEvent) entry;
    }
}
