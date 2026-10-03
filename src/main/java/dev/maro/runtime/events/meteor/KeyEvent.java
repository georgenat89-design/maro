package dev.maro.runtime.events.meteor;

    import net.minecraft.client.input.KeyInput;
    import dev.maro.runtime.utils.misc.input.KeyAction;
    public final class KeyEvent extends dev.maro.runtime.events.Cancellable {
      public final KeyInput input; public final KeyAction action;
      public KeyEvent(KeyInput input, int action) { this.input = input; this.action = KeyAction.of(action); }
      public int key() { return input.key(); }
    }
