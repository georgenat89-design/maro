package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.gui.hud.*;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.*;

/** Native coverage for merged settings search, the Potato section and the cosmetics picker. */
final class VisualStudioChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(net.minecraft.client.gui.screen.Screen screen,double[] p){screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);}
    private static String video(net.minecraft.client.MinecraftClient c){return c.options.getViewDistance().getValue()+"|"+c.options.getAo().getValue()+"|"+c.options.getParticles().getValue()+"|"+c.options.getMipmapLevels().getValue()+"|"+c.options.getMaxFps().getValue();}
    static void run(ClientGameTestContext context){
        var owner=TexturePacks.get();var potato=ModuleManager.get(PotatoGraphics.class);var cosmetics=ModuleManager.get(SkinAccessories.class);
        int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());boolean enabled=potato.isEnabled(),wearing=cosmetics.isEnabled();
        var mode=(ModeSetting)potato.getSettings().stream().filter(s->s.getName().equals("Textures")).findFirst().orElseThrow();String style=mode.get();
        var saved=new LinkedHashMap<Setting<?>,JsonElement>();cosmetics.getSettings().forEach(s->saved.put(s,s.toJson()));
        try{
            context.runOnClient(c->{
                for(String query:List.of("Motion Blur","motionblur","motion-blur","BLOOM","Potato Graphics","flat textures","better textures","better texures","Gamma Boost")){
                    var matches=ModuleManager.search(query);require(!matches.isEmpty()&&matches.getFirst()==owner,"Merged feature search missed Gameplay Textures: "+query);
                    require(matches.stream().distinct().count()==matches.size(),"Search duplicated an owner module");
                }
                require(ModuleManager.search("sword skins").contains(cosmetics)&&ModuleManager.search("hats").contains(cosmetics),"Cosmetics feature aliases missing");
                require(potato.hiddenInGui()&&ModuleManager.byCategory(potato.getCategory()).stream().noneMatch(m->m==potato),"Potato remains a duplicate visible module");
                potato.setEnabled(false);mode.set("Flat");c.options.getGuiScale().setValue(2);c.onResolutionChanged();
            });
            context.waitTicks(3);context.waitFor(c->PotatoGraphics.texturesSettled(),2400);
            String before=context.computeOnClient(VisualStudioChecks::video);
            var appearance=context.computeOnClient(c->{var s=new BetterLooksScreen(null,owner.looks());require(s.sectionTitles().contains("Potato Graphics"),"Potato is absent from Appearance");s.showSection(s.sectionTitles().indexOf("Potato Graphics"));c.setScreen(s);return s;});
            context.waitTicks(5);context.takeScreenshot("maro-appearance-potato-section");
            context.runOnClient(c->click(appearance,appearance.rowPoint("Potato Graphics")));context.waitTicks(3);context.waitFor(c->PotatoGraphics.texturesSettled(),2400);
            require(potato.isEnabled()&&PotatoGraphics.flattenedSprites()>200,"Appearance toggle did not enable flat block textures");
            context.runOnClient(c->mode.set("Soft"));context.waitTicks(3);context.waitFor(c->PotatoGraphics.texturesSettled(),2400);context.takeScreenshot("maro-appearance-potato-soft");
            require(before.equals(context.computeOnClient(VisualStudioChecks::video)),"Potato changed the user's video settings");
            context.runOnClient(c->click(appearance,appearance.rowPoint("Potato Graphics")));context.waitTicks(3);context.waitFor(c->PotatoGraphics.texturesSettled(),2400);
            require(!potato.isEnabled(),"Appearance toggle did not restore normal block textures");

            for(int scale:new int[]{2,3,4}){
                var screen=context.computeOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();cosmetics.setEnabled(false);var s=new SkinAccessoriesScreen(null,cosmetics);c.setScreen(s);s.showTab("Sword");return s;});context.waitTicks(5);
                context.takeScreenshot("maro-cosmetics-clean-scale-"+scale);
                context.runOnClient(c->{screen.search("chrono");require(!screen.shownChoices().isEmpty()&&screen.shownChoices().stream().allMatch(s->s.toLowerCase(Locale.ROOT).contains("chrono")),"Cosmetics search failed");});context.waitTicks(3);
                String expected=screen.shownChoices().getFirst();context.runOnClient(c->click(screen,screen.choicePoint(0)));
                var sword=(ModeSetting)cosmetics.getSettings().stream().filter(s->s.getName().equals("Sword")).findFirst().orElseThrow();require(sword.get().equals(expected)&&!cosmetics.isEnabled(),"Selecting a searched style missed or enabled cosmetics");
                context.takeScreenshot("maro-cosmetics-search-scale-"+scale);
                context.runOnClient(c->click(screen,screen.categoryPoint()));context.waitTicks(3);context.takeScreenshot("maro-cosmetics-categories-scale-"+scale);
                context.runOnClient(c->click(screen,screen.categoryChoicePoint("Pickaxe")));context.waitTicks(3);
                require(screen.shownChoices().size()==38,"Category dropdown did not select the 37 pickaxe styles plus None");
                context.runOnClient(c->{screen.showTab("Colors");screen.mouseScrolled(screen.choicePoint(0)[0],screen.choicePoint(0)[1],0,-20);});context.waitTicks(12);context.takeScreenshot("maro-cosmetics-colors-scale-"+scale);
            }
            System.out.println("[visual-studio] PASS: merged feature/setting searches, hidden Potato owner, native Flat/Soft toggle and restoration, unchanged video options, cosmetics filtering/selection/category picker at 3 GUI scales");
        }finally{
            context.runOnClient(c->{c.setScreen(null);potato.setEnabled(false);mode.set(style);potato.setEnabled(enabled);saved.forEach(Setting::fromJson);cosmetics.setEnabled(wearing);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});context.waitTicks(3);context.waitFor(c->PotatoGraphics.texturesSettled(),2400);
        }
    }
}
