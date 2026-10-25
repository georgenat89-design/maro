package dev.maro.gametest.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Fabric's input wrapper drops modifier bits; send a faithful native modified-key event. */
@Mixin(Keyboard.class)
public interface HandPlacementKeyboardProbe {
    @Invoker("onKey") void maro$key(long window,int action,KeyInput key);
}
