package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPlaylists;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.net.URI;

/** Link parsing and where each playlist opens; nothing is launched. */
final class SpotifyPlaylistsChecks {
    private static final String ID = "37i9dQZF1DXcBWIGoYBM5M";

    private SpotifyPlaylistsChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static Setting<String> setting(SpotifyPlaylists module, String name) {
        for (Setting<?> s : module.getSettings()) if (s.getName().equals(name)) return (Setting<String>) s;
        throw new AssertionError("Spotify Playlists has no setting " + name);
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(c -> {
            SpotifyPlaylists module = ModuleManager.get(SpotifyPlaylists.class);
            require(module != null, "Spotify Playlists was not registered");

            require(("playlist/" + ID).equals(SpotifyPlaylists.spotifyPath("https://open.spotify.com/playlist/" + ID + "?si=abc123")), "Share link with ?si= was rejected");
            require(("playlist/" + ID).equals(SpotifyPlaylists.spotifyPath("spotify:playlist:" + ID)), "Spotify URI was rejected");
            require(("playlist/" + ID).equals(SpotifyPlaylists.spotifyPath("  open.spotify.com/intl-de/playlist/" + ID + " ")), "Language-prefixed link was rejected");
            require(("track/" + ID).equals(SpotifyPlaylists.spotifyPath("https://open.spotify.com/track/" + ID + "?si=x")), "A song link was rejected");
            require(("album/" + ID).equals(SpotifyPlaylists.spotifyPath("https://open.spotify.com/album/" + ID)), "An album link was rejected");
            require(SpotifyPlaylists.spotifyPath("https://open.spotify.com/artist/" + ID) == null, "An artist link was accepted");
            require(SpotifyPlaylists.spotifyPath("https://evil.example/open.spotify.com/playlist/" + ID) == null, "Another site's link was accepted");
            require(SpotifyPlaylists.spotifyPath("https://spotify.link/abc") == null, "A short link was accepted");

            // Empty links fall back to the built-in ones.
            Setting<String> mode = setting(module, "Open In");
            String savedMode = mode.get();
            String[][] builtIn = {{"Larp Aux", "spotify:playlist:28pZmz5AmsYUBNvgoQ81gw"}, {"Phonk", "spotify:playlist:1kbzQ66BqTZf0ncvvtEWE6"},
                    {"House Music", "spotify:track:4m17nqEF8zjTEcKCxGI58J"}, {"Summer Music", "spotify:playlist:2TmQRKC2wIDl3bvsWhKv7o"}};
            for (String[] expected : builtIn) {
                Setting<String> link = setting(module, expected[0] + " Link");
                String oldLink = link.get();
                try {
                    link.set("");
                    mode.set("Spotify App");
                    require(URI.create(expected[1]).equals(module.target(expected[0])), expected[0] + " lost its built-in link");
                } finally {
                    link.set(oldLink);
                    mode.set(savedMode);
                }
            }

            for (String playlist : new String[]{"Larp Aux", "Phonk", "House Music", "Summer Music"}) {
                Setting<String> link = setting(module, playlist + " Link"), openIn = setting(module, "Open In");
                String oldLink = link.get(), oldMode = openIn.get();
                try {
                    link.set("not a playlist");
                    require(module.target(playlist) == null, playlist + " accepted a bad link");
                    link.set("https://open.spotify.com/playlist/" + ID + "?si=x");
                    openIn.set("Spotify App");
                    require(URI.create("spotify:playlist:" + ID).equals(module.target(playlist)), playlist + " did not open in the app");
                    openIn.set("Browser");
                    require(URI.create("https://open.spotify.com/playlist/" + ID).equals(module.target(playlist)), playlist + " did not open in the browser");
                    link.set("spotify:track:" + ID);
                    require(URI.create("https://open.spotify.com/track/" + ID).equals(module.target(playlist)), playlist + " did not open a song");
                } finally {
                    link.set(oldLink);
                    openIn.set(oldMode);
                }
            }
        });
        System.out.println("[spotify-playlists] PASS: build 698 registration, four built-in links, app/browser targets, playlist/album/track parsing, language/query handling and invalid-host rejection; no external app launched");
    }
}
