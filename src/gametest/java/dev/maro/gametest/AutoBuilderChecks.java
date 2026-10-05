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
        context.runOnClient(client->imports());
        AutoBuilder builder=ModuleManager.get(AutoBuilder.class);
        BlockPos start=context.computeOnClient(client->client.player.getBlockPos().up(30));
        try{
            if(Boolean.getBoolean("maro.gametest.builderStashOnly")||Boolean.getBoolean("maro.gametest.builderStashUpperOnly")){stashBuild(context,singleplayer,builder,start);return;}
            if(Boolean.getBoolean("maro.gametest.builderChestReturnOnly")){raisedChestReturn(context,singleplayer,builder,start);sealedChestReturn(context,singleplayer,builder,start);return;}
            fixture(context,singleplayer,builder,start);
            raisedTurn(context,singleplayer,start);
            fixture(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderTurnOnly"))return;
            layerTail(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            savedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            rejectedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            retainedPredictions(context,singleplayer,builder,start);
            observerAssembly(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            stalledInteractions(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            startupChestScan(context,singleplayer,builder,start);
            distantChest(context,singleplayer,builder,start);
            raisedChestReturn(context,singleplayer,builder,start);
            sealedChestReturn(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderNavigationOnly")){cancellation(context,builder,context.computeOnClient(client->builder.schematic()));return;}
            if(Boolean.getBoolean("maro.gametest.builderAuctionOnly")){BuilderAuctionChecks.run(context,singleplayer,builder);return;}
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
        var origin=start.add(0,0,2);var chest=start.east(3);
        command(world,"setblock",chest,"chest[facing=north,type=left]");command(world,"setblock",chest.east(),"chest[facing=north,type=right]");
        command(world,"setblock",origin,"stone");world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
        var saved=context.computeOnClient(client->{
            builder.install(new Schematic("persisted.litematic","test",2,1,1,new BlockPos(-2,0,3),new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(origin);
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
            // A re-created ClientWorld must retain the saved anchor and rescan actual blocks.
            try{var field=AutoBuilder.class.getDeclaredField("world");field.setAccessible(true);field.set(builder,null);}catch(Exception error){throw new AssertionError(error);}
            builder.startBuild();require(builder.origin().equals(origin),"Reconnect silently moved the saved placement");
        });
        await(context,builder,300);verify(world,origin,2,1,1,y->Blocks.STONE);
        context.runOnClient(client->require(builder.inventoryCount(Items.STONE)==0,"Resume bought/placed an already completed block"));
    }
    private static void rejectedPlacement(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos start){
        var target=start.add(0,0,2);var rejected=new java.util.concurrent.atomic.AtomicBoolean();var gate=new java.util.concurrent.atomic.AtomicBoolean(true);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClient()&&gate.get()&&hit.getBlockPos().equals(target.down())&&player.getStackInHand(hand).isOf(Items.STONE)&&rejected.compareAndSet(false,true)){((net.minecraft.server.network.ServerPlayerEntity)player).playerScreenHandler.syncState();return net.minecraft.util.ActionResult.FAIL;}
            return net.minecraft.util.ActionResult.PASS;
        });
        try{
            world.getServer().runCommand("give @a stone 1");context.waitTicks(6);
            context.runOnClient(client->{builder.install(new Schematic("rejected-placement.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);builder.startBuild();});
            await(context,builder,350);require(rejected.get(),"Server rejection fixture did not intercept the placement");verify(world,target,1,1,1,y->Blocks.STONE);
        }finally{gate.set(false);}
    }
    private static void stashBuild(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder,BlockPos original){
        var start=new BlockPos(-26210,61,-150577);var origin=start.add(-16,2,-8);var chest=start.east(3);
        Schematic stash;
        try{
            var file=java.nio.file.Files.createTempFile("maro-stash-build", ".litematic");
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/farex-small-stash.litematic")){
                java.nio.file.Files.copy(source,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);stash=SchematicIO.read(file);
            }finally{java.nio.file.Files.deleteIfExists(file);}
        }catch(java.io.IOException error){throw new AssertionError(error);}
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
        boolean upper=Boolean.getBoolean("maro.gametest.builderStashUpperOnly");
        if(upper){
            try(var source=AutoBuilderChecks.class.getResourceAsStream("/fixtures/stash-upper-supports.txt")){
                for(var line:new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()){
                    var parts=line.trim().split("\\s+");priorSupports.add(new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])));
                }
            }catch(java.io.IOException error){throw new AssertionError(error);}
            world.getServer().runOnServer(server->{
                var level=server.getOverworld();
                for(int cell=0;cell<stash.size();cell++){var expected=stash.state(cell);if(stash.local(cell).getY()<=3&&!expected.isAir()&&!(expected.getBlock() instanceof net.minecraft.block.FluidBlock)&&!(expected.getBlock() instanceof net.minecraft.block.ObserverBlock))level.setBlockState(origin.add(stash.local(cell)),expected,net.minecraft.block.Block.NOTIFY_ALL);}
                for(var support:priorSupports)level.setBlockState(support,Blocks.DIRT.getDefaultState(),net.minecraft.block.Block.NOTIFY_ALL);
            });
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a -26209.7 65 -150576.2");context.waitTicks(20);world.getServer().runCommand("gamemode survival @a");context.waitTicks(10);
            // The captured stall was after preparation and the layer-4 chest trip.
            // Restore that batch rather than starting a new preparation journey.
            var held=new HashMap<Item,Integer>();
            for(int cell=0;cell<stash.size();cell++)if(stash.local(cell).getY()==4){var expected=stash.state(cell);var item=Schematic.material(expected);if(item!=Items.AIR)held.merge(item,Schematic.units(expected),Integer::sum);if(expected.getBlock() instanceof net.minecraft.block.FlowerPotBlock)held.merge(Items.FLOWER_POT,1,Integer::sum);}
            held.put(Items.DIRT,64);held.put(Items.DIAMOND_PICKAXE,1);held.put(Items.DIAMOND_SHOVEL,1);held.put(Items.COOKED_BEEF,16);
            held.forEach((item,count)->world.getServer().runCommand("give @a "+net.minecraft.registry.Registries.ITEM.getId(item)+" "+count));context.waitTicks(6);
        }
        try{
            context.runOnClient(client->{
                set(builder,"Temporary Supports",true);set(builder,"Clean Temporary Supports",true);set(builder,"Support Dirt Reserve",64);set(builder,"Auto Buy Tools",true);
                set(builder,"Material Supply","Layer by Layer");set(builder,"Prepare Whole Build",!upper);set(builder,"Stockpile In Chests",true);set(builder,"Auto Eat",true);builder.auctionBudget(1000);
                builder.install(stash);builder.setOrigin(origin);((Set<BlockPos>)field(builder,"supports")).addAll(priorSupports);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();BuilderPacketChecks.begin();builder.startBuild();
                if(upper)try{var attempts=AutoBuilder.class.getDeclaredField("recoveryAttempts");attempts.setAccessible(true);attempts.setInt(builder,3);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            await(context,builder,36000);
            String mismatch=world.getServer().computeOnServer(server->{for(int i=0;i<stash.size();i++)if(!stash.state(i).isAir()&&!AutoBuilder.matchesBuildState(server.getOverworld().getBlockState(origin.add(stash.local(i))),stash.state(i)))return origin.add(stash.local(i)).toShortString();return "";});
            require(mismatch.isEmpty(),"Stash server mismatch at "+mismatch);
            String dirt=world.getServer().computeOnServer(server->{for(int x=-24;x<=24;x++)for(int y=0;y<=12;y++)for(int z=-24;z<=24;z++)if(server.getOverworld().getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return start.add(x,y,z).toShortString();return "";});
            require(dirt.isEmpty(),"Stash left a temporary block on the server at "+dirt);
            context.runOnClient(client->{BuilderPacketChecks.verify();require(builder.temporarySupports().isEmpty(),"Stash left temporary supports");require(client.currentScreen==null,"Stash left its supply menu open");});
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
                arrived=context.computeOnClient(client->walk.standAt(underCeiling));if(arrived)break;context.waitTick();
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
            }
            context.runOnClient(client->{
                require(builder.status().equals("Build complete"),"Sealed build did not return from storage: "+builder.status());
                require(builder.inventoryCount(Items.DIAMOND_PICKAXE)==1&&builder.inventoryCount(Items.DIAMOND_SHOVEL)==1,"Sealed build missed stored tools");
                require(client.player.getHealth()==20&&client.currentScreen==null&&builder.temporarySupports().isEmpty(),"Sealed build damaged player or left a menu/support");BuilderPacketChecks.verify();
            });
            require(world.getServer().computeOnServer(server->{var level=server.getOverworld();for(int i=0;i<room.size();i++)if(!room.state(i).isAir()&&!level.getBlockState(origin.add(room.local(i))).isOf(Blocks.STONE))return false;for(int x=-5;x<=8;x++)for(int y=0;y<=10;y++)for(int z=-5;z<=5;z++)if(level.getBlockState(start.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Sealed build left its access opening or temporary blocks behind");
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
    private static Object field(AutoBuilder builder,String name){try{var field=AutoBuilder.class.getDeclaredField(name);field.setAccessible(true);return field.get(builder);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
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
    private static void raisedTurn(ClientGameTestContext context,TestSingleplayerContext world,BlockPos start){
        command(world,"setblock",start.east(),"stone");
        world.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+.5)+" 90 0");context.waitTicks(10);
        var walker=context.computeOnClient(client->new BuilderWalk());var goal=start.east().up();walker.turning(true,45);boolean arrived=false;
        try{
            for(int tick=0;tick<200&&!arrived;tick++){
                arrived=context.computeOnClient(client->{
                    boolean done=walker.standAt(goal);
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
            arrived=false;for(int tick=0;tick<200&&!arrived;tick++){arrived=context.computeOnClient(client->walker.standAt(goal));context.waitTick();}
            require(arrived,"Walker did not settle at its current-cell placement view");context.waitTicks(10);
            context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(walker.standingPoint(goal))<.28*.28&&client.player.getVelocity().horizontalLengthSquared()<.0004&&client.player.getHealth()==20,"Placement view did not remain settled after native movement"));
            // Reproduce arriving near the view with walking momentum and facing
            // away. Precision movement must brake rather than orbit the point.
            var flat=start.south(3);
            for(int facing=0;facing<4;facing++){
                world.getServer().runCommand("tp @a "+(flat.getX()+.9)+" "+flat.getY()+" "+(flat.getZ()+.1)+" "+(facing*90)+" 0");context.waitTicks(6);
                context.runOnClient(client->{walker.stop();client.player.setVelocity(.11,0,-.1);});
                arrived=false;for(int tick=0;tick<160&&!arrived;tick++){arrived=context.computeOnClient(client->walker.standAt(flat));context.waitTick();}
                require(arrived,"Placement approach orbited instead of settling from facing "+facing);context.waitTicks(8);
                context.runOnClient(client->require(client.player.getEntityPos().squaredDistanceTo(walker.standingPoint(flat))<.28*.28&&client.player.getVelocity().horizontalLengthSquared()<.0004,"Placement braking did not hold its settled native position"));
            }
        }finally{context.runOnClient(client->walker.stop());}
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
        context.runOnClient(client->{builder.setEnabled(false);client.setScreen(null);set(builder,"Save Build Progress",false);set(builder,"Build Mode","Automatic");set(builder,"Auto Move",true);set(builder,"Auto Unstuck",true);set(builder,"Mine Out Schematic",false);set(builder,"Auto Eat",false);set(builder,"Prepare Whole Build",false);set(builder,"Buy Steak",true);set(builder,"Auto Buy Tools",false);set(builder,"Stop On Staff Nearby",false);set(builder,"Auto Buy When Missing",false);set(builder,"Support Dirt Reserve",0);set(builder,"Temporary Supports",false);set(builder,"Rotation","0");set(builder,"Mirror","None");button(builder,"Clear Restock Marks").press();});
        singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(20);player.getHungerManager().setSaturationLevel(5);});
        singleplayer.getServer().runCommand("gamemode creative @a");singleplayer.getServer().runCommand("fill "+coords(start.add(-16,-1,-16))+" "+coords(start.add(16,-1,16))+" stone");
        singleplayer.getServer().runCommand("fill "+coords(start.add(-16,0,-16))+" "+coords(start.add(16,6,16))+" air");
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
    private static void await(ClientGameTestContext context,AutoBuilder builder,int limit){
        for(int i=0;i<limit;i++){
            if(context.computeOnClient(client->!builder.building()&&!builder.buying()&&!builder.depositing()))break;
            if(limit>=18000&&i%200==0){String progress=context.computeOnClient(client->{int complete=0;for(int cell=0;cell<builder.schematic().size();cell++)if(!builder.desired(cell).isAir()&&builder.state(cell)==AutoBuilder.CORRECT)complete++;return "[stash-progress] "+complete+"/710 supports="+builder.temporarySupports().size()+" "+builder.status()+" player="+client.player.getEntityPos()+" view="+client.player.getYaw()+","+client.player.getPitch()+" placement="+field(builder,"placement")+" goal="+field(builder,"standGoal")+" needed="+field(builder,"needed");});System.out.println(progress);}
            context.waitTick();
        }
        String status=context.computeOnClient(client->builder.status());
        if(context.computeOnClient(client->builder.building()||builder.buying()||builder.depositing())){
            context.takeScreenshot("maro-builder-stalled");
            String details=context.computeOnClient(client->{StringBuilder text=new StringBuilder(" player="+client.player.getEntityPos()+" inventory="+builder.remainingMaterials()+" supports="+builder.temporarySupports()+" placement="+field(builder,"placement")+" goal="+field(builder,"standGoal")+" recovery="+field(builder,"recoveryAttempts"));int shown=0;for(int i=0;i<builder.schematic().size()&&shown<12;i++)if(builder.state(i)!=AutoBuilder.CORRECT&&builder.state(i)!=AutoBuilder.IGNORED){shown++;text.append(" cell ").append(i).append(" position=").append(builder.position(i)).append(" status=").append(builder.state(i)).append(" desired=").append(builder.desired(i)).append(" actual=").append(client.world.getBlockState(builder.position(i)));}return text.toString();});
            throw new AssertionError("Builder did not finish: "+status+details);
        }
        require(status.equals("Build complete"),"Builder stopped: "+status);
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
