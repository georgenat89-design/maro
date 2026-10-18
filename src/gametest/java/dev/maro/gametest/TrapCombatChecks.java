package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.anubis.tpa.AutoTpaHereModule;
import dev.maro.config.FriendManager;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.Box;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Client player targets plus real server arrows/collisions. No external server required. */
final class TrapCombatChecks {
    private record Release(int charge, int age) { }
    private static final List<Release> releases = new CopyOnWriteArrayList<>();
    private static boolean watching;
    static void sent(Packet<?> packet) {
        if (watching && packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            var player = net.minecraft.client.MinecraftClient.getInstance().player;
            if (player != null && player.getActiveItem().isOf(Items.BOW))
                releases.add(new Release(player.getItemUseTime(), player.age));
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Setting<?> setting(Module m, String name) { return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private static void number(Module m, String name, double value) { ((NumberSetting) setting(m, name)).set(value); }
    private static void bool(Module m, String name, boolean value) { ((BooleanSetting) setting(m, name)).set(value); }
    private static void mode(Module m, String value) { ((ModeSetting) setting(m, "Attack Mode")).set(value); }
    private static void await(ClientGameTestContext c, java.util.function.BooleanSupplier ok, int ticks, String message) {
        for (int i = 0; i < ticks; i++) { if (c.computeOnClient(client -> ok.getAsBoolean())) return; c.waitTick(); }
        throw new AssertionError(message);
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world, AutoTpaHereModule bot) {
        int[] targetId = {0};
        AtomicBoolean damaged = new AtomicBoolean();
        AtomicInteger sellCommands = new AtomicInteger();
        int commandsBefore = FeaturePortsChecks.commandCount();
        world.getServer().runOnServer(s -> s.getCommandManager().getDispatcher().register(
            net.minecraft.server.command.CommandManager.literal("sell").executes(source -> { sellCommands.incrementAndGet(); return 1; })));
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("tp @a 8 90 8 0 0");
        world.getServer().runCommand("summon minecraft:husk 8.5 90 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,Tags:[\"maro_bow_test\"]}");
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerManager().getPlayerList().getFirst();
            p.getInventory().clear();
            p.getInventory().setSelectedSlot(0);
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            p.getInventory().setStack(1, new ItemStack(Items.BOW));
            p.getInventory().setStack(9, new ItemStack(Items.ARROW, 64));
            p.getInventory().setStack(10, new ItemStack(Items.COBBLESTONE, 64));
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
            p.currentScreenHandler.sendContentUpdates();
        });
        context.waitTicks(10);
        try {
            context.runOnClient(c -> {
                bot.setEnabled(false);
                mode(bot, "Bow Spam");
                bool(bot, "Attack", true);
                bool(bot, "Auto Sell", true);
                number(bot, "Eat Below (hearts, 0 = never)", 0);
                number(bot, "Pause Radius", 1);
                number(bot, "Bow Range", 16);
                number(bot, "Bow Charge (ticks)", 5);
                number(bot, "Shot Delay (ticks)", 3);
                var target = new OtherClientPlayerEntity(c.world, new GameProfile(UUID.randomUUID(), "BowTarget"));
                target.setPosition(8.5, 90, 20.5);
                target.lastX = target.lastRenderX = 8.5; target.lastY = target.lastRenderY = 90; target.lastZ = target.lastRenderZ = 20.5;
                c.world.addEntity(target); targetId[0] = target.getId();
                releases.clear(); watching = true;
                bot.setEnabled(true);
            });
            await(context, () -> !releases.isEmpty(), 120, "Bow Spam never released an arrow outside Pause Radius");
            for (int i = 0; i < 100 && !damaged.get(); i++) {
                world.getServer().runOnServer(s -> damaged.set(s.getOverworld().getEntitiesByClass(HuskEntity.class,
                    new Box(7, 89, 19, 10, 94, 22), e -> e.getCommandTags().contains("maro_bow_test"))
                    .stream().anyMatch(e -> e.getHealth() < e.getMaxHealth())));
                context.waitTick();
            }
            require(damaged.get(), "Native server arrow did not hit the stationary target at twelve blocks");
            require(releases.getFirst().charge() > 5, "Adaptive charge did not increase a short draw for range");
            require(FeaturePortsChecks.commandCount() == commandsBefore && sellCommands.get() == 0,
                "Requests or Auto Sell ran during bow combat outside Pause Radius");
            context.runOnClient(c -> bool(bot, "Auto Sell", false));
            world.getServer().runCommand("kill @e[tag=maro_bow_test]");
            context.runOnClient(c -> {
                bot.setEnabled(false);
                var target = c.world.getEntityById(targetId[0]);
                target.setPosition(8.5, 90, 11);
                bool(bot, "Adaptive Bow Charge", false);
                releases.clear(); bot.setEnabled(true);
            });
            await(context, () -> releases.size() >= 3, 100, "Short-range Bow Spam did not repeat");
            require(releases.stream().allMatch(r -> r.charge() >= 5), "Bow released before its configured native charge");
            for (int i = 1; i < releases.size(); i++) require(releases.get(i).age() - releases.get(i - 1).age() >= 8, "Bow ignored shot delay");

            // Leaving range stops fire; returning starts it without any use-button input.
            context.runOnClient(c -> c.world.getEntityById(targetId[0]).setPosition(8.5, 90, 40.5));
            int outOfRange = releases.size();
            context.waitTicks(15);
            require(releases.size() == outOfRange && context.computeOnClient(c -> !c.player.isUsingItem()
                && !c.options.useKey.isPressed()), "Bow kept firing after its player left range");
            context.runOnClient(c -> c.world.getEntityById(targetId[0]).setPosition(8.5, 90, 11));
            await(context, () -> releases.size() > outOfRange, 60, "Detecting a player did not automatically restart Bow Spam");
            await(context, () -> {
                var p = net.minecraft.client.MinecraftClient.getInstance().player;
                return p.isUsingItem() && p.getItemUseTime() >= 3;
            }, 40, "No active draw for target death");
            int atDeath = releases.size();
            context.runOnClient(c -> {
                var target = (OtherClientPlayerEntity) c.world.getEntityById(targetId[0]);
                target.setHealth(0);
                target.deathTime = 1;
            });
            context.waitTicks(12);
            require(releases.size() == atDeath, "Bow released an arrow after target death");
            require(context.computeOnClient(c -> !c.player.isUsingItem() && !c.options.useKey.isPressed()),
                "Target death left Bow Spam holding use");
            world.getServer().runOnServer(s -> require(!s.getPlayerManager().getPlayerList().getFirst().isUsingItem(),
                "Server retained the bow draw after target death"));
            context.runOnClient(c -> {
                var target = (OtherClientPlayerEntity) c.world.getEntityById(targetId[0]);
                target.setHealth(20);
                target.deathTime = 0;
            });
            await(context, () -> releases.size() > atDeath, 60, "Bow did not automatically start for a live player again");

            // Cancel a charged draw without firing when a target becomes a friend.
            await(context, () -> {
                var p = net.minecraft.client.MinecraftClient.getInstance().player;
                return p.isUsingItem() && p.getActiveItem().isOf(Items.BOW) && p.getItemUseTime() >= 3;
            }, 40, "No active bow draw to cancel");
            int before = releases.size();
            context.runOnClient(c -> FriendManager.add("BowTarget"));
            context.waitTicks(8);
            require(releases.size() == before, "Friend cancellation released an unwanted arrow");
            require(context.computeOnClient(c -> !c.player.isUsingItem() && !c.options.useKey.isPressed()), "Friend cancellation left use held");
            world.getServer().runOnServer(s -> require(!s.getPlayerManager().getPlayerList().getFirst().isUsingItem(), "Server retained a cancelled draw"));

            // A wall suppresses both drawing and firing; removing it resumes ordinary use.
            world.getServer().runCommand("fill 7 90 9 9 95 9 minecraft:stone");
            context.waitTicks(5);
            context.runOnClient(c -> FriendManager.remove("BowTarget"));
            context.waitTicks(20);
            require(releases.size() == before, "Bow fired through a solid wall");
            require(context.computeOnClient(c -> !c.player.isUsingItem()), "Bow drew at an occluded target");
            world.getServer().runCommand("fill 7 90 9 9 95 9 minecraft:air");
            await(context, () -> releases.size() > before, 60, "Bow did not resume after removing obstruction");

            // Changing mode mid-draw releases key ownership and preserves real sword selection.
            await(context, () -> net.minecraft.client.MinecraftClient.getInstance().player.isUsingItem(), 40, "No draw for mode switch");
            context.runOnClient(c -> mode(bot, "Sword"));
            context.waitTicks(5);
            require(context.computeOnClient(c -> !c.player.isUsingItem() && !c.options.useKey.isPressed()), "Mode change kept drawing");
            context.runOnClient(c -> { number(bot, "Attack Radius", 3); });
            context.waitTicks(15);
            require(context.computeOnClient(c -> c.player.getMainHandStack().isOf(Items.DIAMOND_SWORD)), "Sword mode did not select sword");

            // Missing ammo and a fixed slot containing another item must pause combat.
            context.runOnClient(c -> { bot.setEnabled(false); mode(bot, "Bow Spam"); releases.clear(); });
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerManager().getPlayerList().getFirst();
                p.setNoGravity(true); p.getInventory().setStack(9, ItemStack.EMPTY); p.currentScreenHandler.sendContentUpdates();
            });
            context.waitTicks(5);
            context.runOnClient(c -> { c.player.setNoGravity(true); bot.setEnabled(true); });
            context.waitTicks(15);
            require(releases.isEmpty() && context.computeOnClient(c -> !c.player.isUsingItem()), "Bow fired without ammunition");
            world.getServer().runCommand("gamemode creative @a");
            context.waitTicks(5);
            context.runOnClient(c -> number(bot, "Bow Slot (0 = auto)", 1));
            context.waitTicks(15);
            require(releases.isEmpty(), "Bow ignored its fixed slot and used a sword");
            context.runOnClient(c -> number(bot, "Bow Slot (0 = auto)", 2));
            await(context, () -> net.minecraft.client.MinecraftClient.getInstance().player.isUsingItem(), 60, "Pinned bow slot did not start drawing");
            context.runOnClient(c -> bot.setEnabled(false));
            int disabled = releases.size();
            context.waitTicks(8);
            require(releases.size() == disabled && context.computeOnClient(c -> !c.player.isUsingItem() && !c.options.useKey.isPressed()), "Disabling leaked a shot or held use");
            world.getServer().runOnServer(s -> require(!s.getPlayerManager().getPlayerList().getFirst().isUsingItem(), "Disable left server drawing"));
            options(context,bot,targetId[0]);
            System.out.println("TPA BOW passed: native server hit, adaptive charge, player detection/range/death stop, repeated timing, friend/wall/ammo/slot guards, sword switch and cancellation");
        } finally {
            context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(c -> { bot.setEnabled(false); watching = false; FriendManager.remove("BowTarget");
                if (targetId[0] != 0) c.world.removeEntity(targetId[0], Entity.RemovalReason.DISCARDED);
                c.player.setNoGravity(false); c.options.useKey.setPressed(false); });
            world.getServer().runCommand("kill @e[tag=maro_bow_test]");
            world.getServer().runCommand("fill 7 90 9 9 95 9 minecraft:air");
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().setNoGravity(false));
        }
    }

    private static void armor(net.minecraft.client.MinecraftClient c,int targetId,int points) {
        var target=(OtherClientPlayerEntity)c.world.getEntityById(targetId);
        target.equipStack(net.minecraft.entity.EquipmentSlot.CHEST,points==0?ItemStack.EMPTY:new ItemStack(Items.DIAMOND_CHESTPLATE));
        target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ARMOR).setBaseValue(points);
    }
    private static void options(ClientGameTestContext context,AutoTpaHereModule bot,int targetId) {
        context.runOnClient(c->{
            mode(bot,"Bow Spam");bool(bot,"Auto Shoot",false);bool(bot,"Auto Lock",true);
            number(bot,"Attack Radius",3);bool(bot,"Adaptive Bow Charge",true);
            c.player.setYaw(90);c.player.setPitch(0);releases.clear();bot.setEnabled(true);
        });
        context.waitTicks(30);
        require(releases.isEmpty() && context.computeOnClient(c->!c.player.isUsingItem()),"Auto Shoot off still started a draw");
        require(context.computeOnClient(c->Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(c.player.getYaw()))<3),"Auto Lock did not track without automatic shooting");
        context.runOnClient(c->{c.player.getInventory().setSelectedSlot(1);
            ((dev.maro.mixin.ClientPlayerInteractionManagerAccessor)c.interactionManager).maro$syncSelectedSlot();});
        context.getInput().holdMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(10);
        require(releases.isEmpty() && context.computeOnClient(c->c.player.isUsingItem() && c.player.getActiveItem().isOf(Items.BOW)),
            "Auto Shoot off interrupted or released a manual bow draw");
        context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context,()->releases.size()==1,12,"Manual bow release was lost with Auto Shoot off");
        context.waitTicks(12);
        require(releases.size()==1 && context.computeOnClient(c->!c.player.isUsingItem()),"Auto Shoot off restarted after a manual release");
        context.runOnClient(c->releases.clear());
        context.runOnClient(c->{bool(bot,"Auto Lock",false);bool(bot,"Auto Shoot",true);c.player.setYaw(90);c.player.setPitch(0);});
        context.waitTicks(15);
        require(releases.isEmpty() && context.computeOnClient(c->Math.abs(c.player.getYaw()-90)<.1),"Auto Lock off moved aim or shot away from target");
        context.runOnClient(c->bool(bot,"Auto Lock",true));
        await(context,()->!releases.isEmpty(),60,"Enabling Auto Shoot/Auto Lock did not fire");

        context.runOnClient(c->{mode(bot,"Both");((ModeSetting)setting(bot,"Bow Rule")).set("Below Cutoff");
            bool(bot,"Ranged Bow Fallback",true);armor(c,targetId,0);releases.clear();});
        await(context,()->!releases.isEmpty(),60,"Both mode did not select bow for an unarmored player");
        long melee=FeaturePortsChecks.meleeCount();
        context.runOnClient(c->armor(c,targetId,8));
        await(context,()->FeaturePortsChecks.meleeCount()>melee,60,"Both mode did not select sword and hit an armored player");
        require(context.computeOnClient(c->c.player.getMainHandStack().isOf(Items.DIAMOND_SWORD)),"Armor rule selected wrong sword slot");
        context.runOnClient(c->{((ModeSetting)setting(bot,"Bow Rule")).set("At/Above Cutoff");releases.clear();});
        await(context,()->!releases.isEmpty(),60,"Reversed armor rule did not select bow for armor");
        long reversedMelee=FeaturePortsChecks.meleeCount();
        context.runOnClient(c->armor(c,targetId,0));
        await(context,()->FeaturePortsChecks.meleeCount()>reversedMelee,60,"Reversed armor rule did not select sword without armor");

        context.runOnClient(c->{c.world.getEntityById(targetId).setPosition(8.5,90,20.5);releases.clear();});
        await(context,()->!releases.isEmpty(),100,"Ranged fallback did not use bow beyond sword reach");
        context.runOnClient(c->bool(bot,"Ranged Bow Fallback",false));
        int fallbackStopped=releases.size();context.waitTicks(15);
        require(releases.size()==fallbackStopped,"Disabled ranged fallback kept shooting at a sword-only target");

        context.runOnClient(c->{
            c.world.getEntityById(targetId).setPosition(8.5,90,11);
            mode(bot,"Bow Spam");bool(bot,"Conservative Timing",true);
            number(bot,"Bow Charge (ticks)",5);number(bot,"Shot Delay (ticks)",1);releases.clear();
        });
        await(context,()->releases.size()>=2,130,"Conservative timing did not fire two native arrows");
        require(releases.stream().allMatch(r->r.charge()>=20),"Conservative timing released before full native draw");
        require(releases.get(1).age()-releases.get(0).age()>=25,"Conservative timing omitted the pause between draws");
        context.runOnClient(c->bot.setEnabled(false));
        System.out.println("TPA OPTIONS passed: Auto Shoot/Auto Lock toggles, both armor rules, native sword hits, ranged fallback, optional full-draw timing");
    }
}
