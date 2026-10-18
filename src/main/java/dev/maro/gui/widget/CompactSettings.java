package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.setting.SettingSection;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/** Dropdown shares the spacious measured controls used by the Panel inspector. */
public final class CompactSettings {
    private final SettingsList settings=new SettingsList();
    public float render(ClickGuiScreen gui,DrawContext ctx,float x,float y,float width,List<SettingSection> sections){
        return settings.render(gui,ctx,x,y,width,sections,true);
    }
    public void resetPage(){settings.resetPage();}
    public void renderPaged(ClickGuiScreen gui,DrawContext ctx,float x,float y,float width,float height,List<SettingSection> sections){settings.renderPaged(gui,ctx,x,y,width,height,sections,true);}
}
