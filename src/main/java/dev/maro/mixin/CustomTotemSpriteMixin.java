package dev.maro.mixin;

import dev.maro.Maro;
import dev.maro.module.impl.visuals.CustomTotem;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Custom Totem: the totem's texture is replaced by your picture as it loads, before the atlas is
 * stitched and the 3D item model is built from it, so every place the totem is drawn shows it.
 */
@Mixin(SpriteContents.class)
public abstract class CustomTotemSpriteMixin {
    @Shadow @Final @Mutable private int width;
    @Shadow @Final @Mutable private int height;
    @Shadow @Final @Mutable private NativeImage image;
    @Shadow private NativeImage[] mipmapLevelsImages;

    /** Set once swapped: the shorter constructor runs the longer one, so this is reached twice. */
    @Unique
    private boolean maro$customTotem;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void maro$customTotem(CallbackInfo ci) {
        if (maro$customTotem) return;
        var picture = CustomTotem.pictureFor(((SpriteContents) (Object) this).getId());
        if (picture == null) return;
        int size = picture.size();
        NativeImage swapped = new NativeImage(size, size, false);
        try {
            int[] argb = picture.argb();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) swapped.setColorArgb(x, y, argb[y * size + x]);
            }
        } catch (RuntimeException e) {
            swapped.close();
            // The normal totem stays; a picture that fails must never stop the textures loading.
            Maro.LOGGER.warn("Custom Totem could not use its picture", e);
            return;
        }
        if (!maro$stopAnimation()) {
            swapped.close();
            Maro.LOGGER.warn("Custom Totem could not replace this resource pack's animated totem");
            return;
        }
        image.close();
        image = swapped;
        width = size;
        height = size;
        mipmapLevelsImages = new NativeImage[] {swapped};
        maro$customTotem = true;
    }

    /**
     * A resource pack's animated totem would go on reading frames that are no longer there, so its
     * animation is dropped. Its type is not public, hence finding the field by what it holds: the
     * only field of a type nested in {@link SpriteContents}.
     */
    @Unique
    private boolean maro$stopAnimation() {
        for (Field field : SpriteContents.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().getEnclosingClass() != SpriteContents.class) continue;
            try {
                field.setAccessible(true);
                if (field.get(this) != null) field.set(this, null);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        return true;
    }
}
