package dev.maro.nathan.modules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.ProvidedStringSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.settings.StringListSetting;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.render.color.SettingColor;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.hats.HatCatalogue;

/**
 * Hats on players' heads: twenty-six of them, yours only, everyone's, or a list
 * of names.
 *
 * <p><b>It is a drawing and nothing else.</b> Nothing here writes to an entity,
 * to an equipment slot or to a packet. A hat is a model submitted while a player
 * is already being drawn, off a render layer on the player's renderer, and the
 * only things read are the render state the game had already built for that frame
 * and - to answer "is this me" - the entity's own name. Nobody else's game knows
 * about it.
 *
 * <p><b>It hangs off the head bone,</b> so it inherits head yaw and pitch from the
 * part the game already posed, and sneaking, swimming, crawling, riding and
 * sleeping all come with it.
 *
 * <p><b>Everything per player goes through the matrix.</b> The collector copies
 * the pose it is handed but keeps model parts by reference, so a part posed per
 * player would be drawn with whatever the last player left on it. Scale, height,
 * and every animation are matrix work on a whole group at a time; no part is ever
 * posed.
 *
 * <p><b>The three animations.</b> A propeller turns on <em>frame time</em> - the
 * phase is wound on by however long the last frame took, so it is the same speed
 * at 60 frames a second as at 240 and does not step with ticks. A tassel trails
 * the wearer, leaning back from how fast they are going and swinging as they move.
 * A halo turns slowly about itself and is drawn <b>emissive</b>, through
 * {@link RenderLayers#entityTranslucentEmissive} at
 * {@link LightmapTextureManager#MAX_LIGHT_COORDINATE}, so it keeps its own brightness in the dark
 * rather than going grey.
 *
 * <p><b>What is in front of the face</b> - the football helmet's guard - is not
 * drawn when the camera is inside the wearer's own head, which is first person on
 * yourself. Everything behind the face still is.
 *
 * <p><b>The sheets</b> are read from the jar, or from
 * {@code .minecraft/chefhat/<id>.png} if you have painted your own, so the art can
 * be redrawn without rebuilding. Anything loaded is handed back to the texture
 * manager when the module is switched off.
 */
public class Hats extends Module {
    /** Picked in Cosmetics' Hat tab, so not listed on its own. */
    @Override
    public boolean hiddenInGui() {
        return true;
    }

    /** Where a sheet painted by hand is looked for, under the game directory. */
    private static final String EXTERNAL = "chefhat";

    /** How fast a propeller turns, in degrees a second, and how slowly a halo does. */
    private static final double SPIN_RATE = 430;
    private static final double RING_RATE = 26;

    /**
     * How far cloth that hangs leans and swings. Kept well under a tassel's: a
     * veil that swung like a rope would look wrong, and anything past about a
     * dozen degrees stops reading as cloth settling and starts reading as wind.
     */
    private static final double DRAPE_LEAN = 10;
    private static final double DRAPE_WAG = 3.5;

    /** How far a tassel leans and swings, and the speed it reaches that at. */
    private static final double SWING_LEAN = 26;
    private static final double SWING_WAG = 9;
    private static final double SWING_RATE = 6.2;
    private static final double SPRINT = 0.26;

    public enum Target {
        SelfOnly,
        AllPlayers,
        Whitelist
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<String> hat = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("hat")
        .description("Which one. They are built by tools/hatgen.py, so the list is whatever that last wrote.")
        .defaultValue("chef_toque")
        .supplier(HatCatalogue::ids)
        .onChanged(value -> dropModel())
        .build()
    );

