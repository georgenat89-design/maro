package dev.maro.protection;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Windows Credential Manager only. No file/plaintext fallback on failure or other operating systems. */
public final class CredentialVault {
    private static final String TARGET="maro.gg/protected-owner/v1";
    private static WString target(String scope){if(!scope.matches("[a-f0-9]{24}"))throw new IllegalArgumentException();return new WString(TARGET+"/"+scope);}
    public interface Api extends StdCallLibrary {
        Api INSTANCE=Native.load("Advapi32",Api.class);
        boolean CredWriteW(Cred c,int flags);
        boolean CredReadW(WString target,int type,int flags,PointerByReference value);
        boolean CredDeleteW(WString target,int type,int flags);
        void CredFree(Pointer value);
    }
    @Structure.FieldOrder({"flags","type","target","comment","written","size","blob","persist","count","attributes","alias","user"})
    public static class Cred extends Structure {
        public int flags,type; public WString target,comment;public long written; public int size;
        public Pointer blob;public int persist,count;public Pointer attributes;public WString alias,user;
        public Cred(){}public Cred(Pointer p){super(p);read();}
    }
    private static void windows(){if(!Platform.isWindows())throw new IllegalStateException("This protected build requires Windows Credential Manager");}
    public static String read(String scope){
        windows();var out=new PointerByReference();
        if(!Api.INSTANCE.CredReadW(target(scope),1,0,out)){
            if(Native.getLastError()==1168)return null;throw new IllegalStateException("Credential Manager unavailable");
        }
        try{var c=new Cred(out.getValue());if(c.size<1||c.size>2560)throw new IllegalStateException("Invalid saved credential");return new String(c.blob.getByteArray(0,c.size),StandardCharsets.UTF_8);}
        finally{Api.INSTANCE.CredFree(out.getValue());}
    }
    public static void write(String scope,String value){
        windows();byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>2560)throw new IllegalArgumentException();
        try(var mem=new Memory(bytes.length)){
            mem.write(0,bytes,0,bytes.length);var c=new Cred();c.type=1;c.target=target(scope);c.user=new WString("navo");c.size=bytes.length;c.blob=mem;c.persist=2;c.write();
            try{if(!Api.INSTANCE.CredWriteW(c,0))throw new IllegalStateException("Credential Manager write failed");}finally{mem.clear();}
        }finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
    public static void delete(String scope){windows();if(!Api.INSTANCE.CredDeleteW(target(scope),1,0)&&Native.getLastError()!=1168)throw new IllegalStateException("Credential Manager delete failed");}
}
