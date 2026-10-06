package dev.maro.mixin;

import dev.maro.module.impl.visuals.PotatoGraphics;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Potato Graphics' flat textures: block sprites are repainted as they load, before mipmapping. */
@Mixin(SpriteContents.class)
public abstract class PotatoTexturesMixin {
    @Shadow
    @Final
    private NativeImage image;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void maro$potatoTexture(CallbackInfo ci) {
        String style = PotatoGraphics.textureStyleForLoad();
        if (style.equals(PotatoGraphics.TEXTURES_NORMAL)) return;
        if (!((SpriteContents) (Object) this).getId().getPath().startsWith("block/")) return;
        try {
            PotatoGraphics.flatten(image, style);
        } catch (RuntimeException e) {
            // A texture we cannot read is left as it is; it must never stop the textures loading.
        }
    }
}
