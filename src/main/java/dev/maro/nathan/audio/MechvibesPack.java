package dev.maro.nathan.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

/**
 * A Mechvibes sound pack, read the way Mechvibes reads it.
 *
 * <p>A pack is a folder with a {@code config.json} and its sound. The config
 * names the pack and says, key by key, what that key sounds like: in a
 * <i>single</i> pack - which EG Oreo is - one recording of the whole keyboard
 * being typed across, and for each key where in it that key is, as a start and
 * a length in milliseconds. So every key is the recording of <i>that</i> key,
 * and the spacebar, Enter and Backspace are themselves and not an ordinary key
 * pitched about. A <i>multi</i> pack has a file a key instead, and is read too.
 *
 * <p>Mechvibes numbers keys the way the library it listens with does: the
 * keyboard's own scan codes, 1 for Escape, 57 for the spacebar, with the keys
 * that came later - the arrows, the right-hand Control - in ranges of their
 * own. The game knows keys by GLFW's names, so there is a table from one to the
 * other, written out key by key.
 *
 * <p>Nothing of any pack is part of this addon. {@link #importFromMechvibes}
 * copies one out of a Mechvibes that is installed on this machine, whose it is.
 */
public final class MechvibesPack {
    /** One key's sound, ready for the sound card. */
    public record Clip(ShortBuffer pcm, int channels, int rate) {
    }

    /** What to do with a slice's last moment, so that a cut in the recording is not a click. */
    private static final double FADE_MS = 3;

    /** Where the loudest key of a pack is brought to, of full scale: where the addon's own samples peak. */
    private static final double TARGET_PEAK = 0.6;

    public final String name;

    private final Map<Integer, Clip> clips = new HashMap<>();
    private final List<Integer> ordinary = new ArrayList<>();

    private MechvibesPack(String name) {
        this.name = name;
    }

    public int size() {
        return clips.size();
    }

    /** The sound of a key, by GLFW's name for it. A key the pack says nothing about gets one of its letter keys. */
    public Clip clipFor(int glfwKey, java.util.Random random) {
        for (int code : codesFor(glfwKey)) {
            Clip clip = clips.get(code);
            if (clip != null) return clip;
        }

        return ordinary.isEmpty() ? null : clips.get(ordinary.get(random.nextInt(ordinary.size())));
    }

    // ------------------------------------------------------------------ reading

