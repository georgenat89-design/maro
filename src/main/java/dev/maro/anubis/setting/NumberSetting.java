package dev.maro.anubis.setting;
import java.util.function.BooleanSupplier;
public final class NumberSetting implements Setting<Float> {
    private dev.maro.setting.NumberSetting view;
    public NumberSetting(String name,float value,float min,float max) { this(name,"",value,min,max); }
    public NumberSetting(String name,String description,float value,float min,float max) { view=new dev.maro.setting.NumberSetting(name,description,value,min,max,.1); }
    public NumberSetting step(float step) { view=new dev.maro.setting.NumberSetting(view.getName(),view.getDescription(),view.get(),view.getMin(),view.getMax(),step);return this; }
    public Float get() { return view.getFloat(); }
    public int getInt() { return view.getInt(); }
    public String formatted() { return view.format(); }
    public dev.maro.setting.NumberSetting view() { return view; }
    public void visibleWhen(BooleanSupplier visible) { view.visible(visible); }
}
