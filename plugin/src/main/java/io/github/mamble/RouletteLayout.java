package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.mamble.MachineLayout.Kind;
import io.github.mamble.MachineLayout.Part;
import io.github.mamble.MachineLayout.Side;

/**
 * ルーレット卓の部品の配置。卓は 4 幅 × 3 奥行き。手前 2 行が賭けの配置、奥の行がホイール。
 *
 * <p>卓上座標 (x, d): x は正面から見て左端を 0、右端を 4。d は手前の面を 0、奥の面を 3。
 * base (置いたブロック) は手前の行の左から 2 番目なので、x = 1..2 が column 0。
 *
 * <pre>
 *   d 2.5 :            [ ホイール ]                    ← 奥の行 (row 2)
 *   d 1.8 : [ ] [ 3][ 6][ 9] … [36] [2:1]
 *   d 1.4 : [0] [ 2][ 5][ 8] … [35] [2:1]             ← 番号 (row 1)
 *   d 1.0 : [ ] [ 1][ 4][ 7] … [34] [2:1]
 *   d 0.6 :     [ 1st 12 ][ 2nd 12 ][ 3rd 12 ]
 *   d 0.2 :     [1-18][偶数][ 赤 ][ 黒 ][奇数][19-36]  ← 手前の行 (row 0)
 *           ↑ プレイヤー
 * </pre>
 */
public final class RouletteLayout {

    public static final double WIDTH = 4;
    public static final double DEPTH = 3;

    /** 列の幅。0 + 12 列 + コラムで 14 列。 */
    public static final double CELL_W = WIDTH / 14;

    /** 番号の行の間隔。 */
    public static final double ROW_D = 0.4;

    /** 卓の上面より少し上。 */
    public static final double TABLE_TOP = 0.5 + 0.015;

    public static final float CELL_TEXT_SCALE = 0.45f;

    /** セルの文字は 3 行 (空, 名前, チップ数)。平置きの文字は手前の端を起点に奥へ伸びるので、その半分だけ手前に置く。 */
    public static final int CELL_LINES = 3;
    public static final double CELL_TEXT_SHIFT = 0.25 * CELL_TEXT_SCALE * CELL_LINES / 2;

    public static final float PAD_WIDTH = 0.22f;

    /** ホイールの中心。 */
    public static final double WHEEL_X = 2.0;
    public static final double WHEEL_D = 2.5;
    public static final float WHEEL_SCALE = 0.96f;
    public static final float BALL_SCALE = 0.10f;

    /** セル1つ。 */
    public record Cell(String key, double x, double d, double width) {

        public boolean contains(double px, double pd) {
            return Math.abs(px - x) <= width / 2 && Math.abs(pd - d) <= ROW_D / 2;
        }
    }

    public static final List<Cell> CELLS = cells();

    private static List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        // 0 は左端の列の真ん中の行
        out.add(new Cell(RouletteRules.numberCell(0), center(0), 1.4, CELL_W));
        for (int n = 1; n <= 36; n++) {
            int column = (n - 1) / 3 + 1;
            int gridRow = (n - 1) % 3;
            out.add(new Cell(RouletteRules.numberCell(n), center(column), 1.0 + ROW_D * gridRow, CELL_W));
        }
        // コラム: 右端の列。c1 は 1,4,7…の行 (手前)、c3 は 3,6,9…の行 (奥)
        out.add(new Cell("c1", center(13), 1.0, CELL_W));
        out.add(new Cell("c2", center(13), 1.4, CELL_W));
        out.add(new Cell("c3", center(13), 1.8, CELL_W));
        // ダース: 番号 12 列ぶんを 3 等分
        double dozen = CELL_W * 4;
        out.add(new Cell("d1", CELL_W * 1 + dozen * 0.5, 0.6, dozen));
        out.add(new Cell("d2", CELL_W * 1 + dozen * 1.5, 0.6, dozen));
        out.add(new Cell("d3", CELL_W * 1 + dozen * 2.5, 0.6, dozen));
        // 1:1: 同じ幅を 6 等分
        double even = CELL_W * 2;
        String[] evens = {"low", "even", "red", "black", "odd", "high"};
        for (int i = 0; i < evens.length; i++) {
            out.add(new Cell(evens[i], CELL_W * 1 + even * (i + 0.5), 0.2, even));
        }
        return List.copyOf(out);
    }

    /** 列 (0 = 0 の列、1..12 = 番号、13 = コラム) の中心の x。 */
    static double center(int column) {
        return (column + 0.5) * CELL_W;
    }

    public static final List<Part> PARTS = build();

    private static List<Part> build() {
        List<Part> parts = new ArrayList<>();
        for (Cell cell : CELLS) {
            parts.add(flat(cell.key(), Kind.BUTTON, cell.x(), cell.d() - CELL_TEXT_SHIFT, TABLE_TOP, CELL_TEXT_SCALE,
                    BlackjackLayout.FLAT_PITCH));
            parts.add(flat(padKey(cell.key()), Kind.PAD, cell.x(), cell.d(), 0.5, PAD_WIDTH, 0f));
        }
        parts.add(flat("wheel", Kind.WHEEL, WHEEL_X, WHEEL_D, TABLE_TOP + 0.005, WHEEL_SCALE, BlackjackLayout.FLAT_PITCH));
        parts.add(flat("ball", Kind.BALL, WHEEL_X, WHEEL_D, TABLE_TOP + 0.04, BALL_SCALE, BlackjackLayout.FLAT_PITCH));
        // 掲示板: ホイールの上の空間 (1 段上のブロック) に正面向き
        Part board = flat("board", Kind.TEXT, WHEEL_X, WHEEL_D, 0.0, 0.5f, 0f);
        parts.add(new Part(board.key(), board.kind(), board.column(), board.row(), 1, board.u(), -0.2, board.out(),
                board.scale(), 0f, Side.FRONT));
        return List.copyOf(parts);
    }

    /** 卓上座標 (x, d) の部品。 */
    static Part flat(String key, Kind kind, double x, double d, double v, float scale, float pitch) {
        int column = (int) Math.floor(x) - 1;
        double u = x - Math.floor(x) - 0.5;
        int row = (int) Math.floor(d);
        double out = -(d - Math.floor(d));
        if (x >= WIDTH) {
            column = 2;
            u = 0.5;
        }
        if (d >= DEPTH) {
            row = 2;
            out = -1.0;
        }
        return new Part(key, kind, column, row, 0, u, v, out, scale, pitch, Side.FRONT);
    }

    private RouletteLayout() {
    }

    public static String padKey(String cell) {
        return "pad_" + cell;
    }

    /** 当たり判定の部品名からセルを引く。 */
    public static Optional<String> parsePress(String key) {
        if (key == null || !key.startsWith("pad_")) {
            return Optional.empty();
        }
        String cell = key.substring("pad_".length());
        return RouletteRules.isCell(cell) ? Optional.of(cell) : Optional.empty();
    }

    /** 卓上座標からセルを引く (ブロックの右クリック位置から)。 */
    public static Optional<String> cellAt(double x, double d) {
        return CELLS.stream().filter(cell -> cell.contains(x, d)).map(Cell::key).findFirst();
    }

    public static Optional<Cell> cell(String key) {
        return CELLS.stream().filter(cell -> cell.key().equals(key)).findFirst();
    }
}
