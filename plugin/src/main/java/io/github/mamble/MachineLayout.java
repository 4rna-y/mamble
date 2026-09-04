package io.github.mamble;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * 台の形。向きごとのレバー位置と、正面に並べる部品の座標を決める純粋ロジック。
 *
 * <p>座標は「正面の面の上」で表す。u は正面から見て右を正、v は上を正、out は面から手前への距離。
 * {@code facing} は正面の面が向いている方向 (= プレイヤーが立つ側)。
 *
 * <pre>
 *   3段目 (top)  : 配当表 (シンボル9つのアイコンと ×3 ×4 ×5 の倍率)
 *   2段目 (head) : リール5マス。右の面にレバー
 *   1段目 (base) : START / 操作者名、コインと残高、[−] BET [+]
 * </pre>
 */
public final class MachineLayout {

    /** 表示を面から浮かせる距離。0 だと石と重なってちらつく。 */
    public static final double FACE_OFFSET = 0.03;

    /** 当たり判定の高さ。幅は部品ごと ({@link Part#scale})。 */
    public static final float HIT_HEIGHT = 0.26f;

    /** 配当表に載せる行数。シンボルがこれより少なければ余りは空。 */
    public static final int PAYTABLE_ROWS = 9;

    /** 部品の種類。 */
    public enum Kind {
        /** リールのシンボル (ItemDisplay)。 */
        REEL,
        /** コインのアイコン (ItemDisplay)。 */
        ICON,
        /** 配当表のシンボル (ItemDisplay)。key は {@code pay_icon_<行>}。 */
        PAY_ICON,
        /** 文字 (TextDisplay)。 */
        TEXT,
        /** ボタンの見た目 (背景付き TextDisplay)。 */
        BUTTON,
        /** 右クリックを受ける箱 (Interaction)。scale は幅 */
        HIT,
        /** 卓に平置きするカード (ItemDisplay)。 */
        CARD,
        /** NPC ディーラー (村人)。 */
        DEALER,
        /** 平たい当たり判定 (Interaction、高さ {@link #FLAT_HIT_HEIGHT})。卓の上のセル用。scale は幅 */
        PAD,
        /** ルーレットのホイール (ItemDisplay)。 */
        WHEEL,
        /** ルーレットの玉 (ItemDisplay)。 */
        BALL
    }

    /** 卓の上に敷く当たり判定の高さ。奥のセルを狙う視線を手前の判定が遮らない程度に低くする。 */
    public static final float FLAT_HIT_HEIGHT = 0.05f;

    /**
     * 部品1つ。
     *
     * @param key    台の保存に使う名前
     * @param kind   種類
     * @param column 横のブロック位置 (正面から見て右が正)。1ブロック幅の台では 0
     * @param row    奥行きのブロック位置 (正面から奥が正)。奥行き 1 の台では 0
     * @param row    奥行きのブロック位置 (正面から奥が正)。奥行き 1 の台では 0
     * @param level  0 = 下段 (base)、1 = 中段 (head)、2 = 上段 (top)
     * @param u      面上の横位置 (右が正)
     * @param v      面上の縦位置 (上が正)。テキストと当たり判定は下端、アイコンは中心
     * @param out    面から手前への距離 (負なら奥、つまりブロックの上や中)
     * @param scale  表示の縮尺 (HIT は幅)
     * @param pitch  表示の傾き (度)。0 で正面向き、-90 で上向きに平置き
     * @param side   部品が付く面。u/v/out と yaw はこの面を基準にする
     */
    public record Part(String key, Kind kind, int column, int row, int level, double u, double v, double out,
            float scale, float pitch, Side side) {

        /** 1ブロック幅・正面向きの部品。 */
        public Part(String key, Kind kind, int level, double u, double v, double out, float scale) {
            this(key, kind, 0, 0, level, u, v, out, scale, 0f, Side.FRONT);
        }

        /** 正面の面に付く部品 (横に並ぶ台用)。 */
        public Part(String key, Kind kind, int column, int level, double u, double v, double out, float scale,
                float pitch) {
            this(key, kind, column, 0, level, u, v, out, scale, pitch, Side.FRONT);
        }

        /** 面を選べる部品 (奥行き 1 ブロックの台用)。 */
        public Part(String key, Kind kind, int column, int level, double u, double v, double out, float scale,
                float pitch, Side side) {
            this(key, kind, column, 0, level, u, v, out, scale, pitch, side);
        }
    }

    /** 部品が属するブロック。列は右へ、行は奥へ、段は上へ。 */
    public static BlockPos anchor(BlockPos base, BlockFace facing, Part part) {
        return base.up(part.level())
                .offset(rightOf(facing), part.column())
                .offset(facing.getOppositeFace(), part.row());
    }

    /** 部品が付く面。台の正面から見て左・右の側面にも付けられる。 */
    public enum Side { FRONT, LEFT, RIGHT }

    /** その面が向いている方向。 */
    public static BlockFace facingOf(BlockFace facing, Side side) {
        return switch (side) {
            case FRONT -> facing;
            case LEFT -> rightOf(facing).getOppositeFace();
            case RIGHT -> rightOf(facing);
        };
    }

    /** 配当表の文字の縮尺。1行の高さは 0.25 × scale ブロック。 */
    public static final float PAYTABLE_TEXT_SCALE = 0.36f;

    /** 配当表の1行の高さ。 */
    public static final double PAYTABLE_LINE = 0.25 * PAYTABLE_TEXT_SCALE;

