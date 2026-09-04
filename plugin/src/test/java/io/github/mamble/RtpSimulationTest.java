package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 既定の表で実際に回して、計算上の還元率と一致することを見る。
 *
 * <p>表や判定を直したときに、期待値の計算と抽選の実装がずれていないかの見張り。
 */
class RtpSimulationTest {

    @Test
    @DisplayName("100万スピンで還元率 94% ± 1%、当選率 19.6% ± 0.5%")
    void simulate() {
        SymbolTable table = SymbolTable.defaults();
        SlotLogic logic = new SlotLogic(table, new Random(42));
        long bet = 10;
        int spins = 1_000_000;
        long wagered = 0;
        long returned = 0;
        int hits = 0;
        for (int i = 0; i < spins; i++) {
            SpinResult result = logic.spin(bet);
            wagered += bet;
            returned += result.payout();
            if (result.won()) {
                hits++;
            }
        }
        double rtp = returned / (double) wagered;
        double hitRate = hits / (double) spins;
        assertEquals(table.expectedReturn(), rtp, 0.01, "還元率");
        assertEquals(table.hitRate(), hitRate, 0.005, "当選率");
    }
}
