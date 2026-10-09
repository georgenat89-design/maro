package dev.maro.mixin;

import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.events.world.ChunkLoadEvent;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells whoever listens about each chunk the moment it is loaded from the server's packet. */
@Mixin(ClientChunkManager.class)
public abstract class ChunkLoadMixin {
    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void maro$loaded(CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();
        if (chunk != null) MeteorClient.EVENT_BUS.post(new ChunkLoadEvent(chunk));
    }
}
