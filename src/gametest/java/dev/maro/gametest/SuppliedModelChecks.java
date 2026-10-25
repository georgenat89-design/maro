package dev.maro.gametest;

import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.*;
import dev.maro.render.character.kit.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import javax.imageio.ImageIO;

/** Supplied ZIP meshes pass through the real replacement renderer and wardrobe. */
final class SuppliedModelChecks {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void loaded(ClientGameTestContext context,CustomModel model,String name){
        for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
        require(context.computeOnClient(c->model.ready(name)!=null),"Model failed to load: "+name);
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var model=ModuleManager.get(CustomModel.class);require(model!=null,"Custom Model missing");
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var perspective=context.computeOnClient(c->c.options.getPerspective());
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");
            context.runOnClient(c->{model.getSettings().forEach(Setting::reset);c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new CustomModelScreen(null,model));});
            for(String name:new String[]{"SpongeBob","LeBron James","Chun-Li","Skeleton","Jeffrey Epstein","Donald Trump","Michael Jackson","Roblox Bacon Hair","Verity","Minion","Travis Scott","Undead Fuhrer"}){
                context.runOnClient(c->{model.select(false,name);UnifiedPreviewTestView.angle(c,0);});loaded(context,model,name);context.waitTicks(4);
                context.runOnClient(c->{var mesh=model.ready(name).model;require(mesh.triangles()>100&&mesh.triangles()<60000,"Unbounded/empty geometry: "+name);require(mesh.part(Bone.BODY)!=null,"Missing body: "+name);if(!name.equals("Verity"))require(mesh.part(Bone.RIGHT_ARM)!=null&&mesh.part(Bone.LEFT_ARM)!=null&&mesh.part(Bone.RIGHT_LEG)!=null&&mesh.part(Bone.LEFT_LEG)!=null,"Missing animated limbs: "+name);else require(mesh.parts.length==1,"Single-piece Verity was split into fake limbs");for(var p:mesh.parts)for(var surface:new Surface[]{p.base,p.decals})if(surface!=null){for(float v:surface.pos)require(Float.isFinite(v),"Non-finite imported vertex");for(int v:surface.quads)require(v>=0&&v<surface.vertexCount(),"Imported index outside mesh");}if(name.equals("Donald Trump")){var face=mesh.part(Bone.HEAD).base;float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;for(int i=2;i<face.pos.length;i+=3){lo=Math.min(lo,face.pos[i]);hi=Math.max(hi,face.pos[i]);}float sum=0;int count=0;for(int i=2;i<face.pos.length;i+=3)if(face.pos[i]>hi-(hi-lo)*.2f){sum+=face.nrm[i];count++;}require(count>0&&sum/count>.15f,"Repaired Trump face normals point inward");}});
                var shot=context.takeScreenshot("maro-supplied-"+name.toLowerCase().replace(' ','-')+"-front");
                try{var im=ImageIO.read(shot.toFile());int[] area=context.computeOnClient(c->UnifiedPreviewTestView.bounds(c));int colored=0;for(int y=area[1];y<area[3];y++)for(int x=area[0];x<area[2];x++){int rgb=im.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));if((name.equals("Skeleton")||name.equals("Verity"))?min>70&&max-min<30:max-min>40&&max>70)colored++;}require(colored>500,"Imported material was not visibly rendered: "+name);}catch(java.io.IOException e){throw new AssertionError(e);}
                context.runOnClient(c->UnifiedPreviewTestView.angle(c,90));context.waitTicks(3);context.takeScreenshot("maro-supplied-"+name.toLowerCase().replace(' ','-')+"-profile");
                context.runOnClient(c->{model.cycleDetail();});loaded(context,model,name);context.waitTicks(2);context.runOnClient(c->model.cycleDetail());loaded(context,model,name);context.waitTicks(2);context.runOnClient(c->model.cycleDetail());loaded(context,model,name);
            }
            for(int guiScale:new int[]{3,4}){context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new CustomModelScreen(null,model));});context.waitTicks(4);context.takeScreenshot("maro-supplied-wardrobe-scale-"+guiScale);}
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();model.select(false,"SpongeBob");model.setEnabled(true);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);c.setScreen(null);});loaded(context,model,"SpongeBob");context.waitTicks(5);
            context.runOnClient(c->{var state=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(c.player,0);require(CustomModel.worn(state)!=null&&CustomModel.worn(state).model.id.equals("spongebob"),"Self replacement did not reach renderer");c.options.forwardKey.setPressed(true);});context.waitTicks(10);context.runOnClient(c->c.options.forwardKey.setPressed(false));context.takeScreenshot("maro-spongebob-in-world");
            context.runOnClient(c->{c.options.setPerspective(Perspective.FIRST_PERSON);});context.waitTicks(3);context.takeScreenshot("maro-spongebob-first-person");
            context.runOnClient(c->{model.setEnabled(false);var state=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(c.player,0);require(CustomModel.worn(state)==null,"Disabling did not restore vanilla");});
            context.runOnClient(c->{model.select(false,"Travis Scott");model.select(true,"Travis Scott");model.setEnabled(true);c.options.setPerspective(Perspective.FIRST_PERSON);});loaded(context,model,"Travis Scott");context.waitTicks(3);
            context.runOnClient(c->{require(model.selected(true).equals("Travis Scott"),"Travis missing from other-player choices");require(CustomModel.firstPerson()!=null,"Travis first-person arms missing");});context.takeScreenshot("maro-travis-scott-first-person");
            RenderFaultChecks.assertClean();System.out.println("[supplied-models] PASS: all 12 supplied characters including Undead Fuhrer; materials, finite indexed geometry, limb parts, all detail levels, wardrobe scales 2/3/4, self replacement, walking/first-person and vanilla restoration");
        }finally{context.runOnClient(c->{c.options.forwardKey.setPressed(false);model.setEnabled(false);c.setScreen(null);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.options.setPerspective(perspective);});}
    }
}
