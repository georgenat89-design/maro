package dev.maro.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.Maro;
import dev.maro.discord.DiscordIpc;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.List;

/**
 * Discord Rich Presence: your Discord profile shows Maro, with the logo, where you are playing and
 * for how long. It starts with the client and keeps trying quietly while Discord is closed.
 *
 * <p>Talks to the Discord app on this computer over its local socket ({@link DiscordIpc}); nothing
 * goes over the internet from here. The name it shows ("Playing Maro") is the Discord application's
 * whose ID is set below.
 */
public class DiscordPresence extends Module {
    /** The Maro Discord application. Empty until one is made; then the App ID setting fills it in. */
    public static final String DEFAULT_APP_ID = "";
    /** The logo, as Discord shows it: the client's own icon from the public repository. */
    public static final String LOGO = "https://raw.githubusercontent.com/georgenat89-design/maro/f460fb5af42ffd91842b512f12f7a7eac1990e61/src/main/resources/assets/maro/icon.png";
    private static final String DOWNLOAD = "https://github.com/georgenat89-design/maro/releases";

    private final TextSetting appId = add(new TextSetting("App ID", "The Application ID of your Discord app (discord.com/developers); its name is what Discord shows",
            DEFAULT_APP_ID, 24, "Discord Application ID"));
    private final BooleanSetting showServer = add(new BooleanSetting("Show Server", "The server's address, or Singleplayer", true));
    private final BooleanSetting showPlayer = add(new BooleanSetting("Show Player", "Your head and name in the corner of the logo", true));
    private final BooleanSetting showTime = add(new BooleanSetting("Show Time", "How long you have been playing", true));
    private final BooleanSetting showButton = add(new BooleanSetting("Download Button", "A Get Maro button on your profile (others see it, you do not)", true));
    private final TextSetting menuText = add(new TextSetting("Menu Text", "The second line while you are in the menus", "Maro Client", 64, "Maro Client"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Discord", appId),
            SettingSection.of("Shows", showServer, showPlayer, showTime, showButton, menuText));

    private final long startedAt = System.currentTimeMillis() / 1000;
    /** The activity wanted now, as JSON, made on the game thread for the Discord thread to send. */
    private volatile String wanted;
    private volatile String status = "Off";
    private volatile Thread worker;
    private int ticks;

    public DiscordPresence() {
        super("Discord Presence", "Shows Maro on your Discord profile: the logo, where you play and for how long", Category.MISC);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        wanted = activity().toString();
        Thread t = new Thread(this::run, "Maro Discord");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    @Override
    protected void onDisable() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
        status = "Off";
    }

    @Override
    public void onTick() {
        if (ticks++ % 20 == 0) wanted = activity().toString();
    }

    /** Connected, waiting for Discord, or what is wrong; shown in tests and logs. */
    public String status() {
        return status;
    }

    // ---- what Discord shows -----------------------------------------------------------------------

    /** Built on the game thread from what you are doing now. */
    public JsonObject activity() {
        JsonObject a = new JsonObject();
        boolean inGame = mc.world != null && mc.player != null;
        String details, state;
        if (!inGame) {
            details = "In the menus";
            state = menuText.get().isBlank() ? "Maro Client" : menuText.get();
        } else {
            if (mc.isInSingleplayer()) details = "Singleplayer";
            else {
                ServerInfo server = mc.getCurrentServerEntry();
                details = showServer.get() && server != null ? "Playing on " + server.address : "Multiplayer";
            }
            var dim = mc.world.getRegistryKey();
            state = dim == World.NETHER ? "In the Nether" : dim == World.END ? "In the End" : dim == World.OVERWORLD ? "In the Overworld" : "Exploring";
        }
        a.addProperty("details", details);
        a.addProperty("state", state);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", LOGO);
        assets.addProperty("large_text", "Maro Client " + Maro.VERSION);
        if (inGame && showPlayer.get()) {
            assets.addProperty("small_image", "https://mc-heads.net/avatar/" + mc.player.getUuid() + "/64");
            assets.addProperty("small_text", mc.player.getName().getString());
        }
        a.add("assets", assets);
        if (showTime.get()) {
            JsonObject time = new JsonObject();
            time.addProperty("start", startedAt);
            a.add("timestamps", time);
        }
        if (showButton.get()) {
            JsonArray buttons = new JsonArray();
            JsonObject button = new JsonObject();
            button.addProperty("label", "Get Maro");
            button.addProperty("url", DOWNLOAD);
            buttons.add(button);
            a.add("buttons", buttons);
        }
        return a;
    }

    // ---- the Discord thread -------------------------------------------------------------------------

    private void run() {
        DiscordIpc ipc = null;
        String sent = null;
        long lastSent = 0, nextTry = 0;
        try {
            while (worker == Thread.currentThread()) {
                String id = appId.get().trim();
                if (id.isEmpty()) {
                    status = "Needs an App ID";
                } else if (ipc == null) {
                    if (System.currentTimeMillis() >= nextTry) {
                        try {
                            ipc = DiscordIpc.connect(id);
                            sent = null;
                            status = "Connected";
                        } catch (IOException e) {
                            status = "Waiting for Discord (" + e.getMessage() + ")";
                            nextTry = System.currentTimeMillis() + 10_000;
                        }
                    }
                } else {
                    String now = wanted;
                    if (now != null && (!now.equals(sent) || System.currentTimeMillis() - lastSent > 60_000)) {
                        try {
                            ipc.setActivity(JsonParser.parseString(now).getAsJsonObject(), ProcessHandle.current().pid());
                            sent = now;
                            lastSent = System.currentTimeMillis();
                            status = "Connected";
                        } catch (IOException e) {
                            status = "Lost Discord (" + e.getMessage() + ")";
                            ipc.close();
                            ipc = null;
                            nextTry = System.currentTimeMillis() + 5_000;
                        }
                    }
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {
            // Turned off.
        } finally {
            if (ipc != null) {
                try {
                    ipc.setActivity(null, ProcessHandle.current().pid());
                } catch (IOException | RuntimeException ignored) {
                    // Gone already.
                }
                ipc.close();
            }
        }
    }
}
