package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.nathan.NameeProtectAddon;
import dev.maro.runtime.settings.*;
import dev.maro.runtime.systems.modules.Module;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;

import java.util.*;

/** Recreated from SignalDebug's recovered settings and staff names. Its JNI implementations
 * were unavailable; all tracking, rendering, and alerts here are Maro code. */
public final class StaffNotifier extends Module implements HudElement {
    public static final List<String> DEFAULT_STAFF = List.of("0gsummer", "archivepedro", "bautiedgar",
        "fluffymaster07", "frwost", "itszdeath", "pastagamer08", "showered", "w1zox_",
        "Frenk_Btw", "Napooo_", "BobisFound", "CryptoDaveYt", "MunkerLich", "u_vv", "Fallerfly", "Dough4",
        "CaptainMoose35", "Owen1212055");
    /** Names added to the defaults later, by the list revision they came with; a saved list gets each batch once. */
    private static final Map<Integer,List<String>> ADDED=Map.of(
        2,DEFAULT_STAFF.subList(9,17),
        3,List.of("CaptainMoose35","Owen1212055"));
    private static final int LIST_REVISION=3;
    private static final Set<String> FACE_STAFF=Set.copyOf(DEFAULT_STAFF.subList(0,9));
    private static final int WIDTH = 150, MARGIN = 4, HEAD = 24, PAD = 8;
    /** What each staff member is doing, by colour: close by, hidden from tab, spectating, in tab; and nobody about. */
    private static final int NEARBY = 0xFFFF5D6C, HIDDEN = 0xFFB28CFF, SPECTATING = 0xFF6FB6FF, IN_TAB = 0xFFFFB547, CLEAR = 0xFF3DDC97;
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup hud = settings.createGroup("HUD");
    private final Setting<List<String>> names = general.add(new StringListSetting.Builder().name("staff-names")
        .description("Exact account names, case insensitive; edit the list for your server")
        .defaultValue(DEFAULT_STAFF.toArray(String[]::new)).build());
    private final Setting<Boolean> alerts = general.add(new BoolSetting.Builder().name("alerts").defaultValue(true).build());
    private final Setting<Boolean> sound = general.add(new BoolSetting.Builder().name("sound-alerts").defaultValue(true).visible(alerts::get).build());
    public enum SoundMode { AllStaff, SelectedStaff }
    public enum AlertSound { Chime, Bell, Soft, Alert }
    private final Setting<SoundMode> soundMode=general.add(new EnumSetting.Builder<SoundMode>().name("sound-mode")
        .description("Play sounds for every configured staff member or only your selected names").defaultValue(SoundMode.AllStaff).visible(sound::get).build());
    private final Setting<List<String>> soundStaff=general.add(new StringListSetting.Builder().name("sound-staff")
        .description("Names that trigger a sound in SelectedStaff mode; exact names, case insensitive").defaultValue(new String[0])
        .visible(()->sound.get()&&soundMode.get()==SoundMode.SelectedStaff).build());
    private final Setting<AlertSound> alertSound=general.add(new EnumSetting.Builder<AlertSound>().name("alert-sound").defaultValue(AlertSound.Chime).visible(sound::get).build());
    private final Setting<Double> volume=general.add(new DoubleSetting.Builder().name("sound-volume").defaultValue(.65).range(0,1).visible(sound::get).build());
    private final Setting<Double> pitch=general.add(new DoubleSetting.Builder().name("sound-pitch").defaultValue(1.15).range(.5,2).visible(sound::get).build());
    private final Setting<Boolean> leaveSound=general.add(new BoolSetting.Builder().name("sound-on-leave").defaultValue(false).visible(sound::get).build());
    private final Setting<Boolean> proximitySound=general.add(new BoolSetting.Builder().name("sound-on-nearby").defaultValue(true).visible(sound::get).build());
    private final Setting<Boolean> chat = general.add(new BoolSetting.Builder().name("chat-alerts").defaultValue(false).visible(alerts::get).build());
    private final Setting<Boolean> notifyExisting = general.add(new BoolSetting.Builder().name("notify-existing")
        .description("Notify about staff already in tab when enabling or joining a server").defaultValue(true).build());
    private final Setting<Boolean> list = hud.add(new BoolSetting.Builder().name("staff-list").defaultValue(true).build());
    private final Setting<Boolean> empty = hud.add(new BoolSetting.Builder().name("show-empty")
        .description("Keep the panel visible when no configured staff are listed in tab").defaultValue(true).build());
    private final Setting<Boolean> avatars = hud.add(new BoolSetting.Builder().name("show-heads").defaultValue(true).build());
    private final Setting<Boolean> hidden = hud.add(new BoolSetting.Builder().name("show-hidden-profiles")
        .description("Include configured profiles the server supplied but did not list in tab").defaultValue(true).build());
    private final Setting<Boolean> nearbyAlerts=general.add(new BoolSetting.Builder().name("nearby-alerts")
        .description("Highlight and notify when a configured staff player appears in the loaded world").defaultValue(true).build());
    private final Setting<Integer> nearbyCooldown=general.add(new IntSetting.Builder().name("nearby-cooldown")
        .description("Seconds between proximity alerts for each staff player").defaultValue(120).range(10,600).build());
    public enum Layout { Comfortable, Compact }
    private final Setting<Layout> layout=hud.add(new EnumSetting.Builder<Layout>().name("layout").defaultValue(Layout.Comfortable).build());
    private final Setting<Double> textSize = hud.add(new DoubleSetting.Builder().name("text-size")
        .description("Increase names and details without enlarging the whole staff panel").defaultValue(1).range(.8,1.3).build());
    private final Setting<Boolean> smoothText = hud.add(new BoolSetting.Builder().name("smooth-text")
        .description("Antialiased Inter lettering rasterized at its final screen size").defaultValue(true).build());
    private final Setting<Integer> rows = hud.add(new IntSetting.Builder().name("max-rows").defaultValue(9).range(1,16).build());
    private final Setting<Double> x = hud.add(new DoubleSetting.Builder().name("hud-x").defaultValue(98).range(0,100).build());
    private final Setting<Double> y = hud.add(new DoubleSetting.Builder().name("hud-y").defaultValue(12).range(0,100).build());
    private final Setting<Double> scale = hud.add(new DoubleSetting.Builder().name("staff-list-size").defaultValue(.75).range(.5,2).build());
    public record Staff(UUID id, String name, int ping) {}
    public record Change(String name, boolean joined) {}
    private Map<UUID, PlayerListEntry> online = Map.of();
    private List<Staff> display = List.of();
    private final ArrayDeque<Change> recent = new ArrayDeque<>();
    private ClientPlayNetworkHandler connection;
    private Set<String> configured = Set.of();
    private boolean initialized, rebaseline;
    public record HudStaff(UUID id,String name,int ping,boolean listed,boolean spectator,float distance) {
        public String status(){return distance>=0?"Nearby · "+Math.round(distance)+"m":spectator?"Spectating":listed?"Online":"Hidden from tab";}
    }
    private List<HudStaff> hudDisplay=List.of();
    private final Map<UUID,PlayerListEntry> profiles=new HashMap<>();
    private final Map<UUID,Long> activity=new HashMap<>(),proximityAlerts=new HashMap<>(),highlights=new HashMap<>();
    private final Map<UUID,Long> playedProximitySound=new HashMap<>();
    private Set<String> selectedSoundStaff=Set.of();
    public long soundAlertsPlayed(){return soundAlertsPlayed;}
    private long soundAlertsPlayed;
    public boolean shouldSoundFor(String name){return sound.get()&&(soundMode.get()==SoundMode.AllStaff||name!=null&&selectedSoundStaff.contains(name.toLowerCase(Locale.ROOT)));}
    private void playAlertSound(){
        var event=switch(alertSound.get()){
            case Chime -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case Bell -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case Soft -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
            case Alert -> SoundEvents.BLOCK_NOTE_BLOCK_BIT.value();
        };
        mc.player.playSound(event,volume.get().floatValue(),pitch.get().floatValue());soundAlertsPlayed++;
    }

