package dev.maro.gametest;

import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.module.impl.visuals.*;
import dev.maro.render.character.CharacterLabelAnchors;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.*;

/** Real world rendering: elytra/swim labels follow posed heads instead of standing height. */
final class PosedNameTagChecks {
    private static Setting<?> setting(Module m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void run(ClientGameTestContext context){
        var model=ModuleManager.get(CustomModel.class);var tags=ModuleManager.get(Nametags.class);var fakes=ModuleManager.get(FakePlayer.class);var esp=ModuleManager.get(PlayerESP.class);
        var saved=new IdentityHashMap<Setting<?>,com.google.gson.JsonElement>();
        for(var m:List.of(model,tags,fakes,esp))m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy()));
        var view=context.computeOnClient(c->c.options.getPerspective());
        try{
            context.runOnClient(c->{
                c.setScreen(null);c.options.setPerspective(Perspective.FIRST_PERSON);
                model.getSettings().forEach(Setting::reset);tags.getSettings().forEach(Setting::reset);fakes.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(fakes,"Movable")).set(false);((TextSetting)setting(fakes,"Name")).set("PoseTest");fakes.setEnabled(true);
                var fake=fakes.entity();fake.refreshPositionAndAngles(c.player.getX(),c.player.getY()+1,c.player.getZ()+7,180,0);
                fake.bodyYaw=fake.lastBodyYaw=180;fake.headYaw=fake.lastHeadYaw=180;
                fake.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.FIREWORK_ROCKET,32));fake.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));
                ((BooleanSetting)setting(tags,"Hide NPCs")).set(false);((ModeSetting)setting(tags,"Armor")).set("Above");
                ((BooleanSetting)setting(tags,"Held Item")).set(true);((BooleanSetting)setting(tags,"Item Name")).set(true);tags.setEnabled(true);
                ((ModeSetting)setting(model,"Other Players")).set("Everyone");model.setEnabled(true);c.player.setYaw(0);c.player.setPitch(-8);
                esp.getSettings().forEach(Setting::reset);for(String s:List.of("Fill","Glow","Outline"))((BooleanSetting)setting(esp,s)).set(false);esp.setEnabled(true);
            });
            for(String name:List.of("Roblox Bacon Hair","Minion","Verity")){
                context.runOnClient(c->model.select(true,name));for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready(name)!=null),"Missing posed model: "+name);
                double standing=0;
                for(EntityPose pose:List.of(EntityPose.STANDING,EntityPose.CROUCHING,EntityPose.GLIDING,EntityPose.SWIMMING)){
                    context.runOnClient(c->{var fake=fakes.entity();fake.stopGliding();fake.setSwimming(false);fake.setSneaking(false);fake.setOnGround(false);
                        if(pose==EntityPose.GLIDING)fake.startGliding();if(pose==EntityPose.SWIMMING)fake.setSwimming(true);if(pose==EntityPose.CROUCHING)fake.setSneaking(true);fake.setPose(pose);
                    });context.waitTicks(pose==EntityPose.STANDING||pose==EntityPose.CROUCHING?4:14);
                    double y=context.computeOnClient(c->{var fake=fakes.entity();var head=CharacterLabelAnchors.point(fake);
                        require(head!=null,"World renderer did not record a head for "+name+" "+pose);
                        require(tags.tagAnchor(fake,0).distanceTo(head)<.0001,"Nametag ignored posed head");
                        require(Math.hypot(head.x-fake.getX(),head.z-fake.getZ())<3,"Head anchor is not in world coordinates: "+head);
                        require(tags.drawnTags().stream().anyMatch(t->t.contains("PoseTest")),"Name/armor/item caption absent");
                        assertTracer(fake,esp);
                        System.out.println("[posed-tag] "+name+" "+pose+" height="+(head.y-fake.getY())+" horizontal="+Math.hypot(head.x-fake.getX(),head.z-fake.getZ()));
                        return head.y-fake.getY();});
                    if(pose==EntityPose.STANDING){standing=y;require(y>1.5&&y<2.8,"Standing head transform is wrong: "+y);}
                    if(pose==EntityPose.GLIDING)require(y<standing*.85,"Flying gear still floats at standing height: "+name+" "+y+" / "+standing);
                    context.takeScreenshot("maro-posed-tag-"+name.toLowerCase().replace(' ','-')+"-"+pose.name().toLowerCase());
                }
                context.runOnClient(c->{var fake=fakes.entity();fake.stopGliding();fake.setSwimming(false);fake.setSneaking(false);fake.setPose(EntityPose.STANDING);});context.waitTicks(4);
                for(double size:new double[]{50,200}){
                    context.runOnClient(c->((NumberSetting)setting(model,"Model Size")).set(size));context.waitTicks(4);
                    double height=context.computeOnClient(c->{var fake=fakes.entity();var point=CharacterLabelAnchors.point(fake);require(point!=null,"Size edit lost tag anchor");return point.y-fake.getY();});
                    require(Math.abs((height-.25)/(standing-.25)-size/100)<.15,"Head anchor did not resize with character");
                    context.runOnClient(c->assertTracer(fakes.entity(),esp));
                }
                context.runOnClient(c->model.resetSize());
            }
            context.runOnClient(c->tags.setEnabled(false));context.waitTicks(3);context.runOnClient(c->require(CharacterLabelAnchors.point(fakes.entity())==null,"Stale head anchor survived frame cleanup"));
            System.out.println("[posed-tags] PASS: Bacon Hair, Minion and Verity name/gear/item captions follow actual head/body transforms in standing, crouch, elytra and swimming; 50/200% sizing and frame cleanup");
            System.out.println("[posed-tracers] PASS: ESP endpoints follow the same-frame rendered torso in standing, crouch, flight and swimming at 50/100/200% scale, with silhouettes disabled");
        }finally{context.runOnClient(c->{fakes.setEnabled(false);model.setEnabled(false);tags.setEnabled(false);esp.setEnabled(false);saved.forEach(Setting::fromJson);c.options.setPerspective(view);});}
    }
    private static Object field(String name){try{var f=dev.maro.render.esp.PlayerEspRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertTracer(net.minecraft.entity.player.PlayerEntity player,PlayerESP esp){
        var body=CharacterLabelAnchors.body(player);require(body!=null,"No posed torso recorded for ESP");
        var entities=(net.minecraft.entity.Entity[])field("tracerEntities");int count=(int)field("tracerCount"),index=-1;for(int i=0;i<count;i++)if(entities[i]==player){index=i;break;}require(index>=0,"Player tracer absent");
        var camera=net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        var projected=new org.joml.Vector4f((float)(body.x-camera.x),(float)(body.y-camera.y),(float)(body.z-camera.z),1);((org.joml.Matrix4f)field("viewProjection")).transform(projected);
        float[] expected=new float[2];dev.maro.render.esp.TracerGeometry.project(projected.x,projected.y,projected.w,esp.tracersFromBottom(),expected,0);
        var actual=(float[])field("tracerTargets");require(Math.hypot(expected[0]-actual[index*2],expected[1]-actual[index*2+1])<.0001,"ESP endpoint detached from rendered body");
    }
}
