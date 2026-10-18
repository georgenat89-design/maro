package dev.maro.protection;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Duration;
import java.util.*;

/** Network and cryptography run off the render thread. Public trust comes from the signed release. */
public final class LicenseClient {
    private static final Gson G=new Gson();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonObject trust;
    private final String origin;
    private final String scope;
    private PrivateKey privateKey;private PublicKey publicKey;private String rawPublic,refresh="",access="";
    private long verifiedAt,expiry,lastWall,offlineSeconds; private volatile boolean verified;private volatile long deadline;
    private volatile boolean licenseActivated;
    public volatile String status="Owner authentication required";
    public LicenseClient() throws Exception {
        try(var stream=LicenseClient.class.getResourceAsStream("/maro-license-public.json")){
            if(stream==null)throw new SecurityException("Missing release trust configuration");
            trust=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
        origin=trust.get("origin").getAsString();URI u=URI.create(origin);
        if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||!u.getPath().isEmpty()||u.getQuery()!=null||u.getFragment()!=null)throw new SecurityException("Invalid service origin");
        scope=hash(origin.getBytes(StandardCharsets.UTF_8)).substring(0,24);
        var stored=CredentialVault.read(scope);
        if(stored!=null){
            try{var s=JsonParser.parseString(stored).getAsJsonObject();privateKey=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(s.get("private").getAsString())));publicKey=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(s.get("public").getAsString())));refresh=s.get("refresh").getAsString();
                byte[] test="vault-binding".getBytes(StandardCharsets.UTF_8);Signature sig=Signature.getInstance("Ed25519");sig.initSign(privateKey);sig.update(test);byte[] signed=sig.sign();sig.initVerify(publicKey);sig.update(test);if(!sig.verify(signed))throw new SecurityException();
            }catch(Exception e){CredentialVault.delete(scope);throw new SecurityException("Saved credential was invalid; sign in again");}
        }else{var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();privateKey=keys.getPrivate();publicKey=keys.getPublic();save();}
        byte[] der=publicKey.getEncoded();rawPublic=b64(Arrays.copyOfRange(der,der.length-32,der.length));
    }
    private void save(){var s=new JsonObject();s.addProperty("private",Base64.getEncoder().encodeToString(privateKey.getEncoded()));s.addProperty("public",Base64.getEncoder().encodeToString(publicKey.getEncoded()));s.addProperty("refresh",refresh);CredentialVault.write(scope,s.toString());}
    private static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    private String proof(String path,byte[] body,String credential)throws Exception{
        var h=new JsonObject();h.addProperty("alg","EdDSA");h.addProperty("typ","maro-request+jwt");
        var p=new JsonObject();p.addProperty("iat",System.currentTimeMillis()/1000);p.addProperty("jti",UUID.randomUUID().toString());p.addProperty("method","POST");p.addProperty("path",path);p.addProperty("body",hash(body));p.addProperty("credential",hash(credential.getBytes(StandardCharsets.UTF_8)));
        String input=b64(h.toString().getBytes(StandardCharsets.UTF_8))+"."+b64(p.toString().getBytes(StandardCharsets.UTF_8));var sig=Signature.getInstance("Ed25519");sig.initSign(privateKey);sig.update(input.getBytes(StandardCharsets.US_ASCII));return input+"."+b64(sig.sign());
    }
    private JsonObject post(String path,JsonObject data,String credential,boolean bearer)throws Exception{
        byte[] body=G.toJson(data).getBytes(StandardCharsets.UTF_8);
        var req=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(6)).header("Content-Type","application/json").header("X-Maro-Proof",proof(path,body,credential)).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if(bearer)req.header("Authorization","Bearer "+credential);
        var response=http.send(req.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()){
            if(response.statusCode()!=200){
                if(response.statusCode()>=500)throw new java.io.IOException("Licensing service temporarily unavailable");
                verified=false;deadline=0;
                if(response.statusCode()==401){refresh="";access="";save();}
                throw new SecurityException(switch(response.statusCode()){case 401->"Invalid credentials, used MFA, or expired session";case 403->"Access denied: license, activation key, or device permissions";case 429->"Too many attempts; wait before retrying";default->"Licensing service rejected the request";});
            }
            byte[] bytes=in.readNBytes(32769);if(bytes.length>32768)throw new SecurityException("Invalid service response");return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public synchronized void login(String password,String otp)throws Exception{
        verified=false;deadline=0;var p=new JsonObject();p.addProperty("username","navo");p.addProperty("password",password);p.addProperty("otp",otp);p.addProperty("public_key",rawPublic);accept(post("/v1/login",p,"",false),true);
    }
    public synchronized void activate(String key)throws Exception{verified=false;deadline=0;var p=new JsonObject();p.addProperty("activation_key",key);accept(post("/v1/activate",p,access,true),false);}
    public synchronized void renew()throws Exception{
        try{
            if(refresh.isEmpty())return;
            if(access.isEmpty()||expiry-System.currentTimeMillis()/1000<90){var p=new JsonObject();p.addProperty("refresh_token",refresh);accept(post("/v1/refresh",p,refresh,false),true);}
            else accept(post("/v1/lease",new JsonObject(),access,true),false);
        }catch(java.io.IOException|InterruptedException e){
            long max=Math.min(offlineSeconds,trust.get("offline_max_seconds").getAsLong());
            if(!verified||max<=0||System.nanoTime()-verifiedAt>Duration.ofSeconds(max).toNanos()){verified=false;deadline=0;}
            else deadline=Math.min(deadline,verifiedAt+Duration.ofSeconds(max).toNanos());
            status="Licensing service unreachable; protected features locked";throw e;
        }catch(Exception e){verified=false;deadline=0;throw e;}
    }
    private void accept(JsonObject response,boolean rotate)throws Exception{
        String token=response.get("access_token").getAsString();String[] parts=token.split("\\.",-1);if(parts.length!=3)throw new SecurityException("Invalid license");
        JsonObject header=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8)).getAsJsonObject();
        if(!header.get("alg").getAsString().equals("EdDSA")||!header.get("typ").getAsString().equals("maro-license+jwt"))throw new SecurityException("Invalid license signature algorithm");
        String kid=header.get("kid").getAsString();var keys=trust.getAsJsonObject("keys");if(!keys.has(kid))throw new SecurityException("Untrusted license signing key");
        String pem=keys.get(kid).getAsString().replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");
        var key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));var sig=Signature.getInstance("Ed25519");sig.initVerify(key);sig.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));if(!sig.verify(Base64.getUrlDecoder().decode(parts[2])))throw new SecurityException("Invalid license signature");
        JsonObject p=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8)).getAsJsonObject();long t=System.currentTimeMillis()/1000;long exp=p.get("exp").getAsLong();
        if(!p.get("iss").getAsString().equals(origin)||!p.get("aud").getAsString().equals("maro-protected")||exp<=t||exp-t>300||p.get("nbf").getAsLong()>t||p.get("iat").getAsLong()>t+5||!p.getAsJsonObject("cnf").get("key_sha256").getAsString().equals(hash(rawPublic.getBytes(StandardCharsets.UTF_8))))throw new SecurityException("License claims invalid");
        for(String field:List.of("sub","lid","sid","did","jti"))UUID.fromString(p.get(field).getAsString());
        if(p.get("offline_seconds").getAsLong()<0||p.get("offline_seconds").getAsLong()>trust.get("offline_max_seconds").getAsLong())throw new SecurityException("Invalid offline policy");
        boolean use=false;for(var permission:p.getAsJsonArray("permissions"))if(permission.getAsString().equals("client:use"))use=true;
        if(rotate){String next=response.get("refresh_token").getAsString();if(next.length()<40||next.length()>256)throw new SecurityException("Invalid session credential");refresh=next;save();}
        access=token;expiry=exp;offlineSeconds=p.get("offline_seconds").getAsLong();verifiedAt=System.nanoTime();lastWall=System.currentTimeMillis();deadline=verifiedAt+Duration.ofSeconds(exp-t).toNanos();licenseActivated=use;verified=use;status=use?"Owner license verified":"Signed in. Enter the owner activation key.";
    }
    public boolean allowed(){long wall=System.currentTimeMillis();return verified&&System.nanoTime()<deadline&&wall/1000<expiry&&wall>=lastWall-1000;}
    public boolean activationNeeded(){return !access.isEmpty()&&expiry>System.currentTimeMillis()/1000&&!licenseActivated;}
    public boolean hasSavedSession(){return !refresh.isEmpty();}
    public boolean testBuild(){return trust.has("test_build")&&trust.get("test_build").getAsBoolean();}
    public synchronized void logout()throws Exception{try{if(!access.isEmpty())post("/v1/logout",new JsonObject(),access,true);}finally{verified=false;deadline=0;access="";refresh="";CredentialVault.delete(scope);status="Signed out; local device key removed";}}
    public synchronized void resetDevices(String password,String otp)throws Exception{var p=new JsonObject();p.addProperty("password",password);p.addProperty("otp",otp);post("/v1/devices/reset",p,access,true);status="Other devices revoked";}
}
