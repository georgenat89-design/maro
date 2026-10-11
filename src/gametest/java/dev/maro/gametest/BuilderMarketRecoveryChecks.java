package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maro.builder.Schematic;
import dev.maro.module.impl.player.AutoBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Live AH failures must retain the rest of the queue and confirmed purchases. */
final class BuilderMarketRecoveryChecks {
    private record Payment(Item item,int count,double price) { }
    private record CommandTime(int tick,long nanos) { }
    private record Delayed(int tick,Runnable action) { }
    private static final List<Payment> payments=new CopyOnWriteArrayList<>();
    private static final List<CommandTime> commands=new CopyOnWriteArrayList<>();
    private static final Map<Item,Integer> searches=new ConcurrentHashMap<>();
    private static final List<Delayed> delayed=new ArrayList<>();
    private static int scenario;
    static { ServerTickEvents.END_SERVER_TICK.register(server->delayed.removeIf(task->{
        if(server.getTicks()<task.tick)return false;task.action.run();return true;
    })); }

    static void run(ClientGameTestContext context,TestSingleplayerContext world,AutoBuilder builder){
        var base=context.computeOnClient(c->c.player.getBlockPos());var chest=base.east(2);
        install(world);
        world.getServer().runCommand("setblock "+coords(chest)+" chest[facing=north,type=left]");
        world.getServer().runCommand("setblock "+coords(chest.east())+" chest[facing=north,type=right]");
        try{
            for(int selected=1;selected<=3;selected++){
                int current=selected;
                world.getServer().runOnServer(s->{scenario=current;payments.clear();commands.clear();searches.clear();delayed.clear();});
                world.getServer().runCommand("clear @a");context.waitTicks(6);
                context.runOnClient(c->{
                    builder.pause("Market recovery fixture");builder.clearContainers();
                    setting(builder,"Prepare Whole Build",false);setting(builder,"Auto Buy Tools",false);setting(builder,"Auto Eat",false);
                    setting(builder,"Temporary Supports",false);setting(builder,"Support Dirt Reserve",0);setting(builder,"Max Price Per Item",current==2?25:100_000_000);
                    builder.auctionBudget(1_000_000);
                    var cells=new net.minecraft.block.BlockState[current==1?68:current==2?2:1];Arrays.fill(cells,Blocks.NOTE_BLOCK.getDefaultState());
                    if(current==1){cells[66]=Blocks.OBSERVER.getDefaultState();cells[67]=Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState();}
                    if(current==2)cells[1]=Blocks.OBSERVER.getDefaultState();
                    builder.install(new Schematic("market-recovery.nbt","test",current==1?17:cells.length,1,current==1?4:1,BlockPos.ORIGIN,cells));
                    builder.setOrigin(base.add(-5,0,3));builder.preview();
                    c.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(chest),Direction.NORTH,chest,false);builder.markContainer();
                });
                context.waitTicks(5);context.runOnClient(c->builder.buyMaterials());
                for(int i=0;i<1800&&context.computeOnClient(c->builder.buying()||builder.depositing());i++)context.waitTick();
                require(!context.computeOnClient(c->builder.buying()||builder.depositing()),"Recovery queue hung: "+context.computeOnClient(c->builder.status()));
                if(current==1){
                    require(payments.stream().map(p->p.item).toList().equals(List.of(Items.NOTE_BLOCK,Items.NOTE_BLOCK,Items.OBSERVER,Items.POLISHED_BLACKSTONE_BRICK_STAIRS,Items.NOTE_BLOCK)),"Failed to finish other materials before retrying Note Blocks: "+payments);
                    require(payments.stream().map(p->p.count).toList().equals(List.of(1,1,1,1,64)),"Wrong recovery quantities: "+payments);
                    require(chestCount(world,chest,Items.NOTE_BLOCK)==2&&chestCount(world,chest,Items.COBBLESTONE)==35*64,"Earlier purchases/full inventory were not deposited");
                    context.runOnClient(c->require(builder.inventoryCount(Items.NOTE_BLOCK)==64&&builder.inventoryCount(Items.OBSERVER)==1&&builder.inventoryCount(Items.POLISHED_BLACKSTONE_BRICK_STAIRS)==1&&builder.sessionSpend()==164_800&&builder.status().startsWith("Buying finished"),"Queue/spend lost through deposit: "+builder.status()));
                    require(searches.get(Items.OBSERVER)>=4,"Cooldown and dropped-open response were not exercised");
                }else if(current==2){
                    require(payments.size()==1&&payments.getFirst().item==Items.OBSERVER&&searches.get(Items.NOTE_BLOCK)==6,"Over-price Note Blocks stopped the remaining queue or were purchased: "+payments);
                    context.runOnClient(c->require(builder.sessionSpend()==10&&builder.inventoryCount(Items.OBSERVER)==1&&builder.inventoryCount(Items.NOTE_BLOCK)==0&&builder.status().contains("Buying incomplete")&&builder.status().contains("1 Note Block"),"Unresolved quantity/cap report was lost: "+builder.status()));
                }else{
                    require(payments.size()==1&&searches.get(Items.NOTE_BLOCK)==1,"Late menu contents caused an early reopen or duplicate purchase");
                    context.runOnClient(c->require(builder.inventoryCount(Items.NOTE_BLOCK)==1&&builder.sessionSpend()==10,"Late auction inventory was not purchased"));
                }
                for(int i=1;i<commands.size();i++)require(commands.get(i).tick-commands.get(i-1).tick>=20&&commands.get(i).nanos-commands.get(i-1).nanos>=1_000_000_000L,"AH commands skipped cooldown spacing");
                System.out.println("[market-recovery-proof] scenario="+current+" paid="+payments+" searches="+searches+" status="+context.computeOnClient(c->builder.status()));
                context.runOnClient(c->{builder.pause("Recovery case done");builder.clearContainers();});
                world.getServer().runCommand("data remove block "+coords(chest)+" Items");world.getServer().runCommand("data remove block "+coords(chest.east())+" Items");
            }
        }finally{
            context.runOnClient(c->{builder.pause("Market recovery tests done");builder.clearContainers();});
            world.getServer().runOnServer(s->delayed.clear());
            world.getServer().runCommand("clear @a");world.getServer().runCommand("setblock "+coords(chest)+" air");world.getServer().runCommand("setblock "+coords(chest.east())+" air");world.getServer().runCommand("kill @e[type=item]");
        }
    }
    private static void install(TestSingleplayerContext world){
        world.getServer().runOnServer(server->{
            server.getCommandManager().getDispatcher().register(CommandManager.literal("ah").then(CommandManager.argument("query",StringArgumentType.greedyString()).executes(command->{
                var player=command.getSource().getPlayer();String query=command.getArgument("query",String.class);
                Item item=switch(query){case "note block"->Items.NOTE_BLOCK;case "observer"->Items.OBSERVER;case "polished blackstone brick stairs"->Items.POLISHED_BLACKSTONE_BRICK_STAIRS;default->throw new AssertionError("Unexpected recovery search: "+query);};
                commands.add(new CommandTime(server.getTicks(),System.nanoTime()));int n=searches.merge(item,1,Integer::sum);
                if(scenario==1&&item==Items.OBSERVER&&n==1){player.sendMessage(Text.literal("You need to wait another 0.25 seconds to execute a command"),false);return 1;}
                if(scenario==1&&item==Items.OBSERVER&&n==2)return 1;
                open(player,item,n);return 1;
            })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);
        });
    }
    private static void open(ServerPlayerEntity player,Item item,int search){
        var inventory=new SimpleInventory(27);boolean empty=scenario==1&&item==Items.NOTE_BLOCK&&search>=3&&search<=5;
        int count=scenario==1&&item==Items.NOTE_BLOCK&&search>=6?64:1;
        double price=scenario==1?(item==Items.NOTE_BLOCK?(search==1?29_700:search==2?29_600:98_400):item==Items.OBSERVER?2_100:5_000):scenario==2&&item==Items.NOTE_BLOCK?50:10;
        if(empty){var none=new ItemStack(Items.BARRIER);none.set(DataComponentTypes.CUSTOM_NAME,Text.literal("No items found"));inventory.setStack(13,none);}
        else if(scenario!=3)listing(inventory,item,count,price);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,sync,inv,inventory,3){
            private boolean clicked;
            @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){
                delayed.add(new Delayed(player.getEntityWorld().getServer().getTicks()+1,()->player.currentScreenHandler.syncState()));
                if(slot!=0||clicked||inventory.getStack(0).isEmpty())return;clicked=true;
                payments.add(new Payment(item,count,price));player.closeHandledScreen();
                player.getInventory().insertStack(new ItemStack(item,count));
                if(scenario==1&&payments.size()==2)for(int i=0;i<36;i++)if(player.getInventory().getStack(i).isEmpty())player.getInventory().setStack(i,new ItemStack(Items.COBBLESTONE,64));
            }
        },Text.literal("Auction House — Page 1/1")));
        if(scenario==3){var handler=player.currentScreenHandler;delayed.add(new Delayed(player.getEntityWorld().getServer().getTicks()+60,()->{if(player.currentScreenHandler==handler){listing(inventory,item,count,price);handler.sendContentUpdates();}}));}
    }
    private static void listing(SimpleInventory inventory,Item item,int count,double price){var stack=new ItemStack(item,count);stack.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Price: $"+price))));inventory.setStack(0,stack);}
    private static int chestCount(TestSingleplayerContext world,BlockPos chest,Item item){return world.getServer().computeOnServer(s->{int count=0;for(var pos:List.of(chest,chest.east()))if(s.getOverworld().getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity inventory)for(int i=0;i<inventory.size();i++)if(inventory.getStack(i).isOf(item))count+=inventory.getStack(i).getCount();return count;});}
    private static void setting(AutoBuilder builder,String name,boolean value){builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static void setting(AutoBuilder builder,String name,int value){builder.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static String coords(BlockPos p){return p.getX()+" "+p.getY()+" "+p.getZ();}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
