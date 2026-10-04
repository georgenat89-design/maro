package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maro.builder.Schematic;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Test server menus send real open-screen/content packets and receive real inventory clicks. */
final class BuilderAuctionChecks {
    private static final AtomicBoolean changed=new AtomicBoolean();
    private static final AtomicInteger purchases=new AtomicInteger();
    private static final AtomicBoolean noReceipt=new AtomicBoolean();
    private static final AtomicInteger rateScenario=new AtomicInteger(),soldScenario=new AtomicInteger(),soldAttempts=new AtomicInteger(),purchasedPage=new AtomicInteger();
    private static final AtomicInteger lastListingSync=new AtomicInteger(-1),lastConfirmationSync=new AtomicInteger(-1);
    private record Delayed(int tick,Runnable action){}
    private static final List<Delayed> delayed=new ArrayList<>();
    static {ServerTickEvents.END_SERVER_TICK.register(server->delayed.removeIf(task->{if(server.getTicks()<task.tick())return false;task.action().run();return true;}));}
    static void run(ClientGameTestContext context,TestSingleplayerContext singleplayer,AutoBuilder builder){
        changed.set(false);purchases.set(0);noReceipt.set(false);rateScenario.set(0);soldScenario.set(0);soldAttempts.set(0);delayed.clear();
        singleplayer.getServer().computeOnServer(server->{
            server.getCommandManager().getDispatcher().register(CommandManager.literal("ah").then(CommandManager.argument("query",StringArgumentType.greedyString()).executes(command->{
                var player=command.getSource().getPlayer();if(player!=null)open(player,false,command.getArgument("query",String.class).equals("glass")?Items.GLASS:Items.STONE);return 1;
            })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);return true;
        });
        singleplayer.getServer().runCommand("clear @a");context.waitTicks(6);
        context.runOnClient(client->{
            builder.setEnabled(false);builder.install(new Schematic("auction-fixture.nbt","test",6,1,1,BlockPos.ORIGIN,new net.minecraft.block.BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.GLASS.getDefaultState()}));builder.setOrigin(client.player.getBlockPos().add(0,0,2));
            setting(builder,"Max Total Spend",100);setting(builder,"Max Price Per Item",25);setting(builder,"Support Dirt Reserve",0);setting(builder,"Temporary Supports",false);builder.preview();
        });
        context.waitTicks(5);context.runOnClient(client->button(builder,"Buy Materials").press());waitDone(context,builder);
        context.runOnClient(client->{require(builder.inventoryCount(Items.STONE)==4&&builder.inventoryCount(Items.GLASS)==2&&builder.sessionSpend()==100,"Multiple purchase receipts or budget accounting failed: "+builder.status());});
        require(purchases.get()==4,"Buyer did not finish all four purchases, or duplicated a click");
        require(lastListingSync.get()==lastConfirmationSync.get(),"Same-handler confirmation fixture did not run");
        context.takeScreenshot("maro-builder-auction-receipt");

        // Server changes the price on confirmation. The client must not click Yes or spend currency.
        singleplayer.getServer().runCommand("clear @a");changed.set(true);context.waitTicks(6);
        context.runOnClient(client->button(builder,"Buy Materials").press());waitDone(context,builder);
        context.runOnClient(client->{require(builder.inventoryCount(Items.STONE)==0&&builder.sessionSpend()==0&&builder.status().contains("changed"),"Changed confirmation price was accepted: "+builder.status());});
        require(purchases.get()==4,"Buyer clicked confirmation after a price change");
        changed.set(false);noReceipt.set(true);context.runOnClient(client->button(builder,"Buy Materials").press());waitDone(context,builder);
        require(purchases.get()==5,"Buyer retried an unconfirmed purchase");
        context.runOnClient(client->require(builder.status().contains("no retry")&&builder.inventoryCount(Items.STONE)==0&&builder.inventoryCount(Items.GLASS)==0,"Missing receipt did not stop buying: "+builder.status()));
        noReceipt.set(false);
        // The cheapest unit rate may be on a later page or require returning to an earlier one.
        for(int scenario:new int[]{1,2}){
            rateScenario.set(scenario);purchases.set(0);singleplayer.getServer().runCommand("clear @a");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("rates-fixture.nbt","test",2,1,1,BlockPos.ORIGIN,new net.minecraft.block.BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.preview();button(builder,"Buy Materials").press();});
            waitDone(context,builder);
            require(purchases.get()==1&&purchasedPage.get()==(scenario==1?2:1),"Buyer did not select the cheapest auction page");
            context.runOnClient(client->require(builder.sessionSpend()==20&&builder.inventoryCount(Items.STONE)==2,"Buyer did not buy at the best available unit rate: "+builder.status()));
        }
        rateScenario.set(0);
        // Server chat and unavailable-screen notices must skip the stale listing, not cancel shopping.
        for(int scenario:new int[]{1,2}){
            soldScenario.set(scenario);soldAttempts.set(0);purchases.set(0);singleplayer.getServer().runCommand("clear @a");context.waitTicks(5);
            context.runOnClient(client->{builder.install(new Schematic("sold-fixture.nbt","test",1,1,1,BlockPos.ORIGIN,new net.minecraft.block.BlockState[]{Blocks.GLASS.getDefaultState()}));builder.preview();button(builder,"Buy Materials").press();});
            waitDone(context,builder);
            require(soldAttempts.get()==1&&purchases.get()==1,"Buyer cancelled or repeatedly clicked the sold listing");
            context.runOnClient(client->require(builder.sessionSpend()==12&&builder.inventoryCount(Items.GLASS)==1,"Sold listing accounting or continued shopping failed: "+builder.status()));
        }
        soldScenario.set(0);
        inventoryDeposit(context,singleplayer,builder);
    }
    private static void waitDone(ClientGameTestContext context,AutoBuilder builder){for(int i=0;i<600&&context.computeOnClient(client->builder.buying());i++)context.waitTick();require(!context.computeOnClient(client->builder.buying()),"Auction state machine did not finish: "+context.computeOnClient(client->builder.status()));}
    private static void inventoryDeposit(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder){
        var base=context.computeOnClient(client->client.player.getBlockPos());var near=base.add(2,0,0);var far=base.add(7,0,0);
        for(var pos:List.of(near,far)){
            world.getServer().runCommand("setblock "+coords(pos)+" chest[facing=north,type=left]");
            world.getServer().runCommand("setblock "+coords(pos.east())+" chest[facing=north,type=right]");
        }
        purchases.set(0);world.getServer().runOnServer(server->{var player=server.getPlayerManager().getPlayerList().getFirst();for(int i=0;i<36;i++)player.getInventory().setStack(i,new ItemStack(Items.COBBLESTONE,64));});context.waitTicks(8);
        context.runOnClient(client->{builder.install(new Schematic("full-inventory-fixture.nbt","test",2,1,1,BlockPos.ORIGIN,new net.minecraft.block.BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(base.add(0,0,2));builder.preview();button(builder,"Buy Materials").press();});
        for(int i=0;i<1200&&context.computeOnClient(client->builder.buying()||builder.depositing());i++)context.waitTick();
        context.runOnClient(client->require(!builder.buying()&&!builder.depositing()&&builder.inventoryCount(Items.STONE)==2&&builder.inventoryCount(Items.COBBLESTONE)==0&&builder.sessionSpend()==40,"Full inventory did not deposit and resume shopping: "+builder.status()));
        require(purchases.get()==1,"Full inventory caused duplicate purchases");
        require(chestCount(world,near,Items.COBBLESTONE)==36*64&&chestCount(world,far,Items.COBBLESTONE)==0,"Deposit did not choose the nearest double chest or lost items");
        context.runOnClient(client->builder.depositAll());
        for(int i=0;i<600&&context.computeOnClient(client->builder.depositing());i++)context.waitTick();
        require(chestCount(world,near,Items.STONE)==2,"Deposit All did not move the purchased material");
        // No manual chest mark: the build must find its missing stone in the nearest double chest.
        context.runOnClient(client->{button(builder,"Clear Restock Marks").press();builder.restartBuild();});
        for(int i=0;i<800&&context.computeOnClient(client->builder.building());i++)context.waitTick();
        context.runOnClient(client->require(builder.status().equals("Build complete"),"Build did not return to the nearest double chest for missing material: "+builder.status()));
        require(world.getServer().computeOnServer(server->server.getOverworld().getBlockState(base.add(0,0,2)).isOf(Blocks.STONE)&&server.getOverworld().getBlockState(base.add(1,0,2)).isOf(Blocks.STONE)),"Restocked material did not build on the server");
        require(chestCount(world,near,Items.COBBLESTONE)==36*64&&chestCount(world,near,Items.STONE)==0,"Restock changed unrelated chest items");
    }
    private static String coords(BlockPos pos){return pos.getX()+" "+pos.getY()+" "+pos.getZ();}
    private static int chestCount(TestSingleplayerContext world,BlockPos pos,Item item){return world.getServer().computeOnServer(server->{int count=0;for(var half:List.of(pos,pos.east())){var chest=(net.minecraft.block.entity.ChestBlockEntity)server.getOverworld().getBlockEntity(half);for(int i=0;i<chest.size();i++)if(chest.getStack(i).isOf(item))count+=chest.getStack(i).getCount();}return count;});}
    private static void open(ServerPlayerEntity player,boolean confirm,Item item){
        open(player,confirm,item,1);
    }
    private static void open(ServerPlayerEntity player,boolean confirm,Item item,int page){
        SimpleInventory inventory=new SimpleInventory(27);
        contents(inventory,confirm,item,page);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,playerInventory,owner)->new Menu(syncId,playerInventory,inventory,confirm,item,page),Text.literal(confirm?"Donut Trade":"Auction House")));
        if(confirm)lastConfirmationSync.set(player.currentScreenHandler.syncId);else lastListingSync.set(player.currentScreenHandler.syncId);
    }
    private static void contents(SimpleInventory inventory,boolean confirm,Item item,int page){
        int price=rateScenario.get()==0?(item==Items.STONE?40:10):(page==(rateScenario.get()==1?2:1)?20:40);
        inventory.clear();var listing=new ItemStack(item,item==Items.STONE?2:1);listing.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Price: $"+(confirm&&changed.get()?400:price)))));inventory.setStack(confirm?13:0,listing);
        if(!confirm&&rateScenario.get()!=0&&page==1){var next=new ItemStack(Items.ARROW);next.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Next page"));inventory.setStack(26,next);}
        if(!confirm&&soldScenario.get()!=0){var fresh=listing.copy();fresh.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Price: $12"))));inventory.setStack(1,fresh);}
        if(confirm){var yes=new ItemStack(Items.LIME_STAINED_GLASS_PANE);yes.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Yes"));inventory.setStack(11,yes);var no=new ItemStack(Items.RED_STAINED_GLASS_PANE);no.set(DataComponentTypes.CUSTOM_NAME,Text.literal("No"));inventory.setStack(15,no);}
    }
    private static void after(ServerPlayerEntity player,int ticks,Runnable task){
        delayed.add(new Delayed(player.getEntityWorld().getServer().getTicks()+ticks,task));
    }
    private static final class Menu extends GenericContainerScreenHandler {
        private boolean confirm;
        private final Item item;
        private final SimpleInventory inventory;
        private boolean clicked;
        private final int page;
        Menu(int syncId,PlayerInventory player,SimpleInventory inventory,boolean confirm,Item item,int page){super(ScreenHandlerType.GENERIC_9X3,syncId,player,inventory,3);this.confirm=confirm;this.item=item;this.inventory=inventory;this.page=page;}
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){
            var player=(ServerPlayerEntity)entity;
            if(!confirm&&slot==26&&rateScenario.get()!=0&&page==1){open(player,false,item,2);return;}
            if(!confirm&&(slot==0||slot==1&&soldScenario.get()!=0)&&!clicked){
                clicked=true;
                if(slot==0&&soldScenario.get()!=0){
                    soldAttempts.incrementAndGet();
                    if(soldScenario.get()==1)player.sendMessage(Text.literal("This item has already been purchased!"),false);
                    else player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,id,inv,new SimpleInventory(27),3),Text.literal("Item already purchased")));
                    return;
                }
                if(!changed.get()&&!noReceipt.get()&&purchases.get()==0){purchase(player);return;}
                if(!changed.get()&&!noReceipt.get()&&purchases.get()==3){
                    confirm=true;clicked=false;contents(inventory,true,item,page);
                    inventory.getStack(13).remove(DataComponentTypes.LORE);inventory.getStack(11).set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Price: $"+(item==Items.STONE?40:10)))));
                    lastConfirmationSync.set(syncId);sendContentUpdates();return;
                }
                after(player,35,()->open(player,true,item));return;
            }
            if(confirm&&slot==11&&!clicked){clicked=true;purchase(player);return;}
            if(confirm&&slot==15)player.closeHandledScreen();
        }
        private void purchase(ServerPlayerEntity player){purchases.incrementAndGet();purchasedPage.set(page);player.closeHandledScreen();if(!noReceipt.get())after(player,45,()->player.getInventory().insertStack(new ItemStack(item,item==Items.STONE?2:1)));}
    }
    private static void setting(AutoBuilder b,String name,Number value){b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static void setting(AutoBuilder b,String name,boolean value){b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static ButtonSetting button(AutoBuilder b,String name){return (ButtonSetting)b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
