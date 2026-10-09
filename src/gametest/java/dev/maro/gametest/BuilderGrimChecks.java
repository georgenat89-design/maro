package dev.maro.gametest;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.maro.builder.Schematic;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in native client against the isolated Paper/Grim fixture. No in-process server. */
final class BuilderGrimChecks {
    private static final Path ROOT=Path.of(System.getProperty("maro.gametest.grimFixture",""));
    static void run(ClientGameTestContext context){
        require(Files.isRegularFile(ROOT.resolve("connection.json")),"Set -PbuilderGrimFixture to the running local Paper/Grim fixture directory");
        context.runOnClient(client->{
            ModuleManager.all().forEach(module->module.setEnabled(false));
            client.options.getInactivityFpsLimit().setValue(net.minecraft.client.option.InactivityFpsLimit.MINIMIZED);
            client.options.pauseOnLostFocus=false;client.options.getEnableVsync().setValue(false);client.options.getMaxFps().setValue(120);
            var address="127.0.0.1:"+connection().get("gamePort").getAsInt();
            ConnectScreen.connect(client.currentScreen,client,ServerAddress.parse(address),new ServerInfo("Maro Grim fixture",address,ServerInfo.ServerType.OTHER),false,null);
        });
        context.waitFor(client->client.player!=null&&client.world!=null&&client.getNetworkHandler()!=null,600);
        context.waitTicks(60);context.setScreen(()->null);
        AutoBuilder builder=ModuleManager.get(AutoBuilder.class);
        try{
            for(String scenario:List.of("plain-mining","stone-mining","rejected-mining","delayed-mining","rejected-placement")){
                context.runOnClient(client->{builder.pause("Prepare Grim fixture");builder.setEnabled(false);});
                command("marofixture prepare "+scenario);context.waitTicks(40);
                boolean stone=scenario.equals("stone-mining");
                BlockPos target=new BlockPos(0,64,scenario.equals("rejected-placement")?4:2);
                context.runOnClient(client->{
                    require(client.player.isOnGround(),"Grim fixture player did not settle on native floor");
                    require(client.interactionManager.getCurrentGameMode()==net.minecraft.world.GameMode.SURVIVAL,"Grim test player is not in survival");
                    set(builder,"Save Build Progress",false);set(builder,"Builder Homes",false);set(builder,"Head Spoofing",true);
                    set(builder,"Build Mode","Automatic");set(builder,"Material Supply","Layer by Layer");set(builder,"Auto Move",true);set(builder,"Auto Unstuck",true);
                    set(builder,"Mine Out Schematic",false);set(builder,"Replace Wrong Blocks",true);set(builder,"Auto Eat",false);set(builder,"Prepare Whole Build",false);
                    set(builder,"Auto Buy Tools",false);set(builder,"Stop On Staff Nearby",false);set(builder,"Auto Buy When Missing",false);set(builder,"Support Dirt Reserve",0);
                    set(builder,"Temporary Supports",false);set(builder,"Rotation","0");set(builder,"Mirror","None");builder.clearContainers();
                    require(builder.inventoryCount(stone?Items.GLASS:Items.STONE)==1,"Fixture did not supply exactly one replacement block");
                    builder.install(new Schematic("grim-"+scenario+".nbt","local native Grim",1,1,1,BlockPos.ORIGIN,new BlockState[]{stone?Blocks.GLASS.getDefaultState():Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
                    BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(18,14);
                    if(scenario.equals("delayed-mining"))BuilderBlockDelay.begin(target);
                    builder.startBuild();
                });
                requireActive(proof());
                if(scenario.equals("delayed-mining")){
                    context.waitFor(client->field(builder,"pendingBreak")!=null,200);context.waitTicks(8);
                    context.runOnClient(client->{require(client.world.getBlockState(target).isOf(Blocks.DIRT),"Unconfirmed mining exposed predicted air under Grim");require(builder.inventoryCount(Items.STONE)==1,"Builder placed before native mining receipt under Grim");BuilderBlockDelay.release();});
                }
                for(int i=0;i<700&&context.computeOnClient(client->builder.building());i++)context.waitTick();
                context.waitTicks(40);
                context.runOnClient(client->{
                    require(client.player!=null&&client.world!=null,"Grim fixture disconnected");
                    require(!builder.building(),"Grim build stalled: "+builder.status());
                    require(client.world.getBlockState(target).isOf(stone?Blocks.GLASS:Blocks.STONE),"Client target differs after Grim test: "+scenario);
                    require(builder.inventoryCount(stone?Items.GLASS:Items.STONE)==0,"Client replacement count incorrect");
                    require(client.player.currentScreenHandler.getCursorStack().isEmpty(),"Ghost inventory recovery retained an item on the cursor");BuilderPacketChecks.verify(1);
                });
                command("marofixture status");Properties actual=proof();requireActive(actual);
                require(actual.getProperty("scenario").equals(scenario),"Wrong authoritative fixture scenario");
                require(actual.getProperty("target").equals(stone?"GLASS":"STONE"),"Actual Paper target not replaced: "+actual);
                require(actual.getProperty(stone?"glass":"stone").equals("0")&&actual.getProperty("cursor").equals("AIR"),"Actual server inventory/cursor mismatch: "+actual);
                require(actual.getProperty("flags").equals("0"),"Grim violations: "+actual.getProperty("flagDetails"));
                if(scenario.equals("rejected-mining"))require(actual.getProperty("rejectedBreaks").equals("3")&&actual.getProperty("breaks").equals("4"),"Three native mining corrections did not recover: "+actual);
                if(scenario.equals("rejected-placement"))require(actual.getProperty("rejectedPlaces").equals("3")&&actual.getProperty("places").equals("4"),"Three native placement corrections did not recover: "+actual);
                if(scenario.equals("rejected-placement"))require(Double.parseDouble(actual.getProperty("lastPlaceDistance"))<Double.parseDouble(actual.getProperty("firstPlaceDistance"))-.1,"Ghost recovery did not move closer on native safe ground: "+actual.getProperty("placePositions"));
                if(scenario.contains("mining"))require(Integer.parseInt(actual.getProperty("breaks"))>=1,"No native server mining occurred: "+scenario);
                try(var out=Files.newOutputStream(ROOT.resolve("proof-"+scenario+".properties"))){actual.store(out,"Native Paper1.21.11 Grim2.3.73 fixture result");}catch(IOException error){throw new UncheckedIOException(error);}
                System.out.println("[grim-proof] PASS scenario="+scenario+" target="+actual.getProperty("target")+" mining="+actual.getProperty("breakTicks")+" placement="+actual.getProperty("placeTicks")+" enabledChecks="+actual.getProperty("enabledChecks")+" flags=0 no exempt/op cursor=empty");
                context.takeScreenshot("maro-grim-"+scenario);
            }
        }finally{
            context.runOnClient(client->{BuilderPacketChecks.recording=false;BuilderBlockDelay.release();builder.pause("Grim tests finished");builder.setEnabled(false);});
        }
    }
    private static void requireActive(Properties actual){require(actual.getProperty("grimStarted").equals("true")&&actual.getProperty("grimUser").equals("true")&&Integer.parseInt(actual.getProperty("enabledChecks"))>20,"Grim is not actively checking the native player: "+actual);for(String permission:List.of("op","exempt","noModifyPacket","noSetback"))require(actual.getProperty(permission).equals("false"),"Fixture bypass permission: "+permission);}
    private static void set(AutoBuilder builder,String name,Object value){builder.getSettings().stream().filter(setting->setting.getName().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing builder setting "+name)).fromJson(value instanceof Number n?new JsonPrimitive(n):value instanceof Boolean b?new JsonPrimitive(b):new JsonPrimitive(value.toString()));}
    private static Object field(Object target,String name){try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static Properties proof(){try(var stream=Files.newInputStream(ROOT.resolve("server/plugins/MaroGrimFixture/proof.properties"))){var props=new Properties();props.load(stream);return props;}catch(IOException error){throw new UncheckedIOException(error);}}
    private static void require(boolean passed,String message){if(!passed)throw new AssertionError(message);}
    private static com.google.gson.JsonObject connection(){try{return JsonParser.parseString(Files.readString(ROOT.resolve("connection.json"))).getAsJsonObject();}catch(IOException error){throw new UncheckedIOException(error);}}
    private static String command(String command){
        try{
            var config=connection();
            try(var socket=new Socket()){
                socket.connect(new InetSocketAddress("127.0.0.1",config.get("rconPort").getAsInt()),5000);socket.setSoTimeout(5000);
                var input=new DataInputStream(socket.getInputStream());var output=socket.getOutputStream();
                send(output,1,3,config.get("password").getAsString());var auth=read(input);require(ByteBuffer.wrap(auth).order(ByteOrder.LITTLE_ENDIAN).getInt()>=0,"Local fixture RCON authorization failed");
                send(output,2,2,command);var response=read(input);return new String(response,8,response.length-10,StandardCharsets.UTF_8);
            }
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    private static void send(OutputStream output,int identity,int kind,String payload)throws IOException{byte[] text=payload.getBytes(StandardCharsets.UTF_8);var packet=ByteBuffer.allocate(text.length+14).order(ByteOrder.LITTLE_ENDIAN);packet.putInt(text.length+10).putInt(identity).putInt(kind).put(text).put((byte)0).put((byte)0);output.write(packet.array());output.flush();}
    private static byte[] read(DataInputStream input)throws IOException{int length=Integer.reverseBytes(input.readInt());require(length>=10&&length<1048576,"Invalid local RCON packet");return input.readNBytes(length);}
}
