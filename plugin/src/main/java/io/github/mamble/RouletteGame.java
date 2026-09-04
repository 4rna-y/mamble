package io.github.mamble;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.bukkit.configuration.ConfigurationSection;

/**
 * ルーレット卓1つの進行。サーバー無しで動く純粋ロジック。
 *
 * <pre>
 *   IDLE ──最初のチップ──▶ BETTING (受付) ──時間切れ──▶ SPINNING ──時間切れ (精算)──▶ RESULT ──時間切れ──▶ IDLE
 *                          └──チップが全部戻る──▶ IDLE
 * </pre>
 *
 * <p>チップは置いた時点で {@link Wallet} から引く。回転中と結果表示中は置けない。番号は回転の
 * 始まりに決め、回転の終わりで精算する (演出は呼び出し側が {@link RouletteSpin} で描く)。
 */
public final class RouletteGame {

    public enum State { IDLE, BETTING, SPINNING, RESULT }

    /** 設定。 */
    public record Settings(int betTicks, int spinTicks, int resultTicks, int maxChips) {

        public Settings {
            if (betTicks < 1 || spinTicks < 20 || resultTicks < 1 || maxChips < 1) {
                throw new IllegalArgumentException("roulette の設定が変 (bet-seconds >= 1, spin-ticks >= 20, "
                        + "result-seconds >= 1, max-chips >= 1)");
            }
        }

        public static Settings parse(ConfigurationSection root) {
            ConfigurationSection section = root.getConfigurationSection("roulette");
            if (section == null) {
                return defaults();
            }
            return new Settings(
                    section.getInt("bet-seconds", 20) * 20,
                    section.getInt("spin-ticks", 120),
                    section.getInt("result-seconds", 5) * 20,
                    section.getInt("max-chips", 20));
        }

        public static Settings defaults() {
            return new Settings(20 * 20, 120, 5 * 20, 20);
        }
    }

    // ------------------------------------------------------------------ Event

    public sealed interface Event permits ChipPlaced, ChipRemoved, Denied, Countdown, BettingClosed, SpinStarted,
            Landed, Settled, RoundEnded, RoundAborted, Refunded {
    }

    /** 置いた。{@code cellTotal} はそのセルの全員ぶん、{@code playerStake} は本人の全セルの合計。 */
    public record ChipPlaced(UUID player, String cell, long value, long cellTotal, int cellChips, int playerChips,
            long playerStake) implements Event { }
    public record ChipRemoved(UUID player, String cell, long value, long cellTotal, int cellChips, int playerChips,
            long playerStake) implements Event { }
    public record Denied(UUID player, String reason) implements Event { }
    public record Countdown(int secondsLeft) implements Event { }
    /** 受付が終わったがチップが無かった。 */
    public record BettingClosed() implements Event { }
    public record SpinStarted(int number) implements Event { }
    public record Landed(int number) implements Event { }
    /** 1人ぶんの精算。{@code returned} は掛け金込みで戻る額。 */
    public record Settled(UUID player, String name, long staked, long returned, List<Win> wins) implements Event { }
    public record Win(String cell, long stake, long payout) { }
    /** ラウンドが終わりチップが消えた。{@code gone} は途中で退出した人。 */
    public record RoundEnded(Set<UUID> gone) implements Event { }
    public record RoundAborted() implements Event { }
    public record Refunded(UUID player, long amount) implements Event { }

    /** 1人ぶんの賭け。 */
    public static final class Bettor {
        private final UUID player;
        private final String name;
        private final Map<String, Deque<Long>> chips = new LinkedHashMap<>();
        private boolean gone;

        Bettor(UUID player, String name) {
            this.player = player;
            this.name = name;
        }

        public UUID player() {
            return player;
        }

        public String name() {
            return name;
        }

        public boolean gone() {
            return gone;
        }

        /** セルごとの掛け金。 */
        public Map<String, Long> stakes() {
            Map<String, Long> out = new LinkedHashMap<>();
            chips.forEach((cell, values) -> out.put(cell, values.stream().mapToLong(Long::longValue).sum()));
            return out;
        }

