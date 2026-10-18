package dev.maro.gametest;

import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.module.impl.visuals.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.Vec3d;

/** Name, equipment and held-item captions share an anchor above the active model's scaled head. */
final class ModelNametagChecks {
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void run(ClientGameTestContext context){
        var model=ModuleManager.get(CustomModel.class);var tags=ModuleManager.get(Nametags.class);var fakes=ModuleManager.get(FakePlayer.class);
        var perspective=context.computeOnClient(c->c.options.getPerspective());int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            context.runOnClient(c->{
                model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);fakes.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(fakes,"Movable")).set(false);((ModeSetting)setting(fakes,"Armor")).set("Diamond");((TextSetting)setting(fakes,"Name")).set("ModelTest");fakes.setEnabled(true);
                var fake=fakes.entity();fake.refreshPositionAndAngles(c.player.getX(),c.player.getY(),c.player.getZ()+8,180,0);fake.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET,15));c.player.setYaw(0);c.player.setPitch(-8);
                ((BooleanSetting)setting(tags,"Hide NPCs")).set(false);((ModeSetting)setting(tags,"Armor")).set("Above");((BooleanSetting)setting(tags,"Held Item")).set(true);((BooleanSetting)setting(tags,"Item Name")).set(true);tags.setEnabled(true);
                ((ModeSetting)setting(model,"Other Players")).set("Everyone");model.setEnabled(true);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.onResolutionChanged();
            });
            for(String name:new String[]{"Michael Jackson","Skeleton","LeBron James","Chun-Li","SpongeBob","Jeffrey Epstein","Donald Trump","Roblox Bacon Hair"}){
                context.runOnClient(c->{model.select(true,name);model.select(false,name);});
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready(name)!=null),"Missing model: "+name);
                for(double size:new double[]{50,100,200}){
                    context.runOnClient(c->{
                        ((NumberSetting)setting(model,"Model Size")).set(size);var fake=fakes.entity();var worn=model.ready(name);
                        var state=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(fake,0);
                        require(CustomModel.worn(state)==worn,"Tag target differs from rendered model");
                        double height=worn.model.maxY*worn.model.scale/16d*size/100d*fake.getScale();
                        require(Math.abs(tags.tagHeight(fake)-height-.3)<.00001,"Equipment/name tag missed the resized head: "+name+" "+size);
                        require(Math.abs(CustomModel.visualHeight(c.player)-height)<.00001,"Self model height differs from other-player height");
                        var vanilla=new PlayerEntityRenderState();vanilla.baseScale=fake.getScale();vanilla.nameLabelPos=new Vec3d(0,2.1,0);CustomModel.extract(fake,vanilla);
                        double expected=2.1+height-fake.getDimensions(net.minecraft.entity.EntityPose.STANDING).height();
                        require(Math.abs(vanilla.nameLabelPos.y-expected)<.00001,"Vanilla label failed to follow model size");
                    });
                    context.waitTicks(3);
                    require(context.computeOnClient(c->tags.drawnTags().stream().anyMatch(t->t.contains("ModelTest"))),"Resized model's name/gear anchor was not rendered");
                    if(name.equals("Michael Jackson")||name.equals("Roblox Bacon Hair"))context.takeScreenshot("maro-model-armor-tag-"+(name.equals("Michael Jackson")?"size-":"bacon-size-")+(int)size);
                }
            }
            context.runOnClient(c->{
                ((ModeSetting)setting(model,"Other Players")).set("Friends");var fake=fakes.entity();
                require(Float.isNaN(CustomModel.visualHeight(fake)),"Non-selected players inherited another character's height");
                double vanilla=Math.max(fake.getHeight(),fake.getDimensions(net.minecraft.entity.EntityPose.STANDING).height())+.45;
                require(Math.abs(tags.tagHeight(fake)-vanilla)<.00001,"Vanilla tag height changed for an unselected player");
                model.select(false,"None");require(Float.isNaN(CustomModel.visualHeight(c.player)),"None selection kept custom head height");
                model.setEnabled(false);require(Float.isNaN(CustomModel.visualHeight(fake)),"Disabled replacement kept custom tag height");
            });
            RenderFaultChecks.assertClean();System.out.println("[model-nametags] PASS: all 8 models at 50/100/200%, live self/other head height, real replacement state and vanilla name-label offsets; shared equipment/name/item-caption anchor, non-target/None/disabled fallback");
        }finally{context.runOnClient(c->{model.setEnabled(false);tags.setEnabled(false);fakes.setEnabled(false);model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);fakes.getSettings().forEach(Setting::reset);c.options.setPerspective(perspective);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});}
    }
}
