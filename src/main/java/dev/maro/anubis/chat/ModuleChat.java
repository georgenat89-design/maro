package dev.maro.anubis.chat;
import dev.maro.anubis.module.ClientModule;
import net.minecraft.text.*;
/** Retains the recovered module's rich-text calls using Maro notifications. */
public final class ModuleChat {
    private ModuleChat() {}
    public static Body body(){return new Body();}
    public static void send(ClientModule module,Body body){module.announce(body.build());}
    public static final class Body {
        private final MutableText text=Text.empty();
        private Body colored(Object value,int color){text.append(Text.literal(String.valueOf(value)).styled(s->s.withColor(color)));return this;}
        public Body text(Object value){return colored(value,0xB7C4D6);}
        public Body name(Object value){return colored(value,0xECF2FF);}
        public Body value(Object value){return colored(value,0x8FCBE9);}
        public Body warn(Object value){return colored(value,0xFFC281);}
        public Body danger(Object value){return colored(value,0xFF899B);}
        public Body good(Object value){return colored(value,0x8FE2BA);}
        public Body append(Text value){text.append(value);return this;}
        public Text build(){return text.copy();}
    }
}
