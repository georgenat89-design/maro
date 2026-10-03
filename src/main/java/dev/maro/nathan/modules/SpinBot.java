package dev.maro.nathan.modules;

import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import dev.maro.nathan.NameeProtectAddon;

/**
 * Spins your character, for show, and for nobody but you.
 *
 * <p>It turns the model as it is drawn and touches nothing else. Your yaw and
 * pitch - what you aim with, what you move by, what the server is told - are
 * never read for it and never written by it, so you look, walk, fight and fly
 * exactly as you would with it off, and other players see you as you are.
 *
 * <p><b>Where the turn goes.</b> The game places a player by a string of turns:
 * to the way the body faces, then over on to its front for gliding or swimming.
 * The spin is one more, added after all of those, about the model's own long
 * axis - the line from its feet to its head. Standing, that line is upright and
 * the spin is a pirouette. Gliding, the game has already laid that line along
 * your flight, so the same spin is a roll about the line you are flying along:
 * the pose is kept and the turn goes with it, without anything here having to
 * know which pose you are in. Going from one to the other the game swings that
 * line over smoothly, and the spin swings with it. It is a turn of the whole
 * model, so the head, the armour and whatever is in your hands turn as one.
 *
 * <p><b>Smooth.</b> The angle is worked out from the clock each time the model
 * is drawn, so it turns at the same rate at any frame rate, and is the same
 * angle however many times a frame the model is drawn. Turning it on winds it
 * up over a fifth of a second; turning it off lets it run down and come to rest
 * facing the way you really are, rather than snapping back.
 *
 * <p>There is nothing to see in first person, since there is no model there to
 * turn: your own view never moves. It shows wherever the model does - F5,
 * Freelook, Free Cam, the inventory screen.
 */
public class SpinBot extends Module {
    /** Which way round, seen from above. */
    public enum Direction {
        Clockwise,
        CounterClockwise("Counter-clockwise");

        private final String title;

        Direction() {
            this.title = name();
        }

        Direction(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Seconds for the spin to wind up, run down, and come to rest. */
    private static final double EASE = 0.2;

    private static SpinBot instance;

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Double> spinSpeed = sgMain.add(new DoubleSetting.Builder()
        .name("spin-speed")
        .description("How fast your character turns, in degrees a second. 360 is one turn a second.")
        .defaultValue(360)
        .range(10, 7200)
        .sliderRange(30, 1800)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Direction> direction = sgMain.add(new EnumSetting.Builder<Direction>()
        .name("direction")
        .description("Which way round, seen from above.")
        .defaultValue(Direction.Clockwise)
        .build()
    );

    // All in degrees, and all moved by the clock.
    private double angle;
    private double rate;
    private long lastAt;

    public SpinBot() {
        super(NameeProtectAddon.CATEGORY, "spin-bot", "Spins your character for show. Only the model turns: your aim, movement and camera are untouched.");

        instance = this;
    }

    /**
     * How far round to turn the model of the entity with this id, right now, in
     * degrees. Nought for anyone but you, and nought once a spin that has been
     * turned off has come to rest.
     */
    public static float angleFor(int entityId) {
        SpinBot module = instance;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (module == null || mc.player == null || mc.player.getId() != entityId) return 0;

        return (float) module.advance();
    }

    private double advance() {
        long now = System.nanoTime();
        double seconds = lastAt == 0 ? 0 : Math.min((now - lastAt) / 1.0e9, 0.1);

        lastAt = now;

        // Seen from above, the game's turn about this axis runs the other way.
        double wanted = isActive() ? spinSpeed.get() * (direction.get() == Direction.Clockwise ? -1 : 1) : 0;

        // The rate closes on what is wanted by a fixed share in a fixed time,
        // whatever the frame rate: wound up and run down, never switched.
        rate += (wanted - rate) * (1 - Math.exp(-seconds / EASE));
        angle += rate * seconds;

        // Kept within a turn, so it never grows to where a float has no degrees
        // left to spare.
        angle = angle % 360;

        if (!isActive() && Math.abs(rate) < 20) {
            // Nearly stopped, and off: come to rest facing the way you really
            // are, by whichever way round is shorter.
            double rest = angle > 180 ? angle - 360 : angle < -180 ? angle + 360 : angle;

            rest *= Math.exp(-seconds / (EASE / 2));

            if (Math.abs(rest) < 0.05) {
                rest = 0;
                rate = 0;
            }

            angle = rest;
        }

        return angle;
    }
}
