package dev.maro.nathan.modules;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.meteor.KeyEvent;
import dev.maro.runtime.events.meteor.MouseClickEvent;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WLabel;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.containers.WVerticalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.misc.input.KeyAction;
import dev.maro.runtime.utils.render.color.Color;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.audio.KeySoundPlayer;
import dev.maro.nathan.audio.MechvibesPack;

/**
 * The sound of a mechanical keyboard under your keys.
 *
 * <p>It has nothing to do with Keystrokes: it listens to the keyboard itself,
 * not to that module, and is on or off on its own.
 *
 * <p><b>Once a press.</b> A key held down makes the keyboard send the press
 * again and again; those are told apart from the first and are silent. On top
 * of that the keys that are down are kept, and a key already down makes no
 * sound however it is reported, until it has come up.
 *
 * <p><b>The addon's own presets</b> have four takes of a key, a spacebar and a
 * mouse button. A press takes a take other than the last, and plays it a shade
 * faster or slower and a shade louder or quieter, so a run of keys is a run of
 * keys and not one sample on a loop. The spacebar is its own, deeper sample,
 * and the other wide keys - Enter, Backspace, Shift, Tab - are the ordinary key
 * played a little lower. Those samples are made by
 * {@code tools/KeySoundGen.java}.
 *
 * <p><b>EG Oreo</b> is somebody's recording of a real keyboard, from Mechvibes,
 * and is not in this addon: it is read from a folder, which Import fills from a
 * Mechvibes installed on this machine. Every key is the recording of that key,
 * by Mechvibes' own table of which is where, and is played as it was recorded,
 * at its own pitch. Until the pack is there the module says so and plays Creamy.
 */
public class KeySounds extends Module {
    public enum Preset {
        Creamy("creamy", "Creamy"),
        Thock("thock", "Thock"),
        Soft("soft", "Soft"),
        Clicky("clicky", "Clicky"),
        CreamyDeep("creamy-deep", "Creamy Deep"),
        CreamyLight("creamy-light", "Creamy Light"),
        Silky("silky", "Silky"),
        Milky("milky", "Milky"),
        Marshmallow("marshmallow", "Marshmallow"),
        Velvet("velvet", "Velvet"),
        Poppy("poppy", "Poppy"),
        Bubble("bubble", "Bubble"),
        Marble("marble", "Marble"),
        Rain("rain", "Rain"),
        EgOreo("creamy", "EG Oreo");

        /** Where the addon's own samples for it are. For a pack, the ones that stand in while it is missing. */
        private final String folder;
        private final String title;

