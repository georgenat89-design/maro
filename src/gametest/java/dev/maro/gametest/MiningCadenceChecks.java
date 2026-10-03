package dev.maro.gametest;

import dev.maro.util.MiningCadence;
import java.util.HashSet;
import java.util.Random;

final class MiningCadenceChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void run() {
        var cadence = new MiningCadence(new Random(71293)); cadence.reset(3, 5);
        var delays = new HashSet<Integer>(); var speeds = new HashSet<Float>();
        for (int action = 0; action < 100; action++) {
            cadence.prepare(action, 5, 1, .2f); // Inverted settings must be ordered safely.
            float rate = cadence.aimFactor(); speeds.add(rate);
            require(rate >= .8f && rate <= 1.2f, "Turn variation exceeded its configured range");
            int waits = 0;
            while (!cadence.reactionReady()) {
                cadence.prepare(action, 1, 5, .2f);
                require(cadence.aimFactor() == rate, "Turn speed changed while working on the same target");
                cadence.tick(); waits++;
                require(waits <= 5, "Reaction delay was resampled each tick");
            }
            require(waits >= 1, "Minimum reaction delay was ignored"); delays.add(waits);
            for (int tick = 0; tick < 30; tick++) {
                cadence.tick(); cadence.prepare(action, 1, 5, .2f);
                require(cadence.reactionReady(), "Cadence interrupted an already-started block");
            }
        }
        require(delays.size() >= 3 && speeds.size() > 50, "Cadence did not vary between actions");
        cadence.switchedTool(2); require(!cadence.toolReady(), "Tool delay did not start");
        cadence.tick(); require(!cadence.toolReady(), "Tool delay ended early");
        cadence.tick(); require(cadence.toolReady(), "Tool delay did not finish");
        cadence.reset(2, 2);
        cadence.completedBlock(true, 2, 2, 3, 3); require(!cadence.resting(), "Break started before the configured block count");
        cadence.completedBlock(true, 2, 2, 3, 3); require(cadence.restTicks() == 3, "Completed blocks did not schedule a break");
        for (int tick = 0; tick < 3; tick++) cadence.tick();
        require(!cadence.resting(), "Short break did not finish");
        cadence.prepare("zero", 0, 0, 0); require(cadence.reactionReady() && cadence.aimFactor() == 1, "Zero-delay profile added a wait");
        cadence.switchedTool(6); cadence.reset(3, 5);
        require(cadence.reactionReady() && cadence.toolReady() && !cadence.resting(), "Reset retained a previous session's waits");
    }
}
