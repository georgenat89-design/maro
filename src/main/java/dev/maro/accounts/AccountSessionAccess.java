package dev.maro.accounts;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.telemetry.TelemetryManager;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import java.util.concurrent.CompletableFuture;

/** All account-bound client services move together, on the client thread while disconnected. */
public interface AccountSessionAccess {
    record State(Session session,CompletableFuture<ProfileResult> profile,UserApiService api,
                 CompletableFuture<UserApiService.UserProperties> properties,ProfileKeys keys,
                 SocialInteractionsManager social,TelemetryManager telemetry,RealmsPeriodicCheckers realms,AbuseReportContext reports) { }
    State maro$captureAccount();
    void maro$applyAccount(State state);
    void maro$installAccount(Session session,ProfileResult profile,UserApiService api,UserApiService.UserProperties properties);
}
