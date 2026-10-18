package dev.maro.config;

import com.google.gson.*;
import dev.maro.phone.PhoneNetwork;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** One worker, bounded HTTP replies, and a private identity file outside all shared presets. */
public final class CommunityConfigs implements AutoCloseable {
    public record Post(String id,String module,String title,String description,String author,String authorId,
                       String minecraft,String client,int settings,long created,int likes,boolean liked,boolean own,JsonObject config){}
    public record State(String endpoint,String status,boolean busy,List<Post> posts,boolean more){}
    private static CommunityConfigs instance;
    public static synchronized CommunityConfigs get(){
        if(instance==null)instance=new CommunityConfigs(ConfigManager.DIR.resolve("community-configs.json"),MinecraftClient.getInstance().getSession().getUsername());
        return instance;
    }
    public static synchronized void shutdown(){if(instance!=null){instance.close();instance=null;}}
    private final Path file;
    private final String author;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Maro public configs");t.setDaemon(true);return t;});
    private final JsonObject accounts=new JsonObject();
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private String endpoint="";
    private volatile State state=new State("","Add your community server to browse public configs",false,List.of(),false);
    public CommunityConfigs(Path file,String author){this.file=file;this.author=author;worker.execute(()->{load();ready.complete(null);});}
    public CompletableFuture<Void> ready(){return ready;}
    public State state(){return state;}
    private interface Request<T>{T run()throws Exception;}
    private <T>CompletableFuture<T> submit(Request<T> action){
        var result=new CompletableFuture<T>();
        state=new State(state.endpoint(),state.status(),true,state.posts(),state.more());
        worker.execute(()->{
            try{T value=action.run();state=new State(endpoint,state.status(),false,state.posts(),state.more());result.complete(value);}
            catch(Exception error){String message=error.getMessage()==null?"Community server unavailable":error.getMessage();
                state=new State(endpoint,message,false,state.posts(),state.more());result.completeExceptionally(error);}
        });return result;
    }
    public CompletableFuture<Void> connect(String url){
        String normalized;
        try{normalized=PhoneNetwork.relayUrl(url);}catch(IllegalArgumentException error){throw new IllegalArgumentException("Use an HTTPS server URL or local HTTP address");}
        return submit(()->{endpoint=normalized;save();state=new State(endpoint,"Connecting to community server",false,List.of(),false);
            JsonObject health=request("GET","/health",null,false);
            if(!health.has("api")||health.get("api").getAsInt()!=1||!health.has("ok")||!health.get("ok").getAsBoolean())throw new IOException("This URL is not a supported community server");
            state=new State(endpoint,"Connected server; choose a module",false,List.of(),false);return null;});
    }
    public CompletableFuture<List<Post>> browse(String module,String query,boolean top,boolean more){
        return submit(()->{
            if(endpoint.isEmpty())throw new IOException("Add your community server first");
            int offset=more?state.posts().size():0;
            JsonObject response=request("GET","/api/v1/posts?module="+encode(module)+"&q="+encode(query)+"&sort="+(top?"top":"new")+"&offset="+offset,null,false);
            List<Post> posts=new ArrayList<>(more?state.posts():List.of());
            for(var item:response.getAsJsonArray("posts"))posts.add(post(item.getAsJsonObject()));
            boolean next=response.has("next")&&!response.get("next").isJsonNull();
            state=new State(endpoint,posts.isEmpty()?"No public configs for this module yet":posts.size()+" public configs",false,List.copyOf(posts),next);
            return state.posts();
        });
    }
    public CompletableFuture<Post> detail(String id){return submit(()->post(request("GET","/api/v1/posts/"+id(id),null,false)));}
    public CompletableFuture<Post> publish(String title,String description,JsonObject config){
        JsonObject copy=config.deepCopy();
        return submit(()->{JsonObject data=new JsonObject();data.addProperty("title",title.strip());data.addProperty("description",description.strip());data.add("config",copy);
            return post(request("POST","/api/v1/posts",data,true));});
    }
    public CompletableFuture<Post> like(Post post,boolean liked){return submit(()->{JsonObject data=new JsonObject();data.addProperty("liked",liked);return post(request("PUT","/api/v1/posts/"+id(post.id())+"/like",data,true));});}
    public CompletableFuture<Void> delete(Post post){return submit(()->{request("DELETE","/api/v1/posts/"+id(post.id()),null,true);return null;});}
    private static String id(String id){if(!id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid config ID");return id;}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private String token(){return accounts.has(endpoint)?accounts.getAsJsonObject(endpoint).get("token").getAsString():"";}
    private void identity()throws Exception{
        if(!token().isEmpty())return;
        JsonObject data=new JsonObject();data.addProperty("name",author.length()>32?author.substring(0,32):author);
        JsonObject identity=request("POST","/api/v1/identity",data,false);
        if(!identity.has("token")||!identity.get("token").getAsString().matches("[A-Za-z0-9_-]{43}"))throw new IOException("Invalid community identity");
        accounts.add(endpoint,identity);save();
    }
    private JsonObject request(String method,String path,JsonObject data,boolean needsIdentity)throws Exception{
        if(endpoint.isEmpty())throw new IOException("Add your community server first");
        if(needsIdentity)identity();
        var builder=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(8)).header("Accept","application/json");
        if(!token().isEmpty())builder.header("Authorization","Bearer "+token());
        var body=data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(data.toString(),StandardCharsets.UTF_8);
        if(data!=null)builder.header("Content-Type","application/json");
        var response=http.send(builder.method(method,body).build(),info->new LimitedBody());
        JsonObject value;
        try{value=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("The server returned an invalid response");}
        if(response.statusCode()!=200)throw new IOException(value.has("error")?value.get("error").getAsString():"Community request failed ("+response.statusCode()+")");
        return value;
    }
    private static Post post(JsonObject p){
        JsonObject config=p.has("config")&&p.get("config").isJsonObject()?p.getAsJsonObject("config"):null;
        return new Post(id(p.get("id").getAsString()),p.get("module").getAsString(),p.get("title").getAsString(),p.get("description").getAsString(),
                p.get("author").getAsString(),p.get("authorId").getAsString(),p.get("minecraft").getAsString(),p.get("client").getAsString(),
                p.get("settingCount").getAsInt(),p.get("created").getAsLong(),p.get("likes").getAsInt(),p.get("liked").getAsBoolean(),p.get("own").getAsBoolean(),config);
    }
    private void load(){
        try{
            if(Files.isRegularFile(file)&&Files.size(file)<65536){
                JsonObject data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(data.has("endpoint")&&!data.get("endpoint").getAsString().isBlank())endpoint=PhoneNetwork.relayUrl(data.get("endpoint").getAsString());
                if(data.has("accounts"))for(var entry:data.getAsJsonObject("accounts").entrySet()){
                    String url=PhoneNetwork.relayUrl(entry.getKey());var account=entry.getValue().getAsJsonObject();
                    if(account.has("token")&&account.get("token").getAsString().matches("[A-Za-z0-9_-]{43}"))accounts.add(url,account);
                }
            }
        }catch(IOException|RuntimeException ignored){}
        state=new State(endpoint,endpoint.isEmpty()?"Add your community server to browse public configs":"Choose a module to browse public configs",false,List.of(),false);
    }
    private void save()throws IOException{
        Files.createDirectories(file.toAbsolutePath().getParent());JsonObject data=new JsonObject();data.addProperty("endpoint",endpoint);data.add("accounts",accounts);
        Path temporary=Files.createTempFile(file.toAbsolutePath().getParent(),"community-",".tmp");
        try{Files.writeString(temporary,data.toString());Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temporary);}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]>{
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(Long.MAX_VALUE);}
        public void onNext(List<ByteBuffer> buffers){
            for(ByteBuffer buffer:buffers){if(bytes.size()+buffer.remaining()>512*1024){subscription.cancel();body.completeExceptionally(new IOException("Community response exceeds 512 KB"));return;}
                byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}
        }
        public void onError(Throwable error){body.completeExceptionally(error);}
        public void onComplete(){body.complete(bytes.toByteArray());}
    }
    @Override public void close(){worker.shutdownNow();}
}
