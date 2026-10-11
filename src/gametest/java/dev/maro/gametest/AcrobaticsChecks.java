package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Acrobatics;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

/**
 * Acrobatics: a jump turns into a back flip that lands, the trick key flips you standing still, and
 * in first person the view can flip too.
 */
final class AcrobaticsChecks {
    private AcrobaticsChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(Acrobatics module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Acrobatics module = ModuleManager.get(Acrobatics.class);
        require(module != null, "Acrobatics was not registered");
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        try {
            world.getServer().runCommand("tp @a 49 -60 30 0 0");
            context.waitTicks(5);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = false;
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            context.waitTicks(5);

            // A jump becomes a back flip, which lands.
            int landedBefore = context.computeOnClient(c -> module.landed());
            context.getInput().holdKey(o -> o.jumpKey);
            boolean flipping = false;
            for (int i = 0; i < 6 && !flipping; i++) {
                context.waitTick();
                flipping = context.computeOnClient(c -> module.tricking(c.player));
            }
            context.getInput().releaseKey(o -> o.jumpKey);
            require(flipping, "Jumping did not start a flip");
            context.waitTicks(4);
            float angle = context.computeOnClient(c -> module.angle(c.player, 0));
            require(angle > 30 && angle < 330, "Mid-jump the flip should be part way round: " + angle);
            // Mid flip the knees are pulled up and the hands are down at them, not hanging at the sides.
            float[] limbs = context.computeOnClient(c -> module.limbsNow(c.player));
            System.out.println("ACROBATICS mid flip: angle " + Math.round(angle) + ", arm pitch " + (limbs == null ? "none" : limbs[0])
                    + ", leg pitch " + (limbs == null ? "none" : limbs[1]));
            require(limbs != null && limbs[1] < -0.8f && limbs[0] < -0.6f, "Mid flip the knees should be tucked with the hands on them");
            context.takeScreenshot("maro-acrobatics-flip");
            for (int i = 0; i < 40 && context.computeOnClient(c -> module.landed()) == landedBefore; i++) context.waitTick();
            require(context.computeOnClient(c -> module.landed()) > landedBefore, "The flip never landed");

            // The trick key, standing still: a side flip with a hop.
            context.runOnClient(c -> {
                ((ModeSetting) setting(module, "Trick")).set("Side Flip");
                module.perform(c.player);
            });
            context.waitTicks(6);
            require(context.computeOnClient(c -> module.tricking(c.player)), "The trick key did not flip you standing still");
            context.takeScreenshot("maro-acrobatics-side");

            // In first person the view flips with you.
            context.waitTicks(20);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                ((ModeSetting) setting(module, "Trick")).set("Front Flip");
                ((BooleanSetting) setting(module, "First Person Flip")).set(true);
                module.perform(c.player);
            });
            context.waitTicks(4);
            require(context.computeOnClient(c -> Acrobatics.cameraTurn(0) != null), "First Person Flip did not turn the view");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
                c.player.getAbilities().flying = flying;
            });
            context.waitTicks(3);
        }
    }
}
