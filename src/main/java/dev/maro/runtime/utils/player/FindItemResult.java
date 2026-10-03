package dev.maro.runtime.utils.player;

import net.minecraft.client.MinecraftClient;
public record FindItemResult(int slot,int count) {
 public boolean found(){return slot>=0 && count>0;}
 public boolean isHotbar(){return slot>=0 && slot<9;}
 public boolean isMain(){return slot>=9 && slot<36;}
 public boolean isOffhand(){return slot==40;}
 public boolean isMainHand(){var player=MinecraftClient.getInstance().player;return player!=null&&slot==player.getInventory().getSelectedSlot();}
}
