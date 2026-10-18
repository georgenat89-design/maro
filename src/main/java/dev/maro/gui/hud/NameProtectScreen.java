package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.impl.misc.NameProtect;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** The editor keeps real names readable; its live preview uses the exact in-game alias snapshot. */
public final class NameProtectScreen extends Screen {
    private static final float W=780,H=440;
    private static final int TEXT=0xFFF0F3F8,MUTED=0xFF96A5B9,ACCENT=0xFF8FB9FF;
    private final Screen parent;private final NameProtect module;
    private TextFieldWidget self,search,target,alias;
    private float scale=1,ox,oy,mx,my,scroll;
    private String selected="";
    private record Hit(float x,float y,float w,float h,Runnable action){boolean has(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private final List<Hit> hits=new ArrayList<>();
    public NameProtectScreen(Screen parent,NameProtect module){super(Text.literal("Name Protect"));this.parent=parent;this.module=module;}
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override protected void init(){
        self=field(24,105,732,"Your name",32,module.ownAlias());self.setChangedListener(module::ownAlias);
        search=field(24,179,260,"Search players...",32,"");search.setChangedListener(v->scroll=0);
        target=field(310,179,446,"Player account name",16,selected);target.setChangedListener(v->{selected=v;alias.setText(module.alias(v));});
        alias=field(310,242,446,"Their new name",32,module.alias(selected));alias.setChangedListener(v->module.alias(selected,v));
    }
    private TextFieldWidget field(int x,int y,int width,String hint,int max,String value){var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,width,26,Text.literal(hint)));f.setMaxLength(max);f.setPlaceholder(Text.literal(hint));f.setText(value);return f;}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        scale=Math.min(width*.94f/W,height*.92f/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();
        try(var ignored=NameProtect.unprotected()){
            Render2D.rect(c,0,0,width,height,0xB5080D15);c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);Fonts.beginRaw();Fonts.setMeasureScale(scale);
            try{
                Render2D.shadow(c,0,5,W,H,14,18,0x80000000);Render2D.roundRect(c,0,0,W,H,14,0xFF101721);Render2D.roundOutline(c,0,0,W,H,14,.7f,0x28FFFFFF);
                text(c,"NAME PROTECT",24,25,.62f,true,ACCENT);text(c,"A new name. Instantly.",24,53,1.25f,true,TEXT);
                button(c,module.isEnabled()?"Enabled":"Enable",572,22,94,29,module.isEnabled(),module::toggle);button(c,"Done",678,22,78,29,false,this::close);
                text(c,"YOUR DISPLAY NAME",24,89,.60f,true,MUTED);text(c,"Changes appear while you type.",24,146,.68f,false,MUTED);
                text(c,"PLAYERS",24,165,.61f,true,ACCENT);text(c,"ACCOUNT",310,165,.61f,true,MUTED);text(c,"DISPLAY NAME",310,228,.61f,true,MUTED);
                Render2D.roundRect(c,24,215,260,177,8,0xFF0A101A);
                var players=module.players().stream().filter(name->name.toLowerCase(Locale.ROOT).contains(search.getText().toLowerCase(Locale.ROOT))).toList();scroll=Math.clamp(scroll,0,Math.max(0,players.size()*28-169));
                Render2D.clip(c,24,215,284,392);
                try{for(int i=0;i<players.size();i++){
                    String name=players.get(i);float y=219+i*28-scroll;if(y+25<215||y>=392)continue;boolean chosen=name.equalsIgnoreCase(selected);
                    if(chosen||mx>=29&&mx<279&&my>=y&&my<y+25)Render2D.roundRect(c,29,y,250,25,5,chosen?0xFF283E5A:0xFF1C2A3B);
                    text(c,Fonts.trim(name,226,true,.75f),39,y+12,.75f,true,chosen?ACCENT:TEXT);
                    float top=Math.max(y,215),bottom=Math.min(y+25,392);hits.add(new Hit(29,top,250,bottom-top,()->target.setText(name)));
                }}finally{Render2D.unclip(c);}
                if(players.isEmpty())text(c,"Type an account name on the right.",38,245,.62f,false,MUTED);
                Render2D.roundRect(c,310,286,446,106,9,0xFF1B293A);text(c,"LIVE PREVIEW",326,304,.57f,true,ACCENT);
                String real=client.player==null?client.getSession().getUsername():client.player.getGameProfile().name();String other=selected.isBlank()?"OtherPlayer":selected;
                text(c,Fonts.trim(module.preview("<"+real+"> Hello, "+other+"!"),414,true,.92f),326,334,.92f,true,TEXT);
                text(c,Fonts.trim(module.preview(other+" joined the game"),414,false,.74f),326,366,.74f,false,MUTED);
                button(c,"Settings",24,406,94,23,false,()->{var gui=parent instanceof ClickGuiScreen p?p:new ClickGuiScreen();client.setScreen(gui);gui.openModuleOptions(module);});
                button(c,"Clear player alias",310,406,147,23,false,()->alias.setText(""));
                Fonts.drawRight(c,"Names update across chat, tab and labels",756,417,MUTED,false,.62f);
                super.render(c,(int)mx,(int)my,delta);
            }finally{Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
        }
    }
    private void text(DrawContext c,String text,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,text,x,y,color,bold,size);}
    private void button(DrawContext c,String label,float x,float y,float w,float h,boolean on,Runnable action){Render2D.roundRect(c,x,y,w,h,6,on?0xFF2C4A70:0xFF253349);Fonts.drawCentered(c,label,x+w/2,y+h/2,on?ACCENT:TEXT,true,.66f);hits.add(new Hit(x,y,w,h,action));}
    @Override public boolean mouseClicked(Click event,boolean doubled){float x=(float)(event.x()-ox)/scale,y=(float)(event.y()-oy)/scale;if(event.button()==0)for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).has(x,y)){setFocused(null);hits.get(i).action.run();return true;}return super.mouseClicked(new Click(x,y,new MouseInput(event.button(),event.modifiers())),doubled);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=ox+24*scale&&x<ox+284*scale&&y>=oy+215*scale&&y<oy+392*scale){scroll-=vertical*34;return true;}return false;}
    @Override public boolean keyPressed(KeyInput input){if(input.key()==GLFW.GLFW_KEY_ESCAPE){close();return true;}return super.keyPressed(input);}
    @Override public void close(){client.setScreen(parent);}
    @Override public void removed(){ConfigManager.saveAll();}
    @Override public boolean shouldPause(){return false;}
}
