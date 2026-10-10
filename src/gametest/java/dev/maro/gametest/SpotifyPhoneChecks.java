package dev.maro.gametest;

import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifyPlayback;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.nathan.modules.SpotifyHud;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Real screen/input/render paths, with transport clicks isolated from the user's music. */
final class SpotifyPhoneChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Click click(SpotifyPhoneScreen screen, float x, float y) {
        var l = screen.layout(); return new Click(l.screenX(x), l.screenY(y), new MouseInput(0, 0));
    }
    static void run(ClientGameTestContext context) {
        var phone = ModuleManager.get(SpotifyPhone.class);
        var hud = ModuleManager.get(SpotifyHud.class);
        var playback = new IsolatedPlayback();
        try {
            context.runOnClient(client -> {
                phone.setEnabled(false); hud.setEnabled(false); client.setScreen(null);
                phone.getBind().set(GLFW.GLFW_KEY_F10);
                require(!phone.persistEnabled(), "Phone can reactivate on login");
                hud.setEnabled(true);
            });
            Object bridge = field(SpotifySession.media(), "worker");
            context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(phone.isEnabled() && client.currentScreen instanceof SpotifyPhoneScreen, "F10 did not open phone");
                require(!client.currentScreen.shouldPause(), "Phone pauses the world");
                require(field(SpotifySession.media(), "worker") == bridge, "Phone started a second media bridge");
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(!phone.isEnabled() && client.currentScreen == null, "F10 did not close phone");
                require(hud.isEnabled() && field(SpotifySession.media(), "worker") == bridge, "Closing phone disconnected HUD");
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            context.waitTicks(3);
            context.runOnClient(client -> {
                hud.setEnabled(false);
                require(phone.isEnabled() && field(SpotifySession.media(), "worker") == bridge, "Disabling HUD disconnected phone");
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(!phone.isEnabled() && client.currentScreen == null, "Escape left phone enabled");
                require(field(SpotifySession.media(), "worker") == null, "Last player left media process running");
                client.setScreen(new SpotifyPhoneScreen(phone, playback));
            });
            context.waitTicks(8);
            for (String action : new String[]{"previous", "toggle", "next"}) {
                float x = action.equals("previous") ? 53 : action.equals("toggle") ? 104 : 155;
                context.runOnClient(client -> {
                    var screen = (SpotifyPhoneScreen) client.currentScreen;
                    require(screen.mouseClicked(click(screen, x, 350), false), "Transport click unhandled");
                    require(playback.controls.getLast().equals(action), "Phone dispatched incorrect transport action");
                });
                context.waitTicks(7);
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
            context.waitTicks(7);
            context.runOnClient(client -> require(playback.controls.getLast().equals("toggle"), "Space did not play/pause"));
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            context.waitTicks(7);
            context.runOnClient(client -> require(playback.controls.getLast().equals("next"), "Arrow did not skip"));
            for (String hand : new String[]{"Right", "Left"}) for (double size : new double[]{.8, 1.2}) {
                context.runOnClient(client -> {
                    phone.hand.set(hand); phone.size.set(size);
                    var screen = (SpotifyPhoneScreen) client.currentScreen;
                    var l = screen.layout();
                    require(l.y() >= 0 && l.x() >= 0, "Phone left window at supported size");
                    screen.mouseClicked(click(screen, 64, 307), false);
                    screen.mouseDragged(click(screen, 144, 307), 80 * l.scale(), 0);
                    require(playback.seeks.isEmpty(), "Dragging queued transport requests before release");
                    screen.mouseReleased(click(screen, 144, 307));
                    require(playback.seeks.removeLast() == 135000, "Seeking ignored resize or hand placement");
                    screen.mouseClicked(click(screen, 100, 307), false);
                    playback.state = track("Changed while dragging", true, true);
                    screen.mouseReleased(click(screen, 184, 307));
                    require(playback.seeks.isEmpty(), "Scrub applied to a different song");
                    playback.state = track("Midnight Drive", true, true);
                });
            }
            context.runOnClient(client -> {
                phone.hand.set("Right"); phone.size.set(1.0);
                playback.state = new SpotifyMedia.State(true, "Midnight Drive", "Maro Radio", "After Hours", "Spotify",
                    true, true, 62000, 180000, System.currentTimeMillis(), "");
                client.player.setPitch(0);
                client.options.hudHidden = true;
            });
            context.waitTicks(4);
            context.takeScreenshot("maro-spotify-phone-playing");
            context.runOnClient(client -> {
                var screen = (SpotifyPhoneScreen) client.currentScreen;
                require(field(screen, "cover") != null, "Album artwork was not uploaded");
                screen.mouseClicked(click(screen, 182, 56), false);
                require(client.currentScreen == null && field(screen, "cover") == null, "Closing phone leaked album texture");
                client.setScreen(new SpotifyPhoneScreen(phone, playback));
            });
            context.waitTicks(6);
            context.runOnClient(client -> phone.hand.set("Left"));
            context.waitTicks(3);
            context.takeScreenshot("maro-spotify-phone-left");
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(4);
            context.takeScreenshot("maro-spotify-phone-small");
            context.runOnClient(client -> {
                var screen = (SpotifyPhoneScreen) client.currentScreen;
                playback.state = track("No seek", true, false);
                screen.mouseClicked(click(screen, 100, 307), false);
                screen.mouseReleased(click(screen, 184, 307));
                require(playback.seeks.isEmpty(), "Unavailable seeking was dispatched");
                playback.state = SpotifyMedia.State.waiting();
                int count = playback.controls.size();
                screen.mouseClicked(click(screen, 155, 350), false);
                require(playback.controls.size() == count && playback.opened == 1, "Offline phone dispatched a skip");
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-spotify-phone-no-session");
            context.runOnClient(client -> {
                var screen = (SpotifyPhoneScreen) client.currentScreen;
                screen.mouseClicked(click(screen, 182, 56), false);
                require(client.currentScreen == null && field(screen, "cover") == null, "Close button leaked the phone texture");
            });
            System.out.println("[spotify-phone] PASS: real F10/Escape, shared bridge lifecycle, isolated previous/play/next, keyboard controls, seek/track-change guards, both hands, small-window render and cleanup");
        } finally {
            context.runOnClient(client -> {
                client.setScreen(null); phone.setEnabled(false); hud.setEnabled(false);
                phone.getSettings().forEach(setting -> setting.reset());
                client.options.hudHidden = false;
            });
            context.getInput().resizeWindow(1280, 720);
        }
    }

    private static SpotifyMedia.State track(String title, boolean available, boolean seek) {
        return new SpotifyMedia.State(available, title, "Maro Radio", "After Hours", "Spotify", false, seek,
            62000, 180000, System.currentTimeMillis(), "");
    }
    private static final class IsolatedPlayback implements SpotifyPlayback {
        SpotifyMedia.State state = track("Midnight Drive", true, true);
        final List<String> controls = new ArrayList<>();
        final List<Long> seeks = new ArrayList<>();
        int opened;
        final byte[] pixels = new byte[128 * 128 * 4];
        IsolatedPlayback() {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int i = (y * 128 + x) * 4;
                double glow = Math.max(0, 1 - Math.hypot(x - 64, y - 45) / 70);
                pixels[i] = (byte) (30 + 170 * glow); pixels[i + 1] = (byte) (20 + 85 * glow);
                pixels[i + 2] = (byte) (70 + 110 * glow); pixels[i + 3] = (byte) 255;
                if (y > 90 && y % 9 < 2) { pixels[i] = 20; pixels[i + 1] = 15; pixels[i + 2] = 45; }
            }
        }
        public SpotifyMedia.State state() { return state; }
        private SpotifyMedia.Artwork art;
        public SpotifyMedia.Artwork artwork() {
            if (!state.available()) return null;
            if (art == null || !art.key().equals(SpotifyMedia.artworkKey(state)))
                art = new SpotifyMedia.Artwork(SpotifyMedia.artworkKey(state), 128, 128, pixels, 0x8B508F, null, null);
            return art;
        }
        public void control(String action) { controls.add(action); }
        public void seekTo(long ms) { seeks.add(ms); }
        public void openSpotify() { opened++; }
    }
}
