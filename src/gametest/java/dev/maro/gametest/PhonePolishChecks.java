package dev.maro.gametest;

import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.runtime.renderer.NativeImageUpload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;

/** Isolated playback, real texture pixels and vanilla world interaction through the open phone. */
final class PhonePolishChecks {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Object field(Object object, String name) {
        try { var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static SpotifyPhoneScreen screen(net.minecraft.client.MinecraftClient c) { return (SpotifyPhoneScreen)c.currentScreen; }
    private static SpotifyMedia.State track(String title) {
        return new SpotifyMedia.State(true,title,"Native test","Album","Spotify",true,true,2000,180000,System.currentTimeMillis(),"");
    }
    private static void click(ClientGameTestContext context, int button, boolean down) {
        context.runOnClient(c -> { var event=new Click(3,3,new MouseInput(button,0)); if(down)screen(c).mouseClicked(event,false);else screen(c).mouseReleased(event); });
    }
    private static void artworkSource() {
        try {
            var source=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g=source.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,256,256);g.dispose();
            var decode=SpotifyMedia.class.getDeclaredMethod("decodeArtwork",String.class,java.awt.image.BufferedImage.class);decode.setAccessible(true);
            var art=(SpotifyMedia.Artwork)decode.invoke(null,"mask-regression",source);
            int i=(20*256+1)*4+3;require((art.rgba()[i]&255)==0&&(art.rawRgba()[i]&255)==255,"Shared media did not preserve original corner pixels");
            var phone=Class.forName("dev.maro.gui.spotify.PhoneAlbumArt").getDeclaredMethod("prepare",SpotifyMedia.Artwork.class);phone.setAccessible(true);
            byte[] pixels=(byte[])phone.invoke(null,art);
            require((pixels[(40*512+2)*4+3]&255)==255,"Phone reused the HUD mask and retained the large missing corner");
            require((pixels[3]&255)==0,"Phone artwork has square outer corners");
        } catch(Exception e){throw new AssertionError(e);}
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var owner=ModuleManager.get(SpotifyPhone.class);
        var playback=new SpotifyPhoneChecks.IsolatedPlayback();
        try {
            context.runOnClient(c -> {
                require(ModuleManager.getByName("Hand Shader")==null,"Removed Hand Shader is still registered");
                pixels(); artworkSource();
                owner.getSettings().forEach(s->s.reset());owner.mouseLook.set(false);owner.gameInteraction.set(false);
                c.setScreen(new SpotifyPhoneScreen(owner,playback));
            });
            context.waitTicks(10);
            context.runOnClient(c -> {
                var cover=field(screen(c),"cover");require(cover!=null,"Prepared artwork never uploaded");
                var texture=(net.minecraft.client.texture.NativeImageBackedTexture)field(cover,"texture");
                var image=texture.getImage();
                require(image.getColorArgb(0,0)>>>24==255 && image.getColorArgb(256,256)>>>24==255,"Raw album lost corner pixels before fixed-window clipping");
                owner.nextBind.set(GLFW.GLFW_KEY_K);
            });
            int count=playback.controls.size();
            context.runOnClient(c -> {
                var key=new KeyInput(GLFW.GLFW_KEY_K,0,0);screen(c).keyPressed(key);screen(c).keyPressed(key);
                require(playback.controls.size()==count+1 && playback.controls.getLast().equals("next"),"Rebound skip repeated while held");
                screen(c).keyReleased(key);
                require((long)field(screen(c),"swipeAt")>0 && (int)field(screen(c),"swipeDirection")==1,"Skip waited for metadata or artwork before reacting");
            });
            context.waitTicks(7);
            context.getInput().pressKey(GLFW.GLFW_KEY_P);context.waitTicks(7);
            require(playback.controls.getLast().equals("toggle"),"Native play/pause hotkey failed");
            context.getInput().pressKey(GLFW.GLFW_KEY_PAGE_DOWN);context.waitTicks(2);
            require(Math.abs(playback.volume.level()-.60)<.001,"Volume hotkey failed");
            context.getInput().pressKey(GLFW.GLFW_KEY_M);context.waitTicks(2);
            require(playback.volume.muted(),"Mute hotkey failed");
            context.getInput().pressKey(GLFW.GLFW_KEY_M);context.waitTicks(2);
            require(!playback.volume.muted(),"Mute hotkey did not restore volume");
            Object previous=context.computeOnClient(c->field(screen(c),"cover"));
            context.runOnClient(c -> {playback.artworkPaused=true;playback.state=track("Clean artwork and swipe");});context.waitTicks(5);
            require(context.computeOnClient(c->field(screen(c),"cover")==previous),"Delayed artwork discarded the outgoing album");
            require(context.computeOnClient(c->(long)field(screen(c),"swipeAt")>0),"Metadata change waited for artwork to move the hand");
            context.takeScreenshot("maro-phone-immediate-swipe-delayed-artwork");
            long gesture=context.computeOnClient(c->(long)field(screen(c),"swipeAt"));
            context.runOnClient(c->{tint(playback,25,150,235);playback.artworkPaused=false;});context.waitTicks(6);
            require(context.computeOnClient(c -> (long)field(screen(c),"swipeAt")>0),"A confirmed song change did not animate the opposite hand");
            require(context.computeOnClient(c -> (long)field(screen(c),"swipeAt")==gesture),"Late artwork replayed the hand gesture");
            context.takeScreenshot("maro-phone-polish-swipe-right");
            context.runOnClient(c -> { owner.hand.set("Left");tint(playback,245,155,35);playback.state=track("Opposite hand, left phone"); });context.waitTicks(6);
            context.takeScreenshot("maro-phone-polish-swipe-left");context.waitTicks(15);
            require(context.computeOnClient(c->field(screen(c),"previousCover")==null),"Completed transition retained the old GPU texture");
            context.takeScreenshot("maro-phone-polish-artwork");
            context.runOnClient(c->{var key=new KeyInput(owner.previousBind.get(),0,0);screen(c).keyPressed(key);screen(c).keyReleased(key);tint(playback,210,65,150);playback.state=track("Previous track swipe");});
            context.waitTicks(6);context.takeScreenshot("maro-phone-polish-swipe-previous");
            context.runOnClient(c->{owner.device.set("iPad");tint(playback,25,160,120);playback.state=track("Tablet album swipe");});
            context.waitTicks(6);context.takeScreenshot("maro-phone-polish-swipe-ipad");
            context.runOnClient(c->owner.device.set("Phone"));
            context.runOnClient(c->{
                try {
                    var source=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g=source.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect(0,0,256,256);g.setColor(java.awt.Color.WHITE);
                    g.setFont(new java.awt.Font("Arial",java.awt.Font.BOLD,54));g.drawString("MARO",38,140);g.dispose();
                    var decode=SpotifyMedia.class.getDeclaredMethod("decodeArtwork",String.class,java.awt.image.BufferedImage.class);decode.setAccessible(true);
                    var art=(SpotifyMedia.Artwork)decode.invoke(null,SpotifyMedia.artworkKey(playback.state),source);
                    var f=playback.getClass().getDeclaredField("art");f.setAccessible(true);f.set(playback,art);
                } catch(Exception e){throw new AssertionError(e);}
            });context.waitTicks(12);context.takeScreenshot("maro-phone-artwork-real-decoder");

            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -3 139 -3 3 139 4 minecraft:stone");
            world.getServer().runCommand("setblock 0 140 2 minecraft:stone");
            world.getServer().runCommand("tp @a 0.5 141 -0.5 0 25");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:cobblestone 64");
            context.waitTicks(6);
            BlockPos support=new BlockPos(0,140,2), placed=support.up();
            context.runOnClient(c -> {
                owner.mouseLook.set(true);screen(c).tick();
                require(!screen(c).allowsGameInteraction(),"World interaction should be opt-in");
                c.crosshairTarget=new BlockHitResult(new Vec3d(.5,141,2.5),Direction.UP,support,false);
            });
            context.runOnClient(c->{c.crosshairTarget=new BlockHitResult(new Vec3d(.5,141,2.5),Direction.UP,support,false);var e=new Click(3,3,new MouseInput(1,0));screen(c).mouseClicked(e,false);screen(c).mouseReleased(e);});context.waitTicks(3);
            require(context.computeOnClient(c -> c.world.getBlockState(placed).isAir()),"Phone click used an item while option was off");
            context.runOnClient(c -> {
                owner.gameInteraction.set(true);screen(c).tick();
                c.crosshairTarget=new BlockHitResult(new Vec3d(.5,141,2.5),Direction.UP,support,false);
            });
            context.runOnClient(c->{c.crosshairTarget=new BlockHitResult(new Vec3d(.5,141,2.5),Direction.UP,support,false);var e=new Click(3,3,new MouseInput(1,0));screen(c).mouseClicked(e,false);screen(c).mouseReleased(e);});context.waitTicks(5);
            require(context.computeOnClient(c -> c.world.getBlockState(placed).isOf(Blocks.COBBLESTONE)),"Native item use did not place a block with the phone open");
            context.runOnClient(c -> {
                c.crosshairTarget=new BlockHitResult(new Vec3d(.5,141.5,2),Direction.NORTH,placed,false);
                var event=new Click(3,3,new MouseInput(0,0));screen(c).mouseClicked(event,false);screen(c).mouseReleased(event);
            });context.waitTicks(5);
            require(context.computeOnClient(c -> c.world.getBlockState(placed).isAir()),"Native attack did not break a block with the phone open");
            context.getInput().pressKey(GLFW.GLFW_KEY_2);context.waitTicks(2);
            require(context.computeOnClient(c -> c.player.getInventory().getSelectedSlot()==1),"Phone game mode ignored hotbar keys");
            context.runOnClient(c -> {
                screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_LEFT_ALT,0,0));
                require(!screen(c).allowsGameInteraction() && !c.options.attackKey.isPressed()&&!c.options.useKey.isPressed(),"Alt did not release world actions");
                screen(c).keyReleased(new KeyInput(GLFW.GLFW_KEY_LEFT_ALT,0,0));
                c.onWindowFocusChanged(false);screen(c).tick();
                require(!screen(c).allowsGameInteraction(),"Focus loss left world actions active");
                c.onWindowFocusChanged(true);
            });
            System.out.println("[phone-polish] PASS: masked artwork, opposite-hand changes, rebound/repeat-safe playback and volume hotkeys, opt-in vanilla place/break/hotbar, Alt/focus cleanup; Hand Shader absent");
        } finally {
            context.runOnClient(c -> {c.setScreen(null);owner.getSettings().forEach(s->s.reset());});
        }
    }
    private static void reference(NativeImage image, byte[] rgba) {
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int p=(y*image.getWidth()+x)*4;
            image.setColorArgb(x,y,(rgba[p+3]&255)<<24|(rgba[p]&255)<<16|(rgba[p+1]&255)<<8|(rgba[p+2]&255));
        }
    }
    private static void tint(SpotifyPhoneChecks.IsolatedPlayback playback,int red,int green,int blue){
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){
            int i=(y*128+x)*4;float shade=.4f+.6f*(1-y/128f);
            playback.pixels[i]=(byte)(red*shade);playback.pixels[i+1]=(byte)(green*shade);playback.pixels[i+2]=(byte)(blue*shade);
        }
    }
    private static void pixels() {
        byte[] rgba=new byte[512*512*4];new java.util.Random(31).nextBytes(rgba);
        try(var image=new NativeImage(512,512,false);var before=new NativeImage(512,512,false)) {
            reference(before,rgba);NativeImageUpload.rgba(image,rgba);
            for(int y=0;y<512;y+=17)for(int x=0;x<512;x+=13)require(image.getColorArgb(x,y)==before.getColorArgb(x,y),"Bulk upload changed RGBA channels");
            var offset=ByteBuffer.allocate(rgba.length+7);offset.position(7);offset.put(rgba);offset.flip();offset.position(7);
            NativeImageUpload.rgba(image,offset);require(offset.position()==7,"Bulk upload consumed the caller's buffer");
            boolean rejected=false;try{NativeImageUpload.rgba(image,new byte[8]);}catch(IllegalArgumentException e){rejected=true;}require(rejected,"Truncated image was accepted");
            for(int i=0;i<8;i++){reference(before,rgba);NativeImageUpload.rgba(image,rgba);}
            long slow=0,fast=0;
            for(int pass=0;pass<4;pass++) {
                long start=System.nanoTime();for(int i=0;i<20;i++)reference(before,rgba);slow+=System.nanoTime()-start;
                start=System.nanoTime();for(int i=0;i<20;i++)NativeImageUpload.rgba(image,rgba);fast+=System.nanoTime()-start;
            }
            System.out.println("[pixel-upload] 80 x 512px CPU copies: per-pixel-ns="+slow+" bulk-ns="+fast+" reduction="+Math.round((1-(double)fast/slow)*100)+"% (copy workload, not FPS)");
        }
    }
}
