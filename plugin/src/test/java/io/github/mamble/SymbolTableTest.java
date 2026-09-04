package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SymbolTableTest {

    private final SymbolTable table = SymbolTable.defaults();

    @Test
    @DisplayName("既定の表は還元率 94.1%、当選率 19.6%")
    void expectedReturnOfDefaults() {
        assertEquals(0.9414, table.expectedReturn(), 0.0005);
        assertEquals(0.1961, table.hitRate(), 0.0005);
    }

    @Test
    @DisplayName("5マス中ちょうど k 個の確率は二項分布")
    void exactlyIsBinomial() {
        // p=0.24: C(5,3) 0.24^3 0.76^2
        assertEquals(10 * Math.pow(0.24, 3) * Math.pow(0.76, 2), SymbolTable.exactly(0.24, 3), 1e-12);
        assertEquals(Math.pow(0.24, 5), SymbolTable.exactly(0.24, 5), 1e-12);
    }

    @Test
    @DisplayName("抽選の偏りは weight に従う")
    void rollFollowsWeights() {
        Random random = new Random(7);
        Map<String, Integer> counts = new HashMap<>();
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            counts.merge(table.roll(random).id(), 1, Integer::sum);
        }
        for (Symbol symbol : table.all()) {
            double observed = counts.getOrDefault(symbol.id(), 0) / (double) n;
            assertEquals(table.probability(symbol), observed, 0.005, symbol.id());
        }
    }

    @Test
    @DisplayName("weight の合計が 100 でなければ弾く")
    void rejectsWrongTotal() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolTable(List.of(
                new Symbol("a", "A", 50, List.of(1, 2, 3)),
                new Symbol("b", "B", 40, List.of(1, 2, 3)))));
    }

    @Test
    @DisplayName("id の重複、負の倍率、下がる倍率、pays の個数違いを弾く")
    void rejectsBrokenSymbols() {
        assertThrows(IllegalArgumentException.class, () -> new SymbolTable(List.of(
                new Symbol("a", "A", 50, List.of(1, 2, 3)),
                new Symbol("a", "A", 50, List.of(1, 2, 3)))));
        assertThrows(IllegalArgumentException.class, () -> new Symbol("a", "A", 1, List.of(-1, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> new Symbol("a", "A", 1, List.of(3, 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Symbol("a", "A", 1, List.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> new Symbol("a", "A", 0, List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("倍率は個数で引く。3個未満は 0、5個を超えても 5個の倍率")
    void multiplierByCount() {
        Symbol seven = table.byId("seven").orElseThrow();
        assertEquals(0, seven.multiplier(2));
        assertEquals(20, seven.multiplier(3));
        assertEquals(80, seven.multiplier(4));
        assertEquals(1000, seven.multiplier(5));
        assertEquals(1000, seven.multiplier(6));
    }

    @Test
    @DisplayName("序列: チェリー < レモン < オレンジ = プラム < ブドウ < スイカ < ベル < BAR < 7")
    void ordering() {
        List<Symbol> all = table.all();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).weight() <= all.get(i - 1).weight(), all.get(i).id() + " の出現率");
            assertTrue(all.get(i).multiplier(5) >= all.get(i - 1).multiplier(5), all.get(i).id() + " の倍率");
        }
    }
}
