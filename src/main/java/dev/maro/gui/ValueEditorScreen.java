package dev.maro.gui;

import dev.maro.runtime.settings.SettingAdapters.ValueSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Strings and JSON lists, with validation before saving. */
public final class ValueEditorScreen extends Screen {
    private final Screen parent;
    private final ValueSetting setting;
    private TextFieldWidget field;
    private String error="";
    public ValueEditorScreen(Screen parent,ValueSetting setting){super(Text.literal(setting.getName()));this.parent=parent;this.setting=setting;}
    @Override protected void init(){
        int w=Math.min(560,width-32), x=(width-w)/2;
        field=new TextFieldWidget(textRenderer,x,height/2-10,w,22,Text.literal(setting.getName()));
        field.setMaxLength(32767);field.setText(setting.editText());addDrawableChild(field);setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"),b->{try{setting.apply(field.getText());client.setScreen(parent);}catch(RuntimeException e){error=e.getMessage();}}).dimensions(x,height/2+26,w/2-4,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(x+w/2+4,height/2+26,w/2-4,20).build());
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        ctx.fill(0,0,width,height,0xDD10121A);
        ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-54,0xFFECEEF3);
        ctx.drawCenteredTextWithShadow(textRenderer,"Lists accept JSON arrays or entries separated by ;",width/2,height/2-32,0xFF999EAB);
        super.render(ctx,mx,my,delta);
        if(!error.isEmpty())ctx.drawCenteredTextWithShadow(textRenderer,error,width/2,height/2+54,0xFFFF6B7A);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
