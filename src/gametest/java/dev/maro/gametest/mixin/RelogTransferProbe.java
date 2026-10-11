package dev.maro.gametest.mixin;

import dev.maro.gametest.MaroRelogChecks;
import net.minecraft.server.integrated.IntegratedServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

/** A dedicated server keeps ticking while its sole player reconfigures. */
@Mixin(IntegratedServer.class)
public abstract class RelogTransferProbe {
    @Shadow private boolean paused;

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/server/integrated/IntegratedServer;paused:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void maroTest$keepTransferTicking(BooleanSupplier shouldKeepTicking, CallbackInfo info) {
        if (MaroRelogChecks.keepServerTicking) paused = false;
    }
}
