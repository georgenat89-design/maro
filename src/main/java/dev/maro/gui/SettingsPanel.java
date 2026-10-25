package dev.maro.gui;

import dev.maro.config.ConfigManager;
import dev.maro.config.ModuleFavorites;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GlyphNames;
import dev.maro.gui.render.Glyphs;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.Scroll;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Module;
import dev.maro.module.impl.visuals.ViewModel;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An independent settings editor with fixed header/footer and its own clipped scroll view. */
final class SettingsPanel {
    static final float WIDTH=398,PREVIEW_WIDTH=170;
    private final ClickGuiScreen gui;
    private final SettingsList list=new SettingsList();
    private final Scroll scroll=new Scroll();
    private final Map<Module,List<SettingSection>> sections=new HashMap<>();
    private Module open,drawn;
    private String pendingSection;
    private boolean previewSelected;
    private float[] previewTabPlace,settingsTabPlace;
    private float[] scrollBounds;
    SettingsPanel(ClickGuiScreen gui){this.gui=gui;}
    Module module(){return open;}
    Module drawn(){return drawn;}
    void open(Module m){
        if(m!=open){gui.visualPreview().hidden();previewTabPlace=settingsTabPlace=null;scroll.reset();pendingSection=null;previewSelected=hasPreview(m);}
        open=drawn=m;
    }
    void close(){
        if(open!=null&&gui.listening==open.getBind())gui.listening=null;
        open=drawn=null;previewTabPlace=settingsTabPlace=null;previewSelected=false;gui.visualPreview().hidden();
    }
    void scrollTo(String title){pendingSection=title;}
    void onScroll(double amount){scroll.scroll(amount);}
    static boolean hasPreview(Module m){return VisualPreview.supports(m);}
    boolean previewSelected(){return open!=null&&previewSelected&&hasPreview(open);}
    void selectPreview(boolean selected){
        if(open==null||!hasPreview(open))return;
        if(previewSelected!=selected)scroll.reset();previewSelected=selected;if(!selected)gui.visualPreview().hidden();
    }
    float[] previewTabPlace(){return open==null||previewTabPlace==null?null:previewTabPlace.clone();}
    float[] settingsTabPlace(){return open==null||settingsTabPlace==null?null:settingsTabPlace.clone();}
    float[] scrollBounds(){return scrollBounds==null?null:scrollBounds.clone();}
    private List<SettingSection> visibleSections(Module m){
        var all=sections.computeIfAbsent(m,key->{
            List<SettingSection> result=new ArrayList<>();
            for(var original:key.getSettingSections()){
                var copy=new SettingSection(original.getTitle()) {
                    @Override public String getTitle(){return original.getTitle();}
                };
                for(var s:original.getSettings())if(!(key instanceof ViewModel view&&s==view.handPlacementAction()))copy.add(s);
                if(!copy.getSettings().isEmpty())result.add(copy);
            }
            return result;
        });
        return all.stream().filter(section->section.getSettings().stream().anyMatch(Setting::isVisible)).toList();
    }
    void render(DrawContext ctx,float x,float y,float w,float h,float r){
        previewTabPlace=settingsTabPlace=null;
        scrollBounds=null;
        gui.hit(x,y,w,h,(button,mx,my)->{});
        Render2D.roundGradientV(ctx,x,y,w,h,r,Theme.panelBg(),ColorUtil.shade(Theme.panelBg(),-.025f));
        Render2D.roundOutline(ctx,x,y,w,h,r,Render2D.px(),Theme.BORDER);
        Module m=open;
        if(m==null){
            Glyphs.draw(ctx,GlyphNames.SETTINGS,x+w/2,y+h/2-15,29,Theme.TEXT_MUTED);
            Fonts.drawCentered(ctx,"Select a module",x+w/2,y+h/2+25,Theme.TEXT_DIM,false,1.02f);
            Fonts.drawCentered(ctx,"Its settings will appear here",x+w/2,y+h/2+43,Theme.TEXT_MUTED,false,.78f);
            return;
        }
        Glyphs.draw(ctx,ModuleIcons.of(m),x+25,y+25,18,m.isEnabled()?Theme.accent():Theme.TEXT_DIM);
        Fonts.drawV(ctx,Fonts.trim(m.getDisplayName(),w-174,true,1.13f),x+48,y+25,Theme.TEXT,true,1.13f);
        if(gui.hovered(x+47,y+12,w-173,25))gui.tooltip(m.getDisplayName());
        float controls=x+w-112,cy=y+25;
        gui.moduleConfigsButton(ctx,m,controls-7,cy-9,18);
        boolean starred=ModuleFavorites.contains(m),starHover=gui.hovered(controls+18,cy-9,18,18);
        Glyphs.draw(ctx,GlyphNames.STAR,controls+27,cy,12,starred?Theme.accent():starHover?Theme.TEXT_DIM:Theme.TEXT_MUTED);
        gui.hit(controls+18,cy-9,18,18,(button,mx,my)->{if(button==0){ModuleFavorites.toggle(m);Sounds.click();}});
        gui.place("settings-favorite",controls+27,cy);
        Widgets.toggle(ctx,controls+45,cy-5.5f,23,11,Anims.of(m,"on",m.isEnabled()),gui.hovered(controls+42,cy-10,29,20)?1:0);
        gui.hit(controls+42,cy-10,29,20,(button,mx,my)->{if(button==0){m.toggle();Sounds.toggle(m.isEnabled());}});
        gui.place("settings-enabled",controls+56,cy);
        Glyphs.draw(ctx,GlyphNames.X,x+w-20,cy,10,Theme.TEXT_MUTED);
        gui.hit(x+w-29,cy-9,18,18,(button,mx,my)->{if(button==0){gui.closeSettings();Sounds.click();}});
        var about=Fonts.wrap(m.getDescription(),w-28,false,.79f);
        for(int i=0;i<Math.min(2,about.size());i++)Fonts.drawV(ctx,Fonts.trim(about.get(i),w-28,false,.79f),x+14,y+49+i*13,Theme.TEXT_MUTED,false,.79f);
        if(about.size()>2&&gui.hovered(x+14,y+40,w-28,30))gui.tooltip(m.getDescription());
        Render2D.rect(ctx,x+14,y+79,w-28,Render2D.px(),Theme.BORDER);
        float next=y+89;
        if(m instanceof ViewModel view){
            var action=view.handPlacementAction();
            Widgets.button(gui,ctx,action,x+14,next,w-28,27,"Edit hand placement",Widgets.Style.SECONDARY,null,action::press);
            gui.place(action,x+w/2,next+13.5f);next+=37;
        }
        if(hasPreview(m)){
            float gap=5,tabW=(w-28-gap)/2;
            tab(ctx,"Settings",x+14,next,tabW,24,!previewSelected,()->gui.selectPreviewTab(false));
            tab(ctx,"Preview",x+14+tabW+gap,next,tabW,24,previewSelected,()->gui.selectPreviewTab(true));
            settingsTabPlace=new float[]{x+14+tabW/2,next+12};previewTabPlace=new float[]{x+14+tabW+gap+tabW/2,next+12};next+=34;
        }
        Fonts.drawV(ctx,"Keybind",x+16,next+9,Theme.TEXT_MUTED,false,.8f);
        float chipW=Widgets.bindChipWidth(gui,m.getBind());
        Widgets.bindChip(gui,ctx,m.getBind(),x+w-16-chipW,next+9,1);
        gui.place(m.getBind(),x+w-16-chipW/2,next+9);next+=28;
        float footerY=y+h-45,top=next,viewH=Math.max(30,footerY-top-8),off=scroll.update(gui.hasPointerDrag());
        scrollBounds=new float[]{x+1,top,w-2,viewH};
        float cx=x+14,cw=w-28,contentY=top-off;
        gui.scrollHit(x+1,top,w-2,viewH,scroll::scroll);
        gui.pushClip(x+1,top,w-2,viewH);
        List<SettingSection> visible=visibleSections(m);
        float contentH;
        if(visible.isEmpty()){
            Fonts.drawV(ctx,"No additional settings",cx+2,contentY+15,Theme.TEXT_MUTED,false,.82f);contentH=36;
        }else contentH=list.render(gui,ctx,cx,contentY,cw,visible,true);
        gui.popClip();scroll.setBounds(contentH+8,viewH);
        if(pendingSection!=null){for(var section:visible)if(section.getTitle().equalsIgnoreCase(pendingSection)){Float at=list.sectionTop(section);if(at!=null)scroll.scrollTo(at);}pendingSection=null;}
        scroll.drawBar(gui,ctx,x+w-4,top,viewH);
        Render2D.rect(ctx,x+14,footerY,w-28,Render2D.px(),Theme.BORDER);
        Widgets.button(gui,ctx,ClickGuiScreen.MODULE_RESET,x+14,footerY+10,125,24,"Reset settings",Widgets.Style.SECONDARY,null,()->{
            m.getSettings().forEach(Setting::reset);gui.listening=null;dev.maro.gui.widget.Dropdown.close();ConfigManager.saveAll();Sounds.click();
        });
        gui.place(ClickGuiScreen.MODULE_RESET,x+76.5f,footerY+22);
        Fonts.drawRight(ctx,Fonts.trim(ConfigManager.getCurrent(),w-166,false,.7f),x+w-16,footerY+22,Theme.TEXT_MUTED,false,.7f);
    }
    void renderPreview(DrawContext ctx,float x,float y,float w,float h){if(open!=null&&open==drawn&&previewSelected())gui.visualPreview().draw(ctx,open,x,y,w,h);}
    private void tab(DrawContext ctx,String label,float x,float y,float w,float h,boolean selected,Runnable action){
        boolean over=gui.hovered(x,y,w,h);
        Render2D.roundRect(ctx,x,y,w,h,6,selected?Theme.accent(0x20):over?Theme.CARD_HOVER:Theme.INPUT);
        Render2D.roundOutline(ctx,x,y,w,h,6,Render2D.px(),selected?Theme.accent(0x70):Theme.BORDER);
        Fonts.drawCentered(ctx,label,x+w/2,y+h/2,selected?Theme.accent():Theme.TEXT_DIM,false,.83f);
        gui.hit(x,y,w,h,(button,mx,my)->{if(button==0){action.run();Sounds.click();}});
    }
}