        public int chipCount() {
            return chips.values().stream().mapToInt(Deque::size).sum();
        }

        public int chipCount(String cell) {
            Deque<Long> values = chips.get(cell);
            return values == null ? 0 : values.size();
        }

        public long stake() {
            return chips.values().stream().flatMap(Deque::stream).mapToLong(Long::longValue).sum();
        }
    }

    // ------------------------------------------------------------------ 状態

    private final Settings settings;
    private final Wallet wallet;
    private final IntSupplier numbers;
    private final Map<UUID, Bettor> bettors = new LinkedHashMap<>();
    private final List<Integer> history = new ArrayList<>();
    private State state = State.IDLE;
    private long now;
    private long timerEnd;
    private long spinStartedAt;
    private int number = -1;
    private int lastCountdown = -1;

    public RouletteGame(Settings settings, Wallet wallet, IntSupplier numbers) {
        this.settings = settings;
        this.wallet = wallet;
        this.numbers = numbers;
    }

    public State state() {
        return state;
    }

    public Settings settings() {
        return settings;
    }

    /** 回転中か結果表示中の番号。それ以外は -1。 */
    public int number() {
        return number;
    }

    /** 直近の結果 (新しい順、最大 10 件)。 */
    public List<Integer> history() {
        return List.copyOf(history);
    }

    public Iterable<Bettor> bettors() {
        return bettors.values();
    }

    public Bettor bettor(UUID player) {
        return bettors.get(player);
    }

    public boolean hasChips() {
        return bettors.values().stream().anyMatch(b -> b.chipCount() > 0);
    }

    public long cellTotal(String cell) {
        return bettors.values().stream().mapToLong(b -> b.stakes().getOrDefault(cell, 0L)).sum();
    }

    public int cellChips(String cell) {
        return bettors.values().stream().mapToInt(b -> b.chipCount(cell)).sum();
    }

    /** 受付・結果表示の残り秒数。 */
    public int secondsLeft(long at) {
        if (state == State.BETTING || state == State.RESULT) {
            return (int) Math.max(0, (timerEnd - at + 19) / 20);
        }
        return 0;
    }

    /** 回転の経過 tick。回転中でなければ -1。 */
    public int spinElapsed(long at) {
        return state == State.SPINNING ? (int) (at - spinStartedAt) : -1;
    }

    // ------------------------------------------------------------------ 操作

    public List<Event> placeChip(UUID player, String name, String cell, long value, long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        if (!RouletteRules.isCell(cell)) {
            events.add(new Denied(player, "そこには賭けられません"));
            return events;
        }
        if (state == State.SPINNING || state == State.RESULT) {
            events.add(new Denied(player, "回転中です。次のスピンまで待ってください"));
            return events;
        }
        Bettor bettor = bettors.computeIfAbsent(player, ignored -> new Bettor(player, name));
        if (bettor.chipCount() >= settings.maxChips()) {
            events.add(new Denied(player, "1スピンに置けるのは " + settings.maxChips() + " 枚まで"));
            return events;
        }
        if (!wallet.debit(player, value)) {
            events.add(new Denied(player, "クレジットが足りません (チップ " + MambleItems.amount(value) + ")"));
            return events;
        }
        bettor.gone = false;
        bettor.chips.computeIfAbsent(cell, ignored -> new ArrayDeque<>()).addLast(value);
        if (state == State.IDLE) {
            state = State.BETTING;
            timerEnd = at + settings.betTicks();
            lastCountdown = secondsLeft(at);
            events.add(new Countdown(lastCountdown));
        }
        events.add(new ChipPlaced(player, cell, value, cellTotal(cell), cellChips(cell), bettor.chipCount(),
                bettor.stake()));
        return events;
    }

