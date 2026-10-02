package dev.maro.gui.page;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.widget.Scroll;
import net.minecraft.client.gui.DrawContext;

/** One content view on the right side of the menu. */
public abstract class Page {
    protected final ClickGuiScreen gui;
    protected final Scroll scroll = new Scroll();
    protected long openedAt = System.currentTimeMillis();

    protected Page(ClickGuiScreen gui) {
        this.gui = gui;
    }

    public abstract void render(DrawContext ctx, float x, float y, float w, float h);

    public void onOpen() {
        openedAt = System.currentTimeMillis();
    }

    /** @return true if the page consumed Escape (e.g. to close a sub view) */
    public boolean onEscape() {
        return false;
    }

    public void onScroll(double amount) {
        scroll.scroll(amount);
    }

    /** Whether typing on this page should jump into the module search. */
    public boolean typeToSearch() {
        return false;
    }

    /** Staggered entrance progress for the {@code index}-th item since the page opened. */
    protected float intro(int index) {
        float t = (System.currentTimeMillis() - openedAt) / 1000f - Math.min(index, 14) * 0.03f;
        return dev.maro.util.Easing.outCubic(t / 0.32f);
    }
}
