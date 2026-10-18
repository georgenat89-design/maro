package dev.maro.chunkpulse;

import java.nio.file.*;
import java.util.*;

/** Offline replay of the bounded JSON event recordings emitted by /chunkpulse export. */
public final class PulseReplay {
    public static void main(String[] args)throws Exception{
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Usage: replayChunkPulse -PpulseReplay=events.json [-PpulseReplayConfig=config.json]");
        Path input=Path.of(args[0]);if(Files.size(input)>32*1024*1024)throw new IllegalArgumentException("Replay exceeds 32 MiB");
        var tree=com.google.gson.JsonParser.parseString(Files.readString(input));
        var recording=tree.isJsonObject()?PulseConfig.JSON.fromJson(tree,ActivityEngine.Recording.class):null;
        var config=args.length==2?PulseConfig.JSON.fromJson(Files.readString(Path.of(args[1])),PulseConfig.class):recording!=null&&recording.config()!=null?recording.config():new PulseConfig();
        if(config==null)throw new IllegalArgumentException("Invalid config");config.validate();
        var events=tree.isJsonArray()?PulseConfig.JSON.fromJson(tree,SignalEvent[].class):new SignalEvent[0];
        if(events==null||events.length>100000)throw new IllegalArgumentException("Invalid or oversized replay");
        Arrays.sort(events,Comparator.nullsLast(Comparator.comparingLong(SignalEvent::time)));
        var engine=new ActivityEngine(config);long now=0;
        if(recording!=null){if(recording.version()!=2||recording.frames()==null||recording.frames().size()>100000)throw new IllegalArgumentException("Invalid recording");for(var frame:recording.frames()){if(frame.context()!=null)engine.context(frame.context(),frame.time(),false);if(frame.mask()!=null)engine.mask(frame.mask(),false);if(frame.event()!=null)engine.accept(frame.event(),false);now=Math.max(now,frame.time());}}
        for(var event:events)if(event!=null){engine.accept(event,false);now=Math.max(now,event.time());}
        var output=new ArrayList<Map<String,Object>>();
        for(var c:engine.clusters(now)){var row=new LinkedHashMap<String,Object>();row.put("tier",c.tier());row.put("score",c.score());row.put("centroid",List.of(c.x(),c.y(),c.z()));row.put("chunks",c.chunks().size());row.put("positions",c.positions());row.put("bounds",List.of(c.minX(),c.minY(),c.minZ(),c.maxX(),c.maxY(),c.maxZ()));row.put("explanation",c.explanation());output.add(row);}
        System.out.println(PulseConfig.JSON.toJson(Map.of("accepted",engine.accepted,"dropped",engine.dropped,"clusters",output)));
    }
    private PulseReplay(){ }
}
