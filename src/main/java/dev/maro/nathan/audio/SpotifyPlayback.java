package dev.maro.nathan.audio;

/** The phone and HUD use the same local Windows media session. */
public interface SpotifyPlayback {
    SpotifyMedia.State state();
    SpotifyMedia.Artwork artwork();
    SpotifyMedia.Volume volume();
    void setVolume(double level, boolean muted);
    void control(String action);
    void seekTo(long positionMs);
    void openSpotify();
}
