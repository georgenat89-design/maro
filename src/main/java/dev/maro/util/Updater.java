package dev.maro.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.gui.notification.Notifications;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-updater. On launch it asks GitHub for the latest release of this repository; if it is a
 * newer build for the same Minecraft version, the jar is downloaded next to the current one as
 * {@code *.jar.pending} (Fabric ignores that extension). When the game closes a small detached
 * helper waits for the JVM to exit, removes the old jar and renames the new one into place.
 * <p>
 * Nothing is replaced until the download is complete and its size/sha256 match the release, so a
 * failed update never leaves you without a working jar.
 */
public final class Updater {
    private static final String REPO = "georgenat89-design/maro";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Pattern BUILD = Pattern.compile("build[.-](\\d+)");

    private static volatile Path pendingJar;
    private static volatile Path currentJar;
    private static volatile int pendingBuild = -1;

    private Updater() {
    }

    /** Build number of the running jar, or -1 for dev/local builds. */
    public static int currentBuild() {
        Matcher m = BUILD.matcher(Maro.VERSION);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static int pendingBuild() {
        return pendingBuild;
    }

    public static void checkAsync() {
        if (!ClientSettings.autoUpdate.get() || System.getProperty("fabric.client.gametest") != null) return;
        Thread t = new Thread(Updater::check, "maro-updater");
        t.setDaemon(true);
        t.start();
    }

    private static void check() {
        try {
            int current = currentBuild();
            Path jar = ownJar();
            if (current < 0 || jar == null) {
                Maro.LOGGER.info("[updater] skipped (development build)");
                return;
            }
            cleanLeftovers(jar.getParent());

            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> res = http.send(request(API).header("Accept", "application/vnd.github+json").build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                Maro.LOGGER.warn("[updater] GitHub answered {}", res.statusCode());
                return;
            }
            JsonObject release = JsonParser.parseString(res.body()).getAsJsonObject();
            Matcher tag = BUILD.matcher(release.get("tag_name").getAsString());
            if (!tag.find()) return;
            int latest = Integer.parseInt(tag.group(1));
            if (latest <= current) {
                Maro.LOGGER.info("[updater] up to date (build {})", current);
                return;
            }

            String mc = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
            JsonObject asset = findAsset(release.getAsJsonArray("assets"), "-mc" + mc + "-");
            if (asset == null) {
                Maro.LOGGER.info("[updater] build {} has no jar for Minecraft {}", latest, mc);
                return;
            }

            String name = asset.get("name").getAsString();
            long size = asset.get("size").getAsLong();
            String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : null;
            Path target = jar.resolveSibling(name + ".pending");
            Path tmp = jar.resolveSibling(name + ".download");

            Maro.LOGGER.info("[updater] downloading build {} ({})", latest, name);
            HttpResponse<InputStream> dl = http.send(request(asset.get("browser_download_url").getAsString()).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (dl.statusCode() != 200) {
                Maro.LOGGER.warn("[updater] download failed: HTTP {}", dl.statusCode());
                return;
            }
            try (InputStream in = dl.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) != size || (digest != null && !digest.equalsIgnoreCase("sha256:" + sha256(tmp)))) {
                Files.deleteIfExists(tmp);
                Maro.LOGGER.warn("[updater] downloaded file failed verification, ignoring");
                return;
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);

            currentJar = jar;
            pendingJar = target;
            pendingBuild = latest;
            Maro.LOGGER.info("[updater] build {} ready, it will be installed when the game closes", latest);
            MinecraftClient.getInstance().execute(() -> Notifications.push("Update ready",
                    "Build " + latest + " installs when you close the game", Notifications.Type.SUCCESS, 6000));
        } catch (Exception e) {
            Maro.LOGGER.warn("[updater] check failed: {}", e.toString());
        }
    }

    /** Called on shutdown: hands the swap to a helper process that runs after the JVM exits. */
    public static void applyOnExit() {
        Path pending = pendingJar, current = currentJar;
        if (pending == null || current == null || !Files.isRegularFile(pending)) return;
        String finalName = pending.getFileName().toString().replaceFirst("\\.pending$", "");
        Path finalJar = pending.resolveSibling(finalName);
        try {
            ProcessBuilder pb;
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                String script = "ping -n 4 127.0.0.1 >nul"
                        + " & del /f /q \"" + current + "\""
                        + " & move /y \"" + pending + "\" \"" + finalJar + "\"";
                pb = new ProcessBuilder("cmd.exe", "/c", script);
            } else {
                String script = "sleep 3; rm -f '" + current + "'; mv -f '" + pending + "' '" + finalJar + "'";
                pb = new ProcessBuilder("sh", "-c", script);
            }
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            Maro.LOGGER.info("[updater] installing build {} after exit", pendingBuild);
        } catch (IOException e) {
            Maro.LOGGER.warn("[updater] could not schedule the update: {}", e.toString());
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "maro.gg-updater");
    }

    private static JsonObject findAsset(JsonArray assets, String mcTag) {
        if (assets == null) return null;
        for (JsonElement e : assets) {
            JsonObject a = e.getAsJsonObject();
            String n = a.get("name").getAsString();
            if (n.endsWith(".jar") && n.contains(mcTag) && !n.contains("sources") && !n.contains("gametest")) return a;
        }
        return null;
    }

    /** Path of the jar this mod was loaded from, or null when running from a dev environment. */
    private static Path ownJar() {
        ModContainer mod = FabricLoader.getInstance().getModContainer(Maro.MOD_ID).orElse(null);
        if (mod == null || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
        for (Path p : mod.getOrigin().getPaths()) {
            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) return p;
        }
        return null;
    }

    /** Removes half-finished downloads from earlier sessions. */
    private static void cleanLeftovers(Path modsDir) {
        try (var files = Files.list(modsDir)) {
            files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("maro-") && (n.endsWith(".jar.download") || n.endsWith(".jar.pending"));
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
