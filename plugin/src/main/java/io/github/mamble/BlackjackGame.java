package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;

/**
 * ブラックジャック卓1つの進行。サーバー無しで動く純粋ロジック。
 *
 * <p>時間は tick を外から渡して進める ({@link #tick})。クレジットの出し入れは {@link Wallet} を通し、
 * 起きたことは {@link Event} の一覧で返す。表示や音は呼び出し側 (BlackjackService) が Event を見て行う。
 *
 * <pre>
 *   IDLE ──START──▶ JOINING (受付) ──時間切れ──▶ DEALING ──配り終え──▶ PLAYER_TURN (席順)
 *      ◀──────────── RESULT ◀──精算── DEALER_TURN ◀──全員の手番が終わる──┘
 * </pre>
 *
 * <p>掛け金は配る時点で差し引く。バーストは即負け。ダブルダウンは最初の2枚のときだけで、
 * 追加の掛け金を差し引けたときだけ。ディーラーの伏せ札は全員の手番が終わるまで開かない。
 */
public final class BlackjackGame {

    public static final int SEATS = 3;

    /** 1手に持てる枚数の上限 (表示の枠)。超えた分は最後の枠に重ねる。 */
    public static final int MAX_CARDS = 6;

    public enum State { IDLE, JOINING, DEALING, PLAYER_TURN, DEALER_TURN, RESULT }

    public enum SeatPhase {
        /** 誰もいない。 */
        EMPTY,
        /** 座っているが次のラウンドには入っていない。 */
        SEATED,
        /** 次のラウンドに参加する。 */
        WAITING,
        /** 手番がまだ。 */
        PLAYING,
        STOOD,
        BUST,
        BLACKJACK,
        /** 精算済み。 */
        SETTLED
    }

    public enum Button { HIT, STAND, DOUBLE }

    /** クレジットの出し入れ。共通の {@link io.github.mamble.Wallet} の別名 (テストの互換用)。 */
    public interface Wallet extends io.github.mamble.Wallet {
    }

    /** 進行の時間割 (tick)。 */
    public record Timing(int joinTicks, int turnTicks, int seatTimeoutTicks, int dealIntervalTicks,
            int dealerDrawIntervalTicks, int resultTicks) {

        public Timing {
            if (joinTicks < 1 || turnTicks < 1 || seatTimeoutTicks < 1 || dealIntervalTicks < 1
                    || dealerDrawIntervalTicks < 1 || resultTicks < 1) {
                throw new IllegalArgumentException("blackjack の時間は 1 以上");
            }
        }

        public static Timing parse(ConfigurationSection root) {
            ConfigurationSection section = root.getConfigurationSection("blackjack");
            if (section == null) {
                return defaults();
            }
            return new Timing(
                    section.getInt("join-seconds", 10) * 20,
                    section.getInt("turn-seconds", 30) * 20,
                    section.getInt("seat-timeout-seconds", 60) * 20,
                    section.getInt("deal-interval-ticks", 8),
                    section.getInt("dealer-draw-interval-ticks", 12),
                    section.getInt("result-seconds", 5) * 20);
        }

        public static Timing defaults() {
            return new Timing(10 * 20, 30 * 20, 60 * 20, 8, 12, 5 * 20);
        }
    }

    // ------------------------------------------------------------------ Event

    /** 起きたこと。 */
    public sealed interface Event permits Seated, SeatFreed, Joined, Countdown, RoundStarted, SeatSkipped,
            RoundAborted, CardDealt, TurnStarted, TurnTimeout, Busted, DealerRevealed, DealerDrew, Settled,
            RoundEnded, Denied, BetChanged, Refunded {
    }

