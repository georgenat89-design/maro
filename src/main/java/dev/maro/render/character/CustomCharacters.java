// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.gltf.CharacterBake;
import dev.maro.render.character.gltf.ImageDecoder;
import dev.maro.render.character.gltf.SkinBake;
import dev.maro.render.character.kit.BakedDesign;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class CustomCharacters {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"Maro");
    private static final long MAX_FILE = 0x10000000L;
    private static final int MAX_TEXTURE = 8192;

    private CustomCharacters() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("maro").resolve("characters");
    }

    private static Path cache() {
        return CustomCharacters.folder().resolve(".baked");
    }

    static List<BakedDesign> scan() {
        ArrayList<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(CustomCharacters.folder(), "*.{glb,GLB}");){
            for (Path file : dir) {
                if (!Files.isRegularFile(file, new LinkOption[0])) continue;
                files.add(file);
            }
        }
        catch (IOException ignored) {
            return List.of();
        }
        files.sort(null);
        ArrayList<BakedDesign> out = new ArrayList<BakedDesign>();
        HashSet<String> ids = new HashSet<String>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - 4);
            try {
                String stamp = name + Files.size(file) + Files.getLastModifiedTime(file, new LinkOption[0]).toMillis() + "5-appearance-2";
                String id = "custom_" + CustomCharacters.slug(name) + "_" + Integer.toHexString(stamp.hashCode());
                ids.add(id);
                out.add(new BakedDesign(id, name, () -> CustomCharacters.open(file, id)));
            }
            catch (IOException e) {
                LOGGER.warn("[Maro] Skipping custom character {}: {}", (Object)file, (Object)e.toString());
            }
        }
        CustomCharacters.prune(ids);
        return out;
    }

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "model" : (s.length() > 32 ? s.substring(0, 32) : s);
    }

    private static void prune(Set<String> keep) {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(CustomCharacters.cache(), "*.anc");){
            for (Path file : dir) {
                String id = file.getFileName().toString().replace(".anc", "");
                if (keep.contains(id)) continue;
                Files.deleteIfExists(file);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private static synchronized InputStream open(Path file, String id) throws IOException {
        Path baked = CustomCharacters.cache().resolve(id + ".anc");
        if (Files.exists(baked, new LinkOption[0])) {
            return Files.newInputStream(baked, new OpenOption[0]);
        }
        if (Files.size(file) > 0x10000000L) {
            throw new IOException("the file is larger than 256 MB");
        }
        String name = file.getFileName().toString();
        CustomModel.baking(name);
        long start = System.nanoTime();
        byte[] glb = Files.readAllBytes(file);
        byte[] data = null;
        try {
            // Skinned first: the file's skeleton and weights (or a fitted one) and its own clips.
            data = SkinBake.bake(glb, new CharacterBake.Options(), CustomCharacters::decode);
            if (data != null) LOGGER.info("[Maro] Baked custom character {} (skinned) in {} ms", name, (System.nanoTime() - start) / 1000000L);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Maro] Skinned bake of {} failed, using rigid parts: {}", name, e.toString());
        }
        if (data == null) {
            CharacterBake.Result result = CharacterBake.bake(glb, new CharacterBake.Options(), CustomCharacters::decode);
            LOGGER.info("[Maro] Baked custom character {} in {} ms, moving {}", new Object[]{name, (System.nanoTime() - start) / 1000000L, result.limbs()});
            if (result.limbs().size() < 3) {
                CustomModel.notice(name, result.limbs());
            }
            data = result.data();
        }
        Files.createDirectories(CustomCharacters.cache(), new FileAttribute[0]);
        Path temp = CustomCharacters.cache().resolve(id + ".tmp");
        Files.write(temp, data, new OpenOption[0]);
        Files.move(temp, baked, StandardCopyOption.REPLACE_EXISTING);
        return new ByteArrayInputStream(data);
    }

    /** PNG or JPEG (the game's own reader takes PNG only). */
    private static ImageDecoder.Picture decode(byte[] data) throws IOException {
        int[] size = new int[2];
        int[] pixels = dev.maro.render.character.kit.SkinImages.decode(data, size);
        if (size[0] > MAX_TEXTURE || size[1] > MAX_TEXTURE) throw new IOException("texture exceeds 8192 pixels");
        return new ImageDecoder.Picture(size[0], size[1], pixels);
    }
}
