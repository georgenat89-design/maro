package dev.maro.render.character.skin;

/**
 * A character ready to animate: its rig, three levels of detail of the skinned mesh (low, medium,
 * high), how each material draws, its textures as the GLB stored them, and any clips it brought.
 */
public final class SkinnedCharacter {
    public final Rig rig;
    public final SkinLevel[] levels;
    public final SkinMaterial[] materials;
    /** Each image as stored: PNG or JPEG bytes, or raw RGBA when {@link #imageRaw} says so. */
    public final byte[][] images;
    public final int[] imageWidth, imageHeight;
    public final boolean[] imageRaw;
    public final NodeClip[] clips;
    /** Whether the bones came from the file (false when they were fitted to an unrigged mesh). */
    public final boolean rigged;

    public SkinnedCharacter(Rig rig, SkinLevel[] levels, SkinMaterial[] materials, byte[][] images, int[] imageWidth, int[] imageHeight,
                            boolean[] imageRaw, NodeClip[] clips, boolean rigged) {
        this.rig = rig;
        this.levels = levels;
        this.materials = materials;
        this.images = images;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.imageRaw = imageRaw;
        this.clips = clips;
        this.rigged = rigged;
    }

    public SkinLevel level(int detail) {
        return levels[Math.max(0, Math.min(levels.length - 1, detail))];
    }
}
