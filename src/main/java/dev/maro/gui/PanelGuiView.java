package dev.maro.gui;

import dev.maro.config.ClientSettings;
import dev.maro.gui.hud.HudElement;
import dev.maro.gui.page.ModulesPage;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.*;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Animation;
import dev.maro.util.Sounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Tabbed module list and settings workspace, sharing the menu's live input transform. */
public final class PanelGuiView {
    private static final Object WINDOW=new Object();
    private static float offsetX,offsetY;
    private final ClickGuiScreen gui;
    private final Scroll clientScroll=new Scroll();
    private int appearancePage;
    private final Map<String,float[]> places=new HashMap<>();
    private boolean enabledOnly,boundOnly,hudOnly,clientControls,starredOnly,newOnly,entitiesOnly,motionOnly;
    private float moduleHeaderHeight=26;
    public float moduleHeaderHeight(){return moduleHeaderHeight;}
    private float resizeStart=75;
    private final Animation resizeMotion=new Animation(24,1);
    private String lastQuery="";
    private float[] bounds;
    private Module previewModule;
    private float[] previewBounds;
    public float[] previewBounds(){return previewBounds;}
    public void previewSelection(Module module){if(previewModule!=null)previewModule=VisualPreview.supports(module)?module:null;}
    public void expandPreview(Module module){previewModule=module;gui.focused=null;gui.listening=null;}
    public boolean previewExpanded(){return previewModule!=null;}
    public void hidePreview(){previewModule=null;}
    PanelGuiView(ClickGuiScreen gui){this.gui=gui;dev.maro.config.ModuleDiscovery.initialize();}
    public boolean matches(Module m){return (!newOnly||dev.maro.config.ModuleDiscovery.contains(m))&&(!entitiesOnly||ModuleIcons.entities(m))&&(!motionOnly||ModuleIcons.motion(m))&&(!starredOnly||dev.maro.config.ModuleFavorites.contains(m))&&(!enabledOnly||m.isEnabled())&&(!boundOnly||m.getBind().isBound())&&(!hudOnly||m instanceof HudElement);}
    public boolean enabledOnly(){return enabledOnly;}
    public boolean boundOnly(){return boundOnly;}
    public boolean hudOnly(){return hudOnly;}
    public boolean clientControls(){return clientControls;}
    public boolean hasFilters(){return enabledOnly||boundOnly||hudOnly||starredOnly||newOnly||entitiesOnly||motionOnly;}
    public void clearFilters(){enabledOnly=false;boundOnly=false;hudOnly=false;starredOnly=false;newOnly=entitiesOnly=motionOnly=false;gui.getSearch().clear();resetGrid();}
    private void showModules(int category){
        clearFilters();gui.openPage(category);
        if(gui.currentPage() instanceof ModulesPage page)page.showGrid();
    }
    public float[] place(String name){return places.get(name);}
    public float[] bounds(){return bounds;}
    public void pageOpened(){clientControls=false;hudOnly=false;previewModule=null;clientScroll.reset();}
    public boolean onEscape(){if(previewModule!=null){previewModule=null;return true;}if(clientControls||hudOnly){clientControls=false;hudOnly=false;return true;}return false;}
    private void remember(String key,float x,float y,float w,float h){places.put(key,new float[]{x+w/2,y+h/2});}

