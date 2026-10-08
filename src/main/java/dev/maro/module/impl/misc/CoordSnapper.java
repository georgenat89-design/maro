package dev.maro.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.maro.Maro;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.util.math.BlockPos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Coord Snapper: press its key and where you stand is snapped. It is copied, shown in a
 * notification, and sent to your Discord webhook as a neat card with the dimension, server and
 * which way you face, if you have given it one. Nothing is sent anywhere else, and nothing at all
 * unless you press the key.
 *
 * <p>Its key is the module's own: pressing it switches the module on, which snaps once and
 * switches it straight back off, ready for the next press.
 */
public class CoordSnapper extends Module {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final String[] FACING = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};

    private final TextSetting webhook = add(new TextSetting("Webhook URL",
            "Your Discord webhook (Channel settings, Integrations, Webhooks, Copy Webhook URL). Empty: nothing is sent",
            "", 300, "https://discord.com/api/webhooks/…"));
    private final BooleanSetting notification = add(new BooleanSetting("Notification", "Show the coordinates in a notification", true));
    private final BooleanSetting clipboard = add(new BooleanSetting("Copy", "Copy the coordinates, ready to paste", true));
    private final BooleanSetting server = add(new BooleanSetting("Include Server", "Put the server's address in the Discord card", true));
    private final ButtonSetting snapNow = add(new ButtonSetting("Snap Now", "Snap where you are now", "Snap", this::snap));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Coord Snapper", webhook, notification, clipboard, server, snapNow));

    private boolean pending;
    /** The last coordinates snapped, as copied; for tests. */
    private String last = "";

    public CoordSnapper() {
        super("Coord Snapper", "Press its key to snap your coordinates: copied, shown and sent to your Discord webhook", Category.MISC);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        // Snapped on the next tick, then off again: the key is a button, not a switch.
        pending = true;
    }

    @Override
    public void onTick() {
        if (!pending) return;
        pending = false;
        snap();
        setEnabled(false);
    }

    /** Snaps where you are: copies it, shows it and sends it, as set. */
    public void snap() {
        if (!inGame()) {
            Notifications.push(getName(), "Join a world first", Notifications.Type.INFO);
            return;
        }
        BlockPos pos = mc.player.getBlockPos();
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        last = coords;
        if (clipboard.get()) mc.keyboard.setClipboard(coords);

        String url = webhook.get().trim();
        boolean sending = !url.isEmpty();
        if (sending && !looksLikeWebhook(url)) {
            Notifications.push(getName(), "That webhook URL does not look right", Notifications.Type.ERROR);
            sending = false;
        }
        if (notification.get()) {
            String extra = (clipboard.get() ? "  ·  copied" : "") + (sending ? "  ·  sending" : "");
            Notifications.push("Coords snapped", "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ() + extra, Notifications.Type.SUCCESS);
        }
        if (sending) send(url, payload());
    }

    private static boolean looksLikeWebhook(String url) {
        return url.startsWith("https://") && url.contains("/api/webhooks/");
    }

    /** The Discord card: coordinates, dimension, server and facing. */
    public String payload() {
        BlockPos pos = mc.player.getBlockPos();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Coords snapped");
        embed.addProperty("color", 0x7B2CFF);
        JsonArray fields = new JsonArray();
        fields.add(field("Coordinates", "`X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ() + "`", false));
        fields.add(field("Dimension", dimension(), true));
        fields.add(field("Facing", FACING[Math.floorMod(Math.round(mc.player.getYaw() / 45f), 8)], true));
        if (server.get()) {
            var entry = mc.getCurrentServerEntry();
            fields.add(field("Server", entry != null ? entry.address : "Single player", true));
        }
        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "maro.gg  ·  " + mc.player.getName().getString());
        embed.add("footer", footer);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject body = new JsonObject();
        body.addProperty("username", "Coord Snapper");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        return body.toString();
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private String dimension() {
        String path = mc.world.getRegistryKey().getValue().getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path.replace('_', ' ');
        };
    }

    /** Sends off the game thread; how it went comes back as a notification. */
    private void send(String url, String json) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            Notifications.push(getName(), "That webhook URL does not look right", Notifications.Type.ERROR);
            return;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> mc.execute(() -> {
            if (error != null) {
                Maro.LOGGER.warn("Coord Snapper could not reach the webhook", error);
                Notifications.push(getName(), "Could not reach Discord", Notifications.Type.ERROR);
            } else if (response.statusCode() / 100 != 2) {
                Notifications.push(getName(), String.format(Locale.ROOT, "Discord said no (%d): check the webhook URL", response.statusCode()),
                        Notifications.Type.ERROR);
            } else if (notification.get()) {
                Notifications.push(getName(), "Sent to Discord", Notifications.Type.SUCCESS);
            }
        }));
    }

    public String lastSnapped() {
        return last;
    }
}
