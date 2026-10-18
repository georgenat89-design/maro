package dev.maro.gui.page;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.ModuleIcons;
import dev.maro.gui.render.BootAssets;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.Scroll;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.ModuleSectionHeaders;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.ModuleSections;
import dev.maro.setting.ActionSetting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Module grid for a category (or search results when {@code category == null}) plus a per-module settings view. */
public class ModulesPage extends Page {
    private static final float CARD_H = 40f;
    private static final float GAP = 8f;
    private int gridPage, gridPages=1,requestedPage=-1;
    private float gridStride;
    private int gridCellsPerPage;
    private long gridFlipAt;
    private int gridFlipDirection;
    private final Map<Integer,float[]> paginationPlaces=new HashMap<>();
    public float[] paginationPlace(int direction){return paginationPlaces.get(direction);}
    public float gridScrollOffset(){return scroll.get();}
    public int gridPage(){return gridPage;}
    public int gridPages(){return gridPages;}
    public void changeGridPage(int delta){int next=Math.clamp(gridPage+delta,0,gridPages-1);if(next==gridPage)return;gridFlipDirection=Integer.signum(delta);gridFlipAt=System.nanoTime();gridPage=next;requestedPage=gridPage;}
    public float listWidth(float width){return open==null?width:width*.43f;}

    private final Category category;
    private final Animation view = new Animation(13f, 0f);
    private final Scroll settingsScroll = new Scroll();
    private final SettingsList settingsList = new SettingsList();
    public SettingsList settingsControls(){return settingsList;}
    private final Map<Module, List<SettingSection>> sections = new HashMap<>();
    private Module open;
    private Module shown;

    // the open module's settings: a card per category, or one category's settings
    private final Animation sectionView = new Animation(15f, 0f);
    private final Scroll homeScroll = new Scroll();
    private SettingSection section;
    private SettingSection shownSection;
    private long homeSince;
    private boolean snapTabs;
    private SettingSection resetArmed;
    private long resetArmedAt;
    private boolean expandedInspector;

    public static float panelListWidth(float width) { return width; }
    public boolean inspectorExpanded() { return expandedInspector; }

    public ModulesPage(ClickGuiScreen gui, Category category) {
        super(gui);
        this.category = category;
    }

