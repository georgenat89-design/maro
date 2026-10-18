package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.module.impl.visuals.KeyboardStrokes.Cell;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Stable canvas transform during a drag; custom key coordinates are stored in HUD units. */
public final class KeyboardArrangeScreen extends Screen {
    private static final float W=760,H=440,CX=22,CY=80,CW=716,CH=300;
    private final Screen parent;
    private final KeyboardStrokes module;
    private float scale=1,ox,oy,viewScale=2,grabX,grabY;
    private String selected,dragging;
    private boolean snap=true;
    public KeyboardArrangeScreen(Screen parent,KeyboardStrokes module){super(Text.literal("Arrange Keyboard Strokes"));this.parent=parent;this.module=module;}
    public double[] keyPoint(String id){Cell c=module.cells().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow();return point(CX+12+(c.x()+c.w()/2)*viewScale,CY+12+(c.y()+c.h()/2)*viewScale);}
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    public float canvasScale(){return viewScale*scale;}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        scale=Math.min((width-16f)/W,(height-16f)/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        if(dragging==null)viewScale=Math.min(2.4f,Math.min((CW-24)/Math.max(280,module.layoutWidth()+16),(CH-24)/Math.max(110,module.layoutHeight()+16)));
        Theme.update();Render2D.rect(c,0,0,width,height,0xB8060A12);c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);
        Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.roundRect(c,0,0,W,H,13,0xFC111925);
            Fonts.drawV(c,"Arrange Keys",24,26,Theme.TEXT,true,1.14f);
            Fonts.drawV(c,"Drag any key. Arrow keys nudge · Shift for fine movement · Delete resets this layout",24,49,Theme.TEXT_MUTED,false,.7f);
            Render2D.roundRect(c,CX,CY,CW,CH,9,0xFF090F19);
            Render2D.clip(c,(int)CX,(int)CY,(int)(CX+CW),(int)(CY+CH));
            try{
                float step=25*viewScale;
                for(float x=CX+12;x<CX+CW;x+=step)Render2D.rect(c,x,CY,Render2D.px(),CH,0x10FFFFFF);
                for(float y=CY+12;y<CY+CH;y+=step)Render2D.rect(c,CX,y,CW,Render2D.px(),0x10FFFFFF);
                module.draw(c,CX+12,CY+12,viewScale,false);
                for(Cell cell:module.cells())if(cell.id().equals(selected))Render2D.roundOutline(c,CX+12+cell.x()*viewScale,CY+12+cell.y()*viewScale,cell.w()*viewScale,cell.h()*viewScale,5*viewScale,1.2f,Theme.accent());
            }finally{Render2D.unclip(c);}
            if(module.cells().isEmpty())Fonts.drawCentered(c,"Choose keys in the keyboard menu first",W/2,CY+CH/2,Theme.TEXT_MUTED,false,.9f);
            button(c,"Choose Keys",22,397,105);button(c,snap?"Snap: On":"Snap: Off",137,397,94);
            button(c,"Reset layout",241,397,105);button(c,"Place HUD",356,397,100);button(c,"Done",658,397,80);
            Fonts.drawV(c,"Saved automatically",480,410,Theme.TEXT_MUTED,false,.66f);
        }finally{Render2D.setScissor(null);Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
    }
    private void button(DrawContext c,String label,float x,float y,float w){Render2D.roundRect(c,x,y,w,26,6,0xFF23334A);Fonts.drawCentered(c,label,x+w/2,y+13,Theme.TEXT,false,.74f);}
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(click.button()!=0)return true;float x=(float)(click.x()-ox)/scale,y=(float)(click.y()-oy)/scale;
        if(y>=397&&y<423){if(x>=22&&x<127)client.setScreen(new KeyboardStrokesScreen(this,module));else if(x>=137&&x<231)snap=!snap;
            else if(x>=241&&x<346){module.resetLayout();selected=null;}else if(x>=356&&x<456)client.setScreen(new HudPlacementScreen(this,module));else if(x>=658&&x<738)close();return true;}
        if(x<CX||y<CY||x>CX+CW||y>CY+CH)return true;
        float lx=(x-CX-12)/viewScale,ly=(y-CY-12)/viewScale;var cells=module.cells();
        for(int i=cells.size()-1;i>=0;i--){Cell c=cells.get(i);if(lx>=c.x()&&ly>=c.y()&&lx<c.x()+c.w()&&ly<c.y()+c.h()){
            selected=dragging=c.id();grabX=lx-c.x();grabY=ly-c.y();return true;}}
        selected=null;return true;
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){
        if(dragging==null)return true;
        float x=((float)(click.x()-ox)/scale-CX-12)/viewScale-grabX,y=((float)(click.y()-oy)/scale-CY-12)/viewScale-grabY;
        boolean fine=(click.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0;
        float grid=snap&&!fine?5:1;x=Math.round(x/grid)*grid;y=Math.round(y/grid)*grid;
        Cell c=module.cells().stream().filter(v->v.id().equals(dragging)).findFirst().orElse(null);
        if(c!=null)module.moveKey(dragging,Math.clamp(x,0,Math.max(0,(CW-24)/viewScale-c.w())),Math.clamp(y,0,Math.max(0,(CH-24)/viewScale-c.h())));
        return true;
    }
    @Override public boolean mouseReleased(Click c){dragging=null;return true;}
    @Override public boolean keyPressed(KeyInput input){
        int k=input.key();if(k==GLFW.GLFW_KEY_ESCAPE){close();return true;}if(k==GLFW.GLFW_KEY_DELETE){module.resetLayout();selected=null;return true;}
        if(selected==null)return true;Cell c=module.cells().stream().filter(v->v.id().equals(selected)).findFirst().orElse(null);if(c==null)return true;
        float step=(input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?1:5,x=c.x(),y=c.y();
        if(k==GLFW.GLFW_KEY_LEFT)x-=step;else if(k==GLFW.GLFW_KEY_RIGHT)x+=step;else if(k==GLFW.GLFW_KEY_UP)y-=step;else if(k==GLFW.GLFW_KEY_DOWN)y+=step;else return true;
        module.moveKey(selected,x,y);return true;
    }
    @Override public void close(){dragging=null;if(client!=null)client.setScreen(parent);}
    @Override public void removed(){dragging=null;ConfigManager.saveAll();}
    @Override public boolean shouldPause(){return false;}
}
