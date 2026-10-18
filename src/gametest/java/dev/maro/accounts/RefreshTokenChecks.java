package dev.maro.accounts;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.sun.net.httpserver.HttpServer;
import dev.maro.gui.accounts.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.screen.TitleScreen;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real four-hop HTTP, native account UI and DPAPI, using synthetic loopback credentials only. */
public final class RefreshTokenChecks {
    private static final String SECRET="Synthetic_Refresh_Only_Aa123",APP="11111111-2222-3333-4444-555555555555";
    private static final UUID ID=UUID.fromString("11111111-2222-3333-4444-666666666666");
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void finished(ClientGameTestContext c,AccountManager m){for(int i=0;i<100&&m.snapshot().busy();i++)c.waitTicks(2);require(!m.snapshot().busy(),"Refresh timed out");c.waitTicks(2);}
    private static void button(ClientGameTestContext context,String label){context.runOnClient(c->{
        var button=c.currentScreen.children().stream().filter(e->e instanceof ClickableWidget w&&w.getMessage().getString().equals(label)).map(e->(ClickableWidget)e).findFirst().orElseThrow();
        require(button.active,"Refresh UI control disabled");button.mouseClicked(new net.minecraft.client.gui.Click(button.getX()+2,button.getY()+2,new net.minecraft.client.input.MouseInput(0,0)),false);
    });}
    public static void run(ClientGameTestContext context){
        HttpServer server=null;Path dir=null;AccountManager manager=null;
        var original=context.computeOnClient(c->((AccountSessionAccess)c).maro$captureAccount());
        try {
            dir=Files.createTempDirectory("maro-refresh-check-");Path file=dir.resolve("accounts.json");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            AtomicInteger rotations=new AtomicInteger(),hops=new AtomicInteger();AtomicBoolean fail=new AtomicBoolean(),redirect=new AtomicBoolean();
            for(String endpoint:List.of("oauth","xbox","xsts","minecraft"))server.createContext("/"+endpoint,e->{
                String request=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);hops.incrementAndGet();String body;
                require(e.getRequestMethod().equals("POST"),"Refresh used a non-POST exchange");
                if(endpoint.equals("oauth")){
                    require(request.contains("grant_type=refresh_token")&&(request.contains("client_id="+APP)||request.contains("client_id=0000000000000001"))&&request.contains("refresh_token="),"OAuth grant/client/refresh missing");
                    if(request.contains("client_id=0000000000000001"))require(request.contains("redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf")&&URLDecoder.decode(request,StandardCharsets.UTF_8).contains("refresh_token="+SECRET+"*$$"),"Legacy refresh punctuation or redirect lost");
                    body="{\"access_token\":\"Synthetic_Microsoft_Access\",\"refresh_token\":\""+SECRET+"_"+rotations.incrementAndGet()+"\"}";
                }else if(endpoint.equals("xbox")){
                    var j=JsonParser.parseString(request).getAsJsonObject();require(j.getAsJsonObject("Properties").get("RpsTicket").getAsString().equals("d=Synthetic_Microsoft_Access"),"Wrong Xbox ticket");
                    body="{\"Token\":\"Synthetic_Xbox_User\"}";
                }else if(endpoint.equals("xsts")){
                    var j=JsonParser.parseString(request).getAsJsonObject();require(j.get("RelyingParty").getAsString().equals("rp://api.minecraftservices.com/")&&j.getAsJsonObject("Properties").get("SandboxId").getAsString().equals("RETAIL"),"Wrong XSTS audience");
                    body="{\"Token\":\"Synthetic_XSTS\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"123456\"}]}}";
                }else {
                    require(JsonParser.parseString(request).getAsJsonObject().get("identityToken").getAsString().equals("XBL3.0 x=123456;Synthetic_XSTS"),"Wrong Minecraft identity ticket");
                    body="{\"access_token\":\"Synthetic_Minecraft_Access_"+rotations.get()+"\",\"expires_in\":86400}";
                }
                if(redirect.get()){e.getResponseHeaders().add("Location","http://127.0.0.1:"+e.getLocalAddress().getPort()+"/minecraft");e.sendResponseHeaders(302,-1);e.close();return;}
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(fail.get()?400:200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}
            });
            server.start();var client=new RefreshTokenClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            AccountManager.Validator validator=token->{require(token.startsWith("Synthetic_Minecraft_Access_"),"Wrong final token verified");return new AccountManager.Verified(new ProfileResult(new GameProfile(ID,"Refresh_User"),Set.of()),UserApiService.OFFLINE,UserApiService.OFFLINE_PROPERTIES);};
            manager=context.computeOnClient(c->new AccountManager(validator,file,client::refresh));var m=manager;
            context.runOnClient(c->c.setScreen(new AccountsScreen(new TitleScreen(),m)));context.waitTicks(2);button(context,"Refresh Token");context.waitTicks(2);
            context.runOnClient(c->{
                require(c.currentScreen instanceof RefreshLoginScreen,"Refresh Token screen missing");
                for(var e:c.currentScreen.children())if(e instanceof TextFieldWidget f)f.setText(f instanceof TokenField?SECRET:APP);
            });context.waitTicks(2);context.takeScreenshot("maro-refresh-token-masked");button(context,"Refresh & Login");finished(context,m);
            require(hops.get()==4,"Refresh did not complete all four official exchange stages");
            context.runOnClient(c->require(c.getSession().getUuidOrNull().equals(ID)&&m.snapshot().accounts().getFirst().refreshable()&&m.snapshot().accounts().getFirst().remembered(),"Refresh login/default saving missing"));
            require(Files.exists(file)&&!Files.readString(file).contains(SECRET)&&!Files.readString(file).contains("Synthetic_Minecraft"),"Account secret saved in plaintext");
            var saved=new AccountVault(file).load();require(saved.getFirst().refresh().equals(SECRET+"_1")&&saved.getFirst().clientId().equals(APP),"Rotated refresh token or client ID lost");
            context.takeScreenshot("maro-refresh-account-saved");button(context,"Refresh");finished(context,m);
            require(new AccountVault(file).load().getFirst().refresh().equals(SECRET+"_2"),"Refresh button did not persist token rotation");
            var loaded=context.computeOnClient(c->new AccountManager(validator,file,client::refresh));
            require(loaded.snapshot().accounts().getFirst().refreshable(),"Reloaded account lost renewal support");
            var row=new AccountVault(file).load().getFirst();new AccountVault(file).save(List.of(new AccountVault.Saved(row.id(),row.name(),row.token(),0,row.refresh(),row.clientId())));
            var expired=context.computeOnClient(c->new AccountManager(validator,file,client::refresh));context.runOnClient(c->expired.login(ID));finished(context,expired);
            require(new AccountVault(file).load().getFirst().refresh().equals(SECRET+"_3"),"Expired saved account did not renew automatically on Login");
            String active=context.computeOnClient(c->c.getSession().getAccessToken());fail.set(true);context.runOnClient(c->m.refresh(ID));finished(context,m);
            require(context.computeOnClient(c->c.getSession().getAccessToken().equals(active)&&!m.snapshot().status().contains(SECRET)),"Rejected refresh changed active identity or exposed a secret");
            fail.set(false);redirect.set(true);int before=hops.get();try{client.refresh(SECRET,APP);throw new AssertionError("Credential redirect followed");}catch(IllegalArgumentException expected){}
            require(hops.get()==before+1,"Refresh followed HTTP redirect");redirect.set(false);
            context.runOnClient(c->{m.refresh(ID);m.cancel();});context.waitTicks(5);require(context.computeOnClient(c->c.getSession().getAccessToken().equals(active)),"Cancelled refresh applied stale identity");
            require(client.refresh(SECRET+"*$$","0000000000000001").clientId().equals("0000000000000001"),"Legacy public-client refresh failed");
            Path legacy=dir.resolve("legacy.json");var legacyRoot=new JsonObject();legacyRoot.addProperty("version",1);var legacyRows=new JsonArray();var legacyRow=new JsonObject();
            legacyRow.addProperty("id",ID.toString());legacyRow.addProperty("name","Legacy_User");legacyRow.addProperty("expires",0);
            legacyRow.addProperty("secret",Base64.getEncoder().encodeToString(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData("Legacy_Session_Only".getBytes(StandardCharsets.UTF_8))));
            legacyRows.add(legacyRow);legacyRoot.add("accounts",legacyRows);Files.writeString(legacy,new Gson().toJson(legacyRoot));
            require(new AccountVault(legacy).load().getFirst().token().equals("Legacy_Session_Only"),"Existing v1 account vault did not migrate");Files.delete(legacy);
            context.getInput().resizeWindow(854,480);context.runOnClient(c->c.setScreen(new RefreshLoginScreen(new TitleScreen(),m)));context.waitTicks(3);
            context.runOnClient(c->{for(var e:c.currentScreen.children())if(e instanceof ClickableWidget w)require(w.getY()>=0&&w.getBottom()<=c.currentScreen.height&&w.getRight()<=c.currentScreen.width,"Small refresh screen clips controls");});
            context.takeScreenshot("maro-refresh-token-small");context.getInput().resizeWindow(1280,720);
            context.runOnClient(c->{m.remove(ID);});require(!Files.exists(file),"Removing account retained saved credentials");
            System.out.println("[refresh-token] PASS: four-hop loopback exchange, modern/legacy public clients and opaque punctuation, masked native UI, save-by-default DPAPI, token rotation, v1/v2 vault reload, expired Login renewal, rejection/cancellation/redirect protection, small window and removal");
        }catch(Exception e){throw new AssertionError("Refresh fixture failed",e);}
        finally{
            if(server!=null)server.stop(0);AccountManager m=manager;
            context.runOnClient(c->{if(m!=null)m.cancel();((AccountSessionAccess)c).maro$applyAccount(original);c.setScreen(new TitleScreen());});
            try{if(dir!=null){Files.deleteIfExists(dir.resolve("accounts.json"));Files.deleteIfExists(dir);}}catch(Exception ignored){}
        }
    }
}
