package dev.maro.gui.widget;

import dev.maro.util.Animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Keyed animation store for immediate-mode widgets: call {@link #of(Object, String, float)}
 * every frame with the desired target and get back the smoothed value. Keys must be stable
 * between frames (use the owning object plus a part name, never a position).
 */
public final class Anims {
    private static final Map<Key, Animation> MAP = new HashMap<>();

    private record Key(Object owner, String part) {
    }

    private Anims() {
    }

    public static float of(Object owner, String part, float target) {
        return of(owner, part, target, 14f);
    }

    public static float of(Object owner, String part, float target, float speed) {
        return MAP.computeIfAbsent(new Key(owner, part), k -> new Animation(speed, target)).update(target);
    }

    public static float of(Object owner, String part, boolean state) {
        return of(owner, part, state ? 1f : 0f);
    }

    public static void snap(Object owner, String part, float value) {
        MAP.computeIfAbsent(new Key(owner, part), k -> new Animation(14f, value)).snap(value);
    }
}
