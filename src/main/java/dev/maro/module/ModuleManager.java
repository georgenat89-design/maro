package dev.maro.module;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.gui.notification.Notifications;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {
    }

    public static void init() {
        register(new dev.maro.module.impl.player.AutoTool());
        register(new dev.maro.module.impl.player.AutoTrident());
        register(new dev.maro.module.impl.player.TridentUtil());
        register(new dev.maro.module.impl.player.SpeedMine());
        register(new dev.maro.module.impl.player.BreakDelay());
        register(new dev.maro.module.impl.movement.Flight());
        register(new dev.maro.module.impl.movement.BoatFly());
        register(new dev.maro.module.impl.movement.BoatNoClip());
        register(new dev.maro.module.impl.misc.ScreenHider());
        register(new dev.maro.module.impl.misc.InventoryHider());
        register(new dev.maro.module.impl.misc.BlockDisconnect());
        register(new dev.maro.module.impl.misc.CoordSnapper());
        register(new dev.maro.module.impl.misc.DiscordPresence());
        register(new dev.maro.module.impl.misc.AutoGoliath());
        register(new dev.maro.module.impl.player.FastPlace());
        register(new dev.maro.module.impl.player.FakePlayer());
        register(new dev.maro.module.impl.player.AutoMine());
        register(new dev.maro.module.impl.player.AutoBuilder());
        register(new dev.maro.module.impl.player.MaroRelog());
        register(new dev.maro.module.impl.player.CrafterDisabler());
        register(new dev.maro.module.impl.player.ChestStealer());
        register(new dev.maro.module.impl.player.ChestDumper());
        register(new dev.maro.module.impl.visuals.StretchRes());
        register(new dev.maro.module.impl.visuals.InventoryHud());
        register(new dev.maro.module.impl.visuals.Fullbright());
        register(new dev.maro.module.impl.visuals.PotatoGraphics());
        register(new dev.maro.module.impl.visuals.NoRender());
        register(new dev.maro.module.impl.visuals.ItemInspect());
        register(new dev.maro.module.impl.visuals.ViewModel());
        register(new dev.maro.module.impl.visuals.Compass());
        register(new dev.maro.module.impl.visuals.CustomCrosshair());
        register(new dev.maro.module.impl.visuals.SkinAccessories());
        register(new dev.maro.module.impl.visuals.Pet());
        register(new dev.maro.module.impl.visuals.BaseESP());
        register(new dev.maro.module.impl.visuals.BlockESP());
        register(new dev.maro.module.impl.visuals.StorageESP());
        register(new dev.maro.module.impl.visuals.HoleESP());
        register(new dev.maro.module.impl.visuals.Nametags());
        register(new dev.maro.module.impl.visuals.Trajectories());
        register(new dev.maro.module.impl.visuals.HandShader());
        register(new dev.maro.module.impl.misc.OrderDropper());
        register(new dev.maro.module.impl.movement.CoordsFly());
        register(new dev.maro.module.impl.movement.AirStuck());
        register(new dev.maro.module.impl.movement.NoFall());
        register(new dev.maro.module.impl.visuals.SpawnerNotifier());
        register(new dev.maro.module.impl.visuals.BetterTextures());
        register(new dev.maro.module.impl.visuals.BetterLooks());
        register(new dev.maro.module.impl.visuals.FakeBlock());
        register(new dev.maro.module.impl.visuals.PlayerESP());
        register(new dev.maro.module.impl.visuals.CustomSky());
        register(new dev.maro.module.impl.visuals.CustomTotem());
        register(new dev.maro.module.impl.visuals.Emotes());
        register(new dev.maro.module.impl.visuals.StaffNotifier());
        dev.maro.nathan.NameeProtectAddon.init();

        // Register your modules here, e.g.
        // register(new dev.maro.module.impl.ExampleModule());
    }

    public static void register(Module module) {
        if (getByName(module.getName()) != null) throw new IllegalStateException("Duplicate module name: " + module.getName());
        MODULES.add(module);
        if(module instanceof dev.maro.runtime.systems.modules.Module ported) module.getBind().set(ported.keybind.code());
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(MODULES);
    }

    /** A category's modules in alphabetical order. */
    public static List<Module> byCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : MODULES) if (m.getCategory() == category && !m.hiddenInGui()) list.add(m);
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        return list;
    }

    public static long enabledCount(Category category) {
        return MODULES.stream().filter(m -> m.getCategory() == category && m.isEnabled() && !m.hiddenInGui()).count();
    }

    public static List<Module> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Module> starts = new ArrayList<>(), contains = new ArrayList<>(), desc = new ArrayList<>();
        for (Module m : MODULES) {
            if (m.hiddenInGui()) continue;
            String n = m.getName().toLowerCase(Locale.ROOT);
            String compact = n.replace(" ", "");
            if (n.startsWith(q) || compact.startsWith(q.replace(" ", ""))) starts.add(m);
            else if (n.contains(q) || compact.contains(q.replace(" ", ""))) contains.add(m);
            else if (m.getDescription().toLowerCase(Locale.ROOT).contains(q)) desc.add(m);
        }
        starts.addAll(contains);
        starts.addAll(desc);
        return starts;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (m.getClass() == type) return (T) m;
        return null;
    }

    public static Module getByName(String name) {
        for (Module m : MODULES) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    // ---- dispatch ------------------------------------------------------------------------

    public static void onTick() {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                Maro.LOGGER.error("Module {} threw in onTick", m.getName(), t);
            }
        }
    }

    public static void onRender2D(DrawContext context, float tickDelta) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onRender2D(context, tickDelta);
            } catch (Throwable t) {
                Maro.LOGGER.error("Module {} threw in onRender2D", m.getName(), t);
            }
        }
    }

    /** Toggles every module bound to {@code code}. */
    public static void onBind(int code) {
        for (Module m : MODULES) {
            if (!m.getBind().matches(code)) continue;
            m.toggle();
            if (ClientSettings.toggleNotifications.get()) {
                Notifications.push(m.getName(), m.isEnabled() ? "Enabled" : "Disabled",
                        m.isEnabled() ? Notifications.Type.ENABLED : Notifications.Type.DISABLED);
            }
        }
    }
}
