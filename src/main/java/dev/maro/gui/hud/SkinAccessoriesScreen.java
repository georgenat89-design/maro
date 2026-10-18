package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.render.accessories.CosmeticItems;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * Cosmetics: your character turning on the left (Front, Back, Reset; drag to turn it), and on the
 * right a tab for each kind of cosmetic, a list of every choice in it with the current one lit, a
 * size slider for that kind and Apply. Hats come from the Hats module; swords, pickaxes, shovels
 * and tridents are skins over your own items; the rest are the 3D accessories.
 */
public final class SkinAccessoriesScreen extends Screen {
    public static final String[] TABS = {"Hat", "Sword", "Pickaxe", "Shovel", "Trident", "Wings", "Head", "Tail", "Halo", "Back", "Shoulders", "Looks", "Colors"};
    private static final float ROW = 28, ROW_GAP = 5;
    private static final int[] PALETTE = {0xFF48385E, 0xFFBA7BFF, 0xFFFFD98A, 0xFF55E6F5, 0xFFFF536D, 0xFFD77B3B, 0xFF8DE7B4,
            0xFFE9E5EF, 0xFF293447, 0xFFF2A4C3, 0xFF3391FC, 0xFF1E1B1F};

    private final Screen parent;
    private final SkinAccessories module;
    private String tab = "Sword";
    private float rotation = -25, scroll, scrollTarget;
    private float pickaxePitch, pickaxeZoom=1;
    private boolean rotating = true, dragging, sizing;
    private double dragFromX,dragFromY;
    private float dragFromPitch;
    private float dragFromRotation;
    private long previousFrame;
    private float frameTime;
    private float previewTop=42;
    private String query="";
    private boolean typing,categoriesOpen;
    private float categoryScroll,searchX,searchW,categoryX,categoryY,categoryW,dropY,dropH;
    private final Map<String,ItemStack> icons=new HashMap<>();
    private float x, y, w, h, previewW, listX, listY, listW, listH, sliderX, sliderW;

    private interface Action {
        void run(double mx, double my, int button);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public SkinAccessoriesScreen(Screen parent, SkinAccessories module) {
        super(Text.literal("Cosmetics"));
        this.parent = parent;
        this.module = module;
    }

    // ---- for tests -------------------------------------------------------------------------------

    public void showAngle(float degrees) {
        rotation = degrees;
        pickaxePitch=0;pickaxeZoom=1;
        rotating = false;
    }

    public void showTab(String name) {
        if(!List.of(TABS).contains(name))throw new IllegalArgumentException("Unknown cosmetic category");
        tab = name;
        scroll = scrollTarget = 0;
        query="";typing=false;categoriesOpen=false;icons.clear();
    }

    public void search(String words){query=words.substring(0,Math.min(64,words.length()));scroll=scrollTarget=0;}
    public List<String> shownChoices(){String q=query.strip().toLowerCase(Locale.ROOT);return choices().stream().filter(s->s.toLowerCase(Locale.ROOT).contains(q)||tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.matches(s,q)).toList();}
    private int columns(){return listW>=310?2:1;}
    public double[] choicePoint(int index){float cw=(listW-16-(columns()-1)*6)/columns();return new double[]{listX+8+(index%columns())*(cw+6)+cw/2,listY+6+(index/columns())*(ROW+ROW_GAP)-scroll+ROW/2};}
    public double[] categoryPoint(){return new double[]{categoryX+categoryW/2,categoryY+12};}
    public double[] categoryChoicePoint(String name){return new double[]{categoryX+Math.max(132,categoryW)/2,dropY+6+List.of(TABS).indexOf(name)*24-categoryScroll+11};}

    /** The preview's area on screen, in window pixels: {x1, y1, x2, y2}. */
    public int[] previewBounds() {
        double s = client == null ? 1 : client.getWindow().getScaleFactor();
        return new int[] {(int) ((x + 10) * s), (int) ((y + previewTop) * s), (int) ((x + 10 + previewW) * s), (int) ((y + h - 42) * s)};
    }

    // ---- what each tab holds ------------------------------------------------------------------------

