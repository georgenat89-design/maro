package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.MaroHomeScreen;
import dev.maro.gui.render.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import java.net.URI;
import java.util.ArrayList;

final class MenuBrandingChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void run(ClientGameTestContext context){
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());String font=ClientSettings.fontFace.get();
        try{
            for(int size:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(size);c.onResolutionChanged();c.setScreen(new MaroHomeScreen());});context.getInput().setCursorPos(0,0);context.waitTicks(5);
                context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;var link=home.children().stream().filter(MenuCommunityButton.class::isInstance).map(MenuCommunityButton.class::cast).findFirst().orElseThrow();require(link.getX()>=0&&link.getY()>=0&&link.getRight()<=home.width&&link.getBottom()<=home.height,"Community link clipped at scale "+size);require(MenuCommunityButton.LINK.toString().equals("https://discord.gg/navo"),"Wrong community destination");require(ClientSettings.fontFace.get().equals(font),"Menu typography changed saved font");});
                context.takeScreenshot("maro-branding-home-"+size);
            }
            var opened=new ArrayList<URI>();
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new Screen(Text.literal("Community link input test")){
                @Override protected void init(){addDrawableChild(new MenuCommunityButton(width/2-92,height/2-15,184,31,opened::add));}
            });});context.waitTicks(4);
            context.runOnClient(c->{var screen=c.currentScreen;var link=(MenuCommunityButton)screen.children().getFirst();var click=new Click(link.getX()+10,link.getY()+10,new MouseInput(0,0));require(screen.mouseClicked(click,false),"Link missed native mouse input");screen.mouseReleased(click);screen.setFocused(link);require(screen.keyPressed(new KeyInput(257,0,0)),"Enter did not activate link");require(screen.keyPressed(new KeyInput(32,0,0)),"Space did not activate link");link.active=false;require(!link.keyPressed(new KeyInput(257,0,0)),"Inactive link accepted Enter");link.active=true;require(opened.size()==3&&opened.stream().allMatch(MenuCommunityButton.LINK::equals),"Link dispatched wrong destination/count");});context.waitTicks(3);context.takeScreenshot("maro-community-focused");
            System.out.println("[menu-branding] PASS: menu-only font leaves user preference intact, folded mark/link scales 2/3/4, native mouse/Enter/Space open exact navo URL, disabled link rejects input (browser opener captured)");
        }finally{context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new TitleScreen());});}
    }
}
