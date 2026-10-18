package dev.maro.gametest;

import com.sun.net.httpserver.HttpServer;
import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.phone.PhoneNetwork;
import dev.maro.phone.browser.PhoneWebBrowser;
import dev.maro.gametest.mixin.PhoneMouseProbe;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Native browser rendering, video, topic routing, pointer/keyboard input and cleanup. */
final class PhoneTikTokChecks {
    private static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static Object field(Object target,String name) { try { var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }catch(Exception e){throw new AssertionError(e);} }
    private static void set(Object target,String name,Object value) { try { var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value); }catch(Exception e){throw new AssertionError(e);} }
    private static SpotifyPhoneScreen screen(net.minecraft.client.MinecraftClient c) { return (SpotifyPhoneScreen)c.currentScreen; }
    private static Object app(net.minecraft.client.MinecraftClient c) { return field(field(screen(c),"apps"),"tikTok"); }
    private static void until(ClientGameTestContext c,BooleanSupplier condition,String message) { System.out.println("[phone-tiktok] Checking: "+message);for(int i=0;i<900&&!condition.getAsBoolean();i++)c.waitTicks(1);require(condition.getAsBoolean(),message); }
    private static void click(ClientGameTestContext context,float x,float y) {
        context.runOnClient(c->{var s=screen(c);var l=s.layout();var event=new Click(l.screenX(x,y),l.screenY(x,y),new MouseInput(0,0));s.mouseClicked(event,false);s.mouseReleased(event);});context.waitTicks(3);
    }
    private static void pageClick(ClientGameTestContext context,float x,float y) {
        float[] point=context.computeOnClient(c->{Object app=app(c);return new float[]{(float)field(app,"drawX")+x/(int)field(app,"pixelsW")*(float)field(app,"drawW"),
                (float)field(app,"drawY")+y/(int)field(app,"pixelsH")*(float)field(app,"drawH")};});
        click(context,point[0],point[1]);
    }
    /** Exercise Minecraft's real GLFW scroll callback, including other mods and captured-cursor routing. */
    private static void wheel(ClientGameTestContext context, double delta, int count) {
        context.runOnClient(c -> {
            var probe = (PhoneMouseProbe)c.mouse;
            var layout = screen(c).layout();
            if (!screen(c).allowsMouseLook()) {
                double scale = c.getWindow().getScaleFactor();
                probe.maro$cursor(c.getWindow().getHandle(), layout.screenX(90,190)*scale, layout.screenY(90,190)*scale);
            }
            for (int i=0;i<count;i++) probe.maro$scroll(c.getWindow().getHandle(),0,delta);
        });
    }
    private static void idle(ClientGameTestContext context, PhoneWebBrowser browser) {
        until(context, () -> !((java.util.concurrent.atomic.AtomicBoolean)field(browser,"swiping")).get()
                && ((java.util.concurrent.atomic.AtomicInteger)field(browser,"pendingSwipe")).get()==0, "Browser swipe queue did not drain");
    }
    private static com.google.gson.JsonObject command(PhoneWebBrowser browser,String command,com.google.gson.JsonObject params){
        try {
            var method=browser.getClass().getDeclaredMethod("command",String.class,com.google.gson.JsonObject.class);
            method.setAccessible(true);
            return (com.google.gson.JsonObject)((java.util.concurrent.CompletableFuture<?>)method.invoke(browser,command,params)).get(4,java.util.concurrent.TimeUnit.SECONDS);
        }catch(Exception e){throw new AssertionError("Could not inspect browser command "+command,e);}
    }
    private static String evaluate(PhoneWebBrowser browser, String expression) {
        try {
            var params=new com.google.gson.JsonObject();
            params.addProperty("expression",expression);
            params.addProperty("returnByValue",true);
            var response=command(browser,"Runtime.evaluate",params);
            var value=response.getAsJsonObject("result");
            return value.has("value")?value.get("value").getAsString():"";
        } catch(Exception e){throw new AssertionError("Could not inspect the live video's identity",e);}
    }
    private static String liveVideoId(PhoneWebBrowser browser) {
        return evaluate(browser,"""
            (()=>{let candidates=[...document.querySelectorAll('video')].map(v=>{let r=v.getBoundingClientRect();return {v,area:Math.max(0,Math.min(innerWidth,r.right)-Math.max(0,r.left))*Math.max(0,Math.min(innerHeight,r.bottom)-Math.max(0,r.top))*(v.paused?1:2)}}).filter(o=>o.area>8000);candidates.sort((a,b)=>b.area-a.area);return candidates.length?(candidates[0].v.currentSrc||candidates[0].v.src||''):''})()
            """);
    }
    private static void framing(ClientGameTestContext context,PhoneWebBrowser browser,URI origin,SpotifyPhone owner,String device,Set<String> events){
        for(String shape:new String[]{"wide","tall"})for(String kind:new String[]{"canvas","video","scaled"}){
            browser.navigate(origin.resolve("/media-framing?shape="+shape+"&kind="+(kind.equals("scaled")?"canvas&scaled=true":kind)).toString());browser.videoFocus(true);
            double ratio=shape.equals("wide")?16d/9:9d/16;long before=browser.frame().serial();
            until(context,()->browser.frame()!=null&&browser.frame().serial()>before&&browser.frame().focusedMedia()&&Math.abs((double)browser.frame().width()/browser.frame().height()-ratio)<.025,"Focused "+kind+" framing failed for "+shape+" on "+device);
            context.runOnClient(c->{owner.videoFraming.set("Fit");owner.tabletVideoFraming.set("Fit");});context.waitTicks(6);
            context.runOnClient(c->{Object a=app(c);float w=(float)field(a,"w"),h=(float)field(a,"h"),dw=(float)field(a,"drawW"),dh=(float)field(a,"drawH");require(dw<=w+.1&&dh<=h+.1,"Fit clipped the complete "+shape+" clip on "+device);require(Math.abs(dw/dh-ratio)<.025,"Fit stretched media");});
            if(kind.equals("canvas"))context.takeScreenshot("maro-tiktok-"+device.toLowerCase()+"-"+shape+"-fit");
            float width=context.computeOnClient(c->screen(c).layout().width());click(context,width-68,88);context.waitTicks(5);
            context.runOnClient(c->{Object a=app(c);float w=(float)field(a,"w"),h=(float)field(a,"h"),dw=(float)field(a,"drawW"),dh=(float)field(a,"drawH");require(dw>=w-.1&&dh>=h-.1,"Fill left empty margins on "+device);require(Math.abs(dw/dh-ratio)<.025,"Fill stretched media");});
            if(kind.equals("canvas"))context.takeScreenshot("maro-tiktok-"+device.toLowerCase()+"-"+shape+"-fill");
            events.remove("framing-click");events.remove("framing-center");float[] middle=context.computeOnClient(c->{Object a=app(c);return new float[]{(float)field(a,"x")+(float)field(a,"w")/2,(float)field(a,"y")+(float)field(a,"h")/2};});click(context,middle[0],middle[1]);
            until(context,()->events.contains("framing-center"),"Fill pointer missed the media center on "+device+" "+kind);context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE,0,0)));
            require(browser.frame().viewX()>5,"Media focus retained the website sidebar");
        }
        evaluate(browser,"document.querySelector('#login').style.display='block';'opened'");
        until(context,()->browser.frame()!=null&&!browser.frame().focusedMedia(),"A visible sign-in dialog was hidden by media focus");context.waitTicks(6);
        context.runOnClient(c->{Object a=app(c);require((float)field(a,"drawW")<=(float)field(a,"w")+.1&&(float)field(a,"drawH")<=(float)field(a,"h")+.1,"Fill zoomed a website dialog");});
        System.out.println("[phone-media-framing] "+device+" PASS: live canvas/video surfaces, landscape/portrait Fit and Fill, full media in Fit, full panel in Fill, preserved aspect, cropped pointer mapping, sidebar exclusion and unzoomed sign-in dialog");
    }
    private static void liveFraming(ClientGameTestContext context,SpotifyPhone owner,PhoneNetwork phone,java.nio.file.Path dir){
        require(PhoneWebBrowser.available(),"An installed browser is needed for live framing validation");
        for(String device:new String[]{"Phone","iPad"}){
            context.runOnClient(c->{owner.device.set(device);owner.mouseLook.set(false);owner.size.set(1d);owner.videoFraming.set("Fit");owner.tabletVideoFraming.set("Fit");c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),phone));set(app(c),"profile",dir.resolve("live-framing-"+device));});context.waitTicks(12);
            float width=context.computeOnClient(c->screen(c).layout().width());click(context,width/2,37);click(context,width/2+30,225);
            until(context,()->context.computeOnClient(c->field(app(c),"browser")!=null),"Live "+device+" did not open its browser");
            var browser=context.computeOnClient(c->(PhoneWebBrowser)field(app(c),"browser"));
            context.waitTicks(60);System.out.println("[phone-media-state] "+device+" requested="+field(browser,"width")+"x"+field(browser,"height")+" focus="+field(browser,"videoFocus")+" probing="+field(browser,"probing")+" swiping="+field(browser,"swiping")+" crop="+field(browser,"videoRect")+" task="+field(browser,"videoProbe"));
            try{until(context,()->browser.frame()!=null&&browser.frame().focusedMedia(),"Live "+device+" did not detect the visible media surface");}
            catch(AssertionError error){context.takeScreenshot("maro-tiktok-live-"+device.toLowerCase()+"-framing-blocked");System.out.println("[phone-media-live-debug] "+device+" "+evaluate(browser,"JSON.stringify({title:document.title,path:location.pathname,text:document.body.innerText.slice(0,1800),media:[...document.querySelectorAll('video,canvas,img')].filter(e=>e.getBoundingClientRect().width>80).map(e=>({type:e.tagName,visible:e.checkVisibility?.({opacityProperty:true,visibilityProperty:true}),ready:e.readyState,width:e.videoWidth||e.naturalWidth||e.width,height:e.videoHeight||e.naturalHeight||e.height,rect:e.getBoundingClientRect().toJSON()}))})"));System.out.println("[phone-media-live-probe] "+evaluate(browser,"JSON.stringify("+field(browser,"VIDEO_RECT")+")"));throw error;}
            context.waitTicks(10);
            System.out.println("[phone-media-metadata] "+device+" frame="+browser.frame()+" layout="+command(browser,"Page.getLayoutMetrics",new com.google.gson.JsonObject()));
            String media=evaluate(browser,"JSON.stringify("+field(browser,"VIDEO_RECT")+")");require(!media.equals("null")&&!media.contains("blocked"),"Live media probe returned no content: "+media);
            var rectangle=com.google.gson.JsonParser.parseString(media).getAsJsonObject();require(rectangle.has("w")&&Math.abs((double)browser.frame().width()/browser.frame().height()-rectangle.get("w").getAsDouble()/rectangle.get("h").getAsDouble())<.025,"Live media pixels clipped the "+device+" clip: "+browser.frame());
            context.runOnClient(c->{Object a=app(c);require((float)field(a,"drawW")<=(float)field(a,"w")+.1&&(float)field(a,"drawH")<=(float)field(a,"h")+.1,"Live Fit clipped the "+device+" clip");});context.takeScreenshot("maro-tiktok-live-"+device.toLowerCase()+"-fit");
            click(context,width-68,88);context.waitTicks(6);
            context.runOnClient(c->{Object a=app(c);require((float)field(a,"drawW")>=(float)field(a,"w")-.1&&(float)field(a,"drawH")>=(float)field(a,"h")-.1,"Live Fill left margins on "+device);});context.takeScreenshot("maro-tiktok-live-"+device.toLowerCase()+"-fill");
            System.out.println("[phone-media-live] "+device+" PASS: current TikTok media "+media+", Fit retains complete clip and Fill covers the panel; fresh isolated browser, no account sign-in");context.runOnClient(c->c.setScreen(null));
        }
    }
    static void run(ClientGameTestContext context) {
        var owner=ModuleManager.get(SpotifyPhone.class);HttpServer server=null;PhoneNetwork network=null;
        Set<String> events=ConcurrentHashMap.newKeySet(),pages=ConcurrentHashMap.newKeySet();
        var httpWorkers=Executors.newFixedThreadPool(8,r->{var t=new Thread(r,"Maro fixture HTTP");t.setDaemon(true);return t;});
        try {
            var dir=Files.createTempDirectory("maro-tiktok-test-");network=new PhoneNetwork(dir.resolve("phone.json"));var phone=network;
            byte[] video;try(var in=PhoneTikTokChecks.class.getResourceAsStream("/assets/maro/phone/demo-neon.mp4")){video=in.readAllBytes();}
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(httpWorkers);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();byte[] body;
                if(path.equals("/event")) { events.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(),StandardCharsets.UTF_8));body=new byte[0]; }
                else if(path.equals("/clip.mp4")) {body=video;exchange.getResponseHeaders().set("Content-Type","video/mp4");}
                else if(path.startsWith("/photo-")&&path.endsWith(".svg")) {
                    String index=path.substring(7,path.length()-4);
                    body=("<svg xmlns='http://www.w3.org/2000/svg' width='360' height='640'><rect width='360' height='640' fill='#172c45'/><rect width='40' height='40' fill='#ff1414'/><rect x='320' width='40' height='40' fill='#14ff14'/><rect y='600' width='40' height='40' fill='#1414ff'/><rect x='320' y='600' width='40' height='40' fill='#ffff14'/><text x='180' y='320' text-anchor='middle' font-size='42' fill='white'>Photo "+index+"</text></svg>").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","image/svg+xml");
                }
                else if(path.equals("/slideshow")) {
                    body="""
                        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>html{scroll-snap-type:y mandatory}body{margin:0;background:#05080b;color:white}section{height:100vh;scroll-snap-align:start;scroll-snap-stop:always}.sidebar{position:fixed;left:0;top:0;bottom:0;width:40px;background:magenta}.photo-carousel{position:relative;margin-left:40px;height:100vh;overflow:hidden}.photo-carousel img{width:100%;height:100%;object-fit:contain}button{position:absolute;bottom:20px;border:0;background:#18322e;color:white;padding:12px}#prev{left:6px}#next{right:6px}</style>
                        <div class="sidebar">SITE</div><section><div class="photo-carousel" aria-roledescription="carousel"><img id="picture" src="/photo-0.svg"><button id="prev" aria-label="Previous photo" onclick="show(index-1)">Previous</button><button id="next" aria-label="Next photo" onclick="show(index+1)">Next</button></div></section><section style="background:#123e4b"><h1>Next post</h1></section>
                        <script>let index=0;function report(s){fetch('/event?'+encodeURIComponent(s))}function show(i){index=Math.max(0,Math.min(11,i));picture.src='/photo-'+index+'.svg';prev.disabled=index===0;next.disabled=index===11;report('photo='+index)}show(0);onscroll=()=>{if(scrollY>innerHeight*.8)report('photo-next-post')};</script>
                        """.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                }
                else if(path.equals("/media-framing")) {
                    String query=exchange.getRequestURI().getRawQuery();boolean wide=query.contains("shape=wide"),canvas=query.contains("kind=canvas"),scaled=query.contains("scaled=true");
                    body=("""
                        <!doctype html>%s
                        <style>body{margin:0;background:#000}.sidebar{position:fixed;left:0;top:0;bottom:0;width:40px;background:magenta}.media{position:absolute;left:40px;right:24px;top:60px;bottom:60px}.media>*{position:absolute;width:100%%;height:100%%;object-fit:contain}#login{display:none;position:fixed;inset:20%% 10%%;background:#162b4a;color:white;padding:20px;z-index:8}</style>
                        <div class="sidebar">SITE</div><div class="media"><video id="movie" muted autoplay playsinline></video><canvas id="picture" width="%d" height="%d"></canvas></div><div role="dialog" id="login"><h1>Log in</h1><input placeholder="Account"></div>
                        <script>const canvasMode=%s;let g=picture.getContext('2d');let paint=()=>{let w=picture.width,h=picture.height;g.fillStyle='#174b70';g.fillRect(0,0,w,h);g.fillStyle='#4dc8ee';g.fillRect(w*.3,h*.15,w*.4,h*.7);g.fillStyle='#ff2448';g.fillRect(0,0,40,40);g.fillStyle='#24ff48';g.fillRect(w-40,0,40,40);g.fillStyle='#2488ff';g.fillRect(0,h-40,40,40);g.fillStyle='#ffdd24';g.fillRect(w-40,h-40,40,40);g.fillStyle='white';g.font='bold 28px sans-serif';g.textAlign='center';g.fillText('WHOLE CLIP',w/2,h/2);};paint();setInterval(paint,100);movie.srcObject=picture.captureStream(10);movie.play();picture.style.opacity=canvasMode?'1':'0';movie.style.opacity=canvasMode?'0':'1';picture.style.pointerEvents=canvasMode?'auto':'none';document.querySelector('.media').onclick=e=>{fetch('/event?framing-click');let r=e.currentTarget.getBoundingClientRect();if(Math.abs(e.clientX-r.left-r.width/2)<20&&Math.abs(e.clientY-r.top-r.height/2)<20)fetch('/event?framing-center')};</script>
                        """).formatted(scaled?"":"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">",wide?640:360,wide?360:640,canvas).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                }
                else {
                    pages.add(path);String title=path.equals("/tag/gaming")?"Gaming":path.equals("/tag/funny")?"Entertainment":"For You";
                    StringBuilder cards=new StringBuilder();
                    for(int index=1;index<=12;index++)cards.append("<section style='background:hsl(").append(index*29)
                            .append(" 35% 22%)'><h1>Video ").append(index+1)
                            .append(" / 13</h1><video autoplay muted loop playsinline src='/clip.mp4'></video></section>");
                    body=("""
                        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>html{scroll-snap-type:y mandatory}body{margin:0;background:#071719;color:#ecffff;font:18px sans-serif}section{height:100vh;padding:20px;box-sizing:border-box;scroll-snap-align:start;scroll-snap-stop:always}input,button{font:16px sans-serif;width:210px;height:26px}video{display:block;width:140px;height:220px;margin-top:12px}</style>
                        <section><b>%s · browser test</b><br><input placeholder="Type here" oninput="report('typed='+this.value)"><br><button onclick="report('clicked')">Test click</button><video autoplay muted loop playsinline src="/clip.mp4" onclick="report('video-clicked')"></video></section>
                        %s
                        <script>function report(s){fetch('/event?'+encodeURIComponent(s))}onscroll=()=>{report('scrolled');if(scrollY>innerHeight*.5)report('next-video');if(scrollY<innerHeight*.1)report('first-video');let i=Math.round(scrollY/innerHeight);if(Math.abs(scrollY/innerHeight-i)<.03)report('step='+i)};document.querySelector('video').ontimeupdate=e=>{if(e.target.currentTime>0.1)report('playing')};</script>
                        """).formatted(title,cards).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                }
                exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
            });
            server.start();URI origin=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            if(Boolean.getBoolean("maro.gametest.mediaFramingLiveOnly")){liveFraming(context,owner,phone,dir);return;}
            for(String device:Boolean.getBoolean("maro.gametest.liveTikTokOnly")?new String[0]:new String[]{"Phone","iPad"}) {
                context.runOnClient(c->{owner.device.set(device);owner.mouseLook.set(false);owner.size.set(1.0);owner.videoFraming.set("Fit");owner.tabletVideoFraming.set("Fit");c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),phone,null,dir.resolve(device)));set(app(c),"origin",origin);set(app(c),"profile",dir.resolve("browser-"+device));});context.waitTicks(12);
                float width=context.computeOnClient(c->screen(c).layout().width()),height=context.computeOnClient(c->screen(c).layout().height());
                click(context,width/2,37);click(context,width/2+30,225);
                require(context.computeOnClient(c->field(field(screen(c),"apps"),"app").toString().equals("TIKTOK")),"Home did not open TikTok");
                if(PhoneWebBrowser.available()) {
                    until(context,()->context.computeOnClient(c->field(app(c),"browser")!=null && ((PhoneWebBrowser)field(app(c),"browser")).frame()!=null),"Browser never produced a frame");
                    context.takeScreenshot("maro-tiktok-browser-before-playback-"+device.toLowerCase());
                    until(context,()->events.contains("playing"),"Chromium did not play the H.264 fixture video");
                    context.takeScreenshot("maro-tiktok-for-you-"+device.toLowerCase());
                    pageClick(context,45,48);context.getInput().typeChars("gaming");
                    until(context,()->events.contains("typed=gaming"),"Typing failed through phone coordinates");
                    require(context.computeOnClient(c->!screen(c).allowsMovement()),"Typing in TikTok allowed player movement");
                    pageClick(context,45,80);until(context,()->events.contains("clicked"),"Browser click/release did not reach page");
                    context.runOnClient(c->{var l=screen(c).layout();screen(c).mouseScrolled(l.screenX(90,190),l.screenY(90,190),0,-5);});
                    until(context,()->events.contains("next-video"),"Phone swipe did not advance to the next video");
                    context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE,0,0)));
                    require(context.computeOnClient(c->c.currentScreen instanceof SpotifyPhoneScreen && !((Boolean)field(app(c),"focused"))),"Escape did not release web typing");
                    context.waitTicks(10);
                    var browser=context.computeOnClient(c->(PhoneWebBrowser)field(app(c),"browser"));
                    idle(context,browser);
                    for(int index=2;index<=11;index++)events.remove("step="+index);
                    long frameBefore=browser.frame().serial();
                    wheel(context,-1,10);
                    for(int index=2;index<=11;index++) {int expected=index;until(context,()->events.contains("step="+expected),"Ten wheel notches lost video "+expected+" on "+device);}
                    idle(context,browser);
                    System.out.println("[phone-frame-continuity] "+device+" before="+frameBefore+" after="+browser.frame().serial()+" "+evaluate(browser,"JSON.stringify({y:scrollY,width:innerWidth,height:innerHeight,visibility:document.visibilityState,videos:[...document.querySelectorAll('video')].map(v=>({time:v.currentTime,paused:v.paused,ready:v.readyState,rect:v.getBoundingClientRect().toJSON()}))})"));
                    require(browser.frame().serial()>frameBefore+10,"Video frames stalled during the ten-scroll run");
                    context.takeScreenshot("maro-tiktok-ten-scrolls-"+device.toLowerCase());
                    for(int index=1;index<=10;index++)events.remove("step="+index);
                    wheel(context,1,10);
                    for(int index=10;index>=1;index--) {int expected=index;until(context,()->events.contains("step="+expected),"Ten reverse wheel notches lost video "+expected+" on "+device);}
                    idle(context,browser);
                    System.out.println("[phone-tiktok-ten-scrolls] "+device+" PASS: 10 native wheel callbacks forward, every video 2–11 visited, 10 callbacks back, live frames continued");
                    events.remove("first-video");context.waitTicks(8);click(context,28,height-28);
                    until(context,()->events.contains("first-video"),"Previous video button did not swipe back");
                    idle(context,browser);
                    events.remove("next-video");events.remove("first-video");events.remove("step=2");
                    wheel(context,-1,10);wheel(context,1,1);
                    until(context,()->events.contains("next-video"),"Direction-reversal fixture did not start its first swipe");
                    idle(context,browser);
                    require(events.contains("first-video")&&!events.contains("step=2"),"Reversing kept stale forward swipes queued");
                    context.waitTicks(10); // Let touch release and inertial scrolling settle before changing input modes.
                    // Full website mode must retain normal wheel input at the mapped pointer.
                    context.runOnClient(c->set(app(c),"videoFocus",false));events.remove("scrolled");
                    context.runOnClient(c->{var l=screen(c).layout();screen(c).mouseScrolled(l.screenX(90,190),l.screenY(90,190),0,-5);});
                    until(context,()->events.contains("scrolled"),"Full website wheel input failed");
                    context.waitTicks(10);events.remove("first-video");browser.scroll(60,120,10);
                    until(context,()->events.contains("first-video"),"Full website could not scroll back");
                    context.runOnClient(c->set(app(c),"videoFocus",true));browser.videoFocus(true);
                    until(context,()->browser.frame()!=null&&browser.frame().viewWidth()<200,"Video focus never cropped the live player");
                    context.waitTicks(4);context.takeScreenshot("maro-tiktok-focused-video-"+device.toLowerCase());
                    click(context,width/2,106+(height-126)/2);
                    until(context,()->events.contains("video-clicked"),"Focused video pointer did not map back to its page");
                    context.runOnClient(c->screen(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE,0,0)));
                    context.runOnClient(c->{owner.mouseLook.set(true);owner.gameInteraction.set(true);set(screen(c),"interacting",false);});context.waitTicks(8);events.remove("scrolled");events.remove("next-video");
                    require(context.computeOnClient(c->screen(c).allowsMouseLook()),"Mouse-look mode did not activate for video scroll regression");
                    context.runOnClient(c->{
                        long before=(long)field(app(c),"lastStep");screen(c).mouseScrolled(3,3,0,-.2);screen(c).mouseScrolled(3,3,0,-.2);
                        screen(c).mouseScrolled(3,3,0,-.2);screen(c).mouseScrolled(3,3,0,-.2);
                        require((long)field(app(c),"lastStep")==before,"Small trackpad deltas advanced too early");
                        int slot=c.player.getInventory().getSelectedSlot();screen(c).mouseScrolled(3,3,0,-.2);
                        require(c.player.getInventory().getSelectedSlot()==slot&&screen(c).allowsGameInteraction(),"Video wheel changed hotbar or disabled gameplay actions");
                    });
                    until(context,()->events.contains("next-video"),"Video scrolling did not advance while the game owned the cursor");
                    idle(context,browser);
                    for(int index=2;index<=11;index++)events.remove("step="+index);
                    int slot=context.computeOnClient(c->c.player.getInventory().getSelectedSlot());
                    wheel(context,-1,10);
                    for(int index=2;index<=11;index++){
                        int expected=index;until(context,()->events.contains("step="+expected),"Captured cursor lost wheel notch "+expected+" on "+device);
                        require(browser.frame().viewWidth()<200,"Focused scrolling flashed the full website");
                    }
                    idle(context,browser);
                    require(context.computeOnClient(c->c.player.getInventory().getSelectedSlot()==slot),"Ten TikTok wheel notches changed the hotbar");
                    context.takeScreenshot("maro-tiktok-ten-scrolls-game-controls-"+device.toLowerCase());
                    System.out.println("[phone-tiktok-ten-scrolls] "+device+" PASS: 10 native wheel callbacks with game controls, no lost videos or hotbar changes");
                    browser.videoFocus(false);context.runOnClient(c->owner.mouseLook.set(false));context.waitTicks(5);
                    until(context,()->browser.frame()!=null&&browser.frame().viewX()==0&&browser.frame().viewY()==0,"Full website view did not restore");
                } else context.takeScreenshot("maro-tiktok-install-"+device.toLowerCase());
                click(context,70,88);click(context,70,172);
                require(context.computeOnClient(c->field(app(c),"feed").toString().equals("ENTERTAINMENT")),"Entertainment tab routing failed");
                if(PhoneWebBrowser.available())until(context,()->pages.contains("/tag/funny"),"Entertainment did not load its topic page");
                click(context,70,88);click(context,70,219);
                require(context.computeOnClient(c->field(app(c),"feed").toString().equals("GAMING")),"Gaming tab routing failed");
                if(PhoneWebBrowser.available()) {until(context,()->pages.contains("/tag/gaming"),"Gaming did not load its topic page");context.waitTicks(10);context.takeScreenshot("maro-tiktok-gaming-"+device.toLowerCase());}
                if(PhoneWebBrowser.available()) {
                    var browser=context.computeOnClient(c->(PhoneWebBrowser)field(app(c),"browser"));
                    browser.navigate(origin.resolve("/slideshow").toString());browser.videoFocus(true);
                    until(context,()->browser.frame()!=null&&browser.frame().focusedMedia()&&browser.frame().viewX()>20,"Photo focus did not remove the website sidebar");
                    idle(context,browser);events.removeIf(e->e.startsWith("photo="));wheel(context,-1,10);
                    for(int index=1;index<=10;index++){int expected=index;until(context,()->events.contains("photo="+expected),"Slideshow wheel lost photo "+expected+" on "+device);}
                    idle(context,browser);
                    require(Math.abs((double)browser.frame().width()/browser.frame().height()-9d/16)<.02,"Photo framing clipped the portrait on "+device);
                    context.takeScreenshot("maro-tiktok-slideshow-clean-"+device.toLowerCase());
                    events.remove("photo=11");click(context,width/2+21,height-28);until(context,()->events.contains("photo=11"),"Scroll button did not advance a photo");idle(context,browser);
                    events.removeIf(e->e.startsWith("photo="));wheel(context,1,10);
                    for(int index=10;index>=1;index--){int expected=index;until(context,()->events.contains("photo="+expected),"Slideshow reverse lost photo "+expected+" on "+device);}
                    idle(context,browser);
                    wheel(context,-1,10);until(context,()->events.contains("photo=11"),"Slideshow did not return to its final photo");idle(context,browser);
                    click(context,width/2+21,height-28);until(context,()->events.contains("photo-next-post"),"Scrolling beyond the final photo did not advance to the next post");idle(context,browser);
                    System.out.println("[phone-slideshow] "+device+" PASS: ten photos forward/back, Scroll button, final-photo boundary, sidebar removed and whole portrait retained");
                    framing(context,browser,origin,owner,device,events);
                }
                Object view=context.computeOnClient(c->app(c));click(context,width-27,88);click(context,80,193);
                require(field(view,"browser")==null,"Leaving TikTok retained the browser");
                require(context.computeOnClient(c->field(field(screen(c),"apps"),"app").toString().equals("CLIPS")),"Local videos could not be opened");
                click(context,width-25,92);click(context,80,197);context.waitTicks(8);context.runOnClient(c->c.setScreen(null));require(field(view,"browser")==null,"Closing phone retained the browser");
                pages.clear();events.clear();
            }
            if((Boolean.getBoolean("maro.gametest.liveTikTok")||Boolean.getBoolean("maro.gametest.liveTikTokOnly")) && PhoneWebBrowser.available()) {
                context.runOnClient(c->{owner.device.set("Phone");owner.mouseLook.set(false);owner.size.set(1.0);c.setScreen(new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),phone));set(app(c),"profile",dir.resolve("live-browser"));});context.waitTicks(12);click(context,148,30);click(context,134,225);context.waitTicks(200);context.takeScreenshot("maro-tiktok-live-page");
                var browser=context.computeOnClient(c->(PhoneWebBrowser)field(app(c),"browser"));
                until(context,()->!liveVideoId(browser).isBlank(),"Live TikTok did not expose a playable video");
                System.out.println("[phone-media-geometry] "+evaluate(browser,"JSON.stringify({screen:[innerWidth,innerHeight],dialogs:[...document.querySelectorAll('[role=dialog]')].map(e=>({visible:e.checkVisibility?.({opacityProperty:true,visibilityProperty:true}),rect:e.getBoundingClientRect().toJSON(),media:e.querySelectorAll('video,canvas').length})),media:[...document.querySelectorAll('video,canvas')].map(e=>({type:e.tagName,visible:e.checkVisibility?.({opacityProperty:true,visibilityProperty:true}),ready:e.readyState,intrinsic:[e.videoWidth||e.width,e.videoHeight||e.height],fit:getComputedStyle(e).objectFit,rect:e.getBoundingClientRect().toJSON()}))})"));
                System.out.println("[phone-media-crop] "+field(browser,"videoRect"));
                boolean providerPrompt=false;
                for(int index=1;index<=10;index++) {
                    String before=liveVideoId(browser);wheel(context,-1,1);
                    try {
                        until(context,()->{String next=liveVideoId(browser);return !next.isBlank()&&!next.equals(before);},"Live TikTok wheel did not change video "+index);
                    } catch(AssertionError error) {
                        context.takeScreenshot("maro-tiktok-live-scroll-failure");
                        String page=evaluate(browser,"JSON.stringify({title:document.title,path:location.pathname,text:document.body.innerText.slice(-1600),videos:document.querySelectorAll('video').length})");
                        if(page.contains("Sign up")||page.contains("Log in to TikTok")) {
                            // Do not create an account or accept provider terms in an anonymous test.
                            System.out.println("[phone-tiktok-live-ten] PROVIDER PROMPT after "+(index-1)+" verified video changes; anonymous TikTok requested sign-up");
                            providerPrompt=true;break;
                        }
                        System.out.println("[phone-tiktok-live-debug] "+page);
                        throw error;
                    }
                    idle(context,browser);context.waitTicks(5);
                    context.takeScreenshot("maro-tiktok-live-scroll-"+index);
                    System.out.println("[phone-tiktok-live-ten] Changed video "+index+"/10 through native wheel input");
                }
                if(!providerPrompt)System.out.println("[phone-tiktok-live-ten] PASS: ten real TikTok video changes; fresh anonymous test profile, no account sign-in");
            }
            System.out.println("[phone-tiktok] For You/Entertainment/Gaming, Phone+iPad, browser input/video, wheel/trackpad, previous swipe, locked-cursor navigation and close lifecycle passed; installed browser="+PhoneWebBrowser.available());
        }catch(Exception e){throw new AssertionError(e);}
        finally {context.runOnClient(c->{c.setScreen(null);owner.getSettings().forEach(s->s.reset());});if(network!=null)network.close();if(server!=null)server.stop(0);httpWorkers.shutdownNow();}
    }
}
