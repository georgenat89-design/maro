package dev.maro.gametest;

import dev.maro.gui.render.Fonts;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Pose sheets: all bundled characters standing, running, crouching and jumping, then three emotes, and
 * the default skin doing the same emotes. Each sheet is saved whole as crop_*.png, which CI prints
 * unscaled, so the look and movement of the models can be compared between builds.
 */
final class CharacterShotChecks {
    static final String[] CHARACTERS = {"SpongeBob", "LeBron James", "Chun-Li", "Skeleton", "Jeffrey Epstein",
            "Donald Trump", "Michael Jackson", "Roblox Bacon Hair", "Verity", "Minion", "Travis Scott", "Undead Fuhrer"};
    static final Emote[] EMOTES = {Emote.WAVE, Emote.FLOSS, Emote.DEFAULT_DANCE};
    static final String[] POSES = {"Idle", "Run", "Crouch", "Jump"};

    private CharacterShotChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        CustomModel model = ModuleManager.get(CustomModel.class);
        require(model != null, "Custom Model is not registered");
        boolean wasOn = context.computeOnClient(c -> model.isEnabled());
        String wasSelf = context.computeOnClient(c -> model.selected(false));
        context.getInput().resizeWindow(1600, 900);
        try {
            context.runOnClient(c -> {
                c.options.getGuiScale().setValue(2);
                c.onResolutionChanged();
                model.setEnabled(true);
            });
            for (String name : CHARACTERS) {
                context.runOnClient(c -> model.select(false, name));
                for (int i = 0; i < 1200 && !context.computeOnClient(c -> model.ready(name) != null); i++) context.waitTicks(1);
                require(context.computeOnClient(c -> model.ready(name) != null), "Character did not load: " + name);
                sheet(context, name, name.toLowerCase().replaceAll("[^a-z]+", "-"));
            }
            context.runOnClient(c -> model.select(false, "None"));
            sheet(context, "Default skin", "default-skin");
            System.out.println("[character-shots] pose sheets saved for " + String.join(", ", CHARACTERS) + " and the default skin");
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                model.select(false, wasSelf);
                model.setEnabled(wasOn);
            });
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(3);
        }
    }

    private static void sheet(ClientGameTestContext context, String title, String slug) {
        context.runOnClient(c -> c.setScreen(new Sheet(title)));
        // Long enough for blends between poses to settle.
        context.waitTicks(40);
        Path shot = context.takeScreenshot("maro-character-" + slug);
        try {
            BufferedImage image = ImageIO.read(shot.toFile());
            if (title.equals("SpongeBob")) {
                int yellow = 0;
                for (int y = 30; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int p = image.getRGB(x, y), red = p >> 16 & 255, green = p >> 8 & 255, blue = p & 255;
                    if (red > 100 && green > 90 && blue < Math.min(red, green) * .65f) yellow++;
                }
                require(yellow > 8000, "SpongeBob's body lost its authored yellow texture: " + yellow + " pixels");
            }
            ImageIO.write(image, "png", shot.resolveSibling("crop_character-" + slug + ".png").toFile());
        } catch (IOException error) {
            throw new AssertionError(error);
        }
        context.runOnClient(c -> c.setScreen(null));
        context.waitTicks(2);
    }

    /** Asks the animation for a movement state when the build has one; older builds pose from limb swing alone. */
    private static void motion(PlayerEntityRenderState state, float speed, float vertical, boolean grounded) {
        try {
            Method m = state.getClass().getMethod("maro$setMotion", float.class, float.class, boolean.class);
            m.invoke(state, speed, vertical, grounded);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** A grid of cells, each the player drawn in one pose from three quarters. */
    static final class Sheet extends Screen {
        private final String title;
        private final PlayerEntityRenderState[] states = new PlayerEntityRenderState[7];
        private final long opened = System.nanoTime();

        Sheet(String title) {
            super(Text.literal(title));
            this.title = title;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, width, height, 0xFF1A1F2A);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
            float seconds = (System.nanoTime() - opened) / 1e9f;
            Fonts.drawCentered(ctx, title, width / 2f, 10, 0xFFFFFFFF, true, 1f);
            float cellW = width / 4f, cellH = (height - 24) / 2f;
            for (int i = 0; i < 7; i++) {
                if (states[i] == null && renderer.createRenderState() instanceof PlayerEntityRenderState created) states[i] = created;
                PlayerEntityRenderState state = states[i];
                if (state == null) continue;
                renderer.updateRenderState(mc.player, state, 1f);
                state.id = -4000 - i;
                state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                state.shadowPieces.clear();
                state.outlineColor = 0;
                state.relativeHeadYaw = 0;
                state.pitch = 0;
                state.width /= state.baseScale;
                state.height /= state.baseScale;
                state.baseScale = 1;
                state.isInSneakingPose = false;
                state.limbSwingAmplitude = 0;
                state.limbSwingAnimationProgress = 0;
                ((EmoteRenderState) state).maro$setEmote(null, 0, 0);
                String label;
                if (i < 4) {
                    label = POSES[i];
                    state.bodyYaw = 180 + 55;
                    switch (i) {
                        case 1 -> {
                            state.limbSwingAmplitude = 1;
                            state.limbAmplitudeInverse = 1;
                            state.limbSwingAnimationProgress = 2.4f + seconds * 11f;
                            motion(state, 5.6f, 0, true);
                        }
                        case 2 -> {
                            state.isInSneakingPose = true;
                            motion(state, 0, 0, true);
                        }
                        case 3 -> motion(state, 0, 4f, false);
                        default -> motion(state, 0, 0, true);
                    }
                } else {
                    Emote emote = EMOTES[i - 4];
                    label = emote.label();
                    state.bodyYaw = 180 + 20;
                    motion(state, 0, 0, true);
                    float t = emote.loops() ? 0.42f * emote.length() : 0.55f * emote.length();
                    ((EmoteRenderState) state).maro$setEmote(emote, t, 1f);
                    dev.maro.render.character.DefaultSkins.wear(state);
                }
                int col = i < 4 ? i : i - 4, row = i < 4 ? 0 : 1;
                float left = col * cellW + (row == 1 ? cellW / 2f : 0), top = 24 + row * cellH;
                Fonts.drawCentered(ctx, label, left + cellW / 2, top + 10, 0xFFB8C4D8, false, 0.9f);
                var worn = CustomModel.worn(state);
                float modelHeight = worn == null ? 1.9f : worn.model.maxY * worn.model.scale * CustomModel.sizeFactor() / 16;
                float size = Math.min(cellW / 2.4f, (cellH - 30) / Math.max(2.1f, modelHeight + .3f));
                ctx.addEntity(state, size, new Vector3f(0, modelHeight / 2, 0), new Quaternionf().rotateZ((float) Math.PI).rotateX(.12f),
                        new Quaternionf().rotateX(.12f), Math.round(left), Math.round(top + 20), Math.round(left + cellW), Math.round(top + cellH));
            }
        }
    }
}
