package dev.maro.anubis.setting;
import java.util.List;
import java.util.function.BooleanSupplier;
public final class StringListSetting implements Setting<List<String>> {
    private final dev.maro.runtime.settings.Setting<List<String>> source;
    private BooleanSupplier visibility=()->true;
    public StringListSetting(String name,String... values) { source=new dev.maro.runtime.settings.StringListSetting.Builder().name(name).defaultValue(values).visible(()->visibility.getAsBoolean()).build(); }
    public List<String> get() { return source.get(); }
    public dev.maro.setting.Setting<?> view() { return dev.maro.runtime.settings.SettingAdapters.adapt(source); }
    public void visibleWhen(BooleanSupplier visible) { visibility=visible; }
    public StringListSetting itemPicker() { return this; }
}
