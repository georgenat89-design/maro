package dev.maro.gametest;

import com.google.gson.JsonParser;
import dev.maro.config.ConfigManager;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.AirStuck;
import dev.maro.module.impl.movement.NoFall;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Native movement and actual server fall damage, with observation after packet rewriting. */
public final class AirFallChecks {
    private record Move(boolean full, boolean ground, boolean collision, double y) { }
    private static final List<Move> moves = new CopyOnWriteArrayList<>();
    private static final AtomicInteger tickEnds = new AtomicInteger(), commands = new AtomicInteger(), confirmations = new AtomicInteger();
    private static volatile ClientConnection watched;

    public static void sent(ClientConnection connection, Packet<?> packet) {
        if (connection != watched) return;
        if (packet instanceof PlayerMoveC2SPacket move) moves.add(new Move(move instanceof PlayerMoveC2SPacket.Full,
                move.isOnGround(), move.horizontalCollision(), move.getY(Double.NaN)));
        else if (packet instanceof ClientTickEndC2SPacket) tickEnds.incrementAndGet();
        else if (packet instanceof CommandExecutionC2SPacket) commands.incrementAndGet();
        else if (packet instanceof TeleportConfirmC2SPacket) confirmations.incrementAndGet();
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void await(ClientGameTestContext context, BooleanSupplier done, String message) {
        for (int i = 0; i < 180; i++) { if (done.getAsBoolean()) return; context.waitTick(); }
        throw new AssertionError(message);
    }
    private static void record(ClientGameTestContext context) {
        watched = null;
        moves.clear();
        tickEnds.set(0);
        commands.set(0);
        confirmations.set(0);
        context.runOnClient(c -> watched = c.getNetworkHandler().getConnection());
    }
    private static float health(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().getHealth());
    }
    private static void heal(TestSingleplayerContext world) {
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerManager().getPlayerList().getFirst();
            p.setHealth(20);
            p.fallDistance = 0;
            p.getHungerManager().setFoodLevel(20);
        });
    }
    private static void drop(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0.5 78 0.5 0 0");
        context.waitTicks(3);
        await(context, () -> context.computeOnClient(c -> c.player.isOnGround() && c.player.getY() < 63), "Player did not land on the test floor");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var air = ModuleManager.get(AirStuck.class);
        var noFall = ModuleManager.get(NoFall.class);
        require(air != null && noFall != null, "Air Stuck or No Fall not registered");
        require(air.getCategory() == Category.MOVEMENT && noFall.getCategory() == Category.MOVEMENT, "Movement modules have wrong category");
        var initial = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        var mode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        String oldConfig = ConfigManager.getCurrent();
        int oldBind = air.getBind().get();
        try {
            context.runOnClient(c -> { air.setEnabled(false); noFall.setEnabled(false); c.setScreen(null); });
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -4 61 -4 4 110 4 air");
            world.getServer().runCommand("fill -4 60 -4 4 60 4 stone");
            world.getServer().runCommand("tp @a 0.5 94 0.5 0 0");
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerManager().getPlayerList().getFirst();
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
            });
            context.waitTicks(4);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = false;
                require(c.player.getVelocity().y < 0, "Freeze fixture is not falling");
                air.setEnabled(true);
                c.options.forwardKey.setPressed(true);
                c.options.jumpKey.setPressed(true);
            });
            var frozen = context.computeOnClient(c -> c.player.getEntityPos());
            record(context);
            context.runOnClient(c -> {
                var move = new PlayerMoveC2SPacket.Full(c.player.getX(), c.player.getY() - 2, c.player.getZ(), 30, 10, false, false);
                c.getNetworkHandler().getConnection().send(move);
                c.getNetworkHandler().getConnection().send(move, null);
                c.getNetworkHandler().getConnection().send(move, null, true);
                c.getNetworkHandler().sendChatCommand("time query daytime");
            });
            context.waitTicks(20);
            require(context.computeOnClient(c -> c.player.getEntityPos().squaredDistanceTo(frozen) < 1.0E-12), "Air Stuck moved under gravity/input");
            require(moves.isEmpty() && tickEnds.get() == 0, "Movement or tick-end packets escaped freeze: " + moves.size() + "/" + tickEnds.get());
            require(commands.get() == 1, "Freeze suppressed a nonmovement command");
            world.getServer().runCommand("tp @a 12.5 96 0.5 0 0");
            await(context, () -> context.computeOnClient(c -> Math.abs(c.player.getX() - 12.5) < 0.01), "Server correction was blocked by Air Stuck");
            context.waitTicks(5);
            require(confirmations.get() > 0, "Teleport confirmation was suppressed");
            context.takeScreenshot("maro-air-stuck");
            context.runOnClient(c -> {
                c.options.forwardKey.setPressed(false);
                c.options.jumpKey.setPressed(false);
                noFall.setEnabled(true);
            });
            context.waitTicks(4);
            require(moves.isEmpty(), "No Fall bypassed Air Stuck's movement freeze");
            context.runOnClient(c -> { air.setEnabled(false); noFall.setEnabled(false); });
            record(context);
            double releasedY = context.computeOnClient(c -> c.player.getY());
            context.waitTicks(8);
            require(context.computeOnClient(c -> c.player.getY() < releasedY - 0.1), "Gravity did not resume after disabling Air Stuck");
            require(!moves.isEmpty() && tickEnds.get() > 0, "Native movement/tick-end publication did not resume");
            System.out.println("[air-stuck-proof] native midair/input freeze, all send overloads suppressed, commands and server teleport confirmations pass, release resumes gravity");

            world.getServer().runCommand("gamemode survival @a");
            heal(world);
            drop(context, world);
            context.waitTicks(4);
            float baseline = health(world);
            require(baseline < 15 && baseline > 0, "Control drop did not take expected server fall damage: " + baseline);
            heal(world);
            context.runOnClient(c -> noFall.setEnabled(true));
            record(context);
            drop(context, world);
            context.waitTicks(100);
            float protectedHealth = health(world);
            require(protectedHealth > 19, "No Fall did not prevent actual vanilla server damage: " + protectedHealth + ", control=" + baseline);
            require(!moves.isEmpty() && moves.stream().allMatch(Move::full), "No Fall sent duplicate/original short movement variants: " + moves);
            require(moves.stream().noneMatch(Move::collision), "No Fall packet collision flag differs from the source method");
            require(moves.stream().anyMatch(m -> !m.ground) && moves.stream().anyMatch(Move::ground), "No Fall did not finish grounded-report recovery");
            require(context.computeOnClient(c -> !noFall.protecting()), "No Fall remained armed after landing");
            context.takeScreenshot("maro-no-fall-landed");
            System.out.println("[no-fall-proof] native 17-block survival drop; server health control=" + baseline + ", enabled=" + protectedHealth + "; full packets and grounded recovery verified");

            // A new toggle must not reuse the previous fall's armed/ground-report state.
            context.runOnClient(c -> {
                noFall.setEnabled(false);
                noFall.setEnabled(true);
                require(!noFall.protecting(), "No Fall retained armed state across disable/re-enable");
                noFall.setEnabled(false);
                air.getBind().set(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
                air.setEnabled(true);
                require(ConfigManager.save("air-fall-native-check"), "Could not save native freeze config");
                try {
                    var path = ConfigManager.CONFIG_DIR.resolve("air-fall-native-check.json");
                    var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    var saved = json.getAsJsonObject("modules").getAsJsonObject("Air Stuck");
                    require(!saved.get("enabled").getAsBoolean(), "Air Stuck saved its active freeze");
                    saved.addProperty("enabled", true);
                    Files.writeString(path, json.toString());
                    require(ConfigManager.load("air-fall-native-check"), "Could not load legacy enabled freeze config");
                    require(!air.isEnabled() && air.getBind().matches(org.lwjgl.glfw.GLFW.GLFW_KEY_F8), "Config reactivated freeze or lost bind");
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            });

            // Reset an actual armed fall, rather than checking an already-idle module.
            context.runOnClient(c -> noFall.setEnabled(true));
            world.getServer().runCommand("tp @a 0.5 100 0.5");
            await(context, () -> context.computeOnClient(c -> noFall.protecting()), "No Fall never armed during the lifecycle drop");
            context.runOnClient(c -> {
                noFall.setEnabled(false);
                require(!noFall.protecting(), "Disabling No Fall retained its armed fall");
                noFall.setEnabled(true);
                require(!noFall.protecting(), "Re-enabling No Fall reused the old fall");
            });
            await(context, () -> context.computeOnClient(c -> noFall.protecting()), "No Fall did not arm on the continuing fall");
            context.runOnClient(c -> air.setEnabled(true));
            // Native dimension transfer changes the player/world; neither armed state may survive.
            world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0.5 90 0.5");
            await(context, () -> context.computeOnClient(c -> c.world.getRegistryKey() == net.minecraft.world.World.NETHER), "Native Nether transfer did not arrive");
            context.waitTicks(3);
            require(!air.isEnabled() && context.computeOnClient(c -> !noFall.protecting()), "Movement state leaked across a world change");
            System.out.println("[air-fall-lifecycle-proof] config never restores Air Stuck activation, bind retained, toggle/world resets, Air Stuck takes precedence over No Fall");
        } finally {
            watched = null;
            context.runOnClient(c -> {
                air.setEnabled(false);
                noFall.setEnabled(false);
                air.getBind().set(oldBind);
                c.options.forwardKey.setPressed(false);
                c.options.jumpKey.setPressed(false);
                ConfigManager.delete("air-fall-native-check");
                ConfigManager.save(oldConfig);
            });
            world.getServer().runCommand("execute in minecraft:overworld run tp @a " + initial.x + " " + initial.y + " " + initial.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runCommand("gamemode " + mode.getId() + " @a");
            heal(world);
            context.waitFor(c -> c.world != null && c.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD
                    && c.interactionManager.getCurrentGameMode() == mode);
            context.waitTicks(4);
        }
    }
}
