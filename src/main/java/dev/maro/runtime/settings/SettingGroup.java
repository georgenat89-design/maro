package dev.maro.runtime.settings;

import java.util.*;
public final class SettingGroup implements Iterable<Setting<?>> {
 public final String name; private final List<Setting<?>> settings = new ArrayList<>();
 SettingGroup(String name){this.name=name;}
 public <T> Setting<T> add(Setting<T> setting){settings.add(setting);return setting;}
 @Override public Iterator<Setting<?>> iterator(){return settings.iterator();}
}
