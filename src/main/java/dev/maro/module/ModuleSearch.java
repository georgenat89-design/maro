package dev.maro.module;

import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.module.impl.visuals.SkinAccessories;
import java.util.*;

/** Search settings and merged features under the module that owns their visible menu. */
final class ModuleSearch {
    private static final Map<Module,List<String>> FEATURES=new IdentityHashMap<>();
    private ModuleSearch(){}
    static void invalidate(){FEATURES.clear();}
    private static String key(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+","");}
    private static List<String> features(Module module){
        var terms=new ArrayList<String>();
        var section=ModuleSections.of(module);terms.add(section.title());terms.add(section.compactTitle());terms.add(section.title().replace("&"," "));
        module.getSettings().forEach(s->terms.add(s.getName()));
        if(module instanceof dev.maro.module.impl.visuals.Radar)terms.addAll(List.of("nearby players","player radar","Zen Radar","minimap"));
        if(module instanceof TexturePacks){
            terms.addAll(List.of("Better Textures","Better Texures","Better Looks","Texture Packs","resource packs","appearance","graphics","Modrinth","PacksMC"));
            for(String name:List.of("Motion Blur","Bloom","Color Correct","No Render","Potato Graphics")){
                terms.add(name);Module child=ModuleManager.getByName(name);
                if(child!=null)child.getSettings().forEach(s->terms.add(s.getName()));
            }
            terms.addAll(List.of("flat textures","soft textures","color grading"));
        }else if(module instanceof SkinAccessories){
            terms.addAll(List.of("Custom Pickaxe","Fortnite","Minty","Leviathan"));
            terms.addAll(List.of(dev.maro.render.pickaxe.PickaxeModels.NAMES));
            terms.addAll(List.of("skin accessories","hats","hat","sword skins","pickaxe skins","shovel skins","trident skins","accessories"));
            Module hats=ModuleManager.getByName("Hats");if(hats!=null)hats.getSettings().forEach(s->terms.add(s.getName()));
        }
        return terms.stream().map(ModuleSearch::key).filter(s->!s.isEmpty()).distinct().toList();
    }
    static List<Module> find(List<Module> modules,String query){
        String q=query.toLowerCase(Locale.ROOT).trim(),compact=key(q);
        var starts=new ArrayList<Module>();var exactFeatures=new ArrayList<Module>();var contains=new ArrayList<Module>();var settings=new ArrayList<Module>();var descriptions=new ArrayList<Module>();
        for(Module module:modules){
            if(module.hiddenInGui())continue;
            String name=key(module.getName()),label=key(module.getDisplayName());
            if(name.startsWith(compact)||label.startsWith(compact))starts.add(module);
            else if(FEATURES.computeIfAbsent(module,ModuleSearch::features).contains(compact))exactFeatures.add(module);
            else if(name.contains(compact)||label.contains(compact))contains.add(module);
            else if(FEATURES.computeIfAbsent(module,ModuleSearch::features).stream().anyMatch(s->s.contains(compact)))settings.add(module);
            else if(module.getDescription().toLowerCase(Locale.ROOT).contains(q))descriptions.add(module);
        }
        starts.addAll(exactFeatures);starts.addAll(contains);starts.addAll(settings);starts.addAll(descriptions);return starts;
    }
}
