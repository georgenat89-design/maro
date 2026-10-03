package dev.maro.nathan.audio;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Bounded, asynchronous LRCLIB lookup. A late response never replaces another song's lyrics. */
public final class SpotifyLyrics implements AutoCloseable {
    public enum Status { Waiting, Loading, Synced, Plain, Instrumental, Missing, Unavailable }
    public record Track(String title, String artist, String album, long durationMs) {
        public Track { title = title == null ? "" : title; artist = artist == null ? "" : artist; album = album == null ? "" : album; }
        public String key() { return title + '\0' + artist + '\0' + album + '\0' + Math.round(durationMs / 1000.0); }
        public boolean valid() { return !title.isBlank() && !artist.isBlank() && !artist.equalsIgnoreCase("Unknown artist"); }
        public static Track of(SpotifyMedia.State state) { return new Track(state.title(), state.artist(), state.album(), state.durationMs()); }
    }
    public record Word(long atMs, long endMs, String text, int from, int to) { }
    public record Line(long atMs, long endMs, String text, List<Word> words) {
        public Line { words = List.copyOf(words); }
        public Line(long atMs, String text) { this(atMs, -1, text, List.of()); }
    }
    public record Frame(String current, String next, int index) { }
    public record WordFrame(Frame frame, List<Word> words, int active, double progress, boolean estimated) {
        public String currentWord() { return active < 0 ? "♪" : words.get(active).text().strip(); }
        public boolean timed() { return !words.isEmpty(); }
    }
    public record Result(Status status, List<Line> lines, List<String> plain, long retryAfterMs) {
        public Result { lines = List.copyOf(lines); plain = List.copyOf(plain); }
        public static Result status(Status status) { return new Result(status, List.of(), List.of(), 0); }
    }
    public record Snapshot(String key, Result result) {
        public WordFrame karaoke(long positionMs, int offsetMs, int plainIndex, long durationMs, boolean estimate) {
            Frame frame = frame(positionMs, offsetMs, plainIndex);
            if (result.status() != Status.Synced || frame.index() < 0 || frame.current().equals("♪"))
                return new WordFrame(frame, List.of(), -1, 0, false);
            Line line = result.lines().get(frame.index());
            long end = line.endMs() >= line.atMs() ? line.endMs()
                : frame.index() + 1 < result.lines().size() ? result.lines().get(frame.index() + 1).atMs() : durationMs;
            List<Word> words = line.words(); boolean estimated = false;
            if (words.isEmpty() && estimate && end > line.atMs()) {
                words = estimateWords(line, Math.min(end, line.atMs() + 12_000)); estimated = true;
            }
            long time = Math.max(0, positionMs) + offsetMs;
            int active = -1; double progress = 0;
            for (int i = 0; i < words.size(); i++) {
                Word word = words.get(i);
                long until = word.endMs() >= word.atMs() ? word.endMs()
                    : i + 1 < words.size() ? words.get(i + 1).atMs() : end;
                if (time >= word.atMs() && time < until) {
                    active = i; progress = Math.max(0, Math.min(1, (double) (time - word.atMs()) / Math.max(1, until - word.atMs())));
                }
            }
            return new WordFrame(frame, words, active, progress, estimated);
        }
        public Frame frame(long positionMs, int offsetMs, int plainIndex) {
            if (result.status() == Status.Synced) {
                var lines = result.lines();
                long time = Math.max(0, positionMs) + offsetMs;
                int low = 0, high = lines.size() - 1, index = -1;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (lines.get(mid).atMs() <= time) { index = mid; low = mid + 1; } else high = mid - 1;
                }
                String current = index < 0 || lines.get(index).text().isBlank() ? "♪" : lines.get(index).text();
                if (index >= 0 && lines.get(index).endMs() >= 0 && time >= lines.get(index).endMs()) current = "♪";
                String next = index + 1 < lines.size() ? lines.get(index + 1).text() : "";
                return new Frame(current, next, index);
            }
            if (result.status() == Status.Plain && !result.plain().isEmpty()) {
                int index = Math.max(0, Math.min(result.plain().size() - 1, plainIndex));
                return new Frame(result.plain().get(index), index + 1 < result.plain().size() ? result.plain().get(index + 1) : "", index);
            }
            return switch (result.status()) {
                case Loading -> new Frame("Finding lyrics…", "", -1);
                case Instrumental -> new Frame("Instrumental", "", -1);
                case Missing -> new Frame("No lyrics found", "", -1);
                case Unavailable -> new Frame("Lyrics unavailable", "Will retry shortly", -1);
                default -> new Frame("Lyrics follow your song", "", -1);
            };
        }
    }
    private static List<Word> estimateWords(Line line, long end) {
        EstimatedPlan cached = ESTIMATED_PLAN.get();
        if (cached != null && cached.line() == line && cached.end() == end) return cached.words();
        Matcher matcher = WORDS.matcher(line.text());
        List<int[]> spans = new ArrayList<>(); double total = 0;
        while (matcher.find()) { spans.add(new int[]{matcher.start(), matcher.end()}); total += Math.sqrt(matcher.group().strip().codePointCount(0, matcher.group().strip().length()) + 1); }
        List<Word> words = new ArrayList<>(); double consumed = 0;
        for (int[] span : spans) {
            String text = line.text().substring(span[0], span[1]);
            double weight = Math.sqrt(text.strip().codePointCount(0, text.strip().length()) + 1);
            long at = line.atMs() + Math.round((end - line.atMs()) * consumed / total);
            consumed += weight;
            words.add(new Word(at, line.atMs() + Math.round((end - line.atMs()) * consumed / total), text, span[0], span[1]));
        }
        List<Word> plan = List.copyOf(words);
        ESTIMATED_PLAN.set(new EstimatedPlan(line, end, plan));
        return plan;
    }
    private static final Pattern WORDS = Pattern.compile("\\S+\\s*");
    private record EstimatedPlan(Line line, long end, List<Word> words) { }
    private static final ThreadLocal<EstimatedPlan> ESTIMATED_PLAN = new ThreadLocal<>();
    @FunctionalInterface public interface Lookup { Result find(Track track) throws Exception; }
    private record Cached(Result result, long expiresAt) { }
    private final Lookup lookup;
    private final Map<String, Cached> cache = new LinkedHashMap<>(64, .75f, true);
    private ExecutorService worker;
    private Track pending;
    private String wanted = "";
    private boolean running;
    private long generation;
    private volatile Snapshot snapshot = new Snapshot("", Result.status(Status.Waiting));

    public SpotifyLyrics() { this(new HttpLookup()); }
    public SpotifyLyrics(Lookup lookup) { this.lookup = lookup; }
    public Snapshot snapshot() { return snapshot; }
    public synchronized void update(SpotifyMedia.State state) {
        Track track = Track.of(state);
        if (!state.available() || !track.valid()) {
            wanted = ""; pending = null;
            snapshot = new Snapshot("", Result.status(Status.Waiting));
            return;
        }
        String key = track.key();
        Cached known = cache.get(key);
        if (known != null && known.expiresAt() > System.currentTimeMillis()) {
            wanted = key; snapshot = new Snapshot(key, known.result()); pending = null; return;
        }
        if (key.equals(wanted) && snapshot.result().status() == Status.Loading) return;
        wanted = key; pending = track;
        snapshot = new Snapshot(key, Result.status(Status.Loading));
        if (worker == null) worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "maro-spotify-lyrics"); thread.setDaemon(true); return thread;
        });
        if (!running) { running = true; long epoch = generation; worker.execute(() -> drain(epoch)); }
    }
    private void drain(long epoch) {
        while (!Thread.currentThread().isInterrupted()) {
            Track track;
            synchronized (this) {
                if (generation != epoch) return;
                track = pending; pending = null;
                if (track == null) { running = false; return; }
            }
            Result result;
            try { result = lookup.find(track); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); result = Result.status(Status.Unavailable); }
            catch (Exception e) { result = Result.status(Status.Unavailable); }
            if (result == null) result = Result.status(Status.Missing);
            long ttl = result.status() == Status.Unavailable ? Math.max(60_000, result.retryAfterMs())
                : result.status() == Status.Missing ? 15 * 60_000 : 24 * 60 * 60_000L;
            synchronized (this) {
                if (generation != epoch) return;
                cache.put(track.key(), new Cached(result, System.currentTimeMillis() + ttl));
                while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
                if (wanted.equals(track.key())) snapshot = new Snapshot(track.key(), result);
            }
        }
        synchronized (this) { if (generation == epoch) running = false; }
    }
    @Override public synchronized void close() {
        generation++; wanted = ""; pending = null; running = false;
        snapshot = new Snapshot("", Result.status(Status.Waiting));
        if (worker != null) worker.shutdownNow();
        worker = null;
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    public static List<Line> parseLrc(String lrc) {
        if (lrc == null || lrc.length() > 262_144) return List.of();
        long offset = 0;
        var offsetMatch = OFFSET.matcher(lrc);
        if (offsetMatch.find()) { try { offset = Long.parseLong(offsetMatch.group(1)); } catch (NumberFormatException ignored) {} }
        offset = Math.max(-60_000, Math.min(60_000, offset));
        var lines = new TreeMap<Long, String>();
        for (String row : lrc.split("\\R")) {
            Matcher tags = TIME.matcher(row);
            List<Long> stamps = new ArrayList<>(); int end = 0;
            while (tags.find() && stamps.size() < 64) {
                int seconds = Integer.parseInt(tags.group(2));
                if (seconds >= 60) continue;
                String fraction = tags.group(3);
                long ms = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
                stamps.add(Integer.parseInt(tags.group(1)) * 60_000L + seconds * 1000L + ms + offset);
                end = tags.end();
            }
            if (stamps.isEmpty()) continue;
            String text = clean(row.substring(end));
            for (long stamp : stamps) {
                if (lines.size() >= 5000) break;
                long time = Math.max(0, stamp);
                lines.merge(time, text, (a, b) -> a.isBlank() ? b : b.isBlank() || a.equals(b) ? a : a + " / " + b);
            }
        }
        return lines.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue())).toList();
    }
    private static String clean(String value) {
        String text = value.replaceAll("[\\p{Cc}&&[^\\t]]", "").replace('\t', ' ').strip();
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
    private static String text(JsonObject json, String camel, String snake) {
        JsonElement value = json.has(camel) ? json.get(camel) : json.get(snake);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static String normal(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    /** Reject a different remix, artist, or duration rather than displaying the wrong timestamps. */
    public static boolean matches(Track track, JsonObject record) {
        if (!normal(track.title()).equals(normal(text(record, "trackName", "track_name")))
            || !normal(track.artist()).equals(normal(text(record, "artistName", "artist_name")))) return false;
        if (track.durationMs() > 0) {
            try {
                double seconds = record.get("duration").getAsDouble();
                if (!Double.isFinite(seconds) || Math.abs(seconds * 1000 - track.durationMs()) > 3000) return false;
            } catch (RuntimeException e) { return false; }
        }
        return true;
    }
    public static Result decode(JsonObject record) {
        if (record.has("instrumental") && !record.get("instrumental").isJsonNull() && record.get("instrumental").getAsBoolean())
            return Result.status(Status.Instrumental);
        List<Line> rich = parseLyricsfile(text(record, "lyricsfile", "lyricsfile"));
        if (rich.stream().anyMatch(line -> !line.text().isBlank())) return new Result(Status.Synced, rich, List.of(), 0);
        List<Line> synced = parseLrc(text(record, "syncedLyrics", "synced_lyrics"));
        if (synced.stream().anyMatch(line -> !line.text().isBlank())) return new Result(Status.Synced, synced, List.of(), 0);
        String plain = text(record, "plainLyrics", "plain_lyrics");
        if (plain.length() > 262_144) return Result.status(Status.Missing);
        List<String> rows = plain.lines().map(SpotifyLyrics::clean).filter(s -> !s.isBlank()).limit(5000).toList();
        return rows.isEmpty() ? Result.status(Status.Missing) : new Result(Status.Plain, List.of(), rows, 0);
    }

    /** Lyricsfile 1.0 uses absolute milliseconds and preserves whitespace inside word segments. */
    public static List<Line> parseLyricsfile(String yaml) {
        if (yaml.isBlank() || yaml.length() > 262_144) return List.of();
        try {
            LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0); options.setNestingDepthLimit(16); options.setCodePointLimit(262_144);
            Object loaded = new Yaml(new SafeConstructor(options)).load(yaml);
            if (!(loaded instanceof Map<?, ?> root) || !"1.0".equals(String.valueOf(root.get("version")))
                || !(root.get("lines") instanceof List<?> rows)) return List.of();
            List<Line> lines = new ArrayList<>();
            for (Object row : rows) {
                if (lines.size() >= 5000) break;
                if (!(row instanceof Map<?, ?> line)) continue;
                long at = millis(line.get("start_ms")), end = millis(line.get("end_ms"));
                if (at < 0 || end >= 0 && end < at) continue;
                String fallback = line.get("text") instanceof String value ? clean(value) : "";
                List<Word> words = new ArrayList<>(); StringBuilder joined = new StringBuilder(); long previous = -1;
                if (line.get("words") instanceof List<?> segments && segments.size() <= 256) {
                    for (Object segment : segments) {
                        if (!(segment instanceof Map<?, ?> word) || !(word.get("text") instanceof String value)) { words.clear(); break; }
                        long start = millis(word.get("start_ms")), finish = millis(word.get("end_ms"));
                        if (start < at || start < previous || finish >= 0 && finish < start || end >= 0 && (start > end || finish > end)) { words.clear(); break; }
                        String text = value.replaceAll("[\\p{Cc}&&[^\\t]]", "").replace('\t', ' ');
                        if (joined.length() + text.length() > 1000) { words.clear(); break; }
                        int from = joined.length(); joined.append(text);
                        words.add(new Word(start, finish, text, from, joined.length())); previous = start;
                    }
                }
                String text = words.isEmpty() ? fallback : joined.toString();
                if (!fallback.isEmpty() && !words.isEmpty() && !fallback.equals(text.strip())) { words.clear(); text = fallback; }
                lines.add(new Line(at, end, text, words));
            }
            lines.sort(Comparator.comparingLong(Line::atMs));
            return List.copyOf(lines);
        } catch (RuntimeException e) { return List.of(); }
    }
    private static long millis(Object value) {
        if (!(value instanceof Number number)) return -1;
        double time = number.doubleValue();
        return Double.isFinite(time) && time >= 0 && time <= 86_400_000 && time == Math.floor(time) ? number.longValue() : -1;
    }

    /** Uses only public read endpoints. Requests run sequentially with timeout and server backoff. */
    public static final class HttpLookup implements Lookup {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        private long blockedUntil, lastRequest;
        private record Response(int status, JsonElement json, long retryMs) { }
        @Override public Result find(Track track) throws Exception {
            long now = System.currentTimeMillis();
            if (now < blockedUntil) return new Result(Status.Unavailable, List.of(), List.of(), blockedUntil - now);
            String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist());
            String exact = query;
            if (!track.album().isBlank()) exact += "&album_name=" + encode(track.album());
            if (track.durationMs() >= 1000 && track.durationMs() <= 3_600_000) exact += "&duration=" + track.durationMs() / 1000.0;
            Response response = get("get?" + exact);
            Result best = Result.status(Status.Missing); int score = -1;
            if (response.status() == 200 && response.json().isJsonObject() && matches(track, response.json().getAsJsonObject())) {
                Result result = decode(response.json().getAsJsonObject());
                if (result.status() == Status.Instrumental || result.lines().stream().anyMatch(line -> !line.words().isEmpty())) return result;
                best = result; score = rank(result, track, response.json().getAsJsonObject());
            } else if (response.status() != 200 && response.status() != 404) return unavailable(response);
            response = get("search?" + query);
            if (response.status() != 200) return best.status() == Status.Missing ? unavailable(response) : best;
            if (!response.json().isJsonArray()) return Result.status(Status.Unavailable);
            for (JsonElement candidate : response.json().getAsJsonArray()) {
                if (!candidate.isJsonObject() || !matches(track, candidate.getAsJsonObject())) continue;
                Result result = decode(candidate.getAsJsonObject());
                int rank = rank(result, track, candidate.getAsJsonObject());
                if (rank > score) { score = rank; best = result; }
            }
            return best;
        }
        private static int rank(Result result, Track track, JsonObject record) {
            int score = result.status() == Status.Synced ? 400 + Math.min(60, result.lines().size())
                : result.status() == Status.Plain ? 200 + Math.min(60, result.plain().size())
                : result.status() == Status.Instrumental ? 100 : 0;
            if (result.lines().stream().anyMatch(line -> !line.words().isEmpty())) score += 1000;
            if (!track.album().isBlank() && normal(track.album()).equals(normal(text(record, "albumName", "album_name")))) score += 30;
            return score;
        }
        private Result unavailable(Response response) { return new Result(Status.Unavailable, List.of(), List.of(), response.retryMs()); }
        private Response get(String path) throws Exception {
            long delay = 350 - (System.currentTimeMillis() - lastRequest);
            if (delay > 0) Thread.sleep(delay);
            lastRequest = System.currentTimeMillis();
            var request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/" + path)).timeout(Duration.ofSeconds(9))
                .header("User-Agent", "Maro/1.21.11 (https://github.com/georgenat89-design/maro)")
                .header("Lrclib-Client", "Maro-1.21.11").header("Accept", "application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                long retry = retryAfter(response.headers().firstValue("Retry-After").orElse("60"));
                blockedUntil = System.currentTimeMillis() + retry;
                return new Response(response.statusCode(), JsonNull.INSTANCE, retry);
            }
            JsonElement json = response.body().length() <= 1_048_576 && response.statusCode() == 200
                ? JsonParser.parseString(response.body()) : JsonNull.INSTANCE;
            return new Response(response.statusCode(), json, 0);
        }
        private static String encode(String value) { return URLEncoder.encode(value.length() > 512 ? value.substring(0, 512) : value, StandardCharsets.UTF_8); }
        private static long retryAfter(String value) {
            try { return Math.max(1000, Math.min(31_536_000, Long.parseLong(value)) * 1000); }
            catch (NumberFormatException ignored) {
                try { return Math.max(1000, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
                catch (RuntimeException e) { return 60_000; }
            }
        }
    }
}
