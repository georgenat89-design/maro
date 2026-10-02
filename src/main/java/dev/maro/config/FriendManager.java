package dev.maro.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Case-insensitive friend list. Modules can query {@link #isFriend(String)}. */
public final class FriendManager {
    private static final Map<String, String> FRIENDS = new LinkedHashMap<>();

    private FriendManager() {
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    public static boolean add(String name) {
        if (!isValidName(name) || isFriend(name)) return false;
        FRIENDS.put(name.toLowerCase(Locale.ROOT), name);
        return true;
    }

    public static boolean remove(String name) {
        return FRIENDS.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public static boolean isFriend(String name) {
        return name != null && FRIENDS.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static List<String> list() {
        return new ArrayList<>(FRIENDS.values());
    }

    public static void clear() {
        FRIENDS.clear();
    }
}
