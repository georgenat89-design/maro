package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.builder.*;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.*;

/** Real import fixtures and server-verified survival placement/restocking, not client predictions. */
final class AutoBuilderChecks {
    static void imports(){
        try{
            var palette=new NbtCompound();palette.putInt("minecraft:air",0);palette.putInt("minecraft:stone",1);
            for(int version:new int[]{2,3}){
                var n=new NbtCompound();n.putInt("Version",version);n.putShort("Width",(short)2);n.putShort("Height",(short)1);n.putShort("Length",(short)1);n.putIntArray("Offset",new int[]{-2,3,4});
                var data=new NbtCompound();data.put("Palette",palette.copy());data.putByteArray(version==3?"Data":"BlockData",new byte[]{1,0});
                if(version==3)n.put("Blocks",data);else n.copyFrom(data);
                var s=SchematicIO.decode("fixture.schem",n);require(s.size()==2&&s.state(0).isOf(Blocks.STONE)&&s.state(1).isAir()&&s.offset.equals(new BlockPos(-2,3,4)),"Sponge import or offset failed");
            }
            var old=new NbtCompound();old.putShort("Width",(short)2);old.putShort("Height",(short)1);old.putShort("Length",(short)1);old.putByteArray("Blocks",new byte[]{1,5});old.putByteArray("Data",new byte[]{0,2});
            var legacy=SchematicIO.decode("fixture.schematic",old);require(legacy.state(0).isOf(Blocks.STONE)&&legacy.state(1).isOf(Blocks.BIRCH_PLANKS),"Legacy ID / metadata flattening failed");
            var sample=new Schematic("fixture","test",2,1,2,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.AIR.getDefaultState(),Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS,Direction.Axis.X),Blocks.GLASS.getDefaultState()});
            var structure=SchematicIO.decode("fixture.nbt",SchematicIO.encodeStructure(sample));require(structure.size()==4&&structure.state(2).equals(sample.state(2)),"Vanilla structure round trip failed");
            require(sample.transformed(0,1,"None").equals(new BlockPos(1,0,0))&&sample.transformedState(2,1,"None").get(Properties.AXIS)==Direction.Axis.Z,"Rotation did not transform geometry and states");
            require(sample.transformed(0,0,"X").equals(new BlockPos(1,0,0)),"Mirror geometry failed");
            for(int turn=0;turn<4;turn++)for(String mirror:new String[]{"None","X","Z"})for(int i=0;i<sample.size();i++)require(sample.indexAt(sample.transformed(i,turn,mirror),turn,mirror)==i,"Inverse placement transform failed");
            var lite=new NbtCompound();var regions=new NbtCompound();var region=new NbtCompound();region.put("Position",vector(2,0,-1));region.put("Size",vector(-2,1,1));var lp=new NbtList();lp.add(NbtHelper.fromBlockState(Blocks.AIR.getDefaultState()));lp.add(NbtHelper.fromBlockState(Blocks.STONE.getDefaultState()));region.put("BlockStatePalette",lp);region.putLongArray("BlockStates",new long[]{1});regions.put("negative",region);lite.put("Regions",regions);
            var ls=SchematicIO.decode("fixture.litematic",lite);require(ls.width==2&&ls.offset.equals(new BlockPos(1,0,-1))&&ls.state(0).isOf(Blocks.STONE),"Signed litematic region import failed");
            var stashFile=java.nio.file.Files.createTempFile("maro-stash-fixture", ".litematic");
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/farex-small-stash.litematic")){
                require(source!=null,"Missing supplied schematic fixture");java.nio.file.Files.copy(source,stashFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING);var stash=SchematicIO.read(stashFile);require(stash.width==18&&stash.height==8&&stash.length==13&&stash.solidCount()==710,"Supplied stash schematic dimensions or signed-region import changed");
                require(stash.materials().containsKey(Items.WATER_BUCKET)&&stash.materials().containsKey(Items.LAVA_BUCKET),"Fluid sources missing from stash supply list");
            }finally{java.nio.file.Files.deleteIfExists(stashFile);}
            var farmFile=java.nio.file.Files.createTempFile("maro-farm-fixture", ".litematic");
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/sellaxe-bone-meal-farm.litematic")){
                require(source!=null,"Missing supplied large farm fixture");java.nio.file.Files.copy(source,farmFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING);var farm=SchematicIO.read(farmFile);
                require(farm.width==55&&farm.height==39&&farm.length==65&&farm.solidCount()==49_751,"Large farm import dimensions or block count changed");
                require(farm.materials().get(Items.OBSERVER)==3910&&farm.materials().get(Items.HOPPER)==2602&&farm.materials().get(Items.NOTE_BLOCK)==1591,"Large farm redstone material counts changed");
                require(farm.materials().get(Items.LAVA_BUCKET)==880&&farm.materials().get(Items.WATER_BUCKET)==230,"Large farm source-bucket quantities changed");
            }finally{java.nio.file.Files.deleteIfExists(farmFile);}
            long[] packed=new long[3];for(int i=0;i<25;i++){long bit=(long)i*5;int word=(int)(bit/64),shift=(int)(bit%64);long value=i%17;packed[word]|=value<<shift;if(shift+5>64)packed[word+1]|=value>>>(64-shift);}
            for(int i=0;i<25;i++)require(SchematicIO.packedIndex(packed,i,5)==i%17,"Packed state straddling long boundary failed");
            require(AuctionMarket.price("Price: $1.25m","$")==1_250_000&&Double.isNaN(AuctionMarket.price("Seller: 123","$")),"Auction price parsing failed");
            for(String invalid:new String[]{"Price: $-20","Price: $1,23","Price: $sold 20","Total: $100 ($5 each)"})require(Double.isNaN(AuctionMarket.price(invalid,"$")),"Ambiguous or malformed price accepted: "+invalid);
            var single=new AuctionMarket.Offer(1,Items.STONE,1,10);var stack=new AuctionMarket.Offer(2,Items.STONE,64,700);
            require(AuctionMarket.choose(List.of(single,stack),64,0,20,1000,true,15).slot()==2,"Preferred stack price tolerance failed");
            require(AuctionMarket.choose(List.of(stack),10,16,20,1000,false,0)==null,"Overbuy cap ignored");
            require(AuctionMarket.choose(List.of(stack),64,0,20,600,false,0)==null,"Spend cap ignored");
            var bulk=new AuctionMarket.Offer(3,Items.STONE,64,128);var cheap=new AuctionMarket.Offer(4,Items.STONE,1,1);
            require(AuctionMarket.choose(List.of(cheap,bulk),129,16,10,1000,false,0)==bulk&&AuctionMarket.better(bulk,cheap,129)&&!AuctionMarket.better(cheap,bulk,129),"Bulk stack preference differs across pages");
            require(AuctionMarket.choose(List.of(cheap,bulk),1,16,10,1000,false,0)==cheap,"Remainder bought an unnecessary stack");
            require(AuctionMarket.choose(List.of(cheap,bulk),129,16,1,1000,false,0)==cheap&&AuctionMarket.choose(List.of(cheap,bulk),129,16,10,100,false,0)==cheap,"Bulk preference bypassed price or budget cap");
            var pearls=new AuctionMarket.Offer(5,Items.ENDER_PEARL,16,32);var pearl=new AuctionMarket.Offer(6,Items.ENDER_PEARL,1,1);
            require(AuctionMarket.choose(List.of(pearl,pearls),33,0,10,100,false,0)==pearls,"Item-specific stack size ignored");
            require(AuctionMarket.pageCount("Auction House (Page 1/2)")==2&&AuctionMarket.pageCount("Page: 1 of 1")==1&&AuctionMarket.pageCount("Page 3/2")==0&&AuctionMarket.pageCount("Price: $1/2")==0,"Auction page count parsing failed");
            try{var bad=old.copy();bad.putByteArray("Blocks",new byte[]{1});SchematicIO.decode("bad.schematic",bad);throw new AssertionError("Truncated arrays accepted");}catch(java.io.IOException expected){}
            try{Schematic.volume(2048,2048,2048);throw new AssertionError("Volume limit ignored");}catch(IllegalArgumentException expected){}
            var eta=new BuilderEta();eta.tick(0,true);
            for(int second=1;second<=10;second++){eta.tick(second*1000L,true);eta.completed();}
            require(eta.seconds(90)==90&&eta.label(90,0).equals("ETA ~ 1m 30s"),"ETA did not use measured completed work");
            eta.progress(-2);require(eta.seconds(92)==115,"ETA retained progress that was invalidated");eta.progress(2);
            eta.tick(10_000,false);eta.tick(1_000_000,false);eta.tick(1_000_000,true);eta.tick(1_001_000,true);eta.completed();
            require(eta.seconds(90)==90,"Paused time inflated the build ETA");
            eta.tick(1_035_000,true);require(eta.label(90,0).equals("ETA · waiting"),"Stalled build displayed a stale ETA");
            require(eta.label(0,3).equals("ETA · cleanup")&&eta.label(0,0).equals("ETA · done"),"ETA reported completion before temporary cleanup");
            eta.reset();eta.tick(0,true);eta.completed();require(eta.seconds(90)<0,"ETA invented a rate before warming up");
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static NbtCompound vector(int x,int y,int z){var n=new NbtCompound();n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);return n;}
    static void run(ClientGameTestContext context,TestSingleplayerContext singleplayer){
        context.runOnClient(client->{
            // Long unattended native builds must retain normal tick pacing.
            // These options belong only to this isolated test client; never save them.
            client.options.getInactivityFpsLimit().setValue(net.minecraft.client.option.InactivityFpsLimit.MINIMIZED);
            client.options.getEnableVsync().setValue(false);client.options.getMaxFps().setValue(120);client.options.pauseOnLostFocus=false;
            imports();
        });
        AutoBuilder builder=ModuleManager.get(AutoBuilder.class);
        BlockPos start=context.computeOnClient(client->client.player.getBlockPos().up(30));
        try{
            if(Boolean.getBoolean("maro.gametest.builderNetworkOnly")){fixture(context,singleplayer,builder,start);BuilderHomeChecks.run(context,singleplayer,builder,start);ghostMining(context,singleplayer,builder,start);fixture(context,singleplayer,builder,start);rejectedPlacement(context,singleplayer,builder,start);retainedPredictions(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderShoppingOnly")||Boolean.getBoolean("maro.gametest.builderAuctionOnly")){fixture(context,singleplayer,builder,start);BuilderAuctionChecks.run(context,singleplayer,builder);return;}
            if(Boolean.getBoolean("maro.gametest.builderSealedEscapeOnly")){sealedBuildEscape(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderHomeOnly")){fixture(context,singleplayer,builder,start);BuilderHomeChecks.run(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderStashHomesOnly")||Boolean.getBoolean("maro.gametest.builderStashOnly")||Boolean.getBoolean("maro.gametest.builderStashUpperOnly")||Boolean.getBoolean("maro.gametest.builderStashFinalOnly")){stashBuild(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderWaterOnly")){lowBucketSource(context,singleplayer,builder,start);containedTopLiquids(context,singleplayer,builder,start);roofStashLiquids(context,singleplayer,builder,start);floodedAccessDeparture(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderSurfaceOnly")){lowBucketSource(context,singleplayer,builder,start);floodedAccessDeparture(context,singleplayer,builder,start);narrowDropLanding(context,singleplayer,builder,start);}
            if(Boolean.getBoolean("maro.gametest.builderCleanupOnly"))immediateOpeningRepair(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderSurfaceOnly")){fixture(context,singleplayer,builder,start);exhaustedAccessCapacity(context,singleplayer,builder,start);raisedDoorEntry(context,singleplayer,builder,start);partialHeadroom(context,singleplayer,builder,start);stairPlacementPriority(context,singleplayer,builder,start);stairPlacementStaging(context,singleplayer,builder,start);blockedAccessStep(context,singleplayer,builder,start);compactAccessStep(context,singleplayer,builder,start);fixture(context,singleplayer,builder,start);offsetRecovery(context,singleplayer,builder,start);hopperCrossing(context,singleplayer,builder,start);shapedArrival(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderStagingOnly")){verticalPillarPacing(context,singleplayer,builder,start);nearbyCleanupPriority(context,singleplayer,builder,start);fixture(context,singleplayer,builder,start);sameLevelStaging(context,singleplayer,builder,start);thickWallEntry(context,singleplayer,builder,start);ceilingColumnEntry(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderCleanupOnly")){ownedChestCover(context,singleplayer,builder,start);cleanupAccess(context,singleplayer,builder,start);sealedBuildEscape(context,singleplayer,builder,start);airSupportFloorExit(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderEntryOnly")){compactAccessStep(context,singleplayer,builder,start);ownedChestCover(context,singleplayer,builder,start);elevatedFloorEntry(context,singleplayer,builder,start);sealedDirectionalAccess(context,singleplayer,builder,start,3);cleanupAccess(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderChestReturnOnly")){sealedDirectionalAccess(context,singleplayer,builder,start);sealedBuildEscape(context,singleplayer,builder,start);raisedChestReturn(context,singleplayer,builder,start);sealedChestReturn(context,singleplayer,builder,start);return;}
            fixture(context,singleplayer,builder,start);BuilderHomeChecks.run(context,singleplayer,builder,start);
            lowBucketSource(context,singleplayer,builder,start);
            containedTopLiquids(context,singleplayer,builder,start);
            roofStashLiquids(context,singleplayer,builder,start);
            floodedAccessDeparture(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            exhaustedAccessCapacity(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            raisedTurn(context,singleplayer,start);
            narrowDropLanding(context,singleplayer,builder,start);
            shapedArrival(context,singleplayer,builder,start);
            raisedDoorEntry(context,singleplayer,builder,start);
            partialHeadroom(context,singleplayer,builder,start);
            stairPlacementStaging(context,singleplayer,builder,start);
            stairPlacementPriority(context,singleplayer,builder,start);
            blockedAccessStep(context,singleplayer,builder,start);
            compactAccessStep(context,singleplayer,builder,start);
            ownedChestCover(context,singleplayer,builder,start);
            cleanupAccess(context,singleplayer,builder,start);
            immediateOpeningRepair(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            faceReach(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            scaffoldReach(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            offsetRecovery(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            activeStepProtection(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            sameLevelStaging(context,singleplayer,builder,start);
            verticalPillarPacing(context,singleplayer,builder,start);
            nearbyCleanupPriority(context,singleplayer,builder,start);
            thickWallEntry(context,singleplayer,builder,start);
            ceilingColumnEntry(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            accessCapacity(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            elevatedFloorEntry(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderTurnOnly"))return;
            layerTail(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            savedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            rejectedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            retainedPredictions(context,singleplayer,builder,start);
            ghostMining(context,singleplayer,builder,start);
            observerAssembly(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            stalledInteractions(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            startupChestScan(context,singleplayer,builder,start);
            distantChest(context,singleplayer,builder,start);
            raisedChestReturn(context,singleplayer,builder,start);
            sealedChestReturn(context,singleplayer,builder,start);
            sealedBuildEscape(context,singleplayer,builder,start);
            airSupportFloorExit(context,singleplayer,builder,start);
            sealedDirectionalAccess(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderNavigationOnly")){cancellation(context,builder,context.computeOnClient(client->builder.schematic()));return;}
            largeSupply(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            longRestockRoute(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            BlockPos origin=start.add(-1,0,2);
            context.runOnClient(client->{
                BlockState[] cells={Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GLASS.getDefaultState()};
                builder.install(new Schematic("survival-fixture.nbt","test",2,2,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);
                builder.preview();client.player.setYaw(0);client.player.setPitch(0);
            });
            context.waitTicks(8);context.takeScreenshot("maro-builder-textured-preview");
            context.runOnClient(client->{require(SchematicRenderer.renderedCells==8,"Preview did not render all 8 expected cells");require(builder.remainingMaterials().get(Items.STONE)==4,"Remaining materials wrong");});
            context.runOnClient(client->client.setScreen(new BuilderScreen(null,builder,true)));context.waitTicks(4);context.takeScreenshot("maro-builder-materials");
            context.getInput().resizeWindow(960,720);context.waitTicks(4);context.takeScreenshot("maro-builder-materials-compact");context.setScreen(()->null);context.getInput().resizeWindow(1280,720);
            singleplayer.getServer().runCommand("give @a minecraft:stone 64");singleplayer.getServer().runCommand("give @a minecraft:glass 64");context.waitTicks(6);
            singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();for(int i=0;i<9;i++){var stack=player.getInventory().getStack(i);if(stack.isOf(Items.STONE)||stack.isOf(Items.GLASS)){player.getInventory().setStack(stack.isOf(Items.STONE)?20:21,stack.copy());player.getInventory().setStack(i,ItemStack.EMPTY);}}player.playerScreenHandler.syncState();});context.waitTicks(5);
            context.runOnClient(client->{
                set(builder,"Save Build Progress",false);set(builder,"Build Mode","Automatic");set(builder,"Temporary Supports",false);builder.toggleMaterialIgnored(Items.GLASS);
                require(builder.materialIgnored(Items.GLASS)&&!builder.remainingMaterials().containsKey(Items.GLASS)&&builder.saveExtra().getAsJsonArray("ignored-materials").toString().contains("minecraft:glass"),"Ignored material remains required or was not saved");
                builder.startBuild();
            });
            await(context,builder,400);verify(singleplayer,origin,2,2,2,y->y==0?Blocks.STONE:Blocks.AIR);
            context.runOnClient(client->require(builder.inventoryCount(Items.GLASS)==64&&builder.state(4)==AutoBuilder.IGNORED,"Builder placed an ignored material"));
            context.runOnClient(client->{client.setScreen(new BuilderScreen(null,builder,true));});context.waitTicks(4);context.takeScreenshot("maro-builder-materials-ignored");
            context.runOnClient(client->{builder.toggleMaterialIgnored(Items.GLASS);builder.restartBuild();});
            await(context,builder,400);
            verify(singleplayer,origin,2,2,2,y->y==0?Blocks.STONE:Blocks.GLASS);
            context.takeScreenshot("maro-builder-server-built");

            // Restart rescans the same placement and repairs only the removed block.
            var loaded=context.computeOnClient(client->builder.schematic());
            int stoneBefore=context.computeOnClient(client->builder.inventoryCount(Items.STONE));
            int glassBefore=context.computeOnClient(client->builder.inventoryCount(Items.GLASS));
            command(singleplayer,"setblock",origin,"air");context.waitTicks(5);
            context.runOnClient(client->{button(builder,"Restart Build").press();require(builder.schematic()==loaded&&builder.origin().equals(origin),"Restart changed the selected schematic or origin");require(builder.state(0)==AutoBuilder.UNKNOWN&&builder.building(),"Restart did not reset the scan and start building");});
            await(context,builder,400);verify(singleplayer,origin,2,2,2,y->y==0?Blocks.STONE:Blocks.GLASS);
            context.runOnClient(client->require(builder.inventoryCount(Items.STONE)==stoneBefore-1&&builder.inventoryCount(Items.GLASS)==glassBefore,"Restart replaced already-correct blocks"));

            // The supplied stash uses delay-3 repeaters and open trapdoors: place then configure.
            for(boolean repeater:new boolean[]{true,false}){
                fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a "+(repeater?"repeater":"warped_trapdoor")+" 1");context.waitTicks(5);
                context.runOnClient(client->{var state=repeater?Blocks.REPEATER.getDefaultState().with(RepeaterBlock.DELAY,3):Blocks.WARPED_TRAPDOOR.getDefaultState().with(Properties.OPEN,true);builder.install(new Schematic("configurable-block.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{state}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
                await(context,builder,600);require(singleplayer.getServer().computeOnServer(server->{var actual=server.getOverworld().getBlockState(start.add(0,0,2));return repeater?actual.isOf(Blocks.REPEATER)&&actual.get(RepeaterBlock.DELAY)==3:actual.isOf(Blocks.WARPED_TRAPDOOR)&&actual.get(Properties.OPEN);}),"Configurable block did not reach requested state");
            }
            // Source buckets use the item's vanilla raycast; they must not be treated as BlockItem.
            for(boolean water:new boolean[]{true,false}){
                fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a "+(water?"water_bucket":"lava_bucket")+" 1");var fluid=start.add(0,0,2);
                for(var side:Direction.Type.HORIZONTAL)command(singleplayer,"setblock",fluid.offset(side),"stone");context.waitTicks(5);
                context.runOnClient(client->{builder.install(new Schematic("fluid-source.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{(water?Blocks.WATER:Blocks.LAVA).getDefaultState()}));builder.setOrigin(fluid);builder.startBuild();});
                await(context,builder,600);require(singleplayer.getServer().computeOnServer(server->{var actual=server.getOverworld().getBlockState(fluid);return actual.isOf(water?Blocks.WATER:Blocks.LAVA)&&actual.get(FluidBlock.LEVEL)==0;}),"Bucket did not create a server-confirmed fluid source");
            }
            // Missing stone is obtained from a real server chest and then placed, without a creative give.
            fixture(context,singleplayer,builder,start);
            BlockPos chest=start.add(2,0,0);command(singleplayer,"setblock",chest,"chest[facing=north,type=left]");command(singleplayer,"setblock",chest.east(),"chest[facing=north,type=right]");
            singleplayer.getServer().runCommand("item replace block "+chest.toShortString().replace(",","")+" container.0 with minecraft:stone 64");
            context.waitTicks(8);
            context.runOnClient(client->{
                builder.install(new Schematic("restock-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,2));builder.preview();
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);button(builder,"Mark Restock Container").press();builder.startBuild();
            });
            await(context,builder,500);
            verify(singleplayer,start.add(0,0,2),1,1,1,y->Blocks.STONE);
            context.runOnClient(client->require(builder.inventoryCount(Items.STONE)==0,"Chest restock withdrew excess layer materials"));
            require(singleplayer.getServer().computeOnServer(server->((net.minecraft.block.entity.ChestBlockEntity)server.getOverworld().getBlockEntity(chest)).getStack(0).getCount()==63),"Exact restock did not leave the unneeded blocks in the chest");

            // A far target needs normal walking, and the path must keep clear of a lava floor tile.
            fixture(context,singleplayer,builder,start);command(singleplayer,"setblock",start.add(0,-1,4),"lava");singleplayer.getServer().runCommand("give @a stone 64");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("walk-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,12));builder.startBuild();});
            await(context,builder,500);verify(singleplayer,start.add(0,0,12),1,1,1,y->Blocks.STONE);
            context.runOnClient(client->require(client.player.getZ()>start.getZ()+6&&client.player.getY()>=start.getY()-.2,"Builder did not walk to its target safely"));

            // A nearby visible standing cell behind a one-block-high window is sealed off.
            // The builder must route around the enclosure instead of repeatedly choosing it.
            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 1");
            for(int x:new int[]{-1,1})singleplayer.getServer().runCommand("fill "+coords(start.add(x,0,2))+" "+coords(start.add(x,1,4))+" stone");
            for(int z:new int[]{2,4})singleplayer.getServer().runCommand("fill "+coords(start.add(-1,0,z))+" "+coords(start.add(1,1,z))+" stone");
            command(singleplayer,"setblock",start.add(0,0,4),"air");context.waitTicks(5);
            context.runOnClient(client->{var walk=new BuilderWalk();require(walk.canStand(start.add(0,0,3))&&!walk.canReachStand(start.add(0,0,3)),"Sealed route fixture is not unreachable");builder.install(new Schematic("obstructed-route-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,5));builder.startBuild();});
            await(context,builder,700);verify(singleplayer,start.add(0,0,5),1,1,1,y->Blocks.STONE);

            // Food in the main inventory moves into the hotbar and is consumed before building.
            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 1");singleplayer.getServer().runCommand("give @a cooked_beef 3");
            singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(8);player.getHungerManager().setSaturationLevel(0);for(int i=0;i<9;i++)if(player.getInventory().getStack(i).isOf(Items.COOKED_BEEF)){player.getInventory().setStack(20,player.getInventory().getStack(i).copy());player.getInventory().setStack(i,ItemStack.EMPTY);}player.playerScreenHandler.syncState();});context.waitTicks(8);
            context.runOnClient(client->{set(builder,"Auto Eat",true);set(builder,"Buy Steak",false);builder.install(new Schematic("food-inventory-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            await(context,builder,400);verify(singleplayer,start.add(0,0,2),1,1,1,y->Blocks.STONE);
            require(singleplayer.getServer().computeOnServer(server->server.getPlayerManager().getPlayerList().getFirst().getHungerManager().getFoodLevel()>14),"Builder did not consume inventory steak");context.runOnClient(client->require(builder.inventoryCount(Items.COOKED_BEEF)==2&&!client.options.useKey.isPressed(),"Auto Eat consumed extra food or retained use input"));

            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 1");singleplayer.getServer().runCommand("give @a cooked_beef 3");
            singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(8);player.getHungerManager().setSaturationLevel(0);});context.waitTicks(8);
            context.runOnClient(client->{set(builder,"Auto Eat",true);builder.install(new Schematic("food-pause-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            for(int i=0;i<80&&!context.computeOnClient(client->client.player.isUsingItem());i++)context.waitTick();
            context.runOnClient(client->{require(client.player.isUsingItem(),"Auto Eat never started");builder.pause("Food pause test");require(!client.options.useKey.isPressed()&&!client.player.isUsingItem(),"Pause did not stop owned food use");});context.waitTicks(40);
            context.runOnClient(client->require(builder.inventoryCount(Items.COOKED_BEEF)==3,"Paused eating continued consuming steak"));

            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 1");
            command(singleplayer,"setblock",start.add(2,0,0),"chest[facing=north,type=left]");command(singleplayer,"setblock",start.add(3,0,0),"chest[facing=north,type=right]");
            singleplayer.getServer().runCommand("item replace block "+coords(start.add(2,0,0))+" container.0 with cooked_beef 64");singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(8);player.getHungerManager().setSaturationLevel(0);});context.waitTicks(8);
            context.runOnClient(client->{set(builder,"Auto Eat",true);set(builder,"Steak Reserve",3);builder.auctionBudget(0);builder.install(new Schematic("food-chest-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,2));client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(start.add(2,0,0)),Direction.WEST,start.add(2,0,0),false);builder.markContainer();builder.startBuild();});
            await(context,builder,500);verify(singleplayer,start.add(0,0,2),1,1,1,y->Blocks.STONE);
            require(singleplayer.getServer().computeOnServer(server->((net.minecraft.block.entity.ChestBlockEntity)server.getOverworld().getBlockEntity(start.add(2,0,0))).getStack(0).getCount()==61),"Food restock did not take exactly its reserve");context.runOnClient(client->require(builder.inventoryCount(Items.COOKED_BEEF)==2,"Chest steak was not consumed before resuming"));

            // A two-block-deep pocket has no walking route until a jump places a dirt step below the player.
            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 64");singleplayer.getServer().runCommand("give @a dirt 64");
            singleplayer.getServer().runCommand("fill "+coords(start.add(-1,-2,-1))+" "+coords(start.add(1,-2,1))+" stone");
            command(singleplayer,"setblock",start.down(3),"stone");command(singleplayer,"setblock",start.down(2),"air");command(singleplayer,"setblock",start.down(),"air");
            singleplayer.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()-2)+" "+(start.getZ()+.5));context.waitTicks(8);
            context.runOnClient(client->{builder.install(new Schematic("unstuck-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,10));builder.startBuild();});
            boolean recovered=false;for(int i=0;i<800&&context.computeOnClient(client->builder.building());i++){if(context.computeOnClient(client->builder.temporarySupports().contains(start.down(2))))recovered=true;context.waitTick();}
            require(recovered,"Stuck builder did not place a temporary step beneath itself");await(context,builder,100);
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(start.down(2)).isAir()),"Unstuck dirt step was not removed");
            verify(singleplayer,start.add(0,0,10),1,1,1,y->Blocks.STONE);

            // Side-face placement creates a horizontal log. Only this builder's temporary support is cleaned.
            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a oak_log 64");singleplayer.getServer().runCommand("give @a dirt 64");context.waitTicks(5);
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("axis-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS,Direction.Axis.X)}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            awaitSupport(context,builder);
            BlockPos support=context.computeOnClient(client->builder.temporarySupports().iterator().next());
            context.runOnClient(client->builder.pause("Step-off cleanup fixture"));
            singleplayer.getServer().runCommand("tp @a "+(support.getX()+.5)+" "+(support.getY()+1)+" "+(support.getZ()+.5));context.waitTicks(8);
            context.runOnClient(client->{builder.restartBuild();require(builder.temporarySupports().contains(support),"Restart forgot temporary supports before cleanup");});
            await(context,builder,500);
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(start.add(0,0,2)).get(Properties.AXIS)==Direction.Axis.X),"Horizontal log was placed with wrong axis");
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(start.add(-1,0,2)).isAir()&&server.getOverworld().getBlockState(start.add(1,0,2)).isAir()),"Temporary log support was not cleaned");

            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a oak_log 64");singleplayer.getServer().runCommand("give @a dirt 64");context.waitTicks(5);
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("far-cleanup-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS,Direction.Axis.X)}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            awaitSupport(context,builder);var distantSupports=context.computeOnClient(client->builder.temporarySupports());context.runOnClient(client->builder.pause("Distant cleanup fixture"));
            singleplayer.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()-10+.5));context.waitTicks(8);context.runOnClient(client->builder.startBuild());await(context,builder,600);
            require(singleplayer.getServer().computeOnServer(server->distantSupports.stream().allMatch(pos->server.getOverworld().getBlockState(pos).isAir())),"Distant temporary supports were abandoned");

            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone_slab 64");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("slab-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE,net.minecraft.block.enums.SlabType.DOUBLE)}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            await(context,builder,400);
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(start.add(0,0,2)).get(Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE),"Double slab was not built in two interactions");
            context.runOnClient(client->require(builder.inventoryCount(Items.STONE_SLAB)==62,"Double slab did not consume two items"));

            // Air cells participate in Mine Out, even though there is no visible ghost model.
            fixture(context,singleplayer,builder,start);command(singleplayer,"setblock",start.add(0,0,2),"dirt");context.waitTicks(5);
            context.runOnClient(client->{
                builder.install(new Schematic("air-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.AIR.getDefaultState()}));builder.setOrigin(start.add(0,0,2));set(builder,"Mine Out Schematic",true);builder.startBuild();
            });
            await(context,builder,400);verify(singleplayer,start.add(0,0,2),1,1,1,y->Blocks.AIR);

            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a note_block 1");context.waitTicks(5);
            var notePos=start.add(0,0,2);
            context.runOnClient(client->{builder.install(new Schematic("note-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.NOTE_BLOCK.getDefaultState().with(NoteBlock.NOTE,7)}));builder.setOrigin(notePos);builder.startBuild();});
            await(context,builder,500);
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(notePos).get(NoteBlock.NOTE)==7),"New note block did not stop at its requested note");
            context.waitTicks(40);require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(notePos).get(NoteBlock.NOTE)==7),"Builder kept cycling an already-correct note");
            command(singleplayer,"setblock",notePos,"note_block[note=22]");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("note-wrap-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.NOTE_BLOCK.getDefaultState().with(NoteBlock.NOTE,2)}));builder.setOrigin(notePos);builder.startBuild();});
            await(context,builder,400);context.waitTicks(40);
            require(singleplayer.getServer().computeOnServer(server->server.getOverworld().getBlockState(notePos).get(NoteBlock.NOTE)==2),"Note tuning wrapped incorrectly or continued past the target");

            for(String interactive:new String[]{"hopper","chest","note_block"}){
                fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a stone 1");command(singleplayer,"setblock",start.add(0,0,2),interactive);context.waitTicks(5);
                context.runOnClient(client->{builder.install(new Schematic("crouch-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,1,2));builder.startBuild();});
                boolean opened=false;for(int i=0;i<400&&context.computeOnClient(client->builder.building());i++){if(context.computeOnClient(client->client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>))opened=true;context.waitTick();}
                require(!opened,"Builder opened an interactive support instead of crouching: "+interactive);await(context,builder,100);verify(singleplayer,start.add(0,1,2),1,1,1,y->Blocks.STONE);
                context.runOnClient(client->require(!client.options.sneakKey.isPressed(),"Builder retained crouch after placing"));
            }

            fixture(context,singleplayer,builder,start);BuilderAuctionChecks.run(context,singleplayer,builder);

            context.runOnClient(client->{
                builder.pause("Screenshot");client.setScreen(new ClickGuiScreen());((ClickGuiScreen)client.currentScreen).openModuleSettings(builder);
                require(client.currentScreen instanceof BuilderControlScreen,"Builder settings did not open the simple control panel");
                require(builder.getSettingSections().stream().filter(section->!section.getTitle().equals("Saved Builds")).mapToInt(section->section.getSettings().size()).sum()<60,"Unnecessary settings still clutter the builder outside the new saved-build controls");
                require(builder.buildMode().equals("Automatic"),"Automatic build mode is unavailable");
            });context.waitTicks(5);context.takeScreenshot("maro-builder-control-panel");
            context.runOnClient(client->{client.setScreen(new ClickGuiScreen());((ClickGuiScreen)client.currentScreen).openModuleOptions(builder);});context.waitTicks(5);context.takeScreenshot("maro-builder-simplified-options");

            cancellation(context,builder,loaded);
        }finally{
            context.runOnClient(client->{builder.setEnabled(false);client.options.useKey.setPressed(false);client.options.forwardKey.setPressed(false);client.options.jumpKey.setPressed(false);client.setScreen(null);});
            singleplayer.getServer().runCommand("gamemode creative @a");
        }
    }
    private static void cancellation(ClientGameTestContext context,AutoBuilder builder,Schematic snapshot){
        context.runOnClient(client->{
            button(builder,"Cancel Schematic").press();
            require(builder.schematic()==null&&!builder.loading()&&!builder.building()&&!builder.buying()&&!builder.previewVisible()&&!builder.isEnabled(),"Cancel did not unload and stop the schematic");
            require(builder.remainingMaterials().isEmpty()&&builder.visibleCells().isEmpty()&&builder.saveExtra().get("file").getAsString().isEmpty(),"Cancel retained schematic state");
            require(!client.options.forwardKey.isPressed()&&!client.options.useKey.isPressed()&&!AutoBuilder.holdingBreak(),"Cancel retained builder input");
        });
        // Cancel in the same client turn. A warm IO executor may already have completed;
        // either way, cancellation must unload the file and prevent later resurrection.
        var cancelFile=builder.folder().resolve("maro-cancel-fixture.nbt");
        try{NbtIo.writeCompressed(SchematicIO.encodeStructure(snapshot),cancelFile);}catch(java.io.IOException e){throw new AssertionError(e);}
        context.runOnClient(client->{builder.load(cancelFile);require(builder.loading()||builder.schematic()!=null,"Load fixture neither started nor completed");builder.cancelSchematic();});
        context.waitTicks(30);
        context.runOnClient(client->require(builder.schematic()==null&&!builder.loading()&&!builder.previewVisible(),"Cancelled load restored its schematic"));
        try{java.nio.file.Files.deleteIfExists(cancelFile);}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static void savedPlacement(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var origin=start.add(0,0,2);var chest=start.east(3);var support=start.west(2);
        command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",chest.east(),"chest[facing=north,type=right]");
        command(world,"setblock",origin,"stone");command(world,"setblock",support,"dirt");world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        var saved=context.computeOnClient(client->{
            builder.install(new Schematic("persisted.litematic","test",2,1,1,new BlockPos(-2,0,3),new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(origin);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(support);
            @SuppressWarnings("unchecked")var escape=(Set<BlockPos>)field(builder,"escapeSupports");escape.add(support);
            @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"escapeSupportWork");owners.put(support,1);
            client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();set(builder,"Build Slot","10");return builder.savePlacement("Survival stash");
        });
        for(int i=0;i<200&&!saved.isDone();i++)context.waitTick();saved.join();
        var other=context.computeOnClient(client->{
            builder.cancelSchematic();builder.install(new Schematic("second-build.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.GLASS.getDefaultState()}));builder.setOrigin(start.east(10));set(builder,"Build Slot","9");return builder.savePlacement("Glass tower");
        });
        for(int i=0;i<200&&!other.isDone();i++)context.waitTick();other.join();
        context.runOnClient(client->{set(builder,"Build Slot","10");builder.loadPlacement();});
        for(int i=0;i<200&&context.computeOnClient(client->builder.loading());i++)context.waitTick();
        context.runOnClient(client->client.setScreen(new BuilderPlacementsScreen(null,builder)));context.waitTicks(3);context.takeScreenshot("maro-saved-builds");context.runOnClient(client->client.setScreen(null));
        context.runOnClient(client->{
            require(builder.schematic()!=null&&builder.origin().equals(origin)&&builder.schematic().offset.equals(new BlockPos(-2,0,3)),"Saved origin, file offset or snapshot was lost");
            require(builder.restockContainers().contains(chest)&&builder.placementName().equals("Survival stash")&&!builder.building(),"Saved chest/name or paused resume was lost");
            @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"escapeSupportWork");
            require(builder.temporarySupports().contains(support)&&Integer.valueOf(1).equals(owners.get(support)),"Saved escape support lost its unfinished work owner");
            // A re-created ClientWorld must retain the saved anchor and rescan actual blocks.
            try{var field=AutoBuilder.class.getDeclaredField("world");field.setAccessible(true);field.set(builder,null);}catch(Exception error){throw new AssertionError(error);}
            builder.startBuild();require(builder.origin().equals(origin),"Reconnect silently moved the saved placement");
        });
        await(context,builder,300);verify(world,origin,2,1,1,y->Blocks.STONE);
        world.getServer().runOnServer(server->require(server.getOverworld().getBlockState(support).isAir(),"Resumed completed work left its saved temporary support"));
        context.runOnClient(client->require(builder.inventoryCount(Items.STONE)==0,"Resume bought/placed an already completed block"));
    }
    private static void rejectedPlacement(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.add(0,0,2);var rejected=new java.util.concurrent.atomic.AtomicInteger();var gate=new java.util.concurrent.atomic.AtomicBoolean(true);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClient()&&gate.get()&&hit.getBlockPos().equals(target.down())&&player.getStackInHand(hand).isOf(Items.STONE)&&rejected.getAndIncrement()<3){((net.minecraft.server.network.ServerPlayerEntity)player).playerScreenHandler.syncState();return net.minecraft.util.ActionResult.FAIL;}
            return net.minecraft.util.ActionResult.PASS;
        });
        try{
            world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
            context.runOnClient(client->{builder.install(new Schematic("rejected-placement.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);BuilderPacketChecks.begin();BuilderPacketChecks.expectLookLimits(18,14);BuilderPlacementProbe.begin(target);builder.startBuild();});
            await(context,builder,200);require(rejected.get()==4,"Server did not reject exactly three attempts before accepting");verify(world,target,1,1,1,y->Blocks.STONE);
            context.waitTicks(8);
            context.runOnClient(client->{
                require(BuilderPlacementProbe.attempts.size()==4&&BuilderPlacementProbe.slotClicks==6&&BuilderPlacementProbe.emptySlotClicks>=3&&BuilderPlacementProbe.withheld>0,"Missing inventory correction was not recovered through the original slot: attempts="+BuilderPlacementProbe.attempts+" clicks="+BuilderPlacementProbe.slotClicks+" empty="+BuilderPlacementProbe.emptySlotClicks+" withheld="+BuilderPlacementProbe.withheld);
                int maxGap=0;for(int i=1;i<BuilderPlacementProbe.attempts.size();i++)maxGap=Math.max(maxGap,BuilderPlacementProbe.attempts.get(i)-BuilderPlacementProbe.attempts.get(i-1));
                require(maxGap<=10,"Ghost retry retained a long placement pause: "+BuilderPlacementProbe.attempts);
                require(builder.inventoryCount(Items.STONE)==0&&client.player.playerScreenHandler.getCursorStack().isEmpty(),"Ghost recovery lost/duplicated stock or left it on the cursor");BuilderPacketChecks.verify(4);
                System.out.println("[ghost-placement-proof] attempts="+BuilderPlacementProbe.attempts+" slotClicks="+BuilderPlacementProbe.slotClicks+" emptySlotClicks="+BuilderPlacementProbe.emptySlotClicks+" withheld="+BuilderPlacementProbe.withheld+" maxGap="+maxGap);
            });
            world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();require(player.getInventory().count(Items.STONE)==0&&player.playerScreenHandler.getCursorStack().isEmpty(),"Actual server ghost recovery inventory/cursor mismatch");});
        }finally{gate.set(false);context.runOnClient(client->{BuilderPlacementProbe.end();BuilderPacketChecks.recording=false;});}
    }
    private static void ghostMining(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.south(2);
        for(String scenario:List.of("delayed","rejected","paused","timed out")){
            fixture(context,world,builder,start);command(world,"setblock",target,"dirt");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
            var rejected=new java.util.concurrent.atomic.AtomicBoolean();var gate=new java.util.concurrent.atomic.AtomicBoolean(scenario.equals("rejected"));
            net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity)->!gate.get()||!pos.equals(target)||!rejected.compareAndSet(false,true));
            try{
                context.runOnClient(client->{set(builder,"Replace Wrong Blocks",true);builder.install(new Schematic("ghost-mining.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);BuilderBlockDelay.begin(target);builder.startBuild();});
                for(int i=0;i<160&&context.computeOnClient(client->field(builder,"pendingBreak")==null);i++)context.waitTick();
                context.runOnClient(client->require(field(builder,"pendingBreak")!=null&&client.world.getBlockState(target).isOf(Blocks.DIRT),"Unconfirmed native mining did not retain safe collision: "+scenario+" "+builder.status()));
                context.waitTicks(5);
                context.runOnClient(client->require(builder.building()&&builder.inventoryCount(Items.STONE)==1&&field(builder,"pendingBreak")!=null&&client.world.getBlockState(target).isOf(Blocks.DIRT),"Builder placed, completed or exposed a hole before server confirmation: "+scenario+" "+builder.status()));
                if(scenario.equals("paused"))context.runOnClient(client->{builder.pause("Pause ghost mining");require(client.world.getBlockState(target).isOf(Blocks.DIRT),"Pause did not restore unconfirmed mined collision");});
                if(scenario.equals("timed out")){
                    for(int i=0;i<100&&context.computeOnClient(client->field(builder,"pendingBreak")!=null);i++)context.waitTick();
                    context.runOnClient(client->require(field(builder,"pendingBreak")==null&&client.world.getBlockState(target).isOf(Blocks.DIRT),"Mining timeout retained unconfirmed client air"));
                }
                require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(scenario.equals("rejected")?Blocks.DIRT:Blocks.AIR)),"Fixture did not retain actual server mining outcome: "+scenario);
                context.runOnClient(client->{BuilderBlockDelay.release();if(scenario.equals("paused"))builder.startBuild();});
                await(context,builder,500);verify(world,target,1,1,1,y->Blocks.STONE);
                require(!scenario.equals("rejected")||rejected.get(),"Rejected mining fixture did not execute");
                System.out.println("[ghost-proof] native "+scenario+" mining recovered, final server block=stone");
            }finally{gate.set(false);context.runOnClient(client->BuilderBlockDelay.release());}
        }
        context.runOnClient(client->set(builder,"Replace Wrong Blocks",false));
    }
    private static Schematic stashFixture(){
        try{
            var file=java.nio.file.Files.createTempFile("maro-stash-build", ".litematic");
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/farex-small-stash.litematic")){
                java.nio.file.Files.copy(source,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);return SchematicIO.read(file);
            }finally{java.nio.file.Files.deleteIfExists(file);}
        }catch(java.io.IOException error){throw new AssertionError(error);}
    }
    private static void stashBuild(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos original){
        var start=new BlockPos(-26210,61,-150577);var origin=start.add(-16,2,-8);var chest=start.east(3);var stash=stashFixture();
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(start));context.waitTicks(30);fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(-24,-1,-24))+" "+coords(start.add(24,-1,24))+" end_stone");
        world.getServer().runCommand("fill "+coords(start.add(-24,0,-24))+" "+coords(start.add(24,12,24))+" air");
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        world.getServer().runOnServer(server->{
            var level=server.getOverworld();var inventory=net.minecraft.block.ChestBlock.getInventory((net.minecraft.block.ChestBlock)Blocks.CHEST,level.getBlockState(chest),level,chest,true);
            int slot=0;
            for(var entry:stash.materials().entrySet())for(int remaining=entry.getValue();remaining>0;){var stack=new ItemStack(entry.getKey(),Math.min(entry.getKey().getMaxCount(),remaining));inventory.setStack(slot++,stack);remaining-=stack.getCount();}
            inventory.setStack(slot++,new ItemStack(Items.DIRT,64));inventory.setStack(slot++,new ItemStack(Items.DIRT,64));inventory.setStack(slot++,new ItemStack(Items.DIRT,64));inventory.setStack(slot++,new ItemStack(Items.DIRT,64));
            inventory.setStack(slot++,new ItemStack(Items.DIAMOND_PICKAXE));inventory.setStack(slot++,new ItemStack(Items.DIAMOND_SHOVEL));inventory.setStack(slot,new ItemStack(Items.COOKED_BEEF,64));inventory.markDirty();
        });context.waitTicks(6);
        Set<BlockPos> priorSupports=new HashSet<>();
        boolean homeTest=Boolean.getBoolean("maro.gametest.builderStashHomesOnly");
        var checkpoint=Boolean.getBoolean("maro.gametest.builderStashCheckpointOnly")?new BuilderCheckpointFixture():null;
        if(homeTest){BuilderHomeChecks.installCommands(world,chest);System.out.println(checkpoint==null?"[builder-check] Fresh 710-block schematic on empty ground with storage homes and independent head aim enabled":"[builder-check] Captured "+checkpoint.progress+"-block server stall replay with unchanged native supplies and owned posts");}
        if(checkpoint!=null){require(homeTest,"Checkpoint needs native home commands");checkpoint.restoreServer(world);context.waitTicks(20);}
        boolean finalTargets=Boolean.getBoolean("maro.gametest.builderStashFinalOnly");
        boolean upper=Boolean.getBoolean("maro.gametest.builderStashUpperOnly")||finalTargets;
        if(upper){
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/stash-upper-supports.txt")){
                for(var line:new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()){
                    var parts=line.trim().split("\\s+");priorSupports.add(new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])));
                }
            }catch(java.io.IOException error){throw new AssertionError(error);}
            if(finalTargets)priorSupports.removeIf(pos->{int cell=stash.indexAt(pos.subtract(origin),0,"None");return cell>=0&&!stash.state(cell).isAir();});
            world.getServer().runOnServer(server->{
                var level=server.getOverworld();
                for(int cell=0;cell<stash.size();cell++){var expected=stash.state(cell);if((finalTargets||stash.local(cell).getY()<=3)&&!expected.isAir()&&!(expected.getBlock() instanceof net.minecraft.block.FluidBlock)&&!(expected.getBlock() instanceof net.minecraft.block.ObserverBlock))level.setBlockState(origin.add(stash.local(cell)),expected,net.minecraft.block.Block.NOTIFY_ALL);}
                for(var support:priorSupports)level.setBlockState(support,Blocks.DIRT.getDefaultState(),net.minecraft.block.Block.NOTIFY_ALL);
            });
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a -26209.7 65 -150576.2");context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(10);
            // The captured stall was after preparation and the layer-4 chest trip.
            // Restore that batch rather than starting a new preparation journey.
            var held=new HashMap<Item,Integer>();
            for(int cell=0;cell<stash.size();cell++)if(finalTargets?stash.state(cell).getBlock() instanceof net.minecraft.block.FluidBlock||stash.state(cell).getBlock() instanceof net.minecraft.block.ObserverBlock:stash.local(cell).getY()==4){var expected=stash.state(cell);var item=Schematic.material(expected);if(item!=Items.AIR)held.merge(item,Schematic.units(expected),Integer::sum);if(expected.getBlock() instanceof net.minecraft.block.FlowerPotBlock)held.merge(Items.FLOWER_POT,1,Integer::sum);}
            held.put(Items.DIRT,64);held.put(Items.DIAMOND_PICKAXE,1);held.put(Items.DIAMOND_SHOVEL,1);held.put(Items.COOKED_BEEF,16);
            held.forEach((item,count)->world.getServer().runCommand("give @a "+net.minecraft.registry.Registries.ITEM.getId(item)+" "+count));context.waitTicks(6);
        }
        try{
            if(homeTest&&checkpoint==null)require(world.getServer().computeOnServer(server->{for(int cell=0;cell<stash.size();cell++)if(!stash.state(cell).isAir()&&!server.getOverworld().getBlockState(origin.add(stash.local(cell))).isAir())return false;return true;}),"Fresh build contained prebuilt schematic blocks");
            if(checkpoint!=null)require(world.getServer().computeOnServer(server->{int count=0;for(int cell=0;cell<stash.size();cell++)if(!stash.state(cell).isAir()&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(stash.local(cell))),stash.state(cell)))count++;return count;})==checkpoint.progress,"Captured server checkpoint no longer matches its recorded progress");
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);set(builder,"Clean Temporary Supports",true);set(builder,"Support Dirt Reserve",64);set(builder,"Auto Buy Tools",true);
                set(builder,"Material Supply",homeTest?"Nearby Sections":"Layer by Layer");set(builder,"Prepare Whole Build",!upper&&checkpoint==null);set(builder,"Stockpile In Chests",true);set(builder,"Auto Eat",true);builder.auctionBudget(1000);
                if(homeTest){set(builder,"Builder Homes",true);set(builder,"Head Spoofing",true);((BuilderHomes)field(builder,"homes")).reset();}
                builder.install(stash);builder.setOrigin(origin);((Set<BlockPos>)field(builder,"supports")).addAll(priorSupports);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();if(checkpoint!=null)checkpoint.restoreBuilder(builder);BuilderPacketChecks.begin();if(homeTest)BuilderPacketChecks.expectLookLimits(12,8);builder.startBuild();
                if(upper)try{var attempts=AutoBuilder.class.getDeclaredField("recoveryAttempts");attempts.setAccessible(true);attempts.setInt(builder,3);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            // The full empty-ground run also prepares stock and constructs all
            // 710 cells before removing its posts and restoring every opening.
            // Individual access/pickup no-progress limits remain in production.
            await(context,builder,homeTest&&checkpoint==null?90000:72000);
            String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<stash.size();i++)if(!stash.state(i).isAir()&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(stash.local(i))),stash.state(i)))return origin.add(stash.local(i)).toShortString();return "";});
            require(mismatch.isEmpty(),"Stash server mismatch at "+mismatch);
            String dirt=world.getServer().computeOnServer(server->{for(int x=-24;x<=24;x++)for(int y=0;y<=12;y++)for(int z=-24;z<=24;z++)if(server.getOverworld().getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return start.add(x,y,z).toShortString();return "";});
            require(dirt.isEmpty(),"Stash left a temporary block on the server at "+dirt);
            if(homeTest){
                require(world.getServer().computeOnServer(server->{for(var pos:BlockPos.iterate(start.add(-24,0,-24),start.add(24,12,24)))if(!server.getOverworld().getFluidState(pos).isEmpty()){int cell=stash.indexAt(pos.subtract(origin),0,"None");if(cell<0||stash.state(cell).getFluidState().isEmpty())return false;}return true;}),"Fresh schematic leaked water/lava or waterlogged a dry block");
                BuilderHomeChecks.verifyCommands(checkpoint==null);
            }
            context.runOnClient(client->{BuilderPacketChecks.verify();require(builder.temporarySupports().isEmpty(),"Stash left temporary supports");require(client.currentScreen==null,"Stash left its supply menu open");require(client.player.getHealth()==client.player.getMaxHealth(),"Stash survival replay lost health");});
            if(homeTest){System.out.println("[builder-stash] PASS: "+(checkpoint==null?"fresh":"captured-stall replay")+" 710/710 native server blocks; zero temporary dirt; openings restored; liquids contained; bounded head packets; full health");context.takeScreenshot(checkpoint==null?"maro-stash-fresh-homes-complete":"maro-stash-captured-stall-complete");}
        }finally{
            context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(original));context.waitTicks(30);
        }
    }
    private static void retainedPredictions(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.south(2);
        for(String failure:List.of("rejected","timed out","paused")){
            fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 2");context.waitTicks(6);
            context.runOnClient(client->{
                builder.install(new Schematic("retained-prediction.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();
                try{
                    var plan=AutoBuilder.class.getDeclaredMethod("placement",BlockPos.class,BlockState.class,Item.class,int.class,boolean.class);plan.setAccessible(true);
                    var job=plan.invoke(builder,target,Blocks.STONE.getDefaultState(),Items.STONE,0,false);require(job!=null,"Retained prediction fixture has no placement plan");
                    var receipt=AutoBuilder.class.getDeclaredMethod("beginPlacementReceipt",job.getClass());receipt.setAccessible(true);receipt.invoke(builder,job);
                    client.world.setBlockState(target,Blocks.STONE.getDefaultState());
                    if(failure.equals("timed out")){var deadline=AutoBuilder.class.getDeclaredField("placementDeadline");deadline.setAccessible(true);deadline.setInt(builder,0);}
                    else if(failure.equals("rejected"))AutoBuilder.serverBlockUpdate(target,Blocks.AIR.getDefaultState());
                    require(client.world.getBlockState(target).isOf(Blocks.STONE),"Fixture did not retain a ghost prediction");
                    if(failure.equals("paused"))builder.pause("Paused");
                    else{var reconcile=AutoBuilder.class.getDeclaredMethod("placementReceiptTick");reconcile.setAccessible(true);reconcile.invoke(builder);}
                    require(client.world.getBlockState(target).isAir(),"Rejected/timed-out prediction retained client collision");
                    require(((Map<?,?>)field(builder,"unconfirmedPlacements")).isEmpty(),"Rejected/timed-out receipt retained uncertainty");
                    if(failure.equals("paused"))builder.startBuild();
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            await(context,builder,350);verify(world,target,1,1,1,y->Blocks.STONE);
        }
        fixture(context,world,builder,start);command(world,"setblock",start.up().west(),"stone");world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("step-out-of-placement.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.up());builder.startBuild();});
        await(context,builder,350);verify(world,start.up(),1,1,1,y->Blocks.STONE);
        context.runOnClient(client->require(builder.temporarySupports().isEmpty(),"Stepping out of an occupied target created unnecessary scaffolding"));
        fixture(context,world,builder,start);world.getServer().runCommand("give @a flower_pot 1");world.getServer().runCommand("give @a cornflower 1");context.waitTicks(6);
        context.runOnClient(client->{var pot=new Schematic("potted-plant.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.POTTED_CORNFLOWER.getDefaultState()});require(pot.materials().get(Items.FLOWER_POT)==1&&pot.materials().get(Items.CORNFLOWER)==1,"Potted flower materials omitted a component");builder.install(pot);builder.setOrigin(target);builder.startBuild();});
        await(context,builder,350);verify(world,target,1,1,1,y->Blocks.POTTED_CORNFLOWER);
        context.runOnClient(client->require(builder.remainingMaterials().values().stream().allMatch(count->count==0),"Potted flower retained material demand"));
        fixture(context,world,builder,start);world.getServer().runCommand("give @a hopper 1");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a dirt 16");context.waitTicks(6);
        var hopper=Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING,Direction.EAST);
        context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Clean Temporary Supports",false);builder.install(new Schematic("neighbour-before-scaffold.nbt","test",2,1,1,BlockPos.ORIGIN,new BlockState[]{hopper,Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();});
        await(context,builder,350);
        require(world.getServer().computeOnServer(server->AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(target),hopper)&&server.getOverworld().getBlockState(target.east()).isOf(Blocks.STONE)),"Schematic neighbour was not used to orient the hopper");
        context.runOnClient(client->{require(builder.temporarySupports().isEmpty(),"Builder scaffolded a hopper before placing its available neighbour");set(builder,"Clean Temporary Supports",true);});
        fixture(context,world,builder,start);world.getServer().runCommand("give @a yellow_shulker_box 1");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("raised-upward-shulker.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.YELLOW_SHULKER_BOX.getDefaultState()}));builder.setOrigin(target.up(2));builder.startBuild();});
        await(context,builder,700);verify(world,target.up(2),1,1,1,y->Blocks.YELLOW_SHULKER_BOX);
        require(world.getServer().computeOnServer(server->{for(int x=-4;x<=4;x++)for(int y=0;y<=4;y++)for(int z=-4;z<=4;z++)if(server.getOverworld().getBlockState(target.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Raised shulker left temporary support blocks on the server");
        fixture(context,world,builder,start);world.getServer().runCommand("give @a dark_oak_door 2");context.waitTicks(6);
        var right=Blocks.DARK_OAK_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.SOUTH).with(Properties.DOOR_HINGE,net.minecraft.block.enums.DoorHinge.RIGHT);
        var left=right.with(Properties.DOOR_HINGE,net.minecraft.block.enums.DoorHinge.LEFT);
        var doors=new BlockState[]{right,Blocks.STRUCTURE_VOID.getDefaultState(),left,right.with(Properties.DOUBLE_BLOCK_HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER),Blocks.STRUCTURE_VOID.getDefaultState(),left.with(Properties.DOUBLE_BLOCK_HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER)};
        context.runOnClient(client->{builder.install(new Schematic("door-hinge-boundaries.nbt","test",3,2,1,BlockPos.ORIGIN,doors));builder.setOrigin(target);builder.startBuild();});
        await(context,builder,500);
        require(world.getServer().computeOnServer(server->{for(int i=0;i<doors.length;i++)if(!doors[i].isOf(Blocks.STRUCTURE_VOID)&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(target.add(i%3,i/3,0)),doors[i]))return false;return true;}),"Door hinge or companion half did not match on the server");
        fixture(context,world,builder,start);world.getServer().runCommand("give @a redstone 3");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a diamond_shovel 1");
        var platform=start.south(4).up(2);for(int x=0;x<3;x++)command(world,"setblock",platform.east(x),"stone");context.waitTicks(6);
        context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("raised-floor-access.nbt","test",3,2,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.REDSTONE_WIRE.getDefaultState(),Blocks.REDSTONE_WIRE.getDefaultState(),Blocks.REDSTONE_WIRE.getDefaultState()}));builder.setOrigin(platform);builder.startBuild();});
        await(context,builder,900);verify(world,platform.up(),3,1,1,y->Blocks.REDSTONE_WIRE);
        require(world.getServer().computeOnServer(server->{for(int x=-4;x<=6;x++)for(int y=0;y<=4;y++)for(int z=-3;z<=7;z++)if(server.getOverworld().getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Raised floor access stairs were left on the server");
        fixture(context,world,builder,start);
        world.getServer().runCommand("give @a redstone 3");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a diamond_shovel 1");
        for(int x=0;x<3;x++)command(world,"setblock",platform.east(x),"stone");
        var oldSupports=Set.of(start.west(2),start.west(2).south(),start.west(2).north());
        for(var old:oldSupports)command(world,"setblock",old,"dirt");context.waitTicks(6);
        for(int z=-1;z<=1;z++)command(world,"setblock",start.west(3).south(z),"stone");
        var reuseOrigin=start.add(-3,0,-1);var reuseCells=new BlockState[6*4*6];Arrays.fill(reuseCells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int z=0;z<3;z++)reuseCells[z*6]=Blocks.STONE.getDefaultState();
        for(int x=3;x<6;x++){reuseCells[x+5*6+2*36]=Blocks.STONE.getDefaultState();reuseCells[x+5*6+3*36]=Blocks.REDSTONE_WIRE.getDefaultState();}
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(3d);builder.install(new Schematic("bounded-support-reuse.nbt","test",6,4,6,BlockPos.ORIGIN,reuseCells));builder.setOrigin(reuseOrigin);((Set<BlockPos>)field(builder,"supports")).addAll(oldSupports);builder.startBuild();});
            for(int tick=0;tick<1200&&context.computeOnClient(client->builder.building());tick++){
                context.runOnClient(client->require(builder.temporarySupports().size()<=3,"Unstuck recovery exceeded the temporary support limit"));context.waitTick();
            }
            await(context,builder,1);verify(world,platform.up(),3,1,1,y->Blocks.REDSTONE_WIRE);
            require(world.getServer().computeOnServer(server->{for(int z=-1;z<=1;z++)if(!server.getOverworld().getBlockState(start.west(3).south(z)).isOf(Blocks.STONE))return false;return true;}),"Support reuse changed a neighbouring schematic block");
            require(world.getServer().computeOnServer(server->{for(int x=-4;x<=6;x++)for(int y=0;y<=4;y++)for(int z=-3;z<=7;z++)if(server.getOverworld().getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Support reuse left obsolete dirt on the server");
        }finally{context.runOnClient(client->((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(128d));}
        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");
        var post=Set.of(start,start.up(),start.up(2));for(var piece:post)command(world,"setblock",piece,"dirt");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+3)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        var lowerTarget=start.south(5);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("owned-post-descent.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(lowerTarget);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(post);builder.startBuild();
        });
        await(context,builder,900);verify(world,lowerTarget,1,1,1,y->Blocks.STONE);
        require(world.getServer().computeOnServer(server->post.stream().allMatch(piece->server.getOverworld().getBlockState(piece).isAir())),"Stranded post descent left temporary dirt behind");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Owned post descent caused damage or lost cleanup ownership"));
        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");command(world,"setblock",start,"dirt");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+1.03)+" "+(start.getY()+1)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("post-edge-descent.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(lowerTarget);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(start);builder.startBuild();
        });
        await(context,builder,700);verify(world,lowerTarget,1,1,1,y->Blocks.STONE);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(start).isAir()),"Post-edge descent did not clean its support");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Post-edge descent caused damage or lost cleanup ownership"));
        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");
        var island=start.up(3);var descentPieces=Set.of(island.east(),island.east().down(2));
        command(world,"setblock",island,"stone");for(var piece:descentPieces)command(world,"setblock",piece,"dirt");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+4)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("isolated-scaffold-descent.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(lowerTarget);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(descentPieces);builder.startBuild();
        });
        await(context,builder,1000);verify(world,lowerTarget,1,1,1,y->Blocks.STONE);
        require(world.getServer().computeOnServer(server->descentPieces.stream().allMatch(piece->server.getOverworld().getBlockState(piece).isAir())&&server.getOverworld().getBlockState(island).isOf(Blocks.STONE)),"Scaffold descent changed an unrelated block or left dirt");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Scaffold descent caused damage or lost cleanup ownership"));
        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");
        var highLedge=start.up(6);var lowerPost=new HashSet<BlockPos>();
        command(world,"setblock",highLedge,"stone");for(int height=0;height<5;height++){var piece=start.east().up(height);lowerPost.add(piece);command(world,"setblock",piece,"dirt");}
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+7)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("lower-post-descent.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(lowerTarget);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(lowerPost);builder.startBuild();
        });
        await(context,builder,1000);verify(world,lowerTarget,1,1,1,y->Blocks.STONE);
        require(world.getServer().computeOnServer(server->lowerPost.stream().allMatch(piece->server.getOverworld().getBlockState(piece).isAir())&&server.getOverworld().getBlockState(highLedge).isOf(Blocks.STONE)),"Lower-post descent changed the finished ledge or left supports");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Lower-post descent caused damage or lost cleanup ownership"));
        fixture(context,world,builder,start);var lastPost=start.up(2);var lastTarget=start.south(3);
        command(world,"setblock",lastPost,"dirt");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+3)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("three-block-post-descent.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(lastTarget);
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(lastPost);builder.startBuild();
        });
        await(context,builder,600);verify(world,lastTarget,1,1,1,y->Blocks.STONE);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(lastPost).isAir()),"Final post was not removed before descent");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Three-block post descent caused damage or left a support"));
        hopperCrossing(context,world,builder,start);
        fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start)+" "+coords(start.up(3))+" stone");
        for(int distance=1;distance<=3;distance++)world.getServer().runCommand("fill "+coords(start.north(distance))+" "+coords(start.north(distance).up(3-distance))+" stone");
        var underside=start.south(3).up(3);command(world,"setblock",underside.up(),"stone");world.getServer().runCommand("give @a stone 1");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(start.up(4)));context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{builder.install(new Schematic("lower-standing-view.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(underside);builder.startBuild();});
        await(context,builder,700);verify(world,underside,1,1,1,y->Blocks.STONE);
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Lower viewpoint route fell or created unnecessary supports"));
        fixture(context,world,builder,start);
        command(world,"setblock",start,"stone");
        world.getServer().runCommand("fill "+coords(start.west(3).up(2))+" "+coords(start.west().up(2))+" stone");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(start.up()));context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        var walk=context.computeOnClient(client->new dev.maro.builder.BuilderWalk());var underCeiling=start.west(3);
        boolean arrived=false;
        try{
            for(int tick=0;tick<400;tick++){
                arrived=context.computeOnClient(client->nativeStand(walk,underCeiling));if(arrived)break;context.waitTick();
            }
            require(arrived,"Walking descent stuck against a lower cell's ceiling");
            context.runOnClient(client->require(client.player.getHealth()==20,"Low ceiling descent damaged the player"));
        }finally{context.runOnClient(client->walk.stop());}
        for(var facing:List.of(Direction.NORTH,Direction.SOUTH)){
            fixture(context,world,builder,start);var trap=start.south(3).up(4);
            world.getServer().runCommand("fill "+coords(trap.add(-2,-2,-2))+" "+coords(trap.add(2,-1,2))+" stone");
            command(world,"setblock",trap.down(),"air");
            command(world,"setblock",trap.offset(facing.getOpposite()),"stone");
            command(world,"setblock",trap.offset(facing),"dirt");command(world,"setblock",trap.west(),"dirt");command(world,"setblock",trap.east(),"dirt");
            world.getServer().runCommand("give @a warped_trapdoor 1");
            world.getServer().runCommand("gamemode creative @a");var perch=trap.offset(facing.getOpposite()).up();
            world.getServer().runCommand("tp @a "+(perch.getX()+.5)+" "+perch.getY()+" "+(perch.getZ()+.5));context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
            var expected=Blocks.WARPED_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.BLOCK_HALF,net.minecraft.block.enums.BlockHalf.BOTTOM).with(Properties.OPEN,true);
            context.runOnClient(client->{set(builder,"Auto Unstuck",false);set(builder,"Temporary Supports",true);builder.install(new Schematic("crowded-trapdoor.nbt","test",1,2,1,BlockPos.ORIGIN,new BlockState[]{Blocks.WATER.getDefaultState(),expected}));builder.setOrigin(trap.down());builder.toggleMaterialIgnored(Items.WATER_BUCKET);builder.startBuild();});
            await(context,builder,600);
            require(world.getServer().computeOnServer(server->AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(trap),expected)),"Crowded trapdoor did not use an alternative attachment face");
            context.runOnClient(client->{if(builder.materialIgnored(Items.WATER_BUCKET))builder.toggleMaterialIgnored(Items.WATER_BUCKET);});
        }
        fixture(context,world,builder,start);var cutter=start.south(2);var slab=cutter.east();
        command(world,"setblock",cutter,"stonecutter");world.getServer().runCommand("give @a stone_slab 1");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(cutter.getX()+1.1)+" "+(cutter.getY()+.5625)+" "+(cutter.getZ()+.5));context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        context.runOnClient(client->{set(builder,"Auto Move",false);set(builder,"Auto Unstuck",false);builder.install(new Schematic("partial-collision-placement.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE_SLAB.getDefaultState()}));builder.setOrigin(slab);builder.startBuild();});
        await(context,builder,350);verify(world,slab,1,1,1,y->Blocks.STONE_SLAB);
    }
    private static void observerAssembly(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        String previous=context.computeOnClient(client->((dev.maro.setting.ModeSetting)field(builder,"supplyMode")).get());
        try{for(String supply:List.of("Layer by Layer","Nearby Sections")){
            fixture(context,world,builder,start);var origin=start.south(3);
            world.getServer().runCommand("fill "+coords(origin)+" "+coords(origin.add(6,0,3))+" stone");
            for(var floor:List.of(origin.add(3,1,0),origin.add(4,1,0),origin.add(5,1,0),origin.add(3,1,1)))command(world,"setblock",floor,"stone");
            for(int z=0;z<3;z++)command(world,"setblock",origin.add(2,1,z),"stone");
            command(world,"setblock",origin.add(2,1,3),"sticky_piston[facing=east]");
            command(world,"setblock",origin.add(3,2,0),"stone");
            for(int z=0;z<4;z++)command(world,"setblock",origin.add(2,2,z),"redstone_wire");
            // An early observer powers this piston on each note change, destroying
            // the box. The observer is nearer than the note at the same height.
            var box=origin.add(3,1,3);var observer=origin.add(4,2,0);var note=origin.add(5,2,0);
            world.getServer().runCommand("give @a yellow_shulker_box 1");world.getServer().runCommand("give @a observer 1");world.getServer().runCommand("give @a note_block 1");
            world.getServer().runCommand("gamemode creative @a");var perch=origin.add(3,2,1);
            world.getServer().runCommand("tp @a "+(perch.getX()+.5)+" "+perch.getY()+" "+(perch.getZ()+.5));context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
            BlockState[] cells=new BlockState[7*3*4];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
            cells[3+3*7+1*7*4]=Blocks.YELLOW_SHULKER_BOX.getDefaultState();
            cells[4+2*7*4]=Blocks.OBSERVER.getDefaultState().with(Properties.FACING,Direction.EAST);
            cells[5+2*7*4]=Blocks.NOTE_BLOCK.getDefaultState().with(Properties.NOTE,12);
            context.runOnClient(client->{set(builder,"Material Supply",supply);builder.install(new Schematic("observer-machine.nbt","test",7,3,4,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.startBuild();});
            await(context,builder,1000);context.waitTicks(20);
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(box).isOf(Blocks.YELLOW_SHULKER_BOX)&&server.getOverworld().getBlockState(observer).isOf(Blocks.OBSERVER)&&server.getOverworld().getBlockState(note).get(Properties.NOTE)==12),"Observer activated the unfinished machine and destroyed its shulker box");
        }}finally{context.runOnClient(client->set(builder,"Material Supply",previous));}
    }
    private static void distantChest(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos original){
        var start=new BlockPos(-26210,61,-150577);var chest=start.east(3);var target=start.south(2);
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(start));context.waitTicks(30);fixture(context,world,builder,start);
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 64");
        world.getServer().runCommand("item replace entity @a weapon.offhand with totem_of_undying");context.waitTicks(6);
        try{
            context.runOnClient(client->{
                builder.install(new Schematic("distant-west-chest.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();
                client.player.setVelocity(.03,0,0);BuilderPacketChecks.begin();builder.startBuild();
            });
            await(context,builder,500);verify(world,target,1,1,1,y->Blocks.STONE);
            context.runOnClient(client->{BuilderPacketChecks.verify();require(client.currentScreen==null,"Distant chest remained open");});
        }finally{
            context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(original));context.waitTicks(30);
        }
    }
    private static void raisedChestReturn(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Raised ledge, rejected first chest-open and stored tools");
        fixture(context,world,builder,start);
        var chest=start.east(3);var ledge=start.up(6);var lowerPost=new HashSet<BlockPos>();
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 64");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.1 with diamond_pickaxe");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.2 with diamond_shovel");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.3 with dirt 64");
        command(world,"setblock",ledge,"stone");for(int y=0;y<5;y++){var piece=start.east().up(y);lowerPost.add(piece);command(world,"setblock",piece,"dirt");}
        world.getServer().runCommand("give @a dirt 16");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+7)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        var firstOpenDenied=new java.util.concurrent.atomic.AtomicBoolean();var gate=new java.util.concurrent.atomic.AtomicBoolean(true);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClient()&&gate.get()&&(hit.getBlockPos().equals(chest)||hit.getBlockPos().equals(chest.south()))&&firstOpenDenied.compareAndSet(false,true))return net.minecraft.util.ActionResult.FAIL;
            return net.minecraft.util.ActionResult.PASS;
        });
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);set(builder,"Auto Buy Tools",true);set(builder,"Auto Buy When Missing",true);builder.auctionBudget(1000);
                builder.install(new Schematic("raised-chest-return.nbt","test",2,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(ledge);
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(lowerPost);
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();BuilderPacketChecks.begin();builder.startBuild();
            });
            for(int tick=0;tick<2000&&context.computeOnClient(client->builder.building());tick++){
                context.runOnClient(client->require(!builder.buying(),"Uninspected selected chest triggered duplicate auction buying"));context.waitTick();
            }
            context.runOnClient(client->{
                require(builder.status().equals("Build complete"),"Raised chest return did not resume building: "+builder.status());
                require(firstOpenDenied.get(),"Chest retry fixture did not reject its first open");
                require(builder.inventoryCount(Items.DIAMOND_PICKAXE)==1&&builder.inventoryCount(Items.DIAMOND_SHOVEL)==1,"Raised chest return missed stored tools");
                require(client.currentScreen==null&&builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Raised chest return left a screen/support or caused damage");BuilderPacketChecks.verify();
            });
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();if(!level.getBlockState(ledge).isOf(Blocks.STONE)||!level.getBlockState(ledge.east()).isOf(Blocks.STONE))return false;var stored=(net.minecraft.inventory.Inventory)level.getBlockEntity(chest);if(stored.getStack(0).getCount()!=63)return false;for(int x=-5;x<=5;x++)for(int y=0;y<=9;y++)for(int z=-5;z<=5;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Raised chest return did not withdraw exactly the missing stone or clean all temporary dirt");
        }finally{gate.set(false);context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void sealedChestReturn(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Sealed build, selected storage and access floor restoration");
        fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(-5,0,-5))+" "+coords(start.add(8,10,5))+" air");
        var origin=start.add(-2,4,-2);var chest=start.east(6);var cells=new BlockState[100];
        for(int y=0;y<4;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)cells[x+z*5+y*25]=(y==0||y==3||x==0||x==4||z==0||z==4)?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState();
        var room=new Schematic("sealed-chest-return.nbt","test",5,4,5,BlockPos.ORIGIN,cells);
        world.getServer().runOnServer(server->{for(int i=0;i<room.size();i++)if(i!=75&&!room.state(i).isAir())server.getOverworld().setBlockState(origin.add(room.local(i)),room.state(i),net.minecraft.block.Block.NOTIFY_ALL);});
        var posts=Set.of(start,start.up(),start.up(3));for(var post:posts)command(world,"setblock",post,"dirt");
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 64");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.1 with diamond_pickaxe");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.2 with diamond_shovel");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.3 with dirt 64");
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+5)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);set(builder,"Auto Buy Tools",true);set(builder,"Auto Buy When Missing",true);builder.auctionBudget(1000);
                builder.install(room);builder.setOrigin(origin);@SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(posts);
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();BuilderPacketChecks.begin();builder.startBuild();
            });
            for(int tick=0;tick<2400&&context.computeOnClient(client->builder.building());tick++){
                context.runOnClient(client->require(!builder.buying(),"Sealed build bought materials before reading selected storage"));context.waitTick();
                if(tick%200==0){String progress=context.computeOnClient(client->"[room-progress] "+field(builder,"correct")+"/82 "+builder.status()+" player="+client.player.getEntityPos()+" nav="+field(builder,"navigatingCell")+" openings="+field(builder,"floorAccessWork"));System.out.println(progress);}
            }
            context.runOnClient(client->{
                require(builder.status().equals("Build complete"),"Sealed build did not return from storage: "+builder.status());
                require(builder.inventoryCount(Items.DIAMOND_PICKAXE)==1&&builder.inventoryCount(Items.DIAMOND_SHOVEL)==1,"Sealed build missed stored tools");
                require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Sealed build damaged player or left a menu/support");BuilderPacketChecks.verify();
            });
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<room.size();i++)if(!room.state(i).isAir()&&!level.getBlockState(origin.add(room.local(i))).isOf(Blocks.STONE))return false;for(int x=-5;x<=8;x++)for(int y=0;y<=10;y++)for(int z=-5;z<=5;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Sealed build left its access opening or temporary blocks behind");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void sealedDirectionalAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        sealedDirectionalAccess(context,world,builder,start,0);
        sealedDirectionalAccess(context,world,builder,start,3);
    }
    private static void sealedDirectionalAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,int raise){
        System.out.println("[builder-check] Directional shulker inside sealed wall, checked passage and restoration; raise="+raise);
        fixture(context,world,builder,start);
        int span=raise==0?5:128,height=raise==0?4:32;
        var origin=start.add(-2,raise,-2);var cells=new BlockState[span*height*span];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(int y=0;y<4;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)cells[x+z*span+y*span*span]=(y==0||y==3||x==0||x==4||z==0||z==4)?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState();
        int missing=3+2*span+span*span;cells[missing]=Blocks.CYAN_SHULKER_BOX.getDefaultState().with(net.minecraft.block.ShulkerBoxBlock.FACING,Direction.WEST);
        var room=new Schematic("sealed-directional-access.nbt","test",span,height,span,BlockPos.ORIGIN,cells);
        world.getServer().runOnServer(server->{for(int i=0;i<room.size();i++)if(i!=missing&&room.included(i)&&!room.state(i).isAir())server.getOverworld().setBlockState(origin.add(room.local(i)),room.state(i),net.minecraft.block.Block.NOTIFY_ALL);});
        for(String item:List.of("stone 16","cyan_shulker_box","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        if(raise>0)world.getServer().runCommand("give @a dirt 16");
        world.getServer().runCommand("tp @a "+(start.getX()+3.5)+" "+start.getY()+" "+(start.getZ()+.5));context.waitTicks(12);
        try{
            context.runOnClient(client->{
                var walk=new dev.maro.builder.BuilderWalk();var from=client.player.getBlockPos();var inside=raise==0?start.up():start.up(raise+1).south();var bottom=raise==0?start.east(2).up():start.add(2,raise+1,1);var hole=Set.of(bottom,bottom.up());
                require(!walk.canReachStandFrom(from,inside),"Sealed fixture already has an ordinary route");
                if(raise==0)require(walk.canReachAfterClearing(from,inside,hole),"Checked wall passage did not expose its route");
                else{
                    var column=start.add(3,raise+1,1);
                    require(!walk.canReachFromPillar(column,inside)&&walk.canReachFromPillarAfterClearing(column,inside,hole),"Raised room does not require combined column / door recovery");
                    require(!walk.canReachFromPillar(column,inside)&&client.world.getBlockState(column.down()).isAir(),"Combined proof leaked its pillar / door mask");
                    set(builder,"Temporary Supports",true);
                }
                require(!walk.canReachStandFrom(from,inside)&&hole.stream().allMatch(p->client.world.getBlockState(p).isOf(Blocks.STONE)),"Feasibility query modified blocks or retained its collision mask");
                set(builder,"Material Supply","Nearby Sections");builder.install(room);builder.setOrigin(origin);BuilderPacketChecks.begin();builder.startBuild();
            });
            await(context,builder,raise==0?2400:4000);
            context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Sealed directional build left damage/menu/supports");BuilderPacketChecks.verify();});
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<room.size();i++)if(room.included(i)&&!room.state(i).isAir()&&!level.getBlockState(origin.add(room.local(i))).equals(room.state(i)))return false;for(var pos:BlockPos.iterate(start.add(-8,0,-8),start.add(8,raise+4,8)))if(level.getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Directional shulker or passage wall was not restored, or temporary dirt remains on the server");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void sealedBuildEscape(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        sealedBuildEscape(context,world,builder,start,true);
        sealedBuildEscape(context,world,builder,start,false);
    }
    private static void sealedBuildEscape(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean cleanupOnly){
        System.out.println("[builder-check] Sealed build escape without a restock trip, three-block landing; cleanup-only="+cleanupOnly);
        fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(-5,0,-5))+" "+coords(start.add(8,10,5))+" air");
        var origin=start.add(-2,4,-2);var cells=new BlockState[100];
        for(int y=0;y<4;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)cells[x+z*5+y*25]=(y==0||y==3||x==0||x==4||z==0||z==4)?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState();
        var room=new Schematic("sealed-build-escape.nbt","test",5,4,5,BlockPos.ORIGIN,cells);
        world.getServer().runOnServer(server->{for(int i=0;i<room.size();i++)if((cleanupOnly||i!=75)&&!room.state(i).isAir())server.getOverworld().setBlockState(origin.add(room.local(i)),room.state(i),net.minecraft.block.Block.NOTIFY_ALL);});
        // CI 412 finished every room cell, then stranded this four-post column
        // below the closed floor. Reproduce that actual final geometry directly.
        var postBase=start.west(3).north();
        var posts=cleanupOnly?Set.of(postBase,postBase.up(),postBase.up(2),postBase.up(3)):Set.of(start,start.up());for(var post:posts)command(world,"setblock",post,"dirt");
        for(String item:List.of("stone 64","dirt 64","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+(cleanupOnly?-.580728838180052:.5))+" "+(start.getY()+5)+" "+(start.getZ()+(cleanupOnly?-.458279088022074:.5)));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);set(builder,"Auto Buy When Missing",false);set(builder,"Material Supply","Nearby Sections");builder.install(room);builder.setOrigin(origin);
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(posts);BuilderPacketChecks.begin();builder.startBuild();
            });
            await(context,builder,2400);
            context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Sealed build escape left damage/menu/supports");BuilderPacketChecks.verify(cleanupOnly?1:2);});
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<room.size();i++)if(!room.state(i).isAir()&&!level.getBlockState(origin.add(room.local(i))).isOf(Blocks.STONE))return false;for(int x=-5;x<=8;x++)for(int y=0;y<=10;y++)for(int z=-5;z<=5;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Sealed build escape did not restore its floor or clear temporary dirt");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    /** A finished floor above a clear column must admit a checked upward entry. */
    private static void ceilingColumnEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean cleanupOnly:new boolean[]{false,true})ceilingColumnEntry(context,world,builder,start,cleanupOnly);
    }
    private static void ceilingColumnEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean cleanupOnly){
        System.out.println("[builder-check] Checked ceiling column entry, native placement and full opening/support restoration; cleanup-only="+cleanupOnly);
        fixture(context,world,builder,start);var origin=start.add(-3,4,-3);var cells=new BlockState[196];Arrays.fill(cells,Blocks.AIR.getDefaultState());
        for(int y=0;y<4;y++)for(int z=0;z<7;z++)for(int x=0;x<7;x++)if(y==0||y==3||x==0||x==6||z==0||z==6)cells[x+z*7+y*49]=Blocks.STONE.getDefaultState();
        int target=5+3*7+49;cells[target]=cleanupOnly?Blocks.AIR.getDefaultState():Blocks.STONE.getDefaultState();var room=new Schematic("ceiling-column-entry.nbt","test",7,4,7,BlockPos.ORIGIN,cells);
        world.getServer().runOnServer(server->{for(int i=0;i<room.size();i++)if(i!=target&&!room.state(i).isAir())server.getOverworld().setBlockState(origin.add(room.local(i)),room.state(i),net.minecraft.block.Block.NOTIFY_ALL);});
        // Unrelated bedrock prevents an exterior column from bypassing this entry.
        for(int side=-4;side<=4;side++)for(int y=0;y<=10;y++)for(var pos:List.of(start.add(-4,y,side),start.add(4,y,side),start.add(side,y,-4),start.add(side,y,4)))command(world,"setblock",pos,"bedrock");
        for(String item:List.of("stone 64","dirt 64","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);context.waitTicks(12);
        var oldPosts=cleanupOnly?Set.of(origin.add(room.local(target)),start,start.west(2),start.west(2).south()):Set.<BlockPos>of();
        if(cleanupOnly){
            for(var post:oldPosts)command(world,"setblock",post,"dirt");
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+1)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        }
        double oldLimit=context.computeOnClient(client->((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).get());
        boolean opened=false;
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Auto Buy When Missing",false);set(builder,"Material Supply","Nearby Sections");builder.install(room);builder.setOrigin(origin);if(cleanupOnly){((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(5d);((Set<BlockPos>)field(builder,"supports")).addAll(oldPosts);}BuilderPacketChecks.begin();builder.startBuild();});
            for(int tick=0;tick<3600&&context.computeOnClient(client->builder.building());tick++){
                if(context.computeOnClient(client->!((Map<?,?>)field(builder,"floorAccessWork")).isEmpty()))opened=true;
                if(cleanupOnly)context.runOnClient(client->{require(builder.temporarySupports().size()<=5,"Ceiling cleanup exceeded its whole-column support budget");if(((Set<?>)field(builder,"accessSupports")).contains(start))require(client.world.getBlockState(start).isOf(Blocks.DIRT),"Ceiling cleanup recycled its committed base");});
                context.waitTick();
            }
            await(context,builder,1);require(opened,"Ceiling fixture never opened its checked entry");
            context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Ceiling entry left damage/menu/supports");BuilderPacketChecks.verify();});
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<room.size();i++)if(!room.state(i).isAir()&&!level.getBlockState(origin.add(room.local(i))).equals(room.state(i)))return false;for(var pos:BlockPos.iterate(start.add(-4,0,-4),start.add(4,10,4)))if(level.getBlockState(pos).isOf(Blocks.DIRT))return false;for(int side=-4;side<=4;side++)for(int y=0;y<=10;y++)for(var pos:List.of(start.add(-4,y,side),start.add(4,y,side),start.add(side,y,-4),start.add(side,y,4)))if(!level.getBlockState(pos).isOf(Blocks.BEDROCK))return false;return true;}),"Ceiling entry failed to restore its opening or changed unrelated walls");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(oldLimit);});}
    }
    /** Cleanup of a schematic-air post must still escape through its finished floor. */
    private static void airSupportFloorExit(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Clean schematic-air roof support using a real mining view; restore checked access openings");
        fixture(context,world,builder,start);var origin=start.add(-2,0,-2);var cells=new BlockState[175];Arrays.fill(cells,Blocks.AIR.getDefaultState());
        for(int y=2;y<=5;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)if(y==2||y==5||x==0||x==4||z==0||z==4)cells[x+z*5+y*25]=Blocks.STONE.getDefaultState();
        var room=new Schematic("cleanup-air-floor-exit.nbt","test",5,7,5,BlockPos.ORIGIN,cells);
        world.getServer().runOnServer(server->{for(int cell=0;cell<room.size();cell++)if(!room.state(cell).isAir())server.getOverworld().setBlockState(origin.add(room.local(cell)),room.state(cell),net.minecraft.block.Block.NOTIFY_ALL);});
        var posts=Set.of(start,start.up(),start.up(6));for(var post:posts)command(world,"setblock",post,"dirt");
        for(String item:List.of("stone 64","dirt 64","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+3)+" "+(start.getZ()+.5));context.waitTicks(12);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Auto Buy When Missing",false);set(builder,"Material Supply","Nearby Sections");builder.install(room);builder.setOrigin(origin);((Set<BlockPos>)field(builder,"supports")).addAll(posts);BuilderPacketChecks.begin();builder.startBuild();});
            await(context,builder,3600);
            context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Schematic-air floor escape left damage/menu/supports");BuilderPacketChecks.verify();});
            require(world.getServer().computeOnServer(server->{for(int cell=0;cell<room.size();cell++)if(!room.state(cell).isAir()&&!server.getOverworld().getBlockState(origin.add(room.local(cell))).equals(room.state(cell)))return false;for(var pos:BlockPos.iterate(start.add(-8,0,-8),start.add(8,10,8)))if(server.getOverworld().getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Schematic-air cleanup left an open floor or temporary dirt");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void stalledInteractions(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.add(0,0,2);var redirected=new java.util.concurrent.atomic.AtomicBoolean();var gate=new java.util.concurrent.atomic.AtomicBoolean(true);
        // A server/plugin opens an unexpected inventory instead of accepting our placement.
        // The old builder waited behind this screen until the user pressed Escape.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClient()&&gate.get()&&hit.getBlockPos().equals(target.down())&&player.getStackInHand(hand).isOf(Items.STONE)&&redirected.compareAndSet(false,true)){
                var serverPlayer=(net.minecraft.server.network.ServerPlayerEntity)player;
                serverPlayer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((id,inventory,p)->net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(id,inventory),Text.literal("Unexpected chest")));
                serverPlayer.playerScreenHandler.syncState();return net.minecraft.util.ActionResult.FAIL;
            }return net.minecraft.util.ActionResult.PASS;
        });
        try{
            world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
            context.runOnClient(client->{builder.install(new Schematic("unexpected-chest.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();});
            await(context,builder,450);verify(world,target,1,1,1,y->Blocks.STONE);
            require(redirected.get(),"Unexpected chest fixture did not redirect the placement");
            context.runOnClient(client->require(client.currentScreen==null&&client.player.currentScreenHandler==client.player.playerScreenHandler,"Builder left its unexpected chest open"));
        }finally{gate.set(false);}

        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        // Reconcile an acknowledgement-only rollback: no handleBlockUpdate callback is emitted.
        context.runOnClient(client->{
            builder.install(new Schematic("ack-only-rollback.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();
            try{
                var plan=AutoBuilder.class.getDeclaredMethod("placement",BlockPos.class,BlockState.class,Item.class,int.class,boolean.class);plan.setAccessible(true);
                var job=plan.invoke(builder,target,Blocks.STONE.getDefaultState(),Items.STONE,0,false);require(job!=null,"Ack fixture has no placement plan");
                var receipt=AutoBuilder.class.getDeclaredMethod("beginPlacementReceipt",job.getClass());receipt.setAccessible(true);receipt.invoke(builder,job);
                client.world.setBlockState(target,Blocks.STONE.getDefaultState());
                client.world.processPendingUpdate(target,Blocks.AIR.getDefaultState(),client.player.getEntityPos());
                require(field(builder,"pendingServerState")==Blocks.AIR.getDefaultState(),"Sequence acknowledgement was not received by builder");
                require(((Map<?,?>)field(builder,"unconfirmedPlacements")).isEmpty(),"Rejected prediction remained marked uncertain");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        await(context,builder,350);verify(world,target,1,1,1,y->Blocks.STONE);

        fixture(context,world,builder,start);
        var chest=start.east(3);command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",chest.east(),"chest[facing=north,type=right]");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 64");context.waitTicks(6);
        context.runOnClient(client->{builder.install(new Schematic("held-restock-item.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();BuilderPacketChecks.begin();builder.startBuild();});
        for(int i=0;i<250&&context.computeOnClient(client->field(builder,"ownedHandler")==null);i++)context.waitTick();
        context.runOnClient(client->require(field(builder,"ownedHandler")!=null,"Restock fixture did not open its chest"));
        world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.currentScreenHandler.setCursorStack(new ItemStack(Items.STONE,4));player.currentScreenHandler.syncState();});
        await(context,builder,450);verify(world,target,1,1,1,y->Blocks.STONE);
        context.runOnClient(client->BuilderPacketChecks.verify());
        context.runOnClient(client->require(client.currentScreen==null&&client.player.currentScreenHandler.getCursorStack().isEmpty(),"Held restock item left the chest latched open"));
        require(world.getServer().computeOnServer(server->{var level=server.getOverworld();var player=server.getPlayerManager().getPlayerList().getFirst();int count=0;for(var half:List.of(chest,chest.east())){var inventory=(net.minecraft.block.entity.ChestBlockEntity)level.getBlockEntity(half);for(int i=0;i<inventory.size();i++)if(inventory.getStack(i).isOf(Items.STONE))count+=inventory.getStack(i).getCount();}for(int i=0;i<36;i++)if(player.getInventory().getStack(i).isOf(Items.STONE))count+=player.getInventory().getStack(i).getCount();return count==67;}),"Cursor recovery lost or duplicated stone");

        fixture(context,world,builder,start);command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",chest.east(),"chest[facing=north,type=right]");context.waitTicks(6);
        var denied=new java.util.concurrent.atomic.AtomicBoolean();var denyGate=new java.util.concurrent.atomic.AtomicBoolean(true);var chestInventory=new net.minecraft.inventory.SimpleInventory(54);chestInventory.setStack(0,new ItemStack(Items.STONE,64));
        var resync=new java.util.concurrent.atomic.AtomicReference<net.minecraft.server.network.ServerPlayerEntity>();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{var player=resync.getAndSet(null);if(player!=null)player.currentScreenHandler.syncState();});
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClient()&&denyGate.get()&&(hit.getBlockPos().equals(chest)||hit.getBlockPos().equals(chest.east()))){
                ((net.minecraft.server.network.ServerPlayerEntity)player).openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((id,inventory,p)->new net.minecraft.screen.GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X6,id,inventory,chestInventory,6){
                    @Override public void onSlotClick(int slot,int button,net.minecraft.screen.slot.SlotActionType type,net.minecraft.entity.player.PlayerEntity clicking){
                        if(slot==0&&type==net.minecraft.screen.slot.SlotActionType.PICKUP&&denied.compareAndSet(false,true)){resync.set((net.minecraft.server.network.ServerPlayerEntity)clicking);return;}
                        super.onSlotClick(slot,button,type,clicking);
                    }
                },Text.literal("Large Chest")));return net.minecraft.util.ActionResult.SUCCESS;
            }return net.minecraft.util.ActionResult.PASS;
        });
        try{
            context.runOnClient(client->{builder.install(new Schematic("denied-chest-pickup.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();builder.startBuild();});
            await(context,builder,500);verify(world,target,1,1,1,y->Blocks.STONE);require(denied.get()&&chestInventory.getStack(0).getCount()==63,"Rejected chest pickup did not retry the exact quantity");
        }finally{denyGate.set(false);}

        fixture(context,world,builder,start);world.getServer().runCommand("give @a waxed_oxidized_copper_trapdoor 1");world.getServer().runCommand("give @a ender_chest 1");world.getServer().runCommand("give @a waxed_oxidized_copper_bulb 1");context.waitTicks(6);
        var trapdoor=Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.WEST);
        var ender=Blocks.ENDER_CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.EAST);
        context.runOnClient(client->{builder.install(new Schematic("directional-aim.nbt","test",3,1,1,BlockPos.ORIGIN,new BlockState[]{trapdoor,ender,Blocks.WAXED_OXIDIZED_COPPER_BULB.getDefaultState()}));builder.setOrigin(target);client.player.setYaw(138);builder.startBuild();});
        await(context,builder,600);
        require(world.getServer().computeOnServer(server->AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(target),trapdoor)&&AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(target.east()),ender)),"Server received a stale orientation for directional blocks");

        fixture(context,world,builder,start);command(world,"setblock",target,"dirt");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Mine Out Schematic",true);builder.install(new Schematic("scaffold-layer-dependency.nbt","test",1,2,1,BlockPos.ORIGIN,new BlockState[]{Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
            @SuppressWarnings("unchecked") var supports=(Set<BlockPos>)field(builder,"supports");supports.add(target);builder.startBuild();
        });
        await(context,builder,450);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isAir()&&server.getOverworld().getBlockState(target.up()).isOf(Blocks.STONE)),"Own scaffold air cell blocked the upper layer or was not cleaned");

        fixture(context,world,builder,start);var obstruction=start.south().up();var routeTarget=start.south(4);
        command(world,"setblock",obstruction,"dirt");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        context.runOnClient(client->{
            builder.install(new Schematic("stuck-existing-route.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(routeTarget);
            @SuppressWarnings("unchecked") var supports=(Set<BlockPos>)field(builder,"supports");supports.add(obstruction);builder.startBuild();
            var walker=(BuilderWalk)field(builder,"walker");walker.approach(routeTarget,1.5);walker.requestRecovery();
            try{var stalled=BuilderWalk.class.getDeclaredField("movementStalled");stalled.setAccessible(true);stalled.setBoolean(walker,true);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            require(!walker.routeUnavailable(),"Route collision fixture must have a path");
        });
        await(context,builder,500);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(obstruction).isAir()&&server.getOverworld().getBlockState(routeTarget).isOf(Blocks.STONE)),"Stuck route did not clear the builder's obstructing overhead dirt");

        fixture(context,world,builder,start);world.getServer().runCommand("give @a oak_sign 1");world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        var sign=Blocks.OAK_SIGN.getDefaultState().with(Properties.ROTATION,8);
        context.runOnClient(client->{builder.install(new Schematic("sign-editor-resume.nbt","test",2,1,1,BlockPos.ORIGIN,new BlockState[]{sign,Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();});
        await(context,builder,450);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(Blocks.OAK_SIGN)&&server.getOverworld().getBlockState(target.east()).isOf(Blocks.STONE)),"Sign editor prevented placing the next block");
        context.runOnClient(client->require(client.currentScreen==null,"Builder left the sign editor open"));

        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a glass 1");world.getServer().runCommand("give @a oak_planks 1");context.waitTicks(6);
        var area=new BlockState[26];Arrays.fill(area,Blocks.STRUCTURE_VOID.getDefaultState());area[0]=Blocks.STONE.getDefaultState();area[13]=Blocks.GLASS.getDefaultState();area[12]=Blocks.OAK_PLANKS.getDefaultState();
        context.runOnClient(client->{set(builder,"Material Supply","Nearby Sections");builder.install(new Schematic("nearby-sections.nbt","test",13,2,1,BlockPos.ORIGIN,area));builder.setOrigin(target);builder.startBuild();});
        for(int i=0;i<300&&!context.computeOnClient(client->builder.state(13)==AutoBuilder.CORRECT);i++)context.waitTick();
        context.runOnClient(client->{require(builder.state(13)==AutoBuilder.CORRECT,"Nearby section did not finish its upper block");require(builder.state(12)!=AutoBuilder.CORRECT,"Nearby mode walked across the whole bottom layer first");require(builder.remainingMaterials().getOrDefault(Items.OAK_PLANKS,0)<=1,"Section supply exceeded its missing material quantity");});
        await(context,builder,500);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(Blocks.STONE)&&server.getOverworld().getBlockState(target.up()).isOf(Blocks.GLASS)&&server.getOverworld().getBlockState(target.east(12)).isOf(Blocks.OAK_PLANKS)),"Nearby sections did not complete all areas");
        context.runOnClient(client->set(builder,"Material Supply","Layer by Layer"));
    }
    static Object field(AutoBuilder builder,String name){try{var field=AutoBuilder.class.getDeclaredField(name);field.setAccessible(true);return field.get(builder);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void largeSupply(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        // Exercise the actual incremental scanner and inventory batching on a
        // much larger volume, without pretending this is a completed large build.
        int width=100,height=25,length=100;
        var cells=new BlockState[width*height*length];Arrays.fill(cells,Blocks.STONE.getDefaultState());
        var large=new Schematic("large-supply.nbt","test",width,height,length,BlockPos.ORIGIN,cells);
        require(large.solidCount()==250_000&&large.materials().get(Items.STONE)==250_000,"Large schematic material count overflowed");
        context.runOnClient(client->{set(builder,"Material Supply","Nearby Sections");builder.install(large);builder.setOrigin(start.add(-50,8,-50));builder.preview();});
        for(int tick=0;tick<500&&context.computeOnClient(client->(int)field(builder,"completedScans")==0);tick++)context.waitTick();
        context.runOnClient(client->{
            require((int)field(builder,"completedScans")>0,"Large schematic scan did not finish incrementally");
            require(((List<?>)field(builder,"workCells")).size()<=256,"Large scan expanded its bounded work queue");
            var batch=builder.remainingMaterials();int blocks=batch.values().stream().mapToInt(Integer::intValue).sum();
            require(blocks>0&&blocks<=128,"Large schematic requested the whole build instead of a bounded section");
            int stacks=batch.entrySet().stream().mapToInt(entry->(entry.getValue()+entry.getKey().getMaxCount()-1)/entry.getKey().getMaxCount()).sum();
            require(stacks<=24,"Large section exceeded its inventory allowance");
            for(int turn=0;turn<4;turn++)for(String mirror:new String[]{"None","X","Z"})for(int index:new int[]{0,99,12_345,249_999})require(large.indexAt(large.transformed(index,turn,mirror),turn,mirror)==index,"Large placement transform lost a cell");
            set(builder,"Material Supply","Layer by Layer");
        });
        try{
            var path=java.nio.file.Files.createTempFile("maro-farm-scan", ".litematic");
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/sellaxe-bone-meal-farm.litematic")){
                java.nio.file.Files.copy(source,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);var farm=SchematicIO.read(path);
                context.runOnClient(client->{set(builder,"Material Supply","Nearby Sections");builder.install(farm);builder.setOrigin(start.add(-27,8,-32));builder.preview();});
                for(int tick=0;tick<500&&context.computeOnClient(client->(int)field(builder,"completedScans")==0);tick++)context.waitTick();
                context.runOnClient(client->{
                    require((int)field(builder,"completedScans")>0,"Supplied large farm scan did not finish");
                    var batch=builder.remainingMaterials();int blocks=batch.values().stream().mapToInt(Integer::intValue).sum();
                    require(blocks>0&&blocks<=128,"Farm requested unbounded work materials");
                    require(batch.entrySet().stream().mapToInt(entry->(entry.getValue()+entry.getKey().getMaxCount()-1)/entry.getKey().getMaxCount()).sum()<=24,"Farm requested more material stacks than fit its work inventory");
                    require(!batch.containsKey(Items.OBSERVER)&&!batch.containsKey(Items.LAVA_BUCKET)&&!batch.containsKey(Items.WATER_BUCKET),"Farm activated observer/fluid work before assembly");
                    set(builder,"Material Supply","Layer by Layer");
                });
            }finally{java.nio.file.Files.deleteIfExists(path);}
        }catch(java.io.IOException failure){throw new AssertionError(failure);}
    }
    private static void longRestockRoute(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var chest=start.east(80);var target=start.south(2);
        world.getServer().runCommand("fill "+coords(start.add(0,-1,-2))+" "+coords(start.add(83,-1,3))+" stone");
        world.getServer().runCommand("fill "+coords(start.add(17,0,-2))+" "+coords(start.add(83,3,3))+" air");
        command(world,"setblock",chest,"chest[facing=west,type=right]");command(world,"setblock",chest.south(),"chest[facing=west,type=left]");
        world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 64");context.waitTicks(10);
        context.runOnClient(client->{
            require(((dev.maro.setting.NumberSetting)field(builder,"walkDistance")).getInt()==256,"Selected-storage range did not increase for large builds");builder.install(new Schematic("long-restock-route.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
            client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();builder.startBuild();
        });
        await(context,builder,2400);verify(world,target,1,1,1,y->Blocks.STONE);
        context.runOnClient(client->require(client.currentScreen==null,"Long restock journey left its chest open"));
    }
    private static void immediateOpeningRepair(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Restore a registered opening before the next large schematic scan completes");
        fixture(context,world,builder,start);var target=start.south(2);
        command(world,"setblock",target,"stone");for(String item:List.of("stone 1","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);context.waitTicks(6);
        var cells=new BlockState[250_000];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());int repair=cells.length-1;cells[repair]=Blocks.STONE.getDefaultState();
        var schematic=new Schematic("immediate-opening-repair.nbt","test",100,25,100,BlockPos.ORIGIN,cells);
        try{
            context.runOnClient(client->{((dev.maro.setting.NumberSetting)field(builder,"budget")).set(.25);builder.install(schematic);builder.setOrigin(target.subtract(schematic.local(repair)));builder.preview();});
            for(int tick=0;tick<2000&&context.computeOnClient(client->(int)field(builder,"completedScans")==0);tick++)context.waitTick();
            context.runOnClient(client->require(builder.state(repair)==AutoBuilder.CORRECT,"Opening repair fixture lacks an initial exact block"));
            command(world,"setblock",target,"air");context.waitTicks(6);
            int scans=context.computeOnClient(client->{
                try{for(String name:List.of("scanCursor","lastPassTasks")){var value=AutoBuilder.class.getDeclaredField(name);value.setAccessible(true);value.setInt(builder,0);}var phase=AutoBuilder.class.getDeclaredField("activePhase");phase.setAccessible(true);phase.setInt(builder,2);var work=AutoBuilder.class.getDeclaredField("workCells");work.setAccessible(true);work.set(builder,List.of());}catch(ReflectiveOperationException error){throw new AssertionError(error);}
                @SuppressWarnings("unchecked")var openings=(Map<BlockPos,Integer>)field(builder,"floorAccessWork");openings.put(target,-1);@SuppressWarnings("unchecked")var depths=(Map<BlockPos,Integer>)field(builder,"openingRepairDepth");depths.put(target,1);BuilderPacketChecks.begin();builder.startBuild();return (int)field(builder,"completedScans");
            });
            int repaired=-1;for(int tick=0;tick<80;tick++){if(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(target).isOf(Blocks.STONE))){repaired=tick;break;}context.waitTick();}
            require(repaired>=0,"Registered opening waited for a full schematic scan");
            context.runOnClient(client->require((int)field(builder,"completedScans")==scans,"Repair fixture completed its global scan before proving the direct queue"));
            System.out.println("[repair-progress] Registered opening restored in "+repaired+"ticks before global scan completion");
            await(context,builder,250);context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Immediate repair left damage, menu or scaffold");BuilderPacketChecks.verify(1);});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"budget")).set(2d);});}
    }
    private static void narrowDropLanding(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Brake over a narrow lower landing before cleanup can replan");
        for(var side:Direction.Type.HORIZONTAL)for(boolean exact:List.of(false,true))for(boolean edge:List.of(true,false)){
            fixture(context,world,builder,start);
            var high=start.up(6);var landing=start.offset(side).up(3);
            command(world,"setblock",high.down(),"stone");command(world,"setblock",landing.down(),"stone");
            double yaw=Math.toDegrees(Math.atan2(side.getOffsetZ(),side.getOffsetX()))-90;
            double x=edge?landing.getX()+.5-side.getOffsetX()*.218:high.getX()+.63;
            double z=edge?landing.getZ()+.5-side.getOffsetZ()*.218:high.getZ()+.55;
            world.getServer().runCommand("tp @a "+x+" "+high.getY()+" "+z+" "+yaw+" 0");context.waitTicks(10);
            var walker=context.computeOnClient(client->new BuilderWalk());boolean arrived=false;
            try{
                context.runOnClient(client->{require(client.player.isOnGround()&&Math.abs(client.player.getY()-high.getY())<.01,"Narrow drop fixture lacks upper footing");require(walker.canReachStand(landing),"Narrow three-block landing lacks a checked route");});
                for(int tick=0;tick<180&&!arrived;tick++){
                    arrived=context.computeOnClient(client->{require(client.player.getHealth()==20,"Narrow landing caused fall damage");walker.beginLookTick(client.player.age);return exact?nativeStand(walker,landing):walker.approach(landing.down(),client.player.getBlockInteractionRange()-.85);});context.waitTick();
                }
                require(arrived,"Walker did not arrive over its narrow landing: "+side+" exact="+exact+" edge="+edge);
                context.runOnClient(client->walker.release());context.waitTicks(20);
                context.runOnClient(client->require(client.player.isOnGround()&&Math.abs(client.player.getY()-landing.getY())<.01&&client.player.getHealth()==20,"Native drop overshot its lower post: "+side+" exact="+exact));
            }finally{context.runOnClient(client->walker.stop());}
        }
    }
    /** Standalone walkers need the same once-per-native-tick rotation clock as the module. */
    private static boolean nativeStand(BuilderWalk walker,BlockPos target){
        walker.beginLookTick(net.minecraft.client.MinecraftClient.getInstance().player.age);
        return walker.standAt(target);
    }
    private static void raisedTurn(ClientGameTestContext context,TestSingleplayerContext world,BlockPos start){
        command(world,"setblock",start.east(),"stone");
        world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+.5)+" 90 0");context.waitTicks(10);
        var walker=context.computeOnClient(client->new BuilderWalk());var goal=start.east().up();walker.turning(true,45);boolean arrived=false;
        try{
            for(int tick=0;tick<200&&!arrived;tick++){
                arrived=context.computeOnClient(client->{
                    boolean done=nativeStand(walker,goal);
                    if(!done&&client.player.getVelocity().y>.15){
                        var point=walker.standingPoint(goal);float yaw=(float)(Math.toDegrees(Math.atan2(point.z-client.player.getZ(),point.x-client.player.getX()))-90);
                        require(Math.abs(MathHelper.wrapDegrees(yaw-client.player.getYaw()))<24,"Walker jumped before turning toward its raised destination");
                    }
                    return done;
                });context.waitTick();
            }
            require(arrived,"Turn-before-jump route did not reach its raised standing position");
            context.waitTicks(10);context.runOnClient(client->require(client.player.getY()>=start.getY()+.99&&client.player.getHealth()==20,"Turn-before-jump failed its native landing"));
            // A zero-cell route still needs a real walk to its exact viewpoint.
            world.getServer().runCommand("tp @a "+(goal.getX()+.85)+" "+goal.getY()+" "+(goal.getZ()+.15)+" 90 0");context.waitTicks(10);
            context.runOnClient(client->{walker.stop();require(walker.canReachStand(goal),"Current-cell placement view was incorrectly treated as unreachable");});
            arrived=false;for(int tick=0;tick<200&&!arrived;tick++){arrived=context.computeOnClient(client->nativeStand(walker,goal));context.waitTick();}
            require(arrived,"Walker did not settle at its current-cell placement view");context.waitTicks(10);
            context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(walker.standingPoint(goal))<.28*.28&&client.player.getVelocity().horizontalLengthSquared()<.0004&&client.player.getHealth()==20,"Placement view did not remain settled after native movement"));
            // Reproduce arriving near the view with walking momentum and facing
            // away. Precision movement must brake rather than orbit the point.
            var flat=start.south(3);
            for(int facing=0;facing<4;facing++){
                world.getServer().runCommand("tp @a "+(flat.getX()+.9)+" "+flat.getY()+" "+(flat.getZ()+.1)+" "+(facing*90)+" 0");context.waitTicks(6);
                context.runOnClient(client->{walker.stop();client.player.setVelocity(.11,0,-.1);});
                arrived=false;for(int tick=0;tick<160&&!arrived;tick++){arrived=context.computeOnClient(client->nativeStand(walker,flat));context.waitTick();}
                require(arrived,"Placement approach orbited instead of settling from facing "+facing);context.waitTicks(8);
                context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(walker.standingPoint(flat))<.28*.28&&client.player.getVelocity().horizontalLengthSquared()<.0004,"Placement braking did not hold its settled native position"));
            }
        }finally{context.runOnClient(client->walker.stop());}
    }
    private static void faceReach(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.add(4,0,-2);
        world.getServer().runCommand("give @a stone 2");context.waitTicks(6);
        var before=context.computeOnClient(client->client.player.getEntityPos());
        context.runOnClient(client->{
            require(client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target))>4.4*4.4,"Reach fixture did not put the block centre out of range");
            set(builder,"Auto Move",false);set(builder,"Temporary Supports",false);
            var cells=new BlockState[5];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());cells[0]=cells[4]=Blocks.STONE.getDefaultState();
            builder.install(new Schematic("reachable-face.nbt","test",1,1,5,BlockPos.ORIGIN,cells));builder.setOrigin(target);
            BuilderPacketChecks.begin();builder.startBuild();
        });
        try{
            await(context,builder,300);verify(world,target,1,1,1,y->Blocks.STONE);verify(world,target.south(4),1,1,1,y->Blocks.STONE);
            context.runOnClient(client->{require(client.player.getEntityPos().squaredDistanceTo(before)<.01,"Reachable face caused unnecessary walking");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->BuilderPacketChecks.recording=false);}
    }
    private static void scaffoldReach(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.add(0,6,3);
        world.getServer().runCommand("fill "+coords(start.add(1,0,3))+" "+coords(start.add(1,3,3))+" stone");
        world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a dirt 24");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        context.runOnClient(client->{
            set(builder,"Temporary Supports",true);builder.install(new Schematic("distant-scaffold-start.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
            BuilderPacketChecks.begin();builder.startBuild();
        });
        try{
            await(context,builder,2400);verify(world,target,1,1,1,y->Blocks.STONE);
            require(world.getServer().computeOnServer(server->{for(int y=0;y<4;y++)if(!server.getOverworld().getBlockState(start.add(1,y,3)).isOf(Blocks.STONE))return false;return true;}),"Cleanup removed the unrelated stone obstacle");
            require(world.getServer().computeOnServer(server->{for(var pos:BlockPos.iterate(start.add(-7,0,-4),start.add(7,7,10)))if(server.getOverworld().getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Distant scaffold left temporary dirt behind");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Distant scaffold did not finish safely");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->BuilderPacketChecks.recording=false);}
    }
    private static void offsetRecovery(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a diamond_shovel 1");
        world.getServer().runCommand("fill "+coords(start.add(-1,-2,-1))+" "+coords(start.add(1,-2,1))+" stone");
        command(world,"setblock",start.down(3),"stone");command(world,"setblock",start.down(2),"air");command(world,"setblock",start.down(),"air");
        world.getServer().runCommand("tp @a "+(start.getX()+.65)+" "+(start.getY()-2)+" "+(start.getZ()+.35)+" 90 0");context.waitTicks(8);
        context.runOnClient(client->{builder.install(new Schematic("offset-jump-recovery.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.south(10));BuilderPacketChecks.begin();builder.startBuild();});
        boolean receiptLanded=false;
        try{
            for(int tick=0;tick<1000&&context.computeOnClient(client->builder.building());tick++){
                if(context.computeOnClient(client->(int)field(builder,"recoveryPhase")==3))receiptLanded=true;
                context.waitTick();
            }
            await(context,builder,100);verify(world,start.south(10),1,1,1,y->Blocks.STONE);
            require(receiptLanded,"Offset jump did not advance from its actual support receipt");
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(start.down(2)).isAir()),"Offset escape left its temporary step behind");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20,"Offset escape did not complete safely");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->BuilderPacketChecks.recording=false);}
    }
    private static void activeStepProtection(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Keep active stair / escape dirt, clear obsolete route obstruction");
        var obstruction=start.south().up();var target=start.south(4);
        command(world,"setblock",obstruction,"dirt");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        context.runOnClient(client->{
            builder.install(new Schematic("protected-route-step.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();
            @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(obstruction);
            @SuppressWarnings("unchecked")var stairs=(Set<BlockPos>)field(builder,"accessSupports");
            @SuppressWarnings("unchecked")var escapes=(Set<BlockPos>)field(builder,"escapeSupports");
            var walker=(BuilderWalk)field(builder,"walker");walker.approach(target,1.5);
            try{
                var stalled=BuilderWalk.class.getDeclaredField("movementStalled");stalled.setAccessible(true);stalled.setBoolean(walker,true);
                require(obstruction.equals(walker.blockingSupport(owned)),"Protection fixture did not expose the obstruction");
                var clear=AutoBuilder.class.getDeclaredMethod("clearRouteSupportTick");clear.setAccessible(true);
                stairs.add(obstruction);require(!(boolean)clear.invoke(builder)&&field(builder,"mining")==null,"Recovery mined its committed stair");stairs.clear();
                escapes.add(obstruction);require(!(boolean)clear.invoke(builder)&&field(builder,"mining")==null,"Recovery mined its escape step");escapes.clear();
                require((boolean)clear.invoke(builder),"Obsolete obstruction was never considered for removal");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        await(context,builder,500);
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(obstruction).isAir()&&server.getOverworld().getBlockState(target).isOf(Blocks.STONE)),"Obsolete dirt removal did not resume the real build");
        context.runOnClient(client->{
            var walker=new BuilderWalk();var destination=start.north(6);nativeStand(walker,destination);walker.release();
            try{
                var path=BuilderWalk.class.getDeclaredField("path");path.setAccessible(true);path.set(walker,List.of());
                var retry=BuilderWalk.class.getDeclaredField("retry");retry.setAccessible(true);retry.setInt(walker,0);
                var failures=BuilderWalk.class.getDeclaredField("failedRoutes");failures.setAccessible(true);failures.setInt(walker,2);
                walker.requestRecovery();nativeStand(walker,destination);
                require(!walker.needsRecovery()&&!walker.routeUnavailable(),"Successful walking route retained a stale pillar request");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}finally{walker.stop();}
        });
    }
    private static void raisedDoorEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Raised closed-door landing fits, but its panel must block the jump approach");
        fixture(context,world,builder,start);var goal=start.south().up();
        command(world,"setblock",goal.down(),"stone");command(world,"setblock",goal,"dark_oak_door[facing=south,half=lower,hinge=right,open=false]");command(world,"setblock",goal.up(),"dark_oak_door[facing=south,half=upper,hinge=right,open=false]");context.waitTicks(6);
        var walker=context.computeOnClient(client->{var w=new BuilderWalk();require(w.canStand(goal),"Closed-door centre should fit a standing body");w.turning(true,45);return w;});
        try{
            boolean arrived=false;for(int tick=0;tick<300&&!arrived;tick++){arrived=context.computeOnClient(client->nativeStand(walker,goal));context.waitTick();}
            require(arrived,"Native movement kept jumping into the closed door instead of approaching a clear side");
            context.runOnClient(client->require(client.player.getHealth()==20&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Raised door approach intersected a panel or caused damage"));
            require(world.getServer().computeOnServer(server->!server.getOverworld().getBlockState(goal).get(Properties.OPEN)),"Door approach changed the intended closed state");
        }finally{context.runOnClient(client->walker.stop());}
        System.out.println("[builder-check] Lower landing fits, but a closed-door panel must block the ledge approach");
        fixture(context,world,builder,start);var lower=start.north();var door=start.up();
        command(world,"setblock",start,"stone");command(world,"setblock",door,"dark_oak_door[facing=south,half=lower,hinge=right,open=false]");command(world,"setblock",door.up(),"dark_oak_door[facing=south,half=upper,hinge=right,open=false]");
        world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+1)+" "+(start.getZ()+.5)+" 180 0");context.waitTicks(8);
        var descending=context.computeOnClient(client->{var w=new BuilderWalk();require(w.canStand(lower)&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Closed-door descent fixture is not clear at source and landing");w.turning(true,45);return w;});
        try{
            boolean arrived=false;for(int tick=0;tick<300&&!arrived;tick++){arrived=context.computeOnClient(client->nativeStand(descending,lower));context.waitTick();}
            require(arrived,"Native descent kept walking into the closed door instead of choosing a clear edge");
            context.runOnClient(client->require(client.player.getHealth()==20&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Door descent intersected a panel or caused damage"));
            require(world.getServer().computeOnServer(server->!server.getOverworld().getBlockState(door).get(Properties.OPEN)),"Door descent changed the intended closed state");
        }finally{context.runOnClient(client->descending.stop());}
    }
    private static void hopperCrossing(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Jump onto native hopper rims and cross without extra supports");
        fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(1,-1,0))+" "+coords(start.add(5,-1,6))+" lava");
        world.getServer().runCommand("fill "+coords(start.add(-5,-1,0))+" "+coords(start.add(-1,-1,6))+" lava");
        for(int z=1;z<=4;z++)command(world,"setblock",start.south(z),"hopper");
        world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        context.runOnClient(client->{builder.install(new Schematic("hopper-walking-surface.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.south(7));builder.startBuild();});
        boolean crossed=false;
        for(int tick=0;tick<400&&context.computeOnClient(client->builder.building());tick++){
            crossed|=context.computeOnClient(client->client.player.getY()>start.getY()+.55&&client.player.getZ()>start.getZ()+1&&client.player.getZ()<start.getZ()+5);context.waitTick();
        }
        await(context,builder,50);verify(world,start.south(7),1,1,1,y->Blocks.STONE);
        require(crossed,"Walking route did not use the hopper surface");
        context.runOnClient(client->require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Hopper route crossed lava or created unnecessary supports"));
    }
    private static void lowBucketSource(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Refuse a leaking low entrance, then pour from above without waterlogging the panel");
        fixture(context,world,builder,start);var goal=start.south(3).up();var panel=goal.up();
        command(world,"setblock",goal.down(),"blackstone");
        for(var side:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST))command(world,"setblock",goal.offset(side),"blackstone");
        command(world,"setblock",panel,"warped_trapdoor[facing=north,half=bottom,open=true,waterlogged=false]");
        world.getServer().runCommand("give @a water_bucket");
        world.getServer().runCommand("tp @a "+(goal.getX()-.7)+" "+start.getY()+" "+(goal.getZ()+.45)+" -90 0");context.waitTicks(8);
        try{
            context.runOnClient(client->{set(builder,"Auto Move",false);builder.install(new Schematic("low-source.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.WATER.getDefaultState()}));builder.setOrigin(goal);BuilderPacketChecks.begin();builder.startBuild();});
            context.waitTicks(80);
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(goal).isAir()&&!server.getOverworld().getBlockState(panel).get(Properties.WATERLOGGED)),"Unsafe low opening admitted a bucket or waterlogged its panel");
            context.runOnClient(client->{BuilderPacketChecks.verify(0);builder.pause("Test: seal and move above the basin");});
            command(world,"setblock",goal.west(),"blackstone");
            world.getServer().runCommand("tp @a "+(goal.getX()-.5)+" "+(goal.getY()+1)+" "+(goal.getZ()+.5)+" -90 60");context.waitTicks(8);
            context.runOnClient(client->{BuilderPacketChecks.begin();builder.startBuild();});
            require(world.getServer().computeOnServer(server->!server.getOverworld().getBlockState(panel).get(Properties.WATERLOGGED)),"Bucket filled the overhead trapdoor instead of the adjacent source");
            await(context,builder,120);
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(goal).equals(Blocks.WATER.getDefaultState())),"Contained top bucket placement did not create the actual source");
            context.runOnClient(client->{BuilderPacketChecks.verify(1);require(client.player.getHealth()==20&&client.currentScreen==null,"Low access bucket placement damaged the player or left a menu");});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    @SuppressWarnings("unchecked")
    private static void containedTopLiquids(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean water:List.of(true,false)){
            System.out.println("[builder-check] Climb above a closed basin, restore its old low opening before pouring, and restore the roof; water="+water);
            fixture(context,world,builder,start);var origin=start.add(-2,0,3);var cells=new BlockState[75];Arrays.fill(cells,Blocks.STONE.getDefaultState());
            int source=2+2*5+25;cells[source]=(water?Blocks.WATER:Blocks.LAVA).getDefaultState();
            var room=new Schematic("contained-top-liquid.nbt","test",5,3,5,BlockPos.ORIGIN,cells);var goal=origin.add(room.local(source));var oldOpening=goal.west();
            world.getServer().runOnServer(server->{for(int i=0;i<room.size();i++)if(i!=source&&!origin.add(room.local(i)).equals(oldOpening))server.getOverworld().setBlockState(origin.add(room.local(i)),room.state(i),Block.NOTIFY_ALL);});
            for(String item:List.of("stone 16","dirt 64","diamond_pickaxe","diamond_shovel",water?"water_bucket":"lava_bucket"))world.getServer().runCommand("give @a "+item);
            context.waitTicks(10);boolean openedRoof=false,poured=false,sealed=false;
            try{
                context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Auto Buy When Missing",false);set(builder,"Material Supply","Nearby Sections");builder.install(room);builder.setOrigin(origin);BuilderPacketChecks.begin();builder.startBuild();((Map<BlockPos,Integer>)field(builder,"floorAccessWork")).put(oldOpening,source);});
                for(int tick=0;tick<2400&&context.computeOnClient(client->builder.building());tick++){
                    boolean closed=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(oldOpening).isOf(Blocks.STONE));
                    if(sealed)require(closed,"Fluid access reopened its retaining wall");sealed|=closed;
                    openedRoof|=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(goal.up()).isAir());
                    boolean filled=world.getServer().computeOnServer(server->server.getOverworld().getBlockState(goal).isOf(water?Blocks.WATER:Blocks.LAVA));
                    if(filled&&!poured){require(closed,"Bucket poured before repairing the old retaining wall");context.runOnClient(client->require(client.player.getY()>=goal.getY()+1&&!client.player.isTouchingWater(),"Bucket was used from below or inside the liquid"));poured=true;}
                    require(world.getServer().computeOnServer(server->{for(var pos:BlockPos.iterate(origin.add(-3,-1,-3),origin.add(7,4,7)))if(!pos.equals(goal)&&!server.getOverworld().getFluidState(pos).isEmpty())return false;return true;}),"Fluid escaped its basin");
                    context.waitTick();
                }
                await(context,builder,1);require(openedRoof&&poured&&sealed,"Top access did not open, pour and repair in the native world");
                require(world.getServer().computeOnServer(server->{for(int i=0;i<room.size();i++)if(!server.getOverworld().getBlockState(origin.add(room.local(i))).equals(room.state(i)))return false;for(var pos:BlockPos.iterate(start.add(-8,0,-8),start.add(8,8,12)))if(server.getOverworld().getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Top liquid access left a roof hole or temporary dirt");
                context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Top fluid work left damage/menu/supports");BuilderPacketChecks.verify();});
            }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
        }
    }
    /** Reproduce Upper70's roof departure into its real water and lava basins. */
    @SuppressWarnings("unchecked")
    private static void roofStashLiquids(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos original){
        System.out.println("[builder-check] Depart the captured stash roof, pass earlier rejected hatches and fill three water basins plus lava from above");
        var start=new BlockPos(-26210,61,-150577);var origin=start.add(-16,2,-8);var stash=stashFixture();var posts=new HashSet<BlockPos>();var sources=new ArrayList<Integer>();
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(start));context.waitTicks(30);fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(-24,-1,-24))+" "+coords(start.add(24,-1,24))+" end_stone");
        world.getServer().runCommand("fill "+coords(start.add(-24,0,-24))+" "+coords(start.add(24,12,24))+" air");
        try(var input=AutoBuilderChecks.class.getResourceAsStream("/fixtures/stash-upper-supports.txt")){
            for(var line:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()){var parts=line.trim().split("\\s+");var pos=new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));int cell=stash.indexAt(pos.subtract(origin),0,"None");if(cell<0||stash.state(cell).isAir())posts.add(pos);}
        }catch(java.io.IOException error){throw new AssertionError(error);}
        for(int cell=0;cell<stash.size();cell++)if(stash.state(cell).getBlock() instanceof FluidBlock&&Schematic.material(stash.state(cell))!=Items.AIR)sources.add(cell);
        require(sources.size()==4,"Captured roof fixture lost its three water sources or lava source");
        world.getServer().runOnServer(server->{var level=server.getOverworld();for(int cell=0;cell<stash.size();cell++)if(!stash.state(cell).isAir()&&!(stash.state(cell).getBlock() instanceof FluidBlock)&&!(stash.state(cell).getBlock() instanceof net.minecraft.block.ObserverBlock))level.setBlockState(origin.add(stash.local(cell)),stash.state(cell),Block.NOTIFY_ALL);for(var pos:posts)level.setBlockState(pos,Blocks.DIRT.getDefaultState(),Block.NOTIFY_ALL);});
        for(String item:List.of("dirt 64","diamond_pickaxe","diamond_shovel","cooked_beef 16"))world.getServer().runCommand("give @a "+item);
        for(int cell:sources)world.getServer().runCommand("give @a "+net.minecraft.registry.Registries.ITEM.getId(Schematic.material(stash.state(cell))));
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a -26221.657616489858 71 -150578.3950203814");context.waitTicks(15);world.getServer().runCommand("gamemode survival @a");context.waitTicks(6);
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Auto Buy When Missing",false);set(builder,"Restock When Empty",false);set(builder,"Material Supply","Whole Schematic");builder.install(stash);builder.setOrigin(origin);((Set<BlockPos>)field(builder,"supports")).addAll(posts);BuilderPacketChecks.begin();builder.startBuild();});
            boolean filled=false,opened=false;
            for(int tick=0;tick<3600&&!filled;tick++){
                if(tick%100==0)context.runOnClient(client->System.out.println("[roof-fluid-progress] "+builder.status()+" pos="+client.player.getEntityPos()+" openings="+field(builder,"floorAccessWork")));
                context.runOnClient(client->{require(client.player.getHealth()==20&&!client.player.isTouchingWater(),"Roof liquid access caused damage or entered water");for(var entry:((Map<BlockPos,Integer>)field(builder,"floorAccessWork")).entrySet())if(sources.contains(entry.getValue()))require(entry.getKey().getY()>builder.position(entry.getValue()).getY(),"Roof liquid access opened a low entrance at "+entry.getKey()+" for "+entry.getValue());});
                opened|=context.computeOnClient(client->!((Map<?,?>)field(builder,"floorAccessWork")).isEmpty());
                filled=world.getServer().computeOnServer(server->{var level=server.getOverworld();boolean complete=true;for(int cell:sources){var target=origin.add(stash.local(cell));for(var side:Direction.values())if(side!=Direction.UP){var pos=target.offset(side);int other=stash.indexAt(pos.subtract(origin),0,"None");require(other>=0&&level.getBlockState(pos).equals(stash.state(other)),"Roof access changed a basin retaining block");}if(!level.getBlockState(target).equals(stash.state(cell)))complete=false;}return complete;});
                context.waitTick();
            }
            require(filled&&opened,"Captured roof access did not fill all four sources within its bounded native replay");
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(var pos:BlockPos.iterate(start.add(-24,0,-24),start.add(24,12,24)))if(!level.getFluidState(pos).isEmpty()){int cell=stash.indexAt(pos.subtract(origin),0,"None");if(cell<0||!AutoBuilder.matchesBuildState(level.getBlockState(pos),stash.state(cell)))return false;}return true;}),"Captured roof access leaked fluid or waterlogged an unintended block");
            context.runOnClient(client->BuilderPacketChecks.verify(4));
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a "+coords(original));context.waitTicks(20);}
    }
    private static void floodedAccessDeparture(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean lowHealth:List.of(false,true))floodedAccessDeparture(context,world,builder,start,lowHealth);
    }
    private static void floodedAccessDeparture(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean lowHealth){
        System.out.println("[builder-check] Leave the captured Upper68 flooded room with lowHealth="+lowHealth);
        fixture(context,world,builder,start);var source=start.south(3).up(3);var next=start.west(12);
        try(var input=AutoBuilderChecks.class.getResourceAsStream("/fixtures/stash-flooded-access.txt")){
            var lines=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().filter(line->!line.startsWith("#")).toList();
            world.getServer().runOnServer(server->{for(var line:lines){var parts=line.split(" ",4);var pos=source.add(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));try{var state=net.minecraft.command.argument.BlockArgumentParser.block(net.minecraft.registry.Registries.BLOCK,parts[3],false).blockState();server.getOverworld().setBlockState(pos,state,Block.NOTIFY_ALL);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){throw new AssertionError(failure);}}});
        }catch(java.io.IOException failure){throw new AssertionError(failure);}
        context.waitTicks(20);world.getServer().runCommand("give @a stone");
        world.getServer().runCommand("tp @a "+(source.getX()-.7)+" "+(source.getY()-1)+" "+(source.getZ()+.4513929)+" -90 0");context.waitTicks(3);
        if(lowHealth){world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.setHealth(6);player.getHungerManager().setFoodLevel(1);player.getHungerManager().setSaturationLevel(0);});context.waitTick();}
        try{
            context.runOnClient(client->{require(client.player.isSubmergedIn(net.minecraft.registry.tag.FluidTags.WATER),"Captured access fixture did not immerse the native player's head");builder.install(new Schematic("after-bucket.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(next);BuilderPacketChecks.begin();builder.startBuild();});
            for(int tick=0;tick<240&&context.computeOnClient(client->client.player.isTouchingWater()||(boolean)field(builder,"waterDeparture"));tick++){if(tick%40==0)context.runOnClient(client->System.out.println("[flooded-exit] pos="+client.player.getEntityPos()+" air="+client.player.getAir()+" health="+client.player.getHealth()+" status="+builder.status()));context.waitTick();}
            context.runOnClient(client->{require(!client.player.isTouchingWater()&&!(boolean)field(builder,"waterDeparture")&&client.player.getHealth()>=(lowHealth?6:20),"Captured flooded opening stranded or damaged the native player");require(!new BuilderWalk().canStand(source),"Departure admitted ordinary re-entry to the wet source");});
            require(world.getServer().computeOnServer(server->server.getOverworld().getFluidState(source).isIn(net.minecraft.registry.tag.FluidTags.WATER)&&server.getOverworld().getBlockState(source.up()).get(Properties.WATERLOGGED)),"Departure drained the source or its overhead panel");
            if(lowHealth){context.runOnClient(client->{require(builder.status().equals("Paused — low health")&&client.world.getBlockState(next).isAir(),"Low-health departure did not pause normal work on dry ground");BuilderPacketChecks.verify(0);});return;}
            await(context,builder,480);verify(world,next,1,1,1,y->Blocks.STONE);
            context.runOnClient(client->{BuilderPacketChecks.verify(1);require(client.player.getHealth()==20&&builder.temporarySupports().isEmpty(),"Captured flooded departure damaged the player or created unnecessary supports");});
        }finally{
            context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});
            if(lowHealth){world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);player.getHungerManager().setSaturationLevel(5);});context.waitTicks(2);}
        }
    }
    private static void partialHeadroom(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(String facing:List.of("north","south","east","west")){
            System.out.println("[builder-check] Native standing body and entry edge beneath open trapdoor "+facing);
            fixture(context,world,builder,start);var goal=start.south(3);var panel=goal.up();
            command(world,"setblock",panel,"warped_trapdoor[facing="+facing+",half=bottom,open=false]");context.waitTicks(6);
            context.runOnClient(client->require(!new BuilderWalk().canStand(goal),"Closed low trapdoor admitted a standing body"));
            command(world,"setblock",panel,"warped_trapdoor[facing="+facing+",half=bottom,open=true]");context.waitTicks(6);
            var walk=context.computeOnClient(client->{var w=new BuilderWalk();var point=w.standingPoint(goal);require(client.world.isSpaceEmpty(client.player,client.player.getBoundingBox().offset(point.subtract(client.player.getEntityPos())))&&w.canStand(goal)&&w.canReachStand(goal),"Open trapdoor rejected real standing-body clearance");w.turning(true,45);return w;});
            try{
                boolean arrived=false;for(int tick=0;tick<240&&!arrived;tick++){arrived=context.computeOnClient(client->nativeStand(walk,goal));context.waitTick();}
                require(arrived,"Native walking did not route around the open panel for "+facing);
                context.runOnClient(client->require(client.player.getHealth()==20&&client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Partial headroom walk intersected the panel or caused damage"));
            }finally{context.runOnClient(client->walk.stop());}
            if(facing.equals("west")){
                var dryExit=goal.north().up();for(var side:Direction.Type.HORIZONTAL)command(world,"setblock",goal.offset(side),"stone");context.waitTicks(6);
                world.getServer().runCommand("give @a water_bucket");context.waitTicks(6);
                try{
                    context.runOnClient(client->{builder.install(new Schematic("partial-headroom-fluid.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.WATER.getDefaultState()}));builder.setOrigin(goal);BuilderPacketChecks.begin();builder.startBuild();});
                    await(context,builder,300);
                    require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(goal).equals(Blocks.WATER.getDefaultState())&&server.getOverworld().getBlockState(panel).get(Properties.OPEN)),"Fluid under the open trapdoor stayed predicted or changed its panel");
                    context.runOnClient(client->{require(client.player.getHealth()==20&&client.currentScreen==null,"Partial-headroom bucket placement damaged player or left a menu");BuilderPacketChecks.verify(1);});
                    System.out.println("[builder-check] Leave the bucket source beneath an open panel without mining or adding dirt");
                    var exit=context.computeOnClient(client->{var w=new BuilderWalk();w.turning(true,45);return w;});
                    try{
                        boolean escaped=false;for(int tick=0;tick<360&&!escaped;tick++){escaped=context.computeOnClient(client->nativeStand(exit,dryExit));if(tick%40==0)context.runOnClient(client->System.out.println("[water-exit] pos="+client.player.getEntityPos()+" wet="+client.player.isTouchingWater()+" goalReach="+exit.canReachStand(dryExit)+" status="+exit.status));context.waitTick();}
                        require(escaped,"Bucket source stranded the native player beneath the open panel");
                        context.runOnClient(client->{require(client.player.getHealth()==20&&client.world.getBlockState(goal).isOf(Blocks.WATER)&&!exit.canStand(goal),"Water departure damaged the player, removed the source, or allowed re-entry");});
                    }finally{context.runOnClient(client->exit.stop());}
                }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
            }
        }
    }
    private static void stairPlacementPriority(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Place the available stair piece before undoing staging with another climb");
        fixture(context,world,builder,start);var first=start.south();var existing=start.east();var stand=start.up(3).south(3);
        command(world,"setblock",existing,"dirt");command(world,"setblock",stand.down(),"dirt");world.getServer().runCommand("give @a dirt 16");context.waitTicks(8);
        try{
            context.runOnClient(client->{
                builder.install(new Schematic("stair-placement-priority.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(stand);set(builder,"Temporary Supports",true);BuilderPacketChecks.begin();builder.startBuild();
                try{
                    for(String name:List.of("accessStand","accessStairs")){var f=AutoBuilder.class.getDeclaredField(name);f.setAccessible(true);f.set(builder,name.equals("accessStand")?stand:Collections.unmodifiableSet(new LinkedHashSet<>(List.of(first,existing))));}
                    var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,0);
                    require(new BuilderWalk().canReachStand(existing.up()),"Priority fixture has no competing reachable upper step");
                    var method=AutoBuilder.class.getDeclaredMethod("accessStep",BlockPos.class);method.setAccessible(true);require((boolean)method.invoke(builder,stand),"Available stair piece was not scheduled");
                    var job=field(builder,"placement");require(job!=null&&field(builder,"standGoal")==null,"Climbing preempted a native placement at the staged view");
                    var target=job.getClass().getDeclaredMethod("target");target.setAccessible(true);require(target.invoke(job).equals(first),"Staging chose another piece instead of its available next face");
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            });
            for(int tick=0;tick<180&&!context.computeOnClient(client->builder.temporarySupports().contains(first));tick++)context.waitTick();
            context.runOnClient(client->{require(builder.temporarySupports().contains(first)&&client.player.getHealth()==20,"Priority placement was not natively confirmed");BuilderPacketChecks.verify(1);builder.setEnabled(false);});
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(first).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(existing).isOf(Blocks.DIRT)),"Priority placement altered the existing step or stayed predicted");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void stairPlacementStaging(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Resume retained stairs after a capacity trip beyond placement reach");
        fixture(context,world,builder,start);var stand=start.up(3).south(3);var first=start.south();
        var plan=new LinkedHashSet<>(List.of(first,start.south(2),start.up().south(2)));
        command(world,"setblock",stand.down(),"dirt");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a stone 1");
        world.getServer().runCommand("tp @a "+(start.getX()-7.5)+" "+start.getY()+" "+(start.getZ()+.5));context.waitTicks(12);
        try{
            // Exact feasibility is capped at 3 ms, so a cold or busy runner can
            // yield without disproving the route. Require a complete proof on
            // a later frame before injecting the retained stair plan.
            boolean proved=false;
            for(int tick=0;tick<60&&!proved;tick++){
                proved=context.computeOnClient(client->!new BuilderWalk().stairRoute(stand,plan).isEmpty());
                if(!proved)context.waitTick();
            }
            require(proved,"Staging fixture has no complete future stair route");
            context.runOnClient(client->{
                builder.install(new Schematic("stair-placement-return.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(stand);set(builder,"Temporary Supports",true);
                BuilderPacketChecks.begin();builder.startBuild();
                try{
                    for(String name:List.of("accessStand","accessStairs")){var f=AutoBuilder.class.getDeclaredField(name);f.setAccessible(true);f.set(builder,name.equals("accessStand")?stand:Collections.unmodifiableSet(plan));}
                    var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,0);
                    for(String name:List.of("accessStarted","accessProgressAt")){var f=AutoBuilder.class.getDeclaredField(name);f.setAccessible(true);f.setInt(builder,(int)field(builder,"ticks"));}
                    @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.add(stand.down());
                    @SuppressWarnings("unchecked")var protectedSteps=(Set<BlockPos>)field(builder,"accessSupports");protectedSteps.addAll(plan);protectedSteps.add(stand.down());
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            });
            boolean staged=false;
            for(int tick=0;tick<300&&!context.computeOnClient(client->builder.temporarySupports().contains(first));tick++){
                staged|=context.computeOnClient(client->field(builder,"standGoal")!=null&&stand.equals(field(builder,"accessStand")));
                context.waitTick();
            }
            require(staged,"Retained stair never walked to its next native placement face");
            context.runOnClient(client->{require(builder.temporarySupports().contains(first)&&client.player.getHealth()==20,"Stair return failed to confirm its next piece safely");require(field(builder,"routeMining")==null,"Stair return started needless recycling with available capacity");BuilderPacketChecks.verify(1);builder.setEnabled(false);});
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(first).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(stand.down()).isOf(Blocks.DIRT)),"Native stair return lost its next piece or existing destination footing");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void blockedAccessStep(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Unreachable stair destination must not create fragments");
        fixture(context,world,builder,start);var stand=start.up(5).south(4);var step=start.up(2).south(2);
        for(var p:List.of(start,start.up(),stand.down()))command(world,"setblock",p,"dirt");
        command(world,"setblock",start.up().south(),"stone");command(world,"setblock",start.up(2).south(3),"stone");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)for(int y=0;y<=1;y++)command(world,"setblock",stand.add(x,y,z),"stone");
        world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+2)+" "+(start.getZ()+.5));context.waitTicks(12);
        context.runOnClient(client->{builder.install(new Schematic("blocked-stair.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(stand);set(builder,"Temporary Supports",true);@SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(List.of(start,start.up(),stand.down()));});
        context.runOnClient(client->{
            try{
                var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,0);
                var walker=(BuilderWalk)field(builder,"walker");walker.requestRecovery();
                var recover=AutoBuilder.class.getDeclaredMethod("recoveryTick");recover.setAccessible(true);
                require(!(boolean)recover.invoke(builder)&&(int)field(builder,"recoveryPhase")==0&&field(builder,"placement")==null,
                    "Unproved upward target triggered a blind underfoot recovery step");
                walker.stop();
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        });
        boolean exhausted=false;
        for(int attempt=0;attempt<12;attempt++){
            boolean waiting=context.computeOnClient(client->{try{var plan=AutoBuilder.class.getDeclaredMethod("buildAccessStep",BlockPos.class);plan.setAccessible(true);boolean more=(boolean)plan.invoke(builder,stand);require(field(builder,"placement")==null&&field(builder,"pendingPlacement")==null&&builder.temporarySupports().size()==3,"Unproved stair created or queued an unnecessary fragment");require(!new BuilderWalk().canStand(step.up()),"Future stair query leaked collision geometry");return more;}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}});
            if(!waiting){exhausted=true;break;}context.waitTick();
        }
        require(exhausted,"Rejected stair planning did not finish its bounded search");
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(step).isAir()&&server.getOverworld().getBlockState(step.down()).isAir()&&server.getOverworld().getBlockState(step.down(2)).isAir()),"Rejected stair changed native blocks");
    }
    private static void compactAccessStep(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Use an attachable upper step without a ground column; retain it on arrival");
        fixture(context,world,builder,start);var stand=start.up(5).south(4);var step=start.up(2).south(2);
        for(var p:List.of(start,start.up(),stand.down()))command(world,"setblock",p,"dirt");
        command(world,"setblock",start.up().south(),"stone");command(world,"setblock",start.up(2).south(3),"dirt");
        world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+(start.getY()+2)+" "+(start.getZ()+.5));context.waitTicks(12);
        try{
            context.runOnClient(client->{
                builder.install(new Schematic("compact-upper-step.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(stand);set(builder,"Temporary Supports",true);
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(List.of(start,start.up(),stand.down(),start.up(2).south(3)));BuilderPacketChecks.begin();builder.startBuild();
                try{
                    var plan=AutoBuilder.class.getDeclaredMethod("buildAccessStep",BlockPos.class);plan.setAccessible(true);require((boolean)plan.invoke(builder,stand),"Upper step planner found no native placement");
                    var job=field(builder,"placement");var target=job.getClass().getDeclaredMethod("target");target.setAccessible(true);require(target.invoke(job).equals(step),"Planner added a needless lower column instead of the attachable upper step");
                    require(((Set<?>)field(builder,"accessSupports")).contains(start.up(2).south(3)),"Checked stair did not preserve its existing owned attachment");
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            });
            for(int tick=0;tick<200&&!context.computeOnClient(client->builder.temporarySupports().contains(step));tick++)context.waitTick();
            context.runOnClient(client->{
                require(builder.temporarySupports().contains(step),"Upper step was not confirmed");
                try{
                    for(String n:List.of("standGoal","standProgressPos")){var f=AutoBuilder.class.getDeclaredField(n);f.setAccessible(true);f.set(builder,n.equals("standGoal")?step.up():client.player.getEntityPos());}
                    for(String n:List.of("standStarted","standProgressAt")){var f=AutoBuilder.class.getDeclaredField(n);f.setAccessible(true);f.setInt(builder,(int)field(builder,"ticks"));}
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            });
            for(int tick=0;tick<200&&context.computeOnClient(client->field(builder,"standGoal")!=null);tick++)context.waitTick();
            context.runOnClient(client->{require(field(builder,"standGoal")==null&&client.player.getY()>=step.getY()+.9&&((Set<?>)field(builder,"accessSupports")).contains(step),"Intermediate arrival released a still-needed access step");require(client.player.getHealth()==20,"Compact stair caused damage");BuilderPacketChecks.verify(1);builder.setEnabled(false);});
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(step).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(step.down()).isAir()&&server.getOverworld().getBlockState(step.down(2)).isAir()),"Compact step added unnecessary dirt below its native attachment");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void verticalPillarPacing(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Five-block vertical pillar without per-block recovery waits, then native cleanup");
        fixture(context,world,builder,start);var target=start.up(6).east(6);
        for(int x=1;x<=6;x++)command(world,"setblock",start.up(5).east(x),"stone");
        world.getServer().runCommand("give @a stone 1");world.getServer().runCommand("give @a dirt 16");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("quick-vertical-pillar.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);BuilderPacketChecks.begin();builder.startBuild();});
            int first=-1,top=-1;
            for(int tick=0;tick<400;tick++){
                final int sample=tick;
                int count=context.computeOnClient(client->{for(var post:builder.temporarySupports())require(post.getX()==start.getX()&&post.getZ()==start.getZ(),"Vertical route built side stairs instead of its checked pillar");return builder.temporarySupports().size();});
                if(count>0&&first<0)first=tick;
                if(count==5&&context.computeOnClient(client->client.player.isOnGround()&&client.player.getY()>=start.getY()+4.9)){top=sample;break;}
                context.waitTick();
            }
            require(first>=0&&top>=0,"Native five-block pillar was not completed");
            require(top-first<120,"Checked pillar kept the two-second wait between blocks: "+(top-first)+"ticks");
            System.out.println("[pillar-progress] First confirmed post to five-block landing: "+(top-first)+"ticks");
            await(context,builder,900);verify(world,target,1,1,1,y->Blocks.STONE);
            require(world.getServer().computeOnServer(server->{for(int x=-2;x<=8;x++)for(int y=0;y<=8;y++)for(int z=-2;z<=2;z++)if(server.getOverworld().getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;for(int x=1;x<=6;x++)if(!server.getOverworld().getBlockState(start.up(5).east(x)).isOf(Blocks.STONE))return false;return true;}),"Pillar cleanup left dirt or changed its unowned platform");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty(),"Pillar retained owned posts");require(client.player.getHealth()==20,"Pillar build or cleanup caused damage");require(client.currentScreen==null,"Pillar left a menu open");BuilderPacketChecks.verify();});
            System.out.println("[pillar-progress] All five posts removed with full health and unchanged native platform");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void nearbyCleanupPriority(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Remove a reachable column tip before constructing access to distant scaffold");
        fixture(context,world,builder,start);var near=start.east(3);var far=start.east(8).up(7);var target=start.south(2);var lower=start.east(2);var upper=lower.up().north();
        for(var post:List.of(near,far,lower,upper))command(world,"setblock",post,"dirt");command(world,"setblock",target,"stone");world.getServer().runCommand("give @a diamond_shovel 1");world.getServer().runCommand("give @a dirt 16");context.waitTicks(6);
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("nearby-cleanup-first.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);@SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(List.of(near,far,lower,upper));BuilderPacketChecks.begin();builder.startBuild();});
            int cleared=-1;
            for(int tick=0;tick<80;tick++){
                context.runOnClient(client->require(builder.temporarySupports().size()<=4,"Cleanup built new scaffold before removing its visible tip"));
                require(world.getServer().computeOnServer(server->!server.getOverworld().getBlockState(lower).isAir()||!server.getOverworld().getBlockState(upper).isOf(Blocks.DIRT)),"Quick cleanup removed a stair connection before its upper step");
                if(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(near).isAir())){cleared=tick;break;}
                context.waitTick();
            }
            require(cleared>=0,"Cleanup skipped its immediately reachable tip");
            require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(far).isOf(Blocks.DIRT)),"Priority fixture unexpectedly changed its distant post");
            context.runOnClient(client->{require(client.player.getHealth()==20,"Nearby cleanup caused damage");BuilderPacketChecks.verify(0);});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void ownedChestCover(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean owned:List.of(true,false)){
            System.out.println("[builder-check] Reserved supply lids and existing cover recovery; owned="+owned);
            fixture(context,world,builder,start);var chest=start.east(2);var partner=chest.east();var target=start.south(2);
            command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",partner,"chest[facing=north,type=right]");
            world.getServer().runCommand("item replace block "+coords(chest)+" container.0 with stone 4");
            world.getServer().runCommand("give @a diamond_shovel");world.getServer().runCommand("give @a dirt 16");context.waitTicks(8);
            context.runOnClient(client->{
                builder.install(new Schematic("reserved-chest-cover.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);set(builder,"Temporary Supports",true);
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();
                try{
                    var planning=AutoBuilder.class.getDeclaredMethod("placement",BlockPos.class,BlockState.class,Item.class,int.class,boolean.class);planning.setAccessible(true);
                    for(var half:List.of(chest,partner))for(int y=1;y<=2;y++)require(planning.invoke(builder,half.up(y),Blocks.DIRT.getDefaultState(),Items.DIRT,-1,true)==null,"Temporary placement covers a selected supply lid");
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            });
            for(var half:List.of(chest,partner))command(world,"setblock",half.up(),"dirt");context.waitTicks(6);
            try{
                context.runOnClient(client->{if(owned){@SuppressWarnings("unchecked")var supports=(Set<BlockPos>)field(builder,"supports");supports.add(chest.up());supports.add(partner.up());}BuilderPacketChecks.begin();builder.startBuild();});
                if(owned){
                    await(context,builder,700);verify(world,target,1,1,1,y->Blocks.STONE);
                    require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(chest.up()).isAir()&&server.getOverworld().getBlockState(partner.up()).isAir()),"Owned lid covers were not cleared");
                    context.runOnClient(client->require(builder.temporarySupports().isEmpty()&&client.currentScreen==null&&client.player.getHealth()==20,"Owned lid recovery left scaffold, menu or damage"));
                }else{
                    for(int tick=0;tick<300&&context.computeOnClient(client->builder.building());tick++)context.waitTick();
                    context.runOnClient(client->require(!builder.building()&&builder.status().contains("chest is blocked"),"Unowned lid cover did not pause safely"));
                    require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(chest.up()).isOf(Blocks.DIRT)&&server.getOverworld().getBlockState(partner.up()).isOf(Blocks.DIRT)),"Chest recovery mined unrelated dirt");
                }
                if(owned)context.runOnClient(client->BuilderPacketChecks.verify());
            }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
        }
    }
    private static void cleanupAccess(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean full:List.of(false,true)){
            System.out.println("[builder-check] Finished-build cleanup needs a native exterior access column; fullPool="+full);
            fixture(context,world,builder,start);var target=start.add(0,7,5);var complete=start.north(3);var ownedPosts=new HashSet<BlockPos>();ownedPosts.add(target);
            world.getServer().runCommand("fill "+coords(start.add(-4,6,-4))+" "+coords(start.add(4,6,4))+" stone");command(world,"setblock",target,"dirt");command(world,"setblock",complete,"stone");
            if(full){for(int x:new int[]{-3,-2,-1,1,2,3})ownedPosts.add(start.add(x,0,8));ownedPosts.add(start.south(9));for(var p:ownedPosts)command(world,"setblock",p,"dirt");}
            for(String item:List.of("dirt 32","diamond_shovel","diamond_pickaxe"))world.getServer().runCommand("give @a "+item);
            world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+8.5));context.waitTicks(12);
            int limit=full?8:16;
            try{
                context.runOnClient(client->{set(builder,"Temporary Supports",true);set(builder,"Clean Temporary Supports",true);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set((double)limit);builder.install(new Schematic("complete-cleanup-access.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(complete);@SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(ownedPosts);BuilderPacketChecks.begin();builder.startBuild();});
                for(int tick=0;tick<1800&&context.computeOnClient(client->builder.building());tick++){
                    context.runOnClient(client->require(builder.temporarySupports().size()<=limit,"Cleanup access exceeded its support budget"));
                    if(tick%200==0)System.out.println((String)context.computeOnClient(client->"[cleanup-progress] "+builder.status()+" supports="+builder.temporarySupports().size()+" actor="+client.player.getEntityPos()+" target="+field(builder,"cleanupTarget")+" access="+field(builder,"accessStand")));
                    context.waitTick();
                }
                await(context,builder,1);
                context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Cleanup access left owned dirt, damage or an open menu");BuilderPacketChecks.verify();});
                require(world.getServer().computeOnServer(server->{var level=server.getOverworld();if(!level.getBlockState(complete).isOf(Blocks.STONE))return false;for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(!level.getBlockState(start.add(x,6,z)).isOf(Blocks.STONE))return false;for(int x=-8;x<=8;x++)for(int y=0;y<=10;y++)for(int z=-8;z<=12;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Cleanup altered the finished floor or left old/new temporary dirt");
            }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(128d);});}
        }
    }
    private static void shapedArrival(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Native hopper-rim arrival without orbiting the standing cell");
        for(var side:Direction.Type.HORIZONTAL){
            fixture(context,world,builder,start);var floor=start.south(3);var target=floor.up();
            command(world,"setblock",floor,"hopper[facing=east]");
            world.getServer().runCommand("tp @a "+(floor.getX()+.5+side.getOffsetX()*.095)+" "+target.getY()+" "+(floor.getZ()+.5+side.getOffsetZ()*.095)+" 180 0");context.waitTicks(12);
            var walk=context.computeOnClient(client->{require(client.player.isOnGround()&&Math.abs(client.player.getY()-target.getY())<.01,"Rim fixture did not settle on its native high collision surface");var w=new BuilderWalk();w.turning(true,45);return w;});
            boolean arrived=false;float previous=context.computeOnClient(client->client.player.getYaw());double rotation=0;
            try{
                for(int tick=0;tick<40;tick++){
                    arrived=context.computeOnClient(client->nativeStand(walk,target));float current=context.computeOnClient(client->client.player.getYaw());rotation+=Math.abs(MathHelper.wrapDegrees(current-previous));previous=current;
                    if(arrived)break;context.waitTick();
                }
                require(arrived&&rotation<=180,"Hopper rim arrival chased its nominal centre height: arrived="+arrived+" rotation="+rotation+" side="+side);
                context.runOnClient(client->require(client.player.getHealth()==20&&client.player.isOnGround(),"Shaped arrival did not retain safe native footing"));
                require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(floor).isOf(Blocks.HOPPER)),"Shaped arrival changed its floor");
            }finally{context.runOnClient(client->walk.stop());}
        }
    }
    private static void elevatedFloorEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(int height:List.of(3,6))elevatedFloorEntry(context,world,builder,start,height);
        elevatedFloorEntry(context,world,builder,start,6,12,true);
        elevatedFloorEntry(context,world,builder,start,6,12,false,true);
    }
    private static void elevatedFloorEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,int height){
        elevatedFloorEntry(context,world,builder,start,height,4,false);
    }
    private static void elevatedFloorEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,int height,int width,boolean fromEast){
        elevatedFloorEntry(context,world,builder,start,height,width,fromEast,false);
    }
    private static void elevatedFloorEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,int height,int width,boolean fromEast,boolean isolatedPost){
        System.out.println("[builder-check] Exterior column entry onto finished floor at height "+height+" width="+(width*2+1)+" isolatedPost="+isolatedPost);
        fixture(context,world,builder,start);
        world.getServer().runCommand("fill "+coords(start.add(-width,height,-4))+" "+coords(start.add(width,height,4))+" stone");
        var target=start.up(height+2);command(world,"setblock",target.south(),"stone");
        for(String item:List.of("black_shulker_box","dirt 16","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        var strandedPosts=new HashSet<BlockPos>();
        if(isolatedPost)for(int y=0;y<5;y++){var post=start.add(0,y,9);strandedPosts.add(post);command(world,"setblock",post,"dirt");}
        world.getServer().runCommand("tp @a "+(start.getX()+.5+(fromEast?width+2:0))+" "+(start.getY()+(isolatedPost?5:0))+" "+(start.getZ()+.5+(fromEast?0:isolatedPost?9:6)));context.waitTicks(12);
        try{
            context.runOnClient(client->{
                var walk=new BuilderWalk();var top=fromEast?start.add(width+1,Math.min(6,height+1),0):start.add(0,Math.min(6,height+1),5);var view=fromEast?start.up(height+1).east():start.up(height+1).north();
                require(!walk.canStand(top)&&walk.canReachFromPillar(top,view),"Future exterior column does not expose the elevated floor");
                require(!walk.canStand(top)&&client.world.getBlockState(top.down()).isAir(),"Pillar feasibility changed world collision or leaked its mask");
                set(builder,"Temporary Supports",true);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(16d);set(builder,"Auto Buy When Missing",false);
                builder.install(new Schematic("exterior-entry.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.BLACK_SHULKER_BOX.getDefaultState().with(net.minecraft.block.ShulkerBoxBlock.FACING,Direction.NORTH)}));builder.setOrigin(target);BuilderPacketChecks.begin();builder.startBuild();
                if(isolatedPost){
                    @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(strandedPosts);
                    require(!walk.canReachStand(start.up(height+1))&&!walk.canReachFromPillar(start.add(0,6,9),start.up(height+1)),"Isolated post unexpectedly reaches the finished floor");
                }
            });
            if(isolatedPost){
                int firstDrop=-1,landed=-1;
                for(int tick=0;tick<900;tick++){
                    double playerY=context.computeOnClient(client->client.player.getY());
                    if(firstDrop<0&&playerY<start.getY()+4.5)firstDrop=tick;
                    if(playerY<=start.getY()+1.1){landed=tick;break;}
                    if(firstDrop>=0)require(tick-firstDrop<=240,"Committed descent restarted expensive placement searches between steps");
                    context.waitTick();
                }
                require(firstDrop>=0&&landed>=firstDrop&&landed-firstDrop<=240,"Isolated scaffold did not finish its checked descent promptly");
            }
            await(context,builder,1800);
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Exterior entry left supports/damage/menu");BuilderPacketChecks.verify();});
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();if(!level.getBlockState(target).equals(Blocks.BLACK_SHULKER_BOX.getDefaultState().with(net.minecraft.block.ShulkerBoxBlock.FACING,Direction.NORTH)))return false;for(int x=-width;x<=width;x++)for(int z=-4;z<=4;z++)if(!level.getBlockState(start.add(x,height,z)).isOf(Blocks.STONE))return false;for(int x=-width-4;x<=width+4;x++)for(int y=0;y<=10;y++)for(int z=-10;z<=10;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Exterior entry removed the finished floor or left temporary dirt");
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(128d);});}
    }
    private static void thickWallEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean cleanOnly:List.of(false,true))thickWallEntry(context,world,builder,start,cleanOnly);
    }
    private static void thickWallEntry(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean cleanOnly){
        System.out.println("[builder-check] Prove a two-deep elevated wall entry; restore all walls; cleanup-only="+cleanOnly);
        fixture(context,world,builder,start);var origin=start.south(3);var target=origin.add(3,3,3);var spawn=origin.west(3).south(3);
        var blocks=new BlockState[7*6*7];Arrays.fill(blocks,Blocks.AIR.getDefaultState());
        for(int y=2;y<6;y++)for(int z=0;z<7;z++)for(int x=0;x<7;x++)
            if(y==2||y==5||x<2||x>4||z<2||z>4)blocks[(y*7+z)*7+x]=Blocks.STONE.getDefaultState();
        if(!cleanOnly)blocks[(3*7+3)*7+3]=Blocks.STONE.getDefaultState();
        var schematic=new Schematic("thick-elevated-entry.nbt","test",7,6,7,BlockPos.ORIGIN,blocks);
        world.getServer().runOnServer(server->{var level=server.getOverworld();for(int i=0;i<schematic.size();i++){var pos=origin.add(schematic.transformed(i,0,"None"));if(!pos.equals(target)&&!schematic.state(i).isAir())level.setBlockState(pos,schematic.state(i),Block.NOTIFY_ALL);}});
        if(cleanOnly)command(world,"setblock",target,"dirt");
        world.getServer().runCommand("tp @a "+(spawn.getX()+.5)+" "+spawn.getY()+" "+(spawn.getZ()+.5)+" 0 0");
        // Include repair stock: mined wall drops may fall below the exterior
        // platform, and this geometry test deliberately has no chest or market.
        for(String item:List.of("stone 16","dirt 64","diamond_pickaxe","diamond_shovel"))world.getServer().runCommand("give @a "+item);context.waitTicks(10);
        try{
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(schematic);builder.setOrigin(origin);if(cleanOnly)((Set<BlockPos>)field(builder,"supports")).add(target);BuilderPacketChecks.begin();builder.startBuild();});
            await(context,builder,3600);
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<schematic.size();i++)if(!schematic.state(i).isAir()&&!level.getBlockState(origin.add(schematic.transformed(i,0,"None"))).equals(schematic.state(i)))return false;for(var pos:BlockPos.iterate(origin.add(-7,0,-7),origin.add(13,9,13)))if(level.getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Thick entry left dirt or failed to restore a finished wall");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Thick entry did not finish safely");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void sameLevelStaging(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Ground-level posts cannot trigger a destructive staging tour");
        var target=start.add(0,6,3);var posts=Set.of(start.east(4),start.west(4),start.north(4),start.south(6));
        for(var post:posts)command(world,"setblock",post,"dirt");
        world.getServer().runCommand("fill "+coords(start.add(1,0,3))+" "+coords(start.add(1,3,3))+" stone");
        for(String item:List.of("stone 1","dirt 24","diamond_shovel"))world.getServer().runCommand("give @a "+item);
        context.waitTicks(6);
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);builder.install(new Schematic("same-level-staging.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(posts);
                try{
                    var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,0);
                    var search=AutoBuilder.class.getDeclaredMethod("prepareSupportDescent",List.class);search.setAccessible(true);
                    // A bounded search may yield on a slower runner without
                    // committing a descent. Finish it and inspect its intent
                    // on every slice rather than treating a yield as a route.
                    boolean searching=true;
                    for(int attempt=0;searching&&attempt<128;attempt++){
                        searching=(boolean)search.invoke(builder,List.of());
                        require(field(builder,"routeMining")==null&&field(builder,"standGoal")==null&&owned.containsAll(posts),"Same-level descent scheduled destructive work");
                    }
                    require(!searching,"Same-level descent search did not finish");
                    nav.setInt(builder,-1);
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
                BuilderPacketChecks.begin();builder.startBuild();
            });
            await(context,builder,2400);verify(world,target,1,1,1,y->Blocks.STONE);
            require(world.getServer().computeOnServer(server->{for(var pos:BlockPos.iterate(start.add(-7,0,-7),start.add(7,8,10)))if(server.getOverworld().getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Staging regression left temporary dirt behind");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Ground staging did not finish safely");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);});}
    }
    private static void accessCapacity(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        for(boolean retired:List.of(false,true)){fixture(context,world,builder,start);accessCapacity(context,world,builder,start,retired);}
    }
    private static void accessCapacity(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start,boolean retired){
        System.out.println("[builder-check] Reserve full access capacity; completed escape work="+retired);
        var target=start.add(0,6,3);var posts=new HashSet<BlockPos>();
        for(int x=5;x<=6;x++)for(int z=-3;z<=0;z++){var post=start.add(x,0,z);posts.add(post);command(world,"setblock",post,"dirt");}
        world.getServer().runCommand("fill "+coords(start.add(1,0,3))+" "+coords(start.add(1,6,3))+" stone");
        for(String item:List.of("stone 1","dirt 16","diamond_shovel"))world.getServer().runCommand("give @a "+item);context.waitTicks(6);
        boolean jumped=false;var reclaimedCompleted=new java.util.concurrent.atomic.AtomicBoolean();var addedSupport=new java.util.concurrent.atomic.AtomicBoolean();
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(8d);
                builder.install(new Schematic("access-capacity.nbt","test",retired?2:1,1,1,BlockPos.ORIGIN,retired?new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}:new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(posts);
                if(retired){
                    @SuppressWarnings("unchecked")var escape=(Set<BlockPos>)field(builder,"escapeSupports");escape.addAll(posts);
                    @SuppressWarnings("unchecked")var owners=(Map<BlockPos,Integer>)field(builder,"escapeSupportWork");
                    for(var post:posts)owners.put(post,post.getX()==start.getX()+5?0:1);
                }
                BuilderPacketChecks.begin();builder.startBuild();
                try{
                    var stand=start.up(4);var eye=Vec3d.ofBottomCenter(stand).add(0,client.player.getStandingEyeHeight(),0);var body=client.player.getBoundingBox().offset(eye.subtract(client.player.getEyePos()));
                    var plan=AutoBuilder.class.getDeclaredMethod("placement",BlockPos.class,BlockState.class,Item.class,int.class,boolean.class,Vec3d.class,Box.class);plan.setAccessible(true);
                    require(plan.invoke(builder,target,Blocks.STONE.getDefaultState(),Items.STONE,0,false,eye,body)!=null,"Capacity fixture has no valid elevated placement view");
                    var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,0);
                    var commit=AutoBuilder.class.getDeclaredMethod("commitAccess",BlockPos.class,boolean.class);commit.setAccessible(true);commit.invoke(builder,stand,true);
                    ((BuilderWalk)field(builder,"walker")).requestRecovery();
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            for(int tick=0;tick<2400&&context.computeOnClient(client->builder.building());tick++){
                boolean inJump=context.computeOnClient(client->{
                    require(builder.temporarySupports().size()<=8,"Access exceeded its eight-support pool");
                    if(retired&&builder.state(0)!=AutoBuilder.CORRECT){
                        @SuppressWarnings("unchecked")var escape=(Set<BlockPos>)field(builder,"escapeSupports");
                        require(posts.stream().filter(p->p.getX()==start.getX()+5).allMatch(p->builder.temporarySupports().contains(p)&&escape.contains(p)),"Capacity reclaimed escape footing for unfinished work");
                        if(posts.stream().filter(p->p.getX()==start.getX()+6).anyMatch(p->!builder.temporarySupports().contains(p)))reclaimedCompleted.set(true);
                        if(builder.temporarySupports().stream().anyMatch(p->!posts.contains(p)))addedSupport.set(true);
                    }
                    if((int)field(builder,"recoveryPhase")!=2)return false;
                    require(posts.stream().filter(builder.temporarySupports()::contains).count()<=4,"Access jumped before freeing the complete column budget");return true;
                });
                jumped|=inJump;context.waitTick();
            }
            await(context,builder,100);verify(world,target,1,1,1,y->Blocks.STONE);
            require(world.getServer().computeOnServer(server->{for(var pos:BlockPos.iterate(start.add(-7,0,-7),start.add(7,8,10)))if(server.getOverworld().getBlockState(pos).isOf(Blocks.DIRT))return false;return true;}),"Reserved column left temporary dirt behind");
            context.runOnClient(client->{require(builder.temporarySupports().isEmpty()&&client.player.getHealth()==20&&client.currentScreen==null,"Reserved access did not complete safely");BuilderPacketChecks.verify();});
            // The original column case must exercise its actual jump budget.
            // Protected-work capacity may instead use a valid alternative view.
            // Require real reclamation and new scaffolding for that variant.
            require(retired?reclaimedCompleted.get()&&addedSupport.get():jumped,"Capacity fixture did not exercise its required native support work");
            System.out.println("[builder-check] Capacity completed; retired="+retired+" jump="+jumped+" completed-post-reclaimed="+reclaimedCompleted.get()+" new-support="+addedSupport.get());
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(128d);});}
    }
    private static void exhaustedAccessCapacity(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        System.out.println("[builder-check] Exhausted capacity must yield to reachable work without mining protected posts");
        var origin=start.south(3);var ground=origin.east(2);var posts=new HashSet<BlockPos>();
        for(int x=5;x<=6;x++)for(int z=-1;z<=0;z++){var post=start.add(x,0,z);posts.add(post);command(world,"setblock",post,"dirt");}
        world.getServer().runCommand("fill "+coords(origin.east())+" "+coords(origin.east().up(6))+" stone");
        world.getServer().runCommand("give @a stone 3");world.getServer().runCommand("give @a dirt 4");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        var cells=new BlockState[42];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());cells[2]=cells[36]=cells[3]=Blocks.STONE.getDefaultState();
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(4d);
                builder.install(new Schematic("exhausted-access-capacity.nbt","test",3,7,2,BlockPos.ORIGIN,cells));builder.setOrigin(origin);
                require(builder.position(36).equals(origin.up(6))&&builder.position(2).equals(ground)&&builder.position(3).equals(origin.south()),"Exhausted fixture's schematic coordinates do not match its native targets");
                @SuppressWarnings("unchecked")var owned=(Set<BlockPos>)field(builder,"supports");owned.addAll(posts);
                @SuppressWarnings("unchecked")var escape=(Set<BlockPos>)field(builder,"escapeSupports");escape.addAll(posts);
                BuilderPacketChecks.begin();builder.startBuild();
                try{
                    var stand=start.up(4);var eye=Vec3d.ofBottomCenter(stand).add(0,client.player.getStandingEyeHeight(),0);var body=client.player.getBoundingBox().offset(eye.subtract(client.player.getEyePos()));
                    var plan=AutoBuilder.class.getDeclaredMethod("placement",BlockPos.class,BlockState.class,Item.class,int.class,boolean.class,Vec3d.class,Box.class);plan.setAccessible(true);
                    require(plan.invoke(builder,origin.up(6),Blocks.STONE.getDefaultState(),Items.STONE,36,false,client.player.getEyePos(),client.player.getBoundingBox())==null,"Exhausted fixture's high target was already reachable");
                    require(plan.invoke(builder,origin.up(6),Blocks.STONE.getDefaultState(),Items.STONE,36,false,eye,body)!=null,"Exhausted fixture has no valid future elevated view");
                    var scaffold=AutoBuilder.class.getDeclaredMethod("supportPlacement",BlockPos.class,Vec3d.class,Box.class,boolean.class);scaffold.setAccessible(true);
                    var proof=scaffold.invoke(builder,origin.up(6),eye,body,false);
                    require(proof!=null&&field(builder,"placement")==null,"Full pool discarded a hypothetical scaffold view or queued its proof");
                    var queued=AutoBuilder.class.getDeclaredField("placement");queued.setAccessible(true);queued.set(builder,proof);
                    var place=AutoBuilder.class.getDeclaredMethod("placeTick");place.setAccessible(true);place.invoke(builder);
                    require(field(builder,"placement")==null&&field(builder,"routeMining")==null&&builder.temporarySupports().equals(posts),"Full pool queued an extra support or reclaimed protected posts");
                    require(plan.invoke(builder,ground,Blocks.STONE.getDefaultState(),Items.STONE,2,false,client.player.getEyePos(),client.player.getBoundingBox())!=null&&plan.invoke(builder,origin.south(),Blocks.STONE.getDefaultState(),Items.STONE,3,false,client.player.getEyePos(),client.player.getBoundingBox())!=null,"Exhausted fixture's ground work is not natively reachable");
                    var nav=AutoBuilder.class.getDeclaredField("navigatingCell");nav.setAccessible(true);nav.setInt(builder,36);
                    var commit=AutoBuilder.class.getDeclaredMethod("commitAccess",BlockPos.class,boolean.class);commit.setAccessible(true);commit.invoke(builder,stand,true);
                    ((BuilderWalk)field(builder,"walker")).requestRecovery();
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            for(int tick=0;tick<120&&world.getServer().computeOnServer(server->!server.getOverworld().getBlockState(ground).isOf(Blocks.STONE)||!server.getOverworld().getBlockState(origin.south()).isOf(Blocks.STONE));tick++)context.waitTick();
            System.out.println((String)context.computeOnClient(client->"[capacity-yield-progress] "+builder.status()+" player="+client.player.getEntityPos()+" nav="+field(builder,"navigatingCell")+" access="+field(builder,"accessStand")+" goal="+field(builder,"standGoal")+" ground="+client.world.getBlockState(ground)+" second="+client.world.getBlockState(origin.south())+" supports="+builder.temporarySupports().size()));
            verify(world,ground,1,1,1,y->Blocks.STONE);verify(world,origin.south(),1,1,1,y->Blocks.STONE);
            require(world.getServer().computeOnServer(server->posts.stream().allMatch(p->server.getOverworld().getBlockState(p).isOf(Blocks.DIRT))),"Exhausted access mined protected escape posts");
            context.runOnClient(client->{require(builder.temporarySupports().equals(posts)&&client.player.getHealth()==20&&client.currentScreen==null,"Exhausted access changed supports or player safety");BuilderPacketChecks.verify();});
        }finally{context.runOnClient(client->{BuilderPacketChecks.recording=false;builder.setEnabled(false);((dev.maro.setting.NumberSetting)field(builder,"tempDirt")).set(128d);});}
    }
    private static void layerTail(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        // Only diagonal standing cells are safe. A wall blocks the initial placement ray,
        // and the old cardinal-only search leaves this final layer block idle indefinitely.
        var target=start.add(0,0,4);
        for(var side:Direction.Type.HORIZONTAL)for(int distance=1;distance<=3;distance++)command(world,"setblock",target.offset(side,distance).down(),"lava");
        command(world,"setblock",target.north(2),"stone");command(world,"setblock",target.north(2).up(),"stone");
        world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        context.runOnClient(client->{builder.install(new Schematic("diagonal-layer-tail.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();});
        await(context,builder,400);verify(world,target,1,1,1,y->Blocks.STONE);
        context.runOnClient(client->require(client.player.getHealth()==20&&client.player.getY()>=start.getY()-.2,"Diagonal route crossed a hazard"));

        fixture(context,world,builder,start);world.getServer().runCommand("give @a stone 3");world.getServer().runCommand("give @a dirt 12");world.getServer().runCommand("give @a diamond_shovel 1");context.waitTicks(6);
        // A lower layer finishes first, then its last two floating blocks need short dirt columns.
        // No single adjacent support has an existing attachment face until the column is built.
        var origin=start.add(0,0,2);var cells=new BlockState[5*4];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        cells[4]=Blocks.STONE.getDefaultState();cells[15]=cells[17]=Blocks.STONE.getDefaultState();
        context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("floating-layer-tail.nbt","test",5,4,1,BlockPos.ORIGIN,cells));builder.setOrigin(origin);builder.startBuild();});
        // Include native travel/pickup when the finite dirt reserve is recycled,
        // then verify all server blocks and complete cleanup below.
        await(context,builder,1100);
        require(world.getServer().computeOnServer(server->{var level=server.getOverworld();if(!level.getBlockState(origin.east(4)).isOf(Blocks.STONE)||!level.getBlockState(origin.up(3)).isOf(Blocks.STONE)||!level.getBlockState(origin.east(2).up(3)).isOf(Blocks.STONE))return false;for(int x=-1;x<=5;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=1;z++)if(level.getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Layer tail blocks were not built or temporary support columns were left behind");
        context.runOnClient(client->require(builder.temporarySupports().isEmpty(),"Layer-tail scaffold tracking did not finish cleanup"));
    }
    private static void startupChestScan(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var first=start.east(3);var second=start.west(3);var empty=start.north(3);var target=start.south(2);
        for(var chest:List.of(first,second,empty)){
            command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",chest.east(),"chest[facing=north,type=right]");
        }
        world.getServer().runCommand("item replace block "+coords(first)+" container.0 with stone 2");
        world.getServer().runCommand("item replace block "+coords(second)+" container.0 with glass 2");
        world.getServer().runCommand("item replace block "+coords(second)+" container.1 with iron_pickaxe 1");
        world.getServer().runCommand("item replace block "+coords(first)+" container.1 with netherite_shovel 1");context.waitTicks(6);
        try{
            context.runOnClient(client->{
                set(builder,"Prepare Whole Build",true);set(builder,"Auto Buy Tools",true);set(builder,"Auto Buy When Missing",true);builder.auctionBudget(1000);
                builder.install(new Schematic("delayed-chest-preparation.nbt","test",2,2,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GLASS.getDefaultState()}));builder.setOrigin(target);
                for(var chest:List.of(first,second,empty)){client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();}
                BuilderChestDelay.begin(20);BuilderPacketChecks.begin();builder.startBuild();
            });
            for(int i=0;i<1400;i++){
                context.runOnClient(client->{
                    if(BuilderChestDelay.received<3)require(!builder.buying()&&!builder.building(),"Preparation proceeded before all selected chest inventories arrived");
                    BuilderChestDelay.step();
                });
                if(context.computeOnClient(client->!builder.building()&&!builder.buying()&&!builder.depositing()))break;
                context.waitTick();
            }
            context.runOnClient(client->{
                require(builder.status().equals("Build complete")&&builder.sessionSpend()==0,"Stored supplies were not used without AH buying: "+builder.status());
                require(BuilderChestDelay.received>=3,"Preparation skipped an empty or stocked selected chest");
                require(builder.inventoryCount(Items.IRON_PICKAXE)==1&&builder.inventoryCount(Items.NETHERITE_SHOVEL)==1,"Build tools were left in supply chests");
                BuilderPacketChecks.verify();
            });
            verify(world,target,2,2,1,y->y==0?Blocks.STONE:Blocks.GLASS);
        }finally{context.runOnClient(client->{BuilderChestDelay.end();BuilderPacketChecks.recording=false;});}
        // A chest with no inventory receipt must stop preparation rather than be counted empty.
        fixture(context,world,builder,start);command(world,"setblock",first,"chest[facing=north,type=left]");command(world,"setblock",first.east(),"chest[facing=north,type=right]");context.waitTicks(6);
        try{
            context.runOnClient(client->{set(builder,"Prepare Whole Build",true);builder.auctionBudget(1000);builder.install(new Schematic("missing-chest-receipt.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(first),Direction.WEST,first,false);builder.markContainer();BuilderChestDelay.begin(1000);builder.startBuild();});
            for(int i=0;i<350&&context.computeOnClient(client->builder.depositing());i++){context.runOnClient(client->{require(!builder.buying(),"Missing chest receipt started AH buying");BuilderChestDelay.step();});context.waitTick();}
            context.runOnClient(client->require(!builder.buying()&&!builder.depositing()&&builder.status().contains("contents did not arrive"),"Missing chest inventory did not pause preparation: "+builder.status()));
        }finally{context.runOnClient(client->BuilderChestDelay.end());}
    }
    private static void fixture(ClientGameTestContext context,TestSingleplayerContext singleplayer,AutoBuilder builder,BlockPos start){
        context.runOnClient(client->set(builder,"Builder Homes",false));
        context.runOnClient(client->set(builder,"Head Spoofing",false));
        // Liquid-only fixtures disable restocking. Restore the default before
        // each independent case, including runs that reuse the test config.
        context.runOnClient(client->set(builder,"Restock When Empty",true));
        context.runOnClient(client->{builder.setEnabled(false);client.setScreen(null);set(builder,"Save Build Progress",false);set(builder,"Build Mode","Automatic");set(builder,"Material Supply","Layer by Layer");set(builder,"Auto Move",true);set(builder,"Auto Unstuck",true);set(builder,"Mine Out Schematic",false);set(builder,"Auto Eat",false);set(builder,"Prepare Whole Build",false);set(builder,"Buy Steak",true);set(builder,"Auto Buy Tools",false);set(builder,"Stop On Staff Nearby",false);set(builder,"Auto Buy When Missing",false);set(builder,"Support Dirt Reserve",0);set(builder,"Temporary Supports",false);set(builder,"Rotation","0");set(builder,"Mirror","None");button(builder,"Clear Restock Marks").press();});
        singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(20);player.getHungerManager().setSaturationLevel(5);});
        singleplayer.getServer().runCommand("gamemode creative @a");singleplayer.getServer().runCommand("fill "+coords(start.add(-16,-1,-16))+" "+coords(start.add(16,-1,16))+" stone");
        singleplayer.getServer().runCommand("fill "+coords(start.add(-16,0,-16))+" "+coords(start.add(16,12,16))+" air");
        singleplayer.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+.5)+" 0 0");singleplayer.getServer().runCommand("clear @a");
        if(start.getX()<-20000){
            // Let the far-away teleport and its new floor reach the client before enabling
            // survival; the gametest disables network synchronization during setup.
            context.waitTicks(20);
            singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.onLanding();player.setVelocity(Vec3d.ZERO);});
            context.runOnClient(client->{client.player.onLanding();client.player.setVelocity(Vec3d.ZERO);});
        }
        singleplayer.getServer().runCommand("gamemode survival @a");context.waitTicks(10);
    }
    private static boolean active(AutoBuilder builder){return builder.building()||builder.buying()||builder.depositing()||((BuilderHomes)field(builder,"homes")).busy()||(boolean)field(builder,"homeSetupResume");}
    private static void await(ClientGameTestContext context,AutoBuilder builder,int limit){
        for(int i=0;i<limit;i++){
            if(context.computeOnClient(client->!active(builder)))break;
            if((Boolean.getBoolean("maro.gametest.builderChestReturnOnly")||Boolean.getBoolean("maro.gametest.builderCleanupOnly")||Boolean.getBoolean("maro.gametest.builderSealedEscapeOnly"))&&limit>=2400&&(i%200==0||i<200&&i%20==0)){String progress=context.computeOnClient(client->"[room-progress] "+field(builder,"correct")+"/82 "+builder.status()+" player="+client.player.getEntityPos()+" nav="+field(builder,"navigatingCell")+" openings="+field(builder,"floorAccessWork")+" goal="+field(builder,"standGoal")+" descent="+field(builder,"descentView")+" recovery="+field(builder,"recoveryPhase")+" floorCursor="+field(builder,"floorSearchCursor")+" restoration="+field(builder,"openingRestoration"));System.out.println(progress);}
            if(Boolean.getBoolean("maro.gametest.builderSealedEscapeOnly")&&i%200==0)context.runOnClient(client->reportCleanupProof(builder));
            if(Boolean.getBoolean("maro.gametest.builderChestReturnOnly")&&limit>=2400&&i%100==0)System.out.println((String)context.computeOnClient(client->"[passage-progress] mining="+field(builder,"mining")+" routeMining="+field(builder,"routeMining")+" passage="+field(builder,"passageBlocks")+" destination="+field(builder,"passageStand")+" yaw/pitch="+client.player.getYaw()+","+client.player.getPitch()));
            if(limit>=18000&&i%200==0){String progress=context.computeOnClient(client->{int complete=0;for(int cell=0;cell<builder.schematic().size();cell++)if(!builder.desired(cell).isAir()&&builder.state(cell)==AutoBuilder.CORRECT)complete++;return "[stash-progress] "+complete+"/710 supports="+builder.temporarySupports().size()+" "+builder.status()+" player="+client.player.getEntityPos()+" view="+client.player.getYaw()+","+client.player.getPitch()+" placement="+field(builder,"placement")+" goal="+field(builder,"standGoal")+" needed="+field(builder,"needed");});System.out.println(progress);}
            context.waitTick();
        }
        String status=context.computeOnClient(client->builder.status());
        if(context.computeOnClient(client->active(builder))){
            context.takeScreenshot("maro-builder-stalled");
            String details=context.computeOnClient(client->{StringBuilder text=new StringBuilder(" player="+client.player.getEntityPos()+" inventory="+builder.remainingMaterials()+" supports="+builder.temporarySupports()+" placement="+field(builder,"placement")+" goal="+field(builder,"standGoal")+" recovery="+field(builder,"recoveryAttempts"));int shown=0;for(int i=0;i<builder.schematic().size()&&shown<12;i++)if(builder.state(i)!=AutoBuilder.CORRECT&&builder.state(i)!=AutoBuilder.IGNORED){shown++;text.append(" cell ").append(i).append(" position=").append(builder.position(i)).append(" status=").append(builder.state(i)).append(" desired=").append(builder.desired(i)).append(" actual=").append(client.world.getBlockState(builder.position(i)));}return text.toString();});
            throw new AssertionError("Builder did not finish: "+status+details);
        }
        require(status.equals("Build complete"),"Builder stopped: "+status);
    }
    private static void reportCleanupProof(AutoBuilder builder){
        if(!builder.status().contains("Cleanup")&&!builder.status().contains("clean temporary"))return;
        try{
            var walk=(dev.maro.builder.BuilderWalk)field(builder,"walker");
            var safe=builder.getClass().getDeclaredMethod("safeToRecycle",BlockPos.class);safe.setAccessible(true);
            var geometry=builder.getClass().getDeclaredMethod("descentGeometry",BlockPos.class);geometry.setAccessible(true);
            var supports=builder.temporarySupports();
            System.out.println("[cleanup-proof] target="+field(builder,"cleanupTarget")+" supports="+supports+" peek="+field(builder,"peekTarget")+" floor="+field(builder,"floorSearchFeet")+" view="+field(builder,"floorSearchView")+" work="+field(builder,"floorSearchWork"));
            for(var cover:supports.stream().flatMap(p->java.util.stream.IntStream.rangeClosed(1,3).mapToObj(p::up)).distinct().toList()){
                int cell=builder.schematic().indexAt(cover.subtract(builder.position(0)),0,"None");
                if(cell<0||builder.desired(cell).isAir())continue;
                System.out.println("[cleanup-proof] cover="+cover+" cell="+cell+" state="+builder.state(cell)+" descend="+walk.canDescendThrough(cover)+" safe="+safe.invoke(builder,cover)+" approach="+walk.canReachStand(cover.up())+" geometry="+geometry.invoke(builder,cover));
            }
            var searches=(Map<?,?>)field(builder,"viewSearches");
            for(var entry:searches.entrySet()){
                var line=new StringBuilder("[cleanup-proof] search="+entry.getKey());
                for(String name:new String[]{"recoveryStage","cursor","options"}){var member=entry.getValue().getClass().getDeclaredField(name);member.setAccessible(true);line.append(' ').append(name).append('=').append(member.get(entry.getValue()));}
                System.out.println(line);
            }
        }catch(ReflectiveOperationException error){System.out.println("[cleanup-proof] diagnostics failed: "+error);}
    }
    private static void awaitSupport(ClientGameTestContext context,AutoBuilder builder){
        for(int i=0;i<500;i++){if(context.computeOnClient(client->builder.state(0)==AutoBuilder.CORRECT&&!builder.temporarySupports().isEmpty()))return;context.waitTick();}
        throw new AssertionError("Horizontal log/support fixture did not reach cleanup");
    }
    private static String coords(BlockPos p){return p.getX()+" "+p.getY()+" "+p.getZ();}
    private static void command(TestSingleplayerContext world,String command,BlockPos p,String block){world.getServer().runCommand(command+" "+coords(p)+" "+block);}
    private static void verify(TestSingleplayerContext world,BlockPos origin,int w,int h,int l,java.util.function.IntFunction<Block> expected){
        String mismatch=world.getServer().computeOnServer(server->{for(int y=0;y<h;y++)for(int z=0;z<l;z++)for(int x=0;x<w;x++){var p=origin.add(x,y,z);if(!server.getOverworld().getBlockState(p).isOf(expected.apply(y)))return p.toShortString();}return "";});require(mismatch.isEmpty(),"Server block mismatch at "+mismatch);
    }
    private static ButtonSetting button(AutoBuilder builder,String name){return (ButtonSetting)builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void set(AutoBuilder builder,String name,Object value){builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(value instanceof Number n?new JsonPrimitive(n):value instanceof Boolean b?new JsonPrimitive(b):new JsonPrimitive(value.toString()));}
    private static void require(boolean success,String message){if(!success)throw new AssertionError(message);}
}
