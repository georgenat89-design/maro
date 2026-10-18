package dev.maro.phone.browser;

import com.google.gson.*;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded, asynchronous CDP view of a separate installed browser/profile. */
final class ChromePhoneBrowser implements PhoneWebBrowser, WebSocket.Listener {
    private static final Set<ChromePhoneBrowser> LIVE=ConcurrentHashMap.newKeySet();
    private static final ExecutorService LIFECYCLE=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Maro phone browser lifecycle");t.setDaemon(true);return t;});
    private static final Gson JSON=new Gson();
    private static final ScheduledExecutorService VIDEO_PROBES=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Maro video view");t.setDaemon(true);return t;});
    private static final String VIEWPORT="({viewportWidth:visualViewport?.width||innerWidth,viewportHeight:visualViewport?.height||innerHeight})";
    private static final String VIDEO_RECT="""
        (() => {
          const viewport={viewportWidth:visualViewport?.width||innerWidth,viewportHeight:visualViewport?.height||innerHeight};
          const shown=e=>!e.checkVisibility||e.checkVisibility({opacityProperty:true,visibilityProperty:true});
          const area=r=>Math.max(0,Math.min(innerWidth,r.right)-Math.max(0,r.left))*Math.max(0,Math.min(innerHeight,r.bottom)-Math.max(0,r.top));
          if ([...document.querySelectorAll('[role=dialog]')].some(e=>{let r=e.getBoundingClientRect();return shown(e)&&r.width>80&&r.height>80&&area(r)>6400})) return {...viewport,blocked:true};
          let candidates=[...document.querySelectorAll('video,canvas,img')].map(v=>({v,r:v.getBoundingClientRect()}))
            .filter(o=>shown(o.v)&&(o.v.tagName==='VIDEO'?o.v.readyState>=2:o.v.tagName==='CANVAS'?o.v.width>=200&&o.v.height>=100:o.v.complete&&o.v.naturalWidth>=200)&&o.r.width>80&&o.r.height>100&&area(o.r)>8000);
          const score=o=>{let top=Math.max(0,o.r.top),bottom=Math.min(innerHeight,o.r.bottom),center=(top+bottom)/2;return area(o.r)*(.2+.8*(1-Math.min(1,Math.abs(center-innerHeight/2)/(innerHeight/2))))*(o.v.tagName==='IMG'?1:1.2)};
          candidates.sort((a,b)=>score(b)-score(a));
          if (!candidates.length) return viewport;
          let {v,r}=candidates[0],x=r.left,y=r.top,w=r.width,h=r.height;
          let mw=v.videoWidth||v.naturalWidth||v.width,mh=v.videoHeight||v.naturalHeight||v.height;
          if(mw>0&&mh>0){if(v.tagName!=='CANVAS')v.style.objectFit='contain';let fit=Math.min(w/mw,h/mh),nw=mw*fit,nh=mh*fit;x+=(w-nw)/2;y+=(h-nh)/2;w=nw;h=nh;}
          return {...viewport,kind:v.tagName,x:Math.max(0,x),y:Math.max(0,y),w:Math.min(innerWidth,x+w)-Math.max(0,x),h:Math.min(innerHeight,y+h)-Math.max(0,y)};
        })()
        """;
    private static final String PHOTO_STEP="""
        (direction=>{
          const shown=e=>{let r=e.getBoundingClientRect();return (!e.checkVisibility||e.checkVisibility({opacityProperty:true,visibilityProperty:true}))&&r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth};
          let images=[...document.querySelectorAll('img')].filter(e=>shown(e)&&e.naturalWidth>=200&&e.getBoundingClientRect().width>100&&e.getBoundingClientRect().height>120);
          images.sort((a,b)=>{let x=a.getBoundingClientRect(),y=b.getBoundingClientRect();return Math.min(y.height,innerHeight)*Math.min(y.width,innerWidth)-Math.min(x.height,innerHeight)*Math.min(x.width,innerWidth)});
          if(!images.length)return {kind:'vertical'};
          let image=images[0],root=image.closest('[aria-roledescription=carousel],[data-e2e*=photo],[class*=swiper],[class*=carousel],[class*=slick]');
          if(!root){for(let p=image.parentElement,n=0;p&&p!==document.body&&n<6;p=p.parentElement,n++){if(p.querySelectorAll('img').length>1){root=p;break}}}
          if(!root)return {kind:'vertical'};
          let controls=[...root.querySelectorAll('button,[role=button],[class*=swiper-button]')];
          let wanted=direction>0?/next|right|forward/i:/previous|prev|left|back/i;
          let button=controls.find(e=>wanted.test([e.getAttribute('aria-label'),e.title,e.getAttribute('data-e2e'),e.className,e.textContent].join(' ')));
          if(button){if(button.disabled||button.getAttribute('aria-disabled')==='true'||/disabled/.test(button.className))return {kind:'vertical'};if(shown(button)){let r=button.getBoundingClientRect();return {kind:'click',x:r.left+r.width/2,y:r.top+r.height/2}}}
          let horizontal=[root,...root.querySelectorAll('*')].find(e=>e.clientWidth>100&&e.scrollWidth>e.clientWidth+8&&/auto|scroll/.test(getComputedStyle(e).overflowX));
          if(horizontal){let end=horizontal.scrollWidth-horizontal.clientWidth;if(direction>0&&horizontal.scrollLeft>=end-3||direction<0&&horizontal.scrollLeft<=3)return {kind:'vertical'};let r=horizontal.getBoundingClientRect();return {kind:'horizontal',x:r.left+r.width/2,y:r.top+r.height/2,distance:horizontal.clientWidth*.9}}
          return {kind:'vertical'};
        })(DIRECTION)
        """;
    private record VideoRect(float x,float y,float w,float h,long sampled) { }
    private static final long CROP_GRACE=5_000_000_000L;
    private volatile VideoRect videoRect;
    private volatile boolean videoFocus;
    private ScheduledFuture<?> videoProbe;
    private final AtomicBoolean probing=new AtomicBoolean();
    private static final Path EXECUTABLE=findExecutable();
    static { Runtime.getRuntime().addShutdownHook(new Thread(ChromePhoneBrowser::shutdown,"Maro phone browser shutdown")); }
    private final Path profile;
    private final Path downloads;
    private volatile Download download;
    private ScheduledFuture<?> downloadProbe;
    private static final long MAX_DOWNLOAD=256L*1024*1024;
    private final ExecutorService decode=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Maro browser frames");t.setDaemon(true);return t;});
    private record EncodedFrame(String data,int session,JsonObject metadata){}
    private final AtomicReference<EncodedFrame> pendingFrame=new AtomicReference<>();
    private final AtomicReference<Frame> frame=new AtomicReference<>();
    private final AtomicBoolean decoding=new AtomicBoolean();
    private final AtomicBoolean swiping=new AtomicBoolean();
    private static final int MAX_PENDING_SWIPES=16;
    private final AtomicInteger pendingSwipe=new AtomicInteger();
    private final AtomicLong swipeGeneration=new AtomicLong();
    private final AtomicLong ids=new AtomicLong();
    private final ConcurrentMap<Long,CompletableFuture<JsonObject>> responses=new ConcurrentHashMap<>();
    private final StringBuilder incoming=new StringBuilder();
    private volatile Process process;
    private volatile WebSocket socket;
    private volatile String target,status="Starting browser…";
    private volatile int width,height;
    private volatile double inputScaleX=1,inputScaleY=1;
    private volatile boolean closed,loading=true,ready;
    private CompletableFuture<?> sent=CompletableFuture.completedFuture(null);
    private long serial, decodedAt;
    private boolean mouseDown;
    private int lastX=-1,lastY=-1;

