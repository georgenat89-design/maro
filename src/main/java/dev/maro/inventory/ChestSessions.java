package dev.maro.inventory;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

/** Associates a menu with the actual container the player opened, rather than its title. */
public final class ChestSessions {
    private static Object world;
    private static BlockPos pending;
    private static long openedAt;
    private static ScreenHandler physical, ready;

    private ChestSessions() { }

    public static void interact(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        pending = null;
        if (client.world == null || client.player == null) return;
        var block = client.world.getBlockEntity(pos);
        if (block instanceof ChestBlockEntity || block instanceof BarrelBlockEntity || block instanceof EnderChestBlockEntity) {
            world = client.world;
            pending = pos.toImmutable();
            openedAt = System.nanoTime();
        }
    }

    public static void opened() {
        MinecraftClient client = MinecraftClient.getInstance();
        physical = ready = null;
        if (pending != null && client.world == world && client.player != null
                && System.nanoTime() - openedAt <= 5_000_000_000L
                && client.player.squaredDistanceTo(pending.toCenterPos()) <= 64) {
            physical = client.player.currentScreenHandler;
        }
        pending = null;
    }

    public static void contents(int syncId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.currentScreenHandler.syncId == syncId) ready = client.player.currentScreenHandler;
    }

    public static boolean physical(ScreenHandler handler) { return handler == physical; }
    public static boolean ready(ScreenHandler handler) { return handler == ready; }

    /** A command menu cannot consume an earlier container interaction. */
    public static void command() { pending = null; }
}
