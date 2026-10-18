package dev.maro.gametest;

import dev.maro.render.character.CharacterItemGrip;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Item contact tests include the complete native feature rotation, both hands and resource-pack transforms. */
final class SharedItemGripChecks {
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static void contact(ItemStack stack,Vector3f expected){
        var points=new Vector3fc[]{
            new Vector3f(.125f,0,.46875f),new Vector3f(.875f,0,.46875f),
            new Vector3f(.125f,1,.46875f),new Vector3f(.875f,1,.46875f),
            new Vector3f(.125f,0,.53125f),new Vector3f(.875f,1,.53125f)};
        var transformation=new Transformation(new Vector3f(12,-26,33),new Vector3f(.12f,.37f,.08f),new Vector3f(.5f,.8f,.55f));
        var item=new ItemRenderState();var layer=item.newLayer();layer.setVertices(()->points);layer.setTransform(transformation);
        for(var arm:Arm.values())for(float scale:new float[]{.5f,1,1.3f,2}){
            var grip=CharacterItemGrip.offset(item,stack,arm,new Vector3f());require(grip.isFinite()&&grip.lengthSquared()>.001,"Authored transform was ignored");
            var pose=new MatrixStack();pose.scale(scale,scale,scale);
            float sign=arm==Arm.RIGHT?1:-1;
            pose.translate(sign/16+grip.x,-.625f+grip.z,.125f+grip.y);
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            pose.translate(sign/16,.125f,-.625f);transformation.apply(arm==Arm.LEFT,pose.peek());
            var result=pose.peek().getPositionMatrix().transformPosition(expected,new Vector3f());
            require(result.length()<.00001,"Rendered item contact missed the palm: "+stack+" "+arm+" "+scale+" "+result);
        }
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var model=ModuleManager.get(CustomModel.class);var cosmetics=ModuleManager.get(SkinAccessories.class);
        int guiScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var mainArm=context.computeOnClient(c->c.options.getMainArm().getValue());
        var size=(NumberSetting)model.getSettings().stream().filter(s->s.getName().equals("Model Size")).findFirst().orElseThrow();
        try{
            context.runOnClient(c->{
                contact(new ItemStack(Items.TOTEM_OF_UNDYING),new Vector3f(.5f,.24f,.5f));
                contact(new ItemStack(Items.STONE),new Vector3f(.5f,.08f,.5f));
                cosmetics.setEnabled(false);model.getSettings().forEach(Setting::reset);model.setEnabled(true);
                c.options.getGuiScale().setValue(2);c.options.getMainArm().setValue(Arm.RIGHT);c.onResolutionChanged();
            });
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:totem_of_undying");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
            context.waitTicks(3);context.runOnClient(c->c.setScreen(new CustomModelScreen(null,model)));
            for(String name:model.choices()){
                if(name.equals("None"))continue;
                context.runOnClient(c->model.select(false,name));
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready(name)!=null),"Model unavailable: "+name);
                context.runOnClient(c->((CustomModelScreen)c.currentScreen).showAngle(90));
                context.waitTicks(2);context.takeScreenshot("maro-item-grip-totem-"+name.toLowerCase().replace(' ','-'));
            }
            context.runOnClient(c->model.select(false,"SpongeBob"));
            for(String item:new String[]{"totem_of_undying","diamond_pickaxe","diamond_sword","diamond_axe","diamond_shovel","apple","stone","torch","shield","trident","bow"}){
                world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:"+item);context.waitTicks(2);
                context.takeScreenshot("maro-item-grip-spongebob-"+item);
            }
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:totem_of_undying");
            for(int scale:new int[]{2,3,4}){
                context.runOnClient(c->{size.set(130d);c.getToastManager().clear();c.options.getGuiScale().setValue(scale);c.options.getMainArm().setValue(Arm.LEFT);c.onResolutionChanged();c.setScreen(new CustomModelScreen(null,model));});
                context.waitTicks(3);context.takeScreenshot("maro-size-centered-130-scale-"+scale);
            }
            System.out.println("[shared-item-grip] PASS: real native display/feature transforms keep totem/block contact at the palm for both hands and model sizes 50/100/130/200%; native all-ten-model totems, eleven item categories, and centered 130% controls at GUI scales 2/3/4");
        }finally{context.runOnClient(c->{c.setScreen(null);model.setEnabled(false);size.set(100d);c.options.getGuiScale().setValue(guiScale);c.options.getMainArm().setValue(mainArm);c.onResolutionChanged();});}
    }
}
