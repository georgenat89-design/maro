package dev.maro.module.impl.visuals;

import dev.maro.config.FriendManager;
import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.character.*;
import dev.maro.render.character.kit.CharacterDesign;
import dev.maro.render.character.kit.Detail;
import dev.maro.setting.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Anubis character geometry and animation, integrated with Maro's settings and render states. */
public final class CustomModel extends Module {
    private static CustomModel instance;
    private final CharacterChoice self = add(new CharacterChoice("Your Model", "Character you wear", "SpongeBob"));
    private final CharacterChoice others = add(new CharacterChoice("Other Model", "Character other selected players wear", "None"));
    private final ModeSetting targets = add(new ModeSetting("Other Players", "Who gets the other model", "Friends", "Friends", "Everyone"));
    private final ModeSetting detail = add(new ModeSetting("Detail", "High preserves faces and silhouettes; lower levels reduce rendering cost", "High", "Low", "Medium", "High").onChange(v -> prepare()));
    private final NumberSetting size = add(new NumberSetting("Model Size", "Scale the character and its held weapon together; updates live", 100, 50, 200, 5).suffix("%"));
    private final BooleanSetting heldItems = add(new BooleanSetting("Held Items", "Show items in the character's hands", true));
    private final BooleanSetting firstPersonArms = add(new BooleanSetting("First Person Arms", "Use the character's arms when it supports them", true));
    private final BooleanSetting outlines = add(new BooleanSetting("Outlines", "Optional ink outlines; off gives the cleanest edges", false));
    private Object world;
    private CompletableFuture<?> scan;

    public CustomModel() {
        super("Custom Model", "Animated 3D characters, a live wardrobe, and your own GLB models. Visible in your client.", Category.VISUALS);
        instance = this;
        self.onChange(v -> prepare()); others.onChange(v -> prepare());
        add(new ButtonSetting("Wardrobe", "Browse characters with a live preview", "Open", () -> mc.setScreen(new CustomModelScreen(mc.currentScreen, this))));
        add(new ButtonSetting("Characters Folder", "Put GLB files in config/maro/characters", "Open", () -> {
            try { Files.createDirectories(CustomCharacters.folder()); Util.getOperatingSystem().open(CustomCharacters.folder()); }
            catch (java.io.IOException e) { dev.maro.Maro.LOGGER.warn("Could not create character folder", e); }
        }));
        add(new ButtonSetting("Reload Characters", "Rescan your GLB files in the background", "Reload", this::reloadCharacters));
    }

    @Override public Screen panel(Screen parent) { return new CustomModelScreen(parent, this); }
    public List<String> choices() { return self.getModes(); }
    public String selected(boolean other) { return (other ? others : self).get(); }
    public void select(boolean other, String name) { (other ? others : self).set(name); prepare(); }
    public void cycleDetail() { detail.cycle(1); }
    public String detailLabel() { return detail.get(); }
    public static float sizeFactor() { return instance==null?1:instance.size.getFloat()/100f; }
    public String sizeLabel() { return size.format(); }
    public void resize(int steps) { size.set(size.get()+steps*5); }
    public void resetSize() { size.reset(); }
    public boolean heldItems() { return heldItems.get(); }
    public void toggleHeldItems() { heldItems.set(!heldItems.get()); }
    public boolean arms() { return firstPersonArms.get(); }
    public void toggleArms() { firstPersonArms.set(!firstPersonArms.get()); }
    public static boolean outlines() { return instance != null && instance.outlines.get(); }
    public void toggleOutlines() { outlines.set(!outlines.get()); }
    public String targetLabel() { return targets.get(); }
    public void cycleTargets() { targets.cycle(1); }

    public void reloadCharacters() {
        if (scan != null && !scan.isDone()) return;
        scan = CompletableFuture.runAsync(CharacterCatalog::refresh).thenRun(() -> mc.execute(() -> {
            CharacterCache.retryFailures(); prepare();
        }));
    }