    public record Seated(int seat, UUID player) implements Event { }
    /** 席が空いた。{@code gone} は退出済みの人だった場合。 */
    public record SeatFreed(int seat, UUID player, boolean gone) implements Event { }
    public record Joined(int seat) implements Event { }
    public record Countdown(int secondsLeft) implements Event { }
    public record RoundStarted() implements Event { }
    public record SeatSkipped(int seat, UUID player) implements Event { }
    public record RoundAborted() implements Event { }
    /** {@code seat} が -1 ならディーラー。 */
    public record CardDealt(int seat, Card card, boolean faceDown) implements Event { }
    public record TurnStarted(int seat) implements Event { }
    public record TurnTimeout(int seat) implements Event { }
    public record Busted(int seat) implements Event { }
    public record DealerRevealed(Card hole) implements Event { }
    public record DealerDrew(Card card) implements Event { }
    public record Settled(int seat, UUID player, BlackjackRules.Outcome outcome, long wager, long payout,
            boolean doubled) implements Event { }
    public record RoundEnded() implements Event { }
    public record Denied(int seat, UUID player, String reason) implements Event { }
    public record BetChanged(int seat, long bet) implements Event { }
    public record Refunded(int seat, UUID player, long amount) implements Event { }

    // ------------------------------------------------------------------ Seat

    /** 席1つ。 */
    public static final class Seat {
        private UUID player;
        private String name;
        private long bet;
        private long wager;
        private final Hand hand = new Hand();
        private boolean doubled;
        private boolean gone;
        private long lastPress;
        private Button lastButton;
        private SeatPhase phase = SeatPhase.EMPTY;

        public UUID player() {
            return player;
        }

        public String name() {
            return name;
        }

        public long bet() {
            return bet;
        }

        public long wager() {
            return wager;
        }

        public Hand hand() {
            return hand;
        }

        public boolean doubled() {
            return doubled;
        }

        public boolean gone() {
            return gone;
        }

        /** このラウンドで最後に押したボタン。表示の強調に使う。 */
        public Button lastButton() {
            return lastButton;
        }

        public SeatPhase phase() {
            return phase;
        }

        public boolean isEmpty() {
            return phase == SeatPhase.EMPTY;
        }

        /** このラウンドに掛け金を持っているか (まだ精算されていない)。 */
        public boolean hasStake() {
            return phase == SeatPhase.PLAYING || phase == SeatPhase.STOOD || phase == SeatPhase.BLACKJACK;
        }

        /** このラウンドの参加者か (精算済みも含む)。 */
        public boolean inRound() {
            return hasStake() || phase == SeatPhase.BUST || phase == SeatPhase.SETTLED;
        }

        private void free() {
            player = null;
            name = null;
            bet = 0;
            wager = 0;
            hand.clear();
            doubled = false;
            gone = false;
            lastButton = null;
            phase = SeatPhase.EMPTY;
        }
    }

    private record DealStep(int seat, boolean faceDown) { }

    // ------------------------------------------------------------------ 状態

    private final Timing timing;
    private final io.github.mamble.Wallet wallet;
    private final Supplier<Deck> decks;
    private final Seat[] seats = new Seat[SEATS];
    private final Hand dealerHand = new Hand();
    private State state = State.IDLE;
    private Deck deck;
    private Card dealerHole;
    private boolean holeHidden;
    private long now;
    private long timerEnd;
    private int activeSeat = -1;
    private int lastCountdown = -1;
    private final List<DealStep> dealQueue = new ArrayList<>();
    private long nextDealTick;

    public BlackjackGame(Timing timing, io.github.mamble.Wallet wallet, Random random) {
        this(timing, wallet, () -> Deck.shuffled(random));
    }

    /** 山札を差し替えられる (テスト用)。 */
    public BlackjackGame(Timing timing, io.github.mamble.Wallet wallet, Supplier<Deck> decks) {
        this.timing = timing;
        this.wallet = wallet;
        this.decks = decks;
        for (int i = 0; i < SEATS; i++) {
            seats[i] = new Seat();
        }
    }

    // ------------------------------------------------------------------ 参照

    public State state() {
        return state;
    }

    public Seat seat(int index) {
        return seats[index];
    }

    public Hand dealerHand() {
        return dealerHand;
    }

    /** ディーラーの伏せ札がまだ伏せてあるか。 */
    public boolean holeHidden() {
        return holeHidden;
    }

    public Optional<Card> dealerHole() {
        return Optional.ofNullable(dealerHole);
    }

    public int activeSeat() {
        return state == State.PLAYER_TURN ? activeSeat : -1;
    }

    /** ラウンドが動いているか (受付中は含まない)。 */
    public boolean inRound() {
        return state == State.DEALING || state == State.PLAYER_TURN || state == State.DEALER_TURN
                || state == State.RESULT;
    }

