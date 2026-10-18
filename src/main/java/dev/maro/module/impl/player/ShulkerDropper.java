package dev.maro.module.impl.player;

import dev.maro.inventory.DropperMenus;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.*;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import java.util.ArrayList;
import java.util.List;

/** Native shop-buy/drop workflow, informed by the supplied Glazed ShulkerDropper behavior. */
public final class ShulkerDropper extends Module {
    private enum Stage { OPEN,BUY,WAIT_BUY,DROP,WAIT_DROP }
    private final ModeSetting mode=add(new ModeSetting("Mode","Buy boxes from the shop, buy and drop, or drop boxes already in your inventory","Buy & Drop","Buy & Drop","Buy Only","Drop Inventory"));
    private final NumberSetting delay=add(new NumberSetting("Action Delay","Ticks between acknowledged actions",2,1,20,1).suffix(" ticks"));
    private final NumberSetting settle=add(new NumberSetting("Sync Wait","Wait for inventory updates after a drop",4,2,40,1).suffix(" ticks"));
    private final NumberSetting timeout=add(new NumberSetting("Timeout","Stop if a menu or purchase does not respond",80,20,400,5).suffix(" ticks"));
    private final BooleanSetting stopLimit=add(new BooleanSetting("Stop At Limit","Stop after buying the selected number of boxes",true));
    private final NumberSetting limit=add(new NumberSetting("Purchase Limit","Maximum newly bought boxes in one run",64,1,4096,1).visible(stopLimit::get));
    private final NumberSetting maxPrice=add(new NumberSetting("Max Price","Maximum shop price per purchase; 0 disables the price cap",0,0,1e9,100));
    private final BooleanSetting protect=add(new BooleanSetting("Keep Existing Boxes","Keep all matching boxes you owned before Buy & Drop started",true));
    private final BooleanSetting filled=add(new BooleanSetting("Drop Filled Boxes","Also allow boxes containing items in Drop Inventory mode",false).visible(()->mode.is("Drop Inventory")));
    private final NumberSetting keep=add(new NumberSetting("Keep","Additional boxes to retain",0,0,36,1));
    private final BooleanSetting statusHud=add(new BooleanSetting("Status HUD","Show progress above the hotbar",true));
    private final TextSetting command=add(new TextSetting("Shop Command","Command that opens your shop","shop",80,"shop"));
    private final TextSetting titles=add(new TextSetting("Shop Titles","Recognized shop titles, separated by semicolons","shop;shulker",120,"shop;shulker"));
    private final TextSetting category=add(new TextSetting("Category Button","Optional shop category to open before buying","",80,"Leave blank for direct shop"));
    private Stage stage;
    private Object world,connection;
    private GenericContainerScreenHandler owned;
    private final List<ItemStack> baseline=new ArrayList<>();
    private int tick,next,deadline,beforeCount,bought,dropped,rejections,navigations;
    private boolean sent,categoryClicked;
    private String status="Off";
    public ShulkerDropper(){super("Shulker Dropper","Buy and drop shulker boxes with purchase limits and inventory protection",Category.PLAYER);}
    @Override public boolean persistEnabled(){return false;}
    @Override public List<SettingSection> getSettingSections(){return List.of(
        SettingSection.of("Actions",mode,delay,settle,timeout,statusHud),SettingSection.of("Buying",stopLimit,limit,maxPrice,protect,command,titles,category),SettingSection.of("Dropping",keep,filled));}
    public static boolean box(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof ShulkerBoxBlock;}
    private boolean droppable(ItemStack stack){
        if(!box(stack))return false;var contents=stack.get(DataComponentTypes.CONTAINER);
        return mode.is("Drop Inventory")&&filled.get()||contents==null||contents.stream().allMatch(ItemStack::isEmpty);
    }
    private int count(){int total=0;for(int i=0;i<36;i++){var s=mc.player.getInventory().getStack(i);if(box(s))total+=s.getCount();}return total;}
    private int countLike(ItemStack stack){int total=0;for(int i=0;i<36;i++){var s=mc.player.getInventory().getStack(i);if(ItemStack.areItemsAndComponentsEqual(s,stack))total+=s.getCount();}return total;}
    private int retained(ItemStack stack){int total=0;for(var s:baseline)if(ItemStack.areItemsAndComponentsEqual(s,stack))total+=s.getCount();return total;}
    private int availableToDrop(){
        int total=0;for(int i=0;i<36;i++){var s=mc.player.getInventory().getStack(i);if(droppable(s))total+=s.getCount();}
        for(var s:baseline)if(droppable(s))total-=s.getCount();return Math.max(0,total-keep.getInt());
    }
    @Override protected void onEnable(){
        var other=ModuleManager.get(SpawnerDropper.class);if(other!=null)other.setEnabled(false);
        baseline.clear();tick=next=bought=dropped=rejections=navigations=0;sent=categoryClicked=false;owned=null;world=mc.world;connection=mc.getNetworkHandler();
        if(!inGame()){finish("Join a world first",true);return;}
        if(protect.get()&&!mode.is("Drop Inventory"))for(int i=0;i<36;i++)if(box(mc.player.getInventory().getStack(i)))baseline.add(mc.player.getInventory().getStack(i).copy());
        stage=mode.is("Drop Inventory")?Stage.DROP:Stage.OPEN;deadline=timeout.getInt();status="Starting";
    }
    @Override protected void onDisable(){stage=null;owned=null;baseline.clear();}
    private void finish(String message,boolean error){status=message;setEnabled(false);Notifications.push(getName(),message,error?Notifications.Type.WARNING:Notifications.Type.SUCCESS);}
    private boolean shop(GenericContainerScreenHandler menu){return menu!=null&&DropperMenus.title(titles.get())&&!dev.maro.inventory.ChestSessions.physical(menu);}
    private void waitUntil(Stage value,int before){stage=value;beforeCount=before;next=tick+settle.getInt();deadline=tick+timeout.getInt();}
    @Override public void onTick(){
        if(!isEnabled()||stage==null)return;
        if(!inGame()||mc.interactionManager==null||mc.getNetworkHandler()!=connection||mc.world!=world){finish("World or connection changed",true);return;}
        if(mc.isPaused()||DropperMenus.busy()){status="Waiting for other inventory actions";return;}
        tick++;if(tick<next)return;
        var handler=mc.player.currentScreenHandler;
        if(!handler.getCursorStack().isEmpty()){finish("Cursor holds an item; clear it before restarting",true);return;}
        if(stage==Stage.WAIT_BUY){
            int change=count()-beforeCount;
            if(change>0){bought+=change;stage=mode.is("Buy & Drop")?Stage.DROP:Stage.BUY;next=tick+delay.getInt();deadline=tick+timeout.getInt();status="Bought "+bought;return;}
            var page=DropperMenus.menu();if(shop(page)&&page!=owned){
                if(++navigations>4){finish("Shop navigation did not reach a purchase",true);return;}
                owned=page;stage=Stage.BUY;next=tick+settle.getInt();deadline=tick+timeout.getInt();return;
            }
            if(tick>=deadline)finish("Purchase did not add a box; stopped",true);return;
        }
        if(stage==Stage.WAIT_DROP){
            int change=beforeCount-count();
            if(change>0){dropped+=change;rejections=0;stage=Stage.DROP;next=tick+delay.getInt();return;}
            if(tick>=deadline){if(++rejections>=3){finish("Drops were rejected; stopped",true);return;}stage=Stage.DROP;}
            return;
        }
        if(stage==Stage.DROP){
            if(mc.currentScreen!=null&&!(handler==owned&&shop(DropperMenus.menu()))){status="Close this menu to continue dropping";return;}
            int total=availableToDrop();
            for(var slot:handler.slots){
                if(slot.inventory!=mc.player.getInventory()||slot.getIndex()<0||slot.getIndex()>=36||!slot.canTakeItems(mc.player)||!droppable(slot.getStack()))continue;
                int available=countLike(slot.getStack())-retained(slot.getStack());if(available<=0||total<=0)continue;
                int before=count();int button=Math.min(available,total)>=slot.getStack().getCount()?1:0;
                mc.interactionManager.clickSlot(handler.syncId,slot.id,button,SlotActionType.THROW,mc.player);
                waitUntil(Stage.WAIT_DROP,before);status="Dropping bought boxes · "+dropped+" dropped";return;
            }
            if(mode.is("Drop Inventory")||stopLimit.get()&&bought>=limit.getInt()){finish("Finished · "+bought+" bought, "+dropped+" dropped",false);return;}
            stage=Stage.BUY;next=tick+delay.getInt();deadline=tick+timeout.getInt();
        }
        if(stopLimit.get()&&bought>=limit.getInt()){finish("Purchase limit reached · "+bought+" bought",false);return;}
        var menu=DropperMenus.menu();
        if(!shop(menu)){
            if(mc.currentScreen!=null){if(stage==Stage.OPEN&&sent&&tick>=deadline)finish("Unexpected shop menu",true);else status="Open a recognized shop or close this menu";return;}
            if(!sent||stage!=Stage.OPEN){
                String cmd=command.get().strip().replaceFirst("^/","");if(cmd.isBlank()){finish("Set Shop Command first",true);return;}
                mc.getNetworkHandler().sendChatCommand(cmd);stage=Stage.OPEN;sent=true;categoryClicked=false;deadline=tick+timeout.getInt();status="Opening shop";return;
            }
            if(tick>=deadline)finish("Shop did not open",true);return;
        }
        if(owned!=menu){owned=menu;next=tick+delay.getInt();deadline=tick+timeout.getInt();return;}
        stage=Stage.BUY;sent=false;
        int itemSlot=-1;for(int i=0;i<menu.getRows()*9;i++)if(box(menu.getSlot(i).getStack())){itemSlot=i;break;}
        if(itemSlot<0){
            if(!categoryClicked&&!category.get().isBlank())for(int i=0;i<menu.getRows()*9;i++)if(DropperMenus.matches(menu.getSlot(i),category.get())){
                mc.interactionManager.clickSlot(menu.syncId,i,0,SlotActionType.PICKUP,mc.player);categoryClicked=true;next=tick+settle.getInt();deadline=tick+timeout.getInt();return;
            }
            if(tick>=deadline)finish("No shulker listing found in the shop",true);else status="Waiting for shulker listing";return;
        }
        if(mc.player.getInventory().getEmptySlot()<0){finish("Inventory is full",true);return;}
        if(maxPrice.get()>0){double price=DropperMenus.price(menu.getSlot(itemSlot).getStack());if(!Double.isFinite(price)||price>maxPrice.get()){finish("Listing price is unknown or above Max Price",true);return;}}
        int before=count();mc.interactionManager.clickSlot(menu.syncId,itemSlot,0,SlotActionType.PICKUP,mc.player);waitUntil(Stage.WAIT_BUY,before);status="Waiting for purchase · "+bought+" bought";
    }
    @Override public void onRender2D(DrawContext c,float delta){if(inGame()&&statusHud.get())DropperMenus.status(c,getName(),status);}
    public String status(){return status;}
    public int boughtCount(){return bought;}
    public int droppedCount(){return dropped;}
}
