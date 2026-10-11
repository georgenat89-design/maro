package dev.maro.textures;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleConsumer;

/**
 * The bits of the Modrinth API Better Textures needs: search resource packs, find the file of a
 * pack's best version for this Minecraft, and fetch it. All of it off the game thread.
 */
public final class Modrinth {
    private Modrinth() {
    }

    private static final String API = "https://api.modrinth.com/v2";
    private static final String AGENT = "georgenat89-design/maro/1.0.0 (Minecraft resource pack browser)";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Downloads and lookups, a few at once, on daemon threads so they never hold the game open. */
    public static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Maro Modrinth");
        t.setDaemon(true);
        return t;
    });

    /** One pack from a search. */
    public record Pack(String id, String slug, String title, String description, String author, long downloads,
                       String iconUrl, int color, List<String> gameVersions, List<String> categories) {
        /** Whether a version of it is listed for the Minecraft being played. */
        public boolean supportsThisVersion() {
            return gameVersions.contains(gameVersion());
        }

        /** The newest Minecraft it lists, "1.21.4", for a pack not made for this one. */
        public String newestVersion() {
            return gameVersions.isEmpty() ? "?" : gameVersions.getLast();
        }
    }

    /** The file of one version of a pack. */
    public record Version(String id, String name, String versionNumber, List<String> gameVersions, String url,
                          String filename, String sha1, long size) {
    }

    private static String gameVersion;

    /** "1.21.11": the Minecraft being played, as Modrinth names versions. */
    public static String gameVersion() {
        if (gameVersion == null) {
            gameVersion = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
        }
        return gameVersion;
    }

    /** Packs matching the words, most relevant first; with no words, the most downloaded. */
    public static CompletableFuture<List<Pack>> search(String query, int offset, int limit) {
        String q = query == null ? "" : query.trim();
        String url = API + "/search?limit=" + limit + "&offset=" + offset
                + "&index=" + (q.isEmpty() ? "downloads" : "relevance")
                + "&facets=" + encode("[[\"project_type:resourcepack\"]]")
                + (q.isEmpty() ? "" : "&query=" + encode(q));
        return getJson(url).thenApply(json -> {
            List<Pack> packs = new ArrayList<>();
            for (JsonElement e : json.getAsJsonObject().getAsJsonArray("hits")) {
                JsonObject hit = e.getAsJsonObject();
                packs.add(new Pack(
                        string(hit, "project_id"), string(hit, "slug"), string(hit, "title"), string(hit, "description"),
                        string(hit, "author"), hit.has("downloads") ? hit.get("downloads").getAsLong() : 0,
                        string(hit, "icon_url"), hit.has("color") && !hit.get("color").isJsonNull() ? hit.get("color").getAsInt() : 0x3391FC,
                        strings(hit.getAsJsonArray("versions")), strings(hit.getAsJsonArray("display_categories"))));
            }
            return packs;
        });
    }

    /**
     * The version to use: the newest one listed for this Minecraft, or if there is none, the newest
     * there is (an older pack still mostly works; the game marks it as made for another version).
     */
    public static CompletableFuture<Version> bestVersion(String projectId) {
        String exact = API + "/project/" + projectId + "/version?game_versions=" + encode("[\"" + gameVersion() + "\"]");
        return getJson(exact).thenCompose(json -> {
            Version v = first(json.getAsJsonArray());
            if (v != null) return CompletableFuture.completedFuture(v);
            return getJson(API + "/project/" + projectId + "/version").thenApply(all -> {
                Version any = first(all.getAsJsonArray());
                if (any == null) throw new IllegalStateException("This pack has no files to download");
                return any;
            });
        });
    }

    private static Version first(JsonArray versions) {
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            JsonArray files = v.getAsJsonArray("files");
            JsonObject file = null;
            for (JsonElement f : files) {
                JsonObject candidate = f.getAsJsonObject();
                if (file == null || candidate.has("primary") && candidate.get("primary").getAsBoolean()) file = candidate;
            }
            if (file == null) continue;
            JsonObject hashes = file.getAsJsonObject("hashes");
            return new Version(string(v, "id"), string(v, "name"), string(v, "version_number"),
                    strings(v.getAsJsonArray("game_versions")), string(file, "url"), string(file, "filename"),
                    hashes != null ? string(hashes, "sha1") : "", file.has("size") ? file.get("size").getAsLong() : 0);
        }
        return null;
    }

    /**
     * Downloads the version's file into {@code dir} under {@code name}, checking its SHA-1, and tells
     * {@code progress} how far along it is (0 to 1). A file already there with the right hash is kept.
     */
    public static Path download(Version version, Path dir, String name, DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (Files.isRegularFile(target) && !version.sha1().isEmpty() && version.sha1().equalsIgnoreCase(sha1(target))) {
            progress.accept(1);
            return target;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(version.url()))
                .header("User-Agent", AGENT).timeout(Duration.ofMinutes(10)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("Modrinth answered " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(version.size());
        Path part = dir.resolve(name + ".part");
        MessageDigest digest = sha1Digest();
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                digest.update(buffer, 0, n);
                done += n;
                if (total > 0) progress.accept(Math.min(1.0, (double) done / total));
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        String got = HexFormat.of().formatHex(digest.digest());
        if (!version.sha1().isEmpty() && !version.sha1().equalsIgnoreCase(got)) {
            Files.deleteIfExists(part);
            throw new IOException("The download was damaged (hash did not match); try again");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        progress.accept(1);
        return target;
    }

    /** The raw bytes at a URL: pack icons. */
    public static CompletableFuture<byte[]> bytes(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT).timeout(Duration.ofSeconds(20)).GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + r.statusCode());
            return r.body();
        });
    }

    private static CompletableFuture<JsonElement> getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT)
                .timeout(Duration.ofSeconds(20)).GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
            if (r.statusCode() == 429) throw new IllegalStateException("Modrinth is busy, try again in a minute");
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("Modrinth answered " + r.statusCode());
            return JsonParser.parseString(r.body());
        });
    }

    static String sha1(Path file) throws IOException {
        MessageDigest digest = sha1Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    /** A short readable count: 950, 12.4K, 3.1M. */
    public static String count(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format(java.util.Locale.ROOT, n < 10_000 ? "%.1fK" : "%.0fK", n / 1000.0);
        return String.format(java.util.Locale.ROOT, n < 10_000_000 ? "%.1fM" : "%.0fM", n / 1_000_000.0);
    }
}
