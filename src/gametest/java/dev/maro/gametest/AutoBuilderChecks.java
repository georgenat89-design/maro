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
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static NbtCompound vector(int x,int y,int z){var n=new NbtCompound();n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);return n;}
    static void run(ClientGameTestContext context,TestSingleplayerContext singleplayer){
        context.runOnClient(client->imports());
        AutoBuilder builder=ModuleManager.get(AutoBuilder.class);
        BlockPos start=context.computeOnClient(client->client.player.getBlockPos().up(30));
        try{
            fixture(context,singleplayer,builder,start);
            layerTail(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            savedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            rejectedPlacement(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            stalledInteractions(context,singleplayer,builder,start);
            fixture(context,singleplayer,builder,start);
            if(Boolean.getBoolean("maro.gametest.builderNavigationOnly")){cancellation(context,builder,context.computeOnClient(client->builder.schematic()));return;}
            if(Boolean.getBoolean("maro.gametest.builderAuctionOnly")){BuilderAuctionChecks.run(context,singleplayer,builder);return;}
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
        context.runOnClient(client->{builder.install(new Schematic("held-restock-item.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(target);client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);builder.markContainer();builder.startBuild();});
        for(int i=0;i<250&&context.computeOnClient(client->field(builder,"ownedHandler")==null);i++)context.waitTick();
        context.runOnClient(client->require(field(builder,"ownedHandler")!=null,"Restock fixture did not open its chest"));
        world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.currentScreenHandler.setCursorStack(new ItemStack(Items.STONE,4));player.currentScreenHandler.syncState();});
        await(context,builder,450);verify(world,target,1,1,1,y->Blocks.STONE);
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
        await(context,builder,700);
        require(world.getServer().computeOnServer(server->{var level=server.getOverworld();if(!level.getBlockState(origin.east(4)).isOf(Blocks.STONE)||!level.getBlockState(origin.up(3)).isOf(Blocks.STONE)||!level.getBlockState(origin.east(2).up(3)).isOf(Blocks.STONE))return false;for(int x=-1;x<=5;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=1;z++)if(level.getBlockState(origin.add(x,y,z)).isOf(Blocks.DIRT))return false;return true;}),"Layer tail blocks were not built or temporary support columns were left behind");
        context.runOnClient(client->require(builder.temporarySupports().isEmpty(),"Layer-tail scaffold tracking did not finish cleanup"));
    }
    private static void fixture(ClientGameTestContext context,TestSingleplayerContext singleplayer,AutoBuilder builder,BlockPos start){
        context.runOnClient(client->{builder.setEnabled(false);client.setScreen(null);set(builder,"Save Build Progress",false);set(builder,"Build Mode","Automatic");set(builder,"Mine Out Schematic",false);set(builder,"Auto Eat",false);set(builder,"Prepare Whole Build",false);set(builder,"Buy Steak",true);set(builder,"Auto Buy Tools",false);set(builder,"Stop On Staff Nearby",false);set(builder,"Auto Buy When Missing",false);set(builder,"Support Dirt Reserve",0);set(builder,"Temporary Supports",false);set(builder,"Rotation","0");set(builder,"Mirror","None");button(builder,"Clear Restock Marks").press();});
        singleplayer.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();player.getHungerManager().setFoodLevel(20);player.getHungerManager().setSaturationLevel(5);});
        singleplayer.getServer().runCommand("gamemode creative @a");singleplayer.getServer().runCommand("fill "+coords(start.add(-16,-1,-16))+" "+coords(start.add(16,-1,16))+" stone");
        singleplayer.getServer().runCommand("fill "+coords(start.add(-16,0,-16))+" "+coords(start.add(16,6,16))+" air");
        singleplayer.getServer().runCommand("tp @a "+(start.getX()+.5)+" "+start.getY()+" "+(start.getZ()+.5)+" 0 0");singleplayer.getServer().runCommand("clear @a");singleplayer.getServer().runCommand("gamemode survival @a");context.waitTicks(10);
    }
    private static void await(ClientGameTestContext context,AutoBuilder builder,int limit){
        for(int i=0;i<limit;i++){if(context.computeOnClient(client->!builder.building()))break;context.waitTick();}
        String status=context.computeOnClient(client->builder.status());
        if(context.computeOnClient(client->builder.building())){
            context.takeScreenshot("maro-builder-stalled");
            String details=context.computeOnClient(client->{StringBuilder text=new StringBuilder(" player="+client.player.getEntityPos()+" inventory="+builder.remainingMaterials());for(int i=0;i<builder.schematic().size();i++)text.append(" cell ").append(i).append(" status=").append(builder.state(i)).append(" actual=").append(client.world.getBlockState(builder.position(i)));return text.toString();});
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
