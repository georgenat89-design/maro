package dev.maro.render.esp;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The texture behind each armour-style render layer (armour, elytra, armour trims). Those layers
 * have no outline twin, so without this the Player ESP silhouette would leave them out; knowing the
 * texture lets it draw them through an outline layer of its own instead. Filled in by
 * {@link dev.maro.mixin.EquipmentLayersMixin} as the game makes the layers, which it does once per
 * texture, so this stays small.
 */
public final class EquipmentTextures {
    private static final Map<RenderLayer, Identifier> TEXTURES = new ConcurrentHashMap<>();

    private EquipmentTextures() {
    }

    public static void remember(RenderLayer layer, Identifier texture) {
        if (layer != null && texture != null) TEXTURES.putIfAbsent(layer, texture);
    }

    /** The texture {@code layer} was made from, or null if it is not an armour-style layer. */
    public static Identifier of(RenderLayer layer) {
        return TEXTURES.get(layer);
    }
}
