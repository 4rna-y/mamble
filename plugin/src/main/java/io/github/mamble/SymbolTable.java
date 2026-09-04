package io.github.mamble;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

/**
 * シンボル表。出現率と倍率を持ち、抽選と期待値の計算を担う。
 *
 * <p>抽選は5マス独立で、同じシンボルが5マス中どこかに3個以上あれば当選。5マスなので
 * 2種類が同時に3個以上になることは無い。
 */
public final class SymbolTable {

    /** リールの数 (= マスの数)。 */
    public static final int REELS = 5;

    /** 当選に要る最少個数。 */
    public static final int MIN_MATCH = 3;

    /** weight の合計。% で書かせるので 100。 */
    public static final int TOTAL_WEIGHT = 100;

    private final List<Symbol> symbols;

    public SymbolTable(List<Symbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols が空");
        }
        Set<String> ids = new HashSet<>();
        int total = 0;
        for (Symbol symbol : symbols) {
            if (!ids.add(symbol.id())) {
                throw new IllegalArgumentException("シンボル " + symbol.id() + " が重複");
            }
            total += symbol.weight();
        }
        if (total != TOTAL_WEIGHT) {
            throw new IllegalArgumentException("symbols の weight の合計が " + TOTAL_WEIGHT
                    + " ではない: " + total);
        }
        this.symbols = List.copyOf(symbols);
    }

    public List<Symbol> all() {
        return symbols;
    }

    public Optional<Symbol> byId(String id) {
        return symbols.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** 1マスぶんの抽選。 */
    public Symbol roll(Random random) {
        int pick = random.nextInt(TOTAL_WEIGHT);
        for (Symbol symbol : symbols) {
            pick -= symbol.weight();
            if (pick < 0) {
                return symbol;
            }
        }
        throw new IllegalStateException("抽選が壊れている");
    }

    /** 1マスにそのシンボルが出る確率。 */
    public double probability(Symbol symbol) {
        return symbol.weight() / (double) TOTAL_WEIGHT;
    }

    /**
     * 還元率 (RTP)。bet 1 あたりの配当の期待値。
     *
     * <p>5マス中ちょうど k 個が出る確率は二項分布 C(5,k) p^k (1-p)^(5-k)。
     */
    public double expectedReturn() {
        double sum = 0;
        for (Symbol symbol : symbols) {
            double p = probability(symbol);
            for (int k = MIN_MATCH; k <= REELS; k++) {
                sum += exactly(p, k) * symbol.multiplier(k);
            }
        }
        return sum;
    }

    /** 1スピンで何かに当たる確率。 */
    public double hitRate() {
        double sum = 0;
        for (Symbol symbol : symbols) {
            double p = probability(symbol);
            for (int k = MIN_MATCH; k <= REELS; k++) {
                sum += exactly(p, k);
            }
        }
        return sum;
    }

    /** 5マス中ちょうど k 個が確率 p のシンボルになる確率。 */
    static double exactly(double p, int k) {
        return choose(REELS, k) * Math.pow(p, k) * Math.pow(1 - p, REELS - k);
    }

    private static long choose(int n, int k) {
        long result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return result;
    }

    /** {@code symbols} セクションを読む。不備は起動時に気付きたいので例外にする。 */
    public static SymbolTable parse(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("symbols");
        if (section == null) {
            throw new IllegalArgumentException("config.yml に symbols が無い");
        }
        List<Symbol> symbols = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                throw new IllegalArgumentException("symbols." + id + " の形が違う");
            }
            List<Integer> pays = entry.getIntegerList("pays");
            symbols.add(new Symbol(id, entry.getString("name", id), entry.getInt("weight", 0), pays));
        }
        return new SymbolTable(symbols);
    }

    /** 同梱の config.yml と同じ既定の表。config を読めないときの比較用。 */
    public static SymbolTable defaults() {
        return new SymbolTable(List.of(
                new Symbol("cherry", "チェリー", 24, List.of(2, 6, 20)),
                new Symbol("lemon", "レモン", 18, List.of(4, 12, 40)),
                new Symbol("orange", "オレンジ", 14, List.of(6, 16, 60)),
                new Symbol("plum", "プラム", 14, List.of(6, 16, 60)),
                new Symbol("grape", "ブドウ", 10, List.of(8, 20, 80)),
                new Symbol("melon", "スイカ", 8, List.of(12, 40, 200)),
                new Symbol("bell", "ベル", 5, List.of(16, 50, 300)),
                new Symbol("bar", "BAR", 4, List.of(18, 60, 500)),
                new Symbol("seven", "7", 3, List.of(20, 80, 1000))));
    }
}
