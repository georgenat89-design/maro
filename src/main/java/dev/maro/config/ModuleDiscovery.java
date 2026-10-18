package dev.maro.config;

import com.google.gson.*;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import java.nio.file.*;
import java.util.*;

/** New means newly added module names since the last installed module catalog. */
public final class ModuleDiscovery {
    private static Set<String> recent;
    private ModuleDiscovery(){}
    public static void initialize(){if(recent==null)load();}
    public static boolean contains(Module module){initialize();return recent.contains(module.getName());}
    private static void load(){
        var file=ConfigManager.DIR.resolve("module-discovery.json");
        var current=new TreeSet<String>();for(var m:ModuleManager.all())if(!m.hiddenInGui())current.add(m.getName());
        recent=new HashSet<>();
        try{
            if(Files.exists(file)){
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var known=new HashSet<String>();
                for(var value:json.getAsJsonArray("known"))known.add(value.getAsString());
                if(known.equals(current)){for(var value:json.getAsJsonArray("recent"))if(current.contains(value.getAsString()))recent.add(value.getAsString());return;}
                recent.addAll(current);recent.removeAll(known);
            }
            var json=new JsonObject();var known=new JsonArray();current.forEach(known::add);json.add("known",known);
            var added=new JsonArray();recent.stream().sorted().forEach(added::add);json.add("recent",added);
            Files.createDirectories(file.getParent());var temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,json.toString());
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception error){recent.clear();dev.maro.Maro.LOGGER.warn("Could not update module discovery",error);}
    }
}
