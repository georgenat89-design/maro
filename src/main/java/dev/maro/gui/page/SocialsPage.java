package dev.maro.gui.page;

import dev.maro.config.FriendManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.TextField;
import dev.maro.gui.widget.Widgets;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Friend list management, with quick-add from the current server's player list. */
public class SocialsPage extends Page {
    private final TextField name;

    public SocialsPage(ClickGuiScreen gui) {
        super(gui);
        name = new TextField("Add a friend by username...", 16)
                .filter(c -> Character.isLetterOrDigit(c) || c == '_')
                .onEnter(this::addTyped);
    }

    private void addTyped() {
        String n = name.getText().trim();
        if (!FriendManager.isValidName(n)) {
            Notifications.push("Friends", "Invalid username", Notifications.Type.WARNING);
            return;
        }
        if (FriendManager.add(n)) Notifications.push("Friend added", n, Notifications.Type.SUCCESS);
        else Notifications.push("Friends", n + " is already a friend", Notifications.Type.INFO);
        name.clear();
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        float lw = w - 6;
        float bh = 18;
        float addW = Widgets.buttonWidth("Add", Icons.PLUS);
        name.render(gui, ctx, x, y + 1, lw - addW - 5, bh, Icons.SOCIALS, null);
        Widgets.button(gui, ctx, "addFriend", x + lw - addW, y + 1, addW, bh, "Add", Widgets.Style.PRIMARY, Icons.PLUS, this::addTyped);

        // online players (if connected)
        MinecraftClient mc = MinecraftClient.getInstance();
        Map<String, SkinTextures> skins = new HashMap<>();
        List<String> online = new ArrayList<>();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        String self = mc.getSession().getUsername();
        if (net != null) {
            for (PlayerListEntry e : net.getPlayerList()) {
                String n = e.getProfile().name();
                if (n == null || n.isEmpty()) continue;
                skins.put(n.toLowerCase(Locale.ROOT), e.getSkinTextures());
                if (!n.equalsIgnoreCase(self)) online.add(n);
            }
            online.sort(String.CASE_INSENSITIVE_ORDER);
        }

        float top = y + 26, viewH = h - 26;
        float off = scroll.update();
        gui.pushClip(x - 6, top, w + 12, viewH);
        float cy = top - off;
        List<String> friends = FriendManager.list();

        Widgets.sectionLabel(ctx, "Friends • " + friends.size(), x + 2, cy, lw - 4);
        cy += 12;
        float rowH = 24, gap = 4;
        if (friends.isEmpty()) {
            Render2D.roundRect(ctx, x, cy, lw, 30, Theme.radius(), Theme.CARD);
            Render2D.roundOutline(ctx, x, cy, lw, 30, Theme.radius(), 1f, Theme.BORDER);
            Fonts.drawCentered(ctx, "No friends yet — add one above or from the player list", x + lw / 2f, cy + 15, Theme.TEXT_MUTED, false, 0.75f);
            cy += 34;
        }
        int idx = 0;
        for (String f : friends) {
            if (gui.isVisible(cy, rowH)) {
                SkinTextures skin = skins.get(f.toLowerCase(Locale.ROOT));
                row(ctx, f, skin, true, skin != null, x, cy, lw, rowH, idx);
            }
            cy += rowH + gap;
            idx++;
        }

        if (net != null) {
            cy += 6;
            Widgets.sectionLabel(ctx, "Online • " + online.size(), x + 2, cy, lw - 4);
            cy += 12;
            if (online.isEmpty()) {
                Fonts.draw(ctx, "Nobody else is online", x + 4, cy + 2, Theme.TEXT_MUTED, false, 0.75f);
                cy += 14;
            }
            for (String p : online) {
                if (gui.isVisible(cy, rowH)) row(ctx, p, skins.get(p.toLowerCase(Locale.ROOT)), FriendManager.isFriend(p), true, x, cy, lw, rowH, idx);
                cy += rowH + gap;
                idx++;
            }
        }
        gui.popClip();
        scroll.setBounds(cy + off - top, viewH);
        scroll.drawBar(gui, ctx, x + w - 2, top, viewH);
    }

    private void row(DrawContext ctx, String player, SkinTextures skin, boolean friend, boolean isOnline,
                     float x, float y, float w, float h, int index) {
        float in = intro(index);
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * in);
        y += (1 - in) * 6;
        boolean hov = gui.hovered(x, y, w, h);
        float hv = Anims.of(player.toLowerCase(Locale.ROOT), "socialRow", hov);
        float fv = Anims.of(player.toLowerCase(Locale.ROOT), "socialFriend", friend);
        float r = Theme.radius();
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        if (fv > 0.01f) Render2D.roundGradientH(ctx, x, y, w, h, r, ColorUtil.withAlpha(Theme.GREEN, Math.round(0x18 * fv)), ColorUtil.withAlpha(Theme.GREEN, 0));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.BORDER);

        float as = 14;
        if (skin != null) Widgets.head(ctx, skin, x + 6, y + (h - as) / 2f, (int) as);
        else Widgets.avatar(ctx, player, x + 6, y + (h - as) / 2f, as);
        if (isOnline) {
            Render2D.circle(ctx, x + 6 + as - 1, y + (h + as) / 2f - 1, 2.4f, Theme.CARD);
            Render2D.circle(ctx, x + 6 + as - 1, y + (h + as) / 2f - 1, 1.6f, Theme.GREEN);
        }
        Fonts.drawV(ctx, player, x + 26, y + h / 2f, Theme.TEXT, false, 0.88f);
        if (friend) {
            float bx = x + 26 + Fonts.width(player, false, 0.88f) + 5, bw = Fonts.width("FRIEND", true, 0.55f) + 7;
            Render2D.roundRect(ctx, bx, y + h / 2f - 4, bw, 8, 3, ColorUtil.withAlpha(Theme.GREEN, 0x30));
            Fonts.drawCentered(ctx, "FRIEND", bx + bw / 2f, y + h / 2f, Theme.GREEN, true, 0.55f);
        }

        float bh = 15, by = y + (h - bh) / 2f;
        String label = friend ? "Remove" : "Add";
        float bw = Widgets.buttonWidth(label, friend ? Icons.CLOSE : Icons.PLUS);
        Widgets.button(gui, ctx, player.toLowerCase(Locale.ROOT) + "#social", x + w - 5 - bw, by, bw, bh, label,
                friend ? Widgets.Style.DANGER : Widgets.Style.SECONDARY, friend ? Icons.CLOSE : Icons.PLUS, () -> {
                    if (FriendManager.isFriend(player)) {
                        FriendManager.remove(player);
                        Notifications.push("Friend removed", player, Notifications.Type.INFO);
                    } else {
                        FriendManager.add(player);
                        Notifications.push("Friend added", player, Notifications.Type.SUCCESS);
                    }
                });
        Render2D.setAlpha(prev);
    }
}
