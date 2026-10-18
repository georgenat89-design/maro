package dev.maro.render.pickaxe;

import com.google.gson.*;
import java.util.Map;

/** Retire the standalone module without losing an enabled tool or overwriting newer Cosmetics data. */
public final class PickaxeMigration {
    private PickaxeMigration(){}
    public static void apply(JsonObject modules){
        JsonElement removed=modules.remove("Custom Pickaxe");
        if(removed==null||!removed.isJsonObject())return;
        JsonObject old=removed.getAsJsonObject();
        JsonObject merged=modules.has("Cosmetics")&&modules.get("Cosmetics").isJsonObject()?modules.getAsJsonObject("Cosmetics").deepCopy():new JsonObject();
        JsonObject extra=object(merged,"extra");
        if(extra.has("pickaxesMerged")&&extra.get("pickaxesMerged").getAsBoolean())return;
        if(!old.has("enabled")||!old.get("enabled").getAsBoolean())return;
        JsonObject from=object(old,"settings"),to=object(merged,"settings");
        if(!from.has("Pickaxe")||!PickaxeModels.contains(from.get("Pickaxe").getAsString()))return;
        if(from.has("Pickaxe")&&PickaxeModels.contains(from.get("Pickaxe").getAsString()))to.add("Pickaxe",from.get("Pickaxe").deepCopy());
        if(from.has("Size"))to.addProperty("Item Size",Math.clamp(from.get("Size").getAsDouble()/100,.5,1.6));
        for(var entry:Map.of("Metal Shine","Pickaxe Metal Shine","Outline","Pickaxe Outline","Outline Color","Pickaxe Outline Color","Outline Width","Pickaxe Outline Width","First Person","Pickaxe First Person","Third Person","Pickaxe Third Person","Inventory Icons","Pickaxe Inventory Icons").entrySet())
            if(from.has(entry.getKey()))to.add(entry.getValue(),from.get(entry.getKey()).deepCopy());
        merged.addProperty("enabled",true);
        if(old.has("bind")&&(!merged.has("bind")||merged.get("bind").isJsonPrimitive()&&merged.get("bind").getAsInt()==-1))merged.add("bind",old.get("bind").deepCopy());
        extra.addProperty("pickaxesMerged",true);merged.add("settings",to);merged.add("extra",extra);modules.add("Cosmetics",merged);
    }
    private static JsonObject object(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
}
