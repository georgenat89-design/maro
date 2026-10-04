package dev.maro.render.esp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public final class DonutSignatureCatalog {
  private static final String RESOURCE_PATH = "/maro/base-esp/donut-signatures.json.gz";
  private static DonutSignatureCatalog instance;
  private final List<DonutSignatureCatalog.Family> families;

  private DonutSignatureCatalog(List<DonutSignatureCatalog.Family> families) {
    this.families = families;
  }

  public static synchronized DonutSignatureCatalog get() {
    if (instance != null) {
      return instance;
    } else {
      instance = load();
      return instance;
    }
  }

  public List<DonutSignatureCatalog.Family> families() {
    return this.families;
  }

  private static DonutSignatureCatalog load() {
    InputStream stream =
        DonutSignatureCatalog.class.getResourceAsStream("/maro/base-esp/donut-signatures.json.gz");
    if (stream == null) {
      return new DonutSignatureCatalog(List.of());
    } else {
      try (InputStreamReader reader = new InputStreamReader(new java.util.zip.GZIPInputStream(stream), StandardCharsets.UTF_8)) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray familyArray = root.getAsJsonArray("families");
        if (familyArray == null) {
          return new DonutSignatureCatalog(List.of());
        }

        ArrayList<DonutSignatureCatalog.Family> families = new ArrayList<>();
        for (JsonElement element : familyArray) {
          families.add(parseFamily(element.getAsJsonObject()));
        }
        return new DonutSignatureCatalog(List.copyOf(families));
      } catch (Exception ignored) {
        return new DonutSignatureCatalog(List.of());
      }
    }
  }

  private static DonutSignatureCatalog.Family parseFamily(JsonObject json) {
    JsonObject dimensions = json.getAsJsonObject("dimensions");
    int width = dimensions.get("width").getAsInt();
    int height = dimensions.get("height").getAsInt();
    int length = dimensions.get("length").getAsInt();
    ArrayList<DonutSignatureCatalog.FootprintRow> rows = new ArrayList<>();

    for (JsonElement rowElement : json.getAsJsonArray("footprintRows")) {
      JsonObject rowObject = rowElement.getAsJsonObject();
      int z = rowObject.get("z").getAsInt();
      ArrayList<DonutSignatureCatalog.IntRange> ranges = new ArrayList<>();

      for (JsonElement rangeElement : rowObject.getAsJsonArray("ranges")) {
        JsonArray range = rangeElement.getAsJsonArray();
        ranges.add(
            new DonutSignatureCatalog.IntRange(range.get(0).getAsInt(), range.get(1).getAsInt()));
      }

      rows.add(new DonutSignatureCatalog.FootprintRow(z, List.copyOf(ranges)));
    }

    ArrayList<DonutSignatureCatalog.BaseColumn> columns = new ArrayList<>();

    for (JsonElement columnElement : json.getAsJsonArray("baseColumns")) {
      JsonObject columnObject = columnElement.getAsJsonObject();
      HashMap<String, Integer> types = new HashMap<>();

      for (Entry entry : columnObject.getAsJsonObject("types").entrySet()) {
        types.put((String) entry.getKey(), ((JsonElement) entry.getValue()).getAsInt());
      }

      columns.add(
          new DonutSignatureCatalog.BaseColumn(
              columnObject.get("x").getAsInt(),
              columnObject.get("z").getAsInt(),
              columnObject.get("minY").getAsInt(),
              columnObject.get("maxY").getAsInt(),
              columnObject.get("count").getAsInt(),
              Map.copyOf(types)));
    }

    HashMap<String, Integer> histogram = new HashMap<>();

    for (Entry entry : json.getAsJsonObject("baseHistogram").entrySet()) {
      histogram.put((String) entry.getKey(), ((JsonElement) entry.getValue()).getAsInt());
    }

    ArrayList<DonutSignatureCatalog.Alias> aliases = new ArrayList<>();
    JsonArray aliasArray = json.getAsJsonArray("aliases");
    if (aliasArray != null) {
      for (JsonElement aliasElement : aliasArray) {
        JsonObject aliasObject = aliasElement.getAsJsonObject();
        aliases.add(
            new DonutSignatureCatalog.Alias(
                aliasObject.get("slug").getAsString(),
                aliasObject.get("title").getAsString(),
                optionalString(aliasObject, "category"),
                optionalString(aliasObject, "author")));
      }
    }

    return new DonutSignatureCatalog.Family(
        json.get("familyHash").getAsString(),
        json.get("primarySlug").getAsString(),
        json.get("title").getAsString(),
        optionalString(json, "category"),
        optionalString(json, "author"),
        width,
        height,
        length,
        json.get("outlineMinY").getAsInt(),
        json.get("outlineMaxY").getAsInt(),
        json.get("baseMinY").getAsInt(),
        json.get("baseMaxY").getAsInt(),
        json.get("totalBlocks").getAsInt(),
        json.get("baseBlockCount").getAsInt(),
        Map.copyOf(histogram),
        List.copyOf(rows),
        List.copyOf(columns),
        List.copyOf(aliases));
  }

  private static String optionalString(JsonObject json, String key) {
    JsonElement element = json.get(key);
    if (element != null && !element.isJsonNull()) {
      String value = element.getAsString();
      return value.isEmpty() ? null : value;
    } else {
      return null;
    }
  }

  private static DonutSignatureCatalog.VariantPoint transformPoint(
      int x, int z, int width, int length, int variant) {
    return switch (variant) {
      case 0 -> new DonutSignatureCatalog.VariantPoint(x, z, width, length);
      case 1 -> new DonutSignatureCatalog.VariantPoint(z, width - 1 - x, length, width);
      case 2 ->
          new DonutSignatureCatalog.VariantPoint(width - 1 - x, length - 1 - z, width, length);
      case 3 -> new DonutSignatureCatalog.VariantPoint(length - 1 - z, x, length, width);
      case 4 -> new DonutSignatureCatalog.VariantPoint(width - 1 - x, z, width, length);
      case 5 -> new DonutSignatureCatalog.VariantPoint(z, x, length, width);
      case 6 -> new DonutSignatureCatalog.VariantPoint(x, length - 1 - z, width, length);
      case 7 ->
          new DonutSignatureCatalog.VariantPoint(length - 1 - z, width - 1 - x, length, width);
      default -> new DonutSignatureCatalog.VariantPoint(x, z, width, length);
    };
  }

  private static long packXZ(int x, int z) {
    return (long) x << 32 ^ (long) z & 4294967295L;
  }

  public static final class Alias {
    public final String slug;
    public final String title;
    public final String category;
    public final String author;

    private Alias(String slug, String title, String category, String author) {
      this.slug = slug;
      this.title = title;
      this.category = category;
      this.author = author;
    }
  }

  public static final class BaseColumn {
    public final int x;
    public final int z;
    public final int minY;
    public final int maxY;
    public final int count;
    public final Map<String, Integer> types;

    private BaseColumn(int x, int z, int minY, int maxY, int count, Map<String, Integer> types) {
      this.x = x;
      this.z = z;
      this.minY = minY;
      this.maxY = maxY;
      this.count = count;
      this.types = types;
    }

    @Override
    public String toString() {
      return this.x + "," + this.z + "," + this.count + "," + this.types;
    }
  }

  public static final class Family {
    public final String familyHash;
    public final String primarySlug;
    public final String title;
    public final String category;
    public final String author;
    public final int width;
    public final int height;
    public final int length;
    public final int outlineMinY;
    public final int outlineMaxY;
    public final int baseMinY;
    public final int baseMaxY;
    public final int totalBlocks;
    public final int baseBlockCount;
    public final Map<String, Integer> baseHistogram;
    public final List<DonutSignatureCatalog.FootprintRow> footprintRows;
    public final List<DonutSignatureCatalog.BaseColumn> baseColumns;
    public final List<DonutSignatureCatalog.Alias> aliases;
    private final List<DonutSignatureCatalog.Variant> variants;

    private Family(
        String familyHash,
        String primarySlug,
        String title,
        String category,
        String author,
        int width,
        int height,
        int length,
        int outlineMinY,
        int outlineMaxY,
        int baseMinY,
        int baseMaxY,
        int totalBlocks,
        int baseBlockCount,
        Map<String, Integer> baseHistogram,
        List<DonutSignatureCatalog.FootprintRow> footprintRows,
        List<DonutSignatureCatalog.BaseColumn> baseColumns,
        List<DonutSignatureCatalog.Alias> aliases) {
      this.familyHash = familyHash;
      this.primarySlug = primarySlug;
      this.title = title;
      this.category = category;
      this.author = author;
      this.width = width;
      this.height = height;
      this.length = length;
      this.outlineMinY = outlineMinY;
      this.outlineMaxY = outlineMaxY;
      this.baseMinY = baseMinY;
      this.baseMaxY = baseMaxY;
      this.totalBlocks = totalBlocks;
      this.baseBlockCount = baseBlockCount;
      this.baseHistogram = baseHistogram;
      this.footprintRows = footprintRows;
      this.baseColumns = baseColumns;
      this.aliases = aliases;
      this.variants = this.buildVariants();
    }

    public List<DonutSignatureCatalog.Variant> variants() {
      return this.variants;
    }

    public Set<Long> buildWorldFootprint(int variantIndex, int worldMinX, int worldMinZ) {
      HashSet<Long> footprint = new HashSet<>();
      DonutSignatureCatalog.Variant variant = this.variants.get(variantIndex);

      for (DonutSignatureCatalog.FootprintRow row : this.footprintRows) {
        for (DonutSignatureCatalog.IntRange range : row.ranges) {
          for (int x = range.from; x <= range.to; x++) {
            DonutSignatureCatalog.VariantPoint point =
                DonutSignatureCatalog.transformPoint(
                    x, row.z, this.width, this.length, variant.transformIndex);
            footprint.add(DonutSignatureCatalog.packXZ(worldMinX + point.x, worldMinZ + point.z));
          }
        }
      }

      return footprint;
    }

    private List<DonutSignatureCatalog.Variant> buildVariants() {
      ArrayList<DonutSignatureCatalog.Variant> result = new ArrayList<>();
      HashSet<String> seen = new HashSet<>();

      for (int transformIndex = 0; transformIndex < 8; transformIndex++) {
        ArrayList<DonutSignatureCatalog.BaseColumn> transformed = new ArrayList<>();
        int variantWidth = this.width;
        int variantLength = this.length;

        for (DonutSignatureCatalog.BaseColumn column : this.baseColumns) {
          DonutSignatureCatalog.VariantPoint point =
              DonutSignatureCatalog.transformPoint(
                  column.x, column.z, this.width, this.length, transformIndex);
          variantWidth = point.width;
          variantLength = point.length;
          transformed.add(
              new DonutSignatureCatalog.BaseColumn(
                  point.x, point.z, column.minY, column.maxY, column.count, column.types));
        }

        transformed.sort(
            (a, b) -> {
              if (a.x != b.x) {
                return Integer.compare(a.x, b.x);
              } else {
                return a.z != b.z ? Integer.compare(a.z, b.z) : Integer.compare(a.count, b.count);
              }
            });
        String key = variantWidth + "x" + variantLength + ":" + transformed.toString();
        if (seen.add(key)) {
          HashMap<Long, DonutSignatureCatalog.BaseColumn> columnsByPosition = new HashMap<>();

          for (DonutSignatureCatalog.BaseColumn column : transformed) {
            columnsByPosition.put(DonutSignatureCatalog.packXZ(column.x, column.z), column);
          }

          result.add(
              new DonutSignatureCatalog.Variant(
                  transformIndex,
                  variantWidth,
                  variantLength,
                  List.copyOf(transformed),
                  Collections.unmodifiableMap(columnsByPosition)));
        }
      }

      return List.copyOf(result);
    }
  }

  public static final class FootprintRow {
    public final int z;
    public final List<DonutSignatureCatalog.IntRange> ranges;

    private FootprintRow(int z, List<DonutSignatureCatalog.IntRange> ranges) {
      this.z = z;
      this.ranges = ranges;
    }
  }

  public static final class IntRange {
    public final int from;
    public final int to;

    private IntRange(int from, int to) {
      this.from = from;
      this.to = to;
    }
  }

  public static final class Variant {
    public final int transformIndex;
    public final int width;
    public final int length;
    public final List<DonutSignatureCatalog.BaseColumn> columns;
    public final Map<Long, DonutSignatureCatalog.BaseColumn> columnsByPosition;

    private Variant(
        int transformIndex,
        int width,
        int length,
        List<DonutSignatureCatalog.BaseColumn> columns,
        Map<Long, DonutSignatureCatalog.BaseColumn> columnsByPosition) {
      this.transformIndex = transformIndex;
      this.width = width;
      this.length = length;
      this.columns = columns;
      this.columnsByPosition = columnsByPosition;
    }
  }

  private static record VariantPoint(int x, int z, int width, int length) {}
}
