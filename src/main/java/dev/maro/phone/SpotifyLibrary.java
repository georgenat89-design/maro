package dev.maro.phone;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import dev.maro.config.ConfigManager;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Read-only Spotify library access through browser OAuth/PKCE; no client secret or password. */
public final class SpotifyLibrary implements AutoCloseable {
    public record Snapshot(String clientId, boolean connected, boolean busy, String status, List<PhoneNetwork.Playlist> playlists) { }
    private static SpotifyLibrary instance;
    public static synchronized SpotifyLibrary get() {
        if(instance==null)instance=new SpotifyLibrary(ConfigManager.DIR.resolve("spotify-library.json"),
            URI.create("https://accounts.spotify.com/api/token"),URI.create("https://api.spotify.com/v1"),
            URI.create("https://accounts.spotify.com/authorize"),uri->Util.getOperatingSystem().open(uri));
        return instance;
    }
    public static synchronized void shutdown(){if(instance!=null){instance.close();instance=null;}}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final URI tokenEndpoint,api,authorize;
    private final Consumer<URI> opener;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Maro Spotify library");t.setDaemon(true);return t;});
    private volatile Snapshot snapshot=new Snapshot("",false,false,"Connect Spotify to see your playlists",List.of());
    private volatile HttpServer callback;
    private volatile long generation;
    private String clientId="",refreshToken="",accessToken="";
    private long expires;
    private List<PhoneNetwork.Playlist> playlists=List.of();
    /** Custom endpoints/opener allow isolated native tests; the default instance uses Spotify only. */
    public SpotifyLibrary(Path file,URI tokenEndpoint,URI api,URI authorize,Consumer<URI> opener){
        this.file=file;this.tokenEndpoint=tokenEndpoint;this.api=api;this.authorize=authorize;this.opener=opener;
        worker.execute(()->{
            if(Files.exists(file))try{
                var saved=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                clientId=saved.get("client_id").getAsString();refreshToken=saved.get("refresh_token").getAsString();
                if(!clientId.matches("[a-fA-F0-9]{32}"))throw new IOException("Invalid app ID");
            }catch(Exception e){clientId=refreshToken="";}
            publish(false,refreshToken.isEmpty()?"Connect Spotify to see your playlists":"Connected · Refresh to load playlists");
        });
    }
    public Snapshot snapshot(){return snapshot;}
    public synchronized void connect(String id){
        if(!id.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException("Enter your Spotify app's 32-character client ID");
        if(snapshot.busy())throw new IllegalArgumentException("Spotify is already connecting");
        long run=++generation;
        snapshot=new Snapshot(id,false,true,"Sign in using your browser…",List.of());
        worker.execute(()->{
            try {
                stopCallback();clientId=id;refreshToken=accessToken="";playlists=List.of();save();
                String verifier=random(),state=random();
                String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
                callback=server;
                String redirect="http://127.0.0.1:"+server.getAddress().getPort()+"/callback";
                server.createContext("/callback",exchange->{
                    boolean valid=false;String code="",denied="";
                    try {
                        if(!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/callback"))throw new IOException();
                        String raw=exchange.getRequestURI().getRawQuery();
                        if(raw==null || raw.length()>4096)throw new IOException();
                        var query=new HashMap<String,String>();
                        for(String pair:raw.split("&")){var bits=pair.split("=",2);query.put(decode(bits[0]),bits.length==2?decode(bits[1]):"");}
                        valid=run==generation && MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),query.getOrDefault("state","").getBytes(StandardCharsets.UTF_8));
                        code=query.getOrDefault("code","");denied=query.getOrDefault("error","");
                        valid&=!code.isEmpty() || !denied.isEmpty();
                    }catch(Exception ignored){}
                    byte[] response=(valid?"You can return to Maro Phone.":"Invalid sign-in response. Return to Maro and try again.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control","no-store");
                    exchange.sendResponseHeaders(valid?200:400,response.length);try(var out=exchange.getResponseBody()){out.write(response);}
                    if(valid){
                        String authCode=code;boolean rejected=!denied.isEmpty();server.stop(0);
                        worker.execute(()->{
                            if(run!=generation)return;
                            try {
                                if(rejected)throw new IOException("Spotify sign-in cancelled");
                                tokens(post(Map.of("grant_type","authorization_code","code",authCode,"redirect_uri",redirect,"client_id",id,"code_verifier",verifier)),run);
                                if(run==generation)loadPlaylists(run);
                            }catch(Exception e){if(run==generation)publish(false,message(e));}
                        });
                    }
                });
                server.start();
                if(run!=generation){server.stop(0);return;}
                String params=form(Map.of("response_type","code","client_id",id,"redirect_uri",redirect,"state",state,
                    "scope","playlist-read-private playlist-read-collaborative","code_challenge_method","S256","code_challenge",challenge));
                opener.accept(URI.create(authorize+"?"+params));
                worker.schedule(()->{if(run==generation && snapshot.busy()){stopCallback();publish(false,"Sign-in expired · Try again");}},180,TimeUnit.SECONDS);
            }catch(Exception e){if(run==generation){stopCallback();publish(false,message(e));}}
        });
    }
    public synchronized void refresh(){
        if(snapshot.busy())return;
        if(refreshToken.isEmpty())throw new IllegalArgumentException("Connect Spotify first");
        long run=generation; snapshot=new Snapshot(clientId,true,true,"Loading playlists…",playlists);
        worker.execute(()->{try{loadPlaylists(run);}catch(Exception e){if(run==generation)publish(false,message(e));}});
    }
    public void disconnect(){
        ++generation;stopCallback();
        worker.execute(()->{refreshToken=accessToken="";expires=0;playlists=List.of();try{save();publish(false,"Spotify disconnected");}catch(IOException e){publish(false,"Could not save Spotify settings");}});
    }
    private void loadPlaylists(long run)throws Exception{
        if(accessToken.isEmpty() || System.currentTimeMillis()>expires-30000)
            tokens(post(Map.of("grant_type","refresh_token","refresh_token",refreshToken,"client_id",clientId)),run);
        if(run!=generation)return;
        List<PhoneNetwork.Playlist> all=new ArrayList<>();
        for(int page=0;page<10;page++){
            var req=HttpRequest.newBuilder(URI.create(api+"/me/playlists?limit=50&offset="+page*50)).timeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+accessToken).header("Accept","application/json").GET().build();
            var data=request(req);all.addAll(parsePlaylists(data));
            if(!data.has("next") || data.get("next").isJsonNull())break;
        }
        if(run!=generation)return;
        playlists=List.copyOf(all);publish(false,playlists.size()+" Spotify playlists");
    }
    public static List<PhoneNetwork.Playlist> parsePlaylists(JsonObject data){
        List<PhoneNetwork.Playlist> out=new ArrayList<>();
        if(!data.has("items"))return List.of();
        for(var item:data.getAsJsonArray("items"))try{
            if(item.isJsonNull())continue;
            var p=item.getAsJsonObject();String id=p.get("id").getAsString(),title=p.get("name").getAsString().strip();
            if(!id.matches("[A-Za-z0-9]{22}") || title.isEmpty())continue;
            out.add(new PhoneNetwork.Playlist(id,title.substring(0,Math.min(60,title.length())),"https://open.spotify.com/playlist/"+id));
        }catch(RuntimeException ignored){}
        return List.copyOf(out);
    }
    private JsonObject post(Map<String,String> values)throws Exception{
        return request(HttpRequest.newBuilder(tokenEndpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(values))).build());
    }
    private JsonObject request(HttpRequest request)throws Exception{
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()){
            byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Spotify response too large");
            if(response.statusCode()!=200)throw new IOException(switch(response.statusCode()){
                case 401 -> "Spotify sign-in expired · Reconnect";case 403 -> "Spotify app access denied · Check its allowed users";
                case 429 -> "Spotify rate limit · Try again later";default -> "Spotify request failed ("+response.statusCode()+")";});
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private void tokens(JsonObject data,long run)throws IOException{
        if(run!=generation)return;
        accessToken=data.get("access_token").getAsString();if(data.has("refresh_token"))refreshToken=data.get("refresh_token").getAsString();
        expires=System.currentTimeMillis()+Math.clamp(data.get("expires_in").getAsLong(),1,86400)*1000;save();
    }
    private void save()throws IOException{
        var data=new JsonObject();data.addProperty("client_id",clientId);data.addProperty("refresh_token",refreshToken);
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,JSON.toJson(data));
        try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    private void publish(boolean busy,String status){snapshot=new Snapshot(clientId,!refreshToken.isEmpty() || !accessToken.isEmpty(),busy,status,playlists);}
    private static String message(Exception e){return e instanceof IOException && e.getMessage()!=null?e.getMessage():"Could not load Spotify · Try again";}
    private static String random(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static String decode(String value){return URLDecoder.decode(value,StandardCharsets.UTF_8);}
    private static String form(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    private void stopCallback(){var server=callback;callback=null;if(server!=null)server.stop(0);}
    @Override public void close(){++generation;stopCallback();worker.shutdownNow();http.close();}
}
