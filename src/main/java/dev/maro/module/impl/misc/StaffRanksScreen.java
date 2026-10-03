package dev.maro.module.impl.misc;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Every rank on the server you are on, drawn the way the tab list draws it (icons included),
 * highest first, with how many players have it. Click a rank to count it as staff here.
 */
public class StaffRanksScreen extends Screen {
    private static final int ROW = 22;
    private static final int WIDTH = 380;

    private final Screen parent;
    private final StaffList module;
    private List<StaffList.Rank> ranks = List.of();
    private float scroll;
    private int refresh;

    public StaffRanksScreen(Screen parent, StaffList module) {
        super(Text.literal("Server Ranks"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        ranks = module.ranksHere();
    }

    @Override
    public void tick() {
        // The tab list changes as people join and leave.
        if (++refresh % 20 == 0) ranks = module.ranksHere();
    }

    private int panelWidth() {
        return Math.min(WIDTH, width - 24);
    }

    private int listTop() {
        return 58;
    }

    private int listHeight() {
        return height - listTop() - 34;
    }

    private float maxScroll() {
        return Math.max(0, ranks.size() * ROW - listHeight());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xD0050608);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = panelWidth();
        int left = (width - w) / 2;

        Fonts.drawCentered(ctx, "Server Ranks", width / 2f, 18, Theme.TEXT, true, 1.1f);
        Fonts.beginRaw();
        Fonts.drawCentered(ctx, module.serverKey(), width / 2f, 31, Theme.accent(), true, 0.75f);
        Fonts.endRaw();
        Fonts.drawCentered(ctx, "Click the ranks that are staff here. Highest ranks are at the top", width / 2f, 43,
                ColorUtil.withAlpha(Theme.TEXT, 130), true, 0.72f);

        int top = listTop();
        int bottom = top + listHeight();
        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        if (ranks.isEmpty()) {
            Fonts.drawCentered(ctx, "Nobody in the tab list has a rank", width / 2f, top + 20, ColorUtil.withAlpha(Theme.TEXT, 120), true, 0.85f);
        }

        ctx.enableScissor(left, top, left + w, bottom);
        Render2D.setScissor(new ScreenRect(left, top, w, bottom - top));
        for (int i = 0; i < ranks.size(); i++) {
            StaffList.Rank rank = ranks.get(i);
            float y = top + i * ROW - scroll;
            if (y + ROW < top || y > bottom) continue;
            boolean staff = rank.picked() || rank.byWord();
            boolean over = mouseX >= left && mouseX < left + w && mouseY >= y && mouseY < y + ROW - 2 && mouseY >= top && mouseY < bottom;

            int fill = staff ? Theme.accent(over ? 58 : 40) : over ? 0x1AFFFFFF : 0x0DFFFFFF;
            Render2D.roundRect(ctx, left, y, w, ROW - 3, 5, fill);
            if (staff) Render2D.roundOutline(ctx, left, y, w, ROW - 3, 5, 1, Theme.accent(170));

            float mid = y + (ROW - 3) / 2f;
            ctx.drawText(textRenderer, rank.badge(), left + 8, Math.round(mid - 4), 0xFFFFFFFF, false);
            int badgeWidth = Math.max(textRenderer.getWidth(rank.badge()), 18);

            String who = String.join(", ", rank.players().subList(0, Math.min(3, rank.players().size())))
                    + (rank.players().size() > 3 ? " +" + (rank.players().size() - 3) : "");
            float textLeft = left + 8 + badgeWidth + 8;
            float tagWidth = 62;
            Fonts.beginRaw();
            Fonts.drawV(ctx, Fonts.trim(who, left + w - tagWidth - 14 - textLeft, false, 0.8f), textLeft, mid,
                    ColorUtil.withAlpha(Theme.TEXT, 170), false, 0.8f);
            Fonts.endRaw();

            String tag = rank.byWord() ? "Staff (word)" : rank.picked() ? "Staff" : "Not staff";
            int tagColor = staff ? Theme.accent() : ColorUtil.withAlpha(Theme.TEXT, 110);
            Fonts.drawRight(ctx, tag, left + w - 8, mid, tagColor, true, 0.7f);
        }
        Render2D.setScissor(null);
        ctx.disableScissor();

        Fonts.drawCentered(ctx, "Scroll for more  ·  Esc when done", width / 2f, height - 18, ColorUtil.withAlpha(Theme.TEXT, 110), true, 0.7f);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() == 0) {
            int w = panelWidth();
            int left = (width - w) / 2;
            int top = listTop();
            if (event.x() >= left && event.x() < left + w && event.y() >= top && event.y() < top + listHeight()) {
                int index = (int) Math.floor((event.y() - top + scroll) / ROW);
                if (index >= 0 && index < ranks.size() && !ranks.get(index).byWord()) {
                    module.togglePicked(ranks.get(index).key());
                    ranks = module.ranksHere();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = (float) Math.max(0, Math.min(maxScroll(), scroll - scrollY * ROW * 2));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
