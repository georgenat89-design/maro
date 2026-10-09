package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.maro.builder.*;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static AutoBuilder looseTraceBuilder;
    private static BlockPos looseTraceOrigin;
    private static Object looseTraceChest;
    static {ClientTickEvents.END_CLIENT_TICK.register(client->{
        var builder=looseTraceBuilder;if(builder==null||client.world==null)return;
        var chest=field(builder,"restockTarget");if(chest==null){looseTraceChest=null;return;}if(Objects.equals(chest,looseTraceChest))return;looseTraceChest=chest;
        System.out.println("[builder-loose-restock-decision] ticks="+field(builder,"ticks")+" status="+builder.status()+" needed="+field(builder,"needed")+" held-bricks="+builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)+" held-glass="+builder.inventoryCount(Items.GLASS)+" pickup="+field(builder,"accessPickupId")+" pickup-deadline="+field(builder,"accessPickupUntil")+" pickup-retry="+field(builder,"accessPickupRetry")+" openings="+field(builder,"floorAccessWork")+" restock="+chest+" recovery="+field(builder,"recoveringAccessStock")+" player="+client.player.getEntityPos()+" drops="+client.world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(looseTraceOrigin).expand(8),item->true).stream().map(item->item.getStack()+" at "+item.getEntityPos()).toList());
    });}
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
        if(Boolean.getBoolean("maro.gametest.builderCleanupCeilingOnly")){
            cleanupCeilingOwnership(context,world,builder,start);
            System.out.println("[builder-cleanup-ceiling] PASS: native pickup, retained exit, owned-post cleanup and roof repair");return;
        }
        if(Boolean.getBoolean("maro.gametest.builderDoorOnly")){
            closedDoorAccess(context,world,builder,start);rotations(context,builder,start);
            System.out.println("[builder-door] PASS: native door route, contained water and brisk visible aim");return;
        }
        if(Boolean.getBoolean("maro.gametest.builderRepairCycleOnly")){
            repairDependencyCycles(context,world,builder,home2);
            System.out.println("[builder-cycle] PASS: native repair cycles, delayed material and scaffold cleanup");return;
        }
        if(Boolean.getBoolean("maro.gametest.builderReceiverOnly")){
            receiverColumnClimb(context,world,builder,start);
            System.out.println("[builder-receiver] PASS: prompt native receiver column, real stock recovery and cleanup");return;
        }
        if(Boolean.getBoolean("maro.gametest.builderEntryOnly")){
            pistonBeforeFrontContainer(context,world,builder,start);elevatedRoomAccess(context,world,builder,start);raisedLiquidEntrance(context,world,builder,start);rotations(context,builder,start);
            System.out.println("[builder-entry] PASS: prompt proved exterior entry, contained upper liquid and brisk rotations");return;
        }
        if(Boolean.getBoolean("maro.gametest.builderHopperOnly")){
            hopperCutProtection(context,world,builder,start);hopperRepairStock(context,world,builder,start);
            for(int sample=1;sample<32;sample++){
                System.out.println("[builder-home] Native loose repair sample "+(sample+1)+"/32");
                hopperRepairStock(context,world,builder,start,false,true);
            }
            buriedHopperRoofAccess(context,world,builder,start);existingViewBeforeRoof(context,world,builder,start);
            System.out.println("[builder-hopper] PASS: protected native hopper catchments, existing routes and legacy repair stock");return;
        }
        longCheckedWalk(context,world,builder,start);
        unproductiveHomeEscape(context,world,builder,start);
        missingMaterialBeforeAccess(context,world,builder,start);
        blockedRepairReceivers(context,world,builder,start,chest);
        receiverColumnClimb(context,world,builder,start);
        crouchedChestPlacementView(context,world,builder,start);
        scaffoldObstructedSign(context,world,builder,start);
        roofEdgeRoundTrip(context,world,builder,home2,chest);
        obstructedStorageRoundTrip(context,world,builder,home2,chest);
        immediateWorkBeforeAccess(context,world,builder,home2);
        verticalRepairOrder(context,world,builder,home2);
        repairOwnerOrder(context,world,builder,home2);
        repairDependencyCycles(context,world,builder,home2);
        sectionDependencyChain(context,world,builder,home2);
        buriedHopperRoofAccess(context,world,builder,start);
        offsetPistonRoofAccess(context,world,builder,start);
        pistonBeforeFrontContainer(context,world,builder,start);
        existingViewBeforeRoof(context,world,builder,start);
        hopperCutProtection(context,world,builder,start);
        hopperRepairStock(context,world,builder,start);
        cleanupCeilingOwnership(context,world,builder,start);
        offsetRepairReceiver(context,world,builder,start);
        closedDoorAccess(context,world,builder,start);
        restockRoundTrip(context,world,builder,home2,chest);
        temporaryFootingReturn(context,world,builder,home2,chest);
        teleport(world,start);context.waitTicks(12);
        promptRepair(context,world,builder,start);
        checkedRoomAccess(context,world,builder,start);
        elevatedRoomAccess(context,world,builder,start);
        raisedLiquidEntrance(context,world,builder,start);
        sealedRepairDrop(context,world,builder,start);
        crouchedMining(context,world,builder,start);
        columnEdgeMining(context,world,builder,start);
        teleport(world,start);context.waitTicks(12);
        rotations(context,builder,start);
        cameraContinuity(context,world,builder,start);
        context.runOnClient(client->{builder.setEnabled(false);setting(builder,"Builder Homes",false);setting(builder,"Head Spoofing",false);});
        System.out.println("[builder-home] PASS: storage home 1; exact transient home 2 restock order with native chest supply; deletion only after return; home 3 untouched; saved return resumes on temporary footing; rejected commands bounded; prompt repairs/crouch cleanup/smooth independent camera");
    }
    private static void cleanupCeilingOwnership(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Keep a cleanup ceiling open across real dropped-material pickup, then restore it after route cleanup");
        var origin=start.south(10);var cells=new BlockState[125];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        var roof=origin.add(2,4,2);var target=origin.add(6,2,2);var ledge=origin.add(5,2,2);var view=ledge.up();
        for(int y=0;y<5;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++){
            boolean wall=y==0||y==4||x==0||x==4||z==0||z==4;
            if(wall)cells[(y*5+z)*5+x]=Blocks.BEDROCK.getDefaultState();
            command(world,"setblock",origin.add(x,y,z),wall?"bedrock":"air");
        }
        cells[112]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();command(world,"setblock",roof,"cracked_polished_blackstone_bricks");
        command(world,"setblock",ledge,"stone");command(world,"setblock",target,"dirt");command(world,"setblock",target.down(),"dirt");
        world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");
        for(String item:List.of("dirt 32","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        teleport(world,origin.add(2,1,2));context.waitTicks(12);int firstCommand=commands.size();
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",false);
            builder.install(new Schematic("cleanup-ceiling-owner.nbt","test",5,5,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.preview();
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(target);owned.add(target.down());
        });context.waitTicks(12);
        Object search;
        try{var type=Class.forName("dev.maro.module.impl.player.AutoBuilder$ViewSearch");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);search=constructor.newInstance();}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
        context.runOnClient(client->{BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();setField(builder,"cleanupTarget",target);});
        boolean proved=false;
        for(int tick=0;tick<40;tick++){
            context.runOnClient(client->call(builder,"prepareCeilingEntry",new Class<?>[]{List.class,int.class,search.getClass()},List.of(view),-1,search));
            proved=context.computeOnClient(client->roof.equals(field(builder,"ceilingTop")));if(proved)break;context.waitTick();
        }
        require(proved,"Native cleanup fixture could not prove its ceiling exit: "+context.computeOnClient(client->builder.status()));
        boolean opened=false,reloaded=false;int elapsed=0;
        for(;elapsed<1800&&context.computeOnClient(client->builder.building());elapsed++){
            var pair=world.getServer().computeOnServer(server->List.of(server.getOverworld().getBlockState(roof).isAir(),server.getOverworld().getBlockState(target).isOf(Blocks.DIRT)));
            opened|=pair.get(0);
            require(!opened||!pair.get(1)||pair.get(0),"Repaired cleanup ceiling before removing owned target");
            if(opened&&!reloaded){context.runOnClient(client->{var data=builder.saveExtra();call(builder,"loadOpenings",new Class<?>[]{com.google.gson.JsonObject.class},data);});reloaded=true;}
            if(elapsed%200==0)System.out.println((String)context.computeOnClient(client->"[builder-cleanup-ceiling-progress] "+builder.status()+" player="+client.player.getEntityPos()+" supports="+builder.temporarySupports().size()));
            context.waitTick();
        }
        require(opened&&reloaded,"Cleanup fixture did not remove and reload its actual ceiling opening");
        String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++){if(cells[i].isOf(Blocks.STRUCTURE_VOID))continue;var at=origin.add(i%5,i/25,i/5%5);var actual=server.getOverworld().getBlockState(at);if(!AutoBuilder.matchesBuildState(actual,cells[i]))return at+" expected="+cells[i]+" actual="+actual;}return "";});
        require(mismatch.isEmpty(),"Cleanup ceiling or permanent room was not restored: "+mismatch);
        require(world.getServer().computeOnServer(server->{for(int y=0;y<8;y++)for(int z=-3;z<8;z++)for(int x=-3;x<9;x++)if(server.getOverworld().getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Cleanup ceiling left native owned scaffold dirt");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)==0&&client.player.getHealth()==20&&client.currentScreen==null,"Cleanup ceiling retained work, lost its recovered repair block or caused damage: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("cleanup ceiling checked");setting(builder,"Restock When Empty",true);});
        var cleanupCommands=commands.subList(firstCommand,commands.size());
        require((cleanupCommands.isEmpty()||cleanupCommands.equals(List.of("home 1")))&&!movedDuringWarmup,"Cleanup ceiling changed home slots or repeated its storage escape: "+cleanupCommands);
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+6)+" "+(origin.getY()+4)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Cleanup exit survived native pickup and saved-opening reload; target and access columns removed, all permanent blocks restored, zero dirt, exact recovered material and full health in "+elapsed+" ticks");
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
    private static void buriedHopperRoofAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Prove typed buried hopper placement through a roof opening, then restore the whole roof");
        var origin=start.add(-2,0,10);var cells=new BlockState[75];Arrays.fill(cells,Blocks.STONE.getDefaultState());
        for(int y=0;y<3;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)command(world,"setblock",origin.add(x,y,z),"stone");
        int target=37;var pos=origin.add(2,1,2);var roof=pos.up();
        cells[target]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.NORTH);command(world,"setblock",pos,"air");
        for(String item:List.of("hopper 1","stone 16","dirt 32","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        teleport(world,origin.add(2,3,3));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("buried-hopper-roof.nbt","test",5,3,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean opened=false,registered=false;int elapsed=0;
        for(;elapsed<1800&&context.computeOnClient(client->builder.building()||((BuilderHomes)field(builder,"homes")).busy());elapsed++){
            opened|=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(roof).isAir());
            registered|=context.computeOnClient(client->Objects.equals(((Map<?,?>)field(builder,"floorAccessWork")).get(roof),target));
            if(elapsed%200==0)System.out.println((String)context.computeOnClient(client->"[builder-hopper-roof-progress] "+builder.status()+" player="+client.player.getEntityPos()+" target="+builder.state(target)));
            context.waitTick();
        }
        require(opened&&registered,"Typed buried hopper never opened its proved roof view: "+context.computeOnClient(client->builder.status()));
        String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++){var at=origin.add(i%5,i/25,i/5%5);var actual=server.getOverworld().getBlockState(at);if(!AutoBuilder.matchesBuildState(actual,cells[i]))return at+" expected="+cells[i]+" actual="+actual;}return "";});
        require(mismatch.isEmpty(),"Buried native hopper or roof restoration failed: "+mismatch);
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Typed roof access left work, supports, damage or menu: "+builder.status());BuilderPacketChecks.verify(2);builder.pause("buried hopper roof checked");});
        require(world.getServer().computeOnServer(server->{for(int y=0;y<7;y++)for(int z=-4;z<9;z++)for(int x=-4;x<9;x++)if(server.getOverworld().getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Typed roof access left raw scaffold dirt");
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+2)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Typed native hopper placed facing north through its registered roof opening; all 75 blocks restored, zero dirt, bounded look and full health in "+elapsed+" ticks");
    }
    private static void offsetPistonRoofAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Open only proved offset roof beams for an oriented piston; preserve its neighbouring container");
        var origin=start.add(-2,0,10);var cells=new BlockState[75];Arrays.fill(cells,Blocks.STONE.getDefaultState());
        for(int y=0;y<3;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)command(world,"setblock",origin.add(x,y,z),"stone");
        int target=(1*5+2)*5+1;var pos=origin.add(1,1,2);cells[target]=Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING,Direction.EAST);command(world,"setblock",pos,"air");
        for(var gap:List.of(new BlockPos(1,2,2),new BlockPos(3,2,3))){cells[(gap.getY()*5+gap.getZ())*5+gap.getX()]=Blocks.AIR.getDefaultState();command(world,"setblock",origin.add(gap),"air");}
        var container=origin.add(2,1,2);cells[(1*5+2)*5+2]=Blocks.YELLOW_SHULKER_BOX.getDefaultState();command(world,"setblock",container,"yellow_shulker_box");
        for(String item:List.of("sticky_piston 1","stone 16","dirt 32","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        teleport(world,origin.add(3,2,3));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("offset-piston-roof.nbt","test",5,3,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean opened=false;int elapsed=0;
        for(;elapsed<1600&&context.computeOnClient(client->builder.building()||((BuilderHomes)field(builder,"homes")).busy());elapsed++){
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(container).isOf(Blocks.YELLOW_SHULKER_BOX)),"Offset piston access removed its protected container");
            opened|=context.computeOnClient(client->{var repairs=(Map<?,?>)field(builder,"floorAccessWork");return Objects.equals(repairs.get(origin.add(2,2,2)),target)||Objects.equals(repairs.get(origin.add(2,2,3)),target);});
            if(elapsed%200==0)System.out.println((String)context.computeOnClient(client->"[builder-piston-roof-progress] "+builder.status()+" player="+client.player.getEntityPos()+" target="+builder.state(target)+" correct="+field(builder,"correct")+" solid="+field(builder,"solid")+" ignored="+field(builder,"ignoredSolid")+" restoring="+field(builder,"openingRestoration")+" repairs="+field(builder,"floorAccessWork")+" roof="+field(builder,"liquidTopBlocks")+" passage="+field(builder,"passageBlocks")+" mining="+field(builder,"mining")));
            context.waitTick();
        }
        require(opened,"Oriented piston never registered an offset roof opening: "+context.computeOnClient(client->builder.status()));
        String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++){var at=origin.add(i%5,i/25,i/5%5);var actual=server.getOverworld().getBlockState(at);if(!AutoBuilder.matchesBuildState(actual,cells[i]))return at+" expected="+cells[i]+" actual="+actual;}return "";});
        require(mismatch.isEmpty(),"Native east-facing piston or offset roof was not restored: "+mismatch);
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Offset piston access left work, dirt, damage or menu: "+builder.status());BuilderPacketChecks.verify(2);builder.pause("offset piston roof checked");});
        require(world.getServer().computeOnServer(server->{for(int y=0;y<7;y++)for(int z=-4;z<9;z++)for(int x=-4;x<9;x++)if(server.getOverworld().getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Offset piston access left raw dirt");
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+2)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Native east-facing piston through registered offset roof beams; all cells restored, protected container retained, zero dirt, full health and bounded look in "+elapsed+" ticks");
    }
    private static void pistonBeforeFrontContainer(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Place horizontal pistons before their front containers seal the native placement view");
        var origin=start.add(-2,0,10);int width=5,height=4,length=5;
        for(var facing:Direction.Type.HORIZONTAL){
            var cells=new BlockState[width*height*length];Arrays.fill(cells,Blocks.AIR.getDefaultState());
            var center=new BlockPos(2,1,2);var front=center.offset(facing);var rear=center.offset(facing.getOpposite());
            for(int z=0;z<length;z++)for(int x=0;x<width;x++)cells[z*width+x]=Blocks.STONE.getDefaultState();
            for(var side:Direction.Type.HORIZONTAL)if(side!=facing)for(int y=1;y<=2;y++){
                var wall=center.offset(side).withY(y);cells[(y*length+wall.getZ())*width+wall.getX()]=Blocks.STONE.getDefaultState();
            }
            for(int z=1;z<=3;z++)for(int x=1;x<=3;x++)cells[(3*length+z)*width+x]=Blocks.STONE.getDefaultState();
            int target=(center.getY()*length+center.getZ())*width+center.getX(),container=(front.getY()*length+front.getZ())*width+front.getX();
            cells[target]=Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING,facing);
            cells[container]=Blocks.YELLOW_SHULKER_BOX.getDefaultState();
            var hopper=center.down();cells[hopper.getZ()*width+hopper.getX()]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,facing);
            for(int i=0;i<cells.length;i++){
                var at=origin.add(i%width,i/(width*length),i/width%length);var state=cells[i];
                command(world,"setblock",at,i==target||i==container?"air":state.isOf(Blocks.HOPPER)?"hopper[facing="+facing.asString()+"]":state.isOf(Blocks.STONE)?"stone":"air");
            }
            world.getServer().runCommand("give @a sticky_piston 1");world.getServer().runCommand("give @a yellow_shulker_box 1");
            teleport(world,origin.add(center.offset(facing,2)));context.waitTicks(12);
            context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("piston-before-container-"+facing.asString()+".nbt","test",width,height,length,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
            int elapsed=0;
            for(;elapsed<600&&context.computeOnClient(client->builder.building()||((BuilderHomes)field(builder,"homes")).busy());elapsed++){
                require(world.getServer().computeOnServer(server->{var w=server.getOverworld();return !w.getBlockState(origin.add(front)).isOf(Blocks.YELLOW_SHULKER_BOX)||AutoBuilder.matchesBuildState(w.getBlockState(origin.add(center)),cells[target]);}),"Front container sealed the missing "+facing+" piston placement view");context.waitTick();
            }
            require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%width,i/(width*length),i/width%length)),cells[i]))return false;return true;}),"Native "+facing+" piston/container order did not finish: "+context.computeOnClient(client->builder.status()));
            context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Piston/container ordering left work, dirt, damage or menu");BuilderPacketChecks.verify(2);builder.pause("piston approach checked");setting(builder,"Temporary Supports",true);});
            teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+3)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
            System.out.println("[builder-piston-order] "+facing+" native piston before front container, all cells complete, zero cuts/dirt, full health in "+elapsed+" ticks");
        }
    }
    private static void existingViewBeforeRoof(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Walk to an existing native view before opening an intact glass roof");
        var origin=start.add(-1,0,10);var cells=new BlockState[8];
        for(int y=0;y<2;y++)for(int z=0;z<2;z++)for(int x=0;x<2;x++){int i=(y*2+z)*2+x;cells[i]=(y==0?Blocks.STONE:Blocks.GLASS).getDefaultState();command(world,"setblock",origin.add(x,y,z),i==0?"air":y==0?"stone":"glass");}
        world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a glass 8");teleport(world,origin.add(1,2,1));context.waitTicks(12);
        int glass=context.computeOnClient(client->builder.inventoryCount(Items.GLASS));
        context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("existing-view-before-roof.nbt","test",2,2,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        for(int tick=0;tick<500&&context.computeOnClient(client->builder.building()||((BuilderHomes)field(builder,"homes")).busy());tick++){
            require(world.getServer().computeOnServer(server->{for(int z=0;z<2;z++)for(int x=0;x<2;x++)if(!server.getOverworld().getBlockState(origin.add(x,1,z)).isOf(Blocks.GLASS))return false;return true;}),"Opened glass despite a reachable existing placement view");context.waitTick();
        }
        require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%2,i/4,i/2%2)),cells[i]))return false;return true;}),"Existing-view repair did not complete every native cell");
        context.runOnClient(client->{require(!builder.building()&&builder.inventoryCount(Items.GLASS)==glass&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Existing view consumed glass, left work, dirt, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("existing view checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+1)+" "+(origin.getY()+1)+" "+(origin.getZ()+1)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Missing native stone repaired from an existing view; intact glass roof and inventory, zero dirt, full health and bounded look");
    }
    private static void hopperCutProtection(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Keep covered, disabled and deep hopper catchments intact; reject stale mining and finish from the existing view");
        var origin=start.south(10);var cells=new BlockState[24];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        clearPriorHopperFixtureDrops(world,origin);
        cells[0]=Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING,Direction.WEST);
        cells[1]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.WEST).with(HopperBlock.ENABLED,false);
        for(int i:List.of(2,6,9,17))cells[i]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();cells[10]=Blocks.GLASS.getDefaultState();
        for(int i=0;i<cells.length;i++)command(world,"setblock",origin.add(i%4,i/8,i/4%2),i==0?"dispenser[facing=west]":List.of(2,6,9,17).contains(i)?"cracked_polished_blackstone_bricks":"air");
        var beam=origin.add(1,1,0);var upper=beam.up();var temp=origin.add(0,2,1);
        world.getServer().runOnServer(server->{var stock=(net.minecraft.inventory.Inventory)server.getOverworld().getBlockEntity(origin);stock.setStack(0,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,4));stock.setStack(1,new ItemStack(Items.DIAMOND,7));stock.markDirty();});
        world.getServer().runCommand("clear @a glass");world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");world.getServer().runCommand("give @a glass 1");world.getServer().runCommand("give @a diamond_shovel");
        teleport(world,origin.add(2,1,1));context.waitTicks(12);int first=commands.size();
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("protected-hopper-cuts.nbt","test",4,3,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);
            require(!(boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},beam,false),"Empty native fall column was marked as a hopper");
        });
        command(world,"setblock",origin.east(),"hopper[facing=west,enabled=false]");command(world,"setblock",temp,"dirt");context.waitTicks(8);
        context.runOnClient(client->{
            require((boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},beam,false),"New native hopper did not invalidate the cached clear column");
            require((boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},beam.up(12),false),"Deep covered hopper was missed");
            require((boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},beam.east().south(),false),"Adjacent deflection column was not protected");
            require(!(boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},origin.add(2,0,1),false),"Same-height block was incorrectly above a hopper");
            require(!(boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},beam.east(3),false),"Distant unrelated fall column was protected");
            @SuppressWarnings("unchecked")var posts=(Set<BlockPos>)field(builder,"supports");posts.add(temp);
            require(!(boolean)call(builder,"aboveHopper",new Class<?>[]{BlockPos.class,boolean.class},temp,true),"Owned temporary dirt could not be cleaned above a hopper");
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);BuilderPacketChecks.expectIntactBlocks(Set.of(beam,upper));builder.startBuild();
            @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(beam,10);
            @SuppressWarnings("unchecked")var passage=(Set<BlockPos>)field(builder,"passageBlocks");passage.add(beam);setField(builder,"passageStand",client.player.getBlockPos());setField(builder,"routeMining",beam);setField(builder,"mining",beam);
            setField(builder,"descentLanding",true);setField(builder,"recoveryPhase",2);setField(builder,"recoveryJump",true);client.options.jumpKey.setPressed(true);
            call(builder,"mineTick",new Class<?>[]{});
            require(field(builder,"mining")==null&&field(builder,"routeMining")==null&&passage.isEmpty()&&builder.status().startsWith("Keeping blocks above hoppers"),"A stale verified route bypassed the final hopper mining guard: "+builder.status());
            require(!(boolean)field(builder,"descentLanding")&&(int)field(builder,"recoveryPhase")==0&&!client.options.jumpKey.isPressed(),"Rejected hopper route retained its stale descent or jump");
        });
        int elapsed=0;
        for(;elapsed<600&&context.computeOnClient(client->builder.building());elapsed++){
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(beam).isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)&&server.getOverworld().getBlockState(upper).isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)),"A protected beam was removed during alternate-route building");context.waitTick();
        }
        require(world.getServer().computeOnServer(server->{var w=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(w.getBlockState(origin.add(i%4,i/8,i/4%2)),cells[i]))return false;var stock=(net.minecraft.inventory.Inventory)w.getBlockEntity(origin);return w.getBlockState(temp).isAir()&&stock.getStack(0).getCount()==4&&stock.getStack(1).isOf(Items.DIAMOND)&&stock.getStack(1).getCount()==7;}),"Protected route left missing cells, temporary dirt or changed hopper stock");
        require(commands.size()==first,"Protected local access used a storage home");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Protected route left work, supports, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("hopper cuts protected");});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+3)+" "+(origin.getY()+2)+" "+(origin.getZ()+1)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Native hopper guard rejected stale cuts, preserved covered/disabled/deep/adjacent beams and exact stock; existing-view placement plus owned dirt cleanup, no mining packets on protected blocks, full health in "+elapsed+" ticks");
    }
    private static void hopperRepairStock(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        hopperRepairStock(context,world,builder,start,false,false);hopperRepairStock(context,world,builder,start,true,false);hopperRepairStock(context,world,builder,start,false,true);
    }
    private static void hopperRepairStock(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean chestReceiver,boolean looseReceiver){
        System.out.println("[builder-home] Recover "+(looseReceiver?"mined access blocks through native loose drops":"legacy holes from stock already in a native hopper pipe")+"; preserve existing stock");
        var origin=start.south(10);var cells=new BlockState[24];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        clearPriorHopperFixtureDrops(world,origin);
        cells[0]=Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING,Direction.WEST);cells[1]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.WEST);
        if(chestReceiver){cells[0]=Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.WEST).with(ChestBlock.CHEST_TYPE,net.minecraft.block.enums.ChestType.RIGHT);cells[4]=cells[0].with(ChestBlock.CHEST_TYPE,net.minecraft.block.enums.ChestType.LEFT);}
        cells[2]=cells[6]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();cells[9]=cells[17]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();cells[10]=Blocks.GLASS.getDefaultState();
        if(looseReceiver)cells[1]=cells[5]=cells[7]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        // Lower beam9 closes before upper17; job10 owns both openings.
        for(int i:List.of(0,1,2,6,9,17))command(world,"setblock",origin.add(i%4,i/8,i/4%2),i==0?"dispenser[facing=west]":i==1?"hopper[facing=west]":"cracked_polished_blackstone_bricks");
        if(chestReceiver){command(world,"setblock",origin,"chest[facing=west,type=right]");command(world,"setblock",origin.south(),"chest[facing=west,type=left]");}
        if(looseReceiver)for(int i:List.of(1,5,7))command(world,"setblock",origin.add(i%4,0,i/4),"cracked_polished_blackstone_bricks");
        if(looseReceiver)world.getServer().runCommand("give @a diamond_pickaxe");
        var beam=origin.add(1,1,0);var upper=beam.up();var target=origin.add(2,1,0);command(world,"setblock",target,"air");
        // Legacy damage predates this run. New access must never mine these beams above a hopper.
        if(!looseReceiver){command(world,"setblock",beam,"air");command(world,"setblock",upper,"air");}
        world.getServer().runOnServer(server->{var inventory=(net.minecraft.inventory.Inventory)server.getOverworld().getBlockEntity(origin);inventory.setStack(0,new net.minecraft.item.ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,looseReceiver?4:6));inventory.setStack(1,new net.minecraft.item.ItemStack(Items.DIAMOND,7));inventory.markDirty();});
        world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");world.getServer().runCommand("clear @a glass");world.getServer().runCommand("give @a glass 1");teleport(world,origin.add(looseReceiver?3:2,1,1));context.waitTicks(12);int first=commands.size();
        context.runOnClient(client->{if(looseReceiver){looseTraceBuilder=builder;looseTraceOrigin=origin;looseTraceChest=null;}setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("hopper-access-stock.nbt","test",4,3,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(beam,10);openings.put(upper,10);
            @SuppressWarnings("unchecked")var depths=(Map<BlockPos,Integer>)field(builder,"openingRepairDepth");depths.put(beam,-beam.getY());depths.put(upper,-upper.getY());
            if(looseReceiver){@SuppressWarnings("unchecked")var passage=(Set<BlockPos>)field(builder,"passageBlocks");passage.add(upper);passage.add(beam);setField(builder,"passageStand",client.player.getBlockPos());}
            else{
                @SuppressWarnings("unchecked")var sources=(Set<BlockPos>)call(builder,"accessPipeBelow",new Class<?>[]{BlockPos.class},beam);
                @SuppressWarnings("unchecked")var receipts=(Map<BlockPos,Set<BlockPos>>)field(builder,"accessDropSources");receipts.put(beam,sources);receipts.put(upper,sources);
                @SuppressWarnings("unchecked")var materials=(Map<Item,Set<BlockPos>>)field(builder,"accessStockSources");materials.put(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,new LinkedHashSet<>(sources));
                BuilderPacketChecks.expectIntactBlocks(Set.of(beam,upper));
            }
        });
        boolean mined=false,recovered=false,persisted=looseReceiver;int elapsed=0;
        for(;elapsed<800&&context.computeOnClient(client->builder.building());elapsed++){
            mined|=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(beam).isAir());
            recovered|=context.computeOnClient(client->looseReceiver?(int)field(builder,"accessPickupId")>=0:(boolean)field(builder,"recoveringAccessStock"));
            if(!persisted&&context.computeOnClient(client->!((Map<?,?>)field(builder,"accessDropSources")).isEmpty())){context.runOnClient(client->{var savedBuild=builder.saveExtra();call(builder,"loadOpenings",new Class<?>[]{com.google.gson.JsonObject.class},savedBuild);require(!((Map<?,?>)field(builder,"accessDropSources")).isEmpty()&&!((Map<?,?>)field(builder,"accessStockSources")).isEmpty(),"Saved access openings lost their native pipe/material receipts");});persisted=true;}context.waitTick();
        }
        require(mined&&recovered&&persisted,"Native access did not recover its replacement stock: "+context.computeOnClient(client->builder.status()));
        String stockResult=world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(level.getBlockState(origin.add(i%4,i/8,i/4%2)),cells[i]))return "block mismatch at "+i+" actual="+level.getBlockState(origin.add(i%4,i/8,i/4%2));var inventory=(net.minecraft.inventory.Inventory)level.getBlockEntity(origin);int stone=0,diamonds=0;for(int i=0;i<inventory.size();i++){var stack=inventory.getStack(i);if(stack.isOf(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS))stone+=stack.getCount();else if(stack.isOf(Items.DIAMOND))diamonds+=stack.getCount();else if(!stack.isEmpty())return "unexpected stock "+stack;}return stone==4&&diamonds==7?"":"brick stock="+stone+" diamonds="+diamonds;});require(stockResult.isEmpty(),"Access recovery changed native pipe blocks or unrelated/pre-existing stock: "+stockResult);
        context.runOnClient(client->looseTraceBuilder=null);
        require(commands.size()==first,"Local access-stock recovery travelled through storage homes");
        context.runOnClient(client->{require(!builder.building()&&builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)==0&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Native access recovery left materials, work, supports, damage or a menu: "+builder.status());if(!looseReceiver){require(!((Map<?,?>)field(builder,"accessStockSources")).isEmpty(),"Completed openings discarded their material receiver ledger");var saved=builder.saveExtra();call(builder,"loadOpenings",new Class<?>[]{com.google.gson.JsonObject.class},saved);require(!((Map<?,?>)field(builder,"accessStockSources")).isEmpty(),"Completed material receivers did not survive reload");}BuilderPacketChecks.verify(3);builder.pause("hopper repair stock checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+3)+" "+(origin.getY()+2)+" "+(origin.getZ()+1)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Access material recovered through "+(looseReceiver?"native loose drops":"native hopper/"+(chestReceiver?"double chest":"dispenser")+" UI without new cuts above hoppers")+"; exact two-beam repairs, original brick/diamond stock intact, no home trip, zero supports and full health in "+elapsed+" ticks");
    }
    private static void clearPriorHopperFixtureDrops(TestSingleplayerContext world,BlockPos origin){
        // These cases reuse the same footprint. A disabled hopper in the prior
        // case leaves its mined dirt loose; a newly enabled hopper must not
        // collect that unrelated item after this case seeds its exact stock.
        world.getServer().runOnServer(server->{
            var drops=server.getOverworld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(origin.getX()-2,origin.getY()-2,origin.getZ()-2,origin.getX()+6,origin.getY()+5,origin.getZ()+4),item->true);
            drops.forEach(net.minecraft.entity.Entity::discard);
            System.out.println("[builder-home] Cleared "+drops.size()+" prior native loose items before hopper fixture setup");
        });
    }
    private static void closedDoorAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Open an existing wooden door for dry basin assembly, then restore both closed halves without mining it");
        var origin=start.add(-3,-1,8);var cells=new BlockState[196];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<4;y++)for(int z=0;z<7;z++)for(int x=0;x<7;x++){
            var pos=origin.add(x,y,z);
            if(y==0||y==3||x==0||x==6||z==0||z==6){cells[(y*7+z)*7+x]=Blocks.BEDROCK.getDefaultState();command(world,"setblock",pos,"bedrock");}
            else command(world,"setblock",pos,"air");
        }
        int target=(1*7+4)*7+3;cells[target]=Blocks.GLASS.getDefaultState();
        int futureWater=target+49;cells[futureWater]=Blocks.WATER.getDefaultState();
        for(var side:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
            var wall=new BlockPos(3,2,4).offset(side);cells[(wall.getY()*7+wall.getZ())*7+wall.getX()]=Blocks.BEDROCK.getDefaultState();command(world,"setblock",origin.add(wall),"bedrock");
        }
        var roofOpening=origin.add(3,3,4);cells[(3*7+4)*7+3]=Blocks.STRUCTURE_VOID.getDefaultState();
        var door=origin.add(3,1,0);
        for(int part=0;part<2;part++){
            var state=Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING,Direction.SOUTH).with(DoorBlock.HINGE,net.minecraft.block.enums.DoorHinge.RIGHT).with(DoorBlock.HALF,part==0?net.minecraft.block.enums.DoubleBlockHalf.LOWER:net.minecraft.block.enums.DoubleBlockHalf.UPPER);
            cells[((1+part)*7)*7+3]=state;command(world,"setblock",door.up(part),"dark_oak_door[facing=south,hinge=right,half="+(part==0?"lower":"upper")+",open=false]");
        }
        world.getServer().runCommand("give @a glass 1");world.getServer().runCommand("give @a water_bucket 1");teleport(world,origin.add(3,1,-2));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("closed-door-dry-basin.nbt","test",7,4,7,BlockPos.ORIGIN,cells));builder.setOrigin(origin);
            require((boolean)call(builder,"liquidBoundary",new Class<?>[]{BlockPos.class},origin.add(3,1,4)),"Dry fixture did not establish its planned basin floor");
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean opened=false,registered=false,roofReady=false;int elapsed=0;
        for(;elapsed<1200&&context.computeOnClient(client->builder.building()||((BuilderHomes)field(builder,"homes")).busy());elapsed++){
            boolean open=world.getServer().computeOnServer(server->{var lower=server.getOverworld().getBlockState(door);var upper=server.getOverworld().getBlockState(door.up());require(lower.isOf(Blocks.DARK_OAK_DOOR)&&upper.isOf(Blocks.DARK_OAK_DOOR),"Access mined an existing door");return lower.get(DoorBlock.OPEN)&&upper.get(DoorBlock.OPEN);});
            opened|=open;
            if(open)registered|=context.computeOnClient(client->{var repairs=(Map<?,?>)field(builder,"floorAccessWork");return Objects.equals(repairs.get(door),target)&&Objects.equals(repairs.get(door.up()),target);});
            if(!roofReady&&world.getServer().computeOnServer(server->server.getOverworld().getBlockState(origin.add(3,1,4)).isOf(Blocks.GLASS))){
                // Make the fixture's separate roof access available only after
                // real dry assembly; it cannot serve as an above-only shortcut.
                command(world,"setblock",roofOpening,"air");command(world,"setblock",origin.add(7,1,2),"bedrock");command(world,"setblock",origin.add(7,2,3),"bedrock");roofReady=true;
            }
            if(elapsed%100==0)System.out.println((String)context.computeOnClient(client->"[builder-door-progress] "+builder.status()+" player="+client.player.getEntityPos()+" target="+builder.state(target)+" open="+client.world.getBlockState(door).get(DoorBlock.OPEN)+" goal="+field(builder,"standGoal")+" tuning="+field(builder,"tuningTarget")+" route="+field(field(builder,"walker"),"goal")));
            context.waitTick();
        }
        require(opened&&registered,"Existing door was not opened and registered for its enclosed work: "+context.computeOnClient(client->builder.status()));
        require(roofReady,"Dry basin floor never completed before roof access");
        String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++){var pos=origin.add(i%7,i/49,i/7%7);var actual=server.getOverworld().getBlockState(pos);if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(actual,cells[i]))return pos+" expected="+cells[i]+" actual="+actual;if(i!=futureWater&&!actual.getFluidState().isEmpty())return "Fluid escaped into "+pos;}return "";});
        require(mismatch.isEmpty(),"Dry basin/native fill changed a wall or leaked: "+mismatch+" status="+context.computeOnClient(client->builder.status()));
        context.runOnClient(client->{require(!builder.building()&&builder.state(futureWater)==AutoBuilder.CORRECT&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Door access left active work, dirt, damage or menu: "+builder.status());BuilderPacketChecks.verify(2);builder.pause("closed door checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+(origin.getY()+1)+" "+origin.getZ()+" "+(origin.getX()+6)+" "+(origin.getY()+3)+" "+(origin.getZ()+6)+" air");
        world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+6)+" "+origin.getY()+" "+(origin.getZ()+6)+" stone");context.waitTicks(4);
        command(world,"setblock",origin.add(7,1,2),"air");command(world,"setblock",origin.add(7,2,3),"air");
        System.out.println("[builder-home] Dry basin floor placed through an existing native door, then real water contained from roof access; both door halves restored in "+elapsed+" ticks; unchanged walls, full health and zero supports");
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
        var retired=origin;
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("home-elevated-entry.nbt","test",5,4,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var oldPassage=(Set<BlockPos>)field(builder,"passageBlocks");oldPassage.add(retired);
        });
        boolean retained=false;int elapsed=0;var stageTicks=new LinkedHashMap<String,Integer>();
        for(;elapsed<2400&&context.computeOnClient(client->builder.building());elapsed++){
            retained|=context.computeOnClient(client->{stageTicks.merge(builder.status(),1,Integer::sum);return field(builder,"entryPassageTop")!=null;});
            context.runOnClient(client->{if(field(builder,"passageStand")!=null)require(!((Set<?>)field(builder,"passageBlocks")).contains(retired),"New verified passage retained a retired opening");});context.waitTick();
        }
        System.out.println("[builder-entry-stage-ticks] "+stageTicks);
        require(retained,"Elevated room did not exercise the retained exterior-column opening");
        require(elapsed<1000,"Proved exterior entry was delayed by speculative access steps: "+elapsed+" ticks, "+context.computeOnClient(client->builder.status()));
        require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%5,i/25,i/5%5)),cells[i]))return false;return true;}),"Exterior column did not open its proved passage and restore the room");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Elevated room left work, dirt, damage or menu");BuilderPacketChecks.verify(1);builder.pause("elevated entry checked");});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+3)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Exterior-column opening retained, mined and replaced; elevated work and zero-support cleanup completed in "+elapsed+" ticks");
    }
    private static void sealedRepairDrop(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Recover a required repair drop on a sealed upper ledge through native proved access");
        var origin=start.add(-3,0,10);var cells=new BlockState[7*5*8];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<5;y++)for(int z=0;z<8;z++)for(int x=0;x<7;x++){
            boolean room=z<7&&(y==0||y==3||(y==1||y==2)&&(x==0||x==6||z==0||z==6));
            boolean ledge=y==1&&z==7&&x>=2&&x<=4;
            if(room||ledge)cells[(y*8+z)*7+x]=Blocks.STONE.getDefaultState();
            command(world,"setblock",origin.add(x,y,z),room||ledge?"stone":"air");
        }
        int repair=(3*8+1)*7+1;var opening=origin.add(1,3,1);cells[repair]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();command(world,"setblock",opening,"air");
        int owner=(2*8+7)*7+2;var ownerPos=origin.add(2,2,7);cells[owner]=Blocks.GLASS.getDefaultState();
        world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");
        world.getServer().runCommand("give @a cracked_polished_blackstone_bricks 1");
        world.getServer().runCommand("clear @a glass");
        for(String item:List.of("stone 16","dirt 64","diamond_pickaxe","diamond_shovel","cooked_beef 16"))world.getServer().runCommand("give @a "+item);
        // The case starts with a settled drop on this one-block-deep ledge.
        // ItemEntity's random spawn impulse can otherwise throw it off the fixture.
        world.getServer().runOnServer(server->{var drop=new net.minecraft.entity.ItemEntity(server.getOverworld(),origin.getX()+3.5,origin.getY()+2,origin.getZ()+7.5,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS));drop.setVelocity(Vec3d.ZERO);drop.setPickupDelay(0);server.getOverworld().spawnEntity(drop);});
        teleport(world,origin.add(3,1,3));context.waitTicks(12);
        require(world.getServer().computeOnServer(server->server.getOverworld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(origin.getX()+2,origin.getY()+2,origin.getZ()+7,origin.getX()+5,origin.getY()+3,origin.getZ()+8),drop->drop.getStack().isOf(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)).size()==1),"Sealed repair drop did not remain on its intended upper ledge");
        context.runOnClient(client->{
            require(builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)==1,"Stocked repair-drop fixture has the wrong initial inventory");
            var walker=(BuilderWalk)field(builder,"walker");require(!walker.canReachStand(origin.add(3,2,7)),"Sealed drop fixture already had walking access");
            setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",false);setting(builder,"Auto Buy When Missing",false);builder.install(new Schematic("sealed-repair-drop.nbt","test",7,5,8,BlockPos.ORIGIN,cells));builder.setOrigin(origin);
            @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");owners.put(opening,owner);
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            require((boolean)call(builder,"collectAccessDrop",new Class<?>[]{}),"Sealed native drop did not start a pickup intent");
            require(field(builder,"accessPickupSearch")!=null,"Sealed native drop did not need a routed pickup");
            int dropId=(int)field(builder,"accessPickupId");
            call(builder,"repositionTarget",new Class<?>[]{BlockPos.class,Map.class},opening,new HashMap<BlockPos,Integer>());
            @SuppressWarnings("unchecked")var searches=(Map<Object,Object>)field(builder,"viewSearches");
            var receipts=new LinkedHashMap<>(searches);require(!receipts.isEmpty(),"Native roof planning did not retain a partial route receipt");
            setField(builder,"accessPickupUntil",field(builder,"ticks"));
            require(!(boolean)call(builder,"collectAccessDrop",new Class<?>[]{}),"Expired pickup kept its access intent");
            require((int)field(builder,"accessPickupId")==-1&&receipts.entrySet().stream().allMatch(entry->searches.get(entry.getKey())==entry.getValue()),"Expired pickup erased the unfinished native roof route and restarted planning");
            require(client.world.getEntityById(dropId) instanceof net.minecraft.entity.ItemEntity drop&&drop.isAlive()&&builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)==1,"Expired pickup forged or removed the real repair material");
            // Resume the deliberately expired fixture attempt immediately; the
            // complete native pickup/repair flow below still has to finish.
            ((Map<?,?>)field(builder,"accessPickupRetry")).remove(dropId);
            var travel=origin.add(3,1,1);require(walker.canReachStand(travel),"Repair pickup priority fixture has no native travel route");
            setField(builder,"standGoal",travel);setField(builder,"standStarted",field(builder,"ticks"));setField(builder,"standProgressAt",field(builder,"ticks"));setField(builder,"standProgressPos",client.player.getEntityPos());
            System.out.println("[builder-home] Expired native ledge pickup retained the unfinished roof route; real drop and inventory unchanged");
        });
        context.waitTick();
        context.runOnClient(client->require((int)field(builder,"accessPickupId")>=0&&field(builder,"accessPickupSearch")!=null&&field(builder,"standGoal")==null,
            "Stocked repair material did not preempt the unrelated native walk on the next tick"));
        boolean planned=false,secured=false;int elapsed=0;
        for(;elapsed<2000&&context.computeOnClient(client->builder.building());elapsed++){
            planned|=context.computeOnClient(client->field(builder,"accessPickupSearch")!=null);
            if(!secured&&context.computeOnClient(client->builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)>1)){
                require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(ownerPos).isAir()),"Repair material was not secured before its unfinished owner");
                world.getServer().runCommand("give @a glass 1");secured=true;
            }
            context.waitTick();
        }
        require(planned&&secured,"Sealed repair drop never secured material through native pickup access before its owner: planned="+planned+", secured="+secured+", "+context.computeOnClient(client->builder.status()+" at "+client.player.getEntityPos()));
        require(world.getServer().computeOnServer(server->{var w=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(w.getBlockState(origin.add(i%7,i/56,i/7%8)),cells[i]))return false;for(int y=0;y<8;y++)for(int z=-3;z<11;z++)for(int x=-3;x<10;x++)if(w.getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Sealed repair drop access did not collect and restore every block/temporary post");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Sealed drop left work, supports, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("sealed drop checked");setting(builder,"Restock When Empty",true);setting(builder,"Auto Buy When Missing",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+6)+" "+(origin.getY()+4)+" "+(origin.getZ()+7)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Sealed upper repair drop collected through native access; all openings restored, zero dirt, full health and bounded look in "+elapsed+" ticks");
        System.out.println("[builder-home] Mined native repair drop collected despite spare matching inventory; unrelated walk preempted on the next tick; unfinished owner waited for real pickup");
    }
    private static void unproductiveHomeEscape(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Leave an exhausted enclosed route through safe storage home 1, then complete the next native placement");
        var room=start.add(-15,0,-5);var target=start.south(4);var abandoned=start.north(2);
        world.getServer().runCommand("fill "+room.down().toShortString().replace(",","")+" "+room.add(2,2,2).toShortString().replace(",","")+" bedrock");
        command(world,"setblock",room.add(1,0,1),"air");command(world,"setblock",room.add(1,1,1),"air");command(world,"setblock",target,"air");
        for(int y=0;y<3;y++)command(world,"setblock",abandoned.up(y),"dirt");
        world.getServer().runCommand("give @a stone 1");teleport(world,room.add(1,0,1));context.waitTicks(12);int first=commands.size(),savesBefore=saveCommands,deletesBefore=deleteCommands;Home secondBefore=saved[1],thirdBefore=saved[2];
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("exhausted-enclosed-route.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var posts=(Set<BlockPos>)field(builder,"supports");
            @SuppressWarnings("unchecked")var escape=(Set<BlockPos>)field(builder,"escapeSupports");
            @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"escapeSupportWork");
            for(int y=0;y<3;y++){var post=abandoned.up(y);posts.add(post);escape.add(post);owners.put(post,0);
                require((boolean)call(builder,"servesActiveScaffold",new Class<?>[]{BlockPos.class},post),"Escape column lost protection before native storage arrival");}
            setField(builder,"navigatingCell",0);((Map<Integer,Integer>)field(builder,"navigationWorkTicks")).put(0,359);
        });
        boolean retiredBeforePlacement=false;int elapsed=0;
        for(;elapsed<400&&context.computeOnClient(client->builder.building());elapsed++){
            retiredBeforePlacement|=context.computeOnClient(client->{var homes=(BuilderHomes)field(builder,"homes");var escape=(Set<?>)field(builder,"escapeSupports");
                if(homes.busy()&&client.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(room.add(1,0,1)))<1)
                    require(escape.contains(abandoned),"Escape column retired while native home travel was still pending");
                return !escape.contains(abandoned)&&builder.state(0)!=AutoBuilder.CORRECT&&client.world.getBlockState(abandoned).isOf(Blocks.DIRT);});
            context.waitTick();
        }
        require(retiredBeforePlacement,"Confirmed storage arrival kept old escape columns protected until their unfinished owner completed");
        require(world.getServer().computeOnServer(server->{for(int y=0;y<3;y++)if(!server.getOverworld().getBlockState(abandoned.up(y)).isAir())return false;return true;}),"Retired escape column was removed from the ledger instead of being mined natively");
        require(world.getServer().computeOnServer(server->{var w=server.getOverworld();if(!w.getBlockState(target).isOf(Blocks.STONE))return false;for(var pos:BlockPos.iterate(room.down(),room.add(2,2,2)))if(!pos.equals(room.add(1,0,1))&&!pos.equals(room.add(1,1,1))&&!w.getBlockState(pos).isOf(Blocks.BEDROCK))return false;return true;}),"Exhausted route did not complete the native placement or damaged its enclosure");
        require(commands.subList(first,commands.size()).equals(List.of("home 1"))&&saveCommands==savesBefore&&deleteCommands==deletesBefore&&saved[1]==secondBefore&&saved[2]==thirdBefore,"Exhausted route changed existing homes or repeatedly teleported: "+commands.subList(first,commands.size()));
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Storage escape left work, supports or damage");BuilderPacketChecks.verify(1);builder.pause("storage escape checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+room.down().toShortString().replace(",","")+" "+room.add(2,2,2).toShortString().replace(",","")+" air");world.getServer().runCommand("fill "+room.down().toShortString().replace(",","")+" "+room.add(2,-1,2).toShortString().replace(",","")+" end_stone");command(world,"setblock",target,"air");context.waitTicks(4);
        System.out.println("[builder-home] Exhausted route escaped through one native home 1 arrival and completed placement in "+elapsed+" ticks; enclosure/other homes intact, zero supports and full health");
        System.out.println("[builder-home] Confirmed native storage arrival retired abandoned escape columns before owner placement; pending travel retained protection; real three-block cleanup completed");
    }
    private static void missingMaterialBeforeAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var origin=start.south(24);var cells=new BlockState[27];Arrays.fill(cells,Blocks.STONE.getDefaultState());cells[13]=Blocks.GLASS.getDefaultState();
        for(int i=0;i<cells.length;i++)command(world,"setblock",origin.add(i%3,i/9,i/3%3),i==13?"air":"stone");
        world.getServer().runCommand("clear @a glass");teleport(world,start);context.waitTicks(12);int first=commands.size();
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",false);setting(builder,"Auto Buy When Missing",false);
            builder.install(new Schematic("missing-material-before-access.nbt","test",3,3,3,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.startBuild();});
        context.waitTicks(40);
        context.runOnClient(client->{require(builder.inventoryCount(Items.GLASS)==0&&field(builder,"needed")==Items.GLASS&&(int)field(builder,"navigatingCell")==-1&&field(builder,"standGoal")==null,
                "Missing native glass started a distant access route instead of requesting stock");
            require(client.player.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(start))<.04&&builder.temporarySupports().isEmpty()&&((Map<?,?>)field(builder,"floorAccessWork")).isEmpty()&&client.player.getHealth()==20,
                "Missing material moved the player or created scaffold/access cuts");builder.pause("missing material checked");setting(builder,"Restock When Empty",true);setting(builder,"Auto Buy When Missing",true);});
        require(commands.size()==first,"Missing material issued an unrelated home command");
        require(world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!server.getOverworld().getBlockState(origin.add(i%3,i/9,i/3%3)).isOf(i==13?Blocks.AIR:Blocks.STONE))return false;return true;}),"Missing material damaged the native enclosed build");
        world.getServer().runCommand("fill "+origin.toShortString().replace(",","")+" "+origin.add(2,2,2).toShortString().replace(",","")+" air");context.waitTicks(4);
        System.out.println("[builder-home] Missing native material requested before distant travel; enclosed build intact, no access cuts/supports/home commands and full health");
    }
    private static void longCheckedWalk(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Finish a long proved native walk without cancelling it at the old 240-tick deadline");
        var from=start.north(10);var destination=from.east(58);var target=destination.south(2);
        world.getServer().runCommand("fill "+from.add(0,-1,-1).toShortString().replace(",","")+" "+destination.add(0,-1,2).toShortString().replace(",","")+" stone");
        world.getServer().runCommand("fill "+from.north().toShortString().replace(",","")+" "+destination.add(0,2,2).toShortString().replace(",","")+" air");
        world.getServer().runCommand("clear @a stone");world.getServer().runCommand("give @a stone 1");teleport(world,from);context.waitTicks(12);int first=commands.size();
        // The production solver deliberately yields after three milliseconds.
        // Let the native client warm/settle before requiring the full long-route proof.
        boolean proved=false;for(int tick=0;tick<100&&!proved;tick++){proved=context.computeOnClient(client->((BuilderWalk)field(builder,"walker")).canReachStand(destination));context.waitTick();}
        require(proved,"Long route fixture has no proved native walk");
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("long-checked-walk.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
            var walker=(BuilderWalk)field(builder,"walker");BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            setField(builder,"standGoal",destination);setField(builder,"standStarted",field(builder,"ticks"));setField(builder,"standProgressAt",field(builder,"ticks"));setField(builder,"standProgressPos",client.player.getEntityPos());setField(builder,"navigatingCell",0);
        });
        int elapsed=0;
        for(;elapsed<900&&context.computeOnClient(client->field(builder,"standGoal")!=null);elapsed++)context.waitTick();
        final int walked=elapsed;
        context.runOnClient(client->{var walker=(BuilderWalk)field(builder,"walker");require(walked>240&&client.player.getEntityPos().squaredDistanceTo(walker.standingPoint(destination))<.4*.4,"Long advancing route was cancelled before arrival after "+walked+" ticks: "+builder.status()+", allowance="+walker.routeTimeoutTicks()+", position="+client.player.getEntityPos());});
        for(int i=0;i<200&&context.computeOnClient(client->builder.building());i++)context.waitTick();
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(Blocks.STONE)),"Long route did not finish its native placement");require(commands.size()==first,"Long route used a home teleport");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Long route left active work, supports or damage");BuilderPacketChecks.verify(1);builder.pause("long walk checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);
        System.out.println("[builder-home] Long proved native walk retained past 240 ticks and finished its placement without a restart/home/support/damage in "+elapsed+" walking ticks");
    }
    private static void crouchedChestPlacementView(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.south(15).up(2);var footing=target.add(-1,0,2);var panel=footing.up();
        command(world,"setblock",target.down(),"chest[facing=north]");command(world,"setblock",footing,"chest[facing=south]");command(world,"setblock",panel,"oak_sign[rotation=8]");
        world.getServer().runCommand("tp @a "+(target.getX()-.369858265)+" "+(target.getY()+.875)+" "+(target.getZ()+2.48539117115));context.waitTicks(12);
        context.runOnClient(client->{
            var wanted=Blocks.OAK_SIGN.getDefaultState().with(net.minecraft.state.property.Properties.ROTATION,2);
            builder.install(new Schematic("crouched-chest-ray.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{wanted}));builder.setOrigin(target);
            var eye=client.player.getEyePos();var point=new Vec3d(target.getX()+.9,target.getY()-.125,target.getZ()+.9);
            var ray=client.world.raycast(new net.minecraft.world.RaycastContext(eye,point.add(point.subtract(eye).normalize().multiply(1.1)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
            require(ray.getBlockPos().equals(target.down())&&ray.getSide()==Direction.UP,"Crouch fixture lost its clear standing chest ray");
            var crouched=client.player.getEntityPos().add(0,client.player.getDimensions(net.minecraft.entity.EntityPose.CROUCHING).eyeHeight(),0);
            var blocked=client.world.raycast(new net.minecraft.world.RaycastContext(crouched,point.add(point.subtract(crouched).normalize().multiply(1.1)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
            require(blocked.getBlockPos().equals(panel),"Crouch fixture did not hit the existing sign panel");
            require(call(builder,"placement",new Class<?>[]{BlockPos.class,BlockState.class,Item.class,int.class,boolean.class},target,wanted,Items.OAK_SIGN,0,false)==null,"Standing-only chest placement accepted a blocked mandatory crouch ray");
            builder.pause("crouched chest ray checked");
        });
        teleport(world,start);context.waitTicks(12);for(var pos:List.of(target.down(),footing,panel))command(world,"setblock",pos,"air");
        System.out.println("[builder-home] Native standing chest ray rejected when mandatory crouch is blocked by an existing sign");
    }
    private static void scaffoldObstructedSign(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Clear an owned scaffold from a native rotated sign view, then clean up and preserve its chest/floor");
        var origin=start.south(9);var target=origin.add(2,1,2);var cells=new BlockState[100];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int z=0;z<5;z++)for(int x=0;x<5;x++){cells[z*5+x]=Blocks.STONE.getDefaultState();command(world,"setblock",origin.add(x,0,z),"stone");}
        cells[12]=Blocks.CHEST.getDefaultState();command(world,"setblock",target.down(),"chest");cells[37]=Blocks.OAK_SIGN.getDefaultState().with(net.minecraft.state.property.Properties.ROTATION,2);command(world,"setblock",target,"air");
        var owned=new LinkedHashSet<BlockPos>();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0){var post=target.add(dx,0,dz);owned.add(post);command(world,"setblock",post,"dirt");}
        owned.add(target.up());command(world,"setblock",target.up(),"dirt");
        var footing=origin.add(0,1,4);command(world,"setblock",footing,"stone");
        world.getServer().runCommand("clear @a oak_sign");world.getServer().runCommand("give @a oak_sign 1");teleport(world,footing.up());context.waitTicks(12);
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);setting(builder,"Restock When Empty",false);setting(builder,"Auto Buy When Missing",false);
            builder.install(new Schematic("scaffold-obstructed-sign.nbt","test",5,4,5,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var supports=(Set<BlockPos>)field(builder,"supports");supports.addAll(owned);
            require(call(builder,"placement",new Class<?>[]{BlockPos.class,BlockState.class,Item.class,int.class,boolean.class},target,cells[37],Items.OAK_SIGN,37,false)==null,"Sign fixture already had an unobstructed native placement");
            setField(builder,"navigatingCell",37);setField(builder,"navigationStarted",field(builder,"ticks"));
            require(call(builder,"ownedPlacementOpening",new Class<?>[]{BlockPos.class,BlockPos.class,Vec3d.class,net.minecraft.util.math.Box.class,int.class},target,client.player.getBlockPos(),client.player.getEyePos(),client.player.getBoundingBox(),37)!=null,"Active-job proximity protection hid the proved owned ray obstruction");
        });
        boolean cleared=false;int elapsed=0;
        for(;elapsed<1200&&context.computeOnClient(client->builder.building());elapsed++){
            cleared|=world.getServer().computeOnServer(server->owned.stream().anyMatch(post->server.getOverworld().getBlockState(post).isAir()));context.waitTick();
        }
        require(cleared&&world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(level.getBlockState(origin.add(i%5,i/25,i/5%5)),cells[i]))return false;return owned.stream().allMatch(post->level.getBlockState(post).isAir())&&level.getBlockState(footing).isOf(Blocks.STONE);}),"Owned scaffold blocked the sign or cleanup changed native build/footing: "+context.computeOnClient(client->builder.status()));
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Sign recovery left work, dirt, damage or a menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("scaffold-obstructed sign checked");setting(builder,"Restock When Empty",true);setting(builder,"Auto Buy When Missing",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+4)+" "+(origin.getY()+3)+" "+(origin.getZ()+4)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Rotated native sign placed after owned ray obstruction removal; chest/floor/footing intact, zero dirt and full health in "+elapsed+" ticks");
    }
    private static void receiverColumnClimb(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Continue a proved receiver column above the chest's own height without idle replanning");
        var previous=context.computeOnClient(client->{var values=new HashMap<String,com.google.gson.JsonElement>();for(var option:builder.getSettings())if(List.of("Temporary Supports","Prepare Whole Build").contains(option.getName()))values.put(option.getName(),option.toJson());return values;});
        var base=start.west(3).south(7);var top=base.up(2);var receiver=base.east(2);var opening=base.north(2);
        command(world,"setblock",receiver,"dispenser[facing=west]");command(world,"setblock",opening,"air");
        world.getServer().runOnServer(server->{var stock=(net.minecraft.inventory.Inventory)server.getOverworld().getBlockEntity(receiver);stock.setStack(0,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS));stock.setStack(1,new ItemStack(Items.DIAMOND,7));stock.markDirty();});
        world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");world.getServer().runCommand("give @a dirt 4");teleport(world,base);context.waitTicks(12);int first=commands.size();
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);
            builder.install(new Schematic("receiver-column-height.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()}));builder.setOrigin(opening);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");owners.put(opening,-1);
            @SuppressWarnings("unchecked")var sources=(Map<Item,Set<BlockPos>>)field(builder,"accessStockSources");sources.put(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,new LinkedHashSet<>(List.of(receiver)));
            setField(builder,"needed",Items.CRACKED_POLISHED_BLACKSTONE_BRICKS);require((boolean)call(builder,"beginRestock",new Class<?>[]{}),"Native receiver recovery did not begin");
            require(receiver.equals(field(builder,"restockTarget"))&&(boolean)field(builder,"recoveringAccessStock"),"Column fixture selected a different receiver");
            var walker=(BuilderWalk)field(builder,"walker");require(walker.canClimbAfterClearing(base,top,top,Set.of()),"Native receiver column lacks a checked climb");
            var eye=Vec3d.ofBottomCenter(top).add(0,client.player.getStandingEyeHeight(),0);require(call(builder,"chestHit",new Class<?>[]{BlockPos.class,Vec3d.class},receiver,eye)!=null,"Column top has no native receiver ray");
            setField(builder,"navigatingCell",0);call(builder,"commitColumnAccess",new Class<?>[]{BlockPos.class,BlockPos.class},base,top);walker.requestRecovery();
        });
        int lift=0;boolean climbed=false;
        for(;lift<80;lift++){
            climbed=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(base).isOf(Blocks.DIRT))&&context.computeOnClient(client->client.player.getY()>base.getY()+.8);
            if(climbed)break;context.waitTick();
        }
        require(climbed,"Committed receiver column waited instead of climbing above chest height: "+context.computeOnClient(client->builder.status()));
        int elapsed=lift;for(;elapsed<800&&context.computeOnClient(client->builder.building());elapsed++)context.waitTick();
        require(world.getServer().computeOnServer(server->{var level=server.getOverworld();var stock=(net.minecraft.inventory.Inventory)level.getBlockEntity(receiver);if(!level.getBlockState(opening).isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)||!stock.getStack(0).isEmpty()||!stock.getStack(1).isOf(Items.DIAMOND)||stock.getStack(1).getCount()!=7||!level.getBlockState(base.down()).isOf(Blocks.STONE))return false;for(var pos:BlockPos.iterate(base.add(-3,0,-3),base.add(3,4,3)))if(level.getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Receiver column failed real stock recovery, hole restoration or native cleanup");
        require(commands.size()==first,"Local receiver column used a storage home");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Receiver column left work, dirt, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("receiver column checked");for(var option:builder.getSettings())if(previous.containsKey(option.getName()))option.fromJson(previous.get(option.getName()));});
        teleport(world,start);context.waitTicks(12);command(world,"setblock",receiver,"air");command(world,"setblock",opening,"air");context.waitTicks(4);
        System.out.println("[builder-home] Native receiver column lifted above chest height in "+lift+" ticks; actual stock patched the hole, diamonds/footing preserved, zero dirt and full health in "+elapsed+" ticks");
    }
    private static void blockedRepairReceivers(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,BlockPos chest){
        System.out.println("[builder-home] Skip two unreachable repair receivers and patch the hole from actual selected storage");
        var origin=start.south(7);var first=origin.east(5).up(3);var second=first.south(3);
        for(var receiver:List.of(first,second)){
            command(world,"setblock",receiver,"dispenser");
            for(var direction:Direction.values())command(world,"setblock",receiver.offset(direction),"bedrock");
        }
        world.getServer().runOnServer(server->{
            var level=server.getOverworld();var stock=(net.minecraft.inventory.Inventory)level.getBlockEntity(chest);stock.clear();stock.setStack(0,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS));stock.markDirty();
            var caught=(net.minecraft.inventory.Inventory)level.getBlockEntity(first);caught.setStack(0,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS));caught.markDirty();
        });
        command(world,"setblock",origin,"air");world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");teleport(world,start);context.waitTicks(12);
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);
            builder.install(new Schematic("blocked-repair-receivers.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()}));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(origin,-1);
            @SuppressWarnings("unchecked")var sources=(Map<Item,Set<BlockPos>>)field(builder,"accessStockSources");sources.put(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,new LinkedHashSet<>(List.of(first,second)));
        });
        var attempted=new HashSet<BlockPos>();int elapsed=0;
        for(;elapsed<1000&&context.computeOnClient(client->builder.building());elapsed++){
            var target=context.computeOnClient(client->(BlockPos)field(builder,"restockTarget"));if(target!=null)attempted.add(target);context.waitTick();
        }
        require(attempted.containsAll(List.of(first,second,chest)),"Receiver search starved native selected storage: "+attempted+"; "+context.computeOnClient(client->builder.status()));
        require(world.getServer().computeOnServer(server->{var level=server.getOverworld();return level.getBlockState(origin).isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)&&((net.minecraft.inventory.Inventory)level.getBlockEntity(first)).getStack(0).getCount()==1&&((net.minecraft.inventory.Inventory)level.getBlockEntity(chest)).getStack(0).isEmpty();}),"Storage did not patch the hole or unreachable caught stock changed");
        context.runOnClient(client->{
            require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Blocked receiver repair left work, supports, damage or menu: "+builder.status());
            @SuppressWarnings("unchecked")var sources=(Map<Item,Set<BlockPos>>)field(builder,"accessStockSources");require(sources.get(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS).containsAll(List.of(first,second)),"Failed access discarded possible caught material");
            @SuppressWarnings("unchecked")var empty=(Map<BlockPos,Set<Item>>)field(builder,"emptyChestItems");for(var receiver:List.of(first,second))require(!empty.getOrDefault(receiver,Set.of()).contains(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS),"Unopened receiver was declared empty");
            BuilderPacketChecks.verify(1);builder.pause("blocked repair receivers checked");setting(builder,"Temporary Supports",true);
        });
        teleport(world,start);context.waitTicks(12);command(world,"setblock",origin,"air");
        for(var receiver:List.of(first,second)){command(world,"setblock",receiver,"air");for(var direction:Direction.values())command(world,"setblock",receiver.offset(direction),"air");}
        context.waitTicks(4);System.out.println("[builder-home] Both blocked receivers retired fairly; native storage patched the hole, unopened stock retained and full health in "+elapsed+" ticks");
    }
    private static void offsetRepairReceiver(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Recover deflected repair stock through an adjacent native hopper column");
        var origin=start.south(10);var cells=new BlockState[18];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        cells[0]=Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING,Direction.WEST);cells[1]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.WEST);
        cells[4]=cells[11]=Blocks.STONE.getDefaultState();cells[17]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();var opening=origin.add(2,2,1);
        for(int i=0;i<cells.length;i++)command(world,"setblock",origin.add(i%3,i/6,i/3%2),i==17||cells[i].isOf(Blocks.STRUCTURE_VOID)?"air":i==0?"dispenser[facing=west]":i==1?"hopper[facing=west]":"stone");
        world.getServer().runOnServer(server->{var stock=(net.minecraft.inventory.Inventory)server.getOverworld().getBlockEntity(origin);stock.setStack(0,new ItemStack(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS,5));stock.setStack(1,new ItemStack(Items.DIAMOND,7));stock.markDirty();});
        world.getServer().runCommand("clear @a cracked_polished_blackstone_bricks");teleport(world,origin.add(1,1,1));context.waitTicks(12);int first=commands.size();
        context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("offset-repair-receiver.nbt","test",3,3,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();@SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");owners.put(opening,-1);});
        boolean recovered=false;int elapsed=0;
        for(;elapsed<600&&context.computeOnClient(client->builder.building());elapsed++){recovered|=context.computeOnClient(client->(boolean)field(builder,"recoveringAccessStock"));context.waitTick();}
        require(recovered,"Adjacent fall column was not searched for repair stock");
        require(world.getServer().computeOnServer(server->{var w=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(w.getBlockState(origin.add(i%3,i/6,i/3%2)),cells[i]))return false;var stock=(net.minecraft.inventory.Inventory)w.getBlockEntity(origin);return stock.getStack(0).isOf(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)&&stock.getStack(0).getCount()==4&&stock.getStack(1).isOf(Items.DIAMOND)&&stock.getStack(1).getCount()==7;}),"Adjacent receiver recovery failed repair or changed original stock");
        require(commands.size()==first,"Adjacent repair receiver used storage homes");
        context.runOnClient(client->{require(!builder.building()&&builder.inventoryCount(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS)==0&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Adjacent receiver left material, supports, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("offset receiver checked");setting(builder,"Temporary Supports",true);});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+2)+" "+(origin.getY()+2)+" "+(origin.getZ()+1)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Adjacent native hopper/dispenser stock repaired the opening; original four bricks/seven diamonds intact, no home trip, full health in "+elapsed+" ticks");
    }
    private static void raisedLiquidEntrance(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-home] Enter above a liquid view, retain the wall footing, then descend and restore the entrance");
        var origin=start.add(-4,4,10);var cells=new BlockState[9*6*9];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<6;y++)for(int z=0;z<9;z++)for(int x=0;x<9;x++){
            boolean rim=x==0||x==8||z==0||z==8;
            // Leave both lower rim cells empty. A floor under the rim would
            // permit a lower doorway and fail to exercise the higher entrance.
            boolean solid=(y==0||y==1)&&!rim||y==5||y>=2&&y<=4&&rim;
            if(solid)cells[(y*9+z)*9+x]=Blocks.STONE.getDefaultState();
            command(world,"setblock",origin.add(x,y,z),solid?"stone":"air");
        }
        int target=(1*9+4)*9+4;cells[target]=Blocks.WATER.getDefaultState();command(world,"setblock",origin.add(4,1,4),"air");
        for(String item:List.of("water_bucket","stone 16","dirt 64","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        teleport(world,start.south(8));context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",true);setting(builder,"Prepare Whole Build",false);builder.install(new Schematic("raised-liquid-entrance.nbt","test",9,6,9,BlockPos.ORIGIN,cells));builder.setOrigin(origin);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();});
        boolean raised=false,budgetAged=false;int elapsed=0;
        for(;elapsed<2400&&context.computeOnClient(client->builder.building());elapsed++){
            raised|=context.computeOnClient(client->{var top=(BlockPos)field(builder,"entryPassageTop");var destination=(BlockPos)field(builder,"entryPassageDestination");return top!=null&&destination!=null&&top.getY()>destination.getY();});
            if(raised&&!budgetAged){context.runOnClient(client->{@SuppressWarnings("unchecked")var spent=(Map<Integer,Integer>)field(builder,"navigationWorkTicks");spent.put(target,359);});budgetAged=true;}
            require(world.getServer().computeOnServer(server->{var w=server.getOverworld();for(int z=-1;z<10;z++)for(int x=-1;x<10;x++){var p=origin.add(x,1,z);if(!p.equals(origin.add(4,1,4))&&!w.getFluidState(p).isEmpty())return false;}return true;}),"Raised liquid entrance leaked through its retaining blocks");
            context.waitTick();
        }
        require(raised,"Liquid entry did not exercise a higher exterior entrance: "+context.computeOnClient(client->builder.status()));
        require(world.getServer().computeOnServer(server->{var w=server.getOverworld();for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(w.getBlockState(origin.add(i%9,i/81,i/9%9)),cells[i]))return false;for(int y=-4;y<10;y++)for(int z=-5;z<14;z++)for(int x=-5;x<14;x++)if(w.getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Raised liquid placement or entrance restoration/cleanup failed");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Raised liquid entry left work, supports, damage or menu: "+builder.status());BuilderPacketChecks.verify(1);builder.pause("raised liquid entrance checked");});
        teleport(world,start);context.waitTicks(12);world.getServer().runCommand("fill "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+" "+(origin.getX()+8)+" "+(origin.getY()+5)+" "+(origin.getZ()+8)+" air");context.waitTicks(4);
        System.out.println("[builder-home] Higher liquid entrance, native descent and contained source; all retaining/entry blocks restored, zero dirt, full health and bounded look in "+elapsed+" ticks");
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
            // Observe placement and movement in one authoritative server task.
            // Separate server/client tasks can straddle the successful place
            // and wrongly reject the legitimate next walk on a slower runner.
            placed=world.getServer().computeOnServer(server->{
                boolean done=server.getOverworld().getBlockState(ready).isOf(Blocks.STONE);
                if(!done)require(server.getPlayerManager().getPlayerList().getFirst().getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(work))<.0004,"Started access movement while another full cube was immediately placeable");
                return done;
            });if(placed)break;context.waitTick();
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
    private static void repairOwnerOrder(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        String previousSupply=builder.sectionSupply()?"Nearby Sections":builder.layerSupply()?"Layer by Layer":"Whole Schematic";
        var origin=work.south(2);for(int x:new int[]{0,1,4})command(world,"setblock",origin.east(x),"air");command(world,"setblock",origin.east(2),"stone");world.getServer().runCommand("give @a stone 3");teleport(world,work);context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Temporary Supports",false);setting(builder,"Restock When Empty",false);setting(builder,"Stockpile In Chests",false);setting(builder,"Prepare Whole Build",false);setting(builder,"Material Supply","Whole Schematic");
            builder.install(new Schematic("repair-owner-before-dependent.nbt","test",5,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STRUCTURE_VOID.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(origin);builder.preview();});context.waitTicks(12);
        context.runOnClient(client->{require(builder.state(2)==AutoBuilder.CORRECT,"Repair owner fixture did not establish its completed owner");
            @SuppressWarnings("unchecked")var repairs=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");repairs.put(origin,2);repairs.put(origin.east(),0);
            @SuppressWarnings("unchecked")var depths=(Map<BlockPos,Integer>)field(builder,"openingRepairDepth");depths.put(origin,1);depths.put(origin.east(),2);
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var retries=(Map<Integer,Integer>)field(builder,"retryAt");retries.put(4,(int)field(builder,"ticks")+120);});
        boolean repaired=false;int elapsed=0;
        for(;elapsed<80;elapsed++){
            var pair=world.getServer().computeOnServer(server->List.of(server.getOverworld().getBlockState(origin).isOf(Blocks.STONE),server.getOverworld().getBlockState(origin.east()).isOf(Blocks.STONE)));
            require(!pair.get(1)||pair.get(0),"Dependent repair closed before its unfinished owner");if(pair.get(0)&&pair.get(1)){repaired=true;break;}context.waitTick();
        }
        require(repaired,"Deeper opening waited on the block it was opened to repair: "+context.computeOnClient(client->builder.status()));
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(origin.east(4)).isAir()),"Repair owner fixture did not retain unrelated unfinished work");
        context.runOnClient(client->((Map<?,?>)field(builder,"retryAt")).remove(4));
        for(int tick=0;tick<300&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
        require(world.getServer().computeOnServer(server->{for(int x:new int[]{0,1,2,4})if(!server.getOverworld().getBlockState(origin.east(x)).isOf(Blocks.STONE))return false;return true;}),"Repair owner fixture did not finish all native work");
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Repair owner order left work, supports, damage or menu");BuilderPacketChecks.verify(3);builder.pause("repair owner order checked");setting(builder,"Temporary Supports",true);setting(builder,"Restock When Empty",true);setting(builder,"Stockpile In Chests",true);setting(builder,"Material Supply",previousSupply);});
        teleport(world,work);context.waitTicks(12);for(int x:new int[]{0,1,2,4})command(world,"setblock",origin.east(x),"air");context.waitTicks(4);
        System.out.println("[builder-home] Ready owner repaired before its deeper dependent access opening in "+elapsed+" ticks; unrelated work stayed deferred, then all native blocks completed; full health and zero supports");
    }
    private static void repairDependencyCycles(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        repairDependencyCycle(context,world,builder,work,false);repairDependencyCycle(context,world,builder,work,true);
    }
    private static void repairDependencyCycle(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work,boolean attachment){
        var previous=context.computeOnClient(client->{var values=new HashMap<String,com.google.gson.JsonElement>();for(var option:builder.getSettings())if(List.of("Temporary Supports","Restock When Empty","Auto Buy Tools","Stockpile In Chests","Prepare Whole Build","Material Supply").contains(option.getName()))values.put(option.getName(),option.toJson());return values;});
        var origin=work.south(2);var post=work.west(10).north(6).up(3);var cells=new BlockState[20];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(post.down(4)).isOf(Blocks.STONE)),"Repair-cycle cleanup post is outside the native test platform");
        cells[4]=Blocks.OBSERVER.getDefaultState();cells[0]=attachment?Blocks.BLACKSTONE.getDefaultState():Blocks.STONE.getDefaultState();
        if(attachment){cells[10]=Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();cells[11]=Blocks.STONE.getDefaultState();cells[15]=Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING,Direction.SOUTH);}
        else cells[2]=Blocks.BLACKSTONE.getDefaultState();
        for(var pos:BlockPos.iterate(origin,origin.add(4,1,1)))command(world,"setblock",pos,"air");
        if(attachment)command(world,"setblock",origin.east().up(),"stone");
        command(world,"setblock",post,"dirt");
        for(String item:List.of("stone","blackstone","cracked_polished_blackstone_bricks","torch","observer"))world.getServer().runCommand("clear @a "+item);
        world.getServer().runCommand("give @a blackstone 1");
        if(attachment){world.getServer().runCommand("give @a cracked_polished_blackstone_bricks 1");world.getServer().runCommand("give @a torch 1");}
        else world.getServer().runCommand("give @a stone 1");
        teleport(world,work.west(12));context.waitTicks(12);
        context.runOnClient(client->{
            setting(builder,"Temporary Supports",true);setting(builder,"Restock When Empty",false);setting(builder,"Auto Buy Tools",false);setting(builder,"Stockpile In Chests",false);setting(builder,"Prepare Whole Build",false);setting(builder,"Material Supply","Nearby Sections");
            builder.install(new Schematic("repair-dependency-cycle.nbt","test",5,2,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.preview();
        });context.waitTicks(15);
        context.runOnClient(client->{
            @SuppressWarnings("unchecked")var repairs=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");
            @SuppressWarnings("unchecked")var depths=(Map<BlockPos,Integer>)field(builder,"openingRepairDepth");
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(post);
            var other=attachment?origin.up():origin.east(2);repairs.put(origin,attachment?15:2);repairs.put(other,attachment?15:0);depths.put(origin,1);depths.put(other,2);
            require((boolean)call(builder,"repairDependencyCycle",new Class<?>[]{BlockPos.class},other),"Missing recorded repair cycle");
            require(!(boolean)call(builder,"floorDeferred",new Class<?>[]{BlockPos.class},other),"Cyclic repair still waits for its impossible owner");
            if(attachment)require((boolean)call(builder,"floorDeferred",new Class<?>[]{BlockPos.class},origin),"Non-cyclic lower opening closed before its torch owner");
            @SuppressWarnings("unchecked")var passage=(Set<BlockPos>)field(builder,"passageBlocks");passage.add(other);setField(builder,"passageStand",client.player.getBlockPos());
            require((boolean)call(builder,"floorDeferred",new Class<?>[]{BlockPos.class},other),"Cycle repair closed an unfinished proved passage");passage.clear();setField(builder,"passageStand",null);
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
        });
        boolean repaired=false,anchorSeen=false;int elapsed=0;
        for(;elapsed<180;elapsed++){
            var nativeState=world.getServer().computeOnServer(server->{var level=server.getOverworld();return List.of(level.getBlockState(origin).isOf(cells[0].getBlock()),level.getBlockState(attachment?origin.up():origin.east(2)).isOf(attachment?Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS:Blocks.BLACKSTONE),!attachment||AutoBuilder.matchesBuildState(level.getBlockState(origin.up().south()),cells[15]));});
            if(attachment&&!anchorSeen&&nativeState.get(1)){require(!nativeState.get(0)&&!nativeState.get(2),"Torch attachment was not repaired before its deferred lower opening and owner");anchorSeen=true;}
            if(nativeState.stream().allMatch(Boolean::booleanValue)){repaired=true;break;}context.waitTick();
        }
        require(repaired&&(!attachment||anchorSeen),"Native dependency cycle did not repair: "+context.computeOnClient(client->builder.status()));
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(post).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(origin.east(4)).isAir()),"Cycle repairs waited for cleanup or completed unrelated work without its material");
        world.getServer().runCommand("give @a observer 1");
        for(int tick=0;tick<900&&context.computeOnClient(client->builder.building());tick++){
            if(tick%100==0)System.out.println("[builder-cycle] "+context.computeOnClient(client->"status="+builder.status()+" player="+client.player.getEntityPos()+" phase="+field(builder,"activePhase")+" section="+field(builder,"sectionCells")+" needed="+field(builder,"needed")+" supports="+builder.temporarySupports()));
            context.waitTick();
        }
        String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<cells.length;i++)if(!cells[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(i%5,i/10,i/5%2)),cells[i]))return i+"="+server.getOverworld().getBlockState(origin.add(i%5,i/10,i/5%2));return server.getOverworld().getBlockState(post).isAir()?"":"post="+server.getOverworld().getBlockState(post);});
        require(mismatch.isEmpty(),"Cycle repair left "+mismatch+"; "+context.computeOnClient(client->builder.status()));
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Cycle repair left work, dirt, damage or menu: "+builder.status());BuilderPacketChecks.verify(attachment?4:3);builder.pause("repair dependency cycle checked");for(var option:builder.getSettings())if(previous.containsKey(option.getName()))option.fromJson(previous.get(option.getName()));});
        teleport(world,work);context.waitTicks(12);for(var pos:BlockPos.iterate(origin,origin.add(4,1,1)))command(world,"setblock",pos,"air");context.waitTicks(4);
        System.out.println("[builder-home] "+(attachment?"Native torch attachment cycle repaired prerequisite, then owner and lower hole":"Native mutual repair-owner cycle closed both holes")+" before scaffold cleanup in "+elapsed+" ticks; missing unrelated material respected, all cells/posts completed, full health and bounded look");
    }
    private static void sectionDependencyChain(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos work){
        var first=work.south(2);var origin=first.west(6);var parent=first.east(2);var cells=new BlockState[9];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        cells[5]=Blocks.STONE.getDefaultState();cells[6]=cells[7]=Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.EAST);cells[8]=Blocks.STONE.getDefaultState();
        for(int x=-1;x<3;x++)command(world,"setblock",first.east(x),"air");world.getServer().runCommand("give @a hopper 2");world.getServer().runCommand("give @a stone 2");teleport(world,work);context.waitTicks(12);
        context.runOnClient(client->{setting(builder,"Material Supply","Nearby Sections");builder.install(new Schematic("section-hopper-chain.nbt","test",9,1,1,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.preview();});context.waitTicks(15);
        context.runOnClient(client->{
            setField(builder,"sectionCells",List.of(6,7));setField(builder,"sectionProgressAt",field(builder,"ticks"));setField(builder,"sectionCorrect",field(builder,"correct"));
            require((boolean)call(builder,"waitingForBuiltNeighbour",new Class<?>[]{BlockPos.class,BlockState.class},first,cells[6]),"Section fixture did not need its outlet chain");
            BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
            @SuppressWarnings("unchecked")var retries=(Map<Integer,Integer>)field(builder,"retryAt");retries.put(8,(int)field(builder,"ticks")+120);
        });
        boolean otherPlaced=false;
        for(int tick=0;tick<80;tick++){otherPlaced=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(first.west()).isOf(Blocks.STONE));if(otherPlaced)break;context.waitTick();}
        require(otherPlaced&&world.getServer().computeOnServer(server->server.getOverworld().getBlockState(parent).isAir()),"A deferred outlet pinned its dependants instead of selecting other native work: "+context.computeOnClient(client->builder.status()));
        boolean finished=false;
        for(int tick=0;tick<160;tick++){
            finished=world.getServer().computeOnServer(server->AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(parent),cells[8])&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(first.east()),cells[7])&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(first),cells[6]));
            if(finished)break;context.waitTick();
        }
        require(finished,"Nearby section waited for a missing outlet outside its batch: "+context.computeOnClient(client->builder.status()));
        for(int tick=0;tick<80&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
        context.runOnClient(client->{require(!builder.building()&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Section dependency chain left work, scaffolds or damage: "+builder.status());BuilderPacketChecks.verify(4);builder.pause("section dependencies checked");setting(builder,"Temporary Supports",true);setting(builder,"Restock When Empty",true);setting(builder,"Stockpile In Chests",true);});
        teleport(world,work);context.waitTicks(12);for(int x=-1;x<3;x++)command(world,"setblock",first.east(x),"air");context.waitTicks(4);
        System.out.println("[builder-home] Deferred hopper outlet selected other native work immediately; released outlet chain crossed the eight-cell batch boundary and completed; full health and zero supports");
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
        context.runOnClient(client->{builder.pause("brisk head check");client.player.setYaw(0);client.player.setPitch(0);});
        boolean quickReady=false;int quickTicks=0;
        for(;quickTicks<15;quickTicks++){
            quickReady=context.computeOnClient(client->(boolean)call(builder,"aim",new Class<?>[]{Vec3d.class},Vec3d.ofCenter(start.east(3))));
            context.waitTick();if(quickReady)break;
        }
        require(quickReady,"Native visible 90-degree aim still takes more than 15 ticks");
        System.out.println("[builder-camera] Brisk native 90-degree visible aim settled in "+(quickTicks+1)+" ticks; action waited for the visible head");
        context.runOnClient(client->builder.pause("brisk head checked"));
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
        var upper=target.up(3);command(world,"setblock",upper,"dirt");command(world,"setblock",beam.up(),"stone");context.waitTicks(4);
        var beforePeek=context.computeOnClient(client->client.player.getEntityPos());
        for(int i=0;i<20;i++){context.runOnClient(client->{require(call(builder,"visibleHit",new Class<?>[]{BlockPos.class},upper)==null,"Upper peek fixture did not hide its post");require(!(boolean)call(builder,"beginPeek",new Class<?>[]{BlockPos.class},upper)&&!client.options.sneakKey.isPressed(),"Inaccessible upper post started blind sneak edging");});context.waitTick();}
        context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(beforePeek)<.0001,"Blind upper peek moved the route's starting feet"));command(world,"setblock",upper,"air");command(world,"setblock",beam.up(),"air");context.waitTicks(4);
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

