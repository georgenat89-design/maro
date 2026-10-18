package dev.maro.gametest;

import dev.maro.config.ConfigManager;
import dev.maro.gui.hud.PacksMcScreen;
import dev.maro.gui.hud.GameplayTexturesScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.textures.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

final class PacksMcChecks {
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static void await(ClientGameTestContext c,BooleanSupplier test,String m){for(int i=0;i<1000&&!c.computeOnClient(mc->test.getAsBoolean());i++)c.waitTicks(1);require(c.computeOnClient(mc->test.getAsBoolean()),m);}
    private static String packMeta(net.minecraft.client.MinecraftClient c) {
        var profile=c.getResourcePackManager().getProfile("programmer_art");
        try(var p=profile.createResourcePack();var in=p.openRoot("pack.mcmeta").get()){
            var root=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            root.remove("overlays");root.getAsJsonObject("pack").addProperty("description","PacksMC import fixture");return root.toString();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static void run(ClientGameTestContext context){
        var module=ModuleManager.get(TexturePacks.class);var prior=module.saveExtra();boolean enabled=module.isEnabled();int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());
        Path source=null,installed=null;
        try{
            context.runOnClient(c->module.refreshCatalog(true));
            await(context,()->!module.catalog().loading()&&module.catalog().packs().size()==50,"Catalog did not load exactly 50 packs");
            var packs=module.catalog().packs();require(new HashSet<>(packs.stream().map(PacksMcCatalog.Pack::id).toList()).size()==50,"Catalog has duplicates");
            require(packs.stream().allMatch(p->!p.author().isBlank()&&!p.preview().isBlank()&&PacksMcCatalog.packPage(p.page())),"Missing preview, credit or canonical page");
            try(var in=PacksMcChecks.class.getResourceAsStream("/assets/maro/catalog/packsmc.json")){
                var root=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                root.getAsJsonArray("packs").add(root.getAsJsonArray("packs").get(0));
                require(PacksMcCatalog.parse(root.toString()).size()==50,"Parser exceeded 50-pack cap");
            }
            require(!PacksMcCatalog.imageUri("https://packsmc.com.evil.test/image.png")&&!PacksMcCatalog.imageUri("http://packsmc.com/p.png"),"Untrusted preview source accepted");
            // Check every one of the imported preview links, not just the first row visible in the menu.
            var futures=packs.stream().map(p->PacksMcCatalog.previewBytes(p.preview(),true)).toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(100,TimeUnit.SECONDS);
            require(futures.stream().allMatch(f->f.join().length>100),"A preview was empty");
            require(packs.stream().allMatch(p->Files.isRegularFile(PacksMcCatalog.previewFile(p.preview()))),"A preview was not saved for offline use");
            require(Arrays.equals(futures.getFirst().join(),PacksMcCatalog.previewBytes(packs.getFirst().preview()).get(5,TimeUnit.SECONDS)),"Saved preview changed its bytes");
            for(int scale:new int[]{2,3,4}){
                PacksMcScreen screen=context.computeOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();var s=new PacksMcScreen(null,module);c.setScreen(s);return s;});
                context.waitTicks(5);await(context,()->screen.previewReady(packs.getFirst()),"Native preview did not upload");
                context.runOnClient(c->{var p=screen.cardPoint(1);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);require(screen.selected().id().equals(packs.get(1).id()),"Pack card input missed");});
                context.takeScreenshot("maro-packsmc-scale-"+scale);
                context.runOnClient(c->{screen.query(packs.get(1).author());require(screen.shown().stream().allMatch(p->(p.name()+" "+p.author()+" "+p.resolution()).toLowerCase(Locale.ROOT).contains(packs.get(1).author().toLowerCase(Locale.ROOT))),"Creator search failed");screen.query("");
                    module.toggleFavorite(packs.getFirst().key());screen.favoritesOnly(true);require(screen.shown().contains(packs.getFirst()),"Favorites filter failed");screen.favoritesOnly(false);module.toggleFavorite(packs.getFirst().key());screen.zoom(true);});
                context.waitTicks(3);context.takeScreenshot("maro-packsmc-preview-scale-"+scale);
                context.runOnClient(c->{screen.zoom(false);screen.scrollToEnd();});context.waitTicks(15);context.takeScreenshot("maro-packsmc-last-row-scale-"+scale);
            }
            source=Files.createTempFile("maro-packsmc-proof-",".zip");
            try(var zip=new ZipOutputStream(Files.newOutputStream(source))){zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write(context.computeOnClient(c->packMeta(c)).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
            var file=source;var chosen=packs.getFirst();var imported=context.computeOnClient(c->module.importPack(chosen,file));await(context,imported::isDone,"Import never completed");installed=imported.join();
            await(context,()->!module.importing()&&module.gameHasOn(module.importedProfile(chosen))&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null,"Imported ZIP did not apply");
            require(Arrays.equals(Files.readAllBytes(source),Files.readAllBytes(installed)),"Import changed resource-pack bytes");
            context.runOnClient(c->{var saved=module.saveExtra();module.loadExtra(saved);require(!module.importedProfile(chosen).isEmpty(),"Imported association did not persist");module.setEnabled(false);});
            await(context,()->!module.gameHasOn(module.importedProfile(chosen))&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null,"Disabling did not restore previous packs");
            try(var zip=new ZipOutputStream(Files.newOutputStream(source))){zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{}}".getBytes());zip.closeEntry();zip.putNextEntry(new ZipEntry("../outside"));zip.write(1);zip.closeEntry();}
            boolean rejected=false;try{PackArchive.validate(source);}catch(java.io.IOException expected){rejected=true;}require(rejected,"Path traversal ZIP accepted");
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new GameplayTexturesScreen(null,module));});
            context.waitTicks(10);context.takeScreenshot("maro-gameplay-textures-ready");
            BundledPackChecks.run(context);
            System.out.println("[packsmc-proof] PASS: exactly 50 live cards/previews, creator credits/canonical pages, search/favorites, native rendering at 3 scales, ZIP import/application/restoration and malformed ZIP rejection");
        }catch(Exception e){throw new AssertionError(e);}finally{
            context.runOnClient(c->{c.setScreen(null);module.setEnabled(false);module.loadExtra(prior);module.setEnabled(enabled);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();ConfigManager.saveAll();});context.waitTicks(10);
            try{if(source!=null)Files.deleteIfExists(source);if(installed!=null)Files.deleteIfExists(installed);}catch(Exception ignored){}
        }
    }
}
