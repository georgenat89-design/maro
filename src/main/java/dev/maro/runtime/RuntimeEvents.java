package dev.maro.runtime;

import dev.maro.runtime.events.game.*;
import dev.maro.runtime.events.meteor.*;
import dev.maro.runtime.events.render.*;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.renderer.Renderer2D;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.misc.Keybind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;

/** Minecraft/Fabric hooks feeding the imported modules on the client thread. */
public final class RuntimeEvents {
    private static ClientWorld lastWorld;
    private static long lastHudFrame,lastWorldFrame;
    public static void tickPre(){
        var client=MinecraftClient.getInstance();
        if(lastWorld!=client.world){
            if(lastWorld!=null)MeteorClient.EVENT_BUS.post(new GameLeftEvent());
            lastWorld=client.world;
            if(lastWorld!=null)MeteorClient.EVENT_BUS.post(new GameJoinedEvent());
        }
        syncBinds();
        MeteorClient.EVENT_BUS.post(new TickEvent.Pre());
    }
    public static void tickPost(){MeteorClient.EVENT_BUS.post(new TickEvent.Post());}
    private static void syncBinds(){for(var module:Modules.get().getAll())module.keybind=Keybind.fromCode(module.getBind().get());}
    public static void hud(DrawContext context){
        long now=System.nanoTime();double delta=lastHudFrame==0?1.0/60:Math.min(.1,(now-lastHudFrame)/1e9);lastHudFrame=now;
        Renderer2D.context(context);
        try{MeteorClient.EVENT_BUS.post(new Render2DEvent(context,delta));}
        finally{Renderer2D.context(null);}
    }
    public static void worldFrame(){long now=System.nanoTime();double delta=lastWorldFrame==0?1.0/60:Math.min(.1,(now-lastWorldFrame)/1e9);lastWorldFrame=now;MeteorClient.EVENT_BUS.post(new Render3DEvent(delta));}
    public static boolean key(KeyInput input,int action){
        syncBinds();
        var event=MeteorClient.EVENT_BUS.post(new KeyEvent(input,action));
        actionBind(input.key(),action);
        return event.isCancelled();
    }
    public static boolean mouse(MouseInput input,int action){
        syncBinds();
        var event=MeteorClient.EVENT_BUS.post(new MouseClickEvent(input,action));
        actionBind(dev.maro.util.KeyUtil.mouse(input.button()),action);
        return event.isCancelled();
    }
    private static void actionBind(int code,int action){
        var client=MinecraftClient.getInstance();
        boolean controls=client.currentScreen instanceof dev.maro.nathan.gui.SpotifyControlsScreen;
        if(action==GLFW.GLFW_RELEASE){
            for(var module:Modules.get().getAll())if(module.toggleOnBindRelease&&module.isActive()&&module.getBind().matches(code))module.setEnabled(false);
        }
        if(action!=GLFW.GLFW_PRESS||client.player==null||(client.currentScreen!=null&&!controls))return;
        for(var module:Modules.get().getAll()){
            if(!module.isActive())continue;
            for(var group:module.settings)for(Setting<?> setting:group){
                if(setting.action!=null&&setting.get() instanceof Keybind key&&key.isSet()&&key.code()==code)setting.action.run();
            }
        }
    }
    public static void packet(Packet<?> packet){
        var client=MinecraftClient.getInstance();
        if(!client.isOnThread())return; // The packet handler repeats after forceMainThread.
        MeteorClient.EVENT_BUS.post(new dev.maro.runtime.events.packets.PacketEvent.Receive(packet));
    }
    public static void shutdown(){for(var module:dev.maro.module.ModuleManager.all())if(module.isEnabled())module.setEnabled(false);dev.maro.runtime.renderer.Texture.endFrame();}
}
