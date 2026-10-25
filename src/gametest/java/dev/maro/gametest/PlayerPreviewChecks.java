package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.PlayerVisualCapabilities;
import dev.maro.gui.VisualPreview;
import dev.maro.gui.render.VisualPreviewEffect;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SkinChanger;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.module.impl.visuals.Emotes;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.render.character.CharacterHolder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EquipmentSlot;

/** Native portraits, selection/layout isolation and immediate model selection changes. */
public final class PlayerPreviewChecks {
    private PlayerPreviewChecks() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ClickGuiScreen gui(MinecraftClient c){return (ClickGuiScreen)c.currentScreen;}
    private static void settle(ClientGameTestContext context){context.waitTicks(14);}

    public static void run(ClientGameTestContext context) {
        var model=ModuleManager.get(CustomModel.class);
        var cosmetics=ModuleManager.get(SkinAccessories.class);
        var skin=ModuleManager.get(SkinChanger.class);
        var esp=ModuleManager.get(PlayerESP.class);
        var emotes=ModuleManager.get(Emotes.class);
        boolean spatial=ClientSettings.spatialMenu.get(),screenGui=ClientSettings.screenGui.get();
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        String originalModel=model.selected(false);
        var oldScreen=context.computeOnClient(c->c.currentScreen);
        try {
            context.runOnClient(c->{ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.setScreen(new ClickGuiScreen());});
            for(Module target:java.util.List.of(model,cosmetics,skin,esp,emotes)) {
                require(PlayerVisualCapabilities.supports(target),"Missing supported player appearance: "+target.getName());
                context.runOnClient(c->gui(c).openModuleOptions(target));settle(context);
                context.runOnClient(c->{
                    var preview=gui(c).visualPreview();var state=preview.lastState();
                    require(state!=null&&state.id==VisualPreview.ENTITY_ID,"Player portrait was not rendered for "+target.getName());
                    var bounds=preview.bounds();var settings=gui(c).settingsBounds();
                    require(bounds!=null&&settings!=null&&bounds[0]+bounds[2]<=settings[0],"Player portrait overlaps settings");
                    require(bounds[0]>=0&&settings[0]+settings[2]<=gui(c).menuWidth(),"Player layout extends beyond canvas");
                    require(state.equippedHeadStack.getItem()==c.player.getEquippedStack(EquipmentSlot.HEAD).getItem(),"Portrait lost equipped helmet");
                    var worldState=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getRenderer(c.player).getAndUpdateRenderState(c.player,1);
                    require(worldState!=state&&worldState.id==c.player.getId(),"Portrait edits vanilla's shared player render state");
                });
            }
            require(context.computeOnClient(c->VisualPreviewEffect.frames()>0),"Player ESP portrait did not run the real silhouette effect");

            // Disabled Custom Model still previews its choice, without changing enable state or entity data.
            boolean on=model.isEnabled();
            context.runOnClient(c->{gui(c).openModuleOptions(model);model.select(false,"None");});settle(context);
            require(context.computeOnClient(c->((CharacterHolder)gui(c).visualPreview().lastState()).maro$character()==null),"None model did not update portrait");
            context.runOnClient(c->model.select(false,"SpongeBob"));
            for(int i=0;i<1000&&!context.computeOnClient(c->model.ready("SpongeBob")!=null);i++)context.waitTicks(1);
            settle(context);
            require(context.computeOnClient(c->((CharacterHolder)gui(c).visualPreview().lastState()).maro$character()!=null),"Selected custom model did not update portrait");
            require(model.isEnabled()==on,"Preview changed the module enable state");

            // Each GUI scale and a narrow window use the same non-overlapping complete layout.
            for(int guiScale:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();});settle(context);
                context.takeScreenshot("maro-player-preview-scale-"+guiScale);
                context.runOnClient(c->{var b=gui(c).visualPreview().bounds();var p=gui(c).settingsBounds();require(b!=null&&b[0]>=0&&b[0]+b[2]<=p[0],"Portrait clipped at GUI scale "+guiScale);});
            }
            context.getInput().resizeWindow(960,640);settle(context);
            context.takeScreenshot("maro-player-preview-narrow");
            context.runOnClient(c->{var b=gui(c).visualPreview().bounds();var p=gui(c).settingsBounds();require(b!=null&&b[0]>=0&&p[0]+p[2]<=gui(c).menuWidth(),"Narrow portrait layout clipped");});

            for(String name:java.util.List.of("Custom Sky","Fullbright","Nametags","Jump Circle","Freecam","Coordinates")){
                var target=ModuleManager.getByName(name);if(target==null)continue;
                require(!PlayerVisualCapabilities.supports(target),"Unrelated module gained a player portrait: "+name);
                context.runOnClient(c->gui(c).openModuleOptions(target));settle(context);
                require(context.computeOnClient(c->gui(c).visualPreview().lastState()==null),"Unrelated module rendered a player model: "+name);
            }
            context.runOnClient(c->gui(c).closeSettings());settle(context);
            require(context.computeOnClient(c->gui(c).visualPreview().lastState()==null),"Portrait remained after closing settings");
            System.out.println("[player-preview] explicit capability mapping, live selection, equipment, private render state, GUI scales 2/3/4 and narrow layout passed");
        } finally {
            context.runOnClient(c->{model.select(false,originalModel);ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screenGui);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(oldScreen);});
            context.getInput().resizeWindow(1280,720);
        }
    }
}
