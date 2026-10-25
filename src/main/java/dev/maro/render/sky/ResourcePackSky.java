package dev.maro.render.sky;

import dev.maro.gui.notification.Notifications;
import dev.maro.textures.Modrinth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** An imported pack's single-layer OptiFine skybox, rendered through Maro's own sky pipeline. */
public final class ResourcePackSky {
    private static final SkyImage IMAGE = new SkyImage();
    private static final Pattern SKY_PROFILE = Pattern.compile("file/maro-packsmc-[a-f0-9-]{36}-[a-f0-9]{12}\\.zip");
    private static String wanted = "", ready = "";
    private static boolean stopped;
    private ResourcePackSky() { }

    /** Called after ordinary pack reloads settle; follows the actual game pack order. */
    public static void tick(MinecraftClient mc) {
        if (stopped || mc.getOverlay() != null || mc.getResourcePackManager() == null) return;
        String chosen = "";
        for (String id : mc.getResourcePackManager().getEnabledIds())
            if (SKY_PROFILE.matcher(id).matches()) chosen = id;
        if (chosen.equals(wanted)) return;
        wanted = chosen;
        if (chosen.isEmpty()) { IMAGE.close(); ready = ""; return; }
        String key = chosen;
        Path file = mc.getResourcePackDir().resolve(chosen.substring(5));
        CompletableFuture.supplyAsync(() -> {
            try { return decode(file); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }, Modrinth.POOL).whenComplete((decoded, error) -> mc.execute(() -> {
            if (stopped || !wanted.equals(key)) return;
            if (error != null) {
                IMAGE.close(); ready = "";
                Notifications.push("Texture Packs", "This sky could not be loaded. Your usual sky is kept.", Notifications.Type.WARNING, 5000);
                return;
            }
            if (decoded == null) { IMAGE.close(); ready = ""; return; }
            try { IMAGE.install(decoded); ready = key; }
            catch (RuntimeException failure) {
                release();
                Notifications.push("Texture Packs", "This sky could not be uploaded. Your usual sky is kept.", Notifications.Type.WARNING, 5000);
            }
        }));
    }

    public static SkyImage.Decoded decode(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            String property = zip.stream().map(e -> e.getName()).filter(n ->
                    (n.startsWith("assets/minecraft/optifine/sky/world0/") || n.startsWith("assets/minecraft/mcpatcher/sky/world0/"))
                            && n.matches(".*/sky[0-9]+\\.properties")).sorted().findFirst().orElse(null);
            if (property == null) return null;
            Properties properties = new Properties();
            try (var in = zip.getInputStream(zip.getEntry(property))) { properties.load(in); }
            String png = resolveSource(property, properties.getProperty("source", "./" + Path.of(property).getFileName().toString().replace(".properties", ".png")));
            var entry = zip.getEntry(png);
            if (entry == null) throw new IOException("Missing sky image");
            Path temporary = Files.createTempFile("maro-sky-", ".png");
            try {
                try (var in = zip.getInputStream(entry)) {
                    byte[] bytes = in.readNBytes(64 * 1024 * 1024 + 1);
                    if (bytes.length > 64 * 1024 * 1024) throw new IOException("Sky image too large");
                    Files.write(temporary, bytes);
                }
                SkyImage.Decoded image = SkyImage.decode(temporary);
                // The properties identify a skybox. A user's ordinary 3:2 photo must still wrap.
                if (Math.abs(image.width() / (double)image.height() - 1.5) < .03)
                    return new SkyImage.Decoded(image.pixels(), image.width(), image.height(), image.top(), image.bottom(), image.horizon(), SkyImage.Fit.ATLAS);
                return image;
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    public static String resolveSource(String property, String source) throws IOException {
        String result;
        if (source.startsWith("./")) result = Path.of(property).getParent().resolve(source.substring(2)).normalize().toString().replace('\\', '/');
        else if (source.startsWith("~/")) result = "assets/minecraft/optifine/" + source.substring(2);
        else if (source.contains(":")) result = "assets/" + source.replace(':', '/');
        else result = "assets/minecraft/" + source;
        if (!result.startsWith("assets/") || result.contains("..") || result.contains("\\") || result.contains(":")) throw new IOException("Invalid sky source");
        return result;
    }

    public static boolean active() {
        if (dev.maro.render.capture.CapturePass.active()) return false;
        var mc = MinecraftClient.getInstance();
        return !wanted.isEmpty() && wanted.equals(ready) && IMAGE.loaded() && mc.world != null
                && mc.world.getRegistryKey().equals(World.OVERWORLD);
    }
    public static SkyImage image() { return IMAGE; }
    public static float[] uniforms(int picture, float pixelAngle) {
        var info = IMAGE.image();
        int top = info.top(), bottom = info.bottom();
        return new float[]{0, picture, picture, 0, 1, pixelAngle, info.fit().ordinal(), 2, 0, 1, 0, 0,
                (top >> 16 & 255) / 255f, (top >> 8 & 255) / 255f, (top & 255) / 255f, 0,
                (bottom >> 16 & 255) / 255f, (bottom >> 8 & 255) / 255f, (bottom & 255) / 255f, 0};
    }
    // Keep wanted so a failed upload is retried only after changing packs, never every frame.
    public static void release() { ready = ""; IMAGE.close(); }
    public static void shutdown() { stopped = true; release(); }
}
