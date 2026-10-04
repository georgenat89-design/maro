package dev.maro.nathan.render;

/** Shared physical-pixel geometry for the mini player's rendering and mouse targets. */
public record SpotifyHudLayout(double left, double top, double scale, double expansion, double lyricsHeight) {
    public static final double WIDTH = 420;
    public static final double HEIGHT = 108;
    public static final double TIME_CAPS = 9.5;
    public static final double MINI_WIDTH = 264;
    public static final double MINI_HEIGHT = 72;

    public SpotifyHudLayout(double left, double top, double scale) { this(left, top, scale, 1, 0); }
    public SpotifyHudLayout(double left, double top, double scale, double expansion) { this(left, top, scale, expansion, 0); }
    public SpotifyHudLayout { expansion = Math.max(0, Math.min(1, expansion)); lyricsHeight = Math.max(0, Math.min(84, lyricsHeight)); }

    public record Rect(double x, double y, double width, double height) {
        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    public double x(double offset) { return left + offset * scale; }
    public double y(double offset) { return top + offset * scale; }
    public Rect rect(double x, double y, double width, double height) {
        return new Rect(x(x), y(y), width * scale, height * scale);
    }
    public double lerp(double mini, double full) { return mini + (full - mini) * expansion; }
    public double width() { return lerp(MINI_WIDTH, WIDTH); }
    public double height() { return lerp(MINI_HEIGHT, HEIGHT); }
    public double textLeft() { return x(lerp(72, 94)); }
    public double textWidth() { return lerp(128, 206) * scale; }
    public double detailOpacity() { return Math.max(0, (expansion - 0.55) / 0.45); }
    public double controlOpacity() { return Math.max(0, (expansion - 0.94) / 0.06); }
    public Rect panel() { return rect(0, 0, width(), height()); }
    public double totalHeight() { return height() + (lyricsHeight > 0 ? 8 + lyricsHeight : 0); }
    public Rect bounds() { return rect(0, 0, width(), totalHeight()); }
    public Rect lyrics() { return rect(0, height() + 8, width(), lyricsHeight); }
    public Rect cover() { return rect(lerp(12, 14), 12, lerp(48, 64), lerp(48, 64)); }
    public Rect previous() { return rect(lerp(184, 310), lerp(24, 21), lerp(24, 32), lerp(24, 32)); }
    public Rect toggle() { return rect(lerp(216, 343), lerp(20, 18), lerp(32, 38), lerp(32, 38)); }
    public Rect next() { return rect(lerp(240, 382), lerp(24, 21), lerp(24, 32), lerp(24, 32)); }
    public Rect volumeMute() { return rect(158, 55, 22, 22); }
    public Rect volumeTrack() { return rect(184, 65, 88, 4); }
    public Rect volumeHit() { return rect(180, 56, 96, 20); }
    public Rect timeline() { return rect(lerp(72, 57), lerp(57, 87), lerp(128, 306), lerp(3, 6)); }
    public Rect timeline(double elapsedWidth, double remainingWidth) {
        double fullStart = Math.max(57, 14 + elapsedWidth / scale + 9);
        double fullEnd = Math.min(363, 406 - remainingWidth / scale - 9);
        double start = x(lerp(72, fullStart));
        double end = x(lerp(200, fullEnd));
        return new Rect(start, y(lerp(57, 87)), Math.max(scale, end - start), lerp(3, 6) * scale);
    }
    public Rect timelineHit() { return timelineHit(timeline()); }
    public Rect timelineHit(Rect track) { return new Rect(track.x - 4 * scale, track.y - 9 * scale, track.width + 8 * scale, lerp(21, 25) * scale); }
    public double fractionAt(double mouseX) {
        return fractionAt(mouseX, timeline());
    }
    public double fractionAt(double mouseX, Rect track) {
        return Math.max(0, Math.min(1, (mouseX - track.x) / track.width));
    }
}
