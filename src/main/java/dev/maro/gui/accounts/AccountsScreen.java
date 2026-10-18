package dev.maro.gui.accounts;

import dev.maro.accounts.AccountManager;
import dev.maro.gui.render.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** Compact account rows with explicit session login, public details and removal. */
public final class AccountsScreen extends Screen {
    private final Screen parent;private final AccountManager accounts;
    private final Map<UUID,Supplier<SkinTextures>> skins=new HashMap<>();
    private int x,y,w,h,scroll;private long revision=-1;private UUID info;
    public AccountsScreen(Screen parent){this(parent,AccountManager.get());}
    public AccountsScreen(Screen parent,AccountManager accounts){super(Text.literal("Accounts"));this.parent=parent;this.accounts=accounts;}
    public AccountManager manager(){return accounts;}
    @Override protected void init(){w=Math.min(500,width-28);h=Math.min(340,height-24);x=(width-w)/2;y=(height-h)/2;rebuild();}
    private void rebuild(){
        clearChildren();var state=accounts.snapshot();revision=state.revision();
        int visible=Math.max(1,(h-125)/35);scroll=Math.max(0,Math.min(scroll,Math.max(0,state.accounts().size()-visible)));
        int row=0;
        for(var account:state.accounts().stream().skip(scroll).limit(visible).toList()) {
            int ry=y+72+row++*35;
            if(account.refreshable()){
                var refresh=addDrawableChild(new AccountButton("Refresh",x+w-244,ry+5,70,23,false,false,b->accounts.refresh(account.id())));
                refresh.active=accounts.canSwitch()&&!state.busy();
            }
            addDrawableChild(new AccountButton("Info",x+w-168,ry+5,44,23,false,false,b -> {info=Objects.equals(info,account.id())?null:account.id();}));
            var login=addDrawableChild(new AccountButton("Login",x+w-118,ry+5,66,23,false,false,b -> accounts.login(account.id())));
            login.active=accounts.canSwitch()&&!state.busy();
            var remove=addDrawableChild(new AccountButton("-",x+w-46,ry+5,28,23,false,true,b -> accounts.remove(account.id())));remove.active=!state.busy();
        }
        int by=y+h-65;
        int bw=(w-54)/4;
        var add=addDrawableChild(new AccountButton("Add Session",x+18,by,bw,25,true,false,b -> client.setScreen(new SessionLoginScreen(this,accounts))));
        add.active=accounts.canSwitch()&&!state.busy();
        var refresh=addDrawableChild(new AccountButton("Refresh Token",x+24+bw,by,bw,25,false,false,b->client.setScreen(new RefreshLoginScreen(this,accounts))));refresh.active=accounts.canSwitch()&&!state.busy();
        var launcher=addDrawableChild(new AccountButton("Launcher",x+30+bw*2,by,bw,25,false,false,b -> accounts.launcher()));launcher.active=accounts.canSwitch()&&!state.busy();
        addDrawableChild(new AccountButton("Done",x+36+bw*3,by,bw,25,false,false,b -> close()));
    }
    @Override public void tick(){if(revision!=accounts.snapshot().revision())rebuild();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(mx>=x&&mx<=x+w&&my>=y+70&&my<=y+h-80){scroll+=vertical<0?1:-1;rebuild();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        Render2D.setAlpha(1);Fonts.setMeasureScale(1);BootScene.background(ctx,width,height,BootScene.time());
        Render2D.roundRect(ctx,x,y,w,h,8,0xF20B1420);Render2D.roundOutline(ctx,x,y,w,h,8,1,0xFF2D4965);
        BootAssets.mark(ctx,x+18,y+17,20,BootScene.WHITE);Fonts.draw(ctx,"Accounts",x+48,y+21,BootScene.WHITE,true,1.5f);
        Fonts.draw(ctx,Fonts.trim(ctx,"Active: "+client.getSession().getUsername(),w-36,false,1),x+18,y+51,0xFF9BB5CD,false,1);
        Render2D.rect(ctx,x+18,y+65,w-36,1,0xFF26374B);
        var state=accounts.snapshot();int visible=Math.max(1,(h-125)/35),row=0;
        for(var account:state.accounts().stream().skip(scroll).limit(visible).toList()) {
            int ry=y+72+row++*35;boolean active=account.id().equals(client.getSession().getUuidOrNull());
            Render2D.roundRect(ctx,x+12,ry,w-24,33,4,active?0xFF132D42:0xFF101C2B);
            try {var skin=skins.computeIfAbsent(account.id(),id -> client.getSkinProvider().supplySkinTextures(new GameProfile(id,account.name()),false));
                PlayerSkinDrawer.draw(ctx,skin.get(),x+19,ry+6,21);}
            catch(RuntimeException ignored){Render2D.roundRect(ctx,x+19,ry+6,21,21,3,0xFF286CAE);}
            float nameRoom=Math.max(20,w-(account.refreshable()?326:250));
            Fonts.draw(ctx,Fonts.trim(ctx,account.name(),nameRoom,true,1),x+48,ry+7,active?0xFF7DE6C0:BootScene.WHITE,true,1);
            String kind=account.refreshable()?"Microsoft":"Session";
            String hint=account.expires()>0&&account.expires()<System.currentTimeMillis()?"Expired":kind+(account.remembered()?" / Saved":" / Temporary");
            Fonts.draw(ctx,hint,x+48,ry+20,0xFF7F94AB,false,.7f);
        }
        if(state.accounts().isEmpty()) {
            Fonts.drawCentered(ctx,"No saved sessions",x+w/2f,y+114,0xFFADBED1,true,1);
            Fonts.drawCentered(ctx,"Add a token to sign in",x+w/2f,y+135,0xFF7F94AB,false,.85f);
        }
        if(state.accounts().size()>visible)Fonts.draw(ctx,(scroll+1)+"-"+Math.min(scroll+visible,state.accounts().size())+" / "+state.accounts().size(),x+18,y+h-81,0xFF7F94AB,false,.7f);
        super.render(ctx,mx,my,delta);
        String status=accounts.canSwitch()?state.status():"Return to the title screen to switch accounts";
        Fonts.draw(ctx,Fonts.trim(ctx,status,w-36,false,.8f),x+18,y+h-23,0xFF9AB5CF,false,.8f);
        if(info!=null)state.accounts().stream().filter(a -> a.id().equals(info)).findFirst().ifPresent(a -> details(ctx,a));
    }
    private void details(DrawContext ctx,AccountManager.Account account){
        ctx.createNewRootLayer();int dw=Math.min(390,w-24),dx=x+(w-dw)/2,dy=y+96;
        Render2D.roundRect(ctx,dx,dy,dw,105,5,0xFF132337);Render2D.roundOutline(ctx,dx,dy,dw,105,5,1,0xFF4B83B4);
        Fonts.draw(ctx,account.name(),dx+12,dy+13,BootScene.WHITE,true,1.2f);
        Fonts.draw(ctx,Fonts.trim(ctx,"UUID: "+account.id(),dw-24,false,.78f),dx+12,dy+35,0xFFB7C9DC,false,.78f);
        String expiry=account.expires()==0?"Expiry: Not provided":"Expires: "+DateTimeFormatter.ofPattern("MMM d, HH:mm").format(Instant.ofEpochMilli(account.expires()).atZone(ZoneId.systemDefault()));
        Fonts.draw(ctx,expiry,dx+12,dy+53,0xFFB7C9DC,false,.85f);
        Fonts.draw(ctx,account.remembered()?"Saved with Windows encryption":"Temporary session / Memory only",dx+12,dy+71,0xFF7FE0BE,false,.78f);
        Fonts.draw(ctx,"Click anywhere to close",dx+12,dy+89,0xFF7895B0,false,.7f);
    }
    @Override public boolean mouseClicked(net.minecraft.client.gui.Click click,boolean doubled){
        if(info!=null){info=null;return true;}return super.mouseClicked(click,doubled);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input){
        if(info!=null){info=null;return true;}return super.keyPressed(input);
    }
    @Override public void close(){accounts.cancel();client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
