package dev.maro.gametest;

import com.google.gson.JsonParser;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.nio.file.Files;
import java.util.List;

/** Native clicks in both layouts use the same saved Frosted Glass control. */
final class FrostedGlassChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Object field(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static Object value(Object record,String name){try{var method=record.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(record);}catch(Exception e){throw new AssertionError(e);}}
    private static void toggle(ClickGuiScreen gui){
        for(Object hit:(List<?>)field(gui,"hits")){
            Object action=value(hit,"handler");boolean owns=false;
            for(var f:action.getClass().getDeclaredFields())try{f.setAccessible(true);owns|=f.get(action)==ClientSettings.backgroundBlur;}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            if(!owns)continue;
            float x=(float)value(hit,"x")+(float)value(hit,"w")/2,y=(float)value(hit,"y")+(float)value(hit,"h")/2;
            var p=gui.screenPose().project(x,y);var click=new Click(p[0],p[1],new MouseInput(0,0));gui.mouseMoved(p[0],p[1]);gui.mouseClicked(click,false);gui.mouseReleased(click);return;
        }
        throw new AssertionError("Frosted Glass has no visible input control");
    }
    static void run(ClientGameTestContext context){
        String style=ClientSettings.menuStyle.get();boolean blur=ClientSettings.backgroundBlur.get(),spatial=ClientSettings.spatialMenu.get(),screen=ClientSettings.screenGui.get();
        var file=ConfigManager.DIR.resolve("client.json");byte[] original;
        try{original=Files.exists(file)?Files.readAllBytes(file):null;}catch(Exception e){throw new AssertionError(e);}
        try{
            context.runOnClient(c->{try{
                ConfigManager.saveClient();var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var settings=root.getAsJsonObject("settings");
                settings.remove("Frosted Glass");settings.addProperty("Background Blur",true);Files.writeString(file,root.toString());ClientSettings.backgroundBlur.set(false);ConfigManager.loadClient();require(ClientSettings.backgroundBlur.get(),"Legacy blur preference lost");
                settings.addProperty("Frosted Glass",false);Files.writeString(file,root.toString());ConfigManager.loadClient();require(!ClientSettings.backgroundBlur.get(),"Legacy preference overrode newer Frosted Glass choice");
            }catch(Exception e){throw new AssertionError(e);}
                ClientSettings.menuStyle.set("Panels");ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.setScreen(new ClickGuiScreen());});context.waitTicks(15);
            context.runOnClient(c->((ClickGuiScreen)c.currentScreen).openPanelPage("Settings"));context.waitTicks(12);
            context.runOnClient(c->{require(ClientSettings.backgroundBlur.getName().equals("Frosted Glass"),"Layouts use different control names");toggle((ClickGuiScreen)c.currentScreen);require(ClientSettings.backgroundBlur.get(),"Dropdown Frosted Glass did not enable blur");});context.waitTicks(5);context.takeScreenshot("maro-dropdown-frosted-glass");
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");c.setScreen(new ClickGuiScreen());});context.waitTicks(15);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var at=g.cardPanel().place("client");var p=g.screenPose().project(at[0],at[1]);g.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);});context.waitTicks(5);
            context.runOnClient(c->{toggle((ClickGuiScreen)c.currentScreen);require(!ClientSettings.backgroundBlur.get(),"Panel Frosted Glass did not share Dropdown's value");});context.waitTicks(4);context.takeScreenshot("maro-panel-shared-frosted-glass");
            System.out.println("[frosted-glass] PASS: saved Background Blur migration and canonical preference; actual Dropdown Settings toggle enables, Panel Client toggle disables the same setting");
        }finally{context.runOnClient(c->{c.setScreen(null);ClientSettings.menuStyle.set(style);ClientSettings.backgroundBlur.set(blur);ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screen);});try{if(original==null)Files.deleteIfExists(file);else Files.write(file,original);}catch(Exception e){throw new AssertionError(e);}}
    }
}
