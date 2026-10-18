package dev.maro.textures;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Optional private library: archives are streamed from the client only when selected. */
public final class BundledPacks {
    private static final String ROOT="/maro-pack-library/";
    private static final Map<String,String> INDEX=readIndex();
    private BundledPacks(){}
    private static Map<String,String> readIndex(){
        try(var in=BundledPacks.class.getResourceAsStream(ROOT+"index.json")){
            if(in==null)return Map.of();byte[] bytes=in.readNBytes(131073);
            if(bytes.length>131072)return Map.of();
            var packs=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("packs");
            if(packs.size()>PacksMcCatalog.LIMIT)return Map.of();var out=new HashMap<String,String>();
            for(var item:packs){var p=item.getAsJsonObject();String id=p.get("id").getAsString(),hash=p.get("sha256").getAsString();
                if(!id.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")||!hash.matches("[a-f0-9]{64}")||out.putIfAbsent(id,hash)!=null)return Map.of();}
            return Map.copyOf(out);
        }catch(Exception ignored){return Map.of();}
    }
    public static boolean contains(String id){return INDEX.containsKey(id);}
    public static Path install(String id,Path directory) throws Exception{
        String hash=INDEX.get(id);if(hash==null)throw new IOException("This pack is not included in the client library");
        try(var in=BundledPacks.class.getResourceAsStream(ROOT+"packs/"+id+".zip")){
            if(in==null)throw new IOException("The included pack archive is missing");
            return PackArchive.install(in,id,directory,hash);
        }
    }
    public static byte[] preview(String url) throws Exception{
        if(!PacksMcCatalog.imageUri(url))return null;
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
        try(var in=BundledPacks.class.getResourceAsStream(ROOT+"previews/"+hash+".img")){
            if(in==null)return null;byte[] bytes=in.readNBytes(4*1024*1024+1);
            if(bytes.length>4*1024*1024)throw new IOException("Included preview is too large");return bytes;
        }
    }
}
