package dev.maro.runtime.events.meteor;

    import net.minecraft.client.input.MouseInput;
    import dev.maro.runtime.utils.misc.input.KeyAction;
    public final class MouseClickEvent extends dev.maro.runtime.events.Cancellable {
      public final MouseInput input; public final KeyAction action;
      public MouseClickEvent(MouseInput input, int action) { this.input = input; this.action = KeyAction.of(action); }
      public int button() { return input.button(); }
    }
