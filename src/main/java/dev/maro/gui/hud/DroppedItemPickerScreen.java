package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ItemSelectionSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Search is cached per edit; only the visible page creates widgets or renders item icons. */
public final class DroppedItemPickerScreen extends Screen {
    private record Entry(Item item, ItemStack icon, String name, String id, String search) { }
    private final Screen parent;
    private final ItemSelectionSetting selection;
    private final List<Entry> entries;
    private List<Entry> filtered = List.of();
    private final List<ButtonWidget> rows = new ArrayList<>();
    private TextFieldWidget search;
    private ButtonWidget selectedButton;
    private String query = "";
    private boolean selectedOnly, dragging;
    private int x, y, w, h, columns, visibleRows, firstRow;
    private static final int ROW = 36;

    public DroppedItemPickerScreen(Screen parent, ItemSelectionSetting selection) {
        super(Text.literal("Nametags · Dropped Items"));
        this.parent = parent;
        this.selection = selection;
        entries = Registries.ITEM.stream().filter(item -> item != Items.AIR).map(item -> {
            String name = item.getName().getString(), id = Registries.ITEM.getId(item).toString();
            return new Entry(item, new ItemStack(item), name, id, (name + " " + id).toLowerCase(Locale.ROOT));
        }).sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
    }

    @Override protected void init() {
        w = Math.min(700, width - 20); h = Math.min(460, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        columns = w >= 520 ? 2 : 1;
        visibleRows = Math.max(1, (h - 132) / ROW);
        search = new TextFieldWidget(textRenderer, x + 20, y + 48, w - 40, 14, Text.literal("Search items"));
        search.setDrawsBackground(false);
        search.setMaxLength(100); search.setPlaceholder(Text.literal("Search by item name or ID...")); search.setText(query);
        search.setChangedListener(value -> { query = value; firstRow = 0; filter(); });
        addDrawableChild(search); setInitialFocus(search);
        int bw = (w - 40) / 3;
        selectedButton = control("Selected only", x + 14, y + 68, bw, 18, b -> {
            selectedOnly = !selectedOnly; firstRow = 0; filter();
        });
        control("Select results", x + 20 + bw, y + 68, bw, 18, b -> {
            var next = new LinkedHashSet<>(selection.get()); filtered.forEach(entry -> next.add(entry.item)); selection.set(next); filter();
        });
        control("Clear selection", x + 26 + bw * 2, y + 68, bw, 18, b -> { selection.set(java.util.Set.of()); filter(); });
        control("All Blocks", x + w - 174, y + h - 30, 80, 20, b -> {
            selection.selectAllBlocks(); selectedOnly = true; firstRow = 0; search.setText(""); filter();
        });
        control("Done", x + w - 86, y + h - 30, 72, 20, b -> close());
        filter();
    }

    private ButtonWidget control(String label,int bx,int by,int bw,int bh,ButtonWidget.PressAction action) {
        return addDrawableChild(new ControlButton(label,bx,by,bw,bh,action));
    }
    private static final class ControlButton extends ButtonWidget {
        ControlButton(String label,int bx,int by,int bw,int bh,PressAction action) {
            super(bx,by,bw,bh,net.minecraft.text.Text.literal(label),action,DEFAULT_NARRATION_SUPPLIER);
        }
        @Override public void drawIcon(DrawContext ctx,int mx,int my,float delta) {
            Render2D.roundRect(ctx,getX(),getY(),getWidth(),getHeight(),4,isSelected()?0xFF2B3D54:0xFF1F2A3B);
            Render2D.roundOutline(ctx,getX(),getY(),getWidth(),getHeight(),4,.7f,isSelected()?Theme.accent():0xFF354860);
            Fonts.drawCentered(ctx,Fonts.trim(getMessage().getString(),getWidth()-8,true,.65f),getX()+getWidth()/2f,getY()+getHeight()/2f,Theme.TEXT,true,.65f);
        }
    }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = entries.stream().filter(e -> (!selectedOnly || selection.contains(e.item)) && e.search.contains(needle)).toList();
        firstRow = Math.clamp(firstRow, 0, maxRow());
        if (selectedButton != null) selectedButton.setMessage(Text.literal(selectedOnly ? "Showing selected" : "Selected only"));
        rebuildRows();
    }

