package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouletteSpinTest {

    @Test
    @DisplayName("どの番号・開始角でも、落ちた後は玉が当たりのポケットの真上に居続ける")
    void ballLandsOnPocket() {
        Random random = new Random(3);
        for (int number = 0; number < 37; number++) {
            for (int trial = 0; trial < 5; trial++) {
                RouletteSpin spin = new RouletteSpin(number, random.nextDouble() * 360, random.nextDouble() * 360, 120);
                for (int t = spin.dropTick(); t <= 120; t++) {
                    assertTrue(spin.ballOverPocket(spin.frame(t)), number + " at " + t);
                }
                assertEquals(RouletteSpin.POCKET_R, spin.rest().radius(), 1e-9);
                assertEquals(RouletteSpin.RIM_R, spin.frame(0).radius(), 1e-9);
            }
        }
    }

    @Test
    @DisplayName("ホイールは 3 周して止まり、玉は逆向きに 5 周以上まわる。1 tick の動きは 180° 未満")
    void motion() {
        RouletteSpin spin = new RouletteSpin(17, 10, 20, 120);
        assertEquals(10 + 3 * 360, spin.rest().wheelDeg(), 1e-9);
        assertEquals(spin.rest().wheelDeg(), spin.frame(500).wheelDeg(), 1e-9, "止まった後は動かない");
        double prevWheel = spin.frame(0).wheelDeg();
        double prevBall = spin.frame(0).ballDeg();
        for (int t = 1; t <= 120; t++) {
            RouletteSpin.Frame frame = spin.frame(t);
            assertTrue(frame.wheelDeg() >= prevWheel - 1e-9, "ホイールは戻らない");
            double wheelStep = frame.wheelDeg() - prevWheel;
            double ballStep = frame.ballDeg() - prevBall;
            assertTrue(wheelStep < 180 && Math.abs(ballStep) < 180, "t=" + t + " の動きが大きすぎる");
            if (t <= spin.dropTick()) {
                assertTrue(ballStep >= -1e-9, "落ちるまでは玉は時計回り");
            }
            prevWheel = frame.wheelDeg();
            prevBall = frame.ballDeg();
        }
        assertTrue(spin.frame(spin.dropTick()).ballDeg() - 20 >= 5 * 360, "5 周以上");
    }

    @Test
    @DisplayName("玉の位置: 角 0 は奥 (z 負)、90 は右 (x 正)")
    void translation() {
        RouletteSpin.Frame far = new RouletteSpin.Frame(0, 0, 0.4);
        assertEquals(0, far.ballX(), 1e-9);
        assertEquals(-0.4, far.ballZ(), 1e-9);
        RouletteSpin.Frame right = new RouletteSpin.Frame(0, 90, 0.4);
        assertEquals(0.4, right.ballX(), 1e-9);
        assertEquals(0, right.ballZ(), 1e-9);
    }

    @Test
    @DisplayName("検証: 短すぎる回転と変な番号は弾く")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new RouletteSpin(1, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RouletteSpin(37, 0, 0, 120));
        assertEquals(0, RouletteSpin.pocketAngle(0), 1e-9);
        assertEquals(360.0 / 37, RouletteSpin.pocketAngle(32), 1e-9);
    }
}
