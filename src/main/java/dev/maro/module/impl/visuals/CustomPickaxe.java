package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.SkinAccessoriesScreen;
import dev.maro.render.pickaxe.PickaxeModels;
import dev.maro.render.pickaxe.PickaxeRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;
import java.util.*;

/** Client-only original 3D pickaxe cosmetics; the real stack and mining behavior are untouched. */
public final class CustomPickaxe {
    private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    private CustomPickaxe(){}
    public static ItemStack icon(String name){var stack=new ItemStack(Items.DIAMOND_PICKAXE);stack.set(DataComponentTypes.ITEM_MODEL,Identifier.of("maro","custom_pickaxe/"+PickaxeModels.key(name)));return stack;}
    public static boolean handles(ItemStack stack){var m=SkinAccessories.get();var own=stack.get(DataComponentTypes.ITEM_MODEL);return !dev.maro.render.capture.CapturePass.active()&&m!=null&&m.isEnabled()&&PickaxeModels.contains(m.selected())&&m.pickaxeFirst.get()&&stack.isIn(ItemTags.PICKAXES)&&(own==null||Objects.equals(own,stack.getItem().getComponents().get(DataComponentTypes.ITEM_MODEL)));}
    public static boolean renderHand(net.minecraft.client.util.math.MatrixStack pose,net.minecraft.client.render.command.OrderedRenderCommandQueue queue,int light,ItemStack stack,boolean left,float equip,float swing){
        var m=SkinAccessories.get();if(!handles(stack)||!m.pickaxeSwing.get()||mc.player==null||mc.player.isUsingItem())return false;
        pose.push();
        try{
            pose.translate(left?-.45f:.45f,-.24f-equip*.45f,-.91f);
            if(left)pose.scale(-1,1,1);
            dev.maro.render.pickaxe.PickaxeMotion.apply(pose,m.selected(),swing);
            pose.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-24));
            pose.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-17));
            float scale=.69f*m.itemSize();pose.scale(scale,scale,scale);
            dev.maro.render.pickaxe.PickaxeRenderer.draw(pose,queue,light,stack,m.selected(),m.previewStyle(0,0,1));
        }finally{pose.pop();}
        return true;
    }
    public static boolean update(ItemRenderState state,ItemStack stack,ItemDisplayContext context,HeldItemContext holder){
        var m=SkinAccessories.get();if(dev.maro.render.capture.CapturePass.active()||m==null||stack.isEmpty()||!stack.isIn(ItemTags.PICKAXES))return false;
        String name=m.selected();boolean preview=false;var own=stack.get(DataComponentTypes.ITEM_MODEL);
        if(context==ItemDisplayContext.GUI&&mc.currentScreen instanceof SkinAccessoriesScreen&&own!=null&&own.getNamespace().equals("maro")&&own.getPath().startsWith("custom_pickaxe/")){
            for(String choice:PickaxeModels.NAMES)if(own.getPath().equals("custom_pickaxe/"+PickaxeModels.key(choice))){name=choice;preview=true;break;}
        }
        if(!preview){
            if(!(m.isEnabled()||dev.maro.gui.render.PlayerPreviewState.extracting(m))||!PickaxeModels.contains(name)||own!=null&&!Objects.equals(own,stack.getItem().getComponents().get(DataComponentTypes.ITEM_MODEL)))return false;
            boolean mine=holder!=null&&holder.getEntity()==mc.player;
            boolean first=context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            boolean third=context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            if(!(context==ItemDisplayContext.GUI&&m.pickaxeIcons.get()||mine&&(first&&m.pickaxeFirst.get()||third&&m.pickaxeThird.get())))return false;
        }
        var style=m.previewStyle(0,0,1);
        PickaxeRenderer.update(state,name,context,style,stack.hasGlint());return true;
    }
}
