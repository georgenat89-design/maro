package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** Smooth scrolling with eased target and a thin overlay scrollbar. */
public final class Scroll {
    private final Animation anim = new Animation(16f, 0f);
    private float target;
    private float max;
    private float value;

    public void setBounds(float contentHeight, float viewHeight) {
        max = Math.max(0f, contentHeight - viewHeight);
        target = Math.max(0f, Math.min(max, target));
    }

    public void scroll(double amount) {
        target = Math.max(0f, Math.min(max, target - (float) amount * 30f));
    }

    /** Advances the animation; call once per frame before layout. */
    public float update() {
        value = anim.update(target);
        return value;
    }

    public float get() {
        return value;
    }

    public float getMax() {
        return max;
    }

    public void reset() {
        target = 0;
        anim.snap(0);
        value = 0;
    }

    public void drawBar(ClickGuiScreen gui, DrawContext ctx, float x, float y, float viewHeight) {
        if (max <= 0.5f) return;
        float content = viewHeight + max;
        float barH = Math.max(18f, viewHeight * viewHeight / content);
        float by = y + (viewHeight - barH) * (value / max);
        boolean hovered = gui.hovered(x - 4, y, 10, viewHeight);
        float h = Anims.of(this, "bar", hovered);
        float w = 2f + h;
        Render2D.roundRect(ctx, x - h, by, w, barH, w / 2f, ColorUtil.lerp(ColorUtil.withAlpha(Theme.TEXT_MUTED, 0x70), Theme.accent(0xC0), h));
    }
}
