package dev.maro.accounts;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Explicitly supplied sessions only. No launcher/browser credential discovery, export or token logging. */
public final class AccountManager {
    public record Account(UUID id,String name,long expires,boolean remembered,boolean refreshable) { }
    public record Snapshot(List<Account> accounts,boolean busy,String status,long revision) { }
    record Verified(ProfileResult profile,UserApiService api,UserApiService.UserProperties properties) { }
    interface Validator { Verified verify(String token) throws Exception; }
    interface Refresher { RefreshTokenClient.Tokens refresh(String token,String clientId) throws Exception; }
    private static AccountManager instance;
    public static AccountManager get(){
        if(instance==null) instance=new AccountManager(defaultValidator(),FabricLoader.getInstance().getGameDir().resolve("maro/accounts.json"));
        return instance;
    }
    private static Validator defaultValidator(){
        var profileClient=new SessionTokenClient();
        return token -> {
            GameProfile profile=profileClient.verify(token);
            var client=MinecraftClient.getInstance();
            var auth=new YggdrasilAuthenticationService(client.getNetworkProxy());
            var api=auth.createUserApiService(token);
            var properties=api.fetchProperties();
            // Signed skin/profile properties are public; this request never sends the access token.
            var signed=auth.createMinecraftSessionService().fetchProfile(profile.id(),true);
            if(signed==null || !signed.profile().id().equals(profile.id()))throw new IllegalArgumentException("Could not verify Minecraft profile. Try again");
            return new Verified(signed,api,properties);
        };
    }
    private static final class Entry {
        final UUID id;final String name,token,refresh,clientId;final long expires;final boolean remembered;
        Entry(UUID id,String name,String token,long expires,boolean remembered,String refresh,String clientId){this.id=id;this.name=name;this.token=token;this.expires=expires;this.remembered=remembered;this.refresh=refresh;this.clientId=clientId;}
        Account view(){return new Account(id,name,expires,remembered,!refresh.isEmpty());}
        AccountVault.Saved saved(){return new AccountVault.Saved(id,name,token,expires,refresh,clientId);}
        @Override public String toString(){return "Account["+name+"]";}
    }
    private final Validator validator;
    private final Refresher refresher;
    private final AccountVault vault;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {var thread=new Thread(r,"Maro account login");thread.setDaemon(true);return thread;});
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    private final AccountSessionAccess.State launcher;
    private boolean healthy=true;
    private Future<?> pending;
    private long request,revision;
    private volatile Snapshot snapshot=new Snapshot(List.of(),false,"Add a session to get started",0);
    AccountManager(Validator validator,Path file){
        this(validator,file,new RefreshTokenClient()::refresh);
    }
    AccountManager(Validator validator,Path file,Refresher refresher){
        this.validator=validator;this.refresher=refresher;this.vault=new AccountVault(file);
        var client=MinecraftClient.getInstance();launcher=((AccountSessionAccess)client).maro$captureAccount();
        try {for(var saved:vault.load())entries.put(saved.id(),new Entry(saved.id(),saved.name(),saved.token(),saved.expires(),true,saved.refresh(),saved.clientId()));publish(false,"Ready");}
        catch(Exception ignored){healthy=false;publish(false,"Could not open saved sessions on this Windows account");}
    }
    public Snapshot snapshot(){return snapshot;}
    public boolean canRemember(){return healthy&&AccountVault.supported();}
    public boolean canSwitch(){var client=MinecraftClient.getInstance();return client.world==null && client.getNetworkHandler()==null;}
    public String launcherName(){return launcher.session().getUsername();}
    private void publish(boolean busy,String status){snapshot=new Snapshot(entries.values().stream().map(Entry::view).toList(),busy,status,++revision);}
    private boolean guard(){if(!canSwitch()){publish(false,"Return to the title screen to switch accounts");return false;}return true;}
    public void addAndLogin(String input,boolean remember){
        if(!guard())return;
        final String token;
        try {token=SessionTokenClient.normalize(input);}
        catch(IllegalArgumentException error){publish(false,error.getMessage());return;}
        if(remember&&!canRemember()){publish(false,"Remembering sessions is unavailable on this PC");return;}
        authenticate(()->new RefreshTokenClient.Tokens(token,"","",SessionTokenClient.expiry(token)),remember,false);
    }
    public void addRefreshAndLogin(String input,String clientId,boolean remember){
        if(!guard())return;
        try {
            String refresh=RefreshTokenClient.normalize(input),id=RefreshTokenClient.clientId(clientId);
            if(remember&&!canRemember()){publish(false,"Remembering accounts is unavailable on this PC");return;}
            authenticate(()->refresher.refresh(refresh,id),remember,true);
        } catch(IllegalArgumentException error){publish(false,error.getMessage());}
    }
    public void login(UUID id){
        if(!guard())return;
        var entry=entries.get(id);if(entry==null)return;
        if(!entry.refresh.isEmpty()&&entry.expires<System.currentTimeMillis()+60_000){refresh(id);return;}
        authenticate(()->new RefreshTokenClient.Tokens(entry.token,entry.refresh,entry.clientId,entry.expires),entry.remembered,false);
    }
    public void refresh(UUID id){
        if(!guard())return;var entry=entries.get(id);
        if(entry==null||entry.refresh.isEmpty()){publish(false,"Add a refresh token to renew this account");return;}
        authenticate(()->refresher.refresh(entry.refresh,entry.clientId),entry.remembered,true,id);
    }
    private void authenticate(Callable<RefreshTokenClient.Tokens> credentials,boolean remember,boolean refreshing){
        authenticate(credentials,remember,refreshing,null);
    }
    private void authenticate(Callable<RefreshTokenClient.Tokens> credentials,boolean remember,boolean refreshing,UUID expected){
        cancel();long operation=++request;publish(true,refreshing?"Refreshing Microsoft session...":"Verifying session with Minecraft...");
        pending=worker.submit(() -> {
            try {
                var tokens=credentials.call();String token=tokens.access();
                var verified=validator.verify(token);
                if(expected!=null&&!verified.profile().profile().id().equals(expected))throw new IllegalArgumentException("Refresh returned a different account. Add it separately");
                MinecraftClient.getInstance().execute(() -> {
                    if(operation!=request)return;pending=null;
                    if(!guard())return;
                    var profile=verified.profile().profile();var next=new Entry(profile.id(),profile.name(),token,tokens.expires(),remember,tokens.refresh(),tokens.clientId());
                    var proposed=new LinkedHashMap<>(entries);proposed.put(next.id,next);
                    try {
                        var client=MinecraftClient.getInstance();var access=(AccountSessionAccess)client;
                        var previous=access.maro$captureAccount();
                        try {access.maro$installAccount(new Session(profile.name(),profile.id(),token,Optional.empty(),Optional.empty()),
                                verified.profile(),verified.api(),verified.properties());
                            if(healthy)vault.save(proposed.values().stream().filter(e -> e.remembered).map(Entry::saved).toList());}
                        catch(Exception failure){
                            var failed=access.maro$captureAccount();access.maro$applyAccount(previous);
                            if(failed.telemetry()!=previous.telemetry())failed.telemetry().close();throw failure;
                        }
                        if(previous.telemetry()!=launcher.telemetry())previous.telemetry().close();
                        entries.clear();entries.putAll(proposed);publish(false,"Signed in as "+profile.name());
                    } catch(Exception ignored){publish(false,"Could not switch or save this session. Try without Remember");}
                });
            } catch(Exception error){
                // Exception bodies, token payloads and Authorization headers never enter logs or UI.
                String message=error instanceof IllegalArgumentException ? error.getMessage() : "Could not reach Minecraft login services. Try again";
                MinecraftClient.getInstance().execute(() -> {if(operation==request){pending=null;publish(false,message);}});
            }
        });
    }
    public void cancel(){request++;if(pending!=null){pending.cancel(true);pending=null;}if(snapshot.busy())publish(false,"Login cancelled");}
    public void launcher(){
        if(!guard())return;cancel();var client=MinecraftClient.getInstance();var access=(AccountSessionAccess)client;var previous=access.maro$captureAccount();
        access.maro$applyAccount(launcher);if(previous.telemetry()!=launcher.telemetry())previous.telemetry().close();
        publish(false,"Restored launcher account: "+launcherName());
    }
    public void remove(UUID id){
        if(snapshot.busy())return;var proposed=new LinkedHashMap<>(entries);proposed.remove(id);
        try {if(healthy)vault.save(proposed.values().stream().filter(e -> e.remembered).map(Entry::saved).toList());
            entries.clear();entries.putAll(proposed);publish(false,"Removed saved account");}
        catch(Exception ignored){publish(false,"Could not remove the saved session");}
    }
}
