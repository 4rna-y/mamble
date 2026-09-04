package io.github.mamble;

/**
 * ホイールと玉の動き。番号と開始角から、tick ごとの角度と玉の半径を返す。
 *
 * <p>角度は度。ホイール角 θ は上から見て反時計回りが正 (モデルの法線まわりの回転)。
 * 玉の角 ψ は「奥」を 0 として上から見て時計回りが正。ホイール上のポケット i はテクスチャで
 * 12 時 (= 奥) から時計回りに {@code i·360/37} にあるので、ホイール角 θ のとき {@code ψ_i = β_i − θ}。
 *
 * <p>ホイールは 3 周、玉は逆向きにリムを 5 周以上まわり、{@link #dropTick()} で当たりの真上に来て
 * 以後はホイールと一緒に回りながら内側へ落ちる。減速は {@code 1 − (1−u)²}。
 *
 * @param number     当たりの番号
 * @param wheelStart 開始時のホイール角
 * @param ballStart  開始時の玉の角
 * @param spinTicks  回転の長さ (tick)
 */
public record RouletteSpin(int number, double wheelStart, double ballStart, int spinTicks) {

    /** 玉がリムを走るときの半径 (ブロック)。ホイールの絵 (scale ≈ 1) の縁の内側。 */
    public static final double RIM_R = 0.44;

    /** 玉が落ち着くポケットの半径。 */
    public static final double POCKET_R = 0.33;

    public static final int WHEEL_TURNS = 3;
    public static final int BALL_TURNS = 5;

    /** 落ちてから半径が縮むまでの tick。 */
    public static final int DROP_TICKS = 10;

    /** テクスチャの並びが上から見て時計回りなら true。鏡像で出たら false にする。 */
    public static final boolean TEXTURE_CLOCKWISE = true;

    /** ホイール角 0 のときの 0 のポケットの位置 (奥を 0)。ずれていたらここで直す。 */
    public static final double ZERO_OFFSET_DEG = 0;

    /** ある時点の姿。 */
    public record Frame(double wheelDeg, double ballDeg, double radius) {

        /** 玉の平行移動 (表示の座標系: x が右、z の負が奥)。 */
        public double ballX() {
            return radius * Math.sin(Math.toRadians(ballDeg));
        }

        public double ballZ() {
            return -radius * Math.cos(Math.toRadians(ballDeg));
        }
    }

    public RouletteSpin {
        if (spinTicks < 20) {
            throw new IllegalArgumentException("spin-ticks は 20 以上: " + spinTicks);
        }
        if (number < 0 || number >= RouletteRules.POCKETS) {
            throw new IllegalArgumentException("番号が変: " + number);
        }
    }

    /** 玉が当たりのポケットの真上に来る tick。 */
    public int dropTick() {
        return (int) Math.round(spinTicks * 0.75);
    }

    /** テクスチャ上のポケットの角 (奥を 0、時計回り)。 */
    public static double pocketAngle(int number) {
        double base = RouletteRules.pocketIndex(number) * 360.0 / RouletteRules.POCKETS;
        return (TEXTURE_CLOCKWISE ? base : -base) + ZERO_OFFSET_DEG;
    }

    static double ease(double u) {
        double clamped = Math.max(0, Math.min(1, u));
        return 1 - (1 - clamped) * (1 - clamped);
    }

    public double wheelAt(int tick) {
        return wheelStart + WHEEL_TURNS * 360.0 * ease(tick / (double) spinTicks);
    }

    /** {@code tick} 時点の姿。{@code spinTicks} 以降は止まったまま。 */
    public Frame frame(int tick) {
        int t = Math.max(0, Math.min(tick, spinTicks));
        int drop = dropTick();
        double wheel = wheelAt(t);
        double target = pocketAngle(number);
        if (t <= drop) {
            double wheelAtDrop = wheelAt(drop);
            double remaining = Math.floorMod(Math.round((target - wheelAtDrop - ballStart) * 1000), 360_000) / 1000.0;
            double sweep = BALL_TURNS * 360.0 + remaining;
            double ball = ballStart + sweep * ease(t / (double) drop);
            return new Frame(wheel, ball, RIM_R);
        }
        // 落ちた後はホイールと一緒に回る。角は落ちた瞬間から連続に (mod 360 では target − wheel と同じ)
        double wheelAtDrop = wheelAt(drop);
        double ballAtDrop = frame(drop).ballDeg();
        double ball = ballAtDrop - (wheel - wheelAtDrop);
        double fall = Math.min(1, (t - drop) / (double) DROP_TICKS);
        return new Frame(wheel, ball, RIM_R + (POCKET_R - RIM_R) * fall);
    }

    /** 止まったときの姿。次のスピンの開始角に使う。 */
    public Frame rest() {
        return frame(spinTicks);
    }

    /** 玉がポケットの真上にいるか (テスト用)。 */
    public boolean ballOverPocket(Frame frame) {
        double diff = Math.floorMod(Math.round((frame.ballDeg() + frame.wheelDeg() - pocketAngle(number)) * 1000),
                360_000) / 1000.0;
        return diff < 0.01 || diff > 359.99;
    }
}