    /** 自分のチップを1枚戻す。受付中だけ。 */
    public List<Event> removeChip(UUID player, String cell, long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        if (state != State.BETTING) {
            events.add(new Denied(player, state == State.IDLE ? "チップを置いていません" : "回転中は戻せません"));
            return events;
        }
        Bettor bettor = bettors.get(player);
        Deque<Long> values = bettor == null ? null : bettor.chips.get(cell);
        if (values == null || values.isEmpty()) {
            events.add(new Denied(player, "そこにあなたのチップはありません"));
            return events;
        }
        long value = values.pollLast();
        if (values.isEmpty()) {
            bettor.chips.remove(cell);
        }
        wallet.credit(player, value);
        events.add(new ChipRemoved(player, cell, value, cellTotal(cell), cellChips(cell), bettor.chipCount(),
                bettor.stake()));
        if (bettor.chipCount() == 0) {
            bettors.remove(player);
        }
        if (!hasChips()) {
            state = State.IDLE;
            events.add(new BettingClosed());
        }
        return events;
    }

    /**
     * 退出した。
     *
     * @return チップが精算待ちなら true (帳簿のキャッシュを保持する必要がある)
     */
    public boolean playerLeft(UUID player, long at, List<Event> events) {
        Bettor bettor = bettors.get(player);
        if (bettor == null) {
            return false;
        }
        bettor.gone = true;
        return state == State.BETTING || state == State.SPINNING;
    }

    /** 停止などで中断する。精算前のチップは返す。 */
    public List<Event> abort() {
        List<Event> events = new ArrayList<>();
        if (state == State.BETTING || state == State.SPINNING) {
            for (Bettor bettor : bettors.values()) {
                long stake = bettor.stake();
                if (stake > 0) {
                    wallet.credit(bettor.player, stake);
                    events.add(new Refunded(bettor.player, stake));
                }
            }
        }
        Set<UUID> gone = goneBettors();
        bettors.clear();
        number = -1;
        state = State.IDLE;
        events.add(new RoundAborted());
        events.add(new RoundEnded(gone));
        return events;
    }

    // ------------------------------------------------------------------ 進行

    public List<Event> tick(long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        switch (state) {
            case IDLE -> { }
            case BETTING -> {
                int left = secondsLeft(at);
                if (left != lastCountdown) {
                    lastCountdown = left;
                    events.add(new Countdown(left));
                }
                if (at >= timerEnd) {
                    if (!hasChips()) {
                        state = State.IDLE;
                        events.add(new BettingClosed());
                    } else {
                        number = numbers.getAsInt();
                        state = State.SPINNING;
                        spinStartedAt = at;
                        events.add(new SpinStarted(number));
                    }
                }
            }
            case SPINNING -> {
                if (at - spinStartedAt >= settings.spinTicks()) {
                    settle(events);
                }
            }
            case RESULT -> {
                if (at >= timerEnd) {
                    Set<UUID> gone = goneBettors();
                    bettors.clear();
                    number = -1;
                    state = State.IDLE;
                    events.add(new RoundEnded(gone));
                }
            }
        }
        return events;
    }

    private void settle(List<Event> events) {
        for (Bettor bettor : bettors.values()) {
            long staked = 0;
            long returned = 0;
            List<Win> wins = new ArrayList<>();
            for (Map.Entry<String, Long> entry : bettor.stakes().entrySet()) {
                staked += entry.getValue();
                long payout = RouletteRules.payout(entry.getKey(), entry.getValue(), number);
                if (payout > 0) {
                    returned += payout;
                    wins.add(new Win(entry.getKey(), entry.getValue(), payout));
                }
            }
            if (returned > 0) {
                wallet.credit(bettor.player, returned);
            }
            events.add(new Settled(bettor.player, bettor.name, staked, returned, List.copyOf(wins)));
        }
        history.add(0, number);
        while (history.size() > 10) {
            history.remove(history.size() - 1);
        }
        events.add(new Landed(number));
        state = State.RESULT;
        timerEnd = now + settings.resultTicks();
    }

    private Set<UUID> goneBettors() {
        Set<UUID> gone = new HashSet<>();
        for (Bettor bettor : bettors.values()) {
            if (bettor.gone) {
                gone.add(bettor.player);
            }
        }
        return gone;
    }
}
