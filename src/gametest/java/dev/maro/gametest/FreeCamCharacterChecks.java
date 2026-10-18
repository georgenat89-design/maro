package dev.maro.gametest;

import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.nathan.modules.FreeCam;
import dev.maro.render.capture.CaptureScopeChecks;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.Vec3d;

/** Exercises the real detached camera while the saved perspective remains first person. */
final class FreeCamCharacterChecks {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static PlayerEntityRenderState state(MinecraftClient c){return (PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(c.player,0);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var model=ModuleManager.get(CustomModel.class);var cam=ModuleManager.get(FreeCam.class);var tags=ModuleManager.get(Nametags.class);var pickaxe=ModuleManager.get(SkinAccessories.class);
        var savedCam=cam.saveExtra();var perspective=context.computeOnClient(c->c.options.getPerspective());int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0 -60 0 0 0");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");context.waitTicks(5);
            context.runOnClient(c->{
                c.setScreen(null);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.onResolutionChanged();
                model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);pickaxe.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(tags,"Self")).set(true);((ModeSetting)setting(tags,"Armor")).set("Above");((BooleanSetting)setting(tags,"Held Item")).set(true);tags.setEnabled(true);
                pickaxe.select("Driver");pickaxe.setEnabled(true);model.setEnabled(true);
            });context.waitTicks(3);
            context.runOnClient(c->{require(!c.gameRenderer.getCamera().isThirdPerson(),"First-person fixture did not use the ordinary camera");require(CustomModel.worn(state(c))==null,"Ordinary first person draws the full custom body");cam.setEnabled(true);FreeCam.placeCamera(c.player.getX(),c.player.getY()+1.4,c.player.getZ()+4.5,180,6);});
            context.waitTicks(3);Vec3d player=context.computeOnClient(c->c.player.getEntityPos());float yaw=context.computeOnClient(c->c.player.getYaw()),pitch=context.computeOnClient(c->c.player.getPitch());
            for(String name:new String[]{"SpongeBob","LeBron James","Chun-Li","Skeleton","Jeffrey Epstein","Donald Trump","Michael Jackson","Verity","Minion","Travis Scott","Undead Fuhrer","Roblox Bacon Hair"}){
                context.runOnClient(c->model.select(false,name));
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready(name)!=null),"Model did not load: "+name);context.waitTicks(3);
                context.runOnClient(c->{
                    require(c.options.getPerspective()==Perspective.FIRST_PERSON&&c.gameRenderer.getCamera().isThirdPerson(),"Freecam modified perspective or failed to detach");
                    require(CustomModel.worn(state(c))==model.ready(name),"Selected model missing from the detached self renderer: "+name);
                    require(CustomModel.hidesModel(state(c)),"Vanilla body was not replaced in Freecam");
                    require(tags.drawnTags().stream().anyMatch(t->t.contains(c.player.getGameProfile().name())),"Enabled self nametag missing in Freecam");
                    require(c.player.getEntityPos().distanceTo(player)<.00001&&c.player.getYaw()==yaw&&c.player.getPitch()==pitch,"Moving the camera moved or turned the real player");
                });
                context.takeScreenshot("maro-freecam-model-"+name.toLowerCase().replace(' ','-'));
            }
            for(double size:new double[]{50,200}){
                context.runOnClient(c->{((NumberSetting)setting(model,"Model Size")).set(size);FreeCam.placeCamera(player.x+2,player.y+2.4,player.z+6,161.565f,8);});
                context.waitTicks(3);context.takeScreenshot("maro-freecam-model-size-"+(int)size);
                context.runOnClient(c->require(CustomModel.worn(state(c))==model.ready("Roblox Bacon Hair"),"Sizing or camera movement lost the model"));
            }
            context.runOnClient(c->{
                ((NumberSetting)setting(model,"Model Size")).set(100d);c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                CaptureScopeChecks.during(()->require(CustomModel.worn(state(c))==null,"Freecam character leaked into clean capture"));
                require(model.isEnabled()&&cam.isEnabled(),"Clean scope changed live module states");
            });context.waitTicks(3);
            context.runOnClient(c->{require(CustomModel.worn(state(c))!=null,"Third-person selection lost the Freecam model");model.setEnabled(false);});context.waitTicks(3);
            context.runOnClient(c->require(CustomModel.worn(state(c))==null,"Disabling Custom Model did not restore vanilla"));context.takeScreenshot("maro-freecam-vanilla-restored");
            context.runOnClient(c->{model.setEnabled(true);c.options.setPerspective(Perspective.FIRST_PERSON);cam.setEnabled(false);});
            for(int i=0;i<300&&!context.computeOnClient(c->model.ready("Roblox Bacon Hair")!=null);i++)context.waitTicks(1);context.waitTicks(3);
            context.runOnClient(c->{require(!c.gameRenderer.getCamera().isThirdPerson()&&CustomModel.worn(state(c))==null,"Ending Freecam left a full body in first person");require(CustomModel.firstPerson()!=null,"Ending Freecam lost the custom first-person hands");});
            context.takeScreenshot("maro-freecam-first-person-restored");RenderFaultChecks.assertClean();
            System.out.println("[freecam-models] PASS: all 12 selected self models and held tools render with a detached camera while saved perspective stays first person; self nametag/gear follows, player position/yaw/pitch unchanged, size 50/200 and camera movement, third-person mode, clean capture suppression, vanilla fallback and first-person hands restored");
        }finally{context.runOnClient(c->{c.setScreen(null);cam.setEnabled(false);model.setEnabled(false);tags.setEnabled(false);pickaxe.setEnabled(false);cam.loadExtra(savedCam);model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);pickaxe.getSettings().forEach(Setting::reset);c.options.setPerspective(perspective);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});}
    }
}
