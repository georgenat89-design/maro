package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.render.capture.CleanCapture;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Main-screen capture setup, with the normal Minecraft window as the public source. */
public final class StreamproofScreen extends Screen {
    private static final float W=620,H=330;
    private static final int TEXT=0xFFF1F4F7,MUTED=0xFF97A5B4,ACCENT=0xFF8FDFBE;
    private final Screen parent;
    private final Streamproof module;
    private float scale=1,ox,oy,mx,my;
    private record Hit(float x,float y,float w,float h,Runnable action){boolean has(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private final List<Hit> hits=new ArrayList<>();
    public StreamproofScreen(Screen parent,Streamproof module){super(Text.literal("Streamproof"));this.parent=parent;this.module=module;}
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        scale=Math.min(width*.93f/W,height*.88f/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();
        Render2D.rect(c,0,0,width,height,0xB5080C12);c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.shadow(c,0,5,W,H,12,18,0x80000000);Render2D.roundRect(c,0,0,W,H,12,0xFF111821);Render2D.roundOutline(c,0,0,W,H,12,.7f,0x28FFFFFF);
            text(c,"STREAMPROOF",24,25,.65f,true,ACCENT);text(c,"Your game. A clean live view.",24,53,1.15f,true,TEXT);
            button(c,module.isEnabled()?"Enabled":"Enable",420,21,90,29,module.isEnabled(),module::toggle);button(c,"Done",521,21,75,29,false,this::close);
            Render2D.roundRect(c,24,86,572,78,9,0xFF1C2B30);Render2D.circle(c,43,109,3,module.isEnabled()&&CleanCapture.frames()>0?ACCENT:MUTED);
            text(c,"BUILT INTO YOUR MAIN SCREEN",56,109,.60f,true,ACCENT);text(c,"Share Minecraft normally",40,135,1.01f,true,TEXT);
            text(c,"1",30,190,.76f,true,ACCENT);text(c,"Windows 10 (2004+) / 11. Use windowed or borderless Minecraft.",50,190,.72f,false,TEXT);
            text(c,"2",30,216,.76f,true,ACCENT);text(c,"Scene-changing effects pause. Menus, HUD and ESP stay private.",50,216,.72f,false,TEXT);
            text(c,"3",30,242,.76f,true,ACCENT);text(c,"Check its recording preview before going live.",50,242,.76f,false,TEXT);
            text(c,module.status(),24,272,.64f,false,MUTED);
            text(c,"Scene effects restore when disabled.",24,304,.65f,false,MUTED);
            Fonts.drawRight(c,"One world render · Chat "+(Streamproof.hideChat()?"hidden":"shown"),596,304,MUTED,false,.65f);
        }finally{Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
    }
    private void text(DrawContext c,String text,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,text,x,y,color,bold,size);}
    private void button(DrawContext c,String label,float x,float y,float w,float h,boolean on,Runnable action){boolean hover=mx>=x&&my>=y&&mx<x+w&&my<y+h;Render2D.roundRect(c,x,y,w,h,6,on?0xFF2C564B:hover?0xFF374552:0xFF26333F);Fonts.drawCentered(c,label,x+w/2,y+h/2,on?ACCENT:TEXT,true,.67f);hits.add(new Hit(x,y,w,h,action));}
    @Override public boolean mouseClicked(Click event,boolean doubled){if(event.button()!=0)return true;float x=(float)(event.x()-ox)/scale,y=(float)(event.y()-oy)/scale;for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).has(x,y)){hits.get(i).action.run();break;}return true;}
    @Override public boolean keyPressed(KeyInput input){if(input.key()==GLFW.GLFW_KEY_ESCAPE)close();return true;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public void removed(){ConfigManager.saveAll();}
    @Override public boolean shouldPause(){return false;}
}
