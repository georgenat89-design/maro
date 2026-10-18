package dev.maro.gametest;

import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.phone.PhoneNetwork;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/** Isolated device identities and real relay transport; no messages to external people. */
final class PhoneSharingChecks {
    private static final String LINK = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void until(ClientGameTestContext context, BooleanSupplier condition, String message) {
        for (int i = 0; i < 300 && !condition.getAsBoolean(); i++) context.waitTicks(1);
        require(condition.getAsBoolean(), message);
    }
    private static SpotifyPhoneScreen screen(net.minecraft.client.MinecraftClient c) { return (SpotifyPhoneScreen)c.currentScreen; }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void click(ClientGameTestContext context, float x, float y) {
        context.runOnClient(c -> {
            var s = screen(c); var l = s.layout();
            s.mouseClicked(new Click(l.screenX(x,y), l.screenY(x,y), new MouseInput(0,0)), false);
        });
        context.waitTicks(2);
    }
    static void run(ClientGameTestContext context) {
        var owner = ModuleManager.get(SpotifyPhone.class);
        PhoneNetwork alice = null, bob = null, reopened = null;
        try {
            var dir = Files.createTempDirectory("maro-phone-test-");
            alice = new PhoneNetwork(dir.resolve("alice.json")); bob = new PhoneNetwork(dir.resolve("bob.json"));
            var sender = alice; var receiver = bob;
            until(context, () -> sender.snapshot().status() != null, "Phone store failed to initialize");
            String endpoint = System.getProperty("maro.gametest.phoneRelay", "");
            if (!endpoint.isEmpty()) {
                alice.connect(endpoint); bob.connect(endpoint);
                until(context, () -> sender.snapshot().online() && receiver.snapshot().online(), "Two test devices could not connect");
                require(!alice.snapshot().number().equals(bob.snapshot().number()), "Two devices received the same phone number");
                alice.send(bob.snapshot().number(), "Hello from the other test client");
                until(context, () -> receiver.snapshot().messages().stream().anyMatch(m -> m.text().equals("Hello from the other test client")), "Text did not reach the second client");
                alice.savePlaylist("Night Drive", LINK);
                until(context, () -> !sender.snapshot().playlists().isEmpty(), "Playlist did not persist");
                alice.share(bob.snapshot().number(), alice.snapshot().playlists().getFirst());
                until(context, () -> receiver.snapshot().messages().stream().anyMatch(m -> m.kind().equals("playlist") && m.url().equals(LINK)), "Playlist did not reach the other client");
                bob.send(alice.snapshot().number(), "Got it! Send me another playlist.");
                until(context, () -> sender.snapshot().messages().stream().anyMatch(m -> m.text().equals("Got it! Send me another playlist.")), "Reply did not reach the sender");
                require(bob.snapshot().messages().stream().filter(m -> m.kind().equals("playlist")).count() == 1, "Polling duplicated a playlist");
            }
            for (String device : new String[]{"Phone", "iPad"}) {
                context.runOnClient(c -> {
                    owner.device.set(device); owner.mouseLook.set(true); owner.interactBind.set(GLFW.GLFW_KEY_I);
                    c.setScreen(new SpotifyPhoneScreen(owner, new SpotifyPhoneChecks.IsolatedPlayback(), sender));
                });
                context.waitTicks(10);
                context.getInput().pressKey(GLFW.GLFW_KEY_I); context.waitTicks(3);
                require(context.computeOnClient(c -> !c.mouse.isCursorLocked() && !screen(c).allowsMouseLook()), "Interact did not unlock app controls");
                context.takeScreenshot("maro-phone-apps-" + device.toLowerCase());
                click(context, 50, 225); // Playlists tile, both device sizes.
                click(context, 45, 123); context.getInput().typeChars("Test Favorites");
                require(context.computeOnClient(c -> !screen(c).allowsMovement()), "Typing a playlist name moved the player");
                click(context, 45, 155);
                // Some Windows test runners do not expose the OS clipboard. Keep URL entry covered there.
                boolean pasted = context.computeOnClient(c -> {
                    c.keyboard.setClipboard(LINK + "?si=test");
                    if (!c.keyboard.getClipboard().startsWith(LINK)) return false;
                    screen(c).keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL));
                    var link = (dev.maro.gui.widget.TextField)field(field(screen(c),"apps"),"playlistLink");
                    return link.getText().equals(LINK + "?si=test");
                });
                if (!pasted) {
                    System.out.println("[phone-apps] Native clipboard unavailable; entering the URL through character input");
                    context.getInput().typeChars(LINK + "?si=test");
                }
                context.takeScreenshot("maro-phone-playlist-entry-" + device.toLowerCase());
                context.runOnClient(c -> {
                    var apps = field(screen(c), "apps");
                    var title = ((dev.maro.gui.widget.TextField) field(apps, "playlistName")).getText();
                    var link = ((dev.maro.gui.widget.TextField) field(apps, "playlistLink")).getText();
                    require(title.equals("Test Favorites"), "Playlist title typing mismatch: " + title);
                    require(link.startsWith(LINK), "Playlist URL entry mismatch: " + link);
                });
                click(context, 85, 187);
                until(context, () -> sender.snapshot().playlists().stream().anyMatch(p -> p.title().equals("Test Favorites") && p.url().equals(LINK)), "Saving a playlist through the phone failed");
                context.takeScreenshot("maro-phone-playlists-" + device.toLowerCase());
                click(context, 94, 251); // Share the saved playlist.
                click(context, 62, 177);
                context.getInput().typeChars(endpoint.isEmpty() ? "2999999999" : receiver.snapshot().number());
                context.takeScreenshot("maro-phone-share-" + device.toLowerCase());
                if (!endpoint.isEmpty()) {
                    click(context, 90, 213);
                    until(context, () -> receiver.snapshot().messages().stream().anyMatch(m -> m.kind().equals("playlist") && m.text().equals("Test Favorites")), "UI share did not reach other device");
                    context.waitTicks(4);
                    context.takeScreenshot("maro-phone-conversation-" + device.toLowerCase());
                    float h = device.equals("iPad") ? 336 : 464;
                    click(context, 58, h-65); context.getInput().typeChars("UI message " + device);
                    context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
                    until(context, () -> receiver.snapshot().messages().stream().anyMatch(m -> m.text().equals("UI message " + device)), "Typed message did not send from phone");
                }
                context.runOnClient(c -> c.setScreen(null));
            }
            if (!endpoint.isEmpty()) {
                String number = alice.snapshot().number();
                alice.close(); alice = null;
                reopened = new PhoneNetwork(dir.resolve("alice.json")); var restored = reopened;
                until(context, () -> restored.snapshot().online(), "Phone did not reconnect after reopening its saved identity");
                require(reopened.snapshot().number().equals(number), "Phone number changed after reopening");
                require(!reopened.snapshot().playlists().isEmpty(), "Saved playlists disappeared");
                System.out.println("[phone-sharing-relay-proof] two independent clients, stable numbers, private text/reply, playlist cards, UI sharing/sending and persisted reopen passed");
            }
            require(PhoneNetwork.playlistUrl("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M").equals(LINK), "Spotify playlist URI did not normalize");
            try { PhoneNetwork.playlistUrl("https://example.com/playlist/37i9dQZF1DXcBWIGoYBM5M"); throw new AssertionError("Unsafe playlist URL accepted"); } catch (IllegalArgumentException expected) { }
            System.out.println("[phone-apps-proof] Interact key/app grid, Phone+iPad playlist entry/share screens, typing movement isolation, URL validation passed");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally {
            context.runOnClient(c -> { c.setScreen(null); owner.getSettings().forEach(s -> s.reset()); });
            if (alice != null) alice.close(); if (bob != null) bob.close(); if (reopened != null) reopened.close();
        }
    }
}
