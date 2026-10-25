package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.HandPlacementRender;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.ViewModel;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A screen-local draft. No setting or player item is written until Save is pressed. */
public final class HandPlacementScreen extends Screen {
    private record Region(float x,float y,float width,float height,Runnable action){
        boolean contains(double px,double py){return px>=x&&py>=y&&px<x+width&&py<y+height;}
        float[] centre(){return new float[]{x+width/2,y+height/2};}
    }
    private final Screen parent;
    private final ViewModel module;
    private final Object world,player;
    private final Map<String,Region> actions=new LinkedHashMap<>();
    private ViewModel.Placement left,right;
    private Arm selected=Arm.RIGHT;
    private boolean linked,dragging,shift,finished,initialized;
    private Perspective perspective;
    private String saveError="";
    private float canvasTop,canvasBottom,pad=14,uiScale=1;
    private int layoutWidth=-1,layoutHeight=-1;

    public HandPlacementScreen(Screen parent,ViewModel module){
        super(Text.literal("Hand placement"));this.parent=parent;this.module=module;
        left=module.placement(Arm.LEFT);right=module.placement(Arm.RIGHT);
        world=net.minecraft.client.MinecraftClient.getInstance().world;
        player=net.minecraft.client.MinecraftClient.getInstance().player;
    }
    public ViewModel module(){return module;}
    public boolean finished(){return finished;}
    public Arm selectedArm(){return selected;}
    public boolean linked(){return linked;}
    public String saveError(){return saveError;}
    public ViewModel.Placement draft(Arm arm){return arm==Arm.LEFT?left:right;}
    public float[] actionPlace(String id){var r=actions.get(id.toUpperCase(Locale.ROOT));return r==null?null:r.centre();}
    public float[] canvasBounds(){return new float[]{pad,canvasTop,Math.max(1,width-pad*2),Math.max(1,canvasBottom-canvasTop)};}
    public float[] regionBounds(Arm arm){float gap=10*uiScale,w=(width-pad*2-gap)/2;return new float[]{arm==Arm.LEFT?pad:pad+w+gap,canvasTop,w,Math.max(1,canvasBottom-canvasTop)};}

