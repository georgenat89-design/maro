package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.ScreenGuiPose;
import dev.maro.gui.widget.Scroll;
import dev.maro.module.Category;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.List;

/** Actual module-list wheel, track, drag and release events in both GUI layouts. */
final class ModuleScrollChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object field(Object object,String name){for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new AssertionError(e);}throw new AssertionError(name);}
    private static Object call(Object object,String method){try{var m=object.getClass().getDeclaredMethod(method);m.setAccessible(true);return m.invoke(object);}catch(Exception e){throw new AssertionError(e);}}
    private record Track(float x,float y,float h,Scroll scroll){}
    private static Track track(ClickGuiScreen gui,boolean panels){
        Scroll scroll;float expectedX=Float.NaN;
        if(panels){var view=field(gui,"panels");var rectangles=(java.util.Map<?,?>)field(view,"panelRects");float[] rect=(float[])rectangles.get(Category.VISUALS);expectedX=rect[0]+rect[2]-2.5f;
            Object panel=((List<?>)field(view,"PANELS")).stream().filter(p->field(p,"category")==Category.VISUALS).findFirst().orElseThrow();scroll=(Scroll)field(panel,"scroll");
        }else{scroll=(Scroll)field(call(gui,"currentPage"),"scroll");}
        for(Object hit:(List<?>)field(gui,"scrollHits")){float w=(float)call(hit,"w"),x=(float)call(hit,"x");if(w>=5&&w<=12&&(!panels||Math.abs(x+5-expectedX)<.1))return new Track(x+5,(float)call(hit,"y"),(float)call(hit,"h"),scroll);}
        throw new AssertionError("Module scrollbar has no input region: "+field(gui,"scrollHits"));
    }
    private static double[] point(ClickGuiScreen gui,double x,double y){return ((ScreenGuiPose)field(gui,"screenPose")).project(x,y);}
    private static Click click(double[] p){return new Click(p[0],p[1],new MouseInput(0,0));}
    private static Object visualPanel(ClickGuiScreen gui){return ((List<?>)field(field(gui,"panels"),"PANELS")).stream().filter(p->field(p,"category")==Category.VISUALS).findFirst().orElseThrow();}
    private static void resizeChecks(ClientGameTestContext context){
        for(int scale:new int[]{2,4}){
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panels");c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new ClickGuiScreen());});context.waitTicks(12);
            float initial=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).panelResizePlace(Category.VISUALS)[1]);
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var at=gui.panelResizePlace(Category.VISUALS);var p=point(gui,at[0],at[1]);gui.mouseMoved(p[0],p[1]);gui.mouseClicked(click(p),false);Object handle=field(visualPanel(gui),"resizeHandle");require(gui.isDragging(handle),"Bottom grip did not capture resize");var up=point(gui,at[0],at[1]-100);gui.mouseDragged(click(up),0,-100);gui.mouseReleased(click(up));require(!gui.isDragging(handle),"Resize remained captured after release");});context.waitTicks(3);
            require(context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).panelResizePlace(Category.VISUALS)[1])<initial-20,"Pulling up did not shrink category");
            context.takeScreenshot("maro-panel-resize-small-"+scale);
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var at=gui.panelResizePlace(Category.VISUALS);var p=point(gui,at[0],at[1]);gui.mouseMoved(p[0],p[1]);gui.mouseClicked(click(p),false);var down=point(gui,at[0],gui.menuHeight()+300);gui.mouseDragged(click(down),0,500);gui.mouseReleased(click(down));});context.waitTicks(3);
            float expanded=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).panelResizePlace(Category.VISUALS)[1]);require(expanded>initial,"Pulling down did not reveal additional rows");
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;require(gui.panelResizePlace(Category.VISUALS)[1]<=gui.menuHeight()-34,"Expanded category covered the footer");gui.getSearch().setText("radar");});context.waitTicks(5);
            context.runOnClient(c->((ClickGuiScreen)c.currentScreen).getSearch().clear());context.waitTicks(5);
            require(Math.abs(context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).panelResizePlace(Category.VISUALS)[1])-expanded)<.1,"Filtering lost chosen category height");
            context.runOnClient(c->{var saved=dev.maro.gui.PanelsView.save();var value=saved.getAsJsonObject(Category.VISUALS.name()).get("height").getAsFloat();require(value>0,"Manual height was not saved");dev.maro.gui.PanelsView.load(saved);require((float)field(visualPanel((ClickGuiScreen)c.currentScreen),"bodyHeight")==value,"Saved category height did not reload");});
            context.takeScreenshot("maro-panel-resize-expanded-"+scale);
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var at=gui.panelResizePlace(Category.VISUALS);var p=point(gui,at[0],at[1]);gui.mouseClicked(new Click(p[0],p[1],new MouseInput(1,0)),false);require((float)field(visualPanel(gui),"bodyHeight")==0,"Right click did not reset automatic height");});context.waitTicks(3);
            var layout=dev.maro.gui.PanelsView.save();
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.panelPlace(Category.VISUALS);var p=point(g,at[0],at[1]);g.mouseMoved(p[0],p[1]);g.mouseClicked(click(p),false);var bottom=point(g,at[0],g.menuHeight()+200);g.mouseDragged(click(bottom),0,500);g.mouseReleased(click(bottom));});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.panelResizePlace(Category.VISUALS)[1]<g.menuHeight()-34,"Dragging header left bottom grip off-screen");dev.maro.gui.PanelsView.load(layout);});context.waitTicks(2);
        }
        System.out.println("[panel-resize] PASS: bottom grip capture, shrink, expand to viewport, outside release, search/clear retains height saved height reload, header bottom bounds and right-click reset at GUI scales 2/4");
    }
    static void run(ClientGameTestContext context){
        dev.maro.gui.widget.ScrollMotionChecks.run();
        var savedPanels=dev.maro.gui.PanelsView.save();String menu=ClientSettings.menuStyle.get();boolean spatial=ClientSettings.spatialMenu.get(),screen=ClientSettings.screenGui.get();int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            for(boolean panels:new boolean[]{false,true})for(int guiScale:new int[]{2,4}){
                context.runOnClient(c->{ClientSettings.menuStyle.set(panels?"Panels":"Window");ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();var gui=new ClickGuiScreen();c.setScreen(gui);if(!panels)gui.openPage(Category.VISUALS.ordinal());});context.waitTicks(20);
                context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var t=track(gui,panels);require(t.scroll.getMax()>0,"Visuals fixture does not overflow");t.scroll.reset();var p=point(gui,t.x,t.y+t.h/2);for(int i=0;i<10;i++)gui.mouseScrolled(p[0],p[1],0,-1);});context.waitTicks(10);
                context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var t=track(gui,panels);require(t.scroll.get()>0,"Ten wheel events did not scroll modules");
                    // Track click centers the thumb and captures it immediately.
                    var p=point(gui,t.x,t.y+t.h*.5);gui.mouseClicked(click(p),false);require(gui.isDragging(t.scroll),"Track did not capture drag");
                    var bottom=point(gui,t.x,t.y+t.h+80);gui.mouseDragged(click(bottom),0,100);require(Math.abs(t.scroll.get()-t.scroll.getMax())<.01,"Drag outside track did not clamp to bottom");gui.mouseReleased(click(bottom));require(!gui.isDragging(t.scroll),"Release left scrollbar stuck");
                    gui.mouseScrolled(p[0],p[1],0,1);
                });context.waitTicks(6);
                context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;var t=track(gui,panels);require(t.scroll.get()<t.scroll.getMax(),"Wheel failed after drag");var p=point(gui,t.x,t.y+t.h*.8);gui.mouseClicked(click(p),false);var top=point(gui,t.x,t.y-100);gui.mouseDragged(click(top),0,-150);require(t.scroll.get()==0,"Drag outside track did not clamp to top");gui.mouseReleased(click(top));t.scroll.scroll(Double.NaN);require(Float.isFinite(t.scroll.update()),"Nonfinite wheel poisoned scroll");t.scroll.setBounds(10,100);require(t.scroll.get()==0&&t.scroll.getMax()==0,"Content shrink left stale offset");});context.waitTicks(2);
                context.takeScreenshot("maro-module-scroll-"+(panels?"panels":"window")+"-"+guiScale);
            }
            resizeChecks(context);
            int selected=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).selectedEntry());
            long enabled=dev.maro.module.ModuleManager.all().stream().filter(dev.maro.module.Module::isEnabled).count();
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("radar");var at=g.layoutPlace(false);require(at!=null,"Window footer control missing");g.mouseClicked(click(point(g,at[0],at[1])),false);});context.waitTicks(7);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(ClientSettings.menuStyle.is("Panel")&&!g.showingPanels(),"Panel footer did not switch layout");require(g.selectedEntry()==selected&&g.getSearch().getText().equals("radar"),"Layout switch discarded category or search");var at=g.layoutPlace(true);g.mouseClicked(click(point(g,at[0],at[1])),false);});context.waitTicks(7);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(ClientSettings.menuStyle.is("Panels")&&g.showingPanels(),"Dropdown footer did not switch layout");require(g.selectedEntry()==selected&&g.getSearch().getText().equals("radar"),"Reverse switch discarded category or search");require(enabled==dev.maro.module.ModuleManager.all().stream().filter(dev.maro.module.Module::isEnabled).count(),"Layout switch toggled modules");g.getSearch().clear();});context.waitTicks(4);
            context.takeScreenshot("maro-gui-layout-switch-dropdown");
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.layoutPlace(false);g.mouseClicked(click(point(g,at[0],at[1])),false);});context.waitTicks(7);
            context.takeScreenshot("maro-gui-layout-switch-window");
            System.out.println("[module-scroll] PASS: ten wheel events, track clicks, captured thumb drag beyond both edges, release outside, reverse wheel after drag and shrink clamp in Panel/Dropdown at GUI scales 2/4; native Panel/Dropdown footer preserves search, category and module states");
        }finally{context.runOnClient(c->{c.setScreen(null);dev.maro.gui.PanelsView.load(savedPanels);ClientSettings.menuStyle.set(menu);ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screen);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }

    static void fullscreenAndSwipeChecks(ClientGameTestContext context){
        var saved=new java.util.IdentityHashMap<dev.maro.setting.Setting<?>,com.google.gson.JsonElement>();
        ClientSettings.ALL.forEach(section->section.getSettings().forEach(setting->saved.put(setting,setting.toJson().deepCopy())));
        int nativeScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.guiFullscreen.set(false);ClientSettings.guiSize.set(75d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.setScreen(new ClickGuiScreen());});context.waitTicks(16);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("client");g.mouseClicked(click(point(g,p[0],p[1])),false);});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("scale");var at=point(g,p[0],p[1]);g.mouseScrolled(at[0],at[1],0,-1);ClientSettings.guiSize.set(60d);});context.waitTicks(1);
            float[] held=context.computeOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("scale");var at=point(g,p[0],p[1]);g.mouseMoved(at[0],at[1]);g.mouseClicked(click(at),false);require(g.isDragging(ClientSettings.guiSize),"Swipe then scale did not capture the slider");var top=point(g,0,0);return new float[]{(float)top[0],(float)top[1]};});
            float scroll=context.computeOnClient(c->((Scroll)field(((ClickGuiScreen)c.currentScreen).cardPanel(),"clientScroll")).get());
            for(int i=0;i<5;i++){
                context.waitTicks(1);context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=point(g,0,0);require(Math.hypot(at[0]-held[0],at[1]-held[1])<.1,"Scale transform drifted under the held pointer");require(Math.abs(((Scroll)field(g.cardPanel(),"clientScroll")).get()-scroll)<.02,"Swipe moved the held scale row");g.mouseScrolled(0,0,0,-5);});
            }
            context.takeScreenshot("maro-swipe-scale-held");
            context.runOnClient(c->((ClickGuiScreen)c.currentScreen).mouseReleased(new Click(0,0,new MouseInput(0,0))));context.waitTicks(16);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var p=g.cardPanel().place("fullscreen");g.mouseClicked(click(point(g,p[0],p[1])),false);require(ClientSettings.guiFullscreen.get(),"Fullscreen control did not switch on");});context.waitTicks(16);
            for(String style:List.of("Panel","Panels"))for(int scale:new int[]{2,4}){
                context.runOnClient(c->{ClientSettings.menuStyle.set(style);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new ClickGuiScreen());});context.waitTicks(15);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var a=point(g,0,0);var b=point(g,g.menuWidth(),g.menuHeight());require((b[0]-a[0])/g.width>.94&&(b[1]-a[1])/g.height>.94,"Fullscreen canvas did not fill the viewport");require(a[0]>=-1&&a[1]>=-1&&b[0]<=g.width+1&&b[1]<=g.height+1,"Fullscreen canvas clipped");
                    var footer=g.layoutPlace(style.equals("Panels"));require(footer!=null,"Fullscreen hid the layout footer");var at=point(g,footer[0],footer[1]);require(at[0]>=0&&at[1]>=0&&at[0]<=g.width&&at[1]<=g.height,"Fullscreen footer left screen");});
                context.takeScreenshot("maro-fullscreen-"+style.toLowerCase()+"-"+scale);
            }
            context.runOnClient(c->{dev.maro.config.ConfigManager.saveClient();try{var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(dev.maro.config.ConfigManager.DIR.resolve("client.json"))).getAsJsonObject();require(json.getAsJsonObject("settings").get("Fullscreen View").getAsBoolean(),"Fullscreen preference was not saved");}catch(java.io.IOException error){throw new AssertionError(error);}
                double custom=ClientSettings.guiSize.get();ClientSettings.guiFullscreen.set(false);require(ClientSettings.guiSize.get()==custom,"Fullscreen replaced custom scale");});context.waitTicks(15);
            System.out.println("[fullscreen-swipe] PASS: native swipe then scale capture, frozen scroll/transform, ignored wheel while dragging, fullscreen toggle, both layouts at GUI scales 2/4, visible footer and saved preference/custom-scale preservation");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(dev.maro.setting.Setting::fromJson);c.options.getGuiScale().setValue(nativeScale);c.onResolutionChanged();});}
    }
}
