package dev.maro.gametest;

import dev.maro.gui.hud.CrosshairImageScreen;
import dev.maro.gui.render.PngCrosshairImage;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomCrosshair;
import dev.maro.setting.BooleanSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.texture.NativeImage;
import java.nio.file.Files;
import java.util.Arrays;

final class CrosshairPngChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void run(ClientGameTestContext context) {
        var module = ModuleManager.get(CustomCrosshair.class);
        var stored = module.pngFolder().resolve("custom.png");
        int guiScale = context.computeOnClient(client -> client.options.getGuiScale().getValue());
        java.nio.file.Path png = null, invalid = null;
        byte[] previous = null;
        try {
            if (Files.exists(stored)) previous = Files.readAllBytes(stored);
            png = Files.createTempFile("maro-crosshair-test", ".png");
            invalid = Files.createTempFile("maro-crosshair-empty-test", ".png");
            try (var image = new NativeImage(64, 64, true)) {
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setColorArgb(x, y, 0);
                image.writeTo(invalid);
                // Deliberately asymmetric transparent margins around a symmetric magenta cross.
                for (int i = 0; i < 15; i++) {
                    image.setColorArgb(8 + i, 42, 0xFFFD3CCF);
                    image.setColorArgb(15, 35 + i, 0xFFFD3CCF);
                }
                image.writeTo(png);
            }
            var decoded = PngCrosshairImage.decode(png);
            require(decoded.left() == 8 && decoded.top() == 35 && decoded.right() == 23 && decoded.bottom() == 50,
                "Transparent padding was not detected correctly");
            var chosen = png;
            var imported = context.computeOnClient(client -> module.importPng(chosen));
            context.waitFor(client -> imported.isDone(), 300);
            require(imported.join(), "Valid PNG import failed");
            require(module.pngMode(), "Import did not activate PNG mode");
            require(Arrays.equals(Files.readAllBytes(chosen), Files.readAllBytes(stored)), "Imported PNG was not saved faithfully");
            context.getInput().resizeWindow(1291, 733);
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(3); client.onResolutionChanged();
                module.setEnabled(true);
            });
            context.waitTicks(4);
            var screenshot = context.takeScreenshot("maro-crosshair-png-centered");
            var pixels = javax.imageio.ImageIO.read(screenshot.toFile());
            int minX = pixels.getWidth(), minY = pixels.getHeight(), maxX = -1, maxY = -1;
            for (int y = pixels.getHeight() / 2 - 90; y <= pixels.getHeight() / 2 + 90; y++)
                for (int x = pixels.getWidth() / 2 - 90; x <= pixels.getWidth() / 2 + 90; x++) {
                    int rgb = pixels.getRGB(x, y);
                    if (((rgb >> 16) & 255) > 220 && ((rgb >> 8) & 255) < 100 && (rgb & 255) > 170) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
            require(maxX >= minX, "Imported PNG was not rendered in its original color");
            require(Math.abs((minX + maxX + 1) / 2.0 - pixels.getWidth() / 2.0) <= 1
                && Math.abs((minY + maxY + 1) / 2.0 - pixels.getHeight() / 2.0) <= 1,
                "Visible PNG pixels are not centered at an odd window size");
            context.runOnClient(client -> {
                ((BooleanSetting) module.getSettings().stream().filter(s -> s.getName().equals("Smooth PNG"))
                    .findFirst().orElseThrow()).set(true);
                client.setScreen(new CrosshairImageScreen(null, module));
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-crosshair-png-import");
            var rejected = invalid;
            var rejectedImport = context.computeOnClient(client -> module.importPng(rejected));
            context.waitFor(client -> rejectedImport.isDone(), 300);
            require(!rejectedImport.join(),
                "Completely transparent PNG should not replace the working image");
            require(Arrays.equals(decoded.file(), Files.readAllBytes(stored)), "Rejected image overwrote the working PNG");
        } catch (Exception e) { throw new AssertionError("PNG crosshair checks failed", e); }
        finally {
            context.runOnClient(client -> {
                module.setEnabled(false); module.getSettings().forEach(s -> s.reset()); client.setScreen(null);
                client.options.getGuiScale().setValue(guiScale); client.onResolutionChanged();
            });
            context.getInput().resizeWindow(1280, 720);
            try {
                if (previous != null) Files.write(stored, previous); else Files.deleteIfExists(stored);
                if (png != null) Files.deleteIfExists(png);
                if (invalid != null) Files.deleteIfExists(invalid);
            } catch (java.io.IOException e) { throw new AssertionError("Could not clean up PNG test files", e); }
            context.waitTicks(3);
        }
    }
}
