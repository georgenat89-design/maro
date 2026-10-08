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
    private static final int[] saves=new int[3],travels=new int[3],deletes=new int[3];
    private static final List<String> commands=new ArrayList<>();
    private static Home reservedThird;
    private static boolean returnIssued;
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
    static void installCommands(TestSingleplayerContext world,BlockPos storageChest){
        storage=storageChest;
        world.getServer().runOnServer(server->{
            Arrays.fill(saved,null);Arrays.fill(saves,0);Arrays.fill(travels,0);Arrays.fill(deletes,0);commands.clear();saveCommands=travelCommands=deleteCommands=0;waiting=null;returnIssued=false;movedDuringWarmup=rejectTravel=rejectDelete=silentDelete=commandsAwayFromStorage=false;
            reservedThird=new Home(Vec3d.ofBottomCenter(storage.east(1000)),35,4);saved[2]=reservedThird;
            server.getCommandManager().getDispatcher().register(CommandManager.literal("home")
                .executes(command->{open(command.getSource().getPlayer());return 1;})
                .then(CommandManager.argument("id",IntegerArgumentType.integer(1,3)).executes(command->{
                    var player=command.getSource().getPlayer();int id=IntegerArgumentType.getInteger(command,"id")-1;require(id<2,"Builder travelled to home 3");travelCommands++;travels[id]++;commands.add("home "+(id+1));
                    if(rejectTravel){player.sendMessage(Text.literal("Home teleport failed: cooldown"),false);return 1;}
                    if(saved[id]==null){player.sendMessage(Text.literal("Home not set"),false);return 1;}
                    if(id==1)returnIssued=true;waiting=player;arrival=saved[id];warmupStart=player.getEntityPos();arriveAt=server.getTicks()+30;
                    player.sendMessage(Text.literal("Teleporting to home "+(id+1)+"; stand still"),false);return 1;
                })));
            server.getCommandManager().getDispatcher().register(CommandManager.literal("sethome").executes(command->{
                var player=command.getSource().getPlayer();saveCommands++;
                for(int i=0;i<3;i++)if(saved[i]==null){require(i<2,"Builder saved home 3");require(player.isOnGround(),"Home saved while airborne");if(i==0)commandsAwayFromStorage|=storage.getSquaredDistance(player.getBlockPos())>9;saves[i]++;commands.add("sethome "+(i+1));saved[i]=new Home(player.getEntityPos(),player.getYaw(),player.getPitch());player.sendMessage(Text.literal("Home "+(i+1)+" set successfully"),false);return 1;}
                player.sendMessage(Text.literal("Home slots full"),false);return 1;
            }));
            server.getCommandManager().getDispatcher().register(CommandManager.literal("delhome")
                .then(CommandManager.argument("id",IntegerArgumentType.integer(1,3)).executes(command->{
                    var player=command.getSource().getPlayer();int id=IntegerArgumentType.getInteger(command,"id")-1;deleteCommands++;
                    require(id<2,"Builder deleted home 3");deletes[id]++;commands.add("delhome "+(id+1));if(id==0)commandsAwayFromStorage|=!player.isOnGround()||storage.getSquaredDistance(player.getBlockPos())>9;
                    if(id==1&&returnIssued)require(waiting==null&&saved[1]!=null&&player.isOnGround()&&player.getEntityPos().squaredDistanceTo(saved[1].pos)<.36,"Home 2 deleted before actual native arrival");
                    if(rejectDelete){player.sendMessage(Text.literal("Home "+(id+1)+" could not be deleted: permission denied"),false);return 1;}
                    if(silentDelete)return 1;
                    boolean absent=saved[id]==null;saved[id]=null;if(id==1)returnIssued=false;
                    player.sendMessage(Text.literal("Home "+(id+1)+(absent?" not set":" deleted successfully")),false);return 1;
                })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);
        });
    }
    static void restoreStorage(TestSingleplayerContext world,com.google.gson.JsonArray p){world.getServer().runOnServer(server->saved[0]=new Home(new Vec3d(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble()),p.get(3).getAsFloat(),p.get(4).getAsFloat()));}
    static void verifyCommands(){verifyCommands(true);}
    static void verifyCommands(boolean fresh){require(!movedDuringWarmup&&!commandsAwayFromStorage,"Home commands moved during warmup or saved away from storage");int expected=fresh?1:0;require(deletes[0]==expected&&saves[0]==expected,"Build did not preserve the expected storage home setup");require(saves[1]>0&&saved[1]==null&&deletes[1]==2*saves[1]&&travels[1]==saves[1]&&saved[2]==reservedThird,"Restock did not use/clear home 2 after arrival or home 3 changed");System.out.println("[builder-home] Run: storage saves="+saves[0]+" restock returns="+saves[1]+" native travels="+travelCommands+" home 3 untouched");}
    static void run(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Automatic storage home replacement, warmup, native arrival, prompt repair and independent camera aim");
        installCommands(world,start.east(2));var chest=storage;
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
        world.getServer().runOnServer(server->{saved[0]=saved[1]=null;});
        teleport(world,start);context.waitTicks(12);
        context.runOnClient(client->((BuilderHomes)field(builder,"homes")).reset());
        int before=saveCommands;
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,80);
        context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(homes.ready()&&!builder.building(),"Storage setup failed or unexpectedly started building: "+builder.status());var entry=homes.saveData().get(0).getAsJsonObject();var feet=BlockPos.ofFloored(entry.get("x").getAsDouble(),entry.get("y").getAsDouble(),entry.get("z").getAsDouble());for(var reserved:List.of(feet,feet.up())){require((boolean)call(builder,"reservedSupplyAccess",new Class<?>[]{BlockPos.class},reserved),"Storage-home arrival cell was available to scaffolding");require(call(builder,"placement",new Class<?>[]{BlockPos.class,BlockState.class,Item.class,int.class,boolean.class},reserved,Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true)==null,"Scaffold could block native storage arrival");}});require(saveCommands==before+1,"Absent home 1 did not save exactly once");
        roofEdgeRoundTrip(context,world,builder,home2,chest);
        obstructedStorageRoundTrip(context,world,builder,home2,chest);
        immediateWorkBeforeAccess(context,world,builder,home2);
        verticalRepairOrder(context,world,builder,home2);
        sectionDependencyChain(context,world,builder,home2);
        restockRoundTrip(context,world,builder,home2,chest);
        temporaryFootingReturn(context,world,builder,home2,chest);
        teleport(world,start);context.waitTicks(12);
        promptRepair(context,world,builder,start);
        checkedRoomAccess(context,world,builder,start);
        elevatedRoomAccess(context,world,builder,start);
        crouchedMining(context,world,builder,start);
        columnEdgeMining(context,world,builder,start);
        teleport(world,start);context.waitTicks(12);
        rotations(context,builder,start);
        cameraContinuity(context,world,builder,start);
        context.runOnClient(client->{builder.setEnabled(false);setting(builder,"Builder Homes",false);setting(builder,"Head Spoofing",false);});
        System.out.println("[builder-home] PASS: storage home 1; exact transient home 2 restock order with native chest supply; deletion only after return; home 3 untouched; saved return resumes on temporary footing; rejected commands bounded; prompt repairs/crouch cleanup/smooth independent camera");
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
    private static void checkedRoomAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Reach enclosed unfinished work through a verified dry opening with homes enabled");
        var origin=start.add(-2,-1,10);var cells=new BlockState[100];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<4;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++){
            if(y==0||y==3||x==0||x==4||z==0||z==4){cells[(y*5+z)*5+x]=Blocks.STONE.getDefaultState();command(world,"setblock",origin.add(x,y,z),"stone");}
            else command(world,"setblock",origin.add(x,y,z),"air");
        }
        int target=(1*5+2)*5+2;cells[target]=Blocks.GLASS.getDefaultState();
        world.getServer().runCommand("give @a glass 1");world.getServer().runCommand("give @a stone 16");world.getServer().runCommand("give @a diamond_pickaxe");
        teleport(world,origin.add(2,1,-2));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("home-enclosed-access.nbt","test",5,4,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean opened=false;int elapsed=0;
        for(;elapsed<1000&&context.computeOnClient(client->builder.building());elapsed++){
            opened|=context.computeOnClient(client->!((Map<?,?>)field(builder,"floorAccessWork")).isEmpty());context.waitTick();
        }
        require(opened,"Homes-enabled room never registered native access mining");
        require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%5,i/25,i/5%5)),cells[i]))return false;return true;}),"Enclosed home access failed to finish work and replace its openings");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Enclosed home access left active work, scaffold, damage or menu");BuilderPacketChecks.verify(1);builder.pause("enclosed home access checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+(origin.getY()+1)+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+3)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Enclosed dry access and every broken wall restored in "+elapsed+" ticks; homes enabled; full health; zero supports");
    }
    private static void elevatedRoomAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Carry an exterior column's proved opening into native elevated-room entry");
        var origin=start.add(-2,2,10);var cells=new BlockState[100];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<4;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++){
            if(y==0||y==3||x==0||x==4||z==0||z==4){cells[(y*5+z)*5+x]=Blocks.STONE.getDefaultState();command(world,"setblock",origin.add(x,y,z),"stone");}
            else command(world,"setblock",origin.add(x,y,z),"air");
        }
        int target=37;cells[target]=Blocks.GLASS.getDefaultState();
        for(String item:List.of("glass 1","stone 16","dirt 32","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        teleport(world,start.south(8));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("home-elevated-entry.nbt","test",5,4,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean retained=false;int elapsed=0;
        for(;elapsed<2400&&context.computeOnClient(client->builder.building());elapsed++){
            retained|=context.computeOnClient(client->field(builder,"entryPassageTop")!=null);context.waitTick();
        }
        require(retained,"Elevated room did not exercise the retained exterior-column opening");
        require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%5,i/25,i/5%5)),cells[i]))return false;return true;}),"Exterior column did not open its proved passage and restore the room");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Elevated room left work, dirt, damage or menu");BuilderPacketChecks.verify(1);builder.pause("elevated entry checked");});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+3)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Exterior-column opening retained, mined and replaced; elevated work and zero-support cleanup completed in "+elapsed+" ticks");
    }
    private static void roofEdgeRoundTrip(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,BlockPos chest){
        var feet=work.south(4).up(4);var footing=feet.north().down();
        // Exact offsets from the 706-block native roof save failure: the
        // player's body overlaps the neighbouring floor while its feet cell
        // has air below. A centre-cell standing query rejects this safe pose.
        var position=Vec3d.ofBottomCenter(feet).add(-.168610556495,0,-.40800152568);
        for(String material:List.of("stone","dirt")){
            command(world,"setblock",footing,material);world.getServer().runCommand("tp @a "+position.x+" "+position.y+" "+position.z+" 0 0");context.waitTicks(12);int first=commands.size();
            context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(client.player.isOnGround()&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Roof-edge fixture did not establish a real grounded body");require(client.world.getBlockState(feet.down()).isAir()&&!new BuilderWalk().canStand(feet),"Roof-edge fixture's nominal centre unexpectedly has footing");require(homes.safeHere()==material.equals("stone"),"Roof-edge storage safety did not distinguish permanent and temporary footing");require(homes.restock(chest),"Native roof-edge body refused a restock save");});
            waitHome(context,builder,100);
            context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(homes.protectsFooting(footing)&&!homes.protectsFooting(feet.down()),"Home return protected the air cell instead of its actual supporting block");var savedData=homes.saveData();homes.loadData(savedData);require(homes.protectsFooting(footing)&&homes.returnToWork(),"Roof-edge footing was lost across save/load");});waitHome(context,builder,100);
            require(commands.subList(first,commands.size()).equals(List.of("delhome 2","sethome 2","home 1","home 2","delhome 2")),"Roof-edge restock command order changed");
            context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(client.player.isOnGround()&&client.player.getEntityPos().squaredDistanceTo(position)<.04&&client.player.getHealth()==20&&!homes.protectsFooting(footing)&&homes.saveData().get(1).isJsonNull(),"Roof-edge restock did not safely return and clear home 2");});
            command(world,"setblock",footing,"air");teleport(world,work);context.waitTicks(12);
        }
        System.out.println("[builder-home] Native off-centre roof footing: stone and dirt saved/protected/persisted, exact restock round trips, grounded returns and home 2 deletion");
    }
    private static void obstructedStorageRoundTrip(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,BlockPos chest){
        var arrival=chest.west(3);teleport(world,arrival);context.waitTicks(12);
        context.runOnClient(client->button(builder,"Set Storage Home").press());waitHome(context,builder,100);
        var obstacle=chest.west(2);command(world,"setblock",obstacle,"dirt");command(world,"setblock",obstacle.up(),"dirt");context.waitTicks(6);
        context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(homes.readyFor(chest)&&client.player.getBlockPos().equals(arrival)&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Obstructed storage fixture did not retain a safe three-cell home arrival");require(call(builder,"chestHit",new Class<?>[]{BlockPos.class,Vec3d.class},chest,client.player.getEyePos())==null,"Obstructed storage fixture still sees the chest");});
        restockRoundTrip(context,world,builder,work,chest,true);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(obstacle).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(obstacle.up()).isOf(Blocks.DIRT)),"Chest route removed an unrelated obstacle");
        command(world,"setblock",obstacle,"air");command(world,"setblock",obstacle.up(),"air");context.waitTicks(6);
        System.out.println("[builder-home] Obstructed three-cell storage arrival walked to a real chest view, collected exact stock and returned with one home 1 travel; obstacle intact");
    }
    private static void immediateWorkBeforeAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        String previousSupply=builder.sectionSupply()?"Nearby Sections":builder.layerSupply()?"Layer by Layer":"Whole Schematic";
        var ready=work.south(2);command(world,"setblock",work,"air");command(world,"setblock",ready,"air");world.getServer().runCommand("give @a stone 2");teleport(world,work);context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",false);setting(builder,"Auto Buy Tools",false);setting(builder,"Temporary Supports",false);setting(builder,"Stockpile In Chests",false);setting(builder,"Material Supply","Whole Schematic");builder.install(new Schematic("ready-before-access.nbt","test",1,1,3,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(work);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean placed=false;
        for(int tick=0;tick<80;tick++){
            placed=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(ready).isOf(Blocks.STONE));if(placed)break;
            context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(work))<.0004,"Started access movement while another full cube was immediately placeable"));context.waitTick();
        }
        require(placed&&world.getServer().computeOnServer(server->server.getOverworld().getBlockState(work).isAir()),"Immediate cube did not precede the occupied nearer target");
        for(int tick=0;tick<300&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(work).isOf(Blocks.STONE)&&server.getOverworld().getBlockState(ready).isOf(Blocks.STONE)),"Ready-first selection did not finish both native blocks");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Ready-first selection left work, supports or damage");BuilderPacketChecks.verify(2);builder.pause("ready-first order checked");setting(builder,"Temporary Supports",true);setting(builder,"Restock When Empty",true);setting(builder,"Stockpile In Chests",true);setting(builder,"Material Supply",previousSupply);});
        teleport(world,work.west(3));context.waitTicks(12);command(world,"setblock",work,"air");command(world,"setblock",ready,"air");context.waitTicks(4);
        System.out.println("[builder-home] Ready native cube placed before access movement; nearer occupied target then completed; bounded look packets, zero supports and full health");
    }
    private static void verticalRepairOrder(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        var lower=work.south(2);var owner=lower.east(2);
        command(world,"setblock",lower,"air");command(world,"setblock",lower.up(),"air");command(world,"setblock",owner,"stone");world.getServer().runCommand("give @a stone 2");teleport(world,work);context.waitTicks(12);
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",false);setting(builder,"Restock When Empty",false);setting(builder,"Stockpile In Chests",false);setting(builder,"Material Supply","Whole Schematic");
            builder.install(new Schematic("vertical-repair-order.nbt","test",3,2,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState()}));builder.setOrigin(lower);builder.preview();
        });context.waitTicks(15);
        context.runOnClient(client->{
            require(((byte[])field(builder,"states"))[2]==AutoBuilder.CORRECT,"Vertical repair owner was not already complete");
            @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(lower,2);openings.put(lower.up(),2);
            @SuppressWarnings("unchecked")var depths=(Map<BlockPos,Integer>)field(builder,"openingRepairDepth");depths.put(lower,1);depths.put(lower.up(),2);
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
        });
        boolean finished=false;
        for(int tick=0;tick<120;tick++){
            var pair=world.getServer().computeOnServer(server->List.of(server.getOverworld().getBlockState(lower).isOf(Blocks.STONE),server.getOverworld().getBlockState(lower.up()).isOf(Blocks.STONE)));
            require(!pair.get(1)||pair.get(0),"Upper repair preceded its native lower anchor");if(pair.get(0)&&pair.get(1)){finished=true;break;}context.waitTick();
        }
        require(finished,"Vertical repair depth blocked its missing lower anchor: "+context.computeOnClient(client->builder.status()));
        for(int tick=0;tick<80&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Vertical repair left work, scaffolds or damage: "+builder.status());BuilderPacketChecks.verify(2);builder.pause("vertical repair checked");});
        teleport(world,work);context.waitTicks(12);command(world,"setblock",lower,"air");command(world,"setblock",lower.up(),"air");command(world,"setblock",owner,"air");context.waitTicks(4);
        System.out.println("[builder-home] Registered vertical repairs completed lower anchor then upper cube; full health, bounded native look and zero supports");
    }
    private static void sectionDependencyChain(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        var first=work.south(2);var origin=first.west(6);var parent=first.east(2);var cells=new BlockState[9];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        cells[6]=cells[7]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.EAST);cells[8]=Blocks.STONE.getDefaultState();
        for(int x=0;x<3;x++)command(world,"setblock",first.east(x),"air");world.getServer().runCommand("give @a hopper 2");world.getServer().runCommand("give @a stone 1");teleport(world,work);context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Material Supply","Nearby Sections");builder.install(new Schematic("section-hopper-chain.nbt","test",9,1,1,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.preview();});context.waitTicks(15);
        context.runOnClient(client->{
            setField(builder,"sectionCells",List.of(6,7));setField(builder,"sectionProgressAt",field(builder,"ticks"));setField(builder,"sectionCorrect",field(builder,"correct"));
            require((boolean)call(builder,"waitingForBuiltNeighbour",new Class<?>[]{BlockPos.class,BlockState.class},first,cells[6]),"Section fixture did not need its outlet chain");
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
        });
        boolean finished=false;
        for(int tick=0;tick<160;tick++){
            finished=world.getServer().computeOnServer(server->AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(parent),cells[8])&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(first.east()),cells[7])&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(first),cells[6]));
            if(finished)break;context.waitTick();
        }
        require(finished,"Nearby section waited for a missing outlet outside its batch: "+context.computeOnClient(client->builder.status()));
        for(int tick=0;tick<80&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Section dependency chain left work, scaffolds or damage: "+builder.status());BuilderPacketChecks.verify(3);builder.pause("section dependencies checked");setting(builder,"Temporary Supports",true);setting(builder,"Restock When Empty",true);setting(builder,"Stockpile In Chests",true);});
        teleport(world,work);context.waitTicks(12);for(int x=0;x<3;x++)command(world,"setblock",first.east(x),"air");context.waitTicks(4);
        System.out.println("[builder-home] Missing native hopper outlet chain crossed the eight-cell batch boundary and finished before section timeout; full health and zero supports");
    }
    private static void restockRoundTrip(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,BlockPos chest){
        restockRoundTrip(context,world,builder,work,chest,false);
    }
    private static void restockRoundTrip(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,BlockPos chest,boolean reloadAtStorage){
        String previousSupply=builder.sectionSupply()?"Nearby Sections":builder.layerSupply()?"Layer by Layer":"Whole Schematic";
        var target=work.south(2);command(world,"setblock",target,"air");world.getServer().runCommand("clear @a stone");world.getServer().runCommand("clear @a water_bucket");world.getServer().runCommand("give @a water_bucket 1");
        world.getServer().runOnServer(server->{((net.minecraft.block.entity.ChestBlockEntity)server.getOverworld().getBlockEntity(chest)).setStack(0,new ItemStack(Items.STONE,8));saved[1]=new Home(Vec3d.ofBottomCenter(work.west(10)),0,0);});
        teleport(world,work);context.waitTicks(12);int first=commands.size();var third=saved[2];
        context.runOnClient(client->{
            setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",true);setting(builder,"Auto Buy Tools",false);setting(builder,"Temporary Supports",false);setting(builder,"Material Supply","Nearby Sections");
            builder.install(new Schematic("restock-return.nbt","test",3,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.WATER.getDefaultState()}));builder.setOrigin(target);setting(builder,"Stockpile In Chests",true);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            // The requested work survives an exhausted/refreshed section. Its
            // real chest pickup must still happen on this first home trip.
            try{var needed=builder.getClass().getDeclaredField("needed");needed.setAccessible(true);needed.set(builder,Items.STONE);}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
            require((boolean)call(builder,"beginRestock",new Class<?>[]{}),"Requested material with an empty section did not begin restock");
        });
        boolean placed=false,reloaded=false;for(int tick=0;tick<500;tick++){
            if(reloadAtStorage&&!reloaded)reloaded=context.computeOnClient(client->{
                var homes=(BuilderHomes)field(builder,"homes");var data=homes.saveData();
                if(homes.busy()||client.currentScreen!=null||data.get(1).isJsonNull())return false;
                var storage=data.get(0).getAsJsonObject();var arrival=new Vec3d(storage.get("x").getAsDouble(),storage.get("y").getAsDouble(),storage.get("z").getAsDouble());
                if(client.player.getEntityPos().squaredDistanceTo(arrival)>.04)return false;
                homes.loadData(data);return true;
            });
            placed=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(Blocks.STONE));if(placed)break;context.waitTick();
        }
        require(placed,"Restock round trip did not resume native placement: "+context.computeOnClient(client->builder.status()+" pos="+client.player.getEntityPos()+" health="+client.player.getHealth()+" grounded="+client.player.isOnGround()+" velocity="+client.player.getVelocity()+" homes="+((BuilderHomes)field(builder,"homes")).saveData()+" stage="+field(field(builder,"homes"),"stage")+" clock="+field(field(builder,"homes"),"clock")+" started="+field(field(builder,"homes"),"started")));
        require(commands.subList(first,commands.size()).equals(List.of("delhome 2","sethome 2","home 1","home 2","delhome 2")),"Incorrect restock command order: "+commands.subList(first,commands.size()));
        require(!reloadAtStorage||reloaded,"Obstructed restock did not exercise saved-return reload at storage");
        require(saved[1]==null&&saved[2]==third&&!movedDuringWarmup,"Home 2 was retained, home 3 changed or warmup moved");
        require(world.getServer().computeOnServer(server->((net.minecraft.block.entity.ChestBlockEntity)server.getOverworld().getBlockEntity(chest)).getStack(0).getCount()==7),"Native chest restock took an incorrect quantity");
        context.runOnClient(client->{require(client.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(work))<9&&client.player.getHealth()==20&&client.currentScreen==null,"Restock did not return safely to the work area");require(builder.inventoryCount(Items.WATER_BUCKET)==1,"Restock returned an unfinished source bucket while repairing another section");BuilderPacketChecks.verify(1);builder.pause("restock order checked");setting(builder,"Temporary Supports",true);setting(builder,"Material Supply",previousSupply);});
        world.getServer().runCommand("clear @a water_bucket");
        System.out.println("[builder-home] Requested material survives empty section: exact native pickup/placement, unfinished bucket retained; delhome 2 -> sethome 2 -> home 1 -> home 2 -> delhome 2; home 3 untouched");
    }
    private static void temporaryFootingReturn(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,BlockPos chest){
        var platform=work.up(2);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)command(world,"setblock",platform.add(x,0,z),"dirt");context.waitTicks(24);
        context.runOnClient(client->require(client.player.getVelocity().horizontalLengthSquared()<.0004,"Fixture TP retained walking momentum"));
        world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.onLanding();player.setVelocity(Vec3d.ZERO);});
        teleport(world,platform.up());context.waitTicks(24);
        var floor=context.computeOnClient(client->{var feet=client.player.getBlockPos().down();require(client.world.getBlockState(feet).isOf(Blocks.DIRT)&&client.player.isOnGround(),"Temporary-floor fixture did not settle: "+client.player.getEntityPos());return feet;});
        context.runOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");require(homes.restock(chest),"Temporary work footing refused restock");});waitHome(context,builder,100);
        require(saved[1]!=null,"Restock did not retain home 2 while at storage");var returnPoint=saved[1];int previousDeletes=deletes[1];var third=saved[2];
        context.runOnClient(client->{
            var homes=(BuilderHomes)field(builder,"homes");require(homes.protectsFooting(floor)&&!(boolean)call(builder,"safeToRecycle",new Class<?>[]{BlockPos.class},floor),"Return footing could be recycled while away: expected="+floor+" homes="+homes.saveData()+" native="+returnPoint.pos);
            var data=homes.saveData();homes.cancel();homes.loadData(data);require(homes.protectsFooting(floor),"Saved transient return metadata was lost");
        });
        rejectTravel=true;context.runOnClient(client->require(((BuilderHomes)field(builder,"homes")).returnToWork(),"Return retry did not begin"));waitHome(context,builder,50);rejectTravel=false;
        require(saved[1]==returnPoint&&deletes[1]==previousDeletes,"Rejected return deleted home 2 before arrival");
        context.runOnClient(client->{require(builder.status().contains("cooldown"),"Rejected return did not pause");builder.startBuild();});waitHome(context,builder,100);context.waitTick();
        require(saved[1]==null&&saved[2]==third&&!movedDuringWarmup,"Resumed return failed to clear only home 2");
        context.runOnClient(client->{require(client.player.isOnGround()&&client.player.getHealth()==20&&client.player.getEntityPos().squaredDistanceTo(returnPoint.pos)<.36,"Temporary-floor return was not confirmed safely");require(!((BuilderHomes)field(builder,"homes")).protectsFooting(floor),"Completed trip retained footing protection");builder.pause("temporary return checked");});
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(floor).isOf(Blocks.DIRT)),"Restock destroyed its return footing");
        teleport(world,work.west(3));context.waitTicks(12);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)command(world,"setblock",platform.add(x,0,z),"air");
        System.out.println("[builder-home] Saved work return survives pause/reload; temporary dirt footing protected; rejected teleport keeps home 2; resume confirms arrival then deletes it");
    }
    private static void rotations(ClientGameTestContext context,AutoBuilder builder,BlockPos start){
        context.runOnClient(client->{client.player.setYaw(178);client.player.setPitch(0);});
        float[] previous={178,0},previousView={178,0};boolean settled=false,visibleTurn=false;
        for(int tick=0;tick<65;tick++){
            final int turn=tick;
            settled=context.computeOnClient(client->{
                var point=Vec3d.ofCenter(start.east(3));boolean ready=(boolean)call(builder,"aim",new Class<?>[]{Vec3d.class},point);
                float delta=Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-previous[0]));require(delta<=12.01,"Aim exceeded natural yaw limit");require(Math.abs(client.player.getPitch()-previous[1])<=8.01,"Aim exceeded natural pitch limit");
                previous[0]=client.player.getYaw();previous[1]=client.player.getPitch();
                var camera=builder.builderCameraLook();require(camera!=null,"Visible head view was not active");
                client.player.changeLookDirection(0,0);
                require(Math.abs(MathHelper.wrapDegrees(camera[0]-previousView[0]))<=12.01&&Math.abs(camera[1]-previousView[1])<=8.01,"Visible head turn exceeded the eased rate");previousView[0]=camera[0];previousView[1]=camera[1];
                if(turn==0){float yaw=client.player.getYaw(),view=camera[0];client.player.changeLookDirection(20,0);require(client.player.getYaw()==yaw&&Math.abs(builder.builderCameraLook()[0]-view-3)<.01,"Mouse look changed server aim");client.player.changeLookDirection(-20,0);}
                return ready;
            });visibleTurn|=Math.abs(MathHelper.wrapDegrees(previousView[0]-178))>10;context.waitTick();if(settled)break;
        }
        require(settled&&visibleTurn,"Visible head rotation stayed frozen or failed to settle");
        context.runOnClient(client->{require(Math.abs(MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw()-178))>10,"Rendered head view did not visibly turn");require(Math.abs(MathHelper.wrapDegrees(builder.builderCameraLook()[0]-client.player.getYaw()))<1.5,"Visible view did not face the action before aim completed");builder.pause("visible head turn complete");require(builder.builderCameraLook()==null,"Pause retained head lock");});
    }
    private static void cameraContinuity(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var origin=start.west(5).south(3);var blocks=List.of(origin,origin.east(5),origin.east(10));
        for(var pos:blocks)command(world,"setblock",pos,"air");world.getServer().runCommand("give @a stone 3");context.waitTicks(6);
        context.runOnClient(client->{
            setting(builder,"Builder Homes",false);var states=new BlockState[11];Arrays.fill(states,Blocks.STRUCTURE_VOID.getDefaultState());for(int i:List.of(0,5,10))states[i]=Blocks.STONE.getDefaultState();
            builder.install(new Schematic("camera-placement-walk.nbt","test",11,1,1,BlockPos.ORIGIN,states));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
        });
        float[] previous={Float.NaN,Float.NaN};float[] total={0,0};boolean[] walking={false};int placed=0;
        for(int tick=0;tick<500;tick++){
            context.runOnClient(client->{
                var look=builder.builderCameraLook();
                if(look!=null&&builder.building()&&client.currentScreen==null){
                    if(!Float.isNaN(previous[0])){float yaw=Math.abs(MathHelper.wrapDegrees(look[0]-previous[0])),pitch=Math.abs(look[1]-previous[1]);require(yaw<=12.01&&pitch<=8.01,"Visible placement/walking head snapped");total[0]+=yaw;total[1]+=pitch;}
                    previous[0]=look[0];previous[1]=look[1];
                    require(Math.abs(MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw()-look[0]))<=24.01,"Rendered view snapped away from eased head");walking[0]|=((BuilderWalk)field(builder,"walker")).moving();
                }
            });
            placed=world.getServer().computeOnServer(server->(int)blocks.stream().filter(pos->server.getOverworld().getBlockState(pos).isOf(Blocks.STONE)).count());if(placed==3)break;context.waitTick();
        }
        require(placed==3&&walking[0]&&total[0]>30&&total[1]>10,"Visible head fixture did not turn, walk and place all three blocks");
        context.runOnClient(client->{BuilderPacketChecks.verify(3);builder.pause("camera continuity checked");setting(builder,"Builder Homes",true);});
        System.out.println("[builder-camera] PASS: visible eased yaw/pitch plus walking and three native placements; frame interpolation; no frozen view or post-placement snapping");
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
    private static void setField(Object instance,String name,Object value){try{var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);field.set(instance,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object call(Object instance,String name,Class<?>[] args,Object...values){try{var method=instance.getClass().getDeclaredMethod(name,args);method.setAccessible(true);return method.invoke(instance,values);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void require(boolean success,String message){if(!success)throw new AssertionError(message);}
}