        Preset(String folder, String title) {
            this.folder = folder;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final int TAKES = 4;

    /** What Mechvibes calls the pack's folder, and what it is called here. */
    private static final String OREO = "eg-oreo";

    private static final Color GOOD = new Color(110, 220, 130);
    private static final Color BAD = new Color(240, 100, 100);

    private final KeySoundPlayer player = new KeySoundPlayer();
    private final Random random = new Random();

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Preset> preset = sgMain.add(new EnumSetting.Builder<Preset>()
        .name("sound-pack")
        .description("14 bundled keyboard sounds, from deep Creamy and Marshmallow to bright Poppy and Marble. EG Oreo is a separate Mechvibes pack that needs importing.")
        .defaultValue(Preset.Creamy)
        .onChanged(value -> packChanged(true))
        .build()
    );

    private final Setting<Boolean> previewOnChange = sgMain.add(new BoolSetting.Builder()
        .name("preview-on-change")
        .description("Play a short preview when you choose a different sound pack in the menu.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> volume = sgMain.add(new IntSetting.Builder()
        .name("volume")
        .description("How loud, in per cent. The game's master volume applies on top.")
        .defaultValue(60)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> mouseClickSounds = sgMain.add(new BoolSetting.Builder()
        .name("mouse-click-sounds")
        .description("A sound for mouse buttons as well.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> playWhileTyping = sgMain.add(new BoolSetting.Builder()
        .name("play-while-typing-in-chat")
        .description("Also sound while chat, or any other screen, is open. Off, only keys pressed in the game itself are heard.")
        .defaultValue(true)
        .build()
    );

    /** What is down, so that nothing sounds twice without coming up in between. */
    private final Set<Integer> keysDown = new HashSet<>();
    private final Set<Integer> buttonsDown = new HashSet<>();

    private int lastTake;

    /** Goes up with every preview, so that one started before the last is dropped. */
    private int previews;

    /** EG Oreo, once it has been read; and if it could not be, why not. */
    private MechvibesPack oreo;
    private String oreoProblem = "";
    private boolean oreoLoading;
    private boolean oreoTried;
    private boolean warned;

    private WLabel packLabel;

    public KeySounds() {
        super(NameeProtectAddon.CATEGORY, "key-sounds", "Mechanical keyboard sounds for your key presses. Separate from Keystrokes.");

        // Keys are keys at the menu too.
        runInMainMenu = true;
    }

    /** The setting was called Preset before there was a pack that is not one. What was chosen is kept. */
    @Override
    public Module fromTag(NbtCompound tag) {
        for (NbtElement group : tag.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(group instanceof NbtCompound groupTag)) continue;

            for (NbtElement setting : groupTag.getListOrEmpty("settings")) {
                if (setting instanceof NbtCompound saved && saved.getString("name", "").equals("preset")) saved.putString("name", "sound-pack");
            }
        }

        return super.fromTag(tag);
    }

    @Override
    public void onActivate() {
        warned = false;
        packChanged(false);
    }

    @Override
    public void onDeactivate() {
        previews++;
        keysDown.clear();
        buttonsDown.clear();

        // Stopped, and the buffers and sources handed back to the sound card.
        player.close();
    }

    /** Nothing should ring on across a disconnect. */
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        player.stop();
    }

    // ----------------------------------------------------------------- the pack

    /** Where EG Oreo is read from: {@code config.json} and the sound it names. */
    public static Path oreoFolder() {
        return MeteorClient.FOLDER.toPath().resolve("nameeprotect").resolve("keysounds").resolve(OREO);
    }

    private void packChanged(boolean preview) {
        previews++;
        lastTake = 0;
        player.unload();

        if (preset.get() == Preset.EgOreo && oreo == null && !oreoTried) readOreo();

        showPack();
        if (preview && previewOnChange.get() && mc.currentScreen != null)
            preview(new int[] {GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_SPACE}, 110);
    }

    /** Off the main thread: it is a third of a megabyte of Ogg to decode. */
    private void readOreo() {
        if (oreoLoading) return;

        oreoLoading = true;
        oreoTried = true;
        showPack();

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return MechvibesPack.load(oreoFolder());
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }, Util.getIoWorkerExecutor())
            .whenCompleteAsync((pack, error) -> {
                oreoLoading = false;
                oreo = pack;

                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;

                    oreoProblem = String.valueOf(cause.getMessage());
                    NameeProtectAddon.LOG.warn("key-sounds: EG Oreo is not available: {}", oreoProblem);

                    if (isActive() && preset.get() == Preset.EgOreo && !warned && mc.player != null) {
                        warned = true;
                        warning("EG Oreo is not installed, so Creamy is playing instead. Open the module and press Import from Mechvibes.");
                    }
                } else {
                    oreoProblem = "";
                }

                // Reading an optional pack must not interrupt another selected preset.
                if (preset.get() == Preset.EgOreo) {
                    previews++;
                    player.unload();
                    if (oreo != null && previewOnChange.get() && mc.currentScreen != null)
                        preview(new int[] {GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_SPACE}, 110);
                }
                showPack();
            }, mc);
    }

