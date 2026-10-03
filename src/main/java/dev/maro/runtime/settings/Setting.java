package dev.maro.runtime.settings;

import java.util.function.*;
import java.util.*;
public class Setting<T> {
 public final String name, description; public final T defaultValue;
 private T value; public final BooleanSupplier visible; public final Consumer<T> changed;
 private final List<Consumer<T>> observers=new ArrayList<>();
 private final Predicate<T> validator;
 public final double min, max, step; public final Runnable action; public final Supplier<String[]> choices;
 public Setting(Builder<T, ?> b) { name=b.name;description=b.description;defaultValue=copy(b.defaultValue);value=copy(defaultValue);visible=b.visible;changed=b.changed;min=b.min;max=b.max;step=b.step;action=b.action;choices=b.choices;validator=b.validator; }
 @SuppressWarnings("unchecked") private static <T> T copy(T value) {
  if (value instanceof List<?> list) return (T)new ArrayList<>(list);
  if (value instanceof dev.maro.runtime.utils.render.color.SettingColor c) return (T)new dev.maro.runtime.utils.render.color.SettingColor(c);
  return value;
 }
 public T get() { return value; }
 public boolean set(T next) {
  if (next == null) return false;
  if (!validator.test(next)) throw new IllegalArgumentException("Invalid value for " + name);
  if (next instanceof Number n) {
   if (!Double.isFinite(n.doubleValue())) return false;
   double v = Math.max(min, Math.min(max, n.doubleValue()));
   @SuppressWarnings("unchecked") T checked = (T)(next instanceof Integer ? (Object)(int)Math.round(v) : (Object)v);
   next = checked;
  }
  if (Objects.equals(value,next)) return false;
  value=copy(next); if(changed != null) changed.accept(value); for(var observer:List.copyOf(observers))observer.accept(value); return true;
 }
 public void observe(Consumer<T> observer){observers.add(observer);}
 public void reset() { set(copy(defaultValue)); }
 public Setting<T> fromTag(net.minecraft.nbt.NbtCompound tag){
  tag.getString("value").ifPresent(v->{try{SettingAdapters.adapt(this).fromJson(com.google.gson.JsonParser.parseString(v));}catch(RuntimeException ignored){}});
  return this;
 }
 public abstract static class Builder<T, B extends Builder<T,B>> {
  protected String name="",description=""; protected T defaultValue;
  protected BooleanSupplier visible=()->true; protected Consumer<T> changed;
  protected Predicate<T> validator=value->true;
  protected double min=-1.0e9,max=1.0e9,step=.01; protected Runnable action; protected Supplier<String[]> choices;
  @SuppressWarnings("unchecked") private B self(){return (B)this;}
  public B name(String v){name=v;return self();} public B description(String v){description=v;return self();}
  public B defaultValue(T v){defaultValue=v;return self();} public B visible(BooleanSupplier v){visible=v;return self();}
  public B onChanged(Consumer<T> v){changed=v;return self();} public B min(double v){min=v;return self();} public B max(double v){max=v;return self();}
  public B range(double a,double b){min=a;max=b;return self();}
  public B sliderRange(double a,double b){if(min == -1e9) min=Math.min(0,a);if(max == 1e9)max=Math.max(b,b*4);return self();}
  public B decimalPlaces(int v){step=Math.pow(10,-v);return self();}
  public B action(Runnable v){action=v;return self();}
  public Setting<T> build(){return new Setting<>(this);}
 }
}