    @Override protected void onEnable() {
        CharacterCache.listen(); world = mc.world; CharacterMotion.clear(); prepare(); reloadCharacters();
    }
    @Override protected void onDisable() { CharacterMotion.clear(); CharacterCache.clear(); }
    @Override public void onTick() {
        if (world != mc.world) { world = mc.world; CharacterMotion.clear(); CharacterCache.releaseTextures(); }
    }
    private void prepare() {
        if (!isEnabled() && !(mc.currentScreen instanceof CustomModelScreen)) return;
        CharacterCache.listen();
        for (String name : new String[]{self.get(), others.get()}) {
            CharacterDesign design = CharacterCatalog.byName(name);
            if (design != null) CharacterCache.prepare(design, Detail.byLabel(detail.get()));
        }
    }
    public WornCharacter ready(String name) {
        CharacterDesign design = CharacterCatalog.byName(name);
        return design == null ? null : CharacterCache.ready(design, Detail.byLabel(detail.get()));
    }
    public static void extract(Entity entity, PlayerEntityRenderState state) {
        CharacterHolder holder = (CharacterHolder) state;
        holder.maro$setCharacter(null); holder.maro$setSeat(0);
        motion(entity, state, holder.maro$motion());
        CustomModel m = instance;
        if (m == null || state.spectator || mc.player == null) return;
        boolean preview = mc.currentScreen instanceof CustomModelScreen screen && screen.isPreviewing();
        String name;
        if (entity == mc.player) {
            if (!m.isEnabled() && !preview) return;
            if (!preview && !mc.gameRenderer.getCamera().isThirdPerson() && mc.currentScreen == null) return;
            name = preview ? ((CustomModelScreen)mc.currentScreen).previewChoice() : m.self.get();
        } else {
            if (!m.isEnabled() || !(entity instanceof PlayerEntity player)) return;
            if (m.targets.is("Friends") && !FriendManager.isFriend(player.getGameProfile().name())) return;
            name = m.others.get();
        }
        WornCharacter worn = m.ready(name);
        holder.maro$setCharacter(worn);
        Entity vehicle = entity.getVehicle();
        holder.maro$setSeat(vehicle == null ? 0 : vehicle instanceof net.minecraft.entity.LivingEntity ? 1.35f : .3f);
        if (worn != null && state.nameLabelPos != null) {
            float renderedHeight=worn.model.maxY*worn.model.scale*sizeFactor()/16f*state.baseScale;
            float standingHeight=entity.getDimensions(net.minecraft.entity.EntityPose.STANDING).height();
            state.nameLabelPos=state.nameLabelPos.add(0,renderedHeight-standingHeight,0);
        }
    }
    /** How the entity moved over its last tick, along and across the way its body faces. */
    private static void motion(Entity entity, PlayerEntityRenderState state, float[] m) {
        double dx = entity.getX() - entity.lastX, dy = entity.getY() - entity.lastY, dz = entity.getZ() - entity.lastZ;
        if (entity.hasVehicle()) dx = dy = dz = 0;
        double yaw = Math.toRadians(state.bodyYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        m[0] = (float) (Math.sqrt(dx * dx + dz * dz) * 20);
        m[1] = (float) (dy * 20);
        m[2] = (float) ((dx * fx + dz * fz) * 20);
        m[3] = (float) ((dx * fz - dz * fx) * 20);
        m[4] = entity.isOnGround() || entity.hasVehicle() ? 1 : 0;
        m[5] = 1;
        m[6] = entity.isSprinting() ? 1 : 0;
        m[7] = entity.isTouchingWater() ? 1 : 0;
    }

    /** Uses the same cached character and target selection as the replacement renderer. */
    public static float visualHeight(PlayerEntity player){
        CustomModel m=instance;
        if(m==null||!m.isEnabled()||player.isSpectator()||mc.player==null)return Float.NaN;
        boolean self=player==mc.player;
        if(!self&&m.targets.is("Friends")&&!FriendManager.isFriend(player.getGameProfile().name()))return Float.NaN;
        WornCharacter worn=m.ready(self?m.self.get():m.others.get());
        return worn==null?Float.NaN:worn.model.maxY*worn.model.scale*sizeFactor()/16f*player.getScale();
    }
    public static WornCharacter worn(EntityRenderState state) {
        return state instanceof CharacterHolder holder ? holder.maro$character() : null;
    }
    public static float seat(EntityRenderState state) { return state instanceof CharacterHolder holder ? holder.maro$seat() : 0; }
    public static boolean hidesModel(EntityRenderState state) { return worn(state) != null; }
    public static boolean hidesLayer(FeatureRenderer<?, ?> layer, EntityRenderState state) {
        WornCharacter worn = worn(state);
        if (worn == null || layer instanceof CharacterLayer || worn.projected) return false;
        // Incompatible player equipment/cosmetics stay out of replacement geometry.
        return !(layer instanceof HeldItemFeatureRenderer) || instance == null || !instance.heldItems.get();
    }
    public static WornCharacter firstPerson() {
        CustomModel m = instance;
        if (m == null || !m.isEnabled() || !m.firstPersonArms.get() || mc.player == null || mc.player.isSpectator()) return null;
        WornCharacter worn = m.ready(m.self.get());
        return worn != null && worn.model.firstPersonArms ? worn : null;
    }
    public static void baking(String file) { dev.maro.Maro.LOGGER.info("Preparing character {}", file); }
    public static void notice(String file, List<String> limbs) { dev.maro.Maro.LOGGER.info("Character {} animates {}", file, limbs); }
    public static void failed(CharacterDesign design, Throwable failure) { dev.maro.Maro.LOGGER.warn("Character {} could not load", design.name(), failure); }

    /** Includes newly imported models without changing the shared settings implementation. */
    private static final class CharacterChoice extends ModeSetting {
        CharacterChoice(String name, String description, String value) { super(name, description, value, "None", "SpongeBob"); }
        @Override public List<String> getModes() { var list = new ArrayList<String>(); list.add("None"); list.addAll(CharacterCatalog.names()); return List.copyOf(list); }
        @Override protected String validate(String value) { return getModes().stream().filter(n -> n.equalsIgnoreCase(value)).findFirst().orElse(null); }
        @Override public int index() { return Math.max(0, getModes().indexOf(get())); }
        @Override public void cycle(int delta) { var list = getModes(); set(list.get(Math.floorMod(index() + delta, list.size()))); }
    }
}
