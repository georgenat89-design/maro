package dev.maro.nathan.audio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.render.SpotifyCardRaster;

/** Reads and controls the Windows media session without blocking Minecraft's render thread. */
public final class SpotifyMedia implements AutoCloseable {
    /** Artwork is decoded off the render thread and uploaded only when the song changes. */
    public record Artwork(String key, int width, int height, byte[] rgba, int tintRgb,
                          SpotifyCardRaster.Raster cardRaster, SpotifyCardRaster.Raster lyricsRaster) {
    }

    public record State(boolean available, String title, String artist, String album, String source,
                        boolean playing, boolean canSeek, long positionMs, long durationMs, long sampledAtMs,
                        String error) {
        public State(boolean available, String title, String artist, String source, boolean playing, boolean canSeek,
                     long positionMs, long durationMs, long sampledAtMs, String error) {
            this(available, title, artist, "", source, playing, canSeek, positionMs, durationMs, sampledAtMs, error);
        }
        public static State waiting() {
            return new State(false, "Waiting for Spotify", "Open Spotify and play something", "", false, false, 0, 0, 0, "");
        }

        public long currentPositionMs() {
            long extra = playing ? Math.max(0, System.currentTimeMillis() - sampledAtMs) : 0;
            return durationMs > 0 ? Math.min(durationMs, positionMs + extra) : positionMs + extra;
        }
    }

    private static final String SCRIPT = "/assets/nameeprotect/spotify/spotify-bridge.ps1";
    private static final String AUDIO_SCRIPT = "/assets/nameeprotect/spotify/spotify-audio.ps1";
    private static final String AUDIO_SOURCE = "/assets/nameeprotect/spotify/spotify-audio.cs";
    private static final float[] SILENT_LEVELS = new float[7];

    private volatile State state = State.waiting();
    private volatile Artwork artwork;
    private String artworkLoadingKey = "";
    private long artworkRetryAt;
    private volatile Process statusProcess;
    private Thread statusReader;
    private volatile String artworkKey = "";
    private boolean artworkPending;
    private final Object artworkLock = new Object();
    private volatile ScheduledExecutorService worker;
    private Path script;
    private Path audioDirectory;
    private volatile Process audioProcess;
    private Thread audioReader;
    private volatile float[] audioLevels = SILENT_LEVELS;
    private volatile long audioSampleAt;
    private record SeekRequest(State track, long positionMs) { }
    private final AtomicReference<SeekRequest> pendingSeek = new AtomicReference<>();
    private final AtomicBoolean seeking = new AtomicBoolean();
    private final Set<Process> calls = ConcurrentHashMap.newKeySet();

    public State state() {
        return state;
    }

    public Artwork artwork() {
        return artwork;
    }

    /** Exact identity shared by metadata, artwork and HUD transitions. */
    public static String artworkKey(State state) {
        return state.source + "\u0000" + state.title + "\u0000" + state.artist + "\u0000" + state.durationMs;
    }

    /** Seven read-only frequency bands from Windows output, low bass through treble. */
    public float[] audioLevels() {
        if (!state.playing() || System.currentTimeMillis() - audioSampleAt > 500) return SILENT_LEVELS;
        return audioLevels;
    }

    /** Open Spotify from a cover click without blocking the game's render thread. */
    public void openSpotify() {
        ScheduledExecutorService active = worker;
        if (active == null) return;
        String source = state.source();
        active.execute(() -> {
            try {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                try {
                    desktop.browse(java.net.URI.create(source.equals("Browser") ? "https://open.spotify.com/" : "spotify:app:home"));
                } catch (Exception ignored) {
                    desktop.browse(java.net.URI.create("https://open.spotify.com/"));
                }
            } catch (Exception e) {
                NameeProtectAddon.LOG.debug("Could not open Spotify: {}", e.toString());
            }
        });
    }

    public void start() {
        if (worker != null) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            state = new State(false, "Windows required", "This HUD reads Windows media sessions", "", false, false, 0, 0, 0, "");
            return;
        }

