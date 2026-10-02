package dev.maro.setting;

import java.util.ArrayList;
import java.util.List;

/** A titled group of settings, rendered as one block in the GUI. */
public class SettingSection {
    private final String title;
    private final List<Setting<?>> settings = new ArrayList<>();

    public SettingSection(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }
}
