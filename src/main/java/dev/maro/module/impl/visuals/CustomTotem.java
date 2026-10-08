package dev.maro.module.impl.visuals;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.Maro;
import dev.maro.gui.hud.TotemImageScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.TotemTexture;
import dev.maro.runtime.renderer.Texture;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Your own picture as the Totem of Undying: in your hand, in the inventory, dropped, and in the pop
 * animation when one saves you. By default it is wrapped over the totem's own shape; it can also be
 * cut out of its background or used whole (see {@link TotemTexture}). The totem's texture is swapped
 * while the game loads its textures (see {@link dev.maro.mixin.CustomTotemSpriteMixin}), so
 * everything that draws the totem shows it. The pop animation can also be resized or switched off.
 */
public class CustomTotem extends Module {
    private static final String TOTEM_SPRITE = "item/totem_of_undying";
    public static final String WRAP = "Wrap", CUT_OUT = "Cut Out", SQUARE = "Square";

    private static CustomTotem instance;

    private final ButtonSetting choose = add(new ButtonSetting("Your Image",
            "Pick a PNG for the totem: in your hand, your inventory and the pop animation", "Choose",
            () -> mc.setScreen(new TotemImageScreen(mc.currentScreen, this))));
    private final ModeSetting shape = add(new ModeSetting("Shape",
            "Wrap covers the totem's own shape with your picture, Cut Out removes a plain background so your picture is the shape, Square uses the whole picture",
            WRAP, WRAP, CUT_OUT, SQUARE));
    private final NumberSetting detail = add(new NumberSetting("Totem Detail", "How much of the totem's own shading shows through a wrap",
            50, 0, 100, 5).suffix("%").visible(() -> shape.is(WRAP)));
    private final BooleanSetting pop = add(new BooleanSetting("Pop Animation", "Show the big totem on screen when one saves you", true));
    private final NumberSetting popSize = add(new NumberSetting("Pop Size", "How big the pop animation is", 100, 50, 250, 5)
            .suffix("%").visible(pop::get));

    /** A picture ready to be the totem, numbered so the loaded texture can say which one it was made from. */
    private record Picture(TotemTexture.Decoded image, int version) { }

    /** How the picture is made into the totem. */
    private record Look(TotemTexture.Shape shape, int detail) {
        TotemTexture.Made make(Picture picture, TotemTexture.Base totem) {
            return TotemTexture.make(picture.image(), shape, totem, detail / 100f);
        }
    }

    /** What the preview was last made from. */
    private record PreviewOf(Picture picture, Look look, TotemTexture.Base totem) { }

    private static final AtomicInteger versions = new AtomicInteger();

    /** Your picture; also read by the texture loader on its own thread. */
    private volatile Picture picture;
    private PreviewOf previewOf;
    private Texture preview;
    private boolean busy, closing;
    private String error;

    /** The game's own totem texture, seen as it last loaded. */
    private static volatile TotemTexture.Base totemBase;
    /** What the loaded totem texture was made from (0: the normal totem), and the last reload asked for. */
    private static volatile int loadedKey;
    private static int requestedKey = -1;
    /** A wanted texture waiting for the settings to settle, and for how many ticks it has. */
    private static int pendingKey = -1, pendingTicks;

