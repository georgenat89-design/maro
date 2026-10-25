package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.gui.PngFilePickerScreen;
import dev.maro.gui.spotify.SpotifyPhoneScreen;
import dev.maro.gui.widget.TextField;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.phone.PhoneNetwork;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local UI-only attachment checks: never connects, sends a message or uploads a video. */
final class PhonePickerChecks {
    private static final String CONTACT="1234567890",DRAFT="Unsent local picker draft";
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void set(Object target,String name,Object value){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void clickVideo(ClientGameTestContext context,SpotifyPhoneScreen screen){
        context.runOnClient(c->{var l=screen.layout();screen.mouseClicked(new Click(l.screenX(l.width()-83,l.height()-65),l.screenY(l.width()-83,l.height()-65),new MouseInput(0,0)),false);});
        context.waitTicks(2);require(context.computeOnClient(c->c.currentScreen instanceof PngFilePickerScreen),"Phone video action did not open an in-client picker");
    }
    static void run(ClientGameTestContext context,Path fixtures){
        var owner=ModuleManager.get(SpotifyPhone.class);
        Map<Setting<?>,JsonElement> saved=context.computeOnClient(c->{var result=new LinkedHashMap<Setting<?>,JsonElement>();for(var setting:owner.getSettings())result.put(setting,setting.toJson());return result;});
        var previous=context.computeOnClient(c->c.currentScreen);
        try(var network=new PhoneNetwork(fixtures.resolve("offline-phone-picker.json"))){
            Path file=fixtures.resolve("local-draft.mp4");Files.writeString(file,"Local attachment picker fixture; never sent or uploaded");
            SpotifyPhoneScreen phone=context.computeOnClient(c->{
                owner.mouseLook.set(false);owner.gameInteraction.set(false);owner.moveWhileOpen.set(false);owner.showHand.set(false);owner.device.set("Phone");
                var screen=new SpotifyPhoneScreen(owner,new SpotifyPhoneChecks.IsolatedPlayback(),network);c.setScreen(screen);
                Object apps=field(screen,"apps");Object messages=java.util.Arrays.stream(field(apps,"app").getClass().getEnumConstants()).filter(value->value.toString().equals("MESSAGES")).findFirst().orElseThrow();
                set(apps,"app",messages);set(apps,"peer",CONTACT);((TextField)field(apps,"compose")).setText(DRAFT);return screen;
            });
            context.waitTicks(10);
            clickVideo(context,phone);context.takeScreenshot("maro-phone-private-attachment-picker");
            context.runOnClient(c->{
                var picker=(PngFilePickerScreen)c.currentScreen;double[] p=picker.point(590,406);picker.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);
                require(c.currentScreen==phone,"Cancel replaced the phone session");require(!(Boolean)field(phone,"removed"),"Phone was disposed when its picker opened");
                Object apps=field(phone,"apps");require(!(Boolean)field(apps,"closed")&&CONTACT.equals(field(apps,"peer")),"Cancel lost the conversation");
                require(DRAFT.equals(((TextField)field(apps,"compose")).getText()),"Cancel lost the unsent draft");
            });
            context.waitTicks(3);clickVideo(context,phone);
            context.runOnClient(c->((PngFilePickerScreen)c.currentScreen).onFilesDropped(java.util.List.of(file)));
            for(int tick=0;tick<200&&context.computeOnClient(c->c.currentScreen instanceof PngFilePickerScreen);tick++)context.waitTick();
            context.runOnClient(c->{
                require(c.currentScreen==phone,"MP4 selection did not return to the same phone");Object apps=field(phone,"apps");
                require(file.toAbsolutePath().normalize().equals(field(apps,"videoDraft")),"MP4 selection did not populate the local draft");
                require(!(Boolean)field(apps,"closed")&&DRAFT.equals(((TextField)field(apps,"compose")).getText()),"Selection closed the apps or lost text");
            });
            context.waitTicks(3);clickVideo(context,phone);
            context.runOnClient(c->{c.setScreen(null);require((Boolean)field(phone,"removed")&&(Boolean)field(field(phone,"apps"),"closed"),"Interrupted picker leaked the suspended phone");});
            require(!network.snapshot().active()&&network.snapshot().messages().isEmpty(),"UI-only picker check activated transport or queued a message");
            System.out.println("[phone-picker] PASS: in-client cancel/select return to same conversation and draft; interruption closes resources; no transport activity");
        }catch(java.io.IOException error){throw new AssertionError(error);}
        finally{context.runOnClient(c->{c.setScreen(previous);saved.forEach(Setting::fromJson);});}
    }
}
