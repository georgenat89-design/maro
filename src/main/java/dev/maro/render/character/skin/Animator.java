package dev.maro.render.character.skin;

import dev.maro.render.character.skin.Locomotion.State;

/**
 * One player's animation: picks a state from what they are doing (standing, walking, running,
 * crouching, in the air, landing, swimming, riding, asleep, gliding), crossfades between states,
 * plays steps exactly as fast as the body moves over the ground, layers an emote, the head's look
 * and the game's arm poses (swings, bows, blocking, eating) on top, and keeps the feet on the
 * floor. The rig's own clips (from its GLB) replace the shared ones for the states they name.
 *
 * <p>Allocation free after construction; {@link #update} is safe to call twice for the same
 * frame (the second call advances nothing).
 */
public final class Animator {
    private static final float FADE = 0.22f, LAND_TIME = 0.34f;
    private static final State[] STATES = State.values();

    public final Rig rig;
    /** The finished pose: world and skinning matrices for this frame. */
    public final SkinPose out;
    private final NodeClip[] own;
    private final SkinPose current, previous, a, b, c;
    private final HumanPose human = new HumanPose();
    private final float[] strides = new float[STATES.length];
    private final float[] q = new float[8];
    private final boolean[] upper, lower;

    private Group group = Group.GROUND, fadingFrom;
    private float fade = 1, groupTime, previousTime, lastTime = Float.NaN;
    private float phase, previousPhase, airTime, fallSpeed, landTime = -1;
    private float rightOverlay, leftOverlay;
    private float speedSmooth, wRun, wMove;
    private float lastEmoteBlend;

    /** States grouped as the blends inside them are continuous (idle to walk to run, crouch to creep). */
    private enum Group { GROUND, CROUCH, AIR, LAND, SWIM, RIDE, SLEEP, GLIDE }

    public Animator(Rig rig, NodeClip[] clips) {
        this.rig = rig;
        own = new NodeClip[STATES.length];
        if (clips != null) for (NodeClip clip : clips) if (clip.state >= 0 && own[clip.state] == null) own[clip.state] = clip;
        out = new SkinPose(rig);
        current = new SkinPose(rig);
        previous = new SkinPose(rig);
        a = new SkinPose(rig);
        b = new SkinPose(rig);
        c = new SkinPose(rig);
        upper = new boolean[rig.nodes];
        lower = new boolean[rig.nodes];
        for (int n = 0; n < rig.nodes; n++) {
            boolean up = false;
            for (int p = n; p >= 0; p = rig.parent[p]) {
                int d = rig.drive[p * 2];
                if (d >= 0) {
                    up = Human.ALL[d].upper();
                    break;
                }
            }
            upper[n] = up;
            lower[n] = !up;
        }
        out.evaluate();
    }

