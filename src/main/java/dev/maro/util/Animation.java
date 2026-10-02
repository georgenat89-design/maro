package dev.maro.util;

import dev.maro.config.ClientSettings;

/**
 * Frame-rate independent exponential smoothing. Every instance keeps its own clock,
 * so it can be updated from anywhere without a global delta.
 */
public final class Animation {
    private float value;
    private float target;
    private final float speed;
    private long last = System.nanoTime();

    public Animation(float speed, float initial) {
        this.speed = speed;
        this.value = initial;
        this.target = initial;
    }

    public Animation setTarget(float target) {
        this.target = target;
        return this;
    }

    public float getTarget() {
        return target;
    }

    /** Advances the animation and returns the new value. */
    public float update() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        float k = 1f - (float) Math.exp(-speed * ClientSettings.animationSpeed() * dt);
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.0005f) value = target;
        return value;
    }

    public float update(float target) {
        this.target = target;
        return update();
    }

    public float get() {
        return value;
    }

    public void snap(float v) {
        value = v;
        target = v;
        last = System.nanoTime();
    }

    public boolean isDone() {
        return value == target;
    }
}
