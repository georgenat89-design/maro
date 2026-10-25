package dev.maro.gui.render;

import dev.maro.mixin.AccessorSkinChangeEntityRenderDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.SkinTextures;

/** A private render-state instance: a portrait never edits vanilla's shared world-render state. */
public final class PlayerPreviewState {
    private static final ThreadLocal<dev.maro.module.Module> EXTRACTING = new ThreadLocal<>();
    private PlayerEntityRenderer<AbstractClientPlayerEntity> renderer;
    private PlayerEntityRenderState state;

    public PlayerEntityRenderState update(AbstractClientPlayerEntity player, float delta) {
        return update(player, delta, player.getSkin());
    }

    public PlayerEntityRenderState update(AbstractClientPlayerEntity player, float delta, SkinTextures skin) {
        return update(player, delta, skin, null);
    }

    /** Only private item-state extraction may preview disabled subject cosmetics. */
    public static boolean extracting(dev.maro.module.Module subject) { return EXTRACTING.get() == subject; }

    public PlayerEntityRenderState update(AbstractClientPlayerEntity player, float delta, SkinTextures skin,
                                         dev.maro.module.Module subject) {
        var renderers = ((AccessorSkinChangeEntityRenderDispatcher) MinecraftClient.getInstance()
                .getEntityRenderDispatcher()).anubis$skinChangePlayerRenderers();
        var selected = renderers.get(skin.model());
        if (selected == null) throw new IllegalStateException("No player renderer for preview skin");
        if (renderer != selected || state == null) {
            renderer = selected;
            state = renderer.createRenderState();
        }
        ((PlayerPreviewData)state).maro$setPreview(null,0,false);
        var previous = EXTRACTING.get();
        EXTRACTING.set(subject);
        try { renderer.updateRenderState(player, state, delta); }
        finally { if (previous == null) EXTRACTING.remove(); else EXTRACTING.set(previous); }
        state.skinTextures = skin;
        ((dev.maro.render.character.CharacterHolder) state).maro$setSkin(skin.body().texturePath(),
                skin.model() == net.minecraft.entity.player.PlayerSkinType.SLIM);
        return state;
    }

    public void release() { renderer = null; state = null; }
}
