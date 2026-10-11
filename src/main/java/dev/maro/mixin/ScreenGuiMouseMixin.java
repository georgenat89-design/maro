package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class ScreenGuiMouseMixin {
    @WrapOperation(method = "lockCursor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void maro$keepWorldMenu(MinecraftClient client, Screen next, Operation<Void> original) {
        if (!(client.currentScreen instanceof ClickGuiScreen gui) || !gui.capturesMouse()) original.call(client, next);
    }
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    private Screen maro$worldMouseLook(Screen screen) {
        return screen instanceof ClickGuiScreen gui && gui.capturesMouse() && MinecraftClient.getInstance().mouse.isCursorLocked() ? null : screen;
    }
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void maro$crosshairClick(long window, MouseInput input, int action, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || !(client.currentScreen instanceof ClickGuiScreen gui) || !gui.worldControls()) return;
        if (gui.capturesMouse()) {
            var click = new Click(gui.width / 2d, gui.height / 2d, input);
            if (action == GLFW.GLFW_PRESS) gui.mouseClicked(click, false);
            else if (action == GLFW.GLFW_RELEASE) gui.mouseReleased(click);
        }
        ci.cancel();
    }
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void maro$crosshairScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (window == client.getWindow().getHandle() && client.currentScreen instanceof ClickGuiScreen gui && gui.worldControls()) {
            if (gui.capturesMouse()) gui.mouseScrolled(gui.width / 2d, gui.height / 2d, horizontal, vertical);
            ci.cancel();
        }
    }
}
