package dev.maro.anubis.gui;
import dev.maro.gui.notification.Notifications;
import java.util.UUID;
/** Replace Anubis's full-screen ghost with Maro's native alert presentation. */
public final class StaffGhost {
    private StaffGhost() {}
    public static void watching(UUID id,String name,int others) {
        Notifications.push("Staff nearby",name+(others>0?" +"+others:"")+" · evidence of activity in your area",Notifications.Type.WARNING,5000);
    }
}