    private final Setting<Target> target = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("target")
        .description("Whose head it goes on.")
        .defaultValue(Target.SelfOnly)
        .build()
    );

    private final Setting<List<String>> names = sgGeneral.add(new StringListSetting.Builder()
        .name("whitelist")
        .description("The names that get one, when Target is Whitelist. Case is ignored.")
        .defaultValue(new ArrayList<>())
        .visible(() -> target.get() == Target.Whitelist)
        .build()
    );

    private final Setting<Boolean> hideWithHelmet = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-helmet-equipped")
        .description("Off the head while a helmet is on it, which is the only way the two do not clip. Off, the hat is drawn over the helmet and stood off it a little instead - which the two that enclose the head, the box and the pumpkin, will not survive tidily.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> motion = sgGeneral.add(new BoolSetting.Builder()
        .name("motion")
        .description("The propeller's spin, the tassels' swing and the halo's turn. Off holds all three still.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("How big it is. It grows about the top of the head, so it stays seated.")
        .defaultValue(1)
        .min(0.5)
        .max(2)
        .sliderRange(0.5, 2)
        .build()
    );

    private final Setting<Double> height = sgLook.add(new DoubleSetting.Builder()
        .name("height-offset")
        .description("Up or down, in head pixels, for fine-tuning where it sits.")
        .defaultValue(0)
        .min(-4)
        .max(4)
        .sliderRange(-2, 2)
        .build()
    );

    private final Setting<SettingColor> tint = sgLook.add(new ColorSetting.Builder()
        .name("tint")
        .description("A colour laid over the whole hat. White leaves the sheet as it was painted.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    /** The hat in use, baked. Dropped when the choice changes or the module stops. */
    private HatCatalogue.Baked baked;

    /** Sheets already loaded from disk, so a file is read once and given back once. */
    private final Map<String, Identifier> textures = new HashMap<>();
    private final List<Identifier> loaded = new ArrayList<>();

    /** The propeller's angle and the halo's, both wound on by the clock. */
    private double spin;
    private double turn;
    private long lastFrameAt;

    public Hats() {
        super(NameeProtectAddon.CATEGORY, "hats", "A hat on players' heads: twenty-six to pick from. Drawn on your screen only.");
    }

    @Override
    public void onDeactivate() {
        dropModel();

        // The parts are plain geometry; a sheet read off disk is a GL object and
        // has to go back.
        for (Identifier id : loaded) mc.getTextureManager().destroyTexture(id);

        loaded.clear();
        textures.clear();
        lastFrameAt = 0;
    }

    private void dropModel() {
        baked = null;
    }

    // ------------------------------------------------------------- the choice

    /** Whether this player gets one at all. */
    public boolean wants(PlayerEntityRenderState state) {
        if (mc.player == null || mc.world == null) return false;

        // A hidden HUD is someone taking a clean picture; an invisible or
        // spectating player is not being drawn in the first place.
        if (mc.options.hudHidden || state.invisible || state.spectator) return false;
        if (hideWithHelmet.get() && !state.equippedHeadStack.isEmpty()) return false;

        return switch (target.get()) {
            case SelfOnly -> state.id == mc.player.getId();
            case AllPlayers -> true;
            case Whitelist -> named(state);
        };
    }

    /** The entity is only ever read, and only to find out what it is called. */
    private boolean named(PlayerEntityRenderState state) {
        if (names.get().isEmpty()) return false;

        Entity entity = mc.world.getEntityById(state.id);
        if (entity == null) return false;

        String name = entity.getName().getString();

        for (String wanted : names.get()) {
            if (wanted.equalsIgnoreCase(name)) return true;
        }

        return false;
    }

    /**
     * Whether the camera is inside this player's own head, which is the one case
     * where anything drawn in front of the face would fill the screen.
     */
    private boolean insideHead(PlayerEntityRenderState state) {
        if (!mc.options.getPerspective().isFirstPerson()) return false;

        Entity camera = mc.getCameraEntity();

        return camera != null && camera.getId() == state.id;
    }

    // -------------------------------------------------------------- the frame

    /**
     * Draws the chosen hat for one player. The pose arrives already walked into
     * the head bone, so from here it is head model space: negative y up, sixteen
     * units to the block.
     */
    public void submit(MatrixStack pose, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state) {
        if (baked == null) {
            HatCatalogue.Spec spec = HatCatalogue.get(hat.get());

            if (spec == null) spec = HatCatalogue.get("chef_toque");
            if (spec == null) return;

            baked = HatCatalogue.bake(spec);
        }

        Identifier sheet = texture(baked.spec().id());
        boolean overHelmet = !hideWithHelmet.get() && !state.equippedHeadStack.isEmpty();
        boolean hideFront = insideHead(state);

        RenderLayer solid = overHelmet
            ? RenderLayers.entityCutoutNoCullZOffset(sheet)
            : RenderLayers.entityCutoutNoCull(sheet);
        RenderLayer lit = RenderLayers.entityTranslucentEmissive(sheet);

        SettingColor chosen = tint.get();
        int colour = chosen.a << 24 | chosen.r << 16 | chosen.g << 8 | chosen.b;

        wind();

        pose.push();

        // Grown about the head's own top face, so a bigger hat sits on the head
        // rather than over it, and stood off a little when it has to clear a
        // helmet.
        double size = scale.get() * (overHelmet ? 1.07 : 1);

        pose.translate(0, -8 / 16.0, 0);
        pose.scale((float) size, (float) size, (float) size);
        pose.translate(0, 8 / 16.0, 0);

        // Negative y is up, so a positive offset lifts it.
        pose.translate(0, -height.get() / 16.0, 0);

        for (HatCatalogue.Group group : baked.groups()) {
            boolean emissive = HatCatalogue.RING.equals(group.name());

            pose.push();
            move(pose, group, state);

            for (int i = 0; i < group.parts().size(); i++) {
                if (hideFront && group.front().get(i)) continue;

                collector.submitModelPart(group.parts().get(i), pose,
                    emissive ? lit : solid,
                    emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
                    OverlayTexture.DEFAULT_UV, null, colour, null);
            }

            pose.pop();
        }

        pose.pop();
    }

    /**
     * Winds the two running angles on by however long the last frame took.
     *
     * <p>By the clock and not by ticks, and capped so a stutter cannot throw them
     * a long way round: a propeller at 430 degrees a second turns at the same rate
     * whatever the frame rate, which is the whole point of it.
     */
    private void wind() {
        long now = System.nanoTime();
        double seconds = lastFrameAt == 0 ? 0 : Math.min((now - lastFrameAt) / 1.0e9, 0.05);
        lastFrameAt = now;

        if (!motion.get()) return;

        spin = (spin + seconds * SPIN_RATE) % 360;
        turn = (turn + seconds * RING_RATE) % 360;
    }

    /** What moves a group, about the point it is fixed at. */
    private void move(MatrixStack pose, HatCatalogue.Group group, PlayerEntityRenderState state) {
        String name = group.name();

        if (HatCatalogue.STATIC.equals(name)) return;

        float[] at = group.anchor();

        pose.translate(at[0] / 16.0, at[1] / 16.0, at[2] / 16.0);

        switch (name) {
            case HatCatalogue.SPIN -> pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) spin));
            case HatCatalogue.RING -> pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) turn));
            case HatCatalogue.DRAPE -> {
                // The same idea as a tassel and a third of the amount: cloth
                // trails the wearer and settles, rather than swinging.
                double speed = motion.get() ? Math.min(1, state.limbAmplitudeInverse / SPRINT) : 0;
                double lean = speed * DRAPE_LEAN;
                double wag = Math.sin(state.age * 0.07 + spin * 0.008) * speed * DRAPE_WAG;

                pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) -lean));
                pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) wag));
            }
            case HatCatalogue.SWING -> {
                // Leans back from how fast the wearer is going and wags across as
                // they move, both small, and both still when they are.
                double speed = motion.get() ? Math.min(1, state.limbAmplitudeInverse / SPRINT) : 0;
                double lean = speed * SWING_LEAN;
                double wag = Math.sin(state.age * 0.1 + spin * 0.01) * speed * SWING_WAG;

                pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) -lean));
                pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) wag));
            }
            default -> {
            }
        }

        pose.translate(-at[0] / 16.0, -at[1] / 16.0, -at[2] / 16.0);
    }

    /**
     * A hat's sheet: the one in {@code .minecraft/chefhat/<id>.png} if it is there
     * and the one in the jar if it is not. Each is read once, and a file that
     * cannot be read falls back rather than being tried every frame.
     */
    private Identifier texture(String id) {
        Identifier known = textures.get(id);

        if (known != null) return known;

        Path file = mc.runDirectory.toPath().resolve(EXTERNAL).resolve(id + ".png");

        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                Identifier own = Identifier.of("nameeprotect", "hats/loaded/" + id);

                mc.getTextureManager().registerTexture(own, new NativeImageBackedTexture(() -> "nameeprotect hat " + id, image));
                textures.put(id, own);
                loaded.add(own);

                return own;
            } catch (IOException | RuntimeException e) {
                NameeProtectAddon.LOG.warn("Hats could not read {}: {}", file, e.toString());
            }
        }

        Identifier bundled = Identifier.of("nameeprotect", "textures/hats/" + id + ".png");
        textures.put(id, bundled);

        return bundled;
    }
}
