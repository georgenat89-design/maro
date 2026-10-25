package dev.maro.gui.preview;

import dev.maro.gui.VisualPreview;
import dev.maro.gui.hud.HudElement;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SkinChanger;
import dev.maro.module.impl.visuals.*;
import dev.maro.nathan.modules.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.maro.gui.preview.PreviewProvider.Content.*;
import static dev.maro.gui.preview.PreviewProvider.Controls.MODEL;
import static dev.maro.gui.preview.PreviewProvider.Controls.PLAYBACK;
import static dev.maro.gui.preview.PreviewProvider.Controls.STATIC;

/** One deliberate type registry. Names, categories and enabled flags never decide support. */
public final class PreviewProviders {
    private record Definition(String id, VisualPreview.Kind kind, PreviewProvider.Content content,
                              PreviewProvider.Controls controls, String caption,
                              boolean exactRenderer, boolean animated) implements PreviewProvider { }
    private static final Map<Class<?>, PreviewProvider> TYPES = new LinkedHashMap<>();

    static {
        add(CustomModel.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Character model and equipment", true, true);
        add(SkinAccessories.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Cosmetics and equipment", true, true);
        add(SkinChanger.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Selected skin and equipment", true, true);
        add(PlayerESP.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Player silhouette shader", true, true);
        add(Emotes.class, VisualPreview.Kind.EMOTE, PLAYER, MODEL, "Selected emote and equipment", true, true);
        add(Hats.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Hat geometry and material", true, true);
        add(Acrobatics.class, VisualPreview.Kind.PLAYER, PLAYER, MODEL, "Player animation and pose", true, true);
        add(Pet.class, VisualPreview.Kind.PET, ENTITY, MODEL, "Local companion model", true, true);
        add(CustomSky.class, VisualPreview.Kind.SKY, SHADER, PLAYBACK, "Actual sky shader", true, true);
        add(Geeked.class, VisualPreview.Kind.GEEKED, SHADER, PLAYBACK, "Actual shader on a neutral scene", true, true);
        add(CustomCrosshair.class, VisualPreview.Kind.CROSSHAIR, HUD, STATIC, "Actual crosshair renderer", true, false);
        add(CustomTotem.class, VisualPreview.Kind.TOTEM, ITEM, STATIC, "Configured totem artwork", true, false);
        add(ItemInspect.class, VisualPreview.Kind.ITEM, ITEM, PLAYBACK, "Item model and inspection motion", true, true);
        add(dev.maro.anubis.inspect.WeaponInspectModule.class, VisualPreview.Kind.ITEM, ITEM, PLAYBACK, "Weapon model and inspection motion", true, true);
        add(ViewModel.class, VisualPreview.Kind.ITEM, ITEM, PLAYBACK, "Hand item transforms", true, true);
        add(SwingSpeed.class, VisualPreview.Kind.ITEM, ITEM, PLAYBACK, "Item swing timing", true, true);
        add(JumpCircle.class, VisualPreview.Kind.RING, SAMPLE, PLAYBACK, "Projected jump-ring sample", false, true);
        add(Nametags.class, VisualPreview.Kind.NAMETAG, SAMPLE, STATIC, "Actual label renderer with sample data", true, false);
        add(ChestPreview.class, VisualPreview.Kind.CHEST, SAMPLE, STATIC, "Actual container card with sample contents", true, false);
        add(BlockESP.class, VisualPreview.Kind.BLOCKS, SAMPLE, STATIC, "Block annotation sample", false, false);
        add(StorageESP.class, VisualPreview.Kind.BLOCKS, SAMPLE, STATIC, "Storage annotation sample", false, false);
        add(BaseESP.class, VisualPreview.Kind.BASE, SAMPLE, STATIC, "Illustrative base-detection sample", false, false);
        add(Fullbright.class, VisualPreview.Kind.FULLBRIGHT, COMPARISON, PreviewProvider.Controls.COMPARISON, "Illustrative lighting comparison", false, false);
        add(StretchRes.class, VisualPreview.Kind.ASPECT, COMPARISON, PreviewProvider.Controls.COMPARISON, "Projection comparison", false, false);
        add(CustomFov.class, VisualPreview.Kind.FOV, COMPARISON, PreviewProvider.Controls.COMPARISON, "Projection comparison", false, false);
        add(KeyZoom.class, VisualPreview.Kind.FOV, COMPARISON, PreviewProvider.Controls.COMPARISON, "Zoom projection comparison", false, false);
        add(FakeBlock.class, VisualPreview.Kind.FAKE_BLOCK, COMPARISON, PreviewProvider.Controls.COMPARISON, "Source and replacement item models", true, false);
        add(FakeXp.class, VisualPreview.Kind.XP, HUD, PLAYBACK, "Configured experience-bar sample", true, true);
        add(SpawnerNotifier.class, VisualPreview.Kind.SPAWNER, SAMPLE, STATIC, "Illustrative spawner notification", false, false);
        add(SpotifyHud.class, VisualPreview.Kind.SPOTIFY, SAMPLE, PLAYBACK, "Music card with sample track data", false, true);
        add(SpinBot.class, VisualPreview.Kind.SPIN, SAMPLE, PLAYBACK, "Rotation direction indicator", false, true);
        add(Trajectories.class, VisualPreview.Kind.TRAJECTORY, SAMPLE, PLAYBACK, "Illustrative trajectory sample", false, true);
        // HUD inheritance is itself the explicit capability; each subject uses its own renderer.
        add(HudElement.class, VisualPreview.Kind.HUD, HUD, STATIC, "Actual HUD renderer", true, false);
    }

    private PreviewProviders() { }

    private static void add(Class<?> type, VisualPreview.Kind kind, PreviewProvider.Content content,
                            PreviewProvider.Controls controls, String caption, boolean exact, boolean animated) {
        TYPES.put(type, new Definition(type.getName(), kind, content, controls, caption, exact, animated));
    }

    public static PreviewProvider provider(Module module) {
        if (module == null) return null;
        for (Class<?> type = module.getClass(); type != null; type = type.getSuperclass()) {
            PreviewProvider provider = TYPES.get(type);
            if (provider != null) return provider;
        }
        return module instanceof HudElement ? TYPES.get(HudElement.class) : null;
    }

    public static boolean supports(Module module) { return provider(module) != null; }
    public static VisualPreview.Kind kind(Module module) {
        PreviewProvider provider = provider(module);
        return provider == null ? VisualPreview.Kind.NONE : provider.kind();
    }
    /** Introspection for the module list, documentation and native routing checks. */
    public static List<Module> supportedModules() {
        return ModuleManager.all().stream().filter(PreviewProviders::supports).toList();
    }
}
