package dev.maro.util;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Bind codes: {@link #NONE} for unbound, GLFW key codes for keys and
 * {@link #MOUSE_OFFSET} + button for mouse buttons.
 */
public final class KeyUtil {
    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = 1000;

    private KeyUtil() {
    }

    public static int mouse(int button) {
        return MOUSE_OFFSET + button;
    }

    public static boolean isMouse(int code) {
        return code >= MOUSE_OFFSET;
    }

    public static String name(int code) {
        if (code == NONE) return "None";
        if (isMouse(code)) return "Mouse " + (code - MOUSE_OFFSET + 1);
        switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LAlt";
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "Caps";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_PAGE_UP: return "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PgDn";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            default: break;
        }
        try {
            String s = InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
            return s.length() == 1 ? s.toUpperCase() : s;
        } catch (Throwable t) {
            return "Key " + code;
        }
    }
}
