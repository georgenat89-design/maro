package dev.maro.runtime.settings;

import java.util.*;
import net.minecraft.nbt.NbtCompound;
import com.google.gson.*;
public final class Settings implements Iterable<SettingGroup> {
 private final List<SettingGroup> groups=new ArrayList<>();
 public Settings(){createGroup("General");}
 public SettingGroup getDefaultGroup(){return groups.getFirst();}
 public SettingGroup createGroup(String name){var group=new SettingGroup(name);groups.add(group);return group;}
 public SettingGroup createGroup(String name,boolean expanded){return createGroup(name);}
 @Override public Iterator<SettingGroup> iterator(){return groups.iterator();}
 public List<dev.maro.setting.SettingSection> sections(){var result=new ArrayList<dev.maro.setting.SettingSection>();for(var group:groups){var section=new dev.maro.setting.SettingSection(group.name);for(var s:group)section.add(SettingAdapters.adapt(s));result.add(section);}return result;}
 public NbtCompound toTag(){var tag=new NbtCompound();for(var group:groups)for(var s:group)tag.putString(s.name,SettingAdapters.adapt(s).toJson().toString());return tag;}
 public Settings fromTag(NbtCompound tag){for(var group:groups)for(var s:group)tag.getString(s.name).ifPresent(v -> {try{SettingAdapters.adapt(s).fromJson(JsonParser.parseString(v));}catch(RuntimeException ignored){}});return this;}
}
