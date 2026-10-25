package dev.maro.gui;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.page.ConfigsPage;
import dev.maro.gui.page.ModulesPage;
import dev.maro.gui.page.Page;
import dev.maro.gui.page.SettingsPage;
import dev.maro.gui.page.SocialsPage;
import dev.maro.gui.page.ThemePage;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GlyphNames;
import dev.maro.gui.render.Glyphs;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Logo;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.TextField;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.KeybindSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.KeyUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * The menu: horizontal navigation, a two-column browser and an independent settings editor.
 * A supported module can also show its common preview between those panels.
 * Uses an immediate-mode approach: everything (including click regions) is rebuilt
 * every frame during {@link #render}, so pages only need to describe what they draw.
 */
public class ClickGuiScreen extends Screen {
    @FunctionalInterface
    public interface ClickHandler {
        void click(int button, double mouseX, double mouseY);
    }

    @FunctionalInterface
    public interface DragHandler {
        void drag(double mouseX, double mouseY);
    }

    /** Which of a page's modules the grid shows. */
    public enum Filter {
        ALL("All"), ENABLED("Enabled"), STARRED("Starred");

        private final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private record Hit(float x, float y, float w, float h, ClickHandler handler) {
    }

    private record ScrollHit(float x, float y, float w, float h, DoubleConsumer handler) {
    }

    private record Rect(float x, float y, float w, float h) {
        Rect intersect(Rect o) {
            float nx = Math.max(x, o.x), ny = Math.max(y, o.y);
            float nr = Math.min(x + w, o.x + o.w), nb = Math.min(y + h, o.y + o.h);
            return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private record Entry(String label, String glyph, Page page, Category category, String subtitle) {
    }

    private static final Object WINDOW = new Object();
    /** The layout the menu is drawn in, scaled to fit the screen. */
    private static final int CANVAS_W = 1080, CANVAS_H = 640;
    private static final float MAIN_W = 530f, MAIN_H = 468f, PANEL_GAP = 16f;
    private static final Icons.Icon SEARCH_ICON = (ctx, cx, cy, s, c, h) -> Glyphs.draw(ctx, GlyphNames.SEARCH, cx, cy, s * 1.15f, c);
    /** The test hook name for the sidebar's Screen GUI switch. */
    public static final String SCREEN_GUI_SWITCH = "screen-gui";
    public static final String PROFILE_SELECTOR = "profile-selector", MODULE_RESET = "module-reset";
    public static final String MODULE_TOGGLE = "module-toggle:", MODULE_FAVORITE = "module-favorite:";
    private boolean profileOpen;
    private List<ConfigManager.ConfigInfo> profileOptions = List.of();
    private float profileScroll;
    private Rect profileRect;

    // remembered between openings
    private static int lastEntry = 0;
    private static float dragX, dragY;
    private static Filter filter = Filter.ALL;

    public TextField focused;
    public KeybindSetting listening;

    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollHit> scrollHits = new ArrayList<>();
    private final Deque<Rect> clips = new ArrayDeque<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Object, float[]> places = new HashMap<>();
    /** The middle of each sidebar row last frame, by entry; for tests. */
    private final Map<Integer, float[]> entryPlaces = new HashMap<>();
    private final TextField search;
    private final ModulesPage searchPage;
    private final SettingsPanel settings = new SettingsPanel(this);
    private final VisualPreview visualPreview = new VisualPreview(this);
    /** With Floating Window: the menu on a glass pane in the world. */
    private final dev.maro.gui.spatial.SpatialView spatial = new dev.maro.gui.spatial.SpatialView(this);
    /** The glass behind the floating menu last frame, {x, y, w, h, corner radius}. */
    private float[] spatialWindow;
    /** Set while the menu is being drawn for the floating pane (no dimming of its own). */
    private boolean paneDraw;
    private final Map<Module, float[]> moduleConfigPlaces = new HashMap<>();
    private Rect panelRect;
    private Rect previewRect, mainRect, pageRect;
    private boolean previewCollapsed;
    private int selected;
    private long pageSwitchedAt = System.currentTimeMillis();
    private boolean wasSearching;
    private boolean antiCheatSectionUnlocked;
    private float[] antiCheatEnterPlace, antiCheatCancelPlace;
    private final int moduleEntries;

    public boolean antiCheatSectionUnlocked() { return antiCheatSectionUnlocked; }
    public float[] antiCheatEnterPlace() { return antiCheatEnterPlace; }
    public float[] antiCheatCancelPlace() { return antiCheatCancelPlace; }

    /** Deliberate entry into the user's anti-cheat-off modules; renewed on each menu opening. */
    public void renderAntiCheatPrompt(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2, cy = y + h / 2;
        int amber = 0xFFFFC36A;
        Glyphs.draw(ctx, GlyphNames.LOCK, cx, cy - 49, 22, amber);
        Fonts.drawCentered(ctx, "ANTI-CHEAT OFF", cx, cy - 19, Theme.TEXT, true, 1.15f);
        Fonts.drawCentered(ctx, "Are you sure you want to enter this section?", cx, cy + 1, Theme.TEXT_DIM, false, 0.8f);
        Fonts.drawCentered(ctx, "These modules are highly detectable.", cx, cy + 16, Theme.TEXT_MUTED, false, 0.73f);
        Fonts.drawCentered(ctx, "Use them only where anti-cheat is switched off.", cx, cy + 28, Theme.TEXT_MUTED, false, 0.73f);
        float by = cy + 47, cancelW = 76, enterW = 110, gap = 8, left = cx - (cancelW + enterW + gap) / 2;
        antiCheatCancelPlace = new float[] {left + cancelW / 2, by + 11};
        antiCheatEnterPlace = new float[] {left + cancelW + gap + enterW / 2, by + 11};
        Widgets.button(this, ctx, "ac-cancel", left, by, cancelW, 22, "Cancel", Widgets.Style.SECONDARY, null,
            () -> openPage(Category.MOVEMENT.ordinal()));
        float ex = left + cancelW + gap;
        boolean hov = hovered(ex, by, enterW, 22);
        Render2D.roundRect(ctx, ex, by, enterW, 22, 5, hov ? 0xFF62441F : 0xFF3D2C19);
        Render2D.roundOutline(ctx, ex, by, enterW, 22, 5, 1, amber);
        Fonts.drawCentered(ctx, "Enter Section", ex + enterW / 2, by + 11, amber, true, 0.82f);
        hit(ex, by, enterW, 22, (button, mx, my) -> { if (button == 0) { antiCheatSectionUnlocked = true; Sounds.click(); } });
    }

    private Object dragOwner;
    private DragHandler drag;
    private double mouseXd, mouseYd;
    private double rawMouseX, rawMouseY;
    /** A held widget owns relative mouse motion, while the ordinary crosshair stays captured. */
    private double screenDragX, screenDragY;
    private boolean screenDragReleasePending;
    private boolean pointerKnown;
    private int menuWidth = CANVAS_W, menuHeight = CANVAS_H;
    private float lastLayoutScale;
    public int menuWidth() { return menuWidth; }
    public int menuHeight() { return menuHeight; }
    private final dev.maro.gui.render.ScreenGuiPose screenPose = new dev.maro.gui.render.ScreenGuiPose();
    public dev.maro.gui.render.ScreenGuiPose screenPose() { return screenPose; }
    private final dev.maro.gui.hud.ScreenGuiMovement worldMovement = new dev.maro.gui.hud.ScreenGuiMovement();
    /** Floating Window previews retain their flat pointer; Screen GUI previews stay on its plane. */
    public boolean previewPointerMode() { return settings.previewSelected() && spatialStyle(); }
    public boolean worldControls() { return !previewPointerMode() && !spatialStyle() && ClientSettings.screenGui.get() && client != null && client.world != null && client.player != null; }
    public boolean capturesMouse() { return worldControls() && client.isWindowFocused() && client.getOverlay() == null; }
    public net.minecraft.util.PlayerInput movementInput() { return worldControls() && focused == null && listening == null ? worldMovement.read() : net.minecraft.util.PlayerInput.DEFAULT; }
    public void prepareScreenView(net.minecraft.client.render.Camera camera) {
        screenPose.canvas(CANVAS_W, CANVAS_H);
        screenPose.update(width, height, worldControls());
        if (worldControls()) screenPose.view(camera, client.options.getFov().getValue(), ClientSettings.screenDistance.getFloat());
    }
    private void syncMouseCapture() {
        if (spatialOn()) return; // the floating pane owns its cursor capture and look
        if (capturesMouse()) { if (!client.mouse.isCursorLocked()) client.mouse.lockCursor(); }
        else if (client.mouse.isCursorLocked()) client.mouse.unlockCursor();
    }
    private boolean mouseDown;
    private boolean interactive = true;

    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1;
    private float lastProgress;

    private String tooltip, shownTooltip;
    private long tooltipSince;

    public ClickGuiScreen() {
        super(Text.literal(Maro.NAME));
        for (Category c : Category.values()) entries.add(new Entry(c.getDisplayName(), ModuleIcons.of(c), new ModulesPage(this, c), c, null));
        moduleEntries = entries.size();
        entries.add(new Entry("Settings", GlyphNames.SETTINGS, new SettingsPage(this), null, "Menu, input and behaviour options"));
        entries.add(new Entry("Configs", GlyphNames.FOLDER, new ConfigsPage(this), null, "Save and switch module setups"));
        entries.add(new Entry("Theme", GlyphNames.PALETTE, new ThemePage(this), null, "Accent, colours and window style"));
        entries.add(new Entry("Socials", GlyphNames.USERS, new SocialsPage(this), null, "Friends and online players"));
        searchPage = new ModulesPage(this, null);
        search = new TextField("Search modules", 48);
        selected = Math.max(0, Math.min(lastEntry, entries.size() - 1));
        // Open on a category with something in it, not an empty one.
        if (selected < moduleEntries && entries.get(selected).category() != Category.ANTI_CHEAT_OFF
                && ((ModulesPage) entries.get(selected).page()).all().isEmpty()) {
            for (int i = 0; i < moduleEntries; i++) {
                if (!((ModulesPage) entries.get(i).page()).all().isEmpty()) {
                    selected = i;
                    break;
                }
            }
        }
        entries.get(selected).page().onOpen();
    }

    // ---- immediate mode API used by pages and widgets -----------------------------------

    public boolean hovered(float x, float y, float w, float h) {
        if (!interactive || closingAt >= 0 || drag != null) return false;
        if (!new Rect(x, y, w, h).contains(mouseXd, mouseYd)) return false;
        Rect clip = clips.peek();
        return clip == null || clip.contains(mouseXd, mouseYd);
    }

    public void hit(float x, float y, float w, float h, ClickHandler handler) {
        if (!interactive) return;
        Rect r = new Rect(x, y, w, h);
        Rect clip = clips.peek();
        if (clip != null) r = r.intersect(clip);
        if (r.w <= 0 || r.h <= 0) return;
        hits.add(new Hit(r.x, r.y, r.w, r.h, handler));
    }

    /** Registers a region that consumes mouse wheel input before the page scrolls. */
    public void scrollHit(float x, float y, float w, float h, DoubleConsumer handler) {
        if (!interactive) return;
        Rect r = new Rect(x, y, w, h);
        Rect clip = clips.peek();
        if (clip != null) r = r.intersect(clip);
        if (r.w <= 0 || r.h <= 0) return;
        scrollHits.add(new ScrollHit(r.x, r.y, r.w, r.h, handler));
    }

    /** Remembers where something clickable was drawn this frame, if it can be clicked there; for tests. */
    public void place(Object key, float x, float y) {
        if (!interactive) return;
        Rect clip = clips.peek();
        if (clip == null || clip.contains(x, y)) places.put(key, new float[] {x, y});
    }

    /** Where a module's card (or anything else {@link #place}d) was drawn last frame, as {x, y} on screen; null if it was not. */
    public float[] placeOf(Object key) {
        float[] point = places.get(key);
        if (point == null || !spatial.isOpen()) return point;
        double[] mapped = screenPose.project(point[0], point[1]);
        return new float[] {(float) mapped[0], (float) mapped[1]};
    }

    public float[] modulePlace(Module module) {
        return placeOf(module);
    }
    public float[] moduleTogglePlace(Module module) { return placeOf(MODULE_TOGGLE + module.getName()); }
    public float[] moduleFavoritePlace(Module module) { return placeOf(MODULE_FAVORITE + module.getName()); }
    public float[] profilePlace() { return placeOf(PROFILE_SELECTOR); }
    public float[] moduleResetPlace() { return placeOf(MODULE_RESET); }

    public void pushClip(float x, float y, float w, float h) {
        Rect r = new Rect(x, y, w, h);
        Rect parent = clips.peek();
        if (parent != null) r = r.intersect(parent);
        clips.push(r);
        applyClip(r);
    }

    public void popClip() {
        clips.pop();
        Rect parent = clips.peek();
        if (parent != null) applyClip(parent);
        else if (scissorActive) {
            currentContext().disableScissor();
            scissorActive = false;
            Render2D.setScissor(null);
        }
    }

    private DrawContext currentCtx;

    private DrawContext currentContext() {
        return currentCtx;
    }

    private void applyClip(Rect r) {
        DrawContext ctx = currentContext();
        // DrawContext scissors stack; we manage our own stack so always replace the top one
        if (scissorActive) ctx.disableScissor();
        int x1 = (int) Math.floor(r.x), y1 = (int) Math.floor(r.y);
        int x2 = (int) Math.ceil(r.x + r.w), y2 = (int) Math.ceil(r.y + r.h);
        ctx.enableScissor(x1, y1, x2, y2);
        Render2D.setScissor(new ScreenRect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)).transformEachVertex(ctx.getMatrices()));
        scissorActive = true;
    }

    private boolean scissorActive;

    /** True if a vertical span intersects the current clip, used to skip off-screen rows. */
    public boolean isVisible(float y, float h) {
        Rect clip = clips.peek();
        return clip == null || (y + h >= clip.y && y <= clip.y + clip.h);
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void tooltip(String text) {
        if (ClientSettings.tooltips.get()) tooltip = text;
    }

    public void startDrag(Object owner, DragHandler handler) {
        dragOwner = owner;
        drag = handler;
        screenDragReleasePending = false;
        if (worldControls()) {
            double[] point = screenPose.project(mouseXd, mouseYd);
            screenDragX = Double.isFinite(point[0]) ? point[0] : width / 2d;
            screenDragY = Double.isFinite(point[1]) ? point[1] : height / 2d;
        }
        handler.drag(mouseXd, mouseYd);
    }

    public boolean screenInteractionDragging() { return worldControls() && drag != null; }
    /** Menu-local pointer owned by a held widget, for input isolation checks. */
    public double[] screenInteractionPointer() { return new double[] {mouseXd, mouseYd}; }

    /** Raw physical cursor motion. The native Mouse hook consumes it before player look. */
    public boolean consumeScreenInteractionMouse(double deltaX, double deltaY) {
        if (!worldControls()) return false;
        if (!screenInteractionDragging()) {
            boolean consumed = screenDragReleasePending;
            screenDragReleasePending = false;
            return consumed;
        }
        var window = client.getWindow();
        dragScreenPointer(net.minecraft.client.Mouse.scaleX(window, deltaX), net.minecraft.client.Mouse.scaleY(window, deltaY));
        return true;
    }

    private void dragScreenPointer(double deltaX, double deltaY) {
        if (!screenInteractionDragging() || !Double.isFinite(deltaX) || !Double.isFinite(deltaY)) return;
        double x = screenDragX + deltaX, y = screenDragY + deltaY;
        double[] point = screenPose.unprojectDrag(x, y);
        if (!Double.isFinite(point[0]) || !Double.isFinite(point[1])) return;
        screenDragX = x;
        screenDragY = y;
        mouseXd = point[0];
        mouseYd = point[1];
        drag.drag(mouseXd, mouseYd);
    }

    private void clearPointerDrag() {
        if (screenInteractionDragging()) screenDragReleasePending = true;
        drag = null;
        dragOwner = null;
        mouseDown = false;
    }

    public boolean isDragging(Object owner) {
        return drag != null && dragOwner == owner;
    }

    public boolean hasPointerDrag() {
        return drag != null;
    }

    public boolean isMouseDown() {
        return mouseDown;
    }

    public VisualPreview visualPreview() {
        return visualPreview;
    }

    /** Whether the menu floats on a glass window in the world (Floating Window). */
    public static boolean spatialStyle() {
        return ClientSettings.spatialMenu.get();
    }

    /** Whether the menu is on the floating pane now: Floating Window, in a world. */
    private boolean spatialOn() {
        return !previewPointerMode() && spatialStyle() && client != null && client.world != null && client.player != null;
    }

    /** The floating pane; for tests. */
    public dev.maro.gui.spatial.SpatialView spatialView() {
        return spatial;
    }

    /** The floating pane draws the menu, flat, into its picture with this. */
    public void spatialDraw(DrawContext ctx, float delta) {
        paneDraw = true;
        try {
            drawMenu(ctx, (int) Math.floor(mouseXd), (int) Math.floor(mouseYd));
        } finally {
            paneDraw = false;
        }
    }

    /** Where the crosshair points on the floating pane, in menu coordinates (far off when it is not on it). */
    public void spatialPointer(double x, double y) {
        rawMouseX = x;
        rawMouseY = y;
        pointerKnown = true;
    }

    /** The window as last drawn, {x, y, w, h, corner radius}, on screen; null before it is drawn. */
    public float[] spatialWindow() {
        if (spatialWindow == null) return null;
        double[] point = screenPose.project(spatialWindow[0], spatialWindow[1]);
        double[] far = screenPose.project(spatialWindow[0] + spatialWindow[2], spatialWindow[1] + spatialWindow[3]);
        float scale = (float) ((far[0] - point[0]) / spatialWindow[2]);
        return new float[] {(float) point[0], (float) point[1], (float) (far[0] - point[0]), (float) (far[1] - point[1]), spatialWindow[4] * scale};
    }

    /** How far the menu is open, 0 to 1 (falls back to 0 as it closes). */
    public float menuProgress() {
        return progress();
    }

    public boolean isClosing() {
        return closingAt >= 0;
    }

    /** A module's public presets, from the configs button in its settings. */
    public void openPublicConfigs(Module module) {
        listening = null;
        focused = null;
        net.minecraft.client.MinecraftClient.getInstance().setScreen(new ModuleConfigsScreen(this, module));
    }

    /** The button in a module's settings that opens its public presets. */
    public void moduleConfigsButton(DrawContext ctx, Module module, float x, float y, float size) {
        boolean over = hovered(x, y, size, size);
        float hover = Anims.of(module, "publicConfigsHeader", over);
        if (hover > .01f) Render2D.circle(ctx, x + size / 2f, y + size / 2f, size / 2f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x16 * hover)));
        Glyphs.draw(ctx, GlyphNames.FOLDER, x + size / 2f, y + size / 2f, size * .62f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hover));
        hit(x, y, size, size, (button, mx, my) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                Sounds.click();
                openPublicConfigs(module);
            }
        });
        moduleConfigPlaces.put(module, new float[] {x + size / 2f, y + size / 2f});
        if (over) tooltip("Public configs for " + module.getDisplayName());
    }

    /** Where a module's public configs button was drawn last frame (on screen with the floating pane). */
    public float[] moduleConfigsPlace(Module module) {
        float[] point = moduleConfigPlaces.get(module);
        if (point == null || !spatial.isOpen()) return point;
        double[] at = screenPose.project(point[0], point[1]);
        return new float[] {(float) at[0], (float) at[1]};
    }

    /** Back from a module's public presets to its settings. */
    public void openModuleContextSettings(Module module) {
        if (settings.module() != module) openModuleOptions(module);
    }

    public TextField getSearch() {
        return search;
    }

    public Filter filter() {
        return filter;
    }

    public void openPage(int index) {
        profileOpen = false;
        if (index == selected && search.getText().isEmpty()) return;
        if (index != selected && settings.module() != null) closeSettings();
        search.clear();
        if (focused == search) focused = null;
        selected = index;
        lastEntry = index;
        pageSwitchedAt = System.currentTimeMillis();
        entries.get(index).page().onOpen();
    }

    /** The index of the page showing, as {@link #openPage} takes it. */
    public int selectedPage() {
        return selected;
    }

    public int selectedEntry() {
        return selected;
    }

    public int entryCount() {
        return entries.size();
    }

    /** The middle of a page's row in the sidebar as last drawn, {x, y} (on screen with the floating pane); for tests. */
    public float[] entryPlace(int index) {
        float[] point = entryPlaces.get(index);
        if (point == null || !spatial.isOpen()) return point;
        double[] mapped = screenPose.project(point[0], point[1]);
        return new float[] {(float) mapped[0], (float) mapped[1]};
    }

    /** Jumps to a module's category and opens its settings (its own screen, if it has one). */
    public void openModuleSettings(Module module) {
        int index = module.getCategory().ordinal();
        openPage(index);
        if (entries.get(index).page() instanceof ModulesPage page) page.openSettings(module);
    }

    /** Opens a module's settings panel, even for a module with a screen of its own. */
    public void openModuleOptions(Module module) {
        openPage(module.getCategory().ordinal());
        showSettings(module);
    }

    /** Opens a module's settings panel scrolled to one of its sections, by title. */
    public void openModuleSettings(Module module, String section) {
        openModuleOptions(module);
        settings.scrollTo(section);
    }

    /** Shows a module's settings in the panel beside the window. */
    public void showSettings(Module module) {
        if (settings.module() != module) clearSettingsInteraction();
        settings.open(module);
        refreshPreviewInputMode();
    }

    public void closeSettings() {
        clearSettingsInteraction();
        settings.close();
        refreshPreviewInputMode();
    }

    /** The module whose settings panel is open, or null. */
    public Module settingsModule() {
        return settings.module();
    }

    /** The settings rectangle in menu coordinates; used by layout verification. */
    public float[] settingsBounds() {
        return panelRect == null ? null : new float[] {panelRect.x, panelRect.y, panelRect.w, panelRect.h};
    }
    public float[] settingsContentBounds() { return settings.scrollBounds(); }
    public float[] browserContentBounds() { return pageRect == null ? null : new float[]{pageRect.x,pageRect.y,pageRect.w,pageRect.h}; }

    /** Preview support comes from the explicit provider registry, never category or module names. */
    public static boolean hasPreviewTab(Module module) { return SettingsPanel.hasPreview(module); }
    public void selectPreviewTab(boolean selected) {
        if (!SettingsPanel.hasPreview(settings.module())) return;
        if (settings.previewSelected() != selected) clearSettingsInteraction();
        settings.selectPreview(selected);
        refreshPreviewInputMode();
    }
    public boolean previewTabSelected() { return settings.previewSelected(); }
    public boolean previewCollapsedToFit() { return previewCollapsed && settings.previewSelected(); }
    public boolean previewPanelVisible() { return closingAt < 0 && settings.previewSelected() && !previewCollapsed && previewRect != null; }
    /** Panel rectangles and tab centers use menu coordinates; screenPose projects them for input. */
    public float[] previewPanelBounds() { return !previewPanelVisible() ? null : new float[]{previewRect.x,previewRect.y,previewRect.w,previewRect.h}; }
    public float[] mainWindowBounds() { return mainRect == null ? null : new float[]{mainRect.x,mainRect.y,mainRect.w,mainRect.h}; }
    public float[] previewTabPlace() { return settings.previewTabPlace(); }
    public float[] settingsTabPlace() { return settings.settingsTabPlace(); }
    private void cancelPreviewDrag() { if (dragOwner == visualPreview) clearPointerDrag(); }
    private void clearSettingsInteraction() {
        if (dragOwner != WINDOW) clearPointerDrag();
        if (focused != search) focused = null;
        listening = null;
        worldMovement.clear();
        dev.maro.gui.widget.Dropdown.close();
    }
    private void refreshPreviewInputMode() {
        if (client == null || client.currentScreen != this) return;
        pointerKnown = false;
        if (previewPointerMode()) {
            if (spatial.isOpen()) spatial.close();
            screenPose.update(width,height,false);
            syncMouseCapture();
        } else if (spatialOn()) {
            spatial.open();
        } else {
            if (spatial.isOpen()) spatial.close();
            screenPose.update(width,height,worldControls());
            syncMouseCapture();
        }
    }

    private Page currentPage() {
        return search.getText().isEmpty() ? entries.get(selected).page() : searchPage;
    }

    // ---- rendering ----------------------------------------------------------------------

    private float progress() {
        long now = System.currentTimeMillis();
        float speed = ClientSettings.animationSpeed();
        if (closingAt >= 0) {
            float t = (now - closingAt) / (200f / speed);
            return lastProgress * (1f - Easing.outCubic(t));
        }
        return Easing.outQuint((now - openedAt) / (420f / speed));
    }

    private static float windowRadius() {
        return 6f + Theme.radius() * 0.8f;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // drawn manually in render() so it can fade with the menu
    }

    @Override
    protected void init() {
        clearPointerDrag();
        pointerKnown = false;
        hits.clear();
        scrollHits.clear();
        if (spatialOn()) spatial.open();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Mark the main framebuffer before either flat or floating menu content is submitted.
        if (!spatialOn() && !worldControls() && ClientSettings.backgroundBlur.get() && client.world != null && progress() > 0.2f) {
            try {
                ctx.applyBlur();
            } catch (IllegalStateException ignored) {
                // something else already blurred this frame
            }
        }
        if (spatialOn()) {
            try {
                spatial.frame(ctx, delta);
                return;
            } catch (RuntimeException e) {
                Maro.LOGGER.error("The floating menu could not be drawn; using the flat window", e);
                ClientSettings.spatialMenu.set(false);
                spatial.close();
                Notifications.push("Floating Window", "Could not be drawn here, so the menu is flat again", Notifications.Type.WARNING, 5000);
            }
        } else if (spatial.isOpen()) {
            spatial.close();
        }
        drawMenu(ctx, mouseX, mouseY);
    }

    private void drawMenu(DrawContext ctx, int mouseX, int mouseY) {
        Fonts.beginMenu();
        try {
            drawMenuContent(ctx, mouseX, mouseY);
        } finally {
            Fonts.endMenu();
        }
    }

    private void drawMenuContent(DrawContext ctx, int mouseX, int mouseY) {
        DrawContext mainContext = ctx;
        if (!pointerKnown) { rawMouseX = mouseX; rawMouseY = mouseY; pointerKnown = true; }
        boolean previewRequested = settings.previewSelected() && closingAt < 0;
        boolean anchored = worldControls();
        previewCollapsed = previewRequested && !anchored && !previewFitsViewport();
        boolean previewVisible = previewRequested && !previewCollapsed;
        menuWidth = CANVAS_W;
        menuHeight = CANVAS_H;
        float fit = Math.min(width / (float) menuWidth, height / (float) menuHeight);
        float scale = fit * (ClientSettings.guiFullscreen.get() ? .97f : Math.min(ClientSettings.guiSize.getFloat() / 100f, .98f));
        // Keep the size slider under the cursor until it is released.
        if (isDragging(ClientSettings.guiSize) && lastLayoutScale > 0) scale = lastLayoutScale;
        else lastLayoutScale = scale;
        float offsetX = (width - menuWidth * scale) / 2, offsetY = (height - menuHeight * scale) / 2;
        screenPose.layout(scale, offsetX, offsetY);
        if (client.world == null || paneDraw) screenPose.update(width, height, false);
        if (!screenInteractionDragging()) {
            double[] pointer = screenPose.unproject(anchored ? width / 2d : rawMouseX, anchored ? height / 2d : rawMouseY);
            mouseXd = pointer[0];
            mouseYd = pointer[1];
        }
        hits.clear();
        scrollHits.clear();
        places.clear();
        entryPlaces.clear();
        visualPreview.beginFrame();
        if (!previewVisible) { cancelPreviewDrag(); visualPreview.hidden(); }
        moduleConfigPlaces.clear();
        clips.clear();
        scissorActive = false;
        Render2D.setScissor(null);
        tooltip = null;
        interactive = true;
        Theme.update();
        if (drag != null && Double.isFinite(mouseXd) && Double.isFinite(mouseYd)) drag.drag(mouseXd, mouseYd);

        float p = progress();
        if (closingAt >= 0 && p <= 0.001f) return; // tick() removes the screen
        if (closingAt < 0) lastProgress = p;

        // background
        Render2D.setAlpha(p);
        if (!paneDraw && !anchored) dev.maro.gui.render.GuiBackground.draw(ctx, 0, 0, width, height);
        // The contour lines of the logo, faint, behind everything.
        if (!paneDraw && !anchored && dev.maro.gui.render.GuiBackground.isDefault()) Logo.contours(ctx, width, height, 0.16f);

        if (screenPose.active()) ctx = dev.maro.gui.render.ScreenGuiSurface.begin(client, (int) mouseXd, (int) mouseYd, screenPose);
        else dev.maro.gui.render.ScreenGuiSurface.release();
        currentCtx = ctx;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(offsetX, offsetY);
        ctx.getMatrices().scale(scale, scale);
        int width = menuWidth, height = menuHeight;
        Fonts.setMeasureScale(scale);

        renderWindow(ctx, p, width, height);
        interactive = true;

        ctx.createNewRootLayer(); // overlays always above the menu
        // A mode setting's open list, over the window and the settings panel.
        dev.maro.gui.widget.Dropdown.render(this, ctx, width, height);
        ctx.createNewRootLayer();
        renderProfiles(ctx);
        ctx.createNewRootLayer();
        renderTooltip(ctx, p, width, height);
        if (screenInteractionDragging() && Double.isFinite(mouseXd) && Double.isFinite(mouseYd)) {
            Render2D.circle(ctx, (float)mouseXd, (float)mouseYd, 1.5f, Theme.TEXT);
            Render2D.ring(ctx, (float)mouseXd, (float)mouseYd, 3.5f, 1f, Theme.accent());
        }
        Render2D.setAlpha(1f);
        ctx.getMatrices().popMatrix();
        Fonts.setMeasureScale(1);
        if (!paneDraw && !anchored) Notifications.render(mainContext);
    }

    /** One coherent shell; opening settings never changes its world geometry. */
    private void renderWindow(DrawContext ctx, float p, int width, int height) {
        boolean anchored=worldControls();
        float shellW=width-28,shellH=height-24,wx=14,wy=12;
        if(!anchored&&!paneDraw){dragX=Math.clamp(dragX,-8,8);dragY=Math.clamp(dragY,-8,8);wx+=dragX;wy+=dragY;}
        float r=Math.max(10,Theme.radius());
        spatialWindow=new float[]{wx,wy,shellW,shellH,r};
        if(ClientSettings.shadow.get()&&!paneDraw)Render2D.shadow(ctx,wx,wy+4,shellW,shellH,r,20,0x60000000);
        hit(wx,wy,shellW,shellH,(button,mx,my)->{});
        Render2D.roundGradientV(ctx,wx,wy,shellW,shellH,r,Theme.windowBg(),ColorUtil.shade(Theme.windowBg(),-.035f));
        if((paneDraw||anchored)&&!dev.maro.gui.render.GuiBackground.isDefault()){
            pushClip(wx+1,wy+1,shellW-2,shellH-2);dev.maro.gui.render.GuiBackground.draw(ctx,wx,wy,shellW,shellH);
            Render2D.rect(ctx,wx,wy,shellW,shellH,0xA0191C22);popClip();
        }
        Render2D.roundOutline(ctx,wx,wy,shellW,shellH,r,Render2D.px(),Theme.BORDER);
        renderTopHeader(ctx,wx+18,wy+12,shellW-36,32);
        renderNavigation(ctx,wx+18,wy+58,shellW-36,30);
        Render2D.rect(ctx,wx+18,wy+96,shellW-36,Render2D.px(),Theme.BORDER);
        boolean searching=!search.getText().isEmpty();
        if(searching!=wasSearching){wasSearching=searching;pageSwitchedAt=System.currentTimeMillis();if(searching)searchPage.onOpen();else entries.get(selected).page().onOpen();}
        boolean modulePage=searching||entries.get(selected).category()!=null;
        float x=wx+18,y=wy+110,w=shellW-36,h=shellH-148;
        boolean showPreview=modulePage&&settings.previewSelected()&&!previewCollapsed&&closingAt<0;
        float browserW=modulePage?(showPreview?414:MAIN_W):w;
        mainRect=new Rect(x,y,browserW,h);panelRect=previewRect=null;
        if(modulePage){
            renderBrowserHeader(ctx,x,y,browserW);
            float pageY=y+64,pageH=h-64;
            pageRect=new Rect(x,pageY,browserW,Math.max(0,pageH-22));
            pushClip(x-4,pageY,browserW+10,pageH);currentPage().render(ctx,x,pageY,browserW,pageH);popClip();
            float settingsW=showPreview?380:SettingsPanel.WIDTH,sx=x+w-settingsW;
            panelRect=new Rect(sx,y,settingsW,h);
            if(showPreview){
                float px=sx-PANEL_GAP-SettingsPanel.PREVIEW_WIDTH;previewRect=new Rect(px,y,SettingsPanel.PREVIEW_WIDTH,h);
                scrollHit(px,y,SettingsPanel.PREVIEW_WIDTH,h,amount->{});
                if(anchored&&!screenPose.visible())visualPreview.suspendFrame();else settings.renderPreview(ctx,px,y,SettingsPanel.PREVIEW_WIDTH,h);
            }
            settings.render(ctx,sx,y,settingsW,h,Math.max(8,Theme.radius()));
        }else{
            Fonts.drawV(ctx,entries.get(selected).label(),x,y+13,Theme.TEXT,true,1.15f);
            String about=entries.get(selected).subtitle();if(about!=null)Fonts.drawV(ctx,about,x,y+32,Theme.TEXT_MUTED,false,.78f);
            pageRect=new Rect(x,y+50,w,h-50);
            pushClip(x-6,y+50,w+12,h-50);currentPage().render(ctx,x,y+50,w,h-50);popClip();
        }
        Render2D.rect(ctx,wx+18,wy+shellH-27,shellW-36,Render2D.px(),Theme.BORDER);
        Fonts.drawV(ctx,"Maro · "+Maro.VERSION,wx+20,wy+shellH-13,Theme.TEXT_MUTED,false,.67f);
        float modeW=108,modeX=wx+shellW-18-modeW,modeY=wy+shellH-23;
        Widgets.button(this,ctx,SCREEN_GUI_SWITCH,modeX,modeY,modeW,19,worldControls()?"GUI: World":"GUI: Screen",Widgets.Style.SECONDARY,Icons.VISUALS,()->{ClientSettings.screenGui.set(!ClientSettings.screenGui.get());refreshPreviewInputMode();Sounds.click();});
        place(SCREEN_GUI_SWITCH,modeX+modeW/2,modeY+9.5f);
    }

    private void renderTopHeader(DrawContext ctx,float x,float y,float w,float h){
        hit(x,y,Math.max(100,w-220),h,(button,mx,my)->{if(button!=0||paneDraw||spatial.isOpen()||worldControls())return;float sx=dragX,sy=dragY;startDrag(WINDOW,(nx,ny)->{dragX=sx+(float)(nx-mx);dragY=sy+(float)(ny-my);});});
        Logo.mark(ctx,x+13,y+h/2,26,0);Fonts.drawV(ctx,"Maro",x+34,y+h/2,Theme.TEXT,true,1.55f);
        float closeX=x+w-27,profileX=closeX-12-132;profileRect=new Rect(profileX,y+3,132,h-6);
        boolean over=hovered(profileX,y+3,132,h-6);
        Render2D.roundRect(ctx,profileX,y+3,132,h-6,6,over?Theme.CARD_HOVER:Theme.INPUT);
        Render2D.roundOutline(ctx,profileX,y+3,132,h-6,6,Render2D.px(),Theme.BORDER);
        Fonts.drawCentered(ctx,Fonts.trim(ConfigManager.getCurrent(),103,false,.9f),profileX+60,y+h/2,Theme.TEXT_DIM,false,.9f);
        Render2D.line(ctx,profileX+116,y+h/2-1,profileX+119,y+h/2+2,1,Theme.TEXT_MUTED);
        Render2D.line(ctx,profileX+119,y+h/2+2,profileX+122,y+h/2-1,1,Theme.TEXT_MUTED);
        hit(profileX,y+3,132,h-6,(button,mx,my)->{if(button!=0)return;profileOpen=!profileOpen;profileScroll=0;if(profileOpen){profileOptions=ConfigManager.list();dev.maro.gui.widget.Dropdown.close();focused=null;}Sounds.click();});
        place(PROFILE_SELECTOR,profileX+66,y+h/2);
        Widgets.button(this,ctx,"close-menu",closeX,y+3,27,h-6,"",Widgets.Style.SECONDARY,Icons.CLOSE,this::close);place("close-menu",closeX+13.5f,y+h/2);
    }

    private void renderNavigation(DrawContext ctx,float x,float y,float w,float h){
        float total=0;float[] sizes=new float[entries.size()];
        for(int i=0;i<entries.size();i++){sizes[i]=Fonts.width(entries.get(i).label(),false,.84f)+32;total+=sizes[i]+4;}
        float fit=Math.min(1,w/Math.max(1,total));
        for(int i=0;i<entries.size();i++){
            Entry entry=entries.get(i);float tw=sizes[i]*fit;boolean active=selected==i&&search.getText().isEmpty(),over=hovered(x,y,tw,h);
            if(active||over)Render2D.roundRect(ctx,x,y,tw,h,6,active?Theme.accent(0x1D):0x0CFFFFFF);
            int color=active?Theme.TEXT:over?Theme.TEXT_DIM:Theme.TEXT_MUTED;
            Glyphs.draw(ctx,entry.glyph(),x+12,y+h/2,11,active?Theme.accent():color);
            Fonts.drawV(ctx,Fonts.trim(entry.label(),tw-29,false,.84f),x+24,y+h/2,color,false,.84f);
            if(active)Render2D.roundRect(ctx,x+10,y+h-1,tw-20,1.5f,.75f,Theme.accent());
            final int index=i;hit(x,y,tw,h,(button,mx,my)->{if(button==0){openPage(index);Sounds.click();}});
            entryPlaces.put(i,new float[]{x+tw/2,y+h/2});if(over&&entry.subtitle()!=null)tooltip(entry.subtitle());x+=tw+4*fit;
        }
    }

    private void renderBrowserHeader(DrawContext ctx,float x,float y,float w){
        String title=search.getText().isEmpty()?entries.get(selected).label():"Search results";float searchW=Math.min(245,w*.53f);
        Fonts.drawV(ctx,Fonts.trim(title,w-searchW-12,true,1.08f),x+1,y+13,Theme.TEXT,true,1.08f);
        search.render(this,ctx,x+w-searchW,y,searchW,26,SEARCH_ICON,null);place(search,x+w-searchW/2,y+13);filters(ctx,x,y+40);
    }

    private void filters(DrawContext ctx,float x,float cy){
        for(Filter f:Filter.values()){
            float w=Fonts.width(f.label,false,.8f)+20,h=22,y=cy-h/2;boolean selected=filter==f,over=hovered(x,y,w,h);
            if(selected||over)Render2D.roundRect(ctx,x,y,w,h,6,selected?Theme.accent(0x20):0x0CFFFFFF);
            Fonts.drawCentered(ctx,f.label,x+w/2,cy,selected?Theme.TEXT:Theme.TEXT_MUTED,false,.8f);
            if(selected)Render2D.roundRect(ctx,x+9,y+h-1,w-18,1.5f,.75f,Theme.accent());
            hit(x,y,w,h,(button,mx,my)->{if(button==0){filter=f;Sounds.click();}});place(f,x+w/2,cy);x+=w+3;
        }
    }

    private void renderProfiles(DrawContext ctx){
        if(!profileOpen||profileRect==null)return;
        float x=profileRect.x,y=profileRect.y+profileRect.h+5,w=profileRect.w,row=25,content=profileOptions.size()*row,h=Math.min(175,Math.max(row,content))+row+6;
        hit(0,0,menuWidth,menuHeight,(button,mx,my)->profileOpen=false);
        scrollHit(0,0,menuWidth,menuHeight,amount->{});
        Render2D.shadow(ctx,x,y,w,h,6,12,0x70000000);Render2D.roundRect(ctx,x,y,w,h,6,Theme.panelBg());Render2D.roundOutline(ctx,x,y,w,h,6,Render2D.px(),Theme.BORDER);hit(x,y,w,h,(button,mx,my)->{});
        float viewH=h-row-6,max=Math.max(0,content-viewH);scrollHit(x,y,w,viewH,amount->profileScroll=(float)Math.clamp(profileScroll-amount*row,0,max));profileScroll=Math.clamp(profileScroll,0,max);
        pushClip(x+1,y+2,w-2,viewH);
        if(profileOptions.isEmpty())Fonts.drawCentered(ctx,"No saved profiles",x+w/2,y+row/2,Theme.TEXT_MUTED,false,.76f);
        for(int i=0;i<profileOptions.size();i++){
            var info=profileOptions.get(i);float py=y+i*row-profileScroll;if(!isVisible(py,row))continue;
            boolean over=hovered(x+3,py,w-6,row),active=info.name().equals(ConfigManager.getCurrent());
            if(over||active)Render2D.roundRect(ctx,x+3,py+1,w-6,row-2,4,Theme.accent(active?0x25:0x12));
            Fonts.drawV(ctx,Fonts.trim(info.name(),w-16,false,.82f),x+8,py+row/2,active?Theme.accent():Theme.TEXT_DIM,false,.82f);
            hit(x+3,py,w-6,row,(button,mx,my)->{if(button==0){if(!ConfigManager.load(info.name()))Notifications.push("Config","Could not load "+info.name(),Notifications.Type.ERROR);profileOpen=false;Sounds.click();}});place("profile:"+info.name(),x+w/2,py+row/2);
        }
        popClip();float py=y+h-row-2;Render2D.rect(ctx,x+6,py,w-12,Render2D.px(),Theme.BORDER);Fonts.drawCentered(ctx,"Manage configs",x+w/2,py+row/2,Theme.TEXT_DIM,false,.79f);
        hit(x,py,w,row,(button,mx,my)->{if(button==0)openPage(moduleEntries+1);});place("manage-configs",x+w/2,py+row/2);
    }

    /** The anchored canvas stays complete; flat menus may hide only the optional subject dock. */
    private boolean previewFitsViewport(){
        float fit=Math.min(width/(float)CANVAS_W,height/(float)CANVAS_H),preferred=ClientSettings.guiFullscreen.get()?.97f:Math.min(ClientSettings.guiSize.getFloat()/100f,.98f);
        float physical=fit*preferred*(float)client.getWindow().getScaleFactor();return SettingsPanel.PREVIEW_WIDTH*physical>=130&&380*physical>=160&&MAIN_H*physical>=230;
    }
    private void renderTooltip(DrawContext ctx, float p, int width, int height) {
        long now = System.currentTimeMillis();
        if (tooltip == null || !tooltip.equals(shownTooltip)) {
            shownTooltip = tooltip;
            tooltipSince = now;
        }
        if (shownTooltip == null) return;
        float a = Easing.outCubic((now - tooltipSince - 280) / 140f);
        if (a <= 0) return;
        List<String> lines = Fonts.wrap(shownTooltip, 170, false, 0.75f);
        float lw = 0;
        for (String l : lines) lw = Math.max(lw, Fonts.width(l, false, 0.75f));
        float w = lw + 12, h = lines.size() * 8.5f + 8;
        float x = (float) mouseXd + 8, y = (float) mouseYd + 10 + (1 - a) * 4;
        if (x + w > width - 4) x = (float) mouseXd - w - 6;
        if (y + h > height - 4) y = (float) mouseYd - h - 6;
        Render2D.setAlpha(p * a);
        Render2D.shadow(ctx, x, y, w, h, 4, 6, 0x60000000);
        Render2D.roundRect(ctx, x, y, w, h, Theme.CONTROL_RADIUS, Theme.panelBg());
        Render2D.roundOutline(ctx, x, y, w, h, Theme.CONTROL_RADIUS, Render2D.px(), Theme.BORDER);
        for (int i = 0; i < lines.size(); i++) Fonts.draw(ctx, lines.get(i), x + 6, y + 5 + i * 8.5f, Theme.TEXT_DIM, false, 0.75f);
    }

    // ---- input --------------------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (worldControls() || spatial.isOpen()) return;
        pointerKnown = true;
        rawMouseX = mouseX;
        rawMouseY = mouseY;
        double[] point = screenPose.unproject(mouseX, mouseY);
        mouseXd = point[0];
        mouseYd = point[1];
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closingAt >= 0) return true;
        if (worldControls() && focused == null && listening == null) worldMovement.mouse(click, true);
        double mouseX = worldControls() ? width / 2d : click.x(), mouseY = worldControls() ? height / 2d : click.y();
        int button = click.button();
        if (spatial.isOpen()) {
            // On the floating pane a click lands where the crosshair is; the bar and the dot are the pane's own.
            if (spatial.press(button) || !spatial.onPane()) return true;
        } else {
            pointerKnown = true;
            rawMouseX = mouseX;
            rawMouseY = mouseY;
            double[] point = screenPose.unproject(mouseX, mouseY);
            mouseXd = point[0];
            mouseYd = point[1];
        }
        if (!Double.isFinite(mouseXd) || !Double.isFinite(mouseYd)) return true;
        mouseDown = true;
        if (listening != null) {
            if (button >= 2) {
                listening.set(KeyUtil.mouse(button));
                listening = null;
                Sounds.click();
                return true;
            }
            listening = null;
        }
        focused = null;
        return clickAt(mouseXd, mouseYd, button);
    }

    /** Clicks whatever was drawn at {@code x, y} in the last frame, front-most first. */
    public boolean clickAt(double x, double y, int button) {
        Fonts.beginMenu();
        try {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
                    h.handler.click(button, x, y);
                    return true;
                }
            }
            return false;
        } finally {
            Fonts.endMenu();
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        worldMovement.mouse(click, false);
        spatial.release();
        clearPointerDrag();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (worldControls()) {
            dragScreenPointer(deltaX, deltaY);
            return true;
        }
        if (spatial.isOpen()) {
            if (drag != null) drag.drag(mouseXd, mouseYd);
            return true;
        }
        mouseMoved(click.x(), click.y());
        if (drag != null && Double.isFinite(mouseXd) && Double.isFinite(mouseYd)) drag.drag(mouseXd, mouseYd);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (drag != null || closingAt >= 0 || !Double.isFinite(verticalAmount)) return true;
        if (spatial.isOpen()) {
            if (spatial.scroll(verticalAmount) || !spatial.onPane()) return true;
            mouseX = mouseXd;
            mouseY = mouseYd;
        } else {
            if (worldControls()) { mouseX = width / 2d; mouseY = height / 2d; }
            double[] point = screenPose.unproject(mouseX, mouseY);
            mouseX = point[0];
            mouseY = point[1];
        }
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return true;
        for (int i = scrollHits.size() - 1; i >= 0; i--) {
            ScrollHit h = scrollHits.get(i);
            if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                h.handler.accept(verticalAmount);
                return true;
            }
        }
        if (previewRect != null && previewRect.contains(mouseX,mouseY)) return true;
        if (panelRect != null && panelRect.contains(mouseX, mouseY)) return true;
        if (pageRect != null && pageRect.contains(mouseX, mouseY)) currentPage().onScroll(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), modifiers = input.modifiers();
        if (worldControls() && focused == null && listening == null && worldMovement.key(input, true)) return true;
        if (closingAt >= 0 || justOpened()) return true;
        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) listening.set(KeyUtil.NONE);
            else if (keyCode != GLFW.GLFW_KEY_ESCAPE) listening.set(keyCode);
            listening = null;
            Sounds.click();
            return true;
        }
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl && (keyCode == GLFW.GLFW_KEY_K || keyCode == GLFW.GLFW_KEY_F)) {
            profileOpen = false;
            worldMovement.clear();
            focused = search;
            return true;
        }
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (focused == search && !search.getText().isEmpty()) search.clear();
                else focused = null;
                return true;
            }
            focused.keyPressed(keyCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Back one step at a time: the open list, the settings panel, the search, then the menu.
            if (profileOpen) profileOpen=false;
            else if (dev.maro.gui.widget.Dropdown.anyOpen()) dev.maro.gui.widget.Dropdown.close();
            else if (settings.module() != null) closeSettings();
            else if (!search.getText().isEmpty()) search.clear();
            else if (!currentPage().onEscape()) close();
            return true;
        }
        if (ClientSettings.guiBind.matches(keyCode)) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            int dir = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            openPage(Math.floorMod(selected + dir, entries.size()));
            Sounds.click();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) { worldMovement.key(input, false); return true; }

    @Override
    public boolean charTyped(CharInput input) {
        if (profileOpen) return true;
        if (worldControls() && focused == null) return true;
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char chr = (char) input.codepoint();
        if (closingAt >= 0 || listening != null || justOpened()) return true;
        if (focused != null) return focused.charTyped(chr);
        // Not with the floating window: there the letters walk you about.
        if (ClientSettings.typeToSearch.get() && !spatial.isOpen() && currentPage().typeToSearch() && Character.isLetterOrDigit(chr)) {
            focused = search;
            return search.charTyped(chr);
        }
        return false;
    }

    // ---- lifecycle ----------------------------------------------------------------------

    @Override
    public void close() {
        if (closingAt >= 0) return;
        closingAt = System.currentTimeMillis();
        listening = null;
        focused = null;
        clearPointerDrag();
        visualPreview.hidden();
    }

    @Override
    public void tick() {
        if (!client.isWindowFocused() || client.getOverlay() != null) { worldMovement.clear(); clearPointerDrag(); }
        if (!worldControls()) worldMovement.clear();
        if (!spatialOn() && spatial.isOpen()) spatial.close();
        syncMouseCapture();
        if ((worldControls() || spatial.isOpen()) && !client.player.isAlive()) { client.setScreen(null); return; }
        // You can walk about with the floating window open, except while typing into a box.
        if (spatial.isOpen()) spatial.walk(closingAt < 0 && focused == null && listening == null);
        if (closingAt >= 0 && progress() <= 0.001f && client != null) client.setScreen(null);
    }

    /** The key/char that opened the menu is also delivered to it; ignore input for a moment. */
    private boolean justOpened() {
        return System.currentTimeMillis() - openedAt < 150;
    }

    @Override
    public void removed() {
        clearPointerDrag();
        screenDragReleasePending = false;
        worldMovement.clear();
        if (client.mouse.isCursorLocked()) client.mouse.unlockCursor();
        spatial.close();
        Render2D.setAlpha(1f);
        Render2D.setScissor(null);
        dev.maro.gui.render.ScreenGuiSurface.release();
        dev.maro.gui.render.VisualPreviewEffect.release();
        visualPreview.release();
        Fonts.setMeasureScale(1);
        ConfigManager.saveClient();
        if (ClientSettings.autoSave.get()) ConfigManager.save(ConfigManager.getCurrent());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void resetPosition() {
        dragX = 0;
        dragY = 0;
    }
}