    public void update(MotionInput in) {
        boolean first = Float.isNaN(lastTime);
        float dt = first ? 0 : Math.max(0, Math.min(0.1f, in.time - lastTime));
        lastTime = in.time;
        speedSmooth += (in.speed - speedSmooth) * Math.min(1, dt * 12);
        float speed = Math.max(0, speedSmooth);

        // ---- which state
        if (!in.grounded && !in.swimming && !in.riding && !in.gliding) airTime += dt;
        else {
            if (airTime > 0.3f && fallSpeed < -5f && !in.inWater) landTime = 0;
            airTime = 0;
        }
        if (!in.grounded) fallSpeed = in.vertical;
        Group want;
        if (in.sleeping) want = Group.SLEEP;
        else if (in.riding) want = Group.RIDE;
        else if (in.gliding) want = Group.GLIDE;
        else if (in.swimming || in.crawling) want = Group.SWIM;
        else if (!in.grounded && !in.inWater && (airTime > 0.12f || in.vertical > 2f)) want = Group.AIR;
        else if (landTime >= 0 && landTime < LAND_TIME && speed < 3f) want = Group.LAND;
        else if (in.sneaking) want = Group.CROUCH;
        else want = Group.GROUND;
        if (landTime >= 0) {
            landTime += dt;
            if (landTime >= LAND_TIME) landTime = -1;
        }
        if (want != group) {
            fadingFrom = group;
            previousTime = groupTime;
            previousPhase = phase;
            group = want;
            groupTime = 0;
            fade = 0;
        }
        groupTime += dt;
        previousTime += dt;
        fade = Math.min(1, fade + dt / FADE);

        // ---- steps matched to ground speed
        float targetRun = in.sprinting || speed > 5f ? 1 : speed > 4.6f ? (speed - 4.6f) / 0.4f : 0;
        wRun += (targetRun - wRun) * Math.min(1, dt * 6);
        wMove = smooth(Math.min(1, speed / (in.sneaking ? 0.9f : 1.6f)));
        if (dt > 0) {
            float stride;
            if (group == Group.CROUCH) stride = stride(State.CROUCH_WALK);
            else if (group == Group.SWIM) stride = stride(State.SWIM);
            else stride = lerp(stride(State.WALK), stride(State.RUN), wRun);
            float blocks = Math.max(1e-3f, stride * in.unit);
            float rate = group == Group.SWIM ? Math.max(0.5f, speed / blocks) : speed / blocks;
            // Backing up plays the steps backwards.
            float direction = in.forward < -0.3f * Math.max(0.05f, speed) && group != Group.SWIM ? -1 : 1;
            phase = (phase + direction * Math.min(4.5f, rate) * dt) % 1f;
            if (phase < 0) phase += 1;
        }

        // ---- the pose
        evaluate(group, groupTime, phase, speed, current);
        if (fade < 1 && fadingFrom != null) {
            evaluate(fadingFrom, previousTime, previousPhase, speed, previous);
            out.set(previous);
            out.blend(current, smooth(fade), null);
        } else {
            out.set(current);
        }

        // ---- an emote on top: the whole body when standing, the upper body when moving
        float emote = in.emote != null ? smooth(Math.max(0, Math.min(1, in.emoteBlend))) : 0;
        lastEmoteBlend = emote;
        if (emote > 0) {
            in.emote.sample(in.emoteTime, human);
            c.apply(human);
            boolean moving = speed > 0.6f || !in.grounded || in.swimming;
            out.blend(c, emote, moving ? upper : null);
        }

        // ---- the game's arm poses: attacking, aiming, blocking, eating
        float ease = first ? 1 : Math.min(1, dt * 14);
        rightOverlay += (in.rightArmWeight - rightOverlay) * ease;
        leftOverlay += (in.leftArmWeight - leftOverlay) * ease;
        float free = 1 - emote;
        arm(Human.RIGHT_UPPER_ARM, Human.RIGHT_FOREARM, in.rightArm, in.rightElbow, rightOverlay * free);
        arm(Human.LEFT_UPPER_ARM, Human.LEFT_FOREARM, in.leftArm, in.leftElbow, leftOverlay * free);
        if (in.bodyTwist != 0) {
            Human.vanilla(0, in.bodyTwist * free, 0, q, 0);
            out.turnBy(chest(), q, 0);
        }

        // ---- the head looks where the player looks
        float look = free;
        if (look > 0 && (in.headYaw != 0 || in.headPitch != 0)) {
            float yaw = Math.max(-80, Math.min(80, in.headYaw)) * look, pitch = Math.max(-75, Math.min(80, in.headPitch)) * look;
            int neck = rig.node(Human.NECK), head = rig.node(Human.HEAD);
            float split = neck >= 0 ? 0.35f : 0;
            if (neck >= 0) {
                Human.euler(pitch * split, yaw * split, 0, q, 0);
                out.turnBy(neck, q, 0);
            }
            Human.euler(pitch * (1 - split), yaw * (1 - split), 0, q, 0);
            out.turnBy(head, q, 0);
        }

        // ---- world pose, then the feet on the ground
        float lift = out.shiftY;
        out.shiftY = 0;
        out.forward(0);
        out.shiftY = lift;
        float sole = out.sole();
        float dy = lift;
        if (Float.isFinite(sole)) {
            if (out.grounded > 0) dy -= sole * out.grounded;
            // In the air the legs may tuck up but never reach down through the floor of the body's box.
            float below = sole + dy;
            if (below < 0 && (group == Group.AIR || fadingFrom == Group.AIR && fade < 1)) dy -= below * (1 - out.grounded);
        }
        out.raise(dy);
        if (plant(in, dt)) {
            out.shiftY = 0;
            out.forward(0);
            out.shiftY = lift;
            out.raise(dy);
        }
        out.skin();
    }

