package dev.maro.gametest;

import dev.maro.gui.MaroHomeScreen;
import dev.maro.gui.render.MaroTitleStyle;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.*;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.TranslatableTextContent;

/** Native menu actions and input stay inside one home page at every supported GUI scale. */
final class HomeNavigationChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Click click(double x,double y){return new Click(x,y,new MouseInput(0,0));}
    private static String key(ClickableWidget b){return b.getMessage().getContent() instanceof TranslatableTextContent text?text.getKey():b.getMessage().getString();}
    private static void nav(ClientGameTestContext context,String key){
        context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;var b=home.children().stream().filter(e->e instanceof ClickableWidget w&&MaroTitleStyle.key(w).equals(key)).map(e->(ClickableWidget)e).findFirst().orElseThrow();require(b.active,"Navigation disabled: "+key);require(home.mouseClicked(click(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d),false),"Navigation missed: "+key);});context.waitTicks(5);
    }
    private static void pageButton(ClientGameTestContext context,String key){context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;var b=home.page().children().stream().filter(e->e instanceof ClickableWidget w&&key(w).equals(key)).map(e->(ClickableWidget)e).findFirst().orElseThrow();var p=home.pagePoint(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d);require(home.mouseClicked(click(p[0],p[1]),false),"Embedded button missed: "+key);});context.waitTicks(4);}
    static void run(ClientGameTestContext context){
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());boolean warning=context.computeOnClient(c->c.options.skipMultiplayerWarning);
        try{
            for(int guiScale:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.skipMultiplayerWarning=true;c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();c.setScreen(new TitleScreen());});context.waitTicks(4);
                var home=context.computeOnClient(c->(MaroHomeScreen)c.currentScreen);
                context.takeScreenshot("maro-clean-home-scale-"+guiScale);
                context.runOnClient(c->{var first=home.children().stream().filter(e->e instanceof ClickableWidget w&&MaroTitleStyle.key(w).equals("menu.singleplayer")).map(e->(ClickableWidget)e).findFirst().orElseThrow();require(Math.abs(first.getX()+first.getWidth()/2f-home.width/2f)<=2,"Home actions are not centered");});
                nav(context,"menu.singleplayer");
                require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof SelectWorldScreen),"Centered primary button bypassed the native world selector");
                context.runOnClient(c->home.keyPressed(new KeyInput(256,0,0)));context.waitTicks(3);
                nav(context,"menu.singleplayer");require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof SelectWorldScreen),"Singleplayer replaced the home page");context.takeScreenshot("maro-home-worlds-scale-"+guiScale);
                if(guiScale==2){
                    pageButton(context,"selectWorld.create");
                    require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof net.minecraft.client.gui.screen.world.CreateWorldScreen),"Native Create World escaped the pane");
                    context.takeScreenshot("maro-home-create-world");
                    context.runOnClient(c->home.keyPressed(new KeyInput(256,0,0)));context.waitTicks(3);
                    require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof SelectWorldScreen),"Create World Back lost the home host");
                }
                nav(context,"menu.multiplayer");require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof MultiplayerScreen),"Multiplayer replaced the home page");
                context.runOnClient(c->{var multiplayer=(MultiplayerScreen)home.page();for(int i=0;i<20;i++)multiplayer.getServerList().add(new net.minecraft.client.network.ServerInfo("Local test "+i,"127.0.0.1:1",net.minecraft.client.network.ServerInfo.ServerType.OTHER),false);var list=multiplayer.children().stream().filter(e->e instanceof MultiplayerServerListWidget).map(e->(MultiplayerServerListWidget)e).findFirst().orElseThrow();list.setServers(multiplayer.getServerList());var p=home.pagePoint(list.getX()+list.getWidth()/2d,list.getY()+list.getHeight()/2d);double before=list.getScrollY();for(int i=0;i<10;i++)home.mouseScrolled(p[0],p[1],0,-1);require(list.getScrollY()>before,"Ten embedded server-list scrolls did not move the list");});context.waitTicks(3);context.takeScreenshot("maro-home-servers-scale-"+guiScale);
                pageButton(context,"selectServer.direct");require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof DirectConnectScreen),"Direct connect escaped the pane");
                context.runOnClient(c->{var field=home.page().children().stream().filter(e->e instanceof TextFieldWidget).map(e->(TextFieldWidget)e).findFirst().orElseThrow();field.setText("");var p=home.pagePoint(field.getX()+15,field.getY()+10);home.mouseClicked(click(p[0],p[1]),false);});context.getInput().typeChars("127.0.0.1");context.waitTicks(2);
                context.runOnClient(c->{var field=home.page().children().stream().filter(e->e instanceof TextFieldWidget).map(e->(TextFieldWidget)e).findFirst().orElseThrow();require(field.getText().equals("127.0.0.1"),"Embedded address input lost typing");home.keyPressed(new KeyInput(256,0,0));});context.waitTicks(3);require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof MultiplayerScreen),"Back did not restore server list in place");
                nav(context,"menu.options");require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof OptionsScreen),"Options replaced home");context.takeScreenshot("maro-home-options-scale-"+guiScale);
                nav(context,"maro.menu.accounts");require(context.computeOnClient(c->c.currentScreen==home&&home.page() instanceof dev.maro.gui.accounts.AccountsScreen),"Accounts replaced home");context.takeScreenshot("maro-home-accounts-scale-"+guiScale);
                context.runOnClient(c->{var pane=home.paneBounds();require(pane[0]>=0&&pane[1]>=0&&pane[2]<=home.width&&pane[3]<=home.height,"Embedded viewport left the screen");home.keyPressed(new KeyInput(256,0,0));});context.waitTicks(3);require(context.computeOnClient(c->c.currentScreen==home&&home.page()==null),"Escape replaced home instead of closing the pane");
            }
            System.out.println("[home-navigation] PASS: one persistent page for native worlds/servers/options/accounts, ten server-list wheel events, native Direct Connect text entry and nested Back/Escape, pane bounds and GUI scales 2/3/4");
        }finally{context.runOnClient(c->{c.options.skipMultiplayerWarning=warning;c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new TitleScreen());});}
    }
}
