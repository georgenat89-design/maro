package dev.maro.gametest;

import com.google.gson.JsonParser;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

final class RenderCompatibilityChecks {
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        System.out.println("[render-compat] Starting native renderer checks");
        // Exercise companion block outlines before any other renderer initializes its triangle mesh.
        context.runOnClient(c -> c.player.setPitch(90));
        context.waitTicks(8);
        if (!context.computeOnClient(c -> c.crosshairTarget != null && c.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK))
            throw new AssertionError("No solid block under the test player's crosshair");
        RenderFaultChecks.assertClean();
        HandShaderChecks.run(context);
        if (!Boolean.getBoolean("maro.gametest.renderCompatProfileOnly")) {
            context.runOnClient(c -> c.player.setPitch(0));
            PlayerEspChecks.run(context, world);
            TrajectoriesChecks.run(context, world);
            NametagsChecks.run(context);
            GuiMeshChecks.run(context);
            ArmorHudChecks.run(context, world);
            HudReadabilityChecks.run(context);
            SpotifyPhoneChecks.run(context);
            OrderDropperChecks.run(context, world);
            System.out.println("[render-compat] Individual renderers passed");
        }
        System.out.println("[render-compat] Enabling combined visuals");
        var names = Set.of("Nametags", "Trajectories", "Player ESP", "Custom Sky", "Custom Crosshair", "Compass",
            "Region Map", "Keystrokes", "Spotify Hud", "Motion Blur", "Color Correct", "View Model", "Inventory", "Better Looks", "Hand Shader");
        String profile = System.getProperty("maro.gametest.renderProfile", "");
        try {
            context.runOnClient(c -> {
                if (!profile.isBlank()) {
                    try {
                        var modules = JsonParser.parseString(Files.readString(Path.of(profile))).getAsJsonObject().getAsJsonObject("modules");
                        for (var module : ModuleManager.all()) {
                            if (!names.contains(module.getName()) || !modules.has(module.getName())) continue;
                            var json = modules.getAsJsonObject(module.getName());
                            for (var s : module.getSettings()) if (json.getAsJsonObject("settings").has(s.getName())) s.fromJson(json.getAsJsonObject("settings").get(s.getName()));
                        }
                    } catch (Exception e) { throw new AssertionError(e); }
                }
                c.player.getInventory().setStack(0, new ItemStack(Items.ENDER_PEARL, 16));
                c.player.getInventory().setSelectedSlot(0);
                c.options.hudHidden = false;
                c.options.setPerspective(Perspective.FIRST_PERSON);
                for (var module : ModuleManager.all()) if (names.contains(module.getName())) module.setEnabled(true);
                ModuleManager.get(FakePlayer.class).setEnabled(true);
            });
            context.waitTicks(10);
            context.runOnClient(c -> RenderAllocationChecks.start());
            context.waitTicks(70);
            context.takeScreenshot("maro-render-compat-first-person");
            context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
            context.waitTicks(80);
            context.runOnClient(c -> RenderAllocationChecks.stop());
            context.takeScreenshot("maro-render-compat-third-person");
            context.runOnClient(c -> c.setScreen(new dev.maro.gui.ClickGuiScreen()));
            context.waitTicks(10);
            context.takeScreenshot("maro-render-compat-menu");
            context.runOnClient(c -> c.setScreen(null));
            RenderFaultChecks.assertClean();
            System.out.println("[render-compat] PASS: native renderer checks and combined visual profile survived");
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                for (var module : ModuleManager.all()) if (names.contains(module.getName()) || module instanceof FakePlayer) {
                    module.setEnabled(false); module.getSettings().forEach(Setting::reset);
                }
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.player.getInventory().setStack(0, ItemStack.EMPTY);
            });
        }
    }
}
