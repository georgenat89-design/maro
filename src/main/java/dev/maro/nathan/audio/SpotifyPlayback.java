package dev.maro.nathan.audio;

/** The phone and HUD use the same local Windows media session. */
public interface SpotifyPlayback {
    SpotifyMedia.State state();
    SpotifyMedia.Artwork artwork();
    void control(String action);
    void seekTo(long positionMs);
    void openSpotify();
}
