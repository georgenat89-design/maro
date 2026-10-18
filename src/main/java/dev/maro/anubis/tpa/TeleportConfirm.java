// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.tpa;

import java.util.Map;
import dev.maro.anubis.ModuleChat;
import dev.maro.anubis.ClientModule;
import dev.maro.anubis.tpa.Dialogs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(value=EnvType.CLIENT)
final class TeleportConfirm {
    private final ClientModule owner;
    private Object pressed;
    private boolean warned;

    TeleportConfirm(ClientModule owner) {
        this.owner = owner;
    }

    void reset() {
        this.pressed = null;
        this.warned = false;
    }

    boolean tick(MinecraftClient mc) {
        Dialogs.View view = Dialogs.current(mc);
        if (view == null || !view.titled("teleport")) {
            return false;
        }
        if (view.dialog() == this.pressed) {
            return true;
        }
        Dialogs.Button confirm = view.labelled("confirm", "yes", "accept");
        if (confirm == null) {
            return false;
        }
        if (!this.warned) {
            this.warned = true;
            ModuleChat.send(this.owner, ModuleChat.body().warn("Your teleport confirm menu is on").text(". Turn it off in /settings so requests go out faster. Confirming each one for now."));
        }
        this.pressed = view.dialog();
        Dialogs.press(mc, view, confirm, Map.of());
        return true;
    }
}