    // ---- planted feet: a foot bearing weight stays where it is on the ground

    /**
     * Holds each foot that bears weight where it touched down, moving it back exactly as the
     * ground passes under the body (and round as the body turns), the leg reaching it by bending
     * the knee. A foot lets go as the clip lifts it or when it would trail too far. Returns whether
     * any leg was moved.
     */
    private boolean plant(MotionInput in, float dt) {
        boolean ground = group == Group.GROUND || group == Group.CROUCH || group == Group.LAND;
        float units = 1 / Math.max(1e-5f, in.unit);
        float fwd = in.forward, side = in.strafe;
        if (fwd == 0 && side == 0) fwd = in.speed;
        float gz = -fwd * units, gx = -side * units;
        float turn = 0;
        if (!Float.isNaN(lastYaw) && dt > 0) {
            turn = in.bodyYaw - lastYaw;
            turn -= 360 * Math.round(turn / 360);
            turn = (float) Math.toRadians(turn);
        }
        lastYaw = in.bodyYaw;
        float leg = rig.legLength;
        boolean moved = false;
        for (int s = 0; s < 2; s++) {
            int thigh = rig.node(s == 0 ? Human.RIGHT_THIGH : Human.LEFT_THIGH), shin = rig.node(s == 0 ? Human.RIGHT_SHIN : Human.LEFT_SHIN);
            int foot = rig.node(s == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT);
            if (thigh < 0 || shin < 0 || foot < 0) continue;
            float ax = out.x(foot), az = out.z(foot);
            float vx = dt > 0 ? (ax - lastX[s]) / dt : gx, vz = dt > 0 ? (az - lastZ[s]) / dt : gz;
            lastX[s] = ax;
            lastZ[s] = az;
            boolean contact = ground && soleOf(s) < leg * 0.07f;
            if (locked[s]) {
                if (turn != 0) {
                    float c = (float) Math.cos(turn), sn = (float) Math.sin(turn);
                    float x = lockX[s], z = lockZ[s];
                    lockX[s] = x * c + z * sn;
                    lockZ[s] = -x * sn + z * c;
                }
                lockX[s] += gx * dt;
                lockZ[s] += gz * dt;
                float ex = lockX[s] - ax, ez = lockZ[s] - az;
                if (!contact || ex * ex + ez * ez > leg * leg * 0.16f) locked[s] = false;
            } else if (contact && dt > 0) {
                // Touching down: the foot moves with the ground (not still swinging forward).
                float rx = vx - gx, rz = vz - gz;
                float slip = (float) Math.sqrt(rx * rx + rz * rz), pace = (float) Math.sqrt(gx * gx + gz * gz);
                if (slip < pace * 0.6f + leg * 0.6f) {
                    locked[s] = true;
                    lockX[s] = ax;
                    lockZ[s] = az;
                }
            }
            lockW[s] += ((locked[s] ? 1 : 0) - lockW[s]) * Math.min(1, dt * 18);
            if (!ground) lockW[s] = Math.min(lockW[s], fade < 1 && fadingFrom != null ? 1 - fade : 0);
            float w = lockW[s];
            if (w < 0.002f) continue;
            reach(thigh, shin, foot, ax + (lockX[s] - ax) * w, out.y(foot), az + (lockZ[s] - az) * w);
            moved = true;
        }
        return moved;
    }

    private final float[] lockX = new float[2], lockZ = new float[2], lockW = new float[2], lastX = new float[2], lastZ = new float[2];
    private final boolean[] locked = new boolean[2];
    private float lastYaw = Float.NaN;
    private final float[] ik = new float[24];

    /** Height of one foot's lowest sole point in the current world pose. */
    private float soleOf(int side) {
        float best = Float.POSITIVE_INFINITY;
        float[] w = out.world, p = rig.solePoint;
        for (int k = 0; k < 3; k++) {
            int i = side * 3 + k, n = rig.soleNode[i];
            if (n < 0) continue;
            int o = n * 12;
            best = Math.min(best, w[o + 4] * p[i * 3] + w[o + 5] * p[i * 3 + 1] + w[o + 6] * p[i * 3 + 2] + w[o + 7]);
        }
        if (best == Float.POSITIVE_INFINITY) {
            int foot = rig.node(side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT);
            best = foot >= 0 ? out.y(foot) - rig.footRest[side] : 0;
        }
        return best;
    }

