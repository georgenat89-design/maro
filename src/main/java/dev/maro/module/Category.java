package dev.maro.module;

import dev.maro.gui.render.Icons;

/** Sidebar categories. Add, remove or rename freely - the GUI adapts. */
public enum Category {
    COMBAT("Combat", Icons.COMBAT),
    MOVEMENT("Movement", Icons.MOVEMENT),
    PLAYER("Player", Icons.PLAYER),
    VISUALS("Visuals", Icons.VISUALS),
    MISC("Misc", Icons.MISC),
    ANTI_CHEAT_OFF("Anti-Cheat Off", Icons.LOCK);

    private final String displayName;
    private final Icons.Icon icon;

    Category(String displayName, Icons.Icon icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Icons.Icon getIcon() {
        return icon;
    }
}
