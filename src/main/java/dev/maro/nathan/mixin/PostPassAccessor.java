package dev.maro.nathan.mixin;

import java.util.Map;
import net.minecraft.client.gl.PostEffectPass;
import com.mojang.blaze3d.buffers.GpuBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A pass's uniform buffers, by block name. The buffer the game makes for a
 * block is baked when the chain loads; Motion Blur swaps in one of its own that
 * it can write every frame.
 */
@Mixin(PostEffectPass.class)
public interface PostPassAccessor {
    @Accessor
    Map<String, GpuBuffer> getUniformBuffers();
}
