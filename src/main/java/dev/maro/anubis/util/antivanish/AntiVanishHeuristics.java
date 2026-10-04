// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Anubis by 4ldenz, recovered from the user-provided Anubis Client Beta 0.9.8.jar.
// Modified for Maro / Yarn 1.21.11 on 2026-10-04; see THIRD_PARTY.md.
package dev.maro.anubis.util.antivanish;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class AntiVanishHeuristics {
    private AntiVanishHeuristics() {
    }

    public static boolean suspiciousSound(String id) {
        String path = AntiVanishHeuristics.path(id);
        return path.contains("chest.open") || path.contains("chest.close") || path.contains("chest.locked") || path.contains("barrel.open") || path.contains("barrel.close") || path.contains("shulker_box.open") || path.contains("shulker_box.close") || path.contains("door.open") || path.contains("door.close") || path.contains("fence_gate.open") || path.contains("fence_gate.close") || path.contains("button.click") || path.contains("lever.click");
    }

    public static boolean suspiciousParticle(String id) {
        String path = AntiVanishHeuristics.path(id);
        return path.contains("crit") || path.contains("enchanted_hit") || path.contains("damage_indicator") || path.contains("smoke") || path.equals("block");
    }

    public static String path(String id) {
        if (id == null) {
            return "unknown";
        }
        int split = id.indexOf(58);
        return (split >= 0 && split + 1 < id.length() ? id.substring(split + 1) : id).toLowerCase(Locale.ROOT);
    }
}

