package dev.maro.anubis;
import dev.maro.config.FriendManager;
public final class FriendStore {
    private static final FriendStore INSTANCE=new FriendStore();
    public static FriendStore getInstance() { return INSTANCE; }
    public boolean isFriend(String name) { return FriendManager.isFriend(name); }
}
