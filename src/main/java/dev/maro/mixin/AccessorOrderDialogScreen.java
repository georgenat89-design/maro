// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(value=EnvType.CLIENT)
@Mixin(value={DialogScreen.class})
public interface AccessorOrderDialogScreen {
    @Accessor(value="dialog")
    public Dialog anubis$dialog();
}
