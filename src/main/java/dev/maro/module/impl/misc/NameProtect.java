package dev.maro.module.impl.misc;

import com.google.gson.JsonObject;
import dev.maro.config.FriendManager;
import dev.maro.gui.hud.NameProtectScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.privacy.AliasSnapshot;
import dev.maro.setting.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.*;
import java.util.*;

/** Render-only aliases: profiles, commands, signatures and packets keep the real account names. */
public final class NameProtect extends Module {
    private static NameProtect instance;
    private static final ThreadLocal<Integer> BYPASS=ThreadLocal.withInitial(()->0);
    private volatile boolean protecting;
    private volatile AliasSnapshot snapshot=new AliasSnapshot(Map.of());
    private final TextSetting own=add(new TextSetting("Your Name","Your name shown in game","Maro",32,"Your alias").onChange(v->rebuild()));
    private final BooleanSetting others=add(new BooleanSetting("Anonymous Players","Give other players stable numbered aliases",false).onChange(v->rebuild()));
    private final TextSetting prefix=add(new TextSetting("Player Prefix","Prefix for automatically assigned aliases","Player",16,"Player").visible(others::get).onChange(v->rebuild()));
    private final BooleanSetting keepFriends=add(new BooleanSetting("Keep Friends","Keep friends' real names when using anonymous players",false).onChange(v->rebuild()));
    private final Map<String,String> aliases=new LinkedHashMap<>();
    private final Map<UUID,Integer> numbers=new HashMap<>();
    private List<String> roster=List.of();
    private List<String> playerChoices=List.of();
    private Object connection;
    private long fingerprint=Long.MIN_VALUE;
    private int next=1;
    public NameProtect(){super("Name Protect","Live aliases for you and other players, with a searchable editor and anonymous-player mode",Category.MISC);instance=this;}
    @Override public List<SettingSection> getSettingSections(){return List.of(SettingSection.of("Your Name",own),SettingSection.of("Other Players",others,prefix,keepFriends));}
    @Override public Screen panel(Screen parent){return new NameProtectScreen(parent,this);}
    public String ownAlias(){return own.get();}
    public void ownAlias(String value){own.set(value);}
    public List<String> players(){return playerChoices;}
    public String alias(String name){return aliases.getOrDefault(name.toLowerCase(Locale.ROOT),"");}
    public void alias(String name,String value){
        if(name==null||!name.matches("[a-zA-Z0-9_]{1,16}"))return;String key=name.toLowerCase(Locale.ROOT),clean=clean(value,32);
        if(Objects.equals(aliases.getOrDefault(key,""),clean))return;if(clean.isBlank())aliases.remove(key);else if(aliases.containsKey(key)||aliases.size()<512)aliases.put(key,clean);rebuild();
    }
    private static String clean(String value,int limit){if(value==null)return "";var result=new StringBuilder();value.codePoints().filter(TextSetting::allowed).limit(limit).forEach(result::appendCodePoint);return result.toString();}
    public String preview(String text){return snapshot.mask(text);}
    public int cachedStrings(){return snapshot.stringCacheSize();}
    private static final Scope ACTIVE_SCOPE=new Scope(true),NO_SCOPE=new Scope(false);
    public static Scope unprotected(){if(instance==null||!instance.protecting)return NO_SCOPE;BYPASS.set(BYPASS.get()+1);return ACTIVE_SCOPE;}
    public static final class Scope implements AutoCloseable {private final boolean active;private Scope(boolean active){this.active=active;}@Override public void close(){if(active)BYPASS.set(Math.max(0,BYPASS.get()-1));}}
    private static boolean active(){return instance!=null&&instance.protecting&&BYPASS.get()==0;}
    public static String mask(String text){return active()?instance.snapshot.mask(text):text;}
    public static OrderedText mask(OrderedText text){return active()?instance.snapshot.mask(text):text;}
    public static StringVisitable mask(StringVisitable text){return active()?instance.snapshot.mask(text):text;}
    @Override protected void onEnable(){protecting=true;fingerprint=Long.MIN_VALUE;onTick();rebuild();}
    @Override protected void onDisable(){protecting=false;refreshChat();}
    @Override public void onTick(){
        var handler=mc.getNetworkHandler();if(handler!=connection){connection=handler;numbers.clear();next=1;fingerprint=Long.MIN_VALUE;}
        long hash=handler==null?0:handler.getPlayerList().size();
        if(handler!=null)for(var entry:handler.getPlayerList())hash+=31L*entry.getProfile().id().hashCode()+entry.getProfile().name().hashCode()+(FriendManager.isFriend(entry.getProfile().name())?1:0);
        hash=31*hash+(mc.player==null?mc.getSession().getUsername():mc.player.getGameProfile().name()).hashCode();
        if(hash==fingerprint)return;fingerprint=hash;var names=new ArrayList<String>();if(handler!=null)for(var entry:handler.getPlayerList()){names.add(entry.getProfile().name());numbers.computeIfAbsent(entry.getProfile().id(),id->next++);}
        roster=List.copyOf(names);rebuild();
    }
    private void rebuild(){
        var map=new LinkedHashMap<String,String>();String self=mc.player==null?mc.getSession().getUsername():mc.player.getGameProfile().name();
        if(others.get()&&mc.getNetworkHandler()!=null)for(var entry:mc.getNetworkHandler().getPlayerList()){
            String name=entry.getProfile().name();if(name.equalsIgnoreCase(self)||keepFriends.get()&&FriendManager.isFriend(name))continue;
            int number=numbers.computeIfAbsent(entry.getProfile().id(),id->next++);String digits=number<10?"00"+number:number<100?"0"+number:Integer.toString(number);map.put(name.toLowerCase(Locale.ROOT),(prefix.get().isBlank()?"Player":prefix.get())+" "+digits);
        }
        map.putAll(aliases);map.put(self.toLowerCase(Locale.ROOT),own.get().isBlank()?"Maro":own.get());snapshot=new AliasSnapshot(map);
        var choices=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);choices.addAll(roster);choices.addAll(aliases.keySet());playerChoices=List.copyOf(choices);refreshChat();
    }
    private static void refreshChat(){if(mc.inGameHud!=null)((dev.maro.mixin.NameProtectMixins.Chat)mc.inGameHud.getChatHud()).maro$refreshAliases();}
    @Override public JsonObject saveExtra(){var out=new JsonObject();var data=new JsonObject();aliases.forEach(data::addProperty);out.add("aliases",data);return out;}
    @Override public void loadExtra(JsonObject data){aliases.clear();if(data.has("aliases")&&data.get("aliases").isJsonObject())for(var entry:data.getAsJsonObject("aliases").entrySet()){if(aliases.size()>=512)break;if(entry.getKey().matches("[a-zA-Z0-9_]{1,16}")&&entry.getValue().isJsonPrimitive()&&entry.getValue().getAsJsonPrimitive().isString()){String value=clean(entry.getValue().getAsString(),32);if(!value.isBlank())aliases.put(entry.getKey().toLowerCase(Locale.ROOT),value);}}rebuild();}
}
