package dev.maro.accounts;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Verifies a deliberately supplied Minecraft access token with the official profile service. */
public final class SessionTokenClient {
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public SessionTokenClient() { endpoint=URI.create("https://api.minecraftservices.com/minecraft/profile"); }
    SessionTokenClient(URI fixture) {
        if (!"http".equals(fixture.getScheme()) || !"127.0.0.1".equals(fixture.getHost())) throw new IllegalArgumentException("Fixture must be loopback");
        endpoint=fixture;
    }
    public static String normalize(String input) {
        String value=input.strip();
        if(value.regionMatches(true,0,"Bearer ",0,7))value=value.substring(7).strip();
        if(value.startsWith("token:")) {
            int colon=value.lastIndexOf(':');
            if(colon<=6)throw new IllegalArgumentException("Invalid session format");
            uuid(value.substring(colon+1)); value=value.substring(6,colon);
        }
        if(value.length()<8 || value.length()>16384 || !value.matches("[A-Za-z0-9._~+/-]+={0,2}"))
            throw new IllegalArgumentException("Paste a Minecraft access token or session ID");
        return value;
    }
    public static UUID uuid(String value) {
        if(value.matches("[a-fA-F0-9]{32}")) value=value.substring(0,8)+"-"+value.substring(8,12)+"-"+value.substring(12,16)+"-"+value.substring(16,20)+"-"+value.substring(20);
        if(!value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw new IllegalArgumentException("Invalid profile ID");
        return UUID.fromString(value);
    }
    public GameProfile verify(String token) throws Exception {
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(18))
                .header("Authorization","Bearer "+token).header("Accept","application/json").GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            switch(response.statusCode()) {
                case 200 -> { }
                case 401 -> throw new IllegalArgumentException("Session expired or invalid. Add a fresh token");
                case 403 -> throw new IllegalArgumentException("Minecraft rejected this session");
                case 404 -> throw new IllegalArgumentException("This token has no Minecraft Java profile");
                case 429 -> throw new IllegalArgumentException("Too many requests. Try again shortly");
                default -> throw new IllegalArgumentException("Minecraft login service unavailable. Try again");
            }
            byte[] bytes=body.readNBytes(65537);
            if(bytes.length>65536)throw new IllegalArgumentException("Invalid profile response");
            var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            String name=json.get("name").getAsString();
            if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid Minecraft profile");
            return new GameProfile(uuid(json.get("id").getAsString()),name);
        }
    }
    /** Expiry is only a display hint. Identity and validity always come from the profile service. */
    public static long expiry(String token) {
        try {
            var claims=JsonParser.parseString(new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),StandardCharsets.UTF_8)).getAsJsonObject();
            return claims.has("exp") ? Math.multiplyExact(claims.get("exp").getAsLong(),1000L) : 0;
        } catch(Exception ignored) { return 0; }
    }
}
