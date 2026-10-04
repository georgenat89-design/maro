package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.module.ModuleManager;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.gui.SpotifyControlsScreen;
import dev.maro.nathan.modules.SpotifyHud;
import dev.maro.nathan.render.SpotifyHudLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.io.InputStream;
import java.io.OutputStream;

/** Real HUD mouse paths with an isolated transport; never changes the user's output volume. */
final class SpotifyVolumeChecks {
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static Object field(Object owner, String name) {
        try { var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void field(Object owner, String name, Object value) {
        try { var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void setting(SpotifyHud hud, String name, Object value) {
        var json = value instanceof Boolean bool ? new JsonPrimitive(bool) : value instanceof Number number
            ? new JsonPrimitive(number) : new JsonPrimitive(value.toString());
        hud.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow().fromJson(json);
    }
    private static SpotifyHudLayout layout(SpotifyHud hud) {
        try { var method = SpotifyHud.class.getDeclaredMethod("layout"); method.setAccessible(true); return (SpotifyHudLayout)method.invoke(hud); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void sample(SpotifyMedia media, boolean available, double level, boolean muted) {
        field(media, "volume", new SpotifyMedia.Volume(available, level, muted, available ? "" : "Windows output unavailable"));
        field(media, "volumeSampleAt", System.currentTimeMillis());
    }
    static void run(ClientGameTestContext context) {
        var hud = ModuleManager.get(SpotifyHud.class);
        var media = (SpotifyMedia)field(hud, "media");
        context.runOnClient(client -> {
            setting(hud, "lyrics", false); setting(hud, "auto hide", false);
            setting(hud, "volume control", true); setting(hud, "player mode", "Expanded");
            setting(hud, "anchor", "Free"); setting(hud, "x", 20); setting(hud, "y", 70);
            setting(hud, "scale", 1);
            hud.setEnabled(true); media.close();
            field(media, "audioProcess", new IsolatedProcess());
            field(media, "state", new SpotifyMedia.State(true, "Volume controls", "Spotify HUD", "", "test",
                false, true, 25000, 180000, System.currentTimeMillis(), ""));
            sample(media, true, .5, false);
            client.setScreen(new SpotifyControlsScreen(hud));
        });
        try {
            context.waitTicks(12);
            for (double scale : new double[]{.65, 1, 2}) {
                context.runOnClient(client -> {
                    setting(hud, "scale", scale); sample(media, true, .5, false);
                    var layout = layout(hud); var track = layout.volumeTrack(); var mute = layout.volumeMute();
                    require(!layout.timelineHit().contains(track.centerX(), track.centerY()), "Volume target overlaps seek target");
                    require(hud.mousePressed(track.centerX(), track.centerY()), "Volume slider not clickable");
                    require(hud.mouseDragged(track.x() + track.width() * .75, track.centerY()), "Volume drag not captured");
                    require(Math.abs(media.volume().level() - .75) < .001, "Volume drag used incorrect pixel scale");
                    require(hud.mouseReleased(track.x() + track.width() * .75, track.centerY()), "Volume release not handled");
                    require(!hud.mouseDragged(track.x(), track.centerY()), "Volume kept dragging after release");
                    hud.mousePressed(mute.centerX(), mute.centerY());
                    require(media.volume().muted() && Math.abs(media.volume().level() - .75) < .001, "Mute lost the remembered level");
                    hud.mousePressed(mute.centerX(), mute.centerY());
                    require(!media.volume().muted(), "Speaker button did not unmute");
                    require(hud.scrollVolume(track.centerX(), track.centerY(), -1), "Volume scroll not handled");
                    require(Math.abs(media.volume().level() - .73) < .001, "Volume scroll step incorrect");
                    hud.mousePressed(track.x(), track.centerY()); hud.mouseDragged(track.x() - 100, track.centerY());
                    hud.mouseReleased(track.x() - 100, track.centerY());
                    require(media.volume().level() == 0, "Slider did not clamp at zero");
                    hud.mousePressed(mute.centerX(), mute.centerY());
                    require(media.volume().level() > 0 && !media.volume().muted(), "Zero volume could not be restored with speaker button");
                    hud.mousePressed(track.x(), track.centerY()); hud.mouseReleased(track.x() + track.width() + 100, track.centerY());
                    require(media.volume().level() == 1, "Slider did not clamp at 100%");
                    media.setVolume(Double.NaN, false);
                    require(media.volume().level() == 1, "Nonfinite volume accepted");
                    sample(media, false, .5, false);
                    require(hud.mousePressed(track.centerX(), track.centerY()) && !hud.mouseDragged(track.x(), track.centerY()),
                        "Unavailable volume started dragging the card or slider");
                    require(!media.volume().available(), "Unavailable backend reported a successful volume change");
                });
            }
            context.runOnClient(client -> {
                setting(hud, "scale", 1); sample(media, true, .64, false);
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-spotify-volume");
            context.runOnClient(client -> {
                sample(media, true, .64, true);
                var track = layout(hud).volumeTrack(); hud.mousePressed(track.centerX(), track.centerY());
                hud.cancelInteraction(); require(!hud.mouseDragged(track.x(), track.centerY()), "Screen exit left volume drag captured");
                sample(media, true, .64, true);
            });
            context.takeScreenshot("maro-spotify-volume-muted");
            context.runOnClient(client -> {
                setting(hud, "player mode", "Mini");
            });
            context.waitTicks(8);
            context.runOnClient(client -> require(layout(hud).expansion() == 1, "Mini mode did not reveal volume controls in F9"));
        } finally {
            context.runOnClient(client -> {
                client.setScreen(null); hud.setEnabled(false);
                for (var group : hud.settings) for (var setting : group) setting.reset();
            });
        }
    }
    private static final class IsolatedProcess extends Process {
        public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
        public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        public int waitFor() { return 0; }
        public int exitValue() { return 0; }
        public void destroy() { }
    }
}
