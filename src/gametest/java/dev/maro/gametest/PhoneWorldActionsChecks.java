package dev.maro.gametest;

import dev.maro.gametest.mixin.PhoneMouseProbe;
import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.input.MouseInput;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Native mouse callbacks, real raycasts and authoritative server results with the phone open. */
final class PhoneWorldActionsChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static SpotifyPhoneScreen phone(net.minecraft.client.MinecraftClient c){return (SpotifyPhoneScreen)c.currentScreen;}
    private static void mouse(ClientGameTestContext context,int button,boolean down){context.runOnClient(c->
        ((PhoneMouseProbe)c.mouse).maro$button(c.getWindow().getHandle(),new MouseInput(button,0),down?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE));}
    private static Set<UUID> rockets(TestSingleplayerContext world){return world.getServer().computeOnServer(s->{
        var ids=new HashSet<UUID>();for(var entity:s.getOverworld().iterateEntities())if(entity instanceof FireworkRocketEntity)ids.add(entity.getUuid());return ids;
    });}
    private static int mined(TestSingleplayerContext world){return world.getServer().computeOnServer(s->{
        int count=0;for(int y=160;y<=164;y++)if(s.getOverworld().getBlockState(new BlockPos(0,y,0)).isAir())count++;return count;
    });}
    private static void miningPad(ClientGameTestContext context,TestSingleplayerContext world){
        context.runOnClient(c->c.setScreen(null));
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("fill -1 159 -1 1 164 1 minecraft:stone");
        world.getServer().runCommand("tp @a 0.5 165 0.5 0 90");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        context.waitTicks(8);
        context.runOnClient(c->{c.player.getAbilities().flying=false;c.gameRenderer.updateCrosshairTarget(1);
            require(c.crosshairTarget instanceof BlockHitResult hit&&hit.getBlockPos().equals(new BlockPos(0,164,0)),"Downward native raycast missed the mining column");});
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var owner=ModuleManager.get(SpotifyPhone.class);var playback=new SpotifyPhoneChecks.IsolatedPlayback();
        var saved=new LinkedHashMap<Setting<?>,com.google.gson.JsonElement>();owner.getSettings().forEach(s->saved.put(s,s.toJson()));
        try {
            context.runOnClient(c->{c.setScreen(null);owner.getSettings().forEach(Setting::reset);
                require(owner.gameInteraction.get(),"Game Actions must work by default after upgrading the old opt-in setting");});
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -2 140 -2 2 140 2 minecraft:stone");
            world.getServer().runCommand("tp @a 0.5 141 0.5 0 90");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:firework_rocket 16");
            context.waitTicks(8);
            context.runOnClient(c->{c.player.getAbilities().flying=false;c.setScreen(new SpotifyPhoneScreen(owner,playback));phone(c).tick();
                require(phone(c).allowsGameInteraction()&&c.mouse.isCursorLocked(),"Default phone did not capture normal game controls");});
            context.waitTicks(2);var seen=new HashSet<>(rockets(world));int before=seen.size();
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_RIGHT,true);
            for(int i=0;i<9;i++){context.waitTick();seen.addAll(rockets(world));}
            require(seen.size()>=before+2,"Held native right-click failed to launch repeated server-confirmed fireworks");
            context.takeScreenshot("maro-phone-fireworks");
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_ALT);context.waitTicks(2);seen.addAll(rockets(world));int paused=seen.size();
            context.waitTicks(6);seen.addAll(rockets(world));require(seen.size()==paused,"Alt controls continued firing rockets");
            context.runOnClient(c->require(!c.options.useKey.isPressed()&&!phone(c).allowsGameInteraction(),"Alt retained held item use"));
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_RIGHT,false);
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_ALT);context.waitTicks(2);

            // Airborne offhand use must reach vanilla's Elytra rocket path, not only block placement.
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:elytra");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:firework_rocket 16");
            world.getServer().runCommand("tp @a 0.5 220 0.5 0 -10");context.waitTicks(6);
            world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.setOnGround(false);p.startGliding();});
            context.runOnClient(c->{c.player.getAbilities().flying=false;c.player.setOnGround(false);c.player.startGliding();});context.waitTicks(2);
            require(context.computeOnClient(c->c.player.isGliding()),"Elytra fixture never entered real gliding");
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_RIGHT,true);context.waitTicks(2);mouse(context,GLFW.GLFW_MOUSE_BUTTON_RIGHT,false);
            require(world.getServer().computeOnServer(s->s.getPlayerManager().getPlayerList().getFirst().getOffHandStack().getCount()<16),"Phone blocked offhand Elytra rocket use");
            context.takeScreenshot("maro-phone-elytra-rocket");

            miningPad(context,world);
            context.runOnClient(c->{c.setScreen(new SpotifyPhoneScreen(owner,playback));phone(c).tick();});context.waitTicks(2);
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,true);
            for(int i=0;i<100&&mined(world)<3;i++)context.waitTick();
            require(mined(world)>=3,"Held attack did not mine multiple successive blocks directly below the player");
            require(context.computeOnClient(c->c.currentScreen instanceof SpotifyPhoneScreen&&c.player.getY()<164.5),"Phone closed or player did not descend into the shaft");
            context.takeScreenshot("maro-phone-mining-down");mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,false);context.waitTicks(2);int stopped=mined(world);
            context.waitTicks(12);require(mined(world)==stopped,"Mining continued after mouse release");

            // Open while already digging: no second mouse press should be needed.
            miningPad(context,world);mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,true);context.waitTicks(2);
            context.runOnClient(c->{require(c.options.attackKey.isPressed(),"Native pre-open mining input missing");c.setScreen(new SpotifyPhoneScreen(owner,playback));});
            for(int i=0;i<100&&mined(world)<2;i++)context.waitTick();
            require(mined(world)>=2,"Opening the phone dropped the existing held mining input");
            context.runOnClient(c->{c.onWindowFocusChanged(false);phone(c).tick();require(!c.options.attackKey.isPressed(),"Focus loss retained mining");c.onWindowFocusChanged(true);});
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,false);context.waitTicks(2);stopped=mined(world);context.waitTicks(10);
            require(mined(world)==stopped,"Focus regain restarted mining without a fresh press");

            context.runOnClient(c->owner.gameInteraction.set(false));context.waitTicks(2);int disabled=mined(world);
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,true);context.waitTicks(12);mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,false);
            require(mined(world)==disabled,"Disabled Game Actions still mined blocks");
            context.runOnClient(c->{owner.gameInteraction.set(true);phone(c).tick();});
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,true);context.waitTick();context.runOnClient(c->phone(c).close());context.waitTicks(7);
            require(context.computeOnClient(c->c.currentScreen==null&&!c.options.attackKey.isPressed()&&!c.options.useKey.isPressed()),"Closing the phone left a game action held");
            mouse(context,GLFW.GLFW_MOUSE_BUTTON_LEFT,false);
            System.out.println("[phone-world-actions] PASS: default-on game input, native held fireworks, authoritative offhand Elytra rocket consumption, real downward raycast and three-block survival shaft, held mining across phone opening, release/Alt/focus/disable/close cleanup");
        } finally {context.runOnClient(c->{c.setScreen(null);c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);saved.forEach(Setting::fromJson);});}
    }
}
