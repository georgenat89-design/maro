package dev.maro.gametest;

import dev.maro.config.ConfigManager;
import dev.maro.gui.hud.DroppedItemPickerScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Nametags;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.*;

final class DroppedItemTagChecks {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Setting<?> setting(Nametags tags,String name){return tags.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static Object field(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static void click(DroppedItemPickerScreen picker,ButtonWidget button){var click=new Click(button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,new MouseInput(0,0));picker.mouseClicked(click,false);picker.mouseReleased(click);}
    static void run(ClientGameTestContext context){
        var tags=ModuleManager.get(Nametags.class);
        var saved=new IdentityHashMap<Setting<?>,com.google.gson.JsonElement>();tags.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy()));
        var entities=new ArrayList<ItemEntity>();
        var perspective=context.computeOnClient(c->c.options.getPerspective());
        int guiScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            context.runOnClient(c->{
                c.setScreen(null);tags.getSettings().forEach(Setting::reset);tags.setEnabled(true);
                c.options.setPerspective(Perspective.FIRST_PERSON);c.player.setYaw(0);c.player.setPitch(0);
                ((BooleanSetting)setting(tags,"Item Tags")).set(true);
                ((ItemSelectionSetting)setting(tags,"Selected Items")).set(Set.of());
                for(int i=0;i<3;i++){
                    var item=new ItemEntity(c.world,c.player.getX()+(i-1)*1.5,c.player.getY()+.5,c.player.getZ()+5,
                            new ItemStack(i==0?Items.TOTEM_OF_UNDYING:i==1?Items.DIAMOND:Items.COBBLESTONE,i==0?7:i==1?32:64));
                    item.setNoGravity(true);item.setVelocity(net.minecraft.util.math.Vec3d.ZERO);item.setId(-973201-i);c.world.addEntity(item);entities.add(item);
                }
            });context.waitTicks(4);
            context.runOnClient(c->{require(tags.drawnItemTags().isEmpty(),"Empty selection labelled drops");((ButtonSetting)setting(tags,"Choose Items")).press();});context.waitTicks(3);
            require(context.computeOnClient(c->c.currentScreen instanceof DroppedItemPickerScreen),"Choose Items did not open picker");
            context.getInput().typeChars("totem");context.waitTicks(3);
            context.runOnClient(c->{
                var picker=(DroppedItemPickerScreen)c.currentScreen;var rows=(List<ButtonWidget>)field(picker,"rows");
                require(rows.size()==1&&rows.getFirst().getMessage().getString().contains("Totem"),"Search did not isolate totem");click(picker,rows.getFirst());
                require(((ItemSelectionSetting)setting(tags,"Selected Items")).contains(Items.TOTEM_OF_UNDYING),"Click failed to select item");
            });context.waitTicks(3);context.takeScreenshot("maro-dropped-item-picker-totem");
            context.runOnClient(c->{
                var picker=(DroppedItemPickerScreen)c.currentScreen;var search=(TextFieldWidget)field(picker,"search");search.setText("minecraft:diamond");
                var rows=(List<ButtonWidget>)field(picker,"rows");var diamond=rows.stream().filter(b->b.getMessage().getString().endsWith("· minecraft:diamond")).findFirst().orElseThrow();
                click(picker,diamond);require(((ItemSelectionSetting)setting(tags,"Selected Items")).get().size()==2,"Multi-select replaced the previous selection");
                search.setText("");click(picker,(ButtonWidget)field(picker,"selectedButton"));
                require(((List<?>)field(picker,"rows")).size()==2,"Selected-only filter wrong");
            });context.waitTicks(3);context.takeScreenshot("maro-dropped-item-picker-selected");
            for(int scale:new int[]{3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});context.waitTicks(3);
                context.runOnClient(c->{var picker=(DroppedItemPickerScreen)c.currentScreen;var rows=(List<ButtonWidget>)field(picker,"rows");require(!rows.isEmpty()&&rows.stream().allMatch(b->b.getX()>=0&&b.getY()+b.getHeight()<=picker.height-30),"Picker clipped at GUI scale "+scale);});
                context.takeScreenshot("maro-dropped-item-picker-scale-"+scale);
            }
            context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();});context.waitTicks(3);
            context.runOnClient(c->{
                var picker=(DroppedItemPickerScreen)c.currentScreen;click(picker,(ButtonWidget)field(picker,"selectedButton"));
                picker.mouseScrolled(picker.width/2d,picker.height/2d,0,-100);require((int)field(picker,"firstRow")>0,"Picker did not scroll");
                var rows=(List<ButtonWidget>)field(picker,"rows");require(rows.size()<=20,"Picker built offscreen widgets");
                picker.close();
                var selection=(ItemSelectionSetting)setting(tags,"Selected Items");var json=selection.toJson();selection.set(Set.of());selection.fromJson(json);
                require(selection.contains(Items.DIAMOND)&&selection.contains(Items.TOTEM_OF_UNDYING),"Selection JSON did not roundtrip");
                selection.fromJson(com.google.gson.JsonParser.parseString("[\"minecraft:totem_of_undying\",\"missing:removed_item\",null,32,{},\"minecraft:air\"]"));
                require(selection.get().equals(Set.of(Items.TOTEM_OF_UNDYING)),"Unknown/malformed item IDs were not ignored");
            });context.waitTicks(4);
            context.runOnClient(c->{
                var labels=tags.drawnItemTags();require(labels.size()==1&&labels.getFirst().contains("Totem")&&labels.getFirst().contains("×7")&&labels.getFirst().contains("m"),"Ground label content/filter wrong: "+labels);
                try{var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ConfigManager.CONFIG_DIR.resolve(ConfigManager.getCurrent()+".json"))).getAsJsonObject();
                    require(json.getAsJsonObject("modules").getAsJsonObject("Nametags").getAsJsonObject("settings").getAsJsonArray("Selected Items").size()==2,"Picker did not save selections to disk");
                }catch(java.io.IOException error){throw new AssertionError(error);}
            });context.takeScreenshot("maro-dropped-item-tags");
            context.runOnClient(c->((BooleanSetting)setting(tags,"Distance")).set(false));context.waitTicks(3);
            context.runOnClient(c->{
                require(tags.drawnItemTags().getFirst().matches(".*\\s\\d+m$"),"Player distance toggle hid item distance");
                ((BooleanSetting)setting(tags,"Item Distance")).set(false);
            });context.waitTicks(3);
            context.runOnClient(c->{
                require(!tags.drawnItemTags().getFirst().matches(".*\\s\\d+m$"),"Item Distance off still shows metres");
                require(tags.drawnItemTags().getFirst().contains("Totem")&&tags.drawnItemTags().getFirst().contains("×7"),"Hiding distance lost item name/count");
                ConfigManager.saveAll();
                try{
                    var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ConfigManager.CONFIG_DIR.resolve(ConfigManager.getCurrent()+".json"))).getAsJsonObject();
                    var stored=json.getAsJsonObject("modules").getAsJsonObject("Nametags").getAsJsonObject("settings").get("Item Distance");
                    var option=(BooleanSetting)setting(tags,"Item Distance");option.set(true);option.fromJson(stored);
                    require(!option.get(),"Item distance preference did not save/reload");
                }catch(java.io.IOException error){throw new AssertionError(error);}
                ((BooleanSetting)setting(tags,"Distance")).set(true);
            });context.waitTicks(3);context.takeScreenshot("maro-item-nametags-distance-off");
            context.runOnClient(c->{
                require(!tags.drawnItemTags().getFirst().matches(".*\\s\\d+m$"),"Player distance toggle re-enabled item distance");
                ((BooleanSetting)setting(tags,"Item Distance")).set(true);
            });context.waitTicks(3);
            context.runOnClient(c->require(tags.drawnItemTags().getFirst().matches(".*\\s\\d+m$"),"Item distance did not reappear live"));
            context.takeScreenshot("maro-item-nametags-distance-on");
            context.runOnClient(c->((ItemSelectionSetting)setting(tags,"Selected Items")).set(Set.of(Items.DIAMOND)));context.waitTicks(3);
            context.runOnClient(c->{require(tags.drawnItemTags().size()==1&&tags.drawnItemTags().getFirst().contains("Diamond")&&tags.drawnItemTags().getFirst().contains("×32"),"Selection did not update live");entities.get(1).setPosition(c.player.getX(),c.player.getY(),c.player.getZ()+20);((NumberSetting)setting(tags,"Item Range")).set(8d);});context.waitTicks(3);
            context.runOnClient(c->{require(tags.drawnItemTags().isEmpty(),"Item range filter ignored");((NumberSetting)setting(tags,"Item Range")).set(64d);((ButtonSetting)setting(tags,"Choose Items")).press();});context.waitTicks(3);
            context.runOnClient(c->{
                var picker=(DroppedItemPickerScreen)c.currentScreen;
                ((TextFieldWidget)field(picker,"search")).setText("totem");
                var button=picker.children().stream().filter(e->e instanceof ButtonWidget b&&b.getMessage().getString().equals("All Blocks")).map(e->(ButtonWidget)e).findFirst().orElseThrow();
                click(picker,button);
                var selection=(ItemSelectionSetting)setting(tags,"Selected Items");
                var expected=net.minecraft.registry.Registries.ITEM.stream().filter(item->item!=Items.AIR&&item instanceof net.minecraft.item.BlockItem).collect(java.util.stream.Collectors.toSet());
                require(expected.size()>500&&selection.get().equals(expected),"All Blocks did not select every registered block item");
                require(!selection.contains(Items.DIAMOND)&&!selection.contains(Items.TOTEM_OF_UNDYING)&&!selection.contains(Items.AIR),"All Blocks kept non-block items");
                require(((TextFieldWidget)field(picker,"search")).getText().isEmpty()&&(boolean)field(picker,"selectedOnly"),"All Blocks left the previous search hiding the selection");
            });context.waitTicks(3);context.takeScreenshot("maro-nametags-all-blocks-picker");
            context.runOnClient(c->{
                ((DroppedItemPickerScreen)c.currentScreen).close();
                try{
                    var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ConfigManager.CONFIG_DIR.resolve(ConfigManager.getCurrent()+".json"))).getAsJsonObject();
                    var stored=json.getAsJsonObject("modules").getAsJsonObject("Nametags").getAsJsonObject("settings").getAsJsonArray("Selected Items");
                    var selection=(ItemSelectionSetting)setting(tags,"Selected Items");var expected=selection.get();selection.set(Set.of());selection.fromJson(stored);
                    require(selection.get().equals(expected),"All Blocks selection did not persist to disk and reload");
                }catch(java.io.IOException error){throw new AssertionError(error);}
            });context.waitTicks(3);
            context.runOnClient(c->require(tags.drawnItemTags().size()==1&&tags.drawnItemTags().getFirst().contains("Cobblestone")&&tags.drawnItemTags().getFirst().contains("×64"),"All Blocks failed to label only dropped blocks: "+tags.drawnItemTags()));
            context.takeScreenshot("maro-nametags-all-blocks-labels");
            context.runOnClient(c->((BooleanSetting)setting(tags,"Item Tags")).set(false));context.waitTicks(3);
            context.runOnClient(c->require(tags.drawnItemTags().isEmpty(),"Off switch left item tags behind"));
            System.out.println("[dropped-item-tags] PASS: native name/ID search, click multi-select, selected-only list, bounded scroll widgets, saved registry IDs, malformed-ID handling, real ground labels/count/distance, live filter, range and off switch");
            System.out.println("[nametags-all-blocks] PASS: native All Blocks click replaces selection with every registered block item, clears stale search, excludes non-blocks, saves/reloads and labels live dropped cobblestone");
            System.out.println("[item-nametag-distance] PASS: live item distance on/off is independent of player distance, preserves item name/count and survives config save/reload");
        }finally{context.runOnClient(c->{entities.forEach(ItemEntity::discard);tags.setEnabled(false);saved.forEach(Setting::fromJson);c.setScreen(null);c.options.setPerspective(perspective);c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();});}
    }
}
