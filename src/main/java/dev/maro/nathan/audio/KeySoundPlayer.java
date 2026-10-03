package dev.maro.nathan.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Plays Key Sounds' samples, straight through OpenAL.
 *
 * <p><b>Why not the game's sound system.</b> That one is built for a world: a
 * sound is a resource looked up by name, queued, and started on the sound
 * thread at the next opportunity, which is fine for a creeper and late for a
 * key. Here a sample is a buffer that is already on the sound card's side and a
 * press is one call to start it, made on the spot.
 *
 * <p><b>Whose OpenAL.</b> The game's. It has a device open and a context
 * current for the whole process, and this makes its buffers and sources in
 * that, beside the game's own. Two things follow. The game's master volume is
 * the listener's gain, which applies to every source in the context, so these
 * go up and down with it without being told. And when the game throws its
 * context away - a device change, a resource reload - everything made in it is
 * gone with it, so the context is checked before every use and everything is
 * made afresh if it is not the one the handles came from.
 *
 * <p><b>Several at once.</b> Eight sources, so eight sounds can overlap, and
 * the ninth takes over from the one that has been going longest. Each new sound
 * is turned down by how many are already going, so that a fistful of keys is
 * not eight times one key, and the samples themselves peak well under full
 * scale, so the sum has room.
 *
 * <p>Nothing here throws. No device, no context, no sources left: it plays
 * nothing and says so once in the log.
 */
public final class KeySoundPlayer {
    private static final int VOICES = 8;

    private final Map<String, Integer> buffers = new HashMap<>();

    /** Buffers made from a pack's clips, by the clip itself and not by what is in it. */
    private final Map<MechvibesPack.Clip, Integer> clipBuffers = new IdentityHashMap<>();
    private final int[] sources = new int[VOICES];
    private final long[] started = new long[VOICES];

    private int made;
    private long context;
    private boolean complained;

    /**
     * Starts a sample now.
     *
     * @param path  where it is in the jar
     * @param gain  how loud, where 1 is as the file is
     * @param pitch how fast, where 1 is as the file is
     */
    public void play(String path, float gain, float pitch) {
        try {
            if (!ready()) return;

            start(buffer(path), gain, pitch);
        } catch (Throwable e) {
            complain("could not play " + path, e);
        }
    }

