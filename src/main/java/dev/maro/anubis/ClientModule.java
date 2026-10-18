package dev.maro.anubis;

import dev.maro.module.Category;
import dev.maro.anubis.setting.Setting;

/** Lifecycle and settings boundary for the GPL Anubis feature ports. */
public abstract class ClientModule extends dev.maro.module.Module {
    protected ClientModule(String id, String title, String description, Category category) {
        super(title, description, category);
    }
    protected <S extends Setting<?>> S addSetting(String id, S setting) {
        add(setting.view());
        return setting;
    }
    protected void usesNotifications() { }
    protected static dev.maro.setting.SettingSection section(String title, Setting<?>... settings) {
        var section=new dev.maro.setting.SettingSection(title);
        for(var setting:settings)section.add(setting.view());
        return section;
    }
    protected final void requestDisable() { setEnabled(false); }
}
