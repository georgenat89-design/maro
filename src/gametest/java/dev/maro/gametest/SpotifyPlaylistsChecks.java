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

            require(ID.equals(SpotifyPlaylists.playlistId("https://open.spotify.com/playlist/" + ID + "?si=abc123")), "Share link with ?si= was rejected");
            require(ID.equals(SpotifyPlaylists.playlistId("spotify:playlist:" + ID)), "Spotify URI was rejected");
            require(ID.equals(SpotifyPlaylists.playlistId("  open.spotify.com/intl-de/playlist/" + ID + " ")), "Language-prefixed link was rejected");
            require(SpotifyPlaylists.playlistId("https://open.spotify.com/album/" + ID) == null, "An album link was taken as a playlist");
            require(SpotifyPlaylists.playlistId("https://evil.example/open.spotify.com/playlist/" + ID) == null, "Another site's link was accepted");
            require(SpotifyPlaylists.playlistId("https://spotify.link/abc") == null, "A short link was accepted");

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
                } finally {
                    link.set(oldLink);
                    openIn.set(oldMode);
                }
            }
        });
    }
}