    private void importOreo() {
        if (oreoLoading) return;

        oreoLoading = true;
        showPack();

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return MechvibesPack.importFromMechvibes(OREO, oreoFolder());
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }, Util.getIoWorkerExecutor())
            .whenCompleteAsync((from, error) -> {
                oreoLoading = false;

                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;

                    oreo = null;
                    oreoProblem = String.valueOf(cause.getMessage());
                    showPack();
                    return;
                }

                NameeProtectAddon.LOG.info("key-sounds: EG Oreo imported from {}", from);

                oreo = null;
                readOreo();
            }, mc);
    }

    private String packLine() {
        if (oreoLoading) return "EG Oreo: reading...";
        if (oreo != null) return "EG Oreo: installed, " + oreo.size() + " keys." + (preset.get() == Preset.EgOreo ? "" : " Choose it under Sound Pack.");

        String using = preset.get() == Preset.EgOreo ? " Creamy is playing instead." : "";

        return "EG Oreo: NOT INSTALLED." + using + (oreoProblem.isEmpty() ? "" : " (" + oreoProblem + ")");
    }

    private void showPack() {
        if (packLabel == null) return;

        packLabel.set(packLine());
        packLabel.color(oreo != null ? GOOD : oreoLoading ? packLabel.theme.textSecondaryColor() : BAD);
    }

    // ------------------------------------------------------------------ playing

    @EventHandler
    private void onKey(KeyEvent event) {
        int key = event.key();

        if (event.action == KeyAction.Release) {
            keysDown.remove(key);
            return;
        }

        if (event.action != KeyAction.Press || !keysDown.add(key) || silenced()) return;

        playKey(key);
    }

    @EventHandler
    private void onMouse(MouseClickEvent event) {
        int button = event.button();

        if (event.action == KeyAction.Release) {
            buttonsDown.remove(button);
            return;
        }

        if (event.action != KeyAction.Press || !buttonsDown.add(button) || !mouseClickSounds.get() || silenced()) return;

        // A keyboard pack has no mouse in it; the click is the addon's own.
        play("mouse", 1);
    }

    private boolean silenced() {
        return mc.currentScreen != null && !playWhileTyping.get();
    }

    private void playKey(int key) {
        if (preset.get() == Preset.EgOreo && oreo != null) {
            MechvibesPack.Clip clip = oreo.clipFor(key, random);
            float gain = volume.get() / 100f;

            // As it was recorded: that key, at its own pitch and its own level.
            if (clip != null && gain > 0) player.play(clip, gain, 1);

            return;
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            play("space", 1);
        } else {
            play(take(), wide(key) ? 0.9f : 1);
        }
    }

    /** The keys with a wide cap, which sit lower than the rest. */
    private static boolean wide(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_TAB
            || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_CAPS_LOCK;
    }

    /** One of the four, and not the one before. */
    private String take() {
        int next = 1 + random.nextInt(TAKES - 1);

        lastTake = (lastTake + next) % TAKES;

        return "key" + (lastTake + 1);
    }

    private void play(String sample, float pitch) {
        float gain = volume.get() / 100f * (0.92f + random.nextFloat() * 0.16f);

        if (gain <= 0) return;

        player.play("/assets/nameeprotect/keysounds/" + preset.get().folder + "/" + sample + ".wav", gain, pitch * (0.97f + random.nextFloat() * 0.06f));
    }

    // -------------------------------------------------------------------- panel

    /** Under the settings: the preview, and whether EG Oreo is there. */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WButton preview = list.add(theme.button("Preview Sound")).expandX().widget();

        preview.action = () -> preview(new int[] {GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_BACKSPACE, -1}, 140);

        list.add(theme.horizontalSeparator("EG Oreo")).expandX();

        packLabel = list.add(theme.label(packLine(), 360)).expandX().widget();

        WHorizontalList row = list.add(theme.horizontalList()).expandX().widget();

        WButton fetch = row.add(theme.button("Import from Mechvibes")).expandX().widget();
        fetch.action = this::importOreo;

        WButton again = row.add(theme.button("Read Folder Again")).expandX().widget();

        again.action = () -> {
            oreo = null;
            readOreo();
        };

        WButton open = row.add(theme.button("Open Folder")).expandX().widget();

        open.action = () -> {
            try {
                java.nio.file.Files.createDirectories(oreoFolder());
                Util.getOperatingSystem().open(oreoFolder());
            } catch (IOException | RuntimeException e) {
                NameeProtectAddon.LOG.warn("key-sounds: could not open {}", oreoFolder(), e);
            }
        };

        list.add(theme.label("The pack is not bundled. Import copies it from Mechvibes installed on this computer. Or put its config.json and oreo.ogg in maro / nameeprotect / keysounds / eg-oreo under the game folder - Open Folder goes there.", 360)).widget().color(theme.textSecondaryColor());

        if (oreo == null && !oreoLoading && !oreoTried) readOreo();
        showPack();
        return list;
    }

    /** Changing presets cancels earlier queued previews and stops their live sources. */
    private void preview(int[] order, int spacingMs) {
        int mine = ++previews;
        player.stop();

        for (int i = 0; i < order.length; i++) {
            int key = order[i];

            if (key < 0 && !mouseClickSounds.get()) continue;

            CompletableFuture.delayedExecutor(i * (long)spacingMs, TimeUnit.MILLISECONDS).execute(() -> mc.execute(() -> {
                if (mine != previews) return;

                if (key < 0) play("mouse", 1);
                else playKey(key);
            }));
        }

        // Off, it holds nothing between previews either.
        if (!isActive()) {
            CompletableFuture.delayedExecutor(2500, TimeUnit.MILLISECONDS).execute(() -> mc.execute(() -> {
                if (mine == previews && !isActive()) player.close();
            }));
        }
    }
}
