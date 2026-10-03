package dev.maro.runtime.utils.misc;

import dev.maro.util.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
public final class Keybind {
 private int code;
 private Keybind(int code) { this.code = code; }
 public static Keybind none() { return new Keybind(KeyUtil.NONE); }
 public static Keybind fromKey(int key) { return new Keybind(key); }
 public static Keybind fromButton(int button) { return new Keybind(KeyUtil.mouse(button)); }
 public static Keybind fromCode(int code) { return new Keybind(code); }
 public int code() { return code; }
 public void set(boolean key,int value,int scanCode){code=key?value:KeyUtil.mouse(value);}
 public int getValue() { return code; }
 public boolean isKey() { return code >= 0 && !KeyUtil.isMouse(code); }
 public boolean isSet() { return code != KeyUtil.NONE; }
 public boolean matches(KeyInput input) { return code == input.key(); }
 public boolean matches(MouseInput input) { return code == KeyUtil.mouse(input.button()); }
 public boolean isPressed() {
  if (!isSet()) return false;
  long window = MinecraftClient.getInstance().getWindow().getHandle();
  return isKey() ? GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS : GLFW.glfwGetMouseButton(window, code-KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
 }
 @Override public String toString() { return KeyUtil.name(code); }
 @Override public boolean equals(Object other) { return other instanceof Keybind k && k.code == code; }
 @Override public int hashCode() { return code; }
}
