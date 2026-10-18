package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep client menus and module sub-screens live without changing vanilla pause screens. */
@Mixin(MinecraftClient.class)
public abstract class MaroGuiPauseMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"))
    private boolean maro$menuPause(Screen screen,Operation<Boolean> original) {
        String name=screen.getClass().getName();
        boolean menu=name.startsWith("dev.maro.gui.")&&!name.startsWith("dev.maro.gui.spotify.")
            ||name.startsWith("dev.maro.runtime.gui.")||name.startsWith("dev.maro.nathan.gui.");
        return menu?false:original.call(screen);
    }
}
