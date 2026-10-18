package dev.maro.gui.widget;

import java.util.concurrent.atomic.AtomicLong;

/** Deterministic gestures and viewport changes, including a reversal while resizing. */
public final class ScrollMotionChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void frame(Scroll scroll,AtomicLong clock,long nanos){clock.addAndGet(nanos);scroll.update();}
    public static void run(){
        float reference=-1;
        for(int fps:new int[]{30,60,144}){
            var curveClock=new AtomicLong();var curve=new Scroll(curveClock::get);curve.setBounds(1000,200);curve.scroll(-1);
        for(int i=0;i<7;i++)frame(curve,curveClock,50_000_000);require(Math.abs(curve.get()-24.7267)<.03,"Reference 1.4-second ease-out quarter-time curve differs");
        for(int i=0;i<21;i++)frame(curve,curveClock,50_000_000);require(curve.get()==30&&!curve.isMoving(),"Reference glide did not settle by 1.4 seconds");
        var clock=new AtomicLong();var s=new Scroll(clock::get);s.setBounds(1200,300);s.scroll(-12);float last=0;
            for(int i=0;i<fps;i++){frame(s,clock,1_000_000_000L/fps);require(s.get()>=last&&s.get()<=360,"Swipe overshot/reversed at "+fps);last=s.get();}
            if(reference<0)reference=s.get();require(Math.abs(s.get()-reference)<.05,"Frame rate changed scroll distance");
        }
        var curveClock=new AtomicLong();var curve=new Scroll(curveClock::get);curve.setBounds(1000,200);curve.scroll(-1);
        for(int i=0;i<7;i++)frame(curve,curveClock,50_000_000);require(Math.abs(curve.get()-24.7267)<.03,"Reference 1.4-second ease-out quarter-time curve differs");
        for(int i=0;i<21;i++)frame(curve,curveClock,50_000_000);require(curve.get()==30&&!curve.isMoving(),"Reference glide did not settle by 1.4 seconds");
        var clock=new AtomicLong();var s=new Scroll(clock::get);s.setBounds(1200,300);
        for(int i=0;i<4;i++)s.scroll(-.25);frame(s,clock,30_000_000);require(s.get()>0&&s.get()<30,"Fractional swipe lost precision or snapped");
        s.scroll(-20);frame(s,clock,50_000_000);float before=s.get();s.scroll(1);frame(s,clock,16_000_000);require(s.get()<before,"Reverse gesture waited for old momentum");
        s.scroll(-20);frame(s,clock,50_000_000);before=s.get();clock.addAndGet(16_000_000);s.update(true);
        for(int i=0;i<20;i++)frame(s,clock,16_000_000);require(s.get()==before&&!s.isMoving(),"Dragging a control left scrolling behind it");
        s.scroll(-20);frame(s,clock,50_000_000);before=s.get();s.scroll(1);s.setBounds(before-10+100,100);float limit=s.getMax();frame(s,clock,16_000_000);
        require(s.get()<limit,"Resizing discarded the queued reverse gesture");
        s.scroll(Double.NaN);s.scroll(Double.POSITIVE_INFINITY);s.setBounds(10,100);require(s.get()==0&&s.getMax()==0,"Shrinking content kept a stale viewport");
        s.setBounds(Float.MAX_VALUE,100);s.scroll(-Double.MAX_VALUE);frame(s,clock,16_000_000);require(Float.isFinite(s.get()),"Large finite gesture poisoned scroll state");
        System.out.println("[scroll-motion] PASS: reference 1.4s ease-out timing, bounded monotonic motion at 30/60/144 Hz, fractional swipes, immediate reversal, held-control freeze, resize reversal preservation and finite extreme inputs");
    }
}
