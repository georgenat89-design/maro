package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.maro.builder.*;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import java.util.*;

/** Homes run through native command, menu and teleport packets; never fixture client teleports. */
final class BuilderHomeChecks {
    private record Home(Vec3d pos,float yaw,float pitch){}
    private static final Home[] saved=new Home[3];
    private static ServerPlayerEntity waiting;
    private static Home arrival;
    private static int arriveAt,saveCommands,travelCommands,deleteCommands;
    private static BlockPos storage;
    private static Vec3d warmupStart;
    private static boolean movedDuringWarmup,rejectTravel,rejectDelete,silentDelete,commandsAwayFromStorage;
    static {ServerTickEvents.END_SERVER_TICK.register(server->{
        if(waiting==null)return;
        if(waiting.getEntityPos().squaredDistanceTo(warmupStart)>.04)movedDuringWarmup=true;
        if(server.getTicks()<arriveAt)return;
        var player=waiting;waiting=null;
        player.teleport(server.getOverworld(),arrival.pos.x,arrival.pos.y,arrival.pos.z,Set.of(),arrival.yaw,arrival.pitch,true);
    });}
    static void run(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Automatic storage home replacement, warmup, native arrival, prompt repair and independent camera aim");
        storage=start.east(2);
        world.getServer().runOnServer(server->{
            Arrays.fill(saved,null);saveCommands=travelCommands=deleteCommands=0;waiting=null;movedDuringWarmup=rejectTravel=rejectDelete=silentDelete=commandsAwayFromStorage=false;
            server.getCommandManager().getDispatcher().register(CommandManager.literal("home")
                .executes(command->{open(command.getSource().getPlayer());return 1;})
                .then(CommandManager.argument("id",IntegerArgumentType.integer(1,3)).executes(command->{
                    var player=command.getSource().getPlayer();int id=IntegerArgumentType.getInteger(command,"id")-1;travelCommands++;
                    if(rejectTravel){player.sendMessage(Text.literal("Home teleport failed: cooldown"),false);return 1;}
                    if(saved[id]==null){player.sendMessage(Text.literal("Home not set"),false);return 1;}
                    waiting=player;arrival=saved[id];warmupStart=player.getEntityPos();arriveAt=server.getTicks()+30;
                    player.sendMessage(Text.literal("Teleporting to home "+(id+1)+"; stand still"),false);return 1;
                })));
            server.getCommandManager().getDispatcher().register(CommandManager.literal("sethome").executes(command->{
                var player=command.getSource().getPlayer();saveCommands++;
                for(int i=0;i<3;i++)if(saved[i]==null){if(i==0)commandsAwayFromStorage|=!player.isOnGround()||storage.getSquaredDistance(player.getBlockPos())>9;saved[i]=new Home(player.getEntityPos(),player.getYaw(),player.getPitch());player.sendMessage(Text.literal("Home "+(i+1)+" set successfully"),false);return 1;}
                player.sendMessage(Text.literal("Home slots full"),false);return 1;
            }));
            server.getCommandManager().getDispatcher().register(CommandManager.literal("delhome")
                .then(CommandManager.argument("id",IntegerArgumentType.integer(1,3)).executes(command->{
                    var player=command.getSource().getPlayer();int id=IntegerArgumentType.getInteger(command,"id")-1;deleteCommands++;
                    require(id==0,"Builder deleted an unrelated home");commandsAwayFromStorage|=!player.isOnGround()||storage.getSquaredDistance(player.getBlockPos())>9;
                    if(rejectDelete){player.sendMessage(Text.literal("Home 1 could not be deleted: permission denied"),false);return 1;}
                    if(silentDelete)return 1;
                    boolean absent=saved[id]==null;saved[id]=null;
                    player.sendMessage(Text.literal(absent?"Home 1 not set":"Home 1 deleted successfully"),false);return 1;
                })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);
        });
        var chest=storage;
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        var home2=start.east(12);var home3=home2.up(4);command(world,"setblock",home3.down(),"stone");context.waitTicks(10);
        context.runOnClient(client->{
            setting(builder,"Builder Homes",true);setting(builder,"Head Spoofing",true);
            builder.install(new Schematic("home-check.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.south(2));
            var homes=(BuilderHomes)field(builder,"homes");homes.reset();
            builder.startBuild();require(!builder.building()&&!homes.busy(),"Missing storage did not block mandatory home setup");
            client.crosshairTarget=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();
        });
        storageReplacement(context,world,builder,start,chest,home2,home3);
        world.getServer().runOnServer(server->Arrays.fill(saved,null));
        teleport(world,start);context.waitTicks(12);
        context.runOnClient(client->((BuilderHomes)field(builder,"homes")).reset());
        int before=saveCommands;
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,80);
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).ready()&&!builder.building(),"Storage setup failed or unexpectedly started building: "+builder.status()));require(saveCommands==before+1,"Absent home 1 did not save exactly once");
        teleport(world,home2);context.waitTicks(12);
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).capture(true,false),"Dry interior home was refused"));waitHome(context,builder,80);
        teleport(world,home3);context.waitTicks(12);
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).capture(true,true),"Dry upper home was refused"));waitHome(context,builder,80);require(saveCommands==before+3,"Homes were not allocated exactly once in order");
        var interior=saved[1];var upper=saved[2];int previousDeletes=deleteCommands;
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,120);
        require(travelCommands==1&&!movedDuringWarmup,"Storage teleport duplicated or walked during warmup");
        require(deleteCommands==previousDeletes+1&&saveCommands==before+4&&saved[1]==interior&&saved[2]==upper&&!commandsAwayFromStorage,"Verified storage return failed to replace only home 1 at arrival");
        context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(saved[0].pos)<.36,"Storage arrival was not confirmed"));
        context.waitTicks(42);
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).work(home2.east(),List.of(home2)),"Home 2 did not prove an onward dry route"));waitHome(context,builder,90);
        require(travelCommands==2&&!movedDuringWarmup,"Work teleport duplicated or walked during warmup");
        context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(saved[1].pos)<.36,"Interior arrival was not confirmed"));
        context.waitTicks(42);rejectTravel=true;
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).storage(chest),"Cooldown test did not issue home"));waitHome(context,builder,50);
        context.runOnClient(client->require(builder.status().contains("cooldown"),"Server cooldown did not stop home travel"));require(travelCommands==3,"Rejected home was retried");rejectTravel=false;
        teleport(world,start);context.waitTicks(12);
        promptRepair(context,world,builder,start);
        crouchedMining(context,world,builder,start);
        columnEdgeMining(context,world,builder,start);
        teleport(world,start);context.waitTicks(12);
        rotations(context,builder,start);
        context.runOnClient(client->{builder.setEnabled(false);setting(builder,"Builder Homes",false);setting(builder,"Head Spoofing",false);});
        System.out.println("[builder-home] PASS: home 1 replaced at storage; homes 2/3 kept; Start resumed; absent/rejected/missing receipts bounded; 3 confirmed homes; 2 native arrivals; cooldown bounded; repair before cleanup; crouch mining; smooth independent camera");
    }
    private static void storageReplacement(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,BlockPos chest,BlockPos home2,BlockPos home3){
        var old=new Home(Vec3d.ofBottomCenter(start.west(12)),0,0);var second=new Home(Vec3d.ofBottomCenter(home2),15,3);var third=new Home(Vec3d.ofBottomCenter(home3),30,6);
        world.getServer().runOnServer(server->{saved[0]=old;saved[1]=second;saved[2]=third;});teleport(world,start.west(12));context.waitTicks(12);
        context.runOnClient(client->builder.startBuild());context.waitTicks(8);
        require(deleteCommands==0&&saveCommands==0&&saved[0]==old,"Home 1 was deleted before reaching storage");
        context.runOnClient(client->{builder.pause("pause storage approach");require(!client.options.forwardKey.isPressed()&&!client.options.jumpKey.isPressed()&&!((BuilderHomes)field(builder,"homes")).busy(),"Paused storage approach kept movement");builder.startBuild();});
        waitHome(context,builder,240);context.waitTick();
        context.runOnClient(client->{require(((BuilderHomes)field(builder,"homes")).readyFor(chest)&&builder.building(),"Start did not resume after home replacement: "+builder.status());builder.pause("replacement test");});
        require(deleteCommands==1&&saveCommands==1&&!commandsAwayFromStorage,"Replacement duplicated commands or ran away from grounded storage");
        require(saved[0]!=old&&saved[1]==second&&saved[2]==third,"Replacing home 1 modified homes 2 or 3");
        context.runOnClient(client->{builder.startBuild();require(((BuilderHomes)field(builder,"homes")).capture(true,false),"Optional home check did not start");});waitHome(context,builder,80);
        context.runOnClient(client->{require(builder.building()&&client.currentScreen==null,"Occupied optional home stopped building or left a blocking menu");require(!((BuilderHomes)field(builder,"homes")).capture(true,false),"Occupied optional home was checked repeatedly");builder.pause("optional home kept");});
        require(saveCommands==1&&saved[1]==second&&saved[2]==third,"Optional home capture overwrote an existing slot");
        rejectDelete=true;
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,80);
        context.runOnClient(client->require(!((BuilderHomes)field(builder,"homes")).ready()&&builder.status().contains("permission"),"Rejected deletion did not pause setup"));
        require(saveCommands==1&&saved[1]==second&&saved[2]==third,"Rejected deletion issued sethome");rejectDelete=false;silentDelete=true;
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,340);
        context.runOnClient(client->require(!((BuilderHomes)field(builder,"homes")).ready()&&builder.status().contains("did not confirm"),"Missing deletion receipt was assumed successful"));
        require(saveCommands==1,"Unconfirmed deletion issued sethome");silentDelete=false;
        // An inaccessible marked chest must leave the old slot untouched.
        var sealed=start.east(7);command(world,"setblock",sealed,"chest");
        for(var direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP))command(world,"setblock",sealed.offset(direction),"stone");context.waitTicks(6);
        int deletes=deleteCommands;
        context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).setup(sealed),"Blocked-storage test did not begin"));waitHome(context,builder,120);
        require(deleteCommands==deletes&&saveCommands==1,"Unreachable storage deleted or saved home 1");
        for(var direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.UP))command(world,"setblock",sealed.offset(direction),"air");command(world,"setblock",sealed,"air");
        System.out.println("[builder-home] Storage approached before delete/save; occupied home 1 replaced; homes 2/3 retained; Start resumed; rejection/timeout/inaccessible storage bounded");
    }
    private static void promptRepair(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var gap=start.south(2);var post=start.east(13).up(3);
        command(world,"setblock",gap.east(2),"stone");command(world,"setblock",post,"dirt");world.getServer().runCommand("give @a stone 2");context.waitTicks(6);
        context.runOnClient(client->{
            builder.install(new Schematic("home-repair.nbt","test",3,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(gap);builder.preview();
        });context.waitTicks(15);
        context.runOnClient(client->{
            @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(gap,2);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(post);
            builder.pause("pause repair test");require(openings.containsKey(gap),"Pause forgot the access repair");
            require(builder.saveExtra().getAsJsonArray("access-openings").size()==1,"Saved build forgot the opening");
            var savedBuild=builder.saveExtra();openings.clear();call(builder,"restorePlacementFields",new Class<?>[]{com.google.gson.JsonObject.class},savedBuild);require(openings.containsKey(gap),"Loaded build forgot the access repair");
            BuilderPacketChecks.begin();builder.startBuild();
        });
        int repaired=-1;for(int tick=0;tick<60;tick++){if(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(gap).isOf(Blocks.STONE))){repaired=tick;break;}context.waitTick();}
        require(repaired>=0,"Repair waited for temporary-block cleanup");
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(post).isOf(Blocks.DIRT)),"Fixture did not repair before cleanup");
        context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null,"Repair caused damage or left menu");BuilderPacketChecks.verify(1);builder.pause("rotation test");});
        System.out.println("[builder-home] Access restored in "+repaired+" ticks with other temporary posts still present");
    }
    private static void rotations(ClientGameTestContext context,AutoBuilder builder,BlockPos start){
        context.runOnClient(client->{client.player.setYaw(178);client.player.setPitch(0);});
        float[] previous={178,0};boolean settled=false;
        for(int tick=0;tick<35;tick++){
            final int turn=tick;
            settled=context.computeOnClient(client->{
                var point=Vec3d.ofCenter(start.east(3));boolean ready=(boolean)call(builder,"aim",new Class<?>[]{Vec3d.class},point);
                float delta=Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-previous[0]));require(delta<=45.01,"Aim exceeded smooth turn limit");
                previous[0]=client.player.getYaw();previous[1]=client.player.getPitch();
                var camera=builder.builderCameraLook();require(camera!=null&&Math.abs(MathHelper.wrapDegrees(camera[0]-178))<.01,"Head aim changed independent camera");
                if(turn==0){float yaw=client.player.getYaw();client.player.changeLookDirection(20,0);require(client.player.getYaw()==yaw&&Math.abs(builder.builderCameraLook()[0]-181)<.01,"Mouse look changed server aim");client.player.changeLookDirection(-20,0);}
                return ready;
            });context.waitTick();if(settled)break;
        }
        require(settled,"Smooth rotation did not settle promptly");
        context.runOnClient(client->{require(Math.abs(MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw()-178))<.1,"Rendered camera followed spoofed head");builder.pause("head spoof complete");require(builder.builderCameraLook()==null,"Pause retained head lock");});
    }
    private static void crouchedMining(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.south(2);var beam=start.south().up();command(world,"setblock",target,"dirt");command(world,"setblock",beam,"stone");world.getServer().runCommand("give @a diamond_shovel");context.waitTicks(8);
        context.runOnClient(client->{
            builder.install(new Schematic("crouch-mining.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STRUCTURE_VOID.getDefaultState()}));builder.setOrigin(target);
            require(call(builder,"visibleHit",new Class<?>[]{BlockPos.class},target)==null,"Crouch fixture already had a standing view");
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(target);BuilderPacketChecks.begin();builder.startBuild();
        });
        boolean crouched=false,removed=false;
        for(int i=0;i<100;i++){crouched|=context.computeOnClient(client->client.player.isSneaking());removed=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isAir());if(removed)break;context.waitTick();}
        require(crouched&&removed,"Crouch did not expose and remove the obstructed owned block");context.waitTicks(4);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(beam).isOf(Blocks.STONE)&&server.getOverworld().getBlockState(start.down()).isOf(Blocks.STONE)),"Crouch mining removed the obstruction or footing");
        context.runOnClient(client->{require(client.player.getHealth()==20&&!client.options.sneakKey.isPressed()&&builder.temporarySupports().isEmpty(),"Crouch did not release cleanly");BuilderPacketChecks.verify(0);builder.pause("crouch finished");});command(world,"setblock",beam,"air");
        System.out.println("[builder-home] Native crouch exposed the hidden block and kept obstruction/footing intact");
    }
    private static void columnEdgeMining(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var ledge=start.up(3);var column=List.of(start.south(),start.south().up(),start.south().up(2));
        command(world,"setblock",ledge,"stone");for(var block:column)command(world,"setblock",block,"dirt");teleport(world,ledge.up());context.waitTicks(12);
        context.runOnClient(client->{
            builder.install(new Schematic("column-edge-peek.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STRUCTURE_VOID.getDefaultState()}));builder.setOrigin(column.getFirst());
            require(call(builder,"visibleHit",new Class<?>[]{BlockPos.class},column.getFirst())==null,"Column fixture did not hide the remaining lower block below the lip");
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(column);BuilderPacketChecks.begin();BuilderPacketChecks.expectCrouchedMining(Set.of(column.getFirst()));builder.startBuild();
        });
        boolean edged=false;int removed=0;
        for(int tick=0;tick<240;tick++){
            edged|=context.computeOnClient(client->client.player.isSneaking()&&client.player.getZ()>start.getZ()+.88);
            int count=world.getServer().computeOnServer(server->(int)column.stream().filter(p->server.getOverworld().getBlockState(p).isAir()).count());
            if(count>removed){context.runOnClient(client->require(client.player.isOnGround()&&client.player.getHealth()==20,"Column cleanup lost retained footing: "+client.player.getEntityPos()));removed=count;}
            if(removed==3)break;context.waitTick();
        }
        require(edged&&removed==3,"Sneak edging did not finish the vertical column: "+removed+"/3 "+context.computeOnClient(client->builder.status()));context.waitTicks(5);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(ledge).isOf(Blocks.STONE)),"Column cleanup removed its permanent ledge");
        context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&!client.options.sneakKey.isPressed(),"Column cleanup left supports, damage or held sneak");BuilderPacketChecks.verify(0);builder.pause("column peek complete");});
        teleport(world,start);context.waitTicks(12);command(world,"setblock",ledge,"air");System.out.println("[builder-home] Vertical column 3/3 removed; hidden lower block mined while crouched over the ledge; permanent footing/full health retained");
    }
    private static void waitHome(ClientGameTestContext context,AutoBuilder builder,int ticks){for(int i=0;i<ticks;i++){if(context.computeOnClient(client->!((BuilderHomes)field(builder,"homes")).busy()))return;context.waitTick();}throw new AssertionError("Home operation timed out: "+context.computeOnClient(client->builder.status()));}
    private static void open(ServerPlayerEntity player){
        var inventory=new SimpleInventory(27);for(int i=0;i<3;i++){var item=new ItemStack(saved[i]==null?Items.GRAY_DYE:Items.RED_BED);item.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Home "+(i+1)+(saved[i]==null?" — No home set":" — Saved")));inventory.setStack(11+i*2,item);}
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,id,inv,inventory,3),Text.literal("Homes")));
    }
    private static void teleport(TestSingleplayerContext world,BlockPos pos){world.getServer().runCommand("tp @a "+(pos.getX()+.5)+" "+pos.getY()+" "+(pos.getZ()+.5)+" 0 0");}
    private static void command(TestSingleplayerContext world,String command,BlockPos pos,String value){world.getServer().runCommand(command+" "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+value);}
    private static ButtonSetting button(AutoBuilder builder,String name){return (ButtonSetting)builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void setting(AutoBuilder builder,String name,Object value){builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(value instanceof Boolean b?new JsonPrimitive(b):new JsonPrimitive(value.toString()));}
    private static Object field(Object instance,String name){try{var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object call(Object instance,String name,Class<?>[] args,Object...values){try{var method=instance.getClass().getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(instance,values);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void require(boolean success,String message){if(!success)throw new AssertionError(message);}
}
