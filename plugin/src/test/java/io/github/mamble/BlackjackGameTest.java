package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.mamble.BlackjackGame.Button;
import io.github.mamble.BlackjackGame.Event;
import io.github.mamble.BlackjackGame.SeatPhase;
import io.github.mamble.BlackjackGame.State;
import io.github.mamble.BlackjackRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 卓の進行。偽の財布と積んだ山札で、tick を手で進める。
 *
 * <p>配る順は「席…、ディーラー表、席…、ディーラー伏せ」。1人なら山札の上から
 * [自分1, ディーラー表, 自分2, ディーラー伏せ, 以後の引き札…]。
 */
class BlackjackGameTest {

    /** 受付 40 tick、手番 100 tick、席の時間切れ 200 tick、配りとディーラーの引きは 1 tick、結果 20 tick。 */
    static final BlackjackGame.Timing TIMING = new BlackjackGame.Timing(40, 100, 200, 1, 1, 20);

    static final class FakeWallet implements BlackjackGame.Wallet {
        final Map<UUID, Long> balances = new HashMap<>();

        FakeWallet give(UUID player, long amount) {
            balances.merge(player, amount, Long::sum);
            return this;
        }

        @Override
        public boolean debit(UUID player, long amount) {
            long balance = balances.getOrDefault(player, 0L);
            if (balance < amount) {
                return false;
            }
            balances.put(player, balance - amount);
            return true;
        }

        @Override
        public void credit(UUID player, long amount) {
            balances.merge(player, amount, Long::sum);
        }

        long of(UUID player) {
            return balances.getOrDefault(player, 0L);
        }
    }

    final FakeWallet wallet = new FakeWallet();
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID carol = UUID.randomUUID();
    final List<Event> log = new ArrayList<>();
    long now = 0;

    static Card c(Rank rank) {
        return new Card(rank, Suit.CLUBS);
    }

    BlackjackGame game(Card... stacked) {
        return new BlackjackGame(TIMING, wallet, () -> Deck.of(List.of(stacked)));
    }

