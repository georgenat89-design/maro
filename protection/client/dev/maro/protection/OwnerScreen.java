package dev.maro.protection;
import dev.maro.gui.render.BootAssets;
import dev.maro.gui.render.Render2D;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import java.util.*;

/** One task at a time. View state never grants permissions; only the verified license can do that. */
public final class OwnerScreen extends Screen {
    private enum Page { SIGN_IN, ACTIVATE, READY, HELP, DEVICES, RETRY }
    private Page page=Page.SIGN_IN;
    private TextFieldWidget password,otp,activation;
    private ActionButton primary;
    private final List<ActionButton> operations=new ArrayList<>();
    private String localMessage="";
    private float scale=1,ox,oy;
    private static final int TEXT=0xFFE9EDF4,MUTED=0xFF929DAC,ACCENT=0xFF9EBFEA;
    public OwnerScreen(){super(Text.literal("Maro account"));}
    private Page currentStep(){return ProtectedGate.allowed()?Page.READY:ProtectedGate.activationNeeded()?Page.ACTIVATE:ProtectedGate.hasSavedSession()?Page.RETRY:Page.SIGN_IN;}
    public double[] screenPoint(double x,double y){return new double[]{ox+x*scale,oy+y*scale};}
    @Override protected void init(){
        scale=Math.max(.01f,Math.min(width/800f,height/580f));ox=(width-800*scale)/2;oy=(height-580*scale)/2;
        if(page!=Page.HELP&&page!=Page.DEVICES)page=currentStep();build();
    }
    private void clearSecrets(){if(password!=null)password.setText("");if(otp!=null)otp.setText("");if(activation!=null)activation.setText("");}
    private void change(Page next){clearSecrets();page=next;localMessage="";build();}
    private void build(){
        clearChildren();operations.clear();password=null;otp=null;activation=null;primary=null;
        if(page==Page.SIGN_IN){
            password=field(212,212,376,"Password",256,true);otp=field(212,286,376,"Six-digit code",6,false);otp.setTextPredicate(s->s.matches("[0-9]{0,6}"));
            primary=button("Continue",212,365,376,true,()->{
                if(password.getText().isBlank()){localMessage="Enter the password you created during owner setup.";setFocused(password);return;}
                if(otp.getText().length()!=6){localMessage="Enter the six-digit code from your authenticator app.";setFocused(otp);return;}
                String p=password.getText(),o=otp.getText();clearSecrets();localMessage="";ProtectedGate.login(p,o);
            });setInitialFocus(password);
        }else if(page==Page.ACTIVATE){
            activation=field(212,245,376,"Paste your activation key",128,true);
            primary=button("Activate and continue",212,365,376,true,()->{
                if(activation.getText().isBlank()){localMessage="Paste the activation key saved during owner setup.";setFocused(activation);return;}
                String key=activation.getText().trim();clearSecrets();localMessage="";ProtectedGate.activate(key);
            });setInitialFocus(activation);
        }else if(page==Page.READY){
            primary=button("Open Maro",212,365,376,true,()->{clearSecrets();ProtectedGate.openModules();});
            button("Manage devices",212,317,181,false,()->change(Page.DEVICES));button("Sign out",407,317,181,false,ProtectedGate::logout);
        }else if(page==Page.DEVICES){
            password=field(212,226,376,"Current password",256,true);otp=field(212,294,376,"New six-digit code",6,false);otp.setTextPredicate(s->s.matches("[0-9]{0,6}"));
            primary=button("Sign out other devices",212,365,376,true,()->{
                if(password.getText().isBlank()||otp.getText().length()!=6){localMessage="Enter your password and a fresh authenticator code.";return;}
                String p=password.getText(),o=otp.getText();clearSecrets();ProtectedGate.reset(p,o);
            });
        }else if(page==Page.RETRY){
            primary=button("Try again",212,365,376,true,()->{localMessage="";ProtectedGate.checkNow();});
            button("Clear saved sign-in",212,317,376,false,ProtectedGate::logout);
        }else{
            button("Open setup guide",212,365,376,true,ProtectedGate::openGuide);
        }
        button(page==Page.HELP||page==Page.DEVICES?"Back":"Need help?",212,421,176,false,()->change(page==Page.HELP||page==Page.DEVICES?currentStep():Page.HELP));
        button("Back to Minecraft",402,421,186,false,this::close);
    }
    private TextFieldWidget field(int x,int y,int w,String name,int max,boolean secret){
        var f=new TextFieldWidget(textRenderer,x+12,y+10,w-24,18,Text.literal(name)){
            @Override protected net.minecraft.text.MutableText getNarrationMessage(){return Text.literal(name);}
        };
        f.setMaxLength(max);f.setDrawsBackground(false);f.setEditableColor(TEXT);f.setTextShadow(false);f.setPlaceholder(Text.literal(name));
        if(secret)f.addFormatter((s,i)->Text.literal("•".repeat(s.length())).asOrderedText());addDrawableChild(f);return f;
    }
    private ActionButton button(String text,int x,int y,int w,boolean filled,Runnable run){var b=new ActionButton(text,x,y,w,filled,run);addDrawableChild(b);if(filled)operations.add(b);return b;}
    private static void label(DrawContext c,String text,float x,float y,float size,int color,boolean bold){BootAssets.label(c,text,x,y,size,color,bold);}
    private void input(DrawContext c,TextFieldWidget f,int y){
        if(f==null)return;Render2D.roundRect(c,212,y,376,38,7,0xFF10151D);Render2D.roundOutline(c,212,y,376,38,7,f.isFocused()?1.2f:.7f,f.isFocused()?0xFF7395C1:0xFF333E4E);
    }
    private void lines(DrawContext c,String text,int y,int color){
        StringBuilder line=new StringBuilder();int row=0;
        for(String word:text.split(" ")){
            String next=line.isEmpty()?word:line+" "+word;
            if(BootAssets.labelWidth(next,12,false)>374&&!line.isEmpty()){label(c,line.toString(),212,y+row++*19,12,color,false);line=new StringBuilder(word);}else line=new StringBuilder(next);
        }
        if(!line.isEmpty())label(c,line.toString(),212,y+row*19,12,color,false);
    }
    private String helpfulMessage(){
        if(!localMessage.isBlank())return localMessage;
        String s=ProtectedGate.status();
        if(s.contains("unreachable"))return ProtectedGate.testBuild()?"This preview needs its test service. To play without owner setup, use Maro-Updated.jar. The setup guide explains both versions.":"We couldn't reach the sign-in service. Check your connection, then try again.";
        if(s.contains("Invalid credentials")||s.contains("expired session"))return "Sign-in didn't work. Check your password and use a new code from your app.";
        if(s.contains("Access denied"))return "Access wasn't approved. Check your activation key or use Help for device and setup steps.";
        if(s.contains("Too many"))return "There have been several attempts. Wait a few minutes before trying again.";
        if(s.contains("integrity")||s.contains("signature")||s.contains("artifact"))return "This copy couldn't be verified. Install a fresh signed copy from the owner.";
        if(s.contains("credential")||s.contains("Credential"))return "Windows couldn't restore your saved sign-in. Try signing in again or check the setup guide.";
        if(s.equals("Other devices revoked"))return "Done. Your other devices have been signed out.";
        return "";
    }
    @Override public void tick(){
        if(page!=Page.HELP&&page!=Page.DEVICES&&currentStep()!=page&&!ProtectedGate.working())change(currentStep());
        if(primary!=null)primary.active=!ProtectedGate.working();for(var b:operations)b.active=!ProtectedGate.working();
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        float alpha=Render2D.getAlpha();Render2D.setAlpha(1);Render2D.rectGradient(c,0,0,width,height,0xFF11151D,0xFF11151D,0xFF090D13,0xFF090D13);
        var m=c.getMatrices();m.pushMatrix();m.translate(ox,oy);m.scale(scale,scale);
        try{
            Render2D.roundRect(c,180,28,440,464,14,0xFF191F29);Render2D.roundOutline(c,180,28,440,464,14,.8f,0xFF303A48);c.createNewRootLayer();
            label(c,"maro.gg",212,55,24,TEXT,true);label(c,ProtectedGate.testBuild()?"Test preview":"Owner account",468,62,11,MUTED,false);
            String[] steps={"1  Sign in","2  Activate","3  Play"};int step=ProtectedGate.allowed()?2:ProtectedGate.activationNeeded()?1:0;
            for(int i=0;i<3;i++){label(c,steps[i],212+i*132,102,11,i==step?ACCENT:MUTED,i==step);Render2D.roundRect(c,212+i*132,122,112,2,1,i<=step?0xFF7EA7D9:0xFF343E4C);}
            if(page==Page.SIGN_IN){
                label(c,"Welcome to Maro",212,151,25,TEXT,true);label(c,"Owner account: navo",212,181,12,MUTED,false);label(c,"Password",212,198,10,TEXT,false);
                input(c,password,212);label(c,"Authenticator code",212,266,12,TEXT,false);input(c,otp,286);
                label(c,"Open your authenticator app for a new six-digit code.",212,335,10,MUTED,false);
            }else if(page==Page.ACTIVATE){
                label(c,"One last step",212,151,25,TEXT,true);lines(c,"Paste the activation key you saved during owner setup. You only need it the first time.",190,MUTED);
                label(c,"Activation key",212,225,12,TEXT,false);input(c,activation,245);label(c,"Your key is different from your account password.",212,300,11,MUTED,false);
            }else if(page==Page.READY){
                label(c,"You're ready to play",212,151,25,TEXT,true);lines(c,"You're signed in as navo. Your license is active on this device.",192,MUTED);
                Render2D.roundRect(c,212,248,376,42,7,0xFF1F332E);label(c,"Connected and verified",230,264,13,0xFFA5D8C4,true);
            }else if(page==Page.DEVICES){
                label(c,"Sign out other devices",212,151,23,TEXT,true);lines(c,"Use this if a device is lost. This device will stay signed in. Enter your password and a new app code.",187,MUTED);
                input(c,password,226);label(c,"Authenticator code",212,275,12,TEXT,false);input(c,otp,294);
            }else if(page==Page.RETRY){
                label(c,"Let's reconnect",212,151,25,TEXT,true);lines(c,"Your saved sign-in is still on this device. Check your internet connection and the owner's service, then try again.",196,MUTED);
                label(c,"Your protected features are paused for now.",212,278,12,ACCENT,false);
            }else{
                label(c,"Let's get you unstuck",212,151,24,TEXT,true);
                label(c,"First time here?",212,195,13,TEXT,true);lines(c,"The owner must finish setup first. There is no public registration and navo is not a password.",219,MUTED);
                label(c,"Lost your authenticator?",212,271,13,TEXT,true);lines(c,"Use an unused recovery code with the recovery tool in the owner setup kit.",296,MUTED);
            }
            String help=helpfulMessage();
            if(!help.isEmpty()||ProtectedGate.working()){
                Render2D.roundRect(c,180,498,440,68,9,0xFF202B3A);c.createNewRootLayer();lines(c,ProtectedGate.working()?"Please wait while we check your account…":help,511,0xFFBED2EA);
            }
            if(ProtectedGate.testBuild()&&page==Page.HELP)label(c,"This preview needs the owner's test service to sign in.",212,345,10,ACCENT,false);
            c.createNewRootLayer();super.render(c,(int)((mx-ox)/scale),(int)((my-oy)/scale),delta);
        }finally{m.popMatrix();Render2D.setAlpha(alpha);}
    }
    private Click local(Click click){return new Click((click.x()-ox)/scale,(click.y()-oy)/scale,click.buttonInfo());}
    @Override public boolean mouseClicked(Click click,boolean twice){localMessage="";return super.mouseClicked(local(click),twice);}
    @Override public boolean mouseReleased(Click click){return super.mouseReleased(local(click));}
    @Override public boolean mouseDragged(Click click,double dx,double dy){return super.mouseDragged(local(click),dx/scale,dy/scale);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved((x-ox)/scale,(y-oy)/scale);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){return super.mouseScrolled((x-ox)/scale,(y-oy)/scale,h,v);}
    @Override public boolean keyPressed(KeyInput key){if(key.key()==257&&primary!=null&&primary.active&&!(getFocused() instanceof ActionButton)){primary.submit();return true;}return super.keyPressed(key);}
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){clearSecrets();super.removed();}
    @Override public void close(){clearSecrets();client.setScreen(null);}
    private static final class ActionButton extends ClickableWidget {
        private final boolean filled;private final Runnable run;
        ActionButton(String label,int x,int y,int w,boolean filled,Runnable run){super(x,y,w,34,net.minecraft.text.Text.literal(label));this.filled=filled;this.run=run;}
        void submit(){if(active)run.run();}
        @Override public void onClick(Click click,boolean twice){submit();}
        @Override public boolean keyPressed(KeyInput key){if(active&&isFocused()&&(key.key()==257||key.key()==335||key.key()==32)){submit();return true;}return false;}
        @Override protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
        @Override public void renderWidget(DrawContext c,int mx,int my,float d){
            boolean hover=isSelected();int fill=!active?0xFF2A3340:filled?hover?0xFFBAD1EF:0xFFA2C0E6:hover?0xFF303C4E:0xFF232C39;
            Render2D.roundRect(c,getX(),getY(),getWidth(),getHeight(),7,fill);if(isFocused())Render2D.roundOutline(c,getX()-2,getY()-2,getWidth()+4,getHeight()+4,9,1,ACCENT);
            String text=getMessage().getString();float size=12;label(c,text,getX()+(getWidth()-BootAssets.labelWidth(text,size,filled))/2,getY()+12,size,filled?0xFF142235:active?TEXT:MUTED,filled);
        }
    }
}
