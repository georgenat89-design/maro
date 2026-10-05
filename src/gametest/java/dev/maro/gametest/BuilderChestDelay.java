package dev.maro.gametest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import java.util.*;

/** Delay the initial contents after OpenScreen, including genuinely empty double chests. */
public final class BuilderChestDelay {
    private record Held(ScreenHandler handler,InventoryS2CPacket packet,int due){}
    private static final Set<ScreenHandler> delivered=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Held> held=new ArrayList<>();
    private static boolean enabled,replaying;
    private static int ticks,delay;
    public static int received;
    public static void begin(int wait){enabled=true;replaying=false;ticks=received=0;delay=wait;delivered.clear();held.clear();}
    public static void end(){enabled=false;held.clear();delivered.clear();}
    public static boolean intercept(InventoryS2CPacket packet){
        var client=MinecraftClient.getInstance();
        if(!enabled||replaying||!client.isOnThread()||client.player==null)return false;
        var handler=client.player.currentScreenHandler;
        if(!(handler instanceof GenericContainerScreenHandler chest)||chest.getRows()!=6||packet.syncId()!=handler.syncId||delivered.contains(handler))return false;
        if(held.stream().noneMatch(entry->entry.handler()==handler))held.add(new Held(handler,packet,ticks+delay));
        return true;
    }
    public static void step(){
        ticks++;var client=MinecraftClient.getInstance();
        for(var iterator=held.iterator();iterator.hasNext();){var entry=iterator.next();if(entry.due()>ticks)continue;
            iterator.remove();delivered.add(entry.handler());
            if(client.player.currentScreenHandler!=entry.handler())throw new AssertionError("Builder closed a chest before receiving its inventory");
            replaying=true;try{entry.packet().apply(client.getNetworkHandler());received++;}finally{replaying=false;}
        }
    }
}
