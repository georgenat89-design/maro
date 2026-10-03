package dev.maro.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of screen rectangles stored as fractions of the screen (0..1), so they cover the same
 * part of the screen at any resolution, window size or GUI scale. Never shown as a row; it is
 * edited through its own screen and saved with the module config.
 */
public class RegionsSetting extends Setting<List<RegionsSetting.Region>> {
    /** x, y, width, height as fractions of the screen size. */
    public record Region(float x, float y, float w, float h) {
        public Region clamped() {
            float nx = clamp(x), ny = clamp(y);
            return new Region(nx, ny, Math.min(clamp(w), 1f - nx), Math.min(clamp(h), 1f - ny));
        }

        private static float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    public RegionsSetting(String name) {
        super(name, "", new ArrayList<>());
        setVisibility(() -> false);
    }

    public List<Region> list() {
        return value;
    }

    @Override
    public void reset() {
        value.clear();
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (Region r : value) {
            JsonArray a = new JsonArray();
            a.add(r.x());
            a.add(r.y());
            a.add(r.w());
            a.add(r.h());
            arr.add(a);
        }
        return arr;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonArray()) return;
        value.clear();
        for (JsonElement e : json.getAsJsonArray()) {
            if (!e.isJsonArray() || e.getAsJsonArray().size() != 4) continue;
            JsonArray a = e.getAsJsonArray();
            Region r = new Region(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()).clamped();
            if (r.w() > 0 && r.h() > 0) value.add(r);
        }
    }
}
