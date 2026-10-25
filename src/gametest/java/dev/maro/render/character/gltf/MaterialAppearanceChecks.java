package dev.maro.render.character.gltf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Diffuse-only exports and ordinary PBR exports must keep the same authored base appearance. */
public final class MaterialAppearanceChecks {
    private MaterialAppearanceChecks() { }
    public static void run() {
        String json = """
            {"asset":{"version":"2.0"},"materials":[
              {"extensions":{"KHR_materials_pbrSpecularGlossiness":{
                "diffuseFactor":[0.2,0.4,0.6,0.8],"diffuseTexture":{"index":0,"texCoord":1}}}},
              {"pbrMetallicRoughness":{"baseColorFactor":[1,0,0,1],"baseColorTexture":{"index":1}},
               "extensions":{"KHR_materials_pbrSpecularGlossiness":{"diffuseTexture":{"index":0}}}}
            ],"textures":[{"source":2},{"source":3}]}
            """;
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer glb = ByteBuffer.allocate(20 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity()).putInt(bytes.length).putInt(0x4E4F534A).put(bytes);
        try {
            GltfFile file = GltfFile.read(glb.array());
            if (file.baseColorSource(0) != 2 || file.texCoord(0) != 1 || Math.abs(file.baseColorFactor(0)[1] - .4f) > .0001)
                throw new AssertionError("Diffuse-only material lost its source appearance");
            if (file.baseColorSource(1) != 3 || file.baseColorFactor(1)[0] != 1)
                throw new AssertionError("PBR base color lost precedence");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        System.out.println("[character-materials] PASS: legacy diffuse and PBR source selection");
    }
}
