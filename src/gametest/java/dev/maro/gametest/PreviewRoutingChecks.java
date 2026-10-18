package dev.maro.gametest;

import com.google.gson.*;
import dev.maro.config.ClientSettings;
import dev.maro.gui.*;
import dev.maro.gui.render.CameraPreview;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.Category;
import dev.maro.module.impl.visuals.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import javax.imageio.ImageIO;
import java.util.*;

final class PreviewRoutingChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Setting<?> setting(Module m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void open(ClientGameTestContext context,Module m){context.runOnClient(c->{c.setScreen(new ClickGuiScreen());((ClickGuiScreen)c.currentScreen).openModuleOptions(m);});context.getInput().setCursorPos(0,0);context.waitTicks(5);}
    private static int diff(java.nio.file.Path a,java.nio.file.Path b){try{var aa=ImageIO.read(a.toFile());var bb=ImageIO.read(b.toFile());int count=0;for(int y=0;y<aa.getHeight();y++)for(int x=aa.getWidth()/2;x<aa.getWidth();x++)if(aa.getRGB(x,y)!=bb.getRGB(x,y))count++;return count;}catch(Exception e){throw new AssertionError(e);}}
    static void run(ClientGameTestContext context){
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));ModuleManager.all().forEach(m->m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue()),fov=context.computeOnClient(c->c.options.getFov().getValue());var perspective=context.computeOnClient(c->c.options.getPerspective());
        var stretch=ModuleManager.get(StretchRes.class);var pet=ModuleManager.get(Pet.class);var customFov=ModuleManager.get(dev.maro.nathan.modules.CustomFov.class);var zoom=ModuleManager.get(dev.maro.nathan.modules.KeyZoom.class);
        try{
            context.runOnClient(c->{ClientSettings.menuStyle.set("Panel");ClientSettings.guiSize.set(85d);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.options.getGuiScale().setValue(2);c.onResolutionChanged();});
            context.runOnClient(c->{var audit=new JsonArray();for(Module m:ModuleManager.all()){var kind=VisualPreview.kind(m);if(m.getCategory()!=Category.VISUALS&&kind==VisualPreview.Kind.NONE)continue;var entry=new JsonObject();entry.addProperty("module",m.getName());entry.addProperty("class",m.getClass().getName());entry.addProperty("preview",kind.name());entry.addProperty("reason",kind==VisualPreview.Kind.NONE?"Use in-world effect or dedicated editor; no unrelated portrait":"Explicit subject renderer");audit.add(entry);if(kind==VisualPreview.Kind.PLAYER)require(m instanceof PlayerESP||m instanceof CustomModel||m instanceof Nametags||m instanceof SkinAccessories,"Unrelated player route: "+m.getName());}try{java.nio.file.Files.writeString(java.nio.file.Path.of("preview-routing-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(audit));}catch(Exception e){throw new AssertionError(e);}});
            open(context,stretch);
            context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.visualPreview().bounds()!=null&&g.visualPreview().lastState()==null&&g.visualPreview().lastSubjectState()==null,"Stretch Res queued an unrelated character");((ModeSetting)setting(stretch,"Ratio")).set("4:3");((NumberSetting)setting(stretch,"Stretch Amount")).set(100d);});context.waitTicks(4);var before=context.takeScreenshot("maro-preview-stretch-4-3");
            float normal=context.computeOnClient(c->CameraPreview.stretchScale(stretch));
            context.runOnClient(c->((ModeSetting)setting(stretch,"Ratio")).set("1:1"));context.waitTicks(4);var after=context.takeScreenshot("maro-preview-stretch-1-1");require(context.computeOnClient(c->CameraPreview.stretchScale(stretch))>normal,"Aspect selector did not change horizontal scale");require(diff(before,after)>100,"Aspect selector did not change native preview pixels");
            context.runOnClient(c->((NumberSetting)setting(stretch,"Stretch Amount")).set(200d));context.waitTicks(4);require(context.computeOnClient(c->CameraPreview.stretchScale(stretch))>normal*1.4,"Stretch amount did not change comparison");context.takeScreenshot("maro-preview-stretch-amount");
            for(Module target:List.of(customFov,zoom)){
                open(context,target);context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;require(g.visualPreview().bounds()!=null&&g.visualPreview().lastState()==null,"FOV needs a camera comparison");});
                if(target==customFov){context.runOnClient(c->setting(target,"fov").fromJson(new JsonPrimitive(40)));context.waitTicks(4);var narrow=context.takeScreenshot("maro-preview-fov-40");context.runOnClient(c->setting(target,"fov").fromJson(new JsonPrimitive(130)));context.waitTicks(4);var wide=context.takeScreenshot("maro-preview-fov-130");require(diff(narrow,wide)>100,"FOV setting did not change native preview pixels");}
                else context.runOnClient(c->require(CameraPreview.configuredFov(target)<fov,"Key Zoom preview is not zoomed"));
            }
            open(context,pet);for(String kind:Pet.STYLES){context.runOnClient(c->((ModeSetting)setting(pet,"Pet")).set(kind));context.waitTicks(3);context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(p.lastState()==null&&p.lastSubjectState()!=null,"Pet failed to render its own subject: "+kind);require(!pet.isEnabled()&&pet.companion()==null,"Preview created a live companion");});if(kind.equals("Wolf")||kind.equals("Dragon"))context.takeScreenshot("maro-preview-pet-"+kind.toLowerCase());}
            context.runOnClient(c->{((ModeSetting)setting(pet,"Pet")).set("Wolf");((ModeSetting)setting(pet,"Collar")).set("Red");((BooleanSetting)setting(pet,"Baby")).set(true);((NumberSetting)setting(pet,"Size")).set(.6d);});context.waitTicks(4);context.runOnClient(c->{var state=((ClickGuiScreen)c.currentScreen).visualPreview().lastSubjectState();require(state instanceof net.minecraft.client.render.entity.state.WolfEntityRenderState wolf&&wolf.collarColor==net.minecraft.util.DyeColor.RED&&wolf.baby,"Pet appearance settings did not reach native renderer");});context.takeScreenshot("maro-preview-pet-baby-red");
            for(Module target:List.of(ModuleManager.get(CustomTotem.class),ModuleManager.get(BlockESP.class),ModuleManager.get(StorageESP.class),ModuleManager.get(JumpCircle.class))){open(context,target);context.runOnClient(c->{var p=((ClickGuiScreen)c.currentScreen).visualPreview();require(p.bounds()!=null&&(target instanceof JumpCircle?p.lastState()!=null:p.lastState()==null),"Wrong subject for "+target.getName());require(!target.isEnabled(),"Preview enabled "+target.getName());});context.takeScreenshot("maro-preview-subject-"+target.getName().replace(' ','-').toLowerCase());}
            for(Module target:ModuleManager.byCategory(Category.VISUALS))if(VisualPreview.kind(target)==VisualPreview.Kind.NONE){open(context,target);context.runOnClient(c->require(((ClickGuiScreen)c.currentScreen).visualPreview().bounds()==null,"Unsupported effect kept a misleading portrait: "+target.getName()));}
            for(String style:new String[]{"Panel","Panels"})for(int size:new int[]{2,4}){
                context.runOnClient(c->{ClientSettings.menuStyle.set(style);c.options.getGuiScale().setValue(size);c.onResolutionChanged();});open(context,stretch);
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var b=g.visualPreview().bounds();require(b!=null,"Aspect preview missing at "+style+"/"+size);var a=g.screenPose().project(b[0],b[1]);var z=g.screenPose().project(b[0]+b[2],b[1]+b[3]);require(a[0]>=0&&a[1]>=0&&z[0]<=g.width+1&&z[1]<=g.height+1,"Preview exceeds viewport");});context.takeScreenshot("maro-preview-camera-"+style.toLowerCase()+"-"+size);
            }
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new net.minecraft.client.gui.screen.Screen(net.minecraft.text.Text.literal("Hidden comparison clipping")){
                @Override public void render(net.minecraft.client.gui.DrawContext ctx,int mx,int my,float delta){
                    var render=dev.maro.gui.render.Render2D.getScissor();dev.maro.gui.render.Render2D.setAlpha(1);dev.maro.gui.render.Render2D.setScissor(null);dev.maro.gui.render.Render2D.rect(ctx,0,0,width,height,0xFF000000);
                    ctx.enableScissor(0,0,8,8);var tiny=new net.minecraft.client.gui.ScreenRect(0,0,8,8);dev.maro.gui.render.Render2D.setScissor(tiny);
                    try{CameraPreview.draw(ctx,stretch,100,100,280,120);require(dev.maro.gui.render.Render2D.getScissor()==tiny,"Comparison failed to restore parent clip");}finally{ctx.disableScissor();dev.maro.gui.render.Render2D.setScissor(render);}
                }
            });});context.waitTicks(4);var clipped=context.takeScreenshot("maro-preview-hidden-clip");
            try{var image=ImageIO.read(clipped.toFile());for(int y=180;y<460;y++)for(int x=180;x<780;x++)require((image.getRGB(x,y)&0xFFFFFF)==0,"Offscreen comparison leaked beyond settings clip");}catch(java.io.IOException e){throw new AssertionError(e);}
            context.runOnClient(c->require(c.options.getFov().getValue()==fov&&c.options.getPerspective()==perspective&&ModuleManager.all().stream().noneMatch(Module::isEnabled),"Preview changed real options or enabled a module"));
            System.out.println("[preview-routing] PASS: audited every Visuals module; aspect ratio/amount and FOV native pixel edits; Key Zoom; all 13 native pet types, baby/collar; totem/block/storage/ring subjects; unsupported effects have no false portrait; Panel/Dropdown scales 2/4; real FOV/perspective/module states unchanged");
        }finally{context.runOnClient(c->{c.setScreen(null);saved.forEach(Setting::fromJson);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
