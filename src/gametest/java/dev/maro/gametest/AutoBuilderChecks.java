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
            context.runOnClient(client->{set(builder,"Build Mode","Automatic");set(builder,"Temporary Supports",false);builder.startBuild();});
            await(context,builder,400);
            verify(singleplayer,origin,2,2,2,y->y==0?Blocks.STONE:Blocks.GLASS);
            context.takeScreenshot("maro-builder-server-built");

            // Missing stone is obtained from a real server chest and then placed, without a creative give.
            fixture(context,singleplayer,builder,start);
            BlockPos chest=start.add(2,0,0);command(singleplayer,"setblock",chest,"chest");
            singleplayer.getServer().runCommand("item replace block "+chest.toShortString().replace(",","")+" container.0 with minecraft:stone 64");
            context.waitTicks(8);
            context.runOnClient(client->{
                builder.install(new Schematic("restock-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,2));builder.preview();
                client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.WEST,chest,false);button(builder,"Mark Restock Container").press();builder.startBuild();
            });
            await(context,builder,500);
            verify(singleplayer,start.add(0,0,2),1,1,1,y->Blocks.STONE);
            context.runOnClient(client->require(builder.inventoryCount(Items.STONE)==63,"Chest restock did not move server items"));

            // A far target needs normal walking, and the path must keep clear of a lava floor tile.
            fixture(context,singleplayer,builder,start);command(singleplayer,"setblock",start.add(0,-1,4),"lava");singleplayer.getServer().runCommand("give @a stone 64");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("walk-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.STONE.getDefaultState()}));builder.setOrigin(start.add(0,0,12));builder.startBuild();});
            await(context,builder,500);verify(singleplayer,start.add(0,0,12),1,1,1,y->Blocks.STONE);
            context.runOnClient(client->require(client.player.getZ()>start.getZ()+6&&client.player.getY()>=start.getY()-.2,"Builder did not walk to its target safely"));

            // Side-face placement creates a horizontal log. Only this builder's temporary support is cleaned.
            fixture(context,singleplayer,builder,start);singleplayer.getServer().runCommand("give @a oak_log 64");singleplayer.getServer().runCommand("give @a dirt 64");context.waitTicks(5);
            context.runOnClient(client->{set(builder,"Temporary Supports",true);builder.install(new Schematic("axis-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new BlockState[]{Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS,Direction.Axis.X)}));builder.setOrigin(start.add(0,0,2));builder.startBuild();});
            awaitSupport(context,builder);
            BlockPos support=context.computeOnClient(client->builder.temporarySupports().iterator().next());
            context.runOnClient(client->builder.pause("Step-off cleanup fixture"));
            singleplayer.getServer().runCommand("tp @a "+(support.getX()+.5)+" "+(support.getY()+1)+" "+(support.getZ()+.5));context.waitTicks(8);
            context.runOnClient(client->builder.startBuild());
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

            fixture(context,singleplayer,builder,start);BuilderAuctionChecks.run(context,singleplayer,builder);

            context.runOnClient(client->{
                builder.pause("Screenshot");client.setScreen(new ClickGuiScreen());((ClickGuiScreen)client.currentScreen).openModuleSettings(builder);
                require(client.currentScreen instanceof BuilderControlScreen,"Builder settings did not open the simple control panel");
                require(builder.getSettings().size()<50,"Unnecessary settings still clutter the builder");
                require(builder.buildMode().equals("Automatic"),"Automatic build mode is unavailable");
            });context.waitTicks(5);context.takeScreenshot("maro-builder-control-panel");
            context.runOnClient(client->{client.setScreen(new ClickGuiScreen());((ClickGuiScreen)client.currentScreen).openModuleOptions(builder);});context.waitTicks(5);context.takeScreenshot("maro-builder-simplified-options");
        }finally{
            context.runOnClient(client->{builder.setEnabled(false);client.options.useKey.setPressed(false);client.options.forwardKey.setPressed(false);client.options.jumpKey.setPressed(false);client.setScreen(null);});
            singleplayer.getServer().runCommand("gamemode creative @a");
        }
    }
    private static void fixture(ClientGameTestContext context,TestSingleplayerContext singleplayer,AutoBuilder builder,BlockPos start){
        context.runOnClient(client->{builder.setEnabled(false);client.setScreen(null);set(builder,"Build Mode","Automatic");set(builder,"Mine Out Schematic",false);set(builder,"Stop On Staff Nearby",false);set(builder,"Auto Buy When Missing",false);set(builder,"Support Dirt Reserve",0);set(builder,"Temporary Supports",false);set(builder,"Rotation","0");set(builder,"Mirror","None");button(builder,"Clear Restock Marks").press();});
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
