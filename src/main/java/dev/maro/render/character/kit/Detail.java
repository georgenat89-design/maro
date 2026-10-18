// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public enum Detail {
    LOW("Low", 7500, 0.42f),
    MEDIUM("Medium", 15000, 0.3f),
    HIGH("High", 30000, 0.21f);

    public final String label;
    public final int budget;
    public final float step;

    private Detail(String label, int budget, float step) {
        this.label = label;
        this.budget = budget;
        this.step = step;
    }

    public static Detail byLabel(String label) {
        for (Detail d : Detail.values()) {
            if (!d.label.equalsIgnoreCase(label) && !d.name().equalsIgnoreCase(label)) continue;
            return d;
        }
        return MEDIUM;
    }
}
