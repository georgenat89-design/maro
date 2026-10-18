package dev.maro.gametest;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import dev.maro.config.ConfigManager;
import dev.maro.gui.hud.*;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.phone.browser.PhoneWebBrowser;
import dev.maro.textures.GameplayTextureMigration;
import dev.maro.textures.PacksMcInstallQueue;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

final class GameplayTexturesChecks {
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void field(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void until(ClientGameTestContext c,BooleanSupplier test,String message){for(int i=0;i<700&&!c.computeOnClient(mc->test.getAsBoolean());i++)c.waitTicks(1);require(c.computeOnClient(mc->test.getAsBoolean()),message);}
    private static void migration(){
        var modules=JsonParser.parseString("""
          {"Texture Packs":{"enabled":false,"bind":-1,"settings":{"Notify":false},"extra":{"picked":["file/local.zip"],"usual":["file/usual.zip"]}},
           "Better Looks":{"enabled":true,"bind":71,"settings":{"No Clouds":true}},
           "Better Textures":{"enabled":true,"bind":-1,"extra":{"packs":[{"project":"demo","file":"download.zip"}],"active":["demo"]}}}
          """).getAsJsonObject();
        GameplayTextureMigration.apply(modules);var result=modules.getAsJsonObject("Gameplay Textures");
        require(result.get("enabled").getAsBoolean()&&result.get("bind").getAsInt()==71,"Legacy activation or bind lost");
        require(result.getAsJsonObject("settings").get("No Clouds").getAsBoolean()&&!result.getAsJsonObject("settings").get("Notify").getAsBoolean(),"Legacy settings lost");
        require(result.getAsJsonObject("extra").getAsJsonArray("picked").toString().equals("[\"file/download.zip\",\"file/local.zip\"]"),"Merged pack priority changed");
        require(result.getAsJsonObject("extra").getAsJsonArray("legacyManaged").size()==1,"Legacy packs would be restored as usual packs");
        var before=result.deepCopy();GameplayTextureMigration.apply(modules);require(before.equals(modules.get("Gameplay Textures")),"Migration overwrote the merged profile");
    }
    static void run(ClientGameTestContext context){
        var module=TexturePacks.get();var saved=module.saveExtra();boolean enabled=module.isEnabled();int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        HttpServer server=null;Path installed=null;var collectionFiles=new ArrayList<Path>();
        try{
            migration();
            context.runOnClient(c->{
                for(String old:new String[]{"Texture Packs","Better Looks","Better Textures"})require(ModuleManager.getByName(old)==null,"Duplicate legacy module: "+old);
                require(ModuleManager.search("Gameplay Textures").size()==1,"Unified module missing or duplicated");
                module.appearance.set(true);module.setEnabled(true);
            });
            BetterLooksChecks.run(context);
            VisualStudioChecks.run(context);
            for(int gui:new int[]{2,3,4}){
                var screen=context.computeOnClient(c->{c.options.getGuiScale().setValue(gui);c.onResolutionChanged();var s=new GameplayTexturesScreen(null,module);c.setScreen(s);return s;});context.waitTicks(4);
                context.takeScreenshot("maro-gameplay-textures-scale-"+gui);
                context.runOnClient(c->{var p=screen.point(675,325);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);require(c.currentScreen instanceof BetterLooksScreen,"Appearance card coordinates failed");});
                for(int nav=0;nav<3;nav++){
                    int index=nav;context.runOnClient(c->c.setScreen(screen));context.waitTicks(2);
                    context.runOnClient(c->{var p=screen.point(75,new float[]{134,172,350}[index]);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);
                        require(index==0?c.currentScreen instanceof PacksMcScreen:index==1?c.currentScreen instanceof BetterTexturesScreen:c.currentScreen instanceof TexturePacksScreen,"Sidebar navigation failed");});
                }
            }
            if(PhoneWebBrowser.available()){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                String metadata=context.computeOnClient(c->{try(var pack=c.getResourcePackManager().getProfile("programmer_art").createResourcePack();var in=pack.openRoot("pack.mcmeta").get()){var o=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();o.remove("overlays");return o.toString();}catch(Exception e){throw new AssertionError(e);}});
                try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
                byte[] archive=bytes.toByteArray();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
                server.createContext("/",exchange->{byte[] body;
                    if(exchange.getRequestURI().getPath().equals("/pack.zip")){body=archive;exchange.getResponseHeaders().set("Content-Type","application/zip");exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=fixture-pack.zip");}
                    else {body="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#132335;color:white;font:24px sans-serif;margin:20px}a{display:block;padding:20px;background:#258765;color:white;width:350px}</style><h1>Creator's pack page</h1><a href='/pack.zip'>Download Java resource pack</a>".getBytes();exchange.getResponseHeaders().set("Content-Type","text/html");}
                    exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
                });server.start();String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";
                var chosen=module.catalog().packs().getFirst();
                var screen=context.computeOnClient(c->{var s=new PackInstallScreen(null,module,chosen);field(s,"url",url);c.setScreen(s);return s;});
                until(context,()->field(screen,"browser")!=null&&((PhoneWebBrowser)field(screen,"browser")).frame()!=null,"Embedded page did not render");context.waitTicks(8);
                context.takeScreenshot("maro-gameplay-textures-install-page");
                context.runOnClient(c->{var p=screen.point(120,205);var event=new Click(p[0],p[1],new MouseInput(0,0));screen.mouseClicked(event,false);screen.mouseReleased(event);});
                until(context,()->(boolean)field(screen,"installed"),"Download did not install automatically: "+field(screen,"message"));
                until(context,()->module.gameHasOn(module.importedProfile(chosen))&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null,"Downloaded pack did not apply");
                installed=module.packDir().resolve(module.importedProfile(chosen).substring(5));require(java.util.Arrays.equals(Files.readAllBytes(installed),archive),"Embedded install changed archive bytes");
                context.takeScreenshot("maro-gameplay-textures-installed");
                Path staging=(Path)field(screen,"staging");context.runOnClient(c->c.setScreen(null));until(context,()->!Files.exists(staging),"Download staging was not cleaned");

                // Two distinct packs, saved by the collection without changing the active loadout.
                var candidates=module.catalog().packs().subList(1,3);var active=module.picked();boolean wasEnabled=module.isEnabled();
                var batch=context.computeOnClient(c->{var s=new PackInstallScreen(null,module,candidates);field(s,"url",url);c.setScreen(s);return s;});
                for(int i=0;i<2;i++){
                    if(i>0)context.runOnClient(c->{field(batch,"url",url);try{var m=PackInstallScreen.class.getDeclaredMethod("restart");m.setAccessible(true);m.invoke(batch);}catch(Exception e){throw new AssertionError(e);}});
                    until(context,()->field(batch,"browser")!=null&&((PhoneWebBrowser)field(batch,"browser")).frame()!=null,"Collection page did not render");context.waitTicks(8);
                    context.runOnClient(c->{var p=batch.point(120,205);var event=new Click(p[0],p[1],new MouseInput(0,0));batch.mouseClicked(event,false);batch.mouseReleased(event);});
                    int expected=i+1;until(context,()->((PacksMcInstallQueue)field(batch,"queue")).saved()==expected,"Collection did not advance after saving");
                    var pack=candidates.get(i);collectionFiles.add(module.packDir().resolve(module.importedProfile(pack).substring(5)));
                    require(context.computeOnClient(c->module.picked().equals(active)&&module.isEnabled()==wasEnabled),"Saving collection switched the active loadout");
                }
                context.takeScreenshot("maro-gameplay-textures-collection-saved");
                var queue=(PacksMcInstallQueue)field(batch,"queue");require(queue.current()==null&&queue.saved()==2,"Collection finish state is incorrect");
                context.runOnClient(c->{
                    var resumed=new PacksMcInstallQueue(candidates,module::cached);require(resumed.current()==null&&resumed.saved()==2,"Resume downloads saved packs again");
                    var extra=module.saveExtra();extra.add("packsmcImports",new JsonObject());module.loadExtra(extra);module.opened();
                    require(candidates.stream().allMatch(module::cached),"Resourcepacks library was not recovered without import metadata");
                    var s=new PacksMcScreen(null,module);s.selectPack(candidates.getLast());c.setScreen(s);
                });context.waitTicks(4);context.takeScreenshot("maro-packsmc-saved-one-click");
                context.runOnClient(c->{var s=(PacksMcScreen)c.currentScreen;var p=s.point(700,319);s.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);});
                until(context,()->module.gameHasOn(module.importedProfile(candidates.getLast()))&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null,"Saved pack's one-click Apply failed");
                var pending=new PacksMcInstallQueue(candidates,p->false);pending.skip();require(pending.current().id().equals(candidates.getLast().id())&&pending.skipped()==1,"Collection Skip failed");
                var overCap=new ArrayList<>(module.catalog().packs());overCap.addAll(candidates);
                require(new PacksMcInstallQueue(overCap,p->false).total()==50,"Collection exceeded its 50-pack cap");
            }
            context.runOnClient(c->require(ModuleManager.get(JumpCircle.class).getSettings().stream().noneMatch(s->s.getName().equals("Display Name")),"Circles rename control remains"));
            SkinAccessoriesChecks.run(context);
            System.out.println("[gameplay-textures] PASS: redesigned hub/sidebar at 3 GUI scales, single registry entry, migration, native browser download/install/apply/cleanup, two-pack collection advancement without activation, resume, library recovery, one-click Apply, Skip, 50 cap and cosmetics");
        }catch(Exception e){throw new AssertionError(e);}finally{
            context.runOnClient(c->{c.setScreen(null);module.setEnabled(false);module.loadExtra(saved);module.setEnabled(enabled);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});context.waitTicks(10);
            if(server!=null)server.stop(0);try{if(installed!=null)Files.deleteIfExists(installed);for(var file:collectionFiles)Files.deleteIfExists(file);}catch(IOException ignored){}
        }
    }
}
