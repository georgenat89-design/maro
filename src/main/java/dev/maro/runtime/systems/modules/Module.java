package dev.maro.runtime.systems.modules;

import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.settings.Settings;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.runtime.gui.*;
import dev.maro.runtime.gui.widgets.WWidget;
import net.minecraft.nbt.NbtCompound;
import java.util.*;
/** Nathan lifecycle translated into Maro modules, settings and config files. */
public abstract class Module extends dev.maro.module.Module {
 public final String name,title;
 public final Settings settings=new Settings();
 public Keybind keybind=Keybind.none();
 public boolean toggleOnBindRelease, runInMainMenu;
 private List<dev.maro.setting.SettingSection> sections;
 protected Module(Category ignored,String name,String description){super(title(name),description,category(name));this.name=name;title=title(name);}
 private static String title(String name){if(name.equals("autoowalk"))return "Auto Walk";return Arrays.stream(name.split("-")).map(s->s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1)).reduce((a,b)->a+" "+b).orElse(name);}
 private static dev.maro.module.Category category(String name){return switch(name){case "autoowalk","free-cam","free-look","freelook"->dev.maro.module.Category.MOVEMENT;case "fast-xp","smart-eat","spawner-protect","auto-relog"->dev.maro.module.Category.PLAYER;case "chat-macros","key-sounds"->dev.maro.module.Category.MISC;default->dev.maro.module.Category.VISUALS;};}
 public boolean isActive(){return isEnabled();}
 public String getInfoString(){return null;}
 public void onActivate(){} public void onDeactivate(){}
 @Override protected final void onEnable(){MeteorClient.EVENT_BUS.subscribe(this);onActivate();}
 @Override protected final void onDisable(){MeteorClient.EVENT_BUS.unsubscribe(this);onDeactivate();}
 public WWidget getWidget(GuiTheme theme){return null;}
 @Override public List<dev.maro.setting.SettingSection> getSettingSections(){
  if(sections==null){sections=settings.sections();WWidget widget=getWidget(new GuiTheme());if(widget!=null){var actions=new dev.maro.setting.SettingSection("Actions");widget.collect(actions);if(!actions.getSettings().isEmpty())sections.add(actions);}}
  return sections;
 }
 @Override public List<dev.maro.setting.Setting<?>> getSettings(){return getSettingSections().stream().flatMap(s->s.getSettings().stream()).toList();}
 public NbtCompound toTag(){return settings.toTag();}
 public Module fromTag(NbtCompound tag){settings.fromTag(tag);return this;}
 @Override public com.google.gson.JsonObject saveExtra(){return NbtCompound.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,toTag()).result().filter(com.google.gson.JsonElement::isJsonObject).map(com.google.gson.JsonElement::getAsJsonObject).orElseGet(com.google.gson.JsonObject::new);}
 @Override public void loadExtra(com.google.gson.JsonObject data){NbtCompound.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,data).result().ifPresent(this::fromTag);}
 public void info(String text,Object... args){message(text,args);} public void warning(String text,Object... args){message(text,args);} public void error(String text,Object... args){message(text,args);}
 private void message(String text,Object... args){String value;try{value=String.format(java.util.Locale.ROOT,text,args);}catch(RuntimeException e){value=text;}if(mc.player!=null)mc.player.sendMessage(net.minecraft.text.Text.literal("[Maro] "+value),false);}
}
