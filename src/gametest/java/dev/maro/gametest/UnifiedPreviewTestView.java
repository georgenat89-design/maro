package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.VisualPreview;
import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.gui.hud.SkinAccessoriesScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import net.minecraft.client.MinecraftClient;

/** Legacy model/material regression fixtures now present through the single production preview. */
final class UnifiedPreviewTestView {
    static ClickGuiScreen open(MinecraftClient client,Module module){var gui=new ClickGuiScreen();client.setScreen(gui);gui.openModuleOptions(module);gui.selectPreviewTab(true);return gui;}
    private static ClickGuiScreen gui(MinecraftClient client){
        if(client.currentScreen instanceof CustomModelScreen wardrobe)wardrobe.openUnifiedPreview();
        else if(client.currentScreen instanceof SkinAccessoriesScreen assets)assets.openUnifiedPreview();
        if(!(client.currentScreen instanceof ClickGuiScreen gui))throw new AssertionError("No unified preview is open");return gui;
    }
    static void angle(MinecraftClient client,float degrees){
        VisualPreview preview=gui(client).visualPreview();
        try{var field=VisualPreview.class.getDeclaredField("angle");field.setAccessible(true);field.setFloat(preview,degrees);var turn=VisualPreview.class.getDeclaredField("turn");turn.setAccessible(true);((dev.maro.util.Animation)turn.get(preview)).snap(degrees);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    static void other(MinecraftClient client,boolean other){gui(client).visualPreview().selectModelTarget(ModuleManager.get(CustomModel.class),other);}
    static int[] bounds(MinecraftClient client){
        var gui=gui(client);float[] box=gui.visualPreview().contentBounds();if(box==null)throw new AssertionError("Unified preview has no rendered content bounds");
        double[] top=gui.screenPose().project(box[0],box[1]),bottom=gui.screenPose().project(box[0]+box[2],box[1]+box[3]);double scale=client.getWindow().getScaleFactor();
        return new int[]{(int)Math.floor(top[0]*scale),(int)Math.floor(top[1]*scale),(int)Math.ceil(bottom[0]*scale),(int)Math.ceil(bottom[1]*scale)};
    }
}
