package dev.maro.gui.spotify;

/** Keep each wheel notch; fractional trackpad input accumulates independently of animation timing. */
final class FeedWheel {
    private static final long IDLE=350_000_000L;
    private double amount;
    private long lastInput;
    void reset(){amount=0;lastInput=0;}
    int take(double delta,long now){
        if(!Double.isFinite(delta)||delta==0)return 0;
        if(now-lastInput>IDLE||Math.signum(delta)!=Math.signum(amount))amount=0;
        lastInput=now;amount+=Math.clamp(delta,-1,1);
        if(Math.abs(amount)<1-1e-9)return 0;
        int direction=amount<0?1:-1;amount=0;
        return direction;
    }
}
