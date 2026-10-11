package dev.maro.phone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.Maro;
import dev.maro.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One bounded worker handles relay requests and disk writes; rendering only reads snapshots. */
public final class PhoneNetwork implements AutoCloseable {
    public record Playlist(String id, String title, String url) { }
    public record Message(long id, String clientId, String sender, String recipient, String kind,
                          String text, String url, long created, String status, boolean unread) {
        public String peer(String ownNumber) { return sender.equals(ownNumber) ? recipient : sender; }
    }
    public record Snapshot(String endpoint, String number, boolean active, boolean online, String status,
                           List<Message> messages, List<Playlist> playlists, Set<String> blocked) { }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static PhoneNetwork instance;
    public static synchronized PhoneNetwork get() {
        if (instance == null) instance = new PhoneNetwork(ConfigManager.DIR.resolve("phone.json"));
        return instance;
    }
    public static synchronized void shutdown() { if (instance != null) { instance.close(); instance = null; } }
    public static synchronized void tickNotifications() {
        if (instance == null) return;
        var message = instance.notice.getAndSet(null);
        if (message != null) dev.maro.gui.notification.Notifications.push("Maro Phone", "New " + (message.kind().equals("playlist") ? "playlist" : "message") + " from " + message.sender(), dev.maro.gui.notification.Notifications.Type.INFO);
    }

