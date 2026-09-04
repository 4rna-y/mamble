package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreditLedgerTest {

    private final FakeKvClient kv = new FakeKvClient();
    private final RecordingLog log = new RecordingLog();
    private final CreditLedger ledger = new CreditLedger(kv, "mamble", 2, log);
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void close() {
        ledger.close(2);
    }

    @Test
    @DisplayName("記録の無い人は 0 から。名前も控える")
    void loadsZeroForNewPlayer() throws Exception {
        assertTrue(ledger.balance(player).isEmpty());
        assertEquals(0L, ledger.load(player, "Steve").get(2, TimeUnit.SECONDS));
        assertEquals(Optional.of(0L), ledger.balance(player));
        assertEquals(Optional.of("Steve"), kv.get("mamble/name/" + player));
        assertTrue(ledger.reachable());
    }

    @Test
    @DisplayName("保存済みの残高を読む")
    void loadsStoredBalance() throws Exception {
        kv.put("mamble/credit/" + player, "1234");
        assertEquals(1234L, ledger.load(player, "Steve").get(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("増減は即座にキャッシュへ、そして順に mstore へ")
    void adjustPersistsInOrder() throws Exception {
        ledger.load(player, "Steve").get(2, TimeUnit.SECONDS);
        assertEquals(100L, ledger.adjust(player, 100));
        assertEquals(70L, ledger.adjust(player, -30));
        assertEquals(Optional.of(70L), ledger.balance(player));
        ledger.close(2);
        assertEquals(Optional.of("70"), kv.get("mamble/credit/" + player));
    }

    @Test
    @DisplayName("読み込んでいない人と、負になる増減は弾く")
    void guards() throws Exception {
        assertThrows(IllegalStateException.class, () -> ledger.adjust(player, 10));
        ledger.load(player, "Steve").get(2, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> ledger.adjust(player, -1));
        assertEquals(Optional.of(0L), ledger.balance(player));
        assertThrows(IllegalArgumentException.class, () -> ledger.set(player, -5));
        assertEquals(500L, ledger.set(player, 500));
    }

    @Test
    @DisplayName("退出でキャッシュから外れる")
    void unload() throws Exception {
        ledger.load(player, "Steve").get(2, TimeUnit.SECONDS);
        ledger.unload(player);
        assertFalse(ledger.isLoaded(player));
    }

    @Test
    @DisplayName("mstore に届かなければ reachable が false になり、ログに残る")
    void unreachable() throws Exception {
        kv.pingFails = true;
        assertThrows(Exception.class, () -> ledger.ping().get(2, TimeUnit.SECONDS));
        assertFalse(ledger.reachable());
        assertTrue(log.lines.stream().anyMatch(line -> line.contains("疎通確認")));
    }

    @Test
    @DisplayName("1回の失敗なら挑み直して成功する")
    void retries() throws Exception {
        kv.put("mamble/credit/" + player, "42");
        kv.failures.set(1);
        assertEquals(42L, ledger.load(player, "Steve").get(2, TimeUnit.SECONDS));
        assertTrue(ledger.reachable());
    }

    @Test
    @DisplayName("キーの形")
    void keys() {
        assertEquals("mamble/credit/" + player, ledger.creditKey(player));
        assertEquals("mamble/name/" + player, ledger.nameKey(player));
        assertEquals("x/credit/" + player, new CreditLedger(kv, "/x/", 1, log).creditKey(player));
    }
}
