package dev.maro.module;

import java.util.*;

/** Stable browser sections. New modules remain visible in their category's General section. */
public final class ModuleSections {
    public record Section(String id, Category category, String title, String compactTitle, String description) { }
    public record Group(Section section, List<Module> modules) { }
    private static final List<Section> SECTIONS=new ArrayList<>();
    private static final Map<String,Section> NAMES=new HashMap<>();
    private static final Map<Module,Section> CACHE=new IdentityHashMap<>();
    private static final Map<Category,Section> GENERAL=new EnumMap<>(Category.class);
    static {
        add(Category.MOVEMENT,"travel","Travel & Movement","Travel","Walking, guided travel and movement helpers.","Auto Walk","Coords Fly");
        add(Category.MOVEMENT,"camera","Camera & View","Camera & View","Detached cameras and looking around.","Free Cam","Free Look");
        add(Category.MOVEMENT,"vehicles","Vehicles","Vehicles","Cars, boats and other ride controls.","Vehicles");
        add(Category.PLAYER,"building","Building & Mining","Build & Mine","Build schematics, mine tunnels and select tools.","Auto Builder","Auto Mine","Auto Tool","Fast Place");
        add(Category.PLAYER,"inventory","Inventory & Loot","Inventory & Loot","Restocking, chest transfers, food and experience.","Auto Replenish","Chest Stealer","Chest Dumper","Crafter Disabler","Smart Eat","Fast Xp");
        add(Category.PLAYER,"farming","Farming & Droppers","Farming","Spawner and shulker loot workflows.","Shulker Dropper","Spawner Dropper","Spawner Protect");
        add(Category.PLAYER,"automation","Automation & Testing","Automation","Local test players and repeatable world actions.","Fake Player","Maro Relog","Auto Relog");
        add(Category.VISUALS,"hud","HUD & Overlays","HUD & Overlays","On-screen equipment, inventory, keys and navigation.","Armor HUD","Inventory","Keyboard Strokes","Compass","Spotify Hud");
        add(Category.VISUALS,"awareness","Players & Awareness","Player Awareness","Player visibility, names, staff, radar and projectile guidance.","Player ESP","Nametags","Staff Notifier","Radar","Trajectories");
        add(Category.VISUALS,"scanning","World & Base Scans","World Scans","Blocks, storage, chest previews, spawners and base activity.","Base ESP","Bedrock Void ESP","Block ESP","Storage ESP","Redstone Bypass","Region Map","Spawner Notifier","Chest Preview");
        add(Category.VISUALS,"camera","Camera & View","Camera & View","Field of view, zoom and stretched resolution.","Custom Fov","Key Zoom","Stretch Res");
        add(Category.VISUALS,"world","World & Lighting","World & Lighting","Sky, brightness, graphics and resource packs.","Fullbright","Custom Sky","Gameplay Textures","Better Textures","Better Looks","No Render","Potato Graphics","Bloom","Color Correct","Motion Blur");
        add(Category.VISUALS,"characters","Characters & Cosmetics","Characters","Character models, pets, cosmetics and emotes.","Custom Model","Cosmetics","Pet","Emotes","Acrobatics","Spin Bot","Hats");
        add(Category.VISUALS,"items","Hands & Items","Hands & Items","First-person hands, weapon inspections and totems.","View Model","Item Inspect","Weapon Inspect","Swing Speed","Custom Totem");
        add(Category.VISUALS,"effects","Visual Effects","Visual Effects","Crosshairs, jump rings and other local visual effects.","Custom Crosshair","Jump Circle","Geeked","Fake Block","Fake Xp");
        add(Category.MISC,"social","Chat & Social","Chat & Social","Chat shortcuts, Discord and teleport requests.","Chat Macros","Discord Presence","Auto Tpa Here","Tp Macro","Tpa Here Kill","Tpahere Kill Bot");
        add(Category.MISC,"media","Media & Devices","Media & Devices","Phone apps, videos, music and playlists.","Spotify Phone","Spotify Playlists");
        add(Category.MISC,"privacy","Identity & Privacy","Privacy","Player identity, aliases and information shown while streaming.","Name Protect","Skin Changer","Screen Hider","Streamproof","Inventory Hider");
        add(Category.MISC,"orders","Trading & Orders","Trading & Orders","Order collection and local payment visuals.","Order Dropper","Fake Pay");
        add(Category.MISC,"travel","Travel & Searching","Travel & Searching","Coordinate shortcuts and repeated location searches.","Coord Snapper","Block Disconnect","Auto Goliath");
        add(Category.MISC,"utilities","Client Utilities","Client Utilities","Small client tools and examples.","Key Sounds","Example");
        add(Category.ANTI_CHEAT_OFF,"flight","Flight & Vehicles","Flight & Vehicles","Flight and ridden-vehicle controls.","Flight","Boat Fly","Boat No Clip");
        add(Category.ANTI_CHEAT_OFF,"mining","Mining","Mining","Mining speed and block-break timing.","Speed Mine","Break Delay");
        for(Category category:Category.values()){
            var section=new Section(category.name().toLowerCase(Locale.ROOT)+"/general",category,"General","General","Other "+category.getDisplayName().toLowerCase(Locale.ROOT)+" modules.");
            SECTIONS.add(section);GENERAL.put(category,section);
        }
    }
    private ModuleSections() { }
    private static String key(String name){return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private static void add(Category category,String id,String title,String compact,String description,String... names){
        var section=new Section(category.name().toLowerCase(Locale.ROOT)+"/"+id,category,title,compact,description);SECTIONS.add(section);
        for(String name:names){String key=category.name()+"/"+key(name);if(NAMES.putIfAbsent(key,section)!=null)throw new IllegalStateException("Duplicate browser assignment: "+name);}
    }
    public static List<Section> all(){return Collections.unmodifiableList(SECTIONS);}
    public static Section of(Module module){return CACHE.computeIfAbsent(module,m->NAMES.getOrDefault(m.getCategory().name()+"/"+key(m.getName()),GENERAL.get(m.getCategory())));}
    public static List<Group> groups(List<Module> modules){
        var buckets=new HashMap<Section,List<Module>>();
        for(Module module:modules)buckets.computeIfAbsent(of(module),s->new ArrayList<>()).add(module);
        var result=new ArrayList<Group>();
        for(Section section:SECTIONS){var members=buckets.get(section);if(members!=null)result.add(new Group(section,List.copyOf(members)));}
        return result;
    }
}
