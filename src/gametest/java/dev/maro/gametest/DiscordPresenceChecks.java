package dev.maro.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.discord.DiscordIpc;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.DiscordPresence;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Discord Presence against a stand-in Discord on a local socket: it is on from the start, says
 * hello with the App ID, and sends an activity with the logo, where you are and the time.
 */
final class DiscordPresenceChecks {
    private DiscordPresenceChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        try {
            check(context);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void check(ClientGameTestContext context) throws IOException {
        DiscordPresence module = ModuleManager.get(DiscordPresence.class);
        require(module != null, "Discord Presence was not registered");
        require(module.enabledByDefault(), "Discord Presence does not start with the client");

        Path socket = Files.createTempDirectory("maro-discord").resolve("discord-ipc-0");
        List<JsonObject> frames = new CopyOnWriteArrayList<>();
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        Thread fake = new Thread(() -> {
            try (SocketChannel client = server.accept()) {
                while (true) {
                    ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                    while (head.hasRemaining()) if (client.read(head) < 0) return;
                    head.flip();
                    int opcode = head.getInt(), length = head.getInt();
                    ByteBuffer body = ByteBuffer.allocate(length);
                    while (body.hasRemaining()) if (client.read(body) < 0) return;
                    JsonObject json = JsonParser.parseString(new String(body.array(), StandardCharsets.UTF_8)).getAsJsonObject();
                    json.addProperty("_op", opcode);
                    frames.add(json);
                    if (opcode == 2) return;
                    // Answer as Discord does: READY to the hello, the command echoed back otherwise.
                    JsonObject reply = new JsonObject();
                    if (opcode == 0) {
                        reply.addProperty("cmd", "DISPATCH");
                        reply.addProperty("evt", "READY");
                    } else {
                        reply.addProperty("cmd", "SET_ACTIVITY");
                        reply.add("data", new JsonObject());
                        reply.add("evt", null);
                    }
                    byte[] out = reply.toString().getBytes(StandardCharsets.UTF_8);
                    ByteBuffer frame = ByteBuffer.allocate(8 + out.length).order(ByteOrder.LITTLE_ENDIAN);
                    frame.putInt(1).putInt(out.length).put(out).flip();
                    while (frame.hasRemaining()) client.write(frame);
                }
            } catch (IOException ignored) {
                // The test is over.
            }
        }, "Fake Discord");
        fake.setDaemon(true);
        fake.start();

        TextSetting appId = (TextSetting) module.getSettings().stream().filter(s -> s.getName().equals("App ID")).findFirst().orElseThrow();
        boolean wasOn = context.computeOnClient(c -> module.isEnabled());
        try {
            DiscordIpc.override = socket;
            context.runOnClient(c -> {
                module.setEnabled(false);
                appId.set("1234567890");
                module.setEnabled(true);
            });
            for (int i = 0; i < 60 && frames.stream().noneMatch(f -> f.has("cmd")); i++) context.waitTicks(5);
            System.out.println("DISCORD frames: " + frames + " status " + module.status());
            require(!frames.isEmpty() && frames.getFirst().get("_op").getAsInt() == 0
                    && "1234567890".equals(frames.getFirst().get("client_id").getAsString()), "Discord Presence did not say hello with the App ID: " + frames);
            JsonObject set = frames.stream().filter(f -> f.has("cmd")).findFirst().orElseThrow(() -> new AssertionError("No activity was sent: " + frames));
            JsonObject activity = set.getAsJsonObject("args").getAsJsonObject("activity");
            require(activity.getAsJsonObject("assets").get("large_image").getAsString().equals(DiscordPresence.LOGO), "The activity has no logo: " + activity);
            require(activity.has("details") && activity.has("timestamps"), "The activity has no details or time: " + activity);
            require(module.status().equals("Connected"), "Discord Presence is not connected: " + module.status());
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(wasOn);
            });
            DiscordIpc.override = null;
            context.waitTicks(5);
            server.close();
            Files.deleteIfExists(socket);
        }
    }
}
