package dev.maro.runtime.gui;
import com.google.gson.JsonParser;
import dev.maro.setting.Setting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
final class ScalarEditorScreen extends Screen {
    private final Screen parent;private final Setting<?> setting;private String error="";
    ScalarEditorScreen(Screen parent,Setting<?> setting){super(Text.literal(setting.getName()));this.parent=parent;this.setting=setting;}
    @Override protected void init(){
        var field=new TextFieldWidget(textRenderer,width/2-120,height/2-12,240,22,title);field.setText(setting.toJson().toString());addDrawableChild(field);setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"),b->{try{setting.fromJson(JsonParser.parseString(field.getText()));close();}catch(RuntimeException e){error="Enter a valid value";}}).dimensions(width/2-120,height/2+20,116,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(width/2+4,height/2+20,116,20).build());
    }
    @Override public void render(DrawContext ctx,int x,int y,float delta){ctx.fill(0,0,width,height,0xDD10121A);ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-42,0xFFECEEF3);super.render(ctx,x,y,delta);if(!error.isEmpty())ctx.drawCenteredTextWithShadow(textRenderer,error,width/2,height/2+50,0xFFFF6B7A);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
