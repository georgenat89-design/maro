package dev.maro.mixin;

import dev.maro.module.impl.misc.NameChange;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class NameChangeMixins {
    @Mixin(PlayerEntity.class)
    public abstract static class Label {
        @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
        private void maro$alias(CallbackInfoReturnable<Text> cir) {
            if ((Object) this instanceof AbstractClientPlayerEntity player)
                cir.setReturnValue(NameChange.nameTag(player.getUuid(), cir.getReturnValue()));
        }
    }
    @Mixin(PlayerListHud.class)
    public abstract static class Tab {
        @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
        private void maro$alias(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
            cir.setReturnValue(NameChange.tabName(entry.getProfile().id(), cir.getReturnValue()));
        }
    }
    @Mixin(ChatHud.class)
    public abstract static class Chat {
        @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
                at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private Text maro$alias(Text original) { return NameChange.chatText(original); }
    }
}
