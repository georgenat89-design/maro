package dev.maro.gametest;

import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.ShulkerDropper;
import dev.maro.module.impl.player.SpawnerDropper;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Real vanilla menu packets and server inventory updates, including delayed purchases. */
final class DropperChecks {
    private record Delayed(int tick,Runnable task){}
    private static final List<Delayed> pending=new ArrayList<>();
    private static boolean active,rejectBuy,confirmWindow=true;
    private static int shopOpens,buys,dropClicks,confirmClicks,lootPage,lootThrown;
    private static List<List<ItemStack>> lootPages=List.of();
    static{
        ServerTickEvents.END_SERVER_TICK.register(server->pending.removeIf(d->{if(server.getTicks()<d.tick())return false;d.task().run();return true;}));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(active&&player instanceof ServerPlayerEntity server&&world.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.block.entity.MobSpawnerBlockEntity){openSpawner(server);return ActionResult.SUCCESS;}
            return ActionResult.PASS;
        });
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void number(Module module,String name,double n){((NumberSetting)setting(module,name)).set(n);}
    private static void bool(Module module,String name,boolean b){((BooleanSetting)setting(module,name)).set(b);}
    private static void mode(Module module,String name,String value){((ModeSetting)setting(module,name)).set(value);}
    private static void until(ClientGameTestContext context,BooleanSupplier condition,String message){for(int i=0;i<400;i++){if(context.computeOnClient(c->condition.getAsBoolean()))return;context.waitTicks(1);}throw new AssertionError(message);}
    private static ItemStack named(Item item,String label,String... lines){var s=new ItemStack(item);s.set(DataComponentTypes.CUSTOM_NAME,Text.literal(label));s.set(DataComponentTypes.LORE,new LoreComponent(Arrays.stream(lines).map(Text::literal).map(t->(Text)t).toList()));return s;}
    private static void later(ServerPlayerEntity player,int ticks,Runnable action){pending.add(new Delayed(player.getEntityWorld().getServer().getTicks()+ticks,action));}
    private static void openShop(ServerPlayerEntity player){
        shopOpens++;var inventory=new SimpleInventory(27);inventory.setStack(13,named(Items.SHULKER_BOX,"Shulker Box","Price: $500","Click to buy"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,id,inv,inventory,3){
            @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){
                if(slot==13&&action==SlotActionType.PICKUP){buys++;if(!rejectBuy)later(player,10,()->{player.getInventory().insertStack(new ItemStack(Items.SHULKER_BOX));player.currentScreenHandler.syncState();});}
                else if(slot>=27)super.onSlotClick(slot,button,action,entity);else syncState();
            }
        },Text.literal("Server Shop")));
    }
    private static void fillLoot(SimpleInventory inventory){
        for(int i=0;i<45;i++)inventory.setStack(i,ItemStack.EMPTY);
        if(lootPage<lootPages.size()){var page=lootPages.get(lootPage);for(int i=0;i<page.size();i++)inventory.setStack(i,page.get(i).copy());}
    }
    private static void openSpawner(ServerPlayerEntity player){
        var inventory=new SimpleInventory(54);fillLoot(inventory);
        // Controls moved away from Glazed's hard-coded 50/53; confirm comes first.
        inventory.setStack(47,named(Items.LIME_DYE,"ᴄᴏɴꜰɪʀᴍ ᴅʀᴏᴘ"));inventory.setStack(51,named(Items.LEVER,"ᴅʀᴏᴘ ʟᴏᴏᴛ"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6,id,inv,inventory,6){
            @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){
                if(slot==51&&action==SlotActionType.PICKUP){dropClicks++;if(confirmWindow)openConfirm(player);}
                else if(slot==47&&action==SlotActionType.PICKUP){confirmClicks++;dropPage(player);}
                else if(slot>=0&&slot<45&&action==SlotActionType.THROW){lootThrown+=inventory.getStack(slot).getCount();super.onSlotClick(slot,button,action,entity);}
                else syncState();
            }
        },Text.literal("Spawner Storage")));
    }
    private static void dropPage(ServerPlayerEntity player){later(player,8,()->{lootPage++;openSpawner(player);});}
    private static void openConfirm(ServerPlayerEntity player){
        var inventory=new SimpleInventory(54);inventory.setStack(47,named(Items.LIME_DYE,"Confirm Drop"));inventory.setStack(51,named(Items.RED_DYE,"Cancel"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6,id,inv,inventory,6){
            @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){if(slot==47&&action==SlotActionType.PICKUP){confirmClicks++;dropPage(player);}else syncState();}
        },Text.literal("Confirm Drop")));
    }
    private static void unrelated(ServerPlayerEntity player){
        var inventory=new SimpleInventory(54);inventory.setStack(0,new ItemStack(Items.BONE,64));inventory.setStack(50,named(Items.CHEST,"Deposit All"));inventory.setStack(53,named(Items.ARROW,"Next Page"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,owner)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6,id,inv,inventory,6){
            @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity entity){throw new AssertionError("Dropper clicked an unrelated menu at "+slot);}
        },Text.literal("Personal Storage")));
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var shulkers=ModuleManager.get(ShulkerDropper.class);var spawners=ModuleManager.get(SpawnerDropper.class);
        require(shulkers!=null&&spawners!=null,"Dropper modules missing");
        world.getServer().runCommand("fill -6 99 -6 6 99 6 minecraft:stone");world.getServer().runCommand("tp @a 0.5 100 0.5 0 0");context.waitTicks(6);
        world.getServer().runOnServer(server->{active=true;server.getCommandManager().getDispatcher().register(CommandManager.literal("shop").executes(cmd->{openShop(cmd.getSource().getPlayerOrThrow());return 1;}));server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);});
        try{
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.getInventory().clear();p.getInventory().setStack(9,named(Items.SHULKER_BOX,"Keep This Box"));var filled=new ItemStack(Items.PINK_SHULKER_BOX);filled.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(new ItemStack(Items.DIAMOND,7))));p.getInventory().setStack(10,filled);p.getInventory().setStack(11,new ItemStack(Items.DIAMOND,33));p.currentScreenHandler.syncState();buys=shopOpens=0;rejectBuy=false;});context.waitTicks(4);
            context.runOnClient(c->{shulkers.getSettings().forEach(Setting::reset);number(shulkers,"Purchase Limit",2);shulkers.setEnabled(true);});
            until(context,()->shulkers.boughtCount()>=1,"First delayed purchase did not arrive: "+shulkers.status());context.takeScreenshot("maro-shulker-dropper-running");
            until(context,()->!shulkers.isEnabled(),"Shulker run did not stop: "+shulkers.status());
            require(world.getServer().computeOnServer(s->buys)==2&&shulkers.boughtCount()==2&&shulkers.droppedCount()==2,"Duplicate purchase, failed drop or limit failure: "+shulkers.status());
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();require(p.getInventory().getStack(9).getName().getString().equals("Keep This Box"),"Existing named shulker was dropped");require(p.getInventory().getStack(10).get(DataComponentTypes.CONTAINER)!=null&&p.getInventory().getStack(11).getCount()==33,"Filled box or diamonds were changed");p.closeHandledScreen();});context.waitTicks(3);
            world.getServer().runOnServer(server->{rejectBuy=true;buys=0;});
            context.runOnClient(c->{number(shulkers,"Timeout",20);shulkers.setEnabled(true);});until(context,()->!shulkers.isEnabled(),"Rejected purchase did not time out");
            require(world.getServer().computeOnServer(s->buys)==1&&shulkers.status().contains("did not add"),"Rejected purchase spammed the shop: "+shulkers.status());
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.closeHandledScreen();rejectBuy=false;buys=0;});context.waitTicks(3);
            context.runOnClient(c->{number(shulkers,"Max Price",100);shulkers.setEnabled(true);});until(context,()->!shulkers.isEnabled(),"Price cap did not stop");
            require(world.getServer().computeOnServer(s->buys)==0&&shulkers.status().contains("Max Price"),"Price cap bought an overpriced box");

            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.closeHandledScreen();p.getInventory().clear();p.getInventory().setStack(9,new ItemStack(Items.RED_SHULKER_BOX));p.getInventory().setStack(10,new ItemStack(Items.BLUE_SHULKER_BOX));var full=new ItemStack(Items.PINK_SHULKER_BOX);full.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(new ItemStack(Items.DIAMOND,7))));p.getInventory().setStack(11,full);p.getInventory().setStack(12,new ItemStack(Items.DIAMOND,33));p.currentScreenHandler.syncState();});context.waitTicks(5);
            context.runOnClient(c->{mode(shulkers,"Mode","Drop Inventory");number(shulkers,"Keep",1);shulkers.setEnabled(true);});until(context,()->!shulkers.isEnabled(),"Inventory drop did not finish");context.waitTicks(5);
            require(shulkers.droppedCount()==1,"Keep count included a protected filled box: "+shulkers.status());
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();require(!p.getInventory().getStack(11).isEmpty()&&p.getInventory().getStack(12).getCount()==33,"Drop Inventory removed a filled box or non-shulker item");});

            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.closeHandledScreen();unrelated(p);shopOpens=0;dropClicks=0;confirmClicks=0;});context.waitTicks(3);
            context.runOnClient(c->{spawners.getSettings().forEach(Setting::reset);spawners.setEnabled(true);});context.waitTicks(8);
            require(world.getServer().computeOnServer(s->dropClicks+confirmClicks)==0,"Unrelated storage was clicked");
            context.runOnClient(c->spawners.setEnabled(false));
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.closeHandledScreen();lootPage=dropClicks=confirmClicks=0;confirmWindow=true;lootPages=List.of(List.of(new ItemStack(Items.BONE,64)),List.of(new ItemStack(Items.BONE,64)),List.of(new ItemStack(Items.BONE,64)));openSpawner(p);});context.waitTicks(3);
            context.runOnClient(c->{bool(spawners,"Auto Open",false);spawners.setEnabled(true);});
            until(context,()->spawners.droppedPages()>=1,"Spawner did not pass through confirmation: "+spawners.status());context.takeScreenshot("maro-spawner-dropper-running");
            until(context,()->!spawners.isEnabled(),"Spawner did not finish identical loot pages: "+spawners.status());
            require(world.getServer().computeOnServer(s->lootPage==3&&dropClicks==3&&confirmClicks==3),"Spawner repeated/lost a page or clicked the wrong shifted control: "+spawners.status());

            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();lootPage=dropClicks=confirmClicks=0;lootPages=List.of(List.of(new ItemStack(Items.BONE,64),new ItemStack(Items.ARROW,64)));openSpawner(p);});context.waitTicks(3);
            context.runOnClient(c->{mode(spawners,"Items","Bones Only");spawners.setEnabled(true);});until(context,()->!spawners.isEnabled(),"Mixed bone page did not stop");
            context.waitTicks(5); // Deliver the old menu's close before the fixture opens another one.
            require(world.getServer().computeOnServer(s->dropClicks==0&&confirmClicks==0)&&spawners.status().contains("Mixed"),"Bones Only dropped an arrow page");
            world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();lootThrown=0;openSpawner(p);});context.waitTicks(3);
            context.runOnClient(c->{mode(spawners,"Drop How","Throw Stacks");spawners.setEnabled(true);});until(context,()->!spawners.isEnabled(),"Bone-only stack mode did not finish");
            context.waitTicks(5);
            require(world.getServer().computeOnServer(s->lootThrown)==64,"Bone-only stack throw count was "+world.getServer().computeOnServer(s->lootThrown)+": "+spawners.status());

            world.getServer().runCommand("setblock 0 100 2 minecraft:spawner");context.waitTicks(5);
            world.getServer().runOnServer(server->{lootPage=0;lootPages=List.of();var p=server.getPlayerManager().getPlayerList().getFirst();p.closeHandledScreen();});context.waitTicks(3);
            context.runOnClient(c->{mode(spawners,"Items","All Loot");mode(spawners,"Drop How","Menu Buttons");bool(spawners,"Auto Open",true);spawners.setEnabled(true);});
            until(context,()->spawners.target()!=null,"Auto-open did not find an in-reach visible spawner: "+spawners.status());
            until(context,()->!spawners.isEnabled(),"Empty auto-open spawner did not finish");
            context.runOnClient(c->require(!shulkers.persistEnabled()&&!spawners.persistEnabled(),"Droppers resume destructive actions from a saved enable state"));
            System.out.println("[droppers] PASS: delayed buy/drop with no duplicate purchases, old/filled boxes and diamonds kept, exact Keep across colored/filled boxes in inventory mode, purchase and price limits, rejected purchase timeout, unrelated storage untouched, shifted small-caps drop/confirm controls, new confirmation handlers and identical pages, mixed bone-page preservation, bone-only stack throws, normal-reach auto-open and empty completion");
        }finally{
            context.runOnClient(c->{shulkers.setEnabled(false);spawners.setEnabled(false);shulkers.getSettings().forEach(Setting::reset);spawners.getSettings().forEach(Setting::reset);if(c.player!=null)c.player.closeHandledScreen();});
            world.getServer().runOnServer(server->{active=false;pending.clear();});
        }
    }
}
