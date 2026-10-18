package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.config.SharedModuleConfig;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.module.ModuleManager;
import dev.maro.nathan.modules.*;
import dev.maro.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

final class FreeCamFovChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static float lens(net.minecraft.client.MinecraftClient c){return ((GameRendererSpatialAccessor)c.gameRenderer).maro$fov(c.gameRenderer.getCamera(),1,true);}
    static void run(ClientGameTestContext context){
        var cam=ModuleManager.get(FreeCam.class);var custom=ModuleManager.get(CustomFov.class);var zoom=ModuleManager.get(KeyZoom.class);
        var fov=(NumberSetting)cam.getSettings().stream().filter(s->s.getName().equals("fov")).findFirst().orElseThrow();
        var saved=cam.saveExtra();int own=context.computeOnClient(c->c.options.getFov().getValue());
        var customSaved=custom.saveExtra();var zoomSaved=zoom.saveExtra();String layout=ClientSettings.menuStyle.get();
        try{
            context.runOnClient(c->{cam.setEnabled(false);custom.setEnabled(false);zoom.setEnabled(false);c.setScreen(null);c.options.getFov().setValue(70);fov.set(90d);
                require(cam.getSettingSections().stream().filter(s->s.getTitle().equals("Camera")).anyMatch(s->s.getSettings().contains(fov)),"FOV is not inside Free Cam > Camera");});
            float baseline=context.computeOnClient(FreeCamFovChecks::lens);
            context.runOnClient(c->{fov.set(120d);require(Math.abs(lens(c)-baseline)<.001,"Inactive Freecam changed FOV");cam.setEnabled(true);
                for(double value:new double[]{10,70,90,120,155}){fov.set(value);require(Math.abs(lens(c)-value)<.001,"Freecam FOV did not update live: "+value);require(c.options.getFov().getValue()==70,"Freecam modified game FOV option");}
                fov.set(999d);require(lens(c)==155,"FOV maximum not clamped");fov.set(-99d);require(lens(c)==10,"FOV minimum not clamped");
                fov.set(120d);var state=cam.saveExtra();fov.set(90d);cam.loadExtra(state);require(lens(c)==120,"Saved Freecam FOV did not reload");
                require(SharedModuleConfig.capture(cam).getAsJsonObject("settings").get("fov").getAsInt()==120,"Public config omitted FOV");
                custom.setEnabled(true);require(lens(c)==120,"Custom FOV overrode Freecam");cam.setEnabled(false);float playerLens=lens(c);cam.setEnabled(true);require(lens(c)==120,"Activation order changed Freecam FOV");cam.setEnabled(false);require(Math.abs(lens(c)-playerLens)<.001,"Disabling Freecam failed to restore Custom FOV");custom.setEnabled(false);require(Math.abs(lens(c)-baseline)<.001,"Freecam failed to restore normal lens");
                cam.setEnabled(true);fov.set(90d);zoom.getSettings().forEach(dev.maro.setting.Setting::reset);zoom.setEnabled(true);
            });
            context.runOnClient(c->{
                // Fabric's synthetic key events do not change glfwGetKey, which this zoom polls.
                // Seed its animation state, then exercise the actual renderer/event ordering.
                try{var progress=KeyZoom.class.getDeclaredField("progress");var level=KeyZoom.class.getDeclaredField("level");progress.setAccessible(true);level.setAccessible(true);
                    progress.setDouble(zoom,1);level.setDouble(zoom,Math.log(5));require(lens(c)<60,"Key Zoom cannot zoom from Freecam FOV");
                    progress.setDouble(zoom,0);require(Math.abs(lens(c)-90)<.01,"Ending zoom did not return to Freecam FOV");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            context.runOnClient(c->{zoom.setEnabled(false);ClientSettings.menuStyle.set("Window");ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);var gui=new ClickGuiScreen();c.setScreen(gui);gui.openModuleSettings(cam,"Camera");});
            context.waitTicks(12);context.takeScreenshot("maro-freecam-fov-settings");
            System.out.println("[freecam-fov] PASS: native renderer lens at 10/70/90/120/155, live edits, range clamps, saved/public config, both Custom FOV activation orders, Key Zoom animation composition/restoration and unchanged game FOV; Camera settings rendered");
        }finally{context.getInput().releaseKey(GLFW.GLFW_KEY_C);context.runOnClient(c->{c.setScreen(null);cam.setEnabled(false);custom.setEnabled(false);zoom.setEnabled(false);cam.loadExtra(saved);custom.loadExtra(customSaved);zoom.loadExtra(zoomSaved);c.options.getFov().setValue(own);ClientSettings.menuStyle.set(layout);});}
    }
}
