// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.tpa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.maro.anubis.ModuleChat;
import dev.maro.anubis.PacketReceiveEvent;
import dev.maro.anubis.ClientModule;
import dev.maro.module.Category;
import dev.maro.anubis.tpa.ServerText;
import dev.maro.anubis.tpa.TeleportConfirm;
import dev.maro.anubis.setting.ModeSetting;
import dev.maro.anubis.setting.NumberSetting;
import dev.maro.anubis.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(value=EnvType.CLIENT)
public final class TpMacroModule
extends ClientModule {
    private static final Pattern RATE_LIMIT = Pattern.compile("you need to wait another ([\\d.]+) seconds? to execute a command");
    private static final Pattern VALID_NAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");
    private final ModeSetting command = this.addSetting("command", new ModeSetting("Command", "tpa", "tpa", "tpahere"));
    private final StringSetting target = this.addSetting("player", new StringSetting("Player", "").hint("Player name"));
    private final NumberSetting interval = this.addSetting("interval", new NumberSetting("Interval (s)", 1.0f, 0.25f, 60.0f).step(0.25f));
    private final TeleportConfirm confirm = new TeleportConfirm(this);
    private volatile long holdUntil;
    private long nextAt;
    private int sent;

    public TpMacroModule() {
        super("tp-macro", "Tp Macro", "Sends /tpa or /tpahere to one player on a timer.", Category.MISC);
        this.usesNotifications();
        this.target.privateValue();
    }

    @Override public java.util.List<dev.maro.setting.SettingSection> getSettingSections() {
        return java.util.List.of(section("Teleport requests",command,target,interval));
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        String name = this.target.get().trim();
        if (!VALID_NAME.matcher(name).matches()) {
            ModuleChat.send((ClientModule)this, ModuleChat.body().warn("Set a valid Player name first."));
            this.requestDisable();
            return;
        }
        this.confirm.reset();
        this.holdUntil = 0L;
        this.nextAt = 0L;
        this.sent = 0;
        ModuleChat.send((ClientModule)this, ModuleChat.body().text("Sending ").value("/" + this.command.get() + " " + name).text(" every ").value(this.interval.formatted() + "s").text("."));
    }

    @Override
    protected void onDisable() {
        if (this.sent > 0) {
            ModuleChat.send((ClientModule)this, ModuleChat.body().text("Sent ").value(this.sent).text(this.sent == 1 ? " request." : " requests."));
        }
    }

    public void onPacket(PacketReceiveEvent event) {
        GameMessageS2CPacket chat;
        Packet<?> class_25962 = event.packet;
        if (!(class_25962 instanceof GameMessageS2CPacket) || (chat = (GameMessageS2CPacket)class_25962).content() == null) {
            return;
        }
        Matcher wait = RATE_LIMIT.matcher(ServerText.normalize(chat.content().getString()));
        if (!wait.find()) {
            return;
        }
        try {
            this.holdUntil = System.currentTimeMillis() + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 80L;
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.getNetworkHandler() == null || player.isDead()) {
            return;
        }
        this.confirm.tick(mc);
        long now = System.currentTimeMillis();
        if (now < this.nextAt || now < this.holdUntil) {
            return;
        }
        player.networkHandler.sendChatCommand(this.command.get() + " " + this.target.get().trim());
        ++this.sent;
        this.nextAt = now + (long)(this.interval.get() * 1000.0f);
    }
}
