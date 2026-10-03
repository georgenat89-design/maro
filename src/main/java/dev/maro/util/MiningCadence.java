package dev.maro.util;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Tick-based waits, sampled once per action. An active block break is never delayed here. */
public final class MiningCadence {
    private final RandomGenerator random;
    private Object action;
    private int reaction, tool, rest, untilRest;
    private float aimFactor = 1;
    public MiningCadence(RandomGenerator random) { this.random = random; }
    public void reset(int minBlocks, int maxBlocks) {
        clearAction(); rest = 0; untilRest = sample(minBlocks, maxBlocks);
    }
    public void tick() {
        if (reaction > 0) reaction--;
        if (tool > 0) tool--;
        if (rest > 0) rest--;
    }
    public void prepare(Object key, int minDelay, int maxDelay, float variation) {
        if (Objects.equals(action, key)) return;
        action = key; reaction = sample(minDelay, maxDelay); tool = 0;
        aimFactor = 1 + (random.nextFloat() * 2 - 1) * Math.max(0, Math.min(.9f, variation));
    }
    public void clearAction() { action = null; reaction = 0; tool = 0; aimFactor = 1; }
    public boolean reactionReady() { return reaction == 0; }
    public boolean toolReady() { return tool == 0; }
    public float aimFactor() { return aimFactor; }
    public void switchedTool(int ticks) { tool = Math.max(0, ticks); }
    public void completedBlock(boolean breaks, int minBlocks, int maxBlocks, int minRest, int maxRest) {
        clearAction();
        if (!breaks) { untilRest = sample(minBlocks, maxBlocks); return; }
        if (--untilRest <= 0) {
            rest = sample(minRest, maxRest);
            untilRest = sample(minBlocks, maxBlocks);
        }
    }
    public boolean resting() { return rest > 0; }
    public int restTicks() { return rest; }
    public void cancelRest() { rest = 0; }
    private int sample(int a, int b) {
        int low = Math.max(0, Math.min(a, b)), high = Math.max(low, Math.max(a, b));
        return low == high ? low : random.nextInt(low, high + 1);
    }
}
