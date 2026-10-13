package dev.maro.module.impl.misc;

import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import dev.maro.util.KeyUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Saved Spotify playlists (or albums and songs), each opened by a button or its own key. No Spotify login is needed. */
public final class SpotifyPlaylists extends Module {
    private static final Pattern SPOTIFY_URI = Pattern.compile("spotify:(playlist|album|track):([A-Za-z0-9]{22})");
    // Share links may carry a language prefix and a ?si= tracking query; only the kind and ID matter.
    private static final Pattern SPOTIFY_LINK = Pattern.compile(
            "(?:https?://)?open\\.spotify\\.com/(?:intl-[a-z-]+/)?(playlist|album|track)/([A-Za-z0-9]{22})(?:[/?#].*)?",
            Pattern.CASE_INSENSITIVE);

    /** One playlist: its link, an optional key and an Open button. */
    private final class Playlist {
        final String name, builtIn;
        final TextSetting link;
        final KeybindSetting key;
        final ButtonSetting open;
        boolean wasDown;

        Playlist(String name, String builtIn) {
            this.name = name;
            this.builtIn = builtIn;
            // An empty link falls back to the built-in one, so a saved config never hides it.
            link = add(new TextSetting(name + " Link", "Spotify link for " + name + (builtIn.isEmpty() ? "" : "; leave empty for the built-in one"),
                    "", 256, builtIn.isEmpty() ? "Paste a Spotify link" : builtIn));
            key = add(new KeybindSetting(name + " Key", "Press in-game to open " + name, KeyUtil.NONE));
            open = add(new ButtonSetting(name, "Open " + name + " in Spotify", "Open", () -> play(this)));
        }

        String url() {
            String value = link.get().trim();
            return value.isEmpty() ? builtIn : value;
        }
    }

    private final ModeSetting openIn = add(new ModeSetting("Open In", "Spotify App needs desktop Spotify installed; Browser uses the web player",
            "Spotify App", "Spotify App", "Browser"));
    private final List<Playlist> playlists = List.of(
            new Playlist("Larp Aux", "https://open.spotify.com/playlist/28pZmz5AmsYUBNvgoQ81gw"),
            new Playlist("Phonk", "https://open.spotify.com/playlist/1BzL7qnSMTufvz4Uq8Qamj"),
            new Playlist("House Music", "https://open.spotify.com/track/4m17nqEF8zjTEcKCxGI58J"),
            new Playlist("Summer Music", "https://open.spotify.com/playlist/2TmQRKC2wIDl3bvsWhKv7o"));

    public SpotifyPlaylists() {
        super("Spotify Playlists", "Open your saved Spotify playlists from the menu or with a key", Category.MISC);
    }

    @Override public List<SettingSection> getSettingSections() {
        List<SettingSection> sections = new ArrayList<>();
        sections.add(SettingSection.of("Playback", openIn));
        for (Playlist p : playlists) sections.add(SettingSection.of(p.name, p.link, p.key, p.open));
        return sections;
    }

    @Override protected void onEnable() {
        // A key already held when the module turns on should not open its playlist.
        for (Playlist p : playlists) p.wasDown = isDown(p.key.get());
    }

    @Override public void onTick() {
        for (Playlist p : playlists) {
            boolean down = mc.currentScreen == null && isDown(p.key.get());
            if (down && !p.wasDown) play(p);
            p.wasDown = down;
        }
    }

    /** "playlist/ID", "album/ID" or "track/ID" from a Spotify link or URI, or null for anything else. */
    public static String spotifyPath(String raw) {
        String value = raw.trim();
        var match = SPOTIFY_URI.matcher(value);
        if (!match.matches()) match = SPOTIFY_LINK.matcher(value);
        return match.matches() ? match.group(1).toLowerCase(Locale.ROOT) + "/" + match.group(2) : null;
    }

    /** Where the named playlist opens, or null while it has no valid link. */
    public URI target(String name) {
        for (Playlist p : playlists) if (p.name.equals(name)) return target(p);
        throw new IllegalArgumentException("No playlist named " + name);
    }

    private URI target(Playlist p) {
        String path = spotifyPath(p.url());
        if (path == null) return null;
        return URI.create(openIn.is("Browser") ? "https://open.spotify.com/" + path : "spotify:" + path.replace('/', ':'));
    }

    private void play(Playlist p) {
        if (p.url().isEmpty()) {
            Notifications.push(getName(), "Add a Spotify link for " + p.name + " first", Notifications.Type.WARNING);
            return;
        }
        URI target = target(p);
        if (target == null) {
            Notifications.push(getName(), p.name + " needs a Spotify playlist, album or song link", Notifications.Type.WARNING);
            return;
        }
        Util.getOperatingSystem().open(target);
        Notifications.push(getName(), "Opening " + p.name, Notifications.Type.INFO);
    }

    private static boolean isDown(int code) {
        if (code == KeyUtil.NONE) return false;
        long window = mc.getWindow().getHandle();
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(window, code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }
}
