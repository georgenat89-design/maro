package dev.maro.gametest;

import java.lang.management.ManagementFactory;

/** Measures Java allocation on the render thread, excluding transport threads and world ticks. */
public final class RenderAllocationChecks {
    private static final com.sun.management.ThreadMXBean BEAN = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static boolean recording;
    private static final long[] starts = new long[2], bytes = new long[2], calls = new long[2];
    public static void start() { java.util.Arrays.fill(bytes, 0); java.util.Arrays.fill(calls, 0); recording = true; }
    public static void before(int kind) { if (recording && BEAN.isThreadAllocatedMemorySupported()) starts[kind] = BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    public static void after(int kind) { if (recording && BEAN.isThreadAllocatedMemorySupported()) { bytes[kind] += BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId()) - starts[kind]; calls[kind]++; } }
    public static void stop() {
        recording = false;
        for (int i = 0; i < 2; i++) System.out.println("[render-allocation] " + (i == 0 ? "native" : "ported") + " frames=" + calls[i] + " bytes-per-frame=" + (calls[i] == 0 ? 0 : bytes[i] / calls[i]));
    }
}
