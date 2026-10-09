package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.MaroRelog;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Native command packets, server chat receipts and delayed native teleport packets. */
final class MaroRelogChecks {
    private record Attempt(String command, int tick, long nanos) { }
    private static final Vec3d START = new Vec3d(220.5, -10, 220.5);
    private static final Vec3d AWAY = new Vec3d(252.5, 10, 220.5);
    private static final Vec3d HOME_ONE = new Vec3d(500, 20, 500), HOME_TWO = new Vec3d(550, 20, 550);
    private static final Vec3d[] homes = new Vec3d[4];
    private static final List<Attempt> attempts = new CopyOnWriteArrayList<>();
    private static final Set<String> rejectOnce = new HashSet<>();
    private static ServerPlayerEntity waiting;
    private static Vec3d destination, warmupFrom;
    private static int arriveAt;
    private static boolean rejectDelete, wrongSaveReceipt, silentRtp, movedDuringWarmup;

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (waiting == null || waiting.getEntityWorld().getServer() != server) return;
            if (waiting.getEntityPos().squaredDistanceTo(warmupFrom) > .04) movedDuringWarmup = true;
            if (server.getTicks() < arriveAt) return;
            var player = waiting;
            waiting = null;
            player.teleport(server.getOverworld(), destination.x, destination.y, destination.z, Set.of(), 0, 0, true);
        });
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(MaroRelog.class);
        require(module != null && module.getName().equals("Maro Relog"), "Maro Relog registration/name missing");
        var original = context.computeOnClient(c -> c.player.getEntityPos());
        var mode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        install(world);
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("fill 215 -11 215 225 -11 225 stone");
        world.getServer().runCommand("fill 215 -10 215 225 -7 225 air");
        world.getServer().runCommand("fill 247 9 215 257 9 225 stone");
        world.getServer().runCommand("fill 247 10 215 257 13 225 air");
        try {
            teleport(world, AWAY);
            context.waitTicks(12);
            context.runOnClient(c -> {
                module.getSettings().forEach(s -> s.reset());
                setting(module, "Redo Delay", 1);
                setting(module, "Response Timeout", 5);
                module.setEnabled(true);
            });
            context.waitTicks(8);
            require(attempts.isEmpty(), "Triggered above the underground Y threshold");
            world.getServer().runOnServer(s -> rejectOnce.addAll(List.of("sethome 3", "home 3")));
            teleport(world, START);
            waitFor(context, () -> attempts.stream().anyMatch(a -> a.command.equals("rtp")), 180, "Never sent RTP", module);
            require(context.computeOnClient(c -> module.rounds()) == 0, "Finished from warmup chat before teleport");
            context.takeScreenshot("maro-relog-rtp-warmup");
            waitFor(context, () -> context.computeOnClient(c -> module.rounds()) >= 2, 280, "Repeated underground round failed", module);
            context.runOnClient(c -> module.setEnabled(false));
            world.getServer().runOnServer(s -> {
                require(homes[1] == HOME_ONE && homes[2] == HOME_TWO && homes[3] != null, "Relog changed builder homes 1 or 2");
                require(!movedDuringWarmup, "Movement cancelled a teleport warmup");
                require(attempts.stream().map(Attempt::command).toList().equals(List.of(
                        "delhome 3", "sethome 3", "sethome 3", "rtp", "home 3", "home 3",
                        "delhome 3", "sethome 3", "rtp", "home 3")), "Unexpected retry/order: " + attempts);
                for (int i = 1; i < attempts.size(); i++) {
                    require(attempts.get(i).tick - attempts.get(i - 1).tick >= 20, "Command tick gap too short");
                    require(attempts.get(i).nanos - attempts.get(i - 1).nanos >= 1_000_000_000L, "Command wall-clock gap too short");
                }
                System.out.println("[maro-relog-roundtrip-proof] two native underground roundtrips; cooldown retried sethome and home; warmup chat ignored; homes 1/2 preserved; attempts=" + attempts);
            });

            reset(context, world, module);
            world.getServer().runOnServer(s -> rejectDelete = true);
            context.runOnClient(c -> module.setEnabled(true));
            waitFor(context, () -> context.computeOnClient(c -> !module.isEnabled()), 45, "Delete rejection did not stop", module);
            require(attempts.size() == 1, "Continued after deletion was rejected");

            reset(context, world, module);
            world.getServer().runOnServer(s -> wrongSaveReceipt = true);
            context.runOnClient(c -> module.setEnabled(true));
            waitFor(context, () -> context.computeOnClient(c -> !module.isEnabled()), 80, "Wrong home receipt did not stop", module);
            require(attempts.size() == 2, "RTP sent after the server saved a different home slot");

            reset(context, world, module);
            world.getServer().runOnServer(s -> silentRtp = true);
            context.runOnClient(c -> module.setEnabled(true));
            waitFor(context, () -> context.computeOnClient(c -> !module.isEnabled()), 190, "Missing teleport did not time out", module);
            require(attempts.stream().noneMatch(a -> a.command.startsWith("home ")), "Warmup chat was mistaken for an actual RTP");

            reset(context, world, module);
            context.runOnClient(c -> module.setEnabled(true));
            waitFor(context, () -> attempts.stream().anyMatch(a -> a.command.equals("rtp")), 120, "Cancel fixture never reached RTP", module);
            context.runOnClient(c -> module.setEnabled(false));
            context.waitTicks(45);
            require(attempts.size() == 3, "Late teleport continued the disabled relog sequence");
            context.runOnClient(c -> module.setEnabled(true));
            context.waitTicks(8);
            require(attempts.size() == 3, "Re-enable continued a stale sequence");
            System.out.println("[maro-relog-failure-proof] rejected delete, wrong home slot and missing native RTP stop safely; disabled/re-enabled module ignores late teleport");
        } finally {
            context.runOnClient(c -> { module.setEnabled(false); module.getSettings().forEach(s -> s.reset()); });
            world.getServer().runOnServer(s -> waiting = null);
            teleport(world, original);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(mode));
            context.waitTicks(8);
        }
    }

    private static void install(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            resetServer();
            homes[1] = HOME_ONE;
            homes[2] = HOME_TWO;
            homes[3] = null;
            for (String name : List.of("delhome", "sethome", "home")) {
                server.getCommandManager().getDispatcher().register(CommandManager.literal(name)
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1, 3)).executes(command -> {
                            var player = command.getSource().getPlayer();
                            int id = IntegerArgumentType.getInteger(command, "id");
                            if (record(player, name + " " + id)) return 1;
                            require(id == 3, "Relog touched reserved home " + id);
                            switch (name) {
                                case "delhome" -> {
                                    if (rejectDelete) { player.sendMessage(Text.literal("Home 3 could not be deleted: permission denied"), false); return 1; }
                                    boolean absent = homes[id] == null;
                                    homes[id] = null;
                                    player.sendMessage(Text.literal(absent ? "Home does not exist" : "Home 3 deleted successfully"), false);
                                }
                                case "sethome" -> {
                                    homes[id] = player.getEntityPos();
                                    player.sendMessage(Text.literal("Home " + (wrongSaveReceipt ? 1 : id) + " set successfully"), false);
                                }
                                case "home" -> {
                                    require(waiting == null, "Home sent before RTP actually completed");
                                    require(homes[id] != null, "Returned to an unsaved home");
                                    schedule(player, homes[id]);
                                    player.sendMessage(Text.literal("Teleporting to home 3; stand still"), false);
                                }
                            }
                            return 1;
                        })));
            }
            server.getCommandManager().getDispatcher().register(CommandManager.literal("rtp").executes(command -> {
                var player = command.getSource().getPlayer();
                if (record(player, "rtp")) return 1;
                if (!silentRtp) schedule(player, AWAY);
                player.sendMessage(Text.literal("Teleporting to a random location; teleported shortly"), false);
                return 1;
            }));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);
        });
    }

    private static boolean record(ServerPlayerEntity player, String command) {
        attempts.add(new Attempt(command, player.getEntityWorld().getServer().getTicks(), System.nanoTime()));
        if (!rejectOnce.remove(command)) return false;
        player.sendMessage(Text.literal("You need to wait another 0.25 seconds to execute a command"), false);
        return true;
    }
    private static void schedule(ServerPlayerEntity player, Vec3d target) {
        waiting = player;
        destination = target;
        warmupFrom = player.getEntityPos();
        arriveAt = player.getEntityWorld().getServer().getTicks() + 30;
    }
    private static void reset(ClientGameTestContext context, TestSingleplayerContext world, MaroRelog module) {
        context.runOnClient(c -> module.setEnabled(false));
        world.getServer().runOnServer(s -> resetServer());
        teleport(world, START);
        context.waitTicks(10);
    }
    private static void resetServer() {
        attempts.clear();
        rejectOnce.clear();
        waiting = null;
        rejectDelete = wrongSaveReceipt = silentRtp = movedDuringWarmup = false;
    }
    private static void setting(MaroRelog module, String name, int value) {
        module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));
    }
    private static void teleport(TestSingleplayerContext world, Vec3d position) {
        world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z);
    }
    private static void waitFor(ClientGameTestContext context, java.util.function.BooleanSupplier done, int ticks, String reason, MaroRelog module) {
        for (int i = 0; i < ticks && !done.getAsBoolean(); i++) context.waitTicks(1);
        require(done.getAsBoolean(), reason + ": " + context.computeOnClient(c -> module.status()));
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
