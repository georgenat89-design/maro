package dev.maro.gametest;

import dev.maro.gui.hud.PacksMcScreen;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.textures.BundledPacks;
import dev.maro.textures.PackIcons;
import dev.maro.textures.PacksMcCatalog;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.texture.NativeImage;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

final class BundledPackChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void await(ClientGameTestContext c,BooleanSupplier test,String message){for(int i=0;i<1000&&!c.computeOnClient(mc->test.getAsBoolean());i++)c.waitTicks(1);require(c.computeOnClient(mc->test.getAsBoolean()),message);}
    private static PacksMcCatalog.Pack fixture(int n){String id="11111111-2222-4333-8444-"+String.format(Locale.ROOT,"%012d",n);return new PacksMcCatalog.Pack(id,"Included fixture "+n,"Maro","Original test fixture","16x",List.of("1.21.11"),"","https://packsmc.com/pack/library-fixture-"+n,0,0,"Original test asset","",false);}
    private static void corners() throws Exception{
        var source=new java.awt.image.BufferedImage(512,288,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=source.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,512,288);g.dispose();var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"png",bytes);
        var decode=PackIcons.class.getDeclaredMethod("decode",byte[].class,int.class,int.class);decode.setAccessible(true);
        try(var image=(NativeImage)decode.invoke(null,bytes.toByteArray(),512,288)){
            require(image.getColorArgb(0,0)>>>24==0&&image.getColorArgb(511,287)>>>24==0,"Preview has square corner pixels");
            require(image.getColorArgb(256,0)>>>24==255&&image.getColorArgb(256,144)>>>24==255,"Mask removed interior preview pixels");
            boolean smooth=false;for(int y=0;y<16;y++)for(int x=0;x<16;x++){int alpha=image.getColorArgb(x,y)>>>24;if(alpha>0&&alpha<255)smooth=true;}
            require(smooth,"Preview corner edge is not antialiased");
        }
    }
    static void run(ClientGameTestContext context){
        var module=TexturePacks.get();var prior=module.saveExtra();boolean enabled=module.isEnabled();var catalog=module.catalog().packs();var installed=new ArrayList<Path>();
        try{
            corners();var one=fixture(1);var two=fixture(2);var bad=fixture(3);
            require(BundledPacks.contains(one.id())&&BundledPacks.contains(two.id()),"Included archive fixtures missing");
            var field=PacksMcCatalog.class.getDeclaredField("packs");field.setAccessible(true);
            context.runOnClient(c->{try{field.set(module.catalog(),List.of(one,two));}catch(Exception e){throw new AssertionError(e);}});
            var screen=context.computeOnClient(c->{var s=new PacksMcScreen(null,module);s.selectPack(one);c.setScreen(s);return s;});context.waitTicks(4);
            var opened=new java.util.concurrent.atomic.AtomicReference<java.net.URI>();var browser=PacksMcScreen.class.getDeclaredField("openInBrowser");browser.setAccessible(true);
            browser.set(screen,(java.util.function.Consumer<java.net.URI>)opened::set);
            context.runOnClient(c->{var p=screen.point(700,319);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);require(c.currentScreen==screen,"Download replaced the game menu with an embedded browser");
                require(PacksMcScreen.downloadPage(one).equals(opened.get()),"Download did not open the selected pack in the external browser");require(!module.importing()&&!module.cached(one),"Download silently installed the included archive");
                module.installIncluded(one,true);
            });
            await(context,()->module.cached(one)&&module.gameHasOn(module.importedProfile(one))&&net.minecraft.client.MinecraftClient.getInstance().getOverlay()==null,"Included pack did not install/apply");
            installed.add(module.packDir().resolve(module.importedProfile(one).substring(5)));
            try(var in=BundledPacks.class.getResourceAsStream("/maro-pack-library/packs/"+one.id()+".zip")){require(Arrays.equals(in.readAllBytes(),Files.readAllBytes(installed.getFirst())),"Included install changed ZIP bytes");}
            var picks=module.picked();boolean before=module.isEnabled();
            context.runOnClient(c->{var p=screen.point(505,29);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);require(c.currentScreen==screen,"Browse website replaced the game menu");
                require(java.net.URI.create(PacksMcCatalog.WEBSITE).equals(opened.get()),"Browse website opened the wrong external page");module.installIncludedCollection();
            });
            await(context,()->module.cached(two)&&!module.importing(),"Install all did not save the remaining included pack");
            installed.add(module.packDir().resolve(module.importedProfile(two).substring(5)));
            require(context.computeOnClient(c->module.picked().equals(picks)&&module.isEnabled()==before),"Install all changed the active loadout");
            var invalid=context.computeOnClient(c->module.installIncluded(bad,false));await(context,invalid::isDone,"Hash rejection never completed");require(invalid.isCompletedExceptionally(),"Included hash mismatch was accepted");
            try(var files=Files.list(module.packDir())){require(files.noneMatch(p->p.getFileName().toString().contains(bad.id())),"Invalid included pack left a final archive");}
            var late=fixture(4);var closing=context.computeOnClient(c->{var future=module.installIncluded(late,true);c.setScreen(null);return future;});await(context,closing::isDone,"Closed-screen install did not finish saving");installed.add(closing.join());
            require(context.computeOnClient(c->module.picked().equals(picks)&&module.isEnabled()==before),"Closing the screen allowed a late pack switch");
            context.runOnClient(c->{c.setScreen(screen);screen.selectPack(new PacksMcCatalog.Pack("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee","Restricted fixture","Maro","","16x",List.of(),"","https://packsmc.com/pack/restricted-fixture",0,0,"","",true));});context.waitTicks(2);
            context.runOnClient(c->{var p=screen.point(700,319);screen.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);require(c.currentScreen==screen,"Restricted download replaced the game menu");require(PacksMcScreen.downloadPage(screen.selected()).equals(opened.get()),"Restricted download did not use the external creator page");});
            System.out.println("[included-packs] PASS: external Download/Browse website routing without an embedded browser, legacy library import and resume, original bytes, unchanged loadout, hash rejection, no late activation after closing and antialiased preview corners");
        }catch(Exception e){throw new AssertionError(e);}finally{
            context.runOnClient(c->{c.setScreen(null);module.setEnabled(false);module.loadExtra(prior);module.setEnabled(enabled);try{var f=PacksMcCatalog.class.getDeclaredField("packs");f.setAccessible(true);f.set(module.catalog(),catalog);}catch(Exception e){throw new AssertionError(e);}});context.waitTicks(10);
            for(var file:installed)try{Files.deleteIfExists(file);}catch(Exception ignored){}
        }
    }
}
