package dev.maro.nathan.modules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.meteor.KeyEvent;
import dev.maro.runtime.events.meteor.MouseClickEvent;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.settings.Settings;
import dev.maro.runtime.settings.StringListSetting;
import dev.maro.runtime.settings.StringSetting;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.runtime.utils.misc.input.KeyAction;
import dev.maro.runtime.utils.player.ChatUtils;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.gui.ChatMacroScreens;

/**
 * Saved chat messages and commands on keys.
 *
 * <p>A macro is a name, a key, and one or more steps: lines that are sent as
 * you would have typed them - a line starting with a slash is a command - with a
 * wait between them. Nothing is ever sent except because a key was pressed, by
 * you, in the game: not on joining, not on a timer, not while a screen is open,
 * and not again while the key is merely held down.
 *
 * <p><b>Staying inside the rules.</b> A server counts what you send and kicks
 * for too much of it. The game's own count is kept here too - twenty for every
 * message, one off every tick - and a step waits rather than send when sending
 * would take it near the limit, so a long macro slows down instead of getting
 * you kicked. Messages are cut to the 256 characters the game allows. Leaving a
 * server throws away whatever was still waiting to be sent.
 *
 * <p>Each macro is a set of Meteor's own settings, so every box in its editor is
 * Meteor's own - the text boxes, the key box that listens for a key, the numbers
 * you can type into - and looks and behaves like the rest of the client.
 */
public class ChatMacros extends Module {
    /** What a step can say in place of something that is only known when it is sent. */
    public static final String[] PLACEHOLDERS = {"{player}", "{x}", "{y}", "{z}", "{server}", "{dimension}", "{health}"};

    /** A step that sends nothing and waits instead: "@wait 1500" is a second and a half. */
    public static final String WAIT = "@wait";

    /** The least time between any two messages, and how near the server's spam limit of 200 this will go. */
    private static final long GAP = 350;
    private static final int HEAT_LIMIT = 160;

    /** How long a macro that asks first stays waiting for its second press. */
    private static final long ARMED = 4000;

    /** One macro. Its fields are settings, so Meteor draws its editor and saves it. */
    public static final class Macro {
        public final Settings settings = new Settings();

        private final SettingGroup sgMain = settings.getDefaultGroup();
        private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);

        public final Setting<String> name = sgMain.add(new StringSetting.Builder()
            .name("name")
            .description("What this macro is called in the list.")
            .defaultValue("New macro")
            .build()
        );

