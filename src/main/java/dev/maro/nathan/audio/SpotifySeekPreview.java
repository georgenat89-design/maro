package dev.maro.nathan.audio;

/** Keeps a released seek visible while the background media control catches up. */
public final class SpotifySeekPreview {
    private static final long HOLD_MS = 1200;
    private static final long CONFIRM_DISTANCE_MS = 1000;

    private boolean active;
    private String track = "";
    private long duration;
    private long previewPosition;
    private long initialSample;
    private long startedAt;
    private long clockAt;
    private boolean playing;

    public void begin(String track, long duration, long targetPosition, boolean playing,
                      long sampledAtMs, long nowMs) {
        cancel();
        if (duration <= 0) return;
        this.track = track == null ? "" : track;
        this.duration = duration;
        previewPosition = clamp(targetPosition, duration);
        this.playing = playing;
        initialSample = sampledAtMs;
        startedAt = nowMs;
        clockAt = nowMs;
        active = true;
    }

    public long position(String track, long duration, long actualPosition, boolean playing,
                         long sampledAtMs, boolean canSeek, boolean hasError, long nowMs) {
        long actual = clamp(actualPosition, duration);
        if (!active) return actual;
        if (!this.track.equals(track == null ? "" : track) || this.duration != duration
            || !canSeek || hasError || nowMs - startedAt >= HOLD_MS) {
            cancel();
            return actual;
        }

        // Carry the short preview clock forward before applying a pause/resume
        // change, so pausing holds its current position instead of resetting it.
        long elapsed = Math.max(0, nowMs - clockAt);
        if (this.playing) previewPosition += Math.min(elapsed, this.duration - previewPosition);
        clockAt = Math.max(clockAt, nowMs);
        this.playing = playing;

        if (sampledAtMs > initialSample && Math.abs(actual - previewPosition) <= CONFIRM_DISTANCE_MS) {
            cancel();
            return actual;
        }
        return previewPosition;
    }

    public void cancel() {
        active = false;
        track = "";
        duration = 0;
        previewPosition = 0;
        initialSample = 0;
        startedAt = 0;
        clockAt = 0;
        playing = false;
    }

    private static long clamp(long position, long duration) {
        return duration > 0 ? Math.max(0, Math.min(duration, position)) : Math.max(0, position);
    }
}
