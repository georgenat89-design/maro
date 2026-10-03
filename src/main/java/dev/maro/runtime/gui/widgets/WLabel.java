package dev.maro.runtime.gui.widgets;

public final class WLabel extends WWidget {
 public WLabel(String text){this.text=text;}
 public void set(String text){this.text=text;}
 public WLabel color(dev.maro.runtime.utils.render.color.Color color){ink=color.getPacked();return this;}
}
