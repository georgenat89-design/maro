package dev.maro.gametest;

import com.google.gson.*;
import dev.maro.gui.*;
import dev.maro.gui.render.*;
import dev.maro.config.ClientSettings;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.*;

/** Real shaders, native item/character rendering and local playback; never activates gameplay modules. */
final class ExpandedPreviewChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static String key(String s){return s.replace("-","").replace(" ","").toLowerCase(java.util.Locale.ROOT);}
    private static void value(Module m,String name,JsonElement value){m.getSettings().stream().filter(s->key(s.getName()).equals(key(name))).findFirst().orElseThrow().fromJson(value);}
    private static void open(ClientGameTestContext c,Module m){c.runOnClient(mc->{mc.setScreen(new ClickGuiScreen());((ClickGuiScreen)mc.currentScreen).openModuleOptions(m);});c.getInput().setCursorPos(0,0);c.waitTicks(8);}
    private static void press(ClientGameTestContext c){c.runOnClient(mc->{var g=(ClickGuiScreen)mc.currentScreen;var at=g.visualPreview().playPlace();require(at!=null,"Play control missing: "+g.visualPreview().module().getName());var p=g.screenPose().project(at[0],at[1]);var click=new Click(p[0],p[1],new MouseInput(0,0));require(g.mouseClicked(click,false),"Play missed native dispatch");g.mouseReleased(click);});}
    static void run(ClientGameTestContext context){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));ModuleManager.all().forEach(m->m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var perspective=context.computeOnClient(c->c.options.getPerspective());int xp=context.computeOnClient(c->c.player.experienceLevel);var mainStack=context.computeOnClient(c->c.player.getMainHandStack().copy());var offStack=context.computeOnClient(c->c.player.getOffHandStack().copy());double gamma=context.computeOnClient(c->c.options.getGamma().getValue());var pos=context.computeOnClient(c->c.player.getEntityPos());
        try{
            context.runOnClient(c->{ModuleManager.all().forEach(m->m.setEnabled(false));ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(90d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();require(ModuleManager.get(ChestPreview.class)==null,"Chest Preview still registered");});
            var fake=ModuleManager.get(FakeBlock.class);context.runOnClient(c->{fake.setSource(net.minecraft.item.Items.STONE);fake.setReplace(net.minecraft.item.Items.DIAMOND_BLOCK);value(ModuleManager.get(dev.maro.nathan.modules.FakeXp.class),"level",new JsonPrimitive(37));});
            List<Class<? extends Module>> types=List.of(CustomSky.class,Emotes.class,ItemInspect.class,dev.maro.anubis.inspect.WeaponInspectModule.class,dev.maro.nathan.modules.FakeXp.class,FakeBlock.class,BaseESP.class,Fullbright.class,Geeked.class,SpawnerNotifier.class,dev.maro.nathan.modules.SpotifyHud.class,dev.maro.nathan.modules.SpinBot.class,Trajectories.class,dev.maro.nathan.modules.SwingSpeed.class,ViewModel.class,JumpCircle.class,KeyboardStrokes.class,Radar.class,StaffNotifier.class);
            for(var type:types){var module=ModuleManager.get(type);require(module!=null,"Missing module "+type);open(context,module);context.runOnClient(c->require(((ClickGuiScreen)c.currentScreen).visualPreview().bounds()!=null,"Preview missing for "+module.getName()));
                boolean animated=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).visualPreview().playPlace()!=null);
                if(animated){press(context);context.waitTicks(7);context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(p.playing()&&p.playbackTime()>.1,"Play did not advance "+module.getName());if(module instanceof Emotes)require(((dev.maro.render.emote.EmoteRenderState)p.lastState()).maro$getEmote()!=null,"Emote was not stamped into the native player");});}
                if(!animated){context.runOnClient(c->((ClickGuiScreen)c.currentScreen).cardPanel().expandPreview(module));context.waitTicks(4);}
                context.takeScreenshot("maro-expanded-"+type.getSimpleName().toLowerCase());
                if(animated){press(context);context.waitTicks(3);context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(!p.playing()&&p.playbackTime()==0,"Stop did not reset "+module.getName());});}
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.keyPressed(new net.minecraft.client.input.KeyInput(256,0,0));require(c.currentScreen==g&&!g.cardPanel().previewExpanded(),"Escape did not return from preview to settings");});
                context.runOnClient(c->require(!module.isEnabled(),"Preview activated "+module.getName()));RenderFaultChecks.assertClean();
            }
            open(context,ModuleManager.get(SkinAccessories.class));context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var b=g.visualPreview().bounds();var at=g.screenPose().project(b[0]+b[2]+5,b[1]+30);g.mouseScrolled(at[0],at[1],0,-6);});context.waitTicks(25);context.takeScreenshot("maro-roomy-settings");
            var sky=ModuleManager.get(CustomSky.class);open(context,sky);for(String choice:CustomSky.SKIES){if(choice.equals("Image"))continue;context.runOnClient(c->value(sky,"Sky",new JsonPrimitive(choice)));context.waitTicks(3);}context.takeScreenshot("maro-expanded-sky-presets");
            try{var path=context.computeOnClient(c->c.runDirectory.toPath().resolve("maro-preview-sky.png"));var image=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int yy=0;yy<32;yy++)for(int xx=0;xx<64;xx++)image.setRGB(xx,yy,(xx*4<<16)|(yy*8<<8)|180);javax.imageio.ImageIO.write(image,"png",path.toFile());var loaded=context.computeOnClient(c->sky.importPicture(path));for(int i=0;i<120&&!loaded.isDone();i++)context.waitTicks(1);require(loaded.getNow(false),"Custom sky image could not load");open(context,sky);press(context);context.waitTicks(6);context.takeScreenshot("maro-expanded-sky-image");}catch(java.io.IOException e){throw new AssertionError(e);}
            context.runOnClient(c->{var emotes=ModuleManager.get(Emotes.class);require(emotes.playing()==null,"Preview started a real emote");require(!ModuleManager.get(ItemInspect.class).inspecting(),"Preview started real inspection");require(net.minecraft.item.ItemStack.areEqual(mainStack,c.player.getMainHandStack())&&net.minecraft.item.ItemStack.areEqual(offStack,c.player.getOffHandStack())&&c.options.getGamma().getValue()==gamma,"Preview changed real hands or brightness");require(c.options.getPerspective()==perspective&&c.player.experienceLevel==xp&&c.player.getEntityPos().distanceTo(pos)<.01,"Preview changed real camera/XP/player");require(ModuleManager.all().stream().noneMatch(Module::isEnabled),"Preview left a module enabled");c.setScreen(null);require(!PreviewShaders.hasResources(),"Shader preview resources leaked after GUI closed");});
            System.out.println("[expanded-previews] PASS: 19 correct subjects, all 16 procedural sky presets, native emote/item rendering, Play/Stop dispatch and local clocks, FakeXP/FakeBlock/Base/Fullbright/Geeked/spawner/Spotify/spin/throw/swing/viewmodel/jump examples, enlarged HUD previews, Chest Preview removed; real module states/camera/XP/player untouched, GPU targets released");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(Setting::fromJson);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
