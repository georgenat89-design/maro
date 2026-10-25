package dev.maro.render.capture;

import dev.maro.Maro;
import dev.maro.anubis.inspect.WeaponInspectModule;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.NameProtect;
import dev.maro.module.impl.movement.Vehicles;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.module.impl.visuals.*;
import dev.maro.nathan.modules.FakeXp;
import dev.maro.nathan.modules.FreeCam;
import dev.maro.nathan.modules.FreeLook;
import dev.maro.nathan.modules.KeyZoom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Restores scene-changing effects while one ordinary world render supplies clean captures. */
public final class StreamproofScenePolicy {
    private static final Set<Class<? extends Module>> INCOMPATIBLE = Set.of(
            FreeCam.class, FreeLook.class, KeyZoom.class, CustomModel.class, SkinAccessories.class,
            CustomSky.class, Fullbright.class, NoRender.class, FakeBlock.class, ViewModel.class,
            CustomTotem.class, ItemInspect.class, WeaponInspectModule.class, Emotes.class,
            Acrobatics.class, Pet.class, Geeked.class, BetterLooks.class, TexturePacks.class,
            Vehicles.class, FakePlayer.class, PotatoGraphics.class, NameProtect.class, FakeXp.class);
    private static final Map<Module, Boolean> REQUESTED = new LinkedHashMap<>();
    private static volatile boolean running;
    private static boolean suspending;
    private static int failures;
    private static boolean terrainRestoring, terrainRendered, observingTerrain;
    private static ClientWorld terrainWorld;

    private StreamproofScenePolicy() { }

    public static boolean running() {
        return running;
    }

    public static boolean incompatible(Module module) {
        return module != null && INCOMPATIBLE.contains(module.getClass());
    }

    public static void enable() {
        if (running) return;
        REQUESTED.clear();
        failures = 0;
        terrainRestoring = terrainRendered = false;
        terrainWorld = null;
        if (!observingTerrain) {
            observingTerrain = true;
            WorldRenderEvents.END_MAIN.register(context -> {
                if (running && terrainRestoring && MinecraftClient.getInstance().world == terrainWorld) terrainRendered = true;
            });
        }
        running = true;
        suspending = true;
        try {
            for (Module module : ModuleManager.all()) {
                if (!incompatible(module) || !module.isEnabled()) continue;
                REQUESTED.put(module, true);
                if (module instanceof FakeBlock && MinecraftClient.getInstance().world != null) {
                    terrainRestoring = true;
                    terrainWorld = MinecraftClient.getInstance().world;
                }
                try {
                    module.setEnabled(false);
                } catch (Throwable error) {
                    failures++;
                    Maro.LOGGER.error("Could not pause {} for Stream Proof", module.getName(), error);
                }
            }
        } finally {
            suspending = false;
        }
        try {
            CustomTotem.restoreForCapture();
        } catch (Throwable error) {
            failures++;
            Maro.LOGGER.error("Could not restore the ordinary totem texture for Stream Proof", error);
        }
    }

    public static void disable() {
        if (!running) return;
        running = false;
        terrainRestoring = terrainRendered = false;
        terrainWorld = null;
        var restore = new ArrayList<>(REQUESTED.keySet());
        REQUESTED.clear();
        for (Module module : restore) {
            try {
                module.setEnabled(true);
            } catch (Throwable error) {
                Maro.LOGGER.error("Could not restore {} after Stream Proof", module.getName(), error);
                try {
                    module.setEnabled(false);
                } catch (Throwable cleanup) {
                    Maro.LOGGER.error("Could not clean up {} after its failed restoration", module.getName(), cleanup);
                }
            }
        }
        failures = 0;
    }

    /** Client shutdown must not reactivate earlier modules or start new resource reloads. */
    public static void shutdown() {
        running = suspending = false;
        REQUESTED.clear();
        failures = 0;
        terrainRestoring = terrainRendered = false;
        terrainWorld = null;
    }

    /** Called before Module's equality check so changes to a paused module still update intent. */
    public static boolean blockedChange(Module module, boolean on) {
        if (!running || suspending || !incompatible(module)) return false;
        if (on) {
            REQUESTED.put(module, true);
            return true;
        }
        REQUESTED.remove(module);
        return false;
    }

    /** Saved configs retain the user's choices while their effects are temporarily paused. */
    public static boolean configuredEnabled(Module module, boolean actual) {
        return running && REQUESTED.containsKey(module) ? REQUESTED.get(module) : actual;
    }

    public static int count() {
        return REQUESTED.size();
    }

    /** Atlas changes are asynchronous and must settle before clean presentation can be advertised. */
    public static boolean ready() {
        if (!running) return true;
        var client = MinecraftClient.getInstance();
        if (failures != 0 || client.getOverlay() != null
                || !PotatoGraphics.texturesSettled() || !CustomTotem.textureSettled()) return false;
        if (terrainRestoring) {
            if (client.world == terrainWorld && (!terrainRendered || !client.worldRenderer.isTerrainRenderComplete())) return false;
            terrainRestoring = terrainRendered = false;
            terrainWorld = null;
        }
        return true;
    }

    public static String status() {
        if (!running) return "Scene effects restored";
        if (failures > 0) return "Scene restoration failed; capture protection is not ready";
        if (!ready()) return "Restoring ordinary gameplay textures and terrain";
        return count() + " scene effects paused";
    }
}
