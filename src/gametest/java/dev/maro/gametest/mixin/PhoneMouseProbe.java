package dev.maro.gametest.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sends native cursor callbacks; no camera state is modified by the fixture. */
@Mixin(Mouse.class)
public interface PhoneMouseProbe {
    @Invoker("onCursorPos") void maro$cursor(long window, double x, double y);
    @Invoker("onMouseButton") void maro$button(long window, net.minecraft.client.input.MouseInput input, int action);
    @Invoker("onMouseScroll") void maro$scroll(long window, double horizontal, double vertical);
}
