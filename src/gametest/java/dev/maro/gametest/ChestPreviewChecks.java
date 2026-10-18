package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ChestPreview;
import dev.maro.setting.BooleanSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

final class ChestPreviewChecks {
    private static volatile boolean active;
    private static final AtomicInteger uses=new AtomicInteger();
    static{UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(active&&!world.isClient())uses.incrementAndGet();return ActionResult.PASS;});}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static String xyz(BlockPos p){return p.getX()+" "+p.getY()+" "+p.getZ();}
    private static void until(ClientGameTestContext context,BooleanSupplier condition,String why){for(int i=0;i<160;i++){if(context.computeOnClient(c->condition.getAsBoolean()))return;context.waitTicks(1);}throw new AssertionError(why);}
    private static void look(net.minecraft.client.MinecraftClient c,BlockPos pos){
        var d=pos.toCenterPos().subtract(c.player.getEyePos());c.player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));c.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        c.crosshairTarget=new BlockHitResult(pos.toCenterPos(),Direction.NORTH,pos,false);
    }
    private static void open(ClientGameTestContext context,BlockPos pos){context.runOnClient(c->c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(pos.toCenterPos(),Direction.NORTH,pos,false)));}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var preview=ModuleManager.get(ChestPreview.class);var local=(BooleanSetting)preview.getSettings().stream().filter(s->s.getName().equals("Live Singleplayer")).findFirst().orElseThrow();
        var perspective=context.computeOnClient(c->c.options.getPerspective());
        BlockPos p=context.computeOnClient(c->c.player.getBlockPos().add(0,0,3)),barrel=p.add(2,0,0),loot=p.add(-2,0,0);
        active=true;uses.set(0);
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("setblock "+xyz(p)+" chest");
            world.getServer().runCommand("item replace block "+xyz(p)+" container.0 with minecraft:diamond 7");
            world.getServer().runCommand("setblock "+xyz(barrel)+" barrel");world.getServer().runCommand("item replace block "+xyz(barrel)+" container.2 with minecraft:gold_ingot 12");
            world.getServer().runCommand("setblock "+xyz(loot)+" chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
            context.runOnClient(c->{c.setScreen(null);c.options.setPerspective(Perspective.FIRST_PERSON);local.set(false);preview.setEnabled(true);look(c,p);});context.waitTicks(5);
            require(context.computeOnClient(c->preview.preview(p)==null),"Unknown chest was reported as known/empty");require(uses.get()==0,"Preview opened a chest automatically");context.takeScreenshot("maro-chest-preview-unknown");
            open(context,p);until(context,()->preview.preview(p)!=null,"Physical chest inventory was not cached");
            var snapshot=context.computeOnClient(c->preview.preview(p));require(snapshot.items().size()==27&&snapshot.items().getFirst().isOf(Items.DIAMOND)&&snapshot.items().getFirst().getCount()==7,"Received chest items were wrong");
            world.getServer().runCommand("item replace block "+xyz(p)+" container.0 with minecraft:diamond 3");
            until(context,()->preview.preview(p).items().getFirst().getCount()==3,"Open-container slot update did not refresh snapshot");
            require(snapshot.items().getFirst().getCount()==7,"Saved inventory aliases mutable menu stacks");
            context.runOnClient(c->{c.player.closeHandledScreen();look(c,p);});context.waitTicks(5);context.takeScreenshot("maro-chest-preview-saved");
            int manualUses=uses.get();context.waitTicks(10);require(uses.get()==manualUses,"Cached preview sent another open request");
            // Live local-world data is read without a menu, on the integrated-server thread.
            context.runOnClient(c->{local.set(true);look(c,barrel);preview.onTick();});
            until(context,()->preview.preview(barrel)!=null&&preview.preview(barrel).local(),"Live singleplayer preview unavailable before opening");
            require(context.computeOnClient(c->preview.preview(barrel).items().get(2).isOf(Items.GOLD_INGOT)&&preview.preview(barrel).items().get(2).getCount()==12),"Local-world preview contents incorrect");
            require(uses.get()==manualUses,"Local preview opened a menu");context.takeScreenshot("maro-chest-preview-local");
            context.runOnClient(c->{look(c,loot);preview.onTick();});context.waitTicks(8);
            require(context.computeOnClient(c->preview.preview(loot)==null),"Preview generated unopened chest loot");
            var lootPreserved=new AtomicReference<Boolean>();
            context.runOnClient(c->c.getServer().execute(()->{var b=c.getServer().getOverworld().getBlockEntity(loot);lootPreserved.set(b instanceof LootableContainerBlockEntity l&&l.getLootTable()!=null);}));
            until(context,()->lootPreserved.get()!=null,"Server loot observation timed out");require(Boolean.TRUE.equals(lootPreserved.get()),"Preview consumed the loot table");
            // Replacing a chest at the same coordinates must not reuse another chest's contents.
            context.runOnClient(c->{local.set(false);});world.getServer().runCommand("setblock "+xyz(p)+" air");context.waitTicks(2);world.getServer().runCommand("setblock "+xyz(p)+" chest");context.waitTicks(3);
            require(context.computeOnClient(c->preview.preview(p)==null),"Replaced chest retained old items");
            world.getServer().runCommand("setblock "+xyz(p)+" chest[facing=north,type=left]");world.getServer().runCommand("setblock "+xyz(p.east())+" chest[facing=north,type=right]");context.waitTicks(3);
            open(context,p);until(context,()->preview.preview(p)!=null&&preview.preview(p).items().size()==54,"Double chest did not cache 54 slots");
            context.runOnClient(c->{var adjacent=p.offset(ChestBlock.getFacing(c.world.getBlockState(p)));require(preview.preview(adjacent)==preview.preview(p),"Double chest halves do not share contents");c.player.closeHandledScreen();preview.setEnabled(false);require(preview.savedCount()==0,"Disabling did not clear chest cache");});context.waitTicks(6);
            require(context.computeOnClient(c->preview.savedCount()==0),"An old async read repopulated a disabled cache");
            System.out.println("[chest-preview] PASS: unknown vs empty, real physical open/receive, slot updates and copied stacks, saved hover without further opens, live singleplayer reads without menu, untouched loot table, replaced-chest invalidation, double-chest aliases, bounded session cache cleared on disable");
        }finally{active=false;context.runOnClient(c->{if(c.player!=null&&c.player.currentScreenHandler!=c.player.playerScreenHandler)c.player.closeHandledScreen();preview.setEnabled(false);local.reset();c.setScreen(null);c.options.setPerspective(perspective);});}
    }
}
