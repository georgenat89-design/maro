package dev.maro.module.impl.player;

import dev.maro.donut.OrderMarket;
import dev.maro.gui.notification.Notifications;
import dev.maro.inventory.ChestSessions;
import dev.maro.inventory.DropperMenus;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.*;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import java.util.List;

/** Native spawner-loot workflow, informed by the supplied Glazed SpawnerDropper behavior. */
public final class SpawnerDropper extends Module {
    private enum Stage { FIND,DROP,CONFIRM,WAIT }
    private final ModeSetting items=add(new ModeSetting("Items","Drop all loot, or keep pages that contain anything besides bones","All Loot","All Loot","Bones Only"));
    private final ModeSetting how=add(new ModeSetting("Drop How","Use the spawner's drop/confirm buttons, or throw individual matching stacks","Menu Buttons","Menu Buttons","Throw Stacks"));
    private final BooleanSetting confirm=add(new BooleanSetting("Confirm Drop","Click the second server control after Drop",true).visible(()->how.is("Menu Buttons")));
    private final NumberSetting delay=add(new NumberSetting("Action Delay","Ticks between menu actions",5,1,20,1).suffix(" ticks"));
    private final NumberSetting sync=add(new NumberSetting("Sync Wait","Minimum wait for the loot page to update",5,3,40,1).suffix(" ticks"));
    private final NumberSetting timeout=add(new NumberSetting("Timeout","Stop if the server does not open or update the menu",80,20,400,5).suffix(" ticks"));
    private final NumberSetting maxPages=add(new NumberSetting("Page Limit","Maximum drop cycles before stopping",64,1,1024,1));
    private final BooleanSetting autoOpen=add(new BooleanSetting("Auto Open","Open the closest visible spawner within normal block reach",true));
    private final BooleanSetting repeat=add(new BooleanSetting("Repeat","Reopen the same spawner after the interval when it is empty",false));
    private final NumberSetting interval=add(new NumberSetting("Reopen Interval","Minutes between completed runs",10,.1,600,.1).suffix(" min").visible(repeat::get));
    private final BooleanSetting autoClose=add(new BooleanSetting("Auto Close","Close this spawner menu when the run finishes",true));
    private final BooleanSetting statusHud=add(new BooleanSetting("Status HUD","Show progress above the hotbar",true));
    private final TextSetting titles=add(new TextSetting("Menu Titles","Spawner storage titles, separated by semicolons","spawner;virtual storage",120,"spawner;virtual storage"));
    private final TextSetting dropWords=add(new TextSetting("Drop Button","Words used by the server's drop control","drop",100,"drop"));
    private final NumberSetting dropSlot=add(new NumberSetting("Drop Slot","Fallback control slot in a recognized spawner menu, starting at 0",50,0,53,1));
    private final TextSetting confirmWords=add(new TextSetting("Confirm Button","Words used by the server's second control","confirm;next",100,"confirm;next"));
    private final NumberSetting confirmSlot=add(new NumberSetting("Confirm Slot","Fallback second control slot in a recognized menu",53,0,53,1));
    private Object world,connection;
    private GenericContainerScreenHandler owned;
    private BlockPos spawner;
    private Stage stage;
    private int tick,next,deadline,scanAt,pages,beforeCount,beforeHash,failures,reopenAt;
    private boolean opening;
    private String status="Off";
    public SpawnerDropper(){super("Spawner Dropper","Empty spawner loot with menu validation, bone filtering and repeat controls",Category.PLAYER);}
    @Override public boolean persistEnabled(){return false;}
    @Override public List<SettingSection> getSettingSections(){return List.of(
        SettingSection.of("Dropping",items,how,confirm,delay,sync,timeout,maxPages,statusHud),
        SettingSection.of("Opening",autoOpen,repeat,interval,autoClose),SettingSection.of("Server Menu",titles,dropWords,dropSlot,confirmWords,confirmSlot));}
    @Override protected void onEnable(){
        var other=ModuleManager.get(ShulkerDropper.class);if(other!=null)other.setEnabled(false);
        world=mc.world;connection=mc.getNetworkHandler();owned=null;spawner=null;stage=Stage.FIND;
        tick=next=scanAt=pages=failures=reopenAt=0;deadline=timeout.getInt();opening=false;status="Open a spawner";
        if(!inGame())finish("Join a world first",true,false);
    }
    @Override protected void onDisable(){owned=null;spawner=null;stage=null;opening=false;}
    private boolean recognized(GenericContainerScreenHandler menu){
        if(menu==null||menu.getRows()!=6||ChestSessions.physical(menu))return false;
        if(!DropperMenus.title(titles.get())&&!((stage==Stage.CONFIRM&&owned!=null||stage==Stage.WAIT&&owned==menu)&&DropperMenus.title("confirm;drop")))return false;
        return !DropperMenus.title("shop;auction;purchase;orders;buy");
    }
    private boolean loot(ItemStack stack){
        if(stack.isEmpty()||stack.isOf(Items.SPAWNER))return false;
        String id=Registries.ITEM.getId(stack.getItem()).getPath();if(id.contains("glass_pane"))return false;
        // Never treat a navigation widget as a loot stack.
        return !stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)||!OrderMarket.has(stack.getName().getString(),"next page;previous page;go back;drop all;confirm;close menu");
    }
    private int lootCount(GenericContainerScreenHandler menu){int total=0;for(int i=0;i<45;i++)if(loot(menu.getSlot(i).getStack()))total+=menu.getSlot(i).getStack().getCount();return total;}
    private int pageHash(GenericContainerScreenHandler menu){int hash=1;for(int i=0;i<45;i++){
        ItemStack stack=menu.getSlot(i).getStack();if(loot(stack))hash=31*hash+Registries.ITEM.getRawId(stack.getItem())*67+stack.getCount()+stack.getComponents().hashCode();
    }return hash;}
    private boolean mixed(GenericContainerScreenHandler menu){for(int i=0;i<45;i++){var s=menu.getSlot(i).getStack();if(loot(s)&&!s.isOf(Items.BONE))return true;}return false;}
    private void finish(String message,boolean error,boolean again){
        status=message;
        if(autoClose.get()&&owned!=null&&inGame()&&mc.player.currentScreenHandler==owned&&mc.currentScreen!=null)mc.player.closeHandledScreen();
        owned=null;opening=false;
        if(again&&repeat.get()){stage=Stage.FIND;reopenAt=tick+(int)Math.round(interval.get()*1200);next=reopenAt;}
        else setEnabled(false);
        Notifications.push(getName(),message,error?Notifications.Type.WARNING:Notifications.Type.SUCCESS);
    }
    private BlockHitResult nearest(){
        var eye=mc.player.getEyePos();double reach=mc.player.getBlockInteractionRange();BlockHitResult best=null;double distance=reach*reach;
        int cx=mc.player.getBlockX()>>4,cz=mc.player.getBlockZ()>>4;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++){
            if(!mc.world.getChunkManager().isChunkLoaded(x,z))continue;
            for(var block:mc.world.getChunk(x,z).getBlockEntities().values())if(block instanceof MobSpawnerBlockEntity){
                BlockPos pos=block.getPos();if(spawner!=null&&!spawner.equals(pos))continue;double d=eye.squaredDistanceTo(pos.toCenterPos());if(d>distance)continue;
                var hit=mc.world.raycast(new RaycastContext(eye,pos.toCenterPos(),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
                if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)){distance=d;best=hit;}
            }
        }return best;
    }
    @Override public void onTick(){
        if(!isEnabled()||stage==null)return;
        if(!inGame()||mc.interactionManager==null||mc.world!=world||mc.getNetworkHandler()!=connection){finish("World or connection changed",true,false);return;}
        if(mc.isPaused()||DropperMenus.busy()){status="Waiting for other inventory actions";return;}
        tick++;if(tick<next){if(reopenAt>tick)status="Next run in "+((reopenAt-tick+19)/20)+"s";return;}
        if(!mc.player.currentScreenHandler.getCursorStack().isEmpty()){finish("Cursor holds an item; clear it before restarting",true,false);return;}
        var menu=DropperMenus.menu();
        if(!recognized(menu)){
            if(mc.currentScreen!=null){if(opening&&tick>=deadline)finish("Unexpected spawner menu",true,false);else status="Waiting for a recognized spawner menu";return;}
            if(owned!=null){finish("Spawner menu closed · "+pages+" drop cycles",false,false);return;}
            if(opening){if(tick>=deadline)finish("Spawner did not open a storage menu",true,false);return;}
            if(!autoOpen.get()){status="Open a spawner storage menu";return;}
            if(tick<scanAt)return;scanAt=tick+20;
            var hit=nearest();if(hit==null){status="No visible spawner within reach";return;}
            spawner=hit.getBlockPos().toImmutable();mc.interactionManager.interactBlock(mc.player,Hand.MAIN_HAND,hit);
            opening=true;deadline=tick+timeout.getInt();status="Opening spawner";return;
        }
        if(owned!=menu){
            boolean continuing=owned!=null;owned=menu;opening=false;
            if(stage!=Stage.CONFIRM)stage=Stage.DROP;
            if(continuing)failures=0;next=tick+sync.getInt();deadline=tick+timeout.getInt();return;
        }
        if(stage==Stage.CONFIRM){
            int button=DropperMenus.button(menu,confirmWords.get(),confirmSlot.getInt(),"cancel;go back");if(button<0){finish("Confirm control missing; check Server Menu settings",true,false);return;}
            mc.interactionManager.clickSlot(menu.syncId,button,0,SlotActionType.PICKUP,mc.player);pages++;stage=Stage.WAIT;next=tick+sync.getInt();deadline=tick+timeout.getInt();status="Dropping loot · cycle "+pages;return;
        }
        if(stage==Stage.WAIT){
            if(!DropperMenus.title(titles.get())){if(tick>=deadline)finish("Spawner confirmation was not accepted",true,false);return;}
            if(lootCount(menu)<beforeCount||pageHash(menu)!=beforeHash){failures=0;stage=Stage.DROP;next=tick+delay.getInt();return;}
            if(tick>=deadline){if(++failures>=3){finish("Spawner did not update after dropping",true,false);return;}stage=Stage.DROP;}
            return;
        }
        int count=lootCount(menu);if(count==0){finish("Finished · "+pages+" drop cycles",false,true);return;}
        if(pages>=maxPages.getInt()){finish("Page limit reached · "+pages+" drop cycles",false,false);return;}
        if(items.is("Bones Only")&&how.is("Menu Buttons")&&mixed(menu)){finish("Mixed loot page kept in Bones Only mode",false,true);return;}
        beforeCount=count;beforeHash=pageHash(menu);
        if(how.is("Throw Stacks")){
            for(int i=0;i<45;i++){var s=menu.getSlot(i).getStack();if(loot(s)&&(!items.is("Bones Only")||s.isOf(Items.BONE))){
                if(!menu.getSlot(i).canTakeItems(mc.player))continue;
                mc.interactionManager.clickSlot(menu.syncId,i,1,SlotActionType.THROW,mc.player);pages++;stage=Stage.WAIT;next=tick+sync.getInt();deadline=tick+timeout.getInt();return;
            }}finish("No matching loot remains",false,true);return;
        }
        int button=DropperMenus.button(menu,dropWords.get(),dropSlot.getInt(),"confirm;cancel;next");if(button<0){finish("Drop control missing; check Server Menu settings",true,false);return;}
        mc.interactionManager.clickSlot(menu.syncId,button,0,SlotActionType.PICKUP,mc.player);
        stage=confirm.get()?Stage.CONFIRM:Stage.WAIT;if(!confirm.get())pages++;
        next=tick+(confirm.get()?delay.getInt():sync.getInt());deadline=tick+timeout.getInt();status="Dropping loot · cycle "+(pages+1);
    }
    @Override public void onRender2D(DrawContext c,float delta){if(inGame()&&statusHud.get())DropperMenus.status(c,getName(),status);}
    public String status(){return status;}
    public int droppedPages(){return pages;}
    public BlockPos target(){return spawner;}
}