    float[] render(DrawContext ctx,int width,int height,float alpha){
        places.clear();
        previewBounds=null;
        float w=width-24,h=height-16,bx=12,by=8,dockW=0,dockH=0;
        boolean sidePreview=previewModule!=null;
        if(previewModule!=null){
            dockW=Math.clamp(width*.30f,132,240);
            dockH=Math.min(300,h*.83f);
            if(sidePreview)w-=dockW+12;else{by+=dockH+12;h-=dockH+12;}
        }
        if(!ClickGuiScreen.spatialStyle()&&!gui.worldControls()){
            double[] a=gui.screenPose().unproject(0,0),b=gui.screenPose().unproject(gui.width,gui.height);
            offsetX=Math.clamp(offsetX,(float)a[0]+4-bx,(float)b[0]-(width-24)-4-bx);
            offsetY=Math.clamp(offsetY,(float)a[1]+4-8,(float)b[1]-(height-16)-4-8);
        }else{offsetX=0;offsetY=0;}
        float x=bx+offsetX,y=by+offsetY;bounds=new float[]{x,y,w,h,8};
        int background=ColorUtil.lerp(Theme.windowBg(),ColorUtil.withAlpha(0x191E22,ColorUtil.alpha(Theme.windowBg())),.72f);
        if(ClientSettings.shadow.get())Render2D.shadow(ctx,x,y+4,w,h,8,22,0x80000000);
        Render2D.roundGradientV(ctx,x,y,w,h,8,ColorUtil.shade(background,.025f),background);
        Render2D.roundOutline(ctx,x,y,w,h,8,Render2D.px(),0x385D6873);gui.hit(x,y,w,h,(button,mx,my)->{});
        ctx.createNewRootLayer();
        gui.hit(x+7,y+3,133,27,(button,mx,my)->{
            if(button!=0||ClickGuiScreen.spatialStyle()||gui.worldControls())return;
            float sx=offsetX,sy=offsetY;gui.startDrag(WINDOW,(nx,ny)->{offsetX=sx+(float)(nx-mx);offsetY=sy+(float)(ny-my);});
        });remember("drag",x+7,y+3,133,27);
        Logo.mark(ctx,x+20,y+17,17,0);BootAssets.label(ctx,"maro.gg",x+35,y+12,14,Theme.TEXT,true);
        topTools(ctx,x,y,w);
        int categories=(int)Arrays.stream(Category.values()).filter(c->!ModuleManager.byCategory(c).isEmpty()).count();
        float textScale=ClientSettings.menuTextSize.getFloat()/90f;
        int columns=Math.min(categories+1,Math.max(2,(int)((w-24)/(78*textScale))));
        float tw=(w-24)/columns,categoryHeight=(tw<90*textScale?40:32)*textScale;int categoryIndex=0;
        boolean editing=!clientControls&&gui.currentPage() instanceof ModulesPage mp&&mp.inspectorExpanded()&&!mp.showingGrid();
        if(editing){
            Widgets.button(gui,ctx,"back-to-modules",x+12,y+36,w-24,28,"Back to modules",Widgets.Style.GHOST,Icons.BACK,()->((ModulesPage)gui.currentPage()).showGrid());
            remember("back-to-modules",x+12,y+36,w-24,28);
        }
        for(var category:editing?new Category[0]:Category.values()){
            if(ModuleManager.byCategory(category).isEmpty())continue;
            String label=category==Category.ANTI_CHEAT_OFF?"Anti-Cheat":category.getDisplayName();
            navRow(ctx,label,category.getIcon(),x+12+categoryIndex%columns*tw,y+35+categoryIndex/columns*(categoryHeight+4),tw-3,categoryHeight,!clientControls&&!hudOnly&&gui.selectedEntry()==category.ordinal()&&gui.getSearch().getText().isEmpty(),()->showModules(category.ordinal()),"category-"+category.name(),false);
            categoryIndex++;
        }
        if(!editing)navRow(ctx,"HUD",Icons.LAYOUT,x+12+categoryIndex%columns*tw,y+35+categoryIndex/columns*(categoryHeight+4),tw-3,categoryHeight,hudOnly,()->{gui.openPage(Category.VISUALS.ordinal());hudOnly=true;resetGrid();},"tab-HUD",false);
        float navHeight=editing?29:((categories+columns)/columns)*(categoryHeight+4)-4;
        Render2D.rect(ctx,x+10,y+35+navHeight+5,w-20,Render2D.px(),0x2AFFFFFF);
        if(!gui.getSearch().getText().equals(lastQuery)){lastQuery=gui.getSearch().getText();clientControls=false;resetGrid();}
        float cx=x+12,cy=y+35+navHeight+11,cw=w-24,ch=y+h-38-cy;
        if(clientControls){
            Fonts.drawV(ctx,"Appearance",cx,cy+9,Theme.TEXT,true,.86f);clientCards(ctx,cx,cy+26,cw,ch-26);
        }else if(gui.currentPage() instanceof ModulesPage page){
            if(!page.inspectorExpanded()){
                float left=page.listWidth(cw);boolean wrap=left<380;moduleHeaderHeight=wrap?49:26;
                String[] labels={"All","New","Entities","Motion","Starred","Enabled"};
                String[] keys={"all-filter","new-filter","entities-filter","motion-filter","starred-filter","enabled-filter"};
                float[] widths={25,26,44,39,42,44};float at=cx;
                for(int i=0;i<labels.length;i++){
                    String label=labels[i];boolean on=switch(label){case "New"->newOnly;case "Entities"->entitiesOnly;case "Motion"->motionOnly;case "Starred"->starredOnly;case "Enabled"->enabledOnly;default->!enabledOnly&&!starredOnly&&!newOnly&&!entitiesOnly&&!motionOnly;};
                    chip(ctx,label,on,at,cy,widths[i],()->filter(label),keys[i]);
                    if(label.equals("New")&&gui.hovered(at,cy,widths[i],19))gui.tooltip("Modules added since your previous installed module list");
                    at+=widths[i]+2;
                }
                float sx=wrap?cx:at+10,sy=wrap?cy+24:cy,sw=wrap?left:left-(sx-cx);
                gui.getSearch().render(gui,ctx,sx,sy,sw,19,Icons.SEARCH,"Ctrl F");remember("search",sx,sy,sw,19);

            }
            gui.pushClip(cx-2,cy,cw+4,ch);page.render(ctx,cx,cy,cw,ch);gui.popClip();
        }else{
            Fonts.drawV(ctx,gui.selectedEntry()==6?"Client settings":gui.selectedEntry()==7?"Your configs":gui.selectedEntry()==8?"Style":"Friends",cx,cy+9,Theme.TEXT,true,.85f);
            gui.getSearch().render(gui,ctx,cx+cw-180,cy,180,19,Icons.SEARCH,"Ctrl F");remember("search",cx+cw-180,cy,180,19);
            gui.pushClip(cx-2,cy+26,cw+4,ch-26);gui.currentPage().render(ctx,cx,cy+26,cw,ch-26);gui.popClip();
        }
        Render2D.rect(ctx,cx,y+h-32,cw,Render2D.px(),0x16FFFFFF);
        if(!clientControls&&gui.currentPage() instanceof ModulesPage page&&!page.inspectorExpanded()){
            String count=page.visibleModuleCount()+" modules · "+page.visibleModules().stream().filter(Module::isEnabled).count()+" enabled";
            Fonts.drawV(ctx,count,cx+2,y+h-16,Theme.TEXT_MUTED,false,.59f);
            page.renderPagination(ctx,cx+cw-ClickGuiScreen.LAYOUT_SWITCH_W-120,y+h-26);

        }
        gui.layoutSwitch(ctx,x+w-ClickGuiScreen.LAYOUT_SWITCH_W-10,y+h-28);
        if(gui.isDragging(ClientSettings.guiSize))resizePreview(ctx,x,y,w,h);
        if(previewModule!=null){
            float dx=sidePreview?x+w+12:x+w-dockW,dy=sidePreview?y:y-dockH-12;
            previewBounds=new float[]{dx,dy,dockW,dockH};
            ctx.createNewRootLayer();surface(ctx,dx,dy,dockW,dockH);
            gui.hit(dx,dy,dockW,dockH,(button,mx,my)->{});gui.scrollHit(dx,dy,dockW,dockH,amount->{});
            Fonts.drawV(ctx,Fonts.trim(previewModule.getDisplayName(),dockW-55,true,.82f),dx+12,dy+17,Theme.TEXT,true,.82f);
            Widgets.button(gui,ctx,"preview-close",dx+dockW-32,dy+6,24,22,"",Widgets.Style.GHOST,Icons.CLOSE,()->previewModule=null);
            remember("preview-close",dx+dockW-32,dy+6,24,22);
            gui.visualPreview().draw(ctx,previewModule,dx+8,dy+35,dockW-16,dockH-43);
            return new float[]{Math.min(x,dx),Math.min(y,dy),width-24,height-16,8};
        }
        return bounds;
    }
    private void filter(String name){
        enabledOnly=name.equals("Enabled");starredOnly=name.equals("Starred");newOnly=name.equals("New");entitiesOnly=name.equals("Entities");motionOnly=name.equals("Motion");boundOnly=false;resetGrid();
    }
    private void resetGrid(){gui.currentPage().onOpen();if(gui.currentPage() instanceof ModulesPage p)p.resetGridScroll();}
    private void topTools(DrawContext ctx,float x,float y,float w){
        String[] labels={"Presets","Accounts","Style","Setup","Appearance"};
        String[] keys={"tab-Configs","tab-Profile","tab-Theme","tab-Settings","client"};
        Icons.Icon[] icons={Icons.CONFIGS,Icons.PLAYER,Icons.THEME,Icons.SETTINGS,Icons.SETTINGS};
        float toolW=Math.clamp((w-165)/5,24,72),start=x+w-29-(toolW+3)*5;
        for(int i=0;i<5;i++){
            int index=i;boolean on=i==0&&gui.selectedEntry()==7&&!clientControls||i==2&&gui.selectedEntry()==8&&!clientControls||i==3&&gui.selectedEntry()==6&&!clientControls||i==4&&clientControls;
            navRow(ctx,labels[i],icons[i],start+i*(toolW+3),y+7,toolW,21,on,()->{switch(index){
                case 0 -> gui.openPage(7);case 1 -> MinecraftClient.getInstance().setScreen(new dev.maro.gui.accounts.AccountsScreen(gui));
                case 2 -> gui.openPage(8);case 3 -> gui.openPage(6);
                case 4 -> {if(gui.currentPage() instanceof ModulesPage page)page.showGrid();clientControls=true;hudOnly=false;gui.getSearch().clear();lastQuery="";clientScroll.reset();}
            }},keys[i],toolW<64);
        }
        boolean close=gui.hovered(x+w-24,y+8,17,18);if(close)Render2D.roundRect(ctx,x+w-24,y+8,17,18,4,0x18FFFFFF);
        Icons.CLOSE.draw(ctx,x+w-15.5f,y+17,6,close?Theme.TEXT:Theme.TEXT_MUTED,0);gui.hit(x+w-24,y+8,17,18,(button,mx,my)->{if(button==0)gui.close();});
    }
    private void navRow(DrawContext ctx,String label,Icons.Icon icon,float x,float y,float w,float h,boolean on,Runnable action,String key,boolean compact){
        boolean over=gui.hovered(x,y,w,h);
        if(key.startsWith("category-")||key.equals("tab-HUD")){
            Render2D.roundRect(ctx,x,y,w,h,6,on?Theme.accent(0x40):over?0x305B6D83:0x182E3947);
            Render2D.roundOutline(ctx,x,y,w,h,6,Render2D.px(),on?Theme.accent(0xA0):over?0x718A9EB8:0x344A5B70);
            if(on)Render2D.roundRect(ctx,x+8,y+h-2.5f,w-16,2,1,Theme.accent());
            float textScale=ClientSettings.menuTextSize.getFloat()/90f;
            boolean stacked=h>35*textScale;float font=(stacked?10.8f:11.5f)*textScale;
            float available=stacked?w-10:w-34;
            font=Math.min(font,available/Math.max(.1f,BootAssets.labelWidth(label,1,true)));
            float tx=stacked?x+(w-BootAssets.labelWidth(label,font,true))/2:x+28;
            float ty=stacked?y+h-font*.73f-7:y+(h-font*.73f)/2;
            icon.draw(ctx,stacked?x+w/2:x+13,stacked?y+11*textScale:y+h/2,13.5f*textScale,on?Theme.TEXT:Theme.accent(),0);
            BootAssets.label(ctx,label,tx,ty,font,on?Theme.TEXT:0xFFD5DDE9,true);
            gui.hit(x,y,w,h,(button,mx,my)->{if(button==0){action.run();Sounds.click();}});remember(key,x,y,w,h);
            if(over)gui.tooltip(label);
            return;
        }
        if(on||over)Render2D.roundRect(ctx,x,y,w,h,4,on?Theme.accent(0x24):0x10FFFFFF);
        if(on)Render2D.roundRect(ctx,x+5,y+h-1,w-10,1,0.5f,Theme.accent());
        int color=on?Theme.TEXT:Theme.TEXT_DIM;
        icon.draw(ctx,compact?x+w/2:x+12,y+h/2,9,on?Theme.TEXT:Theme.accent(),0);
        if(!compact)Fonts.drawV(ctx,Fonts.trim(label,w-27,on,.64f),x+25,y+h/2,color,on,.64f);
        gui.hit(x,y,w,h,(button,mx,my)->{if(button==0){action.run();Sounds.click();}});remember(key,x,y,w,h);
        if(compact&&over)gui.tooltip(label);
    }
    private void chip(DrawContext ctx,String label,boolean on,float x,float y,float w,Runnable action,String key){
        boolean over=gui.hovered(x,y,w,19);
        if(on||over)Render2D.roundRect(ctx,x,y,w,19,5,on?0xA24C555B:0x353A4247);
        float font=Math.min(8.7f*ClientSettings.menuTextSize.getFloat()/90f,(w-6)/Math.max(.1f,BootAssets.labelWidth(label,1,false)));BootAssets.label(ctx,label,x+(w-BootAssets.labelWidth(label,font,false))/2,y+(19-font*.73f)/2,font,on?Theme.TEXT:Theme.TEXT_MUTED,false);
        gui.hit(x,y,w,19,(button,mx,my)->{if(button==0)action.run();});remember(key,x,y,w,19);
    }
    private void surface(DrawContext ctx,float x,float y,float w,float h){
        Render2D.roundRect(ctx,x,y,w,h,6,0x9C22282B);
        Render2D.roundOutline(ctx,x,y,w,h,6,Render2D.px(),0x354C575D);
    }
    private void clientCards(DrawContext ctx,float x,float y,float w,float h){
        clientScroll.reset();
        String[] titles={"Size","Colors","Interface"};
        for(int i=0;i<titles.length;i++){
            final int index=i;float bx=x+i*(w/3+1);
            Widgets.button(gui,ctx,"appearance-"+i,bx,y,w/3-3,22,titles[i],appearancePage==i?Widgets.Style.PRIMARY:Widgets.Style.GHOST,null,()->appearancePage=index);
            remember("appearance-"+i,bx,y,w/3-3,22);
        }
        y+=30;h-=30;
        gui.scrollHit(x,y,w,h,amount->{});
        if(appearancePage==0){
            float cell=(w-8)/2;
            surface(ctx,x,y,cell,78);
            Fonts.draw(ctx,"UI Scale",x+13,y+10,Theme.TEXT,true,.86f);
            Fonts.drawV(ctx,"Fullscreen",x+cell-101,y+14,Theme.TEXT_DIM,false,.64f);
            Widgets.toggle(ctx,x+cell-30,y+9,19,10,Anims.of(ClientSettings.guiFullscreen,"on",ClientSettings.guiFullscreen.get()),0);
            gui.hit(x+cell-108,y+4,100,20,(button,mx,my)->{if(button==0){ClientSettings.guiFullscreen.toggle();Sounds.toggle(ClientSettings.guiFullscreen.get());}});
            remember("fullscreen",x+cell-108,y+4,100,20);
            Fonts.draw(ctx,Fonts.trim(ClientSettings.guiFullscreen.get()?"Turn off fullscreen to resize.":"Resize the window when you release.",cell-26,false,.65f),x+13,y+27,Theme.TEXT_MUTED,false,.65f);
            float sx=x+14,sw=cell-80,trackY=y+48;
            slider(ctx,ClientSettings.guiSize,sx,trackY,sw,x+cell-14);
            if(!ClientSettings.guiFullscreen.get())gui.hit(sx-5,trackY-8,sw+10,20,(button,mx,my)->{if(button==0){resizeStart=ClientSettings.guiSize.getFloat();resizeMotion.snap(1);gui.startDrag(ClientSettings.guiSize,(nx,ny)->ClientSettings.guiSize.setPercent((nx-sx)/sw));}});
            remember("scale",sx-5,trackY-8,sw+10,20);
            float tx=x+cell+8,tsx=tx+14;
            surface(ctx,tx,y,cell,78);
            Fonts.draw(ctx,"Text Size",tx+13,y+10,Theme.TEXT,true,.86f);
            Fonts.draw(ctx,Fonts.trim("Labels and category navigation resize together.",cell-26,false,.65f),tx+13,y+27,Theme.TEXT_MUTED,false,.65f);
            slider(ctx,ClientSettings.menuTextSize,tsx,trackY,sw,tx+cell-14);
            gui.hit(tsx-5,trackY-8,sw+10,20,(button,mx,my)->{if(button==0)gui.startDrag(ClientSettings.menuTextSize,(nx,ny)->ClientSettings.menuTextSize.setPercent((nx-tsx)/sw));});
            remember("text-size",tsx-5,trackY-8,sw+10,20);
            places.put("text-size-max",new float[]{tsx+sw,trackY+2});
        }else if(appearancePage==1){
            surface(ctx,x,y,w,Math.min(112,h));
            Fonts.draw(ctx,"Accent Color",x+13,y+12,Theme.TEXT,true,.9f);
            Fonts.draw(ctx,"One color across the entire client.",x+13,y+29,Theme.TEXT_MUTED,false,.70f);
            int[] colors={0xFFF44336,0xFFFF9800,0xFFFFCA28,0xFF2ED573,0xFF3391FC,0xFF8B5CF6,0xFFEC4899};
            for(int i=0;i<colors.length;i++){
                float px=x+21+i*22;int c=colors[i];Render2D.circle(ctx,px,y+54,6,c);
                if(ClientSettings.accent.get()==c)Render2D.ring(ctx,px,y+54,8,1.1f,Theme.TEXT);
                gui.hit(px-9,y+45,18,18,(button,mx,my)->{if(button==0){ClientSettings.rainbow.set(false);ClientSettings.accent.set(c);}});
                remember("color-"+i,px-9,y+45,18,18);
            }
            float hueX=x+15,hueW=w-102,hy=y+80;
            for(int i=0;i<6;i++)Render2D.rectGradient(ctx,hueX+hueW*i/6,hy,hueW/6+1,5,ColorUtil.hsv(i/6f,1,1),ColorUtil.hsv((i+1)/6f,1,1),ColorUtil.hsv((i+1)/6f,1,1),ColorUtil.hsv(i/6f,1,1));
            Render2D.ring(ctx,hueX+hueW*ClientSettings.accent.getHue(),hy+2.5f,4,1.4f,Theme.TEXT);
            Fonts.drawRight(ctx,ColorUtil.toHex(ClientSettings.accent.get(),false),x+w-12,hy+3,Theme.TEXT_MUTED,false,.69f);
            gui.hit(hueX-4,hy-8,hueW+8,20,(button,mx,my)->{if(button==0)gui.startDrag(ClientSettings.accent,(nx,ny)->{ClientSettings.rainbow.set(false);ClientSettings.accent.setHsv(Math.clamp((float)(nx-hueX)/hueW,0,1),.8f,.95f,255);});});
        }else{
            toggleCard(ctx,"Frosted Glass","Blur the world behind the window.",ClientSettings.backgroundBlur,x,y,w/2-4,70);
            toggleCard(ctx,"UI Sounds","Soft feedback for menu actions.",ClientSettings.uiSounds,x+w/2+4,y,w/2-4,70);
            Widgets.button(gui,ctx,"all-settings",x,y+78,w,24,"All client settings",Widgets.Style.SECONDARY,Icons.SETTINGS,()->gui.openPage(6));
        }
    }
    private void slider(DrawContext ctx,dev.maro.setting.NumberSetting value,float x,float y,float w,float right){
        Render2D.roundRect(ctx,x,y,w,4,2,0xFF3B4347);
        float percent=(float)value.getPercent();
        Render2D.roundRect(ctx,x,y,w*percent,4,2,Theme.accent());Render2D.circle(ctx,x+w*percent,y+2,4,Theme.TEXT);
        Fonts.drawRight(ctx,value.format(),right,y+2,Theme.TEXT,true,.78f);
    }
    private void toggleCard(DrawContext ctx,String label,String desc,BooleanSetting value,float x,float y,float w,float h){
        surface(ctx,x,y,w,h);Fonts.draw(ctx,Fonts.trim(label,w-47,true,.74f),x+12,y+13,Theme.TEXT,true,.74f);
        var lines=Fonts.wrap(desc,w-24,false,.66f);for(int i=0;i<Math.min(2,lines.size());i++)Fonts.draw(ctx,lines.get(i),x+12,y+32+i*10,Theme.TEXT_MUTED,false,.66f);
        Widgets.toggle(ctx,x+w-30,y+11,19,10,Anims.of(value,"panelToggle",value.get()),0);
        gui.hit(x,y,w,h,(button,mx,my)->{if(button==0){value.set(!value.get());Sounds.click();}});
    }
    private void resizePreview(DrawContext ctx,float x,float y,float w,float h){
        float ratio=resizeMotion.update(Math.min(98,ClientSettings.guiSize.getFloat())/Math.min(98,resizeStart));
        float pw=w*ratio,ph=h*ratio,px=x+(w-pw)/2,py=y+(h-ph)/2;
        for(float i=0;i<pw;i+=8){Render2D.rect(ctx,px+i,py,Math.min(4,pw-i),1,Theme.accent(0xB0));Render2D.rect(ctx,px+i,py+ph,Math.min(4,pw-i),1,Theme.accent(0xB0));}
        for(float i=0;i<ph;i+=8){Render2D.rect(ctx,px,py+i,1,Math.min(4,ph-i),Theme.accent(0xB0));Render2D.rect(ctx,px+pw,py+i,1,Math.min(4,ph-i),Theme.accent(0xB0));}
        Fonts.drawCentered(ctx,ClientSettings.guiSize.format(),x+w/2,py+ph-8,Theme.TEXT,true,.8f);
    }
}
