package dev.maro.builder;

import java.util.ArrayDeque;

/** Rolling rate of completed work, measured only while the builder is running. */
public final class BuilderEta {
    private record Sample(long activeMillis,long completed){}
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private long lastClock=-1,activeMillis,completed,lastProgress;
    private boolean active;
    public void reset(){samples.clear();lastClock=-1;activeMillis=completed=lastProgress=0;active=false;}
    public void tick(long clockMillis,boolean running){
        if(lastClock>=0&&active)activeMillis+=Math.max(0,clockMillis-lastClock);
        lastClock=clockMillis;active=running;
        sample();
    }
    public void completed(){progress(1);}
    public void progress(int change){completed+=change;if(change>0)lastProgress=activeMillis;sample();}
    private void sample(){
        if(!samples.isEmpty()&&samples.getLast().activeMillis==activeMillis)samples.removeLast();
        if(samples.isEmpty()||activeMillis-samples.getLast().activeMillis>=1000)samples.addLast(new Sample(activeMillis,completed));
        else if(samples.getLast().completed!=completed)samples.addLast(new Sample(activeMillis,completed));
        while(samples.size()>2){var iterator=samples.iterator();iterator.next();if(iterator.next().activeMillis>=activeMillis-120_000)break;samples.removeFirst();}
    }
    public long seconds(int remaining){
        if(remaining<=0)return 0;
        if(samples.isEmpty()||activeMillis<5000||activeMillis-lastProgress>=30_000)return -1;
        var first=samples.getFirst();long elapsed=activeMillis-first.activeMillis,done=completed-first.completed;
        if(done<3||elapsed<5000)return -1;
        return Math.max(1,(long)Math.ceil((double)remaining*elapsed/(done*1000.0)));
    }
    public String label(int remaining,int supports){
        if(remaining<=0)return supports>0?"ETA · cleanup":"ETA · done";
        if(!active)return "ETA · paused";
        if(completed>0&&activeMillis-lastProgress>=30_000)return "ETA · waiting";
        long seconds=seconds(remaining);if(seconds<0)return "ETA · calculating";
        long hours=seconds/3600,minutes=seconds/60%60,remainder=seconds%60;
        return "ETA ~ "+(hours>0?hours+"h "+minutes+"m":minutes>0?minutes+"m "+remainder+"s":remainder+"s");
    }
}
