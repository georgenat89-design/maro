package dev.maro.gametest;

/** Records faults before throwing: companion render callbacks can swallow exceptions. */
public final class RenderFaultChecks {
    private static int faults;
    public static void fail(String message) {
        if (++faults <= 3) new AssertionError(message).printStackTrace();
        throw new AssertionError(message);
    }
    static void assertClean() {
        if (faults != 0) throw new AssertionError("Unsafe native mesh writes: " + faults);
    }
}
