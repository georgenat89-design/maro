package dev.maro.runtime.gui.widgets.containers;

import dev.maro.runtime.gui.widgets.WWidget;
public final class WTable extends WVerticalList {
 private WHorizontalList current;
 @Override public <T extends WWidget> WWidget.Cell<T> add(T widget){if(current==null){current=new WHorizontalList();super.add(current);}return current.add(widget);}
 public void row(){current=null;}
}
