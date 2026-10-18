package dev.maro.anubis.setting;
import java.util.function.BooleanSupplier;
public final class StringSetting implements Setting<String> {
    private dev.maro.setting.TextSetting view;
    public StringSetting(String name,String value) { view=new dev.maro.setting.TextSetting(name,"",value,512,""); }
    public StringSetting hint(String hint) { view=new dev.maro.setting.TextSetting(view.getName(),hint,view.get(),512,hint);return this; }
    public String get() { return view.get(); }
    public dev.maro.setting.TextSetting view() { return view; }
    public void onChange(Runnable changed) { view.onChange(v->changed.run()); }
    public void visibleWhen(BooleanSupplier visible) { view.visible(visible); }
}
