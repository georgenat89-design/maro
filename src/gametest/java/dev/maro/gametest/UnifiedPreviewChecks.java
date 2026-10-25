package dev.maro.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.VisualPreview;
import dev.maro.gui.preview.PreviewProvider;
import dev.maro.gui.preview.PreviewProviders;
import dev.maro.gui.render.PlayerPreviewData;
import dev.maro.gui.render.VisualPreviewEffect;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.module.impl.visuals.ArmorHud;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.module.impl.visuals.Pet;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.module.impl.visuals.ViewModel;
import dev.maro.anubis.inspect.ItemSubjectPreview;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.mixin.GuiRendererAccessor;
import dev.maro.render.capture.CleanCapture;
import dev.maro.render.capture.StreamproofScenePolicy;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Exercises the actual Preview tabs, input routing, native subject rendering and isolation. */
public final class UnifiedPreviewChecks {
    private static volatile boolean watching;
    private static Snapshot anchor;
    private static final List<String> MUTATIONS = new CopyOnWriteArrayList<>();
    private record Snapshot(Vec3d position, float yaw, float pitch, int fov, Perspective perspective,
                            int slot, List<ItemStack> inventory, Set<Integer> entities) { }
    private UnifiedPreviewChecks() { }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void lifecycle(Module module, boolean enabled) {
        if (watching) MUTATIONS.add("Lifecycle: " + module.getName() + " -> " + enabled);
    }

    public static void outbound(Packet<?> packet) {
        if (!watching || !MinecraftClient.getInstance().isOnThread()) return;
        if (packet instanceof PlayerMoveC2SPacket move) {
            if (anchor != null && (Math.abs(move.getX(anchor.position().x) - anchor.position().x) > .001
                    || Math.abs(move.getY(anchor.position().y) - anchor.position().y) > .001
                    || Math.abs(move.getZ(anchor.position().z) - anchor.position().z) > .001
                    || Math.abs(move.getYaw(anchor.yaw()) - anchor.yaw()) > .001
                    || Math.abs(move.getPitch(anchor.pitch()) - anchor.pitch()) > .001)) MUTATIONS.add("Changed movement packet");
            return; // Native idle/on-ground heartbeat is independent of the GUI.
        }
        if (packet instanceof PlayerInteractBlockC2SPacket || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerActionC2SPacket
                || packet instanceof UpdateSelectedSlotC2SPacket || packet instanceof ClickSlotC2SPacket
                || packet instanceof CreativeInventoryActionC2SPacket || packet instanceof ClientCommandC2SPacket
                || packet instanceof HandSwingC2SPacket) MUTATIONS.add("Gameplay packet: " + packet.getClass().getName());
    }

    private static ClickGuiScreen gui(MinecraftClient client) {
        require(client.currentScreen instanceof ClickGuiScreen, "Preview replaced the normal ClickGUI");
        return (ClickGuiScreen) client.currentScreen;
    }

