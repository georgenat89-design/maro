package dev.maro.accounts;

import com.mojang.authlib.minecraft.UserApiService;
import com.sun.net.httpserver.HttpServer;
import dev.maro.gui.accounts.*;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.gui.render.MaroTitleStyle;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic loopback credentials only; exercises the real production session/UI/Windows vault. */
public final class AccountChecks {
    private static final String TOKEN="MaroFixture_AaBb123456789";
    private static final UUID ID=UUID.fromString("e63c684b-5437-4460-aa56-df8121923fa1");
    private static AccountManager manager;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(ClientGameTestContext context,String label) {
        context.runOnClient(c -> {
            var button=c.currentScreen.children().stream().filter(e -> e instanceof ClickableWidget w&&w.getMessage().getString().equalsIgnoreCase(label))
                    .map(e -> (ClickableWidget)e).findFirst().orElseThrow();
            require(button.active,"Account control unexpectedly disabled");
            require(button.mouseClicked(new Click(button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,new MouseInput(0,0)),false),"Native account click missed");
        });
    }
    private static void finished(ClientGameTestContext context) {
        for(int i=0;i<150&&context.computeOnClient(c -> manager.snapshot().busy());i++)context.waitTicks(2);
        require(!context.computeOnClient(c -> manager.snapshot().busy()),"Account operation timed out");
        context.waitTicks(3);
    }
    public static void run(ClientGameTestContext context) {
        HttpServer server=null;Path path=null;AccountSessionAccess.State original=context.computeOnClient(c -> ((AccountSessionAccess)c).maro$captureAccount());
        try {
            path=Files.createTempDirectory("maro-account-check-").resolve("accounts.json");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {var t=new Thread(r,"Maro loopback fixture");t.setDaemon(true);return t;}));
            AtomicBoolean header=new AtomicBoolean();
            server.createContext("/profile",exchange -> {
                String auth=exchange.getRequestHeaders().getFirst("Authorization");header.set(("Bearer "+TOKEN).equals(auth));
                int status=auth!=null&&auth.contains("Expired")?401:auth!=null&&auth.contains("Missing")?404:auth!=null&&auth.contains("Limited")?429:200;
                if(auth!=null&&auth.contains("Slow"))try{Thread.sleep(350);}catch(InterruptedException ignored){}
                byte[] body=(status==200?"{\"id\":\""+ID.toString().replace("-","")+"\",\"name\":\"MiXeD_User\"}":"{}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status,body.length);try(var out=exchange.getResponseBody()){out.write(body);}catch(java.io.IOException ignored){}
            });
            server.createContext("/redirect",exchange -> {exchange.getResponseHeaders().add("Location","http://127.0.0.1:"+exchange.getLocalAddress().getPort()+"/profile");exchange.sendResponseHeaders(302,-1);exchange.close();});
            server.start();var service=new SessionTokenClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/profile"));
            require(SessionTokenClient.normalize("Bearer "+TOKEN).equals(TOKEN),"Token case changed");
            require(SessionTokenClient.normalize("token:"+TOKEN+":"+ID).equals(TOKEN),"Native session ID rejected");
            require(service.verify(TOKEN).id().equals(ID)&&header.get(),"Authoritative profile or bearer header wrong");
            for(String suffix:List.of("Expired","Missing","Limited")) {
                try{service.verify(TOKEN+suffix);throw new AssertionError("Rejected token accepted");}catch(IllegalArgumentException expected){require(!expected.getMessage().contains(TOKEN),"Secret in error text");}
            }
            try{new SessionTokenClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/redirect")).verify(TOKEN);throw new AssertionError("Credential redirect followed");}catch(IllegalArgumentException expected){}
            Path file=path;
            context.runOnClient(c -> manager=new AccountManager(token -> new AccountManager.Verified(new com.mojang.authlib.yggdrasil.ProfileResult(service.verify(token),Set.of(com.mojang.authlib.yggdrasil.ProfileActionType.USING_BANNED_SKIN)),UserApiService.OFFLINE,UserApiService.OFFLINE_PROPERTIES),file));
            context.runOnClient(c -> c.setScreen(new TitleScreen()));context.waitTicks(3);
            context.runOnClient(c -> {
                var button=c.currentScreen.children().stream().filter(e -> e instanceof ClickableWidget w&&MaroTitleStyle.key(w).equals("maro.menu.accounts")).map(e -> (ClickableWidget)e).findFirst().orElseThrow();
                button.mouseClicked(new Click(button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,new MouseInput(0,0)),false);
                require(c.currentScreen instanceof AccountsScreen,"Main menu Accounts button missing");
                c.setScreen(new AccountsScreen(new TitleScreen(),manager));
            });
            context.waitTicks(3);context.takeScreenshot("maro-accounts-empty");click(context,"Add Session");context.waitTicks(3);
            context.getInput().typeChars(TOKEN);context.waitTicks(3);context.takeScreenshot("maro-session-masked");
            context.runOnClient(c -> {
                var field=c.currentScreen.children().stream().filter(e -> e instanceof TokenField).map(e -> (TokenField)e).findFirst().orElseThrow();
                require(field.getText().equals(TOKEN),"Native entry changed credential casing");
                try {for(String name:List.of("CACHE","WIDTHS")){var f=SmoothHudText.class.getDeclaredField(name);f.setAccessible(true);
                    for(Object key:((Map<?,?>)f.get(null)).keySet())require(!key.toString().toLowerCase(Locale.ROOT).contains("marofixture"),"Credential reached font cache");}}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            click(context,"Remember on this PC: On");click(context,"Login");finished(context);
            context.runOnClient(c -> {
                var next=((AccountSessionAccess)c).maro$captureAccount();
                require(c.getSession().getAccessToken().equals(TOKEN)&&c.getSession().getUsername().equals("MiXeD_User")&&c.getGameProfile().id().equals(ID),"Native identity not replaced");
                require(next.api()==UserApiService.OFFLINE&&next.properties().join()==UserApiService.OFFLINE_PROPERTIES,"Account properties not replaced");
                require(next.profile().join().actions().contains(com.mojang.authlib.yggdrasil.ProfileActionType.USING_BANNED_SKIN),"Signed profile actions lost during login");
                require(next.keys()!=original.keys()&&next.social()!=original.social()&&next.telemetry()!=original.telemetry()&&next.realms()!=original.realms()&&next.reports()!=original.reports(),"Account services retained old identity");
                require(manager.snapshot().accounts().size()==1,"Account not listed");
            });
            require(!Files.exists(file),"Temporary credential persisted");context.takeScreenshot("maro-accounts-signed-in");
            click(context,"Info");context.waitTicks(2);context.takeScreenshot("maro-account-info");
            context.runOnClient(c -> c.currentScreen.mouseClicked(new Click(1,1,new MouseInput(0,0)),false));
            context.runOnClient(c -> manager.addAndLogin(TOKEN,true));finished(context);
            require(Files.exists(file)&&!Files.readString(file).contains(TOKEN),"Remembered credential stored in plaintext");
            var saved=new AccountVault(file).load();require(saved.size()==1&&saved.getFirst().token().equals(TOKEN),"Windows encryption round trip failed");
            context.runOnClient(c -> {
                var loaded=new AccountManager(token -> {throw new IllegalStateException();},file);
                require(loaded.snapshot().accounts().size()==1&&loaded.snapshot().accounts().getFirst().id().equals(ID),"Remembered account did not reload");
            });
            require(context.computeOnClient(c -> manager.snapshot().accounts().size()==1&&manager.snapshot().accounts().getFirst().remembered()),"Duplicate login produced duplicate account");
            context.runOnClient(c -> manager.addAndLogin(TOKEN+"Expired",false));finished(context);
            context.runOnClient(c -> require(c.getSession().getAccessToken().equals(TOKEN)&&manager.snapshot().status().contains("expired"),"Rejected token changed active account"));
            context.runOnClient(c -> {manager.addAndLogin(TOKEN+"Slow",false);manager.cancel();});context.waitTicks(15);
            context.runOnClient(c -> require(c.getSession().getAccessToken().equals(TOKEN)&&!manager.snapshot().busy(),"Cancelled login applied stale result"));
            click(context,"Launcher");context.waitTicks(3);
            context.runOnClient(c -> require(((AccountSessionAccess)c).maro$captureAccount().equals(original),"Launcher services not restored"));
            click(context,"-");context.waitTicks(3);require(!Files.exists(file),"Removed credential remained on disk");
            context.getInput().resizeWindow(854,480);context.runOnClient(c -> c.setScreen(new AccountsScreen(new TitleScreen(),manager)));context.waitTicks(3);
            context.runOnClient(c -> {for(var child:c.currentScreen.children())if(child instanceof ClickableWidget w)require(w.getX()>=0&&w.getRight()<=c.currentScreen.width&&w.getY()>=0&&w.getBottom()<=c.currentScreen.height,"Small account screen clips controls");});
            context.takeScreenshot("maro-accounts-small");context.getInput().resizeWindow(1280,720);
            context.runOnClient(c -> c.setScreen(new TitleScreen()));context.waitTicks(3);context.takeScreenshot("maro-main-menu-accounts");
            System.out.println("[accounts] PASS: native UI/masking/cache privacy, authoritative profile, rejected/expired/cancelled sessions, deduplication, Windows encryption, deletion, complete launcher-state restore");
        } catch(Exception e){throw new AssertionError("Synthetic account checks failed",e);}
        finally {
            if(server!=null)server.stop(0);
            context.runOnClient(c -> {if(manager!=null){manager.cancel();manager.launcher();}c.setScreen(new TitleScreen());});
            try{if(path!=null){Files.deleteIfExists(path);Files.deleteIfExists(path.getParent());}}catch(Exception ignored){}
        }
    }
    public static void inWorld(ClientGameTestContext context) {
        context.runOnClient(c -> {
            var before=c.getSession();manager.addAndLogin(TOKEN,false);
            require(c.getSession()==before&&!manager.snapshot().busy()&&manager.snapshot().status().contains("title"),"Live world account switching allowed");
        });
    }
}
