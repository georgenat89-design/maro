package dev.maro.gametest;

import dev.maro.gui.render.Render2D;
import dev.maro.module.ModuleManager;
import dev.maro.runtime.RuntimeEvents;
import dev.maro.runtime.systems.modules.Modules;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/** Matched path output and CPU workload; no FPS inference from this microbenchmark. */
final class ClientPerformanceChecks {
    private static volatile long sink;
    static void run() {
        try {
            var type = MethodType.methodType(int.class, float[].class, float.class, float.class,
                float.class, float.class, float.class, int.class);
            var cached = MethodHandles.privateLookupIn(Render2D.class, MethodHandles.lookup())
                .findStatic(Render2D.class, "roundPath", type);
            var reference = MethodHandles.lookup().findStatic(ClientPerformanceChecks.class, "reference", type);
            float[] a = new float[528], b = new float[528];
            for (int seg = 3; seg <= 32; seg++) for (int radius = 0; radius <= 27; radius += 3) {
                Arrays.fill(a, 0); Arrays.fill(b, 0);
                int actual = (int) cached.invokeExact(a, -3.25f, 14.5f, 201.3f, 67.8f, (float) radius, seg);
                int expected = reference(b, -3.25f, 14.5f, 201.3f, 67.8f, radius, seg);
                if (actual != expected || !Arrays.equals(a, b)) throw new AssertionError("Cached rounded geometry differs at " + seg + "/" + radius);
            }
            timed(reference, a, 10000); timed(cached, a, 10000);
            long before = 0, after = 0;
            for (int pass = 0; pass < 4; pass++) {
                if ((pass & 1) == 0) { before += timed(reference, a, 125000); after += timed(cached, a, 125000); }
                else { after += timed(cached, a, 125000); before += timed(reference, a, 125000); }
            }
            var all = ModuleManager.all();
            var ported = Modules.get().getAll();
            if (all != ModuleManager.all() || ported != Modules.get().getAll()) throw new AssertionError("Module lookup rebuilt a list");
            for (var module : all) if (ModuleManager.get(module.getClass()) != module) throw new AssertionError("Indexed module lookup differs");
            var sync = MethodHandles.privateLookupIn(RuntimeEvents.class, MethodHandles.lookup())
                .findStatic(RuntimeEvents.class, "syncBinds", MethodType.methodType(void.class));
            sync.invokeExact();
            var binds = ported.stream().map(m -> m.keybind).toList();
            for (int tick = 0; tick < 20; tick++) sync.invokeExact();
            for (int index = 0; index < ported.size(); index++)
                if (ported.get(index).keybind != binds.get(index)) throw new AssertionError("Unchanged bind was reallocated");
            var module = ported.getFirst();
            int original = module.getBind().get();
            try {
                module.getBind().set(original == 65 ? 66 : 65); sync.invokeExact();
                if (module.keybind.code() != module.getBind().get()) throw new AssertionError("Changed bind was not synchronized");
            } finally { module.getBind().set(original); sync.invokeExact(); }
            System.out.println("[client-performance] PASS: 300 exact rounded-path cases; stable registry views/index and unchanged binds; changed bind synchronized");
            System.out.println("[client-performance] rounded-path 500000 calls reference-ns=" + before + " cached-ns=" + after
                + " reduction=" + Math.round((1 - (double) after / before) * 100) + "% (CPU workload, not FPS)");
        } catch (Throwable error) { throw new AssertionError(error); }
    }

    private static long timed(java.lang.invoke.MethodHandle method, float[] out, int iterations) throws Throwable {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpu = bean.isCurrentThreadCpuTimeSupported();
        if (cpu && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
        long start = cpu ? bean.getCurrentThreadCpuTime() : System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            int count = (int) method.invokeExact(out, 2.5f, 7.75f, 180f, 50f, (float) (i % 28), 3 + i % 30);
            checksum += Float.floatToRawIntBits(out[(count - 1) * 4]);
        }
        sink = checksum;
        return (cpu ? bean.getCurrentThreadCpuTime() : System.nanoTime()) - start;
    }

    private static int reference(float[] out, float x, float y, float w, float h, float r, int seg) {
        int i = 0;
        for (int c = 0; c < 4; c++) {
            float cx = c == 0 || c == 3 ? x + r : x + w - r;
            float cy = c < 2 ? y + r : y + h - r;
            float base = (float) Math.PI + c * (float) (Math.PI / 2);
            for (int s = 0; s <= seg; s++) {
                float angle = base + (float) (Math.PI / 2) * s / seg;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                out[i++] = cx + cos * r; out[i++] = cy + sin * r;
                out[i++] = cos; out[i++] = sin;
            }
        }
        return i / 4;
    }
}
