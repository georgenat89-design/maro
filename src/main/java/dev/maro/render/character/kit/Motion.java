// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Motion {
    private static final float DEG = (float)Math.PI / 180;
    private static final float TAU = (float)Math.PI * 2;
    private static final float STRIDE = 0.6662f;
    float restPitch;
    float restYaw;
    float restRoll;
    float idlePitch;
    float idleYaw;
    float idleRoll;
    float idleRate = 0.4f;
    float idlePhase;
    float walkPitch;
    float walkYaw;
    float walkRoll;
    float walkRate = 1.0f;
    float walkPhase;
    float drag;
    float hang;
    float turnRoll;
    float turnYaw;
    Bone follow;
    float followAmount;
    float followLimit;
    float stiffness;
    float damping;
    float bend;
    float limit = 80.0f;

    public static Motion none() {
        return new Motion();
    }

    public static Motion hair() {
        return new Motion().idle(2.5f, 0.0f, 2.0f, 0.35f).walk(4.0f, 0.0f, 3.0f, 2.0f).drag(16.0f).hang(0.75f).turn(12.0f, 0.0f).spring(55.0f, 7.0f).bend(1.3f);
    }

    public static Motion tail() {
        return new Motion().idle(2.0f, 10.0f, 0.0f, 0.45f).walk(3.0f, 9.0f, 0.0f, 1.0f).drag(8.0f).hang(0.25f).turn(0.0f, 18.0f).spring(35.0f, 5.0f).bend(1.2f);
    }

    public static Motion skirt(Bone leg) {
        return new Motion().follow(leg, 0.45f).drag(4.0f).spring(90.0f, 12.0f).bend(1.0f);
    }

    public static Motion cloth() {
        return new Motion().idle(1.5f, 0.0f, 1.5f, 0.3f).walk(5.0f, 0.0f, 2.0f, 2.0f).drag(10.0f).hang(0.6f).turn(8.0f, 0.0f).spring(45.0f, 6.0f).bend(1.4f);
    }

    public Motion rest(float pitch, float yaw, float roll) {
        this.restPitch = pitch;
        this.restYaw = yaw;
        this.restRoll = roll;
        return this;
    }

    public Motion idle(float pitch, float yaw, float roll, float cyclesPerSecond) {
        this.idlePitch = pitch;
        this.idleYaw = yaw;
        this.idleRoll = roll;
        this.idleRate = cyclesPerSecond;
        return this;
    }

    public Motion idlePhase(float degrees) {
        this.idlePhase = degrees * ((float)Math.PI / 180);
        return this;
    }

    public Motion walk(float pitch, float yaw, float roll, float cyclesPerStride) {
        this.walkPitch = pitch;
        this.walkYaw = yaw;
        this.walkRoll = roll;
        this.walkRate = cyclesPerStride;
        return this;
    }

    public Motion walkPhase(float degrees) {
        this.walkPhase = degrees * ((float)Math.PI / 180);
        return this;
    }

    public Motion drag(float pitchAtFullSpeed) {
        this.drag = pitchAtFullSpeed;
        return this;
    }

    public Motion hang(float amount) {
        this.hang = amount;
        return this;
    }

    public Motion turn(float roll, float yaw) {
        this.turnRoll = roll;
        this.turnYaw = yaw;
        return this;
    }

    public Motion follow(Bone bone, float amount) {
        return this.follow(bone, amount, 0.0f);
    }

    public Motion follow(Bone bone, float amount, float limitDegrees) {
        this.follow = bone;
        this.followAmount = amount;
        this.followLimit = Math.max(0.0f, limitDegrees);
        return this;
    }

    public Motion spring(float stiffness, float damping) {
        this.stiffness = Math.max(0.0f, stiffness);
        this.damping = Math.max(0.0f, damping);
        return this;
    }

    public Motion bend(float power) {
        this.bend = Math.max(0.0f, power);
        return this;
    }

    public Motion limit(float degrees) {
        this.limit = Math.abs(degrees);
        return this;
    }

    public Motion mirrored() {
        Motion m = this.copy();
        m.restYaw = -this.restYaw;
        m.restRoll = -this.restRoll;
        m.follow = this.follow == null ? null : this.follow.opposite();
        return m;
    }

    public Motion copy() {
        Motion m = new Motion();
        m.restPitch = this.restPitch;
        m.restYaw = this.restYaw;
        m.restRoll = this.restRoll;
        m.idlePitch = this.idlePitch;
        m.idleYaw = this.idleYaw;
        m.idleRoll = this.idleRoll;
        m.idleRate = this.idleRate;
        m.idlePhase = this.idlePhase;
        m.walkPitch = this.walkPitch;
        m.walkYaw = this.walkYaw;
        m.walkRoll = this.walkRoll;
        m.walkRate = this.walkRate;
        m.walkPhase = this.walkPhase;
        m.drag = this.drag;
        m.hang = this.hang;
        m.turnRoll = this.turnRoll;
        m.turnYaw = this.turnYaw;
        m.follow = this.follow;
        m.followAmount = this.followAmount;
        m.followLimit = this.followLimit;
        m.stiffness = this.stiffness;
        m.damping = this.damping;
        m.bend = this.bend;
        m.limit = this.limit;
        return m;
    }

    public float bendPower() {
        return this.bend;
    }

    public boolean springy() {
        return this.stiffness > 0.0f;
    }

    public void target(Input in, Bone parent, float[] out) {
        float speed = Math.min(1.0f, Math.max(0.0f, in.walkSpeed));
        float idle = (float)Math.sin(in.time * this.idleRate * ((float)Math.PI * 2) + this.idlePhase);
        float walk = (float)Math.sin(in.walkPos * 0.6662f * this.walkRate + this.walkPhase) * speed;
        float turn = Math.max(-1.5f, Math.min(1.5f, in.turnRate / 180.0f));
        int p = parent.ordinal();
        float pitch = (this.restPitch + this.idlePitch * idle + this.walkPitch * walk + this.drag * speed) * ((float)Math.PI / 180) - this.hang * in.pitch[p];
        float yaw = (this.restYaw + this.idleYaw * idle + this.walkYaw * walk + this.turnYaw * turn) * ((float)Math.PI / 180);
        float roll = (this.restRoll + this.idleRoll * idle + this.walkRoll * walk + this.turnRoll * turn) * ((float)Math.PI / 180);
        if (this.follow != null) {
            int f = this.follow.ordinal();
            pitch += Motion.soft(this.followAmount * (in.pitch[f] - in.pitch[p]), this.followLimit);
            yaw += Motion.soft(this.followAmount * (in.yaw[f] - in.yaw[p]), this.followLimit);
            roll += Motion.soft(this.followAmount * (in.roll[f] - in.roll[p]), this.followLimit);
        }
        float lim = this.limit * ((float)Math.PI / 180);
        out[0] = Math.max(-lim, Math.min(lim, pitch));
        out[1] = Math.max(-lim, Math.min(lim, yaw));
        out[2] = Math.max(-lim, Math.min(lim, roll));
    }

    static float soft(float radians, float limitDegrees) {
        if (limitDegrees <= 0.0f) {
            return radians;
        }
        float max = limitDegrees * ((float)Math.PI / 180);
        return max * (float)Math.tanh(radians / max);
    }

    public void step(float[] state, int offset, float[] target, float dt) {
        if (this.stiffness <= 0.0f || dt <= 0.0f || dt > 0.25f || Float.isNaN(state[offset])) {
            for (int i = 0; i < 3; ++i) {
                state[offset + i] = target[i];
                state[offset + 3 + i] = 0.0f;
            }
            return;
        }
        int steps = Math.max(1, (int)Math.ceil(dt * 120.0f));
        float h = dt / (float)steps;
        for (int s = 0; s < steps; ++s) {
            for (int i = 0; i < 3; ++i) {
                float a = state[offset + i];
                float v = state[offset + 3 + i];
                v += (this.stiffness * (target[i] - a) - this.damping * v) * h;
                state[offset + 3 + i] = v;
                state[offset + i] = a + v * h;
            }
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Input {
        public float time;
        public float walkPos;
        public float walkSpeed;
        public float turnRate;
        public final float[] pitch = new float[6];
        public final float[] yaw = new float[6];
        public final float[] roll = new float[6];
    }
}
