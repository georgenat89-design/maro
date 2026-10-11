package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.NameChange;
import dev.maro.module.impl.visuals.Nametags;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import dev.maro.gametest.mixin.ChatHudProbe;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Optional;

final class NameChangeChecks {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run(ClientGameTestContext context) {
        NameChange module = ModuleManager.get(NameChange.class);
        Nametags tags = ModuleManager.get(Nametags.class);
        require(module != null, "Name Change was not registered");
        try {
            context.runOnClient(c -> {
                String real = c.getSession().getUsername(); var id = c.player.getUuid();
                ((TextSetting) module.getSettings().stream().filter(s -> s.getName().equals("Name")).findFirst().orElseThrow()).set("NEON NATE");
                module.setEnabled(true);
                Text styled = c.player.getDisplayName();
                require(styled.getString().equals("NEON NATE"), "Vanilla display name was not replaced");
                var colors = new HashSet<Integer>();
                styled.visit((style, value) -> { if (style.getColor() != null) colors.add(style.getColor().getRgb()); return Optional.empty(); }, Style.EMPTY);
                require(colors.size() > 2, "Alias gradient was not styled per character");
                require(c.player.getName().getString().equals(real) && c.player.getUuid().equals(id)
                        && c.player.getGameProfile().name().equals(real), "Display alias changed the player's real identity");
                var entry = c.getNetworkHandler().getPlayerListEntry(id);
                require(c.inGameHud.getPlayerListHud().getPlayerName(entry).getString().equals("NEON NATE"), "Tab alias was not applied");
                require(NameChange.label(c.player).equals("NEON NATE"), "Maro Nametags alias was not applied");
                require(NameChange.chatText(Text.literal(real + "_Other " + real + "!")).getString().equals(real + "_Other NEON NATE!"), "Chat replacement matched part of another name");
                c.inGameHud.getChatHud().addMessage(Text.literal("<" + real + "> ready to jump"));
                var messages = ((ChatHudProbe) c.inGameHud.getChatHud()).maroTest$messages();
                require(messages.getFirst().content().getString().equals("<NEON NATE> ready to jump"), "Chat display hook was not applied");
                ((BooleanSetting) tags.getSettings().stream().filter(s -> s.getName().equals("Self")).findFirst().orElseThrow()).set(true);
                tags.setEnabled(true);
            });
            context.getInput().holdKey(o -> o.playerListKey);
            context.waitTicks(4); context.takeScreenshot("maro-name-change-gradient-tab");
            context.getInput().releaseKey(o -> o.playerListKey);
            context.runOnClient(c -> {
                module.setEnabled(false);
                require(c.player.getDisplayName().getString().equals(c.getSession().getUsername()), "Disabling Name Change did not restore the name");
                module.setEnabled(true);
                ((TextSetting) module.getSettings().stream().filter(s -> s.getName().equals("Name")).findFirst().orElseThrow()).set(" ");
                require(c.player.getDisplayName().getString().equals(c.getSession().getUsername()), "Blank alias hid the player's name");
            });
            System.out.println("[name-change] PASS: gradient alias, real profile unchanged, tab, chat hook, whole-name boundaries, Maro tags and disable/blank restore");
        } finally {
            context.getInput().releaseKey(o -> o.playerListKey);
            context.runOnClient(c -> {
                module.setEnabled(false); module.getSettings().forEach(s -> s.reset());
                tags.setEnabled(false); tags.getSettings().forEach(s -> s.reset());
            });
        }
    }
}
