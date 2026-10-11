package dev.maro.gui.spotify;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;

import java.util.HashSet;
import java.util.Set;

/** Screen-local held inputs. Never presses global inventory, attack or item-use bindings. */
final class PhoneMovement {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final KeyBinding[] bindings;
    private final Set<InputUtil.Key> held = new HashSet<>();

    PhoneMovement() {
        var o = client.options;
        bindings = new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey,
            o.jumpKey, o.sneakKey, o.sprintKey};
        // The constructor runs before setScreen clears the ordinary game bindings.
        for (var binding : bindings) {
            var key = key(binding);
            boolean down = key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() >= 0
                && InputUtil.isKeyPressed(client.getWindow(), key.getCode());
            if (binding.isPressed() || down) held.add(key);
        }
    }

    private static InputUtil.Key key(KeyBinding binding) {
        return InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
    }

    boolean key(KeyInput input, boolean down) {
        if (!down) held.remove(InputUtil.fromKeyCode(input));
        for (var binding : bindings) if (binding.matchesKey(input)) {
            if (down) held.add(InputUtil.fromKeyCode(input));
            return true;
        }
        return false;
    }

    void mouse(Click click, boolean down) {
        var key = InputUtil.Type.MOUSE.createFromCode(click.button());
        if (!down) held.remove(key);
        else for (var binding : bindings) if (binding.matchesMouse(click)) { held.add(key); break; }
    }

    PlayerInput read() {
        if (!client.isWindowFocused()) { clear(); return PlayerInput.DEFAULT; }
        return new PlayerInput(down(0), down(1), down(2), down(3), down(4), down(5), down(6));
    }

    private boolean down(int index) { return held.contains(key(bindings[index])); }
    void clear() { held.clear(); }
}
