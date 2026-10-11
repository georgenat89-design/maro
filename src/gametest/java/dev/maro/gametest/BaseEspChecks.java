package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BaseESP;
import dev.maro.module.impl.visuals.StretchRes;
import dev.maro.render.esp.DonutSignatureCatalog;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;

final class BaseEspChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Setting<?> setting(BaseESP module, String name) { return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String failure) {
        await(context,condition,300,failure);
    }
    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, int ticks, String failure) {
        for (int i = 0; i < ticks/3; i++) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return;
            context.waitTicks(3);
        }
        throw new AssertionError(failure);
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        BaseESP module = ModuleManager.get(BaseESP.class);
        require(module != null, "Base ESP was not registered");
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[]{c.player.getYaw(),c.player.getPitch()});
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -14 -44 -14 30 -27 30 minecraft:air");
            world.getServer().runCommand("fill 12 -40 12 23 -34 23 minecraft:deepslate_bricks");
            world.getServer().runCommand("fill 13 -39 13 22 -35 22 minecraft:air");
            world.getServer().runCommand("fill 14 -39 14 17 -38 17 minecraft:barrel");
            // Above-ground storage and natural underground stone should not create detections.
            world.getServer().runCommand("fill -10 20 -10 1 26 1 minecraft:deepslate_bricks");
            world.getServer().runCommand("fill -9 21 -9 0 25 0 minecraft:air");
            world.getServer().runCommand("fill -8 21 -8 -5 22 -5 minecraft:barrel");
            world.getServer().runCommand("fill -12 -43 10 -6 -38 16 minecraft:deepslate");
            world.getServer().runCommand("setblock -10 -37 13 minecraft:chest");
            world.getServer().runCommand("tp @a 17 -30 0 0 28");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.getAbilities().flying = true; player.sendAbilitiesUpdate();
            });
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = true; c.player.setVelocity(0,0,0);
                c.options.setPerspective(Perspective.FIRST_PERSON); module.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(module,"sound alerts")).set(false);
                ((BooleanSetting)setting(module,"chat alerts")).set(false);
                module.setEnabled(true);
            });
            long searching = System.nanoTime();
            await(context, () -> !module.detectedBounds().isEmpty(), "Underground built base was not detected");
            System.out.printf(java.util.Locale.ROOT, "BASE ESP first detection after %.0f ms%n", (System.nanoTime() - searching) / 1e6);
            context.runOnClient(c -> {
                require(module.detectedBounds().size() == 1, "Base split across chunks or a false positive was produced: " + module.detectedBounds());
                var bounds = module.detectedBounds().getFirst();
                require(bounds.minX <= 14 && bounds.maxX >= 18 && bounds.minZ <= 14 && bounds.maxZ >= 18,
                    "Base shell did not contain its storage: " + bounds);
                require(bounds.maxY <= 0, "Above-ground base was included");
                require(module.detections().getFirst().storageBlocks()==32,"HUD storage count included structure blocks");
                require(module.pendingScans()<=module.queueLimit(),"Scan queue exceeded its bound");
                require(((ModeSetting)setting(module,"scan speed")).get().equals("Fast"),"Fast scan was not the default");
                module.hudMove(10000,10000);
                require(module.hudLeft()+module.hudWidth()<=c.getWindow().getScaledWidth(),"Detector HUD escaped the screen");
                module.hudReset();
            });
            require(!DonutSignatureCatalog.get().families().isEmpty(), "Bundled source signature catalog did not load");
            // Solid natural rock hides the vanilla structure; ESP must still render through it.
            world.getServer().runCommand("fill -5 -33 -5 30 -31 30 minecraft:stone");
            context.waitTicks(4);
            var screenshot = context.takeScreenshot("maro-base-esp-underground");
            try {
                var image = ImageIO.read(screenshot.toFile()); int green = 0;
                for (int y = 40; y < image.getHeight()-100; y++) for (int x = 20; x < image.getWidth()-20; x++) {
                    int rgb = image.getRGB(x,y), r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                    if (g > r + 35 && g > b + 35) green++;
                }
                require(green > 200, "Base ESP did not draw visible world geometry: " + green);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            context.runOnClient(c -> {
                ((BooleanSetting)setting(module,"chunk mark")).set(true);
                ModuleManager.get(StretchRes.class).setEnabled(true);
            });
            context.waitTicks(4); context.takeScreenshot("maro-base-esp-pillar-stretched");
            context.runOnClient(c -> ((ModeSetting)setting(module,"display style")).set("Outline"));
            context.waitTicks(3); context.takeScreenshot("maro-base-esp-outline");
            context.runOnClient(c -> ((ModeSetting)setting(module,"display style")).set("Both"));
            context.runOnClient(c -> ((ModeSetting)setting(module,"chunk mark mode")).set("Slab"));
            context.waitTicks(3); context.takeScreenshot("maro-base-esp-flat-marker");
            context.runOnClient(c -> {
                ModuleManager.get(StretchRes.class).setEnabled(false);
                ((BooleanSetting)setting(module,"chunk mark")).set(false);
                ((NumberSetting)setting(module,"min blocks")).set(1000.0);
            });
            await(context, () -> module.detectedBounds().isEmpty(), "Live minimum-block setting did not filter base");
            context.runOnClient(c -> ((NumberSetting)setting(module,"min blocks")).set(15.0));
            await(context, () -> !module.detectedBounds().isEmpty(), "Restoring threshold did not rebuild base");
            context.runOnClient(c -> module.setEnabled(false));
            context.waitTicks(12);
            require(context.computeOnClient(c -> module.detectedBounds().isEmpty()), "A late worker restored disabled detections");
            context.runOnClient(c -> module.setEnabled(true));
            await(context, () -> !module.detectedBounds().isEmpty(), "Re-enabling did not restart scans");
            // A long custom refresh interval makes this a real test of block-update priority,
            // rather than letting the routine periodic scan hide a broken update path.
            context.runOnClient(c -> {
                ((ModeSetting)setting(module,"scan speed")).set("Custom");
                ((NumberSetting)setting(module,"rescan interval")).set(400.0);
            });
            context.waitTicks(30);
            world.getServer().runCommand("fill 12 -40 12 23 -34 23 minecraft:air");
            await(context, () -> module.detectedBounds().isEmpty(),90,"Removed base did not get priority over the 400-tick rescan interval");
            world.getServer().runCommand("fill 12 -40 12 23 -34 23 minecraft:deepslate_bricks");
            world.getServer().runCommand("fill 13 -39 13 22 -35 22 minecraft:air");
            world.getServer().runCommand("fill 14 -39 14 17 -38 17 minecraft:barrel");
            await(context, () -> !module.detectedBounds().isEmpty(),90,"New base in previously checked chunks did not get priority");
            context.runOnClient(c -> {
                require(module.detections().getFirst().storageBlocks()==32,"A stale snapshot replaced changed block counts");
                require(module.pendingScans()<=module.queueLimit(),"Block updates submitted unbounded jobs");
                ((ModeSetting)setting(module,"scan speed")).set("Eco");
            });
            context.waitTicks(10);
            require(context.computeOnClient(c -> !module.detectedBounds().isEmpty()),"Switching scan speed lost existing detections");
            world.getServer().runCommand("fill 12 -40 12 23 -34 23 minecraft:air");
            context.runOnClient(c -> { module.setEnabled(false); require(module.detectedBounds().isEmpty(), "Disable did not clear detections"); });
            context.waitTicks(12);
            require(context.computeOnClient(c -> module.detectedBounds().isEmpty()), "A late scan restored disabled detections");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false); module.getSettings().forEach(Setting::reset);
                ModuleManager.get(StretchRes.class).setEnabled(false);
                c.options.setPerspective(perspective); c.player.getAbilities().flying = flying;
            });
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }
}
