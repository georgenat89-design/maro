package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.gui.*;
import dev.maro.gui.page.ModulesPage;
import dev.maro.gui.widget.ModuleSectionHeaders;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.module.ModuleSections;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.*;

/** Native section folding/search must never hide a match or toggle a module. */
final class ModuleSectionChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ModulesPage page(ClickGuiScreen gui){try{var m=ClickGuiScreen.class.getDeclaredMethod("currentPage");m.setAccessible(true);return (ModulesPage)m.invoke(gui);}catch(Exception e){throw new AssertionError(e);}}
    private static void tap(ClickGuiScreen gui,float[] at){require(at!=null,"Section control is outside the list");var p=gui.screenPose().project(at[0],at[1]);var click=new Click(p[0],p[1],new MouseInput(0,0));gui.mouseMoved(p[0],p[1]);gui.mouseClicked(click,false);gui.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(s->s.getSettings().forEach(v->saved.put(v,v.toJson().deepCopy())));
        var panels=PanelsView.save();int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var armor=ModuleManager.getByName("Armor HUD");var camera=ModuleManager.getByName("Custom Fov");
        var hudSection=ModuleSections.of(armor);var cameraSection=ModuleSections.of(camera);
        long enabled=ModuleManager.all().stream().filter(dev.maro.module.Module::isEnabled).count();
        try{
            context.runOnClient(c->{
                var visible=ModuleManager.all().stream().filter(m->!m.hiddenInGui()).toList();var grouped=ModuleSections.groups(visible);
                var seen=Collections.newSetFromMap(new IdentityHashMap<dev.maro.module.Module,Boolean>());
                for(var group:grouped)for(var module:group.modules()){require(seen.add(module),"Module appears in two sections: "+module.getName());require(group.section().category()==module.getCategory(),"Section changed a module's category");}
                require(seen.size()==visible.size(),"Grouping lost modules");
                var future=new dev.maro.module.Module("Future browser module","",Category.VISUALS){};
                require(ModuleSections.groups(List.of(future)).getFirst().modules().contains(future),"An unmapped future module disappeared");
                System.out.println("[module-sections] assignment audit: "+visible.size()+" modules; General="+visible.stream().filter(m->ModuleSections.of(m).id().endsWith("/general")).map(dev.maro.module.Module::getName).toList());
                require(visible.stream().noneMatch(m->ModuleSections.of(m).id().endsWith("/general")),"An existing module still needs its browser section assigned");
                ModuleSectionHeaders.reset();ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(85d);ClientSettings.guiFullscreen.set(false);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openPage(Category.VISUALS.ordinal());g.mouseMoved(0,0);
            });context.waitTicks(15);context.takeScreenshot("maro-sections-panel");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("collapse-sections"));});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).panelCardPlace(armor)==null,"Collapsed section retained module rows");require(page(g).sectionPlace(cameraSection)!=null,"Collapsed section title disappeared");tap(g,page(g).sectionPlace(cameraSection));});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).panelCardPlace(camera)!=null,"Section title did not expose its modules");require(!ModuleSectionHeaders.expanded(hudSection,false),"Expanding one section expanded another");g.getSearch().setText("Armor HUD");});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(page(g).panelCardPlace(armor)!=null,"Search could not reveal a folded result");require(!ModuleSectionHeaders.expanded(hudSection,false),"Search overwrote the saved fold state");g.mouseMoved(0,0);});context.takeScreenshot("maro-sections-search");
            context.runOnClient(c->{var matches=ModuleManager.search("Hands & Items");var expected=ModuleManager.byCategory(Category.VISUALS).stream().filter(m->ModuleSections.of(m).title().equals("Hands & Items")).toList();require(!expected.isEmpty()&&matches.containsAll(expected)&&ModuleManager.search("hands items").containsAll(expected),"Section-name search lost member modules");var g=(ClickGuiScreen)c.currentScreen;g.getSearch().clear();});context.waitTicks(4);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.layoutPlace(true));});context.waitTicks(12);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.panelPlace(camera)!=null,"Dropdown lost the shared expanded section");tap(g,g.panelPlace(cameraSection));});context.waitTicks(4);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.panelPlace(camera)==null,"Dropdown section did not fold");g.mouseMoved(0,0);});context.takeScreenshot("maro-sections-dropdown-folded");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("Armor HUD");});context.waitTicks(4);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.panelPlace(armor)!=null,"Dropdown search hid a folded result");var json=PanelsView.save();ModuleSectionHeaders.reset();PanelsView.load(json);require(!ModuleSectionHeaders.expanded(hudSection,false),"Section folds did not persist");g.getSearch().clear();});context.waitTicks(3);
            context.runOnClient(c->c.setScreen(new ClickGuiScreen()));context.waitTicks(12);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(!ModuleSectionHeaders.expanded(hudSection,false)&&g.panelPlace(armor)==null,"Reopening the menu lost folded sections");ModuleSectionHeaders.expandAll(true);});context.waitTicks(4);context.runOnClient(c->((ClickGuiScreen)c.currentScreen).mouseMoved(0,0));context.takeScreenshot("maro-sections-dropdown");
            context.runOnClient(c->require(ModuleManager.all().stream().filter(dev.maro.module.Module::isEnabled).count()==enabled,"Folding a section toggled a module"));
            System.out.println("[module-sections] PASS: one section per visible module, future-module fallback, native collapse/expand, shared Panel/Dropdown state, folded-result and section-name search, saved fold reload and unchanged module states");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(Setting::fromJson);PanelsView.load(panels);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
