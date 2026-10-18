package dev.maro.gui.spatial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

/**
 * The floating window's picture of the menu, under a texture name, so it can be drawn in the
 * world like any other texture. It hands out whatever picture {@link SpatialCanvas} holds now,
 * smoothly sampled; the canvas owns it, so closing this frees nothing.
 */
final class CanvasTexture extends AbstractTexture {
    static final Identifier ID = Identifier.of("maro", "floating_window");
    private static CanvasTexture instance;

    private GpuTextureView view;

    private CanvasTexture() {
    }

    /** Points the texture at the canvas's picture, registering it the first time. */
    static void show(GpuTextureView view) {
        if (instance == null) {
            instance = new CanvasTexture();
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, instance);
        }
        instance.view = view;
    }

    @Override
    public GpuTexture getGlTexture() {
        return view.texture();
    }

    @Override
    public GpuTextureView getGlTextureView() {
        return view;
    }

    @Override
    public GpuSampler getSampler() {
        return RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,FilterMode.LINEAR,FilterMode.LINEAR,false);
    }

    @Override
    public void close() {
    }
}
