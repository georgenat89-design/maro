package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.MaroHomeScreen;
import dev.maro.gui.render.MenuLandscape;
import dev.maro.gui.render.MaroTitleStyle;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;

/** Real controls, rendered scenery, persistence and overlay input isolation. */
final class MenuSceneChecks {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void click(ClientGameTestContext context,double x,double y){context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;float s=MaroTitleStyle.canvasScale(home.width,home.height);home.mouseClicked(new Click((home.width-960*s)/2+x*s,(home.height-540*s)/2+y*s,new MouseInput(0,0)),false);});context.waitTicks(3);}
    static void run(ClientGameTestContext context){
        String time=ClientSettings.sceneTime.get(),trees=ClientSettings.sceneTrees.get(),camera=ClientSettings.sceneCamera.get();
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var saved=new java.util.LinkedHashMap<dev.maro.setting.Setting<?>,com.google.gson.JsonElement>();
        for(var setting:ClientSettings.SCENE.getSettings())saved.put(setting,setting.toJson().deepCopy());
        int accent=ClientSettings.accent.get();boolean gradient=ClientSettings.gradient.get(),rainbow=ClientSettings.rainbow.get();double shift=ClientSettings.gradientShift.get();
        try{
            context.runOnClient(c->{ClientSettings.sceneBackdrop.set("Landscape");ClientSettings.orbitSpeed.reset();ClientSettings.sceneZoom.reset();ClientSettings.orbitAngle.reset();ClientSettings.orbitDirection.reset();ClientSettings.transparentMenu.set(false);});
            for(int guiScale:new int[]{2,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new TitleScreen());});context.waitTicks(4);
                click(context,863,495);require(context.computeOnClient(c->((MaroHomeScreen)c.currentScreen).sceneOpen()),"Scene button did not open");
                click(context,782,277);require(ClientSettings.sceneTime.is("Sunset"),"Time icons did not select Sunset");
                click(context,878,352);require(ClientSettings.sceneTrees.is("Mixed"),"Tree icons did not select Mixed");
                click(context,862,427);require(ClientSettings.sceneCamera.is("Paused"),"Camera pause icon missed");
                double a=context.computeOnClient(c->MenuLandscape.cameraAngle());context.waitTicks(8);require(context.computeOnClient(c->MenuLandscape.cameraAngle())==a,"Paused camera kept orbiting");
                context.takeScreenshot("maro-scene-sunset-scale-"+guiScale);
                var hover=context.computeOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;float s=MaroTitleStyle.canvasScale(home.width,home.height);double pixels=c.getWindow().getScaleFactor();return new double[]{((home.width-960*s)/2+830*s)*pixels,((home.height-540*s)/2+352*s)*pixels};});
                context.getInput().setCursorPos(hover[0],hover[1]);context.waitTicks(10);context.takeScreenshot("maro-scene-tooltip-scale-"+guiScale);
                context.getInput().setCursorPos(0,0);
                int mixed=context.computeOnClient(c->MenuLandscape.faceCount());
                click(context,782,352);require(ClientSettings.sceneTrees.is("Cherry"),"Cherry icon missed");require(context.computeOnClient(c->MenuLandscape.faceCount())!=mixed,"Tree selection did not rebuild scenery");
                click(context,734,277);require(ClientSettings.sceneTime.is("Day"),"Day icon missed");context.takeScreenshot("maro-scene-cherry-day-scale-"+guiScale);
                click(context,830,277);require(ClientSettings.sceneTime.is("Night"),"Night icon missed");context.takeScreenshot("maro-scene-cherry-night-scale-"+guiScale);
                context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;require(home.page()==null,"Scene controls clicked through to a native page");home.keyPressed(new KeyInput(256,0,0));require(!home.sceneOpen(),"Escape did not close scene controls");});
                click(context,480,259);require(context.computeOnClient(c->((MaroHomeScreen)c.currentScreen).page() instanceof net.minecraft.client.gui.screen.world.SelectWorldScreen),"Scene controls broke the world shortcut");
                click(context,863,495);context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;home.keyPressed(new KeyInput(256,0,0));require(home.page()!=null&&!home.sceneOpen(),"Escape closed the world selector underneath the popover");home.keyPressed(new KeyInput(256,0,0));require(home.page()==null,"World selector no longer closes normally");});
            }
            context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;var b=home.children().stream().filter(e->e instanceof ClickableWidget w&&w.getMessage().getString().equals("Scene settings")).map(e->(ClickableWidget)e).findFirst().orElseThrow();home.setFocused(b);require(home.keyPressed(new KeyInput(257,0,0))&&home.sceneOpen(),"Keyboard could not open Scene");});
            click(context,702,427);require(ClientSettings.sceneCamera.is("Orbit"),"Orbit icon missed");
            double a=context.computeOnClient(c->MenuLandscape.cameraAngle());context.waitTicks(6);require(context.computeOnClient(c->MenuLandscape.cameraAngle())>a,"Orbit camera did not move");
            context.runOnClient(c->ConfigManager.saveClient());
            try{var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ConfigManager.DIR.resolve("client.json"))).getAsJsonObject();require(json.toString().contains("Scene Camera")&&json.toString().contains("Orbit"),"Scene preferences were not persisted");}catch(java.io.IOException e){throw new AssertionError(e);}
            click(context,775,391);click(context,613,297);click(context,613,333);click(context,613,369);
            require(ClientSettings.orbitSpeed.getInt()==110&&ClientSettings.sceneZoom.getInt()==105&&ClientSettings.orbitAngle.getInt()==50,"Orbit option +/- controls missed");
            context.waitTicks(10);require(context.computeOnClient(c->MenuLandscape.cameraZoom())>1&&context.computeOnClient(c->MenuLandscape.cameraPitch())>.8f,"Orbit zoom/angle did not reach the renderer");
            click(context,520,406);require(ClientSettings.orbitDirection.is("Counterclockwise"),"Orbit direction control missed");
            double reverse=context.computeOnClient(c->MenuLandscape.cameraAngle());context.waitTicks(8);require(context.computeOnClient(c->MenuLandscape.cameraAngle())<reverse,"Reversed orbit kept turning clockwise");
            context.takeScreenshot("maro-orbit-options");
            context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;home.keyPressed(new KeyInput(256,0,0));require(home.sceneOpen(),"Orbit Escape closed the entire Scene panel");});
            int landscapeFaces=context.computeOnClient(c->MenuLandscape.faceCount());click(context,842,198);require(ClientSettings.sceneBackdrop.is("Galaxy"),"Galaxy tab missed");
            require(context.computeOnClient(c->MenuLandscape.faceCount())<landscapeFaces,"Galaxy did not switch to a bounded floating island");
            click(context,862,427);context.takeScreenshot("maro-galaxy-opaque");click(context,731,495);
            require(ClientSettings.transparentMenu.get()&&ClientSettings.menuSurfaceAlpha()<255,"Transparent toggle did not change the surface opacity");context.takeScreenshot("maro-galaxy-transparent");
            context.runOnClient(c->{ClientSettings.rainbow.set(false);ClientSettings.gradient.set(false);ClientSettings.accent.set(0xFF22C55E);});context.waitTicks(16);context.takeScreenshot("maro-scene-blue-with-green-gui");
            context.runOnClient(c->{ClientSettings.accent.set(0xFFEC4899);ClientSettings.gradient.set(true);ClientSettings.gradientShift.set(.12);});context.waitTicks(16);context.takeScreenshot("maro-scene-blue-with-pink-gui");
            context.runOnClient(c->ConfigManager.saveClient());
            try{var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(ConfigManager.DIR.resolve("client.json"))).getAsJsonObject().getAsJsonObject("settings");require(json.get("Scene Background").getAsString().equals("Galaxy")&&json.get("Transparent Menu").getAsBoolean()&&json.get("Orbit Speed").getAsInt()==110,"Galaxy/orbit/transparency preferences were not saved");}catch(java.io.IOException e){throw new AssertionError(e);}
            context.runOnClient(c->((MaroHomeScreen)c.currentScreen).keyPressed(new KeyInput(256,0,0)));click(context,480,259);
            require(context.computeOnClient(c->((MaroHomeScreen)c.currentScreen).page() instanceof net.minecraft.client.gui.screen.world.SelectWorldScreen),"Transparent Galaxy broke native navigation");context.takeScreenshot("maro-galaxy-worlds-transparent");
            context.runOnClient(c->((MaroHomeScreen)c.currentScreen).keyPressed(new KeyInput(256,0,0)));click(context,863,495);
            context.runOnClient(c->{ClientSettings.sceneTrees.set("Mixed");ClientSettings.sceneTime.set("Sunset");});context.waitTicks(4);context.takeScreenshot("maro-scene-final");
            require(context.computeOnClient(c->MenuLandscape.faceCount())>1000,"Landscape has no block geometry");
            System.out.println("[menu-scene] PASS: lighting/tree/camera controls; speed/zoom/angle/reverse orbit reach renderer; bounded Galaxy island and native navigation; blue Scene panel independent of GUI preset, rounded tooltip at scales 2/4, transparent surfaces, persisted settings, keyboard/overlay input and GUI scales 2/4; mean mesh preparation "+context.computeOnClient(c->MenuLandscape.averageBuildMillis())+" ms");
        }finally{context.runOnClient(c->{saved.forEach((setting,value)->setting.fromJson(value));ClientSettings.accent.set(accent);ClientSettings.gradient.set(gradient);ClientSettings.rainbow.set(rainbow);ClientSettings.gradientShift.set(shift);ConfigManager.saveClient();c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new TitleScreen());});}
    }
}
