// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import dev.maro.render.character.kit.Lights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(value=EnvType.CLIENT)
public final class CharacterLighting {
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final Lights NETHER = new Lights(0.2f, 1.0f, -0.7f, -0.2f, -1.0f, 0.7f);
    private static final Lights INVENTORY = new Lights(0.2f, -1.0f, 1.0f, -0.2f, -1.0f, 0.0f);
    private static final Lights ITEMS_FLAT = CharacterLighting.transformed(new Matrix4f().rotationY(-0.3926991f).rotateX(2.3561945f));
    private static final Lights ITEMS_3D = CharacterLighting.transformed(new Matrix4f().scaling(1.0f, -1.0f, 1.0f).rotateYXZ(1.0821041f, 3.2375858f, 0.0f).rotateYXZ(-0.3926991f, 2.3561945f, 0.0f));
    private static DiffuseLighting.Type entry = DiffuseLighting.Type.LEVEL;

    private CharacterLighting() {
    }

    public static void setup(DiffuseLighting.Type current) {
        entry = current;
    }

    static Lights current() {
        return switch (entry) {
            default -> throw new MatchException(null, null);
            case LEVEL -> {
                if (CharacterLighting.nether()) {
                    yield NETHER;
                }
                yield Lights.WORLD;
            }
            case ITEMS_FLAT -> ITEMS_FLAT;
            case ITEMS_3D -> ITEMS_3D;
            case ENTITY_IN_UI, PLAYER_SKIN -> INVENTORY;
        };
    }

    private static boolean nether() {
        ClientWorld level = MinecraftClient.getInstance().world;
        return level != null && level.getDimension().cardinalLightType() == DimensionType.CardinalLightType.NETHER;
    }

    private static Lights transformed(Matrix4f matrix) {
        Vector3f a = matrix.transformDirection((Vector3fc)LIGHT_0, new Vector3f());
        Vector3f b = matrix.transformDirection((Vector3fc)LIGHT_1, new Vector3f());
        return new Lights(a.x, a.y, a.z, b.x, b.y, b.z);
    }
}
