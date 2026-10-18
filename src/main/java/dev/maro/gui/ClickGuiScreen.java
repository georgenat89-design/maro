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
import dev.maro.util.Animation;
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
 * The menu: one window with the categories and client pages down a sidebar and the open one's
 * modules in a grid. A module's settings slide out in a panel beside the window, which moves aside
 * to make room. Uses an immediate-mode approach: everything (including click regions) is rebuilt
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
    private static final int CANVAS_W = 860, CANVAS_H = 480;
    private static final float MAIN_W = 540f, MAIN_H = 384f, SIDEBAR_W = 140f, PANEL_GAP = 10f, HEADER_H = 28f;
    private static final Icons.Icon SEARCH_ICON = (ctx, cx, cy, s, c, h) -> Glyphs.draw(ctx, GlyphNames.SEARCH, cx, cy, s * 1.15f, c);
    /** The test hook name for the sidebar's Screen GUI switch. */
    public static final String SCREEN_GUI_SWITCH = "screen-gui";

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
    private final Animation settingsShown = new Animation(13f, 0f);
    private Rect panelRect;
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
    private boolean pointerKnown;
    private int menuWidth = CANVAS_W, menuHeight = CANVAS_H;
    private float lastLayoutScale;
    public int menuWidth() { return menuWidth; }
    public int menuHeight() { return menuHeight; }
    private final dev.maro.gui.render.ScreenGuiPose screenPose = new dev.maro.gui.render.ScreenGuiPose();
    public dev.maro.gui.render.ScreenGuiPose screenPose() { return screenPose; }
    private final dev.maro.gui.hud.ScreenGuiMovement worldMovement = new dev.maro.gui.hud.ScreenGuiMovement();
    public boolean worldControls() { return !spatialStyle() && ClientSettings.screenGui.get() && client != null && client.world != null && client.player != null; }
    public boolean capturesMouse() { return worldControls() && client.isWindowFocused() && client.getOverlay() == null; }
    public net.minecraft.util.PlayerInput movementInput() { return worldControls() && focused == null && listening == null ? worldMovement.read() : net.minecraft.util.PlayerInput.DEFAULT; }
    public void prepareScreenView(net.minecraft.client.render.Camera camera) {
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
        search = new TextField("Search", 32);
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

    /** Where a module's card (or anything else {@link #place}d) was drawn last frame, as {x, y}; null if it was not. */
    public float[] placeOf(Object key) {
        return places.get(key);
    }

    public float[] modulePlace(Module module) {
        return placeOf(module);
    }

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
        handler.drag(mouseXd, mouseYd);
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
        return spatialStyle() && client != null && client.world != null && client.player != null;
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
        if (index == selected && search.getText().isEmpty()) return;
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
        settings.open(module);
    }

    public void closeSettings() {
        settings.close();
    }

    /** The module whose settings panel is open, or null. */
    public Module settingsModule() {
        return settings.module();
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
        drag = null;
        dragOwner = null;
        mouseDown = false;
        pointerKnown = false;
        hits.clear();
        scrollHits.clear();
        if (spatialOn()) spatial.open();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Mark the main framebuffer before either flat or floating menu content is submitted.
        if (!spatialOn() && ClientSettings.backgroundBlur.get() && client.world != null && progress() > 0.2f) {
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
        double[] pointer = screenPose.unproject(worldControls() ? width / 2d : rawMouseX, worldControls() ? height / 2d : rawMouseY);
        mouseXd = pointer[0];
        mouseYd = pointer[1];
        hits.clear();
        scrollHits.clear();
        places.clear();
        entryPlaces.clear();
        visualPreview.beginFrame();
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
        if (!paneDraw && ClientSettings.backgroundDim.get()) {
            int dim = Math.round(255 * ClientSettings.dimAmount.getFloat() / 100f);
            Render2D.rectGradient(ctx, 0, 0, width, height, ColorUtil.withAlpha(0, Math.round(dim * 0.85f)), ColorUtil.withAlpha(0, Math.round(dim * 0.85f)),
                    ColorUtil.withAlpha(0, dim), ColorUtil.withAlpha(0, dim));
        }
        // The contour lines of the logo, faint, behind everything.
        if (!paneDraw) Logo.contours(ctx, width, height, 0.16f);

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
        renderTooltip(ctx, p, width, height);
        Render2D.setAlpha(1f);
        ctx.getMatrices().popMatrix();
        Fonts.setMeasureScale(1);
        if (!paneDraw) Notifications.render(mainContext);
    }

    /** The window, and the settings panel beside it while a module's settings are open. */
    private void renderWindow(DrawContext ctx, float p, int width, int height) {
        float t = settingsShown.update(settings.module() != null ? 1f : 0f);
        float total = MAIN_W + (PANEL_GAP + SettingsPanel.WIDTH) * t;
        float baseX = (width - total) / 2f, baseY = (height - MAIN_H) / 2f;
        float slackX = Math.max(0, baseX - 2), slackY = Math.max(0, baseY - 2);
        dragX = Math.max(-slackX, Math.min(slackX, dragX));
        dragY = Math.max(-slackY, Math.min(slackY, dragY));
        float wx = baseX + (paneDraw ? 0 : dragX);
        float wy = baseY + (paneDraw ? 0 : dragY) + (1f - p) * 18f;
        float r = windowRadius();
        spatialWindow = new float[] {wx, wy, total, MAIN_H, r};
        boolean shadows = ClientSettings.shadow.get() && !paneDraw;

        // The settings panel first: it slides out from behind the window.
        panelRect = null;
        if (t > 0.01f && settings.drawn() != null) {
            float sx = wx + MAIN_W + PANEL_GAP - (1 - t) * 26f;
            Render2D.setAlpha(p * t);
            if (shadows) Render2D.shadow(ctx, sx, wy + 3, SettingsPanel.WIDTH, MAIN_H, r, 24f, 0x80000000);
            interactive = settings.module() != null;
            settings.render(ctx, sx, wy, SettingsPanel.WIDTH, MAIN_H, r);
            interactive = true;
            if (settings.module() != null) panelRect = new Rect(sx, wy, SettingsPanel.WIDTH, MAIN_H);
        }
        Render2D.setAlpha(p);

        if (shadows) {
            Render2D.shadow(ctx, wx, wy + 3, MAIN_W, MAIN_H, r, 24f, 0x80000000);
            if (Theme.glow()) Render2D.shadow(ctx, wx, wy, MAIN_W, MAIN_H, r, 36f, Theme.accent(0x12));
        }
        hit(wx, wy, MAIN_W, MAIN_H, (button, mx, my) -> {
        }); // the window takes clicks that land on it but on nothing in it
        Render2D.roundRect(ctx, wx, wy, MAIN_W, MAIN_H, r, Theme.windowBg());
        // The sidebar a shade darker, its right edge square against the content.
        pushClip(wx, wy, SIDEBAR_W, MAIN_H);
        Render2D.roundRect(ctx, wx, wy, SIDEBAR_W + r * 2, MAIN_H, r, 0x2A000000);
        popClip();
        Render2D.rect(ctx, wx + SIDEBAR_W, wy, Render2D.px(), MAIN_H, ColorUtil.withAlpha(Theme.BORDER, 0x90));
        Render2D.roundOutline(ctx, wx, wy, MAIN_W, MAIN_H, r, Render2D.px(), Theme.BORDER);
        renderSidebar(ctx, wx, wy, SIDEBAR_W, MAIN_H);

        float hx = wx + SIDEBAR_W + 16, hw = MAIN_W - SIDEBAR_W - 32;
        float hy = wy + 15;
        renderHeader(ctx, hx, hy, hw, HEADER_H);

        // page
        boolean searching = !search.getText().isEmpty();
        if (searching != wasSearching) {
            wasSearching = searching;
            pageSwitchedAt = System.currentTimeMillis();
            if (searching) searchPage.onOpen();
            else entries.get(selected).page().onOpen();
        }
        float py = hy + HEADER_H + 10, ph = wy + MAIN_H - 10 - py;
        float pt = Easing.outCubic((System.currentTimeMillis() - pageSwitchedAt) / (260f / ClientSettings.animationSpeed()));
        Render2D.setAlpha(p * pt);
        pushClip(hx - 4, py, hw + 8, ph);
        currentPage().render(ctx, hx, py + (1 - pt) * 8f, hw, ph);
        popClip();
        Render2D.setAlpha(p);
        interactive = true;
    }

    private void renderSidebar(DrawContext ctx, float x, float y, float w, float h) {
        // The logo row is the handle that drags the window.
        hit(x, y, w, 36, (button, mx, my) -> {
            if (button != 0 || paneDraw || spatial.isOpen()) return;
            final float sx = dragX, sy = dragY;
            startDrag(WINDOW, (nx, ny) -> { dragX = sx + (float) (nx - mx); dragY = sy + (float) (ny - my); });
        });
        // The blue M of the logo, then the rest of the name in white.
        Logo.mark(ctx, x + 23, y + 21, 19, 0);
        Fonts.beginRaw();
        Fonts.drawV(ctx, "aro", x + 32, y + 21.5f, Theme.TEXT, true, 1.32f);
        Fonts.endRaw();

        search.render(this, ctx, x + 10, y + 40, w - 20, 19, SEARCH_ICON, focused == search ? null : "Ctrl K");

        float rx = x + 8, rw = w - 16, rowH = 22f;
        float ry = y + 70;
        sideLabel(ctx, "MODULES", rx + 4, ry);
        ry += 12;
        for (int i = 0; i < moduleEntries; i++) {
            Entry entry = entries.get(i);
            int count = ((ModulesPage) entry.page()).all().size();
            boolean locked = entry.category() == Category.ANTI_CHEAT_OFF && !antiCheatSectionUnlocked;
            if (count == 0 && !locked && selected != i) continue; // nothing to show in it
            sideRow(ctx, entry, i, rx, ry, rw, rowH, locked ? null : String.valueOf(count));
            ry += rowH + 1;
        }
        ry += 8;
        sideLabel(ctx, "CLIENT", rx + 4, ry);
        ry += 12;
        for (int i = moduleEntries; i < entries.size(); i++) {
            sideRow(ctx, entries.get(i), i, rx, ry, rw, rowH, "");
            ry += rowH + 1;
        }

        // Screen GUI, at the bottom: the menu placed in the world.
        float fy = y + h - 28;
        boolean hov = hovered(rx, fy, rw, 20);
        float hv = Anims.of(WINDOW, "screenGuiHover", hov);
        Render2D.roundRect(ctx, rx, fy, rw, 20, 6f, ColorUtil.withAlpha(0xFFFFFFFF, 0x08 + Math.round(0x08 * hv)));
        Glyphs.draw(ctx, GlyphNames.MONITOR, rx + 12, fy + 10, 10f, Theme.TEXT_MUTED);
        Fonts.drawV(ctx, "Screen GUI", rx + 23, fy + 10, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), false, 0.72f);
        Widgets.toggle(ctx, rx + rw - 24, fy + 5.5f, 18, 9, Anims.of(WINDOW, "screenGui", ClientSettings.screenGui.get()), hv);
        hit(rx, fy, rw, 20, (button, mx, my) -> { if (button == 0) { ClientSettings.screenGui.set(!ClientSettings.screenGui.get()); Sounds.click(); } });
        place(SCREEN_GUI_SWITCH, rx + rw / 2f, fy + 10);
        if (hov) tooltip("Place the menu in the world, in front of you");
    }

    private static void sideLabel(DrawContext ctx, String text, float x, float y) {
        Fonts.draw(ctx, text, x, y, ColorUtil.withAlpha(Theme.TEXT_MUTED, 0xB0), true, 0.58f);
    }

    private void sideRow(DrawContext ctx, Entry entry, int index, float x, float y, float w, float h, String count) {
        boolean hov = hovered(x, y, w, h);
        float hv = Anims.of(entry, "sideHover", hov);
        float sel = Anims.of(entry, "sideSelected", selected == index && search.getText().isEmpty());
        Render2D.roundRect(ctx, x, y, w, h, 6f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x0C * hv)), Theme.accent(0x22), sel));
        if (sel > 0.01f) Render2D.roundOutline(ctx, x, y, w, h, 6f, 1f, Theme.accent(Math.round(0x50 * sel)));
        int fg = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, Math.max(hv, sel));
        Glyphs.draw(ctx, entry.glyph(), x + 13, y + h / 2f, 11f, ColorUtil.lerp(fg, Theme.accent(), sel));
        Fonts.drawV(ctx, Fonts.trim(entry.label(), w - 50, false, 0.8f), x + 26, y + h / 2f, fg, false, 0.8f);
        if (count == null) Glyphs.draw(ctx, GlyphNames.LOCK, x + w - 11, y + h / 2f, 8f, Theme.TEXT_MUTED);
        else if (!count.isEmpty()) Fonts.drawRight(ctx, count, x + w - 8, y + h / 2f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, sel), false, 0.64f);
        hit(x, y, w, h, (button, mx, my) -> { if (button == 0) { openPage(index); Sounds.click(); } });
        if (interactive) entryPlaces.put(index, new float[] {x + w / 2f, y + h / 2f});
        if (hov && entry.subtitle() != null) tooltip(entry.subtitle());
    }

    private void renderHeader(DrawContext ctx, float x, float y, float w, float h) {
        boolean searching = !search.getText().isEmpty();
        Entry e = entries.get(selected);
        boolean locked = !searching && e.category() == Category.ANTI_CHEAT_OFF && !antiCheatSectionUnlocked;
        boolean modules = (searching || e.category() != null) && !locked;
        String title = searching ? "Search" : e.label();
        String subtitle;
        if (searching) {
            int n = searchPage.all().size();
            subtitle = n + (n == 1 ? " match for \"" : " matches for \"") + search.getText() + "\"";
        } else subtitle = e.subtitle();

        float filtersW = modules ? filters(ctx, x + w, y + h / 2f) : 0;
        float titleMax = w - filtersW - 12;
        float titleY = subtitle == null ? y + h / 2f : y + 9;
        Fonts.drawV(ctx, Fonts.trim(title, titleMax, true, 1.3f), x, titleY, Theme.TEXT, true, 1.3f);
        if (subtitle != null) Fonts.drawV(ctx, Fonts.trim(subtitle, titleMax, false, 0.68f), x, y + 23, Theme.TEXT_MUTED, false, 0.68f);
    }

    /** The All / Enabled / Starred chips, right-aligned at {@code right}; returns their width. */
    private float filters(DrawContext ctx, float right, float cy) {
        float h = 18, pad = 11, scale = 0.74f;
        Filter[] all = Filter.values();
        float[] widths = new float[all.length];
        float total = 4;
        for (int i = 0; i < all.length; i++) {
            widths[i] = Fonts.width(all[i].label, false, scale) + pad * 2;
            total += widths[i];
        }
        float x = right - total, y = cy - h / 2f;
        Render2D.roundRect(ctx, x, y, total, h, 6f, 0x30000000);
        Render2D.roundOutline(ctx, x, y, total, h, 6f, 1f, ColorUtil.withAlpha(Theme.BORDER, 0x80));
        float sx = x + 2;
        for (int i = 0; i < all.length; i++) {
            Filter f = all[i];
            boolean sel = filter == f;
            boolean hov = hovered(sx, y, widths[i], h);
            float t = Anims.of(f, "chip", sel ? 1f : 0f);
            float hv = Anims.of(f, "chipHover", hov);
            if (t > 0.01f) Render2D.roundRect(ctx, sx, y + 2, widths[i], h - 4, 4.5f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x1A * t)));
            int fg = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), Theme.TEXT, t);
            Fonts.drawCentered(ctx, f.label, sx + widths[i] / 2f, cy, fg, false, scale);
            hit(sx, y, widths[i], h, (button, mx, my) -> { if (button == 0) { filter = f; Sounds.click(); } });
            place(f, sx + widths[i] / 2f, cy);
            sx += widths[i];
        }
        return total;
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
        Render2D.roundRect(ctx, x, y, w, h, 4, 0xF5181A20);
        Render2D.roundOutline(ctx, x, y, w, h, 4, 1f, 0xFF30343E);
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
        mouseDown = false;
        drag = null;
        dragOwner = null;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
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
        for (int i = scrollHits.size() - 1; i >= 0; i--) {
            ScrollHit h = scrollHits.get(i);
            if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                h.handler.accept(verticalAmount);
                return true;
            }
        }
        if (panelRect != null && panelRect.contains(mouseX, mouseY)) settings.onScroll(verticalAmount);
        else currentPage().onScroll(verticalAmount);
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
            if (dev.maro.gui.widget.Dropdown.anyOpen()) dev.maro.gui.widget.Dropdown.close();
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
        drag = null;
    }

    @Override
    public void tick() {
        if (!client.isWindowFocused()) { worldMovement.clear(); drag = null; mouseDown = false; }
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
