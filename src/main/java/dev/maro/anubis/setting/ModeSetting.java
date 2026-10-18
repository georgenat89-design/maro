package dev.maro.anubis.setting;
import java.util.function.BooleanSupplier;
public final class ModeSetting implements Setting<String> {
    private final dev.maro.setting.ModeSetting view;
    public ModeSetting(String name,String value,String... choices) { view=new dev.maro.setting.ModeSetting(name,"",value,choices); }
    private ModeSetting(dev.maro.setting.ModeSetting view) { this.view=view; }
    public static ModeSetting described(String name,String description,String value,String... choices) {
        return new ModeSetting(new dev.maro.setting.ModeSetting(name,description,value,choices));
    }
    public String get() { return view.get(); }
    public boolean is(String value) { return view.is(value); }
    public dev.maro.setting.ModeSetting view() { return view; }
    public void onChange(Runnable changed) { view.onChange(v->changed.run()); }
    public void visibleWhen(BooleanSupplier visible) { view.visible(visible); }
}
