package dev.maro.protection.test;
import dev.maro.protection.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Real Windows vault; a random isolated HTTPS fixture origin prevents touching production credentials. */
public final class VaultChecks {
    public static void main(String[] args)throws Exception{
        String scope=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("https://localhost:8799".getBytes(StandardCharsets.UTF_8))).substring(0,24);
        String previous=CredentialVault.read(scope);
        try{
            CredentialVault.delete(scope);
            new LicenseClient();String saved=CredentialVault.read(scope);
            if(saved==null)throw new AssertionError("No vault record");
            new LicenseClient(); // reload validates private/public key binding
            JsonObject changed=JsonParser.parseString(saved).getAsJsonObject();changed.addProperty("private","not-a-private-key");CredentialVault.write(scope,changed.toString());
            try{new LicenseClient();throw new AssertionError("Tampered credential accepted");}catch(SecurityException expected){}
            if(CredentialVault.read(scope)!=null)throw new AssertionError("Tampered record was not removed");
            System.out.println("[credential-vault] PASS: Windows Credential Manager roundtrip, saved key reload, tampered key denial and removal; no plaintext file fallback");
        }finally{CredentialVault.delete(scope);if(previous!=null)CredentialVault.write(scope,previous);}
    }
}
