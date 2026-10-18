package dev.maro.accounts;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Explicit refresh-token exchange with Microsoft, Xbox and Minecraft. Secrets never enter diagnostics. */
public final class RefreshTokenClient {
    public record Tokens(String access,String refresh,String clientId,long expires) {
        @Override public String toString(){return "Tokens[hidden]";}
    }
    private final URI oauth,legacyOauth,xbox,xsts,minecraft;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public RefreshTokenClient(){
        oauth=URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
        legacyOauth=URI.create("https://login.live.com/oauth20_token.srf");
        xbox=URI.create("https://user.auth.xboxlive.com/user/authenticate");
        xsts=URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
        minecraft=URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    }
    RefreshTokenClient(URI base){
        if(!base.getScheme().equals("http")||!base.getHost().equals("127.0.0.1"))throw new IllegalArgumentException("Fixture must be loopback");
        oauth=base.resolve("/oauth");legacyOauth=oauth;xbox=base.resolve("/xbox");xsts=base.resolve("/xsts");minecraft=base.resolve("/minecraft");
    }
    public static String normalize(String token){
        String value=token.strip();
        if(value.length()<8||value.length()>16384||!value.matches("[\\x21-\\x7E]+"))
            throw new IllegalArgumentException("Paste a Microsoft refresh token");
        return value;
    }
    public static String clientId(String input){
        String value=input.strip();
        if(!value.matches("[A-Fa-f0-9]{16}|[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}"))
            throw new IllegalArgumentException("Enter the OAuth client ID that issued this token");
        return value;
    }
    public Tokens refresh(String input,String application) throws Exception {
        String refresh=normalize(input),id=clientId(application);
        String form="grant_type=refresh_token&client_id="+encode(id)+"&refresh_token="+encode(refresh);
        boolean legacy=id.matches("[A-Fa-f0-9]{16}");
        if(legacy)form+="&redirect_uri="+encode("https://login.live.com/oauth20_desktop.srf");
        var ms=post(legacy?legacyOauth:oauth,form,"application/x-www-form-urlencoded","Microsoft refresh failed. Check the token and its client ID");
        String access=secret(ms,"access_token");
        String rotated=ms.has("refresh_token")?normalize(secret(ms,"refresh_token")):refresh;
        var user=post(xbox,json(Map.of("Properties",Map.of("AuthMethod","RPS","SiteName","user.auth.xboxlive.com","RpsTicket","d="+access),
            "RelyingParty","http://auth.xboxlive.com","TokenType","JWT")),"application/json","Xbox sign-in failed. Check your Xbox account setup");
        var authorized=post(xsts,json(Map.of("Properties",Map.of("SandboxId","RETAIL","UserTokens",new String[]{secret(user,"Token")}),
            "RelyingParty","rp://api.minecraftservices.com/","TokenType","JWT")),"application/json","Xbox authorization failed. Check account and family permissions");
        String hash;
        try {hash=authorized.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
            if(!hash.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalStateException();}
        catch(RuntimeException ignored){throw new IllegalArgumentException("Invalid Xbox authorization response");}
        var mc=post(minecraft,json(Map.of("identityToken","XBL3.0 x="+hash+";"+secret(authorized,"Token"))),"application/json",
            "Minecraft sign-in failed. Check Java Edition access for this account");
        long expires;
        try {expires=System.currentTimeMillis()+Math.multiplyExact(Math.clamp(mc.get("expires_in").getAsLong(),1,86400),1000L);}
        catch(RuntimeException ignored){throw new IllegalArgumentException("Invalid Minecraft login response");}
        return new Tokens(SessionTokenClient.normalize(secret(mc,"access_token")),rotated,id,expires);
    }
    private JsonObject post(URI endpoint,String body,String type,String error) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(18)).header("Content-Type",type)
            .header("Accept","application/json").header("x-xbl-contract-version","1")
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()){
            if(response.statusCode()!=200)throw new IllegalArgumentException(response.statusCode()==429?"Too many login requests. Try again shortly":error);
            byte[] bytes=stream.readNBytes(65537);
            if(bytes.length>65536)throw new IllegalArgumentException("Login response too large");
            try {return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
            catch(RuntimeException ignored){throw new IllegalArgumentException("Invalid login response");}
        }
    }
    private static String secret(JsonObject json,String key){
        try {String value=json.get(key).getAsString();if(value.isBlank()||value.length()>32768)throw new IllegalStateException();return value;}
        catch(RuntimeException ignored){throw new IllegalArgumentException("Incomplete login response");}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String json(Object value){return new Gson().toJson(value);}
}
