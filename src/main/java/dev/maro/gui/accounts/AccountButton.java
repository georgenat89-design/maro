package dev.maro.gui.accounts;
import dev.maro.gui.render.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Native keyboard, focus, narration and click handling with Maro's sharp labels. */
final class AccountButton extends ButtonWidget {
    private final boolean primary,danger;
    AccountButton(String text,int x,int y,int w,int h,boolean primary,boolean danger,PressAction action){super(x,y,w,h,net.minecraft.text.Text.literal(text),action,DEFAULT_NARRATION_SUPPLIER);this.primary=primary;this.danger=danger;}
    @Override public void drawIcon(DrawContext ctx,int mx,int my,float delta){
        int bg=primary?0xFF1D68AE:danger?0xFF341B28:0xFF142234;
        if(isSelected())bg=primary?0xFF267FC9:danger?0xFF58263A:0xFF233C57;
        Render2D.roundRect(ctx,getX(),getY(),getWidth(),getHeight(),4,bg);
        Render2D.roundOutline(ctx,getX(),getY(),getWidth(),getHeight(),4,1,isSelected()?0xFF5DC9FF:0xFF2B4058);
        Fonts.drawCentered(ctx,Fonts.trim(ctx,getMessage().getString(),getWidth()-10,true,1),getX()+getWidth()/2f,getY()+getHeight()/2f,
                active?danger?0xFFFF8598:BootScene.WHITE:0xFF63758A,true,1);
    }
}
