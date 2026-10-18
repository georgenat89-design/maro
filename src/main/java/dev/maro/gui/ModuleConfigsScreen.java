package dev.maro.gui;

import com.google.gson.JsonObject;
import dev.maro.config.CommunityConfigs;
import dev.maro.config.ConfigManager;
import dev.maro.config.SharedModuleConfig;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.Icons;
import dev.maro.module.Module;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A public preset always belongs to one module, with a preview and a local undo. */
public final class ModuleConfigsScreen extends Screen {
    private static final float W=780,H=440;
    private static final int INK=0xFFF1F3F7,MUTED=0xFF98A3B6,ACCENT=0xFFAB90FF;
    private enum View { LIST,DETAIL,POST,SERVER }
    private record Hit(float x,float y,float w,float h,Runnable action){
        boolean contains(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private final Screen parent;
    private final CommunityConfigs api;
    private final List<Hit> hits=new ArrayList<>();
    private final Module module;
    private View view=View.LIST;
    private TextFieldWidget search,titleField,descriptionField,urlField;
    private String query="",notice="";
    private boolean top=true;
    private float scale=1,ox,oy,mx,my,scroll,scrollTarget;
    private long frame;
    private SharedModuleConfig.Preview cachedPreview;
    private boolean clipping;
    private long queryChanged;
    private int focus=-1;
    private CommunityConfigs.Post selected;
    private JsonObject posting,undo;
    private Module undoModule;
    private long deleteArmed;
    public ModuleConfigsScreen(Screen parent,Module module){this(parent,module,CommunityConfigs.get());}
    public ModuleConfigsScreen(Screen parent,Module module,CommunityConfigs api){
        super(Text.literal("Public module configs"));this.parent=parent;this.module=Objects.requireNonNull(module);this.api=api;
    }
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override protected void init(){widgets();later(api.ready(),v->{if(view==View.LIST&&!api.state().endpoint().isEmpty())refresh(false);});}
    private TextFieldWidget field(int x,int y,int w,String hint,int limit,String value){
        var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,24,Text.literal(hint)));
        f.setMaxLength(limit);f.setPlaceholder(Text.literal(hint));f.setText(value);return f;
    }
    private void widgets(){
        clearChildren();search=titleField=descriptionField=urlField=null;focus=-1;
        if(view==View.LIST){search=field(24,104,402,"Search this module's configs...",80,query);search.setChangedListener(s->{query=s;queryChanged=System.currentTimeMillis();});}
        if(view==View.POST){titleField=field(24,134,732,"Config title",80,"");descriptionField=field(24,194,732,"What works well with these settings?",600,"");}
        if(view==View.SERVER)urlField=field(24,160,732,"https://configs.example.com",200,api.state().endpoint());
    }
    private void show(View target){view=target;scroll=scrollTarget=0;notice="";cachedPreview=null;widgets();}
    private void refresh(boolean more){
        if(module==null||api.state().busy())return;
        notice="";queryChanged=0;if(!more)scroll=scrollTarget=0;
        later(api.browse(module.getName(),query,top,more),unused->{});
    }
    private <T>void later(CompletableFuture<T> future,Consumer<T> success){
        future.whenComplete((value,error)->{if(client!=null)client.execute(()->{
            if(error==null)success.accept(value);else notice=api.state().status();
        });});
    }
    private void open(CommunityConfigs.Post post){later(api.detail(post.id()),p->{selected=p;show(View.DETAIL);});}
    private void compose(){posting=SharedModuleConfig.capture(module);show(View.POST);}
    @Override public void tick(){if(view==View.LIST&&queryChanged>0&&System.currentTimeMillis()-queryChanged>=350&&!api.state().busy()&&!api.state().endpoint().isEmpty())refresh(false);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();float dt=frame==0?0:Math.min(.05f,(now-frame)/1e9f);frame=now;
        scroll+=(scrollTarget-scroll)*(1-(float)Math.exp(-22*dt));
        scale=Math.min(width*.94f/W,height*.93f/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();
        Render2D.rect(c,0,0,width,height,0xB0090C11);
        c.getMatrices().pushMatrix();c.getMatrices().translate(ox,oy);c.getMatrices().scale(scale,scale);Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.shadow(c,0,5,W,H,14,18,0x80000000);
            Render2D.roundRect(c,0,0,W,H,14,0xFF10141D);Render2D.roundOutline(c,0,0,W,H,14,.7f,0x25FFFFFF);
            text(c,"PUBLIC CONFIGS",24,24,.65f,true,ACCENT);
            text(c,Fonts.trim(module.getDisplayName(),400,true,1.3f),24,51,1.3f,true,INK);
            button(c,"Settings",440,22,96,28,false,parent instanceof ClickGuiScreen,()->{
                var gui=(ClickGuiScreen)parent;client.setScreen(gui);gui.openModuleContextSettings(module);
            });
            button(c,"Server",548,22,100,28,false,true,()->show(View.SERVER));
            button(c,"Close",660,22,96,28,false,true,this::close);
            String subtitle=switch(view){case LIST->"Find a setup you like. Preview it, then make it yours.";case DETAIL->"See what changes before you apply.";case POST->"Share your setup for this module.";case SERVER->"Choose where your community shares configs.";};
            text(c,subtitle,24,80,.74f,false,MUTED);
            if(view==View.LIST)list(c);else if(view==View.DETAIL)detail(c);else if(view==View.POST)post(c);else server(c);
            Render2D.line(c,24,402,756,402,.7f,0x1EFFFFFF);
            String status=notice.isEmpty()?api.state().status():notice;
            Render2D.circle(c,28,420,2.3f,api.state().endpoint().isEmpty()?MUTED:ACCENT);
            text(c,Fonts.trim((api.state().busy()?"Working…  ":"")+status,undo==null?710:576,false,.64f),39,420,.64f,false,MUTED);
            if(undo!=null)button(c,"Undo apply",632,408,124,24,false,true,this::undo);
            super.render(c,(int)mx,(int)my,delta);
            if(focus>=0&&focus<hits.size()){var hit=hits.get(focus);Render2D.roundOutline(c,hit.x,hit.y,hit.w,hit.h,6,1.2f,ACCENT);}
        }finally{Fonts.setMeasureScale(1);Fonts.endRaw();c.getMatrices().popMatrix();}
    }
    private void list(DrawContext c){
        Render2D.roundRect(c,438,102,164,28,7,0xFF1A2130);
        tab(c,"Top liked",440,104,90,24,top,()->{top=true;refresh(false);});
        tab(c,"New",538,104,62,24,!top,()->{top=false;refresh(false);});
        button(c,"Post config",610,104,146,24,true,!api.state().busy()&&!api.state().endpoint().isEmpty(),this::compose);
        var posts=api.state().posts().stream().filter(p->p.module().equals(module.getName())).toList();
        float total=posts.size()*76+12+(api.state().more()?38:0);clamp(total,244);
        Render2D.roundRect(c,24,142,732,244,10,0xFF0B1019);
        Render2D.roundOutline(c,24,142,732,244,10,.7f,0x18FFFFFF);
        clip(c,142,386);
        try{
            for(int i=0;i<posts.size();i++){
                var p=posts.get(i);float y=148+i*76-scroll;if(y+68<142||y>=386)continue;
                boolean hover=inside(34,y,712,68);Render2D.roundRect(c,34,y,712,68,8,hover?0xFF232A3A:0xFF171E2A);
                Render2D.roundOutline(c,34,y,712,68,8,.7f,hover?0x505F4C9B:0x12FFFFFF);
                Render2D.roundRect(c,44,y+19,29,29,8,i==0&&top?0xFF352A50:0xFF242D3B);
                Fonts.drawCentered(c,top?""+(i+1):"•",58.5f,y+33.5f,i==0&&top?ACCENT:MUTED,true,.78f);
                text(c,Fonts.trim(p.title(),468,true,.88f),86,y+16,.88f,true,INK);
                text(c,Fonts.trim(p.description().isBlank()?p.settings()+" settings":p.description(),468,false,.67f),86,y+34,.67f,false,MUTED);
                text(c,Fonts.trim(p.author()+"  ·  "+p.settings()+" settings  ·  "+p.minecraft(),468,false,.58f),86,y+53,.58f,false,0xFF7E8B9E);
                Render2D.roundRect(c,566,y+23,68,22,6,p.liked()?0xFF352A50:0xFF212A38);
                Fonts.drawCentered(c,p.likes()+" likes",600,y+34,p.liked()?ACCENT:MUTED,false,.61f);
                button(c,"Preview",646,y+21,96,28,false,!api.state().busy(),()->open(p));
            }
            if(api.state().more())button(c,"Load more",304,148+posts.size()*76-scroll,172,28,false,!api.state().busy(),()->refresh(true));
        }finally{unclip(c);}
        if(posts.isEmpty()){
            Render2D.roundRect(c,368,180,44,44,12,0xFF27213B);Icons.CONFIGS.draw(c,390,202,22,ACCENT,0);
            Fonts.drawCentered(c,api.state().endpoint().isEmpty()?"Join your community":"No configs yet",390,248,INK,true,1f);
            Fonts.drawCentered(c,api.state().endpoint().isEmpty()?"Connect a server to browse and share setups.":"Try a different search, or be the first to share.",390,276,MUTED,false,.72f);
            if(api.state().endpoint().isEmpty())button(c,"Connect server",308,306,164,28,true,true,()->show(View.SERVER));
        }
        if(total>244)bar(c,total,244,142);
    }
    private void detail(DrawContext c){
        button(c,"← Back",24,104,90,24,false,true,()->{show(View.LIST);refresh(false);});
        if(selected==null)return;
        text(c,Fonts.trim(selected.title(),620,true,.98f),132,116,.98f,true,INK);
        text(c,Fonts.trim("By "+selected.author()+"   ·   "+selected.likes()+" likes   ·   Minecraft "+selected.minecraft(),720,false,.67f),24,148,.67f,false,MUTED);
        SharedModuleConfig.Preview preview;
        try{preview=cachedPreview==null?(cachedPreview=SharedModuleConfig.preview(module,selected.config())):cachedPreview;}
        catch(RuntimeException invalid){text(c,"This config is incompatible: "+invalid.getMessage(),24,190,.75f,false,0xFFFFBCB2);return;}
        var lines=new ArrayList<String>();lines.add(preview.supported()+" supported settings · "+preview.changes().size()+" changes");
        if(!selected.description().isBlank())lines.add(selected.description());
        lines.addAll(preview.changes());if(preview.changes().isEmpty())lines.add("Your current settings already match.");
        if(preview.skipped()>0)lines.add(preview.skipped()+" unavailable or private settings will be skipped.");
        Render2D.roundRect(c,24,166,732,162,9,0xFF0B1019);Render2D.roundOutline(c,24,166,732,162,9,.7f,0x18FFFFFF);
        clamp(lines.size()*24+12,156);clip(c,168,324);
        try{for(int i=0;i<lines.size();i++){float y=182+i*24-scroll;if(y>=168&&y<324){if(i>0&&i%2==0)Render2D.roundRect(c,30,y-10,720,21,4,0x06FFFFFF);text(c,Fonts.trim(lines.get(i),704,false,.71f),39,y,.71f,false,i==0?ACCENT:MUTED);}}}
        finally{unclip(c);}
        if(lines.size()*24+12>156)bar(c,lines.size()*24+12,156,168);
        text(c,"Applies to this module only. You can undo it.",24,346,.67f,false,MUTED);
        button(c,preview.changes().isEmpty()?"Already applied":"Apply settings",24,366,164,28,true,!preview.changes().isEmpty(),()->{
            try{undo=SharedModuleConfig.apply(module,selected.config());undoModule=module;cachedPreview=null;ConfigManager.saveAll();notice="Applied "+selected.title()+" to "+module.getDisplayName();}
            catch(RuntimeException error){notice="Could not apply: "+error.getMessage();}
        });
        button(c,selected.liked()?"Unlike · "+selected.likes():"Like · "+selected.likes(),200,366,136,28,selected.liked(),!selected.own()&&!api.state().busy(),()->later(api.like(selected,!selected.liked()),p->selected=p));
        if(selected.own())button(c,System.currentTimeMillis()-deleteArmed<3000?"Confirm delete":"Delete post",620,366,136,28,false,!api.state().busy(),()->{
            if(System.currentTimeMillis()-deleteArmed<3000)later(api.delete(selected),v->{show(View.LIST);refresh(false);});else deleteArmed=System.currentTimeMillis();
        });
    }
    private void post(DrawContext c){
        text(c,"TITLE",24,118,.62f,true,MUTED);text(c,"DESCRIPTION",24,178,.62f,true,MUTED);
        int count=posting.getAsJsonObject("settings").size();
        text(c,count+" settings for "+module.getDisplayName(),24,252,.90f,true,INK);
        text(c,"Give your setup a clear name and explain what it is good for.",24,280,.72f,false,MUTED);
        text(c,"Only this module's shareable settings are posted.",24,302,.70f,false,MUTED);
        button(c,"Post config",24,354,164,32,true,!api.state().busy()&&titleField!=null&&!titleField.getText().isBlank(),()->
            later(api.publish(titleField.getText(),descriptionField.getText(),posting),p->{selected=p;show(View.DETAIL);notice="Config posted";}));
        button(c,"Cancel",200,354,106,32,false,true,()->show(View.LIST));
    }
    private void server(DrawContext c){
        text(c,"COMMUNITY SERVER URL",24,138,.65f,true,MUTED);
        text(c,"Paste the config server address from your community.",24,214,.78f,false,INK);
        text(c,"You can also host your own with the included server package.",24,243,.72f,false,MUTED);
        text(c,"Use HTTPS for public servers, or HTTP on your local network.",24,267,.67f,false,MUTED);
        button(c,"Connect",24,306,150,30,true,!api.state().busy(),()->{
            try{later(api.connect(urlField.getText()),v->{show(View.LIST);refresh(false);});}
            catch(RuntimeException error){notice=error.getMessage();}
        });
        button(c,"Back",186,306,100,30,false,true,()->show(View.LIST));
    }
    private void undo(){try{SharedModuleConfig.apply(undoModule,undo);cachedPreview=null;ConfigManager.saveAll();notice="Restored "+undoModule.getDisplayName();undo=null;undoModule=null;}catch(RuntimeException error){notice=error.getMessage();}}
    private void clamp(float total,float visible){float max=Math.max(0,total-visible);scrollTarget=Math.clamp(scrollTarget,0,max);scroll=Math.clamp(scroll,0,max);}
    private void clip(DrawContext c,float top,float bottom){clipping=true;Render2D.clip(c,24,(int)top,756,(int)bottom);}
    private void unclip(DrawContext c){clipping=false;Render2D.unclip(c);}
    private void bar(DrawContext c,float total,float visible,float y){float thumb=Math.max(20,visible*visible/total);Render2D.roundRect(c,751,y+scroll/(total-visible)*(visible-thumb),3,thumb,1.5f,0xFF516575);}
    private void text(DrawContext c,String value,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,value,x,y,color,bold,size);}
    private boolean inside(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void button(DrawContext c,String label,float x,float y,float w,float h,boolean primary,boolean enabled,Runnable action){
        Render2D.roundRect(c,x,y,w,h,7,!enabled?0xFF1C2330:primary?(inside(x,y,w,h)?0xFFBEA8FF:ACCENT):(inside(x,y,w,h)?0xFF343B50:0xFF252D3D));
        Fonts.drawCentered(c,label,x+w/2,y+h/2,!enabled?0xFF667587:primary?0xFF211737:INK,true,.68f);
        if(enabled){
            float hitTop=clipping?Math.max(142,y):y,hitBottom=clipping?Math.min(386,y+h):y+h;
            if(hitBottom>hitTop)hits.add(new Hit(x,hitTop,w,hitBottom-hitTop,action));
        }
    }
    private void tab(DrawContext c,String label,float x,float y,float w,float h,boolean active,Runnable action){
        if(active)Render2D.roundRect(c,x,y,w,h,5,0xFF3B2B58);
        Fonts.drawCentered(c,label,x+w/2,y+h/2,active?0xFFD0BDFF:MUTED,true,.64f);
        if(!api.state().busy())hits.add(new Hit(x,y,w,h,action));
    }
    @Override public boolean mouseClicked(Click event,boolean doubled){
        float x=(float)(event.x()-ox)/scale,y=(float)(event.y()-oy)/scale;
        if(event.button()==0)for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){setFocused(null);focus=-1;hits.get(i).action.run();return true;}
        return super.mouseClicked(new Click(x,y,new MouseInput(event.button(),event.modifiers())),doubled);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=ox+24*scale&&x<ox+756*scale&&y>=oy+142*scale&&y<oy+386*scale)scrollTarget-=vertical*42;return true;}
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(view==View.LIST)close();else show(View.LIST);return true;}
        if(input.key()==GLFW.GLFW_KEY_ENTER&&search!=null&&view==View.LIST){refresh(false);return true;}
        if(getFocused()!=null)return super.keyPressed(input);
        if(input.key()==GLFW.GLFW_KEY_TAB&&!hits.isEmpty()){focus=Math.floorMod(focus+((input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),hits.size());return true;}
        if(input.key()==GLFW.GLFW_KEY_ENTER&&focus>=0&&focus<hits.size()){hits.get(focus).action.run();return true;}
        return super.keyPressed(input);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
