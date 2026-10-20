package dev.maro.render.character.skin;

/** How one material of a skinned character is drawn. */
public final class SkinMaterial {
    public static final int OPAQUE = 0, CUTOUT = 1, BLEND = 2;
    public final String name;
    /** Index into the character's images, or -1 for untextured (vertex colour on white). */
    public final int image;
    public final int mode;
    public final float cutoff;
    public final boolean doubleSided;
    /** A held prop that hides while that hand holds a real item: -1 none, 0 right, 1 left. */
    public final int prop;

    public SkinMaterial(String name, int image, int mode, float cutoff, boolean doubleSided, int prop) {
        this.name = name;
        this.image = image;
        this.mode = mode;
        this.cutoff = cutoff;
        this.doubleSided = doubleSided;
        this.prop = prop;
    }
}
