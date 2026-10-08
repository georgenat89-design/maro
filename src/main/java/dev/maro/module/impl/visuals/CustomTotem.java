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
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
 * animation when one saves you. The totem's texture is swapped while the game loads its textures
 * (see {@link dev.maro.mixin.CustomTotemSpriteMixin}), so everything that draws the totem shows it.
 * The pop animation can also be made bigger, smaller or switched off.
 */
public class CustomTotem extends Module {
    private static final String TOTEM_SPRITE = "item/totem_of_undying";

    private static CustomTotem instance;

    private final ButtonSetting choose = add(new ButtonSetting("Your Image",
            "Pick a PNG for the totem: in your hand, your inventory and the pop animation", "Choose",
            () -> mc.setScreen(new TotemImageScreen(mc.currentScreen, this))));
    private final BooleanSetting pop = add(new BooleanSetting("Pop Animation", "Show the big totem on screen when one saves you", true));
    private final NumberSetting popSize = add(new NumberSetting("Pop Size", "How big the pop animation is", 100, 50, 250, 5)
            .suffix("%").visible(pop::get));

    /** A picture ready to be the totem, numbered so the loaded texture can say which one it was made from. */
    private record Picture(TotemTexture.Decoded image, int version) { }

    private static final AtomicInteger versions = new AtomicInteger();

    /** Your picture; also read by the texture loader on its own thread. */
    private volatile Picture picture;
    private Picture previewOf;
    private Texture preview;
    private boolean busy, closing;
    private String error;

    /** Which picture the loaded totem texture was made from (0: the normal totem), and the last reload asked for. */
    private static volatile int loadedVersion;
    private static int requestedVersion = -1;

    public CustomTotem() {
        super("Custom Totem", "Your own picture as the Totem of Undying, and a bigger, smaller or no pop animation", Category.VISUALS);
        instance = this;
        // Runs whether or not the module is on, so switching it off brings the normal totem back.
        ClientTickEvents.END_CLIENT_TICK.register(client -> syncTexture());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closing = true);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Totem", choose, pop, popSize));
    }

    @Override
    protected void onEnable() {
        loadStoredImage();
    }

    // ---- the texture ------------------------------------------------------------------------

    private static int wantedVersion() {
        CustomTotem m = instance;
        Picture p = m != null && m.isEnabled() ? m.picture : null;
        return p != null ? p.version() : 0;
    }

    /** Asks for one texture reload whenever the totem texture loaded is not the one wanted. */
    private static void syncTexture() {
        int wanted = wantedVersion();
        if (wanted == loadedVersion || wanted == requestedVersion || mc.getOverlay() != null) return;
        requestedVersion = wanted;
        mc.reloadResources();
    }

    /** Whether the loaded totem texture is the one wanted and no reload is running. */
    public static boolean textureSettled() {
        return wantedVersion() == loadedVersion && mc.getOverlay() == null;
    }

    /** Called as each texture loads: your picture if {@code id} is the totem and it should be swapped, else null. */
    public static TotemTexture.Decoded pictureFor(Identifier id) {
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !id.getPath().equals(TOTEM_SPRITE)) return null;
        CustomTotem m = instance;
        Picture p = null;
        if (m != null && m.isEnabled()) {
            // Straight after starting the game the saved picture may still be on its way; it is
            // needed now, so read it here rather than load the textures a second time for it.
            p = m.picture != null ? m.picture : m.readStoredNow();
        }
        loadedVersion = p != null ? p.version() : 0;
        return p != null ? p.image() : null;
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
        return "Using a " + image.sourceWidth() + " × " + image.sourceHeight() + " image (" + image.size() + " × " + image.size() + " totem)";
    }

    /** Your picture, or null if there is none. */
    public TotemTexture.Decoded image() {
        Picture p = picture;
        return p != null ? p.image() : null;
    }

    /** A sharp, unfiltered copy of your picture for the Choose screen; made on the render thread when first asked for. */
    public Texture previewTexture() {
        Picture p = picture;
        if (p != previewOf) {
            if (preview != null) preview.close();
            preview = p != null ? makePreview(p.image()) : null;
            previewOf = p;
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
                Notifications.push(getName(), "Your totem is ready - textures reload for a moment", Notifications.Type.INFO);
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

    private static Texture makePreview(TotemTexture.Decoded image) {
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
