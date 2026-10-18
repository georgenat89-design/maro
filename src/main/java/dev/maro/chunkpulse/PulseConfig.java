package dev.maro.chunkpulse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;

/** Advanced tuning is local JSON; common controls also live in Maro's module settings. */
public final class PulseConfig {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public double low=12,medium=55,high=160,huge=650;
    public int schema=2;
    public double cadenceQuality=.72,sustainSeconds=60,confirmedFreshSeconds=60;
    public int hugeChunks=4,hugePositions=20,maxChunks=2048,maxPositions=128,maxLanes=32;
    public int minY=-64,maxY=0,selfRadius=10,selfMaskTicks=60,minEventsForFlag=6,minDistinctMachines=4,mobClusterMin=20;
    public int maskRadius=3,miningDropRadius=5,mobSelfRadius=24,lightHoldTicks=40,maxMasks=2048,maxEmitters=8192;
    public double otherPlayerWeight=.1,selfCapRatio=.6,machineWindowSeconds=60,fadeSeconds=10,fillAlpha=.32,borderAlpha=.9,historyAlpha=.1;
    public enum Plane { PLAYER,FIXED,CLUSTER }
    public Plane planeMode=Plane.PLAYER;
    public double planeY=.02;
    public boolean seeThrough=true,showLabels=false,showHistory=false,pulse=true,overlay=true,activityHud=true,nearestHud=true;
    public boolean simpleTiles=true;
    public boolean alerts=false,alertSound=false,blockEntitySignals=false,debugRecording=false;
    public ClusterEngine.Tier signalMinTier=ClusterEngine.Tier.MEDIUM;
    public boolean signalEnabled=true,digCue=true;
    public double alignRadius=6,revealRadius=24;
    public int splitChunks=3;
    public int renderDistanceChunks=8,hudRows=4;
    public String minimumTier="LOW";
    public List<String> servers=new ArrayList<>(List.of("donutsmp.net"));
    public EnumMap<ClusterEngine.Tier,Integer> tierColors=new EnumMap<>(ClusterEngine.Tier.class);
    public int maxEntities=8192,maxSignalsPerTick=2048,maxDebugEvents=20_000,retentionHours=168;
    public int maxRenderChunks=128,maxLightSections=8192;
    public EnumMap<SignalEvent.Kind,Double> weights=new EnumMap<>(SignalEvent.Kind.class);
    public PulseConfig(){for(var kind:SignalEvent.Kind.values())weights.put(kind,kind.weight);defaultColors();}
    private void defaultColors(){int[] colors={0xFFFFD54F,0xFFFF963D,0xFFFF4856,0xFFE64DFF};for(var tier:ClusterEngine.Tier.values())tierColors.putIfAbsent(tier,colors[tier.ordinal()]);}
    public void validate() {
        low=bounded(low,1,1e6,12);medium=bounded(medium,low,1e7,55);
        high=bounded(high,medium,1e8,160);huge=bounded(huge,high,1e9,650);
        cadenceQuality=bounded(cadenceQuality,.4,1,.72);sustainSeconds=bounded(sustainSeconds,60,600,60);
        confirmedFreshSeconds=bounded(confirmedFreshSeconds,5,600,60);
        hugeChunks=Math.clamp(hugeChunks,2,128);hugePositions=Math.clamp(hugePositions,4,4096);
        maxY=Math.clamp(maxY,-2046,0);minY=Math.clamp(minY,-2048,maxY-2);
        selfRadius=Math.clamp(selfRadius,1,64);selfMaskTicks=Math.clamp(selfMaskTicks,20,1200);
        minEventsForFlag=Math.clamp(minEventsForFlag,6,1000);minDistinctMachines=Math.clamp(minDistinctMachines,4,128);mobClusterMin=Math.clamp(mobClusterMin,20,500);
        maskRadius=Math.clamp(maskRadius,3,5);miningDropRadius=Math.clamp(miningDropRadius,5,8);mobSelfRadius=Math.clamp(mobSelfRadius,24,64);
        lightHoldTicks=Math.clamp(lightHoldTicks,40,200);maxMasks=Math.clamp(maxMasks,128,4096);maxEmitters=Math.clamp(maxEmitters,128,16384);
        otherPlayerWeight=bounded(otherPlayerWeight,0,.1,.1);selfCapRatio=bounded(selfCapRatio,.1,.6,.6);
        machineWindowSeconds=bounded(machineWindowSeconds,10,120,60);fadeSeconds=bounded(fadeSeconds,1,30,10);
        fillAlpha=bounded(fillAlpha,.25,.4,.32);borderAlpha=bounded(borderAlpha,.5,1,.9);historyAlpha=bounded(historyAlpha,0,.2,.1);
        planeY=bounded(planeY,-2048,2048,.02);if(planeMode==null)planeMode=Plane.PLAYER;
        renderDistanceChunks=Math.clamp(renderDistanceChunks,1,32);hudRows=Math.clamp(hudRows,1,10);
        if(signalMinTier==null)signalMinTier=ClusterEngine.Tier.MEDIUM;
        alignRadius=bounded(alignRadius,1,32,6);revealRadius=bounded(revealRadius,1,128,24);splitChunks=Math.clamp(splitChunks,1,64);
        if(servers==null)servers=new ArrayList<>(List.of("donutsmp.net"));servers=servers.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()&&s.length()<=253).limit(32).toList();
        try{minimumTier=ClusterEngine.Tier.valueOf(minimumTier.toUpperCase(Locale.ROOT)).name();}catch(Exception ignored){minimumTier="LOW";}
        if(tierColors==null)tierColors=new EnumMap<>(ClusterEngine.Tier.class);tierColors.values().removeIf(Objects::isNull);defaultColors();
        maxChunks=Math.clamp(maxChunks,32,8192);maxPositions=Math.clamp(maxPositions,16,256);maxLanes=Math.clamp(maxLanes,4,32);
        maxEntities=Math.clamp(maxEntities,128,16384);maxSignalsPerTick=Math.clamp(maxSignalsPerTick,64,16384);
        maxDebugEvents=Math.clamp(maxDebugEvents,100,50000);retentionHours=Math.clamp(retentionHours,1,720);
        maxRenderChunks=Math.clamp(maxRenderChunks,8,256);maxLightSections=Math.clamp(maxLightSections,128,32768);
        if(weights==null)weights=new EnumMap<>(SignalEvent.Kind.class);
        for(var kind:SignalEvent.Kind.values())weights.put(kind,bounded(weights.getOrDefault(kind,kind.weight),0,50,kind.weight));
        weights.put(SignalEvent.Kind.FLUID,0d);weights.put(SignalEvent.Kind.BLOCK_UPDATE,0d);
    }
    private static double bounded(Double n,double min,double max,double fallback){return n!=null&&Double.isFinite(n)?Math.clamp(n,min,max):fallback;}
    public static PulseConfig load(Path path) {
        try{
            if(Files.isRegularFile(path)&&Files.size(path)<131072){
                String text=Files.readString(path);var config=JSON.fromJson(text,PulseConfig.class);
                var tree=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                if(config!=null&&(!tree.has("schema")||tree.get("schema").getAsInt()<2)){config.schema=2;config.sustainSeconds=60;config.alerts=false;}
                if(config!=null){config.validate();return config;}
            }
        }catch(Exception error){dev.maro.Maro.LOGGER.warn("ChunkPulse config could not be read",error);}
        var config=new PulseConfig();save(path,config);return config;
    }
    public static void save(Path path,PulseConfig config) {
        config.validate();
        try{Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(config));}
        catch(Exception error){dev.maro.Maro.LOGGER.warn("ChunkPulse config could not be saved",error);}
    }
}
