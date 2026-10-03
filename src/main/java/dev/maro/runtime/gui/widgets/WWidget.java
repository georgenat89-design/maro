package dev.maro.runtime.gui.widgets;

import java.util.*;
import dev.maro.setting.SettingSection;
import dev.maro.setting.ActionSetting;
import dev.maro.runtime.gui.GuiTheme;
public class WWidget {
 public final List<WWidget> children=new ArrayList<>();
 public GuiTheme theme=new GuiTheme();public String text="";public int ink=0xFFECEEF3;public boolean horizontal;
 public <T extends WWidget> Cell<T> add(T widget){children.add(widget);return new Cell<>(widget);}
 public void clear(){children.clear();}
 public void collect(SettingSection section){
  if(this instanceof dev.maro.runtime.gui.widgets.pressable.WButton button)section.add(new ActionSetting(button.text,"",()->{if(button.action!=null)button.action.run();}));
  for(var child:children)child.collect(section);
 }
 public static final class Cell<T extends WWidget>{
  private final T widget;Cell(T widget){this.widget=widget;}
  public T widget(){return widget;}
  public Cell<T> expandX(){return this;}public Cell<T> expandCellX(){return this;}public Cell<T> minWidth(double width){return this;}
 }
}
