package io.github.mamble;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** 抽選と判定。サーバー無しで動く純粋ロジック。 */
public final class SlotLogic {

    private final SymbolTable table;
    private final Random random;

    public SlotLogic(SymbolTable table, Random random) {
        this.table = table;
        this.random = random;
    }

    public SymbolTable table() {
        return table;
    }

    /** 5マスを抽選して判定する。 */
    public SpinResult spin(long bet) {
        List<Symbol> reels = new ArrayList<>(SymbolTable.REELS);
        for (int i = 0; i < SymbolTable.REELS; i++) {
            reels.add(table.roll(random));
        }
        return evaluate(reels, bet);
    }

    /** 演出用。回転中に見せるシンボルを1つ引く。 */
    public Symbol randomSymbol() {
        return table.roll(random);
    }

    /** 並んだシンボルから当選を判定する。 */
    public static SpinResult evaluate(List<Symbol> reels, long bet) {
        if (reels.size() != SymbolTable.REELS) {
            throw new IllegalArgumentException("マスは " + SymbolTable.REELS + " 個: " + reels.size());
        }
        if (bet < 1) {
            throw new IllegalArgumentException("bet は 1 以上: " + bet);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Symbol symbol : reels) {
            counts.merge(symbol.id(), 1, Integer::sum);
        }
        Symbol best = null;
        int bestCount = 0;
        for (Symbol symbol : reels) {
            int count = counts.get(symbol.id());
            if (count >= SymbolTable.MIN_MATCH && count > bestCount) {
                best = symbol;
                bestCount = count;
            }
        }
        if (best == null) {
            return new SpinResult(reels, Optional.empty(), 0, 0, bet, 0);
        }
        int multiplier = best.multiplier(bestCount);
        return new SpinResult(reels, Optional.of(best), bestCount, multiplier, bet, bet * multiplier);
    }
}
