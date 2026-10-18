package dev.maro.mixin;

import dev.maro.accounts.AccountSessionAccess;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.telemetry.TelemetryManager;
import net.minecraft.client.session.report.*;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.*;
import org.spongepowered.asm.mixin.*;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class AccountSessionMixin implements AccountSessionAccess {
    @Shadow @Final @Mutable private Session session;
    @Shadow @Final @Mutable private CompletableFuture<ProfileResult> gameProfileFuture;
    @Shadow @Final @Mutable private UserApiService userApiService;
    @Shadow @Final @Mutable private CompletableFuture<UserApiService.UserProperties> userPropertiesFuture;
    @Shadow @Final @Mutable private ProfileKeys profileKeys;
    @Shadow @Final @Mutable private SocialInteractionsManager socialInteractionsManager;
    @Shadow @Final @Mutable private TelemetryManager telemetryManager;
    @Shadow @Final @Mutable private RealmsPeriodicCheckers realmsPeriodicCheckers;
    @Shadow private AbuseReportContext abuseReportContext;
    public State maro$captureAccount(){return new State(session,gameProfileFuture,userApiService,userPropertiesFuture,profileKeys,socialInteractionsManager,telemetryManager,realmsPeriodicCheckers,abuseReportContext);}
    private void maro$guard(){
        var client=(MinecraftClient)(Object)this;
        if(!client.isOnThread() || client.world!=null || client.getNetworkHandler()!=null)throw new IllegalStateException("Return to the title screen to switch accounts");
    }
    public void maro$applyAccount(State state){
        maro$guard();session=state.session();gameProfileFuture=state.profile();userApiService=state.api();userPropertiesFuture=state.properties();
        profileKeys=state.keys();socialInteractionsManager=state.social();telemetryManager=state.telemetry();realmsPeriodicCheckers=state.realms();abuseReportContext=state.reports();
        AccountRealmsAccessor.maro$setInstance(null);
    }
    public void maro$installAccount(Session next,ProfileResult profile,UserApiService api,UserApiService.UserProperties properties){
        maro$guard();var client=(MinecraftClient)(Object)this;
        // Construct account services before publishing the new identity.
        var keys=ProfileKeys.create(api,next,client.runDirectory.toPath());
        var social=new SocialInteractionsManager(client,api);
        var telemetry=new TelemetryManager(client,api,next);
        var reports=AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(),api);
        session=next;gameProfileFuture=CompletableFuture.completedFuture(profile);userApiService=api;
        userPropertiesFuture=CompletableFuture.completedFuture(properties);profileKeys=keys;socialInteractionsManager=social;
        telemetryManager=telemetry;abuseReportContext=reports;
        AccountRealmsAccessor.maro$setInstance(null);
        realmsPeriodicCheckers=new RealmsPeriodicCheckers(RealmsClient.createRealmsClient(client));
    }
}
