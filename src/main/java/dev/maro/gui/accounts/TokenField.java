package dev.maro.gui.accounts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
/** Masking happens before measurement and rendering, so font caches never receive credentials. */
public final class TokenField extends TextFieldWidget {
    public TokenField(TextRenderer font,int x,int y,int width){super(font,x,y,width,24,Text.literal("Login token"));setMaxLength(16384);}
    public static String mask(String text){return "*".repeat(text.length());}
    @Override public void appendClickableNarrations(NarrationMessageBuilder builder){builder.put(NarrationPart.TITLE,Text.literal("Login token, hidden"));}
}
