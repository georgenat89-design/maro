package dev.maro.nathan.audio;

import java.util.OptionalLong;

/** A scrub previews locally and commits once, only if the same track is still selected. */
public final class SpotifyTimeline {
    private String track = "";
    private long duration;
    private double fraction;
    private boolean active;

    public boolean active() { return active; }
    public double fraction() { return fraction; }

    public boolean begin(String track, long duration, boolean canSeek, double fraction) {
        cancel();
        if (!canSeek || duration <= 0) return false;
        this.track = track;
        this.duration = duration;
        active = true;
        drag(fraction);
        return true;
    }

    public void drag(double fraction) {
        if (active && Double.isFinite(fraction)) this.fraction = Math.max(0, Math.min(1, fraction));
    }

    public void validate(String track, long duration, boolean canSeek) {
        if (active && (!canSeek || this.duration != duration || !this.track.equals(track))) cancel();
    }

    public long positionMs() { return Math.round(fraction * duration); }

    public OptionalLong finish(String track, long duration, boolean canSeek, double fraction) {
        validate(track, duration, canSeek);
        if (!active) return OptionalLong.empty();
        drag(fraction);
        long position = positionMs();
        cancel();
        return OptionalLong.of(position);
    }

    public void cancel() {
        active = false;
        track = "";
        duration = 0;
        fraction = 0;
    }
}
