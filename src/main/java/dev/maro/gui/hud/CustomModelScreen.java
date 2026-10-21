package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.CharacterCache;
import dev.maro.setting.ButtonSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Live queued-entity preview; it never turns, equips or teleports the actual player. */
public final class CustomModelScreen extends Screen {
    private final Screen parent;
    private final CustomModel module;
    private boolean other, previewing, dragging;
    private float angle=-20, scroll, scrollTarget, x,y,w,h,pw;
    private long frame, playStart;
    private boolean playing;
    private dev.maro.render.emote.Emote previewEmote=dev.maro.render.emote.Emote.WAVE;
    private final List<Hit> hits=new ArrayList<>();
    private record Hit(float x,float y,float w,float h,Runnable action) {
        boolean contains(double mx,double my) { return mx>=x && mx<x+w && my>=y && my<y+h; }
    }
    public CustomModelScreen(Screen parent, CustomModel module) { super(Text.literal("Custom Model")); this.parent=parent; this.module=module; }
    public boolean isPreviewing() { return previewing; }
    public String previewChoice() { return module.selected(other); }
    public void showAngle(float degrees) { angle=degrees; }
    public void showOther(boolean value) { other=value; }
    public int[] previewBounds() {
        double scale=client.getWindow().getScaleFactor();
        return new int[]{(int)((x+12)*scale),(int)((y+39)*scale),(int)((x+pw)*scale),(int)((y+h-38)*scale)};
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
            drawPreview(ctx);
            // Idle by default; Play runs the chosen emote on the preview (it never touches the real player).
            float half=(pw-30)/2;
            button(ctx,previewEmote.label(),x+18,y+44,half,18,false,()->{previewEmote=dev.maro.render.emote.Emote.values()[(previewEmote.ordinal()+1)%dev.maro.render.emote.Emote.values().length];playStart=System.nanoTime();},mx,my);
            button(ctx,playing?"Stop":"Play",x+24+half,y+44,half,18,playing,()->{playing=!playing;playStart=System.nanoTime();},mx,my);
            Fonts.drawCentered(ctx,previewChoice(),x+pw/2+6,y+h-64,Theme.TEXT,true,Math.min(.8f,(pw-20)/Math.max(1,Fonts.width(ctx,previewChoice(),true,1))));
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
    private void drawPreview(DrawContext ctx) {
        if(client==null || client.player==null) {
            Fonts.drawCentered(ctx,"Join a world to preview",x+pw/2,y+h/2,Theme.TEXT_MUTED,false,.7f); return;
        }
        previewing=true;
        PlayerEntityRenderState state;
        try {
            state=(PlayerEntityRenderState)client.getEntityRenderDispatcher().getRenderer(client.player)
                .getAndUpdateRenderState(client.player,client.getRenderTickCounter().getTickProgress(false));
        } finally { previewing=false; }
        state.id = Integer.MIN_VALUE; // Preview springs are independent of the real player.
        state.light=LightmapTextureManager.MAX_LIGHT_COORDINATE; state.shadowPieces.clear(); state.outlineColor=0;
        state.bodyYaw=180+angle; state.relativeHeadYaw=0; state.pitch=0;
        state.width/=state.baseScale; state.height/=state.baseScale; state.baseScale=1;
        state.age=(float)(System.nanoTime()%1_000_000_000_000L/1e9*20);
        ((dev.maro.render.character.CharacterHolder)state).maro$setMotion(0,0,true);
        if(state instanceof dev.maro.render.emote.EmoteRenderState emote){
            float t=(System.nanoTime()-playStart)/1e9f;
            var e=previewEmote;
            if(playing&&!e.loops()&&t>e.length()+0.6f)playing=false;
            if(playing){
                float blend=e.loops()?Math.min(1,t/.18f):Math.min(1,Math.min(t/.18f,Math.max(0,(e.length()+.3f-t)/.28f)));
                emote.maro$setEmote(e,e.loops()?t:Math.min(t,e.length()),blend);
            } else emote.maro$setEmote(null,0,0);
            dev.maro.render.character.DefaultSkins.wear(state);
        }
        var worn=CustomModel.worn(state);
        float modelHeight=worn==null?2.0f:worn.model.maxY*worn.model.scale*CustomModel.sizeFactor()/16;
        float size=Math.min((pw-30)/2.5f,(h-112)/(Math.max(2,modelHeight)+.3f));
        ctx.addEntity(state,size,new Vector3f(0,modelHeight/2,0),new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f),new Quaternionf().rotateX(.08f),
            Math.round(x+14),Math.round(y+42),Math.round(x+pw-2),Math.round(y+h-78));
        if(!previewChoice().equals("None") && CustomModel.worn(state)==null)
            Fonts.drawCentered(ctx,"Preparing model...",x+pw/2+6,y+56,Theme.TEXT_MUTED,false,.7f);
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
        if(click.x()>x+12 && click.x()<x+pw && click.y()>y+39 && click.y()<y+h-78) {dragging=true;return true;}
        return super.mouseClicked(click,doubled);
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy) { if(dragging){angle+=(float)dx*.8f;return true;}return super.mouseDragged(click,dx,dy); }
    @Override public boolean mouseReleased(Click click) {dragging=false;return super.mouseReleased(click);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(mx>=x+pw+10 && mx<x+w-12 && my>=y+70 && my<y+h-57) {
            scrollTarget-=(float)vertical*32;return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void close() {client.setScreen(parent);if(!module.isEnabled())CharacterCache.clear();}
    @Override public void removed() { if(!module.isEnabled())CharacterCache.clear(); }
    @Override public boolean shouldPause() {return false;}
}
