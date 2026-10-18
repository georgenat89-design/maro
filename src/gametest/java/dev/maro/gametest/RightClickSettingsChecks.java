package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.config.ModuleFavorites;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.page.ModulesPage;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StretchRes;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.*;

/** Native hit dispatch: right click toggles across the entire module row in either layout. */
final class RightClickSettingsChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static ModulesPage page(ClickGuiScreen gui){try{var f=ClickGuiScreen.class.getDeclaredMethod("currentPage");f.setAccessible(true);return (ModulesPage)f.invoke(gui);}catch(Exception e){throw new AssertionError(e);}}
    private static void reveal(ClickGuiScreen gui,StretchRes module){if(ClientSettings.menuStyle.is("Panel"))gui.openPage(module.getCategory().ordinal());else try{var p=field(gui,"panels");var m=p.getClass().getDeclaredMethod("revealCategory",dev.maro.module.Category.class);m.setAccessible(true);m.invoke(p,module.getCategory());}catch(Exception e){throw new AssertionError(e);}}
    private static void tap(ClickGuiScreen gui,float x,float y,int button){var p=gui.screenPose().project(x,y);var click=new Click(p[0],p[1],new MouseInput(button,0));gui.mouseMoved(p[0],p[1]);require(gui.mouseClicked(click,false),"Module missed click");gui.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var module=ModuleManager.get(StretchRes.class);boolean enabled=module.isEnabled(),favorite=ModuleFavorites.contains(module);int bind=module.getBind().get();
        try{
            for(String style:List.of("Panel","Panels"))for(int size:new int[]{2,4})for(String area:style.equals("Panel")?List.of("row","switch","star"):List.of("row","dots")){
                context.runOnClient(c->{ClientSettings.menuStyle.set(style);ClientSettings.guiSize.set(85d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);module.setEnabled(false);module.getBind().set(-1);c.options.getGuiScale().setValue(size);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);reveal(g,module);g.getSearch().setText("stretch res");});context.getInput().setCursorPos(0,0);context.waitTicks(7);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;float x,y;
                    if(style.equals("Panel")){var at=page(g).panelCardPlace(module);require(at!=null,"Panel module missing");int offset=area.equals("switch")?2:area.equals("star")?4:0;x=at[offset];y=at[offset+1];}
                    else{var at=g.panelPlace(module);require(at!=null,"Dropdown module missing");x=at[0];y=at[1];if(area.equals("dots")){var rect=(float[])((Map<?,?>)field(field(g,"panels"),"rowPanels")).get(module);x=rect[0]+rect[2]-12;}}
                    tap(g,x,y,1);require(c.currentScreen==g,"Right click replaced the module screen");require(style.equals("Panel")?!page(g).showingSettings(module):g.panelSettingsModule()==null,"Right click opened settings instead of toggling on "+style+" "+area+" scale "+size);require(module.isEnabled()&&ModuleFavorites.contains(module)==favorite&&module.getBind().get()==-1,"Right click did not toggle cleanly");
                    tap(g,x,y,1);require(!module.isEnabled(),"Second right click did not turn module off");
                    var row=style.equals("Panel")?page(g).panelCardPlace(module):g.panelPlace(module);tap(g,row[0],row[1],0);
                    require(style.equals("Panel")?page(g).showingSettings(module):g.panelSettingsModule()==module,"Left row did not open settings");
                });context.waitTicks(5);
                context.runOnClient(c->require(((ClickGuiScreen)c.currentScreen).moduleConfigsPlace(module)!=null,"Settings header did not render"));
                if(area.equals("row"))context.takeScreenshot("maro-right-click-"+style.toLowerCase()+"-"+size);
            }
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");c.options.getGuiScale().setValue(2);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);reveal(g,module);g.getSearch().setText("stretch res");});context.waitTicks(7);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=page(g).panelCardPlace(module);tap(g,at[2],at[3],0);require(module.isEnabled(),"Left switch stopped toggling");module.setEnabled(false);tap(g,at[4],at[5],0);require(ModuleFavorites.contains(module)!=favorite,"Left star stopped saving favorites");ModuleFavorites.toggle(module);tap(g,at[0],at[1],0);require(page(g).showingSettings(module),"Left row stopped opening inspector");});
            System.out.println("[right-click-toggle] PASS: native row/switch/star and Dropdown row/dots right-clicks toggle on and off at scales 2/4 without changing favorites/binds or opening settings; left row opens settings, left switch/favorite preserved");
        }finally{context.runOnClient(c->{c.setScreen(null);module.setEnabled(enabled);module.getBind().set(bind);if(ModuleFavorites.contains(module)!=favorite)ModuleFavorites.toggle(module);saved.forEach(Setting::fromJson);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
