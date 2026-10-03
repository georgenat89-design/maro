import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Makes the samples Key Sounds plays. Nothing is recorded and nothing is taken
 * from anywhere: every sample is worked out here from numbers, so they are the
 * addon's own and come under its licence.
 *
 * <p>A key going down is a short knock - the stem meeting the housing - and the
 * case and the plate ringing for a moment afterwards. So a sample is a burst of
 * filtered noise that dies in a few milliseconds, plus a handful of damped
 * tones, the whole thing low-passed. What differs between the presets is how
 * low the tones are, how long they ring, how much knock there is and how much
 * top is left in.
 *
 * <p>Run it from the repository root, and it writes the files where the addon
 * looks for them:
 *
 * <pre>java tools/KeySoundGen.java</pre>
 *
 * It prints what it made and how each one measures. The same numbers in give
 * the same files out, bit for bit.
 */
public final class KeySoundGen {
    private static final int RATE = 44100;

    /** One damped tone: where, how loud, and how many milliseconds to die to 1/e. */
    record Mode(double hz, double amp, double decayMs) {
    }

    /** One preset's key. */
    record Voice(String name, Mode[] modes, double noiseAmp, double noiseDecayMs, double noiseLowHz, double noiseHighHz,
                 double attackMs, double cutoffHz, double lengthMs, double peak, double secondClickMs, double secondClickAmp) {
    }

    private static final Voice[] VOICES = {
        new Voice("creamy", new Mode[] {new Mode(190, 1.0, 38), new Mode(410, 0.6, 30), new Mode(880, 0.25, 18), new Mode(1500, 0.08, 10)},
            0.25, 4, 150, 2500, 1.2, 3000, 140, 0.60, 0, 0),
        new Voice("thock", new Mode[] {new Mode(110, 1.0, 55), new Mode(235, 0.7, 45), new Mode(520, 0.3, 25), new Mode(1100, 0.08, 10)},
            0.20, 5, 100, 1800, 1.5, 2200, 190, 0.62, 0, 0),
        new Voice("soft", new Mode[] {new Mode(260, 1.0, 22), new Mode(600, 0.35, 14), new Mode(1200, 0.1, 8)},
            0.12, 3, 200, 1500, 2.5, 1800, 100, 0.42, 0, 0),
        new Voice("clicky", new Mode[] {new Mode(300, 0.8, 28), new Mode(750, 0.5, 18), new Mode(2100, 0.3, 8)},
            0.55, 1.2, 2500, 9000, 0.3, 9000, 120, 0.60, 9, 0.35),
        new Voice("creamy-deep", new Mode[] {new Mode(155, 1.0, 46), new Mode(320, 0.55, 34), new Mode(680, 0.2, 20), new Mode(1200, 0.07, 12)},
            0.18, 4, 100, 2200, 1.8, 2600, 170, 0.58, 0, 0),
        new Voice("creamy-light", new Mode[] {new Mode(245, 1.0, 28), new Mode(490, 0.5, 20), new Mode(1020, 0.25, 13), new Mode(1900, 0.1, 8)},
            0.22, 3, 250, 3500, 0.9, 4200, 115, 0.55, 0, 0),
        new Voice("silky", new Mode[] {new Mode(220, 1.0, 20), new Mode(450, 0.3, 16), new Mode(900, 0.07, 10)},
            0.075, 3, 150, 1400, 3.0, 1400, 105, 0.46, 0, 0),
        new Voice("milky", new Mode[] {new Mode(170, 1.0, 35), new Mode(350, 0.65, 27), new Mode(730, 0.18, 17)},
            0.14, 4.5, 120, 2200, 2.0, 2200, 145, 0.56, 0, 0),
        new Voice("marshmallow", new Mode[] {new Mode(125, 1.0, 55), new Mode(275, 0.4, 40), new Mode(600, 0.09, 26)},
            0.1, 6, 80, 1300, 4.0, 1200, 210, 0.45, 0, 0),
        new Voice("velvet", new Mode[] {new Mode(205, 1.0, 28), new Mode(420, 0.45, 20), new Mode(830, 0.06, 12)},
            0.09, 4, 160, 1750, 2.5, 1750, 125, 0.48, 0, 0),
        new Voice("poppy", new Mode[] {new Mode(390, 0.8, 18), new Mode(850, 0.4, 12), new Mode(1680, 0.2, 6)},
            0.4, 1.5, 1000, 6500, 0.5, 7500, 85, 0.58, 3, 0.18),
        new Voice("bubble", new Mode[] {new Mode(280, 1.0, 24), new Mode(620, 0.3, 15), new Mode(1300, 0.15, 9)},
            0.25, 2.5, 300, 3200, 1.1, 3600, 100, 0.55, 5, 0.25),
        new Voice("marble", new Mode[] {new Mode(520, 1.0, 32), new Mode(1050, 0.55, 20), new Mode(2200, 0.25, 12)},
            0.3, 2, 700, 6500, 0.7, 6500, 150, 0.58, 0, 0),
        new Voice("rain", new Mode[] {new Mode(320, 0.5, 18), new Mode(700, 0.2, 12), new Mode(1600, 0.06, 6)},
            0.5, 7, 700, 5000, 1.0, 4800, 100, 0.45, 0, 0),
    };

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/nameeprotect/keysounds");

