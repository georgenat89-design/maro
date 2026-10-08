package dev.maro.gametest;

import com.google.gson.*;
import dev.maro.builder.BuilderHomes;
import dev.maro.module.impl.player.AutoBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual server blocks, supplies and owned posts captured before stopping fresh-build stalls. */
final class BuilderCheckpointFixture {
    final JsonObject data;
    final int progress;
    BuilderCheckpointFixture(){
        var stage=System.getProperty("maro.gametest.builderStashCheckpointStage","423");
        if(!List.of("423","549","556","646","668","695","698","702","704").contains(stage))throw new AssertionError("Unsupported captured stall: "+stage);
        progress=Integer.parseInt(stage);
        try(var stream=getClass().getResourceAsStream("/fixtures/stash-fresh-"+stage+".json")){
            if(stream==null)throw new AssertionError("Missing real server checkpoint");
            data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(data.has("homeBusy")&&(data.get("homeBusy").getAsBoolean()||data.get("returnTrip").getAsBoolean()||!data.get("serverHome2Empty").getAsBoolean()))throw new AssertionError("Checkpoint interrupted a transient home transaction");
            if(data.has("compatibleBlocks")&&data.get("compatibleBlocks").getAsInt()!=progress)throw new AssertionError("Checkpoint progress differs from captured compatible server states");
        }catch(IOException error){throw new AssertionError(error);}
    }
    private static BlockPos pos(JsonArray row){return new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt());}
    private static ItemStack stack(JsonArray row){
        var stack=new ItemStack(Registries.ITEM.get(Identifier.of(row.get(1).getAsString())),row.get(2).getAsInt());
        if(row.get(3).getAsInt()>0)stack.setDamage(row.get(3).getAsInt());
        return stack;
    }
    void restoreServer(TestSingleplayerContext world){
        world.getServer().runOnServer(server->{
            var level=server.getOverworld();
            // Suppress neighbour updates while restoring the captured world so
            // stairs, redstone and fluid properties retain their real state.
            for(var element:data.getAsJsonArray("blocks")){
                var row=element.getAsJsonArray();
                try{level.setBlockState(pos(row),BlockArgumentParser.block(Registries.BLOCK,row.get(3).getAsString(),false).blockState(),Block.NOTIFY_LISTENERS);}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}
            }
            for(var element:data.getAsJsonArray("chests")){
                var row=element.getAsJsonArray();var inventory=(net.minecraft.inventory.Inventory)level.getBlockEntity(pos(row));
                inventory.clear();for(var entry:row.get(3).getAsJsonArray()){var slot=entry.getAsJsonArray();inventory.setStack(slot.get(0).getAsInt(),stack(slot));}inventory.markDirty();
            }
            for(var player:server.getPlayerManager().getPlayerList()){
                var inventory=player.getInventory();inventory.clear();
                for(var element:data.getAsJsonArray("inventory")){var row=element.getAsJsonArray();inventory.setStack(row.get(0).getAsInt(),stack(row));}inventory.markDirty();
            }
        });
        BuilderHomeChecks.restoreStorage(world,data.getAsJsonArray("serverHome1"));
        var p=data.getAsJsonArray("player");
        world.getServer().runCommand("tp @a "+p.get(0).getAsDouble()+" "+p.get(1).getAsDouble()+" "+p.get(2).getAsDouble()+" "+p.get(3).getAsFloat()+" "+p.get(4).getAsFloat());
    }
    @SuppressWarnings("unchecked") void restoreBuilder(AutoBuilder builder){
        ((BuilderHomes)AutoBuilderChecks.field(builder,"homes")).loadData(data.getAsJsonArray("homes"));
        var supports=(Set<BlockPos>)AutoBuilderChecks.field(builder,"supports");
        for(var element:data.getAsJsonArray("supports"))supports.add(pos(element.getAsJsonArray()));
        var escape=(Set<BlockPos>)AutoBuilderChecks.field(builder,"escapeSupports");
        for(var element:data.getAsJsonArray("escapeSupports"))escape.add(pos(element.getAsJsonArray()));
        var work=(Map<BlockPos,Integer>)AutoBuilderChecks.field(builder,"escapeSupportWork");
        for(var element:data.getAsJsonArray("escapeSupportWork")){var row=element.getAsJsonArray();work.put(pos(row.get(0).getAsJsonArray()),row.get(1).getAsInt());}
        if(data.has("accessOpenings")){
            var openings=(Map<BlockPos,Integer>)AutoBuilderChecks.field(builder,"floorAccessWork");
            var depths=(Map<BlockPos,Integer>)AutoBuilderChecks.field(builder,"openingRepairDepth");
            for(var element:data.getAsJsonArray("accessOpenings")){var row=element.getAsJsonArray();var opening=pos(row.get(0).getAsJsonArray());openings.put(opening,row.get(1).getAsInt());depths.put(opening,row.get(2).getAsInt());}
            try{var restoration=builder.getClass().getDeclaredField("openingRestoration");restoration.setAccessible(true);restoration.setBoolean(builder,data.get("openingRestoration").getAsBoolean());}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
        }
    }
}
