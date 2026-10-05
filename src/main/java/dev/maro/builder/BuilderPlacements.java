package dev.maro.builder;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable per-slot snapshots; all methods run on the builder's ordered IO executor. */
public final class BuilderPlacements {
    public record Saved(Schematic schematic,JsonObject placement){}
    private final Path directory;
    private final Map<Integer,Schematic> cached=new HashMap<>();
    private final Map<Integer,String> snapshots=new HashMap<>();
    public BuilderPlacements(Path directory){this.directory=directory;}
    private Path metadata(int slot){if(slot<0||slot>10)throw new IllegalArgumentException("Build slot");return directory.resolve("build-"+slot+".json");}
    public JsonObject info(int slot)throws IOException{
        var path=metadata(slot);if(!Files.exists(path))return null;
        if(Files.size(path)>128*1024)throw new IOException("Build metadata exceeds 128 KB");
        try{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
        catch(RuntimeException error){throw new IOException("Invalid saved build",error);}
    }
    public Integer activeSlot()throws IOException{
        var path=directory.resolve("active.json");if(!Files.exists(path))return null;
        if(Files.size(path)>128)throw new IOException("Invalid active build slot");
        try{return JsonParser.parseString(Files.readString(path)).getAsInt();}catch(RuntimeException error){throw new IOException("Invalid active build slot",error);}
    }
    public void activate(int slot)throws IOException{
        if(slot<-1||slot>10)throw new IOException("Invalid active build slot");Files.createDirectories(directory);
        atomicText(directory.resolve("active.json"),String.valueOf(slot));
    }
    private static void atomicText(Path path,String text)throws IOException{
        var temporary=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temporary,text);
        try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void save(int slot,Schematic schematic,JsonObject placement)throws IOException{
        Files.createDirectories(directory);var data=placement.deepCopy();String snapshot=snapshots.get(slot);
        if(cached.get(slot)!=schematic||snapshot==null){
            snapshot="build-"+slot+"-"+UUID.randomUUID()+".nbt";
            NbtIo.writeCompressed(SchematicIO.encodeStructure(schematic),directory.resolve(snapshot));
        }
        data.addProperty("snapshot",snapshot);data.addProperty("schematic-name",schematic.name);data.addProperty("schematic-format",schematic.format);
        var offset=new JsonArray();offset.add(schematic.offset.getX());offset.add(schematic.offset.getY());offset.add(schematic.offset.getZ());data.add("file-offset",offset);
        var previous=info(slot);atomicText(metadata(slot),new GsonBuilder().setPrettyPrinting().create().toJson(data));activate(slot);
        cached.put(slot,schematic);snapshots.put(slot,snapshot);
        if(previous!=null&&previous.has("snapshot")&&!previous.get("snapshot").getAsString().equals(snapshot)){
            try{Files.deleteIfExists(snapshotPath(previous));}catch(IOException ignored){} // old metadata is already replaced
        }
    }
    private Path snapshotPath(JsonObject data)throws IOException{
        String name=data.get("snapshot").getAsString();
        if(!name.matches("build-(?:[0-9]|10)-[a-f0-9-]+\\.nbt"))throw new IOException("Invalid snapshot path");
        return directory.resolve(name);
    }
    public Saved load(int slot)throws IOException{
        var data=info(slot);if(data==null)throw new IOException("This build slot is empty");
        var raw=SchematicIO.read(snapshotPath(data));var offset=data.getAsJsonArray("file-offset");
        var cells=new BlockState[raw.size()];for(int i=0;i<cells.length;i++)cells[i]=raw.state(i);
        var schematic=new Schematic(data.get("schematic-name").getAsString(),data.get("schematic-format").getAsString(),raw.width,raw.height,raw.length,new BlockPos(offset.get(0).getAsInt(),offset.get(1).getAsInt(),offset.get(2).getAsInt()),cells);
        activate(slot);cached.put(slot,schematic);snapshots.put(slot,data.get("snapshot").getAsString());return new Saved(schematic,data);
    }
}
