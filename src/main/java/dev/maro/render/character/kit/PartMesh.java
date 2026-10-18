// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class PartMesh {
    public final String name;
    public final Bone bone;
    public final int parent;
    public final boolean extra;
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    public final float gripX;
    public final float gripY;
    public final float gripZ;
    public final Motion motion;
    public final Surface base;
    public final Surface outline;
    public final Surface decals;
    public final int motionSlot;
    public final float swingScale;
    public final float swingLimit;
    public final boolean jointed;
    public final float jointX;
    public final float jointY;
    public final float jointZ;
    public final boolean neck;
    public final float lookPitch;
    public final float lookYaw;
    public final float lookLimit;

    PartMesh(String name, Bone bone, int parent, boolean extra, float pivotX, float pivotY, float pivotZ, float gripX, float gripY, float gripZ, Motion motion, Surface base, Surface outline, Surface decals, int motionSlot, float swingScale, float swingLimit, boolean jointed, float jointX, float jointY, float jointZ, boolean neck, float lookPitch, float lookYaw, float lookLimit) {
        this.name = name;
        this.bone = bone;
        this.parent = parent;
        this.extra = extra;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.gripX = gripX;
        this.gripY = gripY;
        this.gripZ = gripZ;
        this.motion = motion;
        this.base = base;
        this.outline = outline;
        this.decals = decals;
        this.motionSlot = motionSlot;
        this.swingScale = swingScale;
        this.swingLimit = swingLimit;
        this.jointed = jointed;
        this.jointX = jointX;
        this.jointY = jointY;
        this.jointZ = jointZ;
        this.neck = neck;
        this.lookPitch = lookPitch;
        this.lookYaw = lookYaw;
        this.lookLimit = lookLimit;
    }

    public boolean bends() {
        return this.motion != null && this.motion.bendPower() > 0.0f;
    }

    public boolean swings() {
        return this.swingScale != 1.0f || this.swingLimit > 0.0f;
    }

    public float swingPitch(float pitch, float walk, float weight) {
        if (!(weight > 0.0f) || !this.swings()) {
            return pitch;
        }
        float shaped = Motion.soft(walk * this.swingScale, this.swingLimit);
        return pitch + Math.min(1.0f, weight) * (shaped - walk);
    }

    public int triangles() {
        return (this.base == null ? 0 : this.base.triangles) + (this.outline == null ? 0 : this.outline.triangles) + (this.decals == null ? 0 : this.decals.triangles);
    }
}
