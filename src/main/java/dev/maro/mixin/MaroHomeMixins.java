package dev.maro.mixin;

import dev.maro.gui.MaroHomeScreen;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class MaroHomeMixins {
    @Mixin(MinecraftClient.class)
    public static abstract class Navigation {
        @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
        private void maro$home(Screen next,CallbackInfo ci){
            var client=(MinecraftClient)(Object)this;
            if(client.world==null&&client.currentScreen instanceof MaroHomeScreen home&&home.navigate(next)){ci.cancel();return;}
            // Loading and disconnect screens replace the host. Vanilla later returns to a
            // saved (or newly created) library screen, so restore the host on that path too.
            if(client.world==null&&MaroHomeScreen.isLibrary(next)){
                client.setScreen(new MaroHomeScreen(next));ci.cancel();return;
            }
            if(next!=null&&next.getClass()==TitleScreen.class){client.setScreen(new MaroHomeScreen());ci.cancel();}
        }
    }
    @Mixin(Screen.class)
    public static abstract class Background {
        @ModifyExpressionValue(method="method_75367",at=@At(value="FIELD",target="Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
        private Screen maro$activePane(Screen current){
            return current instanceof MaroHomeScreen home&&home.page()==(Object)this?(Screen)(Object)this:current;
        }
        @Inject(method={"renderBackground","renderInGameBackground","renderPanoramaBackground"},at=@At("HEAD"),cancellable=true)
        private void maro$panel(CallbackInfo ci){if(MaroHomeScreen.embeddedRendering())ci.cancel();}
    }
    @Mixin(EntryListWidget.class)
    public static abstract class Lists {
        @Inject(method={"drawMenuListBackground","drawHeaderAndFooterSeparators"},at=@At("HEAD"),cancellable=true)
        private void maro$glass(CallbackInfo ci){if(MaroHomeScreen.embeddedRendering())ci.cancel();}
    }
}
