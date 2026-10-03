package dev.maro.nathan.render;

/** Elapsed-time animation state for the player, independent of rendering and input APIs. */
public final class SpotifyHudAnimation {
    private static final double TRANSITION_SECONDS = 0.42;
    private static final double ARTWORK_SECONDS = 0.35;
    private static final double PRESS_SECONDS = 0.26;
    private static final double ONSET_DECAY = 6;
    private static final double ATTACK = 24;
    private static final double RELEASE = 5;
    private static final double[] BAND_WEIGHTS = {0.34, 0.26, 0.18, 0.10, 0.06, 0.04, 0.02};

    private final double[] hover = new double[3];
    private final double[] pressAge = {PRESS_SECONDS, PRESS_SECONDS, PRESS_SECONDS};
    private double transitionAge = TRANSITION_SECONDS;
    private double artworkAge = ARTWORK_SECONDS;
    private double cardAge = ARTWORK_SECONDS;
    private double coverHover;
    private double playPause;
    private double shimmer;
    private double previousEnergy;
    private double bass;
    private double onsetEnvelope;
    private double onsetBeat;
    private double beat;

    public void reset(boolean playing) {
        for (int i = 0; i < hover.length; i++) {
            hover[i] = 0;
            pressAge[i] = PRESS_SECONDS;
        }
        transitionAge = TRANSITION_SECONDS;
        artworkAge = ARTWORK_SECONDS;
        cardAge = ARTWORK_SECONDS;
        coverHover = 0;
        playPause = playing ? 1 : 0;
        shimmer = 0;
        previousEnergy = 0;
        bass = 0;
        onsetEnvelope = 0;
        onsetBeat = 0;
        beat = 0;
    }

    public void changeTrack() {
        transitionAge = 0;
    }

    public void changeArtwork() {
        artworkAge = 0;
    }

    public void changeCard() {
        cardAge = 0;
    }

    public void press(int buttonIndex) {
        if (validButton(buttonIndex)) pressAge[buttonIndex] = 0;
    }

    public void step(double seconds, boolean playing, float[] bands, boolean hoveredPrevious,
                     boolean hoveredToggle, boolean hoveredNext, boolean hoveredCover, boolean animateControls) {
        double delta = Double.isFinite(seconds) ? Math.max(0, Math.min(0.1, seconds)) : 0;
        transitionAge = Math.min(TRANSITION_SECONDS, transitionAge + delta);
        artworkAge = Math.min(ARTWORK_SECONDS, artworkAge + delta);
        cardAge = Math.min(ARTWORK_SECONDS, cardAge + delta);
        coverHover = approach(coverHover, hoveredCover ? 1 : 0, 15, delta);
        if (animateControls) {
            hover[0] = approach(hover[0], hoveredPrevious ? 1 : 0, 15, delta);
            hover[1] = approach(hover[1], hoveredToggle ? 1 : 0, 15, delta);
            hover[2] = approach(hover[2], hoveredNext ? 1 : 0, 15, delta);
            for (int i = 0; i < pressAge.length; i++) pressAge[i] = Math.min(PRESS_SECONDS, pressAge[i] + delta);
            playPause = approach(playPause, playing ? 1 : 0, 17, delta);
        } else {
            for (int i = 0; i < hover.length; i++) {
                hover[i] = 0;
                pressAge[i] = PRESS_SECONDS;
            }
            playPause = playing ? 1 : 0;
        }

        if (playing) {
            shimmer += delta / 3.8;
            shimmer -= Math.floor(shimmer);
        }

        double energy = 0;
        if (playing && bands != null) {
            for (int i = 0; i < Math.min(bands.length, BAND_WEIGHTS.length); i++) {
                float level = bands[i];
                if (Float.isFinite(level)) energy += BAND_WEIGHTS[i] * clamp(level);
            }
        }
        if (playing) {
            onsetEnvelope = Math.min(1, onsetEnvelope + Math.max(0, energy - previousEnergy) * 1.15);
            bass = approach(bass, energy * 0.78, energy * 0.78 > bass ? ATTACK : RELEASE, delta);

            // Integrate the decaying onset and its attack filter together. This
            // gives the same response at different frame rates for one onset.
            double attackDecay = Math.exp(-ATTACK * delta);
            double onsetDecay = Math.exp(-ONSET_DECAY * delta);
            onsetBeat = onsetBeat * attackDecay
                + onsetEnvelope * ATTACK / (ATTACK - ONSET_DECAY) * (onsetDecay - attackDecay);
            onsetEnvelope *= onsetDecay;
        } else {
            bass *= Math.exp(-RELEASE * delta);
            onsetBeat *= Math.exp(-RELEASE * delta);
            onsetEnvelope = 0;
        }
        previousEnergy = energy;
        beat = clamp(bass + onsetBeat * 0.65);
    }

    public double beat() { return beat; }
    public double transition() { return smooth(transitionAge / TRANSITION_SECONDS); }
    public double artworkTransition() { return smooth(artworkAge / ARTWORK_SECONDS); }
    public double cardTransition() { return smooth(cardAge / ARTWORK_SECONDS); }
    public double hover(int index) { return validButton(index) ? hover[index] : 0; }
    public double pressAmount(int index) {
        if (!validButton(index) || pressAge[index] >= PRESS_SECONDS) return 0;
        return 0.5 + 0.5 * Math.cos(Math.PI * pressAge[index] / PRESS_SECONDS);
    }
    public double coverHover() { return coverHover; }
    public double playPause() { return playPause; }
    public double shimmer() { return shimmer; }

    public static double smooth(double value) {
        double t = Double.isFinite(value) ? clamp(value) : 0;
        return t * t * (3 - 2 * t);
    }

    private static double approach(double value, double target, double rate, double seconds) {
        return clamp(target + (value - target) * Math.exp(-rate * seconds));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static boolean validButton(int index) {
        return index >= 0 && index < 3;
    }
}
