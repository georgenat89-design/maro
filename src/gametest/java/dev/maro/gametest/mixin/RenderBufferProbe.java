package dev.maro.gametest.mixin;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Turn an invalid native write into a readable test failure before the JVM segfaults. */
@Mixin(BufferBuilder.class)
public abstract class RenderBufferProbe {
    @Inject(method="beginVertex", at=@At("RETURN"))
    private void maroTest$vertexPointer(CallbackInfoReturnable<Long> result) {
        if (result.getReturnValue() == 0) throw new AssertionError("Null native vertex allocation");
    }
    @Inject(method="beginElement", at=@At("RETURN"))
    private void maroTest$elementPointer(VertexFormatElement element, CallbackInfoReturnable<Long> result) {
        if (result.getReturnValue() == 0) throw new AssertionError("Null native vertex attribute: " + element);
    }
}
