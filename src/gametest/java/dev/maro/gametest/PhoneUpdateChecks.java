package dev.maro.gametest;

import com.sun.net.httpserver.HttpServer;
import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.phone.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real relay clients, a local OAuth/API fixture, and actual MP4 decoding in both device layouts. */
final class PhoneUpdateChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void until(ClientGameTestContext c,BooleanSupplier condition,String message){for(int i=0;i<900&&!condition.getAsBoolean();i++)c.waitTicks(1);require(condition.getAsBoolean(),message);}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static SpotifyPhoneScreen screen(net.minecraft.client.MinecraftClient c){return (SpotifyPhoneScreen)c.currentScreen;}
    private static void click(ClientGameTestContext context,float x,float y){context.runOnClient(c->{var s=screen(c);var l=s.layout();s.mouseClicked(new Click(l.screenX(x,y),l.screenY(x,y),new MouseInput(0,0)),false);});context.waitTicks(3);}
    private static void home(ClientGameTestContext context){float x=context.computeOnClient(c->screen(c).layout().width()/2);click(context,x,37);}
    private static void homeChecks(ClientGameTestContext context,String device,SpotifyPhone owner){
        int before=context.computeOnClient(c->c.options.getGuiScale().getValue());
        for(int scale:new int[]{2,4})for(double size:new double[]{.55,1,1.45}){
            context.runOnClient(c->{owner.size.set(size);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});context.waitTicks(3);
            context.runOnClient(c->{var s=screen(c);var app=field(s,"apps");require(field(app,"app").toString().equals("HOME"),"Home fixture is on wrong app");var hits=(List<?>)field(app,"hits");require(hits.size()==6,"Home has missing app/footer controls");
                for(int i=0;i<hits.size();i++){var a=hits.get(i);float ax=(float)field(a,"x"),ay=(float)field(a,"y"),aw=(float)field(a,"w"),ah=(float)field(a,"h");require(ax>=6&&ay>=100&&ax+aw<=s.layout().width()-6&&ay+ah<=s.layout().height()-20,"Home control left its device");for(int j=i+1;j<hits.size();j++){var b=hits.get(j);float bx=(float)field(b,"x"),by=(float)field(b,"y"),bw=(float)field(b,"w"),bh=(float)field(b,"h");require(ax+aw<=bx||bx+bw<=ax||ay+ah<=by||by+bh<=ay,"Home app cards overlap: "+device+" "+i+"/"+j);}}
                var top=hits.get(0);var bottom=hits.get(2);require((float)field(bottom,"y")-((float)field(top,"y")+(float)field(top,"h"))>=11,"Home row gap collapsed");
            });
        }
        context.runOnClient(c->{owner.size.set(1d);c.options.getGuiScale().setValue(before);c.onResolutionChanged();});context.waitTicks(3);
        // Click actual transformed controls, returning via the device's real Interact button.
        for(int i:new int[]{0,1,2,4}){
            final int index=i;
            context.runOnClient(c->{var s=screen(c);var h=((List<?>)field(field(s,"apps"),"hits")).get(index);float x=(float)field(h,"x")+(float)field(h,"w")/2,y=(float)field(h,"y")+(float)field(h,"h")/2;var l=s.layout();s.mouseClicked(new Click(l.screenX(x,y),l.screenY(x,y),new MouseInput(0,0)),false);});context.waitTicks(3);
            String expected=switch(i){case 0->"MUSIC";case 1->"MESSAGES";case 2->"PLAYLISTS";default->"SETTINGS";};
            require(context.computeOnClient(c->field(field(screen(c),"apps"),"app").toString()).equals(expected),"Home tile opened wrong app: "+expected);home(context);
        }
        System.out.println("[phone-home] PASS: "+device+" six distinct controls, row spacing and screen bounds at GUI scales 2/4 and sizes .55/1/1.45; transformed Spotify/Messages/Playlists/Settings clicks");
    }
    private static Map<String,String> query(String raw){var out=new HashMap<String,String>();for(String pair:raw.split("&")){var bits=pair.split("=",2);out.put(URLDecoder.decode(bits[0],StandardCharsets.UTF_8),bits.length<2?"":URLDecoder.decode(bits[1],StandardCharsets.UTF_8));}return out;}
    static void run(ClientGameTestContext context){
        var owner=ModuleManager.get(SpotifyPhone.class);HttpServer fixture=null;
        PhoneNetwork sender=null,receiver=null,reopened=null;SpotifyLibrary library=null,restored=null;
        try{
            Path dir=Files.createTempDirectory("maro-phone-update-");
            AtomicReference<URI> authorization=new AtomicReference<>();AtomicInteger tokenCalls=new AtomicInteger(),clipDownloads=new AtomicInteger();AtomicBoolean limited=new AtomicBoolean();
            byte[] clip;try(var in=PhoneUpdateChecks.class.getResourceAsStream("/assets/maro/phone/demo-neon.mp4")){require(in!=null,"Missing bundled MP4 demo");clip=in.readAllBytes();}
            fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            fixture.createContext("/token",exchange->{
                var body=query(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                boolean refresh=body.getOrDefault("grant_type","").equals("refresh_token");
                if(!refresh){
                    var auth=query(authorization.get().getRawQuery());
                    try{String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(body.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));require(challenge.equals(auth.get("code_challenge")),"PKCE verifier did not match challenge");}catch(Exception e){throw new AssertionError(e);}
                    require(body.get("redirect_uri").equals(auth.get("redirect_uri")),"OAuth redirect URI changed");
                }
                tokenCalls.incrementAndGet();byte[] bytes="{\"access_token\":\"isolated-fixture-access\",\"refresh_token\":\"isolated-fixture-refresh\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
            });
            fixture.createContext("/v1/me/playlists",exchange->{
                require(exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer isolated-fixture-access"),"Playlist API missing bearer token");
                boolean page=query(exchange.getRequestURI().getRawQuery()).get("offset").equals("50");
                String json=page?"{\"items\":[{\"id\":\"37i9dQZF1DWZeKCadgRdKQ\",\"name\":\"Night Drive\"}],\"next\":null}":"{\"items\":[null,{\"id\":\"37i9dQZF1DXcBWIGoYBM5M\",\"name\":\"Saved Favorites\"}],\"next\":\"next-page\"}";
                byte[] bytes=(limited.get()?"{}":json).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(limited.get()?429:200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
            });
            fixture.createContext("/clip.mp4",exchange->{clipDownloads.incrementAndGet();exchange.getResponseHeaders().set("Content-Type","video/mp4");exchange.sendResponseHeaders(200,clip.length);try(var out=exchange.getResponseBody()){out.write(clip);}});
            fixture.start();String base="http://127.0.0.1:"+fixture.getAddress().getPort();
            library=new SpotifyLibrary(dir.resolve("spotify.json"),URI.create(base+"/token"),URI.create(base+"/v1"),URI.create(base+"/authorize"),authorization::set);var spotify=library;
            library.connect("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            until(context,()->authorization.get()!=null,"OAuth browser URL never opened");
            var auth=query(authorization.get().getRawQuery());require(auth.get("scope").equals("playlist-read-private playlist-read-collaborative"),"Library requested extra permissions");
            try(var http=HttpClient.newHttpClient()){
                URI wrong=URI.create(auth.get("redirect_uri")+"?code=fixture-code&state=wrong");
                require(http.send(HttpRequest.newBuilder(wrong).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==400,"OAuth accepted a wrong state");
                require(tokenCalls.get()==0,"Wrong-state callback exchanged a token");
                URI callback=URI.create(auth.get("redirect_uri")+"?code=fixture-code&state="+auth.get("state"));
                require(http.send(HttpRequest.newBuilder(callback).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200,"Valid OAuth callback failed");
            }
            until(context,()->spotify.snapshot().connected()&&!spotify.snapshot().busy()&&spotify.snapshot().playlists().size()==2,"Spotify library pagination failed: "+spotify.snapshot().status());
            limited.set(true);library.refresh();until(context,()->!spotify.snapshot().busy(),"Rate-limited request stayed busy");require(library.snapshot().status().contains("rate limit"),"Spotify rate limit was hidden");
            limited.set(false);library.refresh();until(context,()->!spotify.snapshot().busy()&&spotify.snapshot().playlists().size()==2,"Spotify refresh did not recover");

            sender=new PhoneNetwork(dir.resolve("alice.json"));receiver=new PhoneNetwork(dir.resolve("bob.json"));var a=sender;var b=receiver;
            String relay=System.getProperty("maro.gametest.phoneRelay","");
            if(!relay.isEmpty()){
                a.connect(relay);b.connect(relay);until(context,()->a.snapshot().online()&&b.snapshot().online(),"Phone update clients could not connect");
                a.send(b.snapshot().number(),"Hello! Can I share a playlist?");until(context,()->!b.snapshot().messages().isEmpty(),"Message request never arrived");
                String peer=a.snapshot().number();var message=b.snapshot().messages().getFirst();require(b.snapshot().request(peer),"Unknown sender bypassed Requests");
                require(PhoneNetwork.shouldNotify(b.snapshot(),message),"Default request alert was missing");
                b.preferences(new PhoneNetwork.Preferences(false,true,false,false));until(context,()->!b.snapshot().preferences().notifications(),"Notification preference did not update");require(!PhoneNetwork.shouldNotify(b.snapshot(),message),"Muted phone still notified");
                b.preferences(new PhoneNetwork.Preferences(true,false,false,false));until(context,()->b.snapshot().preferences().notifications(),"Notification preference failed");require(!PhoneNetwork.shouldNotify(b.snapshot(),message),"Muted requests still notified");
                context.runOnClient(c->{owner.device.set("Phone");owner.mouseLook.set(true);owner.interactBind.set(GLFW.GLFW_KEY_I);c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),b,spotify,dir.resolve("request-clips")));});context.waitTicks(10);context.getInput().pressKey(GLFW.GLFW_KEY_I);context.waitTicks(3);
                click(context,150,145);click(context,145,119);context.takeScreenshot("maro-phone-requests");click(context,80,197);context.takeScreenshot("maro-phone-request-preview");click(context,40,118);
                until(context,()->!b.snapshot().request(peer),"Accept did not move request to inbox");require(PhoneNetwork.shouldNotify(b.snapshot(),message),"Accepted sender still treated as request for notifications");
                click(context,40,90);click(context,45,119);context.takeScreenshot("maro-phone-inbox");home(context);click(context,50,410);context.takeScreenshot("maro-phone-notification-settings");
                b.ignore(peer);until(context,()->b.snapshot().visibleMessages().isEmpty(),"Deleted request/conversation remained visible");require(!PhoneNetwork.shouldNotify(b.snapshot(),message),"Ignored number notified");b.accept(peer);until(context,()->!b.snapshot().visibleMessages().isEmpty(),"Restoring an ignored contact failed");
                videoMessages(context,owner,a,b,spotify,dir,clip);
            }
            for(String device:new String[]{"Phone","iPad"}){
                Path clipsFolder=dir.resolve("clips-"+device);Files.createDirectories(clipsFolder);
                Files.write(clipsFolder.resolve("sample-1.mp4"),clip);Files.write(clipsFolder.resolve("sample-2.mp4"),clip);
                context.runOnClient(c->{owner.device.set(device);owner.mouseLook.set(true);c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),b,spotify,dir.resolve("clips-"+device)));});context.waitTicks(10);context.getInput().pressKey(GLFW.GLFW_KEY_I);context.waitTicks(3);
                homeChecks(context,device,owner);
                context.takeScreenshot("maro-phone-home-updated-"+device.toLowerCase());
                click(context,50,225);click(context,120,90);context.takeScreenshot("maro-phone-spotify-library-"+device.toLowerCase());
                if(!relay.isEmpty()){
                    click(context,120,200);click(context,65,177);context.getInput().typeChars(a.snapshot().number());click(context,90,213);
                    until(context,()->a.snapshot().messages().stream().anyMatch(m->m.kind().equals("playlist")&&m.text().equals("Saved Favorites")),"Spotify library playlist did not share through relay");
                }
                home(context);float clipTile=context.computeOnClient(c->screen(c).layout().width()/2+30);click(context,clipTile,225);context.waitTicks(3);
                float viewWidth=context.computeOnClient(c->screen(c).layout().width());click(context,viewWidth-27,88);click(context,80,193);context.waitTicks(10);
                PhoneVideoPlayer video=context.computeOnClient(c->(PhoneVideoPlayer)field(field(field(screen(c),"apps"),"clips"),"player"));
                until(context,()->video.frame()!=null,"Imported MP4 did not decode: "+video.status());
                context.takeScreenshot("maro-local-video-entry-"+device.toLowerCase());
                require(!video.paused(),"Imported video entered paused; app="+context.computeOnClient(c->field(field(screen(c),"apps"),"app")));
                long first=video.frame().serial();for(int i=0;i<180&&video.frame()!=null&&video.frame().serial()<=first+2&&video.status().isEmpty();i++)context.waitTicks(1);
                require(video.frame()!=null&&video.frame().serial()>first+2,"Clip stopped: paused="+video.paused()+" status="+video.status()+" first="+first+" serial="+(video.frame()==null?"null":video.frame().serial()));context.takeScreenshot("maro-phone-clips-"+device.toLowerCase());
                context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_SPACE,0,0)));context.waitTicks(5);long paused=video.frame().serial();context.waitTicks(8);require(video.frame().serial()==paused,"Paused clip advanced");
                context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_SPACE,0,0)));until(context,()->video.frame().serial()>paused,"Clip did not resume");
                context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_DOWN,0,0)));context.waitTicks(8);until(context,()->video.frame()!=null,"Next clip did not decode");context.takeScreenshot("maro-phone-clips-next-"+device.toLowerCase());
                int downloads=clipDownloads.get();click(context,100,92);click(context,55,181);context.getInput().typeChars(base+"/clip.mp4");click(context,50,214);context.waitTicks(5);click(context,100,92);
                context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_DOWN,0,0)));context.waitTicks(3);
                until(context,()->clipDownloads.get()>downloads&&video.frame()!=null,"Saved video link did not download and play: "+video.status());context.takeScreenshot("maro-phone-clips-link-"+device.toLowerCase());
                context.runOnClient(c->c.setScreen(null));require(video.frame()==null,"Closing phone left clip frame/decoder active");
            }
            restored=new SpotifyLibrary(dir.resolve("spotify.json"),URI.create(base+"/token"),URI.create(base+"/v1"),URI.create(base+"/authorize"),u->{throw new AssertionError("Refresh reopened browser");});var savedSpotify=restored;
            until(context,()->savedSpotify.snapshot().connected(),"Spotify sign-in did not persist");restored.refresh();until(context,()->savedSpotify.snapshot().playlists().size()==2&&!savedSpotify.snapshot().busy(),"Saved Spotify sign-in failed to refresh");
            restored.disconnect();until(context,()->!savedSpotify.snapshot().connected(),"Disconnect kept Spotify credentials active");require(!Files.readString(dir.resolve("spotify.json")).contains("isolated-fixture-refresh"),"Disconnect retained refresh token on disk");
            if(!relay.isEmpty()){
                reopened=new PhoneNetwork(dir.resolve("bob.json"));var savedPhone=reopened;
                until(context,()->savedPhone.snapshot().online(),"Phone preferences failed to reopen");require(!reopened.snapshot().request(a.snapshot().number()),"Accepted sender was lost on reopen");require(!reopened.snapshot().preferences().requests()&&!reopened.snapshot().preferences().previews(),"Notification preferences were lost");
            }
            System.out.println("[phone-update] Requests/accept/delete, notification preferences, PKCE/CSRF, paginated Spotify sharing, persistence, MP4 playback/pause/next/link and Phone+iPad passed");
        }catch(Exception e){throw new AssertionError(e);}
        finally{
            context.runOnClient(c->{c.setScreen(null);owner.getSettings().forEach(s->s.reset());});
            if(sender!=null)sender.close();if(receiver!=null)receiver.close();if(reopened!=null)reopened.close();
            if(library!=null)library.close();if(restored!=null)restored.close();if(fixture!=null)fixture.stop(0);
        }
    }

    private static void videoMessages(ClientGameTestContext context,SpotifyPhone owner,PhoneNetwork sender,PhoneNetwork receiver,SpotifyLibrary spotify,Path dir,byte[] clip)throws Exception{
        Path file=dir.resolve("Contact video.mp4");Files.write(file,clip);PhoneNetwork.validateVideo(file);
        Path invalid=dir.resolve("invalid.mp4");Files.writeString(invalid,"This is not a video");
        try{PhoneNetwork.validateVideo(invalid);throw new AssertionError("Non-video attachment accepted");}catch(java.io.IOException expected){}
        context.runOnClient(c->{owner.device.set("Phone");owner.mouseLook.set(true);c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),sender,spotify,dir.resolve("video-clips")));});context.waitTicks(8);context.getInput().pressKey(GLFW.GLFW_KEY_I);context.waitTicks(3);
        float phoneWidth=context.computeOnClient(c->screen(c).layout().width());
        click(context,150,145);click(context,70,151);context.getInput().typeChars(receiver.snapshot().number());click(context,phoneWidth-40,151);
        context.runOnClient(c->{var app=field(screen(c),"apps");require(field(app,"peer").equals(receiver.snapshot().number()),"Native New contact did not open recipient");try{var f=app.getClass().getDeclaredField("videoDraft");f.setAccessible(true);f.set(app,file);}catch(Exception e){throw new AssertionError(e);}});context.waitTicks(3);
        context.takeScreenshot("maro-contact-video-draft");float height=context.computeOnClient(c->screen(c).layout().height());click(context,phoneWidth-40,height-65);
        until(context,()->receiver.snapshot().messages().stream().anyMatch(m->m.kind().equals("video")),"Native Send did not deliver the video");
        var message=receiver.snapshot().messages().stream().filter(m->m.kind().equals("video")).findFirst().orElseThrow();
        Path downloaded=receiver.video(message).get(10,java.util.concurrent.TimeUnit.SECONDS);require(Arrays.equals(Files.readAllBytes(downloaded),clip),"Authenticated video download changed bytes");
        for(String device:new String[]{"Phone","iPad"}){
            context.runOnClient(c->{owner.device.set(device);owner.mouseLook.set(true);c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),receiver,spotify,dir.resolve("received-video-clips")));});context.waitTicks(8);context.getInput().pressKey(GLFW.GLFW_KEY_I);context.waitTicks(3);
            float width=context.computeOnClient(c->screen(c).layout().width());click(context,width*.75f,145);click(context,70,151);context.getInput().typeChars(sender.snapshot().number());click(context,width-40,151);
            context.runOnClient(c->{
                var s=screen(c);var app=field(s,"apps");boolean clicked=false;
                for(var hit:(List<?>)field(app,"hits")){
                    Object action=field(hit,"action");boolean owns=false;
                    for(var capture:action.getClass().getDeclaredFields())try{capture.setAccessible(true);Object value=capture.get(action);if(value instanceof PhoneNetwork.Message m&&m.id()==message.id())owns=true;}catch(Exception e){throw new AssertionError(e);}
                    if(!owns)continue;
                    float x=(float)field(hit,"x")+(float)field(hit,"w")/2,y=(float)field(hit,"y")+(float)field(hit,"h")/2;var layout=s.layout();s.mouseClicked(new Click(layout.screenX(x,y),layout.screenY(x,y),new MouseInput(0,0)),false);clicked=true;break;
                }
                require(clicked,"Received video has no Play control");
            });
            until(context,()->context.computeOnClient(c->((PhoneVideoPlayer)field(field(field(screen(c),"apps"),"messageVideo"),"player")).frame()!=null),"Received video did not decode inside "+device);
            context.waitTicks(3);context.takeScreenshot("maro-contact-video-playing-"+device.toLowerCase());
            click(context,40,90);context.runOnClient(c->require(((PhoneVideoPlayer)field(field(field(screen(c),"apps"),"messageVideo"),"player")).frame()==null,"Back left video decoding active"));
        }
        System.out.println("[contact-video] PASS: invalid-file rejection, staged native Send to isolated contact, authenticated exact-byte download, native Play in Phone/iPad, decoded frames and Back cleanup");
    }
}