    @Override protected void init(){
        if(!initialized){
            initialized=true;perspective=client.options.getPerspective();client.options.setPerspective(Perspective.FIRST_PERSON);
            KeyBinding.unpressAll();client.mouse.unlockCursor();
        }
        layout();
    }
    private void layout(){
        if(layoutWidth==width&&layoutHeight==height)return;
        layoutWidth=width;layoutHeight=height;
        uiScale=Math.min(1,height/450f);
        pad=Math.max(8*uiScale,Math.min(16*uiScale,width*.025f));
        canvasTop=98*uiScale;canvasBottom=Math.max(canvasTop+32*uiScale,height-78*uiScale);
        actions.clear();
        float saveW=Math.min(112*uiScale,width*.3f),buttonH=24*uiScale,top=62*uiScale;
        actions.put("SAVE",new Region(width-pad-saveW,12*uiScale,saveW,buttonH,this::save));
        float available=width-pad*2,gap=6*uiScale,w=(available-gap*2)/3;
        actions.put("RESET_SELECTED",new Region(pad,top,w,buttonH,()->setDraft(selected,ViewModel.Placement.defaults())));
        actions.put("RESET_BOTH",new Region(pad+w+gap,top,w,buttonH,()->{left=right=ViewModel.Placement.defaults();}));
        actions.put("LINK",new Region(pad+(w+gap)*2,top,w,buttonH,()->linked=!linked));
        for(var arm:Arm.values()){var r=regionBounds(arm);actions.put(arm.name(),new Region(r[0],r[1],r[2],r[3],()->selected=arm));}
    }
    private void setDraft(Arm arm,ViewModel.Placement p){if(arm==Arm.LEFT)left=p;else right=p;}
    private static double clamp(double n,double low,double high){return Math.clamp(n,low,high);}
    private static double edited(double old,double delta,double low,double high){return delta==0?old:clamp(old+delta,low,high);}
    private void adjust(double dx,double dy,double dz,double ds){
        var old=draft(selected);
        var next=new ViewModel.Placement(edited(old.x(),dx,-2,2),edited(old.y(),dy,-2,2),
                edited(old.depth(),dz,-1.6,.45),edited(old.scale(),ds,.15,3),old.rotX(),old.rotY(),old.rotZ());
        setDraft(selected,next);
        if(linked){
            var arm=selected.getOpposite();var other=draft(arm);
            setDraft(arm,new ViewModel.Placement(edited(other.x(),-(next.x()-old.x()),-2,2),
                    edited(other.y(),next.y()-old.y(),-2,2),edited(other.depth(),next.depth()-old.depth(),-1.6,.45),
                    edited(other.scale(),next.scale()-old.scale(),.15,3),other.rotX(),other.rotY(),other.rotZ()));
        }
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(click.button()!=0)return true;
        for(var entry:actions.entrySet())if(entry.getValue().contains(click.x(),click.y())){
            entry.getValue().action.run();
            dragging=entry.getKey().equals("LEFT")||entry.getKey().equals("RIGHT");return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){
        if(dragging&&click.button()==0){
            if(shift)adjust(0,0,-dy/Math.max(1,height)*1.5,0);
            else{
                var movement=HandPlacementRender.dragDelta(selected,draft(selected),dx,dy,width,height);
                adjust(movement[0],movement[1],0,0);
            }
        }
        return true;
    }
    @Override public boolean mouseReleased(Click click){dragging=false;return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x>=pad&&x<width-pad&&y>=canvasTop&&y<canvasBottom)adjust(0,0,0,vertical*.05);
        return true;
    }
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        if(input.key()==GLFW.GLFW_KEY_LEFT_SHIFT||input.key()==GLFW.GLFW_KEY_RIGHT_SHIFT)shift=true;
        return true;
    }
    @Override public boolean keyReleased(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_LEFT_SHIFT||input.key()==GLFW.GLFW_KEY_RIGHT_SHIFT)shift=false;
        return true;
    }
    @Override public void tick(){
        if(client.world!=world||client.player!=player){finished=true;client.setScreen(null);}
        if(!client.isWindowFocused()){dragging=false;shift=false;KeyBinding.unpressAll();}
    }
    private void save(){
        var savedLeft=module.placement(Arm.LEFT);var savedRight=module.placement(Arm.RIGHT);
        module.commitPlacement(Arm.LEFT,left);module.commitPlacement(Arm.RIGHT,right);
        if(!ConfigManager.save(ConfigManager.getCurrent())){
            module.commitPlacement(Arm.LEFT,savedLeft);module.commitPlacement(Arm.RIGHT,savedRight);
            saveError="Couldn't save the configuration. Your draft is still open; try again.";return;
        }
        ConfigManager.saveClient();leave();
    }
    private void leave(){
        finished=true;dragging=shift=false;
        if(parent instanceof ClickGuiScreen gui){gui.showSettings(module);gui.selectPreviewTab(false);}
        client.setScreen(parent);
    }
    @Override public void close(){leave();}
    @Override public void removed(){
        finished=true;dragging=shift=false;KeyBinding.unpressAll();HandPlacementRender.release();
        if(perspective!=null)client.options.setPerspective(perspective);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        if(dev.maro.render.capture.CapturePass.active())return;
        Theme.update();layout();float alpha=Render2D.getAlpha();Render2D.setAlpha(1);Fonts.beginRaw();
        try{
            float u=uiScale;
            Render2D.roundRect(ctx,pad-5*u,6*u,width-pad*2+10*u,canvasTop-16*u,Theme.PANEL_RADIUS*u,
                    dev.maro.util.ColorUtil.withAlpha(Theme.PANEL,0xDD));
            float titleScale=Math.min(1.2f*u,(width-pad*2-125*u)/Math.max(1,Fonts.width(ctx,"HAND PLACEMENT",true,1)));
            Fonts.draw(ctx,"HAND PLACEMENT",pad,12*u,Theme.TEXT,true,Math.max(.6f*u,titleScale));
            Fonts.draw(ctx,"Live first-person editor",pad,37*u,Theme.TEXT_MUTED,false,.85f*u);
            for(var entry:actions.entrySet()){
                String id=entry.getKey();if(id.equals("LEFT")||id.equals("RIGHT"))continue;
                String label=switch(id){case "SAVE"->"Save & done";case "RESET_SELECTED"->"Reset selected";case "RESET_BOTH"->"Reset both";default->"Linked: "+(linked?"on":"off");};
                var r=entry.getValue();boolean over=r.contains(mx,my),accent=id.equals("SAVE")||id.equals("LINK")&&linked;
                Render2D.roundRect(ctx,r.x,r.y,r.width,r.height,Theme.CONTROL_RADIUS*u,accent?Theme.accent(0xD0):over?Theme.CARD_HOVER:Theme.PANEL);
                Render2D.roundOutline(ctx,r.x,r.y,r.width,r.height,Theme.CONTROL_RADIUS*u,Render2D.px(ctx),accent?Theme.accent(0xA0):Theme.BORDER);
                float type=Math.min(.9f*u,(r.width-12*u)/Math.max(1,Fonts.width(ctx,label,false,1)));
                Fonts.drawCentered(ctx,label,r.x+r.width/2,r.y+r.height/2,Theme.TEXT,false,type);
            }
            for(var arm:Arm.values()){
                var r=regionBounds(arm);boolean active=selected==arm;var p=draft(arm);
                if(active)Render2D.roundRect(ctx,r[0],r[1],r[2],r[3],Theme.PANEL_RADIUS*u,Theme.accent(0x0A));
                Render2D.roundOutline(ctx,r[0],r[1],r[2],r[3],Theme.PANEL_RADIUS*u,Render2D.px(ctx),active?Theme.accent(0xC0):Theme.BORDER);
                String label=(arm==Arm.LEFT?"Left hand":"Right hand")+String.format(Locale.ROOT,"  %.0f%%",p.scale()*100);
                float labelW=Math.min(r[2]-12*u,Fonts.width(ctx,label,active,.85f*u)+16*u);
                Render2D.roundRect(ctx,r[0]+6*u,r[1]+6*u,labelW,22*u,Theme.CONTROL_RADIUS*u,
                        dev.maro.util.ColorUtil.withAlpha(Theme.PANEL,0xDD));
                Fonts.drawV(ctx,Fonts.trim(ctx,label,labelW-16*u,active,.85f*u),r[0]+14*u,r[1]+17*u,
                        active?Theme.accent():Theme.TEXT_DIM,active,.85f*u);
            }
            float footer=canvasBottom+9*u;
            Render2D.roundRect(ctx,pad,footer,width-pad*2,height-footer-8*u,Theme.CONTROL_RADIUS*u,dev.maro.util.ColorUtil.withAlpha(Theme.PANEL,0xE5));
            String instruction="Drag to move | Scroll to resize | Shift + drag for depth";
            float type=Math.min(.85f*u,(width-pad*2-16*u)/Math.max(1,Fonts.width(ctx,instruction,false,1)));
            Fonts.drawCentered(ctx,instruction,width/2f,footer+13*u,Theme.TEXT_DIM,false,type);
            var p=draft(selected);String readout=String.format(Locale.ROOT,"%s hand   X %+.2f   Y %+.2f   Depth %+.2f   Size %.0f%%",selected==Arm.LEFT?"Left":"Right",p.x(),p.y(),p.depth(),p.scale()*100);
            Fonts.drawCentered(ctx,readout,width/2f,footer+31*u,Theme.TEXT_MUTED,false,Math.min(.8f*u,(width-pad*2-16*u)/Math.max(1,Fonts.width(ctx,readout,false,1))));
            if(!saveError.isEmpty())Fonts.drawCentered(ctx,Fonts.trim(ctx,saveError,width-pad*2-12*u,false,.7f*u),width/2f,footer+47*u,Theme.RED,false,.7f*u);
            if(client.player==null)Fonts.drawCentered(ctx,"Join a world to view your hands",width/2f,(canvasTop+canvasBottom)/2,Theme.TEXT,false,.9f*u);
        }finally{Fonts.endRaw();Render2D.setAlpha(alpha);}
    }
}
