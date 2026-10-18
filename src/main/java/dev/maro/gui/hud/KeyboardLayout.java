package dev.maro.gui.hud;

import dev.maro.util.KeyUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared ANSI key geometry for the chooser and the selected keys on the HUD. */
public final class KeyboardLayout {
    public static final int MAIN = 0xFFE3A0A7, FUNCTION = 0xFFE8D88F, NAV = 0xFFA3D0E8, PAD = 0xFF9ED8B4;
    private static final float UNIT = 33;
    public record Cap(int code, String label, float x, float y, float w, float h, int color) { }
    private static final List<Cap> KEYS = keyboard();
    private static final Map<Integer, Cap> BY_CODE = KEYS.stream().collect(Collectors.toUnmodifiableMap(Cap::code, Function.identity()));

    private KeyboardLayout() { }
    public static List<Cap> keys() { return KEYS; }
    public static Cap key(int code) { return BY_CODE.get(code); }

    private static List<Cap> keyboard() {
        List<Cap> out = new ArrayList<>();
        add(out, GLFW.GLFW_KEY_ESCAPE, "Esc", 24, 94, 1, 30, MAIN);
        for (int i = 0; i < 12; i++) add(out, GLFW.GLFW_KEY_F1 + i, "F" + (i + 1), 90 + i * UNIT + (i / 4) * 12, 94, 1, 30, FUNCTION);
        row(out, 134, new String[]{"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "−", "=", "Backspace"},
                new int[]{96,49,50,51,52,53,54,55,56,57,48,45,61,259}, new float[]{1,1,1,1,1,1,1,1,1,1,1,1,1,2});
        row(out, 168, new String[]{"Tab","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"},
                new int[]{258,81,87,69,82,84,89,85,73,79,80,91,93,92}, new float[]{1.5f,1,1,1,1,1,1,1,1,1,1,1,1,1.5f});
        row(out, 202, new String[]{"Caps Lock","A","S","D","F","G","H","J","K","L",";","'","Enter"},
                new int[]{280,65,83,68,70,71,72,74,75,76,59,39,257}, new float[]{1.75f,1,1,1,1,1,1,1,1,1,1,1,2.25f});
        row(out, 236, new String[]{"Shift","Z","X","C","V","B","N","M",",",".","/","Shift"},
                new int[]{340,90,88,67,86,66,78,77,44,46,47,344}, new float[]{2.25f,1,1,1,1,1,1,1,1,1,1,2.75f});
        row(out, 270, new String[]{"Ctrl","Win","Alt","Space","Alt","Win","Menu","Ctrl"},
                new int[]{341,343,342,32,346,347,348,345}, new float[]{1.25f,1.25f,1.25f,6.25f,1.25f,1.25f,1.25f,1.25f});
        int[] nav = {283,281,284,260,268,266,261,269,267};
        String[] names = {"Print","Scroll","Pause","Insert","Home","PgUp","Delete","End","PgDn"};
        for (int i = 0; i < nav.length; i++) add(out, nav[i], names[i], 533 + (i % 3) * UNIT, i < 3 ? 94 : 134 + ((i - 3) / 3) * 34, 1, 30, NAV);
        add(out, 265, "↑", 566, 236, 1, 30, NAV); add(out, 263, "←", 533, 270, 1, 30, NAV);
        add(out, 264, "↓", 566, 270, 1, 30, NAV); add(out, 262, "→", 599, 270, 1, 30, NAV);
        int[][] pad = {{282,331,332,333},{327,328,329,334},{324,325,326,-1},{321,322,323,335}};
        String[][] labels = {{"Num","/","×","−"},{"7","8","9","+"},{"4","5","6",""},{"1","2","3","Enter"}};
        for (int r = 0; r < pad.length; r++) for (int c = 0; c < 4; c++) if (pad[r][c] >= 0)
            add(out, pad[r][c], labels[r][c], 646 + c * UNIT, 134 + r * 34, 1, c == 3 && r > 0 ? 64 : 30, PAD);
        add(out, 320, "0", 646, 270, 2, 30, PAD); add(out, 330, ".", 712, 270, 1, 30, PAD);
        add(out, KeyUtil.mouse(0), "LMB", 24, 337, 2.5f, 42, FUNCTION);
        add(out, KeyUtil.mouse(1), "RMB", 111, 337, 2.5f, 42, FUNCTION);
        add(out, KeyUtil.mouse(2), "MMB", 198, 337, 2.5f, 42, FUNCTION);
        return List.copyOf(out);
    }
    private static void row(List<Cap> out, float y, String[] labels, int[] codes, float[] units) {
        float x = 24;
        for (int i = 0; i < codes.length; i++) { add(out, codes[i], labels[i], x, y, units[i], 30, MAIN); x += units[i] * UNIT; }
    }
    private static void add(List<Cap> out, int code, String label, float x, float y, float units, float height, int color) {
        out.add(new Cap(code, label, x, y, units * UNIT - 3, height, color));
    }
}
