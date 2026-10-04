package dev.maro.anubis.module;

import dev.maro.gui.hud.*;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.*;
import dev.maro.module.Category;
import dev.maro.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.List;

/** Maro UI adapter for the recovered Anti Vanish logic. */
public abstract class ClientModule extends dev.maro.module.Module implements HudElement {
    public final BooleanSetting alerts=add(new BooleanSetting("Alerts","Show notification alerts",true));
    public final BooleanSetting chat=add(new BooleanSetting("Chat alerts","Write evidence to your local chat",false));
    public final BooleanSetting sound=add(new BooleanSetting("Sound alerts","Play a chime for corroborated nearby activity",true));
    private final BooleanSetting hud=add(new BooleanSetting("Show HUD","Show recent evidence on the HUD",true));
    private final NumberSetting x=add(new NumberSetting("HUD X","Horizontal position",2,0,100,1));
    private final NumberSetting y=add(new NumberSetting("HUD Y","Vertical position",60,0,100,1));
    private final NumberSetting scale=add(new NumberSetting("HUD scale","Panel size",.8,.5,1.5,.05));
    protected ClientModule(String title,String description) {
        super(title,description,Category.MISC);
        add(new ActionSetting("Place HUD","Drag and scroll to position the evidence panel",()-> {
            setEnabled(true); hud.set(true); mc.setScreen(new HudPlacementScreen(mc.currentScreen,this));
        }));
    }
    public record Row(String tag,String name,String detail) {}
    protected abstract List<Row> panelRows();
    public void announce(Text text) {
        if(alerts.get()) Notifications.push(getName(),text.getString(),Notifications.Type.WARNING,4000);
        if(chat.get()&&mc.player!=null) mc.player.sendMessage(Text.literal("[Maro] ").append(text),false);
    }
    @Override public String hudName(){return getName();}
    private int height(){return 38+Math.max(1,panelRows().size())*31;}
    @Override public float hudScale(){return (float)Math.min(scale.get(),Math.min((mc.getWindow().getScaledWidth()-8)/214.0,(mc.getWindow().getScaledHeight()-8)/(double)height()));}
    @Override public float hudWidth(){return 214*hudScale();}
    @Override public float hudHeight(){return height()*hudScale();}
    private float roomX(){return Math.max(0,mc.getWindow().getScaledWidth()-hudWidth()-8);}
    private float roomY(){return Math.max(0,mc.getWindow().getScaledHeight()-hudHeight()-8);}
    @Override public float hudLeft(){return 4+(float)(roomX()*x.get()/100);}
    @Override public float hudTop(){return 4+(float)(roomY()*y.get()/100);}
    @Override public void hudMove(float left,float top){x.set(roomX()==0?0:Math.clamp((left-4)/roomX()*100.0,0,100));y.set(roomY()==0?0:Math.clamp((top-4)/roomY()*100.0,0,100));}
    @Override public void hudResize(float by){scale.set(scale.get()+by);}
    @Override public void hudReset(){x.reset();y.reset();scale.reset();}
    @Override public void onRender2D(DrawContext ctx,float delta) {
        if(!inGame()||!hud.get()||mc.options.hudHidden) return;
        var rows=panelRows(); boolean editing=mc.currentScreen instanceof HudPlacementScreen;
        if(rows.isEmpty()&&!editing)return;
        ctx.getMatrices().pushMatrix();Fonts.beginRaw();
        try {
            ctx.getMatrices().translate(hudLeft(),hudTop());ctx.getMatrices().scale(hudScale(),hudScale());
            Render2D.roundRect(ctx,0,0,214,height(),9,0xF0151924);
            Render2D.roundOutline(ctx,0,0,214,height(),9,.6f,0x705E6980);
            Fonts.draw(ctx,"ANTI VANISH",11,10,0xFFF2EAE2,true,.7f);
            Fonts.draw(ctx,"Evidence · possible hidden activity",11,25,0xFFADB5C5,false,.5f);
            if(rows.isEmpty()) Fonts.draw(ctx,"Listening for corroborating clues",11,48,0xFFABB6C7,false,.6f);
            for(int i=0;i<rows.size();i++) {
                Row row=rows.get(i);int top=37+i*31;
                Render2D.roundRect(ctx,6,top,202,28,5,0x60374153);
                Fonts.draw(ctx,row.tag,11,top+7,0xFFFFBB80,true,.48f);
                Fonts.draw(ctx,Fonts.trim(row.name,151,true,.7f),49,top+5,0xFFF5F2EC,true,.7f);
                Fonts.draw(ctx,Fonts.trim(row.detail,151,false,.45f),49,top+18,0xFFA6B4C9,false,.45f);
            }
        } finally {Fonts.endRaw();ctx.getMatrices().popMatrix();}
    }
}
