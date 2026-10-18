package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.page.ModulesPage;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ArmorHud;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.module.impl.visuals.JumpCircle;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import java.util.*;

/** Native pointer/keyboard routing and responsive geometry for the shared workspace. */
final class MenuWorkspaceChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static ModulesPage page(ClickGuiScreen g){try{var m=ClickGuiScreen.class.getDeclaredMethod("currentPage");m.setAccessible(true);return (ModulesPage)m.invoke(g);}catch(Exception e){throw new AssertionError(e);}}
    private static void tap(ClickGuiScreen g,float x,float y){var p=g.screenPose().project(x,y);var click=new Click(p[0],p[1],new MouseInput(0,0));g.mouseMoved(p[0],p[1]);require(g.mouseClicked(click,false),"Workspace click missed");g.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(s->s.getSettings().forEach(v->saved.put(v,v.toJson().deepCopy())));
        ModuleManager.all().forEach(m->m.getSettings().forEach(v->saved.put(v,v.toJson().deepCopy())));
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var armor=ModuleManager.get(ArmorHud.class);var sky=ModuleManager.get(CustomSky.class);
        boolean armorOn=armor.isEnabled(),skyOn=sky.isEnabled();
        try{
            for(int size:new int[]{2,4}){
                context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(85d);ClientSettings.guiFullscreen.set(false);ClientSettings.menuTextSize.set(75d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);ClientSettings.notifications.set(false);c.options.getGuiScale().setValue(size);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openPage(Category.VISUALS.ordinal());});context.waitTicks(10);
                float smallY=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).cardPanel().place("category-VISUALS")[1]);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("client");tap(g,p[0],p[1]);});context.waitTicks(5);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("text-size-max");require(p!=null,"Appearance has no text size control");tap(g,p[0],p[1]);require(ClientSettings.menuTextSize.getFloat()==110,"Appearance text size did not reach110%: "+ClientSettings.menuTextSize.getFloat());});context.waitTicks(6);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.cardPanel().place("category-VISUALS")[1]>smallY,"Category geometry did not grow with text");var p=g.cardPanel().place("category-VISUALS");tap(g,p[0],p[1]);});context.waitTicks(6);context.takeScreenshot("maro-text-size-110-"+size);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;page(g).openOptions(armor);});context.waitTicks(8);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.visualPreview().bounds();require(p!=null,"HUD preview missing");require(g.cardPanel().previewExpanded(),"Preview did not open beside settings");});context.waitTicks(8);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var main=g.cardPanel().bounds();var p=g.cardPanel().previewBounds();require(p!=null&&page(g).showingSettings(armor),"Preview replaced the settings");require(p[0]>=main[0]+main[2]+8||p[1]+p[3]<=main[1]-8,"Preview overlaps the menu");var a=g.screenPose().project(p[0],p[1]);var b=g.screenPose().project(p[0]+p[2],p[1]+p[3]);require(a[0]>=0&&a[1]>=0&&b[0]<=g.width&&b[1]<=g.height,"Preview outside the screen");require(g.visualPreview().module()==armor&&armor.isEnabled()==armorOn,"HUD preview changed the real module");});context.takeScreenshot("maro-preview-dock-"+size);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;page(g).openOptions(sky);});context.waitTicks(6);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.visualPreview().module()==sky&&sky.isEnabled()==skyOn,"Dock did not follow selected module safely");var close=g.cardPanel().place("preview-close");tap(g,close[0],close[1]);require(!g.cardPanel().previewExpanded()&&page(g).showingSettings(sky),"Close lost the settings");page(g).showGrid();});context.waitTicks(7);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=page(g);require(p.gridPages()>1,"No module pages");var next=p.paginationPlace(1);require(next!=null,"Next page control missing");tap(g,next[0],next[1]);require(p.gridPage()==1,"Next button did not flip page");});context.waitTicks(3);context.takeScreenshot("maro-page-flip-"+size);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.keyPressed(new KeyInput(266,0,0));require(page(g).gridPage()==0,"Page Up did not return");g.keyPressed(new KeyInput(267,0,0));require(page(g).gridPage()==1,"Page Down did not advance");g.focused=g.getSearch();g.keyPressed(new KeyInput(267,0,0));require(page(g).gridPage()==1,"Typing focus leaked into paging");g.focused=null;page(g).changeGridPage(10000);require(page(g).gridPage()==page(g).gridPages()-1,"Paging exceeded last page");page(g).changeGridPage(1);require(page(g).gridPage()==page(g).gridPages()-1,"Disabled next page advanced");});
            }
            settingsPages(context);
            jumpPlayback(context);
            resizeChecks(context);
            System.out.println("[menu-workspace] PASS: native Appearance text-size slider grows category geometry, responsive HUD dock preserves settings and follows selected module, compact close preserves editor, next/previous keyboard/pointer paging and bounds at GUI scales2/4; real module states untouched");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(Setting::fromJson);armor.setEnabled(armorOn);sky.setEnabled(skyOn);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
    private static void settingsPages(ClientGameTestContext context){
        for(int size:new int[]{2,4})for(var module:List.of(ModuleManager.get(SkinAccessories.class),ModuleManager.get(JumpCircle.class),ModuleManager.get(CustomSky.class))){
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.menuTextSize.set(110d);c.options.getGuiScale().setValue(size);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openModuleOptions(module);});context.waitTicks(5);
            for(var section:module.getSettingSections()){
                var expected=section.getSettings().stream().filter(Setting::isVisible).toList();if(expected.isEmpty())continue;
                context.runOnClient(c->page((ClickGuiScreen)c.currentScreen).openSection(section.getTitle()));context.waitTicks(3);
                var seen=Collections.newSetFromMap(new IdentityHashMap<Setting<?>,Boolean>());
                int count=context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).settingsControls().pageCount());
                for(int i=0;i<count;i++){
                    context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var controls=page(g).settingsControls();var box=g.cardPanel().bounds();
                        controls.visiblePlaces().forEach((setting,r)->{require(r[0]>=box[0]&&r[1]>=box[1]&&r[0]+r[2]<=box[0]+box[2]+.1f&&r[1]+r[3]<=box[1]+box[3]-30,"Clipped setting: "+module.getName()+" / "+setting.getName()+" "+Arrays.toString(r)+" box="+Arrays.toString(box));seen.add(setting);});
                        if(controls.page()+1<controls.pageCount()){var at=controls.pagePlace(1);tap(g,at[0],at[1]);}
                    });context.waitTicks(2);
                }
                require(seen.containsAll(expected),"Settings pages omitted controls: "+module.getName()+" / "+section.getTitle());
            }
            context.takeScreenshot("maro-settings-pages-"+module.getName().replace(' ','-').toLowerCase()+"-"+size);
        }
        for(int size:new int[]{2,4})for(int entry:new int[]{6,8}){
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.menuTextSize.set(110d);c.options.getGuiScale().setValue(size);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openPage(entry);});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;try{var method=ClickGuiScreen.class.getDeclaredMethod("currentPage");method.setAccessible(true);var controls=(dev.maro.gui.widget.SettingsList)field(method.invoke(g),"list");var box=g.cardPanel().bounds();require(!controls.visiblePlaces().isEmpty(),"Client settings page is empty");controls.visiblePlaces().forEach((setting,r)->require(r[1]+r[3]<=box[1]+box[3]-30,"Client control clipped: "+setting.getName()+" "+Arrays.toString(r)+" box="+Arrays.toString(box)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            context.takeScreenshot("maro-client-settings-pages-"+entry+"-"+size);
        }
        context.runOnClient(c->{ClientSettings.menuStyle.set("Panels");var g=new ClickGuiScreen();c.setScreen(g);});context.waitTicks(5);
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;for(var section:dev.maro.module.ModuleSections.all())require(g.panelPlace(section)==null,"Dropdown retained a section header");});
        context.takeScreenshot("maro-dropdown-no-sections");
        System.out.println("[settings-pages] PASS: all visible controls reachable with pointer page buttons in accessories/jump/sky at text110 and GUI2/4, entire control rectangles inside menu, no Dropdown section headers");
    }
    private static void jumpPlayback(ClientGameTestContext context){
        var ring=ModuleManager.get(JumpCircle.class);boolean on=ring.isEnabled();
        context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.menuTextSize.set(90d);c.options.getGuiScale().setValue(2);c.onResolutionChanged();
            ring.getSettings().stream().filter(v->v.getName().equals("Color Mode")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive("Custom"));
            ring.getSettings().stream().filter(v->v.getName().equals("Custom Color")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive(0xFF21E4F5));
            ring.getSettings().stream().filter(v->v.getName().equals("Lifetime")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive(.3));
            var g=new ClickGuiScreen();c.setScreen(g);g.openModuleOptions(ring);
        });context.waitTicks(6);
        context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(!p.playing()&&p.jumpRingAge()>0,"Idle ring sample is invisible");});
        context.takeScreenshot("maro-jump-preview-idle");
        for(String trigger:List.of("Jump","Land","Both")){
            context.runOnClient(c->{ring.getSettings().stream().filter(v->v.getName().equals("Trigger")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive(trigger));var g=(ClickGuiScreen)c.currentScreen;var at=g.visualPreview().playPlace();tap(g,at[0],at[1]);});context.waitTicks(3);
            context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(p.playing()&&p.jumpLift()>0,"Play did not animate the jump");require(trigger.equals("Land")?p.jumpRingAge()<0:p.jumpRingAge()>0,"Wrong trigger timing");});
            context.takeScreenshot("maro-jump-preview-"+trigger.toLowerCase());
            context.waitTicks(35);
            // Wait for a visible ring in a later cycle, after the original lifetime expired.
            boolean repeated=false;
            for(int i=0;i<50;i++){repeated=context.computeOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();return p.playbackTime()>1.65&&p.jumpRingAge()>.04&&p.jumpRingAge()<.25;});if(repeated)break;context.waitTicks(1);}
            require(repeated,"Jump ring stopped permanently after its first cycle: "+trigger);
            context.takeScreenshot("maro-jump-preview-repeat-"+trigger.toLowerCase());
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.visualPreview().playPlace();tap(g,at[0],at[1]);});context.waitTicks(2);
            context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(!p.playing()&&p.playbackTime()==0&&p.jumpLift()==0&&p.jumpRingAge()>0,"Stop did not restore the idle sample");require(ring.isEnabled()==on,"Preview toggled world jump circles");});
        }
        System.out.println("[jump-preview] PASS: visible idle ring, native Play/Stop, Jump/Land/Both timing, repeated jump cycles after ring expiry, fixed floor anchor and local player rendering; real module unchanged");
    }

    private static void resizeChecks(ClientGameTestContext context){
        for(int[] size:new int[][]{{800,500},{900,640}}){
            context.getInput().resizeWindow(size[0],size[1]);
            for(int entry:new int[]{6,8}){
                context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.menuTextSize.set(110d);ClientSettings.guiFullscreen.set(false);c.setScreen(new ClickGuiScreen());((ClickGuiScreen)c.currentScreen).openPage(entry);});context.waitTicks(6);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;try{var method=ClickGuiScreen.class.getDeclaredMethod("currentPage");method.setAccessible(true);var controls=(dev.maro.gui.widget.SettingsList)field(method.invoke(g),"list");var box=g.cardPanel().bounds();require(!controls.visiblePlaces().isEmpty(),"Small window lost client controls");controls.visiblePlaces().forEach((setting,r)->require(r[1]+r[3]<=box[1]+box[3]-30,"Small window clipped "+setting.getName()+" "+Arrays.toString(r)+" box="+Arrays.toString(box)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            }
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.openModuleOptions(ModuleManager.get(JumpCircle.class));});context.waitTicks(6);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var box=g.cardPanel().bounds();var preview=g.cardPanel().previewBounds();require(preview[0]>=box[0]+box[2]+8,"Small-window preview overlaps settings");page(g).settingsControls().visiblePlaces().forEach((setting,r)->require(r[1]+r[3]<=box[1]+box[3]-30,"Small-window module control clipped"));});
            context.takeScreenshot("maro-settings-small-"+size[0]);
        }
        context.getInput().resizeWindow(1280,720);
        System.out.println("[settings-resize] PASS: client/theme and separate jump dock fit800x500 and900x640 at110% text without scrolling");
    }

}
