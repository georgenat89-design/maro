package dev.maro.gametest;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maro.anubis.inspect.WeaponInspectModule;
import dev.maro.anubis.tpa.AutoTpaHereModule;
import dev.maro.anubis.tpa.TpMacroModule;
import dev.maro.config.FriendManager;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BedrockVoidESP;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Production client, real command suggestions/server execution, native item rendering and chunk updates. */
public final class FeaturePortsChecks {
    private static volatile ClientConnection watched;
    private static final List<Packet<?>> outgoing=new CopyOnWriteArrayList<>();
    private static final List<String> executed=new CopyOnWriteArrayList<>();
    static int commandCount(){return executed.size();}
    static long meleeCount(){return outgoing.stream().filter(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket).count();}
    public static void sent(ClientConnection connection,Packet<?> packet){if(connection==watched){outgoing.add(packet);TrapCombatChecks.sent(packet);}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(Module m,String name){return m.getSettings().stream().filter(s->s.getName().equalsIgnoreCase(name)).findFirst().orElseThrow(()->new AssertionError(name));}
    private static void bool(Module m,String name,boolean value){((BooleanSetting)setting(m,name)).set(value);}
    private static void number(Module m,String name,double value){((NumberSetting)setting(m,name)).set(value);}
    private static void text(Module m,String name,String value){((TextSetting)setting(m,name)).set(value);}
    private static void mode(Module m,String name,String value){((ModeSetting)setting(m,name)).set(value);}
    private static void await(ClientGameTestContext context,java.util.function.BooleanSupplier ok,int ticks,String message){
        for(int i=0;i<ticks;i+=3){if(context.computeOnClient(c->ok.getAsBoolean()))return;context.waitTicks(3);}throw new AssertionError(message);
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        require(ModuleManager.getByName("Trident Util")==null,"Removed Trident Util is still registered");
        commands(context,world);
        inspect(context,world);
        StaffNotifierChecks.run(context);
        pockets(context,world);
        System.out.println("FEATURE PORTS passed: server commands, completion ownership, friend/blacklist filters, inspect skins/input, staff packets/HUD, seam pockets/live updates");
    }
    private static void commands(ClientGameTestContext context,TestSingleplayerContext world){
        var bot=ModuleManager.get(AutoTpaHereModule.class);var macro=ModuleManager.get(TpMacroModule.class);
        require(bot!=null&&macro!=null,"Teleport modules were not registered");
        var friends=FriendManager.list();executed.clear();
        world.getServer().runOnServer(server->{
            for(String cmd:List.of("tpa","tpahere"))server.getCommandManager().getDispatcher().register(CommandManager.literal(cmd)
                .then(CommandManager.argument("player",StringArgumentType.word()).suggests((source,builder)->{
                    for(String name:List.of("Alice","Ally","ABlocked"))if(name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))builder.suggest(name);
                    return builder.buildFuture();
                }).executes(source->{executed.add(cmd+" "+StringArgumentType.getString(source,"player"));return 1;})));
        });
        try{
            context.runOnClient(c->{
                bot.getSettings().forEach(Setting::reset);macro.getSettings().forEach(Setting::reset);
                text(macro,"Player","bad name");macro.setEnabled(true);require(!macro.isEnabled(),"Invalid TP target was accepted");
                text(macro,"Player","MacroTarget");number(macro,"Interval (s)",.25);macro.setEnabled(true);
                watched=c.getNetworkHandler().getConnection();outgoing.clear();
            });
            await(context,()->executed.stream().filter(s->s.equals("tpa MacroTarget")).count()>=2,90,"TP Macro did not execute native commands");
            context.runOnClient(c->c.getNetworkHandler().onGameMessage(new GameMessageS2CPacket(Text.literal("You need to wait another 1.5 seconds to execute a command"),false)));
            context.waitTicks(2);int held=executed.size();context.waitTicks(12);
            require(executed.size()==held,"TP Macro ignored a server cooldown");
            await(context,()->executed.size()>held,90,"TP Macro did not resume after cooldown");
            context.runOnClient(c->{
                macro.setEnabled(false);executed.clear();outgoing.clear();FriendManager.add("Ally");
                text(bot,"Letters","a");number(bot,"Delay (s)",.25);bool(bot,"Repeat Forever",false);bool(bot,"Skip Offline Players",false);
                bool(bot,"Attack",false);bool(bot,"Auto Sell",false);number(bot,"Eat Below (hearts, 0 = never)",0);
                ((SettingAdapters.ValueSetting)setting(bot,"Never Request")).apply("ABlocked");bot.setEnabled(true);
            });
            await(context,()->executed.contains("tpahere Alice"),180,"Kill Bot did not collect suggestions and execute its request");
            context.waitTicks(20);
            require(executed.equals(List.of("tpahere Alice")),"Friend/blacklist filtering or one-pass queue failed: "+executed);
            require(outgoing.stream().anyMatch(p->p instanceof RequestCommandCompletionsC2SPacket),"No real completion requests were sent");
            combat(context,world,bot);
            TrapCombatChecks.run(context,world,bot);
        }finally{context.runOnClient(c->{bot.setEnabled(false);macro.setEnabled(false);bot.getSettings().forEach(Setting::reset);macro.getSettings().forEach(Setting::reset);FriendManager.clear();friends.forEach(FriendManager::add);watched=null;});}
    }
    private static void combat(ClientGameTestContext context,TestSingleplayerContext world,AutoTpaHereModule bot){
        var id=new int[]{0};
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("tp @a 8 90 8 180 0");
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getInventory().setStack(p.getInventory().getSelectedSlot(),new ItemStack(Items.DIAMOND_SWORD));p.getAbilities().flying=true;p.sendAbilitiesUpdate();});
        context.waitTicks(8);
        try{
            context.runOnClient(c->{
                bot.setEnabled(false);bool(bot,"Repeat Forever",true);bool(bot,"Attack",true);number(bot,"Attack Radius",2);
                var p=c.player.getEntityPos();var other=new net.minecraft.client.network.OtherClientPlayerEntity(c.world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Ally"));
                other.setPosition(p.x,p.y,p.z+1.5);other.lastX=other.lastRenderX=other.getX();other.lastY=other.lastRenderY=other.getY();other.lastZ=other.lastRenderZ=other.getZ();c.world.addEntity(other);id[0]=other.getId();
                outgoing.clear();c.player.getAbilities().flying=true;bot.setEnabled(true);
            });
            context.waitTicks(30);
            require(outgoing.stream().noneMatch(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket),"Kill Bot attacked a friend");
            context.runOnClient(c->{FriendManager.remove("Ally");outgoing.clear();});
            await(context,()->outgoing.stream().anyMatch(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket),90,"Kill Bot did not publish its aim and attack an arrival");
            context.waitTicks(20);
            require(outgoing.stream().filter(p->p instanceof net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket).count()<=5,"Kill Bot ignored attack cooldown");
            require(context.computeOnClient(c->Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(c.player.getYaw()))<5),"Kill Bot did not face its target");
            System.out.println("KILL BOT native combat packets, published aim and friend exclusion passed (local client player fixture)");
        }finally{context.runOnClient(c->{bot.setEnabled(false);if(id[0]!=0)c.world.removeEntity(id[0],net.minecraft.entity.Entity.RemovalReason.DISCARDED);});}
    }
    private static void inspect(ClientGameTestContext context,TestSingleplayerContext world){
        var module=ModuleManager.get(WeaponInspectModule.class);require(module!=null,"Weapon Inspect was not registered");
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("tp @a 8 90 8 0 8");
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getInventory().setStack(p.getInventory().getSelectedSlot(),new ItemStack(Items.DIAMOND_SWORD));p.getAbilities().flying=true;p.sendAbilitiesUpdate();});
        context.waitTicks(8);
        try{
            context.runOnClient(c->{module.getSettings().forEach(Setting::reset);c.options.setPerspective(Perspective.FIRST_PERSON);c.player.getAbilities().flying=true;module.setEnabled(true);});
            context.waitTicks(25);
            for(String skin:List.of("Butterfly","Karambit","Katana","None")){
                context.runOnClient(c->mode(module,"Knife Skin",skin));
                context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(2);
                require(context.computeOnClient(c->module.inspecting()),"Native F key did not start inspect for "+skin);
                require(context.computeOnClient(c->c.player.getMainHandStack().isOf(Items.DIAMOND_SWORD)),"Inspect triggered the normal offhand swap");
                context.waitTicks(5);System.out.println("INSPECT SCREENSHOT "+context.takeScreenshot("maro-weapon-inspect-"+skin.toLowerCase(Locale.ROOT)).toAbsolutePath());
                await(context,()->!module.inspecting(),160,"Released inspect did not finish for "+skin);
            }
            context.runOnClient(c->mode(module,"Style","Twirl"));context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(6);context.takeScreenshot("maro-weapon-inspect-twirl");
            context.getInput().holdKey(GLFW.GLFW_KEY_F);context.waitTicks(100);
            require(context.computeOnClient(c->module.inspecting()),"Holding inspect did not loop beyond the normal clip");
            context.getInput().releaseKey(GLFW.GLFW_KEY_F);
            await(context,()->!module.inspecting(),160,"Looping inspect did not finish after releasing F");
            require(context.computeOnClient(c->module.isEnabled()),"Weapon rendering fault disabled the module");
        }finally{context.runOnClient(c->{module.setEnabled(false);module.getSettings().forEach(Setting::reset);require(WeaponInspectModule.active==null,"Inspect renderer remained active after disabling");});}
    }
    private static void pockets(ClientGameTestContext context,TestSingleplayerContext world){
        var module=ModuleManager.get(BedrockVoidESP.class);require(module!=null,"Bedrock Void ESP was not registered");
        var left=new BlockPos(15,-62,2);var right=new BlockPos(16,-62,2);
        world.getServer().runCommand("fill 13 -64 0 18 -60 4 minecraft:bedrock");
        world.getServer().runCommand("fill 15 -62 2 16 -62 2 minecraft:air");
        world.getServer().runCommand("tp @a 15 -56 -5 0 35");
        context.waitTicks(10);
        try{
            context.runOnClient(c->{module.getSettings().forEach(Setting::reset);number(module,"Scan Range",16);c.player.getAbilities().flying=true;module.setEnabled(true);});
            await(context,()->module.pockets().stream().anyMatch(p->p.cells().contains(left)&&p.cells().contains(right)),240,"Enclosed seam pocket was not found");
            context.waitTicks(80);
            require(context.computeOnClient(c->module.pockets().stream().filter(p->p.cells().contains(left)).count()==1),"Chunk seam created duplicate pockets");
            require(context.computeOnClient(c->module.pockets().stream().filter(p->p.cells().contains(left)).findFirst().orElseThrow().cells().size()==2),"Pocket size was incorrect");
            require(context.computeOnClient(c->module.lastScanOperations()<=1500),"Per-tick scanning exceeded the operation cap");
            System.out.println("VOID SCREENSHOT "+context.takeScreenshot("maro-bedrock-void-esp-seam").toAbsolutePath());
            world.getServer().runCommand("fill 15 -62 2 15 -59 2 minecraft:air");
            await(context,()->module.pockets().stream().noneMatch(p->p.cells().contains(left)),120,"Opening a pocket did not invalidate it");
            world.getServer().runCommand("fill 15 -61 2 15 -60 2 minecraft:bedrock");
            await(context,()->module.pockets().stream().anyMatch(p->p.cells().contains(left)),240,"Closing a pocket did not trigger rescan");
            context.runOnClient(c->number(module,"Minimum Size",3));
            await(context,()->module.pockets().stream().noneMatch(p->p.cells().contains(left)),90,"Minimum-size change did not clear old pockets");
            context.runOnClient(c->{number(module,"Minimum Size",2);mode(module,"Nether Layers","Roof");});
            world.getServer().runCommand("execute in minecraft:the_nether run tp @a 15 132 -5 0 35");
            await(context,()->net.minecraft.world.World.NETHER.equals(net.minecraft.client.MinecraftClient.getInstance().world.getRegistryKey()),120,"Nether fixture did not join");
            context.waitTicks(30);
            world.getServer().runCommand("execute in minecraft:the_nether run fill 13 123 0 18 127 4 minecraft:bedrock");
            world.getServer().runCommand("execute in minecraft:the_nether run fill 15 125 2 16 125 2 minecraft:air");
            var roof=new BlockPos(15,125,2);
            await(context,()->module.pockets().stream().anyMatch(p->p.cells().contains(roof)),240,"Nether roof pocket was not found");
            require(context.computeOnClient(c->module.pockets().stream().noneMatch(p->p.cells().contains(left))),"Overworld pockets survived a dimension change");
            System.out.println("VOID NETHER SCREENSHOT "+context.takeScreenshot("maro-bedrock-void-esp-nether-roof").toAbsolutePath());
            context.runOnClient(c->mode(module,"Nether Layers","Floor"));
            context.waitTicks(5);require(context.computeOnClient(c->module.pockets().stream().noneMatch(p->p.key().getY()>=123)),"Floor-only mode retained roof pockets");
            world.getServer().runCommand("execute in minecraft:the_end run tp @a 15 90 -5 0 35");
            await(context,()->net.minecraft.world.World.END.equals(net.minecraft.client.MinecraftClient.getInstance().world.getRegistryKey())&&module.pockets().isEmpty()&&module.pendingScans()==0,120,"Unsupported dimension retained pockets or queued scans");
            world.getServer().runCommand("execute in minecraft:overworld run tp @a 15 -56 -5 0 35");
            await(context,()->module.pockets().stream().anyMatch(p->p.cells().contains(left)),240,"Returning to Overworld did not restart scanning");
        }finally{context.runOnClient(c->{module.setEnabled(false);module.getSettings().forEach(Setting::reset);require(module.pockets().isEmpty()&&module.pendingScans()==0,"Disabled scanner retained state");});}
    }
}
