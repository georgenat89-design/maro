package dev.maro.gui.spotify;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.module.impl.misc.SpotifyPhone;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifyPlayback;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/** A foreground phone with the player's skin, live album art and local media controls. */
public final class SpotifyPhoneScreen extends Screen {
    public static final float PHONE_WIDTH = 208, PHONE_HEIGHT = 464;
    private static final int WHITE = 0xFFF5F7F6, MUTED = 0xFFA3ADA7, GREEN = 0xFF1ED760;
    private final SpotifyPhone owner;
    private final SpotifyPlayback player;
    private final PhoneMovement movement = new PhoneMovement();
    private final PhoneApps apps;
    private final dev.maro.phone.PhoneNetwork network;
    private boolean interacting;
    private final long openedAt = System.nanoTime();
    private long closingAt;
    private float closingFrom;
    private long lastControlAt;
    private String lastControl = "";
    private long requestedSwipeAt;
    private Texture cover, previousCover;
    private SpotifyMedia.Artwork requestedArtwork;
    private java.util.concurrent.CompletableFuture<byte[]> artworkJob;
    private String observedTrack;
    private long swipeAt, coverAt;
    private long animationNow;
    private boolean pendingSwipe;
    private int swipeDirection = 1, requestedDirection = 1;
    private final java.util.Set<Integer> mediaHeld = new java.util.HashSet<>();
    private boolean attackHeld, useHeld;
    private float interactGlow;
    private long lastFrame;

    private SpotifyMedia.Artwork shownArtwork;
    private boolean dragging, draggingVolume, looking, removed, sizeChanged, pickerSuspended;
    private Screen suspendedPicker;
    private boolean leftAlt, rightAlt;
    private double lastAudibleVolume = .5;
    private long scrubPosition;
    private String scrubTrack = "";

    public record Layout(float x, float y, float scale, float rotation, float width, float height) {
        public float localX(double mx, double my) {
            float px = width / 2, py = height - 40;
            double dx = (mx - x) / scale - px, dy = (my - y) / scale - py;
            return px + (float) (dx * Math.cos(rotation) + dy * Math.sin(rotation));
        }
        public float localY(double mx, double my) {
            float px = width / 2, py = height - 40;
            double dx = (mx - x) / scale - px, dy = (my - y) / scale - py;
            return py + (float) (dy * Math.cos(rotation) - dx * Math.sin(rotation));
        }
        public float screenX(float lx, float ly) {
            float px = width / 2, py = height - 40;
            return x + scale * (px + (float) ((lx - px) * Math.cos(rotation) - (ly - py) * Math.sin(rotation)));
        }
        public float screenY(float lx, float ly) {
            float px = width / 2, py = height - 40;
            return y + scale * (py + (float) ((lx - px) * Math.sin(rotation) + (ly - py) * Math.cos(rotation)));
        }
    }

