package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.render.MaroTitleStyle;
import dev.maro.gui.render.BootAssets;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.screen.ScreenTexts;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.resource.ResourceReload;
import net.minecraft.text.Text;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Drives actual production splash overlays, vanilla buttons, resource reload and world entry. */
final class StartupChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Reload implements ResourceReload {
        final CompletableFuture<Void> done = new CompletableFuture<>();
        float progress;
        public CompletableFuture<?> whenComplete() { return done; }
        public float getProgress() { return progress; }
    }
    private static void click(ClientGameTestContext context, String key) {
        context.runOnClient(c -> {
            var button = c.currentScreen.children().stream().filter(e -> e instanceof ClickableWidget widget && MaroTitleStyle.key(widget).equals(key))
                    .map(e -> (ClickableWidget)e).findFirst().orElseThrow();
            require(button.active, "Native button disabled: " + key);
            require(button.mouseClicked(new Click(button.getX() + button.getWidth() / 2d,
                    button.getY() + button.getHeight() / 2d, new MouseInput(0, 0)), false), "Native click missed " + key);
        });
        context.waitTicks(4);
    }
    private static void connectionScreens(ClientGameTestContext context) {
        context.runOnClient(c -> {
            require(BootAssets.caps("Maro.gg").equals("MARO.GG"), "Brand is not uppercase");
            try {
                var constructor = ConnectScreen.class.getDeclaredConstructor(Screen.class, Text.class);
                constructor.setAccessible(true);
                c.setScreen(constructor.newInstance(new TitleScreen(), ScreenTexts.CONNECT_FAILED));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        context.waitTicks(4);
        context.takeScreenshot("maro-server-connecting");
        // Server resource reloads must remain visible above ConnectScreen (the original flashing case).
        var pack = new Reload(); pack.progress = .47f;
        context.runOnClient(c -> c.setOverlay(new SplashOverlay(c, pack, result -> { }, true)));
        context.waitTicks(10);
        context.takeScreenshot("maro-server-resource-reload");
        context.runOnClient(c -> pack.done.complete(null));
        context.waitTicks(35);
        require(context.computeOnClient(c -> c.getOverlay() == null && c.currentScreen instanceof ConnectScreen), "Server reload lost connection screen");
        context.runOnClient(c -> {
            var button = c.currentScreen.children().stream().filter(e -> e instanceof ClickableWidget).map(e -> (ClickableWidget)e).findFirst().orElseThrow();
            require(button.mouseClicked(new Click(button.getX()+button.getWidth()/2d, button.getY()+button.getHeight()/2d,
                    new MouseInput(0,0)), false), "Cancel missed");
            require(c.currentScreen instanceof TitleScreen, "Native Cancel callback changed");
        });
        context.waitTicks(3);
        context.takeScreenshot("maro-server-cancelled");
        context.runOnClient(c -> c.setScreen(new MessageScreen(Text.literal("Negotiating server configuration"))));
        context.waitTicks(3);
        context.takeScreenshot("maro-server-configuration");
        context.runOnClient(c -> c.setScreen(new TitleScreen()));
        // A real refused localhost connection must show the vanilla failure screen immediately.
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
        final String address = "127.0.0.1:" + port;
        context.runOnClient(c -> ConnectScreen.connect(c.currentScreen, c, ServerAddress.parse(address),
                new ServerInfo("Local failure test", address, ServerInfo.ServerType.OTHER), false, null));
        context.waitForScreen(DisconnectedScreen.class);
        context.takeScreenshot("maro-server-failure");
        context.runOnClient(c -> c.setScreen(new TitleScreen()));
        System.out.println("[startup] PASS: connection artwork, reload over connection, native Cancel and real localhost failure");
    }
    static void worldTransition(ClientGameTestContext context) {
        context.runOnClient(c -> c.setScreen(new MessageScreen(Text.literal("Entering your world"))));
        context.waitTicks(3);
        context.takeScreenshot("maro-world-handoff-start");
        context.setScreen(() -> null);
        for (int frame = 0; frame < 6; frame++) {
            context.waitTicks(1);
            context.takeScreenshot("maro-world-handoff-" + frame);
        }
        context.waitTicks(12);
        require(context.computeOnClient(c -> c.world != null && c.currentScreen == null && c.getOverlay() == null), "World fade blocked gameplay");
        System.out.println("[startup] PASS: loading-to-world fade releases gameplay");
    }
    static void dedicatedServer(ClientGameTestContext context) {
        // Dedicated server tests require the operator's existing EULA acceptance.
        var eula = new java.util.Properties();
        try (var file = java.nio.file.Files.newInputStream(java.nio.file.Path.of("eula.txt"))) { eula.load(file); }
        catch (java.io.IOException ignored) { }
        if (!Boolean.parseBoolean(eula.getProperty("eula", "false"))) {
            System.out.println("[startup] SKIP: dedicated server EULA not accepted; connection/reload/cancel/failure fixtures and integrated world tested");
            return;
        }
        var screens = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
        var observing = new java.util.concurrent.atomic.AtomicBoolean(true);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (observing.get()) screens.add(screen.getClass().getName());
        });
        context.setScreen(() -> null);
        try (var server = context.worldBuilder().createServer(); var connection = server.connect()) {
            connection.getClientWorld().waitForChunksRender();
            context.waitTicks(15);
            require(context.computeOnClient(c -> c.world != null && c.getNetworkHandler() != null && c.currentScreen == null), "Dedicated server handoff did not reach the world");
            context.takeScreenshot("maro-server-entered-world");
            RenderFaultChecks.assertClean();
            System.out.println("[startup] PASS: real dedicated localhost server joined; screens=" + screens);
        } finally { observing.set(false); }
    }
    static void run(ClientGameTestContext context) {
        int originalScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        try {
            for (int guiScale : new int[]{2, 3, 4}) {
                context.runOnClient(c -> {
                    c.options.getGuiScale().setValue(guiScale); c.onResolutionChanged();
                    c.setScreen(new TitleScreen());
                });
                context.waitTicks(8);
                context.runOnClient(c -> {
                    for (var child : c.currentScreen.children()) if (child instanceof ClickableWidget button)
                        require(button.getX() >= 0 && button.getY() >= 0 && button.getRight() <= c.currentScreen.width
                                && button.getBottom() <= c.currentScreen.height, "Title control outside viewport: " + button.getMessage().getString());
                });
                var titleShot = context.takeScreenshot("maro-title-scale-" + guiScale);
                if (guiScale == 2) try {
                    var area = context.computeOnClient(c -> {
                        var button = c.currentScreen.children().stream().filter(e -> e instanceof ClickableWidget w && MaroTitleStyle.key(w).equals("menu.singleplayer"))
                            .map(e -> (ClickableWidget)e).findFirst().orElseThrow();
                        double pixels = c.getWindow().getScaleFactor();
                        return new int[]{(int)((button.getX()+8)*pixels),(int)(button.getY()*pixels),
                            (int)((button.getRight()-28)*pixels),(int)(button.getBottom()*pixels)};
                    });
                    var image = javax.imageio.ImageIO.read(titleShot.toFile());
                    int top=image.getHeight(),bottom=-1;
                    for (int y=area[1]; y<area[3]; y++) for (int x=area[0]; x<area[2]; x++) {
                        int rgb=image.getRGB(x,y);
                        if ((rgb>>16&255)>210 && (rgb>>8&255)>210 && (rgb&255)>210) { top=Math.min(top,y); bottom=Math.max(bottom,y); }
                    }
                    int inkHeight=bottom-top+1;
                    // The current home buttons have one centered label, rather than a title
                    // and hint stacked in two halves. Measure the full row and its ink center.
                    int rowHeight=area[3]-area[1];
                    require(inkHeight>=Math.max(8,rowHeight*.2f)&&inkHeight<=rowHeight*.65f,
                            "Menu label is unreadable or overflows its row: "+inkHeight);
                    require(Math.abs((top+bottom+1)/2f-(area[1]+area[3])/2f)<=2,
                            "Menu label is not centered in its button");
                } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
            // Native-size font coverage must stay crisp at common and fractional canvas scales.
            for (int[] resolution : new int[][]{{1920,1080},{2560,1440},{1291,733}}) {
                context.getInput().resizeWindow(resolution[0],resolution[1]);
                context.runOnClient(c -> {c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new TitleScreen());});
                context.waitTicks(5);
                context.takeScreenshot("maro-sharp-title-"+resolution[0]);
                var sizedReload = new Reload(); sizedReload.progress=.58f;
                context.runOnClient(c -> c.setOverlay(new SplashOverlay(c,sizedReload,result -> {},false)));
                context.waitTicks(12);
                var sizedShot=context.takeScreenshot("maro-sharp-loading-"+resolution[0]);
                try {
                    var image=javax.imageio.ImageIO.read(sizedShot.toFile());
                    require(image.getWidth()==resolution[0] && image.getHeight()==resolution[1],"Resolution probe did not resize the framebuffer");
                    double scale=Math.min(image.getWidth()/960d,image.getHeight()/540d)*.9;
                    double ox=(image.getWidth()-960*scale)/2,oy=(image.getHeight()-540*scale)/2;
                    int left=(int)(ox+400*scale),right=(int)(ox+560*scale),top=(int)(oy+210*scale),bottom=(int)(oy+250*scale);
                    int white=0,gray=0;
                    for(int y=top;y<bottom;y++)for(int x=left;x<right;x++) {
                        int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                        if(r>225&&g>225&&b>225)white++;
                        else if(r>65&&Math.abs(r-g)<8&&Math.abs(g-b)<8)gray++;
                    }
                    require(white>100&&white>gray,"Small wordmark lost readable coverage at "+resolution[0]+": "+white+"/"+gray);
                }catch(java.io.IOException error){throw new AssertionError(error);}
                context.runOnClient(c -> sizedReload.done.complete(null));context.waitTicks(30);
            }
            context.getInput().resizeWindow(1280,720);
            context.runOnClient(c -> { c.options.getGuiScale().setValue(2); c.onResolutionChanged(); c.setScreen(new TitleScreen()); });
            click(context, "menu.options");
            require(context.computeOnClient(c -> c.currentScreen instanceof dev.maro.gui.MaroHomeScreen home && home.page() instanceof OptionsScreen), "Options callback changed");
            context.runOnClient(c -> c.setScreen(new TitleScreen()));
            click(context, "menu.singleplayer");
            require(context.computeOnClient(c -> c.currentScreen instanceof dev.maro.gui.MaroHomeScreen home && home.page() instanceof SelectWorldScreen), "Singleplayer callback changed");
            context.runOnClient(c -> c.setScreen(new TitleScreen()));
            context.runOnClient(c -> {
                System.out.println("[startup] Native title controls: " + c.currentScreen.children().stream()
                        .filter(e -> e instanceof ClickableWidget).map(e -> MaroTitleStyle.key((ClickableWidget)e)).toList());
            });

            HomeNavigationChecks.run(context);
            MenuSceneChecks.run(context);
            var reload = new Reload();
            var callbacks = new AtomicInteger();
            var failure = new AtomicReference<Throwable>();
            context.runOnClient(c -> c.setOverlay(new SplashOverlay(c, reload, result -> {
                callbacks.incrementAndGet(); result.ifPresent(failure::set);
            }, false)));
            context.waitTicks(7);
            context.takeScreenshot("maro-loading-empty");
            context.runOnClient(c -> reload.progress = .58f);
            context.waitTicks(12);
            context.takeScreenshot("maro-loading-middle");
            for (int frame = 0; frame < 12; frame++) {
                context.waitTicks(2);
                context.takeScreenshot("maro-loading-motion-" + frame);
            }
            context.runOnClient(c -> reload.progress = 1);
            context.waitTicks(12);
            context.takeScreenshot("maro-loading-full");
            context.runOnClient(c -> reload.done.complete(null));
            context.waitTicks(25);
            require(context.computeOnClient(c -> c.getOverlay() == null), "Completed splash did not dismiss");
            require(callbacks.get() == 1 && failure.get() == null, "Native success callback changed");

            var failed = new Reload();
            context.runOnClient(c -> c.setOverlay(new SplashOverlay(c, failed, result -> result.ifPresent(failure::set), false)));
            context.waitTicks(2);
            context.runOnClient(c -> failed.done.completeExceptionally(new IllegalStateException("startup test failure")));
            context.waitTicks(25);
            require(failure.get() != null, "Resource failure was swallowed by the splash");
            require(context.computeOnClient(c -> c.getOverlay() == null), "Failed splash remained stuck");

            var actualReload = new AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(c -> actualReload.set(c.reloadResources()));
            for (int tick = 0; tick < 400 && (!actualReload.get().isDone() || context.computeOnClient(c -> c.getOverlay() != null)); tick++) context.waitTicks(1);
            require(actualReload.get().isDone() && !actualReload.get().isCompletedExceptionally(), "Real resource reload failed");
            require(context.computeOnClient(c -> c.getOverlay() == null), "Real resource reload did not dismiss");
            context.waitTicks(5);
            context.takeScreenshot("maro-title-after-reload");
            context.runOnClient(c -> {
                var progress = new ProgressScreen(false);
                progress.setTitle(Text.literal("Preparing your world"));
                progress.progressStagePercentage(72);
                c.setScreen(progress);
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-world-preparation");
            context.runOnClient(c -> c.setScreen(new TitleScreen()));
            connectionScreens(context);
            RenderFaultChecks.assertClean();
            System.out.println("[startup] PASS: native title actions, scales 2/3/4, progress, success/failure callbacks and real texture reload");
        } finally {
            context.runOnClient(c -> { c.setOverlay(null); c.options.getGuiScale().setValue(originalScale); c.onResolutionChanged(); c.setScreen(new TitleScreen()); });
        }
    }
}
