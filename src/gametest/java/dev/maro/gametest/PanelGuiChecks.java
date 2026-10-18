package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.gui.*;
import dev.maro.gui.page.ModulesPage;
import dev.maro.gui.hud.HudElement;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.*;

final class PanelGuiChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ModulesPage page(ClickGuiScreen gui){try{var m=ClickGuiScreen.class.getDeclaredMethod("currentPage");m.setAccessible(true);return (ModulesPage)m.invoke(gui);}catch(Exception e){throw new AssertionError(e);}}
    private static Click click(ClickGuiScreen gui,float[] point,int button){require(point!=null,"No hit position");var at=gui.screenPose().project(point[0],point[1]);return new Click(at[0],at[1],new MouseInput(button,0));}
    private static void tap(ClickGuiScreen gui,float[] point,int button){var click=click(gui,point,button);gui.mouseMoved(click.x(),click.y());gui.mouseClicked(click,false);gui.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        var settings=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(s->s.getSettings().forEach(v->settings.put(v,v.toJson().deepCopy())));
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var target=ModuleManager.getByName("Example");int bind=target.getBind().get();boolean enabled=target.isEnabled();
        try{
            context.runOnClient(c->{require(ClientSettings.menuStyle.getModes().equals(java.util.List.of("Panels","Panel")),"Removed Click GUI layouts are still offered");for(String legacy:java.util.List.of("Window","Laptop","iPad")){ClientSettings.menuStyle.fromJson(new com.google.gson.JsonPrimitive(legacy));require(ClientSettings.menuStyle.is("Panel")&&ClientSettings.menuStyle.toJson().getAsString().equals("Panel"),"Saved "+legacy+" choice did not migrate to Panel");}});
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(75d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);target.setEnabled(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();var gui=new ClickGuiScreen();c.setScreen(gui);gui.openPage(Category.VISUALS.ordinal());});
            context.waitTicks(15);context.takeScreenshot("maro-panel-modules");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.cardPanelPlace()!=null,"Panel missing from layout switch");var b=g.cardPanel().bounds();var point=g.screenPose().project(b[0]+b[2],b[1]+b[3]);require(point[0]<=g.width&&point[1]<=g.height,"Panel clipped the screen");var region=g.cardPanel().place("drag");var a=click(g,region,0);g.mouseMoved(a.x(),a.y());g.mouseClicked(a,false);var p=g.screenPose().project(region[0]+20,region[1]+8);var drag=new Click(p[0],p[1],new MouseInput(0,0));g.mouseDragged(drag,20,8);g.mouseReleased(drag);});context.waitTicks(4);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.cardPanel().bounds()[0]>20,"Header drag did not move Panel");var b=g.cardPanel().bounds();var at=g.screenPose().project(b[0]+b[2]-25,b[1]+140);for(int i=0;i<10;i++)g.mouseScrolled(at[0],at[1],0,-1);});context.waitTicks(6);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("example");});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).visibleModuleCount()==1,"Search did not find the single test module");var at=page(g).panelCardPlace(target);require(at!=null,"Searching after scroll did not reset viewport");tap(g,new float[]{at[0],at[1]},0);require(!target.isEnabled()&&page(g).showingSettings(target)&&page(g).showingGrid(),"Selecting a row must retain the list and leave the module off");tap(g,new float[]{at[2],at[3]},0);require(target.isEnabled(),"Panel switch did not enable module");});context.waitTicks(4);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("enabled-filter"),0);});context.waitTicks(3);
            require(context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).visibleModuleCount()==1),"Enabled filter lost enabled module");
            context.runOnClient(c->{target.setEnabled(false);});context.waitTicks(3);
            require(context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).visibleModuleCount()==0),"Enabled filter retained disabled module");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("enabled-filter"),0);target.getBind().set(66);tap(g,g.cardPanel().place("bound-filter"),0);});context.waitTicks(3);
            require(context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).visibleModuleCount()==1),"Keybound filter failed");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=page(g).panelCardPlace(target);tap(g,new float[]{at[4],at[5]},0);require(page(g).showingSettings(target),"Gear did not open module settings");});context.waitTicks(6);context.takeScreenshot("maro-panel-module-settings");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).panelCardPlace(target)!=null,"Selecting settings removed the module list");var b=g.cardPanel().bounds();tap(g,new float[]{b[0]+b[2]-33,b[1]+87},0);require(page(g).inspectorExpanded(),"Settings expand button did not expand inspector");});context.waitTicks(4);context.takeScreenshot("maro-panel-inspector-expanded");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).onEscape()&&!page(g).inspectorExpanded()&&page(g).showingSettings(target),"Escape did not collapse inspector while preserving selection");});context.waitTicks(3);

            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;page(g).onEscape();g.getSearch().clear();g.openPage(Category.MISC.ordinal());g.getSearch().setText("example");});context.waitTicks(5);
            var parent=context.computeOnClient(c->(ClickGuiScreen)c.currentScreen);
            context.runOnClient(c->{var at=page(parent).panelCardPlace(target);tap(parent,new float[]{at[0],at[1]},1);require(c.currentScreen==parent&&page(parent).showingSettings(target),"Right click did not open module settings");c.setScreen(parent);});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("bound-filter"),0);tap(g,g.cardPanel().place("tab-HUD"),0);});context.waitTicks(5);
            context.runOnClient(c->{var list=page((ClickGuiScreen)c.currentScreen).visibleModules();require(!list.isEmpty()&&list.stream().allMatch(m->m instanceof HudElement),"HUD tab contains non-HUD modules");});context.takeScreenshot("maro-panel-hud");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("free cam");});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("client"),0);});context.waitTicks(4);
            require(context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).cardPanel().clientControls()),"Client controls closed after clearing a search");
            context.takeScreenshot("maro-panel-client");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("scale");var a=click(g,p,0);g.mouseMoved(a.x(),a.y());g.mouseClicked(a,false);var to=g.screenPose().project(p[0]+100,p[1]);g.mouseDragged(new Click(to[0],to[1],new MouseInput(0,0)),100,0);require(g.isDragging(ClientSettings.guiSize),"Size slider did not capture drag");});context.waitTicks(4);context.takeScreenshot("maro-panel-scale-preview");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.mouseReleased(new Click(0,0,new MouseInput(0,0)));require(!g.isDragging(ClientSettings.guiSize),"Scale drag stuck after release");});context.waitTicks(14);context.takeScreenshot("maro-panel-scaled");
            for(int guiScale:new int[]{2,4}){context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();ClientSettings.guiSize.set(75d);var g=(ClickGuiScreen)c.currentScreen;g.openPage(Category.MOVEMENT.ordinal());g.getSearch().setText("free cam");});context.waitTicks(10);context.takeScreenshot("maro-panel-scale-"+guiScale);}
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.layoutPlace(true),0);require(ClientSettings.menuStyle.is("Panels"),"Could not leave Panel through footer");});context.waitTicks(5);
            System.out.println("[panel-gui] PASS: tabbed list/inspector rendering and selection without toggling, header drag, ten wheel gestures and search reset, real toggle, Enabled/Keybound/HUD filters, gear/settings and module public configs, Client/scale preview+release, GUI scales 2/4 and footer switch");
        }finally{context.runOnClient(c->{c.setScreen(null);target.setEnabled(enabled);target.getBind().set(bind);settings.forEach(Setting::fromJson);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
