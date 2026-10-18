package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.GameMenuScreen;

/** Client menus keep the integrated world live; Minecraft's own pause menu still pauses. */
final class GuiPauseChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void run(ClientGameTestContext context) {

        boolean spatial=ClientSettings.spatialMenu.get(),screen=ClientSettings.screenGui.get();
        try {
            require(ClientSettings.ALL.stream().flatMap(section->section.getSettings().stream()).noneMatch(s->s.getName().equals("Pause Game")),"Pause Game setting is still exposed");
            for(String layout:new String[]{"Window"})for(int backend=0;backend<3;backend++) {
                int mode=backend;
                context.runOnClient(c->{c.setScreen(null);
                    ClientSettings.spatialMenu.set(mode==2);
                    ClientSettings.screenGui.set(mode==1);c.setScreen(new ClickGuiScreen());});
                context.waitTicks(4);
                require(context.computeOnClient(c->!c.isPaused()&&!c.currentScreen.shouldPause()),"Client menu paused "+layout+" backend "+mode);
                long live=context.computeOnClient(c->c.world.getTime());context.waitTicks(4);
                require(context.computeOnClient(c->c.world.getTime())>live,"World did not tick with Pause off");
                context.runOnClient(c->c.currentScreen.close());context.waitTicks(8);
                require(context.computeOnClient(c->c.currentScreen==null&&!c.isPaused()),"Close animation failed to release the world");
            }
            context.runOnClient(c->{ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);

                c.setScreen(new CustomModelScreen(new ClickGuiScreen(),ModuleManager.get(CustomModel.class)));});
            context.waitTicks(4);require(context.computeOnClient(c->!c.isPaused()),"Module sub-screen paused the world");
            long live=context.computeOnClient(c->c.world.getTime());context.waitTicks(5);
            require(context.computeOnClient(c->c.world.getTime())>live,"Module sub-screen stopped world time");
            context.runOnClient(c->c.setScreen(new GameMenuScreen(true)));context.waitTicks(4);
            require(context.computeOnClient(c->c.isPaused()),"Maro changed the vanilla pause menu");
            System.out.println("[gui-pause] PASS: Pause Game removed; world time stays live across the window and 3 backends plus module sub-screen; animated close releases gameplay and vanilla pause still works");
        } finally {context.runOnClient(c->{c.setScreen(null);
            ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screen);});}
    }
}
