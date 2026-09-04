package io.github.mamble;

import java.util.List;

/**
 * リールに出るシンボル1種。
 *
 * @param id          設定と pack で使う識別子 (例 {@code cherry})
 * @param displayName 表示名
 * @param weight      1リールでの出現率 (%)。表全体で 100 になる
 * @param pays        3個・4個・5個揃いの倍率
 */
public record Symbol(String id, String displayName, int weight, List<Integer> pays) {

    public Symbol {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("シンボルの id が空");
        }
        if (weight < 1) {
            throw new IllegalArgumentException(id + " の weight が 1 未満: " + weight);
        }
        if (pays == null || pays.size() != 3) {
            throw new IllegalArgumentException(id + " の pays は 3個・4個・5個の3つ: " + pays);
        }
        for (int i = 0; i < pays.size(); i++) {
            if (pays.get(i) < 0) {
                throw new IllegalArgumentException(id + " の pays が負: " + pays);
            }
            if (i > 0 && pays.get(i) < pays.get(i - 1)) {
                throw new IllegalArgumentException(id + " の pays は多く揃うほど高くする: " + pays);
            }
        }
        pays = List.copyOf(pays);
    }

    /** {@code count} 個揃ったときの倍率。3個未満は 0。 */
    public int multiplier(int count) {
        if (count < SymbolTable.MIN_MATCH) {
            return 0;
        }
        return pays.get(Math.min(count, SymbolTable.REELS) - SymbolTable.MIN_MATCH);
    }
}
