package dev.maro.nathan.render;

/** Pure geometry and timing for the player's optional presentation features. */
public final class SpotifyHudFeatures {
    public enum Mode { Expanded, Mini, HoverExpand }
    public enum Visualizer { Bars, Wave, CoverRing }
    public enum Anchor { Free, Left, Right, Top, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }
    public record Position(double x, double y) { }
    public record Dock(Anchor anchor, int x, int y) { }

    private SpotifyHudFeatures() { }

    public static double marquee(double textWidth, double viewport, double age, double scale) {
        double travel = Math.max(0, textWidth - viewport);
        if (travel <= 1 || !Double.isFinite(age) || age < 0) return 0;
        double duration = Math.max(1, travel / (22 * Math.max(0.1, scale)));
        double hold = 1.6;
        double phase = age % (duration * 2 + hold * 2);
        if (phase < hold) return 0;
        if (phase < hold + duration) return travel * SpotifyHudAnimation.smooth((phase - hold) / duration);
        if (phase < hold * 2 + duration) return travel;
        return travel * (1 - SpotifyHudAnimation.smooth((phase - hold * 2 - duration) / duration));
    }

    public static Position position(Anchor anchor, double x, double y, double width, double height, double screenWidth, double screenHeight) {
        double maxX = Math.max(0, screenWidth - width);
        double maxY = Math.max(0, screenHeight - height);
        boolean right = anchor == Anchor.Right || anchor == Anchor.TopRight || anchor == Anchor.BottomRight;
        boolean bottom = anchor == Anchor.Bottom || anchor == Anchor.BottomLeft || anchor == Anchor.BottomRight;
        return new Position(clamp(right ? maxX - x : x, maxX), clamp(bottom ? maxY - y : y, maxY));
    }

    public static Dock dock(double left, double top, double width, double height, double screenWidth, double screenHeight, boolean snap) {
        double maxX = Math.max(0, screenWidth - width);
        double maxY = Math.max(0, screenHeight - height);
        left = clamp(left, maxX); top = clamp(top, maxY);
        boolean nearLeft = snap && left <= 20 && maxX > 24;
        boolean nearRight = snap && !nearLeft && maxX - left <= 20 && maxX > 24;
        boolean nearTop = snap && top <= 20 && maxY > 24;
        boolean nearBottom = snap && !nearTop && maxY - top <= 20 && maxY > 24;
        Anchor anchor = nearLeft ? (nearTop ? Anchor.TopLeft : nearBottom ? Anchor.BottomLeft : Anchor.Left)
            : nearRight ? (nearTop ? Anchor.TopRight : nearBottom ? Anchor.BottomRight : Anchor.Right)
            : nearTop ? Anchor.Top : nearBottom ? Anchor.Bottom : Anchor.Free;
        int x = (int) Math.round(nearLeft || nearRight ? 12 : left);
        int y = (int) Math.round(nearTop || nearBottom ? 12 : top);
        return new Dock(anchor, x, y);
    }

    private static double clamp(double value, double max) {
        return Double.isFinite(value) ? Math.max(0, Math.min(max, value)) : 0;
    }
}
