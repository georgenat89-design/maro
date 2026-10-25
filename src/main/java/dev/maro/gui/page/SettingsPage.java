package dev.maro.gui.page;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.MaroWordmark;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;

/** Client options (keybind, font, animations, sounds...). */
public class SettingsPage extends Page {
    private final SettingsList list = new SettingsList();
    private final String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    private long resetConfirm;

    public SettingsPage(ClickGuiScreen gui) {
        super(gui);
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        float lw = w - 6;
        if(h<210){
            float button=Math.min(98,(lw-18)/4);
            Widgets.button(gui,ctx,"save-compact",x,y,button,22,"Save now",Widgets.Style.PRIMARY,Icons.CHECK,ConfigManager::saveAll);
            Widgets.button(gui,ctx,"center-compact",x+button+6,y,button,22,"Center",Widgets.Style.SECONDARY,Icons.REFRESH,ClickGuiScreen::resetPosition);
            Widgets.button(gui,ctx,"accounts-compact",x+2*(button+6),y,button,22,"Accounts",Widgets.Style.SECONDARY,Icons.PLAYER,
                ()->net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.accounts.AccountsScreen(gui)));
            Widgets.button(gui,ctx,"reset-compact",x+3*(button+6),y,button,22,System.currentTimeMillis()-resetConfirm<3000?"Confirm reset":"Reset settings",Widgets.Style.DANGER,null,()->{
                if(System.currentTimeMillis()-resetConfirm<3000){for(var section:ClientSettings.ALL)section.getSettings().forEach(Setting::reset);resetConfirm=0;}
                else resetConfirm=System.currentTimeMillis();
            });
            Widgets.button(gui,ctx,"backgrounds-compact",x,y+29,lw-8,22,"Background Studio",Widgets.Style.SECONDARY,Icons.SETTINGS,
                ()->net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.hud.GuiBackgroundScreen(gui)));
            list.renderPaged(gui,ctx,x,y+58,lw,h-58,ClientSettings.GENERAL_PAGE,false);
            return;
        }
        gui.pushClip(x - 6, y, w + 12, h);
        float cy = y + 1;

        float r = Theme.radius();
        float ch = 96;
        Render2D.roundRect(ctx, x, cy, lw, ch, r, Theme.CARD);
        Render2D.roundGradientH(ctx, x, cy, lw, ch, r, Theme.accent(0x1C), Theme.accent2(0x00));
        Render2D.roundOutline(ctx, x, cy, lw, ch, r, 1f, Theme.BORDER);
        Icons.SETTINGS.draw(ctx, x + 16, cy + 15, 12, Theme.accent(), (System.currentTimeMillis() % 6000) / 6000f * 6f);
        MaroWordmark.draw(ctx,x+30,cy+6,10,Theme.TEXT,MaroWordmark.Style.THEMED,true);
        Fonts.draw(ctx, "v" + Maro.VERSION + "  •  Fabric " + mcVersion + "  •  Config: " + ConfigManager.getCurrent(),
                x + 30, cy + 22, Theme.TEXT_MUTED, false, 0.7f);

        float bx = x + 10, by = cy + 33, bh = 16;
        bx += Widgets.button(gui, ctx, "save", bx, by, 0, bh, "Save Now", Widgets.Style.PRIMARY, Icons.CHECK, () -> {
            ConfigManager.saveAll();
            Notifications.push("Saved", "Settings and config '" + ConfigManager.getCurrent() + "' saved", Notifications.Type.SUCCESS);
        }) + 5;
        bx += Widgets.button(gui, ctx, "resetPos", bx, by, 0, bh, "Center Window", Widgets.Style.SECONDARY, Icons.REFRESH,
                ClickGuiScreen::resetPosition) + 5;
        boolean confirming = System.currentTimeMillis() - resetConfirm < 3000;
        Widgets.button(gui, ctx, "resetAll", bx, by, 0, bh, confirming ? "Click to confirm" : "Reset Settings", Widgets.Style.DANGER, Icons.CLOSE, () -> {
            if (System.currentTimeMillis() - resetConfirm < 3000) {
                for (SettingSection s : ClientSettings.ALL) s.getSettings().forEach(Setting::reset);
                resetConfirm = 0;
                Notifications.push("Reset", "All client settings restored to defaults", Notifications.Type.WARNING);
            } else {
                resetConfirm = System.currentTimeMillis();
            }
        });
        Widgets.button(gui,ctx,"accounts",x+lw-78,cy+7,70,18,"Accounts",Widgets.Style.SECONDARY,Icons.PLAYER,
                () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.accounts.AccountsScreen(gui)));
        Widgets.button(gui,ctx,"background-studio",x+10,cy+65,140,22,"Background Studio",Widgets.Style.SECONDARY,Icons.SETTINGS,
                () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.hud.GuiBackgroundScreen(gui)));
        float previewX=x+160,previewY=cy+62,previewW=Math.max(30,lw-170);
        Render2D.checker(ctx,previewX,previewY,previewW,26,5);
        dev.maro.gui.render.GuiBackground.draw(ctx,previewX,previewY,previewW,26);
        Render2D.roundOutline(ctx,previewX,previewY,previewW,26,3,.8f,Theme.BORDER);
        cy += ch + 8;

        list.renderPaged(gui, ctx, x, cy, lw,h-105,ClientSettings.GENERAL_PAGE,true);
        gui.popClip();
    }
}
