package dev.maro.module.impl.player;

import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.regex.Pattern;

/** Saves an underground home, random-teleports away, then returns to that home. */
public final class MaroRelog extends Module {
    private static final Pattern COMMAND_WAIT = Pattern.compile("wait(?: another)?\\s+([0-9]+(?:\\.[0-9]+)?)\\s*seconds?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOME_ID = Pattern.compile("home\\s*#?\\s*(\\d+)\\b");
    private enum Phase { IDLE, DELETE, SAVE, RTP, HOME }

    private final NumberSetting triggerY = add(new NumberSetting("Trigger Y", "Run at or below this Y level", -2, -64, -2, 1));
    private final NumberSetting homeSlot = add(new NumberSetting("Home Slot", "This home is replaced each round; 3 leaves the builder's homes 1 and 2 available", 3, 1, 3, 1));
    private final NumberSetting redoDelay = add(new NumberSetting("Redo Delay", "Seconds after returning before another underground round", 10, 1, 120, 1).suffix("s"));
    private final NumberSetting commandDelay = add(new NumberSetting("Command Delay", "Minimum seconds between commands, including retries", 1.25, 1, 5, .25).suffix("s"));
    private final NumberSetting timeout = add(new NumberSetting("Response Timeout", "Stop if a home confirmation or teleport never arrives", 30, 5, 120, 1).suffix("s"));
    private final BooleanSetting statusBar = add(new BooleanSetting("Status Bar", "Show the current step and completed rounds", true));

    private Phase phase = Phase.IDLE;
    private ClientPlayNetworkHandler connection;
    private Vec3d homePosition, rtpOrigin;
    private RegistryKey<World> homeWorld, rtpWorld;
    private int clock, sentAt, queuedAt, nextCommandTick, cooldownTick, slot, retries, rounds;
    private long nextCommandTime, cooldownTime;
    private String command = "", status = "Waiting for underground Y";
    private boolean queued, confirmed, arrived, resumeMine;

    public MaroRelog() {
        super("Maro Relog", "Below the trigger Y, save a home, /rtp away and return underground", Category.PLAYER);
        // Server/system feedback only: player chat must never advance home commands.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> message(message.getString()));
    }

    public String status() { return status; }
    public int rounds() { return rounds; }

    @Override protected void onEnable() {
        clear();
        clock = rounds = 0;
        connection = mc.getNetworkHandler();
        status = "Waiting for underground Y";
    }

    @Override protected void onDisable() {
        clear();
        connection = null;
    }

    private void clear() {
        phase = Phase.IDLE;
        command = "";
        queued = confirmed = arrived = resumeMine = false;
        homePosition = rtpOrigin = null;
        homeWorld = rtpWorld = null;
        nextCommandTick = cooldownTick = 0;
        nextCommandTime = cooldownTime = 0;
    }

    @Override public void onTick() {
        clock++;
        if (!inGame() || mc.getNetworkHandler() == null) {
            if (phase != Phase.IDLE) stop("Disconnected; the relog sequence was stopped");
            else connection = null;
            return;
        }
        if (connection != mc.getNetworkHandler()) {
            if (phase != Phase.IDLE) { stop("Connection changed; the relog sequence was stopped"); return; }
            connection = mc.getNetworkHandler();
        }
        if (!mc.player.isAlive()) { stop("Player died; the relog sequence was stopped"); return; }
        var builder = ModuleManager.get(AutoBuilder.class);
        if (builder != null && builder.isEnabled()) {
            if (phase != Phase.IDLE) stop("Builder started; relog stopped with the saved home kept");
            else status = "Waiting for Auto Builder";
            return;
        }
        long now = System.nanoTime();
        if (phase == Phase.IDLE) {
            if (clock < cooldownTick || now < cooldownTime) { status = "Waiting before next round"; return; }
            status = "Waiting for Y " + triggerY.getInt();
            if (mc.player.getY() > triggerY.getInt() || mc.currentScreen != null
                    || !mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()) return;
            slot = homeSlot.getInt();
            var mine = ModuleManager.get(AutoMine.class);
            resumeMine = mine != null && mine.isEnabled();
            if (resumeMine) mine.setEnabled(false);
            queue(Phase.DELETE, "delhome " + slot);
        }
        holdStill();
        if (queued) {
            if (clock > Math.max(queuedAt, nextCommandTick) + timeout.getInt() * 20) {
                stop("Could not stand still to send /" + command);
                return;
            }
            if (clock < nextCommandTick || now < nextCommandTime || mc.currentScreen != null
                    || !mc.player.isOnGround()) return;
            queued = confirmed = arrived = false;
            sentAt = clock;
            if (phase == Phase.SAVE) {
                homePosition = mc.player.getEntityPos();
                homeWorld = mc.world.getRegistryKey();
            } else if (phase == Phase.RTP) {
                rtpOrigin = mc.player.getEntityPos();
                rtpWorld = mc.world.getRegistryKey();
            }
            int gap = (int) Math.ceil(commandDelay.get() * 20);
            nextCommandTick = clock + gap;
            nextCommandTime = now + (long) (commandDelay.get() * 1_000_000_000L);
            status = "Waiting for /" + command;
            connection.sendChatCommand(command);
            return;
        }
        if (clock - sentAt > timeout.getInt() * 20) {
            stop("No confirmation for /" + command + "; check server feedback and /home " + slot);
            return;
        }
        switch (phase) {
            case DELETE -> { if (confirmed) queue(Phase.SAVE, "sethome " + slot); }
            case SAVE -> { if (confirmed) queue(Phase.RTP, "rtp"); }
            case RTP -> { if (arrived) queue(Phase.HOME, "home " + slot); }
            case HOME -> {
                if (!arrived) return;
                phase = Phase.IDLE;
                rounds++;
                cooldownTick = clock + redoDelay.getInt() * 20;
                cooldownTime = now + redoDelay.getInt() * 1_000_000_000L;
                status = "Returned to home " + slot;
                command = "";
                var mine = ModuleManager.get(AutoMine.class);
                if (resumeMine && mine != null && !mine.isEnabled()) mine.setEnabled(true);
                resumeMine = false;
            }
            default -> { }
        }
    }

    private void queue(Phase next, String value) {
        phase = next;
        command = value;
        queued = true;
        queuedAt = clock;
        confirmed = arrived = false;
        retries = 0;
        status = "Preparing /" + command;
    }

    private void holdStill() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    private void message(String raw) {
        if (!isEnabled() || phase == Phase.IDLE || queued) return;
        String text = raw.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        var wait = COMMAND_WAIT.matcher(text);
        if ((text.contains("command") || text.contains("cooldown")) && wait.find()) {
            double seconds = Double.parseDouble(wait.group(1));
            if (!Double.isFinite(seconds) || seconds > 60 || ++retries > 3) {
                stop("Server command cooldown did not clear");
                return;
            }
            nextCommandTick = Math.max(nextCommandTick, clock + (int) Math.ceil(seconds * 20) + 3);
            nextCommandTime = Math.max(nextCommandTime, System.nanoTime() + (long) ((seconds + .15) * 1_000_000_000L));
            queued = true;
            queuedAt = clock;
            confirmed = arrived = false;
            status = "Command cooldown; retrying /" + command;
            return;
        }
        if (!text.contains("home") && !text.contains("teleport") && !text.contains("command") && !text.contains("rtp")) return;
        boolean missing = text.contains("not found") || text.contains("not set") || text.contains("does not exist")
                || text.contains("no home") || text.contains("don't have") || text.contains("do not have");
        boolean failure = text.contains("cancel") || text.contains("cooldown") || text.contains("combat")
                || text.contains("permission") || text.contains("cannot") || text.contains("can't")
                || text.contains("could not") || text.contains("unable") || text.contains("not allowed")
                || text.contains("not deleted") || text.contains("not removed") || text.contains("unknown command")
                || text.contains("failed") || text.contains("maximum") || text.contains("slots full");
        if (failure || missing && phase != Phase.DELETE) { stop("Server rejected /" + command + ": " + raw); return; }
        if (!text.contains("home")) return;
        boolean receipt = phase == Phase.DELETE && (missing || text.contains("deleted") || text.contains("removed"))
                || phase == Phase.SAVE && (text.contains("set") || text.contains("created") || text.contains("saved"));
        if (!receipt) return;
        var id = HOME_ID.matcher(text);
        if (id.find() && !id.group(1).equals(Integer.toString(slot))) {
            stop("Server confirmed a different home slot; check homes before restarting");
            return;
        }
        confirmed = true;
    }

    /** Called after the native server teleport packet has actually moved the player. */
    public void serverTeleport() {
        if (!isEnabled() || queued || !inGame() || connection != mc.getNetworkHandler()) return;
        Vec3d position = mc.player.getEntityPos();
        var dimension = mc.world.getRegistryKey();
        if (phase == Phase.RTP && rtpOrigin != null)
            arrived = !dimension.equals(rtpWorld) || position.squaredDistanceTo(rtpOrigin) >= 64;
        else if (phase == Phase.HOME && homePosition != null)
            arrived = dimension.equals(homeWorld) && position.squaredDistanceTo(homePosition) <= 4;
    }

    private void stop(String reason) {
        status = reason;
        setEnabled(false);
        Notifications.push("Maro Relog stopped", reason, Notifications.Type.WARNING, 5000);
    }

    @Override public void onRender2D(DrawContext context, float tickDelta) {
        if (!statusBar.get() || !inGame() || mc.options.hudHidden) return;
        String text = "Maro Relog  |  " + status + "  |  Rounds " + rounds;
        float width = Fonts.width(text, true, .8f) + 20;
        float left = (mc.getWindow().getScaledWidth() - width) / 2;
        Render2D.roundRect(context, left, 24, width, 18, 8, 0xE00B0D12);
        Fonts.drawV(context, text, left + 10, 33, Theme.TEXT, true, .8f);
    }
}