    private int maxRow() { return Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows); }

    private void rebuildRows() {
        rows.forEach(this::remove); rows.clear();
        int cellW = (w - 34) / columns;
        for (int i = firstRow * columns; i < Math.min(filtered.size(), (firstRow + visibleRows) * columns); i++) {
            Entry entry = filtered.get(i);
            int index = i - firstRow * columns;
            rows.add(addDrawableChild(new ItemButton(x + 14 + index % columns * cellW, y + 94 + index / columns * ROW, cellW - 5, entry)));
        }
    }

    private final class ItemButton extends ButtonWidget {
        private final Entry entry;
        ItemButton(int bx, int by, int bw, Entry entry) {
            super(bx, by, bw, ROW - 4, net.minecraft.text.Text.literal(entry.name + " · " + entry.id), b -> {
                selection.toggle(entry.item); if (selectedOnly) filter();
            }, DEFAULT_NARRATION_SUPPLIER);
            this.entry = entry;
        }
        @Override public void drawIcon(DrawContext ctx, int mx, int my, float delta) {
            boolean picked = selection.contains(entry.item);
            Render2D.roundRect(ctx, getX(), getY(), getWidth(), getHeight(), 5, picked ? 0xFF26364A : isHovered() || isFocused() ? 0xFF242C3B : 0xFF181F2B);
            if (picked || isFocused()) Render2D.roundOutline(ctx, getX(), getY(), getWidth(), getHeight(), 5, .8f, Theme.accent());
            ctx.drawItem(entry.icon, getX() + 7, getY() + 8);
            Fonts.draw(ctx, Fonts.trim(entry.name, getWidth() - 61, true, .72f), getX() + 30, getY() + 6, Theme.TEXT, true, .72f);
            Fonts.draw(ctx, Fonts.trim(entry.id, getWidth() - 61, false, .55f), getX() + 30, getY() + 20, Theme.TEXT_MUTED, false, .55f);
            Fonts.drawCentered(ctx, picked ? "✓" : "+", getX() + getWidth() - 14, getY() + 16, picked ? Theme.accent() : Theme.TEXT_MUTED, true, .9f);
        }
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        Render2D.rect(ctx, 0, 0, width, height, 0xB0080B12);
        Render2D.roundRect(ctx, x, y, w, h, 12, 0xF510151F);
        Render2D.roundOutline(ctx, x, y, w, h, 12, .8f, 0x405A7290);
        Fonts.draw(ctx, title.getString(), x + 14, y + 12, Theme.TEXT, true, .95f);
        Fonts.draw(ctx, "Pick items to label. Changes apply live.", x + 14, y + 29, Theme.TEXT_MUTED, false, .63f);
        Render2D.roundRect(ctx,x+14,y+42,w-28,22,4,0xFF090F18);
        Render2D.roundOutline(ctx,x+14,y+42,w-28,22,4,.7f,search.isFocused()?Theme.accent():0xFF354860);
        Fonts.draw(ctx, selection.get().size() + " selected · " + filtered.size() + " results", x + 14, y + h - 24, Theme.TEXT_MUTED, false, .65f);
        if (filtered.isEmpty()) Fonts.drawCentered(ctx, "No matching items", x + w / 2f, y + 112, Theme.TEXT_MUTED, false, .8f);
        if (maxRow() > 0) {
            float track = visibleRows * ROW - 4, thumb = Math.max(16, track * visibleRows / (maxRow() + visibleRows));
            Render2D.roundRect(ctx, x + w - 10, y + 94, 3, track, 1.5f, 0xFF273143);
            Render2D.roundRect(ctx, x + w - 10, y + 94 + firstRow / (float) maxRow() * (track - thumb), 3, thumb, 1.5f, Theme.accent());
        }
        super.render(ctx, mx, my, delta);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= x && mx < x + w && my >= y + 90 && my < y + h - 36) {
            firstRow = Math.clamp(firstRow - (int) Math.signum(vertical) * 2, 0, maxRow()); rebuildRows(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    private void drag(double my) {
        float track = visibleRows * ROW - 4, thumb = Math.max(16, track * visibleRows / (maxRow() + visibleRows));
        firstRow = Math.clamp((int) Math.round((my - y - 94 - thumb / 2) / Math.max(1, track - thumb) * maxRow()), 0, maxRow());
        rebuildRows();
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0 && maxRow() > 0 && click.x() >= x + w - 14 && click.x() < x + w - 4 && click.y() >= y + 94 && click.y() < y + 94 + visibleRows * ROW) {
            dragging = true; drag(click.y()); return true;
        }
        return super.mouseClicked(click, doubled);
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) { drag(click.y()); return true; }
        return super.mouseDragged(click, dx, dy);
    }
    @Override public boolean mouseReleased(Click click) { dragging = false; return super.mouseReleased(click); }
    @Override public void close() { ConfigManager.saveAll(); client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