    private static Setting<?> setting(Module module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void settle(ClientGameTestContext context) { context.waitTicks(6); }

    private static void open(ClientGameTestContext context, Module module) {
        context.runOnClient(client -> gui(client).openModuleOptions(module));
        context.getInput().setCursorPos(0, 0);
        settle(context);
    }

    private static Click at(ClickGuiScreen gui, float x, float y) {
        double[] point = gui.screenPose().project(x, y);
        return new Click(point[0], point[1], new MouseInput(0, 0));
    }

    private static void tap(ClickGuiScreen gui, float[] point) {
        require(point != null, "Missing preview control");
        Click click = at(gui, point[0], point[1]);
        gui.mouseMoved(click.x(), click.y());
        gui.mouseClicked(click, false);
        gui.mouseReleased(click);
    }

    private static void action(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            var gui = gui(client);
            float[] point = gui.visualPreview().actionPlace(name);
            require(point != null, "Missing " + name + " preview action for " + gui.settingsModule().getName());
            tap(gui, point);
        });
        context.waitTick();
    }

    private static JsonObject settings(Module module) {
        var result = new JsonObject();
        module.getSettings().forEach(s -> result.add(s.getName(), s.toJson().deepCopy()));
        result.add("extra", module.saveExtra().deepCopy());
        return result;
    }

    private static Snapshot snapshot(MinecraftClient client) {
        var inventory = new ArrayList<ItemStack>();
        for (int i = 0; i < client.player.getInventory().size(); i++) inventory.add(client.player.getInventory().getStack(i).copy());
        var ids = new TreeSet<Integer>();
        for (var entity : client.world.getEntities()) ids.add(entity.getId());
        return new Snapshot(client.player.getEntityPos(), client.player.getYaw(), client.player.getPitch(),
                client.options.getFov().getValue(), client.options.getPerspective(),
                client.player.getInventory().getSelectedSlot(), List.copyOf(inventory), Set.copyOf(ids));
    }

    private static void unchanged(MinecraftClient client, String phase) {
        require(client.player.getEntityPos().distanceTo(anchor.position()) < .001
                && client.player.getYaw() == anchor.yaw() && client.player.getPitch() == anchor.pitch(), phase + " moved the real player or camera");
        require(client.options.getFov().getValue() == anchor.fov() && client.options.getPerspective() == anchor.perspective(),
                phase + " changed the real camera options");
        require(client.player.getInventory().getSelectedSlot() == anchor.slot(), phase + " changed selected slot");
        for (int i = 0; i < anchor.inventory().size(); i++) {
            ItemStack before = anchor.inventory().get(i), after = client.player.getInventory().getStack(i);
            require(before.getCount() == after.getCount() && ItemStack.areItemsAndComponentsEqual(before, after), phase + " mutated inventory slot " + i);
        }
        var ids = new TreeSet<Integer>();
        for (var entity : client.world.getEntities()) ids.add(entity.getId());
        require(ids.equals(anchor.entities()), phase + " inserted or removed live-world entities");
        require(MUTATIONS.isEmpty(), phase + " caused lifecycle or gameplay work: " + MUTATIONS);
    }

    private static void layout(ClickGuiScreen gui, String phase) {
        float[] main = gui.mainWindowBounds(), preview = gui.previewPanelBounds(), settings = gui.settingsBounds();
        require(main != null && settings != null, phase + " lost browser or settings");
        if (!gui.previewPanelVisible()) {
            require(preview == null && gui.visualPreview().bounds() == null, phase + " kept hidden preview geometry");
        } else {
            require(preview != null && preview[0] >= main[0] + main[2] - .1f
                    && preview[0] + preview[2] <= settings[0] + .1f, phase + " overlaps browser, preview or settings");
            float[] body = gui.visualPreview().contentBounds();
            require(body != null && body[0] >= preview[0] && body[1] >= preview[1]
                    && body[0] + body[2] <= preview[0] + preview[2] + .1f
                    && body[1] + body[3] <= preview[1] + preview[3] + .1f, phase + " clips preview content");
        }
        for (float[] panel : new float[][] {main, preview, settings}) if (panel != null) {
            double[] a = gui.screenPose().project(panel[0], panel[1]);
            double[] b = gui.screenPose().project(panel[0] + panel[2], panel[1] + panel[3]);
            require(a[0] >= -1 && a[1] >= -1 && b[0] <= gui.width + 1 && b[1] <= gui.height + 1,
                    phase + " exceeds the visible GUI viewport");
        }
    }

    private static void auditProviders(ClientGameTestContext context) {
        var audit = new JsonArray();
        var impostor = new Module("Cosmetics", "Unsupported identity fixture", Category.VISUALS) { };
        require(!PreviewProviders.supports(impostor) && !ClickGuiScreen.hasPreviewTab(impostor),
                "Preview support was inferred from a module name/category");
        int supported = 0, unsupported = 0;
        for (Module module : ModuleManager.all()) {
            var provider = PreviewProviders.provider(module);
            require(ClickGuiScreen.hasPreviewTab(module) == (provider != null), "Preview metadata and tabs disagree: " + module.getName());
            var beforeOpen = context.computeOnClient(client -> settings(module));
            open(context, module);
            if (provider == null) {
                unsupported++;
                context.runOnClient(client -> {
                    var gui = gui(client);
                    require(!gui.previewTabSelected() && gui.previewTabPlace() == null
                            && !gui.previewPanelVisible() && gui.visualPreview().bounds() == null
                            && gui.visualPreview().lastState() == null && gui.visualPreview().lastSubjectState() == null,
                            "Unsupported module retained preview UI: " + module.getName());
                    require(settings(module).equals(beforeOpen), "Opening unsupported settings mutated the module: " + module.getName());
                    unchanged(client, module.getName());
                });
                continue;
            }
            supported++;
            require(provider.id() != null && !provider.id().isBlank() && provider.caption() != null && !provider.caption().isBlank(),
                    "Preview provider has empty content metadata: " + module.getName());
            require(provider.kind() != VisualPreview.Kind.NONE, "Registered provider has no renderer: " + module.getName());
            context.runOnClient(client -> {
                var gui = gui(client);
                require(gui.previewTabSelected() && gui.previewTabPlace() != null && gui.previewPanelVisible(),
                        "Supported module did not open its Preview tab: " + module.getName());
                var preview = gui.visualPreview();
                require(preview.module() == module && preview.workFrames() > 0, "Wrong or inactive provider: " + module.getName());
                if (provider.content() == PreviewProvider.Content.PLAYER)
                    require(preview.lastState() != null && preview.lastState().id == VisualPreview.ENTITY_ID,
                            "Player provider did not render its private player: " + module.getName());
                else require(preview.lastState() == null, "Non-player feature received an unrelated player: " + module.getName());
                if (provider.content() == PreviewProvider.Content.ENTITY)
                    require(preview.lastSubjectState() != null && module instanceof Pet pet && pet.companion() == null,
                            "Entity provider failed to use its isolated native subject: " + module.getName());
                if (provider.content() == PreviewProvider.Content.PLAYER) {
                    var state = preview.lastState();
                    require(ItemStack.areItemsAndComponentsEqual(state.equippedHeadStack, client.player.getEquippedStack(EquipmentSlot.HEAD))
                                    && ItemStack.areItemsAndComponentsEqual(state.equippedChestStack, client.player.getEquippedStack(EquipmentSlot.CHEST))
                                    && ItemStack.areItemsAndComponentsEqual(state.equippedLegsStack, client.player.getEquippedStack(EquipmentSlot.LEGS))
                                    && ItemStack.areItemsAndComponentsEqual(state.equippedFeetStack, client.player.getEquippedStack(EquipmentSlot.FEET)),
                            "Player preview lost applicable equipment: " + module.getName());
                    var worldState = (net.minecraft.client.render.entity.state.PlayerEntityRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).getAndUpdateRenderState(client.player, 1);
                    require(worldState != state && worldState.id == client.player.getId(), "Preview reused the live-world player render state");
                    require(PlayerPreviewData.of(state) != null && PlayerPreviewData.of(state).maro$previewModule() == module
                                    && PlayerPreviewData.of(worldState) == null,
                            "Preview state escaped into live-world rendering: " + module.getName());
                    require(!(state.mainArm == net.minecraft.util.Arm.RIGHT ? state.rightHandItemState : state.leftHandItemState).isEmpty(),
                            "Player preview lost its applicable held item: " + module.getName());
                    if (module instanceof SkinAccessories)
                        require(state.skinTextures.body().texturePath().equals(client.player.getSkin().body().texturePath()),
                                "Cosmetics preview lost the current player's skin");
                }
                if (provider.controls() != PreviewProvider.Controls.MODEL)
                    require(preview.actionPlace("FRONT") == null && preview.actionPlace("BACK") == null,
                            "Non-model content received player controls: " + module.getName());
                else require(preview.actionPlace("FRONT") != null && preview.actionPlace("BACK") != null
                                && preview.actionPlace("RESET") != null,
                        "Model provider is missing its presentation controls: " + module.getName());
                require((preview.actionPlace("PLAY") != null) == provider.animated(),
                        "Playback controls disagree with the provider: " + module.getName());
                require(!module.isEnabled(), "Opening a preview enabled its module: " + module.getName());
                require(settings(module).equals(beforeOpen), "Opening Preview mutated configured settings: " + module.getName());
                layout(gui, module.getName());
                unchanged(client, module.getName());
            });
            var item = new JsonObject();
            item.addProperty("module", module.getName()); item.addProperty("provider", provider.id());
            item.addProperty("kind", provider.kind().name()); item.addProperty("content", provider.content().name());
            item.addProperty("controls", provider.controls().name()); item.addProperty("exactRenderer", provider.exactRenderer());
            audit.add(item);
        }
        require(supported > 20 && unsupported > 0, "Provider audit did not cover meaningful supported/unsupported modules");
        try {
            Path file = Path.of("run/screenshots/unified-preview-audit.json");
            // Production client tests use run/ as their working directory.
            file = context.computeOnClient(client -> client.runDirectory.toPath().resolve("screenshots/unified-preview-audit.json"));
            Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(audit));
        } catch (Exception error) { throw new AssertionError(error); }
        System.out.println("[unified-preview] provider audit: " + supported + " specific providers, " + unsupported + " unsupported modules");
    }

    private static BufferedImage read(Path path) {
        try { return ImageIO.read(path.toFile()); } catch (Exception error) { throw new AssertionError(error); }
    }

    private static int[] contentPixels(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            var gui = gui(client); var body = gui.visualPreview().contentBounds();
            require(body != null, "Preview has no content area");
            double[] a = gui.screenPose().project(body[0], body[1]);
            double[] b = gui.screenPose().project(body[0] + body[2], body[1] + body[3]);
            double scale = client.getWindow().getScaleFactor();
            return new int[] {(int)Math.ceil(a[0] * scale), (int)Math.ceil(a[1] * scale),
                    (int)Math.floor(b[0] * scale), (int)Math.floor(b[1] * scale)};
        });
    }

    private static Object itemRenderer(MinecraftClient client) {
        return ((GuiRendererAccessor)((GameRendererSpatialAccessor)client.gameRenderer).maro$guiRenderer())
                .maro$specialRenderers().get(ItemSubjectPreview.State.class);
    }

    private static void itemResources(ClientGameTestContext context) {
        var view = ModuleManager.get(ViewModel.class);
        open(context, view);
        context.runOnClient(client -> {
            for (String name : List.of("Main X", "Main Y", "Main Z", "Main Rot X", "Main Rot Y", "Main Rot Z"))
                ((NumberSetting)setting(view, name)).set(0d);
            ((NumberSetting)setting(view, "Main Scale")).set(1d);
            ((ModeSetting)setting(view, "Swing Mode")).set("None");
            ((dev.maro.setting.BooleanSetting)setting(view, "Hide Off Hand")).set(true);
        }); settle(context);
        Object renderer = context.computeOnClient(UnifiedPreviewChecks::itemRenderer);
        require(renderer != null, "Item preview did not register its native target renderer");
        var normal = read(context.takeScreenshot("maro-unified-preview-item-normal"));
        int[] body = contentPixels(context);
        context.runOnClient(client -> ((NumberSetting)setting(view, "Main Scale")).set(.45d));
        settle(context);
        var small = read(context.takeScreenshot("maro-unified-preview-item-small"));
        int changed = 0;
        for (int y = Math.max(0, body[1]); y < Math.min(normal.getHeight(), body[3]); y++)
            for (int x = Math.max(0, body[0]); x < Math.min(normal.getWidth(), body[2]); x++)
                if (normal.getRGB(x, y) != small.getRGB(x, y)) changed++;
        require(changed > 100, "Paused disabled ViewModel scale did not change native item pixels: " + changed);
        context.runOnClient(client -> {
            require(itemRenderer(client) == renderer, "Steady item preview recreated its target renderer");
            require(!view.isEnabled() && !gui(client).visualPreview().playing(), "Item edit activated gameplay or playback");
            require(com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride == null
                            && com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride == null,
                    "Item preview left a native output target bound");
            unchanged(client, "Paused native item edit");
            gui(client).selectPreviewTab(false);
            require(itemRenderer(client) == null, "Hiding item Preview retained its native target renderer");
        });
        open(context, ModuleManager.get(SkinAccessories.class));
    }

    private static void customModels(ClientGameTestContext context) {
        var model = ModuleManager.get(CustomModel.class);
        context.runOnClient(client -> { model.select(false, "SpongeBob"); model.select(true, "Skeleton"); });
        open(context, model);
        for (int tick = 0; tick < 1000 && !context.computeOnClient(client -> model.ready("SpongeBob") != null); tick++) context.waitTick();
        settle(context);
        context.runOnClient(client -> require(!model.isEnabled() && !gui(client).visualPreview().otherModel()
                        && model.ready("SpongeBob") != null && CustomModel.worn(gui(client).visualPreview().lastState()) == model.ready("SpongeBob"),
                "Disabled SELF preview did not use the selected bundled SpongeBob model"));
        JsonObject before = context.computeOnClient(client -> settings(model));
        action(context, "TARGET_OTHERS");
        for (int tick = 0; tick < 1000 && !context.computeOnClient(client -> model.ready("Skeleton") != null); tick++) context.waitTick();
        settle(context);
        context.runOnClient(client -> require(gui(client).visualPreview().otherModel() && model.ready("Skeleton") != null
                        && CustomModel.worn(gui(client).visualPreview().lastState()) == model.ready("Skeleton") && settings(model).equals(before),
                "OTHERS did not isolate the selected model or mutated configured settings"));
        context.takeScreenshot("maro-unified-preview-other-model");
        action(context, "TARGET_SELF"); settle(context);
        context.runOnClient(client -> {
            require(!gui(client).visualPreview().otherModel() && CustomModel.worn(gui(client).visualPreview().lastState()) == model.ready("SpongeBob"),
                    "SELF did not return to the configured self model");
            unchanged(client, "Custom Model target controls");
        });
        open(context, ModuleManager.get(SkinAccessories.class));
    }

    private static void appearanceAnimations(ClientGameTestContext context) {
        var hats = ModuleManager.get(dev.maro.nathan.modules.Hats.class);
        var acrobatics = ModuleManager.get(dev.maro.module.impl.visuals.Acrobatics.class);
        context.runOnClient(client -> {
            dev.maro.gui.preview.PreviewSettings.setting(hats, "hat").fromJson(new com.google.gson.JsonPrimitive("propeller_cap"));
            dev.maro.gui.preview.PreviewSettings.setting(hats, "hide-when-helmet-equipped").fromJson(new com.google.gson.JsonPrimitive(true));
            ((ModeSetting)setting(acrobatics, "Trick")).set("Front Flip");
            ((NumberSetting)setting(acrobatics, "Speed")).set(.4d);
        });
        for (Module module : List.of(hats, acrobatics)) {
            open(context, module);
            if (module == hats) {
                context.runOnClient(client -> {
                    require(!hats.wants(gui(client).visualPreview().lastState()), "Hat preview ignored Hide When Helmet Equipped");
                    dev.maro.gui.preview.PreviewSettings.setting(hats, "hide-when-helmet-equipped").fromJson(new com.google.gson.JsonPrimitive(false));
                }); settle(context);
            }
            action(context, "PLAY"); context.waitTicks(4); action(context, "PLAY");
            double paused = context.computeOnClient(client -> gui(client).visualPreview().playbackTime());
            context.waitTicks(4);
            context.runOnClient(client -> {
                var preview = gui(client).visualPreview(); var data = PlayerPreviewData.of(preview.lastState());
                require(!module.isEnabled() && data != null && data.maro$previewModule() == module && data.maro$previewAnimated()
                                && paused > .05 && data.maro$previewSeconds() == paused && preview.playbackTime() == paused,
                        "Disabled appearance PLAY/PAUSE lost its private frozen pose: " + module.getName());
                if (module == hats) require(hats.wants(preview.lastState()), "Selected disabled hat was excluded from its preview layer");
                else {
                    var matrix = new net.minecraft.client.util.math.MatrixStack();
                    acrobatics.previewTransform(matrix, preview.lastState().height, paused);
                    require(acrobatics.previewPose(paused) != null && acrobatics.previewBlend(paused) > 0
                                    && !matrix.peek().getPositionMatrix().equals(new org.joml.Matrix4f()),
                            "Acrobatics preview did not sample the actual transform and limb pose");
                }
                unchanged(client, "Appearance playback " + module.getName());
            });
            context.takeScreenshot("maro-unified-preview-animation-" + module.getName().toLowerCase().replace(' ', '-'));
        }
        open(context, ModuleManager.get(SkinAccessories.class));
    }

    private static void controls(ClientGameTestContext context) {
        var cosmetics = ModuleManager.get(SkinAccessories.class);
        open(context, cosmetics);
        action(context, "FRONT");
        context.runOnClient(client -> require(Math.abs(gui(client).visualPreview().angle()) < .01, "FRONT did not face forward"));
        action(context, "BACK");
        context.runOnClient(client -> require(Math.abs(gui(client).visualPreview().angle() - 180) < .01, "BACK did not face backward"));
        context.runOnClient(client -> {
            var gui = gui(client); var preview = gui.visualPreview(); float[] body = preview.contentBounds();
            Click from = at(gui, body[0] + body[2] / 2, body[1] + body[3] / 2);
            Click to = at(gui, body[0] + body[2] / 2 + 32, body[1] + body[3] / 2 + 9);
            float before = preview.angle();
            gui.mouseMoved(from.x(), from.y()); gui.mouseClicked(from, false);
            gui.mouseDragged(to, to.x() - from.x(), to.y() - from.y()); gui.mouseReleased(to);
            require(Math.abs(preview.angle() - before) > 10, "Model drag did not rotate");
            for (int i = 0; i < 8; i++) gui.mouseScrolled(from.x(), from.y(), 0, -1);
            require(preview.zoom() < .95, "Model scroll did not zoom");
            unchanged(client, "Rotate and zoom");
        });
        action(context, "PLAY");
        context.waitTicks(7);
        double playingTime = context.computeOnClient(client -> gui(client).visualPreview().playbackTime());
        require(playingTime > .05, "PLAY did not advance supported animation");
        action(context, "PLAY");
        double paused = context.computeOnClient(client -> gui(client).visualPreview().playbackTime());
        float poseAge = context.computeOnClient(client -> gui(client).visualPreview().lastState().age);
        context.waitTicks(8);
        context.runOnClient(client -> require(!gui(client).visualPreview().playing()
                && gui(client).visualPreview().playbackTime() == paused && gui(client).visualPreview().lastState().age == poseAge,
                "PAUSE advanced its animation or reset its pose"));
        var beforeReset = context.computeOnClient(client -> settings(cosmetics));
        action(context, "RESET");
        context.runOnClient(client -> {
            var preview = gui(client).visualPreview();
            require(Math.abs(preview.angle() + 22) < .01 && Math.abs(preview.zoom() - 1) < .001
                    && preview.playbackTime() == 0 && !preview.playing(), "RESET did not reset only presentation");
            require(settings(cosmetics).equals(beforeReset), "RESET erased visual module settings");
            unchanged(client, "Player controls");
        });
        context.takeScreenshot("maro-unified-preview-player-controls");

        var esp = ModuleManager.get(PlayerESP.class);
        open(context, esp);
        context.runOnClient(client -> {
            ((ModeSetting) setting(esp, "Fill Style")).set("Solid");
            ((ColorSetting) setting(esp, "Color")).set(0xFFFF2020);
            ((NumberSetting) setting(esp, "Fill Opacity")).set(100d);
        });
        settle(context); var red = read(context.takeScreenshot("maro-unified-preview-esp-red"));
        int[] subject = contentPixels(context);
        context.runOnClient(client -> ((ColorSetting) setting(esp, "Color")).set(0xFF2080FF));
        settle(context); var blue = read(context.takeScreenshot("maro-unified-preview-esp-blue"));
        int changed = 0;
        // Exclude the settings color swatch: only the actual subject can satisfy this check.
        for (int y = Math.max(0, subject[1]); y < Math.min(red.getHeight(), subject[3]); y++)
            for (int x = Math.max(0, subject[0]); x < Math.min(red.getWidth(), subject[2]); x++) {
            int a = red.getRGB(x, y), b = blue.getRGB(x, y);
            if ((a >> 16 & 255) > (a & 255) + 70 && (b & 255) > (b >> 16 & 255) + 70) changed++;
        }
        require(changed > 100 && VisualPreviewEffect.frames() > 0, "ESP settings did not update native silhouette pixels: " + changed);

        open(context, ModuleManager.get(ArmorHud.class));
        context.runOnClient(client -> require(gui(client).visualPreview().lastState() == null
                && gui(client).visualPreview().actionPlace("FRONT") == null, "HUD preview retained model state/controls"));
        open(context, ModuleManager.get(CustomSky.class));
        action(context, "PLAY"); context.waitTicks(5);
        context.runOnClient(client -> require(gui(client).visualPreview().playbackTime() > 0
                && gui(client).visualPreview().lastState() == null, "Shader preview did not use its own animation/content"));
        action(context, "PLAY");
        open(context, cosmetics);
        context.runOnClient(client -> require(gui(client).visualPreview().playbackTime() == 0 && !gui(client).visualPreview().playing()
                && Math.abs(gui(client).visualPreview().angle() + 22) < .01 && Math.abs(gui(client).visualPreview().zoom() - 1) < .001,
                "Switching providers retained stale presentation state"));
    }

    private static void visibility(ClientGameTestContext context) {
        context.runOnClient(client -> tap(gui(client), gui(client).settingsTabPlace()));
        settle(context);
        long hiddenFrames = context.computeOnClient(client -> {
            var gui = gui(client); require(!gui.previewTabSelected() && !gui.previewPanelVisible()
                    && gui.visualPreview().lastState() == null, "Settings tab did not hide preview"); return gui.visualPreview().workFrames();
        });
        context.waitTicks(8);
        context.runOnClient(client -> {
            var gui = gui(client); require(gui.visualPreview().workFrames() == hiddenFrames, "Hidden preview kept doing render work");
            tap(gui, gui.previewTabPlace());
        }); settle(context);
        context.runOnClient(client -> require(gui(client).previewTabSelected() && gui(client).previewPanelVisible(), "Preview tab did not reopen"));
        context.runOnClient(client -> gui(client).closeSettings()); settle(context);
        long closedFrames = context.computeOnClient(client -> gui(client).visualPreview().workFrames());
        context.waitTicks(8);
        context.runOnClient(client -> require(gui(client).visualPreview().workFrames() == closedFrames
                && gui(client).visualPreview().bounds() == null, "Closed settings kept doing preview work"));
    }

    private static void presentationModes(ClientGameTestContext context) {
        var cosmetics = ModuleManager.get(SkinAccessories.class);
        try {
            // StationaryScreenGuiChecks owns Screen GUI's world-plane input contract.
            for (boolean floating : new boolean[] {true}) {
                context.runOnClient(client -> {
                    ClientSettings.screenGui.set(!floating); ClientSettings.spatialMenu.set(floating);
                    gui(client).openModuleOptions(cosmetics);
                }); settle(context);
                var camera = context.computeOnClient(client -> new double[] {client.gameRenderer.getCamera().getCameraPos().x,
                        client.gameRenderer.getCamera().getCameraPos().y, client.gameRenderer.getCamera().getCameraPos().z,
                        client.gameRenderer.getCamera().getYaw(), client.gameRenderer.getCamera().getPitch()});
                context.runOnClient(client -> {
                    var gui = gui(client);
                    require(ClientSettings.screenGui.get() == !floating && ClientSettings.spatialMenu.get() == floating,
                            "Preview rewrote the saved menu presentation mode");
                    require(!gui.worldControls() && !gui.spatialView().isOpen() && !gui.screenPose().active()
                                    && !client.mouse.isCursorLocked() && gui.previewPanelVisible(),
                            "Preview retained world-space camera/input control");
                    var preview = gui.visualPreview(); float[] body = preview.contentBounds();
                    Click from = at(gui, body[0] + body[2] / 2, body[1] + body[3] / 2);
                    Click to = at(gui, body[0] + body[2] / 2 + 35, body[1] + body[3] / 2);
                    float angle = preview.angle(), zoom = preview.zoom();
                    gui.mouseMoved(from.x(), from.y()); gui.mouseClicked(from, false);
                    gui.mouseDragged(to, to.x() - from.x(), 0); gui.mouseReleased(to);
                    gui.mouseScrolled(from.x(), from.y(), 0, -1);
                    require(Math.abs(preview.angle() - angle) > 10 && preview.zoom() < zoom,
                            "Screen/Floating menu preference consumed preview drag or scroll");
                }); settle(context);
                context.runOnClient(client -> {
                    var actual = client.gameRenderer.getCamera();
                    require(actual.getCameraPos().distanceTo(new Vec3d(camera[0], camera[1], camera[2])) < .001
                                    && actual.getYaw() == camera[3] && actual.getPitch() == camera[4],
                            "Preview interaction changed the real camera in Screen/Floating menu mode");
                    unchanged(client, floating ? "Floating Window preview input" : "Screen GUI preview input");
                });
            }
        } finally {
            context.runOnClient(client -> { ClientSettings.screenGui.set(false); ClientSettings.spatialMenu.set(false); });
        }
    }

    private static void resizing(ClientGameTestContext context) {
        var cosmetics = ModuleManager.get(SkinAccessories.class);
        for (int[] size : new int[][] {{1600, 900}, {960, 540}, {800, 600}}) {
            context.getInput().resizeWindow(size[0], size[1]);
            for (int scale : new int[] {2, 3, 4}) {
                context.runOnClient(client -> { client.options.getGuiScale().setValue(scale); client.onResolutionChanged(); });
                open(context, cosmetics);
                context.runOnClient(client -> layout(gui(client), size[0] + "x" + size[1] + " / GUI " + scale));
                if (scale == 3) context.takeScreenshot("maro-unified-preview-" + size[0] + "x" + size[1]);
            }
        }
        context.getInput().resizeWindow(1600, 900);
        context.runOnClient(client -> { client.options.getGuiScale().setValue(2); client.onResolutionChanged(); });
        open(context, cosmetics);
        boolean fullscreen = context.computeOnClient(client -> client.getWindow().isFullscreen());
        try {
            context.runOnClient(client -> { client.getWindow().toggleFullscreen(); client.onResolutionChanged(); });
            settle(context);
            context.runOnClient(client -> {
                require(client.getWindow().isFullscreen() != fullscreen, "Native fullscreen transition did not occur");
                layout(gui(client), "Native fullscreen transition");
                require(gui(client).previewPanelVisible() && gui(client).visualPreview().lastState() != null,
                        "Fullscreen transition lost the visible preview");
                unchanged(client, "Native fullscreen transition");
            });
            context.takeScreenshot("maro-unified-preview-fullscreen");
        } finally {
            context.runOnClient(client -> {
                if (client.getWindow().isFullscreen() != fullscreen) client.getWindow().toggleFullscreen();
                client.onResolutionChanged();
            });
        }
        settle(context);
        context.runOnClient(client -> { require(client.getWindow().isFullscreen() == fullscreen, "Fullscreen state was not restored"); layout(gui(client), "Restored native window"); });
        var reload = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
        context.runOnClient(client -> reload.set(client.reloadResources()));
        for (int tick = 0; tick < 800 && !reload.get().isDone(); tick++) context.waitTick();
        require(reload.get().isDone(), "Resource reload did not finish with Preview open"); reload.get().join(); settle(context);
        context.runOnClient(client -> { layout(gui(client), "Resource reload"); require(gui(client).visualPreview().lastState() != null, "Reload lost native player preview"); });
    }

    private static NativeImage gpu(ClientGameTestContext context, boolean clean) {
        var result = new CompletableFuture<NativeImage>();
        context.runOnClient(client -> ScreenshotRecorder.takeScreenshot(clean ? CleanCapture.framebuffer() : client.getFramebuffer(), result::complete));
        for (int tick = 0; tick < 160 && !result.isDone(); tick++) context.waitTick();
        require(result.isDone(), "GPU preview capture did not finish"); return result.join();
    }

    private static void streamproof(ClientGameTestContext context) {
        watching = false;
        var proof = ModuleManager.get(Streamproof.class);
        context.runOnClient(client -> proof.setEnabled(true));
        for (int tick = 0; tick < 600 && !context.computeOnClient(client -> client.getOverlay() == null
                && StreamproofScenePolicy.ready() && CleanCapture.protectedLayer() && CleanCapture.frames() >= 5); tick++) context.waitTick();
        require(context.computeOnClient(client -> client.getOverlay() == null && StreamproofScenePolicy.ready()
                && CleanCapture.protectedLayer() && CleanCapture.frames() >= 5),
                "Streamproof did not finish restoring its ordinary scene/resources before preview capture");
        long before = context.computeOnClient(client -> gui(client).visualPreview().workFrames());
        var esp = ModuleManager.get(PlayerESP.class);
        open(context, esp);
        for (int tick = 0; tick < 160 && !context.computeOnClient(client -> gui(client).previewPanelVisible()
                && gui(client).visualPreview().module() == esp && gui(client).visualPreview().lastState() != null
                && gui(client).visualPreview().workFrames() >= before + 3); tick++) context.waitTick();
        require(context.computeOnClient(client -> gui(client).previewPanelVisible() && gui(client).visualPreview().module() == esp
                        && gui(client).visualPreview().lastState() != null && gui(client).visualPreview().workFrames() >= before + 3),
                "Private preview did not render fresh frames after capture setup");
        try (var local = gpu(context, false); var clean = gpu(context, true)) {
            // Preserve actual GPU evidence even when a later pixel assertion fails.
            context.runOnClient(client -> { try {
                Path directory = client.runDirectory.toPath().resolve("screenshots"); Files.createDirectories(directory);
                local.writeTo(directory.resolve("maro-unified-preview-private.png"));
                clean.writeTo(directory.resolve("maro-unified-preview-clean.png"));
            } catch (Exception error) { throw new AssertionError(error); } });
            require(local.getWidth() == clean.getWidth() && local.getHeight() == clean.getHeight(), "Clean preview capture has stale dimensions");
            double[] bounds = context.computeOnClient(client -> {
                var gui = gui(client); var panel = gui.previewPanelBounds();
                double[] a = gui.screenPose().project(panel[0], panel[1]);
                double[] b = gui.screenPose().project(panel[0] + panel[2], panel[1] + panel[3]);
                double scale = client.getWindow().getScaleFactor();
                return new double[] {a[0] * scale, a[1] * scale, b[0] * scale, b[1] * scale};
            });
            int different = 0, localPurple = 0, cleanPurple = 0;
            for (int y = Math.max(0, (int) bounds[1]); y < Math.min(local.getHeight(), bounds[3]); y++)
                for (int x = Math.max(0, (int) bounds[0]); x < Math.min(local.getWidth(), bounds[2]); x++) {
                    int a = local.getColorArgb(x, y), b = clean.getColorArgb(x, y);
                    if (a != b) different++;
                    if (purple(a)) localPurple++;
                    if (purple(b)) cleanPurple++;
                }
            require(different > 1000 && localPurple > 20 && cleanPurple < localPurple / 5,
                    "Preview panel was missing locally or present in clean GPU target: " + different + ", purple " + localPurple + "/" + cleanPurple);
        } finally { context.runOnClient(client -> proof.setEnabled(false)); }
    }

    private static boolean purple(int color) {
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        // Include the light purple heading/badge, whose tint is deliberately subtle.
        return red > 100 && blue > 130 && blue > green * 1.1 && red > green * 1.02;
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Map<Setting<?>, JsonElement> saved = new IdentityHashMap<>();
        Map<Module, Boolean> enabled = new LinkedHashMap<>();
        int scale = context.computeOnClient(client -> client.options.getGuiScale().getValue());
        var perspective = context.computeOnClient(client -> client.options.getPerspective());
        var serverInventory = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst(); var items = new ArrayList<ItemStack>();
            for (int slot = 0; slot < player.getInventory().size(); slot++) items.add(player.getInventory().getStack(slot).copy());
            return List.copyOf(items);
        });
        world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
        world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
        context.waitTicks(4);
        context.runOnClient(client -> {
            ClientSettings.ALL.forEach(group -> group.getSettings().forEach(setting -> saved.put(setting, setting.toJson().deepCopy())));
            for (Module module : ModuleManager.all()) {
                enabled.put(module, module.isEnabled()); module.getSettings().forEach(setting -> saved.put(setting, setting.toJson().deepCopy()));
                module.setEnabled(false);
            }
            ClientSettings.spatialMenu.set(false); ClientSettings.screenGui.set(false); ClientSettings.guiSize.set(90d);
            client.options.getGuiScale().setValue(2); client.onResolutionChanged(); client.setScreen(new ClickGuiScreen());
        });
        context.getInput().resizeWindow(1600, 900); settle(context);
        try {
            context.runOnClient(client -> { anchor = snapshot(client); MUTATIONS.clear(); watching = true; });
            auditProviders(context);
            controls(context);
            itemResources(context);
            customModels(context);
            appearanceAnimations(context);
            visibility(context);
            presentationModes(context);
            resizing(context);
            context.runOnClient(client -> { unchanged(client, "Complete preview interaction"); require(GL11.glGetError() == GL11.GL_NO_ERROR, "Preview left an OpenGL error"); });
            streamproof(context);
            context.runOnClient(client -> {
                client.setScreen(null);
                require(!VisualPreviewEffect.hasResources(), "Closing GUI retained player effect render targets");
                require(client.options.getPerspective() == perspective, "Preview changed game perspective");
            });
            RenderFaultChecks.assertClean();
            System.out.println("[unified-preview] PASS: every explicit provider connected to its Preview tab, specific subjects/controls, disabled-module rendering without lifecycle/gameplay packets/world/player/inventory changes; real front/back/reset/play/pause/drag/zoom; native ESP color pixels; settings-tab/closed work stopped; responsive scales 2/3/4, three window sizes and native fullscreen; resource reload and internal Streamproof clean target");
        } finally {
            watching = false; anchor = null;
            context.runOnClient(client -> {
                client.setScreen(null); ModuleManager.get(Streamproof.class).setEnabled(false); saved.forEach(Setting::fromJson);
                enabled.forEach(Module::setEnabled); client.options.setPerspective(perspective); client.options.getGuiScale().setValue(scale); client.onResolutionChanged();
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                for (int slot = 0; slot < serverInventory.size(); slot++) player.getInventory().setStack(slot, serverInventory.get(slot).copy());
                player.playerScreenHandler.syncState();
            });
            context.getInput().resizeWindow(1280, 720);
        }
    }
}
