package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.FakePay;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class FakePayMixins {
    private FakePayMixins() { }
    @Mixin(value = ClientPlayNetworkHandler.class, priority = 1100)
    public static abstract class Commands {
        @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
        private void maro$fakePay(String command, CallbackInfo info) {
            FakePay pay = ModuleManager.get(FakePay.class);
            if (pay != null && pay.handlePay(command)) info.cancel();
        }
    }
    @Mixin(InGameHud.class)
    public static abstract class Sidebar {
        // Change the assembled row before vanilla measures and renders it.
        @WrapOperation(method = "method_55439", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
        private MutableText maro$balance(AbstractTeam team, Text name, Operation<MutableText> original) {
            MutableText row = original.call(team, name);
            FakePay pay = ModuleManager.get(FakePay.class);
            return pay == null ? row : pay.sidebarText(row);
        }
    }
}
