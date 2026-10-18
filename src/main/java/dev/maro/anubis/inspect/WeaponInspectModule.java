// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import dev.maro.anubis.CombatBridge;
import dev.maro.anubis.ClientModule;
import dev.maro.module.Category;
import dev.maro.anubis.inspect.FirstPersonArm;
import dev.maro.anubis.inspect.InspectAnimation;
import dev.maro.anubis.inspect.KnifeMaterial;
import dev.maro.anubis.inspect.KnifeMesh;
import dev.maro.anubis.inspect.KnifeSkin;
import dev.maro.anubis.inspect.SlashTrail;
import dev.maro.anubis.setting.BooleanSetting;
import dev.maro.anubis.setting.KeySetting;
import dev.maro.anubis.setting.ModeSetting;
import dev.maro.anubis.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Environment(value=EnvType.CLIENT)
public final class WeaponInspectModule
extends ClientModule {
    public static volatile WeaponInspectModule active;
    private static final String SWORDS = "Swords";
    private static final String WEAPONS = "Weapons";
    private static final String ANY_ITEM = "Any Item";
    private static final Identifier KNIFE_TEXTURE;
    private static final float EASE_OUT_SECONDS = 0.15f;
    private static final int IDLE = 0;
    private static final int INSPECT = 1;
    private static final int DEPLOY = 2;
    private static final float[] REST;
    private static final float SPARKLE_SIZE = 0.07f;
    private final KeySetting key = this.addSetting("key", new KeySetting("Inspect Key", 70));
    private static String[] skins(){var names=new java.util.ArrayList<String>();names.add("None");names.addAll(java.util.Arrays.asList(InspectionMotion.KNIVES));names.add("Katana");return names.toArray(String[]::new);}
    private final ModeSetting skin = this.addSetting("skin", new ModeSetting("Knife Skin", "Butterfly", skins()));
    private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Classic", WeaponInspectionStyles.STYLES));
    private final ModeSetting items = this.addSetting("items", new ModeSetting("Items", "Swords", "Swords", "Weapons", "Any Item"));
    private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 1.0f, 0.5f, 2.0f).step(0.05f));
    private final NumberSetting knifeSize = this.addSetting("knife-size", new NumberSetting("Knife Size", 1.0f, 0.5f, 2.0f).step(0.05f));
    private final BooleanSetting showArm = this.addSetting("show-arm", new BooleanSetting("Show Arm", true));
    private final ModeSetting handStyle=this.addSetting("hand-style",new ModeSetting("Hand Style","Tactical Gloves","Tactical Gloves","Your Skin"));
    private final ModeSetting finish=this.addSetting("finish",new ModeSetting("Finish","Satin Steel","Satin Steel","Damascus","Black Cerakote","Maro Blue","Item Material"));
    private final BooleanSetting deploy = this.addSetting("deploy", new BooleanSetting("Deploy Animation", true));
    private final BooleanSetting slashTrail = this.addSetting("slash-trail", new BooleanSetting("Slash Trail", true));
    private final NumberSetting trailLength = this.addSetting("trail-length", new NumberSetting("Trail Length", 0.14f, 0.05f, 0.35f).step(0.01f));
    private final BooleanSetting sparkles = this.addSetting("sparkles", new BooleanSetting("Blade Sparkle", true));
    private final BooleanSetting sneakSkips = this.addSetting("sneak-skips", new BooleanSetting("Sneak Skips Inspect", true));
    private int mode = 0;
    private InspectAnimation.Clip clip;
    private KnifeSkin clipKnife;
    private long startedAt;
    private float seconds;
    private boolean easingOut;
    private long easeStartedAt;
    private float easeFrom;
    private int slot;
    private Item item;
    private int heldSlot = -1;
    private Item heldItem;
    private final float[] state = new float[16];
    private final float[] trailSample = new float[12];
    private final SlashTrail[] trails = new SlashTrail[2];
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f baseInverse = new Matrix4f();
    private final Matrix3f baseNormal = new Matrix3f();
    private boolean baseKnown;
    private int styleCycle;
    private String currentStyle="Classic";

    public WeaponInspectModule() {
        super("weapon-inspect", "Weapon Inspect", "Knife skins for your sword and CS-style inspects with a key of your choice (F by default); hold the key to keep the knife spinning.", Category.VISUALS);
        for (int i = 0; i < this.trails.length; ++i) {
            this.trails[i] = new SlashTrail();
        }
        this.knifeSize.visibleWhen(() -> !this.skin.is("None"));
        this.deploy.visibleWhen(() -> !this.skin.is("None"));
        this.sparkles.visibleWhen(() -> !this.skin.is("None"));
        this.trailLength.visibleWhen(this.slashTrail::get);
    }

    @Override public java.util.List<dev.maro.setting.SettingSection> getSettingSections() {
        return java.util.List.of(section("Inspect",key,items,style,speed,sneakSkips),section("Knife",skin,knifeSize,finish,showArm,handStyle,deploy),
            section("Effects",slashTrail,trailLength,sparkles));
    }
    public boolean inspecting() { return mode==INSPECT; }
    public String inspectionStyle() { return currentStyle; }
    public static boolean renderHand(MatrixStack pose,OrderedRenderCommandQueue queue,int light,ItemStack stack,boolean left,float equip,float swing){
        var m=active;
        if(m==null||m.skin.is("None")||!isSword(stack)||dev.maro.module.impl.visuals.ItemInspect.handles(stack))return false;
        long now=System.nanoTime();
        float weight=m.animate(stack,KnifeSkin.of(m.skin.get()),now);
        float t=m.mode!=IDLE?(m.easingOut?m.easeFrom:m.loopedProgress(now)):0;
        boolean inspecting=m.mode==INSPECT&&weight>0&&t<1;
        InspectionMotion.Pose at=InspectionMotion.Pose.REST;
        if(inspecting){
            if(m.currentStyle.equals("Classic"))at=InspectionMotion.sample(m.skin.is("Katana")?"Bayonet":m.skin.get(),false,t);
            else {
                m.clip.sample(t,m.state);
                at=new InspectionMotion.Pose(m.state[0]*.6f,m.state[1]*.6f,m.state[2]*.5f,m.state[4]*.8f,m.state[3]*.75f,
                    m.state[5]*.75f,m.state[6]+m.state[7],0,0);
            }
            if(weight<1)at=new InspectionMotion.Pose(at.x()*weight,at.y()*weight,at.z()*weight,at.pitch()*weight,at.yaw()*weight,
                at.roll()*weight,net.minecraft.util.math.MathHelper.wrapDegrees(at.spin())*weight,at.grip()*weight,at.air()*weight);
        }
        if(m.mode==DEPLOY&&t<1)at=new InspectionMotion.Pose(0,-.14f*(1-t)*(1-t),0,0,0,-18*(1-t)*(1-t),0,0,0);
        InspectionKnifeRenderer.draw(pose,queue,light,stack,m.skin.get(),at,t,inspecting&&m.currentStyle.equals("Classic"),false,left,
            m.showArm.get(),m.knifeSize.get(),equip,swing,m.finish.get(),.85f,m.handStyle.is("Tactical Gloves"),m.baseKnown?m.trailSample:null);
        if(m.baseKnown){
            if(m.slashTrail.get())m.record(now,1);else m.clearTrails();
            if(m.slashTrail.get()||m.sparkles.get())m.submitGlow(queue,now,KnifeMaterial.of(stack).glow,
                m.sparkles.get()&&inspecting?(float)Math.pow(Math.max(0,Math.sin(Math.PI*t)),16)*.45f:0,null);
        }
        return true;
    }

    @Override
    protected void onEnable() {
        this.mode = 0;
        this.heldItem = null;
        this.clearTrails();
        active = this;

    }

    @Override
    protected void onDisable() {
        active = null;
        this.mode = 0;
        this.clearTrails();
    }

    public static void handsBegin(MatrixStack pose) {
        WeaponInspectModule module = active;
        if (module == null) {
            return;
        }
        module.base.set((Matrix4fc)pose.peek().getPositionMatrix());
        module.base.invert(module.baseInverse);
        module.baseNormal.set((Matrix3fc)pose.peek().getNormalMatrix());
        module.baseKnown = true;
    }

    public static boolean renderItem(MatrixStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, OrderedRenderCommandQueue collector, int light, boolean mainHand) {
        if(dev.maro.module.impl.visuals.CustomPickaxe.handles(stack))return false;
        if (mainHand && dev.maro.module.impl.visuals.ItemInspect.handles(stack)) return false;
        WeaponInspectModule module = active;
        return module != null && module.render(pose, holder, stack, context, collector, light, mainHand);
    }

    public static boolean input(int code, int action) {
        return active != null && action != 0 && active.pressed(code, action == 2);
    }

    private boolean pressed(int pressed, boolean repeat) {
        int bound = this.key.value();
        if (!CombatBridge.isBindable(bound) || pressed != bound) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (mc.currentScreen != null || player == null || player.isSpectator()) {
            return false;
        }
        if (this.sneakSkips.get() && player.isSneaking()) {
            return false;
        }
        ItemStack held = player.getMainHandStack();
        if (!this.inspectable(held)) {
            return false;
        }
        if (!repeat && !player.isUsingItem()) {
            this.start(1, player.getInventory().getSelectedSlot(), held);
        }
        return true;
    }

    private boolean held() {
        int bound = this.key.value();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!CombatBridge.isBindable(bound) || mc.currentScreen != null) {
            return false;
        }
        return bound >= 1000 ? GLFW.glfwGetMouseButton((long)mc.getWindow().getHandle(), (int)(bound - 1000)) == 1 : CombatBridge.isKeyDown(mc.getWindow(), bound);
    }

    private boolean inspectable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (this.items.is(ANY_ITEM) || WeaponInspectModule.isSword(stack)) {
            return true;
        }
        return this.items.is(WEAPONS) && (stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SPEARS) || stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT));
    }

    private static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.isIn(ItemTags.SWORDS) || Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword"));
    }

    private static boolean handheld(ItemStack stack) {
        return WeaponInspectModule.isSword(stack) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES);
    }

    private void start(int next, int selected, ItemStack held) {
        KnifeSkin knife;
        KnifeSkin knifeSkin = knife = WeaponInspectModule.isSword(held) ? KnifeSkin.of(this.skin.get()) : null;
        if(next==INSPECT)currentStyle=style.is("Cycle") ? WeaponInspectionStyles.STYLES[Math.floorMod(styleCycle++,WeaponInspectionStyles.STYLES.length-1)] : style.get();
        this.clip = next == DEPLOY && knife!=null ? knife.deploy
            : knife!=null && currentStyle.equals("Classic") ? knife.inspect : WeaponInspectionStyles.clip(currentStyle,knife!=null);
        this.clipKnife = knife;
        this.mode = next;
        this.seconds = next==DEPLOY?.85f:this.clip.seconds;
        this.easingOut = false;
        this.startedAt = System.nanoTime();
        this.slot = selected;
        this.item = held.getItem();
    }

    private void easeOut() {
        long now = System.nanoTime();
        this.easeFrom = Math.min(this.progress(now), 1.0f);
        this.easeStartedAt = now;
        this.easingOut = true;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            this.mode = 0;
            this.heldItem = null;
            return;
        }
        ItemStack held = player.getMainHandStack();
        int selected = player.getInventory().getSelectedSlot();
        if (selected != this.heldSlot || !held.isOf(this.heldItem)) {
            this.heldSlot = selected;
            this.heldItem = held.getItem();
            if (this.deploy.get() && WeaponInspectModule.isSword(held) && !skin.is("None")) {
                this.start(2, selected, held);
            } else if (this.mode != 0 && !this.easingOut) {
                this.easeOut();
            }
            return;
        }
        if (this.mode == 0 || this.easingOut) {
            return;
        }
        if (player.handSwinging) {
            this.mode = 0;
        } else if (player.isUsingItem()) {
            this.easeOut();
        } else if (this.loopedProgress(System.nanoTime()) >= 1.0f) {
            this.mode = 0;
        }
    }

    private float progress(long now) {
        return (float)(now - this.startedAt) / 1.0E9f / (this.seconds / this.speed.get());
    }

    private float loopedProgress(long now) {
        float progress = this.progress(now);
        if (this.mode != 1 || !this.clip.loops() || progress < this.clip.loopExit || !this.held()) {
            return progress;
        }
        float duration = this.seconds / this.speed.get();
        long cycle = (long)((this.clip.loopExit - this.clip.loopEnter) * duration * 1.0E9f);
        if (cycle <= 0L) {
            return progress;
        }
        long over = (long)((progress - this.clip.loopExit) * duration * 1.0E9f);
        this.startedAt += (over / cycle + 1L) * cycle;
        return this.progress(now);
    }

    private float animate(ItemStack stack, KnifeSkin knife, long now) {
        float progress;
        if (this.mode == 0 || !stack.isOf(this.item)) {
            return 0.0f;
        }
        if (knife != this.clipKnife) {
            this.mode = 0;
            return 0.0f;
        }
        float weight = 1.0f;
        if (this.easingOut) {
            float t = (float)(now - this.easeStartedAt) / 1.0E9f / 0.15f;
            if (t >= 1.0f) {
                this.mode = 0;
                return 0.0f;
            }
            progress = this.easeFrom;
            weight = 1.0f - t * t * (3.0f - 2.0f * t);
        } else {
            progress = this.loopedProgress(now);
            if (progress >= 1.0f) {
                this.mode = 0;
                return 0.0f;
            }
        }
        this.clip.sample(progress, this.state);
        return weight;
    }

    private boolean render(MatrixStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, OrderedRenderCommandQueue collector, int light, boolean mainHand) {
        boolean trailing;
        int side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;
        boolean sword = WeaponInspectModule.isSword(stack);
        KnifeSkin knife = sword ? KnifeSkin.of(this.skin.get()) : null;
        long now = System.nanoTime();
        float weight = mainHand ? this.animate(stack, knife, now) : 0.0f;
        float[] at = weight > 0.0f ? this.state : REST;
        boolean bl = trailing = mainHand && sword && this.slashTrail.get() && this.baseKnown;
        if (mainHand && !trailing) {
            this.clearTrails();
        }
        if (knife == null) {
            FirstPersonArm.Arm arm;
            if ((this.showArm.get() && WeaponInspectModule.handheld(stack) || false) && (arm = FirstPersonArm.arm()) != null) {
                FirstPersonArm.hold(pose, arm, stack, context, holder, collector, light, at[0] * weight, at[1] * weight, at[2] * weight);
            }
            if (weight > 0.0f) {
                InspectAnimation.place(pose, at, weight, side, 0.07f, 0.2f, 0.07f);
            }
            if (trailing) {
                this.swordTrail(pose.peek().getPositionMatrix(), side);
                this.record(now, 1);
                this.submitGlow(collector, now, KnifeMaterial.of((ItemStack)stack).glow, 0.0f, null);
            }
            return false;
        }
        KnifeMaterial material = KnifeMaterial.of(stack);
        FirstPersonArm.Arm arm = this.showArm.get() || false ? FirstPersonArm.arm() : null;
        boolean slim = arm != null && arm.slim();
        pose.push();
        if (side < 0) {
            pose.scale(-1.0f, 1.0f, 1.0f);
        }
        knife.draw(pose, at, weight, slim, this.knifeSize.get(), WeaponInspectModule.sink(collector, light, side, arm, mainHand && WeaponInspectModule.offHandFree(), material, stack.hasGlint()), trailing ? this.trailSample : null);
        pose.pop();
        if (trailing) {
            this.record(now, knife.streakCount());
            float flash = this.sparkles.get() && weight > 0.0f ? knife.sparkle(at, weight) : 0.0f;
            this.submitGlow(collector, now, material.glow, flash, knife);
        }
        return true;
    }

    private static KnifeSkin.Sink sink(final OrderedRenderCommandQueue collector, final int light, final int side, final FirstPersonArm.Arm arm, final boolean otherFree, final KnifeMaterial material, final boolean foil) {
        final RenderLayer solid = RenderLayers.entityCutoutNoCull((Identifier)KNIFE_TEXTURE);
        final int[] palette = material.palette;
        return new KnifeSkin.Sink(){

            @Override
            public void part(MatrixStack part, KnifeMesh mesh, KnifeMesh.Shine shine) {
                collector.submitCustom(part, solid, (p, out) -> mesh.emit(p, out, palette, light, shine));
                if (foil) {
                    collector.getBatchingQueue(1).submitCustom(part, RenderLayers.entityGlint(), (p, out) -> mesh.emit(p, out, palette, light, KnifeMesh.Shine.NONE));
                }
            }

            @Override
            public void glow(MatrixStack part, KnifeMesh mesh, KnifeMesh.Shine shine) {
                collector.getBatchingQueue(1).submitCustom(part, RenderLayers.eyes((Identifier)KNIFE_TEXTURE), (p, out) -> mesh.emitGlow(p, out, material.glow, shine));
            }

            @Override
            public void arm(MatrixStack hand, boolean other) {
                if (arm != null && (!other || otherFree)) {
                    arm.draw(hand, side < 0 != other, collector, light);
                }
            }
        };
    }

    private static boolean offHandFree() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && player.getOffHandStack().isEmpty();
    }

    private void swordTrail(Matrix4f pose, int side) {
        Matrix4f m = new Matrix4f((Matrix4fc)pose).translate((float)side * 1.13f / 16.0f, 0.2f, 0.070625f).rotate((Quaternionfc)new Quaternionf().rotationXYZ(0.0f, -1.5707964f, 0.43633232f)).scale(0.68f).translate(-0.5f, -0.5f, -0.5f);
        Vector3f p = m.transformPosition(0.3125f, 0.3125f, 0.5f, new Vector3f());
        this.trailSample[0] = p.x;
        this.trailSample[1] = p.y;
        this.trailSample[2] = p.z;
        m.transformPosition(0.96875f, 0.96875f, 0.5f, p);
        this.trailSample[3] = p.x;
        this.trailSample[4] = p.y;
        this.trailSample[5] = p.z;
    }

    private void record(long now, int streaks) {
        Vector3f p = new Vector3f();
        for (int s = 0; s < this.trails.length; ++s) {
            if (s >= streaks) {
                this.trails[s].clear();
                continue;
            }
            for (int i = s * 6; i < s * 6 + 6; i += 3) {
                this.baseInverse.transformPosition(this.trailSample[i], this.trailSample[i + 1], this.trailSample[i + 2], p);
                this.trailSample[i] = p.x;
                this.trailSample[i + 1] = p.y;
                this.trailSample[i + 2] = p.z;
            }
            this.trails[s].add(now, this.trailSample, s * 6);
        }
    }

    private void clearTrails() {
        for (SlashTrail trail : this.trails) {
            trail.clear();
        }
    }

    private void submitGlow(OrderedRenderCommandQueue collector, long now, int glow, float flash, KnifeSkin knife) {
        long lifetime = (long)(this.trailLength.get() * 1.0E9f);
        int streaks = knife != null ? knife.streakCount() : 1;
        boolean showing = flash > 0.0f;
        for (int s = 0; s < streaks && !showing; ++s) {
            showing = !this.trails[s].isEmpty(now, lifetime);
        }
        if (!showing) {
            return;
        }
        float tipX = this.trailSample[3];
        float tipY = this.trailSample[4];
        float tipZ = this.trailSample[5];
        float turn = (float)(now % 6283185307L) / 1.0E9f * 1.5f;
        MatrixStack hand = new MatrixStack();
        hand.peek().getPositionMatrix().set((Matrix4fc)this.base);
        hand.peek().getNormalMatrix().set((Matrix3fc)this.baseNormal);
        collector.getBatchingQueue(1).submitCustom(hand, RenderLayers.eyes((Identifier)KNIFE_TEXTURE), (p, out) -> {
            for (int s = 0; s < streaks; ++s) {
                this.trails[s].emit(p, out, now, lifetime, glow, knife != null ? knife.streakStrength(s) : 1.0f);
            }
            if (flash > 0.0f) {
                SlashTrail.sparkle(p, out, tipX, tipY, tipZ, 0.07f * flash, turn, glow, flash);
            }
        });
    }

    static {
        KNIFE_TEXTURE = Identifier.of((String)"maro", (String)"textures/misc/knife_base.png");
        REST = new float[16];
    }
}
