package dev.maro.gametest;

import dev.maro.gui.hud.BetterTexturesScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BetterTextures;
import dev.maro.textures.Modrinth;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;

/**
 * Better Textures against the real Modrinth: popular packs load with their icons, a search finds
 * packs, enabling one downloads it and the game switches it on, and Disable All takes it off. With
 * no way to reach Modrinth the network part is skipped rather than failed.
 */
final class BetterTexturesChecks {
    private BetterTexturesChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** Waits up to {@code ticks} for the condition; whether it came true. */
    private static boolean await(ClientGameTestContext context, BooleanSupplier condition, int ticks) {
        for (int i = 0; i < ticks; i += 5) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return true;
            context.waitTicks(5);
        }
        return context.computeOnClient(c -> condition.getAsBoolean());
    }

    static void run(ClientGameTestContext context) {
        BetterTextures module = ModuleManager.get(BetterTextures.class);
        require(module != null, "Better Textures was not registered");
        BetterTexturesScreen screen = context.computeOnClient(c -> {
            BetterTexturesScreen s = new BetterTexturesScreen(null, module);
            c.setScreen(s);
            return s;
        });
        String packId = null;
        try {
            await(context, () -> !screen.loading(), 400);
            if (context.computeOnClient(c -> screen.results().isEmpty())) {
                System.out.println("BETTER TEXTURES skipped the Modrinth part, it could not be reached: "
                        + context.computeOnClient(c -> screen.error()));
                context.takeScreenshot("maro-better-textures-offline");
                return;
            }
            System.out.println("BETTER TEXTURES popular packs: " + context.computeOnClient(c -> screen.results().size()));
            context.waitTicks(60);
            context.takeScreenshot("maro-better-textures");

            context.runOnClient(c -> screen.search("low fire"));
            require(await(context, () -> !screen.loading(), 400), "Searching Modrinth never finished");
            require(context.computeOnClient(c -> !screen.results().isEmpty()), "Searching for low fire found no packs: " + context.computeOnClient(c -> screen.error()));
            Modrinth.Pack pack = context.computeOnClient(c -> screen.results().getFirst());
            packId = pack.id();
            System.out.println("BETTER TEXTURES enabling " + pack.title() + " (" + pack.id() + ")");
            context.runOnClient(c -> screen.toggle(pack));
            String id = packId;
            boolean on = await(context, () -> module.isActive(id) || module.job(id) != null && module.job(id).stage == BetterTextures.Stage.FAILED, 1200);
            BetterTextures.Job job = context.computeOnClient(c -> module.job(id));
            require(on && module.isActive(id), "Enabling " + pack.title() + " did not finish: " + (job == null ? "no job" : job.stage + " " + job.message));
            require(await(context, () -> module.gameHasOn(id) && MinecraftClient.getInstance().getOverlay() == null, 1200),
                    "The game did not switch " + pack.title() + " on");
            context.waitTicks(20);
            context.takeScreenshot("maro-better-textures-enabled");
            String saved = context.computeOnClient(c -> module.saveExtra().toString());
            require(saved.contains(id), "The enabled pack was not saved: " + saved);

            context.runOnClient(c -> module.disableAll());
            require(await(context, () -> !module.gameHasOn(id) && MinecraftClient.getInstance().getOverlay() == null, 1200),
                    "Disable All left " + pack.title() + " on");
        } finally {
            context.runOnClient(c -> {
                module.disableAll();
                module.setEnabled(false);
                if (c.currentScreen instanceof BetterTexturesScreen) c.setScreen(null);
            });
            await(context, () -> MinecraftClient.getInstance().getOverlay() == null, 1200);
            context.waitTicks(5);
        }
    }
}