    private List<Module> modules() {
        List<Module> list = category == null ? ModuleManager.search(gui.getSearch().getText()) : ModuleManager.byCategory(category);
        if(ClickGuiScreen.cardPanelStyle()&&gui.cardPanel().hudOnly()&&gui.getSearch().getText().isEmpty())list=ModuleManager.all().stream().filter(m->!m.hiddenInGui()).sorted(java.util.Comparator.comparing(Module::getDisplayName,String.CASE_INSENSITIVE_ORDER)).toList();
        return list.stream().filter(m->gui.antiCheatSectionUnlocked()||m.getCategory()!=Category.ANTI_CHEAT_OFF)
            .filter(m->!ClickGuiScreen.cardPanelStyle()||gui.cardPanel().matches(m))
            .sorted(java.util.Comparator.comparing(Module::getDisplayName,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public void resetGridScroll(){scroll.reset();gridPage=0;requestedPage=0;}
    public int visibleModuleCount(){return modules().size();}
    public List<Module> visibleModules(){return modules();}
    private final Map<Module,float[]> panelCardPlaces=new HashMap<>();
    public float[] panelCardPlace(Module module){return panelCardPlaces.get(module);}
    private final Map<ModuleSections.Section,float[]> sectionPlaces=new HashMap<>();
    public float[] sectionPlace(ModuleSections.Section section){return sectionPlaces.get(section);}

    public void openSettings(Module m) {
        if(m instanceof dev.maro.module.impl.player.AutoBuilder builder){
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.builder.BuilderControlScreen(gui,builder));return;
        }
        net.minecraft.client.gui.screen.Screen own = m.panel(gui);
        if (own != null) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(own);
            return;
        }
        openOptions(m);
    }

    public void openOptions(Module m) {
        gui.cardPanel().previewSelection(m);
        if(ClickGuiScreen.cardPanelStyle()){
            expandedInspector=true;
            if(dev.maro.gui.VisualPreview.supports(m))gui.cardPanel().expandPreview(m);
        }
        settingsList.resetPage();
        open = m;
        section = null;
        shownSection = null;
        sectionView.snap(0f);
        settingsScroll.reset();
        homeScroll.reset();
        homeSince = System.currentTimeMillis();
        Sounds.click();
    }
    public boolean showingSettings(Module module){return open==module;}
    public boolean showingGrid(){return ClickGuiScreen.cardPanelStyle()?!expandedInspector:open==null;}
    public void showGrid(){expandedInspector=false;open=null;shown=null;section=null;shownSection=null;view.snap(0);sectionView.snap(0);scroll.reset();gui.cardPanel().hidePreview();}

    @Override
    public void onOpen() {
        super.onOpen();
        showGrid();
    }

    @Override
    public boolean onEscape() {
        if (ClickGuiScreen.cardPanelStyle()) {
            if(open!=null){showGrid();return true;}
            return false;
        }
        if (open == null) return false;
        back();
        return true;
    }

    @Override
    public void onScroll(double amount) {
        if (open == null) { if(ClickGuiScreen.cardPanelStyle())scroll.scroll(amount);else if(amount!=0)changeGridPage(amount<0?1:-1); }
        // Settings use explicit pages; wheel input cannot hide controls.
    }

    @Override
    public boolean typeToSearch() {
        return ClickGuiScreen.cardPanelStyle() || open == null;
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        if (category == Category.ANTI_CHEAT_OFF && !gui.antiCheatSectionUnlocked()) {
            gui.renderAntiCheatPrompt(ctx, x, y, w, h);
            return;
        }
        if (ClickGuiScreen.cardPanelStyle()) {
            renderWorkspace(ctx,x,y,w,h);
            return;
        }
        float v = view.update(open != null ? 1f : 0f);
        if (open != null) shown = open;
        float prev = Render2D.getAlpha();
        if (v < 0.999f) {
            Render2D.setAlpha(prev * (1 - v));
            gui.setInteractive(open == null);
            renderGrid(ctx, x - v * 24f, y, w, h);
        }
        if (v > 0.001f && shown != null) {
            Render2D.setAlpha(prev * v);
            gui.setInteractive(open != null);
            renderSettings(ctx, shown, x + (1 - v) * 24f, y, w, h);
        }
        if (v == 0f) shown = null;
        Render2D.setAlpha(prev);
        gui.setInteractive(true);
    }

    // ---- grid ---------------------------------------------------------------------------

    /** Panel keeps the list in place while its independently scrolling inspector changes. */
    private void renderWorkspace(DrawContext ctx,float x,float y,float w,float h) {
        float listW=listWidth(w),ix=expandedInspector?x:x+listW+12,iw=expandedInspector?w:w-listW-12;
        if (!expandedInspector) {
            float header=gui.cardPanel().moduleHeaderHeight();
            gui.scrollHit(x,y+header,listW,h-header,scroll::scroll);
            renderGrid(ctx,x,y+header,listW,h-header);
        } else { panelCardPlaces.clear(); sectionPlaces.clear(); }
        if(open==null)return;
        Render2D.roundRect(ctx,ix,y,iw,h,6,0x58242B2F);
        Render2D.roundOutline(ctx,ix,y,iw,h,6,Render2D.px(),0x284B555F);
        shown=open;
        var m=open;
        ModuleIcons.of(m).draw(ctx,ix+16,y+17,12,Theme.TEXT_DIM,0);
        Fonts.drawV(ctx,Fonts.trim(m.getDisplayName(),iw-190,true,.84f),ix+31,y+17,Theme.TEXT,true,.84f);
        float ex=ix+iw-21,tx=ex-39;
        Icons.LAYOUT.draw(ctx,ex,y+17,8,gui.hovered(ex-9,y+8,18,18)?Theme.TEXT:Theme.TEXT_MUTED,0);
        gui.hit(ex-9,y+8,18,18,(button,mx,my)->{if(button==0)showGrid();});
        if(gui.hovered(ex-9,y+8,18,18))gui.tooltip("Back to modules");
        Widgets.toggle(ctx,tx,y+13,22,11,Anims.of(m,"inspectorOn",m.isEnabled()),0);
        gui.hit(tx-3,y+8,28,20,(button,mx,my)->{if(button==0){m.toggle();Sounds.toggle(m.isEnabled());}});
        Widgets.bindChip(gui,ctx,m.getBind(),ix+iw-115,y+17,.9f);
        gui.moduleConfigsButton(ctx,m,ix+iw-32,y+28,20);
        Icons.SETTINGS.draw(ctx,ix+iw-46,y+38,8,Theme.TEXT_MUTED,0);
        gui.hit(ix+iw-56,y+28,20,20,(button,mx,my)->{if(button==0)openSettings(m);});
        if(gui.hovered(ix+iw-56,y+28,20,20))gui.tooltip("Open full settings editor");
        if(gui.hovered(ix+10,y+5,iw-100,23))gui.tooltip(m.getDescription());
        if(dev.maro.gui.VisualPreview.supports(m)&&!gui.cardPanel().previewExpanded())
            Widgets.button(gui,ctx,"show-preview",ix+iw-212,y+7,92,18,"Show preview",Widgets.Style.SECONDARY,Icons.PLAY,()->gui.cardPanel().expandPreview(m));
        float tabsY=y+28;
        var visible=visibleSections(m);
        if(section!=null&&!visible.contains(section))section=null;
        SettingSection active=section!=null?section:visible.isEmpty()?null:visible.getFirst();
        if(active!=null)tabs(ctx,visible,active,ix+10,tabsY,iw-115);
        float bodyY=tabsY+(active==null?0:31),bodyH=Math.max(96,y+h-8-bodyY);
        gui.pushClip(ix+5,bodyY,iw-10,bodyH);
        settingsList.renderPaged(gui,ctx,ix+10,bodyY,iw-20,bodyH,active==null?List.of():List.of(active),false);
        gui.popClip();
        if(active!=null){
            boolean armed=resetArmed==active&&System.currentTimeMillis()-resetArmedAt<2500;
            Widgets.button(gui,ctx,active,ix+iw-85,y+29,30,18,"",armed?Widgets.Style.DANGER:Widgets.Style.GHOST,Icons.REFRESH,()->{
                if(resetArmed==active&&System.currentTimeMillis()-resetArmedAt<2500){
                    for(var setting:active.getSettings())if(setting.isVisible()&&!(setting instanceof ButtonSetting)&&!(setting instanceof ActionSetting))setting.reset();
                    resetArmed=null;
                }else{resetArmed=active;resetArmedAt=System.currentTimeMillis();}
            });
        }
    }

    private void renderGrid(DrawContext ctx, float x, float y, float w, float h) {
        List<Module> mods = modules();
        panelCardPlaces.clear();
        sectionPlaces.clear();
        float top = y;
        boolean panel=ClickGuiScreen.cardPanelStyle();
        if (category == null&&!panel) {
            String q = gui.getSearch().getText();
            String label = mods.size() + (mods.size() == 1 ? " result" : " results") + " for ";
            Fonts.draw(ctx, label, x + 2, y + 2, Theme.TEXT_MUTED, false, 0.78f);
            Fonts.draw(ctx, "\"" + q + "\"", x + 2 + Fonts.width(label, false, 0.78f), y + 2, Theme.accent(), false, 0.78f);
            top += 14;
        }
        if (mods.isEmpty()) { gridPage=0;gridPages=1;scroll.reset();requestedPage=-1;
            empty(ctx, x, top, w, h - (top - y));
            return;
        }

        int cols = w >= (panel ? 280 : 300) ? 2 : 1;
        float textRatio=dev.maro.config.ClientSettings.menuTextSize.getFloat()/90f;
        float gap=panel?4:GAP,cardH=panel?26*Math.max(.9f,textRatio):CARD_H;
        float cw=(w-gap*(cols-1))/cols;
        float viewH=h-(top-y),footer=panel?0:28;
        int rows=Math.max(1,(int)((viewH-footer+gap)/(cardH+gap)));
        if(panel)rows=Math.min(8,rows);
        int perPage=rows*cols;
        gridPages=Math.max(1,(mods.size()+perPage-1)/perPage);
        gridPage=Math.clamp(gridPage,0,gridPages-1);
        float offset=0,viewport=viewH-footer;
        int start=gridPage*perPage,end=Math.min(mods.size(),start+perPage);
        if(panel){
            float stride=Math.max(1,Math.min(viewH,rows*(cardH+gap)));
            float anchor=gridStride>0&&gridCellsPerPage>0?scroll.getTarget()/gridStride*gridCellsPerPage/perPage*stride:gridPage*stride;
            scroll.setBounds(gridPages*stride,stride);
            if(requestedPage>=0){scroll.jumpTo(requestedPage*stride);requestedPage=-1;}
            else if(gridStride!=stride||gridCellsPerPage!=perPage)scroll.scrollTo(anchor);
            gridStride=stride;gridCellsPerPage=perPage;offset=scroll.update(gui.hasPointerDrag());viewport=stride;
            gridPage=Math.clamp(Math.round(scroll.getTarget()/stride),0,gridPages-1);
            start=Math.max(0,(int)(offset/stride))*perPage;end=Math.min(mods.size(),start+perPage*2);
        }
        gui.pushClip(x,top,w,Math.max(1,viewport));
        float flip=panel?1-Math.clamp((System.nanoTime()-gridFlipAt)/220_000_000f,0,1):0;
        for(int i=start;i<end;i++){
            int local=panel?i%perPage:i-start;
            float cx=x+(local%cols)*(cw+gap)+gridFlipDirection*18*flip*flip,cy=top+(local/cols)*(cardH+gap)+(panel?(i/perPage)*gridStride-offset:0);
            if(cy+cardH<top||cy>=top+viewport)continue;
            if(panel)panelCard(ctx,mods.get(i),cx,cy,cw,cardH,1-.25f*flip);
            else card(ctx,mods.get(i),cx,cy,cw,cardH,1);
        }
        gui.popClip();
        if(!panel)renderPagination(ctx,x+w-100,y+h-24);
    }

    public void renderPagination(DrawContext ctx,float x,float y){
        paginationPlaces.clear();
        for(int direction:new int[]{-1,1}){
            float bx=x+(direction<0?0:72);boolean available=direction<0?gridPage>0:gridPage<gridPages-1;
            Render2D.roundRect(ctx,bx,y,24,20,5,available?0x553D4A59:0x223D4A59);
            Fonts.drawCentered(ctx,direction<0?"<":">",bx+12,y+10,available?Theme.TEXT:Theme.TEXT_MUTED,false,.8f);
            if(available)gui.hit(bx,y,24,20,(button,mx,my)->{if(button==0)changeGridPage(direction);});
            paginationPlaces.put(direction,new float[]{bx+12,y+10});
            if(gui.hovered(bx,y,24,20))gui.tooltip(direction<0?"Previous page · Page Up":"Next page · Page Down");
        }
        Fonts.drawCentered(ctx,(gridPage+1)+" / "+gridPages,x+48,y+10,Theme.TEXT_DIM,false,.65f);
    }

    private void panelCard(DrawContext ctx,Module module,float x,float y,float w,float h,float intro){
        float alpha=Render2D.getAlpha();Render2D.setAlpha(alpha*intro);y+=(1-intro)*7;
        boolean over=gui.hovered(x,y,w,h),selected=open==module;
        float hover=Anims.of(module,"panelHover",over),on=Anims.of(module,"panelOn",module.isEnabled());
        Render2D.roundRect(ctx,x,y,w,h,4,selected?0xB74A545A:ColorUtil.lerp(0x392C3338,0x8D3B454C,hover));
        if(selected)Render2D.roundRect(ctx,x+1,y+4,1.5f,h-8,.75f,Theme.TEXT_DIM);
        ModuleIcons.of(module).draw(ctx,x+14,y+h/2,12,selected?Theme.TEXT:Theme.TEXT_MUTED,0);
        String label=module.getDisplayName();float font=10.3f*dev.maro.config.ClientSettings.menuTextSize.getFloat()/90f;
        while(label.length()>1&&BootAssets.labelWidth(label,font,false)>w-82)label=label.substring(0,label.length()-1);
        if(!label.equals(module.getDisplayName()))label=label.substring(0,Math.max(1,label.length()-1))+"…";
        BootAssets.label(ctx,label,x+31,y+(h-font*.73f)/2,font,selected?Theme.TEXT:Theme.TEXT_DIM,false);
        if(over)gui.tooltip(module.getDisplayName()+" · "+module.getDescription());
        gui.hit(x,y,w,h,(button,mx,my)->{
            if(button==0)openOptions(module);
            else if(button==1){module.toggle();Sounds.toggle(module.isEnabled());}
            else if(button==2)gui.listening=module.getBind();
        });
        float tx=x+w-27,ty=y+h/2-4.5f;
        Render2D.roundRect(ctx,tx,ty,18,9,4.5f,ColorUtil.lerp(0xFF242D36,0xFF7C868F,on));
        Render2D.circle(ctx,tx+4.5f+9*on,ty+4.5f,3.1f,ColorUtil.lerp(Theme.KNOB_OFF,Theme.TEXT,on));
        gui.hit(tx-3,ty-4,22,16,(button,mx,my)->{if(button==0||button==1){module.toggle();Sounds.toggle(module.isEnabled());}});
        float starX=x+w-42,starY=y+h/2;
        boolean starred=dev.maro.config.ModuleFavorites.contains(module);
        for(int i=0;i<10;i++){
            double a=-Math.PI/2+i*Math.PI/5,b=-Math.PI/2+(i+1)*Math.PI/5;
            float r=i%2==0?5:2.3f,r2=i%2==0?2.3f:5;
            Render2D.line(ctx,starX+(float)Math.cos(a)*r,starY+(float)Math.sin(a)*r,starX+(float)Math.cos(b)*r2,starY+(float)Math.sin(b)*r2,.8f,starred?0xFF60A5FA:Theme.TEXT_MUTED);
        }
        gui.hit(starX-7,starY-9,14,18,(button,mx,my)->{if(button==0)dev.maro.config.ModuleFavorites.toggle(module);else if(button==1){module.toggle();Sounds.toggle(module.isEnabled());}});
        if(gui.hovered(starX-7,starY-9,14,18))gui.tooltip(starred?"Remove favorite":"Add favorite");
        panelCardPlaces.put(module,new float[]{x+w*.4f,y+h/2,tx+9,ty+4.5f,starX,starY});
        Render2D.setAlpha(alpha);
    }

    private void card(DrawContext ctx, Module m, float x, float y, float w, float h, float intro) {
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * intro);
        y += (1 - intro) * 10f;

        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(m, "hover", hovered);
        float on = Anims.of(m, "on", m.isEnabled());
        float r = Theme.radius();

        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        // On: a flat tint of the accent and a short bar of it down the left edge.
        if (on > 0.01f) Render2D.roundRect(ctx, x, y, w, h, r, Theme.accent(Math.round(0x1E * on)));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, 0xFF34323F, hv), Theme.accent(0x58), on));
        if (on > 0.01f) Render2D.roundRect(ctx, x + 1.5f, y + 8, 2f, h - 16, 1f, Theme.accent(Math.round(0xFF * on)));

        float nx = x + 11;
        float toggleX = x + w - 32;
        boolean hasSettings = !m.getSettings().isEmpty();
        float dotsX = toggleX - 13;

        // The complete row follows the same settings/toggle actions in both layouts.
        gui.hit(x, y, w, h, (button, mx, my) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                openSettings(m);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                m.toggle();Sounds.toggle(m.isEnabled());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                gui.listening = m.getBind();
            }
        });

        // keybind chip, right-aligned next to the controls: only with a key bound, or to bind one on hover
        float chipRight = hasSettings ? dotsX - 9 : toggleX - 7;
        float chipX = chipRight;
        if (m.getBind().isBound() || gui.listening == m.getBind() || hovered) {
            chipX = chipRight - Widgets.bindChipWidth(gui, m.getBind());
            Widgets.bindChip(gui, ctx, m.getBind(), chipX, y + h / 2f, 1f);
        }

        // name + description
        float textMax = chipX - nx - 8;
        String name = Fonts.trim(m.getDisplayName(), textMax - (m.isExperimental() ? 12 : 0), false, 0.95f);
        Fonts.draw(ctx, name, nx, y + 9f, ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, on), false, 0.95f);
        if (m.isExperimental()) {
            float wx = nx + Fonts.width(name, false, 0.95f) + 7;
            Icons.WARNING.draw(ctx, wx, y + 12.5f, 7.5f, Theme.RED, 0);
            if (gui.hovered(wx - 5, y + 7.5f, 10, 10)) gui.tooltip("Experimental - may be unstable or detectable");
        }
        String desc = m.getDescription();
        String shown = Fonts.trim(desc, textMax, false, 0.7f);
        Fonts.draw(ctx, shown, nx, y + 22.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv * 0.6f), false, 0.7f);
        if (!shown.equals(desc) && gui.hovered(nx, y + 20, textMax, 10)) gui.tooltip(desc);

        // settings button
        if (hasSettings) {
            float dy = y + h / 2f;
            boolean dh = gui.hovered(dotsX - 7, dy - 7, 14, 14);
            float dhv = Anims.of(m, "dots", dh);
            if (dhv > 0.01f) Render2D.roundRect(ctx, dotsX - 7, dy - 7, 14, 14, 4, ColorUtil.withAlpha(0xFF2A2938, Math.round(0xFF * dhv)));
            Icons.DOTS.draw(ctx, dotsX, dy, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, Math.max(dhv, hv * 0.5f)), dhv);
            gui.hit(dotsX - 7, dy - 7, 14, 14, (button, mx, my) -> { if(button==0)openSettings(m);else if(button==1){m.toggle();Sounds.toggle(m.isEnabled());} });
            if (dh) gui.tooltip("Open settings");
        }

        Widgets.toggle(ctx, toggleX, y + h / 2f - 5.5f, 22, 11, on, hv);
        gui.hit(toggleX-3,y+h/2-10,28,20,(button,mx,my)->{if(button==0||button==1){m.toggle();Sounds.toggle(m.isEnabled());}});
        Render2D.setAlpha(prev);
    }

    private void empty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 14;
        float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 1.5f;
        Render2D.circle(ctx, cx, cy + bob, 20, Theme.CARD);
        Render2D.ring(ctx, cx, cy + bob, 20, 1f, Theme.BORDER);
        if (Theme.glow()) Render2D.shadow(ctx, cx - 20, cy - 20 + bob, 40, 40, 20, 10, Theme.accent(0x18));
        Icons.Icon icon = category == null ? Icons.SEARCH : category.getIcon();
        icon.draw(ctx, cx, cy + bob, 15, Theme.accent(), 0);
        boolean search = category == null;
        boolean filtered=ClickGuiScreen.cardPanelStyle()&&gui.cardPanel().hasFilters();
        Fonts.drawCentered(ctx, filtered ? "No matching modules" : search ? "No results" : "No modules in this category", cx, cy + 32, Theme.TEXT, true, 0.95f);
        Fonts.drawCentered(ctx, filtered ? "Your filters hide the modules here" : search ? "Try a different search term" : "Choose another category from the menu", cx, cy + 43, Theme.TEXT_MUTED, false, 0.75f);
        if(filtered)Widgets.button(gui,ctx,this,cx-44,cy+58,88,23,"Clear filters",Widgets.Style.SECONDARY,null,()->gui.cardPanel().clearFilters());
    }

    // ---- settings view ------------------------------------------------------------------
    //
    // A module with a few settings shows them straight away. A bigger one opens on a card per
    // category (Look, Colors, Placement, ...) saying what is inside; clicking a card opens just
    // that category, with tabs along the top to hop between categories and a back arrow home.

    private static final float HEADER_H = 34f;
    private static final float SECTION_CARD_H = 48f;
    private static final float TAB_H = 24f;
    /** Modules with more settings than this, spread over two or more categories, get cards. */
    private static final int CARD_THRESHOLD = 6;

    private void back() {
        if (section != null) {
            section = null;
            homeSince = System.currentTimeMillis();
            Sounds.click();
        } else {
            open = null;
        }
    }

    private void openSection(SettingSection s) {
        if (section == null) snapTabs = true;
        section = s;
        settingsScroll.reset();
        settingsList.resetPage();
        resetArmed = null;
        Sounds.click();
    }

    /** Opens the open module's category called {@code title}, if it has one. */
    public void openSection(String title) {
        if (open == null) return;
        for (SettingSection s : visibleSections(open)) {
            if (s.getTitle().equalsIgnoreCase(title)) {
                openSection(s);
                return;
            }
        }
    }

    private List<SettingSection> visibleSections(Module m) {
        List<SettingSection> result = new ArrayList<>();
        for (SettingSection s : sections.computeIfAbsent(m, Module::getSettingSections)) {
            if (visibleCount(s) > 0) result.add(s);
        }
        return result;
    }

    private static int visibleCount(SettingSection s) {
        int n = 0;
        for (Setting<?> setting : s.getSettings()) if (setting.isVisible()) n++;
        return n;
    }

    private static boolean usesCards(List<SettingSection> visible) {
        if (visible.size() < 2) return false;
        int total = 0;
        for (SettingSection s : visible) total += visibleCount(s);
        return total > CARD_THRESHOLD;
    }

    private void renderSettings(DrawContext ctx, Module m, float x, float y, float w, float h) {
        boolean base = open != null;
        List<SettingSection> visible = visibleSections(m);
        boolean cards = usesCards(visible);
        if (section != null && (!cards || !visible.contains(section))) section = null;
        if (!cards) {
            sectionView.snap(0f);
            shownSection = null;
        }

        header(ctx, m, x, y, w);

        float ly = y + HEADER_H + 7, lh = h - HEADER_H - 7;
        if(dev.maro.gui.VisualPreview.supports(m) && w >= 330 && lh >= 130){
            float previewW=Math.min(148,w*.32f);
            gui.visualPreview().draw(ctx,m,x,ly,previewW,lh);
            x+=previewW+12;w-=previewW+12;
        }
        if (visible.isEmpty()) {
            Fonts.drawCentered(ctx, "Nothing to set up", x + w / 2f, ly + 30, Theme.TEXT_DIM, false, 0.9f);
            Fonts.drawCentered(ctx, "This module just works when it is on", x + w / 2f, ly + 42, Theme.TEXT_MUTED, false, 0.72f);
            return;
        }
        if (!cards) {
            settingsList(ctx, visible, visible.size() > 1, x, ly, w, lh);
            return;
        }

        float v = sectionView.update(section != null ? 1f : 0f);
        if (section != null) shownSection = section;
        float prev = Render2D.getAlpha();
        if (v < 0.999f) {
            Render2D.setAlpha(prev * (1 - v));
            gui.setInteractive(base && section == null);
            renderHome(ctx, visible, x - v * 18f, ly, w, lh);
        }
        if (v > 0.001f && shownSection != null) {
            Render2D.setAlpha(prev * v);
            gui.setInteractive(base && section != null);
            renderSection(ctx, visible, shownSection, x + (1 - v) * 18f, ly, w, lh);
        }
        if (v == 0f) shownSection = null;
        Render2D.setAlpha(prev);
        gui.setInteractive(base);
    }

    private void header(DrawContext ctx, Module m, float x, float y, float w) {
        float r = Theme.radius();
        float hh = HEADER_H;
        Render2D.roundRect(ctx, x, y + 1, w - 6, hh, r, Theme.CARD);
        Render2D.roundOutline(ctx, x, y + 1, w - 6, hh, r, 1f, Theme.BORDER);
        Widgets.button(gui, ctx, this, x + 6, y + 7, 22, 22, "", Widgets.Style.SECONDARY, Icons.BACK, this::back);
        if (gui.hovered(x + 6, y + 7, 22, 22)) gui.tooltip(section != null ? "Back to categories (Esc)" : "Back to modules (Esc)");

        // big toggle, then the keybind chip to its left
        float on = Anims.of(m, "on", m.isEnabled());
        float tx = x + w - 6 - 38, ty = y + 1 + hh / 2f - 6.5f;
        boolean th = gui.hovered(tx - 4, ty - 4, 34, 21);
        Widgets.toggle(ctx, tx, ty, 26, 13, on, Anims.of(m, "bigToggle", th));
        gui.hit(tx - 4, ty - 4, 34, 21, (button, mx, my) -> {
            m.toggle();
            Sounds.toggle(m.isEnabled());
        });
        String bindLabel = gui.listening == m.getBind() ? "Press a key" : m.getBind().getKeyName();
        float chipW = Fonts.width(bindLabel, false, 0.72f) + 9;
        gui.moduleConfigsButton(ctx,m,tx-30,y+1+hh/2f-10,20f);
        float chipX = tx - 40 - chipW;
        Widgets.bindChip(gui, ctx, m.getBind(), chipX, y + 1 + hh / 2f, 1f);
        Fonts.drawRight(ctx, "KEY", chipX - 5, y + 1 + hh / 2f, Theme.TEXT_MUTED, true, 0.6f);

        // name, with the open category after it, and the description below
        float nx = x + 36, maxW = chipX - 26 - nx;
        float nameW = Fonts.width(m.getDisplayName(), true, 1.05f);
        Fonts.draw(ctx, Fonts.trim(m.getDisplayName(), maxW, true, 1.05f), nx, y + 8.5f, Theme.TEXT, true, 1.05f);
        float sv = sectionView.get();
        if (shownSection != null && sv > 0.01f && nameW + 30 < maxW) {
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * sv);
            float cx = nx + nameW + 7;
            Icons.CHEVRON_RIGHT.draw(ctx, cx, y + 12.5f, 6f, Theme.TEXT_MUTED, 0);
            Fonts.draw(ctx, Fonts.trim(shownSection.getTitle(), maxW - nameW - 18, false, 0.92f), cx + 7, y + 9f, Theme.accent(), false, 0.92f);
            Render2D.setAlpha(prev);
        }
        Fonts.draw(ctx, Fonts.trim(m.getDescription(), maxW, false, 0.72f), nx, y + 21f, Theme.TEXT_MUTED, false, 0.72f);
    }

    private void settingsList(DrawContext ctx, List<SettingSection> list, boolean labels, float x, float y, float w, float h) {
        gui.pushClip(x - 6, y, w + 12, h);
        settingsList.renderPaged(gui, ctx, x, y, w - 6, h,list,labels);
        gui.popClip();
    }

    // ---- category cards -----------------------------------------------------------------

    private void renderHome(DrawContext ctx, List<SettingSection> visible, float x, float y, float w, float h) {
        int cols = w >= 330 ? 2 : 1;
        float cw = (w - 6 - GAP * (cols - 1)) / cols;
        int rows = (visible.size() + cols - 1) / cols;
        float hintH = 16;
        homeScroll.setBounds(rows * (SECTION_CARD_H + GAP) - GAP + 2 + hintH, h);
        float off = homeScroll.update(gui.hasPointerDrag());

        gui.pushClip(x - 6, y, w + 12, h);
        for (int i = 0; i < visible.size(); i++) {
            float cx = x + (i % cols) * (cw + GAP);
            float cy = y + 1 + (i / cols) * (SECTION_CARD_H + GAP) - off;
            if (!gui.isVisible(cy - 6, SECTION_CARD_H + 12)) continue;
            sectionCard(ctx, visible.get(i), i, cx, cy, cw, SECTION_CARD_H);
        }
        float hy = y + 1 + rows * (SECTION_CARD_H + GAP) - off + 3;
        Fonts.drawCentered(ctx, "Pick a category to change its settings", x + (w - 6) / 2f, hy + 4, Theme.TEXT_MUTED, false, 0.66f);
        gui.popClip();
        homeScroll.drawBar(gui, ctx, x + w - 2, y, h);
    }

    private void sectionCard(DrawContext ctx, SettingSection s, int index, float x, float y, float w, float h) {
        float t = (System.currentTimeMillis() - homeSince) / 1000f - Math.min(index, 12) * 0.035f;
        float intro = homeScroll.isMoving()?1:dev.maro.util.Easing.outCubic(Math.max(0f, Math.min(1f, t / 0.28f)));
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * intro);
        y += (1 - intro) * 8f;

        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(s, "card", hovered);
        float r = Theme.radius();
        if (hv > 0.01f && Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 6f, Theme.accent(Math.round(0x22 * hv)));
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        if (hv > 0.01f) Render2D.roundRect(ctx, x, y, w, h, r, Theme.accent(Math.round(0x16 * hv)), 0x00000000, 0x00000000, Theme.accent2(Math.round(0x08 * hv)));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(0x70), hv));

        // icon tile
        float ts = 26, tx = x + 10, ty = y + (h - ts) / 2f;
        Render2D.roundRect(ctx, tx, ty, ts, ts, Math.min(7f, r + 1), Theme.accent(0x1C + Math.round(0x22 * hv)));
        Render2D.roundOutline(ctx, tx, ty, ts, ts, Math.min(7f, r + 1), 1f, Theme.accent(0x38 + Math.round(0x40 * hv)));
        iconFor(s.getTitle()).draw(ctx, tx + ts / 2f, ty + ts / 2f, 12f, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, hv * 0.6f), hv);

        // title, what is inside, and a count
        float nx = tx + ts + 10, maxW = x + w - 22 - nx;
        Fonts.draw(ctx, Fonts.trim(s.getTitle(), maxW, false, 0.95f), nx, y + 8.5f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.6f + hv * 0.4f), false, 0.95f);
        StringBuilder names = new StringBuilder();
        int count = 0, toggles = 0, toggled = 0;
        List<Integer> colors = new ArrayList<>();
        for (Setting<?> setting : s.getSettings()) {
            if (!setting.isVisible()) continue;
            if (count++ > 0) names.append(", ");
            names.append(setting.getName());
            if (setting instanceof BooleanSetting b) {
                toggles++;
                if (b.get()) toggled++;
            } else if (setting instanceof ColorSetting c && colors.size() < 5) colors.add(c.get());
        }
        String inside = names.toString();
        String shownInside = Fonts.trim(inside, maxW, false, 0.68f);
        Fonts.draw(ctx, shownInside, nx, y + 20.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), false, 0.68f);

        String meta = count + (count == 1 ? " setting" : " settings") + (toggles > 0 ? "  •  " + toggled + "/" + toggles + " on" : "");
        Fonts.draw(ctx, meta, nx, y + 32f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), hv * 0.7f), false, 0.62f);
        float sx = nx + Fonts.width(meta, false, 0.62f) + 8;
        for (int c : colors) {
            if (sx + 7 > x + w - 22) break;
            Render2D.circle(ctx, sx + 3, y + 35f, 3.2f, c | 0xFF000000);
            Render2D.ring(ctx, sx + 3, y + 35f, 3.2f, 0.8f, 0x40FFFFFF);
            sx += 9;
        }

        Icons.CHEVRON_RIGHT.draw(ctx, x + w - 13, y + h / 2f, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), hv), hv);
        gui.hit(x, y, w, h, (button, mx, my) -> openSection(s));
        if (hovered && !shownInside.equals(inside) && gui.hovered(nx, y + 18, maxW, 10)) gui.tooltip(inside);
        Render2D.setAlpha(prev);
    }

    /** An icon that fits a category, by the words in its title. */
    private static Icons.Icon iconFor(String title) {
        String t = title.toLowerCase(java.util.Locale.ROOT);
        if (has(t, "color", "colour", "palette", "tint", "theme")) return Icons.THEME;
        if (has(t, "anim", "motion", "swing", "transition", "inspect")) return Icons.PLAY;
        if (has(t, "placement", "layout", "position", "fit", "size", "hand", "preset", "shape", "outline", "image", "png")) return Icons.LAYOUT;
        if (has(t, "time", "timing", "delay", "break", "speed", "pace", "lyrics")) return Icons.CLOCK;
        if (has(t, "marker", "region", "rtp", "map", "route", "chunk", "spot", "where")) return Icons.PIN;
        if (has(t, "key", "bind", "control", "input")) return Icons.KEY;
        if (has(t, "stop", "safety", "pause", "alarm", "response", "found", "alert", "protect", "equipment")) return Icons.SHIELD;
        if (has(t, "target", "aim", "entit", "player", "highlight", "who", "companion", "follow")) return Icons.COMBAT;
        if (has(t, "look", "appearance", "style", "visual", "render", "overlay", "hud", "world", "display", "accessor", "camera", "view", "light", "correction")) return Icons.VISUALS;
        if (has(t, "action", "behav", "mining", "tunnel", "tool", "scan", "what", "block", "ore", "food", "eat", "sound", "audio")) return Icons.BOLT;
        if (has(t, "advanced", "extra", "more")) return Icons.MISC;
        return Icons.SETTINGS;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    // ---- one category -------------------------------------------------------------------

    private void renderSection(DrawContext ctx, List<SettingSection> visible, SettingSection s, float x, float y, float w, float h) {
        float rowW = w - 6;

        // reset, with a second click to confirm
        boolean armed = resetArmed == s && System.currentTimeMillis() - resetArmedAt < 2500;
        String resetLabel = armed ? "Sure?" : "Reset";
        float rw = Widgets.buttonWidth(resetLabel, Icons.REFRESH);
        Widgets.button(gui, ctx, s, x + rowW - rw, y, rw, TAB_H, resetLabel, armed ? Widgets.Style.DANGER : Widgets.Style.GHOST, Icons.REFRESH, () -> {
            if (resetArmed == s && System.currentTimeMillis() - resetArmedAt < 2500) {
                for (Setting<?> setting : s.getSettings()) {
                    if (!setting.isVisible() || setting instanceof ButtonSetting || setting instanceof ActionSetting) continue;
                    setting.reset();
                }
                resetArmed = null;
            } else {
                resetArmed = s;
                resetArmedAt = System.currentTimeMillis();
            }
        });
        if (gui.hovered(x + rowW - rw, y, rw, TAB_H)) gui.tooltip(armed ? "Click again to reset " + s.getTitle() : "Put this category back to its defaults");

        tabs(ctx, visible, s, x, y, rowW - rw - 8);

        float ly = y + TAB_H + 8;
        settingsList(ctx, List.of(s), false, x, ly, w, h - TAB_H - 8);
    }

    /** One chip per category, the open one lit, or a pager when they do not fit. */
    private void tabs(DrawContext ctx, List<SettingSection> visible, SettingSection current, float x, float y, float maxW) {
        float scale = 0.82f, pad = 16, gap = 4;
        float[] widths = new float[visible.size()];
        float total = -gap;
        for (int i = 0; i < visible.size(); i++) {
            widths[i] = Fonts.width(visible.get(i).getTitle(), false, scale) + pad;
            total += widths[i] + gap;
        }
        int index = Math.max(0, visible.indexOf(current));
        float cy = y + TAB_H / 2f;
        float r = Math.min(TAB_H / 2f, Theme.radius());

        if (total > maxW) {
            // pager: < Title 2/5 >
            float bw = TAB_H;
            Widgets.button(gui, ctx, "tabPrev", x, y, bw, TAB_H, "", Widgets.Style.SECONDARY, Icons.BACK,
                    () -> openSection(visible.get((index - 1 + visible.size()) % visible.size())));
            float px = x + bw + 4, pw = Math.min(maxW - bw * 2 - 8, 170);
            Render2D.roundGradientH(ctx, px, y, pw, TAB_H, r, Theme.accent(), Theme.accent2());
            Fonts.drawCentered(ctx, Fonts.trim(current.getTitle(), pw - 30, false, scale), px + pw / 2f - 8, cy, 0xFFFFFFFF, false, scale);
            Fonts.drawRight(ctx, (index + 1) + "/" + visible.size(), px + pw - 6, cy, 0xD0FFFFFF, false, 0.62f);
            Widgets.button(gui, ctx, "tabNext", px + pw + 4, y, bw, TAB_H, "", Widgets.Style.SECONDARY, Icons.CHEVRON_RIGHT,
                    () -> openSection(visible.get((index + 1) % visible.size())));
            return;
        }

        Render2D.roundRect(ctx, x, y, total + 4, TAB_H, r, 0xFF15141D);
        Render2D.roundOutline(ctx, x, y, total + 4, TAB_H, r, 1f, Theme.BORDER);
        float sx = x + 2, selX = 0;
        for (int i = 0; i < index; i++) sx += widths[i] + gap;
        selX = sx - x;
        Object owner = shown != null ? shown : this;
        if (snapTabs) {
            Anims.snap(owner, "tabX", selX);
            Anims.snap(owner, "tabW", widths[index]);
            snapTabs = false;
        }
        float ax = Anims.of(owner, "tabX", selX, 18f), aw = Anims.of(owner, "tabW", widths[index], 18f);
        Render2D.roundGradientH(ctx, x + ax, y + 2, aw, TAB_H - 4, Math.max(1f, r - 1), Theme.accent(), Theme.accent2());

        sx = x + 2;
        for (int i = 0; i < visible.size(); i++) {
            SettingSection tab = visible.get(i);
            boolean sel = i == index;
            boolean hov = gui.hovered(sx, y, widths[i], TAB_H);
            float t = Anims.of(tab, "tab", sel ? 1f : hov ? 0.5f : 0f);
            Fonts.drawCentered(ctx, tab.getTitle(), sx + widths[i] / 2f, cy, ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t), false, scale);
            if (!sel) gui.hit(sx, y, widths[i], TAB_H, (button, mx, my) -> openSection(tab));
            sx += widths[i] + gap;
        }
    }
}
