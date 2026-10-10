package dev.maro.nathan.modules;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.nathan.audio.SpotifyLyrics;
import dev.maro.nathan.audio.SpotifySeekPreview;
import dev.maro.nathan.audio.SpotifyTimeline;
import dev.maro.nathan.gui.SpotifyControlsScreen;
import dev.maro.nathan.render.CrispFont;
import dev.maro.nathan.render.RoundedBox;
import dev.maro.nathan.render.SpotifyCardRaster;
import dev.maro.nathan.render.SpotifyHudAnimation;
import dev.maro.nathan.render.SpotifyHudLayout;
import dev.maro.nathan.render.SpotifyHudFeatures;
import dev.maro.nathan.render.SpotifyHudFeatures.Mode;
import dev.maro.nathan.render.SpotifyHudFeatures.Anchor;
import dev.maro.nathan.render.SpotifyHudFeatures.Visualizer;
import dev.maro.nathan.render.SpotifyHudLayout.Rect;

import dev.maro.runtime.events.render.Render2DEvent;
import dev.maro.runtime.renderer.Renderer2D;
import dev.maro.runtime.renderer.Texture;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.runtime.utils.render.color.SettingColor;
import dev.maro.runtime.event.EventHandler;
import dev.maro.runtime.event.EventPriority;

import org.lwjgl.glfw.GLFW;

/** A small media player with native icons, a scrub timeline and live playback bands. */
public class SpotifyHud extends Module {
    public enum LyricsMode { Lines, WordHighlight, SingleWord }
    private static final Color FACE = new Color(44, 49, 62);
    private static final Color WHITE = new Color(239, 241, 246);
    private static final Color MUTED = new Color(160, 169, 187);
    private static final Color TRACK = new Color(62, 69, 84);
    private static final int DEFAULT_TINT = 0x8A9FC2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgControls = settings.createGroup("Controls");
    private final Setting<Boolean> volumeControl = sgControls.add(new BoolSetting.Builder()
        .name("volume-control").description("Show a Windows output volume slider and mute button in F9 controls. Changes system volume for all apps.")
        .defaultValue(true).build());
    private final SettingGroup sgAnimation = settings.createGroup("Animations");
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgLyrics = settings.createGroup("Lyrics");
    private final Setting<Boolean> showLyrics = sgLyrics.add(new BoolSetting.Builder()
        .name("lyrics").description("Show song lyrics from LRCLIB. Timed lyrics follow playback and seeking.")
        .defaultValue(true).build());
    private final Setting<LyricsMode> lyricsMode = sgLyrics.add(new EnumSetting.Builder<LyricsMode>()
        .name("word-display").description("Highlight each timed word, show one sung word at a time, or display whole lines. SingleWord requires real word timestamps.")
        .defaultValue(LyricsMode.WordHighlight).visible(showLyrics::get).build());
    private final Setting<Boolean> estimateWords = sgLyrics.add(new BoolSetting.Builder()
        .name("approximate-word-preview").description("Optional guessed highlighting for line-only lyrics. This does not detect vocals and never applies to SingleWord.")
        .defaultValue(false).visible(() -> showLyrics.get() && lyricsMode.get() == LyricsMode.WordHighlight).build());
    private final Setting<Double> lyricsSize = sgLyrics.add(new DoubleSetting.Builder()
        .name("lyrics-size").description("Size of the highlighted lyric line.")
        .defaultValue(12).range(8, 16).sliderRange(8, 16).visible(showLyrics::get).build());
    private final Setting<Integer> lyricsOffset = sgLyrics.add(new IntSetting.Builder()
        .name("lyrics-offset-ms").description("Adjust lyric timing. Positive values show lines earlier.")
        .defaultValue(0).range(-10000, 10000).sliderRange(-3000, 3000).visible(showLyrics::get).build());

    private final Setting<Integer> x = sgPlacement.add(new IntSetting.Builder()
        .name("x").description("Horizontal offset from the selected screen anchor, in pixels.")
        .defaultValue(20).min(0).sliderRange(0, 1920).build());

    private final Setting<Integer> y = sgPlacement.add(new IntSetting.Builder()
        .name("y").description("Vertical offset from the selected screen anchor, in pixels.")
        .defaultValue(20).min(0).sliderRange(0, 1080).build());

