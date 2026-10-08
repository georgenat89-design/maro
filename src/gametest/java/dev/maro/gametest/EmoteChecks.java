package dev.maro.gametest;

import dev.maro.gui.hud.EmoteWheelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Emotes;
import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;

import java.util.Locale;

/**
 * Emotes: an emote poses the player model (a T-pose holds the arms out), the wheel opens with a
 * preview in every segment, picking one plays it from the front, and walking stops it and puts
 * the camera back.
 */
final class EmoteChecks {
    private EmoteChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Emotes emotes = ModuleManager.get(Emotes.class);
        require(emotes != null, "Emotes was not registered");
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("clear @a");
        context.runOnClient(c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.player.setPitch(0f);
            emotes.getSettings().forEach(Setting::reset);
            emotes.setEnabled(true);
        });

        // The pose itself: a T-pose on a render state turns the arms straight out.
        float[] arms = context.computeOnClient(c -> {
            var renderer = (LivingEntityRenderer) c.getEntityRenderDispatcher().getRenderer(c.player);
            var state = (BipedEntityRenderState) renderer.createRenderState();
            renderer.updateRenderState(c.player, state, 1f);
            var model = (BipedEntityModel) renderer.getModel();
            ((EmoteRenderState) state).maro$setEmote(Emote.T_POSE, 1f, 1f);
            model.setAngles(state);
            float right = model.rightArm.roll, left = model.leftArm.roll;
            ((EmoteRenderState) state).maro$setEmote(null, 0, 0);
            model.setAngles(state);
            return new float[] {right, left, model.rightArm.roll};
        });
        System.out.printf(Locale.ROOT, "EMOTES T-pose arm roll: right %.2f, left %.2f, after %.2f%n", arms[0], arms[1], arms[2]);
        require(Math.abs(arms[0] - Math.PI / 2) < 0.05 && Math.abs(arms[1] + Math.PI / 2) < 0.05, "A T-pose did not hold the arms out");
        require(Math.abs(arms[2]) < 0.5, "The arms stayed out once the emote was gone");

        try {
            context.runOnClient(c -> c.setScreen(new EmoteWheelScreen(emotes)));
            context.waitTicks(12);
            context.takeScreenshot("maro-emote-wheel");
            // Point at the third segment, as the mouse would.
            context.runOnClient(c -> {
                var screen = (EmoteWheelScreen) c.currentScreen;
                float r = Math.max(70f, Math.min(130f, Math.min(screen.width, screen.height) * 0.36f)) * 0.71f;
                double angle = Math.toRadians(-90 + 2 * 36);
                screen.mouseMoved(screen.width / 2f + Math.cos(angle) * r, screen.height / 2f + Math.sin(angle) * r);
            });
            context.waitTicks(6);
            context.takeScreenshot("maro-emote-wheel-hover");
            Emote pointed = context.computeOnClient(c -> ((EmoteWheelScreen) c.currentScreen).hoveredEmote());
            require(pointed == Emote.DAB, "Pointing at the third segment did not pick Dab: " + pointed);
            context.runOnClient(c -> ((EmoteWheelScreen) c.currentScreen).choose(2));
            context.waitTicks(4);
            require(context.computeOnClient(c -> c.currentScreen == null && emotes.playing() == Emote.DAB
                    && c.options.getPerspective() == Perspective.THIRD_PERSON_FRONT), "Choosing Dab did not play it from the front");
            context.waitTicks(16);
            context.takeScreenshot("maro-emote-dab");

            for (Emote emote : new Emote[] {Emote.WAVE, Emote.FLOSS, Emote.CHEER, Emote.T_POSE, Emote.SIT}) {
                context.runOnClient(c -> emotes.play(emote));
                context.waitTicks(14);
                context.takeScreenshot("maro-emote-" + emote.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }

            // Walking ends it and brings the camera back.
            context.runOnClient(c -> c.options.forwardKey.setPressed(true));
            context.waitTicks(3);
            context.runOnClient(c -> c.options.forwardKey.setPressed(false));
            context.waitTicks(12);
            require(context.computeOnClient(c -> emotes.playing() == null && c.options.getPerspective() == Perspective.FIRST_PERSON),
                    "Walking did not stop the emote and put the camera back");
        } finally {
            context.runOnClient(c -> {
                c.options.forwardKey.setPressed(false);
                if (c.currentScreen instanceof EmoteWheelScreen) c.setScreen(null);
                emotes.setEnabled(false);
                emotes.getSettings().forEach(Setting::reset);
                c.options.setPerspective(Perspective.FIRST_PERSON);
            });
        }
    }
}
