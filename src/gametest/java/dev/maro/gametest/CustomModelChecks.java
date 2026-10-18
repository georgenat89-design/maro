package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.*;
import dev.maro.render.character.kit.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;

/** Runs all built-in meshes through the production renderer, including its deferred custom commands. */
final class CustomModelChecks {
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(CustomModel m,String name) {return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static PlayerEntityRenderState state(MinecraftClient c,Entity entity) {
        return (PlayerEntityRenderState)c.getEntityRenderDispatcher().getRenderer(entity).getAndUpdateRenderState(entity,0);
    }
    private static void loaded(ClientGameTestContext context,CustomModel m,String name) {
        for(int i=0;i<1200 && !context.computeOnClient(c->m.ready(name)!=null);i++)context.waitTicks(1);
        require(context.computeOnClient(c->m.ready(name)!=null),"Character failed to load: "+name);
    }
    private static BufferedImage read(Path path) {try{return ImageIO.read(path.toFile());}catch(Exception e){throw new AssertionError(e);}}
    private static int difference(BufferedImage a,BufferedImage b,int[] area) {
        int count=0;
        for(int y=area[1]+30;y<area[3]-50;y++)for(int x=area[0]+5;x<area[2]-5;x++) {
            int u=a.getRGB(x,y),v=b.getRGB(x,y);
            int d=Math.abs((u>>16&255)-(v>>16&255))+Math.abs((u>>8&255)-(v>>8&255))+Math.abs((u&255)-(v&255));
            if(d>45)count++;
        }
        return count;
    }
    private static void geometry(CharacterModel model) {
        require(model.triangles()>50,"Empty model: "+model.name);
        for(var p:model.parts)for(Surface s:new Surface[]{p.base,p.outline,p.decals})if(s!=null) {
            require(s.quads.length%4==0,"Incomplete quad: "+model.name);
            for(float v:s.pos)require(Float.isFinite(v),"Non-finite vertex: "+model.name);
            for(int v:s.quads)require(v>=0 && v<s.vertexCount(),"Bad vertex index: "+model.name);
        }
    }
    private static float field(CustomModelScreen screen,String name) {
        try {var f=CustomModelScreen.class.getDeclaredField(name);f.setAccessible(true);return f.getFloat(screen);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void scroll(CustomModelScreen screen,float value) {
        for(String name:java.util.List.of("scroll","scrollTarget"))try {
            var f=CustomModelScreen.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(screen,value);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void wardrobeLayout(ClientGameTestContext context,CustomModel m) {
        for(int scale:new int[]{2,3,4}) {
            context.runOnClient(c->{
                c.options.getGuiScale().setValue(scale);c.onResolutionChanged();m.select(false,"None");
                c.setScreen(new CustomModelScreen(null,m));scroll((CustomModelScreen)c.currentScreen,13);
            });context.waitTicks(4);
            float[] bounds=context.computeOnClient(c->{
                var s=(CustomModelScreen)c.currentScreen;
                return new float[]{field(s,"x")+field(s,"pw")+10,field(s,"y")+70,
                    field(s,"w")-field(s,"pw")-28,field(s,"h")-127,field(s,"y")+field(s,"h")-49,
                    (float)c.getWindow().getScaleFactor()};
            });
            int row=(int)((bounds[3]+13)/29),index=row*2;
            String partial=m.choices().get(index);
            BufferedImage before=read(context.takeScreenshot("maro-wardrobe-clip-baseline-"+scale));
            context.runOnClient(c->m.select(false,partial));loaded(context,m,partial);context.waitTicks(3);
            BufferedImage after=read(context.takeScreenshot("maro-wardrobe-clip-selected-"+scale));
            int changes=0;
            for(int y=(int)Math.ceil((bounds[1]+bounds[3]+1)*bounds[5]);y<(bounds[4]-2)*bounds[5];y++)
                for(int x=(int)((bounds[0]+2)*bounds[5]);x<(bounds[0]+bounds[2]-2)*bounds[5];x++)
                    if(before.getRGB(x,y)!=after.getRGB(x,y))changes++;
            require(changes==0,"Selected card leaked into footer gap at scale "+scale+": "+changes);
            context.runOnClient(c->{
                var screen=(CustomModelScreen)c.currentScreen;
                String selected=m.selected(false),detail=m.detailLabel();boolean items=m.heldItems();
                screen.mouseClicked(new net.minecraft.client.gui.Click(bounds[0]+20,bounds[1]+bounds[3]+2,
                    new net.minecraft.client.input.MouseInput(0,0)),false);
                require(m.selected(false).equals(selected),"Invisible card captured footer gap");
                screen.mouseClicked(new net.minecraft.client.gui.Click(bounds[0]+20,bounds[4]+10,
                    new net.minecraft.client.input.MouseInput(0,0)),false);
                require(!m.detailLabel().equals(detail),"Detail footer could not be clicked");
                screen.mouseClicked(new net.minecraft.client.gui.Click(bounds[0]+bounds[2]*.75f,bounds[4]+10,
                    new net.minecraft.client.input.MouseInput(0,0)),false);
                require(m.heldItems()!=items,"Items footer could not be clicked");
                scroll(screen,10000);
            });context.waitTicks(4);
            context.takeScreenshot("maro-wardrobe-scrolled-bottom-"+scale);
            context.runOnClient(c->{
                var s=(CustomModelScreen)c.currentScreen;
                require(field(s,"scrollTarget")<10000,"Resize did not clamp scroll");
                float value=field(s,"scrollTarget");
                s.mouseScrolled(bounds[0]+20,bounds[4]+10,0,-10);
                require(field(s,"scrollTarget")==value,"Footer wheel moved the model list");
            });
        }
        context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();
            m.getSettings().forEach(Setting::reset);m.select(false,"Jewish Skin");c.setScreen(new CustomModelScreen(null,m));});
        loaded(context,m,"Jewish Skin");context.waitTicks(3);context.takeScreenshot("maro-wardrobe-fixed");
        System.out.println("[custom-model] Scale 2/3/4 selected-card pixel clipping, hit clipping, footer buttons and scroll clamping passed");
    }
    private static void motion(MinecraftClient c,CharacterModel model) {
        try {
            var update=CharacterMotion.class.getDeclaredMethod("update",PlayerEntityRenderState.class,CharacterModel.class,
                net.minecraft.client.render.entity.model.PlayerEntityModel.class,float[].class);
            update.setAccessible(true);
            var renderer=(net.minecraft.client.render.entity.PlayerEntityRenderer<?>)c.getEntityRenderDispatcher().getRenderer(c.player);
            var state=new PlayerEntityRenderState();state.id=-333;state.age=10;state.limbAmplitudeInverse=1;
            float[] first=(float[])update.invoke(null,state,model,renderer.getModel(),new float[6]);
            if(model.motionSlots==0) {require(first==null,"Rigid model created spring state");return;}
            require(first!=null,"Animated model omitted its spring state");
            float[] a=first.clone();
            state.limbSwingAnimationProgress=3;state.limbSwingAmplitude=1;
            float[] duplicate=(float[])update.invoke(null,state,model,renderer.getModel(),new float[6]);
            require(java.util.Arrays.equals(a,duplicate),"Duplicate render pass advanced animation");
            state.id=-334;state.age=30;
            update.invoke(null,state,model,renderer.getModel(),new float[6]);
            state.id=-333;state.age=11;
            for(float v:(float[])update.invoke(null,state,model,renderer.getModel(),new float[6]))require(Float.isFinite(v),"Spring became non-finite");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void customImport(ClientGameTestContext context,CustomModel m) {
        Path file=CustomCharacters.folder().resolve("Maro Native Test.glb");
        boolean created=false;
        try {
            Files.createDirectories(file.getParent());
            require(!Files.exists(file),"Test import path already exists");
            try(var input=CustomModelChecks.class.getResourceAsStream("/assets/maro_test/characters/humanoid.glb")) {
                require(input!=null,"Missing GLB test fixture");Files.write(file,input.readAllBytes());
                created=true;
            }
            context.runOnClient(c->m.reloadCharacters());
            for(int i=0;i<200 && !m.choices().contains("Maro Native Test");i++)context.waitTicks(1);
            require(m.choices().contains("Maro Native Test"),"Background GLB scan did not update wardrobe");
            context.runOnClient(c->m.select(false,"Maro Native Test"));loaded(context,m,"Maro Native Test");
            context.runOnClient(c->geometry(m.ready("Maro Native Test").model));
            context.waitTicks(4);context.takeScreenshot("maro-character-custom-glb");
            context.runOnClient(c->{CharacterCache.clear();m.select(false,"Maro Native Test");});loaded(context,m,"Maro Native Test");
            System.out.println("[custom-model] Textured GLB import and disk-cache reload passed");
        }catch(java.io.IOException e){throw new AssertionError(e);}
        finally {
            context.runOnClient(c->m.select(false,"None"));
            try{if(created)Files.deleteIfExists(file);}catch(java.io.IOException e){throw new AssertionError(e);}
            context.runOnClient(c->m.reloadCharacters());
        }
    }
    /** Actual pack reloads must recreate GPU textures while keeping the baked mesh ready. */
    static void texturePacksReload(ClientGameTestContext context) {
        CustomModel m=ModuleManager.get(CustomModel.class);
        require(m!=null,"Custom Model not registered");
        String selected=context.computeOnClient(c->m.selected(false));
        boolean enabled=context.computeOnClient(c->m.isEnabled());
        Perspective perspective=context.computeOnClient(c->c.options.getPerspective());
        try {
            context.runOnClient(c->{
                m.select(false,"CJ");m.setEnabled(true);
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            });
            loaded(context,m,"CJ");
            WornCharacter before=context.computeOnClient(c->m.ready("CJ"));
            TexturePacksChecks.run(context);
            loaded(context,m,"CJ");
            context.runOnClient(c->{
                WornCharacter after=m.ready("CJ");
                require(after!=before,"Resource pack reload kept the old character texture");
                require(after.model==before.model,"Resource pack reload unnecessarily rebuilt the mesh");
                require(m.isEnabled() && CustomModel.worn(state(c,c.player))==after,
                    "Character replacement was lost after switching packs");
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-character-after-pack-reload");
            System.out.println("[custom-model] Character survived real Texture Packs reloads with its mesh cached");
        } finally {
            context.runOnClient(c->{m.setEnabled(enabled);m.select(false,selected);c.options.setPerspective(perspective);});
        }
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        CustomModel m=ModuleManager.get(CustomModel.class);
        require(m!=null,"Custom Model not registered");
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        Perspective perspective=context.computeOnClient(c->c.options.getPerspective());
        float[] rotation=context.computeOnClient(c->new float[]{c.player.getYaw(),c.player.getPitch()});
        int[] otherId={-1};
        try {
            context.runOnClient(c->{
                m.getSettings().forEach(Setting::reset); ((ModeSetting)setting(m,"Detail")).set("High");
                for(String removed:java.util.List.of("Sakura","Luna","Mika","Aoi","Miku")) {
                    setting(m,"Your Model").fromJson(new com.google.gson.JsonPrimitive(removed));
                    setting(m,"Other Model").fromJson(new com.google.gson.JsonPrimitive(removed));
                    require(m.selected(false).equals("Neon Robot") && m.selected(true).equals("None"),
                        "Removed saved model did not keep its valid default");
                }
                c.options.getGuiScale().setValue(2);c.onResolutionChanged();
                m.select(false,"None");c.setScreen(new CustomModelScreen(null,m));
                ((CustomModelScreen)c.currentScreen).showAngle(0);
            });
            context.waitTicks(5);
            var plain=read(context.takeScreenshot("maro-character-vanilla"));
            int[] area=context.computeOnClient(c->((CustomModelScreen)c.currentScreen).previewBounds());
            require(m.choices().size()==31,"Expected None and 30 characters");
            require(ModuleManager.getByName("Auto Trident")==null,"Auto Trident remains registered");
            for(String removed:java.util.List.of("Sakura","Luna","Mika","Aoi","Miku"))
                require(!m.choices().contains(removed),"Removed model remains: "+removed);
            for(String name:m.choices()) {
                if(name.equals("None"))continue;
                context.runOnClient(c->m.select(false,name));loaded(context,m,name);
                context.runOnClient(c->{geometry(m.ready(name).model);motion(c,m.ready(name).model);});
                context.waitTicks(4);
                var shot=read(context.takeScreenshot("maro-character-"+CharacterCatalog.byName(name).id()));
                int changed=difference(plain,shot,area);
                require(changed>600,"Model not visibly rendered: "+name+" ("+changed+" pixels)");
                require(!m.isEnabled(),"Wardrobe preview enabled module");
                context.runOnClient(c->((CustomModelScreen)c.currentScreen).showAngle(70));
                context.waitTicks(2);
                context.takeScreenshot("maro-character-"+CharacterCatalog.byName(name).id()+"-profile");
                if(CharacterCatalog.byName(name) instanceof dev.maro.render.character.designs.PortraitCharacter) {
                    context.runOnClient(c->((CustomModelScreen)c.currentScreen).showAngle(180));
                    context.waitTicks(2);context.takeScreenshot("maro-character-"+CharacterCatalog.byName(name).id()+"-back");
                }
                context.runOnClient(c->((CustomModelScreen)c.currentScreen).showAngle(0));
                System.out.println("[custom-model] "+name+": "+changed+" changed pixels");
            }
            wardrobeLayout(context,m);
            context.runOnClient(c->((ModeSetting)setting(m,"Detail")).set("Low"));
            for(String name:m.choices()) {
                if(name.equals("None") || name.equals("CJ") || name.equals("Neighbor"))continue;
                context.runOnClient(c->m.select(false,name));loaded(context,m,name);
                context.runOnClient(c->geometry(m.ready(name).model));
                context.waitTicks(2);context.takeScreenshot("maro-character-"+CharacterCatalog.byName(name).id()+"-low");
            }
            context.runOnClient(c->((ModeSetting)setting(m,"Detail")).set("High"));
            context.runOnClient(c->m.select(false,"Ninja"));loaded(context,m,"Ninja");
            context.runOnClient(c->motion(c,m.ready("Ninja").model));
            customImport(context,m);
            context.runOnClient(c->{
                require(c.player.getYaw()==rotation[0] && c.player.getPitch()==rotation[1],"Preview rotated real player");
                ((ModeSetting)setting(m,"Detail")).set("Medium");m.select(false,"Neon Robot");
            });
            loaded(context,m,"Neon Robot");context.waitTicks(3);context.takeScreenshot("maro-character-wardrobe-medium");
            context.runOnClient(c->((ModeSetting)setting(m,"Detail")).set("High"));loaded(context,m,"Neon Robot");
            context.runOnClient(c->geometry(m.ready("Neon Robot").model));context.waitTicks(3);context.takeScreenshot("maro-character-high-detail");
            context.runOnClient(c->{
                ((ModeSetting)setting(m,"Detail")).set("Medium");c.options.getGuiScale().setValue(4);c.onResolutionChanged();
                ((CustomModelScreen)c.currentScreen).showOther(true);m.select(true,"Astronaut");
            });loaded(context,m,"Astronaut");context.waitTicks(3);context.takeScreenshot("maro-character-small-other-preview");
            context.runOnClient(c->{
                c.options.getGuiScale().setValue(2);c.onResolutionChanged();((CustomModelScreen)c.currentScreen).showOther(false);
            });
            context.runOnClient(c->{
                m.setEnabled(true);m.select(true,"Frog");((ModeSetting)setting(m,"Other Players")).set("Everyone");
                c.setScreen(null);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                var other=new OtherClientPlayerEntity(c.world,new GameProfile(UUID.randomUUID(),"MaroModelFixture"));
                other.setPosition(c.player.getX()+1.4,c.player.getY(),c.player.getZ());
                other.lastX=other.lastRenderX=other.getX();other.lastY=other.lastRenderY=other.getY();other.lastZ=other.lastRenderZ=other.getZ();
                c.world.addEntity(other);otherId[0]=other.getId();
            });loaded(context,m,"Frog");context.waitTicks(5);
            context.runOnClient(c->{
                require(CustomModel.worn(state(c,c.player)).model.id.equals("robot"),"Self selection lost");
                require(CustomModel.worn(state(c,c.world.getEntityById(otherId[0]))).model.id.equals("frog"),"Other selection leaked from self cache");
                var first=state(c,c.player);((CharacterHolder)first).maro$setCharacter(null);first.spectator=true;CustomModel.extract(c.player,first);
                require(!CustomModel.hidesModel(first),"Spectator was replaced");
            });
            context.takeScreenshot("maro-character-two-models");
            context.runOnClient(c->{c.options.sneakKey.setPressed(true);c.player.getInventory().setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));});
            context.waitTicks(5);context.takeScreenshot("maro-character-crouch-held-item");
            for(String name:m.choices()) {
                if(name.equals("None"))continue;
                context.runOnClient(c->m.select(false,name));loaded(context,m,name);
                context.waitTicks(2);context.takeScreenshot("maro-character-"+CharacterCatalog.byName(name).id()+"-crouch-equipment");
            }
            context.runOnClient(c->m.select(false,"Neon Robot"));loaded(context,m,"Neon Robot");
            context.runOnClient(c->{c.options.sneakKey.setPressed(false);c.player.setSneaking(false);});
            context.runOnClient(c->{
                ((ModeSetting)setting(m,"Other Players")).set("Friends");
                require(!CustomModel.hidesModel(state(c,c.world.getEntityById(otherId[0]))),"Friends filter includes strangers");
                c.options.setPerspective(Perspective.FIRST_PERSON);c.player.getInventory().clear();
                require(CustomModel.firstPerson()!=null,"First person model unavailable");
            });context.waitTicks(5);context.takeScreenshot("maro-character-first-person");
            context.runOnClient(c->{
                ((BooleanSetting)setting(m,"First Person Arms")).set(false);require(CustomModel.firstPerson()==null,"Arms toggle ignored");
                ((BooleanSetting)setting(m,"First Person Arms")).set(true);
                require(!CustomModel.hidesModel(state(c,c.player)),"First person body fills camera");
                m.setEnabled(false);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                require(!CustomModel.hidesModel(state(c,c.player)),"Disabled model persisted");
            });context.waitTicks(4);context.takeScreenshot("maro-character-disabled");
            // A file reload does not leave old GPU textures attached to a newly baked selection.
            context.runOnClient(c->{m.setEnabled(true);m.select(false,"Neon Robot");});loaded(context,m,"Neon Robot");
            context.runOnClient(c->CharacterCache.releaseTextures());loaded(context,m,"Neon Robot");
            context.runOnClient(c->geometry(m.ready("Neon Robot").model));
            System.out.println("[custom-model] All models, independent targets, preview, first person and texture recreation passed");
        } finally {
            context.runOnClient(c->{
                c.setScreen(null);m.setEnabled(false);CharacterCache.clear();m.getSettings().forEach(Setting::reset);
                c.options.sneakKey.setPressed(false);c.player.setSneaking(false);
                if(otherId[0]>=0)c.world.removeEntity(otherId[0],Entity.RemovalReason.DISCARDED);
                c.options.setPerspective(perspective);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();
                c.player.setYaw(rotation[0]);c.player.setPitch(rotation[1]);
            });
        }
    }
}
