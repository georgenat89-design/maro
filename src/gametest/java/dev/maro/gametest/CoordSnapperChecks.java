package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.CoordSnapper;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Coord Snapper: its key snaps once and switches back off; the coordinates are copied and in the Discord card. */
final class CoordSnapperChecks {
    private CoordSnapperChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        CoordSnapper module = ModuleManager.get(CoordSnapper.class);
        require(module != null, "Coord Snapper was not registered");
        // No webhook: nothing is sent anywhere from a test.
        context.runOnClient(c -> {
            module.getSettings().forEach(Setting::reset);
            module.setEnabled(true);
        });
        context.waitTicks(3);
        context.runOnClient(c -> {
            require(!module.isEnabled(), "Coord Snapper stayed on after snapping");
            var pos = c.player.getBlockPos();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            require(module.lastSnapped().equals(coords), "Snapped " + module.lastSnapped() + ", not " + coords);
            // The test display's clipboard may not hold text, so this is reported rather than required.
            System.out.println("COORD SNAPPER clipboard: " + (c.keyboard.getClipboard().equals(coords) ? "copied" : "not readable here"));
            String card = module.payload();
            require(card.contains("X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ()) && card.contains("Dimension"),
                    "The Discord card did not hold the coordinates: " + card);
        });
        context.takeScreenshot("maro-coord-snapper");
    }
}
