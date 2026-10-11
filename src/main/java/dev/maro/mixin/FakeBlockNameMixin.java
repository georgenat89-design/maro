package dev.maro.mixin;

import dev.maro.module.impl.visuals.FakeBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fake Block's Use Its Name: an item drawn as another is called by the other's name. */
@Mixin(ItemStack.class)
public abstract class FakeBlockNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void maro$fakeName(CallbackInfoReturnable<Text> cir) {
        Text name = FakeBlock.nameLook((ItemStack) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
