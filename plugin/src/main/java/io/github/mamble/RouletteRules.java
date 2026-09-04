package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ヨーロピアンルーレットのルール。0〜36 の 37 ポケット。
 *
 * <p>セルの名前: 単番号 {@code n0}〜{@code n36}、1:1 の {@code red black odd even low high}、
 * 2:1 の {@code d1 d2 d3} (ダース) と {@code c1 c2 c3} (コラム)。
 */
public final class RouletteRules {

    public static final int POCKETS = 37;

    /** ホイール上の並び (時計回り)。 */
    public static final int[] WHEEL = {
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
    };

    private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public enum Color { GREEN, RED, BLACK }

    public static final List<String> OUTSIDE = List.of("low", "even", "red", "black", "odd", "high",
            "d1", "d2", "d3", "c1", "c2", "c3");

    /** 全セル (49)。 */
    public static final List<String> CELLS = cells();

    private static List<String> cells() {
        List<String> out = new ArrayList<>();
        for (int n = 0; n < POCKETS; n++) {
            out.add(numberCell(n));
        }
        out.addAll(OUTSIDE);
        return List.copyOf(out);
    }

    private RouletteRules() {
    }

    public static String numberCell(int number) {
        return "n" + number;
    }

    public static boolean isCell(String cell) {
        return cell != null && CELLS.contains(cell);
    }

    public static Color color(int number) {
        if (number == 0) {
            return Color.GREEN;
        }
        return RED.contains(number) ? Color.RED : Color.BLACK;
    }

    /** ホイール上の位置 (0 = 0 のポケット、時計回り)。 */
    public static int pocketIndex(int number) {
        for (int i = 0; i < WHEEL.length; i++) {
            if (WHEEL[i] == number) {
                return i;
            }
        }
        throw new IllegalArgumentException("番号が変: " + number);
    }

    /** そのセルが番号を含むか。0 は外賭けを全部外す。 */
    public static boolean covers(String cell, int number) {
        if (cell.startsWith("n")) {
            return Integer.parseInt(cell.substring(1)) == number;
        }
        if (number == 0) {
            return false;
        }
        return switch (cell) {
            case "red" -> color(number) == Color.RED;
            case "black" -> color(number) == Color.BLACK;
            case "odd" -> number % 2 == 1;
            case "even" -> number % 2 == 0;
            case "low" -> number <= 18;
            case "high" -> number >= 19;
            case "d1" -> number <= 12;
            case "d2" -> number >= 13 && number <= 24;
            case "d3" -> number >= 25;
            case "c1" -> number % 3 == 1;
            case "c2" -> number % 3 == 2;
            case "c3" -> number % 3 == 0;
            default -> throw new IllegalArgumentException("知らないセル: " + cell);
        };
    }

    /** 配当の倍率 (掛け金は別に戻る)。単番号 35、ダース・コラム 2、1:1 は 1。 */
    public static int multiplier(String cell) {
        if (cell.startsWith("n")) {
            return 35;
        }
        return cell.startsWith("d") || cell.startsWith("c") ? 2 : 1;
    }

    /** 戻ってくる額 (掛け金込み)。外れは 0。 */
    public static long payout(String cell, long stake, int number) {
        return covers(cell, number) ? stake * (multiplier(cell) + 1) : 0;
    }

    /** 演出の派手さ。単番号 3、ダース・コラム 2、1:1 は 1。 */
    public static int tier(String cell) {
        return switch (multiplier(cell)) {
            case 35 -> 3;
            case 2 -> 2;
            default -> 1;
        };
    }

    /** セルの表示名。 */
    public static String label(String cell) {
        if (cell.startsWith("n")) {
            return cell.substring(1);
        }
        return switch (cell) {
            case "red" -> "赤";
            case "black" -> "黒";
            case "odd" -> "奇数";
            case "even" -> "偶数";
            case "low" -> "1-18";
            case "high" -> "19-36";
            case "d1" -> "1st 12";
            case "d2" -> "2nd 12";
            case "d3" -> "3rd 12";
            case "c1", "c2", "c3" -> "2:1";
            default -> cell;
        };
    }
}
