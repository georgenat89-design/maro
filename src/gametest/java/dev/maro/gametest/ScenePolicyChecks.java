package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.module.impl.visuals.FakeBlock;
import dev.maro.render.capture.StreamproofScenePolicy;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/** Actual chunk reload preparation and stop-time lifecycle behavior of paused scene modules. */
final class ScenePolicyChecks {
    private ScenePolicyChecks() { }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean terrainPending() {
        try {
            var field = StreamproofScenePolicy.class.getDeclaredField("terrainRestoring");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static void run(ClientGameTestContext context) {
        FakeBlock fake = ModuleManager.get(FakeBlock.class);
        require(fake != null, "Fake Block is not registered");
        require(!ModuleManager.get(Streamproof.class).isEnabled() && !StreamproofScenePolicy.running(),
                "Scene policy fixture must start outside capture");
        Map<Module, Boolean> enabled = new LinkedHashMap<>();
        Map<Setting<?>, JsonElement> settings = new LinkedHashMap<>();
        context.runOnClient(c -> {
            for (Module module : ModuleManager.all()) enabled.put(module, module.isEnabled());
            fake.getSettings().forEach(setting -> settings.put(setting, setting.toJson().deepCopy()));
        });
        try {
            context.runOnClient(c -> {
                fake.setEnabled(false);
                fake.getSettings().forEach(Setting::reset);
                fake.setSource(Items.STONE);
                fake.setReplace(Items.GOLD_BLOCK);
                fake.setEnabled(true);
                require(FakeBlock.spoofingWorld(), "Fake Block did not install its real chunk-mesh replacement");
                StreamproofScenePolicy.enable();
                require(!fake.isEnabled() && !FakeBlock.spoofingWorld(), "Policy did not restore ordinary block states before rebuilding");
                require(terrainPending() && !StreamproofScenePolicy.ready(),
                        "Policy declared terrain restored before a post-reload world frame");
            });
            boolean restored = false;
            for (int tick = 0; tick < 800 && !restored; tick++) {
                context.waitTick();
                restored = context.computeOnClient(c -> StreamproofScenePolicy.ready());
            }
            require(restored, "Ordinary chunk rebuild did not settle");
            context.runOnClient(c -> {
                require(!terrainPending(), "The initial terrain-restoration latch did not clear");
                c.worldRenderer.reload();
                require(StreamproofScenePolicy.ready() && !terrainPending(),
                        "Later vanilla terrain work incorrectly re-gated private visuals");
                StreamproofScenePolicy.disable();
                require(fake.isEnabled() && FakeBlock.spoofingWorld(), "Disabling protection did not restore requested Fake Block state");
                StreamproofScenePolicy.enable();
                require(!fake.isEnabled() && StreamproofScenePolicy.configuredEnabled(fake, false),
                        "Shutdown fixture did not remember a paused module");
                StreamproofScenePolicy.shutdown();
                require(!StreamproofScenePolicy.running() && StreamproofScenePolicy.count() == 0
                                && !fake.isEnabled() && !FakeBlock.spoofingWorld() && !terrainPending(),
                        "Shutdown reactivated scene effects or retained capture policy state");
            });
            System.out.println("[scene-policy] PASS: real Fake Block chunk replacement cleared, readiness waits a rebuild frame and vanilla task completion once, subsequent terrain updates do not hide visuals, disable restores requested state, shutdown clears without reactivation");
        } finally {
            context.runOnClient(c -> {
                StreamproofScenePolicy.shutdown();
                fake.setEnabled(false);
                settings.forEach(Setting::fromJson);
                enabled.forEach(Module::setEnabled);
            });
            context.waitTicks(3);
        }
    }
}
