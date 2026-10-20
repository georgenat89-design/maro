package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Full-resolution crops of the settings panel's preview card and of the in-game Coords Fly card, at
 * GUI scales 2, 3, 4 and Auto, in each font. They are saved as crop_*.png, which CI prints unscaled,
 * so how sharp the text, icons and edges are can be checked pixel for pixel.
 */
final class PreviewSharpnessChecks {
    private PreviewSharpnessChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static TextSetting text(Module module, String name) {
        return (TextSetting) module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context) {
        String face = context.computeOnClient(c -> ClientSettings.fontFace.get());
        boolean custom = context.computeOnClient(c -> ClientSettings.customFont.get());
        int guiScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        float[] look = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        Module coords = ModuleManager.getByName("Coords Fly");
        require(coords != null, "Coords Fly is not registered");
        String typed = context.computeOnClient(c -> text(coords, "Coords").get());
        boolean wasOn = context.computeOnClient(c -> coords.isEnabled());
        context.getInput().resizeWindow(1920, 1080);
        try {
            // Facing north with the coords 24 km to the west: the card is full and the edge arrow is on the left.
            context.runOnClient(c -> {
                c.player.setYaw(180);
                c.player.setPitch(8);
                text(coords, "Coords").set(Math.round(c.player.getX() - 24100) + " 64 " + Math.round(c.player.getZ() - 2000));
            });
            for (int scale : new int[]{2, 3, 4, 0}) preview(context, coords, scale, "Sora", true, "coords-fly-sora-gui" + scale);
            for (String family : new String[]{"Rubik", "Inter"})
                for (int scale : new int[]{2, 3}) preview(context, coords, scale, family, true, "coords-fly-" + family.toLowerCase() + "-gui" + scale);
            for (int scale : new int[]{2, 3}) preview(context, coords, scale, "Sora", false, "coords-fly-vanilla-gui" + scale);

            Class<?>[] kinds = {dev.maro.module.impl.visuals.CustomCrosshair.class, dev.maro.module.impl.visuals.BlockESP.class,
                    dev.maro.module.impl.visuals.BaseESP.class, dev.maro.module.impl.visuals.SpawnerNotifier.class,
                    dev.maro.module.impl.visuals.Trajectories.class, dev.maro.nathan.modules.SpotifyHud.class,
                    dev.maro.nathan.modules.CustomFov.class, dev.maro.module.impl.visuals.CustomTotem.class,
                    dev.maro.module.impl.visuals.ArmorHud.class};
            for (Class<?> kind : kinds) {
                Module module = ModuleManager.all().stream().filter(kind::isInstance).findFirst().orElse(null);
                require(module != null, "No module of " + kind.getSimpleName());
                String slug = module.getName().toLowerCase().replace(' ', '-');
                for (int scale : new int[]{2, 3}) preview(context, module, scale, "Sora", true, slug + "-gui" + scale);
            }

            // The real card in game keeps its place and size.
            context.runOnClient(c -> c.setScreen(null));
            context.runOnClient(c -> coords.setEnabled(true));
            for (int scale : new int[]{2, 3}) {
                hud(context, coords, scale, true, "coords-fly-hud-sora-gui" + scale);
                hud(context, coords, scale, false, "coords-fly-hud-vanilla-gui" + scale);
            }
            System.out.println("[preview-sharpness-proof] preview and HUD crops saved at GUI scales 2/3/4/Auto");
        } finally {
            context.runOnClient(c -> {
                if (c.currentScreen != null) c.setScreen(null);
                coords.setEnabled(wasOn);
                text(coords, "Coords").set(typed);
                ClientSettings.fontFace.set(face);
                ClientSettings.customFont.set(custom);
                c.options.getGuiScale().setValue(guiScale);
                c.onResolutionChanged();
                c.player.setYaw(look[0]);
                c.player.setPitch(look[1]);
            });
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(3);
        }
    }

    private static void fonts(MinecraftClient c, int scale, String family, boolean custom) {
        ClientSettings.fontFace.set(family);
        ClientSettings.customFont.set(custom);
        c.options.getGuiScale().setValue(scale);
        c.onResolutionChanged();
    }

    /** Opens a module's settings beside the menu and crops its preview card. */
    private static void preview(ClientGameTestContext context, Module module, int scale, String family, boolean custom, String name) {
        context.runOnClient(c -> {
            fonts(c, scale, family, custom);
            c.setScreen(new ClickGuiScreen());
        });
        context.waitTicks(2);
        context.runOnClient(c -> ((ClickGuiScreen) c.currentScreen).openModuleOptions(module));
        context.waitTicks(24);
        double[] rect = context.computeOnClient(c -> {
            var gui = (ClickGuiScreen) c.currentScreen;
            float[] b = gui.visualPreview().bounds();
            if (b == null) return null;
            double[] a = gui.screenPose().project(b[0], b[1]), z = gui.screenPose().project(b[0] + b[2], b[1] + b[3]);
            double f = c.getWindow().getScaleFactor();
            return new double[]{a[0] * f, a[1] * f, z[0] * f, z[1] * f};
        });
        require(rect != null, "No preview for " + module.getName());
        crop(context.takeScreenshot("maro-sharp-" + name), rect, 6, "crop_" + name);
        context.runOnClient(c -> c.setScreen(null));
        context.waitTicks(2);
    }

    /** Crops the in-game card and the edge arrow pointing round to the coords. */
    private static void hud(ClientGameTestContext context, Module coords, int scale, boolean custom, String name) {
        context.runOnClient(c -> fonts(c, scale, "Sora", custom));
        context.waitTicks(6);
        double[][] rects = context.computeOnClient(c -> {
            var hud = (dev.maro.gui.hud.HudElement) coords;
            double f = c.getWindow().getScaleFactor(), h = c.getWindow().getScaledHeight();
            return new double[][]{
                    {hud.hudLeft() * f, hud.hudTop() * f, (hud.hudLeft() + hud.hudWidth()) * f, (hud.hudTop() + hud.hudHeight()) * f},
                    {0, (h / 2 - 50) * f, 110 * f, (h / 2 + 50) * f}};
        });
        Path shot = context.takeScreenshot("maro-sharp-" + name);
        crop(shot, rects[0], 14, "crop_" + name);
        crop(shot, rects[1], 0, "crop_" + name + "-arrow");
        System.out.println("[preview-sharpness] " + name + " card at " + java.util.Arrays.toString(rects[0]));
    }

    private static void crop(Path shot, double[] rect, int margin, String name) {
        try {
            BufferedImage image = ImageIO.read(shot.toFile());
            int x0 = Math.max(0, (int) Math.floor(rect[0]) - margin), y0 = Math.max(0, (int) Math.floor(rect[1]) - margin);
            int x1 = Math.min(image.getWidth(), (int) Math.ceil(rect[2]) + margin), y1 = Math.min(image.getHeight(), (int) Math.ceil(rect[3]) + margin);
            require(x1 > x0 && y1 > y0, "Empty crop for " + name);
            ImageIO.write(image.getSubimage(x0, y0, x1 - x0, y1 - y0), "png", shot.resolveSibling(name + ".png").toFile());
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }
}
