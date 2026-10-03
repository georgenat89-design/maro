package dev.maro.gametest;

import dev.maro.gui.hud.SkinAccessoriesScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import dev.maro.render.accessories.AccessoryModels;
import dev.maro.render.accessories.SkinAccessoriesLayer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import javax.imageio.ImageIO;
import java.io.IOException;

final class SkinAccessoriesChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Setting<?> setting(SkinAccessories m, String name) { return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    static void run(ClientGameTestContext context) {
        checkWingDirection();
        SkinAccessories m = ModuleManager.get(SkinAccessories.class);
        require(m != null, "Skin Accessories not registered");
        int scale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        float[] rotation = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        try {
            context.runOnClient(c -> {
                c.options.getGuiScale().setValue(2); c.onResolutionChanged(); m.setEnabled(false);
                c.setScreen(new SkinAccessoriesScreen(null, m)); m.selectPreset("Cyber");
                ((SkinAccessoriesScreen)c.currentScreen).showAngle(0);
                ((ModeSetting)setting(m, "Halo")).set("Ring");
                ((ColorSetting)setting(m, "Halo Color")).set(0xFF55E6F5);
                ((BooleanSetting)setting(m, "Animate")).set(false);
            });
            context.waitTicks(5);
            require(!m.isEnabled(), "Preview unexpectedly enabled module");
            float[] after = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
            require(after[0] == rotation[0] && after[1] == rotation[1], "Preview rotated the real player");
            var screenshot = context.takeScreenshot("maro-skin-accessories-cyber");
            int[] bounds = context.computeOnClient(c -> {
                int w = Math.min(610, c.getWindow().getScaledWidth() - 20), h = Math.min(306, c.getWindow().getScaledHeight() - 20);
                int x = (c.getWindow().getScaledWidth() - w) / 2, y = (c.getWindow().getScaledHeight() - h) / 2;
                double s = c.getWindow().getScaleFactor();
                return new int[]{(int)((x + 10) * s), (int)((y + 35) * s), (int)((x + w * .43 + 2) * s), (int)((y + h - 45) * s)};
            });
            try {
                var image = ImageIO.read(screenshot.toFile()); int cyan = 0;
                for (int y = bounds[1]; y < bounds[3]; y++) for (int x = bounds[0]; x < bounds[2]; x++) {
                    int color = image.getRGB(x, y), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                    if (r < 130 && g > 165 && b > 170) cyan++;
                }
                require(cyan > 80, "3D preview did not render cyan halo geometry (pixels=" + cyan + ")");
            } catch (IOException e) { throw new AssertionError("Cannot inspect accessory screenshot", e); }
            context.runOnClient(c -> ((BooleanSetting)setting(m, "Animate")).set(true));

            context.runOnClient(c -> {
                m.selectPreset("Angel"); ((BooleanSetting)setting(m, "Animate")).set(false);
                ((SkinAccessoriesScreen)c.currentScreen).showAngle(0);
                ((NumberSetting)setting(m, "Wing Spread")).set(0.0);
            });
            context.waitTicks(3); context.takeScreenshot("maro-wings-folded-behind");
            context.runOnClient(c -> ((NumberSetting)setting(m, "Wing Spread")).set(90.0));
            context.waitTicks(3); context.takeScreenshot("maro-wings-open-outward");
            context.runOnClient(c -> {
                ((NumberSetting)setting(m, "Wing Spread")).reset();
                ((BooleanSetting)setting(m, "Animate")).set(true);
            });

            // Exercise every style through the actual GUI entity renderer and queued geometry.
            for (String slot : new String[]{"Head", "Wings", "Tail", "Halo", "Shoulders", "Back"}) {
                var mode = (ModeSetting)setting(m, slot);
                for (String style : mode.getModes()) {
                    context.runOnClient(c -> mode.set(style)); context.waitTicks(2);
                }
            }
            for (String preset : SkinAccessories.PRESETS) {
                context.runOnClient(c -> m.selectPreset(preset)); context.waitTicks(2);
                if (preset.equals("Angel") || preset.equals("Fox") || preset.equals("Dragon")) context.takeScreenshot("maro-skin-accessories-" + preset.toLowerCase());
            }
            // Real click on the first preset, then mix a part and rotate.
            int[] click = context.computeOnClient(c -> {
                int w = Math.min(610, c.getWindow().getScaledWidth() - 20), h = Math.min(306, c.getWindow().getScaledHeight() - 20);
                double s = c.getWindow().getScaleFactor();
                return new int[]{(int)(((c.getWindow().getScaledWidth() - w) / 2 + w * .43 + 25) * s),
                    (int)(((c.getWindow().getScaledHeight() - h) / 2 + 67) * s)};
            });
            context.getInput().setCursorPos(click[0], click[1]); context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick(); context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); context.waitTick();
            require(m.preset().equals("Dragon"), "Clicking accessory preset did not apply Dragon");
            context.runOnClient(c -> {
                var state = new PlayerEntityRenderState(); state.id = c.player.getId();
                state.equippedHeadStack = new ItemStack(Items.DIAMOND_HELMET);
                state.equippedChestStack = new ItemStack(Items.ELYTRA);
                require(!m.showHead(state) && !m.showWings(state), "Equipment overlap guards failed");
                ((BooleanSetting)setting(m, "Hide Head With Helmet")).set(false);
                ((BooleanSetting)setting(m, "Hide Wings With Elytra")).set(false);
                require(m.showHead(state) && m.showWings(state), "Equipment overlap options did not update");
                require(m.wants(state), "Disabled module did not allow its own preview");
                state.invisible = true; require(!m.wants(state), "Invisible player accessories rendered");
                state.invisible = false; state.spectator = true; require(!m.wants(state), "Spectator accessories rendered");
                state.spectator = false; state.id++; require(!m.wants(state), "Preview accessories leaked to another player");
                c.setScreen(null); m.setEnabled(true); c.options.setPerspective(Perspective.FIRST_PERSON);
                state.id = c.player.getId(); require(!m.wants(state), "Accessories filled first person camera");
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                require(m.wants(state), "Self accessories not visible in third person");
                state.id++; require(!m.wants(state), "Self target drew on other players");
                ((ModeSetting)setting(m, "Players")).set("Everyone"); require(m.wants(state), "Everyone target failed");
                ((BooleanSetting)setting(m, "Animate")).set(false); require(m.phase(state) == 0 && m.motionAmount(state) == 0, "Animation toggle did not freeze motion");
                m.selectPreset("Dragon"); ((BooleanSetting)setting(m, "Animate")).set(true);
                c.player.setPitch(0); c.options.sneakKey.setPressed(true);
            });
            context.waitTicks(5);
            require(context.computeOnClient(c -> c.player.isInSneakingPose()), "Third person pose check did not enter crouching");
            context.takeScreenshot("maro-skin-accessories-third-person-crouch");
            context.runOnClient(c -> {
                c.options.sneakKey.setPressed(false); c.player.setSneaking(false); m.setEnabled(false);
                c.options.getGuiScale().setValue(4); c.onResolutionChanged(); c.setScreen(new SkinAccessoriesScreen(null, m));
            });
            context.waitTicks(3); context.takeScreenshot("maro-skin-accessories-small-preview");
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null); m.setEnabled(false); m.getSettings().forEach(Setting::reset);
                c.player.setSneaking(false); c.player.setYaw(rotation[0]); c.player.setPitch(rotation[1]);
                c.options.sneakKey.setPressed(false);
                c.options.setPerspective(perspective); c.options.getGuiScale().setValue(scale); c.onResolutionChanged();
            });
        }
    }
    private static void checkWingDirection() {
        float previousWidth = -1;
        for (int spread = 0; spread <= 90; spread += 5) {
            var left = wingTip(AccessoryModels.Motion.LEFT_WING, spread, 0, 0);
            var right = wingTip(AccessoryModels.Motion.RIGHT_WING, spread, 0, 0);
            require(left.x >= previousWidth - .0001f, "Increasing spread folded the wings inward");
            require(left.z >= -.0001f && right.z >= -.0001f, "Spread moved wings toward the player's front");
            require(Math.abs(left.x + right.x) < .0001f && Math.abs(left.z - right.z) < .0001f, "Wing opening is not mirrored");
            previousWidth = left.x;
            for (int frame = 0; frame < 100; frame++) {
                var animated = wingTip(AccessoryModels.Motion.LEFT_WING, spread, frame * .1f, 2);
                require(animated.x >= -.0001f && animated.z >= -.0001f, "Strong wing animation crossed the shoulder plane");
            }
        }
        require(previousWidth > 9.99f, "90° did not fully open the wings outward");
        require(wingTip(AccessoryModels.Motion.LEFT_WING, 0, 0, 0).z > 9.99f, "0° did not fold wings behind the back");
    }
    private static Vector3f wingTip(AccessoryModels.Motion wing, float spread, float phase, float amount) {
        var pose = new MatrixStack();
        SkinAccessoriesLayer.applyWingPose(pose, wing, spread, 1, phase, amount);
        return pose.peek().getPositionMatrix().transformPosition(new Vector3f(wing == AccessoryModels.Motion.LEFT_WING ? 10 : -10, 0, 0));
    }
}