    static Path executable() { return EXECUTABLE; }
    private static Path findExecutable() {
        var paths=new ArrayList<String>();String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win")) {
            for(String root:new String[]{System.getenv("ProgramFiles(x86)"),System.getenv("ProgramFiles"),System.getenv("LOCALAPPDATA")})
                if(root!=null)for(String name:new String[]{"Microsoft/Edge/Application/msedge.exe","Google/Chrome/Application/chrome.exe"})paths.add(Path.of(root,name).toString());
        } else if(os.contains("mac"))paths.addAll(List.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge","/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        else paths.addAll(List.of("/usr/bin/google-chrome","/usr/bin/google-chrome-stable","/usr/bin/microsoft-edge","/usr/bin/chromium","/usr/bin/chromium-browser"));
        return paths.stream().map(Path::of).filter(Files::isRegularFile).findFirst().orElse(null);
    }
    ChromePhoneBrowser(String url,int width,int height,Path profile) {
        this(url,width,height,profile,null);
    }
    ChromePhoneBrowser(String url,int width,int height,Path profile,Path downloads) {
        this.target=url;this.width=width;this.height=height;this.profile=profile.toAbsolutePath();
        this.downloads=downloads==null?null:downloads.toAbsolutePath().normalize();LIVE.add(this);LIFECYCLE.execute(this::start);
    }
    private void start() {
        try {
            if(closed)return;Files.createDirectories(profile);Path portFile=profile.resolve("DevToolsActivePort");Files.deleteIfExists(portFile);
            var args=List.of(EXECUTABLE.toString(),"--headless=new","--no-first-run","--no-default-browser-check","--disable-extensions",
                    "--remote-debugging-address=127.0.0.1","--remote-debugging-port=0","--user-data-dir="+profile,
                    "--autoplay-policy=no-user-gesture-required","--window-size="+width+","+height,"about:blank");
            process=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
            while(!closed && process.isAlive() && !Files.exists(portFile) && System.nanoTime()<deadline)Thread.sleep(40);
            if(closed)return;
            if(!process.isAlive() || !Files.exists(portFile))throw new IOException("Browser startup failed");
            var portLines=Files.readAllLines(portFile);int port=Integer.parseInt(portLines.getFirst());if(port<1||port>65535)throw new IOException("Invalid browser port");
            HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/json/list")).timeout(Duration.ofSeconds(5)).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.body().length()>262144)throw new IOException("Invalid browser targets");
            JsonObject page=null;for(var entry:JsonParser.parseString(response.body()).getAsJsonArray())if(entry.getAsJsonObject().get("type").getAsString().equals("page")){page=entry.getAsJsonObject();break;}
            if(page==null)throw new IOException("No browser page");URI ws=URI.create(page.get("webSocketDebuggerUrl").getAsString());
            if(!"ws".equals(ws.getScheme())||!("127.0.0.1".equals(ws.getHost())||"localhost".equals(ws.getHost()))||ws.getPort()!=port)throw new IOException("Invalid browser endpoint");
            socket=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(ws,this).join();
            if(closed)return;
            command("Page.enable",new JsonObject()).get(5,TimeUnit.SECONDS);
            if(downloads==null)command("Browser.setDownloadBehavior",obj("behavior","deny")).get(5,TimeUnit.SECONDS);
            else {
                Files.createDirectories(downloads);
                command("Browser.setDownloadBehavior",obj("behavior","allowAndName","downloadPath",downloads.toString(),"eventsEnabled",true)).get(5,TimeUnit.SECONDS);
                downloadProbe=VIDEO_PROBES.scheduleWithFixedDelay(this::probeDownload,250,250,TimeUnit.MILLISECONDS);
            }
            metrics();command("Page.bringToFront",new JsonObject());
            command("Page.startScreencast",screencast()).get(5,TimeUnit.SECONDS);
            ready=true;status="";navigate(target);
            videoProbe=VIDEO_PROBES.scheduleWithFixedDelay(this::probeVideo,200,400,TimeUnit.MILLISECONDS);if(closed)videoProbe.cancel(false);
        } catch(Exception ex) { if(!closed) {status="Browser could not start. Try Reload.";loading=false;} }
        finally { if(closed || !ready)cleanup(); }
    }
    private static JsonObject obj(Object... pairs) {
        var out=new JsonObject();for(int i=0;i<pairs.length;i+=2)out.add((String)pairs[i],JSON.toJsonTree(pairs[i+1]));return out;
    }
    private synchronized CompletableFuture<JsonObject> command(String method,JsonObject params) {
        WebSocket current=socket;if(current==null)return CompletableFuture.failedFuture(new IOException("Browser not ready"));
        long id=ids.incrementAndGet();var result=new CompletableFuture<JsonObject>();responses.put(id,result);
        String message=JSON.toJson(obj("id",id,"method",method,"params",params));
        sent=sent.handle((v,e)->null).thenCompose(v->current.sendText(message,true));
        sent.exceptionally(ex->{result.completeExceptionally(ex);return null;});
        result.orTimeout(8,TimeUnit.SECONDS).whenComplete((v,e)->responses.remove(id));return result;
    }
    @Override public void onOpen(WebSocket ws) { ws.request(1); }
    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last) {
        if(closed){ws.request(1);return null;}
        incoming.append(data);
        if(incoming.length()>2_000_000){incoming.setLength(0);status="Browser frame is too large";ws.abort();return null;}
        if(last) {
            try {
                JsonObject message=JsonParser.parseString(incoming.toString()).getAsJsonObject();incoming.setLength(0);
                if(message.has("id")) {var waiting=responses.remove(message.get("id").getAsLong());if(waiting!=null){if(message.has("error"))waiting.completeExceptionally(new IOException("Browser command failed: "+message.getAsJsonObject("error")));else waiting.complete(message.getAsJsonObject("result"));}}
                else if(message.has("method")) {
                    String method=message.get("method").getAsString();var p=message.getAsJsonObject("params");
                    if(method.equals("Page.screencastFrame")) {
                        var replaced=pendingFrame.getAndSet(new EncodedFrame(p.get("data").getAsString(),p.get("sessionId").getAsInt(),p.getAsJsonObject("metadata")));
                        if(replaced!=null)ackFrame(replaced);scheduleDecode();
                    } else if(method.equals("Browser.downloadWillBegin")&&downloads!=null) {
                        String id=p.get("guid").getAsString();
                        if(id.matches("[a-fA-F0-9-]{36}")) {
                            if(download!=null)command("Browser.cancelDownload",obj("guid",id));
                            else download=new Download(id,p.get("suggestedFilename").getAsString().replaceAll("[\\p{Cntrl}]"," "),0,0,"downloading",null,"");
                        }
                    } else if(method.equals("Browser.downloadProgress")&&downloads!=null&&download!=null&&download.id().equals(p.get("guid").getAsString())) {
                        long received=p.get("receivedBytes").getAsLong(),total=p.get("totalBytes").getAsLong();String state=p.get("state").getAsString();
                        if(state.equals("inProgress"))state="downloading";
                        if(received>MAX_DOWNLOAD||total>MAX_DOWNLOAD)failDownload("Pack exceeds 256 MB");
                        else if(!download.state().equals("failed"))download=new Download(download.id(),download.filename(),received,total,state,state.equals("completed")?downloads.resolve(download.id()):null,state.equals("canceled")?"Download canceled":"");
                        if(state.equals("completed"))command("Browser.setDownloadBehavior",obj("behavior","deny"));
                    } else if(method.equals("Page.frameStartedLoading"))loading=true;
                    else if(method.equals("Page.frameStoppedLoading") || method.equals("Page.loadEventFired"))loading=false;
                    else if(method.equals("Page.javascriptDialogOpening"))command("Page.handleJavaScriptDialog",obj("accept",false));
                    else if(method.equals("Page.windowOpen") && p.has("url")) {
                        URI uri=URI.create(p.get("url").getAsString());if("https".equals(uri.getScheme()))navigate(uri.toString());
                    }
                }
            }catch(RuntimeException ignored){incoming.setLength(0);}
        }
        ws.request(1);return null;
    }
    @Override public Download download(){return download;}
    @Override public void cancelDownload(){if(download!=null&&download.state().equals("downloading"))failDownload("Download canceled");}
    private void failDownload(String message){
        Download d=download;if(d==null)return;
        download=new Download(d.id(),d.filename(),d.received(),d.total(),"failed",null,message);
        command("Browser.cancelDownload",obj("guid",d.id()));
    }
    private void probeDownload(){
        if(closed||downloads==null||download!=null&&download.state().equals("completed"))return;
        try(var files=Files.list(downloads)){
            for(Path file:files.limit(8).toList()){
                String name=file.getFileName().toString();if(!name.matches("[a-fA-F0-9-]{36}(\\.crdownload)?")||!Files.isRegularFile(file))continue;
                long bytes=Files.size(file);
                if(bytes>MAX_DOWNLOAD){if(download==null)download=new Download(name.replace(".crdownload",""),"Resource pack",bytes,0,"downloading",null,"");failDownload("Pack exceeds 256 MB");continue;}
                // File observation also works with browser versions which emit download events on a separate session.
                if(!name.endsWith(".crdownload")&&bytes>0&&(download==null||download.state().equals("downloading")&&download.id().equals(name))) {
                    download=new Download(name,download==null?"Resource pack.zip":download.filename(),bytes,bytes,"completed",file,"");
                    command("Browser.setDownloadBehavior",obj("behavior","deny"));
                }
            }
        }catch(IOException ignored){}
    }
    private void ackFrame(EncodedFrame encoded){if(!closed)command("Page.screencastFrameAck",obj("sessionId",encoded.session()));}
    private void scheduleDecode() {
        if(closed||!decoding.compareAndSet(false,true))return;
        try { decode.execute(()->{
            try {while(!closed && pendingFrame.get()!=null) {
                long wait=16_666_667L-(System.nanoTime()-decodedAt);
                if(wait>0)java.util.concurrent.locks.LockSupport.parkNanos(wait);
                if(closed)break;
                var encoded=pendingFrame.getAndSet(null);if(encoded==null)continue;
                decodedAt=System.nanoTime();
                try {
                byte[] bytes=Base64.getDecoder().decode(encoded.data());if(bytes.length>1_500_000)continue;
                var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null||image.getWidth()>1024||image.getHeight()>1536||(long)image.getWidth()*image.getHeight()>1_048_576)continue;
                if(Boolean.getBoolean("maro.debug")&&serial==0)System.out.println("[phone-capture-surface] "+image.getWidth()+"x"+image.getHeight()+" "+encoded.metadata);
                int iw=image.getWidth(),ih=image.getHeight(),px=0,py=0,pw=iw,ph=ih;
                float vx=0,vy=0,vw=width,vh=height;
                VideoRect rect=videoFocus?videoRect:null;
                boolean media=rect!=null && (swiping.get() || System.nanoTime()-rect.sampled<CROP_GRACE);
                if(media){
                    float surfaceW=encoded.metadata.has("deviceWidth")?encoded.metadata.get("deviceWidth").getAsFloat():width;
                    float surfaceH=encoded.metadata.has("deviceHeight")?encoded.metadata.get("deviceHeight").getAsFloat():height;
                    if(!Float.isFinite(surfaceW)||!Float.isFinite(surfaceH)||surfaceW<1||surfaceH<1)continue;
                    float sx=iw/surfaceW,sy=ih/surfaceH;
                    px=Math.clamp(Math.round(rect.x*sx),0,iw-1);py=Math.clamp(Math.round(rect.y*sy),0,ih-1);
                    pw=Math.clamp(Math.round(rect.w*sx),1,iw-px);ph=Math.clamp(Math.round(rect.h*sy),1,ih-py);
                    vx=px/sx;vy=py/sy;vw=pw/sx;vh=ph/sy;
                }
                int[] pixels=image.getRGB(px,py,pw,ph,null,0,pw);
                if(!closed)frame.set(new Frame(++serial,pw,ph,dev.maro.runtime.renderer.NativeImageUpload.rgba(pixels),vx,vy,vw,vh,media));
                } finally {ackFrame(encoded);}
            }}catch(IOException|RuntimeException ignored){}finally {decoding.set(false);if(!closed&&pendingFrame.get()!=null)scheduleDecode();}
        });}catch(RejectedExecutionException ignored){decoding.set(false);}
    }
    @Override public void videoFocus(boolean enabled) {if(videoFocus!=enabled)cancelSwipes();videoFocus=enabled;if(!enabled)videoRect=null;}
    private void probeVideo(){
        if(!ready||closed||swiping.get()||!probing.compareAndSet(false,true))return;
        long generation=swipeGeneration.get();
        command("Runtime.evaluate",obj("expression",videoFocus?VIDEO_RECT:VIEWPORT,"returnByValue",true)).whenComplete((reply,error)->{
            try{
                VideoRect next=null;
                boolean blocked=false;
                if(error==null&&reply!=null&&reply.has("result")){
                    var result=reply.getAsJsonObject("result");
                    if(result.has("value")&&result.get("value").isJsonObject()){
                        var value=result.getAsJsonObject("value");
                        blocked=value.has("blocked");
                        double cssW=value.get("viewportWidth").getAsDouble(),cssH=value.get("viewportHeight").getAsDouble();
                        if(!Double.isFinite(cssW)||!Double.isFinite(cssH)||cssW<1||cssH<1||cssW>32768||cssH>32768)return;
                        double sx=cssW/width,sy=cssH/height;
                        if(!closed&&swipeGeneration.get()==generation){inputScaleX=sx;inputScaleY=sy;}
                        if(videoFocus&&!blocked&&value.has("x")) {
                            float x=(float)(value.get("x").getAsDouble()/sx),y=(float)(value.get("y").getAsDouble()/sy),w=(float)(value.get("w").getAsDouble()/sx),h=(float)(value.get("h").getAsDouble()/sy);
                            if(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(w)&&Float.isFinite(h)&&x>=0&&y>=0&&w>40&&h>60&&x+w<=width+1&&y+h<=height+1)
                                next=new VideoRect(x,y,w,h,System.nanoTime());
                        }
                    }
                }
                if(!closed&&videoFocus&&!swiping.get()&&swipeGeneration.get()==generation) {
                    // A new video may briefly have no decoded frames. Retain its viewport while
                    // buffering, but expose real dialogs immediately so their controls still work.
                    VideoRect previous=videoRect;
                    videoRect=blocked?null:next!=null?next:previous!=null&&System.nanoTime()-previous.sampled<CROP_GRACE?previous:null;
                }
            }catch(RuntimeException ignored){}finally{probing.set(false);}
        });
    }
    @Override public void onError(WebSocket ws,Throwable error) { if(!closed)status="Browser connection lost. Try Reload."; }
    @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason) { if(!closed)status="Browser closed. Try Reload.";return null; }
    @Override public Frame frame() { return frame.get(); }
    @Override public String status() { return status; }
    private JsonObject screencast() { return obj("format","jpeg","quality",94,"maxWidth",width,"maxHeight",height,"everyNthFrame",1); }
    private void metrics() {
        command("Emulation.setDeviceMetricsOverride",obj("width",width,"height",height,"screenWidth",width,"screenHeight",height,"deviceScaleFactor",1,"mobile",true));
        command("Emulation.setVisibleSize",obj("width",width,"height",height));
        command("Emulation.setTouchEmulationEnabled",obj("enabled",true,"maxTouchPoints",1));
    }
    @Override public void resize(int width,int height) {cancelSwipes();this.width=width;this.height=height;videoRect=null;if(ready){metrics();command("Page.stopScreencast",new JsonObject()).thenCompose(v->command("Page.startScreencast",screencast()));}}
    @Override public void navigate(String url) {cancelSwipes();target=url;videoRect=null;if(ready&&!closed){loading=true;status="";command("Page.navigate",obj("url",url)).whenComplete((result,error)->{
            if(!url.equals(target)||closed)return;
            if(error!=null || result!=null&&result.has("errorText")){status="Page could not load. Try Reload or Browser.";loading=false;}
        });}}
    @Override public void reload() {cancelSwipes();if(ready){loading=true;status="";command("Page.reload",new JsonObject());}}
    @Override public void focus(boolean focused) {if(ready)command("Emulation.setFocusEmulationEnabled",obj("enabled",focused));}
    @Override public void move(int x,int y) {if(x!=lastX||y!=lastY){lastX=x;lastY=y;mouse("mouseMoved",x,y,0);}}
    private void mouse(String type,int x,int y,int count) {if(ready&&!closed)command("Input.dispatchMouseEvent",obj("type",type,"x",x*inputScaleX,"y",y*inputScaleY,"button",mouseDown?"left":"none","buttons",mouseDown&&!type.equals("mouseReleased")?1:0,"clickCount",count));}
    @Override public void press(int x,int y) {mouseDown=true;mouse("mousePressed",x,y,1);}
    @Override public void release(int x,int y) {mouse("mouseReleased",x,y,1);mouseDown=false;}
    @Override public void scroll(int x,int y,double amount) {if(ready&&!closed)command("Input.dispatchMouseEvent",obj("type","mouseWheel","x",x*inputScaleX,"y",y*inputScaleY,"deltaX",0,"deltaY",-amount*100*inputScaleY));}
    @Override public void videoStep(int direction) {
        if (!ready || closed || direction == 0) return;
        int step=Integer.signum(direction);
        // A reverse request replaces pending forward motion; repeated notches retain their count.
        pendingSwipe.updateAndGet(pending -> pending!=0&&Integer.signum(pending)!=step?step:
                Math.clamp(pending+step,-MAX_PENDING_SWIPES,MAX_PENDING_SWIPES));
        drainSwipe();
    }
    private void drainSwipe() {
        if(closed||!ready||!swiping.compareAndSet(false,true))return;
        int direction=Integer.signum(pendingSwipe.getAndUpdate(pending -> pending-Integer.signum(pending)));
        if(direction==0){swiping.set(false);if(pendingSwipe.get()!=0)drainSwipe();return;}
        long generation=swipeGeneration.incrementAndGet();
        // Let the browser's wheel handler and CSS snap animation own the transition. Synthesized
        // touch drags can leave TikTok's carousel latched after the first video.
        VideoRect rect=videoRect;
        double x=rect==null?width*.5:rect.x+rect.w*.5,y=rect==null?height*.5:rect.y+rect.h*.5;
        command("Runtime.evaluate",obj("expression",PHOTO_STEP.replace("DIRECTION",Integer.toString(direction)),"returnByValue",true))
                .handle((reply,error)->{
                    if(error==null&&reply!=null&&reply.has("result")){
                        JsonObject result=reply.getAsJsonObject("result");
                        if(result.has("value")&&result.get("value").isJsonObject())return result.getAsJsonObject("value");
                    }
                    return obj("kind","vertical");
                }).thenCompose(action->{
                    if(closed||swipeGeneration.get()!=generation)return CompletableFuture.failedFuture(new CancellationException());
                    String kind=action.get("kind").getAsString();
                    if(kind.equals("click"))return command("Input.dispatchMouseEvent",obj("type","mousePressed","button","left","buttons",1,"clickCount",1,"x",action.get("x").getAsDouble(),"y",action.get("y").getAsDouble()))
                            .thenCompose(v->command("Input.dispatchMouseEvent",obj("type","mouseReleased","button","left","buttons",0,"clickCount",1,"x",action.get("x").getAsDouble(),"y",action.get("y").getAsDouble())));
                    if(kind.equals("horizontal"))return command("Input.dispatchMouseEvent",obj("type","mouseWheel","x",action.get("x").getAsDouble(),"y",action.get("y").getAsDouble(),"deltaX",direction*action.get("distance").getAsDouble(),"deltaY",0));
                    return command("Input.dispatchMouseEvent",obj("type","mouseWheel","x",x*inputScaleX,"y",y*inputScaleY,"deltaX",0,"deltaY",direction*Math.max(300,height*.85)*inputScaleY));
                }).orTimeout(3000,TimeUnit.MILLISECONDS)
                .whenComplete((result,error)->{
                    if(!swipeGeneration.compareAndSet(generation,generation+1)||closed)return;
                    // Serialize gestures through snap completion; never replace a queued notch.
                    CompletableFuture.delayedExecutor(450,TimeUnit.MILLISECONDS).execute(()->{
                        if(closed||swipeGeneration.get()!=generation+1)return;
                        VideoRect crop=videoRect;
                        if(crop!=null)videoRect=new VideoRect(crop.x,crop.y,crop.w,crop.h,System.nanoTime());
                        swiping.set(false);probeVideo();if(pendingSwipe.get()!=0)drainSwipe();
                    });
                });
    }
    private void cancelSwipes() {
        pendingSwipe.set(0);swipeGeneration.incrementAndGet();
        swiping.set(false);
    }
    @Override public void key(KeyInput input,boolean pressed) {
        int key=input.key(),mods=input.modifiers(),vk=key;String name="",code="";
        if(key>=GLFW.GLFW_KEY_A&&key<=GLFW.GLFW_KEY_Z){name=String.valueOf((char)(key+((mods&GLFW.GLFW_MOD_SHIFT)==0?32:0)));code="Key"+(char)key;}
        else if(key>=GLFW.GLFW_KEY_0&&key<=GLFW.GLFW_KEY_9){name=String.valueOf((char)key);code="Digit"+(char)key;}
        else switch(key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {vk=8;name=code="Backspace";}
            case GLFW.GLFW_KEY_TAB -> {vk=9;name=code="Tab";}
            case GLFW.GLFW_KEY_ENTER -> {vk=13;name=code="Enter";}
            case GLFW.GLFW_KEY_ESCAPE -> {vk=27;name=code="Escape";}
            case GLFW.GLFW_KEY_SPACE -> {vk=32;name=" ";code="Space";}
            case GLFW.GLFW_KEY_LEFT -> {vk=37;name=code="ArrowLeft";}
            case GLFW.GLFW_KEY_UP -> {vk=38;name=code="ArrowUp";}
            case GLFW.GLFW_KEY_RIGHT -> {vk=39;name=code="ArrowRight";}
            case GLFW.GLFW_KEY_DOWN -> {vk=40;name=code="ArrowDown";}
            case GLFW.GLFW_KEY_DELETE -> {vk=46;name=code="Delete";}
            case GLFW.GLFW_KEY_HOME -> {vk=36;name=code="Home";}
            case GLFW.GLFW_KEY_END -> {vk=35;name=code="End";}
            default -> {return;}
        }
        int cdpMods=((mods&GLFW.GLFW_MOD_ALT)!=0?1:0)|((mods&GLFW.GLFW_MOD_CONTROL)!=0?2:0)|((mods&GLFW.GLFW_MOD_SUPER)!=0?4:0)|((mods&GLFW.GLFW_MOD_SHIFT)!=0?8:0);
        if(ready&&!closed)command("Input.dispatchKeyEvent",obj("type",pressed?"rawKeyDown":"keyUp","key",name,"code",code,"windowsVirtualKeyCode",vk,"modifiers",cdpMods));
    }
    @Override public void character(int point) {if(Character.isValidCodePoint(point))paste(new String(Character.toChars(point)));}
    @Override public void paste(String text) {if(ready&&!closed)command("Input.insertText",obj("text",text));}
    @Override public boolean loading() {return loading;}
    @Override public void close() {
        if(closed)return;cancelSwipes();closed=true;ready=false;if(videoProbe!=null)videoProbe.cancel(false);if(downloadProbe!=null)downloadProbe.cancel(false);videoRect=null;frame.set(null);pendingFrame.set(null);decode.shutdownNow();LIFECYCLE.execute(this::cleanup);
    }
    private void cleanup() {
        if(socket!=null){try{command("Browser.close",new JsonObject()).get(1,TimeUnit.SECONDS);}catch(Exception ignored){}socket.abort();socket=null;}
        Process owned=process;if(owned!=null){try{if(!owned.waitFor(1,TimeUnit.SECONDS)){owned.destroy();if(!owned.waitFor(1,TimeUnit.SECONDS))owned.destroyForcibly();}}catch(InterruptedException e){owned.destroyForcibly();Thread.currentThread().interrupt();}}
        LIVE.remove(this);
    }
    static void shutdown() {
        for(var browser:List.copyOf(LIVE))browser.close();
        try {LIFECYCLE.submit(()->{}).get(4,TimeUnit.SECONDS);}catch(Exception ignored) {
            for(var browser:List.copyOf(LIVE))if(browser.process!=null&&browser.process.isAlive())browser.process.destroyForcibly();
        }
    }
}
