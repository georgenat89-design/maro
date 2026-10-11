package dev.maro.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A minimal client for Discord's local RPC socket: the named pipe {@code \\.\pipe\discord-ipc-N}
 * on Windows, a Unix socket {@code discord-ipc-N} in the runtime folder elsewhere. Frames are an
 * opcode and a length (little-endian ints) and then JSON. Enough to say hello and set the activity.
 */
public final class DiscordIpc implements Closeable {
    private static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2, PING = 3, PONG = 4;

    /** Where to look for the socket; a test points this at its own. */
    public static volatile Path override;

    private interface Pipe extends Closeable {
        void write(ByteBuffer buffer) throws IOException;

        void readFully(ByteBuffer buffer) throws IOException;
    }

    private final Pipe pipe;

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    /** Connects to the first Discord that answers and says hello with the application's id. */
    public static DiscordIpc connect(String clientId) throws IOException {
        IOException last = new IOException("Discord is not running");
        for (Path path : candidates()) {
            Pipe pipe;
            try {
                pipe = open(path);
            } catch (IOException e) {
                last = e;
                continue;
            }
            DiscordIpc ipc = new DiscordIpc(pipe);
            try {
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", clientId);
                ipc.send(HANDSHAKE, hello);
                JsonObject ready = ipc.receive();
                if (ready.has("code") && !ready.has("evt")) throw new IOException("Discord refused: " + ready.get("message"));
                if (ready.has("evt") && "ERROR".equals(ready.get("evt").getAsString())) {
                    throw new IOException("Discord refused: " + ready.getAsJsonObject("data").get("message"));
                }
                return ipc;
            } catch (IOException | RuntimeException e) {
                ipc.close();
                last = e instanceof IOException io ? io : new IOException(e);
            }
        }
        throw last;
    }

    private static List<Path> candidates() {
        List<Path> out = new ArrayList<>();
        if (override != null) {
            out.add(override);
            return out;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            if (windows) {
                out.add(Path.of("\\\\.\\pipe\\discord-ipc-" + i));
                continue;
            }
            for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String dir = System.getenv(env);
                if (dir == null || dir.isBlank()) continue;
                for (String sub : new String[] {"", "app/com.discordapp.Discord", "snap.discord", ".flatpak/com.discordapp.Discord/xdg-run"}) {
                    Path p = Path.of(dir, sub, "discord-ipc-" + i);
                    if (Files.exists(p)) out.add(p);
                }
            }
            Path tmp = Path.of("/tmp", "discord-ipc-" + i);
            if (Files.exists(tmp)) out.add(tmp);
        }
        return out;
    }

    private static Pipe open(Path path) throws IOException {
        if (path.toString().startsWith("\\\\")) {
            RandomAccessFile file = new RandomAccessFile(path.toString(), "rw");
            return new Pipe() {
                @Override
                public void write(ByteBuffer buffer) throws IOException {
                    file.write(buffer.array(), buffer.position(), buffer.remaining());
                }

                @Override
                public void readFully(ByteBuffer buffer) throws IOException {
                    file.readFully(buffer.array(), buffer.position(), buffer.remaining());
                    buffer.position(buffer.limit());
                }

                @Override
                public void close() throws IOException {
                    file.close();
                }
            };
        }
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(path));
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        return new Pipe() {
            @Override
            public void write(ByteBuffer buffer) throws IOException {
                while (buffer.hasRemaining()) channel.write(buffer);
            }

            @Override
            public void readFully(ByteBuffer buffer) throws IOException {
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) throw new IOException("Discord closed the connection");
                }
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }

    /** Sets what you are doing; null clears it. */
    public void setActivity(JsonObject activity, long pid) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        if (activity != null) args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        send(FRAME, command);
        JsonObject reply = receive();
        if (reply.has("evt") && !reply.get("evt").isJsonNull() && "ERROR".equals(reply.get("evt").getAsString())) {
            throw new IOException("Discord rejected the activity: " + reply.getAsJsonObject("data").get("message"));
        }
    }

    private synchronized void send(int opcode, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode).putInt(body.length).put(body).flip();
        pipe.write(buffer);
    }

    /** The next frame's JSON, answering pings on the way. */
    private JsonObject receive() throws IOException {
        while (true) {
            ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            pipe.readFully(head);
            head.flip();
            int opcode = head.getInt(), length = head.getInt();
            if (length < 0 || length > 1 << 20) throw new IOException("Bad frame from Discord");
            ByteBuffer body = ByteBuffer.allocate(length);
            pipe.readFully(body);
            String text = new String(body.array(), StandardCharsets.UTF_8);
            JsonObject json = text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
            if (opcode == PING) {
                send(PONG, json);
                continue;
            }
            if (opcode == CLOSE) throw new IOException("Discord closed: " + json.get("message"));
            return json;
        }
    }

    @Override
    public void close() {
        try {
            send(CLOSE, new JsonObject());
        } catch (IOException | RuntimeException ignored) {
            // Gone already.
        }
        try {
            pipe.close();
        } catch (IOException ignored) {
            // Gone already.
        }
    }
}
