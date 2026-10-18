package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.FakePay;
import dev.maro.gametest.mixin.FakePayOverlayAccessor;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Native command interception, real HUD/scoreboard rendering and outbound packet checks. */
public final class FakePayChecks {
    public static volatile boolean observe;
    public static volatile int commands;
    public static final CopyOnWriteArrayList<String> rendered = new CopyOnWriteArrayList<>();
    public static void outbound(Packet<?> packet) {
        if (observe && (packet instanceof CommandExecutionC2SPacket || packet instanceof ChatCommandSignedC2SPacket)) commands++;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Setting<?> setting(FakePay m, String name) { return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private static Text overlay(net.minecraft.client.MinecraftClient c) {
        return ((FakePayOverlayAccessor)c.inGameHud).maroTest$overlay();
    }
    static void run(ClientGameTestContext context) {
        FakePay m = ModuleManager.get(FakePay.class);
        require(m != null, "Fake Pay missing");
        ScoreboardObjective[] objective = {null}, old = {null};
        try {
            context.runOnClient(c -> {
                m.setEnabled(false); m.getSettings().forEach(Setting::reset); m.setEnabled(true);
                ((BooleanSetting)setting(m,"Sound")).set(false);
                require(!m.persistEnabled(), "Simulation should not restore itself from config");
                require(FakePay.parseAmount("$3,500.25").compareTo(new BigDecimal("3500.25")) == 0,"Grouped cents");
                require(FakePay.parseAmount("1.234567m").compareTo(new BigDecimal("1234567")) == 0,"Exact suffix arithmetic");
                for (String bad : new String[]{"NaN","Infinity","1e9","-1","0.001","1,2","$1$","1.2.3","1m;pay","1001t","9".repeat(49)})
                    require(FakePay.parseAmount(bad) == null,"Accepted invalid amount: " + bad);
                require(FakePay.formatAmount(new BigDecimal("999999.99"),false).equals("1M"),"Unit rounding carry");
                require(FakePay.formatAmount(new BigDecimal("1500000.01"),true).equals("1,500,000.01"),"Full decimal format");
                commands = 0; observe = true;
                c.getNetworkHandler().sendChatCommand("pay Notch 1.5m");
                require(m.lastMessage().getString().equals("You paid Notch $ 1.5M"),"Glazed style line: " + m.lastMessage().getString());
                require(overlay(c).getString().equals(m.lastMessage().getString()),"Action bar feedback missing");
                m.lastMessage().visit((style, fragment) -> {
                    require(style.getColor() != null && style.getColor().getRgb() == (fragment.equals("$") ? 0x55FF55 : 0xFFFFFF),"Payment text color");
                    return Optional.empty();
                },m.lastMessage().getStyle());
                String before = m.lastMessage().getString();
                for (String bad : new String[]{"pay","pay Steve","pay Steve NaN","pay Steve 0","pay Steve -1","pay Steve 1 extra","pay Bad! 1","pay Steve 0.001"})
                    c.getNetworkHandler().sendChatCommand(bad);
                require(m.lastMessage().getString().equals(before),"Invalid payment displayed as success");
                require(commands == 0,"A simulated or invalid /pay escaped to the server");
                require(!m.handlePay("payday Steve 1") && !m.handlePay("msg Steve hi"),"Unrelated commands intercepted");
                ((BooleanSetting)setting(m,"Adjust Sidebar Balance")).set(true);
                c.getNetworkHandler().sendChatCommand("essentials:pay .Bedrock 250k");
                require(m.spentOffset().compareTo(new BigDecimal("250000")) == 0,"Balance not recorded");
                MutableText row = Text.literal("Balance: ").formatted(Formatting.GRAY).append(Text.literal("$ ").formatted(Formatting.GREEN))
                    .append(Text.literal("1,").formatted(Formatting.WHITE)).append(Text.literal("000,000").formatted(Formatting.WHITE))
                    .append(Text.literal("  |  Kills: 12").formatted(Formatting.AQUA));
                Text changed = m.sidebarText(row);
                require(changed.getString().equals("Balance: $ 750k  |  Kills: 12"),"Styled split sidebar amount: " + changed.getString());
                require(row.getString().equals("Balance: $ 1,000,000  |  Kills: 12"),"Original sidebar mutated");
                require(m.sidebarText(Text.literal("Kills: 1200")).getString().equals("Kills: 1200"),"Non-money row changed");
                require(m.sidebarText(Text.literal("$ 10")).getString().equals("$ 0"),"Balance underflow");
                ((ModeSetting)setting(m,"Amount Style")).set("Full");
                c.getNetworkHandler().sendChatCommand("PAY Steve $3,500.25");
                require(m.lastMessage().getString().equals("You paid Steve $ 3,500.25"),"Full payment");
                ((ModeSetting)setting(m,"Amount Style")).set("Compact");
                ((BooleanSetting)setting(m,"Action Bar")).set(false);
                Text priorOverlay = overlay(c);
                c.getNetworkHandler().sendChatCommand("pay Steve 0.01");
                require(overlay(c) == priorOverlay,"Disabled action bar changed");
                BigDecimal spent = m.spentOffset();
                ((ButtonSetting)setting(m,"Preview")).press();
                require(m.spentOffset().equals(spent),"Preview charged local balance");
                ((ButtonSetting)setting(m,"Reset Balance")).press();
                require(m.spentOffset().signum() == 0,"Reset action failed");
                c.getNetworkHandler().sendChatCommand("pay Steve 250k");
                Scoreboard board = c.world.getScoreboard();
                old[0] = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                objective[0] = board.addObjective("maro_fakepay_test",ScoreboardCriterion.DUMMY,Text.literal("Payment Preview"),ScoreboardCriterion.RenderType.INTEGER,false,BlankNumberFormat.INSTANCE);
                board.getOrCreateScore(ScoreHolder.fromName("$ 1M"),objective[0]).setScore(2);
                board.getOrCreateScore(ScoreHolder.fromName("Kills: 12"),objective[0]).setScore(1);
                board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR,objective[0]);
                rendered.clear(); c.setScreen(new ChatScreen("",false));
            });
            context.waitTicks(4);
            require(rendered.contains("$ 750k"),"Native sidebar did not render the adjusted amount: " + rendered);
            require(rendered.contains("Kills: 12"),"Native sidebar lost unrelated row");
            context.takeScreenshot("maro-fake-pay-replacement");
            context.runOnClient(c -> {
                require(m.isEnabled(),"Simulation switched itself off");
                require(commands == 0,"Payment packet escaped");
                require(c.world.getScoreboard().getScoreboardEntries(objective[0]).stream().anyMatch(e -> e.owner().equals("$ 1M")),"Server sidebar entry changed");
                m.setEnabled(false);
                require(m.spentOffset().signum() == 0,"Disable did not reset balance");
                c.getNetworkHandler().sendChatCommand("pay Steve 1"); // Harmless unknown command in the integrated test world.
                require(commands == 1,"Disabled Fake Pay still intercepted /pay");
                m.setEnabled(true);
                c.getNetworkHandler().sendChatCommand("help");
                require(commands == 2,"Unrelated command did not reach native network path");
            });
        } finally {
            observe = false;
            context.runOnClient(c -> {
                m.setEnabled(false); m.getSettings().forEach(Setting::reset); c.setScreen(null);
                if (c.world != null && objective[0] != null) {
                    var board = c.world.getScoreboard(); board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR,old[0]); board.removeObjective(objective[0]);
                }
            });
        }
    }
}
