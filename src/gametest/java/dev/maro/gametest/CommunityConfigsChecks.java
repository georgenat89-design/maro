package dev.maro.gametest;

import com.google.gson.JsonObject;
import dev.maro.config.CommunityConfigs;
import dev.maro.config.SharedModuleConfig;
import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.ModuleConfigsScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.List;

/** Production client + real SQLite API; no mock votes or list fixtures. */
final class CommunityConfigsChecks {
    private static final class Fixture extends Module {
        final NumberSetting amount=add(new NumberSetting("Amount","",4.5,0,10,.5));
        final BooleanSetting flag=add(new BooleanSetting("Flag","",true));
        final ModeSetting mode=add(new ModeSetting("Mode","","Fast","Fast","Smooth"));
        final ColorSetting color=add(new ColorSetting("Color","",0xFF336699,true));
        final BooleanSetting rejecting=add(new BooleanSetting("Listener check","",false).onChange(value->{if(value)throw new IllegalStateException("Fixture listener rejected the change");}));
        final TextSetting secret=add(new TextSetting("Private text","","local-only-secret",80,""));
        Fixture(String name){super(name,"Community preset test",Category.MISC);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static <T>T done(CompletableFuture<T> future){try{return future.get(12,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError("Community API request failed",e);}}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void until(ClientGameTestContext context,BooleanSupplier test,String message){for(int i=0;i<240;i++){if(context.computeOnClient(c->test.getAsBoolean()))return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void click(ClientGameTestContext context,ModuleConfigsScreen screen,float x,float y){context.runOnClient(c->{var p=screen.point(x,y);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);});context.waitTicks(3);}
    private static void rejected(Fixture module,JsonObject config){try{SharedModuleConfig.apply(module,config);throw new AssertionError("Invalid config was applied");}catch(IllegalArgumentException expected){}}

    private static double[] modulePoint(ClickGuiScreen gui,Module module){
        if(gui.showingPanels()){var p=gui.panelPlace(module);require(p!=null,"Module row missing");return new double[]{p[0],p[1]};}
        for(var hit:(List<?>)field(gui,"hits"))if(Math.abs(((Number)field(hit,"h")).floatValue()-40)<.01&&((Number)field(hit,"w")).floatValue()>100)
            return new double[]{((Number)field(hit,"x")).doubleValue()+16,((Number)field(hit,"y")).doubleValue()+20};
        throw new AssertionError("Module card missing");
    }
    private static void navigation(ClientGameTestContext context,Module module){
        String layout=ClientSettings.menuStyle.get();boolean spatial=ClientSettings.spatialMenu.get(),screenGui=ClientSettings.screenGui.get();
        try{
            for(String style:new String[]{"Window","Panels"}){
                var gui=context.computeOnClient(c->{ClientSettings.menuStyle.set(style);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);
                    var g=new ClickGuiScreen();c.setScreen(g);g.getSearch().setText(module.getName());
                    if(g.showingPanels())try{var panels=field(g,"panels");var reveal=panels.getClass().getDeclaredMethod("revealCategory",Category.class);reveal.setAccessible(true);reveal.invoke(panels,module.getCategory());}catch(Exception e){throw new AssertionError(e);}
                    return g;});context.waitTicks(16);
                boolean enabled=module.isEnabled();
                context.runOnClient(c->{var p=modulePoint(gui,module);gui.clickAt(p[0],p[1],1);
                    require(c.currentScreen==gui,"Right-click did not retain settings in "+style);
                    require(module.isEnabled()==enabled,"Right-click toggled the module");});context.waitTicks(4);
                context.takeScreenshot("maro-public-configs-header-"+style.toLowerCase());
                context.runOnClient(c->{var p=gui.moduleConfigsPlace(module);require(p!=null,"Settings page icon missing in "+style);gui.clickAt(p[0],p[1],0);require(c.currentScreen instanceof ModuleConfigsScreen&&field(c.currentScreen,"module")==module,"Page icon opened wrong module");require(module.isEnabled()==enabled,"Page icon toggled module");});context.waitTicks(4);
                context.takeScreenshot("maro-public-configs-from-icon-"+style.toLowerCase());
                var publicScreen=context.computeOnClient(c->(ModuleConfigsScreen)c.currentScreen);
                click(context,publicScreen,488,36);
                context.runOnClient(c->{require(c.currentScreen==gui,"Settings did not return to the module in "+style);
                    if(style.equals("Panels")){var pop=field(field(gui,"panels"),"popover");require(pop!=null&&field(pop,"module")==module,"Wrong module popover");}
                    else{Object page=gui.getSearch().getText().isEmpty()?field(((List<?>)field(gui,"entries")).get(gui.selectedEntry()),"page"):field(gui,"searchPage");require(field(page,"open")==module,"Wrong module settings page");}
                });context.takeScreenshot("maro-public-configs-settings-return-"+style.toLowerCase());
                context.runOnClient(c->{gui.openPublicConfigs(module);});context.waitTicks(3);
                context.runOnClient(c->{((ModuleConfigsScreen)c.currentScreen).close();require(c.currentScreen==gui,"Close did not return to original GUI");});
            }
            for(var custom:List.of(ModuleManager.get(dev.maro.module.impl.visuals.TexturePacks.class),ModuleManager.get(dev.maro.module.impl.visuals.SkinAccessories.class))){
                var gui=context.computeOnClient(c->{ClientSettings.menuStyle.set("Window");var g=new ClickGuiScreen();c.setScreen(g);g.getSearch().setText(custom.getName());return g;});context.waitTicks(16);
                context.runOnClient(c->{var p=modulePoint(gui,custom);gui.clickAt(p[0],p[1],1);require(c.currentScreen!=gui&&!(c.currentScreen instanceof ModuleConfigsScreen),"Custom panel did not open as usual");c.setScreen(gui);gui.openModuleOptions(custom);});context.waitTicks(5);
                context.runOnClient(c->{var p=gui.moduleConfigsPlace(custom);require(p!=null,"Custom module options icon missing");gui.clickAt(p[0],p[1],0);require(c.currentScreen instanceof ModuleConfigsScreen&&field(c.currentScreen,"module")==custom,"Custom module page icon mismatch");});context.waitTicks(3);
                click(context,context.computeOnClient(c->(ModuleConfigsScreen)c.currentScreen),488,36);
                context.runOnClient(c->require(c.currentScreen==gui,"Custom raw options were not retained"));
            }
            System.out.println("[public-config-context] PASS: real Window/Panels right-click settings and header page clicks, exact module binding, no activation change, original settings/popover return, close/back and custom-module panel access");
        }finally{context.runOnClient(c->{c.setScreen(null);ClientSettings.menuStyle.set(layout);ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screenGui);});CommunityConfigs.shutdown();}
    }

    static void run(ClientGameTestContext context){
        String url=System.getProperty("maro.gametest.communityUrl","");require(!url.isBlank(),"Start tools/config-community/server.py and pass -PcommunityTestUrl");
        Path temporary;
        try{temporary=Files.createTempDirectory("maro-community-test-");}catch(Exception e){throw new AssertionError(e);}
        var alice=new CommunityConfigs(temporary.resolve("alice.json"),"Alice");
        var bob=new CommunityConfigs(temporary.resolve("bob.json"),"Bob");
        int savedScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var module=context.computeOnClient(c->{var m=new Fixture("Config Test");ModuleManager.register(m);m.getBind().set(71);m.setEnabled(true);return m;});
        var other=context.computeOnClient(c->new Fixture("Other Test"));
        try{
            JsonObject baseline=context.computeOnClient(c->{
                int count=0;
                for(var m:ModuleManager.all()){
                    var shared=SharedModuleConfig.capture(m);SharedModuleConfig.preview(m,shared);
                    require(!shared.has("enabled")&&!shared.has("bind"),"Preset includes activation or bind");count++;
                }
                System.out.println("[community-configs] Captured and validated "+count+" modules");
                var data=SharedModuleConfig.capture(module);require(!data.toString().contains("local-only-secret")&&!data.getAsJsonObject("settings").has("Private text"),"Public preset contains private text");
                return data;
            });
            JsonObject proposed=baseline.deepCopy();proposed.getAsJsonObject("settings").addProperty("Amount",7.5);proposed.getAsJsonObject("settings").addProperty("Flag",false);proposed.getAsJsonObject("settings").addProperty("Mode","Smooth");
            proposed.getAsJsonObject("settings").addProperty("Private text","attack text");proposed.getAsJsonObject("settings").addProperty("Keybind",72);
            context.runOnClient(c->{
                var preview=SharedModuleConfig.preview(module,proposed);require(preview.changes().size()==3&&preview.skipped()==2,"Preview did not separate supported/private options");
                var before=SharedModuleConfig.apply(module,proposed);require(module.amount.get()==7.5&&!module.flag.get()&&module.mode.is("Smooth"),"Module options not applied");
                require(module.isEnabled()&&module.getBind().get()==71&&module.secret.get().equals("local-only-secret"),"Preset changed state, bind or private text");
                require(other.amount.get()==4.5&&other.flag.get(),"Another module was changed");SharedModuleConfig.apply(module,before);
                var invalid=proposed.deepCopy();invalid.getAsJsonObject("settings").addProperty("Amount",1000);rejected(module,invalid);
                require(module.amount.get()==4.5&&module.flag.get(),"Failed validation changed a setting");
                var wrong=proposed.deepCopy();wrong.addProperty("module","Other Test");rejected(module,wrong);
                var activation=proposed.deepCopy();activation.addProperty("enabled",true);rejected(module,activation);
                var wrongVersion=proposed.deepCopy();wrongVersion.addProperty("minecraft","1.20");rejected(module,wrongVersion);
                var file=proposed.deepCopy();file.getAsJsonObject("extra").addProperty("file","../secret");rejected(module,file);
                var failing=baseline.deepCopy();failing.getAsJsonObject("settings").addProperty("Amount",8);failing.getAsJsonObject("settings").addProperty("Listener check",true);
                try{SharedModuleConfig.apply(module,failing);throw new AssertionError("Listener failure was ignored");}catch(IllegalStateException expected){}
                require(module.amount.get()==4.5&&!module.rejecting.get(),"Listener failure left partial changes");
            });
            navigation(context,module);
            done(alice.connect(url));done(bob.connect(url));
            // Publish the actual capture, without unknown/private fields.
            JsonObject shared=proposed.deepCopy();shared.getAsJsonObject("settings").remove("Private text");shared.getAsJsonObject("settings").remove("Keybind");
            var first=done(alice.publish("Smooth community preset","A tested per-module preset",shared));
            JsonObject newer=shared.deepCopy();newer.getAsJsonObject("settings").addProperty("Amount",6);
            var second=done(alice.publish("Newer community preset","Alternative settings",newer));
            require(first.own()&&!first.liked()&&first.likes()==0,"Author state is incorrect");
            require(done(bob.like(first,true)).likes()==1&&done(bob.like(first,true)).likes()==1,"Duplicate like changed the count");
            var ranked=done(bob.browse(module.getName(),"",true,false));require(ranked.getFirst().id().equals(first.id()),"Top liked did not rank by likes");
            require(ranked.getFirst().config()==null&&ranked.getFirst().liked(),"Browse summary carries wrong details or vote");
            require(done(bob.browse(module.getName(),"Alternative",true,false)).size()==1,"Search failed");
            done(bob.browse(module.getName(),"",true,false));
            var screen=context.computeOnClient(c->{var s=new ModuleConfigsScreen(null,module,bob);c.setScreen(s);return s;});
            until(context,()->!bob.state().busy(),"Browse did not finish");context.waitTicks(4);
            for(int gui:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});context.waitTicks(5);
                until(context,()->!bob.state().busy(),"Browse resize did not finish");context.takeScreenshot("maro-public-configs-scale-"+gui);
            }
            click(context,screen,690,177);
            until(context,()->field(screen,"selected")!=null,"Preview did not open");context.waitTicks(4);
            context.takeScreenshot("maro-public-configs-preview");
            click(context,screen,100,380);
            context.runOnClient(c->require(module.amount.get()==7.5&&!module.flag.get()&&module.isEnabled()&&module.getBind().get()==71,"Apply button failed module isolation"));
            click(context,screen,690,420);
            context.runOnClient(c->require(module.amount.get()==4.5&&module.flag.get(),"Undo button did not restore the module"));
            click(context,screen,265,380);
            until(context,()->!bob.state().busy(),"Unlike did not finish");
            require(done(bob.detail(first.id())).likes()==0,"Unlike button failed");
            // The local identity survives reconnecting and reopening the client.
            bob.close();
            try(var reloaded=new CommunityConfigs(temporary.resolve("bob.json"),"Bob")){
            done(reloaded.connect(url));require(!done(reloaded.detail(first.id())).liked(),"Identity restore changed the saved vote");
            var compose=context.computeOnClient(c->{var s=new ModuleConfigsScreen(null,module,reloaded);c.setScreen(s);return s;});
            until(context,()->!reloaded.state().busy(),"Reload browse did not finish");context.waitTicks(4);
            click(context,compose,680,116);
            context.runOnClient(c->{require(field(compose,"titleField") instanceof TextFieldWidget,"Post form did not open");((TextFieldWidget)field(compose,"titleField")).setText("Bob's preset");((TextFieldWidget)field(compose,"descriptionField")).setText("Posted from the game");});context.waitTicks(3);
            context.takeScreenshot("maro-public-configs-post");click(context,compose,100,370);
            until(context,()->field(compose,"selected")!=null,"Post button did not publish");
            var posted=(CommunityConfigs.Post)context.computeOnClient(c->field(compose,"selected"));require(posted.own()&&posted.author().equals("Bob"),"Wrong post ownership");
            click(context,compose,690,380);click(context,compose,690,380);
            until(context,()->!reloaded.state().busy(),"Delete did not finish");
            require(done(reloaded.browse(module.getName(),"Bob's preset",true,false)).isEmpty(),"Delete button left the post online");
            click(context,compose,595,36);context.takeScreenshot("maro-public-configs-server");
            }
            done(alice.delete(first));done(alice.delete(second));
            System.out.println("[community-configs] PASS: all-module validation, private text/bind exclusion, strict ranges/module/version, one-module apply/undo, real API publish/search/likes/top, identity reload, post/delete buttons and 3 GUI scales");
        }finally{
            alice.close();bob.close();context.runOnClient(c->{module.setEnabled(false);c.setScreen(null);c.options.getGuiScale().setValue(savedScale);c.onResolutionChanged();});
        }
    }
}