    public Optional<Integer> seatOf(UUID player) {
        for (int i = 0; i < SEATS; i++) {
            if (player.equals(seats[i].player)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** 受付・手番・結果表示の残り秒数。その状態でなければ 0。 */
    public int secondsLeft(long at) {
        if (state == State.JOINING || state == State.PLAYER_TURN || state == State.RESULT) {
            return (int) Math.max(0, (timerEnd - at + 19) / 20);
        }
        return 0;
    }

    // ------------------------------------------------------------------ 操作

    /**
     * START。空いている席なら座り、次のラウンドに入る。
     *
     * @param bet その人の今の BET
     */
    public List<Event> pressStart(int index, UUID player, String name, long bet, long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        Seat seat = seats[index];
        if (!seat.isEmpty() && !player.equals(seat.player)) {
            events.add(new Denied(index, player, "その席は使われています"));
            return events;
        }
        Optional<Integer> elsewhere = seatOf(player);
        if (elsewhere.isPresent() && elsewhere.get() != index) {
            events.add(new Denied(index, player, "別の席に座っています"));
            return events;
        }
        if (seat.isEmpty()) {
            seat.player = player;
            seat.name = name;
            seat.bet = bet;
            seat.phase = SeatPhase.SEATED;
            events.add(new Seated(index, player));
        }
        seat.lastPress = at;
        seat.gone = false;
        switch (seat.phase) {
            case SEATED -> {
                seat.bet = bet;
                seat.phase = SeatPhase.WAITING;
                events.add(new Joined(index));
                if (state == State.IDLE) {
                    beginJoining(events);
                }
            }
            case WAITING -> { }
            default -> events.add(new Denied(index, player, "このラウンドが終わるまで待ってください"));
        }
        return events;
    }

    /** BET を変える。ラウンド中の席は変えられない。 */
    public List<Event> setBet(int index, UUID player, long bet, long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        Seat seat = seats[index];
        if (!player.equals(seat.player)) {
            events.add(new Denied(index, player, "あなたの席ではありません"));
            return events;
        }
        if (seat.inRound()) {
            events.add(new Denied(index, player, "このラウンドが終わるまで BET は変えられません"));
            return events;
        }
        seat.lastPress = at;
        seat.bet = bet;
        events.add(new BetChanged(index, bet));
        return events;
    }

    /** ヒット / スタンド / ダブルダウン。手番の人だけ。 */
    public List<Event> press(int index, UUID player, Button button, long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        Seat seat = seats[index];
        if (!player.equals(seat.player)) {
            events.add(new Denied(index, player, "あなたの席ではありません"));
            return events;
        }
        if (state != State.PLAYER_TURN || activeSeat != index) {
            events.add(new Denied(index, player, "あなたの手番ではありません"));
            return events;
        }
        seat.lastPress = at;
        seat.lastButton = button;
        switch (button) {
            case HIT -> {
                dealTo(index, false, events);
                if (seat.hand.isBust()) {
                    bust(index, events);
                    nextTurn(index, events);
                } else if (seat.hand.value() == Hand.TARGET) {
                    seat.phase = SeatPhase.STOOD;
                    nextTurn(index, events);
                } else {
                    timerEnd = at + timing.turnTicks();
                }
            }
            case STAND -> {
                seat.phase = SeatPhase.STOOD;
                nextTurn(index, events);
            }
            case DOUBLE -> {
                if (seat.hand.size() != 2 || seat.doubled) {
                    events.add(new Denied(index, player, "ダブルダウンは最初の2枚のときだけ"));
                    return events;
                }
                if (!wallet.debit(player, seat.bet)) {
                    events.add(new Denied(index, player, "ダブルダウンぶんのクレジットが足りません"));
                    return events;
                }
                seat.wager += seat.bet;
                seat.doubled = true;
                dealTo(index, false, events);
                if (seat.hand.isBust()) {
                    bust(index, events);
                } else {
                    seat.phase = SeatPhase.STOOD;
                }
                nextTurn(index, events);
            }
        }
        return events;
    }

    /**
     * 退出した。手番ならスタンド、まだ配られていなければ席を空ける。
     *
     * @return 掛け金を持ったままなら true (帳簿のキャッシュを保持する必要がある)
     */
    public boolean playerLeft(UUID player, long at, List<Event> events) {
        this.now = at;
        Optional<Integer> index = seatOf(player);
        if (index.isEmpty()) {
            return false;
        }
        Seat seat = seats[index.get()];
        seat.gone = true;
        if (seat.hasStake()) {
            if (state == State.PLAYER_TURN && activeSeat == index.get()) {
                seat.phase = SeatPhase.STOOD;
                nextTurn(index.get(), events);
            }
            return true;
        }
        if (seat.phase == SeatPhase.BUST || seat.phase == SeatPhase.SETTLED) {
            // 精算済み。ラウンドの終わりに席を空ける
            return false;
        }
        freeSeat(index.get(), events);
        return false;
    }

    /** 停止などで中断する。掛け金は返す。 */
    public List<Event> abort() {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < SEATS; i++) {
            Seat seat = seats[i];
            if (seat.hasStake()) {
                wallet.credit(seat.player, seat.wager);
                events.add(new Refunded(i, seat.player, seat.wager));
            }
            if (!seat.isEmpty()) {
                freeSeat(i, events);
            }
        }
        dealerHand.clear();
        dealerHole = null;
        holeHidden = false;
        dealQueue.clear();
        state = State.IDLE;
        events.add(new RoundAborted());
        return events;
    }

    // ------------------------------------------------------------------ 進行

    /** 時間を進める。毎 tick 呼ぶ。 */
    public List<Event> tick(long at) {
        List<Event> events = new ArrayList<>();
        this.now = at;
        expireSeats(events);
        switch (state) {
            case IDLE -> { }
            case JOINING -> {
                int left = secondsLeft(at);
                if (left != lastCountdown) {
                    lastCountdown = left;
                    events.add(new Countdown(left));
                }
                if (at >= timerEnd) {
                    startDealing(events);
                }
            }
            case DEALING -> {
                while (state == State.DEALING && at >= nextDealTick) {
                    if (dealQueue.isEmpty()) {
                        afterDeal(events);
                    } else {
                        DealStep step = dealQueue.remove(0);
                        dealTo(step.seat(), step.faceDown(), events);
                        nextDealTick = at + timing.dealIntervalTicks();
                    }
                }
            }
            case PLAYER_TURN -> {
                if (at >= timerEnd) {
                    int index = activeSeat;
                    events.add(new TurnTimeout(index));
                    seats[index].phase = SeatPhase.STOOD;
                    nextTurn(index, events);
                }
            }
            case DEALER_TURN -> {
                if (at >= nextDealTick) {
                    if (anyoneStanding() && BlackjackRules.dealerShouldHit(dealerHand)) {
                        Card card = deck.draw();
                        dealerHand.add(card);
                        events.add(new DealerDrew(card));
                        nextDealTick = at + timing.dealerDrawIntervalTicks();
                    } else {
                        settle(events);
                    }
                }
            }
            case RESULT -> {
                if (at >= timerEnd) {
                    endRound(events);
                }
            }
        }
        return events;
    }

    private void expireSeats(List<Event> events) {
        for (int i = 0; i < SEATS; i++) {
            Seat seat = seats[i];
            if (seat.phase == SeatPhase.SEATED && now - seat.lastPress > timing.seatTimeoutTicks()) {
                freeSeat(i, events);
            }
        }
    }

    private void beginJoining(List<Event> events) {
        state = State.JOINING;
        timerEnd = now + timing.joinTicks();
        lastCountdown = secondsLeft(now);
        events.add(new Countdown(lastCountdown));
    }

    private void startDealing(List<Event> events) {
        List<Integer> participants = new ArrayList<>();
        for (int i = 0; i < SEATS; i++) {
            Seat seat = seats[i];
            if (seat.phase != SeatPhase.WAITING) {
                continue;
            }
            if (seat.gone || !wallet.debit(seat.player, seat.bet)) {
                events.add(new SeatSkipped(i, seat.player));
                if (seat.gone) {
                    freeSeat(i, events);
                } else {
                    seat.phase = SeatPhase.SEATED;
                    seat.lastPress = now;
                }
                continue;
            }
            seat.wager = seat.bet;
            seat.hand.clear();
            seat.doubled = false;
            seat.lastButton = null;
            seat.phase = SeatPhase.PLAYING;
            participants.add(i);
        }
        if (participants.isEmpty()) {
            state = State.IDLE;
            events.add(new RoundAborted());
            return;
        }
        deck = decks.get();
        dealerHand.clear();
        dealerHole = null;
        holeHidden = false;
        dealQueue.clear();
        for (int round = 0; round < 2; round++) {
            for (int index : participants) {
                dealQueue.add(new DealStep(index, false));
            }
            dealQueue.add(new DealStep(-1, round == 1));
        }
        state = State.DEALING;
        nextDealTick = now;
        events.add(new RoundStarted());
    }

    private void dealTo(int index, boolean faceDown, List<Event> events) {
        Card card = deck.draw();
        if (index < 0) {
            dealerHand.add(card);
            if (faceDown) {
                dealerHole = card;
                holeHidden = true;
            }
        } else {
            seats[index].hand.add(card);
        }
        events.add(new CardDealt(index, card, faceDown));
    }

    private void afterDeal(List<Event> events) {
        for (Seat seat : seats) {
            if (seat.phase == SeatPhase.PLAYING && seat.hand.isBlackjack()) {
                seat.phase = SeatPhase.BLACKJACK;
            }
        }
        nextTurn(-1, events);
    }

    private void nextTurn(int after, List<Event> events) {
        for (int i = after + 1; i < SEATS; i++) {
            if (seats[i].phase == SeatPhase.PLAYING) {
                state = State.PLAYER_TURN;
                activeSeat = i;
                timerEnd = now + timing.turnTicks();
                events.add(new TurnStarted(i));
                return;
            }
        }
        activeSeat = -1;
        state = State.DEALER_TURN;
        holeHidden = false;
        if (dealerHole != null) {
            events.add(new DealerRevealed(dealerHole));
        }
        nextDealTick = now + timing.dealerDrawIntervalTicks();
    }

    private boolean anyoneStanding() {
        for (Seat seat : seats) {
            if (seat.phase == SeatPhase.STOOD) {
                return true;
            }
        }
        return false;
    }

    private void bust(int index, List<Event> events) {
        Seat seat = seats[index];
        seat.phase = SeatPhase.BUST;
        events.add(new Busted(index));
        events.add(new Settled(index, seat.player, BlackjackRules.Outcome.LOSE, seat.wager, 0, seat.doubled));
    }

    private void settle(List<Event> events) {
        for (int i = 0; i < SEATS; i++) {
            Seat seat = seats[i];
            if (seat.phase != SeatPhase.STOOD && seat.phase != SeatPhase.BLACKJACK) {
                continue;
            }
            BlackjackRules.Outcome outcome = BlackjackRules.outcome(seat.hand, dealerHand);
            long payout = BlackjackRules.payout(outcome, seat.wager);
            if (payout > 0) {
                wallet.credit(seat.player, payout);
            }
            seat.phase = SeatPhase.SETTLED;
            events.add(new Settled(i, seat.player, outcome, seat.wager, payout, seat.doubled));
        }
        state = State.RESULT;
        timerEnd = now + timing.resultTicks();
    }

    private void endRound(List<Event> events) {
        events.add(new RoundEnded());
        boolean waiting = false;
        for (int i = 0; i < SEATS; i++) {
            Seat seat = seats[i];
            if (seat.isEmpty()) {
                continue;
            }
            if (seat.gone) {
                freeSeat(i, events);
                continue;
            }
            if (seat.inRound()) {
                seat.phase = SeatPhase.SEATED;
                seat.lastPress = now;
                seat.hand.clear();
                seat.wager = 0;
                seat.doubled = false;
                seat.lastButton = null;
            }
            waiting |= seat.phase == SeatPhase.WAITING;
        }
        dealerHand.clear();
        dealerHole = null;
        holeHidden = false;
        if (waiting) {
            beginJoining(events);
        } else {
            state = State.IDLE;
        }
    }

    private void freeSeat(int index, List<Event> events) {
        Seat seat = seats[index];
        UUID player = seat.player;
        boolean gone = seat.gone;
        seat.free();
        events.add(new SeatFreed(index, player, gone));
    }
}
