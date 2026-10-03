package dev.maro.nathan.regionmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Region placements adapted from the user-provided RegionMapUpdated-SOURCE.zip. */
public final class RegionGrid {
    public static final int SIDE = 36;
    public static final double CELL_BLOCKS = 12500;
    public static final double ORIGIN = -225000;

    public enum Locale {
        EuCentral("EU Central"),
        EuWest("EU West"),
        NaEast("NA East"),
        NaWest("NA West"),
        Asia("Asia"),
        Oceania("Oceania"),
        Europe("Europe");

        private final String title;
        Locale(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public record Shard(Locale locale, int number, int col, int row, int width, int height) {}

    private static final int[] AT = new int[SIDE * SIDE];
    private static final Shard[] SHARDS;

    static {
        Arrays.fill(AT, -1);
        List<Shard> shards = new ArrayList<>();
        add(shards, Locale.EuCentral, new int[][] {
            {12, 20, 2, 4}, {14, 20, 2, 4}, {16, 20, 2, 4}, {18, 20, 2, 4}, {20, 20, 2, 4},
            {22, 20, 2, 2}, {26, 20, 2, 4}, {28, 20, 2, 4}, {30, 20, 2, 2}, {22, 22, 2, 2},
            {24, 22, 1, 2}, {25, 22, 1, 2}, {12, 24, 2, 4}, {14, 24, 2, 4}, {16, 24, 2, 4},
            {18, 24, 2, 4}, {20, 24, 2, 4}, {22, 24, 2, 4}, {24, 24, 2, 4}, {28, 24, 2, 2},
            {30, 24, 2, 4}, {26, 26, 2, 2}, {0, 28, 4, 1}, {8, 28, 2, 2}, {10, 28, 2, 4},
            {12, 28, 2, 2}, {14, 28, 2, 2}, {16, 28, 2, 2}, {18, 28, 2, 2}, {20, 28, 2, 2},
            {22, 28, 2, 2}, {24, 28, 2, 2}, {26, 28, 2, 4}, {28, 28, 2, 2}, {30, 28, 2, 2},
            {32, 28, 2, 2}, {0, 29, 4, 1}, {0, 30, 2, 2}, {2, 30, 2, 2}, {16, 30, 2, 2},
            {18, 30, 2, 2}, {22, 30, 2, 2}, {24, 30, 2, 2}, {32, 30, 2, 2}, {34, 30, 2, 2},
            {0, 32, 2, 2}, {28, 32, 2, 4}, {32, 32, 2, 4}, {0, 34, 4, 2}, {30, 34, 2, 2},
            {34, 34, 2, 2}, {26, 24, 2, 2}
        });
        add(shards, Locale.EuWest, new int[][] {
            {4, 20, 2, 4}, {6, 20, 2, 4}, {8, 20, 2, 4}, {10, 20, 2, 4}, {4, 24, 2, 4},
            {6, 24, 2, 4}, {8, 24, 2, 4}, {10, 24, 2, 4}, {4, 28, 2, 4}, {6, 28, 2, 4},
            {4, 32, 2, 4}, {6, 32, 2, 2}, {8, 32, 2, 2}, {10, 32, 2, 2}, {12, 32, 2, 2},
            {14, 32, 2, 4}, {16, 32, 2, 4}, {18, 32, 2, 2}, {20, 32, 2, 4}, {22, 32, 2, 4},
            {24, 32, 2, 4}, {26, 32, 2, 4}, {6, 34, 2, 2}, {8, 34, 2, 2}, {10, 34, 2, 2},
            {12, 34, 2, 2}, {18, 34, 2, 2}
        });
        add(shards, Locale.NaEast, new int[][] {
            {16, 0, 2, 4}, {18, 0, 2, 4}, {20, 0, 2, 4}, {22, 0, 2, 4}, {24, 0, 4, 4},
            {28, 0, 2, 4}, {30, 0, 2, 4}, {32, 0, 2, 4}, {34, 0, 2, 4}, {16, 4, 2, 2},
            {18, 4, 2, 2}, {20, 4, 2, 2}, {22, 4, 2, 2}, {24, 4, 2, 2}, {26, 4, 2, 4},
            {28, 4, 2, 2}, {30, 4, 2, 2}, {32, 4, 2, 4}, {34, 4, 2, 4}, {16, 6, 2, 2},
            {18, 6, 2, 2}, {20, 6, 2, 2}, {22, 6, 2, 2}, {24, 6, 2, 2}, {28, 6, 2, 2},
            {30, 6, 2, 2}, {16, 8, 2, 4}, {18, 8, 2, 4}, {20, 8, 4, 4}, {24, 8, 2, 4},
            {26, 8, 2, 4}, {28, 8, 2, 4}, {30, 8, 2, 4}, {32, 8, 2, 4}, {34, 8, 2, 4},
            {16, 12, 2, 4}, {18, 12, 2, 4}, {20, 12, 4, 4}, {24, 12, 2, 4}, {26, 12, 2, 4},
            {28, 12, 2, 4}, {30, 12, 2, 4}, {32, 12, 2, 4}, {34, 12, 2, 4}, {16, 16, 4, 4},
            {20, 16, 2, 4}, {22, 16, 2, 2}, {24, 16, 4, 4}, {28, 16, 2, 4}, {30, 16, 2, 4},
            {32, 16, 2, 4}, {34, 16, 2, 4}, {22, 18, 2, 2}, {32, 20, 2, 4}, {34, 20, 2, 4},
            {32, 24, 2, 4}, {34, 24, 2, 4}
        });
        add(shards, Locale.NaWest, new int[][] {
            {4, 0, 2, 4}, {6, 0, 2, 4}, {8, 0, 2, 4}, {10, 0, 2, 4}, {12, 0, 2, 4},
            {14, 0, 2, 4}, {4, 4, 2, 4}, {6, 4, 2, 4}, {8, 4, 2, 4}, {10, 4, 2, 4},
            {12, 4, 2, 4}, {14, 4, 2, 4}, {4, 8, 2, 4}, {6, 8, 2, 4}, {8, 8, 2, 4},
            {10, 8, 2, 4}, {12, 8, 2, 4}, {14, 8, 2, 4}, {12, 12, 2, 4}, {14, 12, 2, 4}
        });
        add(shards, Locale.Asia, new int[][] {
            {0, 16, 2, 4}, {2, 16, 2, 4}, {4, 16, 2, 4}, {6, 16, 2, 4}, {8, 16, 2, 2},
            {10, 16, 2, 2}, {12, 16, 2, 2}, {14, 16, 2, 2}, {8, 18, 2, 2}, {10, 18, 2, 2},
            {12, 18, 2, 2}, {14, 18, 2, 2}, {0, 20, 2, 4}, {2, 20, 2, 4}, {0, 24, 2, 4},
            {2, 24, 2, 4}
        });
        add(shards, Locale.Oceania, new int[][] {
            {0, 0, 2, 2}, {2, 0, 2, 4}, {0, 2, 2, 2}, {0, 4, 2, 2}, {2, 4, 2, 4},
            {0, 6, 2, 2}, {0, 8, 2, 2}, {2, 8, 2, 4}, {0, 10, 2, 2}, {0, 12, 2, 2},
            {2, 12, 2, 4}, {4, 12, 2, 4}, {6, 12, 2, 4}, {8, 12, 2, 4}, {10, 12, 2, 4},
            {0, 14, 2, 2}
        });
        add(shards, Locale.Europe, new int[][] {
            {24, 20, 2, 2}, {30, 22, 2, 2}, {28, 26, 2, 2}, {34, 28, 2, 2}, {8, 30, 2, 2},
            {12, 30, 2, 2}, {14, 30, 2, 2}, {20, 30, 2, 2}, {28, 30, 2, 2}, {30, 30, 2, 2},
            {2, 32, 2, 2}, {30, 32, 2, 2}, {34, 32, 2, 2}
        });
        for (int cell : AT) if (cell < 0) throw new IllegalStateException("Unassigned region-map box");
        SHARDS = shards.toArray(Shard[]::new);
    }

    private RegionGrid() {}

    private static void add(List<Shard> shards, Locale locale, int[][] rectangles) {
        Arrays.sort(rectangles, Comparator.<int[]>comparingInt(r -> r[1]).thenComparingInt(r -> r[0]));
        int number = 1;
        for (int[] rectangle : rectangles) {
            int col = rectangle[0], row = rectangle[1], width = rectangle[2], height = rectangle[3];
            if (col < 0 || row < 0 || width <= 0 || height <= 0 || col + width > SIDE || row + height > SIDE)
                throw new IllegalStateException("Region-map shard outside grid: " + locale + " " + number);
            int id = shards.size();
            shards.add(new Shard(locale, number++, col, row, width, height));
            for (int y = row; y < row + height; y++) {
                for (int x = col; x < col + width; x++) {
                    int at = y * SIDE + x;
                    if (AT[at] >= 0) throw new IllegalStateException("Overlapping region-map shards at " + x + "," + y);
                    AT[at] = id;
                }
            }
        }
    }

    public static double column(double worldX) { return (worldX - ORIGIN) / CELL_BLOCKS; }
    public static double row(double worldZ) { return (worldZ - ORIGIN) / CELL_BLOCKS; }

    public static int cell(double column, double row) {
        if (column < 0 || row < 0 || column >= SIDE || row >= SIDE) return -1;
        return AT[(int) row * SIDE + (int) column];
    }

    public static int count() { return SHARDS.length; }
    public static Shard shard(int id) { return SHARDS[id]; }
    public static int region(int id) { return SHARDS[id].number(); }
    public static Locale servedBy(int id) { return SHARDS[id].locale(); }

    public static long from(int box) { return (long) (ORIGIN + box * CELL_BLOCKS); }
    public static long to(int box) { return from(box) + (long) CELL_BLOCKS - 1; }
}
