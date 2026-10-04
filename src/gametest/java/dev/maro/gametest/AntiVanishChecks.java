package dev.maro.gametest;

import dev.maro.anubis.module.impl.misc.AntiVanishModule;
import dev.maro.anubis.util.antivanish.AntiVanishText;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.Text;
import java.util.*;

/** Exercise actual client packet hooks and client tick processing on the production jar. */
final class AntiVanishChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Setting<?> setting(dev.maro.module.Module m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    static void run(ClientGameTestContext context) {
        require(AntiVanishText.looksLikeLeaveMessage("§eFrenk_Btw left the game","Frenk_Btw"),"Leave-message normalization lost spaces");
        require(!AntiVanishText.looksLikeLeaveMessage("Frenk_BtwFan left the game","Frenk_Btw"),"Leave-message matching accepted a substring");
        var staff=ModuleManager.get(StaffNotifier.class);var anti=ModuleManager.get(AntiVanishModule.class);
        require(staff!=null&&anti!=null,"Staff modules not registered");
        UUID selected=UUID.randomUUID(),other=UUID.randomUUID();
        try {
            context.runOnClient(client->{
                staff.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(staff,"notify existing")).set(false);
                ((BooleanSetting)setting(staff,"sound alerts")).set(true);
                ((ModeSetting)setting(staff,"sound mode")).set("SelectedStaff");
                ((SettingAdapters.ValueSetting)setting(staff,"sound staff")).apply("frenk_btw");
                require(staff.shouldSoundFor("FRENK_BTW")&&!staff.shouldSoundFor("Napooo_"),"Selected staff sound filter was not applied");
                staff.setEnabled(true);
                anti.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(anti,"Sound alerts")).set(false);
                ((BooleanSetting)setting(anti,"Completion probe")).set(false);
                anti.setEnabled(true);
            });
            context.waitTicks(5);
            context.runOnClient(client->{
                StaffNotifierChecks.tab(client,selected,"Frenk_Btw",true,true);
                StaffNotifierChecks.tab(client,other,"Napooo_",true,true);
            });
            context.waitTicks(50);
            context.runOnClient(client->{
                require(staff.soundAlertsPlayed()==1,"Selected staff join did not produce exactly one coalesced sound: "+staff.soundAlertsPlayed());
                StaffNotifierChecks.tab(client,selected,"Frenk_Btw",false,false);
            });
            context.waitTicks(15);
            require(context.computeOnClient(client->AntiVanishModule.hudEntries().stream().anyMatch(e->e.name().equals("Frenk_Btw")&&e.reason().contains("hidden from TAB"))),"Hidden-tab update did not reach Anti Vanish: "+AntiVanishModule.hudEntries());
            context.waitTicks(90); // Let transient notifications fade before checking panel layout.
            context.takeScreenshot("maro-anti-vanish-tab-evidence");
            context.runOnClient(client->StaffNotifierChecks.tab(client,selected,"Frenk_Btw",true,false));
            context.waitTicks(25);
            require(context.computeOnClient(client->AntiVanishModule.hudEntries().stream().noneMatch(e->e.name().equals("Frenk_Btw"))),"Visible return did not clear hidden state");
            // A real translated departure followed by profile removal must suppress a vanish alert.
            context.runOnClient(client->{
                client.getNetworkHandler().onGameMessage(new GameMessageS2CPacket(Text.translatable("multiplayer.player.left",Text.literal("Frenk_Btw")),false));
                client.getNetworkHandler().onPlayerRemove(new PlayerRemoveS2CPacket(List.of(selected)));
            });
            context.waitTicks(30);
            require(context.computeOnClient(client->AntiVanishModule.hudEntries().stream().noneMatch(e->e.name().equals("Frenk_Btw"))),"Ordinary departure was misreported as vanish");
            // Repeated latency/list updates cannot replay join sounds; live edits take effect.
            context.runOnClient(client->{
                long before=staff.soundAlertsPlayed();StaffNotifierChecks.tab(client,other,"Napooo_",true,false);
                require(staff.soundAlertsPlayed()==before,"Latency update replayed join sound");
                ((SettingAdapters.ValueSetting)setting(staff,"sound staff")).apply("Napooo_");
                require(staff.shouldSoundFor("napooo_")&&!staff.shouldSoundFor("Frenk_Btw"),"Sound target changes were not applied live");
                var old=staff.saveExtra();old.remove("staff-list-revision");
                StaffNotifier restored=new StaffNotifier();restored.loadExtra(old);
                require(restored.isStaffName("Dough4")&&restored.isStaffName("u_vv"),"Old staff configuration did not migrate");
            });
        } finally {
            context.runOnClient(client->{
                anti.setEnabled(false);staff.setEnabled(false);
                anti.getSettings().forEach(Setting::reset);staff.getSettings().forEach(Setting::reset);
                if(client.getNetworkHandler()!=null)client.getNetworkHandler().onPlayerRemove(new PlayerRemoveS2CPacket(List.of(selected,other)));
            });
        }
    }
}
