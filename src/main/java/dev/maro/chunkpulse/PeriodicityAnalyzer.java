package dev.maro.chunkpulse;

import java.util.Arrays;

/** Small fixed interval history per emitter. Simultaneous packets cannot create a zero-tick clock. */
public final class PeriodicityAnalyzer {
    public record Cadence(double quality, int ticks, int samples) { }
    private final long[] intervals=new long[32];
    private final long[] stableSince={-1,-1,-1,-1};
    private long last=-1,first=-1;
    private int size,index;
    private Cadence cached;
    public void add(long time) {
        if(last>=0&&time<=last+24)return;
        cached=null;
        if(first<0||last>=0&&time-last>10000)first=time;
        if(last>=0){long delta=time-last;if(delta<=10_000){
            // Observe continuity on every event in constant time. A long random prelude must not
            // turn a few seconds of regular final samples into a minute-long periodic clock.
            for(int phases=1;phases<=4;phases++){
                if(stableSince[phases-1]<0)stableSince[phases-1]=last;
                if(size>=phases){long prior=intervals[(index-phases)&31];if(Math.abs(delta-prior)>Math.max(36,Math.max(delta,prior)*.28))stableSince[phases-1]=time;}
            }
            intervals[index++&31]=delta;size=Math.min(32,size+1);
        }else{size=0;index=0;Arrays.fill(stableSince,-1);}}
        last=time;
    }
    public long last(){return last;}
    public double span(){return first<0?0:Math.max(0,last-first)/1000d;}
    public double stableSpan(){long since=last;for(long candidate:stableSince)if(candidate>=0)since=Math.min(since,candidate);return Math.max(0,last-since)/1000d;}
    public Cadence cadence() {
        if(cached!=null)return cached;
        if(size<7)return cached=new Cadence(0,0,size);
        var best=new Cadence(0,0,size);
        int start=size==32?index&31:0;
        // Alternating on/off intervals (for example 2gt + 6gt) are an 8gt clock too.
        for(int phases=1;phases<=4;phases++){
            if(size<Math.max(7,phases*4))continue;
            double[] targets=new double[phases];int totalTicks=0;boolean valid=true;
            for(int phase=0;phase<phases;phase++){
                long[] gaps=new long[(size+phases-1-phase)/phases];int n=0;
                for(int i=phase;i<size;i+=phases)gaps[n++]=intervals[(start+i)&31];
                Arrays.sort(gaps);int ticks=Math.max(1,(int)Math.round(gaps[n/2]/50d));
                if(ticks>200){valid=false;break;}targets[phase]=ticks*50d;totalTicks+=ticks;
            }
            if(!valid)continue;
            int matches=0;double error=0;
            for(int i=0;i<size;i++){
                double target=targets[i%phases],difference=Math.abs(intervals[(start+i)&31]-target);
                if(difference<=Math.max(18,target*.14))matches++;
                error+=Math.min(1,difference/Math.max(25,target));
            }
            double quality=(double)matches/size*Math.max(0,1-error/size)*Math.min(1,size/(double)Math.max(12,phases*4))*(1-(phases-1)*.04);
            if(quality>best.quality())best=new Cadence(quality,totalTicks,size);
        }
        return cached=best;
    }
}
