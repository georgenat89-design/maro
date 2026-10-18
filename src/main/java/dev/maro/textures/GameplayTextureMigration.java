package dev.maro.textures;

import com.google.gson.*;
import java.util.*;

/** One-way profile migration. Existing Gameplay Textures data always takes precedence. */
public final class GameplayTextureMigration {
    private GameplayTextureMigration(){}
    public static void apply(JsonObject modules){
        if(modules.has("Gameplay Textures"))return;
        JsonObject packs=object(modules,"Texture Packs"),textures=object(modules,"Better Textures"),looks=object(modules,"Better Looks");
        if(packs==null&&textures==null&&looks==null)return;
        JsonObject merged=packs==null?new JsonObject():packs.deepCopy();
        JsonObject settings=object(merged,"settings");if(settings==null){settings=new JsonObject();merged.add("settings",settings);}
        JsonObject lookSettings=looks==null?null:object(looks,"settings");
        if(lookSettings!=null)for(var entry:lookSettings.entrySet())settings.add(entry.getKey(),entry.getValue().deepCopy());
        settings.addProperty("Appearance",enabled(looks));
        JsonObject extra=object(merged,"extra");if(extra==null){extra=new JsonObject();merged.add("extra",extra);}
        JsonObject modrinth=textures==null?null:object(textures,"extra");
        if(modrinth!=null){
            extra.add("modrinth",modrinth.deepCopy());
            LinkedHashSet<String> picked=new LinkedHashSet<>();
            JsonArray legacy=new JsonArray();
            if(enabled(textures)&&modrinth.has("packs")&&modrinth.has("active")){
                Map<String,String> files=new HashMap<>();
                for(var e:modrinth.getAsJsonArray("packs")){var p=e.getAsJsonObject();files.put(p.get("project").getAsString(),p.get("file").getAsString());}
                for(var e:modrinth.getAsJsonArray("active")){String file=files.get(e.getAsString());if(file!=null){picked.add("file/"+file);legacy.add("file/"+file);}}
            }
            extra.add("legacyManaged",legacy);
            if(extra.has("picked")&&extra.get("picked").isJsonArray())for(var e:extra.getAsJsonArray("picked"))picked.add(e.getAsString());
            JsonArray list=new JsonArray();picked.forEach(list::add);extra.add("picked",list);extra.addProperty("seeded",true);
        }
        // Prefer the previous pack-manager bind, then a bound legacy browser/appearance key.
        for(JsonObject old:Arrays.asList(packs,textures,looks))if(old!=null&&old.has("bind")&&old.get("bind").isJsonPrimitive()){
            if(!merged.has("bind")||merged.get("bind").getAsInt()==-1)merged.add("bind",old.get("bind").deepCopy());
        }
        merged.addProperty("enabled",enabled(packs)||enabled(textures)||enabled(looks));
        modules.add("Gameplay Textures",merged);
    }
    private static JsonObject object(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):null;}
    private static boolean enabled(JsonObject o){return o!=null&&o.has("enabled")&&o.get("enabled").getAsBoolean();}
}
