package dev.maro.gametest;

import dev.maro.anubis.inspect.WeaponInspectModule;
import dev.maro.config.ClientSettings;
import dev.maro.config.SharedModuleConfig;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.SkinAccessoriesScreen;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomPickaxe;
import dev.maro.module.impl.visuals.ItemInspect;
import dev.maro.render.pickaxe.PickaxeModels;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.network.OtherClientPlayerEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import java.util.*;

/** Runs the real gallery and production item renderer, including legacy inspection coexistence. */
final class CustomPickaxeChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(SkinAccessories m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static Click click(double[] point){return new Click(point[0],point[1],new MouseInput(0,0));}
    private static void equip(ClientGameTestContext context,TestSingleplayerContext world,ItemStack stack){
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getInventory().setSelectedSlot(0);p.getInventory().setStack(0,stack.copy());});
        context.waitTicks(4);context.runOnClient(c->{c.player.getInventory().setSelectedSlot(0);c.player.getInventory().setStack(0,stack.copy());});context.waitTicks(8);
    }
    private static void migration(){
        var root=com.google.gson.JsonParser.parseString("{\"Custom Pickaxe\":{\"enabled\":true,\"bind\":82,\"settings\":{\"Pickaxe\":\"Star Wand\",\"Size\":125,\"Metal Shine\":40,\"Outline\":true}},\"Cosmetics\":{\"enabled\":false,\"settings\":{\"Wings\":\"Angel\",\"Pickaxe\":\"Ruby Pickaxe\"}}}").getAsJsonObject();
        dev.maro.render.pickaxe.PickaxeMigration.apply(root);var merged=root.getAsJsonObject("Cosmetics");require(!root.has("Custom Pickaxe")&&merged.get("enabled").getAsBoolean()&&merged.get("bind").getAsInt()==82&&merged.getAsJsonObject("settings").get("Item Size").getAsDouble()==1.25&&merged.getAsJsonObject("settings").get("Pickaxe").getAsString().equals("Star Wand")&&merged.getAsJsonObject("settings").get("Wings").getAsString().equals("Angel"),"Migration lost a legacy selection, bind or accessories");
        var old=com.google.gson.JsonParser.parseString("{\"enabled\":true,\"settings\":{\"Pickaxe\":\"Driver\"}}").getAsJsonObject();root.add("Custom Pickaxe",old);dev.maro.render.pickaxe.PickaxeMigration.apply(root);require(merged.getAsJsonObject("settings").get("Pickaxe").getAsString().equals("Star Wand"),"Old data overwrote unified settings");
        var disabled=new com.google.gson.JsonObject();old.addProperty("enabled",false);disabled.add("Custom Pickaxe",old);dev.maro.render.pickaxe.PickaxeMigration.apply(disabled);require(!disabled.has("Cosmetics")&&!disabled.has("Custom Pickaxe"),"Disabled legacy module activated Cosmetics");
    }
    private static void sounds(ClientGameTestContext context,TestSingleplayerContext world,SkinAccessories m){
        world.getServer().runCommand("setblock 8 79 10 stone");context.waitTicks(4);
        context.runOnClient(c->{c.setScreen(null);m.select("Leviathan Axe");m.setEnabled(true);m.audio().reset();int before=m.audio().played();m.onTick();require(m.audio().played()==before+1,"Equip effect missing");m.onTick();require(m.audio().played()==before+1,"Equip repeats every tick");c.player.handSwinging=false;c.player.swingHand(Hand.MAIN_HAND);require(m.audio().played()==before+2,"Actual swing hook did not sound");for(int i=0;i<20;i++)dev.maro.render.pickaxe.PickaxeSounds.swing();require(m.audio().played()==before+2,"Repeated swings flood audio");dev.maro.render.pickaxe.PickaxeSounds.impact();require(m.audio().played()==before+2,"Contact doubles the recorded swing sound");});
        context.waitTicks(8);
        context.runOnClient(c->{int before=m.audio().played();require(c.interactionManager.attackBlock(new net.minecraft.util.math.BlockPos(8,79,10),net.minecraft.util.math.Direction.UP),"Creative mining fixture rejected attack");require(m.audio().played()==before+1,"Actual instant-mining impact hook missing");for(int i=0;i<20;i++)dev.maro.render.pickaxe.PickaxeSounds.impact();require(m.audio().played()==before+1,"Mining floods audio");m.pickaxeSounds.set(false);m.onTick();require(!m.audio().playing(),"Sound toggle left an active voice");dev.maro.render.pickaxe.PickaxeSounds.swing();dev.maro.render.pickaxe.PickaxeSounds.impact();m.onTick();require(m.audio().played()==before+1,"Sound toggle ignored");m.pickaxeSounds.set(true);m.pickaxeVolume.set(0d);m.audio().preview(m);require(m.audio().played()==before+1,"Muted preview still played");m.pickaxeVolume.reset();m.setEnabled(false);dev.maro.render.pickaxe.PickaxeSounds.swing();require(m.audio().played()==before+1,"Disabled Cosmetics sounded");});context.waitTicks(4);
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var m=ModuleManager.get(SkinAccessories.class);require(m!=null&&ModuleManager.getByName("Custom Pickaxe")==null,"Pickaxes not merged into Cosmetics");var saved=SharedModuleConfig.capture(m);
        var inspect=ModuleManager.get(ItemInspect.class);var inspectSaved=SharedModuleConfig.capture(inspect);var weapon=ModuleManager.get(WeaponInspectModule.class);
        var arm=context.computeOnClient(c->c.options.getMainArm().getValue());var perspective=context.computeOnClient(c->c.options.getPerspective());
        int guiScale=context.computeOnClient(c->c.options.getGuiScale().getValue());boolean spatial=ClientSettings.spatialMenu.get(),screen=ClientSettings.screenGui.get();
        try{
            var counts=new HashSet<Integer>();
            for(String name:PickaxeModels.NAMES){var mesh=PickaxeModels.get(name);require(mesh.finite()&&mesh.faces()>50&&mesh.faces()<12000,"Broken or unbounded mesh: "+name+"/"+mesh.faces());for(var v:mesh.bounds)require(Math.abs(v.x())<2&&Math.abs(v.y())<2&&Math.abs(v.z())<2,"Mesh exceeds tool bounds");counts.add(mesh.faces());}
            require(counts.size()==5,"Pickaxe silhouettes unexpectedly share geometry");
            require(java.util.Arrays.equals(dev.maro.render.accessories.CosmeticItems.choices(dev.maro.render.accessories.CosmeticItems.Kind.PICKAXE),new String[]{"None","Driver","Merry Mint Axe","Leviathan Axe","Cold Snap","Star Wand"}),"Unlisted pickaxes remain in Cosmetics");
            for(String name:PickaxeModels.NAMES){
                var start=dev.maro.render.pickaxe.PickaxeMotion.sample(name,0);var end=dev.maro.render.pickaxe.PickaxeMotion.sample(name,1);require(start.equals(end),"Swing does not return exactly to rest");
                var prior=start;for(int i=1;i<=1000;i++){var at=dev.maro.render.pickaxe.PickaxeMotion.sample(name,i/1000f);require(Float.isFinite(at.x()+at.y()+at.z()+at.pitch()+at.yaw()+at.roll())&&Math.abs(at.pitch()-prior.pitch())<2,"Swing discontinuity: "+name);prior=at;}
            }
            migration();
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 8 80 8 0 8");context.waitTicks(8);
            context.runOnClient(c->{m.getSettings().forEach(Setting::reset);m.setEnabled(true);c.player.getAbilities().flying=true;c.options.getMainArm().setValue(Arm.RIGHT);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.onResolutionChanged();ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);var g=(SkinAccessoriesScreen)m.panel(new ClickGuiScreen());g.showTab("Pickaxe");m.select("Driver");c.setScreen(g);});context.waitTicks(4);
            context.takeScreenshot("maro-pickaxe-gallery-driver");
            context.runOnClient(c->{var g=(SkinAccessoriesScreen)c.currentScreen;g.search("minty");require(g.shownChoices().equals(List.of("Merry Mint Axe")),"Minty alias search missing");g.search("");});
            for(String name:PickaxeModels.NAMES){
                context.runOnClient(c->{var gallery=(SkinAccessoriesScreen)c.currentScreen;gallery.showTab("Pickaxe");gallery.search(name);});context.waitTicks(2);
                context.runOnClient(c->{var gallery=(SkinAccessoriesScreen)c.currentScreen;int index=gallery.shownChoices().indexOf(name);require(index>=0,"Missing unified choice: "+name);gallery.mouseClicked(click(gallery.choicePoint(index)),false);require(m.selected().equals(name),"Gallery selection failed: "+name);});context.waitTicks(2);context.takeScreenshot("maro-pickaxe-gallery-"+PickaxeModels.key(name));
            }
            context.runOnClient(c->{var g=(SkinAccessoriesScreen)c.currentScreen;g.search("");m.select("Leviathan Axe");g.showAngle(0);});context.waitTicks(3);context.takeScreenshot("maro-pickaxe-unified-gallery");
            context.runOnClient(c->{var g=(SkinAccessoriesScreen)c.currentScreen;var b=g.previewBounds();double scale=c.getWindow().getScaleFactor(),px=(b[0]+b[2])/2/scale,py=(b[1]+b[3])/2/scale;g.mouseClicked(click(new double[]{px,py}),false);g.mouseDragged(click(new double[]{px+30,py+15}),30,15);g.mouseReleased(click(new double[]{px+30,py+15}));for(int i=0;i<40;i++)g.mouseScrolled(px,py,0,1);m.audio().preview(m);});context.waitTicks(3);context.takeScreenshot("maro-pickaxe-rotated-preview");
            require(context.computeOnClient(c->m.audio().playing()),"Native sound engine did not play the bundled swing effect");
            context.runOnClient(c->{var gui=new ClickGuiScreen();c.setScreen(gui);gui.openModuleOptions(m);});context.waitTicks(3);context.takeScreenshot("maro-pickaxe-cosmetics-options");
            context.runOnClient(c->c.setScreen(null));
            var stack=new ItemStack(Items.DIAMOND_PICKAXE);stack.setDamage(23);var original=stack.copy();equip(context,world,stack);
            for(String name:PickaxeModels.NAMES){context.runOnClient(c->{m.select(name);var state=new ItemRenderState();c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,c.player);require(!state.isEmpty()&&state.getParticleSprite(c.world.random)==null,"Production hand model did not use custom geometry");require(ItemStack.areItemsAndComponentsEqual(original,c.player.getMainHandStack()),"Cosmetic mutated real pickaxe");});context.waitTicks(4);context.takeScreenshot("maro-pickaxe-hand-"+PickaxeModels.key(name));context.runOnClient(c->{c.player.handSwinging=false;c.player.swingHand(Hand.MAIN_HAND);});context.waitTicks(2);context.takeScreenshot("maro-pickaxe-swing-"+PickaxeModels.key(name));}
            context.runOnClient(c->{m.select("Driver");c.options.getMainArm().setValue(Arm.LEFT);});context.waitTicks(5);context.takeScreenshot("maro-pickaxe-left-hand");
            context.runOnClient(c->{c.options.getMainArm().setValue(Arm.RIGHT);inspect.getSettings().stream().filter(s->s.getName().equals("Items")).map(s->(ModeSetting)s).findFirst().orElseThrow().set("Any Item");inspect.setEnabled(true);weapon.setEnabled(true);inspect.inspect();require(CustomPickaxe.handles(c.player.getMainHandStack()),"Legacy inspection won over pickaxe");});context.waitTicks(5);context.takeScreenshot("maro-pickaxe-inspection-coexistence");
            context.runOnClient(c->{inspect.setEnabled(false);weapon.setEnabled(false);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);});context.waitTicks(4);context.takeScreenshot("maro-pickaxe-third-person");
            context.runOnClient(c->{c.options.setPerspective(Perspective.FIRST_PERSON);((BooleanSetting)setting(m,"Pickaxe Outline")).set(false);});context.waitTicks(3);context.takeScreenshot("maro-pickaxe-outline-off");
            context.runOnClient(c->{((BooleanSetting)setting(m,"Pickaxe Outline")).set(true);((NumberSetting)setting(m,"Item Size")).set(1.35d);});context.waitTicks(4);context.takeScreenshot("maro-pickaxe-size-135");
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);equip(context,world,stack);context.waitTicks(3);context.takeScreenshot("maro-pickaxe-glint");
            context.runOnClient(c->{var state=new ItemRenderState();var stranger=new OtherClientPlayerEntity(c.world,new GameProfile(UUID.randomUUID(),"PickaxeStranger"));c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,stranger);require(state.getParticleSprite(c.world.random)!=null,"Other player's pickaxe was replaced");((BooleanSetting)setting(m,"Pickaxe First Person")).set(false);c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,c.player);require(state.getParticleSprite(c.world.random)!=null,"First Person switch ignored");((BooleanSetting)setting(m,"Pickaxe First Person")).set(true);((BooleanSetting)setting(m,"Pickaxe Third Person")).set(false);c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,c.player);require(state.getParticleSprite(c.world.random)!=null,"Third Person switch ignored");((BooleanSetting)setting(m,"Pickaxe Third Person")).set(true);});
            context.runOnClient(c->{var serverTool=new ItemStack(Items.DIAMOND_PICKAXE);serverTool.set(DataComponentTypes.ITEM_MODEL,net.minecraft.util.Identifier.ofVanilla("iron_pickaxe"));var serverState=new ItemRenderState();c.getItemModelManager().updateForLivingEntity(serverState,serverTool,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,c.player);require(serverState.getParticleSprite(c.world.random)!=null&&!CustomPickaxe.handles(serverTool),"Explicit server model was overridden");});
            context.runOnClient(c->{((NumberSetting)setting(m,"Item Size")).reset();var state=new ItemRenderState();c.getItemModelManager().updateForLivingEntity(state,new ItemStack(Items.DIAMOND_SWORD),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,c.player);require(state.getParticleSprite(c.world.random)!=null,"Sword was replaced by custom pickaxe");((BooleanSetting)setting(m,"Pickaxe Inventory Icons")).set(false);c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.GUI,c.player);require(state.getParticleSprite(c.world.random)!=null,"Disabled inventory override still active");((BooleanSetting)setting(m,"Pickaxe Inventory Icons")).set(true);m.setEnabled(false);c.getItemModelManager().updateForLivingEntity(state,c.player.getMainHandStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,c.player);require(state.getParticleSprite(c.world.random)!=null&&!CustomPickaxe.handles(c.player.getMainHandStack()),"Disabling did not restore vanilla pickaxe");});context.waitTicks(4);context.takeScreenshot("maro-pickaxe-disabled-vanilla");
            sounds(context,world,m);
            for(int scale:new int[]{3,4}){context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();var g=(SkinAccessoriesScreen)m.panel(new ClickGuiScreen());g.showTab("Pickaxe");m.select("Cold Snap");c.setScreen(g);});context.waitTicks(3);context.takeScreenshot("maro-pickaxe-gallery-scale-"+scale);context.runOnClient(c->c.currentScreen.close());}
            System.out.println("[custom-pickaxe] PASS: 5 cached finite meshes, unified Cosmetics search/selection/rotation/zoom/options, all 5 in-game hand models, legacy migration and local sound lifecycle, unchanged stack components, left hand, third person, legacy inspection coexistence, outline/size/glint, other-player/sword/hand/inventory filters, vanilla restoration and three GUI scales");
        }finally{context.runOnClient(c->{c.setScreen(null);m.setEnabled(false);SharedModuleConfig.apply(m,saved);inspect.setEnabled(false);SharedModuleConfig.apply(inspect,inspectSaved);weapon.setEnabled(false);c.options.getMainArm().setValue(arm);c.options.setPerspective(perspective);c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screen);});}
    }
}
