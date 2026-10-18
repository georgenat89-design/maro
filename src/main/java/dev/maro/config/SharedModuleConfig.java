package dev.maro.config;

import com.google.gson.*;
import dev.maro.Maro;
import dev.maro.module.Module;
import dev.maro.module.impl.misc.AutoGoliath;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.setting.*;
import java.util.*;

/** Public presets contain one module's options; personal text, credentials and activation stay local. */
public final class SharedModuleConfig {
    public static final String MINECRAFT="1.21.11";
    private SharedModuleConfig(){}
    public record Preview(List<String> changes,int supported,int skipped){}
    private static boolean shareable(Setting<?> s){
        return s instanceof BooleanSetting||s instanceof NumberSetting||s instanceof ModeSetting
                ||s instanceof ColorSetting||s instanceof RegionsSetting;
    }
    public static JsonObject capture(Module module){
        JsonObject out=new JsonObject(),settings=new JsonObject();
        out.addProperty("format",1);out.addProperty("module",module.getName());
        out.addProperty("minecraft",MINECRAFT);out.addProperty("client",Maro.VERSION);
        for(var setting:module.getSettings())if(shareable(setting))settings.add(setting.getName(),setting.toJson().deepCopy());
        out.add("settings",settings);
        JsonObject extra=new JsonObject(),saved=module.saveExtra();
        for(String key:extraKeys(module))if(saved.has(key))extra.add(key,saved.get(key).deepCopy());
        out.add("extra",extra);return out;
    }
    private static Set<String> extraKeys(Module module){
        if(module instanceof AutoGoliath)return Set.of("targets");
        if(module instanceof BlockESP)return Set.of("blocks");
        if(module instanceof KeyboardStrokes)return Set.of("keyPositions");
        return Set.of();
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    private static JsonObject settings(Module module,JsonObject config){
        require(config.toString().length()<=96*1024,"Config exceeds 96 KB");
        require(config.has("format")&&config.get("format").getAsInt()==1,"Unsupported config format");
        require(config.has("module")&&module.getName().equals(config.get("module").getAsString()),"This preset belongs to another module");
        require(config.has("minecraft")&&MINECRAFT.equals(config.get("minecraft").getAsString()),"This preset uses another Minecraft version");
        require(Set.of("format","module","minecraft","client","settings","extra").containsAll(config.keySet()),"Unexpected preset fields");
        require(config.has("settings")&&config.get("settings").isJsonObject(),"Missing module settings");
        JsonObject source=config.getAsJsonObject("settings");require(source.size()<=512,"Too many settings");
        for(var s:module.getSettings())if(shareable(s)&&source.has(s.getName()))validate(s,source.get(s.getName()));
        require(!config.has("extra")||config.get("extra").isJsonObject(),"Invalid module data");
        JsonObject extra=config.has("extra")?config.getAsJsonObject("extra"):new JsonObject();
        require(extraKeys(module).containsAll(extra.keySet()),"This preset contains unsupported module data");
        if(extra.has("targets")){
            JsonArray targets=extra.getAsJsonArray("targets");require(targets.size()>0&&targets.size()<=200,"Invalid Goliath selections");
            for(var item:targets){var target=item.getAsJsonObject();String region=target.get("region").getAsString();
                int index=Arrays.asList(AutoGoliath.REGIONS).indexOf(region),number=target.get("number").getAsInt();
                require(index>=0&&number>=1&&number<=AutoGoliath.count(index),"Unknown Goliath destination");}
        }
        if(extra.has("blocks")){
            var blocks=extra.getAsJsonObject("blocks");require(blocks.size()<=256,"Too many block selections");
            for(var block:blocks.entrySet())require(block.getKey().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")&&block.getValue().isJsonPrimitive()
                    &&block.getValue().getAsJsonPrimitive().isString()&&block.getValue().getAsString().matches("#?[A-Fa-f0-9]{6}"),"Invalid block selection");
        }
        if(extra.has("keyPositions")){
            var positions=extra.getAsJsonObject("keyPositions");require(positions.size()<=300,"Too many key positions");
            for(var entry:positions.entrySet()){
                require(entry.getKey().matches("(key\\.[a-zA-Z0-9_.-]+|physical:[0-9]+)"),"Invalid keyboard key");
                var p=entry.getValue().getAsJsonObject();double x=p.get("x").getAsDouble(),y=p.get("y").getAsDouble();
                require(Double.isFinite(x)&&Double.isFinite(y)&&x>=0&&x<=1200&&y>=0&&y<=600,"Invalid keyboard placement");
            }
        }
        return source;
    }
    private static void validate(Setting<?> s,JsonElement value){
        if(s instanceof BooleanSetting)require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean(),"Invalid toggle: "+s.getName());
        else if(s instanceof NumberSetting number){
            require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber(),"Invalid number: "+s.getName());
            double v=value.getAsDouble();require(Double.isFinite(v)&&v>=number.getMin()&&v<=number.getMax(),"Out of range: "+s.getName());
        }else if(s instanceof ModeSetting mode)require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&mode.getModes().stream().anyMatch(v->v.equalsIgnoreCase(value.getAsString())),"Unavailable option: "+s.getName());
        else if(s instanceof ColorSetting)require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().matches("#?(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"),"Invalid color: "+s.getName());
        else if(s instanceof RegionsSetting){
            require(value.isJsonArray()&&value.getAsJsonArray().size()<=64,"Invalid screen regions");
            for(var item:value.getAsJsonArray()){
                require(item.isJsonArray()&&item.getAsJsonArray().size()==4,"Invalid screen region");
                for(var coordinate:item.getAsJsonArray()){require(coordinate.isJsonPrimitive()&&coordinate.getAsJsonPrimitive().isNumber(),"Invalid region coordinate");double v=coordinate.getAsDouble();require(Double.isFinite(v)&&v>=0&&v<=1,"Invalid region coordinate");}
            }
        }
    }
    public static Preview preview(Module module,JsonObject config){
        JsonObject source=settings(module,config);List<String> changes=new ArrayList<>();int supported=0;
        for(var s:module.getSettings())if(shareable(s)&&source.has(s.getName())){
            supported++;JsonElement after=source.get(s.getName()),before=s.toJson();
            if(!before.equals(after))changes.add(s.getName()+": "+shortValue(before)+" → "+shortValue(after));
        }
        if(config.has("extra")&&!config.getAsJsonObject("extra").equals(capture(module).getAsJsonObject("extra")))changes.add("Selections / layout: updated");
        return new Preview(List.copyOf(changes),supported,source.size()-supported);
    }
    private static String shortValue(JsonElement value){String s=value.toString();return s.length()>50?s.substring(0,47)+"…":s;}
    /** Validates the complete payload before touching settings, then rolls back if a listener fails. */
    public static JsonObject apply(Module module,JsonObject config){
        JsonObject source=settings(module,config),before=capture(module);
        try{assign(module,source,config.getAsJsonObject("extra"));}
        catch(RuntimeException error){assign(module,before.getAsJsonObject("settings"),before.getAsJsonObject("extra"));throw error;}
        return before;
    }
    private static void assign(Module module,JsonObject source,JsonObject extra){
        for(var s:module.getSettings())if(shareable(s)&&source.has(s.getName()))s.fromJson(source.get(s.getName()));
        if(extra!=null&&!extra.isEmpty()){
            JsonObject merged=module.saveExtra();extra.entrySet().forEach(e->merged.add(e.getKey(),e.getValue().deepCopy()));module.loadExtra(merged);
        }
    }
}