    /** Starts a clip of an imported pack now. */
    public void play(MechvibesPack.Clip clip, float gain, float pitch) {
        try {
            if (!ready()) return;

            Integer buffer = clipBuffers.get(clip);

            if (buffer == null) {
                AL10.alGetError();

                buffer = AL10.alGenBuffers();
                AL10.alBufferData(buffer, clip.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, clip.pcm(), clip.rate());

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    AL10.alDeleteBuffers(buffer);
                    complain("the sound card would not take a clip", null);
                    buffer = 0;
                }

                clipBuffers.put(clip, buffer);
            }

            start(buffer, gain, pitch);
        } catch (Throwable e) {
            complain("could not play a clip", e);
        }
    }

    private void start(int buffer, float gain, float pitch) {
        if (buffer == 0) return;

        int going = 0;
        int voice = -1;
        int oldest = 0;

        for (int i = 0; i < made; i++) {
            if (AL10.alGetSourcei(sources[i], AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                going++;
                if (started[i] < started[oldest]) oldest = i;
            } else if (voice < 0) {
                voice = i;
            }
        }

        if (voice < 0 && made < VOICES) voice = makeSource();
        if (voice < 0 && made > 0) voice = oldest;
        if (voice < 0) return;

        int source = sources[voice];

        AL10.alSourceStop(source);
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, (float) (gain / Math.sqrt(1 + going)));
        AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        AL10.alSourcePlay(source);

        started[voice] = System.nanoTime();

        AL10.alGetError();
    }

    /** Whatever is sounding, stopped. The buffers and sources are kept. */
    public void stop() {
        try {
            if (made == 0 || ALC10.alcGetCurrentContext() != context) return;

            for (int i = 0; i < made; i++) AL10.alSourceStop(sources[i]);

            AL10.alGetError();
        } catch (Throwable e) {
            complain("could not stop", e);
        }
    }

    /** Everything stopped and given back. It can be played again afterwards; it starts over. */
    public void close() {
        try {
            if (ALC10.alcGetCurrentContext() == context && context != 0) {
                for (int i = 0; i < made; i++) {
                    AL10.alSourceStop(sources[i]);
                    AL10.alSourcei(sources[i], AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(sources[i]);
                }

                for (int buffer : buffers.values()) AL10.alDeleteBuffers(buffer);
                for (int buffer : clipBuffers.values()) if (buffer != 0) AL10.alDeleteBuffers(buffer);

                AL10.alGetError();
            }
        } catch (Throwable e) {
            complain("could not release", e);
        }

        forget();
    }

    /** Drops the samples that are loaded, so that another preset's are. Sources are kept. */
    public void unload() {
        try {
            if (ALC10.alcGetCurrentContext() == context && context != 0) {
                for (int i = 0; i < made; i++) {
                    AL10.alSourceStop(sources[i]);
                    AL10.alSourcei(sources[i], AL10.AL_BUFFER, 0);
                }

                for (int buffer : buffers.values()) AL10.alDeleteBuffers(buffer);
                for (int buffer : clipBuffers.values()) if (buffer != 0) AL10.alDeleteBuffers(buffer);

                AL10.alGetError();
            }
        } catch (Throwable e) {
            complain("could not unload", e);
        }

        buffers.clear();
        clipBuffers.clear();
    }

    private void forget() {
        buffers.clear();
        clipBuffers.clear();
        made = 0;
        context = 0;
    }

    /** Whether there is a context to play in, and that it is the one the handles belong to. */
    private boolean ready() {
        long current = ALC10.alcGetCurrentContext();

        if (current == 0) return false;

        // The game made a new context. What was made in the old one went with it.
        if (current != context) {
            forget();
            context = current;
        }

        return true;
    }

    private int makeSource() {
        AL10.alGetError();

        int source = AL10.alGenSources();

        if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) return -1;

        // In your head and not in the world: fixed to the listener, at no
        // distance, and no quieter for being anywhere.
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);

        sources[made] = source;

        return made++;
    }

    private int buffer(String path) throws IOException {
        Integer known = buffers.get(path);
        if (known != null) return known;

        int buffer = load(path);

        buffers.put(path, buffer);

        return buffer;
    }

    /** A 16 bit PCM wave file, mono or stereo, into a buffer. Nought if it is anything else. */
    private int load(String path) throws IOException {
        byte[] bytes;

        try (InputStream in = KeySoundPlayer.class.getResourceAsStream(path)) {
            if (in == null) {
                complain("missing sample " + path, null);
                return 0;
            }

            bytes = in.readAllBytes();
        }

        ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        if (bytes.length < 44 || file.getInt(0) != 0x46464952 || file.getInt(8) != 0x45564157) {
            complain(path + " is not a wave file", null);
            return 0;
        }

        int channels = 0;
        int rate = 0;
        int bits = 0;

        // Chunk by chunk: the format, then whatever else there is, then the data.
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

                ByteBuffer pcm = BufferUtils.createByteBuffer(size);
                pcm.put(bytes, body, size).flip();

                AL10.alGetError();

                int buffer = AL10.alGenBuffers();

                AL10.alBufferData(buffer, channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, rate);

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    AL10.alDeleteBuffers(buffer);
                    complain("the sound card would not take " + path, null);
                    return 0;
                }

                return buffer;
            }

            at = body + size + (size & 1);
        }

        complain(path + " is not 16 bit PCM", null);
        return 0;
    }

    private void complain(String what, Throwable e) {
        if (complained) return;

        complained = true;
        NameeProtectAddon.LOG.warn("key-sounds: {}. Saying so once.", what, e);
    }
}