    /** Reads the pack in a folder. Says what is wrong with it, in words, if it cannot. */
    public static MechvibesPack load(Path folder) throws IOException {
        Path config = folder.resolve("config.json");

        if (!Files.isRegularFile(config)) throw new IOException("there is no config.json in the pack folder");

        JsonObject json;

        try {
            json = JsonParser.parseString(Files.readString(config, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("config.json is not valid JSON: " + e.getMessage());
        }

        MechvibesPack pack = new MechvibesPack(json.has("name") ? json.get("name").getAsString() : folder.getFileName().toString());
        JsonObject defines = json.has("defines") && json.get("defines").isJsonObject() ? json.getAsJsonObject("defines") : new JsonObject();
        boolean single = !json.has("key_define_type") || "single".equalsIgnoreCase(json.get("key_define_type").getAsString());

        if (single) {
            if (!json.has("sound")) throw new IOException("config.json names no sound file");

            Decoded whole = decode(folder.resolve(json.get("sound").getAsString()));

            for (Map.Entry<String, JsonElement> define : defines.entrySet()) {
                if (!define.getValue().isJsonArray()) continue;

                JsonArray span = define.getValue().getAsJsonArray();
                if (span.size() < 2) continue;

                Clip clip = whole.slice(span.get(0).getAsDouble(), span.get(1).getAsDouble());
                if (clip != null) pack.put(define.getKey(), clip);
            }
        } else {
            Map<String, Clip> files = new HashMap<>();

            for (Map.Entry<String, JsonElement> define : defines.entrySet()) {
                if (!define.getValue().isJsonPrimitive()) continue;

                String file = define.getValue().getAsString();
                Clip clip = files.get(file);

                if (clip == null) {
                    Decoded decoded = decode(folder.resolve(file));

                    clip = decoded.slice(0, decoded.frames * 1000.0 / decoded.rate);
                    files.put(file, clip);
                }

                if (clip != null) pack.put(define.getKey(), clip);
            }
        }

        if (pack.clips.isEmpty()) throw new IOException("config.json gives no key a sound");

        pack.level();

        return pack;
    }

    /**
     * Brings the pack up to the level of the addon's own samples. Recordings
     * come in at whatever level they were made at - EG Oreo peaks at about a
     * third of full scale - and a source cannot be turned up past what is in
     * its buffer, so it is done here, once. Every clip is scaled by the same
     * amount, worked out from the loudest, so a quiet key is still the quiet
     * key it was beside the others, and nothing is pushed past full scale.
     */
    private void level() {
        Map<Clip, Boolean> each = new java.util.IdentityHashMap<>();
        int loudest = 1;

        for (Clip clip : clips.values()) each.put(clip, true);

        for (Clip clip : each.keySet()) {
            for (int i = 0; i < clip.pcm().limit(); i++) loudest = Math.max(loudest, Math.abs(clip.pcm().get(i)));
        }

        double gain = Math.min(8, TARGET_PEAK * 32767 / loudest);

        if (Math.abs(gain - 1) < 0.01) return;

        for (Clip clip : each.keySet()) {
            for (int i = 0; i < clip.pcm().limit(); i++) clip.pcm().put(i, (short) Math.round(clip.pcm().get(i) * gain));
        }
    }

    private void put(String key, Clip clip) {
        int code;

        try {
            code = Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            return;
        }

        clips.put(code, clip);

        // The three rows of letters: what a key nobody has named sounds like.
        if ((code >= 16 && code <= 25) || (code >= 30 && code <= 38) || (code >= 44 && code <= 50)) ordinary.add(code);
    }

    /** A whole sound file as 16 bit samples. */
    private record Decoded(short[] samples, int channels, int rate, int frames) {
        Clip slice(double startMs, double lengthMs) {
            int from = (int) Math.round(startMs * rate / 1000);
            int count = (int) Math.round(lengthMs * rate / 1000);

            from = Math.max(0, Math.min(frames, from));
            count = Math.max(0, Math.min(frames - from, count));

            if (count == 0) return null;

            ShortBuffer pcm = BufferUtils.createShortBuffer(count * channels);
            int fade = Math.min(count, (int) (FADE_MS * rate / 1000));

            for (int i = 0; i < count; i++) {
                // Only the very end is touched, and only down to nothing.
                double gain = i >= count - fade ? (count - 1 - i) / (double) fade : 1;

                for (int c = 0; c < channels; c++) pcm.put((short) Math.round(samples[(from + i) * channels + c] * gain));
            }

            pcm.flip();

            return new Clip(pcm, channels, rate);
        }
    }

    private static Decoded decode(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("the sound file " + file.getFileName() + " is missing");

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] bytes = Files.readAllBytes(file);

        if (name.endsWith(".ogg")) return decodeOgg(bytes, name);
        if (name.endsWith(".wav")) return decodeWav(bytes, name);

        throw new IOException(name + " is neither .ogg nor .wav, which are what can be read here");
    }

    private static Decoded decodeOgg(byte[] bytes, String name) throws IOException {
        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
        data.put(bytes).flip();

        // Opened, read through and closed by hand. The one-call decoder hands
        // back memory of its own that has to be freed by whoever made it, and
        // this way there is none: the samples go straight into a buffer of ours.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_memory(data, error, null);

            if (decoder == 0) throw new IOException(name + " could not be decoded as Ogg Vorbis (error " + error.get(0) + ")");

            try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                STBVorbis.stb_vorbis_get_info(decoder, info);

                int channels = info.channels();
                int rate = info.sample_rate();
                int frames = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

                if (channels < 1 || channels > 2 || frames <= 0) throw new IOException(name + " is not mono or stereo sound");

                ShortBuffer pcm = BufferUtils.createShortBuffer(frames * channels);
                int got = 0;

                while (got < frames) {
                    pcm.position(got * channels);

                    int read = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                    if (read <= 0) break;

                    got += read;
                }

                short[] samples = new short[got * channels];

                pcm.position(0);
                pcm.get(samples);

                return new Decoded(samples, channels, rate, got);
            } finally {
                STBVorbis.stb_vorbis_close(decoder);
            }
        }
    }

    private static Decoded decodeWav(byte[] bytes, String name) throws IOException {
        ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        if (bytes.length < 44 || file.getInt(0) != 0x46464952 || file.getInt(8) != 0x45564157) throw new IOException(name + " is not a wave file");

        int channels = 0;
        int rate = 0;
        int bits = 0;

        for (int at = 12; at + 8 <= bytes.length; ) {
            int id = file.getInt(at);
            int size = file.getInt(at + 4);
            int body = at + 8;

            if (size < 0 || body + size > bytes.length) size = bytes.length - body;

            if (id == 0x20746d66 && size >= 16) {
                if (file.getShort(body) != 1) break;

                channels = file.getShort(body + 2);
                rate = file.getInt(body + 4);
                bits = file.getShort(body + 14);
            } else if (id == 0x61746164) {
                if (bits != 16 || (channels != 1 && channels != 2)) break;

                short[] samples = new short[size / 2];
                file.position(body);
                file.asShortBuffer().get(samples);

                return new Decoded(samples, channels, rate, samples.length / channels);
            }

            at = body + size + (size & 1);
        }

        throw new IOException(name + " is not 16 bit PCM");
    }

    // ------------------------------------------------------------ whose key

    /** Mechvibes' numbers for a key, likeliest first. Empty for a key it has no number for. */
    private static int[] codesFor(int key) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return new int[] {2 + key - GLFW.GLFW_KEY_1};
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F10) return new int[] {59 + key - GLFW.GLFW_KEY_F1};

        int row = "QWERTYUIOP".indexOf(key);
        if (row >= 0) return new int[] {16 + row};

        row = "ASDFGHJKL".indexOf(key);
        if (row >= 0) return new int[] {30 + row};

        row = "ZXCVBNM".indexOf(key);
        if (row >= 0) return new int[] {44 + row};

        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> new int[] {1};
            case GLFW.GLFW_KEY_0 -> new int[] {11};
            case GLFW.GLFW_KEY_MINUS -> new int[] {12};
            case GLFW.GLFW_KEY_EQUAL -> new int[] {13};
            case GLFW.GLFW_KEY_BACKSPACE -> new int[] {14};
            case GLFW.GLFW_KEY_TAB -> new int[] {15};
            case GLFW.GLFW_KEY_LEFT_BRACKET -> new int[] {26};
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> new int[] {27};
            case GLFW.GLFW_KEY_ENTER -> new int[] {28};
            case GLFW.GLFW_KEY_LEFT_CONTROL -> new int[] {29};
            case GLFW.GLFW_KEY_SEMICOLON -> new int[] {39};
            case GLFW.GLFW_KEY_APOSTROPHE -> new int[] {40};
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> new int[] {41};
            case GLFW.GLFW_KEY_LEFT_SHIFT -> new int[] {42};
            case GLFW.GLFW_KEY_BACKSLASH -> new int[] {43};
            case GLFW.GLFW_KEY_COMMA -> new int[] {51};
            case GLFW.GLFW_KEY_PERIOD -> new int[] {52};
            case GLFW.GLFW_KEY_SLASH -> new int[] {53};
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> new int[] {54};
            case GLFW.GLFW_KEY_KP_MULTIPLY -> new int[] {55};
            case GLFW.GLFW_KEY_LEFT_ALT -> new int[] {56};
            case GLFW.GLFW_KEY_SPACE -> new int[] {57};
            case GLFW.GLFW_KEY_CAPS_LOCK -> new int[] {58};
            case GLFW.GLFW_KEY_NUM_LOCK -> new int[] {69};
            case GLFW.GLFW_KEY_SCROLL_LOCK -> new int[] {70};
            case GLFW.GLFW_KEY_KP_7 -> new int[] {71};
            case GLFW.GLFW_KEY_KP_8 -> new int[] {72};
            case GLFW.GLFW_KEY_KP_9 -> new int[] {73};
            case GLFW.GLFW_KEY_KP_SUBTRACT -> new int[] {74};
            case GLFW.GLFW_KEY_KP_4 -> new int[] {75};
            case GLFW.GLFW_KEY_KP_5 -> new int[] {76};
            case GLFW.GLFW_KEY_KP_6 -> new int[] {77};
            case GLFW.GLFW_KEY_KP_ADD -> new int[] {78};
            case GLFW.GLFW_KEY_KP_1 -> new int[] {79};
            case GLFW.GLFW_KEY_KP_2 -> new int[] {80};
            case GLFW.GLFW_KEY_KP_3 -> new int[] {81};
            case GLFW.GLFW_KEY_KP_0 -> new int[] {82};
            case GLFW.GLFW_KEY_KP_DECIMAL -> new int[] {83};
            case GLFW.GLFW_KEY_F11 -> new int[] {87};
            case GLFW.GLFW_KEY_F12 -> new int[] {88};
            case GLFW.GLFW_KEY_KP_ENTER -> new int[] {3612, 28};
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> new int[] {3613, 29};
            case GLFW.GLFW_KEY_KP_DIVIDE -> new int[] {3637, 53};
            case GLFW.GLFW_KEY_PRINT_SCREEN -> new int[] {3639};
            case GLFW.GLFW_KEY_RIGHT_ALT -> new int[] {3640, 56};
            case GLFW.GLFW_KEY_PAUSE -> new int[] {3653};
            case GLFW.GLFW_KEY_HOME -> new int[] {3655, 60999};
            case GLFW.GLFW_KEY_PAGE_UP -> new int[] {3657, 61001};
            case GLFW.GLFW_KEY_END -> new int[] {3663, 61007};
            case GLFW.GLFW_KEY_PAGE_DOWN -> new int[] {3665, 61009};
            case GLFW.GLFW_KEY_INSERT -> new int[] {3666, 61010};
            case GLFW.GLFW_KEY_DELETE -> new int[] {3667, 61011};
            case GLFW.GLFW_KEY_LEFT_SUPER -> new int[] {3675};
            case GLFW.GLFW_KEY_RIGHT_SUPER -> new int[] {3676};
            case GLFW.GLFW_KEY_MENU -> new int[] {3677};
            case GLFW.GLFW_KEY_UP -> new int[] {57416, 61000};
            case GLFW.GLFW_KEY_LEFT -> new int[] {57419, 61003};
            case GLFW.GLFW_KEY_RIGHT -> new int[] {57421, 61005};
            case GLFW.GLFW_KEY_DOWN -> new int[] {57424, 61008};
            default -> new int[0];
        };
    }

    // ---------------------------------------------------------------- importing

    /**
     * Copies a pack that ships inside Mechvibes out of a Mechvibes installed on
     * this machine, into {@code into}. Mechvibes keeps its own packs in one
     * archive, {@code app.asar}: a list of what is in it and where, and then the
     * files end to end. The list is read, the pack's two files are found in it,
     * and those bytes are copied. Returns where it was found.
     */
    public static String importFromMechvibes(String packFolder, Path into) throws IOException {
        List<Path> places = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        String local = System.getenv("LOCALAPPDATA");

        // A pack folder of that name, already unpacked, beside the custom packs.
        places.add(Path.of(home, "mechvibes_custom", packFolder));

        if (local != null) places.add(Path.of(local, "Programs", "Mechvibes", "resources", "app.asar"));

        places.add(Path.of(home, "AppData", "Local", "Programs", "Mechvibes", "resources", "app.asar"));
        places.add(Path.of("/Applications/Mechvibes.app/Contents/Resources/app.asar"));
        places.add(Path.of("/opt/Mechvibes/resources/app.asar"));
        places.add(Path.of("/usr/lib/mechvibes/resources/app.asar"));

        for (Path place : places) {
            if (Files.isDirectory(place) && Files.isRegularFile(place.resolve("config.json"))) {
                Files.createDirectories(into);

                try (var files = Files.list(place)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (Files.isRegularFile(file)) Files.copy(file, into.resolve(file.getFileName().toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                return place.toString();
            }

            if (Files.isRegularFile(place) && fromAsar(place, "src/audio/" + packFolder, into)) return place.toString();
        }

        throw new IOException("no Mechvibes was found on this computer. Install it, or copy the pack in by hand");
    }

    private static boolean fromAsar(Path asar, String folder, Path into) throws IOException {
        try (FileChannel channel = FileChannel.open(asar, StandardOpenOption.READ)) {
            ByteBuffer head = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);

            if (channel.read(head, 0) < 16) return false;

            int headerSize = head.getInt(4);
            int jsonSize = head.getInt(12);

            if (jsonSize <= 0 || jsonSize > 64 << 20 || headerSize < jsonSize) return false;

            ByteBuffer jsonBytes = ByteBuffer.allocate(jsonSize);
            channel.read(jsonBytes, 16);

            JsonObject node = JsonParser.parseString(new String(jsonBytes.array(), StandardCharsets.UTF_8)).getAsJsonObject();

            for (String part : folder.split("/")) {
                if (!node.has("files") || !node.getAsJsonObject("files").has(part)) return false;

                node = node.getAsJsonObject("files").getAsJsonObject(part);
            }

            if (!node.has("files")) return false;

            long base = 8L + headerSize;
            boolean any = false;

            Files.createDirectories(into);

            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject("files").entrySet()) {
                JsonObject file = entry.getValue().getAsJsonObject();

                if (!file.has("offset") || !file.has("size")) continue;

                long offset = Long.parseLong(file.get("offset").getAsString());
                int size = file.get("size").getAsInt();

                if (size < 0 || size > 64 << 20) continue;

                ByteBuffer bytes = ByteBuffer.allocate(size);
                channel.read(bytes, base + offset);

                // A name from inside an archive: the last part of it only.
                Files.write(into.resolve(Path.of(entry.getKey()).getFileName().toString()), bytes.array());
                any = true;
            }

            return any;
        } catch (RuntimeException e) {
            throw new IOException("Mechvibes' archive could not be read: " + e);
        }
    }
}
