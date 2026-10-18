package dev.maro.protection;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.jar.JarFile;

/** A removable client signal; server authorization never depends on this check being unpatchable. */
public final class ReleaseIntegrity {
    public static void verify()throws Exception{
        var paths=FabricLoader.getInstance().getModContainer("maro").orElseThrow().getOrigin().getPaths();
        if(paths.size()!=1||!Files.isRegularFile(paths.getFirst()))throw new SecurityException("Signed protected artifact required");
        try(var jar=new JarFile(paths.getFirst().toFile())){
            byte[] payload=jar.getInputStream(jar.getJarEntry("META-INF/maro-integrity.json")).readAllBytes();
            var trust=JsonParser.parseString(new String(jar.getInputStream(jar.getJarEntry("maro-license-public.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            String pem=trust.get("release_public_key").getAsString().replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
            var key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));var signature=Signature.getInstance("Ed25519");signature.initVerify(key);signature.update(payload);
            if(!signature.verify(jar.getInputStream(jar.getJarEntry("META-INF/maro-integrity.sig")).readAllBytes()))throw new SecurityException("Protected artifact signature invalid");
            var entries=JsonParser.parseString(new String(payload,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("entries");var seen=new HashSet<String>();
            for(var e:Collections.list(jar.entries())){
                if(e.isDirectory()||e.getName().equals("META-INF/maro-integrity.json")||e.getName().equals("META-INF/maro-integrity.sig"))continue;
                if(!seen.add(e.getName())||!entries.has(e.getName()))throw new SecurityException("Unexpected protected artifact entry");
                byte[] digest;try(var in=jar.getInputStream(e)){var md=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)md.update(buffer,0,n);digest=md.digest();}
                if(!HexFormat.of().formatHex(digest).equals(entries.get(e.getName()).getAsString()))throw new SecurityException("Protected artifact has been modified");
            }
            if(seen.size()!=entries.size())throw new SecurityException("Missing protected artifact entries");
        }
    }
}
