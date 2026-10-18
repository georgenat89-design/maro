// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public enum Bone {
    HEAD(0.0f, 24.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    BODY(0.0f, 24.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    RIGHT_ARM(-5.0f, 22.0f, 0.0f, -1.0f, -10.0f, 2.0f),
    LEFT_ARM(5.0f, 22.0f, 0.0f, 1.0f, -10.0f, 2.0f),
    RIGHT_LEG(-1.9f, 12.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    LEFT_LEG(1.9f, 12.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    final float gripX;
    final float gripY;
    final float gripZ;

    private Bone(float pivotX, float pivotY, float pivotZ, float gripX, float gripY, float gripZ) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.gripX = gripX;
        this.gripY = gripY;
        this.gripZ = gripZ;
    }

    public boolean arm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }

    public boolean leg() {
        return this == RIGHT_LEG || this == LEFT_LEG;
    }

    public Bone opposite() {
        return switch (this.ordinal()) {
            case 2 -> LEFT_ARM;
            case 3 -> RIGHT_ARM;
            case 4 -> LEFT_LEG;
            case 5 -> RIGHT_LEG;
            default -> this;
        };
    }
}