    private final Setting<Double> scale = sgPlacement.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the whole player, including its text and mouse targets.")
        .defaultValue(1).range(0.65, 2).sliderRange(0.65, 2).build());

    private final Setting<Mode> playerMode = sgAppearance.add(new EnumSetting.Builder<Mode>()
        .name("player-mode").description("Full player, mini player, or a mini player that expands on hover in F9 controls.")
        .defaultValue(Mode.Expanded).build());
    private final Setting<SpotifyCardRaster.Theme> theme = sgAppearance.add(new EnumSetting.Builder<SpotifyCardRaster.Theme>()
        .name("theme").description("Choose album colours, midnight, translucent frosted glass, or monochrome.")
        .defaultValue(SpotifyCardRaster.Theme.AlbumColours).build());
    private final Setting<Visualizer> visualizerStyle = sgAppearance.add(new EnumSetting.Builder<Visualizer>()
        .name("visualizer-style").description("Show bars, a smooth wave, or a reactive ring around the cover.")
        .defaultValue(Visualizer.Bars).build());
    private final Setting<Boolean> scrollTitles = sgAppearance.add(new BoolSetting.Builder()
        .name("scroll-titles").description("Gently scroll long song and artist names so you can read them fully.")
        .defaultValue(true).build());
    private final Setting<Boolean> seekTooltip = sgControls.add(new BoolSetting.Builder()
        .name("seek-preview").description("Show the exact time under the mouse before seeking.").defaultValue(true).build());
    private final Setting<Boolean> autoHide = sgPlacement.add(new BoolSetting.Builder()
        .name("auto-hide").description("Fade out when no music session is available. F9 always reveals the player.").defaultValue(true).build());
    private final Setting<Boolean> dragPlayer = sgPlacement.add(new BoolSetting.Builder()
        .name("drag-player").description("Drag the song text or empty card space to move the player in F9 controls.").defaultValue(true).build());
    private final Setting<Boolean> snapEdges = sgPlacement.add(new BoolSetting.Builder()
        .name("snap-to-edges").description("Snap the player to nearby screen edges when you finish dragging.").defaultValue(true).build());
    private final Setting<Anchor> anchor = sgPlacement.add(new EnumSetting.Builder<Anchor>()
        .name("anchor").description("Keep the player attached to an edge or corner when the window or player size changes.")
        .defaultValue(Anchor.TopRight).build());

    private final Setting<Boolean> visualizer = sgAppearance.add(new BoolSetting.Builder()
        .name("audio-bars").description("Small bars that react to audio playing through Windows. Other desktop audio can also affect them.")
        .defaultValue(true).build());

    private final Setting<Boolean> albumAccent = sgAppearance.add(new BoolSetting.Builder()
        .name("album-accent").description("Give the card a soft glow using colours from the album cover.")
        .defaultValue(true).build());

    private final Setting<SettingColor> barColor = sgAppearance.add(new ColorSetting.Builder()
        .name("bar-color").description("The colour of the small audio bars.")
        .defaultValue(new SettingColor(210, 215, 226)).visible(visualizer::get).build());

    private final Setting<Boolean> beatGlow = sgAnimation.add(new BoolSetting.Builder()
        .name("beat-glow").description("Pulse the soft album glow with the music.").defaultValue(true).build());
    private final Setting<Boolean> songTransitions = sgAnimation.add(new BoolSetting.Builder()
        .name("song-transitions").description("Fade covers and slide song details when the track changes.").defaultValue(true).build());
    private final Setting<Boolean> floatingCover = sgAnimation.add(new BoolSetting.Builder()
        .name("floating-cover").description("Gently lift the album cover when hovered in mouse controls.").defaultValue(true).build());
    private final Setting<Boolean> progressShimmer = sgAnimation.add(new BoolSetting.Builder()
        .name("progress-shimmer").description("Sweep a faint highlight along the played part of the timeline.").defaultValue(true).build());
    private final Setting<Boolean> animatedControls = sgAnimation.add(new BoolSetting.Builder()
        .name("animated-controls").description("Softly animate button hover, presses and play/pause changes.").defaultValue(true).build());

    private final Setting<Keybind> previous = sgControls.add(new KeybindSetting.Builder()
        .name("previous").description("Skip to the previous track. Default: F6.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F6)).action(() -> control("previous")).build());

    private final Setting<Keybind> playPause = sgControls.add(new KeybindSetting.Builder()
        .name("play-pause").description("Pause or resume playback. Default: F7.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F7)).action(() -> control("toggle")).build());

    private final Setting<Keybind> next = sgControls.add(new KeybindSetting.Builder()
        .name("next").description("Skip to the next track. Default: F8.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F8)).action(() -> control("next")).build());

    private final Setting<Keybind> clickControls = sgControls.add(new KeybindSetting.Builder()
        .name("click-controls").description("Release the mouse to use the player or drag its timeline. Default: F9; Esc closes it.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_F9))
        .action(() -> {
            if (!isActive()) return;
            if (mc.currentScreen instanceof SpotifyControlsScreen) mc.setScreen(null);
            else if (mc.currentScreen == null) mc.setScreen(new SpotifyControlsScreen(this));
        }).build());

    private final SpotifyMedia media = SpotifySession.media();
    private SpotifyLyrics lyrics = new SpotifyLyrics();
    private String lyricsKey = "", lyricLine = "";
    private int plainLyricIndex;
    private double lyricFade = 1;
    private double lyricScroll, wordFade = 1;
    private double lyricAge;
    private record WrappedLyric(String text, double width, double unit, double desired, String first, String rest, double size) { }
    private WrappedLyric wrappedLyric;
    private long lyricPosition = -1;
    private int lyricIndex = -1;
    private String liveWord = "";
    private final SpotifyTimeline timeline = new SpotifyTimeline();
    private final SpotifySeekPreview seekPreview = new SpotifySeekPreview();
    private final SpotifyHudAnimation animation = new SpotifyHudAnimation();
    private final double[] levels = new double[7];
    private SpotifyMedia.Artwork shownArtwork;
    private SpotifyMedia.State shownState;
    private SpotifyMedia.State leavingState;
    private double leavingOpacity = 1;
    private double leavingShift;
    private Texture coverTexture;
    private Texture leavingCoverTexture;
    private Texture cardTexture;
    private Texture leavingCardTexture;
    private Texture glowTexture;
    private int cardTint = Integer.MIN_VALUE;
    private SpotifyCardRaster.Theme cardTheme;
    private int cardHeight = -1;
    private double expansion = 1;
    private double visibility = 1;
    private double scrollAge;
    private double lastExpansionTarget = -1;
    private double leavingScrollAge;
    private boolean draggingPlayer;
    private boolean draggingVolume;
    private double lastAudibleVolume = 0.5;
    private double dragOffsetX, dragOffsetY, dragExpansion;
    private double timelineHover;
    private boolean waitingArtwork;
    private double artworkWait;
    private long lastFrameAt;

    public SpotifyHud() {
        super(NameeProtectAddon.CATEGORY, "spotify-hud", "A compact Spotify player with cover art, live audio bars and a draggable timeline.");
        SpotifyCardRaster.prepareThemes();
    }

    @Override
    public void onActivate() {
        lastFrameAt = 0;
        expansion = playerMode.get() == Mode.Expanded ? 1 : 0;
        visibility = autoHide.get() ? 0 : 1;
        scrollAge = 0;
        animation.reset(false);
        SpotifySession.acquire();
    }

    @Override
    public void onDeactivate() {
        cancelScrub();
        seekPreview.cancel();
        SpotifySession.release();
        lyrics.close();
        lyricsKey = ""; lyricLine = ""; plainLyricIndex = 0; lyricScroll = 0; lyricPosition = -1; liveWord = "";
        if (coverTexture != null) coverTexture.close();
        if (leavingCoverTexture != null) leavingCoverTexture.close();
        if (cardTexture != null) cardTexture.close();
        if (leavingCardTexture != null) leavingCardTexture.close();
        if (glowTexture != null) glowTexture.close();
        coverTexture = null;
        leavingCoverTexture = null;
        cardTexture = null;
        leavingCardTexture = null;
        glowTexture = null;
        cardTint = Integer.MIN_VALUE;
        shownArtwork = null;
        shownState = null;
        leavingState = null;
        leavingOpacity = 1;
        leavingShift = 0;
        waitingArtwork = false;
        artworkWait = 0;
        java.util.Arrays.fill(levels, 0);
        animation.reset(false);
        timelineHover = 0;
        cardTheme = null;
        draggingPlayer = false;
        draggingVolume = false;
    }

    private SpotifyHudLayout layout() {
        double unit = scale.get();
        double width = (SpotifyHudLayout.MINI_WIDTH + (SpotifyHudLayout.WIDTH - SpotifyHudLayout.MINI_WIDTH) * expansion) * unit;
        double height = (SpotifyHudLayout.MINI_HEIGHT + (SpotifyHudLayout.HEIGHT - SpotifyHudLayout.MINI_HEIGHT) * expansion) * unit;
        // Reserve room for lowercase descenders and bottom padding, not only cap height.
        double extra = showLyrics.get() ? Math.max(72, lyricsSize.get() * 2 + 50) : 0;
        height += extra > 0 ? (extra + 8) * unit : 0;
        SpotifyHudFeatures.Position position = SpotifyHudFeatures.position(anchor.get(), x.get(), y.get(), width, height,
            mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        return new SpotifyHudLayout(position.x(), position.y(), unit, expansion, extra);
    }

    private void control(String action) {
        if (!isActive()) return;
        cancelScrub();
        seekPreview.cancel();
        animation.press(action.equals("previous") ? 0 : action.equals("toggle") ? 1 : 2);
        media.control(action);
    }

    /** Called by the controls screen with physical, rather than GUI, pixels. */
    public boolean mousePressed(double mouseX, double mouseY) {
        if (!isActive()) return false;
        SpotifyHudLayout layout = layout();
        SpotifyMedia.State state = media.state();
        Rect track = timelineRect(layout, state);
        if (volumeVisible(layout) && layout.volumeMute().contains(mouseX, mouseY)) {
            SpotifyMedia.Volume current = media.volume();
            if (current.available()) {
                if (current.level() > 0) lastAudibleVolume = current.level();
                media.setVolume(current.level() > 0 ? current.level() : lastAudibleVolume, !current.muted() && current.level() > 0);
            }
        } else if (volumeVisible(layout) && layout.volumeHit().contains(mouseX, mouseY)) {
            if (media.volume().available()) {
                draggingVolume = true; dragExpansion = expansion;
                if (media.volume().level() > 0) lastAudibleVolume = media.volume().level();
                media.setVolume(layout.fractionAt(mouseX, layout.volumeTrack()), false);
            }
        } else if (layout.controlOpacity() > 0.01 && layout.previous().contains(mouseX, mouseY)) control("previous");
        else if (layout.toggle().contains(mouseX, mouseY)) control("toggle");
        else if (layout.controlOpacity() > 0.01 && layout.next().contains(mouseX, mouseY)) control("next");
        else if (layout.cover().contains(mouseX, mouseY)) media.openSpotify();
        else if (layout.timelineHit(track).contains(mouseX, mouseY)) {
            seekPreview.cancel();
            dragExpansion = expansion;
            return timeline.begin(trackKey(state), state.durationMs(), state.available() && state.canSeek(), layout.fractionAt(mouseX, track));
        } else if (dragPlayer.get() && layout.bounds().contains(mouseX, mouseY)) {
            draggingPlayer = true;
            dragExpansion = expansion;
            dragOffsetX = mouseX - layout.left(); dragOffsetY = mouseY - layout.top();
            anchor.set(Anchor.Free);
            x.set((int) Math.round(layout.left())); y.set((int) Math.round(layout.top()));
        } else return false;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingVolume) {
            SpotifyHudLayout layout = layout();
            if (media.volume().level() > 0) lastAudibleVolume = media.volume().level();
            media.setVolume(layout.fractionAt(mouseX, layout.volumeTrack()), false);
            return true;
        }
        if (draggingPlayer) {
            SpotifyHudLayout layout = layout();
            SpotifyHudFeatures.Position position = SpotifyHudFeatures.position(Anchor.Free, mouseX - dragOffsetX, mouseY - dragOffsetY,
                layout.bounds().width(), layout.bounds().height(), mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
            x.set((int) Math.round(position.x())); y.set((int) Math.round(position.y()));
            return true;
        }
        if (!timeline.active()) return false;
        SpotifyMedia.State state = media.state();
        timeline.validate(trackKey(state), state.durationMs(), state.available() && state.canSeek());
        SpotifyHudLayout layout = layout();
        timeline.drag(layout.fractionAt(mouseX, timelineRect(layout, state)));
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY) {
        if (draggingVolume) {
            mouseDragged(mouseX, mouseY); draggingVolume = false;
            return true;
        }
        if (draggingPlayer) {
            mouseDragged(mouseX, mouseY);
            SpotifyHudLayout layout = layout();
            SpotifyHudFeatures.Dock dock = SpotifyHudFeatures.dock(layout.left(), layout.top(), layout.bounds().width(), layout.bounds().height(),
                mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), snapEdges.get());
            anchor.set(dock.anchor()); x.set(dock.x()); y.set(dock.y());
            draggingPlayer = false;
            return true;
        }
        if (!timeline.active()) return false;
        SpotifyMedia.State state = media.state();
        SpotifyHudLayout layout = layout();
        timeline.finish(trackKey(state), state.durationMs(), state.available() && state.canSeek(), layout.fractionAt(mouseX, timelineRect(layout, state)))
            .ifPresent(position -> {
                seekPreview.begin(trackKey(state), state.durationMs(), position, state.playing(), state.sampledAtMs(), System.currentTimeMillis());
                media.seekTo(position);
            });
        return true;
    }

    public void cancelScrub() {
        timeline.cancel();
    }

    public void cancelInteraction() { cancelScrub(); draggingPlayer = false; draggingVolume = false; }

    private boolean volumeVisible(SpotifyHudLayout layout) {
        return volumeControl.get() && mc.currentScreen instanceof SpotifyControlsScreen && layout.expansion() > 0.99;
    }

    public boolean scrollVolume(double mouseX, double mouseY, double amount) {
        if (!isActive() || !volumeVisible(layout()) || (!layout().volumeHit().contains(mouseX, mouseY)
            && !layout().volumeMute().contains(mouseX, mouseY))) return false;
        SpotifyMedia.Volume current = media.volume();
        if (current.available() && amount != 0) {
            if (current.level() > 0) lastAudibleVolume = current.level();
            media.setVolume(current.level() + Math.signum(amount) * 0.02, false);
        }
        return true;
    }

    public boolean scrollLyrics(double mouseX, double mouseY, double amount) {
        if (!isActive() || !showLyrics.get() || !layout().lyrics().contains(mouseX, mouseY)
            || lyrics.snapshot().result().status() != SpotifyLyrics.Status.Plain) return false;
        plainLyricIndex = Math.max(0, Math.min(lyrics.snapshot().result().plain().size() - 1,
            plainLyricIndex + (amount < 0 ? 1 : amount > 0 ? -1 : 0)));
        return true;
    }

    public String controlsHint() {
        SpotifyMedia.State state = media.state();
        if (volumeControl.get() && !media.volume().available()) return media.volume().error();
        if (!state.error().isBlank()) return state.error();
        if (!state.available()) return "Open Spotify and play a track";
        if (showLyrics.get() && lyrics.snapshot().result().status() == SpotifyLyrics.Status.Plain) return "Untimed lyrics: scroll over the lyrics panel";
        if (!state.canSeek() || state.durationMs() <= 0) return "Seeking is unavailable for this track";
        return "";
    }

    private static Rect timelineRect(SpotifyHudLayout layout, SpotifyMedia.State state) {
        if (!CrispFont.POPPINS_MEDIUM.ready()) return layout.timeline();
        CrispFont.Sized font = CrispFont.POPPINS_MEDIUM.forCaps(SpotifyHudLayout.TIME_CAPS * layout.scale());
        // Reserve the longest time this track can display; dragging across an
        // hour boundary then leaves the track and its hit target in one place.
        String longest = state.durationMs() > 0 ? time(state.durationMs()) : "--:--";
        return layout.timeline(font.width(longest, 0), font.width("-" + longest, 0));
    }

    private static String trackKey(SpotifyMedia.State state) {
        return state.source() + '\0' + state.title() + '\0' + state.artist();
    }

    @EventHandler(priority = EventPriority.LOWEST - 100)
    private void onRender2D(Render2DEvent event) {
        if (mc.options == null || mc.options.hudHidden) return;
        SpotifyMedia.State state = media.state();
        if (!(mc.currentScreen instanceof SpotifyControlsScreen)) cancelInteraction();
        timeline.validate(trackKey(state), state.durationMs(), state.available() && state.canSeek());

        long now = System.nanoTime();
        double seconds = lastFrameAt == 0 ? 1.0 / 60 : Math.min(0.1, (now - lastFrameAt) / 1.0e9);
        lastFrameAt = now;
        float[] measured = media.audioLevels();
        for (int i = 0; i < levels.length; i++) {
            double target = state.playing() && visualizer.get() && i < measured.length ? measured[i] : 0;
            if (!Double.isFinite(target)) target = 0;
            double rate = target > levels[i] ? 18 : 6;
            levels[i] += (Math.max(0, Math.min(1, target)) - levels[i]) * (1 - Math.exp(-rate * seconds));
        }

        double mouseX = mc.currentScreen instanceof SpotifyControlsScreen ? mc.mouse.getScaledX(mc.getWindow()) * mc.getWindow().getScaleFactor() : -1;
        double mouseY = mc.currentScreen instanceof SpotifyControlsScreen ? mc.mouse.getScaledY(mc.getWindow()) * mc.getWindow().getScaleFactor() : -1;
        SpotifyHudLayout before = layout();
        double targetExpansion = volumeControl.get() && mc.currentScreen instanceof SpotifyControlsScreen ? 1
            : playerMode.get() == Mode.Expanded ? 1 : playerMode.get() == Mode.Mini ? 0
            : before.bounds().contains(mouseX, mouseY) ? 1 : 0;
        if (draggingPlayer || draggingVolume || timeline.active()) targetExpansion = dragExpansion;
        if (targetExpansion != lastExpansionTarget) { scrollAge = 0; lastExpansionTarget = targetExpansion; }
        expansion = ease(expansion, targetExpansion, 13, seconds);
        if (Math.abs(expansion - targetExpansion) < 0.001) expansion = targetExpansion;
        visibility = ease(visibility, !autoHide.get() || state.available() || mc.currentScreen instanceof SpotifyControlsScreen ? 1 : 0, 8, seconds);
        scrollAge += seconds;
        SpotifyHudLayout layout = layout();
        double unit = layout.scale();
        Rect panel = layout.panel();
        Rect cover = layout.cover();
        updateTrack(state);
        updateArtwork(state, seconds);
        int tintRgb = theme.get() == SpotifyCardRaster.Theme.Monochrome ? 0xBFC4CC
            : theme.get() != SpotifyCardRaster.Theme.AlbumColours ? DEFAULT_TINT
            : albumAccent.get() && shownArtwork != null ? shownArtwork.tintRgb()
            : albumAccent.get() && waitingArtwork && cardTint != Integer.MIN_VALUE ? cardTint : DEFAULT_TINT;
        Color accent = new Color(tintRgb >> 16 & 255, tintRgb >> 8 & 255, tintRgb & 255);
        updateCard(tintRgb);
        animation.step(seconds, state.playing(), measured, layout.previous().contains(mouseX, mouseY),
            layout.toggle().contains(mouseX, mouseY), layout.next().contains(mouseX, mouseY),
            floatingCover.get() && cover.contains(mouseX, mouseY), animatedControls.get());
        double artFade = songTransitions.get() ? animation.artworkTransition() : 1;
        double cardFade = songTransitions.get() ? animation.cardTransition() : 1;
        double titleFade = songTransitions.get() ? animation.transition() : 1;
        if (visibility < 0.005) { closeLeavingArtwork(artFade, cardFade); return; }
        double coverLift = floatingCover.get() ? animation.coverHover() : 0;
        double coverTilt = 2 * coverLift;
        cover = new Rect(cover.x() - 0.5 * coverLift * unit, cover.y() - 2 * coverLift * unit,
            cover.width() + coverLift * unit, cover.height() + coverLift * unit);
        Rect track = timelineRect(layout, state);
        boolean canSeek = state.available() && state.canSeek() && state.durationMs() > 0;
        boolean hoverTrack = canSeek && layout.timelineHit(track).contains(mouseX, mouseY);
        timelineHover = ease(timelineHover, hoverTrack || timeline.active() ? 1 : 0, 15, seconds);
        long position = timeline.active() ? timeline.positionMs() : seekPreview.position(trackKey(state), state.durationMs(),
            state.currentPositionMs(), state.playing(), state.sampledAtMs(), canSeek, !state.error().isBlank(), System.currentTimeMillis());
        double progress = state.durationMs() > 0 ? Math.max(0, Math.min(1, (double) position / state.durationMs())) : 0;

        double pad = SpotifyCardRaster.PADDING * unit;
        Rect surface = new Rect(panel.x() - pad, panel.y() - pad, panel.width() + pad * 2, layout.bounds().height() + pad * 2);
        if (leavingCardTexture != null && cardFade < 1) textured(leavingCardTexture, surface,
            new Color(255, 255, 255, theme.get() == SpotifyCardRaster.Theme.FrostedGlass ? alpha(1 - cardFade) : 255));
        textured(cardTexture, surface, new Color(255, 255, 255, leavingCardTexture == null ? 255 : alpha(cardFade)));
        if (beatGlow.get() && animation.beat() > 0.005) {
            if (glowTexture == null) {
                SpotifyCardRaster.Raster glow = SpotifyCardRaster.glow();
                glowTexture = new Texture(glow.width(), glow.height(), TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
                glowTexture.upload(glow.rgba());
            }
            textured(glowTexture, surface, new Color(accent.r, accent.g, accent.b, (int) Math.round(52 * animation.beat())));
        }

        Renderer2D.COLOR.begin();
        RoundedBox.shadow(cover.centerX(), cover.centerY() + 2 * unit, cover.width() + 2 * unit, cover.height() + 2 * unit,
            11 * unit, 8 * unit, coverTilt, 0.3 * visibility);

        if (layout.controlOpacity() > 0) button(layout.previous(), "previous", 0, state.available(), unit, accent, layout.controlOpacity());
        button(layout.toggle(), "toggle", 1, state.available(), unit, accent, 1);
        if (layout.controlOpacity() > 0) button(layout.next(), "next", 2, state.available(), unit, accent, layout.controlOpacity());

        Color statusInk = opacity(state.playing() ? mix(accent, WHITE, 0.25) : MUTED, layout.detailOpacity());
        if (!volumeVisible(layout)) RoundedBox.draw(layout.textLeft() + 2.5 * unit, layout.y(66), 5 * unit, 5 * unit, 2.5 * unit, 0, unit, 0, statusInk, statusInk);
        else volumeShapes(layout, mouseX, mouseY, accent);

        if (visualizer.get()) {
            renderVisualizer(layout, cover, unit, accent);
        }

        box(track, track.height() / 2, 0, TRACK, TRACK);
        double filled = track.width() * progress;
        if (filled > 0) {
            Color progressInk = opacity(mix(WHITE, accent, 0.5));
            RoundedBox.draw(track.x() + filled / 2, track.centerY(), filled, track.height(),
                Math.min(track.height() / 2, filled / 2), 0, Math.min(1, filled), 0, progressInk, progressInk);
            if (progressShimmer.get() && state.playing() && !timeline.active()) shimmer(track, filled, animation.shimmer(), unit);
        }
        if (timelineHover > 0.01) {
            double size = (6 + 4 * timelineHover) * unit;
            Color thumb = opacity(new Color(WHITE.r, WHITE.g, WHITE.b, (int) Math.round(255 * timelineHover)));
            RoundedBox.draw(track.x() + filled, track.centerY(), size, size, size / 2,
                0, Math.min(1, unit), 0, thumb, thumb);
        }
        Renderer2D.COLOR.render();

        renderCover(cover, unit, artFade, coverTilt);
        closeLeavingArtwork(artFade, cardFade);
        if (titleFade >= 1) leavingState = null;

        if (!CrispFont.POPPINS_MEDIUM.ready() || !CrispFont.POPPINS_SEMIBOLD.ready()) return;
        CrispFont.Sized artist = CrispFont.POPPINS_MEDIUM.forCaps(10 * unit);
        CrispFont.Sized title = CrispFont.POPPINS_SEMIBOLD.forCaps(15 * unit);
        CrispFont.Sized timeFont = CrispFont.POPPINS_MEDIUM.forCaps(SpotifyHudLayout.TIME_CAPS * unit);
        CrispFont.Sized statusFont = CrispFont.POPPINS_MEDIUM.forCaps(7 * unit);
        String elapsed = state.available() ? time(position) : "--:--";
        String remaining = state.durationMs() > 0 ? "-" + time(Math.max(0, state.durationMs() - position)) : "--:--";

        CrispFont.begin(null);
        try {
            if (leavingState != null && titleFade < 0.5) {
                songText(leavingState, layout, artist, title, leavingOpacity * Math.max(0, 1 - titleFade * 2),
                    (leavingShift - 4 * titleFade) * unit, leavingScrollAge);
            }
            double incoming = leavingState == null ? 1 : Math.max(0, (titleFade - 0.45) / 0.55);
            songText(state, layout, artist, title, incoming, 4 * (1 - incoming) * unit, scrollAge);
            if (volumeVisible(layout)) {
                SpotifyMedia.Volume current = media.volume();
                statusFont.draw("SYSTEM", layout.textLeft(), layout.y(62.5) - statusFont.capsTop(), opacity(MUTED), 0, false);
                statusFont.draw(current.available() ? current.muted() ? "MUTED" : current.percent() + "%" : "—",
                    layout.x(280), layout.y(62.5) - statusFont.capsTop(), opacity(current.available() ? WHITE : MUTED), 0, false);
            } else statusFont.draw(!state.available() ? "WAITING FOR MUSIC" : state.playing() ? "NOW PLAYING" : "PAUSED",
                layout.textLeft() + 12 * unit, layout.y(62.5) - statusFont.capsTop(), opacity(MUTED, layout.detailOpacity()), 0, false);
            timeFont.draw(elapsed, layout.x(14), track.centerY() - timeFont.caps() / 2 - timeFont.capsTop(), opacity(MUTED, layout.detailOpacity()), 0, false);
            timeFont.draw(remaining, layout.x(layout.width() - 14) - timeFont.width(remaining, 0),
                track.centerY() - timeFont.caps() / 2 - timeFont.capsTop(), opacity(MUTED, layout.detailOpacity()), 0, false);
        } finally {
            CrispFont.end();
        }
        if (showLyrics.get()) renderLyrics(layout, state, position, seconds, accent);
        else if (!lyrics.snapshot().key().isEmpty()) { lyrics.close(); lyricsKey = ""; }
        if (seekTooltip.get() && (hoverTrack || timeline.active()) && canSeek) {
            long previewPosition = timeline.active() ? timeline.positionMs() : Math.round(state.durationMs() * layout.fractionAt(mouseX, track));
            renderSeekTooltip(timeFont, time(previewPosition), mouseX, track, unit);
        }
    }

    private void renderLyrics(SpotifyHudLayout layout, SpotifyMedia.State state, long position, double seconds, Color accent) {
        lyrics.update(state);
        var snapshot = lyrics.snapshot();
        if (!snapshot.key().equals(lyricsKey)) { lyricsKey = snapshot.key(); plainLyricIndex = 0; lyricLine = ""; lyricPosition = -1; lyricIndex = -1; }
        var words = lyricWords(snapshot, position, state.durationMs());
        var frame = words.frame();
        boolean changed = frame.index() != lyricIndex || !frame.current().equals(lyricLine);
        lyricIndex = frame.index();
        String preview = frame.next().strip().equalsIgnoreCase(frame.current().strip()) ? "" : frame.next();
        boolean seeked = lyricPosition < 0 || Math.abs(position - lyricPosition) > 750;
        lyricPosition = position;
        if (changed) { lyricLine = frame.current(); lyricFade = 0; lyricScroll = 0; lyricAge = 0; }
        lyricAge += seconds;
        lyricFade += (1 - lyricFade) * (1 - Math.exp(-14 * seconds));
        Rect area = layout.lyrics(); double unit = layout.scale();
        Renderer2D.COLOR.begin();
        // One shared surface, with a quiet divider instead of a second outlined box.
        box(new Rect(area.x() + 16 * unit, area.y() - 3 * unit, area.width() - 32 * unit, .6 * unit), .3 * unit, 0,
            new Color(146, 165, 203, 28), new Color(146, 165, 203, 28));
        Color dot = opacity(mix(accent, WHITE, .2));
        RoundedBox.draw(area.x() + 18 * unit, area.y() + 9 * unit, 3 * unit, 3 * unit, 1.5 * unit, 0, unit, 0, dot, dot);
        Renderer2D.COLOR.render();
        boolean karaoke = lyricsMode.get() != LyricsMode.Lines && words.timed();
        boolean ambient = snapshot.result().status() != SpotifyLyrics.Status.Synced && snapshot.result().status() != SpotifyLyrics.Status.Plain
            || frame.current().equals("♪");
        boolean missingWordTiming = !ambient && snapshot.result().status() == SpotifyLyrics.Status.Synced
            && lyricsMode.get() != LyricsMode.Lines && !words.timed();
        String heading = snapshot.result().status() == SpotifyLyrics.Status.Plain ? "UNTIMED LYRICS"
            : ambient ? "LISTENING" : missingWordTiming ? "LINE SYNC / NO WORD TIMING"
            : karaoke ? words.estimated() ? "ESTIMATED WORDS" : "LIVE WORDS" : "LIVE LYRICS";
        CrispFont.begin(null);
        try {
            var caption = CrispFont.POPPINS_MEDIUM.forCaps(6 * unit);
            caption.draw(heading, area.x() + 26 * unit, area.y() + 6 * unit - caption.capsTop(), opacity(MUTED, .65), 0, false);
        } finally { CrispFont.end(); }
        if (ambient) {
            renderLyricsAmbient(snapshot.result().status(), area, unit, state.playing(), accent);
        } else if (karaoke && lyricsMode.get() == LyricsMode.SingleWord) {
            String word = words.currentWord();
            if (changed || seeked || !word.equals(liveWord)) { liveWord = word; wordFade = 0; }
            wordFade += (1 - wordFade) * (1 - Math.exp(-22 * seconds));
            double size = (lyricsSize.get() + 9) * unit;
            double width = lyricWidth(word, size, true);
            lyricText(word, area.centerX() - Math.min(width, area.width() - 36 * unit) / 2,
                area.y() + 22 * unit, area.width() - 36 * unit, size, opacity(WHITE, .85 + .15 * wordFade), true);
            String next = words.active() + 1 < words.words().size() ? words.words().get(words.active() + 1).text().strip() : "";
            double nextWidth = lyricWidth(next, 8 * unit, false);
            lyricText(next, area.centerX() - nextWidth / 2, area.y() + (lyricsSize.get() + 38) * unit,
                area.width() - 36 * unit, 8 * unit, opacity(MUTED, .65), false);
        } else if (karaoke) {
            renderWordLine(words, area, unit, seconds, changed || seeked, accent);
            lyricText(preview, area.x() + 18 * unit, area.y() + area.height() - 18 * unit,
                area.width() - 36 * unit, 8 * unit, opacity(MUTED, .65), false);
        } else {
            double width = area.width() - 36 * unit, size = lyricsSize.get() * unit;
            WrappedLyric wrapped = wrapLyric(frame.current(), width, unit, size);
            String first = wrapped.first(), rest = wrapped.rest(); size = wrapped.size();
            lyricText(first, area.x() + 18 * unit, area.y() + (21 + 2 * (1 - lyricFade)) * unit,
                width, size, opacity(WHITE, .65 + .35 * lyricFade), true);
            if (!rest.isEmpty()) {
                double overflow = Math.max(0, lyricWidth(rest, size, true) - width);
                double offset = Math.min(overflow, Math.max(0, lyricAge - .7) * 24 * unit);
                double left = area.x() + 18 * unit;
                lyricRun(rest, left - offset, area.y() + (lyricsSize.get() + 26) * unit, size,
                    opacity(WHITE, .65 + .35 * lyricFade), true, left, left + width);
            }
            double nextTop = rest.isEmpty() ? Math.min(area.height() - 18 * unit, (lyricsSize.get() + 33) * unit) : area.height() - 18 * unit;
            lyricText(preview, area.x() + 18 * unit, area.y() + nextTop,
                width, 8 * unit, opacity(MUTED, .8), false);
        }
    }

    private SpotifyLyrics.WordFrame lyricWords(SpotifyLyrics.Snapshot snapshot, long position, long duration) {
        // SingleWord must never substitute a time distribution for the vocalist's word starts.
        var words = snapshot.karaoke(position, lyricsOffset.get(), plainLyricIndex, duration,
            lyricsMode.get() == LyricsMode.WordHighlight && estimateWords.get());
        return lyricsMode.get() == LyricsMode.SingleWord && words.timed() && !words.individualWords()
            ? new SpotifyLyrics.WordFrame(words.frame(), java.util.List.of(), -1, 0, false) : words;
    }

    private WrappedLyric wrapLyric(String text, double width, double unit, double desired) {
        if (wrappedLyric != null && wrappedLyric.text().equals(text) && wrappedLyric.width() == width
            && wrappedLyric.unit() == unit && wrappedLyric.desired() == desired) return wrappedLyric;
        double size = desired; String first, rest;
        while (true) {
            first = fitLyric(text, width - 2 * unit, size, true);
            int split = first.length();
            if (split < text.length()) {
                int space = first.lastIndexOf(' '); if (space > first.length() / 2) split = space;
                first = text.substring(0, split).stripTrailing();
            }
            rest = text.substring(split).stripLeading();
            if (lyricWidth(rest, size, true) <= width - 2 * unit || size <= 8 * unit) break;
            size = Math.max(8 * unit, size - unit);
        }
        return wrappedLyric = new WrappedLyric(text, width, unit, desired, first, rest, size);
    }

    private void renderLyricsAmbient(SpotifyLyrics.Status status, Rect area, double unit, boolean playing, Color accent) {
        String title = switch (status) {
            case Loading -> "Tuning in...";
            case Missing -> "Let the music speak";
            case Unavailable -> "Still vibing";
            case Instrumental -> "Just the music";
            case Waiting -> "Ready when you are";
            default -> "Let it breathe";
        };
        String subtitle = switch (status) {
            case Loading -> "Finding the words to your track";
            case Missing -> "No lyrics for this track yet";
            case Unavailable -> "Lyrics will retry. The music stays on.";
            case Instrumental -> "No vocals. Enjoy the instrumental.";
            case Waiting -> "Play a song to bring this card to life";
            default -> "Waiting for the vocals";
        };
        double phase = animation.shimmer() * Math.PI * 2;
        double cx = area.x() + area.width() - 48 * unit, cy = area.y() + 36 * unit;
        Renderer2D.COLOR.begin();
        Color haze = opacity(new Color(accent.r, accent.g, accent.b, (int) (8 + 4 * Math.sin(phase))));
        RoundedBox.draw(cx, cy, 65 * unit, 42 * unit, 21 * unit, 0, unit, 0, haze, haze);
        for (int i = 0; i < 5; i++) {
            double motion = playing ? (.5 + .5 * Math.sin(phase + i * .85)) : .15;
            double height = (3 + 11 * Math.max(levels[i], motion * .55)) * unit;
            Color ink = opacity(mix(accent, WHITE, .2), .45 + .35 * motion);
            RoundedBox.draw(cx + (i - 2) * 6 * unit, cy, 2.5 * unit, height, 1.25 * unit, 0, unit, 0, ink, ink);
        }
        Renderer2D.COLOR.render();
        lyricText(title, area.x() + 18 * unit, area.y() + 24 * unit, area.width() - 100 * unit,
            12 * unit, opacity(WHITE), true);
        lyricText(subtitle, area.x() + 18 * unit, area.y() + 46 * unit, area.width() - 100 * unit,
            7 * unit, opacity(MUTED, .75), false);
    }

    private void renderWordLine(SpotifyLyrics.WordFrame frame, Rect area, double unit, double seconds, boolean snap, Color accent) {
        String text = frame.frame().current(); double size = lyricsSize.get() * unit;
        double width = area.width() - 36 * unit, full = lyricWidth(text, size, true);
        double activeLeft = 0, activeRight = 0;
        if (frame.active() >= 0) {
            var word = frame.words().get(frame.active());
            activeLeft = lyricWidth(text.substring(0, word.from()), size, true);
            activeRight = lyricWidth(text.substring(0, word.to()), size, true);
        }
        double target = frame.active() < 0 ? lyricScroll : Math.max(0, Math.min(Math.max(0, full - width), (activeLeft + activeRight) / 2 - width * .48));
        lyricScroll = snap ? target : lyricScroll + (target - lyricScroll) * (1 - Math.exp(-12 * seconds));
        double left = area.x() + 18 * unit, top = area.y() + 25 * unit;
        lyricRun(text, left - lyricScroll, top, size, opacity(new Color(119, 134, 159)), true, left, left + width);
        if (frame.active() >= 0) {
            lyricRun(text, left - lyricScroll, top, size, opacity(WHITE), true,
                Math.max(left, left + activeLeft - lyricScroll), Math.min(left + width, left + activeRight - lyricScroll));
            double start = Math.max(left, left + activeLeft - lyricScroll), end = Math.min(left + width, left + activeRight - lyricScroll);
            double span = Math.max(0, end - start);
            if (span > 0) {
                Renderer2D.COLOR.begin();
                box(new Rect(start, top + size + 5 * unit, span, 1.5 * unit), .75 * unit, 0, mix(accent, WHITE, .35), accent);
                Renderer2D.COLOR.render();
            }
        }
    }

    private double lyricWidth(String text, double size, boolean bold) {
        return latin(text) ? (bold ? CrispFont.POPPINS_SEMIBOLD : CrispFont.POPPINS_MEDIUM).forCaps(size).width(text, 0)
            : mc.textRenderer.getWidth(text) * size / 8;
    }
    private static boolean latin(String text) { return text.codePoints().allMatch(c -> c >= 32 && c <= 255); }
    private String fitLyric(String text, double width, double size, boolean bold) {
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lyricWidth(text.substring(0, mid), size, bold) <= width) low = mid; else high = mid - 1;
        }
        if (low > 0 && low < text.length() && Character.isHighSurrogate(text.charAt(low - 1))) low--;
        return text.substring(0, low);
    }

    /** Use Minecraft's Unicode fallback for scripts outside the bundled Latin font. */
    private void lyricText(String text, double left, double top, double width, double size, Color ink, boolean bold) {
        if (text.isBlank() || ink.a < 8) return;
        lyricRun(text, left, top, size, ink, bold, left, left + width);
    }
    private void lyricRun(String text, double left, double top, double size, Color ink, boolean bold, double clipLeft, double clipRight) {
        if (text.isBlank() || ink.a < 8 || clipRight <= clipLeft) return;
        if (latin(text)) {
            var font = (bold ? CrispFont.POPPINS_SEMIBOLD : CrispFont.POPPINS_MEDIUM).forCaps(size);
            CrispFont.begin(null);
            try { font.drawClipped(text, left, top - font.capsTop(), ink, 0, clipLeft, clipRight); }
            finally { CrispFont.end(); }
        } else {
            var context = Renderer2D.context();
            float factor = (float) (size / 8);
            double guiScale = mc.getWindow().getScaleFactor();
            context.enableScissor((int) Math.ceil(clipLeft / guiScale), (int) Math.floor(top / guiScale),
                (int) Math.floor(clipRight / guiScale), (int) Math.ceil((top + size * 1.5) / guiScale));
            var matrices = context.getMatrices(); matrices.pushMatrix();
            try {
                float gui = (float) mc.getWindow().getScaleFactor();
                matrices.scale(1 / gui, 1 / gui); matrices.translate((float) left, (float) top); matrices.scale(factor, factor);
                context.drawText(mc.textRenderer, net.minecraft.text.Text.literal(text), 0, 0, ink.getPacked(), false);
            } finally { matrices.popMatrix(); context.disableScissor(); }
        }
    }

    private void updateTrack(SpotifyMedia.State state) {
        if (shownState == null || !trackKey(state).equals(trackKey(shownState)) || state.available() != shownState.available()) {
            if (songTransitions.get() && shownState != null) {
                // During quick skips, fade the label that is actually visible
                // at its current opacity instead of flashing an unseen title.
                double phase = animation.transition();
                double outgoing = leavingState == null ? 0 : leavingOpacity * Math.max(0, 1 - phase * 2);
                double incoming = leavingState == null ? 1 : Math.max(0, (phase - 0.45) / 0.55);
                if (incoming >= outgoing) {
                    leavingState = shownState;
                    leavingOpacity = incoming;
                    leavingShift = 4 * (1 - incoming);
                    leavingScrollAge = scrollAge;
                } else {
                    leavingOpacity = outgoing;
                    leavingShift -= 4 * phase;
                }
                animation.changeTrack();
            } else leavingState = null;
            scrollAge = 0;
        }
        if (shownState == null || !artworkKey(state).equals(artworkKey(shownState)) || state.available() != shownState.available()) {
            shownArtwork = null;
            waitingArtwork = state.available();
            artworkWait = 0;
            if (!state.available()) replaceCover(null);
        }
        shownState = state;
    }

    private static String artworkKey(SpotifyMedia.State state) {
        return SpotifyMedia.artworkKey(state);
    }

    private void updateArtwork(SpotifyMedia.State state, double seconds) {
        SpotifyMedia.Artwork latest = media.artwork();
        if (state.available() && latest != null && latest.key().equals(artworkKey(state))) {
            if (latest != shownArtwork) {
                // Finish the current crossfade before accepting another cover.
                // The latest matching artwork is still available next frame.
                if (songTransitions.get() && (coverTexture != null || leavingCoverTexture != null)
                    && animation.artworkTransition() < 1) return;
                shownArtwork = latest;
                replaceCover(latest);
            }
            waitingArtwork = false;
        } else if (waitingArtwork) {
            artworkWait += seconds;
            if (artworkWait >= 1.2) {
                waitingArtwork = false;
                replaceCover(null);
            }
        }
    }

    private void replaceCover(SpotifyMedia.Artwork latest) {
        if (leavingCoverTexture != null) leavingCoverTexture.close();
        leavingCoverTexture = coverTexture;
        coverTexture = null;
        if (latest != null) {
            coverTexture = new Texture(latest.width(), latest.height(), TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
            coverTexture.upload(latest.rgba());
        }
        animation.changeArtwork();
    }

    private void closeLeavingArtwork(double artFade, double cardFade) {
        if (artFade >= 1 && leavingCoverTexture != null) {
            leavingCoverTexture.close();
            leavingCoverTexture = null;
        }
        if (cardFade >= 1 && leavingCardTexture != null) {
            leavingCardTexture.close();
            leavingCardTexture = null;
        }
    }

    private void renderCover(Rect cover, double unit, double fade, double tilt) {
        Renderer2D.COLOR.begin();
        Color face = opacity(FACE);
        RoundedBox.draw(cover.centerX(), cover.centerY(), cover.width(), cover.height(), 11 * unit, 0, unit, tilt, face, face);
        // A note sits behind incoming artwork. Outgoing artwork stays opaque
        // under an incoming cover to avoid a dark dip at the middle of a fade.
        if (coverTexture == null || leavingCoverTexture == null) musicNote(cover.centerX(), cover.centerY(), unit * 1.3);
        Renderer2D.COLOR.render();
        if (leavingCoverTexture != null && fade < 1) {
            textured(leavingCoverTexture, cover, new Color(255, 255, 255, coverTexture != null ? 255 : alpha(1 - fade)), tilt);
        }
        if (coverTexture != null) textured(coverTexture, cover, new Color(255, 255, 255, alpha(fade)), tilt);
    }

    private void textured(Texture texture, Rect rect, Color ink) {
        textured(texture, rect, ink, 0);
    }

    private void textured(Texture texture, Rect rect, Color ink, double tilt) {
        Renderer2D.TEXTURE.begin();
        Renderer2D.TEXTURE.texQuad(rect.x(), rect.y(), rect.width(), rect.height(), tilt, 0, 0, 1, 1, opacity(ink));
        Renderer2D.TEXTURE.render(texture.getGlTextureView(), texture.getSampler());
    }

    private static int alpha(double amount) {
        return (int) Math.round(255 * Math.max(0, Math.min(1, amount)));
    }

    private void songText(SpotifyMedia.State state, SpotifyHudLayout layout, CrispFont.Sized artist, CrispFont.Sized title,
                          double fade, double shiftY, double age) {
        if (fade <= 0) return;
        label(state.available() ? state.artist() : "Spotify", artist, layout.textLeft(),
            layout.y(layout.lerp(15, 17)) + shiftY - artist.capsTop(), layout.textWidth(), opacity(MUTED, fade), age, layout.scale());
        label(state.title(), title, layout.textLeft(), layout.y(layout.lerp(34, 38)) + shiftY - title.capsTop(),
            layout.textWidth(), opacity(WHITE, fade), age, layout.scale());
    }

    private void label(String value, CrispFont.Sized font, double left, double top, double width, Color ink, double age, double unit) {
        if (value == null || value.isBlank()) value = "Unknown";
        if (scrollTitles.get()) {
            double offset = SpotifyHudFeatures.marquee(font.width(value, 0), width, age, unit);
            font.drawClipped(value, left - offset, top, ink, 0, left, left + width);
        } else font.drawClipped(clip(value, font, width), left, top, ink, 0, left, left + width);
    }

    private void shimmer(Rect track, double filled, double phase, double unit) {
        // Each small segment stays within the flat interior of the rounded
        // fill. The highlight leaves the edge before its phase wraps around.
        double inset = track.height() / 2;
        double width = Math.max(0, filled - inset * 2);
        if (width < 4 * unit) return;
        double center = -24 * unit + (width + 48 * unit) * phase;
        for (double start = 0; start < width; start += 3 * unit) {
            double segment = Math.min(3 * unit, width - start);
            double distance = (start + segment / 2 - center) / (10 * unit);
            int opacity = (int) Math.round(88 * Math.exp(-distance * distance / 2));
            if (opacity < 1) continue;
            RoundedBox.bar(0, 0, track.x() + inset + start, track.y() + unit, segment, track.height() - 2 * unit,
                0, opacity(new Color(255, 255, 255, opacity)));
        }
    }

    private void box(Rect rect, double radius, double rim, Color fill, Color edge) {
        RoundedBox.draw(rect.centerX(), rect.centerY(), rect.width(), rect.height(), radius, rim, 1, 0, opacity(fill), opacity(edge));
    }

    private void updateCard(int tintRgb) {
        int height = SpotifyCardRaster.PANEL_HEIGHT + (showLyrics.get() ? (int) Math.round(layout().lyricsHeight() + 8) : 0);
        if (cardTexture != null && cardTint == tintRgb && cardTheme == theme.get() && cardHeight == height) return;
        if (leavingCardTexture != null) leavingCardTexture.close();
        leavingCardTexture = cardTexture;
        SpotifyCardRaster.Raster raster = theme.get() == SpotifyCardRaster.Theme.AlbumColours && albumAccent.get() && shownArtwork != null
            ? showLyrics.get() ? shownArtwork.lyricsRaster() : shownArtwork.cardRaster()
            : showLyrics.get() ? SpotifyCardRaster.lyricsPreset(theme.get()) : SpotifyCardRaster.preset(theme.get());
        cardTexture = new Texture(raster.width(), raster.height(), TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
        cardTexture.upload(raster.rgba());
        cardTint = tintRgb;
        cardTheme = theme.get();
        cardHeight = height;
        if (leavingCardTexture != null) animation.changeCard();
    }

    private void button(Rect target, String action, int index, boolean available, double unit, Color accent, double fade) {
        double hover = animation.hover(index);
        double press = animation.pressAmount(index);
        double cx = target.centerX();
        double cy = target.centerY() + press * 0.65 * unit;
        boolean primary = action.equals("toggle");
        double iconUnit = unit * (1 - press * 0.065);
        if (primary) {
            double size = target.width() + (2 * hover - 2.5 * press) * unit;
            RoundedBox.shadow(cx, cy + 2 * unit, size, size, size / 2, 8 * unit, 0, 0.20 * visibility * fade);
            Color fill = available ? mix(new Color(220, 227, 240), WHITE, hover * 0.8) : new Color(75, 82, 98);
            fill = mix(fill, accent, 0.25);
            fill = opacity(fill, fade);
            RoundedBox.draw(cx, cy, size, size, size / 2, 0, unit, 0, fill, fill);
        } else if (hover + press > 0.01) {
            Color fill = new Color(150, 171, 206, (int) Math.round(26 * Math.max(hover, press) * fade));
            box(new Rect(target.x() + press * unit, target.y() + press * unit, target.width() - 2 * press * unit,
                target.height() - 2 * press * unit), 11 * unit, 0, fill, fill);
        }
        Color ink = primary ? new Color(28, 34, 46) : available ? mix(new Color(194, 204, 223), WHITE, hover) : MUTED;
        ink = opacity(ink, fade);
        unit = iconUnit;
        if (action.equals("previous")) {
            triangle(cx - 3 * unit, cy, cx + 4 * unit, cy - 4.5 * unit, cx + 4 * unit, cy + 4.5 * unit, ink);
            RoundedBox.draw(cx - 6 * unit, cy, 1.7 * unit, 9 * unit, 0.65 * unit, 0, 0.65, 0, ink, ink);
        } else if (action.equals("next")) {
            triangle(cx + 3 * unit, cy, cx - 4 * unit, cy - 4.5 * unit, cx - 4 * unit, cy + 4.5 * unit, ink);
            RoundedBox.draw(cx + 6 * unit, cy, 1.7 * unit, 9 * unit, 0.65 * unit, 0, 0.65, 0, ink, ink);
        } else {
            double pause = animation.playPause();
            if (pause > 0.005) {
                Color pauseInk = new Color(ink.r, ink.g, ink.b, (int) Math.round(ink.a * pause));
                RoundedBox.draw(cx - 3.3 * unit, cy, 3 * unit, 13 * unit, 0.8 * unit, 0, unit, 0, pauseInk, pauseInk);
                RoundedBox.draw(cx + 3.3 * unit, cy, 3 * unit, 13 * unit, 0.8 * unit, 0, unit, 0, pauseInk, pauseInk);
            }
            if (pause < 0.995) triangle(cx + 6 * unit, cy, cx - 3.5 * unit, cy - 6.5 * unit, cx - 3.5 * unit, cy + 6.5 * unit,
                new Color(ink.r, ink.g, ink.b, (int) Math.round(ink.a * (1 - pause))));
        }
    }

    private static double ease(double from, double target, double rate, double seconds) {
        return from + (target - from) * (1 - Math.exp(-rate * seconds));
    }

    private void volumeShapes(SpotifyHudLayout layout, double mouseX, double mouseY, Color accent) {
        SpotifyMedia.Volume current = media.volume();
        double unit = layout.scale();
        Rect mute = layout.volumeMute(), track = layout.volumeTrack();
        Color ink = opacity(current.available() ? WHITE : MUTED, current.available() ? 1 : 0.5);
        if (current.available() && mute.contains(mouseX, mouseY)) box(mute, 7 * unit, 0, opacity(FACE), opacity(FACE));
        double cx = mute.centerX() - 2 * unit, cy = mute.centerY();
        RoundedBox.draw(cx - 3 * unit, cy, 4 * unit, 6 * unit, unit, 0, 0.7 * unit, 0, ink, ink);
        RoundedBox.polygon(new double[]{cx - 2 * unit, cx + 3 * unit, cx + 3 * unit, cx - 2 * unit},
            new double[]{cy - 3 * unit, cy - 6 * unit, cy + 6 * unit, cy + 3 * unit}, 4, 0, 0.7 * unit, ink);
        if (current.muted() || current.level() <= 0) {
            RoundedBox.draw(cx + 7 * unit, cy, 1.4 * unit, 7 * unit, 0.7 * unit, 0, 0.7 * unit, 45, ink, ink);
            RoundedBox.draw(cx + 7 * unit, cy, 1.4 * unit, 7 * unit, 0.7 * unit, 0, 0.7 * unit, -45, ink, ink);
        } else {
            for (int i = -2; i <= 2; i++) {
                double angle = i * Math.PI / 8;
                RoundedBox.draw(cx + 2 * unit + Math.cos(angle) * 6 * unit, cy + Math.sin(angle) * 6 * unit,
                    1.4 * unit, 2.5 * unit, 0.7 * unit, 0, 0.6 * unit, Math.toDegrees(angle), ink, ink);
            }
        }
        box(track, 2 * unit, 0, opacity(TRACK), opacity(TRACK));
        double filled = track.width() * current.level();
        Color fill = opacity(current.muted() ? MUTED : mix(accent, WHITE, .5), current.available() ? 1 : .3);
        if (filled > 0) RoundedBox.draw(track.x() + filled / 2, track.centerY(), filled, track.height(),
            Math.min(2 * unit, filled / 2), 0, .7 * unit, 0, fill, fill);
        double size = (draggingVolume || layout.volumeHit().contains(mouseX, mouseY) ? 8 : 6) * unit;
        RoundedBox.draw(track.x() + filled, track.centerY(), size, size, size / 2, 0, .7 * unit, 0, ink, ink);
    }

    private static Color mix(Color from, Color to, double amount) {
        return new Color((int) Math.round(from.r + (to.r - from.r) * amount),
            (int) Math.round(from.g + (to.g - from.g) * amount),
            (int) Math.round(from.b + (to.b - from.b) * amount), from.a);
    }

    private static void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color ink) {
        RoundedBox.polygon(new double[]{x1, x2, x3}, new double[]{y1, y2, y3}, 3, 0, 0.7, ink);
    }

    private void musicNote(double cx, double cy, double unit) {
        Color ink = opacity(MUTED);
        RoundedBox.draw(cx + 2 * unit, cy - unit, 2 * unit, 13 * unit, 0.8 * unit, 0, 0.7, 0, ink, ink);
        RoundedBox.draw(cx + 6 * unit, cy - 6 * unit, 9 * unit, 3 * unit, 0.7 * unit, 0, 0.7, 12, ink, ink);
        RoundedBox.draw(cx - unit, cy + 5 * unit, 7 * unit, 5 * unit, 2.5 * unit, 0, 0.7, -15, ink, ink);
    }

    private Color opacity(Color color) { return opacity(color, 1); }
    private Color opacity(Color color, double fade) {
        int red = color.r, green = color.g, blue = color.b;
        if (theme.get() == SpotifyCardRaster.Theme.Monochrome) red = green = blue = (int) Math.round(red * 0.2126 + green * 0.7152 + blue * 0.0722);
        return new Color(red, green, blue, (int) Math.round(color.a * visibility * fade));
    }

    private void renderVisualizer(SpotifyHudLayout layout, Rect cover, double unit, Color accent) {
        Color base = theme.get() == SpotifyCardRaster.Theme.Monochrome ? new Color(207, 210, 217) : mix(barColor.get(), accent, 0.14);
        Color ink = opacity(base);
        if (visualizerStyle.get() == Visualizer.CoverRing) {
            double radius = cover.width() / 2 + 4 * unit;
            for (int i = 0; i < 28; i++) {
                double angle = Math.PI * 2 * i / 28;
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double length = (1.8 + 3.8 * levels[i % levels.length]) * unit;
                double px = cover.centerX() + Math.copySign(Math.sqrt(Math.abs(cos)), cos) * radius;
                double py = cover.centerY() + Math.copySign(Math.sqrt(Math.abs(sin)), sin) * radius;
                RoundedBox.drawCompact(px, py, 1.7 * unit, length, 0.85 * unit, 0, 0.7 * unit,
                    Math.toDegrees(angle) - 90, ink, ink);
            }
            return;
        }
        double step = layout.lerp(4.5, 6.5) * unit;
        double start = layout.toggle().centerX() - 3 * step;
        double bottom = layout.y(layout.lerp(64, 74));
        if (visualizerStyle.get() == Visualizer.Bars) {
            double width = layout.lerp(2.5, 3.5) * unit;
            for (int i = 0; i < levels.length; i++) {
                double height = (layout.lerp(1.7, 2.5) + layout.lerp(5.3, 12.5) * levels[i]) * unit;
                RoundedBox.drawCompact(start + i * step, bottom - height / 2, width, height, width / 2,
                    0, Math.min(1, unit), 0, ink, ink);
            }
        } else {
            double lastX = start, lastY = waveY(0, bottom, layout, unit);
            for (int i = 1; i <= 24; i++) {
                double band = i / 4.0;
                double px = start + band * step, py = waveY(band, bottom, layout, unit);
                double length = Math.hypot(px - lastX, py - lastY);
                double degrees = Math.toDegrees(Math.atan2(py - lastY, px - lastX));
                RoundedBox.drawCompact((lastX + px) / 2, (lastY + py) / 2, length + 0.5 * unit, 1.8 * unit,
                    0.9 * unit, 0, 0.8 * unit, degrees, ink, ink);
                lastX = px; lastY = py;
            }
        }
    }

    private double waveY(double band, double bottom, SpotifyHudLayout layout, double unit) {
        int index = Math.min(5, (int) band);
        double level = levels[index] + (levels[index + 1] - levels[index]) * (band - index);
        return bottom - layout.lerp(4.5, 6) * unit
            + Math.sin(animation.shimmer() * Math.PI * 8 + band * 1.3) * level * layout.lerp(3.5, 5.5) * unit;
    }

    private void renderSeekTooltip(CrispFont.Sized font, String value, double mouseX, Rect track, double unit) {
        double width = font.width(value, 0) + 18 * unit;
        double left = Math.max(2, Math.min(mc.getWindow().getFramebufferWidth() - width - 2, mouseX - width / 2));
        double top = Math.max(2, track.y() - 29 * unit);
        Rect tip = new Rect(left, top, width, 22 * unit);
        Renderer2D.COLOR.begin();
        RoundedBox.shadow(tip.centerX(), tip.centerY() + 2 * unit, tip.width(), tip.height(), 7 * unit, 5 * unit, 0, 0.30 * visibility);
        box(tip, 7 * unit, 0.7 * unit, new Color(25, 30, 41, 250), new Color(88, 102, 128, 180));
        double fraction = timeline.active() ? (double) timeline.positionMs() / Math.max(1, media.state().durationMs()) : layout().fractionAt(mouseX, track);
        Color marker = opacity(new Color(210, 223, 245, 175));
        RoundedBox.draw(track.x() + track.width() * fraction, track.centerY(), 2 * unit, 10 * unit, unit, 0, unit, 0, marker, marker);
        Renderer2D.COLOR.render();
        CrispFont.begin(null);
        try { font.centred(value, tip.centerX(), top + (tip.height() - font.caps()) / 2, opacity(WHITE), 0, false); }
        finally { CrispFont.end(); }
    }

    public static String time(long milliseconds) {
        long total = Math.max(0, milliseconds) / 1000;
        return total >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
            : String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static String clip(String value, CrispFont.Sized font, double width) {
        if (value == null || value.isBlank()) return "Unknown";
        if (font.width(value, 0) <= width) return value;
        while (!value.isEmpty() && font.width(value + "...", 0) > width) value = value.substring(0, value.length() - 1);
        return value + "...";
    }
}