    /** {@code ticks} 進める。 */
    void run(BlackjackGame game, int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            log.addAll(game.tick(now));
        }
    }

    long count(Class<? extends Event> type) {
        return log.stream().filter(type::isInstance).count();
    }

    <T extends Event> List<T> of(Class<T> type) {
        return log.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** alice が席 1 に座って受付を終え、配り終えて手番になるまで。 */
    BlackjackGame dealToAlice(long bet, Card... stacked) {
        wallet.give(alice, 100);
        BlackjackGame game = game(stacked);
        log.addAll(game.pressStart(1, alice, "alice", bet, now));
        run(game, TIMING.joinTicks() + 10);
        return game;
    }

    @Test
    @DisplayName("START で座って受付が始まり、時間切れで配られ、手番になる")
    void startJoinDeal() {
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN), c(Rank.FIVE));
        assertEquals(1, count(BlackjackGame.Seated.class));
        assertEquals(1, count(BlackjackGame.Joined.class));
        assertTrue(count(BlackjackGame.Countdown.class) >= 2, "秒読みが出る");
        assertEquals(1, count(BlackjackGame.RoundStarted.class));
        List<BlackjackGame.CardDealt> dealt = of(BlackjackGame.CardDealt.class);
        assertEquals(4, dealt.size());
        assertEquals(List.of(1, -1, 1, -1), dealt.stream().map(BlackjackGame.CardDealt::seat).toList());
        assertEquals(List.of(false, false, false, true), dealt.stream().map(BlackjackGame.CardDealt::faceDown).toList());
        assertEquals(State.PLAYER_TURN, game.state());
        assertEquals(1, game.activeSeat());
        assertEquals(17, game.seat(1).hand().value());
        assertTrue(game.holeHidden());
        assertEquals(90, wallet.of(alice), "配った時点で掛け金を引く");
        assertEquals(10, game.seat(1).wager());
    }

    @Test
    @DisplayName("スタンド → ディーラーが 17 まで引く → 精算 → 結果表示のあと待機に戻る")
    void standAndDealerPlays() {
        // 自分 9+8=17、ディーラー 7 + 伏せ 5 = 12 → 引いて 10 で 22 バースト → 勝ち
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.FIVE), c(Rank.TEN));
        log.addAll(game.press(1, alice, Button.STAND, now));
        assertEquals(State.DEALER_TURN, game.state());
        assertFalse(game.holeHidden());
        assertEquals(1, count(BlackjackGame.DealerRevealed.class));
        run(game, 3);
        assertEquals(1, count(BlackjackGame.DealerDrew.class));
        assertEquals(State.RESULT, game.state());
        BlackjackGame.Settled settled = of(BlackjackGame.Settled.class).get(0);
        assertEquals(Outcome.WIN, settled.outcome());
        assertEquals(20, settled.payout());
        assertEquals(110, wallet.of(alice));
        assertEquals(SeatPhase.SETTLED, game.seat(1).phase());
        run(game, TIMING.resultTicks() + 1);
        assertEquals(1, count(BlackjackGame.RoundEnded.class));
        assertEquals(State.IDLE, game.state());
        assertEquals(SeatPhase.SEATED, game.seat(1).phase(), "座ったまま。次は START で参加");
        assertEquals(0, game.dealerHand().size());
    }

    @Test
    @DisplayName("ヒットでバーストすると即負け。全員バーストならディーラーは引かない")
    void hitToBust() {
        // 自分 10+6=16、ディーラー 7/5。ヒットで 10 → 26
        BlackjackGame game = dealToAlice(10, c(Rank.TEN), c(Rank.SEVEN), c(Rank.SIX), c(Rank.FIVE), c(Rank.TEN), c(Rank.TEN));
        log.addAll(game.press(1, alice, Button.HIT, now));
        assertEquals(1, count(BlackjackGame.Busted.class));
        BlackjackGame.Settled settled = of(BlackjackGame.Settled.class).get(0);
        assertEquals(Outcome.LOSE, settled.outcome());
        assertEquals(0, settled.payout());
        assertEquals(SeatPhase.BUST, game.seat(1).phase());
        assertEquals(State.DEALER_TURN, game.state());
        run(game, 3);
        assertEquals(0, count(BlackjackGame.DealerDrew.class), "全員バーストなら引かない");
        assertEquals(State.RESULT, game.state());
        assertEquals(1, count(BlackjackGame.Settled.class), "バーストの精算は1回だけ");
        assertEquals(90, wallet.of(alice));
    }

    @Test
    @DisplayName("ヒットで 21 になったら自動でスタンド")
    void hitToTwentyOne() {
        BlackjackGame game = dealToAlice(10, c(Rank.TEN), c(Rank.SEVEN), c(Rank.SIX), c(Rank.TEN), c(Rank.FIVE));
        log.addAll(game.press(1, alice, Button.HIT, now));
        assertEquals(21, game.seat(1).hand().value());
        assertEquals(SeatPhase.STOOD, game.seat(1).phase());
        assertEquals(State.DEALER_TURN, game.state());
    }

    @Test
    @DisplayName("ブラックジャックは自動でスタンドし 3:2")
    void blackjackPays() {
        // 自分 A+K、ディーラー 9/8
        // 配り終えた時点で BJ なら手番は来ず、ディーラーの手番 → 精算まで進む (dealToAlice は配り終えた後も数 tick 進める)
        BlackjackGame game = dealToAlice(10, c(Rank.ACE), c(Rank.NINE), c(Rank.KING), c(Rank.EIGHT));
        assertEquals(0, count(BlackjackGame.TurnStarted.class), "BJ の席に手番は来ない");
        assertEquals(SeatPhase.SETTLED, game.seat(1).phase());
        assertEquals(0, count(BlackjackGame.DealerDrew.class), "スタンドした人がいなければディーラーは引かない");
        BlackjackGame.Settled settled = of(BlackjackGame.Settled.class).get(0);
        assertEquals(Outcome.BLACKJACK, settled.outcome());
        assertEquals(25, settled.payout());
        assertEquals(115, wallet.of(alice));
    }

    @Test
    @DisplayName("双方ブラックジャックは引き分けで返金。ディーラーだけ BJ なら 21 でも負け")
    void dealerBlackjack() {
        BlackjackGame game = dealToAlice(10, c(Rank.ACE), c(Rank.ACE), c(Rank.KING), c(Rank.QUEEN));
        run(game, 2);
        assertEquals(Outcome.PUSH, of(BlackjackGame.Settled.class).get(0).outcome());
        assertEquals(100, wallet.of(alice));

        log.clear();
        BlackjackGameTest fresh = new BlackjackGameTest();
        BlackjackGame game2 = fresh.dealToAlice(10, c(Rank.SEVEN), c(Rank.ACE), c(Rank.SEVEN), c(Rank.KING), c(Rank.SEVEN));
        fresh.log.addAll(game2.press(1, fresh.alice, Button.HIT, fresh.now));
        assertEquals(21, game2.seat(1).hand().value());
        fresh.run(game2, 2);
        assertEquals(Outcome.LOSE, fresh.of(BlackjackGame.Settled.class).get(0).outcome());
        assertEquals(90, fresh.wallet.of(fresh.alice));
    }

    @Test
    @DisplayName("ダブルダウン: 掛け金を倍にして1枚だけ引き、勝てば 4 倍戻る")
    void doubleDown() {
        // 自分 5+6=11、ディーラー 10/7=17。ダブルで 10 → 21 勝ち
        BlackjackGame game = dealToAlice(10, c(Rank.FIVE), c(Rank.TEN), c(Rank.SIX), c(Rank.SEVEN), c(Rank.TEN));
        log.addAll(game.press(1, alice, Button.DOUBLE, now));
        assertEquals(80, wallet.of(alice), "追加の掛け金を引く");
        assertEquals(20, game.seat(1).wager());
        assertTrue(game.seat(1).doubled());
        assertEquals(SeatPhase.STOOD, game.seat(1).phase());
        run(game, 3);
        BlackjackGame.Settled settled = of(BlackjackGame.Settled.class).get(0);
        assertEquals(Outcome.WIN, settled.outcome());
        assertTrue(settled.doubled());
        assertEquals(40, settled.payout());
        assertEquals(120, wallet.of(alice));
    }

    @Test
    @DisplayName("ダブルダウンはヒット後や残高不足では断られる")
    void doubleDenied() {
        BlackjackGame game = dealToAlice(10, c(Rank.TWO), c(Rank.TEN), c(Rank.THREE), c(Rank.SEVEN), c(Rank.TWO), c(Rank.TEN));
        log.addAll(game.press(1, alice, Button.HIT, now));
        log.addAll(game.press(1, alice, Button.DOUBLE, now));
        assertEquals(1, count(BlackjackGame.Denied.class));
        assertEquals(State.PLAYER_TURN, game.state());

        // 残高ぴったりの人はダブルできない
        BlackjackGameTest fresh = new BlackjackGameTest();
        fresh.wallet.give(fresh.bob, 10);
        BlackjackGame game2 = fresh.game(c(Rank.FIVE), c(Rank.TEN), c(Rank.SIX), c(Rank.SEVEN), c(Rank.TEN));
        fresh.log.addAll(game2.pressStart(0, fresh.bob, "bob", 10, fresh.now));
        fresh.run(game2, TIMING.joinTicks() + 10);
        fresh.log.addAll(game2.press(0, fresh.bob, Button.DOUBLE, fresh.now));
        assertEquals(1, fresh.count(BlackjackGame.Denied.class));
        assertEquals(0, fresh.wallet.of(fresh.bob));
        assertEquals(10, game2.seat(0).wager());
    }

    @Test
    @DisplayName("手番の時間切れで自動スタンド")
    void turnTimeout() {
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN));
        run(game, TIMING.turnTicks() + 1);
        assertEquals(1, count(BlackjackGame.TurnTimeout.class));
        assertTrue(game.seat(1).phase() == SeatPhase.STOOD || game.seat(1).phase() == SeatPhase.SETTLED);
        assertTrue(game.state() == State.DEALER_TURN || game.state() == State.RESULT);
    }

    @Test
    @DisplayName("他人の席・他人の手番・使われている席・二重の着席は断られる")
    void deniedPresses() {
        wallet.give(alice, 100).give(bob, 100);
        BlackjackGame game = game(c(Rank.NINE), c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.EIGHT), c(Rank.TEN), c(Rank.TEN));
        log.addAll(game.pressStart(0, alice, "alice", 10, now));
        log.addAll(game.pressStart(0, bob, "bob", 10, now));
        log.addAll(game.pressStart(2, alice, "alice", 10, now));
        assertEquals(2, count(BlackjackGame.Denied.class));
        log.addAll(game.pressStart(1, bob, "bob", 10, now));
        run(game, TIMING.joinTicks() + 10);
        assertEquals(State.PLAYER_TURN, game.state());
        assertEquals(0, game.activeSeat(), "左の席から");
        log.clear();
        log.addAll(game.press(1, bob, Button.HIT, now));
        log.addAll(game.press(0, bob, Button.HIT, now));
        assertEquals(2, count(BlackjackGame.Denied.class));
        assertEquals(2, game.seat(0).hand().size(), "何も配られていない");
        log.addAll(game.press(0, alice, Button.STAND, now));
        assertEquals(1, game.activeSeat(), "次は右隣");
    }

    @Test
    @DisplayName("払えない席は飛ばし、誰も払えなければ中止")
    void unfundedSeats() {
        wallet.give(alice, 100);
        BlackjackGame game = game(c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN));
        log.addAll(game.pressStart(0, alice, "alice", 10, now));
        log.addAll(game.pressStart(1, bob, "bob", 10, now));
        run(game, TIMING.joinTicks() + 10);
        assertEquals(1, count(BlackjackGame.SeatSkipped.class));
        assertEquals(SeatPhase.SEATED, game.seat(1).phase());
        assertEquals(SeatPhase.PLAYING, game.seat(0).phase());

        BlackjackGameTest fresh = new BlackjackGameTest();
        BlackjackGame game2 = fresh.game();
        fresh.log.addAll(game2.pressStart(0, fresh.carol, "carol", 10, fresh.now));
        fresh.run(game2, TIMING.joinTicks() + 5);
        assertEquals(1, fresh.count(BlackjackGame.RoundAborted.class));
        assertEquals(State.IDLE, game2.state());
    }

    @Test
    @DisplayName("ラウンド中の START は次のラウンドの参加になり、終わると受付が始まる")
    void joinDuringRound() {
        wallet.give(bob, 100);
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN),
                c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN), c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN));
        log.addAll(game.pressStart(2, bob, "bob", 10, now));
        assertEquals(SeatPhase.WAITING, game.seat(2).phase());
        log.addAll(game.press(1, alice, Button.STAND, now));
        run(game, 3 + TIMING.resultTicks() + 1);
        assertEquals(State.JOINING, game.state(), "待っている人がいるので受付へ");
        assertEquals(SeatPhase.SEATED, game.seat(1).phase());
    }

    @Test
    @DisplayName("座ったまま START を押さない席は時間切れで空く")
    void seatTimeout() {
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN), c(Rank.TEN));
        log.addAll(game.press(1, alice, Button.STAND, now));
        run(game, 3 + TIMING.resultTicks() + 1);
        assertEquals(SeatPhase.SEATED, game.seat(1).phase());
        run(game, TIMING.seatTimeoutTicks() + 2);
        assertEquals(SeatPhase.EMPTY, game.seat(1).phase());
        assertEquals(1, count(BlackjackGame.SeatFreed.class));
    }

    @Test
    @DisplayName("BET はラウンド中は変えられない")
    void betChange() {
        wallet.give(alice, 100);
        BlackjackGame game = game(c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN), c(Rank.TEN));
        log.addAll(game.pressStart(1, alice, "alice", 10, now));
        log.addAll(game.setBet(1, alice, 30, now));
        assertEquals(1, count(BlackjackGame.BetChanged.class));
        assertEquals(30, game.seat(1).bet());
        run(game, TIMING.joinTicks() + 10);
        assertEquals(30, game.seat(1).wager());
        log.addAll(game.setBet(1, alice, 50, now));
        assertEquals(1, count(BlackjackGame.Denied.class));
        log.addAll(game.setBet(1, bob, 50, now));
        assertEquals(2, count(BlackjackGame.Denied.class));
    }

    @Test
    @DisplayName("手番中に退出するとスタンド扱いで掛け金は残り、ラウンドの終わりに席が空く")
    void quitDuringTurn() {
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN), c(Rank.TEN));
        assertTrue(game.playerLeft(alice, now, log), "掛け金を持ったまま");
        assertEquals(SeatPhase.STOOD, game.seat(1).phase());
        assertEquals(State.DEALER_TURN, game.state());
        run(game, 3);
        assertEquals(Outcome.PUSH, of(BlackjackGame.Settled.class).get(0).outcome(), "17 vs 17");
        assertEquals(100, wallet.of(alice));
        run(game, TIMING.resultTicks() + 1);
        BlackjackGame.SeatFreed freed = of(BlackjackGame.SeatFreed.class).get(0);
        assertEquals(alice, freed.player());
        assertTrue(freed.gone());
        assertEquals(State.IDLE, game.state());
    }

    @Test
    @DisplayName("配られる前に退出した席はすぐ空く")
    void quitWhileWaiting() {
        wallet.give(alice, 100);
        BlackjackGame game = game(c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN));
        log.addAll(game.pressStart(1, alice, "alice", 10, now));
        assertFalse(game.playerLeft(alice, now, log));
        assertEquals(SeatPhase.EMPTY, game.seat(1).phase());
        run(game, TIMING.joinTicks() + 5);
        assertEquals(1, count(BlackjackGame.RoundAborted.class));
    }

    @Test
    @DisplayName("中断すると掛け金が返り、席が空く")
    void abortRefunds() {
        BlackjackGame game = dealToAlice(10, c(Rank.NINE), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.TEN));
        assertEquals(90, wallet.of(alice));
        log.addAll(game.abort());
        assertEquals(1, count(BlackjackGame.Refunded.class));
        assertEquals(100, wallet.of(alice));
        assertEquals(State.IDLE, game.state());
        assertEquals(SeatPhase.EMPTY, game.seat(1).phase());
    }

    @Test
    @DisplayName("3人: 左から順に手番が回り、精算が全員ぶん出る")
    void threeSeats() {
        wallet.give(alice, 100).give(bob, 100).give(carol, 100);
        // 配る順: a1 b1 c1 d1 a2 b2 c2 d2
        BlackjackGame game = game(c(Rank.TEN), c(Rank.TEN), c(Rank.TEN), c(Rank.SEVEN),
                c(Rank.NINE), c(Rank.EIGHT), c(Rank.SEVEN), c(Rank.TEN));
        log.addAll(game.pressStart(0, alice, "alice", 10, now));
        log.addAll(game.pressStart(1, bob, "bob", 10, now));
        log.addAll(game.pressStart(2, carol, "carol", 10, now));
        run(game, TIMING.joinTicks() + 12);
        assertEquals(State.PLAYER_TURN, game.state());
        assertEquals(List.of(0), of(BlackjackGame.TurnStarted.class).stream().map(BlackjackGame.TurnStarted::seat).toList());
        log.addAll(game.press(0, alice, Button.STAND, now));
        log.addAll(game.press(1, bob, Button.STAND, now));
        log.addAll(game.press(2, carol, Button.STAND, now));
        assertEquals(List.of(0, 1, 2), of(BlackjackGame.TurnStarted.class).stream().map(BlackjackGame.TurnStarted::seat).toList());
        run(game, 3);
        assertEquals(State.RESULT, game.state());
        List<BlackjackGame.Settled> settled = of(BlackjackGame.Settled.class);
        assertEquals(3, settled.size());
        // ディーラー 17: 19 勝ち、18 勝ち、17 引き分け
        assertEquals(Outcome.WIN, settled.get(0).outcome());
        assertEquals(Outcome.WIN, settled.get(1).outcome());
        assertEquals(Outcome.PUSH, settled.get(2).outcome());
        assertEquals(110, wallet.of(alice));
        assertEquals(110, wallet.of(bob));
        assertEquals(100, wallet.of(carol));
    }

    @Test
    @DisplayName("Timing の検証と既定")
    void timing() {
        assertEquals(200, BlackjackGame.Timing.defaults().joinTicks());
        assertEquals(600, BlackjackGame.Timing.defaults().turnTicks());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlackjackGame.Timing(0, 1, 1, 1, 1, 1));
    }
}
