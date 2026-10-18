package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import java.util.function.LongSupplier;

/** Bounded 1.4-second exponential wheel glide; native thumb dragging stays immediate. */
public final class Scroll {
    private final LongSupplier clock;
    private long last;
    private double elapsed;
    private float from;
    private static final double DURATION=1.4;
    private float target;
    private float max;
    private float value;

    public Scroll() { this(System::nanoTime); }
    Scroll(LongSupplier clock) { this.clock=clock;last=clock.getAsLong(); }

    public void setBounds(float contentHeight, float viewHeight) {
        float previousTarget=target,previousValue=value;
        max=Float.isFinite(contentHeight)&&Float.isFinite(viewHeight)&&viewHeight>0?(float)Math.clamp((double)contentHeight-viewHeight,0,Float.MAX_VALUE):0;
        target=Math.clamp(target,0,max);value=Math.clamp(value,0,max);
        if(previousTarget!=target||previousValue!=value){from=value;elapsed=0;last=clock.getAsLong();}
    }
    public void scroll(double amount) {
        if(!Double.isFinite(amount)||amount==0)return;
        update();double delta=-amount*30;
        if(delta*(target-value)<0)target=value;
        scrollTo((float)Math.clamp(target+delta,0,max));
    }
    public void scrollTo(float position){
        if(!Float.isFinite(position))return;
        from=value;target=Math.clamp(position,0,max);elapsed=0;last=clock.getAsLong();
    }
    public float getTarget(){return target;}
    public void jumpTo(float position){if(Float.isFinite(position))jump(position);}
    private void jump(float position){value=target=from=Math.clamp(position,0,max);elapsed=0;last=clock.getAsLong();}
    public float update(){return update(false);}
    /** Freeze the row under a dragged slider; pending wheel motion must not move its hitbox. */
    public float update(boolean holdingControl){
        long now=clock.getAsLong();double dt=Math.clamp((now-last)/1e9,0,.1);last=now;
        if(holdingControl){jump(value);return value;}
        if(!isMoving()){value=target;return value;}
        elapsed+=dt;double phase=Math.clamp(elapsed/DURATION,0,1);
        double eased=phase<=0?0:Math.min(1,1.001-Math.pow(2,-10*phase));
        value=(float)Math.clamp(from+((double)target-from)*eased,0,max);
        if(phase>=1||Math.abs(value-target)<.02)value=target;
        return value;
    }
    public boolean isMoving(){return Math.abs(value-target)>.02;}

    public float get() {
        return value;
    }

    public float getMax() {
        return max;
    }

    public void reset() {
        jump(0);
    }

    public void drawBar(ClickGuiScreen gui, DrawContext ctx, float x, float y, float viewHeight) {
        if (max <= 0.5f || !Float.isFinite(viewHeight) || viewHeight <= 2) return;
        float insideHeight=viewHeight-2,insideY=y+1;
        float barH = Math.min(insideHeight, Math.max(18f, (float)(insideHeight * viewHeight / ((double)viewHeight+max))));
        float travel = insideHeight - barH;
        float by = insideY + travel * Math.clamp(value / max,0,1);
        boolean hovered = gui.hovered(x - 5, y, 12, viewHeight) || gui.isDragging(this);
        float h = Anims.of(this, "bar", hovered);
        float w = 2f + h;
        Render2D.roundRect(ctx, x - 2, y, 4, viewHeight, 2, Theme.accent(Math.round(0x18 + 0x14 * h)));
        Render2D.roundRect(ctx, x - w/2, by, w, barH, w / 2f, ColorUtil.lerp(ColorUtil.withAlpha(Theme.TEXT_MUTED, 0x70), Theme.accent(0xC0), h));
        gui.scrollHit(x - 5, y, 12, viewHeight, this::scroll);
        gui.hit(x - 5, y, 12, viewHeight, (button, mx, my) -> {
            if (button != 0 || travel <= 0) return;
            float grab = my >= by && my <= by + barH ? (float)my - by : barH / 2;
            gui.startDrag(this, (dx, dy) -> jump((float)(dy - insideY - grab) / travel * max));
        });
    }
}
