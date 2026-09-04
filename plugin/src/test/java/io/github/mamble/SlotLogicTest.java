package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotLogicTest {

    private final SymbolTable table = SymbolTable.defaults();

    private Symbol s(String id) {
        return table.byId(id).orElseThrow();
    }

    @Test
    @DisplayName("3個揃い: どこにあっても数える")
    void threeAnywhere() {
        SpinResult result = SlotLogic.evaluate(
                List.of(s("cherry"), s("lemon"), s("cherry"), s("bar"), s("cherry")), 10);
        assertTrue(result.won());
        assertEquals("cherry", result.winning().orElseThrow().id());
        assertEquals(3, result.count());
        assertEquals(2, result.multiplier());
        assertEquals(20, result.payout());
        assertEquals(10, result.net());
        assertEquals(List.of(0, 2, 4), result.winningIndices());
    }

    @Test
    @DisplayName("4個・5個揃い")
    void fourAndFive() {
        SpinResult four = SlotLogic.evaluate(
                List.of(s("seven"), s("seven"), s("cherry"), s("seven"), s("seven")), 100);
        assertEquals(4, four.count());
        assertEquals(80, four.multiplier());
        assertEquals(8000, four.payout());

        SpinResult five = SlotLogic.evaluate(
                List.of(s("seven"), s("seven"), s("seven"), s("seven"), s("seven")), 1000);
        assertEquals(5, five.count());
        assertEquals(1000, five.multiplier());
        assertEquals(1_000_000, five.payout());
    }

    @Test
    @DisplayName("2個ずつでは当たらない")
    void pairsLose() {
        SpinResult result = SlotLogic.evaluate(
                List.of(s("cherry"), s("cherry"), s("lemon"), s("lemon"), s("bar")), 10);
        assertFalse(result.won());
        assertEquals(0, result.payout());
        assertEquals(-10, result.net());
        assertTrue(result.winningIndices().isEmpty());
    }

    @Test
    @DisplayName("3個と2個なら3個の方が当たる")
    void threeBeatsPair() {
        SpinResult result = SlotLogic.evaluate(
                List.of(s("cherry"), s("lemon"), s("cherry"), s("lemon"), s("lemon")), 10);
        assertEquals("lemon", result.winning().orElseThrow().id());
        assertEquals(3, result.count());
    }

    @Test
    @DisplayName("マス数と bet の検証")
    void validation() {
        assertThrows(IllegalArgumentException.class,
                () -> SlotLogic.evaluate(List.of(s("cherry"), s("cherry"), s("cherry")), 10));
        assertThrows(IllegalArgumentException.class, () -> SlotLogic.evaluate(
                List.of(s("cherry"), s("cherry"), s("cherry"), s("cherry"), s("cherry")), 0));
    }

    @Test
    @DisplayName("種を固定すれば同じ結果になる")
    void deterministicWithSeed() {
        SpinResult a = new SlotLogic(table, new Random(123)).spin(10);
        SpinResult b = new SlotLogic(table, new Random(123)).spin(10);
        assertEquals(a, b);
        assertEquals(5, a.reels().size());
    }
}
