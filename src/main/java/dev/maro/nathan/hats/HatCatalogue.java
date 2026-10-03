package dev.maro.nathan.hats;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.nathan.NameeProtectAddon;

/**
 * Every hat, read out of {@code assets/nameeprotect/hats/hats.json}.
 *
 * <p>That file is written by {@code tools/hatgen.py}, which is also what paints
 * the sheets, so a hat's boxes and its texture come out of one description and
 * cannot drift apart. The sheet offsets are worked out there and written into the
 * manifest rather than being computed again here, for the same reason. Changing a
 * hat is changing that script and running it; no Java moves.
 *
 * <p><b>Boxes.</b> Head space, negative y up. A piece with no pivot is in head
 * coordinates as it stands. A piece with one is placed and turned by that pivot,
 * and its box is relative to it - which is how the rings of spikes, the bent
 * wizard's tip and the horns are laid out.
 *
 * <p><b>Groups.</b> A piece's group says what moves it, and a group is moved as a
 * whole by the matrix rather than by posing its parts, because the render
 * collector this version submits to keeps model parts by reference and copies
 * only the pose. {@code spin} turns on frame time, {@code swing} trails the
 * wearer, {@code ring} turns slowly and is drawn emissive.
 */
public final class HatCatalogue {
    /** Where the manifest lives in the jar. */
    private static final String PATH = "/assets/nameeprotect/hats/hats.json";

    /** The groups, in the order they are drawn. The empty one is everything bolted down. */
    public static final String STATIC = "";
    public static final String SPIN = "spin";
    public static final String SWING = "swing";
    public static final String RING = "ring";

    /** Cloth that hangs: a gentler sway than a tassel, for a veil or a drape. */
    public static final String DRAPE = "drape";

    public record Piece(String group, boolean front, float[] box, float[] pivot, float[] rot, float inflate, int[] tex) {
    }

    public record Spec(String id, String title, List<String> flags, List<Piece> pieces, int width, int height, String note) {
        public boolean has(String flag) {
            return flags.contains(flag);
        }
    }

    /** One group's parts, and the point the whole group turns about. */
    public record Group(String name, List<ModelPart> parts, List<Boolean> front, float[] anchor) {
    }

    public record Baked(Spec spec, List<Group> groups) {
    }

    private static Map<String, Spec> hats;

    private HatCatalogue() {
    }

    public static Map<String, Spec> all() {
        if (hats == null) hats = read();

        return hats;
    }

    public static Spec get(String id) {
        return all().get(id);
    }

    public static String[] ids() {
        return all().keySet().toArray(new String[0]);
    }

    private static Map<String, Spec> read() {
        Map<String, Spec> out = new LinkedHashMap<>();

        try (InputStream in = HatCatalogue.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing from the jar: " + PATH);

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("hats").entrySet()) {
                JsonObject hat = entry.getValue().getAsJsonObject();
                List<String> flags = new ArrayList<>();

                for (JsonElement flag : hat.getAsJsonArray("flags")) flags.add(flag.getAsString());

                List<Piece> pieces = new ArrayList<>();

                for (JsonElement element : hat.getAsJsonArray("pieces")) {
                    JsonObject piece = element.getAsJsonObject();

                    pieces.add(new Piece(
                        piece.has("group") ? piece.get("group").getAsString() : STATIC,
                        piece.has("front") && piece.get("front").getAsBoolean(),
                        floats(piece.getAsJsonArray("box")),
                        piece.has("pivot") ? floats(piece.getAsJsonArray("pivot")) : null,
                        piece.has("rot") ? floats(piece.getAsJsonArray("rot")) : null,
                        piece.has("inflate") ? piece.get("inflate").getAsFloat() : 0,
                        ints(piece.getAsJsonArray("tex"))));
                }

                JsonArray size = hat.getAsJsonArray("texture");

                out.put(entry.getKey(), new Spec(entry.getKey(),
                    hat.get("title").getAsString(), flags, pieces,
                    size.get(0).getAsInt(), size.get(1).getAsInt(),
                    hat.has("note") ? hat.get("note").getAsString() : ""));
            }
        } catch (Exception e) {
            NameeProtectAddon.LOG.error("Hats could not read {}: {}", PATH, e.toString());
        }

        return out;
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];

        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();

        return out;
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];

        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt();

        return out;
    }

    /** One part per box, each named for its place in the list. */
    public static TexturedModelData mesh(Spec spec) {
        ModelData mesh = new ModelData();
        ModelPartData root = mesh.getRoot();

        for (int i = 0; i < spec.pieces().size(); i++) {
            Piece piece = spec.pieces().get(i);
            float[] box = piece.box();
            int[] tex = piece.tex();

            ModelPartBuilder cubes = ModelPartBuilder.create()
                .uv(tex[0], tex[1])
                .cuboid(box[0], box[1], box[2], box[3], box[4], box[5], new Dilation(piece.inflate()));

            root.addChild("p" + i, cubes, pose(piece));
        }

        return TexturedModelData.of(mesh, spec.width(), spec.height());
    }

    private static ModelTransform pose(Piece piece) {
        if (piece.pivot() == null) return ModelTransform.NONE;

        float[] at = piece.pivot();
        float[] rot = piece.rot();

        if (rot == null) return ModelTransform.origin(at[0], at[1], at[2]);

        return ModelTransform.of(at[0], at[1], at[2],
            (float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2]));
    }

    /**
     * Bakes a hat and sorts its parts into groups.
     *
     * <p>A group's anchor is the point the whole of it turns about. For a
     * propeller or a tassel that is where it is fixed, which is the pivot its own
     * pieces already carry. For the halo it is the middle of the ring rather than
     * any one of the twelve blocks, so the ring turns about itself instead of
     * swinging round.
     */
    public static Baked bake(Spec spec) {
        ModelPart root = mesh(spec).createModel();
        Map<String, Group> groups = new LinkedHashMap<>();

        for (int i = 0; i < spec.pieces().size(); i++) {
            Piece piece = spec.pieces().get(i);
            Group group = groups.get(piece.group());

            if (group == null) {
                float[] pivot = piece.pivot() == null ? new float[]{0, 0, 0} : piece.pivot().clone();

                // A ring turns about its own middle, not about one of its blocks.
                if (RING.equals(piece.group())) {
                    pivot[0] = 0;
                    pivot[2] = 0;
                }

                group = new Group(piece.group(), new ArrayList<>(), new ArrayList<>(), pivot);
                groups.put(piece.group(), group);
            }

            group.parts().add(root.getChild("p" + i));
            group.front().add(piece.front());
        }

        // Static first, so anything that moves is drawn over what does not.
        List<Group> order = new ArrayList<>();

        for (String name : new String[]{STATIC, SPIN, SWING, DRAPE, RING}) {
            Group group = groups.remove(name);

            if (group != null) order.add(group);
        }

        order.addAll(groups.values());

        return new Baked(spec, order);
    }
}
