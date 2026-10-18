package dev.maro.phone;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** Reused video surface with smooth scaling and no sampling outside the frame. */
public final class PhoneFrameTexture extends NativeImageBackedTexture {
    public PhoneFrameTexture(String label, int width, int height) {
        super(() -> label, new NativeImage(width, height, false));
        sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false);
    }
}
