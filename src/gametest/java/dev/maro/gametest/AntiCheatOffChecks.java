package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.*;
import dev.maro.module.impl.player.*;
import dev.maro.setting.*;
import dev.maro.mixin.MiningAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Exercises the production mixins in a real client, including UI entry, mining, vehicle movement and use packets. */
public final class AntiCheatOffChecks {
    private static volatile ClientConnection watched;
    private static final List<Packet<?>> sent = new CopyOnWriteArrayList<>();
    public static void sent(ClientConnection c, Packet<?> packet) { if (c == watched) sent.add(packet); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Setting<?> setting(Module m, String name) { return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private static void record(ClientGameTestContext ctx) { ctx.runOnClient(c -> { sent.clear(); watched = c.getNetworkHandler().getConnection(); }); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var fly = ModuleManager.get(BoatFly.class);
        var clip = ModuleManager.get(BoatNoClip.class);
        var flight = ModuleManager.get(Flight.class);
        var mine = ModuleManager.get(SpeedMine.class);
        var delay = ModuleManager.get(BreakDelay.class);
        var util = ModuleManager.get(TridentUtil.class);
        var modules = List.of(fly, clip, flight, mine, delay);
        for (var m : modules) require(m.getCategory() == Category.ANTI_CHEAT_OFF && !m.persistEnabled(), "AC module category/persistence: " + m.getName());
        var initial = ctx.computeOnClient(c -> c.player.getEntityPos());
        var initialMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        var initialAngles = ctx.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        var initialMenu = ClientSettings.menuStyle.get();
        try {
            ctx.runOnClient(c -> { ClientSettings.menuStyle.set("Panels"); c.setScreen(new ClickGuiScreen()); });
            ctx.waitTicks(12);
            require(ctx.computeOnClient(c -> ((ClickGuiScreen)c.currentScreen).panelPlace(fly) == null), "Locked panel exposed boat controls");
            ctx.runOnClient(c -> ((ClickGuiScreen)c.currentScreen).openPage(Category.ANTI_CHEAT_OFF.ordinal()));
            ctx.waitTicks(12); ctx.takeScreenshot("maro-ac-off-entry");
            ctx.runOnClient(c -> { var gui = (ClickGuiScreen)c.currentScreen; var at = gui.antiCheatCancelPlace(); gui.clickAt(at[0], at[1], 0); });
            ctx.waitTicks(12);
            require(ctx.computeOnClient(c -> ((ClickGuiScreen)c.currentScreen).showingPanels() && !((ClickGuiScreen)c.currentScreen).antiCheatSectionUnlocked()), "Cancel entered AC section");
            ctx.runOnClient(c -> ((ClickGuiScreen)c.currentScreen).openPage(Category.ANTI_CHEAT_OFF.ordinal()));
            ctx.waitTicks(12);
            ctx.runOnClient(c -> { var gui = (ClickGuiScreen)c.currentScreen; var at = gui.antiCheatEnterPlace(); gui.clickAt(at[0], at[1], 0); });
            ctx.waitTicks(12);
            require(ctx.computeOnClient(c -> ((ClickGuiScreen)c.currentScreen).antiCheatSectionUnlocked()), "Enter Section did not unlock");
            ctx.takeScreenshot("maro-ac-off-modules");
            ctx.runOnClient(c -> c.setScreen(new ClickGuiScreen()));
            require(ctx.computeOnClient(c -> !((ClickGuiScreen)c.currentScreen).antiCheatSectionUnlocked()), "New menu skipped the entry prompt");
            ctx.setScreen(() -> null);

            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("tp @a 60.5 80 60.5 0 0");
            ctx.waitTicks(5);
            ctx.runOnClient(c -> { ((ModeSetting)setting(flight,"Anti Kick")).set("None"); flight.setEnabled(true); });
            double height = ctx.computeOnClient(c -> c.player.getY());
            ctx.waitTicks(12);
            require(ctx.computeOnClient(c -> c.player.getAbilities().flying && c.player.getAbilities().allowFlying && Math.abs(c.player.getY() - height) < 0.15),
                "Abilities flight did not hover: " + ctx.computeOnClient(c -> "flying=" + c.player.getAbilities().flying + " allow=" + c.player.getAbilities().allowFlying + " delta=" + (c.player.getY()-height)));
            ctx.runOnClient(c -> flight.setEnabled(false));
            require(ctx.computeOnClient(c -> !c.player.getAbilities().flying && !c.player.getAbilities().allowFlying && Math.abs(c.player.getAbilities().getFlySpeed()-0.05f)<0.001), "Flight did not restore survival abilities");
            ctx.runOnClient(c -> { ((ModeSetting)setting(flight,"Mode")).set("Velocity"); flight.setEnabled(true); });
            ctx.waitTicks(3);
            require(ctx.computeOnClient(c -> Math.abs(c.player.getVelocity().y) < 0.001), "Velocity flight did not hover");
            ctx.runOnClient(c -> flight.setEnabled(false));

            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("summon minecraft:oak_boat 60.5 80 60.5 {NoGravity:1b}");
            world.getServer().runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
            ctx.waitTicks(10);
            require(ctx.computeOnClient(c -> c.player.getVehicle() instanceof net.minecraft.entity.vehicle.AbstractBoatEntity), "Boat fixture did not mount");
            ctx.runOnClient(c -> {
                fly.setEnabled(true);
                var boat = c.player.getVehicle(); double y = boat.getY();
                c.options.jumpKey.setPressed(true); boat.move(MovementType.SELF, Vec3d.ZERO); c.options.jumpKey.setPressed(false);
                require(boat.getY() > y + 0.25, "Boat Fly movement hook did not ascend");
                clip.setEnabled(true);
                require(!fly.isEnabled(), "Boat modes fought over the vehicle");
                boat.move(MovementType.SELF, Vec3d.ZERO);
                require(boat.noClip && !boat.collidesWith(c.player), "Boat No Clip collision hook failed");
                clip.setEnabled(false); require(!boat.noClip, "No Clip remained after disable");
            });
            // Actual stone collision must be bypassed by the production Entity.move hook.
            world.getServer().runCommand("fill 59 79 61 62 83 61 minecraft:stone");
            ctx.waitTicks(5);
            ctx.runOnClient(c -> {
                clip.setEnabled(true); var boat = c.player.getVehicle();
                c.player.setYaw(0);
                c.player.input.playerInput = new net.minecraft.util.PlayerInput(true, false, false, false, false, false, false);
                double z = boat.getZ();
                for (int i=0;i<12;i++) boat.move(MovementType.SELF, Vec3d.ZERO);
                require(boat.getZ() > z + 2.5 && boat.noClip, "Boat did not pass through the stone wall locally");
                clip.setEnabled(false); require(!boat.noClip, "Boat flags leaked after crossing");
            });
            world.getServer().runCommand("ride @p dismount");
            world.getServer().runCommand("kill @e[type=minecraft:oak_boat]");
            world.getServer().runCommand("fill 59 79 61 62 83 61 minecraft:air");
            world.getServer().runCommand("fill 66 64 66 74 64 74 minecraft:stone");
            world.getServer().runCommand("tp @a 70.5 65 70.5 0 29");
            ctx.waitTicks(5);

            // Creative's real five-tick inter-block cooldown passes through Break Delay.
            var target = new BlockPos(70,65,72);
            world.getServer().runCommand("setblock 70 65 72 minecraft:stone"); ctx.waitTicks(4);
            ctx.runOnClient(c -> {
                delay.setEnabled(true); ((NumberSetting)setting(delay,"Cooldown")).set(2.0);
                c.interactionManager.attackBlock(target, Direction.NORTH);
                require(((MiningAccessor)c.interactionManager).maro$cooldown()==2, "Creative cooldown did not use Break Delay");
                delay.setEnabled(false);
            });
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("setblock 70 65 72 minecraft:stone"); ctx.waitTicks(5);
            ctx.runOnClient(c -> {
                var state = c.world.getBlockState(target);
                c.crosshairTarget = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(target), Direction.NORTH, target, false);
                float normal = c.player.getBlockBreakingSpeed(state);
                ((ModeSetting)setting(mine,"Mode")).set("Normal"); mine.setEnabled(true);
                require(c.player.getBlockBreakingSpeed(state) > normal * 1.3, "Normal mode did not increase native mining speed");
                ((TextSetting)setting(mine,"Blocks")).set("stone");
                require(Math.abs(c.player.getBlockBreakingSpeed(state)-normal) < 0.001, "Blacklist ignored stone");
                ((TextSetting)setting(mine,"Blocks")).reset();
                ((ModeSetting)setting(mine,"Mode")).set("Haste");
            });
            ctx.waitTicks(3);
            require(ctx.computeOnClient(c -> c.player.hasStatusEffect(StatusEffects.HASTE)), "Haste mode did not inject Haste");
            ctx.runOnClient(c -> mine.setEnabled(false));
            require(ctx.computeOnClient(c -> !c.player.hasStatusEffect(StatusEffects.HASTE)), "Haste leaked after disable");
            ctx.getInput().holdMouse(0);
            ctx.runOnClient(c -> {
                ((ModeSetting)setting(mine,"Mode")).set("Damage"); mine.setEnabled(true);
                c.interactionManager.attackBlock(target,Direction.NORTH);
                ((MiningAccessor)c.interactionManager).maro$progress(0.72f);
            });
            record(ctx); ctx.waitTicks(5);
            require(sent.stream().anyMatch(p -> p instanceof PlayerActionC2SPacket a && a.getAction()==PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK),
                "Damage mode did not complete the native break: " + ctx.computeOnClient(c -> "progress=" + ((MiningAccessor)c.interactionManager).maro$progress() + " target=" + c.crosshairTarget + " attack=" + c.options.attackKey.isPressed()) + " packets=" + sent);
            ctx.getInput().releaseMouse(0);
            ctx.runOnClient(c -> mine.setEnabled(false));

            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a minecraft:trident[minecraft:enchantments={\"minecraft:riptide\":3}]");
            ctx.waitTicks(5);
            ctx.runOnClient(c -> { util.setEnabled(true); ModuleManager.get(AutoTrident.class).setEnabled(true); });
            ctx.getInput().holdMouse(1);
            record(ctx); ctx.waitTicks(20);
            require(sent.stream().noneMatch(p -> p instanceof PlayerInteractItemC2SPacket), "Dry Auto Trident spammed rejected use packets");
            ctx.getInput().releaseMouse(1);
            ctx.runOnClient(c -> ModuleManager.get(AutoTrident.class).setEnabled(false));
            require(ctx.computeOnClient(c -> TridentUtil.minChargeTicks(10,c.player)==10), "Server Timing shortened native charge");
            ctx.runOnClient(c -> {
                ((BooleanSetting)setting(util,"Server Timing")).set(false);
                require(TridentUtil.minChargeTicks(10,c.player)==1 && TridentUtil.allowOutOfWater(c.player), "Anubis utility options not applied");
                TridentUtil.attempted(); TridentUtil.serverCorrection();
                require(!TridentUtil.ready(), "Server correction did not pause trident retries");
            });
            ctx.waitTicks(21);
            require(ctx.computeOnClient(c -> TridentUtil.ready()), "Trident backoff did not recover");
            System.out.println("AC OFF: prompt/cancel/reopen, both boat hooks and wall traversal, Flight restoration, mining modes/cooldown, dry-use packet guard and correction recovery passed");
        } finally {
            watched = null;
            ctx.getInput().releaseMouse(0);
            ctx.getInput().releaseMouse(1);
            ctx.runOnClient(c -> {
                for (var m : modules) { m.setEnabled(false); m.getSettings().forEach(Setting::reset); }
                util.setEnabled(false); util.getSettings().forEach(Setting::reset);
                ModuleManager.get(AutoTrident.class).setEnabled(false);
                c.options.useKey.setPressed(false); c.options.jumpKey.setPressed(false); c.setScreen(null);
                ClientSettings.menuStyle.set(initialMenu);
            });
            world.getServer().runCommand("gamemode " + initialMode.getId() + " @a");
            world.getServer().runCommand("tp @a " + initial.x + " " + initial.y + " " + initial.z + " " + initialAngles[0] + " " + initialAngles[1]);
            ctx.waitFor(c -> c.interactionManager.getCurrentGameMode() == initialMode);
            ctx.waitTicks(5);
        }
    }
}
