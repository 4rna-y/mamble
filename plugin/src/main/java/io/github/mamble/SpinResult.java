package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 1スピンの結果。レバーを引いた瞬間に確定し、演出はこれを見せるだけ。
 *
 * @param reels      5マスのシンボル (左から)
 * @param winning    当選したシンボル。外れなら空
 * @param count      当選シンボルの個数 (外れなら 0)
 * @param multiplier 倍率 (外れなら 0)
 * @param bet        賭けた額
 * @param payout     配当 (bet × 倍率。外れなら 0)
 */
public record SpinResult(List<Symbol> reels, Optional<Symbol> winning, int count, int multiplier,
        long bet, long payout) {

    public SpinResult {
        reels = List.copyOf(reels);
    }

    public boolean won() {
        return winning.isPresent();
    }

    /** 当選シンボルが出ているマスの添字 (0 始まり)。外れなら空。 */
    public List<Integer> winningIndices() {
        List<Integer> out = new ArrayList<>();
        if (winning.isEmpty()) {
            return out;
        }
        for (int i = 0; i < reels.size(); i++) {
            if (reels.get(i).id().equals(winning.get().id())) {
                out.add(i);
            }
        }
        return out;
    }

    /** 残高の増減 (配当 − bet)。 */
    public long net() {
        return payout - bet;
    }
}
