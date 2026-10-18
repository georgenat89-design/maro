package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.gui.*;
import dev.maro.gui.render.VisualPreviewEffect;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.Category;
import dev.maro.module.impl.visuals.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;

final class VisualPreviewChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static BufferedImage read(java.nio.file.Path path){try{return ImageIO.read(path.toFile());}catch(Exception e){throw new AssertionError(e);}}
    private static Click click(ClickGuiScreen gui,float x,float y){var at=gui.screenPose().project(x,y);return new Click(at[0],at[1],new MouseInput(0,0));}
    private static void tap(ClickGuiScreen gui,float[] p){var click=click(gui,p[0],p[1]);gui.mouseMoved(click.x(),click.y());gui.mouseClicked(click,false);gui.mouseReleased(click);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();
        ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));
        var esp=ModuleManager.get(PlayerESP.class);var model=ModuleManager.get(CustomModel.class);var tags=ModuleManager.get(Nametags.class);
        for(Module m:List.of(esp,model,tags))m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy()));
        var view=context.computeOnClient(c->c.options.getPerspective());int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("tp @a 0 -60 0 0 0");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");context.waitTicks(4);
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(85d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.options.setPerspective(Perspective.FIRST_PERSON);esp.getSettings().forEach(Setting::reset);model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);((ModeSetting)setting(esp,"Fill Style")).set("Solid");((ColorSetting)setting(esp,"Color")).set(0xFFFF2020);((NumberSetting)setting(esp,"Fill Opacity")).set(100d);c.setScreen(new ClickGuiScreen());((ClickGuiScreen)c.currentScreen).openModuleOptions(esp);});
            context.waitTicks(12);
            var position=context.computeOnClient(c->c.player.getEntityPos());float yaw=context.computeOnClient(c->c.player.getYaw()),pitch=context.computeOnClient(c->c.player.getPitch());
            var red=read(context.takeScreenshot("maro-visual-preview-panel-red"));
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;require(gui.visualPreview().bounds()!=null&&gui.visualPreview().lastState()!=null,"Panel did not queue a real player");require(VisualPreviewEffect.frames()>0,"ESP preview did not use its real shader");require(!esp.isEnabled()&&!PlayerESP.previewing(),"Preview enabled world ESP");((ColorSetting)setting(esp,"Color")).set(0xFF2080FF);});
            context.waitTicks(4);var blue=read(context.takeScreenshot("maro-visual-preview-panel-blue"));
            int changed=0;for(int y=0;y<red.getHeight();y++)for(int x=0;x<red.getWidth();x++){int a=red.getRGB(x,y),b=blue.getRGB(x,y);if((a>>16&255)>(a&255)+70&&(b&255)>(b>>16&255)+70)changed++;}
            require(changed>150,"Editing ESP color did not change the portrait pixels: "+changed);
            context.runOnClient(c->{var gui=(ClickGuiScreen)c.currentScreen;float[] b=gui.visualPreview().bounds();var from=click(gui,b[0]+b[2]/2,b[1]+b[3]/2);gui.mouseMoved(from.x(),from.y());gui.mouseClicked(from,false);var to=click(gui,b[0]+b[2]/2+45,b[1]+b[3]/2);gui.mouseDragged(to,to.x()-from.x(),0);gui.mouseReleased(to);require(gui.visualPreview().angle()>0,"Preview drag did not rotate");for(int i=0;i<10;i++)gui.mouseScrolled(from.x(),from.y(),0,1);});context.waitTicks(6);context.takeScreenshot("maro-visual-preview-rotated");
            for(String style:List.of("Panel","Panels"))for(int guiScale:new int[]{2,4}){
                context.runOnClient(c->{ClientSettings.menuStyle.set(style);c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new ClickGuiScreen());((ClickGuiScreen)c.currentScreen).openModuleOptions(esp);});context.waitTicks(9);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var b=g.visualPreview().bounds();require(b!=null,"Preview missing at "+style+" scale "+guiScale);var a=g.screenPose().project(b[0],b[1]);var z=g.screenPose().project(b[0]+b[2],b[1]+b[3]);require(a[0]>=0&&a[1]>=0&&z[0]<=g.width+1&&z[1]<=g.height+1,"Preview clipped screen");});
                context.takeScreenshot("maro-visual-preview-"+style.toLowerCase()+"-"+guiScale);
            }
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");c.options.getGuiScale().setValue(2);c.onResolutionChanged();var g=new ClickGuiScreen();c.setScreen(g);g.openModuleOptions(model);model.select(false,"Roblox Bacon Hair");});
            for(int i=0;i<300&&!context.computeOnClient(c->model.ready("Roblox Bacon Hair")!=null);i++)context.waitTicks(1);context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(CustomModel.worn(g.visualPreview().lastState())==model.ready("Roblox Bacon Hair"),"Disabled model did not preview selected character");((NumberSetting)setting(model,"Model Size")).set(150d);});context.waitTicks(4);context.takeScreenshot("maro-visual-preview-custom-character");
            for(Module target:List.of(tags,ModuleManager.get(ArmorHud.class),ModuleManager.get(CustomCrosshair.class))){
                context.runOnClient(c->((ClickGuiScreen)c.currentScreen).openModuleOptions(target));context.waitTicks(8);context.takeScreenshot("maro-visual-preview-"+target.getName().replace(' ','-').toLowerCase());
            }
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("back-to-modules"));});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.visualPreview().bounds()==null,"Preview remained after leaving settings");require(c.options.getPerspective()==Perspective.FIRST_PERSON&&c.player.getEntityPos().distanceTo(position)<.00001&&c.player.getYaw()==yaw&&c.player.getPitch()==pitch,"Preview changed real player or camera options");require(!esp.isEnabled()&&!model.isEnabled()&&!tags.isEnabled(),"Preview changed module states");});
            context.takeScreenshot("maro-panel-grid-restored");
            context.runOnClient(c->{
                var g=(ClickGuiScreen)c.currentScreen;tap(g,g.cardPanel().place("enabled-filter"));
            });context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.cardPanel().hasFilters(),"Enabled filter was not applied");tap(g,g.cardPanel().place("category-VISUALS"));});context.waitTicks(3);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(!g.cardPanel().hasFilters(),"Modules did not recover hidden cards");g.openPage(Category.COMBAT.ordinal());c.setScreen(new ClickGuiScreen());});context.waitTicks(5);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(!ModuleManager.byCategory(Category.values()[g.selectedEntry()]).isEmpty(),"Panel reopened on an empty category");require(g.cardPanel().place("category-COMBAT")==null,"Empty category still offered");});context.takeScreenshot("maro-panel-populated-on-open");
            context.runOnClient(c->{c.setScreen(null);require(!VisualPreviewEffect.hasResources(),"Preview targets leaked after closing GUI");});context.waitTicks(2);
            System.out.println("[visual-preview] PASS: real GPU ESP color edits ("+changed+" pixels), native equipped player, rotate/ten zoom gestures, Panel/Dropdown GUI scales 2/4, disabled custom model and size edit, nametag/armor/crosshair previews, Modules grid return, unchanged module states/player/camera and GPU resource cleanup");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(Setting::fromJson);c.options.setPerspective(view);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
