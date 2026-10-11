package dev.maro.mixin;

import dev.maro.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the real camera; Screen GUI never changes the player's view. */
@Mixin(value = Camera.class, priority = 900)
public abstract class ScreenGuiCameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void maro$worldMenu(World world, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui) gui.prepareScreenView((Camera) (Object) this);
    }
}
