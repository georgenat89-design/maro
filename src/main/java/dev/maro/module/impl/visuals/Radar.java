package dev.maro.module.impl.visuals;

import dev.maro.config.FriendManager;
import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Player radar using tracked entities and vanilla interpolation, with no per-frame world scan. */
public final class Radar extends Module implements HudElement {
    private static final float WIDTH=176,HEIGHT=218,CX=88,CY=106,RADIUS=62,MARGIN=4;
    private static final String[] CARDINALS={"N","E","S","W"};
    private static final int TEXT=0xFFF0F4FA,MUTED=0xFFA1ADBF;
    private final ModeSetting orientation=add(new ModeSetting("Orientation","Heading Up follows your facing direction; North Up keeps north at the top","Heading Up","Heading Up","North Up"));
    private final NumberSetting range=add(new NumberSetting("Range","Horizontal distance in blocks from the center to the outer ring",96,16,512,8).suffix("m"));
    private final ModeSetting markers=add(new ModeSetting("Markers","Player faces, clear dots, or directional arrows","Heads","Heads","Dots","Arrows"));
    private final NumberSetting markerSize=add(new NumberSetting("Marker Size","Size of each player marker",10,6,16,1));
    private final BooleanSetting smooth=add(new BooleanSetting("Smooth Movement","Interpolate player movement between game ticks without delaying your turn",true));
    private final BooleanSetting rings=add(new BooleanSetting("Range Rings","Show the half-range ring and a subtle crosshair",true));
    private final BooleanSetting compass=add(new BooleanSetting("Compass Points","Show north, east, south and west around the radar",true));
    private final BooleanSetting sweep=add(new BooleanSetting("Sweep","A slow, subtle sweep behind the markers",false));
    private final BooleanSetting showNames=add(new BooleanSetting("Names","Label nearby markers when there is space",false));
    private final BooleanSetting height=add(new BooleanSetting("Height Indicators","A small arrow shows players above or below you",true));
    private final BooleanSetting showFriends=add(new BooleanSetting("Show Friends","Include friends in the radar",true));
    private final BooleanSetting invisible=add(new BooleanSetting("Invisible Players","Include invisible players tracked by the game",true));
    private final BooleanSetting spectators=add(new BooleanSetting("Spectators","Include spectator players",false));
    private final BooleanSetting edge=add(new BooleanSetting("Edge Markers","Keep tracked players beyond the range at the edge as directional arrows",false));
    private final NumberSetting heightRange=add(new NumberSetting("Height Range","Maximum vertical difference above or below you",128,8,512,8).suffix("m"));
    private final NumberSetting maximum=add(new NumberSetting("Maximum Players","Draw the nearest players first and keep the radar bounded",64,8,128,8));
    private final ColorSetting playerColor=add(new ColorSetting("Player Color","Color of player dots and marker borders",0xFF9ABBFF));
    private final ColorSetting friendColor=add(new ColorSetting("Friend Color","Color of friends in your Maro friend list",0xFF7DE3BA));
    private final ColorSetting accent=add(new ColorSetting("Accent","Radar details and your own heading",0xFF88DCCD));
    private final ColorSetting background=add(new ColorSetting("Background Color","Color of the radar panel",0xFF111A25));
    private final NumberSetting opacity=add(new NumberSetting("Background Opacity","Panel opacity; markers and text stay readable",88,0,100,1).suffix("%"));
    private final ButtonSetting position=add(new ButtonSetting("Position","Drag to move, scroll to resize, R to reset","Place",()->{
        if(inGame()){setEnabled(true);mc.setScreen(new HudPlacementScreen(mc.currentScreen,this));}
    }));
    private final NumberSetting x=add(new NumberSetting("X","Across the screen",100,0,100,.5).suffix("%"));
    private final NumberSetting y=add(new NumberSetting("Y","Down the screen",5,0,100,.5).suffix("%"));
    private final NumberSetting scale=add(new NumberSetting("Scale","Resize the entire radar; automatically fit smaller screens",1,.55,2,0.05).suffix("x"));
    private final List<SettingSection> sections=List.of(
        SettingSection.of("Display",orientation,range,markers,markerSize,smooth,rings,compass,sweep,showNames),
        SettingSection.of("Players",showFriends,invisible,spectators,height,heightRange,edge,maximum),
        SettingSection.of("Colors",playerColor,friendColor,accent,background,opacity),
        SettingSection.of("Placement",position,x,y,scale));
    private static final class Tracked {
        AbstractClientPlayerEntity player;
        String name;
        boolean friend,visible,edge;
        float x,y,altitude;
        double distance,horizontal;
        Tracked(AbstractClientPlayerEntity player){this.player=player;name=player.getName().getString();}
    }
    private final Map<UUID,Tracked> cache=new HashMap<>();
    private final Set<UUID> selected=new HashSet<>();
    private final ArrayList<AbstractClientPlayerEntity> candidates=new ArrayList<>();
    private final ArrayList<Tracked> tracked=new ArrayList<>();
    private final float[] labelLeft=new float[8],labelTop=new float[8],labelRight=new float[8],labelBottom=new float[8];
    private int labelCount,drawn;
    private UUID nearest;
    private ClientWorld world;
    public record Contact(UUID id,String name,float x,float y,float altitude,double distance,boolean friend,boolean edge){}
    public Radar(){super("Radar","Smooth nearby-player radar with faces, friend colors and height indicators",Category.VISUALS);}
    @Override public List<SettingSection> getSettingSections(){return sections;}
    @Override protected void onEnable(){clear();if(inGame())onTick();}
    @Override protected void onDisable(){clear();}
    private void clear(){cache.clear();candidates.clear();tracked.clear();selected.clear();world=null;nearest=null;drawn=0;}
    private double horizontalSquared(AbstractClientPlayerEntity player){double dx=player.getX()-mc.player.getX(),dz=player.getZ()-mc.player.getZ();return dx*dx+dz*dz;}
    private boolean eligible(AbstractClientPlayerEntity player){
        return player!=mc.player&&!player.isRemoved()&&player.isAlive()&&(spectators.get()||!player.isSpectator())
                &&(invisible.get()||!player.isInvisible())&&(showFriends.get()||!FriendManager.isFriend(player.getName().getString()))
                &&Math.abs(player.getY()-mc.player.getY())<=heightRange.get();
    }
    @Override public void onTick(){
        if(!inGame()){clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        candidates.clear();double padded=range.get()+8;
        for(var player:world.getPlayers())if(eligible(player)&&(edge.get()||horizontalSquared(player)<=padded*padded))candidates.add(player);
        candidates.sort(Comparator.comparingDouble(this::horizontalSquared).thenComparingInt(AbstractClientPlayerEntity::getId));
        tracked.clear();selected.clear();int limit=Math.min(maximum.getInt(),candidates.size());
        for(int i=0;i<limit;i++){
            var player=candidates.get(i);UUID id=player.getUuid();selected.add(id);
            var contact=cache.computeIfAbsent(id,key->new Tracked(player));contact.player=player;
            contact.friend=FriendManager.isFriend(contact.name);tracked.add(contact);
        }
        cache.keySet().removeIf(id->!selected.contains(id));
    }
    /** Screen offsets before the radar's own translation/scale. Angle is a compass bearing. */
    public static double[] project(double dx,double dz,double bearing,double range,double radius){
        double a=Math.toRadians(bearing),c=Math.cos(a),s=Math.sin(a);
        return new double[]{(dx*c+dz*s)*radius/range,(-dx*s+dz*c)*radius/range};
    }
    private static double wrap(double angle){return (angle%360+360)%360;}
    private double heading(float tickDelta){return wrap(mc.player.getYaw(tickDelta)+180);}
    private void update(float tickDelta,double mapBearing){
        Vec3d self=smooth.get()?mc.player.getLerpedPos(tickDelta):mc.player.getEntityPos();
        double a=Math.toRadians(mapBearing),c=Math.cos(a),s=Math.sin(a),r=range.get();
        float safeRadius=mapRadius();
        drawn=0;Tracked closest=null,previous=null;
        for(var contact:tracked){
            var player=contact.player;contact.visible=false;if(!eligible(player))continue;
            Vec3d at=smooth.get()?player.getLerpedPos(tickDelta):player.getEntityPos();
            double dx=at.x-self.x,dz=at.z-self.z,dy=at.y-self.y,d=Math.hypot(dx,dz);
            contact.edge=d>r;if(contact.edge&&!edge.get()||Math.abs(dy)>heightRange.get())continue;
            double factor=safeRadius/Math.max(r,d);
            contact.x=CX+(float)((dx*c+dz*s)*factor);contact.y=CY+(float)((-dx*s+dz*c)*factor);
            contact.altitude=(float)dy;contact.horizontal=d;contact.distance=Math.sqrt(dx*dx+dy*dy+dz*dz);contact.visible=true;drawn++;
            if(closest==null||contact.distance<closest.distance)closest=contact;
            if(player.getUuid().equals(nearest))previous=contact;
        }
        // Keep the footer calm when two players are almost equally near.
        if(previous!=null&&closest!=null&&previous.distance<=closest.distance*1.12+1)closest=previous;
        nearest=closest==null?null:closest.player.getUuid();
    }
    public List<Contact> drawnContacts(){
        var out=new ArrayList<Contact>();for(var c:tracked)if(c.visible)out.add(new Contact(c.player.getUuid(),c.name,c.x-CX,c.y-CY,c.altitude,c.distance,c.friend,c.edge));return List.copyOf(out);
    }
    public int cachedPlayers(){return cache.size();}
    @Override public void onRender2D(DrawContext ctx,float tickDelta){
        if(!inGame()||mc.options.hudHidden)return;
        if(world!=mc.world){clear();return;}
        double heading=heading(tickDelta),mapBearing=orientation.is("Heading Up")?heading:0;update(tickDelta,mapBearing);
        float s=hudScale();var matrices=ctx.getMatrices();matrices.pushMatrix();matrices.translate(hudLeft(),hudTop());matrices.scale(s,s);
        Fonts.beginRaw();Fonts.setMeasureScale(s);
        try{panel(ctx,mapBearing,heading);}finally{Fonts.setMeasureScale(1);Fonts.endRaw();matrices.popMatrix();}
    }
    public void renderPreview(DrawContext ctx){
        double heading=heading(0),bearing=orientation.is("Heading Up")?heading:0;
        var matrices=ctx.getMatrices();matrices.pushMatrix();
        try{matrices.translate(hudLeft(),hudTop());matrices.scale(hudScale(),hudScale());panel(ctx,bearing,heading);}finally{matrices.popMatrix();}
    }
    private void panel(DrawContext c,double mapBearing,double heading){
        int bg=ColorUtil.withAlpha(background.get(),Math.round(opacity.getFloat()*2.55f));
        Render2D.shadow(c,0,2,WIDTH,HEIGHT,10,7,ColorUtil.withAlpha(0xFF000000,Math.round(opacity.getFloat())));
        Render2D.roundRect(c,0,0,WIDTH,HEIGHT,10,bg);Render2D.roundOutline(c,0,0,WIDTH,HEIGHT,10,.7f,0xFF334253);
        Render2D.circle(c,15,16,3,accent.get());Render2D.ring(c,15,16,6,.6f,ColorUtil.withAlpha(accent.get(),70));
        text(c,"Radar",27,16,.84f,true,TEXT);
        Render2D.roundRect(c,122,9,42,15,5,ColorUtil.withAlpha(accent.get(),24));Fonts.drawCentered(c,range.getInt()+"m",143,16.5f,accent.get(),true,.64f);
        Render2D.line(c,12,31,164,31,.6f,0xFF2C394A);
        Render2D.circle(c,CX,CY,RADIUS,ColorUtil.withAlpha(0xFF060C13,Math.round(opacity.getFloat()*1.3f)));
        Render2D.ring(c,CX,CY,RADIUS,.7f,0xFF3A4D5F);
        if(rings.get()){
            float radius=mapRadius();Render2D.ring(c,CX,CY,radius,.5f,0x503E6375);Render2D.ring(c,CX,CY,radius/2,.5f,0x503E6375);
            Render2D.line(c,CX-radius,CY,CX+radius,CY,.5f,0x453E6375);Render2D.line(c,CX,CY-radius,CX,CY+radius,.5f,0x453E6375);
            text(c,range.getInt()/2+"m",CX+4,CY+radius/2+5,.48f,false,0xFF768A9D);
        }
        if(sweep.get()){
            float angle=(System.nanoTime()%8_000_000_000L)/8_000_000_000f*360;
            Render2D.arc(c,CX,CY,RADIUS-2,1.2f,angle-36,36,ColorUtil.withAlpha(accent.get(),0),ColorUtil.withAlpha(accent.get(),75));
        }
        if(compass.get())for(int i=0;i<4;i++){
            double angle=Math.toRadians(i*90-mapBearing);float px=CX+(float)Math.sin(angle)*73,py=CY-(float)Math.cos(angle)*73;
            Fonts.drawCentered(c,CARDINALS[i],px,py,i==0?accent.get():MUTED,true,.62f);
        }
        // Farther markers first; closer heads stay readable where players overlap.
        for(int i=tracked.size()-1;i>=0;i--){var contact=tracked.get(i);if(contact.visible)marker(c,contact,mapBearing);}
        labelCount=0;if(showNames.get())for(var contact:tracked)if(contact.visible&&labelCount<8)name(c,contact);
        Render2D.circle(c,CX,CY,7,0xFF12232C);Render2D.ring(c,CX,CY,7,.7f,ColorUtil.withAlpha(accent.get(),130));
        arrow(c,CX,CY,4,orientation.is("Heading Up")?0:heading,accent.get());
        Render2D.line(c,12,185,164,185,.6f,0xFF2C394A);
        text(c,drawn==1?"1 player nearby":drawn+" players nearby",12,195,.69f,true,TEXT);
        Fonts.drawRight(c,orientation.is("Heading Up")?"VIEW":"NORTH",164,195,MUTED,false,.50f);
        Tracked close=nearest==null?null:cache.get(nearest);
        String detail=close!=null?close.name+" · "+Math.round(close.distance)+"m":"No nearby players";
        text(c,Fonts.trim(detail,152,false,.63f),12,208,.63f,false,close!=null&&close.friend?friendColor.get():MUTED);
    }
    private void marker(DrawContext c,Tracked contact,double mapBearing){
        float size=markerSize.getFloat(),px=contact.x,py=contact.y;int color=contact.friend?friendColor.get():playerColor.get();
        if(contact.edge){arrow(c,px,py,size*.40,Math.toDegrees(Math.atan2(px-CX,-(py-CY))),color);return;}
        Render2D.circle(c,px,py,size*.64f+1,ColorUtil.withAlpha(color,22));
        if(markers.is("Heads")){
            Render2D.roundRect(c,px-size/2-1,py-size/2-1,size+2,size+2,2,0xFF0A111A);
            var matrices=c.getMatrices();matrices.pushMatrix();matrices.translate(px-size/2,py-size/2);
            PlayerSkinDrawer.draw(c,contact.player.getSkin(),0,0,Math.round(size));matrices.popMatrix();
            Render2D.roundOutline(c,px-size/2-1,py-size/2-1,size+2,size+2,2,.8f,color);
        }else if(markers.is("Dots")){
            Render2D.circle(c,px,py,size*.34f+1,0xFF0A111A);Render2D.circle(c,px,py,size*.34f,color);Render2D.circle(c,px-1,py-1,1,0xD0FFFFFF);
        }else arrow(c,px,py,size*.42,wrap(contact.player.getYaw()+180-mapBearing),color);
        if(height.get()&&Math.abs(contact.altitude)>=3){
            float tx=px+size/2+3,ty=py+(contact.altitude>0?-2:2),direction=contact.altitude>0?-1:1;
            Render2D.line(c,tx-1.8f,ty-direction*1.4f,tx,ty+direction*1.4f,.9f,color);
            Render2D.line(c,tx,ty+direction*1.4f,tx+1.8f,ty-direction*1.4f,.9f,color);
        }
    }
    private void name(DrawContext c,Tracked contact){
        if(contact.edge)return;float py=contact.y+markerSize.getFloat()/2+7;
        float dy=py-CY;if(Math.abs(dy)>RADIUS-5)return;
        float halfChord=(float)Math.sqrt((RADIUS-4)*(RADIUS-4)-dy*dy);
        float maxWidth=Math.min(55,2*halfChord-6);if(maxWidth<16)return;
        String label=Fonts.trim(contact.name,maxWidth,true,.5f);float w=Fonts.width(label,true,.5f)+4,left=Math.clamp(contact.x-w/2,CX-halfChord,CX+halfChord-w),top=py-4;
        for(var other:tracked)if(other.visible&&left<other.x+markerSize.getFloat()/2+3&&left+w>other.x-markerSize.getFloat()/2-3&&top<other.y+markerSize.getFloat()/2+3&&top+8>other.y-markerSize.getFloat()/2-3)return;
        if(left<CX+8&&left+w>CX-8&&top<CY+8&&top+8>CY-8)return;
        for(int i=0;i<labelCount;i++)if(left<labelRight[i]&&left+w>labelLeft[i]&&top<labelBottom[i]&&top+8>labelTop[i])return;
        labelLeft[labelCount]=left;labelRight[labelCount]=left+w;labelTop[labelCount]=top;labelBottom[labelCount++]=top+8;
        Render2D.roundRect(c,left,top,w,8,2,0xD90B121B);Fonts.drawCentered(c,label,left+w/2,py,contact.friend?friendColor.get():TEXT,true,.5f);
    }
    private static void arrow(DrawContext c,float x,float y,double radius,double heading,int color){
        var matrices=c.getMatrices();matrices.pushMatrix();matrices.translate(x,y);matrices.rotate((float)Math.toRadians(heading));
        float r=(float)radius;Render2D.line(c,-r*.65f,r*.55f,0,-r,1.4f,color);Render2D.line(c,0,-r,r*.65f,r*.55f,1.4f,color);Render2D.line(c,0,-r*.35f,0,r*.70f,1.1f,color);matrices.popMatrix();
    }
    private static void text(DrawContext c,String s,float x,float y,float scale,boolean bold,int color){Fonts.drawV(c,s,x,y,color,bold,scale);}
    private float mapRadius(){return RADIUS-(float)(markerSize.get()*.71)-(height.get()?5:2);}
    @Override public String hudName(){return "Radar";}
    @Override public float hudScale(){return Math.max(.01f,Math.min(scale.getFloat(),Math.min((mc.getWindow().getScaledWidth()-MARGIN*2)/WIDTH,(mc.getWindow().getScaledHeight()-MARGIN*2)/HEIGHT)));}
    @Override public float hudWidth(){return WIDTH*hudScale();}
    @Override public float hudHeight(){return HEIGHT*hudScale();}
    @Override public float hudLeft(){return MARGIN+Math.round(Math.max(0,mc.getWindow().getScaledWidth()-hudWidth()-MARGIN*2)*x.getFloat()/100);}
    @Override public float hudTop(){return MARGIN+Math.round(Math.max(0,mc.getWindow().getScaledHeight()-hudHeight()-MARGIN*2)*y.getFloat()/100);}
    @Override public void hudMove(float left,float top){
        float roomX=mc.getWindow().getScaledWidth()-hudWidth()-MARGIN*2,roomY=mc.getWindow().getScaledHeight()-hudHeight()-MARGIN*2;
        x.set(roomX<=0?0d:Math.clamp((left-MARGIN)/roomX*100d,0,100));y.set(roomY<=0?0d:Math.clamp((top-MARGIN)/roomY*100d,0,100));
    }
    @Override public void hudResize(float by){scale.set(Math.clamp(Math.round((scale.get()+by)*100)/100d,scale.getMin(),scale.getMax()));}
    @Override public void hudReset(){x.reset();y.reset();scale.reset();}
}
