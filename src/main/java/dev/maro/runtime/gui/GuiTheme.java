package dev.maro.runtime.gui;

import dev.maro.runtime.gui.widgets.*;
import dev.maro.runtime.gui.widgets.containers.*;
import dev.maro.runtime.gui.widgets.pressable.*;
import dev.maro.runtime.gui.widgets.input.*;
public final class GuiTheme {
 public WVerticalList verticalList(){return new WVerticalList();}
 public WHorizontalList horizontalList(){return new WHorizontalList();}
 public WTable table(){return new WTable();}
 public WButton button(String text){return new WButton(text);}
 public WMinus minus(){return new WMinus();}
 public WCheckbox checkbox(boolean checked){return new WCheckbox(checked);}
 public WTextBox textBox(String text,String placeholder){return new WTextBox(text);}
 public WLabel label(String text){return new WLabel(text);}
 public WLabel label(String text,double width){return label(text);}
 public WLabel horizontalSeparator(){return label("");}
 public WLabel horizontalSeparator(String title){return label(title);}
 public dev.maro.runtime.utils.render.color.Color textSecondaryColor(){return new dev.maro.runtime.utils.render.color.Color(160,169,187);}
 public WWidget settings(dev.maro.runtime.settings.Settings settings){return new SettingsWidget(settings);}
 public static final class SettingsWidget extends WVerticalList {public final dev.maro.runtime.settings.Settings settings;SettingsWidget(dev.maro.runtime.settings.Settings settings){this.settings=settings;}}
}
