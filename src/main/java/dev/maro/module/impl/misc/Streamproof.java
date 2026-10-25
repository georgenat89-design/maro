package dev.maro.module.impl.misc;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.capture.CleanCapture;
import dev.maro.render.capture.StreamproofScenePolicy;
import dev.maro.setting.*;
import java.util.List;

/** Presents clean Minecraft with private visuals in an attached, capture-excluded native layer. */
public final class Streamproof extends Module {
    private static Streamproof instance;
    public Streamproof(){super("Streamproof","Keep private visuals on your main screen while sharing normal Minecraft with a clean view",Category.MISC);instance=this;}
    public static Streamproof get(){return instance;}
    public static boolean hideChat(){return true;}
    public String status(){return CleanCapture.status()+" · "+StreamproofScenePolicy.status();}
    @Override public net.minecraft.client.gui.screen.Screen panel(net.minecraft.client.gui.screen.Screen parent){return new dev.maro.gui.hud.StreamproofScreen(parent,this);}
    @Override public List<SettingSection> getSettingSections(){return List.of();}
    @Override public boolean persistEnabled(){return false;}
    @Override protected void onEnable(){StreamproofScenePolicy.enable();}
    @Override protected void onDisable(){CleanCapture.close();StreamproofScenePolicy.disable();}
}
