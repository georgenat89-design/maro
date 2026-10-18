package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.config.FriendManager;
import dev.maro.config.SharedModuleConfig;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.JumpCircle;
import dev.maro.module.impl.visuals.Radar;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import java.util.*;

/** Real tracked players, HUD placement and rendering in the production client. */
final class RadarChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(Radar radar,String name){return radar.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static OtherClientPlayerEntity player(net.minecraft.client.MinecraftClient c,String name,double x,double y,double z,int id){
        var p=new OtherClientPlayerEntity(c.world,new GameProfile(UUID.randomUUID(),name));p.setId(id);p.refreshPositionAndAngles(c.player.getX()+x,c.player.getY()+y,c.player.getZ()+z,0,0);p.setNoGravity(true);c.world.addEntity(p);return p;
    }
    private static Radar.Contact contact(Radar radar,String name){return radar.drawnContacts().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing radar contact "+name+": "+radar.drawnContacts()));}
    static void run(ClientGameTestContext context){
        var radar=ModuleManager.get(Radar.class);require(radar!=null,"Radar not registered");
        var saved=SharedModuleConfig.capture(radar);int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var original=context.computeOnClient(c->c.player.getEntityPos());float yaw=context.computeOnClient(c->c.player.getYaw());
        var fakes=new ArrayList<OtherClientPlayerEntity>();boolean hadFriend=FriendManager.isFriend("RadarFriend");
        try{
            for(double heading:new double[]{0,90,180,270,359,-1,721}){
                double a=Math.toRadians(heading),dx=Math.sin(a)*48,dz=-Math.cos(a)*48;
                var p=Radar.project(dx,dz,heading,96,50);require(Math.abs(p[0])<1e-7&&Math.abs(p[1]+25)<1e-7,"Heading projection points the wrong way: "+heading);
            }
            context.runOnClient(c->{
                radar.getSettings().forEach(Setting::reset);radar.setEnabled(true);((BooleanSetting)setting(radar,"Smooth Movement")).set(false);c.player.setYaw(180);
                fakes.add(player(c,"RadarNorth",0,0,-24,70001));fakes.add(player(c,"RadarFriend",24,8,0,70002));
                fakes.add(player(c,"RadarBelow",-18,-8,6,70003));fakes.add(player(c,"RadarFar",0,0,180,70004));
                fakes.add(player(c,"RadarHidden",10,0,10,70005));fakes.getLast().setInvisible(true);
                fakes.add(player(c,"RadarHigh",0,200,0,70006));FriendManager.add("RadarFriend");radar.onTick();
            });context.waitTicks(4);
            context.runOnClient(c->{
                require(radar.drawnContacts().size()==4,"Range, height or self filtering failed: "+radar.drawnContacts());
                require(contact(radar,"RadarNorth").y()<0&&Math.abs(contact(radar,"RadarNorth").x())<.2,"North not above center");
                require(contact(radar,"RadarFriend").x()>0&&contact(radar,"RadarFriend").friend()&&contact(radar,"RadarFriend").altitude()>3,"Friend/above state missing");
                require(contact(radar,"RadarBelow").altitude()<-3,"Below indicator missing");
            });context.takeScreenshot("maro-radar-player-heads");
            context.runOnClient(c->{((BooleanSetting)setting(radar,"Show Friends")).set(false);((BooleanSetting)setting(radar,"Invisible Players")).set(false);});context.waitTicks(3);
            context.runOnClient(c->require(radar.drawnContacts().size()==2,"Friend/invisibility filter did not update"));
            context.runOnClient(c->{((BooleanSetting)setting(radar,"Show Friends")).set(true);((BooleanSetting)setting(radar,"Invisible Players")).set(true);((BooleanSetting)setting(radar,"Edge Markers")).set(true);});context.waitTicks(3);
            context.runOnClient(c->require(contact(radar,"RadarFar").edge()&&Math.hypot(contact(radar,"RadarFar").x(),contact(radar,"RadarFar").y())<=62,"Edge arrow escaped the radar"));
            context.runOnClient(c->{c.player.setYaw(-90);((ModeSetting)setting(radar,"Markers")).set("Dots");((BooleanSetting)setting(radar,"Names")).set(true);});context.waitTicks(4);
            context.runOnClient(c->require(contact(radar,"RadarFriend").y()<0&&Math.abs(contact(radar,"RadarFriend").x())<.3,"Heading-up failed facing east"));
            context.takeScreenshot("maro-radar-heading-up-dots");
            context.runOnClient(c->{((ModeSetting)setting(radar,"Orientation")).set("North Up");((ModeSetting)setting(radar,"Markers")).set("Arrows");});context.waitTicks(3);
            context.runOnClient(c->require(contact(radar,"RadarFriend").x()>0&&Math.abs(contact(radar,"RadarFriend").y())<.3,"North-up rotated with the player"));
            context.takeScreenshot("maro-radar-north-up-arrows");
            for(int scale:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();radar.hudResize(2);radar.hudMove(10000,10000);});context.waitTicks(3);
                context.runOnClient(c->{require(radar.hudLeft()>=0&&radar.hudTop()>=0&&radar.hudLeft()+radar.hudWidth()<=c.getWindow().getScaledWidth()&&radar.hudTop()+radar.hudHeight()<=c.getWindow().getScaledHeight(),"Radar exceeds screen at GUI "+scale);});
                context.takeScreenshot("maro-radar-scale-"+scale);
            }
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();radar.hudReset();c.setScreen(new HudPlacementScreen(null,radar));});context.waitTicks(3);
            context.runOnClient(c->{var place=(HudPlacementScreen)c.currentScreen;place.dragTo(20,35);require(Math.abs(radar.hudLeft()-20)<=1&&Math.abs(radar.hudTop()-35)<=1,"Drag placement inaccurate");});context.takeScreenshot("maro-radar-placement");
            context.runOnClient(c->{c.setScreen(null);((BooleanSetting)setting(radar,"Smooth Movement")).set(true);
                for(int i=0;i<80;i++)fakes.add(player(c,"R"+i,i%10-5,0,i/10-5,70100+i));((NumberSetting)setting(radar,"Maximum Players")).set(8d);radar.onTick();
            });context.waitTicks(4);context.runOnClient(c->require(radar.cachedPlayers()<=8&&radar.drawnContacts().size()<=8,"Radar tracking/render count unbounded"));
            context.runOnClient(c->{for(var p:fakes)c.world.removeEntity(p.getId(),net.minecraft.entity.Entity.RemovalReason.DISCARDED);radar.onTick();});context.waitTicks(3);
            context.runOnClient(c->require(radar.cachedPlayers()==0&&radar.drawnContacts().isEmpty(),"Departed players left stale markers"));
            context.takeScreenshot("maro-radar-empty");
            var circles=ModuleManager.get(JumpCircle.class);
            context.runOnClient(c->{var radius=(NumberSetting)circles.getSettings().stream().filter(s->s.getName().equals("Radius")).findFirst().orElseThrow();require(radius.getMin()==.5&&radius.getMax()==3,"Existing jump-circle size control missing");var menu=new ClickGuiScreen();c.setScreen(menu);menu.openModuleSettings(circles,"Circles");});context.waitTicks(4);context.takeScreenshot("maro-jump-circle-size-control");
            System.out.println("[radar] PASS: projections, real player faces/dots/arrows, friends/invisible/range/height/edge filters, heading-up/north-up, three GUI scales, drag placement, 8-of-80 cap, despawn cleanup and existing Jump Circle Radius control");
        }finally{context.runOnClient(c->{radar.setEnabled(false);SharedModuleConfig.apply(radar,saved);for(var p:fakes)if(!p.isRemoved())c.world.removeEntity(p.getId(),net.minecraft.entity.Entity.RemovalReason.DISCARDED);c.setScreen(null);c.player.setPosition(original);c.player.setYaw(yaw);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();if(!hadFriend)FriendManager.remove("RadarFriend");});}
    }
}
