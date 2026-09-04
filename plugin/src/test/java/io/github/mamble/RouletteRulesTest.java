package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouletteRulesTest {

    @Test
    @DisplayName("ホイールは 37 ポケットで重複なし。0 の隣は 32 と 26")
    void wheel() {
        assertEquals(37, RouletteRules.WHEEL.length);
        Set<Integer> seen = new HashSet<>();
        for (int n : RouletteRules.WHEEL) {
            assertTrue(seen.add(n), "重複: " + n);
        }
        assertEquals(0, RouletteRules.pocketIndex(0));
        assertEquals(1, RouletteRules.pocketIndex(32));
        assertEquals(36, RouletteRules.pocketIndex(26));
    }

    @Test
    @DisplayName("赤 18・黒 18・緑 1")
    void colors() {
        int red = 0;
        int black = 0;
        for (int n = 0; n <= 36; n++) {
            switch (RouletteRules.color(n)) {
                case RED -> red++;
                case BLACK -> black++;
                case GREEN -> assertEquals(0, n);
            }
        }
        assertEquals(18, red);
        assertEquals(18, black);
        assertEquals(RouletteRules.Color.RED, RouletteRules.color(1));
        assertEquals(RouletteRules.Color.BLACK, RouletteRules.color(2));
        assertEquals(RouletteRules.Color.BLACK, RouletteRules.color(10));
        assertEquals(RouletteRules.Color.RED, RouletteRules.color(19));
    }

    @Test
    @DisplayName("セルは 49 個。各セルが含む番号の数は 1 / 12 / 18 で、0 は外賭けに含まれない")
    void coverage() {
        assertEquals(49, RouletteRules.CELLS.size());
        for (String cell : RouletteRules.CELLS) {
            int count = 0;
            for (int n = 0; n <= 36; n++) {
                if (RouletteRules.covers(cell, n)) {
                    count++;
                }
            }
            int expected = switch (RouletteRules.multiplier(cell)) {
                case 35 -> 1;
                case 2 -> 12;
                default -> 18;
            };
            assertEquals(expected, count, cell);
            if (!cell.startsWith("n")) {
                assertFalse(RouletteRules.covers(cell, 0), cell + " が 0 を含む");
            }
        }
        assertTrue(RouletteRules.covers("c1", 1));
        assertTrue(RouletteRules.covers("c3", 36));
        assertTrue(RouletteRules.covers("d2", 13));
        assertFalse(RouletteRules.covers("d2", 25));
        assertTrue(RouletteRules.isCell("n36"));
        assertFalse(RouletteRules.isCell("n37"));
        assertFalse(RouletteRules.isCell("split"));
    }

    @Test
    @DisplayName("配当: 単番号 36 倍、ダース・コラム 3 倍、1:1 は 2 倍が戻る。期待値はどれも 36/37")
    void payouts() {
        assertEquals(360, RouletteRules.payout("n17", 10, 17));
        assertEquals(0, RouletteRules.payout("n17", 10, 18));
        assertEquals(30, RouletteRules.payout("d1", 10, 5));
        assertEquals(20, RouletteRules.payout("red", 10, 1));
        assertEquals(0, RouletteRules.payout("red", 10, 0));
        for (String cell : RouletteRules.CELLS) {
            long total = 0;
            for (int n = 0; n <= 36; n++) {
                total += RouletteRules.payout(cell, 37, n);
            }
            assertEquals(36 * 37, total, cell + " の期待値");
        }
        assertEquals(3, RouletteRules.tier("n0"));
        assertEquals(2, RouletteRules.tier("c2"));
        assertEquals(1, RouletteRules.tier("odd"));
        assertEquals("赤", RouletteRules.label("red"));
        assertEquals("36", RouletteRules.label("n36"));
    }
}
