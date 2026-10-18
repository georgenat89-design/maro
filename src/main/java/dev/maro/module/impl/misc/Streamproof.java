package dev.maro.module.impl.misc;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.capture.CleanCapture;
import dev.maro.setting.*;
import java.util.List;

/** Presents clean Minecraft with private visuals in an attached, capture-excluded native layer. */
public final class Streamproof extends Module {
    private static Streamproof instance;
    private final NumberSetting fps=add(new NumberSetting("Capture FPS","A second world render costs GPU time; lower this to save performance",30,15,60,5));
    private final BooleanSetting chat=add(new BooleanSetting("Hide Chat","Keep chat messages and client command replies out of the clean view",true));
    public Streamproof(){super("Streamproof","Keep private visuals on your main screen while sharing normal Minecraft with a clean view",Category.MISC);instance=this;}
    public static Streamproof get(){return instance;}
    public int captureFps(){return fps.getInt();}
    public static boolean hideChat(){return instance!=null&&instance.chat.get();}
    public String status(){return CleanCapture.status();}
    @Override public net.minecraft.client.gui.screen.Screen panel(net.minecraft.client.gui.screen.Screen parent){return new dev.maro.gui.hud.StreamproofScreen(parent,this);}
    @Override public List<SettingSection> getSettingSections(){return List.of(SettingSection.of("Main-screen Streamproof",fps,chat));}
    @Override public boolean persistEnabled(){return false;}
    @Override protected void onDisable(){CleanCapture.close();}
}
