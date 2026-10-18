package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.config.FriendManager;
import dev.maro.config.SharedModuleConfig;
import dev.maro.gui.hud.NameProtectScreen;
import dev.maro.gui.render.BootScene;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.NameProtect;
import dev.maro.module.impl.visuals.Compass;
import dev.maro.privacy.AliasSnapshot;
import dev.maro.gametest.mixin.PrivacyProbe;
import dev.maro.render.capture.CapturePass;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import java.util.*;

final class LivePrivacyChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static Setting<?> setting(Module m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static String raw(OrderedText text){var out=new StringBuilder();text.accept((i,s,cp)->{out.appendCodePoint(cp);return true;});return out.toString();}
    private static String chat(net.minecraft.client.gui.hud.ChatHud hud){var out=new StringBuilder();for(var line:((PrivacyProbe.Chat)hud).maro$visible())out.append(raw(line.content()));return out.toString();}
    private static final class Loading extends Screen {
        Loading(){super(Text.literal("Loading preview"));}
        @Override public void renderBackground(DrawContext c,int x,int y,float d){}
        @Override public void render(DrawContext c,int x,int y,float d){BootScene.loading(c,width,height,.62f,"Loading your world",1);}
    }
    @SuppressWarnings("unchecked")
    static void run(ClientGameTestContext context){
        var module=ModuleManager.get(NameProtect.class);var compass=ModuleManager.get(Compass.class);
        String savedOwn=module.ownAlias();var savedExtra=module.saveExtra();var savedSettings=SharedModuleConfig.capture(module);var savedCompass=SharedModuleConfig.capture(compass);
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var id=UUID.randomUUID();boolean friend=FriendManager.isFriend("AliasFriend");
        try{
            context.runOnClient(c->{
                module.loadExtra(new com.google.gson.JsonObject());module.getSettings().forEach(Setting::reset);
                var entries=((PrivacyProbe.Roster)c.getNetworkHandler()).maro$entries();entries.put(id,new PlayerListEntry(new GameProfile(id,"AliasFriend"),false));
                module.setEnabled(true);module.ownAlias("Streamer");module.alias("AliasFriend","Teammate");
                require(NameProtect.mask((String)null)==null&&NameProtect.mask((OrderedText)null)==null&&NameProtect.mask((StringVisitable)null)==null,"Nullable text not preserved");
                String self=c.player.getGameProfile().name();require(NameProtect.mask("<"+self+"> AliasFriend").equals("<Streamer> Teammate"),"Self/other aliases missing");
                require(NameProtect.mask("AliasFriendExtra aliasfriend").equals("AliasFriendExtra Teammate"),"Whole-name/case matching failed");
                var original=Text.literal("AliasFriend").formatted(Formatting.AQUA).append(Text.literal(" says hi").formatted(Formatting.YELLOW));
                var shown=NameProtect.mask(original.asOrderedText());require(raw(shown).equals("Teammate says hi"),"Styled text not aliased");
                int[] position={0};shown.accept((i,s,cp)->{require(s.getColor()!=null&&s.getColor().getRgb()==(position[0]<8?Formatting.AQUA.getColorValue():Formatting.YELLOW.getColorValue()),"Alias changed style at "+position[0]);position[0]+=Character.charCount(cp);return true;});
                int expected;try(var ignored=NameProtect.unprotected()){expected=c.textRenderer.getWidth("Teammate says hi");}
                require(c.textRenderer.getWidth(original)==expected&&c.textRenderer.getWidth(original.asOrderedText())==expected,"Alias layout differs from visible text");
                module.alias("AliasFriend","AliasOther");module.alias("AliasOther","Another");
                var lines=c.textRenderer.wrapLines(Text.literal("AliasFriend"),200);require(raw(lines.getFirst()).equals("AliasOther")&&raw(NameProtect.mask(lines.getFirst())).equals("AliasOther"),"Wrapped alias was remapped twice");
                var chatLines=net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(Text.literal("AliasFriend"),200,c.textRenderer);require(raw(NameProtect.mask(chatLines.getFirst())).equals("AliasOther"),"Chat alias was remapped twice");
                module.alias("AliasFriend","Teammate");module.alias("AliasOther","");
                c.inGameHud.getChatHud().addMessage(Text.literal("AliasFriend joined the game"));require(chat(c.inGameHud.getChatHud()).contains("Teammate joined"),"Existing chat not masked");
                module.alias("AliasFriend","LiveFriend");require(chat(c.inGameHud.getChatHud()).contains("LiveFriend joined"),"Chat did not update immediately");
                require(((PrivacyProbe.Chat)c.inGameHud.getChatHud()).maro$messages().stream().anyMatch(l->l.content().getString().equals("AliasFriend joined the game")),"Original chat message was changed");
                require(c.getNetworkHandler().getPlayerListEntry(id).getProfile().name().equals("AliasFriend")&&c.player.getGameProfile().name().equals(self),"Player identity mutated");
                var editor=(NameProtectScreen)module.panel(null);c.setScreen(editor);
                ((TextFieldWidget)field(editor,"self")).setText("LiveSelf");require(NameProtect.mask(self).equals("LiveSelf"),"Self editor required Apply/tick");
                ((TextFieldWidget)field(editor,"target")).setText("AliasFriend");((TextFieldWidget)field(editor,"alias")).setText("LiveTyped");require(NameProtect.mask("AliasFriend").equals("LiveTyped"),"Other-player editor not live");
                require(((TextFieldWidget)field(editor,"target")).getText().equals("AliasFriend"),"Input text changed");
                var publicConfig=SharedModuleConfig.capture(module).toString();require(!publicConfig.contains("LiveSelf")&&!publicConfig.contains("LiveTyped")&&!publicConfig.contains("AliasFriend"),"Private aliases leaked to public preset");
                dev.maro.render.capture.CaptureScopeChecks.during(()->require(NameProtect.mask(self).equals("LiveSelf"),"Privacy aliases disabled in clean view"));
            });context.waitTicks(5);context.takeScreenshot("maro-name-protect-live-editor");
            context.runOnClient(c->{
                module.alias("AliasFriend","");((BooleanSetting)setting(module,"Anonymous Players")).set(true);require(NameProtect.mask("AliasFriend").matches("Player \\d{3}"),"Automatic aliases missing");
                var before=NameProtect.mask("AliasFriend");module.onTick();require(before.equals(NameProtect.mask("AliasFriend")),"Automatic aliases changed on tick");
                FriendManager.add("AliasFriend");((BooleanSetting)setting(module,"Keep Friends")).set(true);require(NameProtect.mask("AliasFriend").equals("AliasFriend"),"Keep Friends ignored");
                module.alias("AliasFriend","Explicit");require(NameProtect.mask("AliasFriend").equals("Explicit"),"Explicit alias did not override friend exclusion");
                for(int i=0;i<4000;i++)NameProtect.mask("AliasFriend message "+i);require(module.cachedStrings()<=2048,"Alias cache unbounded");
                module.setEnabled(false);require(NameProtect.mask("AliasFriend").equals("AliasFriend")&&chat(c.inGameHud.getChatHud()).contains("AliasFriend joined"),"Disable did not restore original chat");c.setScreen(null);
            });
            for(int gui:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(gui);c.onResolutionChanged();((ModeSetting)setting(compass,"Style")).set("Dial");((NumberSetting)setting(compass,"Scale")).set(2.5d);compass.hudMove(10000,10000);compass.setEnabled(true);
                    require(compass.hudLeft()+compass.hudWidth()<=c.getWindow().getScaledWidth()&&compass.hudTop()+compass.hudHeight()<=c.getWindow().getScaledHeight(),"Compass bottom exceeds screen");
                });context.waitTicks(4);context.takeScreenshot("maro-compass-bottom-scale-"+gui);
            }
            context.runOnClient(c->{compass.setEnabled(false);c.setScreen(new Loading());});context.waitTicks(4);context.takeScreenshot("maro-loading-discord-invite");
            System.out.println("[live-privacy] PASS: immediate self/other edits, stable anonymous aliases/friends/overrides, style/width preservation, whole-name matching, one-pass wrapped aliases, original identities/messages/input retained, existing chat refresh/restore, bounded cache, private preset exclusion, clean-pass aliases, full Compass bottom at three GUI scales and loading Discord invite");
        }finally{context.runOnClient(c->{module.setEnabled(false);SharedModuleConfig.apply(module,savedSettings);module.ownAlias(savedOwn);module.loadExtra(savedExtra);((PrivacyProbe.Roster)c.getNetworkHandler()).maro$entries().remove(id);if(!friend)FriendManager.remove("AliasFriend");compass.setEnabled(false);SharedModuleConfig.apply(compass,savedCompass);c.setScreen(null);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
