package dev.maro.gui;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Filtered local-file picker drawn inside Maro's protected GUI. Never opens a desktop dialog. */
public final class PngFilePickerScreen extends Screen {
    private static final float W=760,H=440,ROW=24,LIST_X=151,LIST_Y=129,LIST_W=589,LIST_H=220;
    private static final int MAX_ENTRIES=2000;
    private record Entry(Path path,String name,boolean directory) { }
    private record Listing(Path directory,List<Entry> entries,List<Path> roots,boolean truncated,Path selected) { }
    private record Hit(float x,float y,float w,float h,Runnable action){boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private final Screen parent;
    private final Path initial;
    private final Consumer<Path> accepted;
    private final List<String> suffixes;
    private final String types;
    private Consumer<Boolean> dismissal=returned->{};
    private final List<Hit> hits=new ArrayList<>();
    private List<Entry> entries=List.of();
    private List<Path> roots=List.of();
    private Path directory,selected;
    private TextFieldWidget path;
    private float scale=1,ox,oy,mx,my;
    private int scroll,selectedIndex=-1,focus=-1,rootOffset;
    private volatile int generation;
    private volatile boolean closed;
    private boolean busy,started,returning,dismissed;
    private String status;

    public PngFilePickerScreen(Screen parent,Path initial,Consumer<Path> accepted){
        this(parent,initial,"Choose PNG Image",accepted,"png");
    }
    public PngFilePickerScreen(Screen parent,Path initial,String title,Consumer<Path> accepted,String... suffixes){
        super(Text.literal(title));this.parent=parent;this.initial=initial;this.accepted=accepted;
        this.suffixes=java.util.Arrays.stream(suffixes).map(s->s.strip().toLowerCase(Locale.ROOT).replaceFirst("^\\.","")).filter(s->!s.isEmpty()).distinct().toList();
        if(this.suffixes.isEmpty())throw new IllegalArgumentException("File picker needs at least one allowed suffix");
        types=String.join(" / ",this.suffixes).toUpperCase(Locale.ROOT);status="Choose a "+types+" file. Directories and supported files are shown.";
    }
    /** true means returning to the same parent; false means another screen interrupted the picker. */
    public PngFilePickerScreen onDismiss(Consumer<Boolean> action){dismissal=action;return this;}
    @Override protected void init(){
        String previous=path==null?(directory==null?initial.toString():directory.toString()):path.getText();
        path=new TextFieldWidget(textRenderer,153,81,488,22,Text.literal("Folder or file path"));
        path.setDrawsBackground(false);path.setMaxLength(32767);path.setText(previous);addSelectableChild(path);
        if(!started){started=true;navigate(initial);}
    }
    public boolean busy(){return busy;}
    public Path directory(){return directory;}
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    /** The actual visible file row, for native input checks. */
    public double[] entryPoint(String name){
        for(int i=0;i<entries.size();i++)if(entries.get(i).name().equals(name)){
            if(i<scroll||i>=scroll+9)return null;
            return point(LIST_X+160,LIST_Y+(i-scroll)*ROW+ROW/2);
        }
        return null;
    }
    private void navigate(Path target){
        if(closed)return;
        int token=++generation;busy=true;status="Reading folder…";selected=null;selectedIndex=-1;focus=-1;
        CompletableFuture.supplyAsync(()->{
            try{
                Path normalized=target.toAbsolutePath().normalize();
                if(Files.isRegularFile(normalized)){
                    if(!accepts(normalized))throw new IOException("Choose a "+types+" file.");
                    return new Listing(null,List.of(),List.of(),false,normalized);
                }
                if(!Files.isDirectory(normalized))throw new IOException("That folder is missing or unavailable.");
                var result=new ArrayList<Entry>();boolean truncated=false;
                try(var stream=Files.newDirectoryStream(normalized)){
                    for(Path item:stream){
                        if(closed||token!=generation)return null;
                        boolean folder=Files.isDirectory(item);
                        if(!folder&&!accepts(item))continue;
                        if(result.size()>=MAX_ENTRIES){truncated=true;break;}
                        result.add(new Entry(item,item.getFileName()==null?item.toString():item.getFileName().toString(),folder));
                    }
                }
                result.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER));
                var drives=new ArrayList<Path>();for(File root:File.listRoots())drives.add(root.toPath());
                return new Listing(normalized,List.copyOf(result),List.copyOf(drives),truncated,null);
            }catch(IOException|SecurityException error){throw new java.util.concurrent.CompletionException(error);}
        }).whenComplete((listing,error)->client.execute(()->{
            if(closed||token!=generation)return;
            busy=false;
            if(error!=null){Throwable cause=error.getCause()==null?error:error.getCause();status=cause instanceof java.nio.file.AccessDeniedException?"Access denied. Choose another folder.":cause.getMessage()==null?"Cannot open this location. Check the path and permissions.":cause.getMessage();return;}
            if(listing.selected()!=null){choose(listing.selected());return;}
            directory=listing.directory();entries=listing.entries();roots=listing.roots();scroll=0;path.setText(directory.toString());
            status=listing.truncated()?"First 2,000 entries shown. Enter a folder or file path to narrow the selection.":entries.isEmpty()?"No supported files here. Open another folder or enter a file path.":"Select a file, then choose Import. Double-click a folder to open it.";
        }));
    }
    public boolean accepts(Path file){if(file.getFileName()==null)return false;String name=file.getFileName().toString().toLowerCase(Locale.ROOT);return suffixes.stream().anyMatch(suffix->name.endsWith("."+suffix));}
    private void go(){
        try{navigate(Path.of(path.getText().strip()));}
        catch(InvalidPathException error){status="This path contains unsupported characters.";}
    }
    private void choose(Path file){
        if(closed)return;
        closed=true;returning=true;generation++;client.setScreen(parent);accepted.accept(file);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        Theme.update();Render2D.setAlpha(1);Render2D.rect(ctx,0,0,width,height,0xB5090D16);
        scale=Math.min(width*.94f/W,height*.92f/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();
        ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(ox,oy);ctx.getMatrices().scale(scale,scale);Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.shadow(ctx,0,5,W,H,12,20,0x80000000);Render2D.roundRect(ctx,0,0,W,H,12,0xFF0A101B);Render2D.roundOutline(ctx,0,0,W,H,12,.8f,0x66475C7D);
            Fonts.drawV(ctx,Fonts.trim(title.getString(),710,true,1.14f),20,25,Theme.TEXT,true,1.14f);
            Fonts.drawV(ctx,"Browse files privately inside Minecraft.",20,47,Theme.TEXT_MUTED,false,.74f);
            Render2D.roundRect(ctx,20,68,118,281,7,0xB7111C2D);
            button(ctx,"Home",30,80,98,25,false,()->navigate(Path.of(System.getProperty("user.home"))));
            button(ctx,"Game folder",30,112,98,25,false,()->navigate(client.runDirectory.toPath()));
            Fonts.drawV(ctx,"DRIVES",30,165,Theme.TEXT_MUTED,true,.63f);
            for(int i=0;i<Math.min(4,roots.size());i++){Path root=roots.get(Math.floorMod(rootOffset+i,roots.size()));button(ctx,root.toString(),30,180+i*30,98,24,false,()->navigate(root));}
            if(roots.size()>4)button(ctx,"More drives →",30,311,98,24,false,()->rootOffset=(rootOffset+4)%roots.size());
            button(ctx,"Up",151,106,49,20,false,()->{if(directory!=null&&directory.getParent()!=null)navigate(directory.getParent());});
            Fonts.drawV(ctx,"Folder or file path",151,66,Theme.TEXT_MUTED,false,.68f);
            Render2D.roundRect(ctx,147,77,502,28,5,path.isFocused()?0xFF243951:0xFF17263C);Render2D.roundOutline(ctx,147,77,502,28,5,.8f,path.isFocused()?Theme.accent():0x33465E7D);
            path.render(ctx,(int)mx,(int)my,delta);button(ctx,"Go",658,77,82,28,true,this::go);
            Fonts.drawV(ctx,"FOLDERS & "+types+" FILES",210,118,Theme.TEXT_MUTED,true,.64f);
            Render2D.roundRect(ctx,LIST_X,LIST_Y,LIST_W,LIST_H,6,0xFF101C2D);
            Render2D.clip(ctx,(int)LIST_X,(int)LIST_Y,(int)(LIST_X+LIST_W),(int)(LIST_Y+LIST_H));
            try{
                int end=Math.min(entries.size(),scroll+10);
                for(int index=scroll;index<end;index++){
                    Entry entry=entries.get(index);float rowY=LIST_Y+(index-scroll)*ROW;boolean chosen=entry.path().equals(selected),hover=hover(LIST_X,rowY,LIST_W,ROW);
                    if(chosen||hover)Render2D.roundRect(ctx,LIST_X+3,rowY+1,LIST_W-6,ROW-2,4,chosen?0xFF244D7B:0xFF1B2E48);
                    String name=entry.name();String suffix=name.lastIndexOf('.')<0?"FILE":name.substring(name.lastIndexOf('.')+1).toUpperCase(Locale.ROOT);
                    Fonts.drawV(ctx,entry.directory()?"DIR":Fonts.trim(suffix,30,true,.61f),LIST_X+10,rowY+ROW/2,entry.directory()?0xFF8EBBEE:0xFF9BB4CB,true,.61f);
                    Fonts.drawV(ctx,Fonts.trim(entry.name(),LIST_W-73,false,.74f),LIST_X+47,rowY+ROW/2,Theme.TEXT,false,.74f);
                }
            }finally{Render2D.unclip(ctx);}
            if(entries.size()>9){float barH=LIST_H*9/entries.size(),barY=LIST_Y+(LIST_H-barH)*scroll/Math.max(1,entries.size()-9);Render2D.roundRect(ctx,LIST_X+LIST_W-5,barY,3,Math.max(10,barH),1.5f,0xFF536D90);}
            Fonts.drawV(ctx,Fonts.trim(busy?"Reading folder…":status,720,false,.68f),20,367,Theme.TEXT_MUTED,false,.68f);
            Fonts.drawV(ctx,Fonts.trim(selected==null?types+" files · paste a full path or drop a file here":selected.getFileName().toString(),500,false,.67f),20,407,selected==null?Theme.TEXT_MUTED:Theme.TEXT_DIM,false,.67f);
            button(ctx,"Cancel",548,392,90,29,false,this::close);
            boolean importable=selected!=null&&selectedIndex>=0&&selectedIndex<entries.size()&&!entries.get(selectedIndex).directory();
            button(ctx,"Import selected",647,392,93,29,importable,()->{if(importable&&!busy)choose(selected);});
            if(focus>=0&&focus<hits.size()){Hit hit=hits.get(focus);Render2D.roundOutline(ctx,hit.x(),hit.y(),hit.w(),hit.h(),5,1.2f,Theme.accent());}
        }finally{Fonts.setMeasureScale(1);Fonts.endRaw();ctx.getMatrices().popMatrix();}
    }
    private boolean hover(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void button(DrawContext ctx,String label,float x,float y,float w,float h,boolean primary,Runnable action){
        Render2D.roundRect(ctx,x,y,w,h,5,primary?(hover(x,y,w,h)?0xFF438FDB:0xFF2C71B8):(hover(x,y,w,h)?0xFF2A405E:0xFF1B2C46));
        Fonts.drawCentered(ctx,Fonts.trim(label,w-10,true,.67f),x+w/2,y+h/2,Theme.TEXT,true,.67f);hits.add(new Hit(x,y,w,h,action));
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(click.button()!=0)return true;
        double x=(click.x()-ox)/scale,y=(click.y()-oy)/scale;
        if(x>=147&&x<649&&y>=77&&y<105){path.setFocused(true);path.mouseClicked(new Click(x,y,new MouseInput(0,0)),doubled);return true;}
        path.setFocused(false);focus=-1;
        if(x>=LIST_X&&x<LIST_X+LIST_W&&y>=LIST_Y&&y<LIST_Y+LIST_H&&!busy){
            int index=scroll+(int)((y-LIST_Y)/ROW);
            if(index<entries.size()){
                Entry entry=entries.get(index);selected=entry.path();selectedIndex=index;Sounds.click();
                if(doubled){if(entry.directory())navigate(entry.path());else choose(entry.path());}
                else if(entry.directory())status="Folder selected. Press Enter or double-click to open it.";
                else status="File selected. Choose Import to use it.";
            }
            return true;
        }
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){Sounds.click();hits.get(i).action().run();return true;}
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        scroll=Math.clamp(scroll-(int)(vertical*3),0,Math.max(0,entries.size()-9));return true;
    }
    @Override public boolean charTyped(CharInput input){return path.isFocused()&&path.charTyped(input);}
    @Override public boolean keyPressed(KeyInput input){
        int key=input.key();
        if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){
            if(path.isFocused())go();
            else if(focus>=0&&focus<hits.size())hits.get(focus).action().run();
            else if(!busy&&selectedIndex>=0&&selectedIndex<entries.size()){Entry entry=entries.get(selectedIndex);if(entry.directory())navigate(entry.path());else choose(entry.path());}
            return true;
        }
        if(key==GLFW.GLFW_KEY_TAB&&!hits.isEmpty()){path.setFocused(false);focus=Math.floorMod(focus+((input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),hits.size());return true;}
        if(path.isFocused())return path.keyPressed(input);
        if((key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP)&&!busy&&!entries.isEmpty()){
            selectedIndex=Math.clamp(selectedIndex+(key==GLFW.GLFW_KEY_DOWN?1:-1),0,entries.size()-1);selected=entries.get(selectedIndex).path();
            if(selectedIndex<scroll)scroll=selectedIndex;if(selectedIndex>=scroll+9)scroll=selectedIndex-8;focus=-1;return true;
        }
        if(key==GLFW.GLFW_KEY_BACKSPACE&&directory!=null&&directory.getParent()!=null){navigate(directory.getParent());return true;}
        return super.keyPressed(input);
    }
    @Override public void onFilesDropped(List<Path> files){if(!files.isEmpty())navigate(files.getFirst());}
    @Override public void close(){closed=true;returning=true;generation++;client.setScreen(parent);}
    @Override public void removed(){closed=true;generation++;if(!dismissed){dismissed=true;dismissal.accept(returning);}}
}
