package dev.maro.gametest;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.maro.module.ModuleManager;
import dev.maro.nathan.audio.SpotifyLyrics;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.modules.SpotifyHud;
import dev.maro.nathan.render.SpotifyHudLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Original synthetic lyrics: no external music player or network is controlled by these tests. */
final class SpotifyLyricsChecks {
    private static final SpotifyLyrics.Result TIMED = new SpotifyLyrics.Result(SpotifyLyrics.Status.Synced,
        SpotifyLyrics.parseLrc("[00:01.00]A quiet test line\n[00:04.00]Another test line\n[00:07.00]テストの歌詞\n[00:10.00]"), List.of(), 0);
    private static final String WORD_YAML = """
        version: '1.0'
        metadata:
          title: Word test song
          artist: Test artist
        lines:
          - text: Keep the music moving
            start_ms: 1000
            end_ms: 4000
            words:
              - text: 'Keep '
                start_ms: 1000
                end_ms: 1600
              - text: 'the '
                start_ms: 1600
                end_ms: 2000
              - text: 'music '
                start_ms: 2000
                end_ms: 2800
              - text: moving
                start_ms: 2800
                end_ms: 4000
          - text: A longer original lyric that should wrap across two lines without cutting off the words
            start_ms: 7000
            end_ms: 11000
        """;
    private static final SpotifyLyrics.Result WORDS = new SpotifyLyrics.Result(SpotifyLyrics.Status.Synced,
        SpotifyLyrics.parseLyricsfile(WORD_YAML), List.of(), 0);
    private static SpotifyMedia.State state(String title, long position) {
        return new SpotifyMedia.State(true, title, "Test artist", "Test album", "test", false, true,
            position, 60_000, System.currentTimeMillis(), "");
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void await(CountDownLatch latch) {
        try { require(latch.await(5, TimeUnit.SECONDS), "Lyrics worker timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static void waitResult(SpotifyLyrics lyrics, String key) {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!lyrics.snapshot().key().equals(key) || lyrics.snapshot().result().status() == SpotifyLyrics.Status.Loading) {
            require(System.nanoTime() < until, "Lyrics never reached expected song");
            try { Thread.sleep(5); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
    }
    static void run(ClientGameTestContext context) {
        var parsed = SpotifyLyrics.parseLrc("[offset:100]\n[00:03.1]Later\n[00:01.20][00:02.345]Repeat\n[00:04.00]\n[00:99.00]Invalid");
        require(parsed.size() == 4 && parsed.get(0).atMs() == 1300 && parsed.get(1).atMs() == 2445
            && parsed.get(2).atMs() == 3200 && parsed.get(3).text().isBlank(), "LRC timestamps, offset or blank lines failed");
        var snapshot = new SpotifyLyrics.Snapshot("test", TIMED);
        require(snapshot.frame(999, 0, 0).index() == -1 && snapshot.frame(1000, 0, 0).index() == 0, "Lyric boundary failed");
        require(snapshot.frame(4000, 0, 0).index() == 1 && snapshot.frame(2000, 2000, 0).index() == 1
            && snapshot.frame(2000, 0, 0).index() == 0, "Seeking backwards or timing adjustment failed");
        require(snapshot.frame(10_000, 0, 0).current().equals("♪"), "Instrumental gap retained old words");
        var candidate = JsonParser.parseString("{\"trackName\":\"Test song\",\"artistName\":\"Test artist\",\"duration\":60}").getAsJsonObject();
        var track = SpotifyLyrics.Track.of(state("Test song", 1500));
        require(SpotifyLyrics.matches(track, candidate), "Matching song rejected");
        candidate.addProperty("duration", 80); require(!SpotifyLyrics.matches(track, candidate), "Wrong duration accepted");
        candidate.addProperty("duration", 60); candidate.addProperty("trackName", "Test song (Remix)");
        require(!SpotifyLyrics.matches(track, candidate), "Wrong remix accepted");
        candidate.addProperty("trackName", "Test song"); candidate.addProperty("artistName", "Other artist");
        require(!SpotifyLyrics.matches(track, candidate), "Wrong artist accepted");
        var plain = SpotifyLyrics.decode(JsonParser.parseString("{\"plainLyrics\":\"First line\\nSecond line\\nThird line\"}").getAsJsonObject());
        require(plain.status() == SpotifyLyrics.Status.Plain
            && new SpotifyLyrics.Snapshot("test", plain).frame(60_000, 1000, 0).current().equals("First line"), "Untimed lyrics invented timing");
        require(SpotifyLyrics.decode(JsonParser.parseString("{\"instrumental\":true}").getAsJsonObject()).status() == SpotifyLyrics.Status.Instrumental,
            "Instrumental status failed");
        require(SpotifyLyrics.decode(JsonParser.parseString("{}").getAsJsonObject()).status() == SpotifyLyrics.Status.Missing, "Missing lyrics status failed");
        require(WORDS.lines().size() == 2 && WORDS.lines().getFirst().words().size() == 4, "Real word timestamps did not parse");
        var wordSnapshot = new SpotifyLyrics.Snapshot("words", WORDS);
        var exact = wordSnapshot.karaoke(2500, 0, 0, 60000, false);
        require(exact.active() == 2 && exact.currentWord().equals("music") && !exact.estimated()
            && Math.abs(exact.progress() - .625) < .001, "Active word or smooth progress used wrong timestamps");
        require(wordSnapshot.karaoke(1600, 0, 0, 60000, false).active() == 1
            && wordSnapshot.karaoke(999, 0, 0, 60000, false).active() == -1, "Word boundary failed");
        require(wordSnapshot.karaoke(4500, 0, 0, 60000, false).active() == -1, "Word held through an explicit vocal gap");
        require(wordSnapshot.karaoke(2500, -1000, 0, 60000, false).active() == 0, "Word offset/backwards seek failed");
        require(wordSnapshot.karaoke(2000, 0, 0, 60000, false).currentWord().equals("music")
            && wordSnapshot.karaoke(2799, 0, 0, 60000, false).currentWord().equals("music")
            && wordSnapshot.karaoke(2800, 0, 0, 60000, false).currentWord().equals("moving"),
            "Individual words advanced before the next supplied vocal timestamp");
        var enhanced = SpotifyLyrics.parseLrc("[offset:100]\n[00:01.00][00:10.00]<00:01.00>Keep <00:01.60>the <00:02.00>music <00:02.80>moving<00:04.00>");
        require(enhanced.size() == 2 && enhanced.getFirst().words().size() == 4
            && enhanced.getFirst().text().equals("Keep the music moving") && enhanced.getFirst().endMs() == 4100
            && enhanced.get(1).words().get(2).atMs() == 11100, "Enhanced LRC words, offset or repeated lines failed");
        var enhancedSnapshot = new SpotifyLyrics.Snapshot("enhanced", new SpotifyLyrics.Result(SpotifyLyrics.Status.Synced, enhanced, List.of(), 0));
        require(enhancedSnapshot.karaoke(2899, 0, 0, 60000, false).currentWord().equals("music")
            && enhancedSnapshot.karaoke(2900, 0, 0, 60000, false).currentWord().equals("moving")
            && enhancedSnapshot.karaoke(4100, 0, 0, 60000, false).active() == -1, "Enhanced word boundaries/gap failed");
        require(SpotifyLyrics.parseLrc("[00:01.00]<00:01.00>First <00:00.50>invalid").getFirst().words().isEmpty(),
            "Backwards enhanced word timestamps accepted");
        var segment = SpotifyLyrics.parseLrc("[00:01.00]<00:01.00>Not individual words<00:04.00>");
        var segmentSnapshot = new SpotifyLyrics.Snapshot("segment", new SpotifyLyrics.Result(SpotifyLyrics.Status.Synced, segment, List.of(), 0));
        require(!segmentSnapshot.karaoke(1500, 0, 0, 60000, false).individualWords(), "Multiword segment treated as individual word timing");
        var enhancedRecord = new com.google.gson.JsonObject();
        enhancedRecord.addProperty("lyricsfile", "version: '1.0'\nlines:\n  - text: Keep the music moving\n    start_ms: 1000\n    end_ms: 4000\n");
        enhancedRecord.addProperty("syncedLyrics", "[00:01.00]<00:01.00>Keep <00:01.60>the <00:02.00>music <00:02.80>moving<00:04.00>");
        require(SpotifyLyrics.decode(enhancedRecord).lines().getFirst().words().size() == 4, "Word starts in enhanced LRC lost to generated line-only Lyricsfile");
        require(!snapshot.karaoke(2500, 0, 0, 60000, false).timed()
            && snapshot.karaoke(2500, 0, 0, 60000, true).estimated(), "Missing word timing silently became estimated");
        var estimatedEarly = snapshot.karaoke(1100, 0, 0, 60000, true);
        var estimatedLater = snapshot.karaoke(3700, 0, 0, 60000, true);
        require(estimatedLater.active() > estimatedEarly.active(), "Line-only lyric word cursor did not advance");
        require(snapshot.karaoke(1100, 0, 0, 60000, true).words() == estimatedEarly.words(), "Estimated timing plan was rebuilt every frame");
        require(SpotifyLyrics.parseLyricsfile(WORD_YAML.replace("version: '1.0'", "version: '2.0'")).isEmpty(), "Unknown Lyricsfile version accepted");
        require(SpotifyLyrics.parseLyricsfile("version: '1.0'\nversion: '1.0'\nlines: []").isEmpty(), "Duplicate YAML keys accepted");
        require(SpotifyLyrics.parseLyricsfile("!!javax.script.ScriptEngineManager []").isEmpty(), "Unsafe YAML tag accepted");
        var malformed = SpotifyLyrics.parseLyricsfile(WORD_YAML.replace("start_ms: 2000", "start_ms: 1500"));
        require(malformed.getFirst().words().isEmpty(), "Out-of-order word timing was displayed as accurate");

        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var lyrics = new SpotifyLyrics(request -> {
            calls.incrementAndGet();
            if (request.title().equals("First")) { entered.countDown(); await(release); }
            return TIMED;
        })) {
            lyrics.update(state("First", 0)); await(entered);
            lyrics.update(state("Second", 0)); release.countDown();
            waitResult(lyrics, SpotifyLyrics.Track.of(state("Second", 0)).key());
            require(calls.get() == 2, "Song changes did not fetch once per song");
            lyrics.update(state("First", 0));
            require(lyrics.snapshot().result() == TIMED && calls.get() == 2, "Lyrics cache missed");
            lyrics.close(); lyrics.update(state("Second", 0));
            require(lyrics.snapshot().result() == TIMED, "Disable/re-enable lost usable cached lyrics");
        }

        var image = new java.awt.image.BufferedImage(96, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 96; row++) for (int col = 0; col < 96; col++) {
            double light = Math.exp(-Math.pow((col - 30) / 35.0, 2) - Math.pow((row - 28) / 40.0, 2));
            image.setRGB(col, row, 0xff000000 | (int)(35 + 175 * light) << 16 | (int)(40 + 95 * light) << 8 | (int)(85 + 115 * (1 - light)));
        }
        SpotifyMedia.Artwork demoArt;
        try {
            var decode = SpotifyMedia.class.getDeclaredMethod("decodeArtwork", String.class, java.awt.image.BufferedImage.class); decode.setAccessible(true);
            demoArt = (SpotifyMedia.Artwork)decode.invoke(null, SpotifyMedia.artworkKey(state("Word test song", 2500)), image);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        var hud = ModuleManager.get(SpotifyHud.class);
        CountDownLatch loadingRelease = new CountDownLatch(1);
        var fakeLyrics = new SpotifyLyrics(request -> {
            if (request.title().equals("Loading test song")) { await(loadingRelease); return SpotifyLyrics.Result.status(SpotifyLyrics.Status.Unavailable); }
            return request.title().equals("Untimed test song") ? plain
                : request.title().equals("Footer test song") ? new SpotifyLyrics.Result(SpotifyLyrics.Status.Synced,
                    SpotifyLyrics.parseLrc("[00:01.00]An original quiet chorus\n[00:04.00]gently playing by the river"), List.of(), 0)
                : request.title().equals("Word test song") ? WORDS
                : request.title().equals("Missing test song") ? SpotifyLyrics.Result.status(SpotifyLyrics.Status.Missing) : TIMED;
        });
        SpotifyLyrics original = context.computeOnClient(client -> {
            try {
                var field = SpotifyHud.class.getDeclaredField("lyrics"); field.setAccessible(true);
                var old = (SpotifyLyrics)field.get(hud); field.set(hud, fakeLyrics);
                setting(hud, "lyrics", new JsonPrimitive(true)); setting(hud, "auto hide", new JsonPrimitive(false));
                setting(hud, "anchor", new JsonPrimitive("BottomRight"));
                hud.setEnabled(true);
                media(hud).close(); setState(hud, state("Original test song", 1500));
                return old;
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        try {
            context.waitTicks(12);
            context.runOnClient(client -> {
                require(fakeLyrics.snapshot().result() == TIMED, "HUD did not consume async lyrics");
                var layout = layout(hud);
                require(layout.bounds().y() + layout.bounds().height() <= client.getWindow().getFramebufferHeight()
                    && layout.lyrics().y() > layout.panel().y() + layout.panel().height(), "Anchored lyrics clipped or overlapped player");
            });
            context.takeScreenshot("maro-spotify-lyrics");
            context.runOnClient(client -> {
                require(!hud.getSettings().stream().filter(s -> s.getName().equals("approximate word preview")).findFirst().orElseThrow()
                    .toJson().getAsBoolean(), "Guessed vocal timing is on by default");
                require(hud.getSettings().stream().filter(s -> s.getName().equals("word display")).findFirst().orElseThrow()
                    .toJson().getAsString().equals("WordHighlight"), "Default word display does not highlight words");
                setting(hud, "word display", new JsonPrimitive("Lines")); setting(hud, "lyrics size", new JsonPrimitive(11));
                setState(hud, state("Footer test song", 1500));
            });
            context.waitTicks(6);
            context.runOnClient(client -> {
                double[] sizes = {8, 11, 16}, scales = {.65, 1, 2};
                for (double size : sizes) for (double scale : scales) {
                    setting(hud, "lyrics size", new JsonPrimitive(size)); setting(hud, "scale", new JsonPrimitive(scale));
                    var area = layout(hud).lyrics();
                    var font = dev.maro.nathan.render.CrispFont.POPPINS_MEDIUM.forCaps(8 * scale);
                    double bottom = area.y() + area.height() - 18 * scale + font.height() - font.capsTop();
                    require(bottom <= area.y() + area.height() - 2 * scale, "Preview descenders extend outside card at size " + size + ", scale " + scale);
                }
                setting(hud, "lyrics size", new JsonPrimitive(11)); setting(hud, "scale", new JsonPrimitive(1));
            });
            context.takeScreenshot("maro-spotify-footer-padding");
            context.runOnClient(client -> setting(hud, "word display", new JsonPrimitive("WordHighlight")));
            context.waitTicks(6);
            context.takeScreenshot("maro-spotify-no-word-timing");
            context.runOnClient(client -> {
                setting(hud, "approximate word preview", new JsonPrimitive(true));
                require(lyricWords(hud, snapshot, 2500).estimated(), "Optional approximation stopped working in WordHighlight");
                setting(hud, "word display", new JsonPrimitive("SingleWord"));
                require(!lyricWords(hud, snapshot, 2500).timed(), "SingleWord still guesses vocal timing with approximation enabled");
                require(!lyricWords(hud, segmentSnapshot, 1500).timed(), "SingleWord split an untimed multiword segment");
                var nativeExact = lyricWords(hud, wordSnapshot, 2500);
                require(nativeExact.currentWord().equals("music") && !nativeExact.estimated(), "SingleWord did not use exact vocal timestamps");
                setting(hud, "approximate word preview", new JsonPrimitive(false));
            });
            context.waitTicks(6);
            context.takeScreenshot("maro-spotify-single-word-unavailable");
            context.runOnClient(client -> {
                setState(hud, state("Original test song", 7500));
                setting(hud, "player mode", new JsonPrimitive("Mini"));
                setting(hud, "lyrics size", new JsonPrimitive(16));
            });
            context.waitTicks(12);
            context.takeScreenshot("maro-spotify-lyrics-unicode-mini");
            context.runOnClient(client -> setState(hud, state("Untimed test song", 5000)));
            context.waitTicks(5);
            context.runOnClient(client -> {
                var area = layout(hud).lyrics();
                require(hud.scrollLyrics(area.centerX(), area.centerY(), -1), "Untimed lyrics did not accept scrolling");
                try {
                    var index = SpotifyHud.class.getDeclaredField("plainLyricIndex"); index.setAccessible(true);
                    require(index.getInt(hud) == 1, "Untimed lyric row did not advance");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.takeScreenshot("maro-spotify-lyrics-plain");
            context.runOnClient(client -> {
                setting(hud, "player mode", new JsonPrimitive("Expanded"));
                setting(hud, "lyrics size", new JsonPrimitive(12));
                setting(hud, "word display", new JsonPrimitive("WordHighlight"));
                setState(hud, state("Word test song", 2500));
                try { var art = SpotifyMedia.class.getDeclaredField("artwork"); art.setAccessible(true); art.set(media(hud), demoArt); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.waitTicks(12);
            context.takeScreenshot("maro-spotify-karaoke-word");
            context.runOnClient(client -> setting(hud, "word display", new JsonPrimitive("SingleWord")));
            context.waitTicks(6);
            context.runOnClient(client -> require(liveWord(hud).equals("music"), "SingleWord rendered the wrong sung word"));
            context.takeScreenshot("maro-spotify-karaoke-single");
            context.runOnClient(client -> setState(hud, state("Word test song", 2799)));
            context.waitTicks(2);
            context.runOnClient(client -> require(liveWord(hud).equals("music"), "SingleWord advanced while paused inside a held word"));
            context.runOnClient(client -> setState(hud, state("Word test song", 2800)));
            context.waitTicks(2);
            context.runOnClient(client -> require(liveWord(hud).equals("moving"), "SingleWord missed the next exact word boundary"));
            context.runOnClient(client -> setState(hud, state("Word test song", 1600)));
            context.waitTicks(2);
            context.runOnClient(client -> require(liveWord(hud).equals("the"), "SingleWord did not follow a backwards seek"));
            context.runOnClient(client -> {
                setting(hud, "word display", new JsonPrimitive("Lines")); setState(hud, state("Word test song", 8000));
            });
            context.waitTicks(8);
            context.takeScreenshot("maro-spotify-lyrics-wrapped");
            context.runOnClient(client -> setState(hud, state("Missing test song", 8000)));
            context.waitTicks(8);
            context.takeScreenshot("maro-spotify-lyrics-missing");
            context.runOnClient(client -> setState(hud, state("Loading test song", 8000)));
            context.waitTicks(5);
            require(fakeLyrics.snapshot().result().status() == SpotifyLyrics.Status.Loading, "Lyrics lookup blocked the game or failed to show loading");
            context.takeScreenshot("maro-spotify-lyrics-loading");
            loadingRelease.countDown();
            context.waitTicks(6);
            context.takeScreenshot("maro-spotify-lyrics-offline");
            context.runOnClient(client -> {
                var layout = layout(hud);
                require(layout.bounds().y() + layout.bounds().height() <= client.getWindow().getFramebufferHeight(), "Mini lyrics clipped at large size");
                setting(hud, "lyrics", new JsonPrimitive(false));
                require(layout(hud).totalHeight() == layout(hud).height(), "Lyrics toggle retained extra space");
            });
        } finally {
            loadingRelease.countDown();
            context.runOnClient(client -> {
                hud.setEnabled(false);
                try { var field = SpotifyHud.class.getDeclaredField("lyrics"); field.setAccessible(true); field.set(hud, original); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                for (var group : hud.settings) for (var setting : group) setting.reset();
            });
            fakeLyrics.close();
        }
    }
    private static void setting(SpotifyHud hud, String name, JsonPrimitive value) {
        hud.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow().fromJson(value);
    }
    private static SpotifyLyrics.WordFrame lyricWords(SpotifyHud hud, SpotifyLyrics.Snapshot snapshot, long position) {
        try {
            var method = SpotifyHud.class.getDeclaredMethod("lyricWords", SpotifyLyrics.Snapshot.class, long.class, long.class);
            method.setAccessible(true); return (SpotifyLyrics.WordFrame) method.invoke(hud, snapshot, position, 60000L);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static String liveWord(SpotifyHud hud) {
        try { var field = SpotifyHud.class.getDeclaredField("liveWord"); field.setAccessible(true); return (String) field.get(hud); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static SpotifyMedia media(SpotifyHud hud) throws ReflectiveOperationException {
        var field = SpotifyHud.class.getDeclaredField("media"); field.setAccessible(true); return (SpotifyMedia)field.get(hud);
    }
    private static void setState(SpotifyHud hud, SpotifyMedia.State state) {
        try { var field = SpotifyMedia.class.getDeclaredField("state"); field.setAccessible(true); field.set(media(hud), state); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static SpotifyHudLayout layout(SpotifyHud hud) {
        try { var method = SpotifyHud.class.getDeclaredMethod("layout"); method.setAccessible(true); return (SpotifyHudLayout)method.invoke(hud); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
