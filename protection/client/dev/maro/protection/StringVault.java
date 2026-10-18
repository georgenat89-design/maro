package dev.maro.protection;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
/** Obfuscates selected non-secret literals. Embedded decoder keys are recoverable by a determined analyst. */
public final class StringVault {
    public static String decode(String text,String key,String nonce){
        try{var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(nonce)));return new String(c.doFinal(Base64.getDecoder().decode(text)),StandardCharsets.UTF_8);}
        catch(Exception e){throw new SecurityException("Invalid protected resource");}
    }
}
