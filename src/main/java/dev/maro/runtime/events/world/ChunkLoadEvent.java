package dev.maro.runtime.events.world;

import net.minecraft.world.chunk.WorldChunk;

/** A chunk has just arrived from the server and been loaded, before it is drawn. */
public final class ChunkLoadEvent {
    public final WorldChunk chunk;

    public ChunkLoadEvent(WorldChunk chunk) {
        this.chunk = chunk;
    }
}
