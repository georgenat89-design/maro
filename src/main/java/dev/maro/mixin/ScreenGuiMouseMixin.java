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
import net.minecraft.util.math.Smoother;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class ScreenGuiMouseMixin {
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    @Shadow @Final private Smoother cursorXSmoother;
    @Shadow @Final private Smoother cursorYSmoother;

    @WrapOperation(method = "lockCursor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void maro$keepWorldMenu(MinecraftClient client, Screen next, Operation<Void> original) {
        if (!(client.currentScreen instanceof ClickGuiScreen gui) || !gui.capturesMouse()) original.call(client, next);
    }
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    private Screen maro$worldMouseLook(Screen screen) {
        return screen instanceof ClickGuiScreen gui && gui.capturesMouse() && MinecraftClient.getInstance().mouse.isCursorLocked() ? null : screen;
    }
    /** Held widgets consume raw motion before sensitivity, inversion or camera smoothing. */
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void maro$screenWidgetDrag(double timeDelta, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ClickGuiScreen gui && gui.consumeScreenInteractionMouse(cursorDeltaX, cursorDeltaY)) {
            cursorXSmoother.clear();
            cursorYSmoother.clear();
            ci.cancel(); // Mouse.tick still clears both raw deltas after this method returns.
        }
    }
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void maro$crosshairClick(long window, MouseInput input, int action, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || !(client.currentScreen instanceof ClickGuiScreen gui) || !gui.worldControls()) return;
        if (gui.capturesMouse()) {
            var click = new Click(gui.width / 2d, gui.height / 2d, input);
            if (action == GLFW.GLFW_PRESS) {
                gui.mouseClicked(click, false);
                if (gui.screenInteractionDragging()) maro$clearWidgetMotion();
            } else if (action == GLFW.GLFW_RELEASE) {
                boolean dragged = gui.consumeScreenInteractionMouse(cursorDeltaX, cursorDeltaY);
                gui.mouseReleased(click);
                if (dragged) {
                    gui.consumeScreenInteractionMouse(0, 0); // Finish the latch before the next ordinary look.
                    maro$clearWidgetMotion();
                }
            }
        }
        ci.cancel();
    }
    private void maro$clearWidgetMotion() {
        cursorDeltaX = cursorDeltaY = 0;
        cursorXSmoother.clear();
        cursorYSmoother.clear();
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
