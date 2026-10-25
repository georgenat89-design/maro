// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class GltfFile {
    private static final int GLTF = 1179937895;
    private static final int JSON = 1313821514;
    private static final int BIN = 5130562;
    private static final int MAX_ELEMENTS = 0x2000000;
    final JsonObject json;
    private final ByteBuffer bin;

    private GltfFile(JsonObject json, ByteBuffer bin) {
        this.json = json;
        this.bin = bin;
    }

    static GltfFile read(byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 20 || buf.getInt(0) != 1179937895) {
            throw new IOException("not a .glb file");
        }
        if (buf.getInt(4) != 2) {
            throw new IOException("only glTF 2.0 files are supported");
        }
        int end = Math.min(buf.getInt(8), data.length);
        int off = 12;
        JsonObject json = null;
        ByteBuffer bin = ByteBuffer.allocate(0);
        while (off + 8 <= end) {
            int length = buf.getInt(off);
            int type = buf.getInt(off + 4);
            if (length < 0 || length > end - off - 8) {
                throw new IOException("the file is cut short");
            }
            if (type == 1313821514) {
                json = JsonParser.parseString((String)new String(data, off + 8, length, StandardCharsets.UTF_8)).getAsJsonObject();
            } else if (type == 5130562) {
                bin = ByteBuffer.wrap(data, off + 8, length).slice().order(ByteOrder.LITTLE_ENDIAN);
            }
            off += 8 + length;
        }
        if (json == null) {
            throw new IOException("the file has no glTF data");
        }
        JsonArray required = GltfFile.array(json, "extensionsRequired");
        if (!required.isEmpty()) {
            throw new IOException("it needs " + String.valueOf(required) + ", export it without compression");
        }
        return new GltfFile(json, bin);
    }

    static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    int nodes() {
        return GltfFile.array(this.json, "nodes").size();
    }

    JsonObject node(int i) {
        return GltfFile.array(this.json, "nodes").get(i).getAsJsonObject();
    }

    String nodeName(int i) {
        return GltfFile.string(this.node(i), "name", "node" + i);
    }

    private JsonObject material(int m) {
        return m < 0 ? new JsonObject() : GltfFile.array(this.json, "materials").get(m).getAsJsonObject();
    }

    String materialName(int m) {
        return GltfFile.string(this.material(m), "name", "material" + m);
    }

    boolean translucent(int m) {
        String mode = GltfFile.string(this.material(m), "alphaMode", "OPAQUE");
        return mode.equals("BLEND") || mode.equals("MASK");
    }

    boolean doubleSided(int m) {
        JsonElement e = this.material(m).get("doubleSided");
        return e != null && e.getAsBoolean();
    }

    private JsonObject baseColorTexture(int m) {
        JsonObject mat = this.material(m);
        JsonObject pbr = GltfFile.object(GltfFile.object(mat, "pbrMetallicRoughness"), "baseColorTexture");
        if (pbr.has("index")) return pbr;
        return GltfFile.object(GltfFile.object(GltfFile.object(mat, "extensions"), "KHR_materials_pbrSpecularGlossiness"), "diffuseTexture");
    }

    int baseColorSource(int m) {
        JsonObject tex = baseColorTexture(m);
        int index = GltfFile.integer(tex, "index", -1);
        JsonArray textures = GltfFile.array(this.json, "textures");
        if (index < 0 || index >= textures.size()) return -1;
        return GltfFile.integer(textures.get(index).getAsJsonObject(), "source", -1);
    }

    int texCoord(int m) {
        return GltfFile.integer(this.baseColorTexture(m), "texCoord", 0);
    }

    float[] baseColorFactor(int m) {
        JsonArray f = GltfFile.array(GltfFile.object(this.material(m), "pbrMetallicRoughness"), "baseColorFactor");
        if (f.size() != 4) f = GltfFile.array(GltfFile.object(GltfFile.object(this.material(m), "extensions"), "KHR_materials_pbrSpecularGlossiness"), "diffuseFactor");
        if (f.size() != 4) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        return new float[]{f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(), f.get(3).getAsFloat()};
    }

    byte[] baseColorImage(int m) throws IOException {
        JsonObject tex = this.baseColorTexture(m);
        if (!tex.has("index")) {
            return null;
        }
        JsonObject texture = GltfFile.array(this.json, "textures").get(tex.get("index").getAsInt()).getAsJsonObject();
        int source = GltfFile.integer(texture, "source", -1);
        if (source < 0) {
            return null;
        }
        JsonObject image = GltfFile.array(this.json, "images").get(source).getAsJsonObject();
        if (image.has("bufferView")) {
            ByteBuffer b = this.view(image.get("bufferView").getAsInt(), 0);
            byte[] out = new byte[b.remaining()];
            b.get(out);
            return out;
        }
        String uri = GltfFile.string(image, "uri", "");
        if (uri.startsWith("data:")) {
            return Base64.getDecoder().decode(uri.substring(uri.indexOf(44) + 1));
        }
        throw new IOException("its textures are separate files, export it as a single .glb");
    }

    float[] floats(int accessor) throws IOException {
        JsonObject a = this.accessor(accessor);
        int count = a.get("count").getAsInt();
        int comps = GltfFile.components(a.get("type").getAsString());
        int type = a.get("componentType").getAsInt();
        JsonElement norm = a.get("normalized");
        boolean normalized = norm != null && norm.getAsBoolean();
        float[] out = new float[count * comps];
        if (!a.has("bufferView")) {
            return out;
        }
        ByteBuffer b = this.view(a.get("bufferView").getAsInt(), GltfFile.integer(a, "byteOffset", 0));
        int stride = this.stride(a, comps, type);
        int size = GltfFile.size(type);
        for (int i = 0; i < count; ++i) {
            for (int c = 0; c < comps; ++c) {
                int at = i * stride + c * size;
                out[i * comps + c] = switch (type) {
                    case 5126 -> b.getFloat(at);
                    case 5121 -> (float)(b.get(at) & 0xFF) / (normalized ? 255.0f : 1.0f);
                    case 5123 -> (float)(b.getShort(at) & 0xFFFF) / (normalized ? 65535.0f : 1.0f);
                    case 5120 -> {
                        if (normalized) {
                            yield Math.max((float)b.get(at) / 127.0f, -1.0f);
                        }
                        yield b.get(at);
                    }
                    case 5122 -> {
                        if (normalized) {
                            yield Math.max((float)b.getShort(at) / 32767.0f, -1.0f);
                        }
                        yield b.getShort(at);
                    }
                    case 5125 -> b.getInt(at);
                    default -> throw new IOException("unknown component type " + type);
                };
            }
        }
        return out;
    }

    int[] ints(int accessor) throws IOException {
        JsonObject a = this.accessor(accessor);
        int count = a.get("count").getAsInt();
        int comps = GltfFile.components(a.get("type").getAsString());
        int type = a.get("componentType").getAsInt();
        ByteBuffer b = this.view(a.get("bufferView").getAsInt(), GltfFile.integer(a, "byteOffset", 0));
        int stride = this.stride(a, comps, type);
        int size = GltfFile.size(type);
        int[] out = new int[count * comps];
        for (int i = 0; i < count; ++i) {
            for (int c = 0; c < comps; ++c) {
                int at = i * stride + c * size;
                out[i * comps + c] = switch (type) {
                    case 5121 -> b.get(at) & 0xFF;
                    case 5123 -> b.getShort(at) & 0xFFFF;
                    case 5125 -> b.getInt(at);
                    default -> throw new IOException("unknown index type " + type);
                };
            }
        }
        return out;
    }

    private JsonObject accessor(int index) throws IOException {
        JsonObject a = GltfFile.array(this.json, "accessors").get(index).getAsJsonObject();
        if (a.has("sparse")) {
            throw new IOException("sparse accessors aren't supported");
        }
        long elements = (long)a.get("count").getAsInt() * (long)GltfFile.components(a.get("type").getAsString());
        if (elements < 0L || elements > 0x2000000L) {
            throw new IOException("a mesh is far too large");
        }
        return a;
    }

    private ByteBuffer view(int index, int extra) throws IOException {
        JsonObject v = GltfFile.array(this.json, "bufferViews").get(index).getAsJsonObject();
        if (GltfFile.integer(v, "buffer", 0) != 0) {
            throw new IOException("its data is split over several files");
        }
        int off = GltfFile.integer(v, "byteOffset", 0) + extra;
        int length = v.get("byteLength").getAsInt() - extra;
        if (off < 0 || length < 0 || off + length > this.bin.capacity()) {
            throw new IOException("the file is cut short");
        }
        return this.bin.slice(off, length).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int stride(JsonObject a, int comps, int type) {
        JsonObject v = GltfFile.array(this.json, "bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        return GltfFile.integer(v, "byteStride", comps * GltfFile.size(type));
    }

    private static int size(int type) {
        return switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            default -> 4;
        };
    }

    private static int components(String type) throws IOException {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> throw new IOException("unknown accessor type " + type);
        };
    }
}
