package dev.maro.gui.render;

import dev.maro.config.ClientSettings;
import dev.maro.render.capture.CapturePass;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Client-owned numeric overlays only. Vanilla inventories and ordinary HUD text never enter here. */
public final class OwnedHudText {
    private OwnedHudText(){}
    public static boolean enabled(){return ClientSettings.unifiedTypography.get()&&!CapturePass.active();}

    public static void stackOverlay(DrawContext context,TextRenderer nativeFont,ItemStack stack,int x,int y){
        if(!enabled()){context.drawStackOverlay(nativeFont,stack,x,y);return;}
        // Empty replacement suppresses only the count. Native durability and cooldown draws remain.
        context.drawStackOverlay(nativeFont,stack,x,y,"");
        if(stack.isEmpty()||stack.getCount()==1)return;
        String count=Integer.toString(stack.getCount());
        int menu=Fonts.pauseMenu();float alpha=Render2D.getAlpha();Render2D.setAlpha(1);Fonts.beginRaw();
        try(var ignored=dev.maro.module.impl.misc.NameProtect.unprotected()){
            float left=x+17-Fonts.width(context,count,false,1),top=y+9;
            Fonts.draw(context,count,left+1,top+1,0xFF3F3F3F,false,1);
            Fonts.draw(context,count,left,top,0xFFFFFFFF,false,1);
        }finally{Fonts.endRaw();Render2D.setAlpha(alpha);Fonts.resumeMenu(menu);}
    }

    /** Same translated value, green ink and black cardinal outline as the native XP level. */
    public static void experienceLevel(DrawContext context,int level,float centreX,float top){
        if(level<=0)return;
        String value=Text.translatable("gui.experience.level",level).getString();
        int menu=Fonts.pauseMenu();float alpha=Render2D.getAlpha();Render2D.setAlpha(1);Fonts.beginRaw();
        try(var ignored=dev.maro.module.impl.misc.NameProtect.unprotected()){
            float x=centreX-Fonts.width(context,value,false,1)/2;
            Fonts.draw(context,value,x+1,top,0xFF000000,false,1);
            Fonts.draw(context,value,x-1,top,0xFF000000,false,1);
            Fonts.draw(context,value,x,top+1,0xFF000000,false,1);
            Fonts.draw(context,value,x,top-1,0xFF000000,false,1);
            Fonts.draw(context,value,x,top,0xFF80FF20,false,1);
        }finally{Fonts.endRaw();Render2D.setAlpha(alpha);Fonts.resumeMenu(menu);}
    }
}
