// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import dev.maro.render.character.CharacterHolder;
import dev.maro.render.character.WornCharacter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityRenderState.class})
public abstract class CustomModelAvatarRenderState
implements CharacterHolder, dev.maro.gui.render.PlayerPreviewData {
    @Unique private dev.maro.module.Module maro$previewModule;
    @Unique private double maro$previewSeconds;
    @Unique private boolean maro$previewAnimated;

    public dev.maro.module.Module maro$previewModule(){return maro$previewModule;}
    public double maro$previewSeconds(){return maro$previewSeconds;}
    public boolean maro$previewAnimated(){return maro$previewAnimated;}
    public void maro$setPreview(dev.maro.module.Module module,double seconds,boolean animated){
        maro$previewModule=module;maro$previewSeconds=seconds;maro$previewAnimated=animated;
    }
    @Unique
    private WornCharacter maro$character;
    @Unique
    private float maro$seat;
    @Unique
    private final float[] maro$motion = new float[8];
    @Unique
    private net.minecraft.util.Identifier maro$skin;
    @Unique
    private boolean maro$slim;

    public net.minecraft.util.Identifier maro$skin() {
        return this.maro$skin;
    }

    public boolean maro$slim() {
        return this.maro$slim;
    }

    public void maro$setSkin(net.minecraft.util.Identifier skin, boolean slim) {
        this.maro$skin = skin;
        this.maro$slim = slim;
    }

    public float[] maro$motion() {
        return this.maro$motion;
    }

    public WornCharacter maro$character() {
        return this.maro$character;
    }

    public void maro$setCharacter(WornCharacter character) {
        this.maro$character = character;
    }

    public float maro$seat() {
        return this.maro$seat;
    }

    public void maro$setSeat(float knee) {
        this.maro$seat = knee;
    }
}
