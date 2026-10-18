package dev.maro.anubis.setting;
import java.util.function.BooleanSupplier;
public final class BooleanSetting implements Setting<Boolean> {
    private final dev.maro.setting.BooleanSetting view;
    public BooleanSetting(String name, boolean value) { this(name,"",value); }
    public BooleanSetting(String name,String description,boolean value) { view=new dev.maro.setting.BooleanSetting(name,description,value); }
    public Boolean get() { return view.get(); }
    public dev.maro.setting.BooleanSetting view() { return view; }
    public void onChange(Runnable changed) { view.onChange(v->changed.run()); }
    public void visibleWhen(BooleanSupplier visible) { view.visible(visible); }
}
