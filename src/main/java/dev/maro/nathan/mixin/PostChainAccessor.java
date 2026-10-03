package dev.maro.nathan.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The passes of a post chain, which Motion Blur writes its per-frame uniforms into. */
@Mixin(PostEffectProcessor.class)
public interface PostChainAccessor {
    @Accessor
    List<PostEffectPass> getPasses();

    /**
     * The targets the chain keeps between frames.
     *
     * <p>Everything else a chain renders through is a framegraph resource that
     * exists only while {@code process} is running and is handed back the moment
     * it returns - so there is nothing left to read afterwards. A target declared
     * persistent is held here instead, which is the only way to get at an
     * intermediate stage after the fact. Bloom's debug dump declares its copies
     * persistent for exactly that reason.
     */
    @Accessor
    Map<Identifier, Framebuffer> getFramebuffers();
}