    public CustomTotem() {
        super("Custom Totem", "Your own picture as the Totem of Undying, and a bigger, smaller or no pop animation", Category.VISUALS);
        instance = this;
        // Runs whether or not the module is on, so switching it off brings the normal totem back.
        ClientTickEvents.END_CLIENT_TICK.register(client -> syncTexture());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closing = true);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Totem", choose, shape, detail), SettingSection.of("Pop", pop, popSize));
    }

    @Override
    protected void onEnable() {
        loadStoredImage();
    }

    // ---- the texture ------------------------------------------------------------------------

    private Look look() {
        TotemTexture.Shape s = switch (shape.get()) {
            case CUT_OUT -> TotemTexture.Shape.CUT_OUT;
            case SQUARE -> TotemTexture.Shape.SQUARE;
            default -> TotemTexture.Shape.WRAP;
        };
        return new Look(s, s == TotemTexture.Shape.WRAP ? (int) Math.round(detail.get()) : 0);
    }

    /** A number for the texture {@code picture} makes with {@code look}; 0 for the normal totem. */
    private static int key(Picture picture, Look look) {
        return picture == null ? 0 : picture.version() * 1000 + look.shape().ordinal() * 101 + look.detail() + 1;
    }

    private static int wantedKey() {
        CustomTotem m = instance;
        return m != null && m.isEnabled() ? key(m.picture, m.look()) : 0;
    }

    /**
     * Asks for one texture reload whenever the totem texture loaded is not the one wanted, once the
     * settings have stopped changing and no menu is open: a slider dragged or a shape clicked
     * through reloads the textures once, when you are back in the game, not at every step.
     */
    private static void syncTexture() {
        int wanted = wantedKey();
        if (wanted == loadedKey || wanted == requestedKey) return;
        if (wanted != pendingKey) {
            pendingKey = wanted;
            pendingTicks = 0;
        }
        if (++pendingTicks < 10 || mc.currentScreen != null || mc.getOverlay() != null) return;
        requestedKey = wanted;
        mc.reloadResources();
    }

    /** Whether the loaded totem texture is the one wanted and no reload is running. */
    public static boolean textureSettled() {
        return wantedKey() == loadedKey && mc.getOverlay() == null;
    }

    /** The game's own totem texture as it last loaded, or null before it has. */
    public static TotemTexture.Base totemBase() {
        return totemBase;
    }

    /**
     * Called as each texture loads, with the image it was read from: the texture to use instead if
     * {@code id} is the totem and it should be swapped, else null.
     */
    public static TotemTexture.Made pictureFor(Identifier id, NativeImage original) {
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !id.getPath().equals(TOTEM_SPRITE)) return null;
        TotemTexture.Base base = baseOf(original);
        if (base != null) totemBase = base;
        CustomTotem m = instance;
        Picture p = null;
        Look look = null;
        if (m != null && m.isEnabled()) {
            // Straight after starting the game the saved picture may still be on its way; it is
            // needed now, so read it here rather than load the textures a second time for it.
            p = m.picture != null ? m.picture : m.readStoredNow();
            look = m.look();
        }
        loadedKey = key(p, look);
        return p != null ? look.make(p, totemBase) : null;
    }

    /** The first frame of the totem texture the game (or a resource pack) has. */
    private static TotemTexture.Base baseOf(NativeImage image) {
        if (image == null || image.getFormat() != NativeImage.Format.RGBA) return null;
        int w = image.getWidth(), h = image.getHeight();
        if (h > w && h % w == 0) h = w; // an animated strip: its first frame
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) argb[y * w + x] = image.getColorArgb(x, y);
        }
        return new TotemTexture.Base(argb, w, h);
    }

    // ---- the pop animation ----------------------------------------------------------------------

    private static boolean on() {
        return instance != null && instance.isEnabled();
    }

    /** Whether to draw the totem pop animation. */
    public static boolean showsPop() {
        return !on() || instance.pop.get();
    }

    /** How much bigger to draw the totem pop animation. */
    public static float popScale() {
        return on() ? instance.popSize.getFloat() / 100f : 1f;
    }

    // ---- choosing a picture ---------------------------------------------------------------------

    public Path imageFolder() {
        return mc.runDirectory.toPath().resolve("maro/totems");
    }

    private Path storedImage() {
        return imageFolder().resolve("custom.png");
    }

    public boolean imageBusy() {
        return busy;
    }

    public String imageStatus() {
        if (busy) return "Loading image…";
        if (error != null) return error;
        Picture p = picture;
        if (p == null) return "No image chosen yet";
        var image = p.image();
        return "Using a " + image.sourceWidth() + " × " + image.sourceHeight() + " image";
    }

    /** Your picture, or null if there is none. */
    public TotemTexture.Decoded image() {
        Picture p = picture;
        return p != null ? p.image() : null;
    }

    /** The shape setting, for the Choose screen's button. */
    public ModeSetting shapeSetting() {
        return shape;
    }

    /**
     * The totem as it will look, sharp and unfiltered, for the Choose screen; made on the render
     * thread when first asked for, and again whenever the picture or its shape changes.
     */
    public Texture previewTexture() {
        Picture p = picture;
        var now = new PreviewOf(p, look(), totemBase);
        if (!now.equals(previewOf)) {
            if (preview != null) preview.close();
            preview = p != null ? makePreview(now.look().make(p, now.totem())) : null;
            previewOf = now;
        }
        return preview;
    }

    /** The picture kept from last time, if none is loaded yet. */
    public void loadStoredImage() {
        if (picture != null || busy || !Files.isRegularFile(storedImage())) return;
        loadImage(storedImage(), false);
    }

    /** Reads the picture kept from last time on this thread; for the texture loader, which needs it at once. */
    private synchronized Picture readStoredNow() {
        if (picture != null) return picture;
        Path stored = storedImage();
        if (!Files.isRegularFile(stored)) return null;
        try {
            picture = new Picture(TotemTexture.decode(stored), versions.incrementAndGet());
        } catch (IOException | RuntimeException e) {
            Maro.LOGGER.warn("Custom Totem could not read {}", stored, e);
        }
        return picture;
    }

    /** Uses {@code path} as the totem, keeps a copy for next time and turns the module on. */
    public CompletableFuture<Boolean> importImage(Path path) {
        return loadImage(path, true);
    }

    private CompletableFuture<Boolean> loadImage(Path path, boolean importing) {
        if (busy || closing) return CompletableFuture.completedFuture(false);
        busy = true;
        var result = new CompletableFuture<Boolean>();
        CompletableFuture.supplyAsync(() -> {
            try {
                var decoded = TotemTexture.decode(path);
                if (importing) keepCopy(path);
                return decoded;
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((decoded, failure) -> mc.execute(() -> {
            busy = false;
            if (closing) {
                result.complete(false);
                return;
            }
            if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                error = cause.getMessage() == null ? "Could not load this image" : cause.getMessage();
                Notifications.push(getName(), error, Notifications.Type.ERROR);
                result.complete(false);
                return;
            }
            error = null;
            synchronized (this) {
                // The saved picture may have been read already by the texture loader; keep that one.
                if (importing || picture == null) picture = new Picture(decoded, versions.incrementAndGet());
            }
            if (importing) {
                setEnabled(true);
                Notifications.push(getName(), "Your totem is ready - it appears once menus are closed", Notifications.Type.INFO);
            }
            result.complete(true);
        }));
        return result;
    }

    private void keepCopy(Path path) throws IOException {
        Path folder = imageFolder();
        Files.createDirectories(folder);
        Path target = storedImage();
        if (Files.exists(target) && Files.isSameFile(path, target)) return;
        Path temporary = Files.createTempFile(folder, "totem-", ".tmp");
        try {
            // Kept as PNG whatever it came as, so it always loads back the same way.
            var image = ImageIO.read(path.toFile());
            if (image == null || !ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("Could not save a copy of this image");
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Texture makePreview(TotemTexture.Made image) {
        int size = image.size();
        byte[] rgba = new byte[size * size * 4];
        for (int i = 0; i < size * size; i++) {
            int argb = image.argb()[i];
            rgba[i * 4] = (byte) (argb >> 16);
            rgba[i * 4 + 1] = (byte) (argb >> 8);
            rgba[i * 4 + 2] = (byte) argb;
            rgba[i * 4 + 3] = (byte) (argb >>> 24);
        }
        Texture texture = new Texture(size, size, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);
        texture.upload(rgba);
        return texture;
    }
}
