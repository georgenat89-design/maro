package dev.maro.runtime.gui;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.runtime.gui.widgets.*;
import dev.maro.runtime.gui.widgets.input.WTextBox;
import dev.maro.runtime.gui.widgets.pressable.*;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import java.util.*;

/** Macro manager/editor rendered with Maro's own fonts and theme. */
public abstract class WindowScreen extends Screen {
    protected final GuiTheme theme;
    private final Screen parent;
    private final WWidget root=new WWidget();
    private final Map<WTextBox,TextFieldWidget> fields=new IdentityHashMap<>();
    private final List<Hit> hits=new ArrayList<>();
    private float scroll, contentHeight;
    private KeybindSetting listening;
    private record Hit(float x,float y,float w,float h,Runnable action) { boolean contains(double mx,double my){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;} }
    protected WindowScreen(GuiTheme theme,String title){super(Text.literal(title));this.theme=theme;parent=MinecraftClient.getInstance().currentScreen;}
    public <T extends WWidget> WWidget.Cell<T> add(T widget){return root.add(widget);}
    public abstract void initWidgets();
    @Override protected void init(){root.clear();fields.clear();initWidgets();}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        Theme.update();Render2D.setAlpha(1);Render2D.setScissor(null);
        ctx.fill(0,0,width,height,0xDD090B10);
        float w=Math.min(width-32,820),x=(width-w)/2,top=44,bottom=height-28;
        Fonts.drawCentered(ctx,title.getString(),width/2f,22,Theme.TEXT,true,1.1f);
        hits.clear();ctx.enableScissor((int)x-4,(int)top,(int)(x+w+4),(int)bottom);
        contentHeight=draw(ctx,root,x,top-scroll,w,mx,my)-top+scroll;
        ctx.disableScissor();
        Fonts.drawCentered(ctx,listening==null?"Scroll to see more - Esc to return":"Press a key (Delete clears it)",width/2f,height-16,Theme.TEXT_MUTED,false,.75f);
    }
    private float draw(DrawContext ctx,WWidget widget,float x,float y,float w,int mx,int my){
        if(widget instanceof GuiTheme.SettingsWidget settings){
            for(var section:settings.settings.sections())for(var setting:section.getSettings()){
                if(!setting.isVisible())continue;
                if(y>22&&y<height-20){
                    String value=setting instanceof SettingAdapters.ValueSetting text?text.editText():setting instanceof KeybindSetting key?key.getKeyName():String.valueOf(setting.get());
                    button(ctx,x,y,w,setting.getName()+": "+value,mx,my,()->edit(setting));
                }
                y+=26;
            }
            return y;
        }
        if(!widget.children.isEmpty()){
            if(widget.horizontal){float cw=(w-4*(widget.children.size()-1))/widget.children.size(),end=y;for(var child:widget.children){end=Math.max(end,draw(ctx,child,x,y,cw,mx,my));x+=cw+4;}return end;}
            for(var child:widget.children)y=draw(ctx,child,x,y,w,mx,my)+4;
            return y;
        }
        if(widget instanceof WTextBox text){
            var field=fields.computeIfAbsent(text,key->{var f=new TextFieldWidget(textRenderer,0,0,1,20,Text.literal("Search"));f.setMaxLength(1024);f.setText(key.text);f.setChangedListener(v->{key.text=v;if(key.action!=null)key.action.run();});addDrawableChild(f);return f;});
            field.setX((int)x);field.setY((int)y);field.setWidth(Math.max(20,(int)w));field.render(ctx,mx,my,0);return y+22;
        }
        if(widget instanceof WButton button){button(ctx,x,y,w,button.text,mx,my,()->{if(button.action!=null)button.action.run();});return y+22;}
        if(widget instanceof WCheckbox check){button(ctx,x,y,w,check.checked?"On":"Off",mx,my,()->{check.checked=!check.checked;if(check.action!=null)check.action.run();});return y+22;}
        if(!widget.text.isEmpty()){Fonts.draw(ctx,Fonts.trim(widget.text,w,false,.75f),x+3,y+6,widget.ink,false,.75f);return y+22;}
        return y+4;
    }
    private void button(DrawContext ctx,float x,float y,float w,String label,int mx,int my,Runnable action){
        boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+22;
        Render2D.roundRect(ctx,x,y,w,22,5,hover?Theme.CARD_HOVER:Theme.CARD);
        Fonts.draw(ctx,Fonts.trim(label,w-12,false,.78f),x+6,y+7,Theme.TEXT,false,.78f);
        hits.add(new Hit(x,y,w,22,action));
    }
    private void edit(Setting<?> setting){
        if(setting instanceof BooleanSetting b){b.set(!b.get());return;}
        if(setting instanceof ModeSetting m){m.cycle(1);return;}
        if(setting instanceof KeybindSetting k){listening=k;return;}
        if(setting instanceof SettingAdapters.ValueSetting v){client.setScreen(new dev.maro.gui.ValueEditorScreen(this,v));return;}
        client.setScreen(new ScalarEditorScreen(this,setting));
    }
    @Override public boolean mouseClicked(Click event,boolean doubled){
        if(event.button()==0&&event.y()>=44&&event.y()<height-28){for(var hit:List.copyOf(hits))if(hit.contains(event.x(),event.y())){hit.action.run();return true;}}
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(Math.max(0,contentHeight-(height-72)),scroll-(float)vertical*24));return true;}
    @Override public boolean keyPressed(KeyInput input){
        if(listening!=null){listening.set(input.key()==256||input.key()==261||input.key()==259?dev.maro.util.KeyUtil.NONE:input.key());listening=null;return true;}
        return super.keyPressed(input);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
