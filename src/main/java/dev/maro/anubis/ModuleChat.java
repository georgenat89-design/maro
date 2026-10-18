package dev.maro.anubis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
public final class ModuleChat {
    public static Body body() { return new Body(); }
    public static void send(ClientModule owner,Body body) {
        var player=MinecraftClient.getInstance().player;
        if(player!=null) player.sendMessage(Text.literal("[Maro · "+owner.getName()+"] "+body),false);
    }
    public static final class Body {
        private final StringBuilder text=new StringBuilder();
        public Body text(Object value) { text.append(value);return this; }
        public Body value(Object value) { return text(value); }
        public Body name(Object value) { return text(value); }
        public Body warn(Object value) { return text(value); }
        public Body danger(Object value) { return text(value); }
        public Body good(Object value) { return text(value); }
        public String toString() { return text.toString(); }
    }
}
