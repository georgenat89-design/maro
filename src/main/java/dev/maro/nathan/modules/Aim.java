/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * marked All-Rights-Reserved. Ported from Meteor 26.2 to 1.21.11.
 */
package dev.maro.nathan.modules;

import java.util.Random;
import net.minecraft.util.math.MathHelper;

import static dev.maro.runtime.MeteorClient.mc;

/**
 * Human-looking aim.
 *
 * Nothing here is tick-based and nothing snaps. Every frame it works out how far the crosshair
 * still has to travel, picks a speed for the whole 2D move (not per axis, because a hand moves a
 * mouse in one motion), and walks the current speed toward that with a fixed acceleration limit.
 * The result eases in, eases out and can overshoot a little on a big turn, which is what a person
 * doing a fast flick actually looks like.
 *
 * The rotation is applied with {@link net.minecraft.entity.Entity#changeLookDirection(double, double)} - the
 * exact call the mouse makes. So there is no separate look packet: the head really moves, the
 * normal movement packet carries it, and the block the game picks under the crosshair is the one
 * we are aiming at. Everything downstream (breaking, using) is then just the vanilla path.
 */
public class Aim {
    private static final double MOUSE_SCALE = 0.15; // Entity.turn multiplies its input by this

    private final Random rng = new Random();

    private double targetYaw, targetPitch;
    private boolean hasTarget;

    /** Signed speed along the current direction of travel, degrees per second. */
    private double speed;

    private long lastNanos;
    private long targetSetNanos;
    /** Reaction delay rolled once per new target, so it is never the same number twice. */
    private long reactionNanos;

    private double tremorPhaseX, tremorPhaseY;
    private double tremorRateX = 1.7, tremorRateY = 1.3;

    private double lastError;

    public void aimAt(double yaw, double pitch) {
        // The reaction delay belongs to a new look, not to every small correction of an existing
        // one. Re-arming on any movement of the target froze the head solid while walking, since
        // the bearing to a block changes every frame you move.
        boolean bigJump = hasTarget && settled(6)
            && (Math.abs(MathHelper.wrapDegrees(yaw - targetYaw)) > 25 || Math.abs(pitch - targetPitch) > 25);

        if (!hasTarget || bigJump) {
            targetSetNanos = System.nanoTime();
            reactionNanos = (long) ((80 + rng.nextDouble() * 120) * 1_000_000L);
            tremorRateX = 1.4 + rng.nextDouble() * 0.8;
            tremorRateY = 1.0 + rng.nextDouble() * 0.8;
        }

        targetYaw = yaw;
        targetPitch = pitch;
        hasTarget = true;
    }

    /**
     * Forget the clock without forgetting the target. Used while a screen is open, so the first
     * frame afterwards does not integrate the whole gap into one jump.
     */
    public void pause() {
        lastNanos = 0;
        speed = 0;
    }

    public void stop() {
        hasTarget = false;
        speed = 0;
        lastNanos = 0;
        lastError = Double.MAX_VALUE;
    }

    /** Degrees the crosshair still has to travel. Large when we have no target at all. */
    public double error() {
        return hasTarget ? lastError : Double.MAX_VALUE;
    }

    public boolean settled(double toleranceDegrees) {
        return hasTarget && lastError <= toleranceDegrees;
    }

    /**
     * Call once per frame. dt comes from the wall clock, so the motion is identical at 30 fps and
     * at 300 fps - that is the whole point of not driving this from ticks or frames.
     */
    public void update(double maxSpeed, double accel, double tremor) {
        if (mc.player == null) return;

        long now = System.nanoTime();
        if (lastNanos == 0) {
            lastNanos = now;
            return;
        }

        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (dt <= 0) return;
        if (dt > 0.25) dt = 0.25; // a lag spike must not teleport the crosshair

        if (!hasTarget) return;

        tremorPhaseX += dt * tremorRateX;
        tremorPhaseY += dt * tremorRateY;

        // A hand never holds perfectly still. Applied to the target, not to the step, so it
        // wanders around the point instead of drifting away from it.
        double aimYaw = targetYaw + Math.sin(tremorPhaseX) * tremor;
        double aimPitch = targetPitch + Math.sin(tremorPhaseY) * tremor * 0.7;

        double errYaw = MathHelper.wrapDegrees(aimYaw - mc.player.getYaw());
        double errPitch = aimPitch - mc.player.getPitch();
        double err = Math.sqrt(errYaw * errYaw + errPitch * errPitch);
        lastError = err;

        if (now - targetSetNanos < reactionNanos) {
            speed = 0; // do not resume a pre-pause speed when the reaction ends
            return;
        }

        if (err < 0.01) {
            speed *= Math.max(0, 1 - dt * 12);
            return;
        }

        double ux = errYaw / err;
        double uy = errPitch / err;

        // Ease-out: want a speed proportional to what is left, capped, with a floor so the last
        // fraction of a degree does not take forever.
        double want = Math.min(maxSpeed, err * 7.5);
        want = Math.max(want, Math.min(6.0, maxSpeed));

        double delta = want - speed;
        double maxDelta = accel * dt;
        speed += MathHelper.clamp(delta, -maxDelta, maxDelta);

        double step = speed * dt;

        // Overshoot is allowed on a real flick and forbidden on a small correction, because
        // overshooting a block means the crosshair leaves it and mining stops.
        double allowed = err > 25 ? err * 1.12 : err;
        if (step > allowed) {
            step = allowed;
            speed *= 0.4;
        }

        double stepYaw = ux * step;
        double stepPitch = uy * step;

        mc.player.changeLookDirection(stepYaw / MOUSE_SCALE, stepPitch / MOUSE_SCALE);
    }
}
