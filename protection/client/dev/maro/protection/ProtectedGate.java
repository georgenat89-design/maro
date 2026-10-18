package dev.maro.protection;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.util.concurrent.*;

/** Supporting client enforcement; the licensing service is the authorization boundary. */
public final class ProtectedGate {
    private static volatile LicenseClient client;
    private static volatile String message="Initializing protected edition";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"maro-license");t.setDaemon(true);return t;});
    private static volatile boolean busy;private static volatile boolean integrityValid;private static long next;private static boolean shown,wasAllowed;
    public static volatile long integrityNanos;
    public static boolean allowed(){return client!=null&&client.allowed();}
    public static String status(){return client==null?message:client.status;}
    public static boolean working(){return busy;}
    public static boolean activationNeeded(){return client!=null&&client.activationNeeded();}
    public static boolean testBuild(){return client!=null&&client.testBuild();}
    public static boolean hasSavedSession(){return client!=null&&client.hasSavedSession();}
    public static void start(){
        run(()->{long t=System.nanoTime();ReleaseIntegrity.verify();integrityNanos=System.nanoTime()-t;integrityValid=true;client=new LicenseClient();client.renew();});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            long t=System.nanoTime();if(!busy&&client!=null&&t>=next){next=t+TimeUnit.SECONDS.toNanos(30);run(()->client.renew());}
            boolean active=allowed();
            if(wasAllowed&&!active)for(var module:dev.maro.module.ModuleManager.all()){
                try{module.setEnabled(false);}catch(RuntimeException ignored){/* state is already disabled; continue cleanup */}
            }
            wasAllowed=active;
            if(!allowed()&&mc.currentScreen instanceof dev.maro.gui.ClickGuiScreen)mc.setScreen(new OwnerScreen());
            if(!shown&&mc.currentScreen!=null&&mc.world==null){shown=true;mc.setScreen(new OwnerScreen());}
        });
    }
    public interface Action {void run()throws Exception;}
    public static void run(Action action){if(busy)return;busy=true;IO.execute(()->{try{action.run();}catch(SecurityException e){setMessage(e.getMessage());}catch(Exception e){setMessage(e instanceof java.io.IOException?"Licensing service unreachable; protected features locked":"Secure credential or licensing operation failed");}finally{busy=false;}});}
    private static void setMessage(String s){if(client==null)message=s;else client.status=s;}
    public static void login(String password,String otp){run(()->{if(!integrityValid)throw new SecurityException("Protected artifact integrity check failed");if(client==null)client=new LicenseClient();client.login(password,otp);});}
    public static void activate(String key){run(()->{if(client==null)throw new SecurityException("Sign in first");client.activate(key);});}
    public static void logout(){run(()->{try{if(client!=null)client.logout();}finally{client=null;message="Signed out";}});}
    public static void reset(String password,String otp){run(()->{if(client!=null)client.resetDevices(password,otp);});}
    public static void open(){MinecraftClient.getInstance().setScreen(new OwnerScreen());}
    public static void checkNow(){run(()->{if(client!=null)client.renew();});}
    public static void openModules(){var mc=MinecraftClient.getInstance();mc.setScreen(allowed()?new dev.maro.gui.ClickGuiScreen():new OwnerScreen());}
    public static void openGuide(){run(()->{
        try(var stream=ProtectedGate.class.getResourceAsStream("/maro-start-here.html")){
            if(stream==null)throw new SecurityException("Setup guide is missing from this copy");
            var folder=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("maro-protected/help");java.nio.file.Files.createDirectories(folder);
            var guide=folder.resolve("start-here.html");java.nio.file.Files.write(guide,stream.readAllBytes());net.minecraft.util.Util.getOperatingSystem().open(guide);
        }
    });}
}
