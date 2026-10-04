package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maro.builder.Schematic;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
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
    static void run(ClientGameTestContext context,TestSingleplayerContext singleplayer,AutoBuilder builder){
        changed.set(false);purchases.set(0);
        singleplayer.getServer().computeOnServer(server->{
            server.getCommandManager().getDispatcher().register(CommandManager.literal("ah").then(CommandManager.argument("query",StringArgumentType.greedyString()).executes(command->{
                var player=command.getSource().getPlayer();if(player!=null)open(player,false);return 1;
            })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);return true;
        });
        singleplayer.getServer().runCommand("clear @a");context.waitTicks(6);
        context.runOnClient(client->{
            builder.setEnabled(false);builder.install(new Schematic("auction-fixture.nbt","test",2,1,1,BlockPos.ORIGIN,new net.minecraft.block.BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE.getDefaultState()}));builder.setOrigin(client.player.getBlockPos().add(0,0,2));
            setting(builder,"Max Total Spend",100);setting(builder,"Max Price Per Item",25);setting(builder,"Buy Temp Dirt",0);setting(builder,"Temporary Supports",false);builder.preview();
        });
        context.waitTicks(5);context.runOnClient(client->button(builder,"Buy Materials").press());waitDone(context,builder);
        context.runOnClient(client->{require(builder.inventoryCount(Items.STONE)==2&&builder.sessionSpend()==40,"Purchase receipt or budget accounting failed: "+builder.status());});
        require(purchases.get()==1,"Buyer did not issue exactly one verified confirmation click");
        context.takeScreenshot("maro-builder-auction-receipt");

        // Server changes the price on confirmation. The client must not click Yes or spend currency.
        singleplayer.getServer().runCommand("clear @a");changed.set(true);context.waitTicks(6);
        context.runOnClient(client->button(builder,"Buy Materials").press());waitDone(context,builder);
        context.runOnClient(client->{require(builder.inventoryCount(Items.STONE)==0&&builder.sessionSpend()==0&&builder.status().contains("changed"),"Changed confirmation price was accepted: "+builder.status());});
        require(purchases.get()==1,"Buyer clicked confirmation after a price change");
    }
    private static void waitDone(ClientGameTestContext context,AutoBuilder builder){for(int i=0;i<160&&context.computeOnClient(client->builder.buying());i++)context.waitTick();require(!context.computeOnClient(client->builder.buying()),"Auction state machine did not finish");}
    private static void open(ServerPlayerEntity player,boolean confirm){
        SimpleInventory inventory=new SimpleInventory(27);
        var listing=new ItemStack(Items.STONE,2);listing.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Price: $"+(confirm&&changed.get()?400:40)))));inventory.setStack(confirm?13:0,listing);
        if(confirm){var yes=new ItemStack(Items.LIME_STAINED_GLASS_PANE);yes.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Confirm Purchase"));inventory.setStack(11,yes);var no=new ItemStack(Items.RED_STAINED_GLASS_PANE);no.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Cancel"));inventory.setStack(15,no);}
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,playerInventory,owner)->new Menu(syncId,playerInventory,inventory,confirm),Text.literal(confirm?"Confirm Purchase":"Auction House")));
    }
    private static final class Menu extends GenericContainerScreenHandler {
        private final boolean confirm;
        Menu(int syncId,PlayerInventory player,SimpleInventory inventory,boolean confirm){super(ScreenHandlerType.GENERIC_9X3,syncId,player,inventory,3);this.confirm=confirm;}
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){
            var player=(ServerPlayerEntity)entity;
            if(!confirm&&slot==0){player.getEntityWorld().getServer().execute(()->open(player,true));return;}
            if(confirm&&slot==11){purchases.incrementAndGet();player.getInventory().insertStack(new ItemStack(Items.STONE,2));player.closeHandledScreen();return;}
            if(confirm&&slot==15)player.closeHandledScreen();
        }
    }
    private static void setting(AutoBuilder b,String name,Number value){b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static void setting(AutoBuilder b,String name,boolean value){b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow().fromJson(new JsonPrimitive(value));}
    private static ButtonSetting button(AutoBuilder b,String name){return (ButtonSetting)b.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