        try (InputStream resource = SpotifyMedia.class.getResourceAsStream(SCRIPT)) {
            if (resource == null) throw new IllegalStateException("Spotify bridge is missing from the add-on jar");
            script = Files.createTempFile("nameeprotect-spotify-", ".ps1");
            Files.copy(resource, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            script.toFile().deleteOnExit();
        } catch (Exception e) {
            state = new State(false, "Spotify unavailable", e.getMessage(), "", false, false, 0, 0, 0, e.getMessage());
            NameeProtectAddon.LOG.error("Could not start Spotify HUD", e);
            return;
        }

        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nameeprotect-spotify");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledExecutorService active = worker;
        active.execute(() -> startStatus(active));
        startAudio();
    }

    /** Keep one lightweight bridge alive; no new PowerShell process is spawned for each sample. */
    private void startStatus(ScheduledExecutorService active) {
        if (worker != active) return;
        try {
            List<String> command = powershell(script); command.add("status"); command.add("-Watch");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            statusProcess = process;
            if (worker != active) { process.destroyForcibly(); return; }
            statusReader = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String row;
                    while (worker == active && process == statusProcess && (row = reader.readLine()) != null) {
                        if (row.startsWith("{")) {
                            try { acceptStatus(JsonParser.parseString(row).getAsJsonObject(), active); }
                            catch (RuntimeException ignored) { }
                        }
                    }
                } catch (Exception e) {
                    if (worker == active) NameeProtectAddon.LOG.debug("Spotify status stream stopped: {}", e.toString());
                } finally {
                    process.destroyForcibly();
                    if (worker == active && process == statusProcess) {
                        statusProcess = null;
                        try { active.scheduleWithFixedDelay(this::poll, 0, 1000, TimeUnit.MILLISECONDS); }
                        catch (java.util.concurrent.RejectedExecutionException ignored) { }
                    }
                }
            }, "maro-spotify-status");
            statusReader.setDaemon(true); statusReader.start();
        } catch (Exception e) {
            if (worker == active) {
                NameeProtectAddon.LOG.debug("Spotify status stream unavailable: {}", e.toString());
                try { active.scheduleWithFixedDelay(this::poll, 0, 1000, TimeUnit.MILLISECONDS); }
                catch (java.util.concurrent.RejectedExecutionException ignored) { }
            }
        }
    }

    public void control(String action) {
        ScheduledExecutorService active = worker;
        if (active == null) return;
        active.execute(() -> {
            try {
                JsonObject response = call(action);
                if (worker != active) return;
                if (!bool(response, "ok")) {
                    state = new State(state.available, state.title, state.artist, state.album, state.source,
                        state.playing, state.canSeek, state.positionMs, state.durationMs, state.sampledAtMs,
                        "Windows could not control this session");
                    return;
                }
            } catch (Exception e) {
                if (worker != active) return;
                NameeProtectAddon.LOG.warn("Spotify control failed: {}", e.toString());
                state = new State(state.available, state.title, state.artist, state.album, state.source,
                    state.playing, state.canSeek, state.positionMs, state.durationMs, state.sampledAtMs,
                    "Control unavailable");
                return;
            }
            poll();
        });
    }

    /** Coalesces scrubbing requests so dragging cannot queue hundreds of helper processes. */
    public void seekTo(long positionMs) {
        State current = state;
        ScheduledExecutorService active = worker;
        if (active == null || !current.available || !current.canSeek || current.durationMs <= 0) return;
        pendingSeek.set(new SeekRequest(current, Math.max(0, Math.min(current.durationMs, positionMs))));
        if (seeking.compareAndSet(false, true)) active.execute(this::applySeek);
    }

    private void applySeek() {
        ScheduledExecutorService active = worker;
        try {
            SeekRequest request;
            while ((request = pendingSeek.getAndSet(null)) != null && !Thread.currentThread().isInterrupted()) {
                State before = state;
                if (!before.available || !before.canSeek || before.durationMs <= 0 || !sameTrack(before, request.track)) continue;
                JsonObject response = call("seek", null, request);
                if (worker != active) return;
                if (bool(response, "changed") || !bool(response, "available")) {
                    poll();
                    continue;
                }
                if (!bool(response, "ok")) {
                    state = new State(before.available, before.title, before.artist, before.album, before.source,
                        before.playing, before.canSeek, before.currentPositionMs(), before.durationMs,
                        System.currentTimeMillis(), string(response, "error", "This player does not support seeking"));
                    continue;
                }
                // Show accepted seeks immediately; the next regular poll verifies the player's timeline.
                state = new State(before.available, before.title, before.artist, before.album, before.source,
                    before.playing, before.canSeek, number(response, "positionMs"), before.durationMs,
                    System.currentTimeMillis(), "");
            }
        } catch (Exception e) {
            if (worker != active) return;
            NameeProtectAddon.LOG.debug("Spotify seek unavailable: {}", e.toString());
            State current = state;
            state = new State(current.available, current.title, current.artist, current.album, current.source,
                current.playing, current.canSeek, current.positionMs, current.durationMs,
                current.sampledAtMs, "This player could not seek");
        } finally {
            if (worker == active) {
                seeking.set(false);
                if (pendingSeek.get() != null && active != null && !active.isShutdown() && seeking.compareAndSet(false, true))
                    active.execute(this::applySeek);
            }
        }
    }

    private static boolean sameTrack(State first, State second) {
        return first.title.equals(second.title) && first.artist.equals(second.artist)
            && first.source.equals(second.source) && first.durationMs == second.durationMs;
    }

    private void startAudio() {
        try {
            audioDirectory = Files.createTempDirectory("nameeprotect-audio-");
            copyAudioResource(AUDIO_SCRIPT, "spotify-audio.ps1");
            copyAudioResource(AUDIO_SOURCE, "spotify-audio.cs");
            Process process = new ProcessBuilder(powershell(audioDirectory.resolve("spotify-audio.ps1")))
                .redirectErrorStream(true).start();
            audioProcess = process;
            audioReader = new Thread(() -> readAudio(process), "nameeprotect-audio-bands");
            audioReader.setDaemon(true);
            audioReader.start();
        } catch (Exception e) {
            NameeProtectAddon.LOG.debug("Spotify audio bars unavailable: {}", e.toString());
        }
    }

    private void copyAudioResource(String resourcePath, String name) throws Exception {
        try (InputStream input = SpotifyMedia.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IllegalStateException("Audio helper resource is missing");
            Path destination = audioDirectory.resolve(name);
            Files.copy(input, destination);
            destination.toFile().deleteOnExit();
        }
    }

    private void readAudio(Process process) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null && process == audioProcess) {
                if (!line.startsWith("LEVELS ")) continue;
                String[] values = line.substring(7).trim().split("\\s+");
                if (values.length != 7) continue;
                float[] bands = new float[7];
                for (int i = 0; i < bands.length; i++) {
                    float value = Float.parseFloat(values[i]);
                    bands[i] = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
                }
                audioLevels = bands;
                audioSampleAt = System.currentTimeMillis();
            }
        } catch (Exception e) {
            if (process == audioProcess) NameeProtectAddon.LOG.debug("Spotify audio bars stopped: {}", e.toString());
        }
    }

    private void poll() {
        ScheduledExecutorService active = worker;
        if (active == null) return;
        try {
            if (statusProcess != null && statusProcess.isAlive()) return;
            JsonObject response = call("status");
            acceptStatus(response, active);
        } catch (Exception e) {
            synchronized (artworkLock) {
                if (worker != active) return;
                state = new State(false, "Spotify unavailable", "Could not read Windows media", "", false, false, 0, 0, 0, e.getMessage());
            }
        }
    }

    private void acceptStatus(JsonObject response, ScheduledExecutorService active) {
            if (worker != active) return;
            if (!bool(response, "ok")) {
                String error = string(response, "error", "Windows media session unavailable");
                synchronized (artworkLock) {
                    if (worker != active) return;
                    state = new State(false, "Spotify unavailable", error, "", false, false, 0, 0, 0, error);
                }
            } else if (!bool(response, "available")) {
                synchronized (artworkLock) {
                    if (worker != active) return;
                    state = State.waiting();
                    artwork = null;
                    artworkKey = "";
                    artworkPending = false;
                }
            } else {
                State current = new State(true,
                    string(response, "title", "Unknown track"),
                    string(response, "artist", "Unknown artist"),
                    string(response, "album", ""),
                    string(response, "source", "Spotify"),
                    bool(response, "playing"),
                    bool(response, "canSeek"),
                    number(response, "positionMs"),
                    number(response, "durationMs"),
                    sampleTime(response), "");
                String key = artworkKey(current);
                boolean load;
                synchronized (artworkLock) {
                    if (worker != active) return;
                    if (state.available() && current.sampledAtMs() < state.sampledAtMs()) return;
                    state = current;
                    if (!key.equals(artworkKey)) {
                        artworkKey = key;
                        artwork = null;
                        artworkPending = true;
                        artworkRetryAt = 0;
                    }
                    load = artworkPending && !key.equals(artworkLoadingKey) && System.currentTimeMillis() >= artworkRetryAt;
                    if (load) artworkLoadingKey = key;
                }
                if (load) {
                    // Artwork decode and raster preparation stay on the media worker, while status samples continue.
                    try { active.execute(() -> loadArtwork(key, active)); }
                    catch (java.util.concurrent.RejectedExecutionException ignored) { }
                }
            }
    }

    private static long sampleTime(JsonObject response) {
        long now = System.currentTimeMillis(), sample = number(response, "sampledAtMs");
        return sample > now - 20_000 && sample <= now + 1000 ? sample : now;
    }

    private void loadArtwork(String key, ScheduledExecutorService active) {
        Path imageFile = null;
        try {
            if (worker != active) return;
            imageFile = Files.createTempFile("nameeprotect-cover-", ".img");
            JsonObject response = call("art", imageFile);
            if (worker != active || !key.equals(artworkKey) || !bool(response, "ok") || !bool(response, "available")) return;
            String returnedKey = string(response, "source", "") + "\u0000" + string(response, "title", "")
                + "\u0000" + string(response, "artist", "") + "\u0000" + number(response, "durationMs");
            // Keep the request pending when Windows switched tracks during the artwork query.
            // The next normal status poll retries once, without spawning a retry loop.
            if (!key.equals(returnedKey)) return;
            if (!bool(response, "artwork")) {
                synchronized (artworkLock) {
                    if (worker == active && key.equals(artworkKey)) artworkPending = false;
                }
                return;
            }
            byte[] encoded = Files.readAllBytes(imageFile);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
            if (image == null) return;
            Artwork decoded = decodeArtwork(key, image);
            synchronized (artworkLock) {
                // Decode/raster work may finish after disable or after a new worker starts.
                if (worker == active && key.equals(artworkKey) && key.equals(artworkKey(state))) {
                    artwork = decoded;
                    artworkPending = false;
                }
            }
        } catch (Exception e) {
            NameeProtectAddon.LOG.debug("Spotify artwork unavailable: {}", e.toString());
        } finally {
            synchronized (artworkLock) {
                if (worker == active && key.equals(artworkLoadingKey)) {
                    artworkLoadingKey = "";
                    if (artworkPending && key.equals(artworkKey)) artworkRetryAt = System.currentTimeMillis() + 2000;
                }
            }
            if (imageFile != null) {
                try { Files.deleteIfExists(imageFile); }
                catch (Exception ignored) {}
            }
        }
    }

    private static Artwork decodeArtwork(String key, BufferedImage source) {
        final int size = 256;
        final double radius = 44;
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = (source.getWidth() - side) / 2;
        int sourceY = (source.getHeight() - side) / 2;
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = square.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, size, size,
                sourceX, sourceY, sourceX + side, sourceY + side, null);
        } finally {
            graphics.dispose();
        }

        byte[] rgba = new byte[size * size * 4];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = square.getRGB(x, y);
                double dx = Math.max(Math.abs(x + 0.5 - size / 2.0) - (size / 2.0 - radius), 0);
                double dy = Math.max(Math.abs(y + 0.5 - size / 2.0) - (size / 2.0 - radius), 0);
                double coverage = Math.max(0, Math.min(1, radius + 0.5 - Math.hypot(dx, dy)));
                int offset = (y * size + x) * 4;
                rgba[offset] = (byte) (argb >> 16);
                rgba[offset + 1] = (byte) (argb >> 8);
                rgba[offset + 2] = (byte) argb;
                rgba[offset + 3] = (byte) Math.round((argb >>> 24) * coverage);
            }
        }
        int tint = artworkTint(square);
        return new Artwork(key, size, size, rgba, tint, SpotifyCardRaster.render(tint),
            SpotifyCardRaster.render(tint, SpotifyCardRaster.Theme.AlbumColours, SpotifyCardRaster.LYRICS_PANEL_HEIGHT));
    }

    /** Samples colour once on the media worker; neutral covers keep a quiet blue accent. */
    private static int artworkTint(BufferedImage thumbnail) {
        double red = 0;
        double green = 0;
        double blue = 0;
        double total = 0;
        int step = Math.max(1, Math.min(thumbnail.getWidth(), thumbnail.getHeight()) / 32);
        for (int y = step / 2; y < thumbnail.getHeight(); y += step) {
            for (int x = step / 2; x < thumbnail.getWidth(); x += step) {
                int argb = thumbnail.getRGB(x, y);
                int r = argb >> 16 & 255;
                int g = argb >> 8 & 255;
                int b = argb & 255;
                int brightest = Math.max(r, Math.max(g, b));
                int chroma = brightest - Math.min(r, Math.min(g, b));
                if (chroma < 16 || brightest == 0) continue;
                double saturation = (double) chroma / brightest;
                double weight = (argb >>> 24) / 255.0 * saturation * saturation
                    * (0.35 + 0.65 * brightest / 255.0);
                red += r * weight;
                green += g * weight;
                blue += b * weight;
                total += weight;
            }
        }
        final int fallback = 0x8A9FC2;
        if (total < 0.01) return fallback;
        float[] hsb = java.awt.Color.RGBtoHSB((int) Math.round(red / total),
            (int) Math.round(green / total), (int) Math.round(blue / total), null);
        if (hsb[1] < 0.1f) return fallback;
        return java.awt.Color.HSBtoRGB(hsb[0], Math.min(0.6f, Math.max(0.22f, hsb[1])),
            Math.min(0.82f, Math.max(0.65f, hsb[2]))) & 0xFFFFFF;
    }

    private JsonObject call(String action) throws Exception {
        return call(action, null);
    }

    private JsonObject call(String action, Path artworkPath) throws Exception {
        return call(action, artworkPath, null);
    }

    private static List<String> powershell(Path file) {
        String root = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        return new ArrayList<>(List.of(
            Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
            "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
            "-File", file.toString()));
    }

    private JsonObject call(String action, Path artworkPath, SeekRequest seek) throws Exception {
        List<String> command = powershell(script);
        command.add(action);
        if (artworkPath != null) command.add(artworkPath.toString());
        if (seek != null) {
            command.add("-SeekMs");
            command.add(Long.toString(seek.positionMs));
            JsonObject expected = new JsonObject();
            expected.addProperty("title", seek.track.title);
            expected.addProperty("artist", seek.track.artist);
            expected.addProperty("source", seek.track.source);
            expected.addProperty("durationMs", seek.track.durationMs);
            command.add("-ExpectedTrack");
            command.add(Base64.getEncoder().encodeToString(expected.toString().getBytes(StandardCharsets.UTF_8)));
        }

        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        calls.add(process);
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) throw new IllegalStateException("Windows media bridge timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isEmpty()) throw new IllegalStateException("Windows media bridge failed: " + output);
            return JsonParser.parseString(output).getAsJsonObject();
        } finally {
            calls.remove(process);
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static boolean bool(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() && object.get(name).getAsBoolean();
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback;
    }

    private static long number(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsLong() : 0;
    }

    @Override
    public void close() {
        ScheduledExecutorService active;
        synchronized (artworkLock) {
            active = worker;
            worker = null;
            state = State.waiting();
            artwork = null;
            artworkKey = "";
            artworkPending = false;
            artworkLoadingKey = ""; artworkRetryAt = 0;
        }
        Process process = audioProcess;
        audioProcess = null;
        if (process != null) process.destroyForcibly();
        if (audioReader != null) audioReader.interrupt();
        audioReader = null;
        Process status = statusProcess; statusProcess = null;
        if (status != null) status.destroyForcibly();
        if (statusReader != null) statusReader.interrupt();
        statusReader = null;
        audioLevels = SILENT_LEVELS;
        audioSampleAt = 0;
        pendingSeek.set(null);
        seeking.set(false);
        if (active != null) active.shutdownNow();
        for (Process call : calls) call.destroyForcibly();
        if (audioDirectory != null) {
            for (String name : List.of("spotify-audio.ps1", "spotify-audio.cs")) {
                try { Files.deleteIfExists(audioDirectory.resolve(name)); }
                catch (Exception ignored) { }
            }
            try { Files.deleteIfExists(audioDirectory); }
            catch (Exception ignored) { }
            audioDirectory = null;
        }
        if (script != null) {
            try { Files.deleteIfExists(script); }
            catch (Exception ignored) {}
            script = null;
        }
        state = State.waiting();
    }
}
