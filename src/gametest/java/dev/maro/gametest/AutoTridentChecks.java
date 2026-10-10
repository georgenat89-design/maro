package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoTrident;
import dev.maro.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Verifies actual server-accepted throws with held mouse input, not just client prediction. */
public final class AutoTridentChecks {
    private static net.minecraft.network.ClientConnection watched;
    private static final java.util.List<net.minecraft.network.packet.Packet<?>> sent=new java.util.ArrayList<>();
    public static void sent(net.minecraft.network.ClientConnection connection,net.minecraft.network.packet.Packet<?> packet){if(connection==watched)sent.add(packet);}
    private static void record(ClientGameTestContext context){context.runOnClient(c->{sent.clear();watched=c.getNetworkHandler().getConnection();});}
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int throwsAccepted(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList().getFirst()
            .getStatHandler().getStat(Stats.USED, Items.TRIDENT));
    }

    private static void equip(ClientGameTestContext context, TestSingleplayerContext world, boolean offhand) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.getInventory().setStack(player.getInventory().getSelectedSlot(),
                new ItemStack(offhand ? Items.STONE : Items.TRIDENT));
            player.setStackInHand(Hand.OFF_HAND, offhand ? new ItemStack(Items.TRIDENT) : ItemStack.EMPTY);
        });
        context.waitTicks(5);
        require(context.computeOnClient(client -> (offhand ? client.player.getOffHandStack()
            : client.player.getMainHandStack()).isOf(Items.TRIDENT)), "Trident fixture did not synchronize");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(AutoTrident.class);
        require(module != null, "Auto Trident was not registered");
        var speed = (NumberSetting) module.getSettings().stream()
            .filter(s -> s.getName().equals("Speed")).findFirst().orElseThrow();
        var original = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            return new ItemStack[]{player.getMainHandStack().copy(), player.getOffHandStack().copy()};
        });
        var gameMode = world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList()
            .getFirst().interactionManager.getGameMode());
        float pitch = context.computeOnClient(client -> client.player.getPitch());
        try {
            world.getServer().runCommand("gamemode creative @a");
            context.runOnClient(client -> { client.player.setPitch(-70); speed.set(10.0); module.setEnabled(true); });
            equip(context, world, false);
            int before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            require(throwsAccepted(world) - before >= 3, "Fast main-hand mode did not repeat server-accepted throws");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            int stopped = throwsAccepted(world);
            context.waitTicks(30);
            require(throwsAccepted(world) == stopped, "Trident kept throwing after mouse release");

            context.runOnClient(client -> speed.set(1.0));
            equip(context, world, false);
            before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            require(throwsAccepted(world) - before == 1, "Slow speed did not hold the charge longer");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);

            context.runOnClient(client -> speed.set(10.0));
            equip(context, world, true);
            before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            int offhandThrows = throwsAccepted(world) - before;
            // The production harness runs client/server tick clocks independently.
            require(offhandThrows >= 2, "Offhand trident did not repeat server-accepted throws: " + offhandThrows);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);

            riptide(context,world,module);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(player.getInventory().getSelectedSlot(), new ItemStack(Items.BOW));
                player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            });
            context.waitTicks(5);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(35);
            require(context.computeOnClient(client -> client.player.isUsingItem()
                && client.player.getActiveItem().isOf(Items.BOW) && client.player.getItemUseTime() >= 20),
                "Auto Trident interrupted a bow charge");
        } finally {
            watched=null;
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(client -> { module.setEnabled(false); speed.reset(); client.player.setPitch(pitch); });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(player.getInventory().getSelectedSlot(), original[0]);
                player.setStackInHand(Hand.OFF_HAND, original[1]);
                player.changeGameMode(gameMode);
            });
            context.waitTicks(5);
        }
    }
    private static void riptide(ClientGameTestContext context,TestSingleplayerContext world,AutoTrident module){
        var position=context.computeOnClient(c->c.player.getEntityPos());
        try{
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("tp @a 200.5 90 200.5 0 -90");
            world.getServer().runCommand("item replace entity @a weapon.offhand with air");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:trident[minecraft:enchantments={\"minecraft:riptide\":3}]");
            context.waitTicks(8);
            require(context.computeOnClient(c->!c.player.isTouchingWaterOrRain()),"Standalone dry Riptide fixture was wet");
            record(context);context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);context.waitTicks(20);
            require(sent.stream().noneMatch(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket),"Auto Trident without Trident Util sent rejected dry-use packets");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            world.getServer().runCommand("fill 196 80 196 204 83 204 minecraft:water");
            world.getServer().runCommand("weather rain");
            world.getServer().runCommand("tp @a 200.5 82 200.5 0 -90");context.waitTicks(8);
            require(context.computeOnClient(c->c.player.isTouchingWaterOrRain()),"Wet Riptide fixture did not synchronize");
            int before=throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);context.waitTicks(70);
            require(throwsAccepted(world)-before>=2,"Server Timing did not repeat actual server-accepted Riptide launches");
            // The real server sends a position correction. It must be accepted, then
            // held input must wait instead of immediately launching another burst.
            int correctionBefore=context.computeOnClient(c->(int)field(module,"lastCorrectionAge"));
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~");
            for(int i=0;i<20&&context.computeOnClient(c->(int)field(module,"lastCorrectionAge")==correctionBefore);i++)context.waitTick();
            require(context.computeOnClient(c->(int)field(module,"lastCorrectionAge")>correctionBefore),"Native correction did not reach standalone Auto Trident");
            record(context);context.waitTicks(15);
            require(sent.stream().noneMatch(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket||p instanceof net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket a&&a.getAction()==net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.RELEASE_USE_ITEM),"Auto Trident repeated a burst during correction backoff");
            before=throwsAccepted(world);context.waitTicks(50);
            require(throwsAccepted(world)>before,"Auto Trident did not resume after correction backoff");
            System.out.println("[auto-trident-server-timing-proof] standalone dry-use packets=0; repeated native wet Riptide accepted; real position correction backoff and resume passed");
        }finally{
            watched=null;context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("fill 196 80 196 204 83 204 minecraft:air");
            world.getServer().runCommand("tp @a "+position.x+" "+position.y+" "+position.z);
            context.waitTicks(5);
        }
    }
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
