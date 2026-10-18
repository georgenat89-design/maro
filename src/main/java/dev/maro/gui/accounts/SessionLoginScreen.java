package dev.maro.gui.accounts;

import dev.maro.accounts.AccountManager;
import dev.maro.gui.render.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Credential entry stays masked, is never uppercased in storage and is cleared on exit. */
public final class SessionLoginScreen extends Screen {
    private final Screen parent;private final AccountManager accounts;
    private TokenField token;private boolean remember=true;private int x,y,w;
    private AccountButton login,rememberButton;private boolean submitted;
    public SessionLoginScreen(Screen parent,AccountManager accounts){super(Text.literal("Session Login"));this.parent=parent;this.accounts=accounts;}
    @Override protected void init(){
        if(!accounts.canRemember())remember=false;
        w=Math.min(430,width-28);x=(width-w)/2;y=(height-240)/2;
        String old=token==null?"":token.getText();
        token=addDrawableChild(new TokenField(textRenderer,x+18,y+94,w-36));token.setText(old);setInitialFocus(token);
        rememberButton=addDrawableChild(new AccountButton("Remember on this PC: "+(remember?"On":"Off"),x+18,y+130,w-36,24,false,false,b -> {
            remember=!remember;b.setMessage(Text.literal("Remember on this PC: "+(remember?"On":"Off")));}));rememberButton.active=accounts.canRemember();
        login=addDrawableChild(new AccountButton("Login",x+18,y+169,(w-44)/2,27,true,false,b -> submit()));
        addDrawableChild(new AccountButton("Cancel",x+26+(w-44)/2,y+169,(w-44)/2,27,false,false,b -> close()));
    }
    private void submit(){submitted=true;accounts.addAndLogin(token.getText(),remember);token.setText("");}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input){
        if((input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)&&login.active){submit();return true;}
        return super.keyPressed(input);
    }
    @Override public void tick(){
        boolean busy=accounts.snapshot().busy();login.active=!busy&&!token.getText().isBlank()&&accounts.canSwitch();token.setEditable(!busy);rememberButton.active=!busy&&accounts.canRemember();
        if(submitted&&!busy&&accounts.snapshot().status().startsWith("Signed in as "))client.setScreen(parent);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        Render2D.setAlpha(1);Fonts.setMeasureScale(1);BootScene.background(ctx,width,height,BootScene.time());
        Render2D.roundRect(ctx,x,y,w,240,8,0xFF0D1826);Render2D.roundOutline(ctx,x,y,w,240,8,1,0xFF315272);
        Fonts.draw(ctx,"Session Login",x+18,y+21,BootScene.WHITE,true,1.5f);
        Fonts.draw(ctx,"Minecraft Java access token",x+18,y+52,0xFFADC4DA,false,1);
        Fonts.draw(ctx,"Accepts a token or token:access-token:uuid",x+18,y+72,0xFF7895B0,false,.75f);
        super.render(ctx,mx,my,delta);
        String status=submitted?accounts.snapshot().status():remember?"Saved on this PC with Windows encryption":"Temporary session / Memory only";
        Fonts.draw(ctx,Fonts.trim(ctx,status,w-36,false,.8f),x+18,y+215,0xFF9DB8D1,false,.8f);
    }
    @Override public void close(){accounts.cancel();client.setScreen(parent);}
    @Override public void removed(){if(token!=null)token.setText("");}
    @Override public boolean shouldPause(){return false;}
}