    private Setting<?> setting(String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Module hats() {
        return ModuleManager.getByName("Hats");
    }

    @SuppressWarnings("unchecked")
    private static dev.maro.runtime.settings.Setting<Object> runtimeSetting(Module m, String name) {
        if (!(m instanceof dev.maro.runtime.systems.modules.Module rm)) return null;
        for (var group : rm.settings) for (var s : group) if (s.name.equals(name)) return (dev.maro.runtime.settings.Setting<Object>) s;
        return null;
    }

    /** The choices in the tab, "None" first where there is one. */
    private List<String> choices() {
        List<String> out = new ArrayList<>();
        switch (tab) {
            case "Hat" -> {
                out.add("None");
                for (String id : dev.maro.nathan.hats.HatCatalogue.ids()) out.add(pretty(id));
            }
            case "Sword", "Pickaxe", "Shovel", "Trident", "Wings", "Head", "Tail", "Halo", "Back", "Shoulders" ->
                    out.addAll(((ModeSetting) setting(tab)).getModes());
            case "Looks" -> out.addAll(List.of(SkinAccessories.PRESETS));
            default -> {
            }
        }
        return out;
    }

    private String current() {
        return switch (tab) {
            case "Hat" -> {
                Module h = hats();
                var s = runtimeSetting(h, "hat");
                yield h == null || !h.isEnabled() || s == null ? "None" : pretty(String.valueOf(s.get()));
            }
            case "Looks" -> module.preset();
            case "Colors" -> "";
            default -> ((ModeSetting) setting(tab)).get();
        };
    }

    private void choose(String choice) {
        switch (tab) {
            case "Hat" -> {
                Module h = hats();
                if (h == null) return;
                if (choice.equals("None")) {
                    h.setEnabled(false);
                    return;
                }
                var s = runtimeSetting(h, "hat");
                if (s != null) s.set(choice.toLowerCase(Locale.ROOT).replace(' ', '_'));
                h.setEnabled(true);
            }
            case "Looks" -> module.selectPreset(choice);
            default -> ((ModeSetting) setting(tab)).set(choice);
        }
    }

    /** The slider under the list for this tab, if it has one. */
    private NumberSetting size() {
        return switch (tab) {
            case "Sword", "Pickaxe", "Shovel", "Trident" -> (NumberSetting) setting("Item Size");
            case "Wings" -> (NumberSetting) setting("Wing Size");
            case "Head" -> (NumberSetting) setting("Head Size");
            case "Tail" -> (NumberSetting) setting("Tail Length");
            case "Halo" -> (NumberSetting) setting("Halo Height");
            default -> null;
        };
    }

    private static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    /** What the player holds in the preview: on a weapon tab, an item of that kind with its skin. */
    private ItemStack previewItem() {
        Item item = switch (tab) {
            case "Sword" -> Items.DIAMOND_SWORD;
            case "Pickaxe" -> Items.DIAMOND_PICKAXE;
            case "Shovel" -> Items.DIAMOND_SHOVEL;
            case "Trident" -> Items.TRIDENT;
            default -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** An icon for a weapon skin: the base item drawn with the skin's model. */
    private ItemStack icon(String choice) {
        if(icons.containsKey(choice))return icons.get(choice);
        if(tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.contains(choice)){var stack=dev.maro.module.impl.visuals.CustomPickaxe.icon(choice);icons.put(choice,stack);return stack;}
        CosmeticItems.Kind kind = switch (tab) {
            case "Sword" -> CosmeticItems.Kind.SWORD;
            case "Pickaxe" -> CosmeticItems.Kind.PICKAXE;
            case "Shovel" -> CosmeticItems.Kind.SHOVEL;
            case "Trident" -> CosmeticItems.Kind.TRIDENT;
            default -> null;
        };
        if (kind == null) return null;
        ItemStack stack = previewItem().copy();
        CosmeticItems.Skin skin = CosmeticItems.byName(kind, choice);
        if (skin != null) stack.set(DataComponentTypes.ITEM_MODEL, skin.model());
        icons.put(choice,stack);return stack;
    }

    // ---- drawing ------------------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        long now = System.nanoTime();
        frameTime=previousFrame==0?0:Math.min(.1f,(now-previousFrame)/1e9f);
        if (previousFrame != 0 && rotating && !dragging) rotation += Math.min(.05f,frameTime) * 22;
        previousFrame = now;
        rotation %= 360;
        Theme.update();

        w = Math.min(760, width - 24);
        h = Math.min(420, height - 20);
        x = Math.round((width - w) / 2f);
        y = Math.round((height - h) / 2f);
        previewW = Math.round(w * 0.38f);
        previewTop=h<240?32:42;

        Fonts.beginRaw();
        try {
            Render2D.rect(ctx, 0, 0, width, height, 0xB5080A0D);
            Render2D.shadow(ctx, x, y + 4, w, h, 14, 20, 0x90000000);
            Render2D.roundRect(ctx, x, y, w, h, 12, 0xFC101216);
            Render2D.roundOutline(ctx, x, y, w, h, 12, 1, 0x1CFFFFFF);
            Fonts.drawV(ctx, "Cosmetics", x + 16, y + 16, Theme.TEXT, true, 1.18f);
            if(h>=240)Fonts.drawV(ctx, "Try a style. Make it yours.",x+16,y+31,Theme.TEXT_MUTED,false,.67f);

            // On/off and Done.
            float bw = 64, bh = 24, by = y + 8;
            button(ctx, "Done", x + w - 12 - bw, by, bw, bh, false, mx, my, this::close);
            boolean on = module.isEnabled();
            float tx = x + w - 12 - bw * 2 - 6;
            button(ctx, on ? "Enabled" : "Disabled", tx, by, bw, bh, false, mx, my, () -> {
                module.toggle();
                Sounds.toggle(module.isEnabled());
            });
            if (on) Render2D.circle(ctx, tx + 8, by + bh / 2f, 2.4f, Theme.GREEN);

            preview(ctx, mx, my);
            tabs(ctx, mx, my);
            list(ctx, mx, my);
            footer(ctx, mx, my);
            if(categoriesOpen)categories(ctx,mx,my);
        } finally {
            Fonts.endRaw();
        }
    }

    private void preview(DrawContext ctx, int mx, int my) {
        float px = x + 10, py = y + previewTop, pw = previewW, ph = h - previewTop - 42;
        Render2D.roundRect(ctx, px, py, pw, ph, 9, 0xFF171C22, 0xFF171C22, 0xFF222A32, 0xFF222A32);
        Render2D.roundOutline(ctx, px, py, pw, ph, 9, 1, 0x18FFFFFF);
        Fonts.drawV(ctx,"LIVE PREVIEW",px+12,py+13,Theme.TEXT_MUTED,true,.59f);
        boolean modelPreview=tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.contains(current());
        if(modelPreview){
            dev.maro.render.pickaxe.PickaxePreview.draw(ctx,current(),module.previewStyle(rotation,pickaxePitch,pickaxeZoom),Math.round(px+6),Math.round(py+25),Math.round(pw-12),Math.round(ph-48));
            Fonts.drawCentered(ctx,current(),px+pw/2,py+ph-12,Theme.TEXT,true,.76f);
        }else if (client != null && client.player != null) {
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            var state = (PlayerEntityRenderState) renderer.getAndUpdateRenderState(client.player, client.getRenderTickCounter().getTickProgress(false));
            state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.bodyYaw = 180 + rotation;
            state.relativeHeadYaw = 0;
            state.pitch = 0;
            state.width /= state.baseScale;
            state.height /= state.baseScale;
            state.baseScale = 1;
            dev.maro.render.accessories.CosmeticPreview.hold(state, previewItem(), client.player);
            float size = Math.min((pw - 12) / (3.4f * Math.max(1, module.wingSize())), (ph - 38) / (2.9f * Math.max(1, module.headSize() * .85f)));
            ctx.addEntity(state, size, new Vector3f(0, state.height / 2 + .08f, 0),
                    new Quaternionf().rotateZ((float) Math.PI).rotateX(.10f), new Quaternionf().rotateX(.10f),
                    Math.round(px + 2), Math.round(py + 24), Math.round(px + pw - 2), Math.round(py + ph - 6));
        } else {
            Fonts.drawCentered(ctx, "Join a world to see yourself", px + pw / 2f, py + ph / 2f, Theme.TEXT_MUTED, false, 0.7f);
        }
        // On a weapon tab, the chosen skin large in the corner, at its Item Size.
        String chosen = current();
        ItemStack big = chosen.equals("None") ? null : icon(chosen);
        if (big != null&&!modelPreview) {
            float s = Math.min(Math.min(3.4f, pw / 70f) * module.itemSize(), (pw - 24) / 32f);
            float bx = px + pw - 16 * s - 8, byy = py + ph - 16 * s - 8;
            Render2D.roundRect(ctx, bx - 4, byy - 4, 16 * s + 8, 16 * s + 8, 8, 0x50000000);
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(bx, byy);
            m.scale(s, s);
            ctx.drawItem(big, 0, 0);
            m.popMatrix();
            Fonts.drawRight(ctx, Fonts.trim(chosen,pw-16,false,.64f), px + pw - 8, byy - 10, Theme.TEXT_DIM, false, 0.64f);
        }
        // Drag across the preview to turn it.
        hit(px, py, pw, ph, (hx, hy, b) -> {
            dragging = true;
            rotating = false;
            dragFromX = hx;dragFromY=hy;dragFromPitch=pickaxePitch;
            dragFromRotation = rotation;
        });
        float third = (pw - 8) / 3f, by = py + ph + 12;
        button(ctx, "Front", px, by, third, 24, false, mx, my, () -> showAngle(0));
        button(ctx, "Back", px + third + 4, by, third, 24, false, mx, my, () -> showAngle(180));
        button(ctx, "Reset", px + (third + 4) * 2, by, third, 24, false, mx, my, () -> {
            rotation = -25;pickaxePitch=0;pickaxeZoom=1;
            rotating = true;
        });
    }

    private void tabs(DrawContext ctx, int mx, int my) {
        float tx = x + previewW + 22, tw = w - previewW - 34, ty = y + previewTop;
        categoryX=tx;categoryY=ty;categoryW=Math.min(132,tw*.43f);
        button(ctx,Fonts.trim(tab+"  ▾",categoryW-14,true,.72f),tx,ty,categoryW,24,false,mx,my,()->{
            categoriesOpen=!categoriesOpen;typing=false;
            categoryScroll=Math.max(0,List.of(TABS).indexOf(tab)*24-70);
        });
        searchX=tx+categoryW+6;searchW=tw-categoryW-6;
        Render2D.roundRect(ctx,searchX,ty,searchW,24,6,0xFF191E25);
        if(typing)Render2D.roundOutline(ctx,searchX,ty,searchW,24,6,.7f,Theme.accent(0xB0));
        String text=tab.equals("Colors")?"Colors & motion":query.isEmpty()&&!typing?"Search styles…":query;
        Fonts.drawV(ctx,Fonts.trim(text,searchW-14,false,.71f),searchX+8,ty+12,query.isEmpty()?Theme.TEXT_MUTED:Theme.TEXT,false,.71f);
        if(typing&&(System.nanoTime()/500_000_000L)%2==0){float caret=searchX+8+Math.min(searchW-16,Fonts.width(query,false,.71f));Render2D.rect(ctx,caret,ty+7,1,10,Theme.TEXT);}
        if(!tab.equals("Colors"))hit(searchX,ty,searchW,24,(hx,hy,b)->{typing=true;categoriesOpen=false;});
        listX = tx;
        listY = ty + 32;
        listW = tw;
        listH = y + h - 64 - listY;
    }

    private void categories(DrawContext ctx,int mx,int my){
        ctx.createNewRootLayer();dropY=categoryY+29;dropH=Math.min(TABS.length*24+12,y+h-dropY-12);
        categoryScroll=Math.clamp(categoryScroll,0,Math.max(0,TABS.length*24+12-dropH));
        Render2D.shadow(ctx,categoryX,dropY,Math.max(132,categoryW),dropH,8,12,0x70000000);
        Render2D.roundRect(ctx,categoryX,dropY,Math.max(132,categoryW),dropH,8,0xFF1D232C);
        Render2D.roundOutline(ctx,categoryX,dropY,Math.max(132,categoryW),dropH,8,.7f,0xFF46515E);
        Render2D.clip(ctx,Math.round(categoryX),Math.round(dropY+4),Math.round(categoryX+Math.max(132,categoryW)),Math.round(dropY+dropH-4));
        try{for(int i=0;i<TABS.length;i++){
            String name=TABS[i];float ry=dropY+6+i*24-categoryScroll;if(ry+22<dropY||ry>dropY+dropH)continue;
            boolean selected=tab.equals(name);if(selected||inside(mx,my,categoryX+4,ry,Math.max(132,categoryW)-8,22))Render2D.roundRect(ctx,categoryX+4,ry,Math.max(132,categoryW)-8,22,5,selected?0xFF31434B:0xFF2B333E);
            Fonts.drawV(ctx,name,categoryX+12,ry+11,selected?0xFF9FE6D0:Theme.TEXT,false,.74f);
            float top=Math.max(ry,dropY+4),bottom=Math.min(ry+22,dropY+dropH-4);
            if(bottom>top)hit(categoryX+4,top,Math.max(132,categoryW)-8,bottom-top,(hx,hy,b)->{showTab(name);Sounds.click();});
        }}finally{Render2D.unclip(ctx);}
    }

    private void list(DrawContext ctx, int mx, int my) {
        Render2D.roundRect(ctx, listX, listY, listW, listH, 8, 0xFF15191F);
        if (tab.equals("Colors")) {
            colors(ctx, mx, my);
            return;
        }
        List<String> choices = shownChoices();
        String now = current();
        int cols=columns();float cw=(listW-16-(cols-1)*6)/cols;
        float content = ((choices.size()+cols-1)/cols) * (ROW + ROW_GAP) + 7;
        float maxScroll = Math.max(0, content - listH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1-(float)Math.exp(-18*frameTime));
        Render2D.clip(ctx, Math.round(listX), Math.round(listY + 2), Math.round(listX + listW), Math.round(listY + listH - 2));
        for (int i=0;i<choices.size();i++) {
            String choice=choices.get(i);float rx=listX+8+(i%cols)*(cw+6),ry=listY+6+(i/cols)*(ROW+ROW_GAP)-scroll;
            if (ry + ROW >= listY && ry <= listY + listH) {
                boolean sel = choice.equals(now);
                boolean hov = inside(mx, my, rx, ry, cw, ROW) && my >= listY && my < listY + listH;
                Render2D.roundRect(ctx, rx, ry, cw, ROW, 6, sel ? 0xFF283B3A : hov ? 0xFF272E38 : 0xFF1D232B);
                if(sel)Render2D.roundOutline(ctx,rx,ry,cw,ROW,6,.7f,0xFF60998C);
                ItemStack icon = choice.equals("None") ? null : icon(choice);
                float textX = rx+10;
                if (icon != null) {
                    var m = ctx.getMatrices();
                    m.pushMatrix();
                    m.translate(rx+7, ry+4);
                    m.scale(1.25f, 1.25f);
                    ctx.drawItem(icon, 0, 0);
                    m.popMatrix();
                    textX=rx+31;
                }
                Fonts.drawV(ctx,Fonts.trim(choice,cw-(textX-rx)-17,sel,.72f), textX, ry + ROW / 2f, sel ? 0xFFE1F9EF : Theme.TEXT, sel, 0.72f);
                if(sel)Render2D.circle(ctx,rx+cw-9,ry+ROW/2,2.5f,0xFF92DCC4);
                float top = Math.max(ry, listY), bottom = Math.min(ry + ROW, listY + listH);
                if (bottom > top) hit(rx, top, cw, bottom - top, (hx, hy, b) -> {
                    choose(choice);
                    Sounds.click();
                });
            }
        }
        Render2D.unclip(ctx);
        if(choices.isEmpty())Fonts.drawCentered(ctx,"No matching styles",listX+listW/2,listY+listH/2,Theme.TEXT_MUTED,false,.72f);
        if (maxScroll > 0) {
            float bar = Math.max(16, listH * listH / content);
            Render2D.roundRect(ctx, listX + listW - 5, listY + (listH - bar) * (scroll / maxScroll), 3, bar, 1.5f, Theme.accent(0xA0));
        }
    }

    /** The Colors tab: the three colours (click to step through a palette) and the motion switches. */
    private void colors(DrawContext ctx, int mx, int my) {
        float content=7*(ROW+ROW_GAP)+12,maxScroll=Math.max(0,content-listH);
        scrollTarget=Math.clamp(scrollTarget,0,maxScroll);scroll+=(scrollTarget-scroll)*(1-(float)Math.exp(-18*frameTime));
        Render2D.clip(ctx,Math.round(listX),Math.round(listY+2),Math.round(listX+listW),Math.round(listY+listH-2));
        float ry = listY + 6-scroll;
        try{
        for (String name : new String[] {"Primary Color", "Accent Color", "Halo Color"}) {
            ColorSetting c = (ColorSetting) setting(name);
            boolean hov = inside(mx, my, listX + 4, ry, listW - 12, ROW);
            Render2D.roundRect(ctx, listX + 4, ry, listW - 12, ROW, 5, hov ? 0xFF232838 : 0xFF181C26);
            Fonts.drawV(ctx, name, listX + 12, ry + ROW / 2f, Theme.TEXT, false, 0.7f);
            Render2D.roundRect(ctx, listX + listW - 40, ry + 4, 26, ROW - 8, 3, c.get());
            clippedHit(listX + 4, ry, listW - 12, ROW, (hx, hy, b) -> {
                int i = 0;
                for (int k = 0; k < PALETTE.length; k++) if ((PALETTE[k] & 0xFFFFFF) == (c.get() & 0xFFFFFF)) i = k;
                c.set(PALETTE[(i + (b == 1 ? PALETTE.length - 1 : 1)) % PALETTE.length]);
                Sounds.click();
            });
            ry += ROW + ROW_GAP;
        }
        for (String name : new String[] {"Rainbow", "Glowing Accents", "Animate", "React To Movement"}) {
            BooleanSetting b = (BooleanSetting) setting(name);
            boolean hov = inside(mx, my, listX + 4, ry, listW - 12, ROW);
            Render2D.roundRect(ctx, listX + 4, ry, listW - 12, ROW, 5, hov ? 0xFF232838 : 0xFF181C26);
            Fonts.drawV(ctx, name, listX + 12, ry + ROW / 2f, Theme.TEXT, false, 0.7f);
            float sx = listX + listW - 40;
            Render2D.roundRect(ctx, sx, ry + 7, 28, 14, 7, b.get() ? Theme.accent() : 0xFF343B45);
            Render2D.circle(ctx, b.get() ? sx + 21 : sx + 7, ry + ROW / 2f, 5, 0xFFFFFFFF);
            clippedHit(listX + 4, ry, listW - 12, ROW, (hx, hy, bt) -> {
                b.toggle();
                Sounds.toggle(b.get());
            });
            ry += ROW + ROW_GAP;
        }
        }finally{Render2D.unclip(ctx);}
        if(maxScroll>0){float bar=Math.max(14,listH*listH/content);Render2D.roundRect(ctx,listX+listW-4,listY+(listH-bar)*scroll/maxScroll,2,bar,1,Theme.accent(0x90));}
    }

    private void footer(DrawContext ctx, int mx, int my) {
        NumberSetting size = size();
        float fy = listY + listH + 6;
        if (size != null) {
            Fonts.drawV(ctx, "Size", listX + 2, fy + 4, Theme.TEXT_DIM, false, 0.66f);
            // Sizes read as a share of normal size (100% is as made); the halo's height in pixels.
            String value = size.getName().equals("Halo Height") ? size.format() : Math.round(size.getFloat() * 100) + "%";
            Fonts.drawRight(ctx, value, listX + listW - 2, fy + 4, Theme.TEXT_DIM, false, 0.66f);
            sliderX = listX + 2;
            sliderW = listW - 4;
            float sy = fy + 14;
            float pct = (float) size.getPercent();
            Render2D.roundRect(ctx, sliderX, sy - 2, sliderW, 4, 2, 0xFF2A2F3D);
            Render2D.roundGradientH(ctx, sliderX, sy - 2, sliderW * pct, 4, 2, Theme.accent2(), Theme.accent());
            Render2D.circle(ctx, sliderX + sliderW * pct, sy, 4.2f, 0xFFFFFFFF);
            hit(sliderX - 4, sy - 6, sliderW + 8, 12, (hx, hy, b) -> {
                if (b == 1) {
                    size.reset();
                    return;
                }
                sizing = true;
                size.setPercent((hx - sliderX) / sliderW);
            });
        }
        float ay = y + h - 30;
        if(tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.contains(current())){
            button(ctx,"Swing",listX,ay,48,24,false,mx,my,()->{dev.maro.render.pickaxe.PickaxeMotion.preview(current());module.audio().preview(module);});
            button(ctx,"Options",listX+52,ay,55,24,false,mx,my,()->{var gui=parent instanceof dev.maro.gui.ClickGuiScreen p?p:new dev.maro.gui.ClickGuiScreen();client.setScreen(gui);gui.openModuleOptions(module);});
        }
        button(ctx, "Apply look", listX+(tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.contains(current())?112:0), ay, listW-(tab.equals("Pickaxe")&&dev.maro.render.pickaxe.PickaxeModels.contains(current())?112:0), 24, true, mx, my, () -> {
            module.setEnabled(true);
            Sounds.toggle(true);
            close();
        });
    }

    private void button(DrawContext ctx, String label, float bx, float by, float bw, float bh, boolean accent, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, bx, by, bw, bh);
        if (accent) Render2D.roundRect(ctx, bx, by, bw, bh, 6, hover ? Theme.accent() : Theme.accent(0xD0));
        else {
            Render2D.roundRect(ctx, bx, by, bw, bh, 6, hover ? 0xFF2B323D : 0xFF20262E);
        }
        Fonts.drawCentered(ctx, label, bx + bw / 2f, by + bh / 2f, accent || hover ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.68f);
        hit(bx, by, bw, bh, (hx, hy, b) -> {
            Sounds.click();
            action.run();
        });
    }

    private void hit(float hx, float hy, float hw, float hh, Action action) {
        hits.add(new Hit(hx, hy, hw, hh, action));
    }
    private void clippedHit(float hx,float hy,float hw,float hh,Action action){float top=Math.max(hy,listY+2),bottom=Math.min(hy+hh,listY+listH-2);if(bottom>top)hit(hx,top,hw,bottom-top,action);}

    private static boolean inside(double mx, double my, float bx, float by, float bw, float bh) {
        return mx >= bx && my >= by && mx < bx + bw && my < by + bh;
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if(categoriesOpen&&!inside(click.x(),click.y(),categoryX,dropY,Math.max(132,categoryW),dropH)){categoriesOpen=false;return true;}
        if(!inside(click.x(),click.y(),searchX,categoryY,searchW,24))typing=false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                hit.action().run(click.x(), click.y(), click.button());
                return true;
            }
        }
        if (click.x() < x || click.x() > x + w || click.y() < y || click.y() > y + h) close();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) {
            rotation = dragFromRotation + (float) (click.x() - dragFromX) * 1.6f;
            pickaxePitch=Math.clamp(dragFromPitch+(float)(click.y()-dragFromY)*.7f,-55,55);
            return true;
        }
        if (sizing && size() != null) {
            size().setPercent((click.x() - sliderX) / sliderW);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        sizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if(tab.equals("Pickaxe")&&inside(mx,my,x+10,y+previewTop,previewW,h-previewTop-42)){pickaxeZoom=Math.clamp(pickaxeZoom+(float)vertical*.06f,.65f,1.15f);return true;}
        if(categoriesOpen)categoryScroll-=(float)vertical*28;
        else if(inside(mx,my,listX,listY,listW,listH))scrollTarget -= (float) vertical * 36;
        return true;
    }

    @Override public boolean charTyped(CharInput input){if(typing&&input.codepoint()>=32&&input.codepoint()<=Character.MAX_VALUE&&query.length()<64){search(query+(char)input.codepoint());return true;}return super.charTyped(input);}

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if(categoriesOpen)categoriesOpen=false;else if(typing){typing=false;search("");}else close();
            return true;
        }
        if(typing&&input.key()==GLFW.GLFW_KEY_BACKSPACE){if(!query.isEmpty())search(query.substring(0,query.length()-1));return true;}
        if(typing&&input.key()==GLFW.GLFW_KEY_ENTER){typing=false;return true;}
        if(!tab.equals("Colors")&&input.key()==GLFW.GLFW_KEY_F&&(input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0){typing=true;categoriesOpen=false;return true;}
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
