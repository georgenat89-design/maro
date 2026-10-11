package dev.maro.gametest.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sends native cursor callbacks; no camera state is modified by the fixture. */
@Mixin(Mouse.class)
public interface PhoneMouseProbe {
    @Invoker("onCursorPos") void maro$cursor(long window, double x, double y);
}
