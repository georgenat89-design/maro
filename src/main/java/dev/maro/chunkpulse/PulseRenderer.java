package dev.maro.chunkpulse;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.impl.visuals.RedstoneBypass;
import dev.maro.render.esp.*;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

public final class PulseRenderer {
    public static int color(ClusterEngine.Tier tier){return switch(tier){case LOW->0xFFFFD54F;case MEDIUM->0xFFFF963D;case HIGH->0xFFFF4856;case HUGE->0xFFE64DFF;};}
    /** END_MAIN is the supported 1.21.11 post-translucent world event. Renderer3D batches custom layers. */
    public static void world(Renderer3D renderer,TileTracker tracker,PulseConfig config,double playerY,long now){
        var camera=renderer.camera();double range=config.renderDistanceChunks*16d;
        var player=MinecraftClient.getInstance().player;var guide=tracker.discovery()==null||player==null?null:tracker.discovery().guidance(new SelfActivityFilter.Position(player.getX(),player.getY(),player.getZ()));
        var nearby=tracker.visible(now,config).stream().filter(t->Math.hypot(t.x()*16+8-camera.x,t.z()*16+8-camera.z)<range).sorted(Comparator.comparingDouble(t->Math.hypot(t.x()*16+8-camera.x,t.z()*16+8-camera.z))).limit(config.maxRenderChunks).toList();
        var merged=new HashMap<Long,TileTracker.Tile>();for(var tile:nearby)merged.put(tile.key(),tile);
        renderer.throughWalls(config.seeThrough);
        for(var t:nearby){
            double x=t.x()*16d,z=t.z()*16d,y=t.plane(config,playerY)+(config.seeThrough?0:.005);
            if(!BlockEspRenderer.inView(x,y-.02,z,x+16,y+.02,z+16,camera))continue;
            double distance=Math.hypot(x+8-camera.x,z+8-camera.z),edgeFade=Math.clamp((range-distance)/16,0,1);
            double fade=t.fade(now,config);boolean ghost=tracker.found(t)||fade<=0;
            boolean dig=!ghost&&guide!=null&&guide.mode()!=ClusterDiscovery.Mode.APPROACH&&guide.target().footprint().contains(t.key());
            int rgb=ghost?0xFF9299A6:config.tierColors.getOrDefault(t.tier(),color(t.tier()));
            double beat=!config.pulse||ghost?1:dig?.8+.2*Math.sin(now*Math.PI*2/700):t.tier().ordinal()>=2?.9+.1*Math.sin(now*Math.PI*2/1000):1;
            float alpha=(float)((ghost?config.historyAlpha:(dig?.4:config.fillAlpha)*fade*beat)*edgeFade);
            int fill=config.simpleTiles&&!ghost?0xFF20252D:rgb;
            renderer.quad(x,y,z,x+16,y,z,x+16,y,z+16,x,y,z+16,new Color(ColorUtil.mulAlpha(fill,alpha)));
            var line=new Color(ColorUtil.mulAlpha(rgb,(float)((ghost?config.historyAlpha:dig?1:config.borderAlpha*fade)*edgeFade)));
            int edges=TileTracker.edges(t,merged,config,playerY);
            renderer.lineWidth(dig?3.5f:tierPerimeter(t,merged)?2f:1f);
            if((edges&1)!=0)renderer.line(x,y,z,x+16,y,z,line);
            if((edges&2)!=0)renderer.line(x+16,y,z,x+16,y,z+16,line);
            if((edges&4)!=0)renderer.line(x+16,y,z+16,x,y,z+16,line);
            if((edges&8)!=0)renderer.line(x,y,z+16,x,y,z,line);
        }
    }
    private static boolean tierPerimeter(TileTracker.Tile tile,Map<Long,TileTracker.Tile> tiles){return tiles.containsKey(SignalEvent.key(tile.x()+1,tile.z()))||tiles.containsKey(SignalEvent.key(tile.x()-1,tile.z()))||tiles.containsKey(SignalEvent.key(tile.x(),tile.z()+1))||tiles.containsKey(SignalEvent.key(tile.x(),tile.z()-1));}
    public static void labels(DrawContext ctx,TileTracker tracker,PulseConfig config){
        var mc=MinecraftClient.getInstance();if(mc.player==null)return;var camera=mc.gameRenderer.getCamera().getCameraPos();long now=System.currentTimeMillis();
        for(var t:tracker.visible(now,config)){
            boolean found=tracker.found(t);if(!config.showLabels&&!found)continue;
            double x=t.x()*16+8,y=t.plane(config,mc.player.getY())+.15,z=t.z()*16+8;
            if(Math.hypot(x-camera.x,z-camera.z)>config.renderDistanceChunks*16||!BlockEspRenderer.inView(x-8,y-.1,z-8,x+8,y+.1,z+8,camera))continue;
            if(!config.seeThrough&&mc.world.raycast(new net.minecraft.world.RaycastContext(camera,new net.minecraft.util.math.Vec3d(x,y,z),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player)).getType()!=net.minecraft.util.hit.HitResult.Type.MISS)continue;
            var at=BlockEspRenderer.toScreen(x,y,z,camera,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight());if(at==null||at[0]<0||at[0]>ctx.getScaledWindowWidth()||at[1]<0||at[1]>ctx.getScaledWindowHeight())continue;
            String text=(found?"FOUND":t.fade(now,config)>0?t.tier().name():"HISTORY")+" "+Math.round(t.score())+" y"+t.minY()+".."+t.maxY();float w=Fonts.width(ctx,text,true,.65f);
            Render2D.roundRect(ctx,at[0]-w/2-5,at[1]-6,w+10,14,4,0xCF0C1420);Fonts.draw(ctx,text,at[0]-w/2,at[1]-3,found?0xFF9299A6:color(t.tier()),true,.65f);
        }
    }
    public static void nearest(DrawContext ctx,TileTracker tracker,PulseConfig config){
        if(tracker.discovery()!=null)guidance(ctx,tracker.discovery(),config);
    }
    public static void guidance(DrawContext ctx,ClusterDiscovery discovery,PulseConfig config){
        var p=MinecraftClient.getInstance().player;if(p==null)return;
        var g=discovery.guidance(new SelfActivityFilter.Position(p.getX(),p.getY(),p.getZ()));if(g==null||g.mode()==ClusterDiscovery.Mode.APPROACH&&!config.nearestHud)return;
        float width=Math.min(400,ctx.getScaledWindowWidth()-24),height=70,x=(ctx.getScaledWindowWidth()-width)/2,y=ctx.getScaledWindowHeight()<260?8:Math.max(8,Math.min(ctx.getScaledWindowHeight()*.65f,ctx.getScaledWindowHeight()-110));if(width<180)return;
        int rgb=config.tierColors.getOrDefault(g.target().cluster().tier(),color(g.target().cluster().tier()));
        Render2D.shadow(ctx,x,y,width,height,9,8,0x55000000);Render2D.roundRect(ctx,x,y,width,height,9,0xED0C1420);Render2D.roundOutline(ctx,x,y,width,height,9,1,ColorUtil.mulAlpha(rgb,.8f));
        String title=switch(g.mode()){case DIG_DOWN->"DIG DOWN";case ALIGN->"TUNNEL TO ACTIVITY";case APPROACH->"HIDDEN ACTIVITY";};
        float scale=Math.min(.9f,(width-66)/Math.max(1,Fonts.width(ctx,title,true,1)));Fonts.draw(ctx,title,x+46,y+10,rgb,true,scale);
        double angle=g.mode()==ClusterDiscovery.Mode.DIG_DOWN?Math.PI:Math.atan2(-g.dx(),g.dz())-Math.toRadians(MinecraftClient.getInstance().gameRenderer.getCamera().getYaw());
        float ax=(float)Math.sin(angle)*10,ay=-(float)Math.cos(angle)*10,cx=x+25,cy=y+22;
        Render2D.line(ctx,cx-ax,cy-ay,cx+ax,cy+ay,2,rgb);Render2D.line(ctx,cx+ax,cy+ay,cx+ax*.3f+ay*.5f,cy+ay*.3f-ax*.5f,2,rgb);Render2D.line(ctx,cx+ax,cy+ay,cx+ax*.3f-ay*.5f,cy+ay*.3f+ax*.5f,2,rgb);
        String detail=switch(g.mode()){case DIG_DOWN->"~"+Math.round(g.depth())+" BLOCKS BELOW";case ALIGN->g.offset()+" TO CENTRE";case APPROACH->Math.round(g.horizontal())+" BLOCKS / "+Math.round(g.depth())+" BELOW";};
        Fonts.draw(ctx,detail,x+46,y+28,0xFFF2F5FA,true,Math.min(.77f,(width-58)/Math.max(1,Fonts.width(ctx,detail,true,1))));
        var c=g.target().cluster();String band="y"+c.minY()+".."+c.maxY()+" / "+c.tier()+" / "+g.target().id();
        if(g.mode()==ClusterDiscovery.Mode.APPROACH)band=g.offset()+" / "+band;
        Fonts.draw(ctx,band,x+13,y+51,0xFF96ADC3,false,Math.min(.64f,(width-26)/Math.max(1,Fonts.width(ctx,band,false,1))));
    }
    public static void hud(DrawContext ctx,RedstoneBypass module,boolean active,int limit){
        if(ctx.getScaledWindowHeight()<70)return;
        var local=MinecraftClient.getInstance().player;
        if(ctx.getScaledWindowHeight()<260&&local!=null&&module.discovery().guidance(new SelfActivityFilter.Position(local.getX(),local.getY(),local.getZ()))!=null)return;
        float y=Math.max(8,Math.min(68,ctx.getScaledWindowHeight()-94));
        limit=Math.min(limit,Math.max(1,(int)((ctx.getScaledWindowHeight()-y-47)/43)));
        var shown=active?(module.discoveryReady()?module.discovery().views().stream().filter(v->v.state()==ClusterDiscovery.State.HIDDEN||module.config().showHistory).map(ClusterDiscovery.View::cluster):module.clusters().stream()).filter(module::visible).sorted(Comparator.comparingDouble(ClusterEngine.Cluster::score).reversed()).limit(limit).toList():List.<ClusterEngine.Cluster>of();
        float width=Math.min(302,ctx.getScaledWindowWidth()-24),height=active?shown.isEmpty()?58:37+shown.size()*43:51;
        if(width<140)return;
        float x=12;
        Render2D.shadow(ctx,x,y,width,height,9,9,0x40000000);Render2D.roundRect(ctx,x,y,width,height,9,0xE40C1420);Render2D.roundOutline(ctx,x,y,width,height,9,.7f,0x454C7E9F);
        Fonts.draw(ctx,"CHUNKPULSE",x+12,y+10,0xFFDCEBF7,true,.83f);
        String state=active?"LISTENING":"SERVER GATED";
        Fonts.draw(ctx,state,x+width-Fonts.width(state,true,.58f)-12,y+12,active?0xFF76CDB8:0xFF8497AC,true,.58f);
        if(!active){Fonts.draw(ctx,"Allowed multiplayer servers only",x+12,y+31,0xFF8CA3B8,false,.7f);return;}
        if(shown.isEmpty()){boolean found=module.discovery().views().stream().anyMatch(v->v.state()==ClusterDiscovery.State.REVEALED);Fonts.draw(ctx,found?"Nearby activity already marked FOUND":"No confirmed nearby activity yet",x+12,y+31,0xFF91A5B9,false,.72f);Fonts.draw(ctx,"Only received activity in loaded areas",x+12,y+45,0xFF667E94,false,.6f);return;}
        var player=MinecraftClient.getInstance().player;long now=System.currentTimeMillis();int row=0;
        for(var c:shown){
            float top=y+31+row++*43;boolean found=c.chunks().stream().allMatch(s->module.discovery().revealed(SignalEvent.key(s.x,s.z)));int color=found?0xFF9299A6:color(c.tier());
            Render2D.roundRect(ctx,x+10,top+2,3,31,1.5f,color);
            String label=(found?"FOUND":c.tier())+"  "+Math.round(c.score())+"  /  "+c.chunks().size()+" CHUNKS";
            Fonts.draw(ctx,label,x+21,top+2,color,true,Math.min(.72f,(width-34)/Math.max(1,Fonts.width(ctx,label,true,1))));
            long distance=player==null?0:Math.round(Math.hypot(c.x()-player.getX(),c.z()-player.getZ()));
            String coords=Math.round(c.x())+" "+Math.round(c.y())+" "+Math.round(c.z())+"   "+distance+"m";
            Fonts.draw(ctx,coords,x+21,top+15,0xFFD5E0EA,false,Math.min(.7f,(width-34)/Math.max(1,Fonts.width(ctx,coords,false,1))));
            String age=Math.max(0,(now-c.last())/1000)+"s ago";
            String evidence=c.positions()+" positions / "+age;
            Fonts.draw(ctx,evidence,x+21,top+28,0xFF8499AE,false,.6f);
        }
    }
    private PulseRenderer(){ }
}
