package dev.maro.anubis.util.staff;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StaffNotifier;
import dev.maro.anubis.chat.ModuleChat;
import net.minecraft.world.GameMode;
import java.util.UUID;
/** Use Maro's editable staff list. The Anubis Staff List module is not ported. */
public final class StaffContext {
    private StaffContext() {}
    public static boolean isStaffName(String name){var m=ModuleManager.get(StaffNotifier.class);return m!=null&&m.isStaffName(name);}
    public static boolean isInYourRegion(UUID id){var m=ModuleManager.get(StaffNotifier.class);return m!=null&&m.isInYourRegion(id);}
    public static boolean alarmedRecently(UUID id){var m=ModuleManager.get(StaffNotifier.class);return m!=null&&m.alarmedRecently(id);}
    public static ModuleChat.Body switchedTo(ModuleChat.Body body,String mode){
        body.text("switched to ");return GameMode.SPECTATOR.getId().equals(mode)?body.danger(mode):body.name(mode);
    }
}