    private static final class Account {
        String secret = "", number = "";
        long cursor;
        List<Message> messages = new ArrayList<>();
        Set<String> blocked = new HashSet<>();
    }
    private static final class Store {
        String endpoint = "http://127.0.0.1:8765";
        boolean active;
        Map<String, Account> accounts = new HashMap<>();
        List<Playlist> playlists = new ArrayList<>();
    }
    private final Path file;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "Maro phone relay"); thread.setDaemon(true); return thread;
    });
    private Store store = new Store();
    private volatile Snapshot snapshot = new Snapshot("http://127.0.0.1:8765", "", false, false, "Connect to the test relay", List.of(), List.of(), Set.of());
    private volatile String playerName = "Player";
    private boolean registered, online, closed;
    private String status = "Connect to the test relay";
    private long nextPoll;
    private String lastSaved = "";
    private final AtomicBoolean reading = new AtomicBoolean();
    private final AtomicReference<Message> notice = new AtomicReference<>();

    public PhoneNetwork(Path file) {
        this.file = file;
        worker.execute(() -> { load(); publish(); });
        worker.scheduleWithFixedDelay(this::poll, 500, 500, TimeUnit.MILLISECONDS);
    }
    public Snapshot snapshot() { return snapshot; }
    public void playerName(String name) { playerName = name; }
    public static String number(String raw) { return raw.replaceAll("[\\s()-]", ""); }
    public static boolean validNumber(String raw) { return number(raw).matches("[2-9][0-9]{9}"); }
    public static String playlistUrl(String raw) {
        String value = raw.trim();
        if (value.matches("spotify:playlist:[A-Za-z0-9]{22}")) value = "https://open.spotify.com/playlist/" + value.substring(17);
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"open.spotify.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().matches("/playlist/[A-Za-z0-9]{22}"))
            throw new IllegalArgumentException("Paste a Spotify playlist link");
        return "https://open.spotify.com" + uri.getPath();
    }
    public static String relayUrl(String raw) {
        URI uri = URI.create(raw.trim());
        String host = uri.getHost();
        boolean local = host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                || host.matches("10\\.(\\d{1,3}\\.){2}\\d{1,3}") || host.matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.\\d{1,3}\\.\\d{1,3}"));
        if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || !("https".equalsIgnoreCase(uri.getScheme()) || local && "http".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("Use a local test relay URL or an HTTPS domain");
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority();
    }
    public void connect(String endpoint) {
        String normalized = relayUrl(endpoint);
        worker.execute(() -> {
            store.endpoint = normalized; store.active = true; registered = false; nextPoll = 0;
            account(); status = "Connecting…"; save(); publish(); poll();
        });
    }
    public void disconnect() { worker.execute(() -> { store.active = false; online = registered = false; status = "Disconnected"; save(); publish(); }); }
    private Account account() {
        return store.accounts.computeIfAbsent(store.endpoint, ignored -> {
            var a = new Account(); byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
            a.secret = Base64.getUrlEncoder().withoutPadding().encodeToString(key); return a;
        });
    }
    public void savePlaylist(String title, String url) {
        String normalized = playlistUrl(url), label = title.strip();
        if (label.isEmpty() || label.length() > 60) throw new IllegalArgumentException("Give the playlist a name (up to 60 characters)");
        worker.execute(() -> {
            store.playlists.removeIf(p -> p.url().equals(normalized));
            store.playlists.addFirst(new Playlist(UUID.randomUUID().toString(), label, normalized));
            if (store.playlists.size() > 64) store.playlists.removeLast(); save(); publish();
        });
    }
    public void removePlaylist(String id) { worker.execute(() -> { store.playlists.removeIf(p -> p.id().equals(id)); save(); publish(); }); }
    public void send(String to, String text) { enqueue(to, "text", text.strip(), ""); }
    public void share(String to, Playlist playlist) { enqueue(to, "playlist", playlist.title(), playlistUrl(playlist.url())); }
    private void enqueue(String to, String kind, String text, String url) {
        String target = number(to);
        if (!validNumber(target)) throw new IllegalArgumentException("Enter a 10-digit phone number");
        if (text.isBlank() || text.length() > 1000) throw new IllegalArgumentException("Enter a message (up to 1000 characters)");
        if (!snapshot.active() || snapshot.number().isEmpty()) throw new IllegalArgumentException("Connect your phone first");
        worker.execute(() -> {
            var a = account();
            if (a.messages.stream().filter(m -> m.status().equals("queued")).count() >= 50) { status = "Outbox full; reconnect before sending more"; publish(); return; }
            a.messages.add(new Message(0, UUID.randomUUID().toString(), a.number, target, kind, text, url, System.currentTimeMillis(), "queued", false));
            trim(a); save(); publish(); if (online) nextPoll = 0;
        });
    }
    public void retry(String clientId) {
        worker.execute(() -> {
            var a = account();
            a.messages.replaceAll(m -> m.clientId().equals(clientId) && m.status().startsWith("Failed")
                    ? new Message(0, m.clientId(), m.sender(), m.recipient(), m.kind(), m.text(), m.url(), m.created(), "queued", false) : m);
            save(); publish(); nextPoll = 0; poll();
        });
    }
    public void read(String peer) {
        if (snapshot.messages().stream().noneMatch(m -> m.unread() && m.peer(snapshot.number()).equals(peer)) || !reading.compareAndSet(false, true)) return;
        worker.execute(() -> {
            var a = account(); a.messages.replaceAll(m -> m.unread() && m.peer(a.number).equals(peer)
                    ? new Message(m.id(), m.clientId(), m.sender(), m.recipient(), m.kind(), m.text(), m.url(), m.created(), m.status(), false) : m);
            save(); publish(); reading.set(false);
        });
    }
    public void block(String peer, boolean blocked) {
        if (!validNumber(peer)) throw new IllegalArgumentException("Enter a 10-digit phone number");
        worker.execute(() -> {
            try {
                var body = new JsonObject(); body.addProperty("number", peer); body.addProperty("blocked", blocked);
                request("POST", "/v1/block", body, account().secret);
                if (blocked) account().blocked.add(peer); else account().blocked.remove(peer);
                status = blocked ? "Number blocked" : "Number unblocked"; save();
            } catch (Exception error) { status = "Could not update blocked number; reconnect and retry"; }
            publish();
        });
    }
    private void poll() {
        if (closed || !store.active || System.nanoTime() < nextPoll) return;
        nextPoll = System.nanoTime() + 2_000_000_000L;
        var a = account();
        try {
            if (!registered) {
                var body = new JsonObject(); body.addProperty("secret", a.secret); body.addProperty("name", playerName);
                String assigned = request("POST", "/v1/register", body, "").get("number").getAsString();
                if (!validNumber(assigned)) throw new IOException("Invalid relay response");
                if (!a.number.isEmpty() && !a.number.equals(assigned)) { a.cursor = 0; a.messages.clear(); a.blocked.clear(); }
                a.number = assigned; registered = true; save();
            }
            int sent = 0;
            for (var m : List.copyOf(a.messages)) {
                if (!m.status().equals("queued") || sent++ >= 5) continue;
                var body = new JsonObject(); body.addProperty("client_id", m.clientId()); body.addProperty("to", m.recipient());
                body.addProperty("kind", m.kind()); body.addProperty("text", m.text()); body.addProperty("url", m.url());
                try { merge(a, message(request("POST", "/v1/messages", body, a.secret).getAsJsonObject("message"), false)); }
                catch (RelayError error) {
                    if (error.code >= 500 || error.code == 429 || error.code == 401) throw error;
                    int index = a.messages.indexOf(m);
                    if (index >= 0) a.messages.set(index, new Message(0, m.clientId(), m.sender(), m.recipient(), m.kind(), m.text(), m.url(), m.created(), "Failed: " + error.getMessage(), false));
                }
            }
            var response = request("GET", "/v1/messages?after=" + a.cursor, null, a.secret);
            for (var item : response.getAsJsonArray("messages")) {
                var m = message(item.getAsJsonObject(), true);
                if (!a.blocked.contains(m.sender())) {
                    boolean fresh = !m.sender().equals(a.number) && a.messages.stream().noneMatch(old -> old.sender().equals(m.sender()) && old.clientId().equals(m.clientId()));
                    merge(a, m); if (fresh) notice.set(m);
                }
            }
            a.cursor = response.get("cursor").getAsLong(); online = true; status = "Connected to test relay";
            trim(a); save();
        } catch (Exception error) {
            if (error instanceof RelayError relay && relay.code == 401) registered = false;
            online = false; status = error instanceof RelayError ? error.getMessage() : "Relay offline · messages stay queued";
            nextPoll = System.nanoTime() + 5_000_000_000L;
        }
        publish();
    }
    private static Message message(JsonObject value, boolean unread) {
        return new Message(value.get("id").getAsLong(), value.get("client_id").getAsString(), value.get("sender").getAsString(),
                value.get("recipient").getAsString(), value.get("kind").getAsString(), value.get("text").getAsString(),
                value.get("url").getAsString(), value.get("created").getAsLong(), "Sent", unread);
    }
    private static void merge(Account a, Message message) {
        for (int i = 0; i < a.messages.size(); i++) {
            var old = a.messages.get(i);
            if (old.sender().equals(message.sender()) && old.clientId().equals(message.clientId())) {
                a.messages.set(i, new Message(message.id(), message.clientId(), message.sender(), message.recipient(), message.kind(), message.text(), message.url(), message.created(), message.status(), old.unread()));
                return;
            }
        }
        a.messages.add(new Message(message.id(), message.clientId(), message.sender(), message.recipient(), message.kind(), message.text(), message.url(), message.created(), message.status(), message.unread() && !message.sender().equals(a.number)));
    }
    private static void trim(Account a) {
        while (a.messages.size() > 500) {
            int remove = -1;
            for (int i = 0; i < a.messages.size(); i++) if (!a.messages.get(i).status().equals("queued")) { remove = i; break; }
            if (remove < 0) break;
            a.messages.remove(remove);
        }
    }
    private static final class RelayError extends IOException {
        final int code;
        RelayError(int code, String message) { super(message); this.code = code; }
    }
    private JsonObject request(String method, String path, JsonObject body, String secret) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(store.endpoint + path)).timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json");
        if (!secret.isEmpty()) builder.header("Authorization", "Bearer " + secret);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            byte[] bytes = stream.readNBytes(262145);
            if (bytes.length > 262144) throw new IOException("Relay response too large");
            var value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (response.statusCode() != 200) throw new RelayError(response.statusCode(), value.has("error") ? value.get("error").getAsString() : "Relay request failed");
            return value;
        }
    }
    private void publish() {
        var a = store.accounts.get(store.endpoint);
        snapshot = new Snapshot(store.endpoint, a == null ? "" : a.number, store.active, online, status,
                a == null ? List.of() : List.copyOf(a.messages), List.copyOf(store.playlists), a == null ? Set.of() : Set.copyOf(a.blocked));
    }
    private void load() {
        if (!Files.exists(file)) return;
        try {
            Store loaded = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
            if (loaded == null || loaded.accounts == null || loaded.playlists == null) throw new IOException("Invalid phone store");
            loaded.endpoint = relayUrl(loaded.endpoint);
            for (var a : loaded.accounts.values()) {
                if (a.secret == null || !a.secret.matches("[A-Za-z0-9_-]{43}") || a.messages == null || a.blocked == null) throw new IOException("Invalid phone identity");
                trim(a);
            }
            store = loaded;
        } catch (Exception error) {
            status = "Phone data could not load; backup preserved"; Maro.LOGGER.warn("Could not load phone data", error);
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".backup-" + System.currentTimeMillis())); }
            catch (IOException ignored) { }
        }
    }
    private void save() {
        try {
            String data = JSON.toJson(store);
            if (data.equals(lastSaved)) return;
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temp, data, StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            lastSaved = data;
        } catch (IOException error) { Maro.LOGGER.warn("Could not save phone data", error); status = "Phone changes could not be saved"; }
    }
    @Override public void close() {
        worker.execute(() -> { closed = true; save(); http.close(); });
        worker.shutdown();
    }
}
