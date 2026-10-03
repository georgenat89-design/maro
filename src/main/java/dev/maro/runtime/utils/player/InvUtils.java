package dev.maro.runtime.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.screen.slot.*;
import java.util.function.Predicate;
public final class InvUtils {
 private static final MinecraftClient mc=MinecraftClient.getInstance();
 public static FindItemResult find(Item item){return find(stack->stack.isOf(item));}
 public static FindItemResult find(Predicate<ItemStack> test){return find(test,false);}
 public static FindItemResult findInHotbar(Item item){return findInHotbar(stack->stack.isOf(item));}
 public static FindItemResult findInHotbar(Predicate<ItemStack> test){return find(test,true);}
 private static FindItemResult find(Predicate<ItemStack> test,boolean hotbar){
  if(mc.player==null)return new FindItemResult(-1,0);var inventory=mc.player.getInventory();int slot=-1,count=0;
  for(int i=0;i<(hotbar?9:36);i++){var stack=inventory.getStack(i);if(!stack.isEmpty()&&test.test(stack)){if(slot<0)slot=i;count+=stack.getCount();}}
  var offhand=mc.player.getOffHandStack();if(!offhand.isEmpty()&&test.test(offhand)){if(slot<0)slot=40;count+=offhand.getCount();}
  return new FindItemResult(slot,count);
 }
 public static boolean testInMainHand(Item item){return mc.player!=null&&mc.player.getMainHandStack().isOf(item);}
 public static boolean testInOffHand(Item item){return mc.player!=null&&mc.player.getOffHandStack().isOf(item);}
 public static boolean swap(int slot,boolean remember){if(mc.player==null||slot<0||slot>8)return false;mc.player.getInventory().setSelectedSlot(slot);return true;}
 private static int slotId(int inventorySlot){
  if(mc.player==null)return -1;
  for(var slot:mc.player.currentScreenHandler.slots)if(slot.inventory==mc.player.getInventory() && slot.getIndex()==inventorySlot)return slot.id;
  return -1;
 }
 private static void click(int slot,int button,SlotActionType action){if(mc.player!=null&&mc.interactionManager!=null&&slot>=0)mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,slot,button,action,mc.player);}
 public static Action shiftClick(){return new Action(SlotActionType.QUICK_MOVE);}
 public static Action move(){return new Action(SlotActionType.SWAP);}
 public static Action quickSwap(){return new Action(SlotActionType.SWAP);}
 public static final class Action {
  private final SlotActionType type;private int from=-1;
  Action(SlotActionType type){this.type=type;}
  public Action from(int slot){from=slot;return this;}
  /** Source is a hotbar button for SWAP, not a container slot ID. */
  public Action fromId(int hotbar){from=hotbar;return this;}
  public void to(int slot){click(InvUtils.slotId(slot),from,type);}
  public void toOffhand(){click(InvUtils.slotId(40),from,type);}
  public void toHotbar(int hotbar){click(InvUtils.slotId(from),hotbar,type);}
  public void slotId(int slot){click(slot,0,type);}
 }
}
