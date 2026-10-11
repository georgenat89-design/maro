package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.JumpCircle;
import dev.maro.nathan.audio.SpotifyMedia;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.nio.file.Files;

final class JumpCircleChecks {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void setting(JumpCircle module, String name, Object value) {
        module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow().fromJson(
                value instanceof Boolean b ? new JsonPrimitive(b) : value instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(value.toString()));
    }
    private static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void field(Object object, String name, Object value) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void emit(JumpCircle module, double x, double y, double z, double now) {
        try {
            var m = JumpCircle.class.getDeclaredMethod("emit", double.class, double.class, double.class, double.class, boolean.class);
            m.setAccessible(true); m.invoke(module, x, y, z, now, false);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static int neon(Path path) {
        try {
            var image = ImageIO.read(path.toFile()); int count = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int c = image.getRGB(x, y), r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                if (b > 100 && (b > g * 1.25 && r > g * 1.3 || g > r * 1.3 && b > r * 1.3)) count++;
            }
            return count;
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void audio(SpotifyMedia media, boolean playing, float level) {
        field(media, "state", new SpotifyMedia.State(true, "Jump Circle test", "Maro", "Spotify", playing, true, 0, 180000, System.currentTimeMillis(), ""));
        field(media, "audioLevels", new float[] {level, level, level, 0, 0, 0, 0});
        field(media, "audioSampleAt", System.currentTimeMillis());
    }
    private static void renameChecks(ClientGameTestContext context, JumpCircle module) {
        context.runOnClient(c -> {
            String current = ConfigManager.getCurrent();
            String snapshot = ConfigManager.sanitize("jump-circle-baseline-" + java.util.UUID.randomUUID());
            String renamed = ConfigManager.sanitize("jump-circle-renamed-" + java.util.UUID.randomUUID());
            try {
                require(ConfigManager.save(snapshot), "Could not snapshot test config");
                setting(module, "Display Name", "  Lunar Pulse  ");
                require(module.getDisplayName().equals("Lunar Pulse"), "Custom name was not trimmed");
                require(module.getName().equals("Jump Circle") && ModuleManager.getByName("Jump Circle") == module,
                        "Renaming broke the stable module identity");
                for (String query : new String[] {"lunar", "lunarpulse", "PULSE", "jumpcircle"})
                    require(ModuleManager.search(query).stream().filter(m -> m == module).count() == 1,
                            "Renamed module missing or duplicated in search: " + query);
                require(ConfigManager.save(renamed), "Could not save custom name");
                var modules = JsonParser.parseString(Files.readString(ConfigManager.CONFIG_DIR.resolve(renamed + ".json")))
                        .getAsJsonObject().getAsJsonObject("modules");
                require(modules.has("Jump Circle") && !modules.has("Lunar Pulse"), "Custom label changed the config key");
                setting(module, "Display Name", "");
                require(module.getDisplayName().equals("Jump Circle"), "Blank name did not restore the default");
                require(ConfigManager.load(renamed) && module.getDisplayName().equals("Lunar Pulse"),
                        "Config reload lost the custom name");
                setting(module, "Display Name", "   ");
                require(module.getDisplayName().equals("Jump Circle"), "Whitespace name left an empty menu entry");
            } catch (Exception e) { throw new AssertionError(e); }
            finally {
                if (ConfigManager.exists(snapshot)) ConfigManager.load(snapshot);
                ConfigManager.delete(snapshot); ConfigManager.delete(renamed);
                try { var f = ConfigManager.class.getDeclaredField("current"); f.setAccessible(true); f.set(null, current); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        });
        String menuStyle = ClientSettings.menuStyle.get();
        try {
            for (String layout : new String[] {"Window", "Panels"}) {
                context.runOnClient(c -> {
                    ClientSettings.menuStyle.set(layout);
                    setting(module, "Display Name", "Lunar Pulse");
                    var gui = new ClickGuiScreen(); c.setScreen(gui);
                    if (layout.equals("Window")) gui.openModuleSettings(module, "Circles");
                    else {
                        gui.getSearch().setText("lunar");
                        Object panels = field(gui, "panels");
                        try { var m = panels.getClass().getDeclaredMethod("openSettings", Module.class); m.setAccessible(true); m.invoke(panels, module); }
                        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    }
                });
                context.waitTicks(10);
                // Rename while the settings are open: the title must update without reopening.
                context.runOnClient(c -> setting(module, "Display Name", "Lunar Pulse Live"));
                context.waitTicks(5);
                context.takeScreenshot("maro-jump-circle-renamed-" + layout.toLowerCase());
                context.setScreen(() -> null);
            }
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(c -> { ClientSettings.menuStyle.set(menuStyle); setting(module, "Display Name", ""); });
        }
        System.out.println("[jump-circle] PASS: custom name, canonical/alias search, stable config key, save/reload and both menu layouts");
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        JumpCircle module = ModuleManager.get(JumpCircle.class);
        require(module != null, "Jump Circle was not registered");
        renameChecks(context, module);
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        var original = context.computeOnClient(c -> c.player.getEntityPos());
        boolean hidden = context.computeOnClient(c -> c.options.hudHidden);
        try {
            world.getServer().runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
            world.getServer().runCommand("fill -8 100 -8 8 105 8 minecraft:air");
            world.getServer().runCommand("tp @a 0.5 100 0.5 0 35");
            context.waitTicks(8);
            context.runOnClient(c -> {
                c.options.hudHidden = true; c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                c.player.getAbilities().flying = false;
                module.getSettings().forEach(s -> s.reset()); setting(module, "Trigger", "Both"); module.setEnabled(true);
            });
            context.waitTicks(3);
            int before = module.emittedCount();
            context.getInput().holdKey(o -> o.jumpKey);
            for (int i = 0; i < 8 && context.computeOnClient(c -> module.emittedCount()) == before; i++) context.waitTick();
            context.getInput().releaseKey(o -> o.jumpKey);
            require(context.computeOnClient(c -> module.emittedCount()) > before, "A real jump did not create a circle");
            context.waitTicks(4);
            int visible = neon(context.takeScreenshot("maro-jump-circle-orbit"));
            require(visible > 100, "Jump Circle did not draw visible neon geometry: " + visible);
            for (int i = 0; i < 35 && context.computeOnClient(c -> module.emittedCount()) < before + 2; i++) context.waitTick();
            require(context.computeOnClient(c -> module.emittedCount()) >= before + 2, "Landing did not create a circle in Both mode");
            for (String style : new String[] {"Neon", "Ripple"}) {
                context.runOnClient(c -> {
                    module.setEnabled(false); module.setEnabled(true); setting(module, "Style", style); module.onTick();
                    emit(module, c.player.getX(), c.player.getY(), c.player.getZ(), c.world.getTime());
                });
                context.waitTicks(6);
                require(neon(context.takeScreenshot("maro-jump-circle-" + style.toLowerCase())) > 100, "Style did not render: " + style);
            }
            // The optional sync acquires its own shared bridge, with the Spotify HUD disabled.
            context.runOnClient(c -> {
                setting(module, "Spotify Sync", true);
                SpotifyMedia media = (SpotifyMedia) field(module, "media"); require(media != null, "Sync did not acquire the shared media session");
                media.close(); audio(media, true, 0);
            });
            context.waitTicks(3);
            int quietCount = context.computeOnClient(c -> module.emittedCount());
            context.runOnClient(c -> audio((SpotifyMedia) field(module, "media"), true, 1));
            context.waitTicks(3);
            require(context.computeOnClient(c -> module.beatLevel()) > .1, "Live audio bands did not pulse the circle");
            require(context.computeOnClient(c -> module.emittedCount()) > quietCount, "A bass onset did not create a standing beat ripple");
            context.takeScreenshot("maro-jump-circle-spotify-beat");
            int activeCount = context.computeOnClient(c -> module.emittedCount());
            context.runOnClient(c -> audio((SpotifyMedia) field(module, "media"), false, 1));
            context.waitTicks(16);
            require(context.computeOnClient(c -> module.emittedCount()) == activeCount, "Paused Spotify created beat ripples");
            require(context.computeOnClient(c -> module.beatLevel()) < .05, "Paused Spotify did not release the beat envelope");
            context.runOnClient(c -> {
                setting(module, "Spotify Sync", false); require(field(module, "media") == null, "Disabling sync retained a media session");
                for (int i = 0; i < 60; i++) emit(module, .5, 100, .5, c.world.getTime());
                require(module.circleCount() == 32, "Circle storage was not bounded");
            });
            context.waitTicks(40);
            require(context.computeOnClient(c -> module.circleCount()) == 0, "Expired circles remained in memory");
            System.out.println("[jump-circle] PASS: real jump/landing, three styles, live bass onset, pause release, shared session and bounded cleanup");
        } finally {
            context.getInput().releaseKey(o -> o.jumpKey);
            context.runOnClient(c -> {
                module.setEnabled(false); module.getSettings().forEach(s -> s.reset());
                c.options.hudHidden = hidden; c.options.setPerspective(perspective);
            });
            world.getServer().runCommand("tp @a " + original.x + " " + original.y + " " + original.z);
            context.waitTicks(3);
        }
    }
}
