/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * marked All-Rights-Reserved. Ported from Meteor 26.2 to 1.21.11.
 */
package dev.maro.nathan.modules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The list of entity ids that should glow, shared with the two mixins.
 *
 * The glow is done the way the game itself does it: Minecraft.shouldEntityAppearGlowing decides
 * whether an entity gets an outline, and Entity.getTeamColor decides what colour that outline is
 * (EntityRenderer reads exactly those two when it fills in outlineColor). Overriding both is
 * purely local - no glowing flag is ever set on the entity, so nothing is sent to the server.
 *
 * Written from the client thread, read from the render thread, hence the concurrent map.
 */
public final class Highlight {
    private static final Map<Integer, Long> ENTRIES = new ConcurrentHashMap<>();

    /** Packed 0xRRGGBB used for the outline. */
    public static volatile int color = 0xFF3355;
    public static volatile boolean enabled = false;

    private Highlight() {
    }

    public static void add(int entityId, long expiresAtMillis) {
        ENTRIES.put(entityId, expiresAtMillis);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static boolean has(int entityId) {
        if (!enabled) return false;

        Long expiry = ENTRIES.get(entityId);
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            ENTRIES.remove(entityId);
            return false;
        }

        return true;
    }

    public static int count() {
        return ENTRIES.size();
    }
}