    public SpotifyPhoneScreen(SpotifyPhone owner, SpotifyPlayback player) {
        this(owner, player, dev.maro.phone.PhoneNetwork.get());
    }
    public SpotifyPhoneScreen(SpotifyPhone owner, SpotifyPlayback player, dev.maro.phone.PhoneNetwork network) {
        this(owner,player,network,null);
    }
    public SpotifyPhoneScreen(SpotifyPhone owner, SpotifyPlayback player, dev.maro.phone.PhoneNetwork network,dev.maro.phone.SpotifyLibrary library) {
        this(owner,player,network,library,null);
    }
    public SpotifyPhoneScreen(SpotifyPhone owner, SpotifyPlayback player, dev.maro.phone.PhoneNetwork network,dev.maro.phone.SpotifyLibrary library,java.nio.file.Path clipsFolder) {
        super(Text.literal("Spotify Phone"));
        this.owner = owner;
        this.player = player;
        this.network = network;
        this.apps = new PhoneApps(network,library,clipsFolder,owner);
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        leftAlt = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT);
        rightAlt = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
        var c = net.minecraft.client.MinecraftClient.getInstance();
        // Capture before setScreen releases vanilla bindings, so opening the phone preserves a held dig/use.
        boolean resume = c.currentScreen == null && c.isWindowFocused() && owner.mouseLook.get()
            && owner.gameInteraction.get() && !leftAlt && !rightAlt;
        attackHeld = resume && c.options.attackKey.isPressed();
        useHeld = resume && c.options.useKey.isPressed();
    }

    public Layout layout() {
        float deviceWidth = deviceWidth(), deviceHeight = deviceHeight();
        float scale = Math.min(height * (tablet() ? .64f : .77f) / deviceHeight,
            width * (tablet() ? .70f : .43f) / deviceWidth) * owner.size.getFloat();
        scale = Math.max(.1f, Math.min(scale, Math.min((height - 16f) / deviceHeight, (width - 16f) / (deviceWidth + 25))));
        float x = owner.hand.is("Left") ? 25 * scale : width - (deviceWidth + 25) * scale;
        float pocket = pocketProgress();
        float direction = owner.hand.is("Left") ? -1 : 1;
        return new Layout(x + direction * pocket * 38 * scale,
            height - (deviceHeight + 10) * scale + pocket * (deviceHeight + 50) * scale,
            scale, direction * pocket * .27f, deviceWidth, deviceHeight);
    }
    private boolean tablet() { return owner.device.is("iPad"); }
    private float deviceWidth() { return tablet() ? 416 : PHONE_WIDTH; }
    private float deviceHeight() { return tablet() ? 336 : PHONE_HEIGHT; }
    private float controlsX(float x) { return x - (tablet() ? 192 : 0); }
    private float controlsY(float y) { return y + (tablet() ? 158 : 0); }

    private float pocketProgress() {
        long now = System.nanoTime();
        animationNow = now;
        if (closingAt != 0) {
            float t = (float) Math.clamp((now - closingAt) / 240_000_000.0, 0, 1);
            return closingFrom + (1 - closingFrom) * t * t * (3 - 2 * t);
        }
        return (float) Math.pow(1 - Math.clamp((now - openedAt) / 320_000_000.0, 0, 1), 3);
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var state = player.state();
        observeTrack(state);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1e9f); lastFrame = now;
        var layout = layout();
        float mx = layout.localX(mouseX, mouseY), my = layout.localY(mouseX, mouseY);
        if (allowsMouseLook()) { mx = -10000; my = -10000; }
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(layout.x, layout.y);
        matrices.scale(layout.scale, layout.scale);
        matrices.translate(layout.width / 2, layout.height - 40);
        matrices.rotate(layout.rotation);
        matrices.translate(-layout.width / 2, -(layout.height - 40));
        Fonts.beginRaw(); Fonts.setMeasureScale(layout.scale);
        try {
            if (owner.showHand.get()) hand(ctx, false);
            int tint = 0xFF25352B;
            updateArtwork(state);
            if (shownArtwork != null) tint = 0xFF000000 | shownArtwork.tintRgb();
            Render2D.shadow(ctx, 0, 0, layout.width, layout.height, 27, 14, 0xA0000000);
            Render2D.roundRect(ctx, -1, -1, layout.width + 2, layout.height + 2, 27, 0xFF49504E);
            Render2D.roundRect(ctx, 0, 0, layout.width, layout.height, 27, 0xFF090B0C);
            Render2D.roundGradientV(ctx, 6, 6, layout.width - 12, layout.height - 12, 22,
                ColorUtil.lerp(0xFF171B1D, tint, .18f), 0xFF111516);
            Render2D.roundRect(ctx, layout.width / 2 - 20, 17, 40, 4, 2, 0xFF080A0B);
            if (apps.music()) {
                PhoneIcons.spotify(ctx, 30, 75);
                text(ctx, "Spotify", 45, 70, WHITE, true, 1.2f);
            }
            float closeX = layout.width - 38, closeY = apps.music() ? 63 : 44;
            boolean closeHover = hit(mx, my, closeX, closeY, 24, 24);
            if (closeHover) Render2D.roundRect(ctx, closeX, closeY, 24, 24, 12, 0xFF303736);
            Render2D.line(ctx, closeX + 8, closeY + 8, closeX + 16, closeY + 16, 1.5f, MUTED);
            Render2D.line(ctx, closeX + 16, closeY + 8, closeX + 8, closeY + 16, 1.5f, MUTED);
            if (apps.music()) {
            Render2D.shadow(ctx, PhoneAlbumMesh.X, PhoneAlbumMesh.Y, PhoneAlbumMesh.SIZE, PhoneAlbumMesh.SIZE, PhoneAlbumMesh.RADIUS, 8, 0x65000000);
            if (cover != null || previousCover != null) drawCover(ctx);
            else {
                Render2D.roundGradientV(ctx, PhoneAlbumMesh.X, PhoneAlbumMesh.Y, PhoneAlbumMesh.SIZE, PhoneAlbumMesh.SIZE, PhoneAlbumMesh.RADIUS,
                    ColorUtil.lerp(0xFF243D2F, tint, .4f), 0xFF14221A);
                PhoneIcons.spotify(ctx, 104, 172);
                centered(ctx, state.available() ? "NOW PLAYING" : "YOUR MUSIC, IN GAME", 104, 207, MUTED, false, .85f);
            }
            matrices.pushMatrix();
            if (tablet()) matrices.translate(192, -158);
            mx = controlsX(mx); my = controlsY(my);
            text(ctx, Fonts.trim(state.available() ? state.title() : "Ready when you are", 160, true, 1.35f),
                24, 263, WHITE, true, 1.35f);
            text(ctx, Fonts.trim(state.available() ? state.artist() : "Open Spotify and start a song", 160, false, .95f),
                24, 281, MUTED, false, .95f);
            long position = dragging ? scrubPosition : state.currentPositionMs();
            double fraction = state.durationMs() <= 0 ? 0 : Math.clamp((double) position / state.durationMs(), 0, 1);
            Render2D.roundRect(ctx, 24, 306, 160, 3, 1.5f, 0xFF3B4940);
            if (fraction > 0) Render2D.roundRect(ctx, 24, 306, (float) fraction * 160, 3, 1.5f, GREEN);
            if (state.available() && state.canSeek() && (dragging || hit(mx, my, 20, 298, 168, 19)))
                Render2D.circle(ctx, 24 + (float) fraction * 160, 307.5f, 3.5f, WHITE);
            text(ctx, time(position), 24, 316, MUTED, false, .75f);
            String duration = time(state.durationMs());
            text(ctx, duration, 184 - Fonts.width(duration, false, .75f), 316, MUTED, false, .75f);
            if (state.available()) {
                skip(ctx, 53, mx, my, false);
                skip(ctx, 155, mx, my, true);
                boolean hover = circleHit(mx, my, 104, 350, 22);
                Render2D.circle(ctx, 104, 350, hover ? 22 : 21, hover ? 0xFF59E58B : GREEN);
                if (state.playing()) {
                    Render2D.roundRect(ctx, 98, 343, 4, 14, 1, 0xFF07140C);
                    Render2D.roundRect(ctx, 106, 343, 4, 14, 1, 0xFF07140C);
                } else triangle(ctx, 100, 342, 100, 358, 112, 350, 0xFF07140C);
                String hint = state.error().isBlank()
                    ? owner.playPauseBind.getKeyName() + ": play · " + owner.previousBind.getKeyName() + " / " + owner.nextBind.getKeyName() + ": skip" : state.error();
                centered(ctx, Fonts.trim(hint, 170, false, .7f), 104, 376, MUTED, false, .7f);
            } else {
                boolean hover = hit(mx, my, 24, 337, 160, 27);
                Render2D.roundRect(ctx, 24, 337, 160, 27, 13.5f, hover ? 0xFF59E58B : GREEN);
                centered(ctx, "Open Spotify", 104, 346, 0xFF07140C, true, 1);
                centered(ctx, Fonts.trim(state.error().isBlank() ? "Desktop or web player" : state.error(), 166, false, .7f),
                    104, 376, MUTED, false, .7f);
            }
            drawVolume(ctx, mx, my);
            text(ctx, "Size", 24, 433, MUTED, false, .8f);
            sizeButton(ctx, 85, mx, my, false);
            centered(ctx, Math.round(owner.size.get() * 100) + "%", 134, 433, WHITE, false, .85f);
            sizeButton(ctx, 162, mx, my, true);
            matrices.popMatrix();
            }
            float appMx = allowsMouseLook() ? -10000 : layout.localX(mouseX, mouseY);
            float appMy = allowsMouseLook() ? -10000 : layout.localY(mouseX, mouseY);
            apps.render(ctx, layout.width, layout.height, appMx, appMy);
            interactionButton(ctx, layout.width, appMx, appMy, dt);
            Render2D.roundRect(ctx, layout.width / 2 - 27, layout.height - 12, 54, 3, 1.5f, 0xFF8B9591);
            if (owner.showHand.get()) hand(ctx, true);
            if (owner.showHand.get() && owner.swipeAnimation.get() && apps.music()) swipeHand(ctx);
        } finally { Fonts.setMeasureScale(1); Fonts.endRaw(); matrices.popMatrix(); }
        if ((allowsMovement() || owner.mouseLook.get()) && closingAt == 0) {
            float hintX = owner.hand.is("Left") ? layout.x + (layout.width + 30) * layout.scale : 8;
            Fonts.beginRaw();
            String hint = owner.mouseLook.get() ? "Mouse: look" + (owner.gameInteraction.get() ? " + game actions" : "") + " · " + owner.interactBind.getKeyName() + ": apps · Alt: controls"
                : "Move keys · Right-drag outside phone to look";
            try { Fonts.draw(ctx, Fonts.trim(hint, width - hintX - 8, false, .7f),
                hintX, height - 11, 0xBDE6ECE8, false, .7f); }
            finally { Fonts.endRaw(); }
        }
    }

    private void drawVolume(DrawContext ctx, float mx, float my) {
        var volume = player.volume();
        int color = volume.available() ? WHITE : 0xFF68716D;
        text(ctx, "Output volume", 24, 393, MUTED, false, .75f);
        String percent = volume.available() ? (volume.muted() ? "Muted" : volume.percent() + "%") : "—";
        text(ctx, percent, 184 - Fonts.width(percent, false, .75f), 393, color, false, .75f);
        if (hit(mx, my, 20, 403, 25, 21) && volume.available()) Render2D.circle(ctx, 32, 413, 11, 0xFF303736);
        PhoneIcons.volume(ctx, 32, 413, volume.available(), volume.muted(), volume.level());
        float fraction = volume.available() && !volume.muted() ? (float) Math.clamp(volume.level(), 0, 1) : 0;
        Render2D.roundRect(ctx, 52, 411, 132, 3, 1.5f, 0xFF3A4240);
        if (fraction > 0) Render2D.roundRect(ctx, 52, 411, 132 * fraction, 3, 1.5f, 0xFFDCE7E0);
        if (volume.available()) Render2D.circle(ctx, 52 + 132 * fraction, 412.5f, draggingVolume ? 4 : 3, WHITE);
    }

    private static void sizeButton(DrawContext ctx, float x, float mx, float my, boolean plus) {
        Render2D.roundRect(ctx, x, 426, 22, 22, 6, hit(mx, my, x, 426, 22, 22) ? 0xFF3A4541 : 0xFF262E2B);
        Render2D.line(ctx, x + 7, 437, x + 15, 437, 1.3f, WHITE);
        if (plus) Render2D.line(ctx, x + 11, 433, x + 11, 441, 1.3f, WHITE);
    }

    private void observeTrack(SpotifyMedia.State state) {
        String key = state.available() ? SpotifyMedia.artworkKey(state) : "";
        if (observedTrack != null && !key.isEmpty() && !observedTrack.isEmpty() && !key.equals(observedTrack)) {
            pendingSwipe = true;
            long now=System.nanoTime();
            // Metadata can arrive before artwork. Local skips have already moved the hand.
            if(requestedSwipeAt==0 || now-requestedSwipeAt>1_500_000_000L) {
                swipeDirection=1;swipeAt=now;
            }
            requestedSwipeAt=0;requestedDirection=1;
        }
        observedTrack = key;
    }

    private void updateArtwork(SpotifyMedia.State state) {
        var art = player.artwork();
        if (art != null && !art.key().equals(SpotifyMedia.artworkKey(state))) art = null;
        if(!state.available()) {
            if(artworkJob!=null)artworkJob.cancel(false);artworkJob=null;requestedArtwork=shownArtwork=null;
            if(cover!=null)cover.close();if(previousCover!=null)previousCover.close();cover=previousCover=null;
            swipeAt=coverAt=0;pendingSwipe=false;return;
        }
        if (art != requestedArtwork) {
            requestedArtwork = art;
            if (artworkJob != null) artworkJob.cancel(false);
            artworkJob = null;
            if (art != null) {
                var request = art;
                artworkJob = java.util.concurrent.CompletableFuture.supplyAsync(() -> PhoneAlbumArt.prepare(request,false),
                    net.minecraft.util.Util.getMainWorkerExecutor());
            }
        }
        if (artworkJob != null && artworkJob.isDone()) {
            try {
                byte[] pixels = artworkJob.join();
                var next = new Texture(PhoneAlbumArt.SIZE, PhoneAlbumArt.SIZE, TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
                try { next.upload(pixels); } catch(RuntimeException error) { next.close();throw error; }
                if(previousCover!=null)previousCover.close();
                previousCover=cover;cover=next;shownArtwork=requestedArtwork;
                boolean transition=pendingSwipe&&previousCover!=null;
                // Late artwork slides in without replaying the hand or blanking the old cover.
                coverAt=transition ? (animationNow-swipeAt<PhoneSwipeMotion.DURATION*.65
                    ? swipeAt : animationNow-(long)(PhoneSwipeMotion.DURATION*.12)) : animationNow;
                pendingSwipe=false;
                if(!transition&&previousCover!=null){previousCover.close();previousCover=null;}
            } catch (RuntimeException ignored) { }
            artworkJob = null;
        }
        if (previousCover != null && cover != null && animationNow - coverAt >= PhoneSwipeMotion.DURATION) {
            previousCover.close(); previousCover = null;
        }
    }

    private void drawCover(DrawContext ctx) {
        float t = PhoneSwipeMotion.stroke(PhoneSwipeMotion.time(animationNow,coverAt));
        if (previousCover != null) PhoneAlbumMesh.draw(ctx, previousCover, -swipeDirection * t * PhoneAlbumMesh.SIZE);
        if (cover != null) PhoneAlbumMesh.draw(ctx, cover, previousCover == null ? 0 : swipeDirection * (1 - t) * PhoneAlbumMesh.SIZE);
    }

    private void interactionButton(DrawContext ctx, float width, float mx, float my, float dt) {
        float x = width / 2 - 56;
        boolean over = hit(mx, my, x, 27, 112, 20);
        interactGlow += ((over ? 1 : 0) - interactGlow) * (1 - (float)Math.exp(-18 * dt));
        Render2D.roundRect(ctx, x, 27, 112, 20, 10, ColorUtil.lerp(0xFF202E34, 0xFF344B49, interactGlow));
        Render2D.roundOutline(ctx, x, 27, 112, 20, 10, .6f, ColorUtil.lerp(0x404C7067, 0xC061D89A, interactGlow));
        for (int i = 0; i < 4; i++) Render2D.roundRect(ctx, x + 9 + i % 2 * 4, 33 + i / 2 * 4, 2.6f, 2.6f, .7f, GREEN);
        String label = interacting && apps.app() == PhoneApps.App.HOME ? "Resume" : "Interact";
        centered(ctx, label, x + 54, 33, WHITE, true, .78f);
        String key = Fonts.trim(owner.interactBind.getKeyName(), 22, true, .61f);
        Render2D.roundRect(ctx, x + 86, 30, 21, 14, 5, 0xFF111F26);
        Fonts.drawCentered(ctx, key, x + 96.5f, 37, MUTED, true, .61f);
    }

    private void interact() {
        if (interacting && apps.app() == PhoneApps.App.HOME) interacting = false;
        else { interacting = true; apps.home(); }
        movement.clear(); cancelDrags(); clearGameInput(); syncMouseCapture();
    }

    private void swipeHand(DrawContext ctx) {
        if (client.player == null || swipeAt == 0) return;
        float t = PhoneSwipeMotion.time(animationNow,swipeAt);
        if (t < 0 || t >= 1) return;
        boolean fromLeft = owner.hand.is("Right");
        float reach = PhoneSwipeMotion.reach(t);
        float stroke = PhoneSwipeMotion.stroke(t);
        float tipX = swipeDirection > 0 ? 139 - stroke * 75 : 64 + stroke * 75;
        float offX = fromLeft ? -90 : deviceWidth() + 90;
        tipX = offX + (tipX - offX) * reach;
        float tipY = deviceHeight() + 30 + (184 - (deviceHeight() + 30)) * reach;
        var skin = client.player.getSkin().body().texturePath();
        var m = ctx.getMatrices(); m.pushMatrix();
        try {
            m.translate(tipX, tipY); m.rotate(fromLeft ? .5f : -.5f);
            PhoneSwipeHand.draw(ctx,skin,fromLeft);
        } finally { m.popMatrix(); }
    }
    private static float smooth(float t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    private void hand(DrawContext ctx, boolean thumb) {
        if (client.player == null) return;
        var skin = client.player.getSkin().body().texturePath();
        boolean left = owner.hand.is("Left");
        boolean slim = client.player.getSkin().model() == net.minecraft.entity.player.PlayerSkinType.SLIM;
        var m = ctx.getMatrices(); m.pushMatrix();
        try {
            if (thumb) {
                m.translate(left ? -9 : deviceWidth() - 6, deviceHeight() - 138); m.rotate(left ? -.14f : .14f);
                Render2D.roundRect(ctx, -1, -1, 17, 30, 3, 0x50000000);
                dev.maro.gui.render.HeldSkin.thumb(ctx, skin, left, slim, 0, 0, 15, 28);
            } else {
                m.translate(left ? -31 : deviceWidth() - 7, deviceHeight() - 165); m.rotate(left ? .25f : -.25f);
                dev.maro.gui.render.HeldSkin.arm(ctx, skin, left, slim, 38, 170);
            }
        } finally { m.popMatrix(); }
    }

    private static void text(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        Fonts.draw(ctx, text, x, y, color, bold, size);
    }
    private static void centered(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        text(ctx, text, x - Fonts.width(text, bold, size) / 2, y, color, bold, size);
    }
    private static String time(long ms) { long s = Math.max(0, ms) / 1000; return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60; }
    private static boolean hit(float x, float y, float rx, float ry, float w, float h) { return x >= rx && x <= rx + w && y >= ry && y <= ry + h; }
    private static boolean circleHit(float x, float y, float cx, float cy, float r) { return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r; }

    private static void triangle(DrawContext ctx, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) > 0) {
            float swapX = bx, swapY = by; bx = cx; by = cy; cx = swapX; cy = swapY;
        }
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect((int) Math.min(ax, Math.min(bx, cx)) - 1, (int) Math.min(ay, Math.min(by, cy)) - 1,
            (int) (Math.max(ax, Math.max(bx, cx)) - Math.min(ax, Math.min(bx, cx))) + 2,
            (int) (Math.max(ay, Math.max(by, cy)) - Math.min(ay, Math.min(by, cy))) + 2).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,
            new float[]{ax,ay,0,0, bx,by,0,0, cx,cy,0,0, ax,ay,0,0}, new int[]{color,color,color,color},
            RenderPipelines.GUI, TextureSetup.empty(), bounds));
    }
    private void skip(DrawContext ctx, float x, float mx, float my, boolean next) {
        boolean hover = circleHit(mx, my, x, 350, 19);
        float pulse=lastControl.equals(next?"next":"previous")
            ? Math.max(0,1-(animationNow-lastControlAt)/220_000_000f):0;
        if(pulse>0)Render2D.circle(ctx,x,350,19+pulse*2,ColorUtil.withAlpha(GREEN,(int)(pulse*95)));
        if (hover) Render2D.circle(ctx, x, 350, 19, 0xFF2D4134);
        int color = hover ? WHITE : 0xFFCAD3CD;
        float direction = next ? 1 : -1;
        triangle(ctx, x - 5 * direction, 343, x - 5 * direction, 357, x + 4 * direction, 350, color);
        Render2D.roundRect(ctx, x + (next ? 6 : -8), 343, 2, 14, .5f, color);
    }

    private void control(String action) {
        if (!player.state().available()) return;
        long now = System.nanoTime();
        if (lastControlAt != 0 && now - lastControlAt < 90_000_000L) return;
        lastControlAt = now;
        lastControl=action;
        if (action.equals("next") || action.equals("previous")) {
            requestedDirection=action.equals("next")?1:-1;
            swipeDirection=requestedDirection;swipeAt=now;requestedSwipeAt=now;
        }
        player.control(action);
    }

    @Override public boolean mouseClicked(Click event, boolean doubleClick) {
        if (owner.getBind().matches(dev.maro.util.KeyUtil.mouse(event.button()))) { close(); return true; }
        if (closingAt != 0) return true;
        if (allowsMouseLook()) {
            if (mediaShortcut(dev.maro.util.KeyUtil.mouse(event.button()))) return true;
            gameMouse(event, true); movement.mouse(event, true); return true;
        }
        var l = layout(); float x = l.localX(event.x(), event.y()), y = l.localY(event.x(), event.y());
        if (allowsMovement() && !hit(x, y, -5, -5, l.width + 10, l.height + 10)) {
            movement.mouse(event, true);
            if (event.button() == 1) { looking = true; return true; }
        }
        if (event.button() != 0) return true;
        if (hit(x, y, l.width / 2 - 56, 27, 112, 20)) { interact(); return true; }
        if (hit(x, y, l.width - 38, apps.music() ? 63 : 44, 24, 24)
            || hit(x, y, l.width / 2 - 37, l.height - 16, 74, 12)) { close(); return true; }
        if (apps.click(x, y)) { if (apps.typing()) {movement.clear();clearGameInput();} syncMouseCapture(); return true; }
        if (tablet() && x < 200) return true;
        x = controlsX(x); y = controlsY(y);
        if (hit(x, y, 85, 426, 22, 22) || hit(x, y, 162, 426, 22, 22)) {
            owner.size.set(owner.size.get() + (x < 120 ? -.05 : .05)); sizeChanged = true; return true;
        }
        if (hit(x, y, 20, 403, 25, 21)) { toggleMute(); return true; }
        if (hit(x, y, 48, 402, 140, 22)) {
            if (player.volume().available()) { draggingVolume = true; volumeAt(x); }
            return true;
        }
        var state = player.state();
        if (!state.available()) {
            if (hit(x, y, 24, 337, 160, 27)) { player.openSpotify(); return true; }
            return true;
        }
        if (hit(x, y, 20, 298, 168, 19) && state.canSeek() && state.durationMs() > 0) {
            dragging = true; scrubTrack = SpotifyMedia.artworkKey(state); scrub(x); return true;
        }
        if (circleHit(x, y, 53, 350, 19)) control("previous");
        else if (circleHit(x, y, 104, 350, 22)) control("toggle");
        else if (circleHit(x, y, 155, 350, 19)) control("next");
        return true;
    }

    private void scrub(float x) {
        scrubPosition = Math.round(Math.clamp((x - 24) / 160, 0, 1) * player.state().durationMs());
    }
    @Override public boolean mouseDragged(Click event, double dx, double dy) {
        if (closingAt != 0 || !client.isWindowFocused()) { cancelDrags(); return true; }
        if (allowsMouseLook()) return true;
        if (event.button()==0 && apps.drag(layout().localX(event.x(),event.y()),layout().localY(event.x(),event.y())))return true;
        if (draggingVolume && event.button() == 0) { volumeAt(controlsX(layout().localX(event.x(), event.y()))); return true; }
        if (dragging && event.button() == 0) { scrub(controlsX(layout().localX(event.x(), event.y()))); return true; }
        if (looking && !owner.mouseLook.get() && event.button() == 1 && allowsMovement() && client.player != null
            && !dev.maro.nathan.modules.FreeCam.active()) {
            double sensitivity = client.options.getMouseSensitivity().getValue() * .6 + .2;
            double factor = sensitivity * sensitivity * sensitivity * 8 * client.getWindow().getScaleFactor();
            client.player.changeLookDirection(dx * factor * (client.options.getInvertMouseX().getValue() ? -1 : 1),
                dy * factor * (client.options.getInvertMouseY().getValue() ? -1 : 1));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(Click event) {
        mediaHeld.remove(dev.maro.util.KeyUtil.mouse(event.button()));
        gameMouse(event, false);
        movement.mouse(event, false);
        if(event.button()==0 && apps.release(layout().localX(event.x(),event.y()),layout().localY(event.x(),event.y())))return true;
        if (event.button() == 1) { looking = false; return true; }
        if (draggingVolume && event.button() == 0) {
            if (client.isWindowFocused()) volumeAt(controlsX(layout().localX(event.x(), event.y())));
            draggingVolume = false; return true;
        }
        if (dragging && event.button() == 0) {
            scrub(controlsX(layout().localX(event.x(), event.y()))); dragging = false;
            var state = player.state();
            if (client.isWindowFocused() && state.available() && state.canSeek() && scrubTrack.equals(SpotifyMedia.artworkKey(state))) player.seekTo(scrubPosition);
            return true;
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (allowsMouseLook()) {
            // With the cursor locked to the game, video navigation uses the center of the phone.
            // Video feeds own the wheel even while mining/use remain enabled. Number keys still select slots.
            if (apps.videos() && closingAt == 0) {
                var l = layout(); apps.scroll(l.width / 2, l.height * .6f, vertical); return true;
            }
            if (allowsGameInteraction() && Double.isFinite(vertical) && vertical != 0)
                client.player.getInventory().setSelectedSlot(Math.floorMod(client.player.getInventory().getSelectedSlot() - (int)Math.signum(vertical), 9));
            return true;
        }
        var l = layout();
        if (!apps.music()) {
            if (hit(l.localX(mx, my), l.localY(mx, my), 6, 74, l.width - 12, l.height - 94)) apps.scroll(l.localX(mx,my),l.localY(mx,my),vertical);
            return true;
        }
        if (closingAt == 0 && hit(controlsX(l.localX(mx, my)), controlsY(l.localY(mx, my)), 20, 390, 168, 34)
            && player.volume().available() && Double.isFinite(vertical)) {
            setVolume(player.volume().level() + vertical * .02, false);
        }
        return true;
    }
    private void volumeAt(float x) { setVolume((x - 52) / 132.0, false); }
    private void setVolume(double level, boolean muted) {
        if (!Double.isFinite(level) || !player.volume().available()) { draggingVolume = false; return; }
        double bounded = Math.clamp(level, 0, 1);
        if (bounded > .001 && !muted) lastAudibleVolume = bounded;
        player.setVolume(bounded, muted);
    }
    private void toggleMute() {
        var v = player.volume();
        if (!v.available()) return;
        if (v.muted() || v.level() <= .001) setVolume(v.level() > .001 ? v.level() : lastAudibleVolume, false);
        else { lastAudibleVolume = v.level(); setVolume(v.level(), true); }
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (owner.getBind().matches(input.key())) { close(); return true; }
        if (apps.key(input)) { movement.clear(); syncMouseCapture(); return true; }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (altKey(input.key(), true)) return true;
        if (owner.interactBind.matches(input.key())) {
            interacting = !interacting; if (interacting) apps.home();
            movement.clear(); cancelDrags(); clearGameInput(); syncMouseCapture(); return true;
        }
        if (closingAt != 0) { if (allowsMovement()) movement.key(input, true); return true; }
        if (apps.music() && input.key() == GLFW.GLFW_KEY_SPACE && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (mediaHeld.add(input.key())) control("toggle"); return true;
        }
        if (gameKey(input, true)) return true;
        if (allowsMovement() && movement.key(input, true)) return true;
        if (mediaShortcut(input.key())) return true;
        if (!apps.music()) return true;
        switch (input.key()) {
            case GLFW.GLFW_KEY_SPACE -> control("toggle");
            default -> { return true; }
        }
        return true;
    }
    @Override public boolean charTyped(CharInput input) { return apps.character(input.codepoint()); }
    @Override public boolean keyReleased(KeyInput input) {
        mediaHeld.remove(input.key());
        gameKey(input, false);
        movement.key(input, false);
        if(apps.keyReleased(input))return true;
        altKey(input.key(), false);
        return true;
    }
    private boolean altKey(int key, boolean down) {
        if (key == GLFW.GLFW_KEY_LEFT_ALT) leftAlt = down;
        else if (key == GLFW.GLFW_KEY_RIGHT_ALT) rightAlt = down;
        else return false;
        cancelDrags();
        clearGameInput();
        syncMouseCapture();
        return true;
    }
    public boolean allowsMouseLook() {
        return owner.mouseLook.get() && !interacting && !apps.typing() && !leftAlt && !rightAlt && !removed && client != null
            && client.isWindowFocused() && client.player != null && client.world != null
            && client.getOverlay() == null && !dev.maro.nathan.modules.FreeCam.active();
    }
    private void syncMouseCapture() {
        if (client == null || client.currentScreen != this) return;
        if (allowsMouseLook()) {
            if (!client.mouse.isCursorLocked()) client.mouse.lockCursor();
        } else if (client.mouse.isCursorLocked()) client.mouse.unlockCursor();
    }
    public boolean allowsMovement() { return owner.moveWhileOpen.get() && !apps.typing() && !removed; }
    public boolean allowsGameInteraction() { return owner.gameInteraction.get() && allowsMouseLook() && closingAt == 0 && client.interactionManager != null; }

    private boolean mediaShortcut(int code) {
        Runnable action;
        if (owner.previousBind.matches(code)) action = () -> control("previous");
        else if (owner.nextBind.matches(code)) action = () -> control("next");
        else if (owner.playPauseBind.matches(code)) action = () -> control("toggle");
        else if (owner.volumeUpBind.matches(code)) action = () -> setVolume(player.volume().level() + .05, false);
        else if (owner.volumeDownBind.matches(code)) action = () -> setVolume(player.volume().level() - .05, false);
        else if (owner.muteBind.matches(code)) action = this::toggleMute;
        else return false;
        if (mediaHeld.add(code)) action.run();
        return true;
    }

    private boolean gameKey(KeyInput input, boolean down) {
        if (down && !allowsGameInteraction()) return false;
        if (client.options.attackKey.matchesKey(input)) return gameInput(true, down);
        if (client.options.useKey.matchesKey(input)) return gameInput(false, down);
        if (down) for (int i = 0; i < 9; i++) if (client.options.hotbarKeys[i].matchesKey(input)) {
            client.player.getInventory().setSelectedSlot(i); return true;
        }
        return false;
    }
    private void gameMouse(Click click, boolean down) {
        if (client.options.attackKey.matchesMouse(click)) gameInput(true, down);
        if (client.options.useKey.matchesMouse(click)) gameInput(false, down);
    }
    private boolean gameInput(boolean attack, boolean down) {
        if (down && !allowsGameInteraction()) return false;
        var access = (dev.maro.mixin.MinecraftClientAccessor)client;
        if (attack) {
            // Vanilla suppresses attacks with a 10000-tick counter while any screen is open.
            if (down && !attackHeld) { access.maro$setAttackCooldown(0); access.maro$attack(); }
            if (!down && attackHeld && client.player != null && client.interactionManager != null) access.maro$breakBlock(false);
            if (attackHeld || down) client.options.attackKey.setPressed(down);
            attackHeld = down;
        } else {
            if (down && !useHeld && access.maro$getItemUseCooldown() == 0) access.maro$useItem();
            if (!down && useHeld && client.player != null && client.interactionManager != null && client.player.isUsingItem())
                client.interactionManager.stopUsingItem(client.player);
            if (useHeld || down) client.options.useKey.setPressed(down);
            useHeld = down;
        }
        return true;
    }
    private void clearGameInput() {
        if (attackHeld) gameInput(true, false);
        if (useHeld) gameInput(false, false);
    }
    public PlayerInput movementInput() { return allowsMovement() ? movement.read() : PlayerInput.DEFAULT; }
    private void cancelDrags() { dragging = false; draggingVolume = false; looking = false; }
    @Override public void close() {
        if (closingAt != 0 || removed) return;
        closingFrom = pocketProgress(); closingAt = System.nanoTime(); cancelDrags(); clearGameInput(); mediaHeld.clear();
    }
    @Override public void tick() {
        if (client.player != null) network.playerName(client.player.getName().getString());
        if (!client.isWindowFocused()) { movement.clear(); cancelDrags(); clearGameInput(); mediaHeld.clear(); leftAlt = rightAlt = false; }
        if (!allowsGameInteraction()) clearGameInput();
        else {
            var access = (dev.maro.mixin.MinecraftClientAccessor)client;
            if (attackHeld) {
                client.options.attackKey.setPressed(true);
                access.maro$setAttackCooldown(0); access.maro$breakBlock(true);
            }
            if (useHeld) {
                client.options.useKey.setPressed(true);
                if (!client.player.isUsingItem() && access.maro$getItemUseCooldown() == 0) access.maro$useItem();
            }
        }
        syncMouseCapture();
        if (client.player == null || client.world == null
            || closingAt != 0 && System.nanoTime() - closingAt >= 240_000_000L) {
            if (client.currentScreen == this) client.setScreen(null);
        }
    }
    @Override public boolean shouldPause() { return false; }
    /** A private file browser temporarily replaces the screen without ending this phone session. */
    void suspendForPicker(Screen picker) {
        pickerSuspended=true;suspendedPicker=picker;cancelDrags();clearGameInput();mediaHeld.clear();movement.clear();
        if(client.mouse.isCursorLocked())client.mouse.unlockCursor();
    }
    void returnFromPicker(boolean returning) {
        pickerSuspended=false;suspendedPicker=null;
        if(!returning)removed();
    }
    /** Module toggles must also dispose a temporarily suspended phone, never revive it on Cancel. */
    public void cancelFilePicker() {
        if(!pickerSuspended)return;
        if(client.currentScreen==suspendedPicker)client.setScreen(null);
        else{pickerSuspended=false;suspendedPicker=null;removed();}
    }
    @Override public void removed() {
        if(pickerSuspended)return;
        if (removed) return;
        removed = true; cancelDrags(); clearGameInput(); mediaHeld.clear(); movement.clear(); apps.close();
        if (client.mouse.isCursorLocked()) client.mouse.unlockCursor();
        if (cover != null) { cover.close(); cover = null; }
        if (previousCover != null) { previousCover.close(); previousCover = null; }
        if (artworkJob != null) { artworkJob.cancel(false); artworkJob = null; }
        requestedArtwork = null;
        shownArtwork = null;
        owner.screenClosed(this);
        if ((sizeChanged || apps.settingsChanged()) && dev.maro.config.ClientSettings.autoSave.get())
            dev.maro.config.ConfigManager.save(dev.maro.config.ConfigManager.getCurrent());
        super.removed();
    }
}
