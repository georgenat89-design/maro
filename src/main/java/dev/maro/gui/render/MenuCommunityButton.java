package dev.maro.gui.render;

import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import java.net.URI;
import java.util.function.Consumer;

/** Native focusable community link with bounded hover motion and a replaceable URL opener. */
public final class MenuCommunityButton extends ClickableWidget {
    public static final URI LINK=URI.create("https://discord.gg/navo");
    private final Consumer<URI> opener;
    private final Animation hover=new Animation(15,0);
    public MenuCommunityButton(int x,int y,int w,int h){this(x,y,w,h,uri->net.minecraft.util.Util.getOperatingSystem().open(uri));}
    public MenuCommunityButton(int x,int y,int w,int h,Consumer<URI> opener){
        super(x,y,w,h,Text.literal("Join the community: discord.gg/navo"));this.opener=opener;
    }
    @Override protected void renderWidget(DrawContext ctx,int mx,int my,float delta){
        float s=getHeight()/31f,t=hover.update(isSelected()?1:0),x=getX(),y=getY()-1.2f*s*t,w=getWidth(),h=getHeight();
        Render2D.shadow(ctx,x,y+2*s,w,h,9*s,10*s,ColorUtil.withAlpha(0x154C90,Math.round(18+t*25)));
        Render2D.roundGradientV(ctx,x,y,w,h,9*s,ColorUtil.lerp(0xEC1A2B43,0xF1264D7B,t),0xF30E1929);
        Render2D.roundOutline(ctx,x,y,w,h,9*s,.75f*s,ColorUtil.lerp(0xA7496687,0xFF82B8F5,t));
        if(isFocused())Render2D.roundOutline(ctx,x-2*s,y-2*s,w+4*s,h+4*s,11*s,s,0xFF9DC9FF);
        ctx.createNewRootLayer();int ink=ColorUtil.lerp(0xFFD8E6F7,0xFFFFFFFF,t);
        float font=10.5f*s,labelWidth=MenuTypography.labelWidth("discord.gg/navo",font,false);
        float iconWidth=14*s,iconGap=9*s,arrowGap=12*s,arrowWidth=6*s;
        float groupX=x+(w-iconWidth-iconGap-labelWidth-arrowGap-arrowWidth)/2;
        float textX=groupX+iconWidth+iconGap,cx=groupX+iconWidth/2,cy=y+h/2;
        Render2D.roundOutline(ctx,cx-7*s,cy-4.5f*s,14*s,9*s,4*s,1.1f*s,ink);
        Render2D.line(ctx,cx-5*s,cy-5*s,cx-2*s,cy-6.2f*s,s,ink);Render2D.line(ctx,cx+2*s,cy-6.2f*s,cx+5*s,cy-5*s,s,ink);
        Render2D.circle(ctx,cx-2.5f*s,cy,.95f*s,ink);Render2D.circle(ctx,cx+2.5f*s,cy,.95f*s,ink);
        MenuTypography.label(ctx,"discord.gg/navo",textX,y+(h-font*.73f)/2,font,ink,false);
        float ax=textX+labelWidth+arrowGap+arrowWidth/2,ay=cy;
        Render2D.line(ctx,ax-3*s,ay+3*s,ax+3*s,ay-3*s,s,ink);Render2D.line(ctx,ax-1*s,ay-3*s,ax+3*s,ay-3*s,s,ink);Render2D.line(ctx,ax+3*s,ay-3*s,ax+3*s,ay+1*s,s,ink);
    }
    @Override public void onClick(Click click,boolean doubled){opener.accept(LINK);}
    @Override public boolean keyPressed(KeyInput input){if(visible&&active&&isFocused()&&(input.key()==257||input.key()==335||input.key()==32)){playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager());opener.accept(LINK);return true;}return false;}
    @Override public net.minecraft.text.MutableText getNarrationMessage(){return Text.literal("Join the Maro Discord community, discord.gg/navo, opens your browser");}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
}
