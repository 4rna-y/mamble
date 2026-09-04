package io.github.mamble;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 1スピンに賭けられる額の範囲。{@code min} から {@code max} まで {@code step} 刻み。
 *
 * @param min        最小の額
 * @param max        最大の額
 * @param step       台の [-] [+] で動く幅
 * @param defaultBet 初めて遊ぶ人の額。刻みに乗っていること
 */
public record BetSteps(long min, long max, long step, long defaultBet) {

    /** スニークしながらボタンを押したときに動く段数。 */
    public static final int SNEAK_MULTIPLIER = 10;

    public BetSteps {
        if (min < 1) {
            throw new IllegalArgumentException("bet.min は 1 以上: " + min);
        }
        if (step < 1) {
            throw new IllegalArgumentException("bet.step は 1 以上: " + step);
        }
        if (max < min) {
            throw new IllegalArgumentException("bet.max は bet.min 以上: " + max + " < " + min);
        }
        if ((max - min) % step != 0) {
            throw new IllegalArgumentException("bet.max " + max + " が bet.min " + min + " から " + step
                    + " 刻みで届く額ではない");
        }
        if (!onGrid(min, max, step, defaultBet)) {
            throw new IllegalArgumentException("bet.default " + defaultBet + " が " + min + "〜" + max + " の "
                    + step + " 刻みに無い");
        }
    }

    private static boolean onGrid(long min, long max, long step, long bet) {
        return bet >= min && bet <= max && (bet - min) % step == 0;
    }

    /** 選べる額か。 */
    public boolean allows(long bet) {
        return onGrid(min, max, step, bet);
    }

    /** {@code count} 段上げる。上限で止まる。刻みに無い値なら既定から数える。 */
    public long up(long current, int count) {
        long base = allows(current) ? current : defaultBet;
        return Math.min(max, base + step * Math.max(1, count));
    }

    /** {@code count} 段下げる。下限で止まる。刻みに無い値なら既定から数える。 */
    public long down(long current, int count) {
        long base = allows(current) ? current : defaultBet;
        return Math.max(min, base - step * Math.max(1, count));
    }

    public long up(long current) {
        return up(current, 1);
    }

    public long down(long current) {
        return down(current, 1);
    }

    /** 保存されていた値を刻みに揃える。config を変えて外れた値は既定に戻す。 */
    public long normalize(long stored) {
        return allows(stored) ? stored : defaultBet;
    }

    /** 説明用: 「10〜1000 (10 刻み)」。 */
    public String describe() {
        return MambleItems.amount(min) + "〜" + MambleItems.amount(max) + " (" + MambleItems.amount(step) + " 刻み)";
    }

    public static BetSteps parse(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("bet");
        if (section == null) {
            throw new IllegalArgumentException("config.yml に bet が無い");
        }
        if (!section.contains("step")) {
            // 旧形式 (bet.steps の一覧) や書きかけの config。既定に倒して起動は止めない
            return defaults();
        }
        return new BetSteps(section.getLong("min", 0), section.getLong("max", 0),
                section.getLong("step", 0), section.getLong("default", 0));
    }

    public static BetSteps defaults() {
        return new BetSteps(10L, 1000L, 10L, 10L);
    }
}
