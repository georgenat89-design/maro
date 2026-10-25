package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.MaroWordmark;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.textures.PackIcons;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** A compact visual workspace with the collection, local library and appearance together. */
public final class GameplayTexturesScreen extends Screen {
    private static final float W=800,H=442;
    private static final int INK=0xFFF0F2F5,MUTED=0xFF969EAB,TEAL=0xFF79D8C4;
    private final Screen parent;
    private final TexturePacks module;
    private record Hit(float x,float y,float w,float h,Runnable action) {
        boolean contains(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private final List<Hit> hits=new ArrayList<>();
    private float scale=1,ox,oy,mx,my;
    private int focus=-1,localCount;
    public GameplayTexturesScreen(Screen parent,TexturePacks module) {
        super(Text.literal("Gameplay Textures"));this.parent=parent;this.module=module;
    }
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override protected void init(){module.opened();localCount=module.scan().size();module.refreshCatalog(false);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        Theme.update();module.pump();scale=Math.min(width*.92f/W,height*.86f/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();
        Render2D.rect(c,0,0,width,height,0xA9080A0D);
        c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.shadow(c,0,5,W,H,14,18,0x80000000);
            Render2D.roundRect(c,0,0,W,H,14,0xFF101216);Render2D.roundOutline(c,0,0,W,H,14,.7f,0x28FFFFFF);
            sidebar(c);
            text(c,"TEXTURES & VISUAL EFFECTS",190,31,.66f,true,MUTED);
            text(c,"Gameplay Textures",190,58,1.38f,true,INK);
            text(c,"Your packs, block styles and visual effects.",190,82,.78f,false,MUTED);
            button(c,"Done",708,22,68,28,false,this::close);
            collection(c);
            tile(c,190,276,"Modrinth","Discover resource packs","Search & install",0xFFA7BCFF,1,()->client.setScreen(new BetterTexturesScreen(this,module.modrinth())));
            tile(c,390,276,"Your library",localCount+" installed packs","Manage loadout",0xFFC3A6EA,2,()->client.setScreen(new TexturePacksScreen(this,module)));
            tile(c,590,276,"Appearance","Potato, blur, bloom & light","Customize",0xFFE6C68A,3,()->client.setScreen(new BetterLooksScreen(this,module.looks())));
            Render2D.line(c,190,404,776,404,.7f,0x19FFFFFF);
            Render2D.circle(c,194,423,2.5f,module.isEnabled()?TEAL:MUTED);
            text(c,module.picked().size()+" active in your loadout",205,423,.69f,false,MUTED);
            Fonts.drawRight(c,"Your usual packs return when switched off",776,423,MUTED,false,.65f);
            if(focus>=0&&focus<hits.size()){var hit=hits.get(focus);Render2D.roundOutline(c,hit.x,hit.y,hit.w,hit.h,7,1.2f,TEAL);}
        }finally{Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
    }
    private void sidebar(DrawContext c){
        Render2D.roundRect(c,9,9,161,H-18,10,0xFF171A20);
        MaroWordmark.draw(c,23,22,18,INK,MaroWordmark.Style.THEMED,false);
        text(c,"MAKE IT YOURS",23,67,.55f,true,0xFF727D8E);
        Render2D.roundRect(c,19,83,141,32,7,0xFF263136);icon(c,30,99,0,TEAL);text(c,"Overview",48,99,.75f,true,TEAL);
        nav(c,"PacksMC",134,0,()->client.setScreen(new PacksMcScreen(this,module)));
        nav(c,"Modrinth",172,1,()->client.setScreen(new BetterTexturesScreen(this,module.modrinth())));
        nav(c,"Appearance",210,3,()->client.setScreen(new BetterLooksScreen(this,module.looks())));
        Render2D.line(c,23,278,156,278,.7f,0x19FFFFFF);
        text(c,"ONE COLLECTION",23,296,.55f,true,0xFF727D8E);text(c,"50 packs. Your choice.",23,316,.69f,false,MUTED);
        nav(c,"Your library",350,2,()->client.setScreen(new TexturePacksScreen(this,module)));
        boolean hover=inside(19,382,141,35);Render2D.roundRect(c,19,382,141,35,7,hover?0xFF2A3036:0xFF20252B);
        text(c,module.isEnabled()?"Textures on":"Textures off",30,399,.69f,true,INK);
        Render2D.roundRect(c,124,392,25,14,7,module.isEnabled()?0xFF467D70:0xFF41454E);
        Render2D.circle(c,module.isEnabled()?142:131,399,4.5f,module.isEnabled()?0xFFB5F5DD:0xFFA7AAB2);
        hits.add(new Hit(19,382,141,35,module::toggle));
    }
    private void nav(DrawContext c,String label,float y,int kind,Runnable action){
        if(inside(19,y-15,141,30))Render2D.roundRect(c,19,y-15,141,30,7,0xFF242930);
        icon(c,30,y,kind,MUTED);text(c,label,48,y,.73f,false,MUTED);hits.add(new Hit(19,y-15,141,30,action));
    }
    private void collection(DrawContext c){
        Render2D.roundRect(c,190,105,586,153,10,0xFF1B2728);
        Render2D.roundOutline(c,190,105,586,153,10,.7f,0xFF304747);
        text(c,"THE PACKSMC COLLECTION",208,126,.60f,true,TEAL);
        text(c,"Find your next look.",208,156,1.23f,true,INK);
        text(c,"50 trending packs, with a preview",208,180,.73f,false,0xFFB5C8C3);
        text(c,"before you make it yours.",208,196,.73f,false,0xFFB5C8C3);
        button(c,"Browse packs  →",208,217,126,27,true,()->client.setScreen(new PacksMcScreen(this,module)));
        var packs=module.catalog().packs();
        for(int i=2;i>=0;i--){
            float x=519+i*9,y=128-i*7;Render2D.roundRect(c,x,y,225,126,7,0xFF111A1D);Render2D.roundOutline(c,x,y,225,126,7,.8f,0xFF526C69);
            if(i<packs.size()){
                var p=packs.get(i);var id=PackIcons.preview(p.id()+"_"+Integer.toHexString(p.preview().hashCode()),p.preview());
                if(id!=null)c.drawTexture(RenderPipelines.GUI_TEXTURED,id,Math.round(x+4),Math.round(y+2),0,0,217,122,512,288,512,288);
                else {Render2D.roundGradientV(c,x+4,y+4,217,118,4,0xFF2C4544,0xFF1A262E);icon(c,x+112,y+60,0,TEAL);}
            }
        }
        if(!packs.isEmpty()){
            Render2D.roundRect(c,526,231,211,17,4,0xE811191C);
            text(c,Fonts.trim(packs.getFirst().name(),195,false,.60f),534,239,.60f,false,0xFFDAE7E2);
        }
    }
    private void tile(DrawContext c,float x,float y,String title,String subtitle,String action,int color,int kind,Runnable run){
        boolean hover=inside(x,y,186,112);Render2D.roundRect(c,x,y,186,112,9,hover?0xFF22272F:0xFF1A1E25);
        Render2D.roundOutline(c,x,y,186,112,9,.7f,hover?ColorUtil.withAlpha(color,110):0x20FFFFFF);
        Render2D.roundRect(c,x+13,y+12,27,27,7,ColorUtil.withAlpha(color,20));icon(c,x+26.5f,y+25.5f,kind,color);
        text(c,title,x+49,y+26,.85f,true,INK);text(c,subtitle,x+14,y+58,.69f,false,MUTED);
        text(c,action,x+14,y+91,.69f,true,color);Fonts.drawRight(c,"→",x+172,y+91,color,true,.82f);
        hits.add(new Hit(x,y,186,112,run));
    }
    private void icon(DrawContext c,float x,float y,int kind,int color){
        if(kind==0){for(int i=0;i<4;i++)Render2D.roundRect(c,x-6+i%2*7,y-6+i/2*7,5,5,1.2f,color);}
        else if(kind==1){Render2D.ring(c,x,y,6,1.4f,color);Render2D.line(c,x+4,y+4,x+8,y+8,1.4f,color);}
        else if(kind==2){
            // Two upright books and one leaning volume, centered in the same icon box.
            Render2D.roundOutline(c,x-7,y-6,4,12,1,1.2f,color);
            Render2D.roundOutline(c,x-2,y-6,4,12,1,1.2f,color);
            Render2D.line(c,x+3,y-5,x+6,y-6,1.2f,color);Render2D.line(c,x+6,y-6,x+9,y+5,1.2f,color);
            Render2D.line(c,x+9,y+5,x+6,y+6,1.2f,color);Render2D.line(c,x+6,y+6,x+3,y-5,1.2f,color);
        }
        else {for(int i=0;i<3;i++){float yy=y-6+i*6;Render2D.line(c,x-7,yy,x+7,yy,1.2f,color);Render2D.circle(c,x+(i==1?-3:3),yy,2.2f,color);}}
    }
    private void text(DrawContext c,String s,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,s,x,y,color,bold,size);}
    private boolean inside(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void button(DrawContext c,String text,float x,float y,float w,float h,boolean primary,Runnable action){
        Render2D.roundRect(c,x,y,w,h,6,primary?(inside(x,y,w,h)?0xFFB5EFDD:0xFF8DDDC7):(inside(x,y,w,h)?0xFF343A45:0xFF252A33));
        Fonts.drawCentered(c,text,x+w/2,y+h/2,primary?0xFF132A23:INK,true,.69f);hits.add(new Hit(x,y,w,h,action));
    }
    @Override public boolean mouseClicked(Click event,boolean doubled){
        if(event.button()!=0)return true;float x=(float)(event.x()-ox)/scale,y=(float)(event.y()-oy)/scale;
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){focus=-1;Sounds.click();hits.get(i).action.run();break;}return true;
    }
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE)close();
        else if(input.key()==GLFW.GLFW_KEY_TAB&&!hits.isEmpty())focus=Math.floorMod(focus+((input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),hits.size());
        else if((input.key()==GLFW.GLFW_KEY_ENTER||input.key()==GLFW.GLFW_KEY_SPACE)&&focus>=0&&focus<hits.size()){Sounds.click();hits.get(focus).action.run();}
        return true;
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public void removed(){ConfigManager.saveAll();}
    @Override public boolean shouldPause(){return false;}
}
