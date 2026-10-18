package dev.maro.accounts;

import com.google.gson.*;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Only explicitly remembered sessions are stored; Windows DPAPI binds secrets to this OS user. */
final class AccountVault {
    record Saved(UUID id,String name,String token,long expires,String refresh,String clientId) {
        Saved(UUID id,String name,String token,long expires){this(id,name,token,expires,"","");}
        @Override public String toString(){return "Saved["+name+"]";}
    }
    private final Path file;
    AccountVault(Path file){this.file=file;}
    static boolean supported(){return Platform.isWindows();}
    List<Saved> load() throws Exception {
        if(!Files.exists(file))return List.of();
        if(!supported())throw new IllegalStateException("Saved sessions require Windows encryption");
        if(Files.size(file)>4*1024*1024)throw new IllegalStateException("Account vault is too large");
        var result=new ArrayList<Saved>();
        var root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
        int version=root.get("version").getAsInt();
        if(version!=1&&version!=2)throw new IllegalStateException("Unsupported account vault");
        for(var element:root.getAsJsonArray("accounts")) {
            var row=element.getAsJsonObject();
            if(!row.get("name").getAsString().matches("[A-Za-z0-9_]{1,16}"))throw new IllegalStateException("Invalid saved account");
            byte[] clear=Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(row.get("secret").getAsString()));
            try {
                String access,refresh="",clientId="";
                if(version==1)access=SessionTokenClient.normalize(new String(clear,StandardCharsets.UTF_8));
                else {
                    var payload=JsonParser.parseString(new String(clear,StandardCharsets.UTF_8)).getAsJsonObject();
                    access=SessionTokenClient.normalize(payload.get("access").getAsString());
                    if(payload.has("refresh")&&!payload.get("refresh").getAsString().isEmpty()){
                        refresh=RefreshTokenClient.normalize(payload.get("refresh").getAsString());
                        clientId=RefreshTokenClient.clientId(payload.get("clientId").getAsString());
                    }
                }
                result.add(new Saved(SessionTokenClient.uuid(row.get("id").getAsString()),row.get("name").getAsString(),access,row.get("expires").getAsLong(),refresh,clientId));
            }
            finally {Arrays.fill(clear,(byte)0);}
        }
        return result;
    }
    void save(List<Saved> accounts) throws Exception {
        if(accounts.isEmpty()){Files.deleteIfExists(file);return;}
        if(!supported())throw new IllegalStateException("Remembering tokens requires Windows encryption");
        JsonObject root=new JsonObject();root.addProperty("version",2);JsonArray rows=new JsonArray();
        for(var entry:accounts) {
            JsonObject row=new JsonObject();row.addProperty("id",entry.id.toString());row.addProperty("name",entry.name);row.addProperty("expires",entry.expires);
            var payload=new JsonObject();payload.addProperty("access",entry.token);payload.addProperty("refresh",entry.refresh);payload.addProperty("clientId",entry.clientId);
            byte[] clear=new Gson().toJson(payload).getBytes(StandardCharsets.UTF_8);
            try {row.addProperty("secret",Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(clear)));}
            finally {Arrays.fill(clear,(byte)0);}
            rows.add(row);
        }
        root.add("accounts",rows);Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"accounts-",".tmp");
        try {Files.writeString(temporary,new Gson().toJson(root),StandardCharsets.UTF_8);
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
}
