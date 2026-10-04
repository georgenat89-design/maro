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

    /** A section holding {@code settings}, in that order. */
    public static SettingSection of(String title, Setting<?>... settings) {
        SettingSection section = new SettingSection(title);
        for (Setting<?> setting : settings) section.add(setting);
        return section;
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
