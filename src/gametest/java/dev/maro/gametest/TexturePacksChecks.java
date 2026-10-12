package dev.maro.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.gui.hud.TexturePacksScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.TexturePacks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Texture Packs with two packs made for the test in the resourcepacks folder: both are listed with
 * their own icons, enabling them puts the newest on top, Apply switches them on in the game in that
 * order, moving one up swaps them, and turning the module off puts the usual packs back.
 */
final class TexturePacksChecks {
    private TexturePacksChecks() {
    }

    private static final String RED = "file/maro-test-red.zip", BLUE = "file/maro-test-blue.zip";

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

    private static boolean settled() {
        return MinecraftClient.getInstance().getOverlay() == null;
    }

    static void run(ClientGameTestContext context) {
        TexturePacks module = ModuleManager.get(TexturePacks.class);
        require(module != null, "Texture Packs was not registered");
        Path dir = context.computeOnClient(MinecraftClient::getResourcePackDir);
        String meta = context.computeOnClient(TexturePacksChecks::packMeta);
        Path red = dir.resolve("maro-test-red.zip"), blue = dir.resolve("maro-test-blue.zip");
        try {
            Files.createDirectories(dir);
            writePack(red, meta, "Red test pack from the Maro tests", 0xFFE5484D);
            writePack(blue, meta, "Blue test pack from the Maro tests", 0xFF3391FC);
        } catch (IOException e) {
            throw new AssertionError("Could not write the test packs", e);
        }

        TexturePacksScreen screen = context.computeOnClient(c -> {
            TexturePacksScreen s = new TexturePacksScreen(null, module);
            c.setScreen(s);
            return s;
        });
        try {
            context.waitTicks(5);
            String listed = context.computeOnClient(c -> screen.packs().stream().map(TexturePacks.Pack::id).toList().toString());
            System.out.println("TEXTURE PACKS listed: " + listed);
            require(listed.contains(RED) && listed.contains(BLUE), "The test packs were not listed: " + listed);
            TexturePacks.Pack redPack = context.computeOnClient(c -> screen.packs().stream().filter(p -> p.id().equals(RED)).findFirst().orElseThrow());
            require(redPack.name().equals("maro-test-red"), "The pack's name kept its .zip or was wrong: " + redPack.name());
            require(redPack.description().startsWith("Red test pack"), "The pack's description was wrong: " + redPack.description());
            require(redPack.fits(), "A pack made for this version was not shown as fitting: " + redPack.fitNote());
            require(await(context, () -> screen.iconReady(RED) && screen.iconReady(BLUE), 200), "The test packs' icons never loaded");

            context.runOnClient(c -> {
                screen.toggle(RED);
                screen.toggle(BLUE);
            });
            List<String> draft = context.computeOnClient(c -> List.copyOf(screen.draft()));
            require(draft.indexOf(BLUE) == 0 && draft.indexOf(RED) == 1, "The newest enabled pack did not go on top: " + draft);
            require(context.computeOnClient(c -> screen.pending()), "Enabling packs left nothing to apply");
            context.waitTicks(3);
            context.takeScreenshot("maro-texture-packs");

            context.runOnClient(c -> screen.apply());
            require(await(context, () -> module.gameHasOn(RED) && module.gameHasOn(BLUE) && settled(), 1200),
                    "Apply did not switch the test packs on");
            List<String> order = context.computeOnClient(c -> module.gameOn());
            System.out.println("TEXTURE PACKS on, top first: " + order);
            require(order.indexOf(BLUE) >= 0 && order.indexOf(BLUE) < order.indexOf(RED), "Blue should be over red: " + order);
            require(context.computeOnClient(c -> module.isEnabled()), "Applying did not turn the module on");
            require(!context.computeOnClient(c -> screen.pending()), "Something was still waiting after Apply");
            context.waitTicks(5);
            context.takeScreenshot("maro-texture-packs-on");

            context.runOnClient(c -> {
                screen.moveUp(RED);
                screen.apply();
            });
            require(await(context, () -> settled() && module.gameOn().indexOf(RED) >= 0 && module.gameOn().indexOf(RED) < module.gameOn().indexOf(BLUE), 1200),
                    "Moving red up did not put it over blue: " + context.computeOnClient(c -> module.gameOn()));
            String saved = context.computeOnClient(c -> module.saveExtra().toString());
            require(saved.contains(RED) && saved.contains(BLUE), "The picks were not saved: " + saved);

            context.runOnClient(c -> module.setEnabled(false));
            require(await(context, () -> settled() && !module.gameHasOn(RED) && !module.gameHasOn(BLUE), 1200),
                    "Turning the module off left the test packs on");
            context.runOnClient(c -> module.setEnabled(true));
            require(await(context, () -> settled() && module.gameHasOn(RED) && module.gameHasOn(BLUE), 1200),
                    "Turning the module back on did not put the picks back");
            require(context.computeOnClient(c -> module.gameOn().indexOf(RED) < module.gameOn().indexOf(BLUE)),
                    "The order was lost going off and on: " + context.computeOnClient(c -> module.gameOn()));

            // Blue switched off in the game's own menu while on: off and on again keeps it off.
            context.runOnClient(c -> {
                ResourcePackManager manager = c.getResourcePackManager();
                List<String> ids = new ArrayList<>(manager.getEnabledIds());
                ids.remove(BLUE);
                manager.setEnabledProfiles(ids);
                c.options.refreshResourcePacks(manager);
            });
            require(await(context, () -> settled() && !module.gameHasOn(BLUE), 1200), "Switching blue off in the game did not take");
            context.runOnClient(c -> module.setEnabled(false));
            require(await(context, () -> settled() && !module.gameHasOn(RED), 1200), "Turning the module off left red on");
            context.runOnClient(c -> module.setEnabled(true));
            require(await(context, () -> settled() && module.gameHasOn(RED), 1200), "Turning the module on again did not put red back");
            context.waitTicks(5);
            require(context.computeOnClient(c -> !module.gameHasOn(BLUE)),
                    "A pack switched off in the game's own menu came back: " + context.computeOnClient(c -> module.gameOn()));
        } finally {
            context.runOnClient(c -> {
                if (c.currentScreen instanceof TexturePacksScreen) c.setScreen(null);
                module.setEnabled(false);
            });
            await(context, () -> settled() && !module.gameHasOn(RED) && !module.gameHasOn(BLUE), 1200);
            context.runOnClient(c -> module.setPickedQuietly(List.of()));
            try {
                Files.deleteIfExists(red);
                Files.deleteIfExists(blue);
            } catch (IOException ignored) {
                // Left in a test folder.
            }
            context.runOnClient(c -> c.getResourcePackManager().scanPacks());
            context.waitTicks(5);
        }
    }

    /** The game's own Programmer Art pack.mcmeta, so the test packs are made for this version. */
    private static String packMeta(MinecraftClient client) {
        ResourcePackProfile art = client.getResourcePackManager().getProfile("programmer_art");
        if (art != null) {
            try (ResourcePack pack = art.createResourcePack()) {
                InputSupplier<InputStream> in = pack.openRoot("pack.mcmeta");
                if (in != null) {
                    try (InputStream stream = in.get()) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                System.out.println("TEXTURE PACKS could not read Programmer Art's pack.mcmeta: " + e);
            }
        }
        return "{\"pack\":{\"pack_format\":34,\"description\":\"\"}}";
    }

    private static void writePack(Path file, String meta, String description, int color) throws IOException {
        JsonObject root = JsonParser.parseString(meta).getAsJsonObject();
        JsonObject pack = root.getAsJsonObject("pack");
        pack.addProperty("description", description);
        root.remove("overlays");
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean light = (x / 4 + y / 4) % 2 == 0;
                icon.setRGB(x, y, light ? color : (color & 0xFF000000) | (color & 0xFEFEFE) >> 1);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(icon, "png", png);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(root.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pack.png"));
            zip.write(png.toByteArray());
            zip.closeEntry();
        }
    }
}
