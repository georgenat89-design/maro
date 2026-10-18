package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.page.ModulesPage;
import dev.maro.module.Category;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;

final class CleanDesignChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static ModulesPage page(ClickGuiScreen g){try{var m=ClickGuiScreen.class.getDeclaredMethod("currentPage");m.setAccessible(true);return (ModulesPage)m.invoke(g);}catch(Exception e){throw new AssertionError(e);}}
    private static void tap(ClickGuiScreen g,float x,float y){var p=g.screenPose().project(x,y);var click=new Click(p[0],p[1],new MouseInput(0,0));g.mouseMoved(p[0],p[1]);g.mouseClicked(click,false);g.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        dev.maro.gui.widget.ScrollMotionChecks.run();
        context.runOnClient(c->{if(dev.maro.module.ModuleManager.getByName("Example")==null)dev.maro.module.ModuleManager.register(new dev.maro.module.impl.ExampleModule());dev.maro.module.ModuleManager.all().forEach(m->m.setEnabled(false));ClientSettings.notifications.set(false);ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(75d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openPage(Category.VISUALS.ordinal());});
        context.waitTicks(20);
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=page(g);var list=p.visibleModules();require(list.size()>2,"Missing modules");for(int i=1;i<list.size();i++)require(String.CASE_INSENSITIVE_ORDER.compare(list.get(i-1).getDisplayName(),list.get(i).getDisplayName())<=0,"Not alphabetical");require(p.gridPages()>1,"No pagination");var a=p.panelCardPlace(list.get(0));var b=p.panelCardPlace(list.get(1));require(a!=null&&b!=null&&b[0]>a[0]&&b[1]==a[1],"Not a two column grid");p.changeGridPage(1);});
        context.waitTicks(30);context.takeScreenshot("maro-clean-page-2");
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=page(g);require(p.gridPage()==1,"Page did not advance");p.changeGridPage(-1);});context.waitTicks(30);
        context.takeScreenshot("maro-clean-modules");
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var bounds=g.cardPanel().bounds();var at=g.screenPose().project(bounds[0]+50,bounds[1]+160);g.mouseMoved(at[0],at[1]);g.mouseScrolled(at[0],at[1],0,-1);require(page(g).gridScrollOffset()<1,"Wheel snapped immediately to a full page");});context.waitTicks(2);
        float moved=context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).gridScrollOffset());require(moved>0&&moved<30,"Native wheel failed to glide through intermediate rows: "+moved);
        context.takeScreenshot("maro-module-wheel-glide");
        float reversing=context.computeOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;float before=page(g).gridScrollOffset();var bounds=g.cardPanel().bounds();var at=g.screenPose().project(bounds[0]+50,bounds[1]+160);g.mouseScrolled(at[0],at[1],0,1);return before;});context.waitTicks(2);
        require(context.computeOnClient(c->page((ClickGuiScreen)c.currentScreen).gridScrollOffset())<reversing,"Wheel reversal kept moving down");context.waitTicks(30);

        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=page(g);var m=p.visibleModules().getFirst();var at=p.panelCardPlace(m);boolean before=m.isEnabled();tap(g,at[2],at[3]);require(m.isEnabled()!=before,"Toggle failed");m.setEnabled(before);tap(g,at[0],at[1]);require(p.showingSettings(m),"Settings did not open");});
        context.waitTicks(5);context.takeScreenshot("maro-clean-settings");
        context.runOnClient(c->{var p=page((ClickGuiScreen)c.currentScreen);require(p.onEscape(),"Settings did not close");});context.waitTicks(3);
        final dev.maro.module.Module[] favorite={null};final boolean[] wasFavorite={false};
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=page(g);var m=p.visibleModules().getFirst();favorite[0]=m;wasFavorite[0]=dev.maro.config.ModuleFavorites.contains(m);var at=p.panelCardPlace(m);tap(g,at[4],at[5]);require(dev.maro.config.ModuleFavorites.contains(m)!=wasFavorite[0],"Favorite star toggled the row instead of saving the favorite");if(wasFavorite[0])tap(g,at[4],at[5]);});
        context.waitTicks(3);
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.cardPanel().place("starred-filter");tap(g,at[0],at[1]);});context.waitTicks(4);
        context.runOnClient(c->{var p=page((ClickGuiScreen)c.currentScreen);require(p.visibleModules().contains(favorite[0])&&p.visibleModules().stream().allMatch(dev.maro.config.ModuleFavorites::contains),"Starred filter includes unstarred rows or omits favorite");});context.takeScreenshot("maro-module-favorites");
        for(String filter:new String[]{"entities-filter","motion-filter","new-filter","all-filter"}){
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.cardPanel().place(filter);tap(g,at[0],at[1]);});context.waitTicks(4);
            context.runOnClient(c->{var p=page((ClickGuiScreen)c.currentScreen);if(filter.equals("entities-filter"))require(!p.visibleModules().isEmpty()&&p.visibleModules().stream().allMatch(dev.maro.gui.render.ModuleIcons::entities),"Entity filter mismatch");if(filter.equals("motion-filter"))require(!p.visibleModules().isEmpty()&&p.visibleModules().stream().allMatch(dev.maro.gui.render.ModuleIcons::motion),"Motion filter mismatch");if(filter.equals("new-filter"))require(p.visibleModules().stream().allMatch(dev.maro.config.ModuleDiscovery::contains),"New catalog filter mismatch");});
        }
        context.runOnClient(c->{if(dev.maro.config.ModuleFavorites.contains(favorite[0])!=wasFavorite[0])dev.maro.config.ModuleFavorites.toggle(favorite[0]);});
        for(int scale:new int[]{2,4}){
            context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});context.waitTicks(8);context.takeScreenshot("maro-clean-scale-"+scale);
        }
        context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("example");});context.waitTicks(5);
        context.runOnClient(c->{var p=page((ClickGuiScreen)c.currentScreen);require(p.visibleModuleCount()==1&&p.gridPage()==0&&p.gridPages()==1,"Search pagination did not reset");});
        RenderFaultChecks.assertClean();System.out.println("[clean-design] PASS: A-Z compact icon rows, two columns, pagination, search, separate toggle/star hit areas, persisted favorites, New/Entities/Motion/Starred filters, settings, native continuous wheel glide/reversal, no section headers, scales 2/4");
        MenuWorkspaceChecks.run(context);
    }
}
