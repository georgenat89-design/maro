package dev.maro.nathan.audio;

/** Client-thread ownership: keep one bridge alive while either player is open. */
public final class SpotifySession {
    private static final SpotifyMedia MEDIA = new SpotifyMedia();
    private static int users;

    private SpotifySession() { }

    public static SpotifyMedia media() { return MEDIA; }

    public static SpotifyMedia acquire() {
        if (users++ == 0) MEDIA.start();
        return MEDIA;
    }

    public static void release() {
        if (users > 0 && --users == 0) MEDIA.close();
    }
}
