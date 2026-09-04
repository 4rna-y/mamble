package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.mamble.MachineLayout.Kind;
import io.github.mamble.MachineLayout.Part;

/**
 * ブラックジャック卓の部品の配置。席ごとに列 (column) を変えて、同じ形を3つ並べる。
 *
 * <p>席 0 は左端のブロックの左の側面、席 1 は中央の正面、席 2 は右端の右の側面に付く。
 * 席 i (column i−1) のパネル (縦面):
 * <pre>
 *   ▶ START / 名前          (v 0.34)   ← hit_start
 *   ◎ 残高 1,234            (v 0.12)
 *   [−]  BET 100  [+]       (v −0.14)  ← hit_minus / hit_plus
 *   [HIT] [STAND] [DOUBLE]  (v −0.42)  ← hit_hit / hit_stand / hit_double
 * </pre>
 * 卓の上 (上面に平置き)、席の面から見て手前から順に: HIT / STAND / DOUBLE の帯 (out −0.14)、
 * カード 6 枚ぶんの枠と左端の合計 (out −0.45)。ディーラーは中央の列の奥 (out −0.80) にカード 6 枚と合計、
 * さらに奥のマスに村人。
 */
public final class BlackjackLayout {

    /** 席ごとのカードの枠。 */
    public static final int CARD_SLOTS = BlackjackGame.MAX_CARDS;

    /** 平置きの傾き。 */
    public static final float FLAT_PITCH = -90f;

    /** 卓の上面 (ブロックの中心から +0.5) より少し浮かせる高さ。 */
    public static final double TABLE_TOP = 0.5 + 0.015;

    /** 席が付く面。左の席は左の側面、中央は正面、右の席は右の側面。 */
    public static final MachineLayout.Side[] SEAT_SIDES = {
        MachineLayout.Side.LEFT, MachineLayout.Side.FRONT, MachineLayout.Side.RIGHT,
    };

    public static final float CARD_SCALE = 0.28f;

    /** 1枚目のカードの横位置。合計を左端に置くので右へ寄せる。 */
    public static final double CARD_FIRST_U = -0.15;

    /** 席のカードの、その席の面からの奥行き。手前の帯 (ボタン) と重ならない。 */
    public static final double CARD_OUT = -0.45;

    /** ディーラーのカードの、正面からの奥行き。 */
    public static final double DEALER_CARD_OUT = -0.80;

    /** 合計の横位置 (カードの左)。 */
    public static final double TOTAL_U = -0.40;

    /** HIT / STAND / DOUBLE の横の間隔。DOUBLE (6文字) が隣と重ならない幅。 */
    public static final double BUTTON_SPACING = 0.33;

    /** ボタンの、その席の面からの奥行き。 */
    public static final double BUTTON_OUT = -0.14;

    public static final float BUTTON_SCALE = 0.35f;

    /** ボタンの当たり判定の幅 (奥行きも同じ)。 */
    public static final float BUTTON_HIT_WIDTH = 0.26f;

    /** 枠ごとの横のずらし。重ねて並べる。 */
    public static final double CARD_STEP = 0.10;

    /** 枠ごとの高さのずらし (ちらつき防止)。 */
    public static final double CARD_LIFT = 0.003;

    public static final List<Part> PARTS = build();

