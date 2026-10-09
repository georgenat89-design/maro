package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonArray;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import java.util.*;

/** Drives real tab-list packet handling, including unlisted entries and repeated updates. */
final class StaffNotifierChecks {
    private static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    private static Setting<?> setting(StaffNotifier m,String name) { return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    static void tab(MinecraftClient client,UUID id,String name,boolean listed,boolean add) {
        var actions = EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LISTED,PlayerListS2CPacket.Action.UPDATE_LATENCY);
        if (add) actions.add(PlayerListS2CPacket.Action.ADD_PLAYER);
        var packet = new PlayerListS2CPacket(actions,List.of());
        var entry = new PlayerListS2CPacket.Entry(id,new GameProfile(id,name),listed,42,GameMode.SURVIVAL,
            Text.literal("[ADMIN] "+name),true,0,null);
        // The public constructor accepts server players. Populate the packet's entry list
        // in test code, then use the actual client handler instead of faking module state.
        try {
            var field = Arrays.stream(PlayerListS2CPacket.class.getDeclaredFields()).filter(f -> f.getType()==List.class).findFirst().orElseThrow();
            field.setAccessible(true); field.set(packet,List.of(entry));
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        client.getNetworkHandler().onPlayerList(packet);
    }
    static void run(ClientGameTestContext context) {
        StaffNotifier module = ModuleManager.get(StaffNotifier.class);
        require(module!=null,"Staff Notifier was not registered");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),d=UUID.randomUUID();
        try {
            context.runOnClient(client -> {
                require(module.isStaffName("DOUGH4")&&module.isStaffName("u_vv")&&module.isStaffName("cryptodaveyt"),"New staff defaults were missing");
                require(module.isStaffName("CaptainMoose35")&&module.isStaffName("owen1212055"),"CaptainMoose35 and Owen1212055 were not in the staff list");
                require(module.isStaffName("Splaterd")&&module.isStaffName("auzzitech")&&module.isStaffName("Zeef69"),"splaterd, auzzitech and zeef69 were not in the staff list");
                // A list saved before they were added gets them once; one saved since keeps them out if removed.
                var names=(SettingAdapters.ValueSetting)setting(module,"staff names");
                names.apply("Builder; frwost");
                var older=module.saveExtra();older.addProperty("staff-list-revision",2);
                var upgraded=new StaffNotifier();upgraded.loadExtra(older);
                String upgradedNames=setting(upgraded,"staff names").toJson().toString().toLowerCase(java.util.Locale.ROOT);
                require(upgradedNames.contains("captainmoose35")&&upgradedNames.contains("owen1212055")&&upgradedNames.contains("splaterd")
                        &&upgradedNames.contains("auzzitech")&&upgradedNames.contains("zeef69")&&upgradedNames.contains("builder"),
                    "An older saved staff list did not gain the new names: "+upgradedNames);
                var current=new StaffNotifier();current.loadExtra(module.saveExtra());
                String currentNames=setting(current,"staff names").toJson().toString().toLowerCase(java.util.Locale.ROOT);
                require(!currentNames.contains("captainmoose35")&&!currentNames.contains("splaterd")&&currentNames.contains("frwost"),"A current saved list had removed names put back: "+currentNames);
                module.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(module,"sound alerts")).set(false);
                ((BooleanSetting)setting(module,"notify existing")).set(false);
                module.setEnabled(true);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                tab(client,a,"SHOWERED",true,true);
                tab(client,b,"showeredFan",true,true);
                tab(client,c,"frwost",false,true);
                tab(client,d,"Builder",false,true);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(module.onlineStaff().size()==1 && module.onlineStaff().getFirst().name().equals("SHOWERED"),"Incorrect visible staff: "+module.onlineStaff()+" enabled="+module.isEnabled()+" listed="+client.getNetworkHandler().getListedPlayerListEntries().stream().map(e->e.getProfile().name()).toList());
                require(module.onlineStaff().getFirst().ping()==42,"Tab ping was not retained");
                require(module.hudStaff().stream().anyMatch(s->s.name().equals("frwost")&&!s.listed()),"Unlisted profile was not shown with its own status");
                require(module.recentChanges().size()==1 && module.recentChanges().getFirst().joined(),"Join was not tracked once");
                tab(client,a,"SHOWERED",true,false); tab(client,c,"frwost",true,false);
            });
            context.waitTicks(3);
            require(context.computeOnClient(client -> module.onlineStaff().size()==2 && module.recentChanges().size()==2),"Repeated tab update produced duplicate join alerts");
            context.takeScreenshot("maro-staff-notifier-online");
            context.runOnClient(client -> tab(client,a,"SHOWERED",false,false));
            context.waitTicks(3);
            require(context.computeOnClient(client -> module.onlineStaff().size()==1 && !module.recentChanges().getLast().joined()),"Removing a staff member from visible tab did not update state");
            context.runOnClient(client -> {
                var names=(SettingAdapters.ValueSetting)setting(module,"staff names");
                names.apply("Builder; builder; invalid name");
            });
            context.waitTicks(3);
            require(context.computeOnClient(client -> module.onlineStaff().isEmpty() && module.recentChanges().size()==3),"Editing staff names produced false leave alerts or retained old matches");
            context.runOnClient(client -> tab(client,d,"Builder",true,false));
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(module.onlineStaff().size()==1 && module.onlineStaff().getFirst().name().equals("Builder"),"Custom staff list was not applied live");
                StaffNotifier restored=new StaffNotifier(); restored.loadExtra(module.saveExtra());
                require(setting(restored,"staff names").toJson().equals(setting(module,"staff names").toJson()),"Custom staff list did not persist");
                module.hudResize(1); module.hudMove(10000,10000);
                require(module.hudLeft()+module.hudWidth()<=client.getWindow().getScaledWidth()+.5f && module.hudTop()+module.hudHeight()<=client.getWindow().getScaledHeight()+.5f,"Resized staff list escaped the screen");
                module.hudReset();
            });
            context.waitTicks(3); context.takeScreenshot("maro-staff-notifier-custom");
            context.runOnClient(client -> {
                var layout=(dev.maro.setting.ModeSetting)setting(module,"layout");layout.set("Compact");
            });
            context.waitTicks(3);context.takeScreenshot("maro-staff-notifier-compact");
            context.runOnClient(client -> client.getNetworkHandler().onPlayerRemove(new PlayerRemoveS2CPacket(List.of(d))));
            context.waitTicks(3);
            require(context.computeOnClient(client -> module.onlineStaff().isEmpty() && !module.recentChanges().getLast().joined()),"Player removal packet did not report staff leaving");
            context.takeScreenshot("maro-staff-notifier-empty");
            context.runOnClient(client -> { module.setEnabled(false); require(module.onlineStaff().isEmpty() && module.recentChanges().isEmpty(),"Disabled module retained stale staff state"); });
        } finally {
            context.runOnClient(client -> {
                module.setEnabled(false); module.getSettings().forEach(Setting::reset);
                if (client.getNetworkHandler()!=null) client.getNetworkHandler().onPlayerRemove(new PlayerRemoveS2CPacket(List.of(a,b,c,d)));
            });
        }
    }
}
