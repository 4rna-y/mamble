package io.github.mamble;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 退出した人の帳簿のキャッシュを、精算が終わるまで保持する。
 *
 * <p>ブラックジャックやルーレットは、退出した人の掛け金もラウンドの終わりに精算する。
 * その書き込みは {@link CreditLedger#adjust} を通るので、キャッシュが残っていなければならない。
 * 台ごとに「保持したい」と言えるようにし、全部の台が手放して本人もオフラインなら unload する。
 */
public final class LedgerHolds {

    private final CreditLedger ledger;
    private final Map<UUID, Set<String>> owners = new HashMap<>();

    public LedgerHolds(CreditLedger ledger) {
        this.ledger = ledger;
    }

    /** {@code owner} (台の種類) がこの人のキャッシュを必要としている。 */
    public void hold(UUID player, String owner) {
        owners.computeIfAbsent(player, ignored -> new HashSet<>()).add(owner);
    }

    /**
     * {@code owner} が手放した。誰も必要とせず、本人もオフラインならキャッシュを捨てる。
     */
    public void release(UUID player, String owner, boolean online) {
        Set<String> set = owners.get(player);
        if (set != null) {
            set.remove(owner);
            if (set.isEmpty()) {
                owners.remove(player);
            }
        }
        if (!online && !owners.containsKey(player)) {
            ledger.unload(player);
        }
    }

    /** {@code owner} が保持している全員を手放す。停止時に使う。 */
    public void releaseAll(String owner) {
        for (UUID player : Set.copyOf(owners.keySet())) {
            release(player, owner, false);
        }
    }

    public boolean isHeld(UUID player) {
        return owners.containsKey(player);
    }
}
