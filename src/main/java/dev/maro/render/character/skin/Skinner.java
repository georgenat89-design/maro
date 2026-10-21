package dev.maro.render.character.skin;

/**
 * Linear blend skinning on the CPU: each vertex moved by the weighted sum of up to four joints'
 * skinning matrices. Writes position and normal (six floats) per vertex into a reused buffer.
 */
public final class Skinner {
    private Skinner() {
    }

    /** Vertices a level must have before it is skinned on several threads at once. */
    public static final int PARALLEL = 12000;

    /**
     * Skins a whole level, a large one across the common pool's threads: the same job objects
     * every frame, so nothing is allocated.
     */
    public static final class Job extends java.util.concurrent.RecursiveAction {
        private static final int PARTS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        private final Job[] parts;
        private final int part;
        private SkinLevel level;
        private float[] skin, out;

        public Job() {
            parts = new Job[PARTS];
            for (int i = 0; i < PARTS; i++) parts[i] = new Job(i);
            part = -1;
        }

        private Job(int part) {
            parts = null;
            this.part = part;
        }

        /** Skins {@code level} with {@code skin} into {@code out} (six floats a vertex). */
        public void run(SkinLevel level, float[] skin, float[] out) {
            if (level.vertices < PARALLEL || PARTS == 1) {
                Skinner.skin(level, skin, 0, level.vertices, out);
                return;
            }
            this.level = level;
            this.skin = skin;
            this.out = out;
            for (Job p : parts) {
                p.reinitialize();
                p.level = level;
                p.skin = skin;
                p.out = out;
            }
            reinitialize();
            java.util.concurrent.ForkJoinPool.commonPool().invoke(this);
        }

        @Override
        protected void compute() {
            if (parts != null) {
                invokeAll(parts);
                return;
            }
            int n = level.vertices, count = PARTS;
            int from = (int) ((long) n * part / count), to = (int) ((long) n * (part + 1) / count);
            Skinner.skin(level, skin, from, to, out, 0);
        }
    }

    /**
     * Skins vertices {@code [from, to)} of a level with a pose's skinning matrices into
     * {@code out}, vertex {@code v} at {@code (v - from) * 6}.
     */
    public static void skin(SkinLevel level, float[] skin, int from, int to, float[] out) {
        skin(level, skin, from, to, out, from);
    }

    /** As {@link #skin(SkinLevel, float[], int, int, float[])}, vertex {@code v} written at {@code (v - base) * 6}. */
    public static void skin(SkinLevel level, float[] skin, int from, int to, float[] out, int base) {
        float[] pos = level.pos, nrm = level.nrm, w = level.weight;
        short[] j = level.joint;
        for (int v = from; v < to; v++) {
            int o = (v - base) * 6, i4 = v * 4;
            float w0 = w[i4], w1 = w[i4 + 1], w2 = w[i4 + 2], w3 = w[i4 + 3];
            int a = j[i4] * 12;
            float m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11;
            if (w1 <= 0) {
                // One joint (props, rigid parts, most of a limb's middle): no blend needed.
                m0 = skin[a];
                m1 = skin[a + 1];
                m2 = skin[a + 2];
                m3 = skin[a + 3];
                m4 = skin[a + 4];
                m5 = skin[a + 5];
                m6 = skin[a + 6];
                m7 = skin[a + 7];
                m8 = skin[a + 8];
                m9 = skin[a + 9];
                m10 = skin[a + 10];
                m11 = skin[a + 11];
            } else {
                int b = j[i4 + 1] * 12;
                m0 = skin[a] * w0 + skin[b] * w1;
                m1 = skin[a + 1] * w0 + skin[b + 1] * w1;
                m2 = skin[a + 2] * w0 + skin[b + 2] * w1;
                m3 = skin[a + 3] * w0 + skin[b + 3] * w1;
                m4 = skin[a + 4] * w0 + skin[b + 4] * w1;
                m5 = skin[a + 5] * w0 + skin[b + 5] * w1;
                m6 = skin[a + 6] * w0 + skin[b + 6] * w1;
                m7 = skin[a + 7] * w0 + skin[b + 7] * w1;
                m8 = skin[a + 8] * w0 + skin[b + 8] * w1;
                m9 = skin[a + 9] * w0 + skin[b + 9] * w1;
                m10 = skin[a + 10] * w0 + skin[b + 10] * w1;
                m11 = skin[a + 11] * w0 + skin[b + 11] * w1;
                if (w2 > 0) {
                    int c = j[i4 + 2] * 12;
                    m0 += skin[c] * w2;
                    m1 += skin[c + 1] * w2;
                    m2 += skin[c + 2] * w2;
                    m3 += skin[c + 3] * w2;
                    m4 += skin[c + 4] * w2;
                    m5 += skin[c + 5] * w2;
                    m6 += skin[c + 6] * w2;
                    m7 += skin[c + 7] * w2;
                    m8 += skin[c + 8] * w2;
                    m9 += skin[c + 9] * w2;
                    m10 += skin[c + 10] * w2;
                    m11 += skin[c + 11] * w2;
                    if (w3 > 0) {
                        int d = j[i4 + 3] * 12;
                        m0 += skin[d] * w3;
                        m1 += skin[d + 1] * w3;
                        m2 += skin[d + 2] * w3;
                        m3 += skin[d + 3] * w3;
                        m4 += skin[d + 4] * w3;
                        m5 += skin[d + 5] * w3;
                        m6 += skin[d + 6] * w3;
                        m7 += skin[d + 7] * w3;
                        m8 += skin[d + 8] * w3;
                        m9 += skin[d + 9] * w3;
                        m10 += skin[d + 10] * w3;
                        m11 += skin[d + 11] * w3;
                    }
                }
            }
            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            out[o] = m0 * x + m1 * y + m2 * z + m3;
            out[o + 1] = m4 * x + m5 * y + m6 * z + m7;
            out[o + 2] = m8 * x + m9 * y + m10 * z + m11;
            float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            out[o + 3] = m0 * nx + m1 * ny + m2 * nz;
            out[o + 4] = m4 * nx + m5 * ny + m6 * nz;
            out[o + 5] = m8 * nx + m9 * ny + m10 * nz;
        }
    }
}
