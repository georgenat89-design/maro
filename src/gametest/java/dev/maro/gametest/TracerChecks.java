package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.module.impl.visuals.StorageESP;
import dev.maro.render.esp.PlayerEspRenderer;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.render.esp.TracerGeometry;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;

/** Regression coverage for camera-plane crossings, stable groups and crowded player tracers. */
final class TracerChecks {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object field(Object owner, Class<?> type, String name) {
        try {
            var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void field(Object owner, String name, Object value) {
        try {
            var f = BlockESP.class.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static long anchor(BlockESP module, long[] positions, Block block) {
        field(module, "shown", positions);
        field(module, "shownBlocks", new Block[] {block, block, block});
        field(module, "shownCount", 3);
        try {
            var pick = BlockESP.class.getDeclaredMethod("pickTracerTargets");
            pick.setAccessible(true); pick.invoke(module);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        require(module.tracerTargets() == 1, "Adjacent blocks did not form one tracer group");
        return positions[((int[]) field(module, BlockESP.class, "tracerTargets"))[0]];
    }

    static void geometry() {
        float[] out = new float[2], before = new float[2];
        for (boolean bottom : new boolean[] {false, true}) {
            for (float w : new float[] {1f, .05001f, .05f, .04999f, .00001f, 0f, -.00001f, -1f}) {
                require(TracerGeometry.project(.4f, .2f, w, bottom, out, 0), "Valid projection rejected");
                require(Float.isFinite(out[0]) && Float.isFinite(out[1]), "Nonfinite tracer endpoint");
                require(Math.abs(out[0]) <= 1.20001 && Math.abs(out[1]) <= 1.20001, "Unbounded tracer quad");
                float depth = Math.max(w, 1e-5f), sy = bottom ? -1f : 0f;
                float cross = out[0] * (.2f - sy * depth) - (out[1] - sy) * .4f;
                require(Math.abs(cross) < 1e-5f, "Clipping changed tracer direction from its origin");
            }
            for (float plane : new float[] {.05f, 0f}) {
                TracerGeometry.project(.4f, .2f, plane + 1e-6f, bottom, before, 0);
                TracerGeometry.project(.4f, .2f, plane - 1e-6f, bottom, out, 0);
                require(Math.hypot(out[0] - before[0], out[1] - before[1]) < .0001,
                        "Tracer popped across the camera plane");
            }
        }
        TracerGeometry.project(.2f, -.3f, 1f, false, out, 0);
        require(Math.abs(out[0] - .2f) < 1e-6 && Math.abs(out[1] + .3f) < 1e-6, "Onscreen target moved");
        require(!TracerGeometry.project(Float.NaN, 0, 1, false, out, 0), "Invalid projection accepted");
        ByteBuffer quad = ByteBuffer.allocate(48);
        for (float length : new float[] {0, .1f, .99f, 1.01f}) {
            TracerGeometry.writeQuad(quad, 0, new float[] {320, 180, 320 + length, 180}, 2, 640, 360, 1);
            require(quad.getFloat(0) != quad.getFloat(24) && quad.getFloat(4) != quad.getFloat(16),
                    "Subpixel tracer geometry disappeared");
        }
        System.out.println("[tracers] Projection continuity, bottom-origin direction and subpixel geometry passed");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        geometry();
        context.runOnClient(c -> {
            for (BlockESP module : new BlockESP[] {new BlockESP(), new StorageESP()}) {
                ((ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals("Tracer To")).findFirst().orElseThrow()).set("Each Group");
                long a = BlockPos.asLong(0, 100, 8), b = BlockPos.asLong(1, 100, 8), d = BlockPos.asLong(2, 100, 8);
                Block block = module instanceof StorageESP ? Blocks.CHEST : Blocks.DIAMOND_ORE;
                long chosen = anchor(module, new long[] {a, b, d}, block);
                require(chosen == anchor(module, new long[] {d, b, a}, block), "Group target moved after distance sorting");
                long removed = chosen;
                long e = BlockPos.asLong(3, 100, 8);
                long replacement = anchor(module, new long[] {b, d, e}, block);
                require(replacement != removed, "Removed group anchor was retained");
                require(replacement == anchor(module, new long[] {e, d, b}, block), "Replacement group anchor was unstable");
            }
            BlockESP blocks = ModuleManager.get(BlockESP.class);
            StorageESP storage = ModuleManager.get(StorageESP.class);
            blocks.setEnabled(true); storage.setEnabled(true);
            try {
                BlockEspRenderer.beginFrame(new Matrix4f(), new Matrix4f());
                BlockEspRenderer.prepareGlow(blocks); BlockEspRenderer.prepareGlow(storage);
                for (int i = 0; i < BlockEspRenderer.MAX_TRACERS; i++) {
                    BlockEspRenderer.addTracer(blocks, .5, .5, 0, Vec3d.ZERO, 0xFFFF00FF, 1);
                    BlockEspRenderer.addTracer(storage, -.5, -.5, 0, Vec3d.ZERO, 0xFF00FF00, 1);
                }
                require(BlockEspRenderer.tracerCount() == 4000, "One ESP module starved the other of its tracer budget");
                // Exercise all batches, bloom and both styles through the production pipeline.
                BlockEspRenderer.composite();
                require(blocks.isEnabled() && storage.isEnabled(), "Batched tracer pipeline failed");
            } finally {
                blocks.setEnabled(false); storage.setEnabled(false);
            }
        });
        PlayerESP esp = ModuleManager.get(PlayerESP.class);
        var ids = new ArrayList<Integer>();
        try {
            context.runOnClient(c -> {
                esp.getSettings().forEach(s -> s.reset());
                for (String name : new String[] {"Fill", "Outline", "Glow"})
                    ((BooleanSetting) esp.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow()).set(false);
                esp.setEnabled(true);
                var base = c.player.getEntityPos();
                for (int i = 0; i < 40; i++) {
                    var other = new OtherClientPlayerEntity(c.world, new GameProfile(new UUID(5, i + 1), "Tracer" + i));
                    double x = base.x + (i % 8 - 4) * 2, y = base.y, z = base.z + 12 + i / 8 * 2;
                    other.setPosition(x, y, z);
                    other.lastX = other.lastRenderX = x;
                    other.lastY = other.lastRenderY = y;
                    other.lastZ = other.lastRenderZ = z;
                    c.world.addEntity(other); ids.add(other.getId());
                }
            });
            context.waitTicks(5);
            require(context.computeOnClient(c -> (int) field(null, PlayerEspRenderer.class, "tracerCount")) == 40,
                    "Crowded player tracers were dropped");
            require(context.computeOnClient(c -> (int) field(null, PlayerEspRenderer.class, "playerCount")) == 0,
                    "Tracer-only mode rendered unnecessary silhouettes");
            context.takeScreenshot("maro-tracers-40-players");
            context.runOnClient(c -> ((ModeSetting) esp.getSettings().stream().filter(s -> s.getName().equals("Tracer Start")).findFirst().orElseThrow()).set("Bottom"));
            context.waitTicks(3);
            context.takeScreenshot("maro-tracers-40-players-bottom");
            System.out.println("[tracers] Block/Storage anchors remained stable; all 40 player tracers rendered");
        } finally {
            context.runOnClient(c -> {
                for (int id : ids) c.world.removeEntity(id, Entity.RemovalReason.DISCARDED);
                esp.setEnabled(false); esp.getSettings().forEach(s -> s.reset());
            });
        }
    }
}