        System.out.println("file                      ms   peak    rms   centroid Hz   above 4 kHz");

        for (Voice voice : VOICES) {
            Path dir = out.resolve(voice.name());
            Files.createDirectories(dir);

            // Four takes of the ordinary key, each a little off the others, the
            // way no two keys on a board sound quite alike.
            for (int i = 1; i <= 4; i++) {
                write(dir.resolve("key" + i + ".wav"), render(voice, 1.0, 1.0, 1.0, 1.0, 0, 100L * i + voice.name().hashCode()));
            }

            // The spacebar: a bigger cap on a stabiliser. Lower, longer, and a
            // second, smaller knock as the wire settles.
            write(dir.resolve("space.wav"), render(voice, 0.72, 1.5, 1.4, 1.0, 0.15, 7L + voice.name().hashCode()));

            // A mouse button: a small switch in a small shell. Higher, and over sooner.
            write(dir.resolve("mouse.wav"), render(voice, 2.6, 0.4, 0.45, 0.8, 0, 11L + voice.name().hashCode()));
        }
    }

    private static double[] render(Voice v, double pitch, double ring, double length, double level, double settle, long seed) {
        Random random = new Random(seed);
        int n = (int) (RATE * v.lengthMs() * length / 1000);
        double[] s = new double[n];

        // The ring. Each take is detuned by a few per cent and starts its tones
        // at its own phase.
        for (Mode mode : v.modes()) {
            double hz = mode.hz() * pitch * (1 + (random.nextDouble() - 0.5) * 0.06);
            // A bigger cap puts more of its weight in the lowest tone.
            double amp = mode.amp() * (1 + (random.nextDouble() - 0.5) * 0.2) * (pitch < 1 && mode != v.modes()[0] ? 0.7 : 1);
            double tau = mode.decayMs() * ring / 1000;
            double phase = random.nextDouble() * Math.PI * 0.5;

            for (int i = 0; i < n; i++) {
                double t = i / (double) RATE;
                s[i] += amp * Math.sin(2 * Math.PI * hz * t + phase) * Math.exp(-t / tau);
            }
        }

        // The knock, and for the presets that have one, the second.
        knock(s, random, 0, v.noiseAmp() * (pitch < 1 ? 0.7 : 1), v.noiseDecayMs(), v.noiseLowHz() * pitch, v.noiseHighHz() * Math.min(pitch, 1.5));

        if (v.secondClickAmp() > 0) knock(s, random, v.secondClickMs(), v.noiseAmp() * v.secondClickAmp(), v.noiseDecayMs(), v.noiseLowHz() * pitch, v.noiseHighHz() * Math.min(pitch, 1.5));
        if (settle > 0) knock(s, random, 14, v.noiseAmp() * settle, v.noiseDecayMs() * 1.5, v.noiseLowHz() * pitch * 0.6, v.noiseHighHz() * pitch * 0.5);

        // Two passes of a gentle low-pass: twelve decibels an octave off the top.
        lowPass(s, v.cutoffHz() * Math.min(pitch, 1.6));
        lowPass(s, v.cutoffHz() * Math.min(pitch, 1.6));

        // In over a moment and not at once, which is the difference between a
        // knock and a tick from the speaker. Out over the last few milliseconds.
        int attack = Math.max(1, (int) (RATE * v.attackMs() / 1000));
        int release = RATE * 6 / 1000;

        for (int i = 0; i < attack && i < n; i++) s[i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / attack);
        for (int i = 0; i < release && i < n; i++) s[n - 1 - i] *= i / (double) release;

        double peak = 0;
        for (double x : s) peak = Math.max(peak, Math.abs(x));
        for (int i = 0; i < n; i++) s[i] *= v.peak() * level / peak;

        return s;
    }

    /** A burst of noise between two frequencies, dying away, mixed in from {@code atMs}. */
    private static void knock(double[] s, Random random, double atMs, double amp, double decayMs, double lowHz, double highHz) {
        int from = (int) (RATE * atMs / 1000);
        int n = Math.min(s.length - from, (int) (RATE * decayMs * 8 / 1000));

        if (n <= 0) return;

        double[] burst = new double[n];

        for (int i = 0; i < n; i++) burst[i] = (random.nextDouble() * 2 - 1) * Math.exp(-i / (RATE * decayMs / 1000));

        lowPass(burst, highHz);
        highPass(burst, lowHz);

        double peak = 1e-9;
        for (double x : burst) peak = Math.max(peak, Math.abs(x));
        for (int i = 0; i < n; i++) s[from + i] += burst[i] * amp / peak;
    }

    private static void lowPass(double[] s, double hz) {
        double a = 1 - Math.exp(-2 * Math.PI * hz / RATE);
        double y = 0;

        for (int i = 0; i < s.length; i++) {
            y += a * (s[i] - y);
            s[i] = y;
        }
    }

    private static void highPass(double[] s, double hz) {
        double a = 1 - Math.exp(-2 * Math.PI * hz / RATE);
        double low = 0;

        for (int i = 0; i < s.length; i++) {
            low += a * (s[i] - low);
            s[i] -= low;
        }
    }

    private static void write(Path file, double[] s) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(44 + s.length * 2).order(ByteOrder.LITTLE_ENDIAN);

        data.put("RIFF".getBytes()).putInt(36 + s.length * 2).put("WAVE".getBytes());
        data.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        data.put("data".getBytes()).putInt(s.length * 2);

        for (double x : s) data.putShort((short) Math.round(Math.max(-1, Math.min(1, x)) * 32767));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(data.array());
        Files.write(file, bytes.toByteArray());

        measure(file, s);
    }

    /** How loud, and where the weight of the sound is: the numbers the presets are judged by. */
    private static void measure(Path file, double[] s) {
        double peak = 0;
        double sum = 0;

        for (double x : s) {
            peak = Math.max(peak, Math.abs(x));
            sum += x * x;
        }

        // A plain Fourier sum at 50 Hz steps is plenty for a tenth of a second.
        double weighted = 0;
        double total = 0;
        double top = 0;

        for (double hz = 50; hz < 16000; hz += 50) {
            double re = 0;
            double im = 0;

            for (int i = 0; i < s.length; i++) {
                double p = 2 * Math.PI * hz * i / RATE;
                re += s[i] * Math.cos(p);
                im += s[i] * Math.sin(p);
            }

            double power = re * re + im * im;

            weighted += hz * power;
            total += power;
            if (hz >= 4000) top += power;
        }

        System.out.printf(Locale.ROOT, "%-22s %5.0f  %.3f  %.3f   %8.0f      %6.3f%%%n",
            file.getParent().getFileName() + "/" + file.getFileName(), s.length * 1000.0 / RATE, peak, Math.sqrt(sum / s.length), weighted / total, 100 * top / total);
    }
}
