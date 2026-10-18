package dev.maro.anubis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
public final class CombatBridge {
    public static boolean isBindable(int key) { return key>=0; }
    public static boolean isKeyDown(Window window,int key) { return InputUtil.isKeyPressed(window,key); }
    public static boolean isEating() { var p=MinecraftClient.getInstance().player;return p!=null&&p.isUsingItem(); }
    public static boolean isPhysicallyDown(MinecraftClient client,KeyBinding key) {
        var binding=InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        return binding.getCategory()==InputUtil.Type.MOUSE ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(),binding.getCode())==GLFW.GLFW_PRESS : InputUtil.isKeyPressed(client.getWindow(),binding.getCode());
    }
}
