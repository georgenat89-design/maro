package dev.maro.gui.render;

import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-menu blue scene controls; every action remains a focusable native widget. */
public final class MenuSceneControls {
    private enum Group { ALWAYS,SCENE,ORBIT }
    private final List<SceneButton> buttons=new ArrayList<>();
    private boolean open,orbitOpen;
    private SceneButton hinted;
    private long hintSince;
    private float scale,ox,oy;
    private int primary,secondary,panelTop,panelBottom,border,muted,track,hover,foreground;
    public boolean isOpen(){return open;}
    public boolean orbitOpen(){return orbitOpen;}
    public boolean owns(Element element){return buttons.contains(element);}
    public void close(){open=false;orbitOpen=false;visibility();}
    public void dismiss(){if(orbitOpen){orbitOpen=false;visibility();}else close();}
    private void visibility(){for(SceneButton b:buttons)b.visible=b.group==Group.ALWAYS||open&&(b.group==Group.SCENE||orbitOpen);}
    private static void save(){ConfigManager.saveClient();}
    public void init(int width,int height,Consumer<ClickableWidget> add){
        buttons.clear();hinted=null;scale=MaroTitleStyle.canvasScale(width,height);ox=(width-960*scale)/2;oy=(height-540*scale)/2;
        var trigger=button(810,480,106,30,"Scene settings",Group.ALWAYS,()->{open=!open;if(!open)orbitOpen=false;visibility();},add);
        trigger.sceneTrigger=true;trigger.chosen=()->open;
        var transparent=button(662,480,138,30,"Transparent main menu",Group.ALWAYS,()->{ClientSettings.transparentMenu.set(!ClientSettings.transparentMenu.get());save();},add);
        transparent.transparentTrigger=true;transparent.chosen=ClientSettings.transparentMenu::get;
        for(int i=0;i<2;i++){
            int index=i;String name=ClientSettings.sceneBackdrop.getModes().get(i);
            var b=button(662+i*120,184,120,28,name+" background",Group.SCENE,()->{ClientSettings.sceneBackdrop.set(name);save();},add);
            b.caption=()->name;b.chosen=()->ClientSettings.sceneBackdrop.index()==index;
        }
        row(ClientSettings.sceneTime,262,add);row(ClientSettings.sceneTrees,337,add);row(ClientSettings.sceneCamera,412,add);
        var options=button(742,380,66,23,"Orbit options",Group.SCENE,()->{orbitOpen=!orbitOpen;visibility();},add);options.caption=()->"Options";options.chosen=()->orbitOpen;
        var exit=button(600,248,22,22,"Close orbit options",Group.ORBIT,()->{orbitOpen=false;visibility();},add);exit.caption=()->"x";
        number(ClientSettings.orbitSpeed,286,add);number(ClientSettings.sceneZoom,322,add);number(ClientSettings.orbitAngle,358,add);
        var direction=button(426,394,190,25,"Orbit direction",Group.ORBIT,()->{ClientSettings.orbitDirection.cycle(1);save();},add);direction.caption=()->"Direction: "+ClientSettings.orbitDirection.get();
        var reset=button(426,431,190,22,"Reset orbit camera",Group.ORBIT,()->{ClientSettings.orbitSpeed.reset();ClientSettings.sceneZoom.reset();ClientSettings.orbitAngle.reset();ClientSettings.orbitDirection.reset();save();},add);reset.caption=()->"Reset camera";
        visibility();
    }
    private void row(ModeSetting setting,float y,Consumer<ClickableWidget> add){
        int count=setting.getModes().size();float width=240f/count;
        for(int i=0;i<count;i++){
            int index=i;String value=setting.getModes().get(i);
            var b=button(662+i*width,y,width,30,setting.getName()+": "+value,Group.SCENE,()->{setting.set(value);save();},add);
            b.icons=setting;b.index=i;b.chosen=()->setting.index()==index;
        }
    }
    private void number(NumberSetting setting,float y,Consumer<ClickableWidget> add){
        var minus=button(530,y,22,23,"Decrease "+setting.getName(),Group.ORBIT,()->{setting.set(setting.get()-setting.getStep());save();},add);minus.caption=()->"-";
        var value=button(556,y,42,23,"Reset "+setting.getName(),Group.ORBIT,()->{setting.reset();save();},add);value.caption=setting::format;
        var plus=button(602,y,22,23,"Increase "+setting.getName(),Group.ORBIT,()->{setting.set(setting.get()+setting.getStep());save();},add);plus.caption=()->"+";
    }
    private SceneButton button(float x,float y,float w,float h,String name,Group group,Runnable action,Consumer<ClickableWidget> add){
        var b=new SceneButton(x,y,w,h,name,group,action);buttons.add(b);add.accept(b);return b;
    }
    private final class SceneButton extends ClickableWidget {
        final Group group;final Runnable action;final String name;
        Supplier<String> caption;BooleanSupplier chosen=()->false;ModeSetting icons;int index;
        boolean sceneTrigger,transparentTrigger;
        SceneButton(float x,float y,float w,float h,String name,Group group,Runnable action){
            super(Math.round(ox+x*scale),Math.round(oy+y*scale),Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),Text.literal(name));
            this.name=name;this.group=group;this.action=action;
        }
        @Override protected void renderWidget(DrawContext ctx,int mx,int my,float delta){
            boolean selected=chosen.getAsBoolean();float bx=getX(),by=getY(),bw=getWidth(),bh=getHeight(),radius=Math.min(13*scale,bh/2);
            if(selected||sceneTrigger||transparentTrigger||caption!=null||isSelected()){
                if(selected&&Theme.glow())Render2D.shadow(ctx,bx+2*scale,by+2*scale,bw-4*scale,bh-4*scale,radius,7*scale,0x303391FC);
                boolean lit=selected||sceneTrigger||transparentTrigger;
                int fill=isSelected()?hover:track;
                Render2D.roundGradientH(ctx,bx+2*scale,by+2*scale,bw-4*scale,bh-4*scale,radius,lit?primary:fill,lit?secondary:fill);
                if(isFocused())Render2D.roundOutline(ctx,bx+2*scale,by+2*scale,bw-4*scale,bh-4*scale,radius,scale,ColorUtil.shade(primary,.55f));
            }
            ctx.createNewRootLayer();int color=sceneTrigger||transparentTrigger?0xFFFFFFFF:selected?foreground:isSelected()?Theme.TEXT:Theme.TEXT_DIM;
            if(sceneTrigger){MenuTypography.label(ctx,"Scene",bx+15*scale,by+9*scale,12*scale,color,true);sliders(ctx,bx+82*scale,by+15*scale,13*scale,color);}
            else if(transparentTrigger){
                MenuTypography.label(ctx,"Transparent",bx+12*scale,by+10*scale,10*scale,color,true);
                float cx=bx+bw-17*scale,cy=by+bh/2;Render2D.roundOutline(ctx,cx-4*scale,cy-4*scale,8*scale,8*scale,2*scale,scale,color);
                if(selected){Render2D.line(ctx,cx-2*scale,cy,cx,cy+2*scale,scale,color);Render2D.line(ctx,cx,cy+2*scale,cx+3*scale,cy-2*scale,scale,color);}
            }else if(icons!=null)icon(ctx,icons,index,bx+bw/2,by+bh/2,16*scale,color);
            else if(caption!=null){String text=caption.get();float size=Math.min(10*scale,(bw-10*scale)/Math.max(1,MenuTypography.labelWidth(text,1,true)));MenuTypography.label(ctx,text,bx+(bw-MenuTypography.labelWidth(text,size,true))/2,by+(bh-size*.73f)/2,size,color,true);}
        }
        @Override public net.minecraft.text.MutableText getNarrationMessage(){return net.minecraft.text.Text.literal(name+(caption==null?"":", "+caption.get())+(chosen.getAsBoolean()?", selected":""));}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
        @Override public void onClick(Click click,boolean doubled){action.run();}
        @Override public boolean keyPressed(KeyInput input){if(visible&&active&&isFocused()&&(input.key()==257||input.key()==335||input.key()==32)){playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager());action.run();return true;}return false;}
    }
    public boolean mouseClicked(Click click,boolean doubled,Consumer<Element> focus){
        for(SceneButton b:buttons)if(b.visible&&b.isMouseOver(click.x(),click.y())&&b.mouseClicked(click,doubled)){focus.accept(b);return true;}
        if(open){if(blocksPointer(click.x(),click.y()))return true;close();return true;}return false;
    }
    public boolean blocksPointer(double x,double y){
        double px=(x-ox)/scale,py=(y-oy)/scale;
        return open&&(px>=648&&px<916&&py>=167&&py<466||orbitOpen&&px>=410&&px<632&&py>=238&&py<466);
    }
    public void render(DrawContext ctx,int mx,int my,float delta){
        palette();ctx.createNewRootLayer();
        if(open){
            var m=ctx.getMatrices();m.pushMatrix();m.translate(ox,oy);m.scale(scale,scale);
            panel(ctx,648,167,268,299);
            Render2D.roundRect(ctx,662,184,240,28,14,track);
            title(ctx,"TIME OF DAY",ClientSettings.sceneTime,237);title(ctx,"TREES",ClientSettings.sceneTrees,312);title(ctx,"CAMERA",ClientSettings.sceneCamera,387);
            if(orbitOpen){
                panel(ctx,410,238,222,228);ctx.createNewRootLayer();
                MenuTypography.label(ctx,"Orbit options",426,255,13,Theme.TEXT,true);
                MenuTypography.label(ctx,"Speed",426,293,11,Theme.TEXT_DIM,false);MenuTypography.label(ctx,"Zoom",426,329,11,Theme.TEXT_DIM,false);MenuTypography.label(ctx,"View angle",426,365,11,Theme.TEXT_DIM,false);
            }
            m.popMatrix();ctx.createNewRootLayer();
        }
        for(SceneButton b:buttons)if(b.visible)b.render(ctx,mx,my,delta);
        tooltip(ctx,mx,my);
    }
    private void panel(DrawContext ctx,float x,float y,float w,float h){
        Render2D.shadow(ctx,x,y,w,h,17,18,0x60060610);Render2D.roundGradientV(ctx,x,y,w,h,17,panelTop,panelBottom);Render2D.roundOutline(ctx,x,y,w,h,17,.8f,border);
    }
    private void palette(){
        // The main menu has its own blue palette, independent of Click GUI presets.
        primary=0xFF2165B5;secondary=0xFF2671C9;foreground=0xFFF6F7FB;
        panelTop=0xF3192434;panelBottom=0xF3101825;border=0xFF3D587A;
        muted=0xFF9EADC2;track=0xA60B1421;hover=0xFF263C57;
    }
    private void tooltip(DrawContext ctx,int mx,int my){
        SceneButton target=null;
        for(var button:buttons)if(button.visible&&button.active&&button.isMouseOver(mx,my)){target=button;break;}
        if(target==null)for(var button:buttons)if(button.visible&&button.active&&button.isFocused()){target=button;break;}
        long now=System.nanoTime();
        if(target!=hinted){hinted=target;hintSince=now;}
        if(target==null||now-hintSince<300_000_000L)return;
        String text=target.name;
        if(target.icons!=null){String row=target.icons==ClientSettings.sceneTime?"Time of day":target.icons==ClientSettings.sceneTrees?"Trees":"Camera";text=row+": "+target.icons.getModes().get(target.index);}
        float edge=6*scale,pad=9*scale,size=10*scale;
        float screenW=net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
        float screenH=net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
        float available=Math.max(1,screenW-2*edge-2*pad);
        size=Math.min(size,available/Math.max(1,MenuTypography.labelWidth(text,1,false)));
        float w=MenuTypography.labelWidth(text,size,false)+pad*2,h=25*scale;
        boolean mouse=target.isMouseOver(mx,my);
        float x=mouse?mx+12*scale:target.getX(),y=mouse?my+17*scale:target.getBottom()+5*scale;
        x=Math.max(edge,Math.min(x,screenW-edge-w));
        if(y+h>screenH-edge)y=(mouse?my:target.getY())-h-7*scale;
        y=Math.max(edge,y);ctx.createNewRootLayer();
        Render2D.shadow(ctx,x,y,w,h,6*scale,8*scale,0x50000000);
        Render2D.roundRect(ctx,x,y,w,h,6*scale,0xFC111D2D);
        Render2D.roundOutline(ctx,x,y,w,h,6*scale,.75f*scale,0xB3476E9D);
        ctx.createNewRootLayer();MenuTypography.label(ctx,text,x+pad,y+(h-size*.73f)/2,size,Theme.TEXT,false);
    }
    private void title(DrawContext ctx,String label,ModeSetting setting,float y){
        Render2D.roundRect(ctx,662,y+25,240,30,15,track);ctx.createNewRootLayer();MenuTypography.label(ctx,label,664,y,9,muted,true);
        String value=setting.get();MenuTypography.label(ctx,value,900-MenuTypography.labelWidth(value,10,true),y,10,Theme.TEXT_DIM,true);
    }
    private static void sliders(DrawContext c,float x,float y,float s,int color){
        for(int i=0;i<3;i++){float yy=y+(i-1)*s*.32f;Render2D.line(c,x-s*.42f,yy,x+s*.42f,yy,s*.08f,color);Render2D.circle(c,x+(i==1?-.17f:.2f)*s,yy,s*.13f,color);}
    }
    private static void icon(DrawContext c,ModeSetting setting,int index,float x,float y,float s,int color){
        if(setting==ClientSettings.sceneTime){
            if(index==4){Render2D.ring(c,x,y,s*.40f,s*.095f,color);Render2D.line(c,x,y,x,y-s*.22f,s*.09f,color);Render2D.line(c,x,y,x+s*.18f,y+s*.08f,s*.09f,color);}
            else if(index==3){Render2D.arc(c,x,y,s*.36f,s*.10f,60,275,color,color);Render2D.line(c,x+s*.07f,y-s*.33f,x-s*.1f,y-s*.01f,s*.10f,color);Render2D.line(c,x-s*.1f,y-s*.01f,x+s*.29f,y+s*.18f,s*.10f,color);}
            else{
                float cy=index==1?y:y+s*.08f;Render2D.ring(c,x,cy,s*.16f,s*.075f,color);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;if(index!=1&&Math.sin(a)>.3)continue;Render2D.line(c,x+(float)Math.cos(a)*s*.29f,cy+(float)Math.sin(a)*s*.29f,x+(float)Math.cos(a)*s*.4f,cy+(float)Math.sin(a)*s*.4f,s*.07f,color);}
                if(index!=1){Render2D.line(c,x-s*.42f,y+s*.28f,x+s*.42f,y+s*.28f,s*.08f,color);Render2D.line(c,x-s*.25f,y+s*.44f,x+s*.25f,y+s*.44f,s*.07f,color);}
            }
        }else if(setting==ClientSettings.sceneTrees){
            if(index==4){Render2D.line(c,x-s*.4f,y+s*.3f,x+s*.35f,y-s*.3f,s*.09f,color);Render2D.line(c,x-s*.4f,y-s*.3f,x+s*.35f,y+s*.3f,s*.09f,color);Render2D.line(c,x+s*.15f,y-s*.32f,x+s*.4f,y-s*.32f,s*.09f,color);Render2D.line(c,x+s*.4f,y-s*.32f,x+s*.4f,y-s*.07f,s*.09f,color);}
            else if(index==3){for(int i=0;i<3;i++){float yy=y-s*.35f+i*s*.2f,r=s*(.18f+i*.09f);Render2D.line(c,x,yy,x-r,yy+s*.27f,s*.1f,color);Render2D.line(c,x,yy,x+r,yy+s*.27f,s*.1f,color);Render2D.line(c,x-r,yy+s*.27f,x+r,yy+s*.27f,s*.1f,color);}Render2D.line(c,x,y+s*.3f,x,y+s*.5f,s*.1f,color);}
            else{float r=index==1?s*.22f:s*.28f;Render2D.ring(c,x,y-s*.14f,r,s*.085f,color);Render2D.ring(c,x-s*.19f,y+s*.04f,r*.78f,s*.08f,color);Render2D.ring(c,x+s*.19f,y+s*.04f,r*.78f,s*.08f,color);Render2D.line(c,x,y+s*.13f,x,y+s*.45f,s*.09f,color);if(index==2)Render2D.circle(c,x,y-s*.12f,s*.075f,color);}
        }else if(index==2){Render2D.roundRect(c,x-s*.22f,y-s*.34f,s*.14f,s*.68f,s*.045f,color);Render2D.roundRect(c,x+s*.08f,y-s*.34f,s*.14f,s*.68f,s*.045f,color);}
        else if(index==1){for(int i=0;i<3;i++)Render2D.line(c,x-s*.4f,y+(i-1)*s*.24f,x+s*(i==1?.4f:.16f),y+(i-1)*s*.24f,s*.09f,color);}
        else{Render2D.arc(c,x,y,s*.38f,s*.09f,35,285,color,color);Render2D.line(c,x+s*.33f,y-s*.19f,x+s*.33f,y-s*.43f,s*.09f,color);Render2D.line(c,x+s*.33f,y-s*.19f,x+s*.09f,y-s*.19f,s*.09f,color);Render2D.circle(c,x,y,s*.10f,color);}
    }
}
