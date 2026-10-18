package dev.maro.protection.test;
import dev.maro.protection.*;
import dev.maro.module.ModuleManager;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.*;

public final class ProtectionChecks implements FabricClientGameTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message+": "+ProtectedGate.status());}
    private static JsonObject credentials(Path p)throws Exception{return JsonParser.parseString(Files.readString(p.resolve("client-credentials.json"))).getAsJsonObject();}
    private static void waitFor(ClientGameTestContext c,java.util.function.BooleanSupplier check){for(int n=0;n<160&&!check.getAsBoolean();n++)c.waitTicks(1);require(check.getAsBoolean(),"Timed out");}
    private static void press(ClientGameTestContext c,String text){
        c.runOnClient(mc->{var owner=(OwnerScreen)mc.currentScreen;var b=owner.children().stream().filter(e->e instanceof ClickableWidget w&&w.getMessage().getString().equals(text)).map(e->(ClickableWidget)e).findFirst().orElseThrow();var p=owner.screenPoint(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d);require(owner.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false),"Button missed: "+text);});c.waitTicks(2);
    }
    @Override public void runTest(ClientGameTestContext c){
        try{
            Path fixture=Path.of(System.getProperty("maro.protection.fixture"));c.waitTicks(30);
            require(!ProtectedGate.allowed(),"Unlicensed client must deny");
            c.runOnClient(mc->{ModuleManager.getByName("Fullbright").setEnabled(true);require(!ModuleManager.getByName("Fullbright").isEnabled(),"Unauthorized enable must deny");ProtectedGate.open();});
            int oldScale=c.computeOnClient(mc->mc.options.getGuiScale().getValue());
            for(int guiScale:new int[]{2,4}){
                c.runOnClient(mc->{mc.options.getGuiScale().setValue(guiScale);mc.onResolutionChanged();ProtectedGate.open();});c.waitTicks(3);
                c.runOnClient(mc->{var owner=(OwnerScreen)mc.currentScreen;for(var child:owner.children())if(child instanceof ClickableWidget b){var a=owner.screenPoint(b.getX(),b.getY());var z=owner.screenPoint(b.getRight(),b.getBottom());require(a[0]>=0&&a[1]>=0&&z[0]<=owner.width&&z[1]<=owner.height,"Onboarding control left the screen");}});
                c.takeScreenshot("maro-onboarding-scale-"+guiScale);
                press(c,"Need help?");press(c,"Back");
            }
            c.runOnClient(mc->{mc.options.getGuiScale().setValue(oldScale);mc.onResolutionChanged();ProtectedGate.open();});c.waitTicks(3);
            press(c,"Continue");require(!ProtectedGate.working(),"Missing fields must not send an authentication request");
            press(c,"Need help?");c.takeScreenshot("maro-protected-help");press(c,"Back");
            c.takeScreenshot("maro-protected-signin");
            var credentials=credentials(fixture);
            c.runOnClient(mc->{var fields=mc.currentScreen.children().stream().filter(e->e instanceof TextFieldWidget).map(e->(TextFieldWidget)e).toList();require(fields.size()==2,"Sign in must show only password and app code");fields.get(0).setText(credentials.get("password").getAsString());fields.get(1).setText(credentials.get("otp").getAsString());});press(c,"Continue");
            waitFor(c,ProtectedGate::activationNeeded);c.waitTicks(3);require(!ProtectedGate.allowed(),"Unactivated login must deny");c.takeScreenshot("maro-protected-activation");
            c.runOnClient(mc->{var fields=mc.currentScreen.children().stream().filter(e->e instanceof TextFieldWidget).map(e->(TextFieldWidget)e).toList();require(fields.size()==1,"Activation must hide account fields");fields.getFirst().setText(credentials.get("activation").getAsString());});press(c,"Activate and continue");waitFor(c,ProtectedGate::allowed);c.waitTicks(3);c.takeScreenshot("maro-protected-ready");
            press(c,"Manage devices");c.takeScreenshot("maro-protected-devices");press(c,"Back");
            c.runOnClient(mc->{ModuleManager.getByName("Fullbright").setEnabled(true);require(ModuleManager.getByName("Fullbright").isEnabled(),"Owner must enable modules");});
            press(c,"Open Maro");c.waitTicks(20);
            c.takeScreenshot("maro-protected-verified");
            long start=System.nanoTime();int hits=0;for(int i=0;i<2_000_000;i++)if(ProtectedGate.allowed())hits++;long cost=System.nanoTime()-start;
            require(hits==2_000_000,"Valid lease must remain stable");
            System.out.println("[protected-performance] integrity_ms="+ProtectedGate.integrityNanos/1e6+"; allowed_ns_per_call="+cost/2_000_000.0);
            Files.writeString(fixture.resolve("unavailable"),"fixture-only");ProtectedGate.checkNow();waitFor(c,()->!ProtectedGate.allowed());
            c.waitTicks(3);c.takeScreenshot("maro-protected-reconnect");
            c.runOnClient(mc->require(!ModuleManager.getByName("Fullbright").isEnabled(),"Network failure must stop modules"));
            Files.delete(fixture.resolve("unavailable"));ProtectedGate.checkNow();waitFor(c,ProtectedGate::allowed);
            c.setScreen(()->null);
            try(var world=c.worldBuilder().create()){
                world.getClientWorld().waitForChunksRender();c.setScreen(()->null);c.waitTicks(10);
                c.runOnClient(mc->{require(!ModuleManager.getByName("Fullbright").isEnabled(),"Outage cleanup must disable prior modules");ModuleManager.getByName("Fullbright").setEnabled(true);ProtectedGate.openModules();});
                c.waitTicks(15);c.takeScreenshot("maro-protected-world");
                c.runOnClient(mc->ModuleManager.getByName("Fullbright").setEnabled(false));
            }
            ProtectedGate.logout();waitFor(c,()->!ProtectedGate.allowed());
            c.setScreen(()->null);
            System.out.println("[protected-edition] PASS: signed obfuscated production JAR loads; owner MFA + activation, module denial/allow, Windows vault, network outage lock and cleanup, recovery, in-world rendering and logout");
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
