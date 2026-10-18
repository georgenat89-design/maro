package dev.maro.gui.accounts;

import dev.maro.accounts.AccountManager;
import dev.maro.gui.render.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** The refresh token stays masked and separate from the public OAuth application ID. */
public final class RefreshLoginScreen extends Screen {
    private final Screen parent;private final AccountManager accounts;
    private TokenField token;private TextFieldWidget application;private AccountButton login,rememberButton;
    private boolean remember=true,submitted;private int x,y,w;
    public RefreshLoginScreen(Screen parent,AccountManager accounts){super(Text.literal("Refresh Token Login"));this.parent=parent;this.accounts=accounts;}
    @Override protected void init(){
        if(!accounts.canRemember())remember=false;
        w=Math.min(450,width-24);x=(width-w)/2;y=(height-224)/2;
        String old=token==null?"":token.getText(),id=application==null?"":application.getText();
        application=addDrawableChild(new TextFieldWidget(textRenderer,x+18,y+74,w-36,20,Text.literal("OAuth client ID")));
        application.setMaxLength(36);application.setText(id);
        token=addDrawableChild(new TokenField(textRenderer,x+18,y+114,w-36));token.setText(old);setInitialFocus(token);
        rememberButton=addDrawableChild(new AccountButton("Remember on this PC: "+(remember?"On":"Off"),x+18,y+145,w-36,20,false,false,b->{
            remember=!remember;b.setMessage(Text.literal("Remember on this PC: "+(remember?"On":"Off")));}));
        rememberButton.active=accounts.canRemember();
        login=addDrawableChild(new AccountButton("Refresh & Login",x+18,y+174,(w-44)/2,24,true,false,b->submit()));
        addDrawableChild(new AccountButton("Cancel",x+26+(w-44)/2,y+174,(w-44)/2,24,false,false,b->close()));
    }
    private void submit(){submitted=true;accounts.addRefreshAndLogin(token.getText(),application.getText(),remember);token.setText("");}
    @Override public void tick(){
        boolean busy=accounts.snapshot().busy();login.active=!busy&&!token.getText().isBlank()&&!application.getText().isBlank()&&accounts.canSwitch();
        token.setEditable(!busy);application.setEditable(!busy);rememberButton.active=!busy&&accounts.canRemember();
        if(submitted&&!busy&&accounts.snapshot().status().startsWith("Signed in as "))client.setScreen(parent);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input){
        if((input.key()==257||input.key()==335)&&login.active){submit();return true;}return super.keyPressed(input);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        Render2D.setAlpha(1);Fonts.setMeasureScale(1);BootScene.background(ctx,width,height,BootScene.time());
        Render2D.roundRect(ctx,x,y,w,224,8,0xFF0D1826);Render2D.roundOutline(ctx,x,y,w,224,8,1,0xFF315272);
        Fonts.draw(ctx,"Refresh Token Login",x+18,y+16,BootScene.WHITE,true,1.3f);
        Fonts.draw(ctx,"Renew a Microsoft account's Minecraft session",x+18,y+41,0xFFADC4DA,false,.75f);
        Fonts.draw(ctx,"Client ID that issued the token",x+18,y+61,0xFF7895B0,false,.7f);
        Fonts.draw(ctx,"Microsoft refresh token",x+18,y+101,0xFF7895B0,false,.7f);
        super.render(ctx,mx,my,delta);
        Fonts.draw(ctx,Fonts.trim(ctx,submitted?accounts.snapshot().status():remember?"Saved on this PC / Renew again with Refresh":"Temporary account / Memory only",w-36,false,.7f),x+18,y+209,0xFF9DB8D1,false,.7f);
    }
    @Override public void close(){accounts.cancel();client.setScreen(parent);}
    @Override public void removed(){if(token!=null)token.setText("");}
    @Override public boolean shouldPause(){return false;}
}
