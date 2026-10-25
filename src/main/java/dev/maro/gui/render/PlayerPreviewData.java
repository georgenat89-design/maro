package dev.maro.gui.render;

import dev.maro.module.Module;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/** Presentation data carried only by a private portrait state, never by the live entity. */
public interface PlayerPreviewData {
    Module maro$previewModule();
    double maro$previewSeconds();
    boolean maro$previewAnimated();
    void maro$setPreview(Module module,double seconds,boolean animated);

    static PlayerPreviewData of(PlayerEntityRenderState state) {
        return state instanceof PlayerPreviewData data && data.maro$previewModule()!=null ? data : null;
    }
}
