package dev.maro.textures;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Local, data-only import of a ZIP selected by the player after downloading on PacksMC. */
public final class PackArchive {
    private PackArchive() { }
    public static Path install(Path source,String id,Path directory) throws Exception {
        if(!id.matches("[a-f0-9-]{36}")||!Files.isRegularFile(source)||Files.size(source)>256L*1024*1024)
            throw new IOException("Choose a resource-pack ZIP under 256 MB");
        try(var in=Files.newInputStream(source)){return install(in,id,directory,null);}
    }
    /** Caller owns the stream; the copy, hash and ZIP checks happen off the game thread. */
    public static Path install(InputStream source,String id,Path directory,String expectedHash) throws Exception {
        if(!id.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw new IOException("Invalid pack identity");
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,".maro-packsmc-",".part");
        try {
            // Bound the copy even if the selected file changes while importing.
            var digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try(var out=Files.newOutputStream(temp)) {
                byte[] buffer=new byte[65536];
                for(int n;(n=source.read(buffer))>=0;) {size+=n;if(size>256L*1024*1024)throw new IOException("ZIP exceeds 256 MB");out.write(buffer,0,n);digest.update(buffer,0,n);}
            }
            String hash=HexFormat.of().formatHex(digest.digest());
            if(expectedHash!=null&&!hash.equals(expectedHash))throw new IOException("Included pack failed its integrity check");
            validate(temp);
            Path target=directory.resolve("maro-packsmc-"+id+"-"+hash.substring(0,12)+".zip");
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {Files.deleteIfExists(temp);}
    }
    public static void validate(Path path) throws IOException {
        try(var zip=new ZipFile(path.toFile())) {
            var metadata=zip.getEntry("pack.mcmeta");
            if(metadata==null||metadata.isDirectory()||metadata.getSize()>65536)throw new IOException("ZIP needs pack.mcmeta at its root");
            try(var in=zip.getInputStream(metadata)) {
                var root=com.google.gson.JsonParser.parseString(new String(in.readNBytes(65537),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(!root.has("pack")||!root.get("pack").isJsonObject())throw new IOException("Invalid pack.mcmeta");
            } catch(RuntimeException e){throw new IOException("Invalid pack.mcmeta",e);}
            long total=0;int count=0;var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();String n=e.getName(),lower=n.toLowerCase(Locale.ROOT);
                if(n.startsWith("/")||n.contains("\\")||n.contains(":")||List.of(n.split("/")).contains("..")
                        ||lower.matches(".*\\.(class|jar|exe|dll|bat|cmd|ps1)$"))throw new IOException("ZIP contains an invalid resource entry");
                if(e.getSize()<0||++count>20000||(total+=e.getSize())>1024L*1024*1024)throw new IOException("Resource pack is too large");
            }
        }
    }
}
