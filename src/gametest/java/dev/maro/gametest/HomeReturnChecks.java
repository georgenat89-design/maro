package dev.maro.gametest;

import dev.maro.gui.MaroHomeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.*;
import net.minecraft.client.gui.screen.world.*;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** Return paths that start outside the home host, including real network failure and saved-world entry. */
final class HomeReturnChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static Click click(double x,double y){return new Click(x,y,new MouseInput(0,0));}
    private static void button(ClientGameTestContext context,String key){
        context.runOnClient(c->{
            Screen screen=c.currentScreen instanceof MaroHomeScreen home?home.page():c.currentScreen;
            var widget=screen.children().stream().filter(e->e instanceof ClickableWidget b&&b.getMessage().getContent() instanceof TranslatableTextContent t&&t.getKey().equals(key)).map(e->(ClickableWidget)e).findFirst().orElseThrow();
            require(widget.active,"Return button disabled: "+key);
            double x=widget.getX()+widget.getWidth()/2d,y=widget.getY()+widget.getHeight()/2d;
            if(c.currentScreen instanceof MaroHomeScreen home){var p=home.pagePoint(x,y);require(home.mouseClicked(click(p[0],p[1]),false),"Embedded return button missed");}
            else require(widget.mouseClicked(click(x,y),false),"Return button missed");
        });context.waitTicks(4);
    }
    private static WorldListWidget worlds(MaroHomeScreen home){return home.page().children().stream().filter(e->e instanceof WorldListWidget).map(e->(WorldListWidget)e).findFirst().orElseThrow();}
    static void run(ClientGameTestContext context,String worldName){
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        boolean warning=context.computeOnClient(c->c.options.skipMultiplayerWarning);
        try{
            for(int guiScale:new int[]{2,4}){
                var library=context.computeOnClient(c->{
                    c.options.skipMultiplayerWarning=true;c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();
                    c.setScreen(new ProgressScreen(false));var page=new MultiplayerScreen(new TitleScreen());c.setScreen(page);
                    require(c.currentScreen instanceof MaroHomeScreen home&&home.page()==page,"Standalone server-list return escaped Maro");return page;
                });context.waitTicks(5);
                var oldHost=context.computeOnClient(c->(MaroHomeScreen)c.currentScreen);
                context.runOnClient(c->{c.setScreen(new ProgressScreen(false));require(c.currentScreen instanceof ProgressScreen,"Loading was swallowed by home");c.setScreen(library);require(c.currentScreen instanceof MaroHomeScreen home&&home.page()==library,"Returning saved server screen became plain");});context.waitTicks(5);
                context.takeScreenshot("maro-home-server-return-"+guiScale);
                button(context,"selectServer.direct");button(context,"gui.cancel");
                require(context.computeOnClient(c->c.currentScreen instanceof MaroHomeScreen home&&home.page()==library),"Direct Connect cancel lost restored host");
                // The native library may still have an older Maro host as its Back target.
                var restoredHost=context.computeOnClient(c->c.currentScreen);
                context.runOnClient(c->c.setScreen(oldHost));context.waitTicks(3);
                require(context.computeOnClient(c->c.currentScreen==restoredHost&&c.currentScreen instanceof MaroHomeScreen home&&home.page()==null),"Old home parent opened a second home");
                context.runOnClient(c->{c.setScreen(new ProgressScreen(false));var page=new SelectWorldScreen(new TitleScreen());c.setScreen(page);require(c.currentScreen instanceof MaroHomeScreen home&&home.page()==page,"Standalone world-list return escaped Maro");});context.waitTicks(5);
                context.takeScreenshot("maro-home-world-return-"+guiScale);
            }
            var library=context.computeOnClient(c->{var page=new MultiplayerScreen(new TitleScreen());c.setScreen(page);return page;});context.waitTicks(4);
            // Use the real Cancel callback with the same parent used by MultiplayerScreen.connect.
            context.runOnClient(c->{try{var constructor=ConnectScreen.class.getDeclaredConstructor(Screen.class,Text.class);constructor.setAccessible(true);c.setScreen(constructor.newInstance(library,Text.literal("Connection failed")));}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            context.waitTicks(3);button(context,"gui.cancel");
            require(context.computeOnClient(c->c.currentScreen instanceof MaroHomeScreen home&&home.page()==library),"Connection Cancel returned to plain server list");
            int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}catch(java.io.IOException e){throw new AssertionError(e);}
            String address="127.0.0.1:"+port;
            context.runOnClient(c->library.connect(new ServerInfo("Return test",address,ServerInfo.ServerType.OTHER)));
            context.waitForScreen(DisconnectedScreen.class);context.takeScreenshot("maro-home-connection-error");
            button(context,"gui.toMenu");
            require(context.computeOnClient(c->c.world==null&&c.currentScreen instanceof MaroHomeScreen home&&home.page()==library),"Real network failure Back returned to plain server list");
            context.takeScreenshot("maro-home-after-failed-connection");
            // Reopen the actual saved fixture through the embedded native world selector and Play button.
            context.runOnClient(c->{c.setScreen(new ProgressScreen(false));c.setScreen(new SelectWorldScreen(new TitleScreen()));});
            context.waitFor(c->worlds((MaroHomeScreen)c.currentScreen).children().stream().anyMatch(e->e instanceof WorldListWidget.WorldEntry entry&&entry.getLevel().getName().equals(worldName)),300);
            context.runOnClient(c->{var list=worlds((MaroHomeScreen)c.currentScreen);list.setSelected(list.children().stream().filter(e->e instanceof WorldListWidget.WorldEntry entry&&entry.getLevel().getName().equals(worldName)).findFirst().orElseThrow());});context.waitTicks(3);
            button(context,"selectWorld.select");
            context.waitFor(c->c.world!=null&&c.player!=null&&c.currentScreen==null,600);
            long before=context.computeOnClient(c->c.world.getTime());context.waitTicks(40);
            require(context.computeOnClient(c->c.world!=null&&c.getNetworkHandler()!=null&&c.currentScreen==null&&c.world.getTime()>before),"World entry bounced back to a menu");
            context.takeScreenshot("maro-home-rejoined-world");
            // Fabric defers the lower disconnect overload inside a runOnClient task. Queue
            // the whole native Quit action so its cleanup and final screen keep their order.
            context.runOnClient(c->c.send(()->c.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT)));
            context.waitFor(c->c.world==null&&c.currentScreen instanceof MaroHomeScreen,100);
            require(context.computeOnClient(c->c.world==null&&c.currentScreen instanceof MaroHomeScreen),"Leaving real world lost Maro home: "+context.computeOnClient(c->"world="+c.world+" screen="+c.currentScreen));
            System.out.println("[home-return] PASS: native new/saved server and world list returns restore Maro at scales 2/4; nested Back, old home parent, connection Cancel and real refused-connection Back; actual saved world Play stays in gameplay and native disconnect returns home");
        }finally{context.runOnClient(c->{if(c.world!=null)c.disconnect(Text.translatable("menu.savingLevel"));c.options.skipMultiplayerWarning=warning;c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new TitleScreen());});}
    }
}