    public StaffNotifier() {
        super(NameeProtectAddon.CATEGORY, "staff-notifier", "Shows configured staff in tab and alerts when they join or leave.");
        names.observe(value -> { configured = normalize(value); rebaseline = true; });
        configured=normalize(names.get());
        soundStaff.observe(value->selectedSoundStaff=normalize(value));
        selectedSoundStaff=normalize(soundStaff.get());
    }
    private static Set<String> normalize(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values.stream().limit(512).toList()) {
            if (value == null) continue;
            for(String part:value.split("[,;\\r\\n]+")) {
                String name = part.strip().toLowerCase(Locale.ROOT);
                if (name.matches("[a-z0-9_]{1,16}")) result.add(name);
            }
        }
        return Set.copyOf(result);
    }
    public List<Staff> onlineStaff() { return display; }
    public List<HudStaff> hudStaff(){return hudDisplay;}
    public boolean isStaffName(String name){return name!=null&&configured.contains(name.toLowerCase(Locale.ROOT));}
    public boolean isInYourRegion(UUID id){return isActive()&&activity.getOrDefault(id,0L)>System.currentTimeMillis()-60000;}
    public boolean alarmedRecently(UUID id){return isActive()&&playedProximitySound.getOrDefault(id,0L)>System.currentTimeMillis()-5000;}
    public void onIncoming(Packet<?> packet) {
        if(packet instanceof PlayerListS2CPacket info&&info.getActions().contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE)
            &&!info.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)&&mc.getNetworkHandler()!=null) {
            for(var entry:info.getEntries()) {
                var known=mc.getNetworkHandler().getPlayerListEntry(entry.profileId());
                if(known!=null&&isStaffName(known.getProfile().name())&&mc.player!=null&&!entry.profileId().equals(mc.player.getUuid()))
                    activity.put(entry.profileId(),System.currentTimeMillis());
            }
        }
    }
    @Override public com.google.gson.JsonObject saveExtra(){var data=super.saveExtra();data.addProperty("staff-list-revision",LIST_REVISION);return data;}
    @Override public void loadExtra(com.google.gson.JsonObject data){
        var copy=data.deepCopy();copy.remove("staff-list-revision");super.loadExtra(copy);
        int saved=data.has("staff-list-revision")?data.get("staff-list-revision").getAsInt():1;
        if(saved>=LIST_REVISION)return;
        // Names added since this list was saved; ones removed by hand since are not brought back.
        var updated=new ArrayList<>(names.get());var existing=normalize(updated);
        for(int revision=saved+1;revision<=LIST_REVISION;revision++)
            for(String name:ADDED.getOrDefault(revision,List.of()))if(!existing.contains(name.toLowerCase(Locale.ROOT)))updated.add(name);
        names.set(updated);
    }
    public List<Change> recentChanges() { return List.copyOf(recent); }
    @Override public String getInfoString() { return Integer.toString(display.size()); }
    private void clear() {
        online = Map.of(); display = List.of(); recent.clear(); connection = null;
        initialized = false; rebaseline = false;
        hudDisplay=List.of();profiles.clear();activity.clear();proximityAlerts.clear();highlights.clear();
        playedProximitySound.clear();soundAlertsPlayed=0;
    }
    @Override public void onActivate() { clear(); configured = normalize(names.get()); }
    @Override public void onDeactivate() { clear(); }
    @Override public void onTick() {
        if (!inGame() || mc.getNetworkHandler() == null) { clear(); return; }
        if (connection != mc.getNetworkHandler()) { clear(); connection = mc.getNetworkHandler(); }
        Map<UUID,PlayerListEntry> next = new HashMap<>();
        for (PlayerListEntry entry : connection.getListedPlayerListEntries()) {
            String name = entry.getProfile().name();
            if (name != null && configured.contains(name.toLowerCase(Locale.ROOT))) next.put(entry.getProfile().id(),entry);
        }
        List<Change> changes = new ArrayList<>();
        if (!rebaseline && (initialized || notifyExisting.get())) {
            for (var entry : next.entrySet()) if (!online.containsKey(entry.getKey())) changes.add(new Change(entry.getValue().getProfile().name(),true));
            for (var entry : online.entrySet()) if (!next.containsKey(entry.getKey())) changes.add(new Change(entry.getValue().getProfile().name(),false));
        }
        online = Map.copyOf(next);
        display = next.values().stream().map(e -> new Staff(e.getProfile().id(),e.getProfile().name(),e.getLatency()))
            .sorted(Comparator.comparing(Staff::name,String.CASE_INSENSITIVE_ORDER)).toList();
        initialized = true; rebaseline = false;
        updateHud();
        changes.sort(Comparator.comparing(Change::name,String.CASE_INSENSITIVE_ORDER));
        for (Change change : changes) {
            if (recent.size()>=12) recent.removeFirst(); recent.addLast(change);
            for(var entry:next.entrySet())if(entry.getValue().getProfile().name().equals(change.name))highlights.put(entry.getKey(),System.currentTimeMillis());
        }
        if (alerts.get() && !changes.isEmpty()) notifyChanges(changes);
    }
    private void updateHud() {
        long now=System.currentTimeMillis();profiles.clear();
        Set<UUID> listed=online.keySet();var entries=new ArrayList<HudStaff>();var newNearby=new ArrayList<String>();
        for(var entry:connection.getPlayerList()) {
            var profile=entry.getProfile();
            if(!isStaffName(profile.name())||profile.id().equals(mc.player.getUuid())||profile.id().version()==2)continue;
            profiles.put(profile.id(),entry);
            var body=mc.world.getPlayerByUuid(profile.id());float distance=body==null?-1:body.distanceTo(mc.player);
            if(body!=null) {
                activity.put(profile.id(),now);
                if(nearbyAlerts.get()&&now-proximityAlerts.getOrDefault(profile.id(),0L)>=nearbyCooldown.get()*1000L) {
                    proximityAlerts.put(profile.id(),now);highlights.put(profile.id(),now);newNearby.add(profile.name());
                }
            }
            if(listed.contains(profile.id())||hidden.get()||body!=null)entries.add(new HudStaff(profile.id(),profile.name(),entry.getLatency(),listed.contains(profile.id()),entry.getGameMode()==GameMode.SPECTATOR,distance));
        }
        // Keep a configured loaded player visible even if their profile was removed from tab.
        for(var body:mc.world.getPlayers()) {
            var profile=body.getGameProfile();
            if(body==mc.player||!isStaffName(profile.name())||profiles.containsKey(profile.id())||profile.id().version()==2)continue;
            activity.put(profile.id(),now);
            entries.add(new HudStaff(profile.id(),profile.name(),0,false,body.isSpectator(),body.distanceTo(mc.player)));
        }
        hudDisplay=entries.stream().sorted(Comparator.comparingInt((HudStaff s)->s.distance>=0?0:!s.listed?1:s.spectator?2:3)
            .thenComparingDouble(s->s.distance>=0?s.distance:Float.MAX_VALUE).thenComparing(HudStaff::name,String.CASE_INSENSITIVE_ORDER)).toList();
        activity.values().removeIf(time->now-time>60000);highlights.values().removeIf(time->now-time>4000);
        proximityAlerts.keySet().removeIf(id->!activity.containsKey(id)&&!profiles.containsKey(id));
        if(alerts.get()&&!newNearby.isEmpty()) {
            Notifications.push("Staff nearby",String.join(", ",newNearby.stream().limit(3).toList())+" · loaded in your area",Notifications.Type.WARNING,4500);
            if(proximitySound.get()&&newNearby.stream().anyMatch(this::shouldSoundFor)) {
                playAlertSound();
                for(var staff:hudDisplay)if(newNearby.contains(staff.name)&&shouldSoundFor(staff.name))playedProximitySound.put(staff.id,now);
            }
        }
    }
    private void notifyChanges(List<Change> changes) {
        // Coalesce a packet burst into at most one join toast, one leave toast, and one sound.
        for (boolean joined : new boolean[]{true,false}) {
            var group = changes.stream().filter(c -> c.joined==joined).toList();
            if (group.isEmpty()) continue;
            String first = String.join(", ",group.stream().limit(3).map(Change::name).toList());
            String message = first+(group.size()>3?" +"+(group.size()-3):"")+(joined?" joined tab":" left tab");
            Notifications.push("Staff Notifier",message,joined?Notifications.Type.WARNING:Notifications.Type.INFO,4000);
            if (chat.get()) mc.player.sendMessage(Text.literal("[Maro] "+message),false);
        }
        if(changes.stream().anyMatch(c->(c.joined||leaveSound.get())&&shouldSoundFor(c.name)))playAlertSound();
    }
    @Override public dev.maro.runtime.gui.widgets.WWidget getWidget(dev.maro.runtime.gui.GuiTheme theme) {
        var button=theme.button("Place staff list");
        button.action=() -> { list.set(true); setEnabled(true); mc.setScreen(new HudPlacementScreen(mc.currentScreen,this)); };
        return button;
    }
    private int rowHeight(){return layout.get()==Layout.Compact?16:20;}
    private int shownRows(){return Math.min(rows.get(),hudDisplay.size());}
    private int height() {
        int n=shownRows();
        return HEAD+(n>0?3+n*rowHeight()+3:0)+(hudDisplay.size()>n?11:0);
    }
    @Override public String hudName() { return "Staff Notifier"; }
    @Override public float hudScale() { return (float)Math.min(scale.get(),Math.min((mc.getWindow().getScaledWidth()-8)/(double)WIDTH,(mc.getWindow().getScaledHeight()-8)/(double)height())); }
    @Override public float hudWidth() { return WIDTH*hudScale(); }
    @Override public float hudHeight() { return height()*hudScale(); }
    private float roomX() { return Math.max(0,mc.getWindow().getScaledWidth()-hudWidth()-MARGIN*2); }
    private float roomY() { return Math.max(0,mc.getWindow().getScaledHeight()-hudHeight()-MARGIN*2); }
    @Override public float hudLeft() { return MARGIN+Math.round(roomX()*x.get()/100); }
    @Override public float hudTop() { return MARGIN+Math.round(roomY()*y.get()/100); }
    @Override public void hudMove(float left,float top) {
        x.set(roomX()==0?0:clamp((left-MARGIN)/roomX()*100,0,100));
        y.set(roomY()==0?0:clamp((top-MARGIN)/roomY()*100,0,100));
    }
    @Override public void hudResize(float by) { scale.set(clamp(scale.get()+by,.5,2)); }
    @Override public void hudReset() { x.reset(); y.reset(); scale.reset(); }
    private static double clamp(double n,double min,double max) { return Math.max(min,Math.min(max,n)); }
    private float text(float size) { return size * textSize.get().floatValue(); }
    private void draw(DrawContext ctx,String value,float x,float y,int color,boolean bold,float size) {
        if(smoothText.get()) SmoothHudText.draw(ctx,value,x,y,color,bold,size); else Fonts.draw(ctx,value,x,y,color,bold,size);
    }
    private void drawRight(DrawContext ctx,String value,float x,float y,int color,boolean bold,float size) {
        if(smoothText.get()) SmoothHudText.drawRight(ctx,value,x,y,color,bold,size); else Fonts.drawRight(ctx,value,x,y,color,bold,size);
    }
    private String trim(DrawContext ctx,String value,float width,boolean bold,float size) {
        return smoothText.get()?SmoothHudText.trim(ctx,value,width,bold,size):Fonts.trim(value,width,bold,size);
    }
    private static int statusColor(HudStaff staff){
        return staff.distance>=0?NEARBY:!staff.listed?HIDDEN:staff.spectator?SPECTATING:IN_TAB;
    }
    /** Text placed by its left edge and vertical centre. */
    private void drawV(DrawContext ctx,String value,float x,float cy,int color,boolean bold,float size) {
        if(smoothText.get()) SmoothHudText.draw(ctx,value,x,cy-size*2.9f,color,bold,size); else Fonts.drawV(ctx,value,x,cy,color,bold,size);
    }
    @Override public void onRender2D(DrawContext ctx,float delta) {
        if (!inGame() || !list.get() || mc.options.hudHidden || (!empty.get() && hudDisplay.isEmpty())) return;
        if(smoothText.get()) SmoothHudText.beginFrame();
        ctx.getMatrices().pushMatrix(); Fonts.beginRaw();
        try {
            ctx.getMatrices().translate(hudLeft(),hudTop()); ctx.getMatrices().scale(hudScale(),hudScale());
            long now=System.currentTimeMillis();
            int h=height(),n=shownRows();
            boolean clear=hudDisplay.isEmpty();
            // The most pressing status sets the colour of the panel's light: someone close by beats
            // someone hidden, who beats someone merely in tab.
            int state=clear?CLEAR:statusColor(hudDisplay.getFirst());
            long near=hudDisplay.stream().filter(s->s.distance>=0).count();
            float pulse=near>0?.55f+.45f*(float)Math.sin(now/220.0):1f;

            Render2D.shadow(ctx,0,1.5f,WIDTH,h,8,10,0x55000000);
            Render2D.roundRect(ctx,0,0,WIDTH,h,8,0xE80D0F14);
            Render2D.roundOutline(ctx,0,0,WIDTH,h,8,.6f,0x24FFFFFF);
            // A thin line of that colour along the top edge, fading out at both ends.
            int line=(state&0xFFFFFF)|0xB0000000,none=state&0xFFFFFF;
            Render2D.rectGradient(ctx,WIDTH*.15f,.3f,WIDTH*.35f,1f,none,line,line,none);
            Render2D.rectGradient(ctx,WIDTH*.5f,.3f,WIDTH*.35f,1f,line,none,none,line);

            // Header: a status light, the title, and a one-line summary on the right.
            float cy=HEAD/2f;
            Render2D.shadow(ctx,PAD,cy-3,6,6,3,4,(Math.round(0x70*pulse)<<24)|(state&0xFFFFFF));
            Render2D.circle(ctx,PAD+3,cy,3,state);
            drawV(ctx,"STAFF",PAD+11,cy,0xFFF2F4F8,true,text(.8f));
            String summary=clear?"All clear":near>0?near+" nearby":hudDisplay.size()+" online";
            drawRight(ctx,summary,WIDTH-PAD,cy,clear?0xFF8FE3BE:near>0?NEARBY:0xFFA8B0BF,false,text(.68f));
            if(n>0) Render2D.rect(ctx,PAD,HEAD,WIDTH-PAD*2,.6f,0x18FFFFFF);

            // One clean line per person: their head with a dot in their status colour, and their
            // name; someone close by also gets a faint red wash and how far away they are.
            boolean compact=layout.get()==Layout.Compact;
            int rh=rowHeight(),head=compact?11:14;
            float nameSize=text(compact?.8f:.86f);
            for (int i=0;i<n;i++) {
                HudStaff staff=hudDisplay.get(i);
                float top=HEAD+3+i*rh,rcy=top+rh/2f;
                int color=statusColor(staff);
                float flash=(float)Math.max(0,1-(now-highlights.getOrDefault(staff.id,0L))/3500.0);
                int tint=Math.round((staff.distance>=0?0x1C:0)+flash*0x30);
                if(i%2==1&&tint==0) Render2D.roundRect(ctx,4,top+1,WIDTH-8,rh-2,5,0x08FFFFFF);
                if(tint>0) Render2D.roundRect(ctx,4,top+1,WIDTH-8,rh-2,5,(Math.min(255,tint)<<24)|(color&0xFFFFFF));

                PlayerListEntry entry=profiles.get(staff.id);
                float textX;
                if(avatars.get()) {
                    int hx=PAD,hy=Math.round(rcy-head/2f);
                    Render2D.roundRect(ctx,hx-1,hy-1,head+2,head+2,2.5f,0xFF1B1F27);
                    if (FACE_STAFF.contains(staff.name.toLowerCase(Locale.ROOT))) {
                        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("maro","textures/staff/"+staff.name.toLowerCase(Locale.ROOT)+".png"),hx,hy,0,0,head,head,8,8,8,8);
                    } else if (entry!=null) PlayerSkinDrawer.draw(ctx,entry.getSkinTextures(),hx,hy,head);
                    Render2D.circle(ctx,hx+head,hy+head,3f,0xFF0D0F14);
                    Render2D.circle(ctx,hx+head,hy+head,2.1f,color);
                    textX=hx+head+7;
                } else {
                    Render2D.circle(ctx,PAD+2,rcy,2.2f,color);
                    textX=PAD+9;
                }
                String far=staff.distance>=0?Math.round(staff.distance)+"m":null;
                float farWidth=far==null?0:Fonts.width(far,true,text(.68f))+6;
                String name=trim(ctx,staff.name,WIDTH-PAD-textX-farWidth,true,nameSize);
                drawV(ctx,name,textX,rcy,0xFFF2F4F8,true,nameSize);
                if(far!=null) drawV(ctx,far,textX+Fonts.width(name,true,nameSize)+5,rcy,NEARBY,true,text(.68f));
            }
            if (hudDisplay.size()>n)
                drawV(ctx,"+"+(hudDisplay.size()-n)+" more",PAD,h-7,0xFF7F8796,false,text(.62f));
        } finally { Fonts.endRaw(); ctx.getMatrices().popMatrix(); }
    }
}
