package dev.maro.gui.spotify;

/** One clock for finger contact and the album's physical slide. */
public final class PhoneSwipeMotion {
    public static final long DURATION=520_000_000L;
    private PhoneSwipeMotion(){}
    public static float time(long now,long start){return (float)((now-start)/(double)DURATION);}
    public static float stroke(float t){return smooth((t-.12f)/.5f);}
    public static float reach(float t){return smooth(Math.min(t/.12f,(1-t)/.3f));}
    private static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
