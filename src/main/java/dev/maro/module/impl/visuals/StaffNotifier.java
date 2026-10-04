package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
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

import java.util.*;

/** Recreated from SignalDebug's recovered settings and staff names. Its JNI implementations
 * were unavailable; all tracking, rendering, and alerts here are Maro code. */
public final class StaffNotifier extends Module implements HudElement {
    public static final List<String> DEFAULT_STAFF = List.of("0gsummer", "archivepedro", "bautiedgar",
        "fluffymaster07", "frwost", "itszdeath", "pastagamer08", "showered", "w1zox_");
    private static final int WIDTH = 186, ROW = 27, MARGIN = 4;
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup hud = settings.createGroup("HUD");
    private final Setting<List<String>> names = general.add(new StringListSetting.Builder().name("staff-names")
        .description("Exact account names, case insensitive; edit the list for your server")
        .defaultValue(DEFAULT_STAFF.toArray(String[]::new)).build());
    private final Setting<Boolean> alerts = general.add(new BoolSetting.Builder().name("alerts").defaultValue(true).build());
    private final Setting<Boolean> sound = general.add(new BoolSetting.Builder().name("sound-alerts").defaultValue(true).visible(alerts::get).build());
    private final Setting<Boolean> chat = general.add(new BoolSetting.Builder().name("chat-alerts").defaultValue(false).visible(alerts::get).build());
    private final Setting<Boolean> notifyExisting = general.add(new BoolSetting.Builder().name("notify-existing")
        .description("Notify about staff already in tab when enabling or joining a server").defaultValue(true).build());
    private final Setting<Boolean> list = hud.add(new BoolSetting.Builder().name("staff-list").defaultValue(true).build());
    private final Setting<Boolean> empty = hud.add(new BoolSetting.Builder().name("show-empty")
        .description("Keep the panel visible when no configured staff are listed in tab").defaultValue(true).build());
    private final Setting<Boolean> ping = hud.add(new BoolSetting.Builder().name("show-ping").defaultValue(true).build());
    private final Setting<Integer> rows = hud.add(new IntSetting.Builder().name("max-rows").defaultValue(9).range(1,16).build());
    private final Setting<Double> x = hud.add(new DoubleSetting.Builder().name("hud-x").defaultValue(98).range(0,100).build());
    private final Setting<Double> y = hud.add(new DoubleSetting.Builder().name("hud-y").defaultValue(12).range(0,100).build());
    private final Setting<Double> scale = hud.add(new DoubleSetting.Builder().name("staff-list-size").defaultValue(.85).range(.5,2).build());
    public record Staff(UUID id, String name, int ping) {}
    public record Change(String name, boolean joined) {}
    private Map<UUID, PlayerListEntry> online = Map.of();
    private List<Staff> display = List.of();
    private final ArrayDeque<Change> recent = new ArrayDeque<>();
    private ClientPlayNetworkHandler connection;
    private Set<String> configured = Set.of();
    private boolean initialized, rebaseline;

    public StaffNotifier() {
        super(NameeProtectAddon.CATEGORY, "staff-notifier", "Shows configured staff in tab and alerts when they join or leave.");
        names.observe(value -> { configured = normalize(value); rebaseline = true; });
    }
    private static Set<String> normalize(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values.stream().limit(512).toList()) {
            if (value == null) continue;
            String name = value.strip().toLowerCase(Locale.ROOT);
            if (name.matches("[a-z0-9_]{1,16}")) result.add(name);
        }
        return Set.copyOf(result);
    }
    public List<Staff> onlineStaff() { return display; }
    public List<Change> recentChanges() { return List.copyOf(recent); }
    @Override public String getInfoString() { return Integer.toString(display.size()); }
    private void clear() {
        online = Map.of(); display = List.of(); recent.clear(); connection = null;
        initialized = false; rebaseline = false;
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
        changes.sort(Comparator.comparing(Change::name,String.CASE_INSENSITIVE_ORDER));
        for (Change change : changes) { if (recent.size()>=12) recent.removeFirst(); recent.addLast(change); }
        if (alerts.get() && !changes.isEmpty()) notifyChanges(changes);
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
        if (sound.get()) mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),.7f,
            changes.stream().anyMatch(Change::joined)?1.1f:.8f);
    }
    @Override public dev.maro.runtime.gui.widgets.WWidget getWidget(dev.maro.runtime.gui.GuiTheme theme) {
        var button=theme.button("Place staff list");
        button.action=() -> { list.set(true); setEnabled(true); mc.setScreen(new HudPlacementScreen(mc.currentScreen,this)); };
        return button;
    }
    private int height() { return 39+Math.max(1,Math.min(rows.get(),display.size()))*ROW+(display.size()>rows.get()?13:0); }
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
    @Override public void onRender2D(DrawContext ctx,float delta) {
        if (!inGame() || !list.get() || mc.options.hudHidden || (!empty.get() && display.isEmpty())) return;
        ctx.getMatrices().pushMatrix(); Fonts.beginRaw();
        try {
            ctx.getMatrices().translate(hudLeft(),hudTop()); ctx.getMatrices().scale(hudScale(),hudScale());
            Render2D.roundRect(ctx,0,0,WIDTH,height(),8,0xEE101319);
            Render2D.roundOutline(ctx,0,0,WIDTH,height(),8,.6f,0x504F5868);
            Fonts.draw(ctx,"STAFF ONLINE",10,9,0xFFE9EDF5,true,.7f);
            Fonts.drawRight(ctx,Integer.toString(display.size()),WIDTH-10,13,display.isEmpty()?0xFF8F9AAE:0xFFFFB575,true,.7f);
            Fonts.draw(ctx,"Players currently listed in tab",10,24,0xFF8794A9,false,.55f);
            if (display.isEmpty()) Fonts.draw(ctx,"No configured staff in tab",10,46,0xFFBCC6D6,false,.65f);
            for (int i=0;i<Math.min(rows.get(),display.size());i++) {
                Staff staff=display.get(i); int top=36+i*ROW;
                Render2D.roundRect(ctx,6,top,WIDTH-12,24,5,0x70262D3B);
                PlayerListEntry entry=online.get(staff.id);
                if (DEFAULT_STAFF.contains(staff.name.toLowerCase(Locale.ROOT))) {
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of("maro","textures/staff/"+staff.name.toLowerCase(Locale.ROOT)+".png"),11,top+4,0,0,16,16,8,8,8,8);
                } else if (entry!=null) PlayerSkinDrawer.draw(ctx,entry.getSkinTextures(),11,top+4,16);
                Fonts.draw(ctx,Fonts.trim(staff.name,ping.get()?112:145,true,.65f),33,top+5,0xFFF0F3F9,true,.65f);
                Fonts.draw(ctx,"Listed in tab",33,top+15,0xFF8CA0B1,false,.45f);
                if (ping.get()) Fonts.drawRight(ctx,Math.max(0,staff.ping)+"ms",WIDTH-12,top+11,0xFFACBCCC,false,.5f);
            }
            if (display.size()>rows.get()) Fonts.draw(ctx,"+"+(display.size()-rows.get())+" more",10,height()-12,0xFF99A7BA,false,.55f);
        } finally { Fonts.endRaw(); ctx.getMatrices().popMatrix(); }
    }
}
