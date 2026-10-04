package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.nathan.modules.RegionMap;
import dev.maro.nathan.regionmap.RegionGrid;
import dev.maro.nathan.regionmap.RegionMapRaster;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import java.util.ArrayList;
import java.util.UUID;

/** Captures a populated panel and compact map; verifies fitting, migration and raster reuse. */
final class HudReadabilityChecks {
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static dev.maro.setting.Setting<?> setting(Module module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
    private static Object raster(RegionMap map) {
        try { var field = RegionMap.class.getDeclaredField("gridTexture"); field.setAccessible(true); return field.get(map); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void run(ClientGameTestContext context) {
        var staff = ModuleManager.get(StaffNotifier.class);
        var map = ModuleManager.get(RegionMap.class);
        var ids = new ArrayList<UUID>();
        context.runOnClient(client -> {
            staff.getSettings().forEach(dev.maro.setting.Setting::reset);
            map.getSettings().forEach(dev.maro.setting.Setting::reset);
            setting(staff, "sound alerts").fromJson(new JsonPrimitive(false));
            setting(staff, "alerts").fromJson(new JsonPrimitive(false));
            setting(staff, "notify existing").fromJson(new JsonPrimitive(false));
            staff.setEnabled(true); map.setEnabled(true);
            for (String name : new String[]{"CryptoDaveYt", "Frenk_Btw", "MunkerLich", "Napooo_", "u_vv", "W1zox_"}) {
                var id = UUID.randomUUID(); ids.add(id); StaffNotifierChecks.tab(client, id, name, true, true);
            }
        });
        try {
            context.waitTicks(12);
            context.runOnClient(client -> {
                require(staff.hudStaff().size() == 6, "Readability preview lost staff members");
                require(setting(map, "scale").toJson().getAsDouble() == .8, "Map default did not become smaller");
                require(map.mapHeight() <= client.getWindow().getScaledHeight() * .42 + .1, "Map exceeded screen height budget");
                require(map.mapWidth() <= client.getWindow().getScaledWidth() * .25 + .1, "Map exceeded screen width budget");
                int[] fills = {0xFF376BBA,0xFFD66770,0xFF528721,0xFFBD7928,0xFF9250BE,0xFF169698,0xFFB541B9};
                int[] inks = new int[7]; java.util.Arrays.fill(inks, 0xFFF4F7FF);
                var rendered = RegionMapRaster.create(250, fills, inks, true);
                require(rendered.labels() == RegionGrid.count(), "Compact raster omitted region numbers");
                var old = map.saveExtra(); old.remove("region-layout-revision");
                var scaleSetting = setting(map, "scale"); scaleSetting.fromJson(new JsonPrimitive(1));
                old = map.saveExtra(); old.remove("region-layout-revision");
                var restored = new RegionMap(); restored.loadExtra(old);
                require(setting(restored, "scale").toJson().getAsDouble() == .8, "Old default layout was not migrated");
                scaleSetting.fromJson(new JsonPrimitive(1.5));
                old = map.saveExtra(); old.remove("region-layout-revision"); restored = new RegionMap(); restored.loadExtra(old);
                require(setting(restored, "scale").toJson().getAsDouble() == 1.5, "Custom map scale was overwritten");
                scaleSetting.reset();
            });
            context.waitTicks(100);
            context.takeScreenshot("maro-readable-staff-and-compact-map");
            Object before = context.computeOnClient(client -> raster(map));
            context.waitTicks(4);
            require(before == context.computeOnClient(client -> raster(map)), "Stable compact map rebuilt its cached raster");
            context.runOnClient(client -> setting(staff, "text size").fromJson(new JsonPrimitive(1.3)));
            context.waitTicks(2); context.takeScreenshot("maro-staff-larger-text");
            context.runOnClient(client -> setting(map, "scale").fromJson(new JsonPrimitive(4)));
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(map.mapHeight() <= client.getWindow().getScaledHeight() * .42 + .1, "Large saved map scale escaped the screen budget");
                map.moveMap(10000, 10000);
                require(map.mapLeft()+map.mapWidth() <= client.getWindow().getScaledWidth()+.1
                    && map.mapTop()+map.mapHeight() <= client.getWindow().getScaledHeight()+.1, "Placement escaped the screen");
                setting(map, "auto fit screen").fromJson(new JsonPrimitive(false));
                require(map.mapScale() == 4, "Disabling screen fitting did not restore the requested scale");
            });
        } finally {
            context.runOnClient(client -> {
                staff.setEnabled(false); map.setEnabled(false);
                staff.getSettings().forEach(dev.maro.setting.Setting::reset);
                map.getSettings().forEach(dev.maro.setting.Setting::reset);
                client.getNetworkHandler().onPlayerRemove(new PlayerRemoveS2CPacket(ids));
            });
        }
    }
}
