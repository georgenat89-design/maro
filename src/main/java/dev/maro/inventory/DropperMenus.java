package dev.maro.inventory;

import dev.maro.donut.OrderMarket;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.OrderDropper;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import java.util.List;

/** Shared menu boundaries and presentation; all clicks remain in the owning module. */
public final class DropperMenus {
    private DropperMenus(){}
    public static GenericContainerScreenHandler menu(){
        var mc=MinecraftClient.getInstance();
        return mc.player!=null&&mc.currentScreen instanceof GenericContainerScreen screen&&mc.player.currentScreenHandler==screen.getScreenHandler()?screen.getScreenHandler():null;
    }
    public static boolean title(String words){var mc=MinecraftClient.getInstance();return mc.currentScreen!=null&&OrderMarket.has(mc.currentScreen.getTitle().getString(),words);}
    public static boolean matches(Slot slot,String words){
        if(!slot.hasStack())return false;
        if(OrderMarket.has(slot.getStack().getName().getString(),words))return true;
        var lore=slot.getStack().get(DataComponentTypes.LORE);return lore!=null&&lore.lines().stream().anyMatch(line->OrderMarket.has(line.getString(),words));
    }
    public static int button(GenericContainerScreenHandler menu,String words,int fallback){
        return button(menu,words,fallback,"");
    }
    public static int button(GenericContainerScreenHandler menu,String words,int fallback,String exclude){
        int size=menu.getRows()*9;
        // Server action controls live below the contents, not in the player's inventory.
        for(int i=Math.max(0,size-9);i<size;i++)if(matches(menu.getSlot(i),words)&&!OrderMarket.has(menu.getSlot(i).getStack().getName().getString(),exclude))return i;
        return fallback>=Math.max(0,size-9)&&fallback<size&&menu.getSlot(fallback).hasStack()&&!OrderMarket.has(menu.getSlot(fallback).getStack().getName().getString(),exclude)?fallback:-1;
    }
    public static double price(ItemStack stack){
        var lore=stack.get(DataComponentTypes.LORE);
        return OrderMarket.priceEach(lore==null?List.of():lore.lines().stream().map(t->t.getString()).toList(),"price;cost;buy;each");
    }
    public static boolean busy(){
        var builder=ModuleManager.get(AutoBuilder.class);var orders=ModuleManager.get(OrderDropper.class);
        return builder!=null&&builder.isEnabled()&&builder.busy()||orders!=null&&orders.isEnabled();
    }
    public static void status(DrawContext c,String name,String text){
        var mc=MinecraftClient.getInstance();if(mc.options.hudHidden)return;
        String label=name+" · "+text;float max=mc.getWindow().getScaledWidth()-24;
        label=Fonts.trim(label,max-16,true,.7f);float w=Fonts.width(label,true,.7f)+16,x=(mc.getWindow().getScaledWidth()-w)/2,y=mc.getWindow().getScaledHeight()-77;
        Render2D.roundRect(c,x,y,w,18,6,0xDE101B27);Render2D.roundOutline(c,x,y,w,18,6,.6f,0xFF33485C);
        Fonts.drawCentered(c,label,x+w/2,y+9,0xFFE1EBF8,true,.7f);
    }
}
