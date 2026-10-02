package dev.maro.gui.page;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
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
        float off = scroll.update();
        float lw = w - 6;
        gui.pushClip(x - 6, y, w + 12, h);
        float cy = y + 1 - off;

        float r = Theme.radius();
        float ch = 56;
        Render2D.roundRect(ctx, x, cy, lw, ch, r, Theme.CARD);
        Render2D.roundGradientH(ctx, x, cy, lw, ch, r, Theme.accent(0x1C), Theme.accent2(0x00));
        Render2D.roundOutline(ctx, x, cy, lw, ch, r, 1f, Theme.BORDER);
        Icons.SETTINGS.draw(ctx, x + 16, cy + 15, 12, Theme.accent(), (System.currentTimeMillis() % 6000) / 6000f * 6f);
        Fonts.draw(ctx, Maro.NAME, x + 30, cy + 8, Theme.TEXT, true, 1f);
        String update = dev.maro.util.Updater.pendingBuild() > 0 ? "  •  Build " + dev.maro.util.Updater.pendingBuild() + " installs on restart" : "";
        Fonts.draw(ctx, "v" + Maro.VERSION + update + "  •  Fabric " + mcVersion + "  •  Config: " + ConfigManager.getCurrent(),
                x + 30, cy + 19, Theme.TEXT_MUTED, false, 0.7f);

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
        cy += ch + 8;

        float content = list.render(gui, ctx, x, cy, lw, ClientSettings.GENERAL_PAGE);
        gui.popClip();
        scroll.setBounds(ch + 8 + content + 4, h);
        scroll.drawBar(gui, ctx, x + w - 2, y, h);
    }
}
