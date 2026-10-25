package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.setting.ButtonSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Character asset browser. All interactive model presentation belongs to the shared Preview tab. */
public final class CustomModelScreen extends Screen {
    private final Screen parent;
    private final CustomModel module;
    private boolean other;
    private float scroll, scrollTarget, x,y,w,h,pw;
    private long frame;
    private final List<Hit> hits=new ArrayList<>();
    private record Hit(float x,float y,float w,float h,Runnable action) {
        boolean contains(double mx,double my) { return mx>=x && mx<x+w && my>=y && my<y+h; }
    }
    public CustomModelScreen(Screen parent, CustomModel module) { super(Text.literal("Custom Model")); this.parent=parent; this.module=module; }
    public void openUnifiedPreview() {
        var gui=parent instanceof dev.maro.gui.ClickGuiScreen existing?existing:new dev.maro.gui.ClickGuiScreen();
        client.setScreen(gui);gui.openModuleOptions(module);gui.selectPreviewTab(true);gui.visualPreview().selectModelTarget(module,other);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
        long now=System.nanoTime(); float dt=frame==0?0:Math.min(.05f,(now-frame)/1e9f); frame=now;
        w=Math.min(610,width-16); h=Math.min(370,height-16); x=(width-w)/2f; y=(height-h)/2f; pw=w*.40f;
        float listW=w-pw-28, listH=h-127, max=Math.max(0,((module.choices().size()+1)/2)*29-listH);
        scrollTarget=Math.max(0,Math.min(max,scrollTarget));scroll=Math.max(0,Math.min(max,scroll));
        scroll+=(scrollTarget-scroll)*(1-(float)Math.exp(-18*dt));
        hits.clear(); Fonts.beginRaw();
        try {
            Render2D.rect(ctx,0,0,width,height,0xCD080B12);
            Render2D.shadow(ctx,x,y+4,w,h,12,20,0x88000000);
            Render2D.roundRect(ctx,x,y,w,h,12,0xFF141A26);
            Render2D.roundOutline(ctx,x,y,w,h,12,1,0x24FFFFFF);
            Fonts.drawV(ctx,"Character Wardrobe",x+14,y+18,Theme.TEXT,true,.95f);
            button(ctx,module.isEnabled()?"Enabled":"Disabled",x+w-140,y+8,74,20,module.isEnabled(),module::toggle,mx,my);
            button(ctx,"Done",x+w-60,y+8,48,20,false,this::close,mx,my);
            Render2D.roundRect(ctx,x+12,y+39,pw-12,h-77,9,0xFF101725,0xFF101725,0xFF22334C,0xFF22334C);
            Fonts.drawCentered(ctx,other?"OTHER PLAYERS":"YOUR CHARACTER",x+pw/2+6,y+54,Theme.TEXT_MUTED,true,.62f);
            String choice=module.selected(other);
            if(h>=240){
                Fonts.drawCentered(ctx,Fonts.trim(choice,pw-24,true,.9f),x+pw/2+6,y+83,Theme.TEXT,true,.9f);
                Fonts.drawCentered(ctx,"Browse character assets here.",x+pw/2+6,y+116,Theme.TEXT_DIM,false,.65f);
                Fonts.drawCentered(ctx,"Inspect the configured player in",x+pw/2+6,y+134,Theme.TEXT_MUTED,false,.61f);
                Fonts.drawCentered(ctx,"the shared Preview tab.",x+pw/2+6,y+148,Theme.TEXT_MUTED,false,.61f);
            }
            button(ctx,"Unified Preview",x+18,Math.max(y+72,y+h-104),pw-30,22,true,this::openUnifiedPreview,mx,my);
            Fonts.drawCentered(ctx,Fonts.trim(choice,pw-20,true,.8f),x+pw/2+6,y+h-64,Theme.TEXT,true,.8f);
            button(ctx,"-",x+18,y+h-57,22,18,false,()->module.resize(-1),mx,my);
            button(ctx,"Size "+module.sizeLabel(),x+44,y+h-57,pw-76,18,false,module::resetSize,mx,my);
            button(ctx,"+",x+pw-28,y+h-57,22,18,false,()->module.resize(1),mx,my);
            float lx=x+pw+10, ly=y+70;
            button(ctx,"Your Model",lx,y+39,listW/2-3,23,!other,()->{other=false;},mx,my);
            button(ctx,"Other Players",lx+listW/2+3,y+39,listW/2-3,23,other,()->{other=true;},mx,my);
            int listHits=hits.size();
            Render2D.clip(ctx,(int)lx,(int)ly,(int)(lx+listW),(int)(ly+listH));
            var names=module.choices();
            try { for(int i=0;i<names.size();i++) {
                String name=names.get(i); float bx=lx+(i%2)*(listW/2+3),by=ly+(i/2)*29-scroll;
                if(by+25<ly || by>ly+listH) continue;
                button(ctx,name,bx,by,listW/2-3,25,module.selected(other).equals(name),()->module.select(other,name),
                    my>=ly && my<ly+listH?mx:-1,my);
            }} finally { Render2D.unclip(ctx); }
            // The visible portion of a card is its entire clickable area.
            for(int i=hits.size()-1;i>=listHits;i--) {
                Hit hit=hits.get(i);float top=Math.max(ly,hit.y),bottom=Math.min(ly+listH,hit.y+hit.h);
                if(bottom<=top)hits.remove(i);
                else hits.set(i,new Hit(hit.x,top,hit.w,bottom-top,hit.action));
            }
            if(max>0) {
                float thumb=Math.max(12,listH*listH/(max+listH));
                Render2D.roundRect(ctx,lx+listW+3,ly,2,listH,1,0x18FFFFFF);
                Render2D.roundRect(ctx,lx+listW+3,ly+(listH-thumb)*scroll/max,2,thumb,1,Theme.accent(0xA0));
            }
            float fy=y+h-49;
            Render2D.rect(ctx,lx,fy-5,listW,1,0x16FFFFFF);
            button(ctx,module.detailLabel()+" Detail",lx,fy,listW/2-3,20,false,module::cycleDetail,mx,my);
            button(ctx,other?module.targetLabel():module.heldItems()?"Items: On":"Items: Off",lx+listW/2+3,fy,listW/2-3,20,false,()->{if(other)module.cycleTargets();else module.toggleHeldItems();},mx,my);
            button(ctx,"Characters Folder",x+12,y+h-28,pw-12,18,false,()->module.getSettings().stream().filter(s->s.getName().equals("Characters Folder")).map(s->(ButtonSetting)s).findFirst().orElseThrow().press(),mx,my);
            float third=(listW-8)/3;
            button(ctx,module.arms()?"Arms: On":"Arms: Off",lx,y+h-26,third,18,false,module::toggleArms,mx,my);
            button(ctx,CustomModel.outlines()?"Lines: On":"Lines: Off",lx+third+4,y+h-26,third,18,false,module::toggleOutlines,mx,my);
            button(ctx,"Reload GLB",lx+2*(third+4),y+h-26,third,18,false,module::reloadCharacters,mx,my);
        } finally { Fonts.endRaw(); }
    }
    private void button(DrawContext ctx,String label,float bx,float by,float bw,float bh,boolean selected,Runnable action,int mx,int my) {
        boolean hover=mx>=bx && mx<bx+bw && my>=by && my<by+bh;
        Render2D.roundRect(ctx,bx,by,bw,bh,5,selected?Theme.accent(0x65):hover?0xFF2B364B:0xFF202A3B);
        if(selected)Render2D.roundOutline(ctx,bx,by,bw,bh,5,1,Theme.accent(0xA0));
        Fonts.drawCentered(ctx,label,bx+bw/2,by+bh/2,selected?Theme.TEXT:Theme.TEXT_MUTED,selected,Math.min(.75f,(bw-8)/Math.max(1,Fonts.width(ctx,label,selected,1))));
        hits.add(new Hit(bx,by,bw,bh,action));
    }
    @Override public boolean mouseClicked(Click click,boolean doubled) {
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(click.x(),click.y())) {hits.get(i).action.run();return true;}
        return super.mouseClicked(click,doubled);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(mx>=x+pw+10 && mx<x+w-12 && my>=y+70 && my<y+h-57) {
            scrollTarget-=(float)vertical*32;return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void close() {client.setScreen(parent);}
    @Override public boolean shouldPause() {return false;}
}