        public final Setting<Boolean> enabled = sgMain.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Off, its key does nothing.")
            .defaultValue(true)
            .build()
        );

        public final Setting<Keybind> keybind = sgMain.add(new KeybindSetting.Builder()
            .name("keybind")
            .description("The key, or mouse button, that runs it. It runs once for each press, not over and over while held.")
            .defaultValue(Keybind.none())
            .build()
        );

        public final Setting<List<String>> steps = sgMain.add(new StringListSetting.Builder()
            .name("steps")
            .description("What is sent, in order. A line starting with / is a command. A line reading \"@wait 1500\" sends nothing and waits that many milliseconds. {player}, {x}, {y}, {z}, {server}, {dimension} and {health} are filled in when the line is sent.")
            .defaultValue(List.of("Hello!"))
            .build()
        );

        public final Setting<Double> cooldown = sgAdvanced.add(new DoubleSetting.Builder()
            .name("cooldown")
            .description("Seconds before this macro will run again, so a slip of the finger does not send it twice.")
            .defaultValue(1)
            .range(0, 3600)
            .sliderRange(0, 30)
            .decimalPlaces(1)
            .build()
        );

        public final Setting<Integer> stepDelay = sgAdvanced.add(new IntSetting.Builder()
            .name("step-delay")
            .description("Milliseconds between one step and the next. \"@wait\" steps add to it.")
            .defaultValue(750)
            .range(0, 60000)
            .sliderRange(0, 5000)
            .build()
        );

        public final Setting<String> server = sgAdvanced.add(new StringSetting.Builder()
            .name("only-on-server")
            .description("Only run on servers whose address contains this, such as \"hypixel\". Empty is everywhere. Single player counts as \"singleplayer\".")
            .defaultValue("")
            .build()
        );

        public final Setting<Boolean> confirm = sgAdvanced.add(new BoolSetting.Builder()
            .name("ask-first")
            .description("The first press shows what would be sent; a second press within four seconds sends it.")
            .defaultValue(false)
            .build()
        );

        long lastRun;
        long armedAt;

        public NbtCompound toTag() {
            return settings.toTag();
        }

        public Macro fromTag(NbtCompound tag) {
            settings.fromTag(tag);
            return this;
        }

        /** The same macro again, without its key: two macros on one key is a conflict, not a copy. */
        public Macro duplicate() {
            Macro copy = new Macro().fromTag(toTag());

            copy.name.set(name.get() + " copy");
            copy.keybind.set(Keybind.none());

            return copy;
        }

        /** The first thing it would send, as it is written, for the list. */
        public String preview() {
            for (String step : steps.get()) {
                if (!step.isBlank() && !step.trim().startsWith(WAIT)) return step.trim();
            }

            return "(nothing to send)";
        }
    }

    /** A macro part way through its steps. */
    private static final class Running {
        final Macro macro;
        int step;
        long nextAt;

        Running(Macro macro, long now) {
            this.macro = macro;
            this.nextAt = now;
        }
    }

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Keybind> cancelKey = sgMain.add(new KeybindSetting.Builder()
        .name("cancel-key")
        .description("Stops every macro that is part way through its steps. Nothing more of them is sent.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> feedback = sgMain.add(new BoolSetting.Builder()
        .name("feedback")
        .description("A line over the hotbar when a macro runs, is on cooldown, or is waiting for its second press. Nothing goes in chat.")
        .defaultValue(true)
        .build()
    );

    public final List<Macro> macros = new ArrayList<>();

    private final List<Running> running = new ArrayList<>();
    private long lastSentAt;
    private int heat;

    public ChatMacros() {
        super(NameeProtectAddon.CATEGORY, "chat-macros", "Saved chat messages and commands on keys, with steps, cooldowns and a preview. Only ever sent by a key press.");
    }

    // ---------------------------------------------------------------- saving

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        NbtList list = new NbtList();

        for (Macro macro : macros) list.add(macro.toTag());

        tag.put("macros", list);
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        Module loaded = super.fromTag(tag);

        macros.clear();

        for (NbtElement entry : tag.getListOrEmpty("macros")) {
            if (!(entry instanceof NbtCompound saved)) continue;

            try {
                macros.add(new Macro().fromTag(saved));
            } catch (RuntimeException e) {
                // One macro that will not load should cost that macro only.
                NameeProtectAddon.LOG.warn("chat-macros could not load a saved macro", e);
            }
        }

        return loaded;
    }

    // --------------------------------------------------------------- running

    @Override
    public void onDeactivate() {
        cancelAll();
    }

    /** Leaving throws away whatever was waiting to be sent. Nothing here ever starts a macro on joining. */
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cancelAll();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        // A press, not a key held down repeating, and not while a screen is
        // open: typing in chat, or into a text box, is not pressing a macro.
        if (event.action != KeyAction.Press || mc.currentScreen != null || mc.player == null) return;

        if (cancelKey.get().isSet() && cancelKey.get().matches(event.input)) {
            cancelByKey();
            return;
        }

        for (Macro macro : macros) {
            if (macro.enabled.get() && macro.keybind.get().isSet() && macro.keybind.get().matches(event.input)) trigger(macro);
        }
    }

    @EventHandler
    private void onMouse(MouseClickEvent event) {
        if (event.action != KeyAction.Press || mc.currentScreen != null || mc.player == null) return;

        if (cancelKey.get().isSet() && cancelKey.get().matches(event.input)) {
            cancelByKey();
            return;
        }

        for (Macro macro : macros) {
            if (macro.enabled.get() && macro.keybind.get().isSet() && macro.keybind.get().matches(event.input)) trigger(macro);
        }
    }

    private void cancelByKey() {
        if (running.isEmpty()) return;

        cancelAll();
        say("Macros cancelled");
    }

    private void cancelAll() {
        running.clear();

        for (Macro macro : macros) macro.armedAt = 0;
    }

    private void trigger(Macro macro) {
        long now = System.currentTimeMillis();

        String only = macro.server.get().trim().toLowerCase(Locale.ROOT);
        if (!only.isEmpty() && !serverName().toLowerCase(Locale.ROOT).contains(only)) return;

        for (Running job : running) {
            if (job.macro == macro) {
                say(macro.name.get() + " is already running");
                return;
            }
        }

        long wait = (long) (macro.cooldown.get() * 1000) - (now - macro.lastRun);

        if (wait > 0) {
            say(String.format(Locale.ROOT, "%s: cooldown %.1fs", macro.name.get(), wait / 1000.0));
            return;
        }

        if (macro.confirm.get() && now - macro.armedAt > ARMED) {
            macro.armedAt = now;
            say("Press again to send: " + resolve(macro.preview()));
            return;
        }

        macro.armedAt = 0;
        macro.lastRun = now;
        running.add(new Running(macro, now));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // The server's own count: twenty for a message, one off each tick.
        if (heat > 0) heat--;

        if (running.isEmpty() || mc.player == null) return;

        long now = System.currentTimeMillis();

        for (Iterator<Running> it = running.iterator(); it.hasNext();) {
            Running job = it.next();
            List<String> steps = job.macro.steps.get();

            if (job.step >= steps.size()) {
                it.remove();
                say("Sent " + job.macro.name.get());
                continue;
            }

            if (now < job.nextAt) continue;

            String step = steps.get(job.step).trim();

            if (step.isEmpty()) {
                job.step++;
                continue;
            }

            if (step.startsWith(WAIT)) {
                job.nextAt = now + waitOf(step);
                job.step++;
                continue;
            }

            // Too soon after the last message, or too near the limit: this
            // step waits for a later tick. It is slowed down, not dropped.
            if (now - lastSentAt < GAP || heat + 20 > HEAT_LIMIT) continue;

            ChatUtils.sendPlayerMsg(clip(resolve(step)));

            lastSentAt = now;
            heat += 20;
            job.nextAt = now + job.macro.stepDelay.get();
            job.step++;

            // One message a tick, whatever is queued.
            break;
        }
    }

    // ------------------------------------------------------------------ text

    private static long waitOf(String step) {
        try {
            return Math.max(0, Math.min(600000, Long.parseLong(step.substring(WAIT.length()).trim())));
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    /** The game will not send more than 256 characters, and kicks for some of what it will not send. */
    private static String clip(String message) {
        return message.length() > 256 ? message.substring(0, 256) : message;
    }

    private String serverName() {
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) return mc.getCurrentServerEntry().address;

        return "singleplayer";
    }

    /** A step with what it stands for filled in: exactly what would be sent, and the same thing the preview shows. */
    public String resolve(String step) {
        if (mc.player == null) return step;

        return step
            .replace("{player}", mc.player.getGameProfile().name())
            .replace("{x}", String.valueOf(mc.player.getBlockX()))
            .replace("{y}", String.valueOf(mc.player.getBlockY()))
            .replace("{z}", String.valueOf(mc.player.getBlockZ()))
            .replace("{server}", serverName())
            .replace("{dimension}", mc.world == null ? "" : mc.world.getRegistryKey().getValue().getPath())
            .replace("{health}", String.valueOf(Math.round(mc.player.getHealth())));
    }

    /** What a macro would do, step by step, with nothing sent. */
    public List<String> testPreview(Macro macro) {
        List<String> lines = new ArrayList<>();

        for (String raw : macro.steps.get()) {
            String step = raw.trim();

            if (step.isEmpty()) continue;

            if (step.startsWith(WAIT)) {
                lines.add("(wait " + waitOf(step) + " ms)");
            } else {
                String sent = clip(resolve(step));

                lines.add((sent.startsWith("/") ? "command: " : "chat: ") + sent);
            }
        }

        if (lines.isEmpty()) lines.add("(nothing to send)");

        return lines;
    }

    /** Who else has this key: another macro, or one of Meteor's modules. Empty if nobody. */
    public String conflict(Macro macro) {
        Keybind key = macro.keybind.get();
        if (!key.isSet()) return "";

        for (Macro other : macros) {
            if (other != macro && other.keybind.get().isSet() && other.keybind.get().equals(key)) return other.name.get();
        }

        for (Module module : Modules.get().getAll()) {
            if (module.keybind.isSet() && module.keybind.equals(key)) return module.title;
        }

        return "";
    }

    private void say(String line) {
        if (feedback.get() && mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(line), false);
    }

    public boolean feedbackOn() {
        return feedback.get();
    }

    public void setFeedback(boolean on) {
        feedback.set(on);
    }

    public Keybind cancelKey() {
        return cancelKey.get();
    }

    public void setCancelKey(Keybind key) {
        cancelKey.set(key);
    }

    /** Under the settings: the macro menu. */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();

        WButton open = list.add(theme.button("Edit Macros (" + macros.size() + ")")).expandX().widget();
        open.action = () -> mc.setScreen(new dev.maro.gui.hud.ChatMacroScreen(mc.currentScreen, this));

        return list;
    }
}