    private static List<Part> build() {
        List<Part> parts = new ArrayList<>();
        for (int seat = 0; seat < BlackjackGame.SEATS; seat++) {
            int column = seat - 1;
            MachineLayout.Side side = SEAT_SIDES[seat];
            String p = "s" + seat + "_";
            parts.add(new Part(p + "name", Kind.TEXT, column, 0, 0.0, 0.34, MachineLayout.FACE_OFFSET, 0.40f, 0f, side));
            parts.add(new Part(p + "hit_start", Kind.HIT, column, 0, 0.0, 0.30, 0.14, 0.6f, 0f, side));
            parts.add(new Part(p + "coin", Kind.ICON, column, 0, -0.40, 0.18, MachineLayout.FACE_OFFSET, 0.12f, 0f, side));
            parts.add(new Part(p + "balance", Kind.TEXT, column, 0, 0.04, 0.12, MachineLayout.FACE_OFFSET, 0.40f, 0f, side));
            parts.add(new Part(p + "bet", Kind.TEXT, column, 0, 0.0, -0.14, MachineLayout.FACE_OFFSET, 0.45f, 0f, side));
            parts.add(new Part(p + "minus", Kind.BUTTON, column, 0, -0.34, -0.14, MachineLayout.FACE_OFFSET, 0.5f, 0f, side));
            parts.add(new Part(p + "plus", Kind.BUTTON, column, 0, 0.34, -0.14, MachineLayout.FACE_OFFSET, 0.5f, 0f, side));
            parts.add(new Part(p + "hit_minus", Kind.HIT, column, 0, -0.34, -0.18, 0.14, 0.24f, 0f, side));
            parts.add(new Part(p + "hit_plus", Kind.HIT, column, 0, 0.34, -0.18, 0.14, 0.24f, 0f, side));
            // 卓の上、手前の帯: HIT / STAND / DOUBLE を平置き。当たり判定は上面から突き出す箱
            parts.add(new Part(p + "hit", Kind.BUTTON, column, 0, -BUTTON_SPACING, TABLE_TOP, BUTTON_OUT, BUTTON_SCALE, FLAT_PITCH, side));
            parts.add(new Part(p + "stand", Kind.BUTTON, column, 0, 0.0, TABLE_TOP, BUTTON_OUT, BUTTON_SCALE, FLAT_PITCH, side));
            parts.add(new Part(p + "double", Kind.BUTTON, column, 0, BUTTON_SPACING, TABLE_TOP, BUTTON_OUT, BUTTON_SCALE, FLAT_PITCH, side));
            parts.add(new Part(p + "hit_hit", Kind.HIT, column, 0, -BUTTON_SPACING, 0.5, BUTTON_OUT, BUTTON_HIT_WIDTH, 0f, side));
            parts.add(new Part(p + "hit_stand", Kind.HIT, column, 0, 0.0, 0.5, BUTTON_OUT, BUTTON_HIT_WIDTH, 0f, side));
            parts.add(new Part(p + "hit_double", Kind.HIT, column, 0, BUTTON_SPACING, 0.5, BUTTON_OUT, BUTTON_HIT_WIDTH, 0f, side));
            // その奥にカード。左端に合計
            for (int k = 1; k <= CARD_SLOTS; k++) {
                parts.add(new Part(cardKey(seat, k), Kind.CARD, column, 0,
                        CARD_FIRST_U + CARD_STEP * (k - 1), TABLE_TOP + CARD_LIFT * (k - 1), CARD_OUT, CARD_SCALE, FLAT_PITCH, side));
            }
            parts.add(new Part(p + "total", Kind.TEXT, column, 0, TOTAL_U, TABLE_TOP, CARD_OUT, 0.30f, FLAT_PITCH, side));
        }
        // ディーラー: 中央の列の奥。席1 のカード (out -0.31〜-0.59) と重ならない位置
        for (int k = 1; k <= CARD_SLOTS; k++) {
            parts.add(new Part(dealerCardKey(k), Kind.CARD, 0, 0,
                    CARD_FIRST_U + CARD_STEP * (k - 1), TABLE_TOP + CARD_LIFT * (k - 1), DEALER_CARD_OUT, CARD_SCALE, FLAT_PITCH));
        }
        parts.add(new Part("d_total", Kind.TEXT, 0, 0, TOTAL_U, TABLE_TOP, DEALER_CARD_OUT, 0.30f, FLAT_PITCH));
        // 村人: 奥のマスの床に立つ (卓の中心から見て奥へ 1.5、下へ 0.5)
        parts.add(new Part("dealer", Kind.DEALER, 0, 0, 0.0, -0.5, -1.5, 1f, 0f));
        return List.copyOf(parts);
    }

    private BlackjackLayout() {
    }

    public static String cardKey(int seat, int slot) {
        return "s" + seat + "_card" + slot;
    }

    public static String dealerCardKey(int slot) {
        return "d_card" + slot;
    }

    public static String seatKey(int seat, String name) {
        return "s" + seat + "_" + name;
    }

    /** 当たり判定の部品名から、どの席のどのボタンかを引く。 */
    public enum Press { START, MINUS, PLUS, HIT, STAND, DOUBLE }

    public record Pressed(int seat, Press press) { }

    public static Optional<Pressed> parsePress(String key) {
        if (key == null || !key.startsWith("s") || key.length() < 3 || key.charAt(2) != '_') {
            return Optional.empty();
        }
        int seat = key.charAt(1) - '0';
        if (seat < 0 || seat >= BlackjackGame.SEATS) {
            return Optional.empty();
        }
        String rest = key.substring(3);
        Press press = switch (rest) {
            case "hit_start" -> Press.START;
            case "hit_minus" -> Press.MINUS;
            case "hit_plus" -> Press.PLUS;
            case "hit_hit" -> Press.HIT;
            case "hit_stand" -> Press.STAND;
            case "hit_double" -> Press.DOUBLE;
            default -> null;
        };
        return press == null ? Optional.empty() : Optional.of(new Pressed(seat, press));
    }
}
