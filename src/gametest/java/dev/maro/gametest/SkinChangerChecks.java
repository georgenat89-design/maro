package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.SkinChanger;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import dev.maro.setting.ButtonSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.option.Perspective;
import java.util.UUID;

final class SkinChangerChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static AssetInfo.TextureAsset fixture(net.minecraft.client.MinecraftClient c, String path, int color) {
        Identifier id = Identifier.of("maro", "skin-test/" + path);
        var image = new NativeImage(64, 64, false);
        for (int x=0;x<64;x++) for (int y=0;y<64;y++) image.setColorArgb(x,y,color);
        c.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Skin changer native fixture", image));
        return new AssetInfo.TextureAsset() { public Identifier id() { return id; } public Identifier texturePath() { return id; } };
    }
    private static void install(SkinChanger module, String slotName, AssetInfo.TextureAsset body, PlayerSkinType model) {
        try {
            var f = SkinChanger.class.getDeclaredField(slotName); f.setAccessible(true); var slot = f.get(module);
            var skin = slot.getClass().getDeclaredField("skin"); skin.setAccessible(true);
            var ctor = skin.getType().getDeclaredConstructor(AssetInfo.TextureAsset.class, PlayerSkinType.class); ctor.setAccessible(true);
            skin.set(slot, ctor.newInstance(body, model));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object slotValue(SkinChanger module, String value) {
        try {
            var f = SkinChanger.class.getDeclaredField("self"); f.setAccessible(true); var slot = f.get(module);
            var field = slot.getClass().getDeclaredField(value); field.setAccessible(true); return field.get(slot);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void liveDownload(ClientGameTestContext context, SkinChanger module) {
        var name = (TextSetting) module.getSettings().stream().filter(s -> s.getName().equals("Self Player")).findFirst().orElseThrow();
        context.runOnClient(c -> { name.set("Notch"); module.setEnabled(true); });
        for (int i = 0; i < 400 && !context.computeOnClient(c -> slotValue(module, "skin") != null); i++) context.waitTicks(1);
        require(context.computeOnClient(c -> slotValue(module, "skin") != null), "Live Mojang skin download did not finish");
        context.runOnClient(c -> {
            require(c.player.getSkin().body().texturePath().getNamespace().equals("maro"), "Downloaded skin did not reach player renderer");
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        });
        context.waitTicks(4); context.takeScreenshot("maro-skin-changer-live-notch");
        context.runOnClient(c -> {
            ((ButtonSetting) module.getSettings().stream().filter(s -> s.getName().equals("Reload Skins")).findFirst().orElseThrow()).press();
            require(Boolean.TRUE.equals(slotValue(module, "loading")), "Reload did not start a new request");
            module.setEnabled(false);
        });
        for (int i = 0; i < 400 && context.computeOnClient(c -> Boolean.TRUE.equals(slotValue(module, "loading"))); i++) context.waitTicks(1);
        require(context.computeOnClient(c -> !Boolean.TRUE.equals(slotValue(module, "loading")) && slotValue(module, "skin") == null), "A stale request restored a disabled skin");
        System.out.println("[skin-changer-live-proof] Mojang username/profile/texture download, renderer application, reload and in-flight disable cleanup passed");
    }
    static void run(ClientGameTestContext context) {
        var module = ModuleManager.get(SkinChanger.class);
        require(module != null, "Skin Changer not registered");
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        var original = context.computeOnClient(c -> c.player.getSkin());
        try {
            context.runOnClient(c -> {
                c.setScreen(null); module.getSettings().forEach(Setting::reset); module.setEnabled(true);
                var self = fixture(c, "self", 0xFFED6547); var others = fixture(c, "others", 0xFF458CFF);
                install(module, "self", self, PlayerSkinType.WIDE); install(module, "others", others, PlayerSkinType.SLIM);
                var worn = c.player.getSkin();
                require(worn.body() == self && worn.model() == PlayerSkinType.WIDE, "Player hook did not apply self skin/model");
                require(worn.cape() == original.cape() && worn.elytra() == original.elytra(), "Skin change replaced cape or elytra");
                require(SkinChanger.skinFor(c.player.getUuid(), original) == SkinChanger.skinFor(c.player.getUuid(), original), "Per-frame skin swaps should reuse the cached object");
                var other = SkinChanger.skinFor(UUID.fromString("22222222-2222-2222-2222-222222222222"), original);
                require(other.body() == others && other.model() == PlayerSkinType.SLIM, "Other-player skin/model was not independent");
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(0);
            });
            context.waitTicks(6); context.takeScreenshot("maro-skin-changer-third-person");
            context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
            context.waitTicks(6); context.takeScreenshot("maro-skin-changer-first-person-hand");
            context.runOnClient(c -> {
                module.setEnabled(false);
                require(c.player.getSkin().body().equals(original.body()), "Disabling should restore original player skin");
                require(SkinChanger.skinFor(UUID.randomUUID(), original) == original, "Disabled module still changed other players");
            });
            System.out.println("[skin-changer-proof] self/other skins, classic/slim models, cache reuse, capes/elytra, first/third-person rendering and disable cleanup passed");
            if (Boolean.getBoolean("maro.gametest.liveSkinDownload")) liveDownload(context, module);
        } finally {
            context.runOnClient(c -> { module.setEnabled(false); module.getSettings().forEach(Setting::reset); c.options.setPerspective(perspective); });
        }
    }
}