    /** 配当表の文字の下端。見出し + 9 行が上段の面に収まる。 */
    public static final double PAYTABLE_BOTTOM = -0.46;

    public static final List<Part> SLOT_PARTS = buildSlotParts();

    private static List<Part> buildSlotParts() {
        List<Part> parts = new ArrayList<>(List.of(
                // 中段: リール
                new Part("reel1", Kind.REEL, 1, -0.40, 0.0, FACE_OFFSET, 0.18f),
                new Part("reel2", Kind.REEL, 1, -0.20, 0.0, FACE_OFFSET, 0.18f),
                new Part("reel3", Kind.REEL, 1, 0.00, 0.0, FACE_OFFSET, 0.18f),
                new Part("reel4", Kind.REEL, 1, 0.20, 0.0, FACE_OFFSET, 0.18f),
                new Part("reel5", Kind.REEL, 1, 0.40, 0.0, FACE_OFFSET, 0.18f),
                // 下段: START / 操作者名、残高、BET
                new Part("name", Kind.TEXT, 0, 0.0, 0.27, FACE_OFFSET, 0.45f),
                new Part("hit_start", Kind.HIT, 0, 0.0, 0.22, 0.14, 0.6f),
                new Part("coin", Kind.ICON, 0, -0.42, 0.10, FACE_OFFSET, 0.14f),
                new Part("balance", Kind.TEXT, 0, 0.04, 0.04, FACE_OFFSET, 0.5f),
                new Part("bet", Kind.TEXT, 0, 0.0, -0.30, FACE_OFFSET, 0.55f),
                new Part("minus", Kind.BUTTON, 0, -0.34, -0.30, FACE_OFFSET, 0.6f),
                new Part("plus", Kind.BUTTON, 0, 0.34, -0.30, FACE_OFFSET, 0.6f),
                new Part("hit_minus", Kind.HIT, 0, -0.34, -0.36, 0.14, 0.26f),
                new Part("hit_plus", Kind.HIT, 0, 0.34, -0.36, 0.14, 0.26f),
                // 上段: 配当表の倍率 (右寄せの3列)。1行目は見出し
                new Part("pay_col3", Kind.TEXT, 2, -0.10, PAYTABLE_BOTTOM, FACE_OFFSET, PAYTABLE_TEXT_SCALE),
                new Part("pay_col4", Kind.TEXT, 2, 0.12, PAYTABLE_BOTTOM, FACE_OFFSET, PAYTABLE_TEXT_SCALE),
                new Part("pay_col5", Kind.TEXT, 2, 0.34, PAYTABLE_BOTTOM, FACE_OFFSET, PAYTABLE_TEXT_SCALE)));
        // 上段: 配当表のアイコン。見出しの下に1行ずつ
        for (int row = 0; row < PAYTABLE_ROWS; row++) {
            parts.add(new Part("pay_icon_" + (row + 1), Kind.PAY_ICON, 2, -0.42, paytableRowCenter(row + 1),
                    FACE_OFFSET, 0.08f));
        }
        return List.copyOf(parts);
    }

    /** 配当表の {@code line} 行目 (0 = 見出し) の中心の高さ。 */
    public static double paytableRowCenter(int line) {
        double top = PAYTABLE_BOTTOM + PAYTABLE_LINE * (PAYTABLE_ROWS + 1);
        return top - PAYTABLE_LINE * (line + 0.5);
    }

    public static final List<Part> EXCHANGE_PARTS = List.of(
            new Part("label", Kind.TEXT, 0, 0.0, 0.62, -0.5, 0.6f),
            new Part("icon", Kind.ICON, 0, 0.0, 0.0, FACE_OFFSET, 0.6f));

    /** リールの部品名。 */
    public static final List<String> REEL_KEYS = List.of("reel1", "reel2", "reel3", "reel4", "reel5");

    private MachineLayout() {
    }

    /** 正面から見て右の方向。レバーはこちらの面に付く。 */
    public static BlockFace rightOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> throw new IllegalArgumentException("水平4方位のみ: " + facing);
        };
    }

    /** yaw からプレイヤーが見ている水平方向を求める。 */
    public static BlockFace lookDirection(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        if (y >= 315 || y < 45) {
            return BlockFace.SOUTH;
        }
        if (y < 135) {
            return BlockFace.WEST;
        }
        if (y < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    /** 設置したプレイヤーの方を正面にする。 */
    public static BlockFace facingToward(float placerYaw) {
        return lookDirection(placerYaw).getOppositeFace();
    }

    /** 正面を向く表示エンティティの yaw。 */
    public static float yawOf(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> throw new IllegalArgumentException("水平4方位のみ: " + facing);
        };
    }

    /**
     * 部品の位置。ブロックの中心からのずれで返す。
     *
     * @param facing 正面の向き
     * @param u      右方向の距離
     * @param v      上方向の距離
     * @param out    面から手前への距離 (負なら面より奥、つまりブロック側)
     */
    public static Vector offset(BlockFace facing, double u, double v, double out) {
        Vector normal = facing.getDirection();
        Vector right = rightOf(facing).getDirection();
        return normal.multiply(0.5 + out).add(right.multiply(u)).add(new Vector(0, v, 0));
    }

    public static Vector offset(BlockFace facing, Part part) {
        return offset(facing, part.u(), part.v(), part.out());
    }
}
