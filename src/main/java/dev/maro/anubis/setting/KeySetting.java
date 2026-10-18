package dev.maro.anubis.setting;
import java.util.function.BooleanSupplier;
public final class KeySetting implements Setting<Integer> {
    private final dev.maro.setting.KeybindSetting view;
    public KeySetting(String name,int value) { view=new dev.maro.setting.KeybindSetting(name,"Press to inspect; hold to loop",value); }
    public Integer get() { return view.get(); }
    public int value() { return get(); }
    public dev.maro.setting.KeybindSetting view() { return view; }
    public void visibleWhen(BooleanSupplier visible) { view.visible(visible); }
}