    /**
     * Two-bone reach: turns the knee and then the hip so the ankle lands on the target, keeping
     * the knee in the plane it bends in and the foot turned as it was.
     */
    private void reach(int thigh, int shin, int foot, float tx, float ty, float tz) {
        float[] w = out.world;
        float ax = w[thigh * 12 + 3], ay = w[thigh * 12 + 7], az = w[thigh * 12 + 11];
        float bx = w[shin * 12 + 3], by = w[shin * 12 + 7], bz = w[shin * 12 + 11];
        float cx = w[foot * 12 + 3], cy = w[foot * 12 + 7], cz = w[foot * 12 + 11];
        float ux = ax - bx, uy = ay - by, uz = az - bz, vx = cx - bx, vy = cy - by, vz = cz - bz;
        float a = (float) Math.sqrt(ux * ux + uy * uy + uz * uz), b = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (a < 1e-5f || b < 1e-5f) return;
        float dx = tx - ax, dy = ty - ay, dz = tz - az;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        d = Math.max(Math.abs(a - b) + 1e-3f, Math.min((a + b) * 0.9995f, d));
        float cur = (float) Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy + uz * vz) / (a * b))));
        float want = (float) Math.acos(Math.max(-1, Math.min(1, (a * a + b * b - d * d) / (2 * a * b))));
        // The knee's bend axis; a straight leg bends forward.
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-4f * a * b) {
            nx = -1;
            ny = nz = 0;
        } else {
            nx /= nl;
            ny /= nl;
            nz /= nl;
        }
        float[] q = ik;
        // q[0..4) knee turn, q[4..8) hip turn, q[8..12) scratch, q[12..16) parent, q[16..19) vector
        SkinMath.axisAngleQ(nx, ny, nz, want - cur, q, 0);
        q[16] = vx;
        q[17] = vy;
        q[18] = vz;
        SkinMath.rotate(q, 0, q, 16, q, 16);
        float ex = bx + q[16] - ax, ey = by + q[17] - ay, ez = bz + q[18] - az;
        SkinMath.arcQ(ex, ey, ez, tx - ax, ty - ay, tz - az, q, 4);
        turnWorld(shin, q, 0, false);
        turnWorld(thigh, q, 4, false);
        // The foot keeps its turn in the world: undo both.
        SkinMath.mulQ(q, 4, q, 0, q, 8);
        q[8] = -q[8];
        q[9] = -q[9];
        q[10] = -q[10];
        turnWorld(foot, q, 8, true);
    }

    /**
     * {@code local = P^-1 * delta * P * local} with P the parent's world rotation as it stood
     * before this reach (so a node under one already turned can be turned back).
     */
    private void turnWorld(int node, float[] delta, int d, boolean unused) {
        int p = rig.parent[node];
        float[] q = ik;
        if (p < 0) System.arraycopy(rig.rootRotation, 0, q, 12, 4);
        else SkinMath.rotationOf(out.world, p * 12, q, 12);
        SkinMath.mulQ(delta, d, q, 12, q, 20);
        SkinMath.mulConjQ(q, 12, q, 20, q, 20);
        SkinMath.mulQ(q, 20, out.r, node * 4, out.r, node * 4);
        SkinMath.normalizeQ(out.r, node * 4);
    }

    private void arm(Human upperArm, Human forearm, float[] angles, float elbow, float weight) {
        if (weight < 0.005f) return;
        Human.vanilla(angles[0], angles[1], angles[2], q, 0);
        out.aim(rig.node(upperArm), q, 0, weight);
        Human.euler(-Math.abs(elbow), 0, 0, q, 0);
        out.aim(rig.node(forearm), q, 0, weight);
    }

    private int chest() {
        int n = rig.node(Human.CHEST);
        return n >= 0 ? n : rig.node(Human.SPINE);
    }

    /** One group's pose at a time and stride phase. */
    private void evaluate(Group g, float time, float cycle, float speed, SkinPose dst) {
        switch (g) {
            case GROUND -> {
                state(State.IDLE, time, cycle, dst);
                if (wMove > 0.001f) {
                    state(State.WALK, time, cycle, a);
                    if (wRun > 0.001f) {
                        state(State.RUN, time, cycle, b);
                        a.blend(b, wRun, null);
                    }
                    dst.blend(a, wMove, null);
                }
            }
            case CROUCH -> {
                state(State.CROUCH, time, cycle, dst);
                if (wMove > 0.001f) {
                    state(State.CROUCH_WALK, time, cycle, a);
                    dst.blend(a, wMove, null);
                }
            }
            case AIR -> {
                // Up through the jump, turning into the fall as the climb runs out.
                state(State.JUMP, time, cycle, dst);
                float falling = smooth(Math.max(0, Math.min(1, (0.5f - fallSpeed) / 4f)));
                if (falling > 0.001f) {
                    state(State.FALL, time, cycle, a);
                    dst.blend(a, falling, null);
                }
            }
            case LAND -> state(State.LAND, time, cycle, dst);
            case SWIM -> state(State.SWIM, time, cycle, dst);
            case RIDE -> state(State.RIDE, time, cycle, dst);
            case SLEEP -> state(State.SLEEP, time, cycle, dst);
            case GLIDE -> state(State.GLIDE, time, cycle, dst);
        }
    }

    /** A state's pose: the model's own clip for it if it has one, otherwise the shared one. */
    private void state(State s, float time, float cycle, SkinPose dst) {
        NodeClip clip = own[s.ordinal()];
        if (clip != null) {
            dst.rest();
            clip.sample(s.strides() ? cycle * clip.duration : time, dst.t, dst.r, dst.s);
            dst.grounded = groundedState(s) ? 1 : 0;
            return;
        }
        HumanClip shared = Locomotion.clip(s);
        shared.sample(shared.cyclic ? cycle * shared.length : time, human);
        dst.apply(human);
    }

    private static boolean groundedState(State s) {
        return s == State.IDLE || s == State.WALK || s == State.RUN || s == State.CROUCH || s == State.CROUCH_WALK || s == State.LAND;
    }

    /**
     * How far one stride (two steps) carries the body, in character units: measured from the
     * clip itself so the planted foot moves back exactly as fast as the ground goes by.
     */
    public float stride(State s) {
        int i = s.ordinal();
        if (strides[i] > 0) return strides[i];
        float fallback = rig.legLength * (s == State.RUN ? 3.4f : s == State.CROUCH_WALK ? 1.3f : s == State.SWIM ? 2.2f : 2.2f);
        float measured = measure(s);
        strides[i] = Float.isFinite(measured) && measured > rig.legLength * 0.3f && measured < rig.legLength * 8 ? measured : Math.max(1, fallback);
        return strides[i];
    }

    private float measure(State s) {
        int rf = foot(0), lf = foot(1);
        if (rf < 0 || lf < 0) return Float.NaN;
        int samples = 48;
        float[] rz = new float[samples], ry = new float[samples], lz = new float[samples], ly = new float[samples];
        for (int i = 0; i < samples; i++) {
            state(s, 0, i / (float) samples, c);
            c.forward(0);
            rz[i] = c.z(rf);
            ry[i] = c.y(rf);
            lz[i] = c.z(lf);
            ly[i] = c.y(lf);
        }
        float total = 0;
        int sides = 0;
        for (int side = 0; side < 2; side++) {
            float[] z = side == 0 ? rz : lz, y = side == 0 ? ry : ly, oy = side == 0 ? ly : ry;
            float moved = 0;
            int planted = 0;
            for (int i = 0; i < samples; i++) {
                int j = (i + 1) % samples;
                if (y[i] <= oy[i]) {
                    planted++;
                    moved += Math.max(0, z[i] - z[j]);
                }
            }
            if (planted == 0 || moved <= 0) continue;
            total += moved / (planted / (float) samples);
            sides++;
        }
        return sides == 0 ? Float.NaN : total / sides;
    }

    private int foot(int side) {
        int n = rig.node(side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT);
        return n >= 0 ? n : rig.node(side == 0 ? Human.RIGHT_SHIN : Human.LEFT_SHIN);
    }

    /** The current group, for tests: "GROUND", "AIR" and so on. */
    public String state() {
        return group.name();
    }

    public float emoteBlend() {
        return lastEmoteBlend;
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
