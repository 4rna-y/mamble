package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.mamble.RouletteGame.Event;
import io.github.mamble.RouletteGame.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 受付 40 tick、回転 20 tick、結果 20 tick、最大 3 枚。番号は固定で注入する。 */
class RouletteGameTest {

    static final RouletteGame.Settings SETTINGS = new RouletteGame.Settings(40, 20, 20, 3);

    final BlackjackGameTest.FakeWallet wallet = new BlackjackGameTest.FakeWallet();
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final List<Event> log = new ArrayList<>();
    long now = 0;
    int nextNumber = 17;

    RouletteGame game() {
        return new RouletteGame(SETTINGS, wallet, () -> nextNumber);
    }

    void run(RouletteGame game, int ticks) {
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

    @Test
    @DisplayName("最初のチップで残高が減り受付が始まる。時間切れで回り、精算して結果、そして待機に戻る")
    void fullRound() {
        wallet.give(alice, 100);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n17", 10, now));
        assertEquals(State.BETTING, game.state());
        assertEquals(90, wallet.of(alice));
        assertEquals(1, count(RouletteGame.ChipPlaced.class));
        assertEquals(1, count(RouletteGame.Countdown.class));
        log.addAll(game.placeChip(alice, "alice", "black", 10, now));
        assertEquals(20, game.bettor(alice).stake());
        run(game, SETTINGS.betTicks() + 1);
        assertEquals(State.SPINNING, game.state());
        assertEquals(17, of(RouletteGame.SpinStarted.class).get(0).number());
        run(game, SETTINGS.spinTicks());
        assertEquals(State.RESULT, game.state());
        RouletteGame.Settled settled = of(RouletteGame.Settled.class).get(0);
        assertEquals(20, settled.staked());
        assertEquals(360 + 20, settled.returned(), "単番号 36 倍 + 黒 2 倍 (17 は黒)");
        assertEquals(2, settled.wins().size());
        assertEquals(80 + 380, wallet.of(alice));
        assertEquals(List.of(17), game.history());
        run(game, SETTINGS.resultTicks() + 1);
        assertEquals(State.IDLE, game.state());
        assertEquals(1, count(RouletteGame.RoundEnded.class));
        assertFalse(game.hasChips());
    }

    @Test
    @DisplayName("0 が出ると外賭けは全部負け")
    void zeroLosesOutside() {
        wallet.give(alice, 100);
        nextNumber = 0;
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "red", 10, now));
        log.addAll(game.placeChip(alice, "alice", "even", 10, now));
        log.addAll(game.placeChip(alice, "alice", "d1", 10, now));
        run(game, SETTINGS.betTicks() + SETTINGS.spinTicks() + 1);
        RouletteGame.Settled settled = of(RouletteGame.Settled.class).get(0);
        assertEquals(30, settled.staked());
        assertEquals(0, settled.returned());
        assertEquals(70, wallet.of(alice));
    }

    @Test
    @DisplayName("回転中と結果表示中は置けず、残高も減らない")
    void deniedWhileSpinning() {
        wallet.give(alice, 100);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        run(game, SETTINGS.betTicks() + 1);
        log.addAll(game.placeChip(alice, "alice", "n2", 10, now));
        assertEquals(1, count(RouletteGame.Denied.class));
        assertEquals(90, wallet.of(alice));
        run(game, SETTINGS.spinTicks());
        log.addAll(game.placeChip(alice, "alice", "n2", 10, now));
        log.addAll(game.removeChip(alice, "n1", now));
        assertEquals(3, count(RouletteGame.Denied.class));
    }

    @Test
    @DisplayName("戻すと返金。他人のチップは戻せない。全部戻すと待機に戻る")
    void removeChip() {
        wallet.give(alice, 100).give(bob, 100);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        log.addAll(game.placeChip(bob, "bob", "n1", 10, now));
        assertEquals(30, game.cellTotal("n1"));
        assertEquals(3, game.cellChips("n1"));
        log.addAll(game.removeChip(bob, "n2", now));
        assertEquals(1, count(RouletteGame.Denied.class));
        log.addAll(game.removeChip(alice, "n1", now));
        assertEquals(90, wallet.of(alice));
        assertEquals(20, game.cellTotal("n1"));
        log.addAll(game.removeChip(alice, "n1", now));
        log.addAll(game.removeChip(bob, "n1", now));
        assertEquals(100, wallet.of(alice));
        assertEquals(100, wallet.of(bob));
        assertEquals(State.IDLE, game.state());
        assertEquals(1, count(RouletteGame.BettingClosed.class));
    }

    @Test
    @DisplayName("枚数の上限と残高不足")
    void limits() {
        wallet.give(alice, 25);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        log.addAll(game.placeChip(alice, "alice", "n2", 10, now));
        log.addAll(game.placeChip(alice, "alice", "n3", 10, now));
        assertEquals(1, count(RouletteGame.Denied.class), "残高不足");
        assertEquals(5, wallet.of(alice));
        wallet.give(alice, 100);
        log.addAll(game.placeChip(alice, "alice", "n3", 10, now));
        log.addAll(game.placeChip(alice, "alice", "n4", 10, now));
        assertEquals(2, count(RouletteGame.Denied.class), "3 枚まで");
        assertEquals(3, game.bettor(alice).chipCount());
    }

    @Test
    @DisplayName("複数人の精算はそれぞれ 1 回ずつ")
    void twoPlayers() {
        wallet.give(alice, 100).give(bob, 100);
        nextNumber = 2;
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "black", 10, now));
        log.addAll(game.placeChip(bob, "bob", "red", 10, now));
        log.addAll(game.placeChip(bob, "bob", "c2", 10, now));
        run(game, SETTINGS.betTicks() + SETTINGS.spinTicks() + 1);
        List<RouletteGame.Settled> settled = of(RouletteGame.Settled.class);
        assertEquals(2, settled.size());
        assertEquals(110, wallet.of(alice), "黒で 2 倍");
        assertEquals(110, wallet.of(bob), "赤は外れ、コラム 2 は 3 倍");
    }

    @Test
    @DisplayName("退出しても精算され、ラウンドの終わりに退出者として知らされる")
    void quitter() {
        wallet.give(alice, 100);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n17", 10, now));
        assertTrue(game.playerLeft(alice, now, log));
        assertFalse(game.playerLeft(bob, now, log));
        run(game, SETTINGS.betTicks() + SETTINGS.spinTicks() + SETTINGS.resultTicks() + 2);
        assertEquals(450, wallet.of(alice));
        assertEquals(java.util.Set.of(alice), of(RouletteGame.RoundEnded.class).get(0).gone());
    }

    @Test
    @DisplayName("中断は受付中・回転中なら返金し、結果表示中なら返金しない")
    void abort() {
        wallet.give(alice, 100);
        RouletteGame game = game();
        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        run(game, SETTINGS.betTicks() + 1);
        log.addAll(game.abort());
        assertEquals(100, wallet.of(alice));
        assertEquals(State.IDLE, game.state());

        log.addAll(game.placeChip(alice, "alice", "n1", 10, now));
        run(game, SETTINGS.betTicks() + SETTINGS.spinTicks() + 1);
        long after = wallet.of(alice);
        log.addAll(game.abort());
        assertEquals(after, wallet.of(alice), "精算済みなので返金しない");
    }

    @Test
    @DisplayName("設定の検証と既定")
    void settings() {
        assertEquals(400, RouletteGame.Settings.defaults().betTicks());
        assertEquals(120, RouletteGame.Settings.defaults().spinTicks());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RouletteGame.Settings(1, 10, 1, 1));
    }
}
